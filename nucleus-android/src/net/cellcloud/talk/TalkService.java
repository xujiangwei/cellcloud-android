/*
-----------------------------------------------------------------------------
This source file is part of Cell Cloud.

Copyright (c) 2009-2017 Cell Cloud Team (www.cellcloud.net)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
-----------------------------------------------------------------------------
*/

package net.cellcloud.talk;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import net.cellcloud.common.Cryptology;
import net.cellcloud.common.LogLevel;
import net.cellcloud.common.Logger;
import net.cellcloud.common.Message;
import net.cellcloud.common.NonblockingAcceptor;
import net.cellcloud.common.Packet;
import net.cellcloud.common.Service;
import net.cellcloud.common.Session;
import net.cellcloud.core.Cellet;
import net.cellcloud.core.CelletSandbox;
import net.cellcloud.core.Nucleus;
import net.cellcloud.core.NucleusContext;
import net.cellcloud.exception.InvalidException;
import net.cellcloud.exception.SingletonException;
import net.cellcloud.talk.dialect.ActionDialectFactory;
import net.cellcloud.talk.dialect.ChunkDialectFactory;
import net.cellcloud.talk.dialect.Dialect;
import net.cellcloud.talk.dialect.DialectEnumerator;
import net.cellcloud.talk.stuff.PrimitiveSerializer;
import net.cellcloud.util.Network;
import net.cellcloud.util.TimeReceiver;
import net.cellcloud.util.Utils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 会话服务。
 * 
 * 会话服务是节点与 Cellet 之间通信的基础服务。
 * 通过会话服务完成节点与 Cellet 之间的数据传输。
 * 
 * @author Ambrose Xu
 * 
 */
public final class TalkService implements Service, SpeakerDelegate {

	private static TalkService instance = null;

	private int port;
	private int block;

	private long sessionTimeout;

	private NonblockingAcceptor acceptor;
	private NucleusContext nucleusContext;
	private TalkAcceptorHandler talkHandler;

	/** 线程执行器。 */
	protected ExecutorService executor;

	/** 待检验 Session 。 */
	private ConcurrentHashMap<Long, Certificate> unidentifiedSessions;
	/** Session 与 Tag 的映射。 */
	private ConcurrentHashMap<Long, String> sessionTagMap;
	/** Tag 与 Session 上下文的映射。 */
	private ConcurrentHashMap<String, TalkSessionContext> tagContexts;

	/** Tag 缓存列表。 */
	private ConcurrentSkipListSet<String> tagList;

	/** 私有协议 Speaker。 */
	private ConcurrentHashMap<String, Speaker> speakerMap;
	/** 存储所有 Speaker 的列表。 */
	protected Vector<Speaker> speakers;

	private ConcurrentHashMap<String, Speakable> httpSpeakerMap = null;

	/** 时间广播接收器。 */
	private TimeReceiver receiver;
	/** 对话守护任务。 */
	private TalkServiceDaemon daemon;

	/** 用户客户端模式下的对话监听器列表。 */
	private ArrayList<TalkListener> listeners;

	/** Cellet 回调事件监听器。 */
	private CelletCallbackListener callbackListener;

	/** 对话事件委派。 */
	private TalkDelegate delegate;

	/** 有序的状态任务切换任务。 */
	private LinkedList<Runnable> orderedTasks;
	private AtomicBoolean taskRunning;

	/**
	 * 构造函数。
	 * 
	 * @param nucleusContext 内核上下文。
	 * 
	 * @throws SingletonException
	 */
	public TalkService(NucleusContext nucleusContext) throws SingletonException {
		if (null == TalkService.instance) {
			TalkService.instance = this;

			this.nucleusContext = nucleusContext;

			this.port = 7000;
			this.block = 32768;

			// 15 分钟
			this.sessionTimeout = 15L * 60L * 1000L;

			// 创建执行器
			this.executor = Executors.newCachedThreadPool();

			// 添加默认方言工厂
			DialectEnumerator.getInstance().addFactory(new ActionDialectFactory(this.executor));
			DialectEnumerator.getInstance().addFactory(new ChunkDialectFactory(this.executor));

			this.callbackListener = DialectEnumerator.getInstance();
			this.delegate = DialectEnumerator.getInstance();

			this.receiver = new TimeReceiver();
			this.orderedTasks = new LinkedList<Runnable>();
			this.taskRunning = new AtomicBoolean(false);
		}
		else {
			throw new SingletonException(TalkService.class.getName());
		}
	}

	/**
	 * 返回会话服务单例。
	 * 
	 * @return 如果返回空指针，则表示内核服务尚未启动，需要先启动内核。
	 */
	public static TalkService getInstance() {
		return TalkService.instance;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean startup() {
		if (null == this.unidentifiedSessions) {
			this.unidentifiedSessions = new ConcurrentHashMap<Long, Certificate>();
		}
		if (null == this.sessionTagMap) {
			this.sessionTagMap = new ConcurrentHashMap<Long, String>();
		}
		if (null == this.tagContexts) {
			this.tagContexts = new ConcurrentHashMap<String, TalkSessionContext>();
		}
		if (null == this.tagList) {
			this.tagList = new ConcurrentSkipListSet<String>();
		}

		if (null == this.acceptor) {
			// 创建网络适配器
			this.acceptor = new NonblockingAcceptor();
			this.acceptor.setBlockSize(this.block);

			// 定义包标识
			byte[] head = { 0x20, 0x10, 0x11, 0x10 };
			byte[] tail = { 0x19, 0x78, 0x10, 0x04 };
			this.acceptor.defineDataMark(head, tail);

			// 设置处理器
			this.talkHandler = new TalkAcceptorHandler(this);
			this.acceptor.setHandler(this.talkHandler);
		}

		// 最大连接数
		this.acceptor.setMaxConnectNum(20);

		boolean succeeded = this.acceptor.bind(this.port);
		if (succeeded) {
			this.startDaemon();
		}

		return succeeded;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shutdown() {
		if (null != this.acceptor) {
			this.acceptor.unbind();
		}

		stopDaemon();

		if (null != this.executor) {
			this.executor.shutdown();
		}
	}

	/**
	 * 设置服务端口。
	 * 
	 * 在 {@link TalkService#startup()} 之前设置才能生效。
	 * 
	 * @param port 指定服务监听端口。
	 */
	public void setPort(int port) {
		if (null != this.acceptor && this.acceptor.isRunning()) {
			throw new InvalidException("Can't set the port in talk service after the start");
		}

		this.port = port;
	}

	/**
	 * 返回服务端口。
	 * 
	 * @return 返回正在使用的服务端口号。
	 */
	public int getPort() {
		return this.port;
	}

	/**
	 * 设置适配器缓存块大小。
	 * 
	 * @param size 指定新的缓存块大小。
	 */
	public void setBlockSize(int size) {
		this.block = size;
	}

	/**
	 * 启动任务守护线程。
	 */
	public void startDaemon() {
		if (null != this.daemon) {
			return;
		}

		this.daemon = new TalkServiceDaemon(false);
		this.receiver.registerReceiver(Nucleus.getInstance().getAppContext(), this.daemon);
	}

	/**
	 * 关闭任务守护线程。
	 */
	public void stopDaemon() {
		if (null != this.daemon) {
			this.daemon.stop();
			this.receiver.unregisterReceiver(Nucleus.getInstance().getAppContext());
			this.daemon = null;
		}

		// 关闭所有 Speaker
		if (null != this.speakers) {
			Iterator<Speaker> iter = this.speakers.iterator();
			while (iter.hasNext()) {
				Speaker speaker = iter.next();
				speaker.hangUp();
			}
			this.speakers.clear();
		}

		if (null != this.speakerMap) {
			this.speakerMap.clear();
		}

		// 关闭所有方言工厂
		DialectEnumerator.getInstance().shutdownAll();
	}

	/**
	 * 守护任务是否正在运行。
	 * 
	 * @return 如果守护任务正在运行返回 <code>true</code> 。
	 */
	public boolean daemonRunning() {
		return (null != this.daemon);
	}

	/**
	 * 进入睡眠模式。
	 */
	public void sleep() {
		if (null == this.daemon) {
			return;
		}

		if (Logger.isDebugLevel()) {
			Logger.d(this.getClass(), "Sleep");
		}

		synchronized (this.orderedTasks) {
			this.orderedTasks.add(new Runnable() {
				@Override
				public void run() {
					if (null != speakers) {
						for (Speaker s : speakers) {
							s.sleep();
						}
					}

					if (null != daemon) {
						daemon.sleep();
					}

					DialectEnumerator.getInstance().sleepAll();
				}
			});
		}

		if (!this.taskRunning.get()) {
			this.taskRunning.set(true);

			this.executor.execute(new Runnable() {
				@Override
				public void run() {
					synchronized (orderedTasks) {
						while (!orderedTasks.isEmpty()) {
							Runnable task = orderedTasks.removeFirst();
							task.run();
						}
					}

					taskRunning.set(false);
				}
			});
		}
	}

	/**
	 * 从睡眠模式唤醒。
	 */
	public void wakeup() {
		if (null == this.daemon) {
			return;
		}

		if (Logger.isDebugLevel()) {
			Logger.d(this.getClass(), "Wakeup");
		}

		synchronized (this.orderedTasks) {
			this.orderedTasks.add(new Runnable() {
				@Override
				public void run() {
					if (null != speakers) {
						for (Speaker s : speakers) {
							s.wakeup();
						}
					}

					if (null != daemon) {
						daemon.wakeup();
					}

					DialectEnumerator.getInstance().wakeupAll();
				}
			});
		}

		if (!this.taskRunning.get()) {
			this.taskRunning.set(true);

			this.executor.execute(new Runnable() {
				@Override
				public void run() {
					synchronized (orderedTasks) {
						while (!orderedTasks.isEmpty()) {
							Runnable task = orderedTasks.removeFirst();
							task.run();
						}
					}

					taskRunning.set(false);
				}
			});
		}
	}

	/**
	 * 添加会话监听器。
	 * 
	 * @param listener 指定添加的监听器对象实例。
	 */
	public void addListener(TalkListener listener) {
		if (null == this.listeners) {
			this.listeners = new ArrayList<TalkListener>();
		}

		synchronized (this.listeners) {
			if (!this.listeners.contains(listener)) {
				this.listeners.add(listener);
			}
		}
	}

	/**
	 * 删除会话监听器。
	 * 
	 * @param listener 指定删除的监听器对象实例。
	 */
	public void removeListener(TalkListener listener) {
		if (null == this.listeners) {
			return;
		}

		synchronized (this.listeners) {
			this.listeners.remove(listener);
		}
	}

	/**
	 * 是否已添加指定的监听器。
	 * 
	 * @param listener 指定待判断的监听器实例。
	 * @return 如果指定的监听器已添加到会话服务则返回 <code>true</code> ，否则返回 <code>false</code> 。
	 */
	public boolean hasListener(TalkListener listener) {
		if (null == this.listeners) {
			return false;
		}

		synchronized (this.listeners) {
			return this.listeners.contains(listener);
		}
	}

	/**
	 * 返回当前所有会话者的 Tag 列表。
	 * 
	 * @return 返回已经连接的会话客户端 Tag 列表。
	 */
	public Set<String> getTalkerList() {
		return this.tagList;
	}

	/**
	 * 向指定 Tag 端发送原语。
	 * 
	 * @param targetTag 指定目标 Tag 。
	 * @param primitive 指定发送的原语。
	 * @param cellet 指定源 Cellet 。
	 * @param sandbox 指定校验用的安全沙箱实例。
	 * @return 如果发送原语成功返回 <code>true</code> ，否则返回 <code>false</code> 。
	 */
	public boolean notice(String targetTag, Primitive primitive, Cellet cellet, CelletSandbox sandbox) {
		// 检查 Cellet 合法性
		if (!Nucleus.getInstance().checkSandbox(cellet, sandbox)) {
			Logger.w(TalkService.class, "Illegal cellet : " + cellet.getFeature().getIdentifier());
			return false;
		}

		TalkSessionContext context = this.tagContexts.get(targetTag);
		if (null == context) {
			if (Logger.isDebugLevel()) {
				Logger.w(TalkService.class, "Can't find target tag in context list : " + targetTag);
			}

			// 因为没有直接发送出去原语，所以返回 false
			return false;
		}

		Message message = null;

		synchronized (context) {
			for (Session session : context.getSessions()) {
				// 返回 tracker
				TalkTracker tracker = context.getTracker(session);

				if (tracker.hasCellet(cellet)) {
					// 对方言进行是否劫持处理
					if (null != this.callbackListener && primitive.isDialectal()) {
						boolean ret = this.callbackListener.doTalk(cellet, targetTag, primitive.getDialect());
						if (!ret) {
							// 劫持会话
							return true;
						}
					}

					// 检查是否加密连接
					TalkCapacity cap = tracker.getCapacity();
					if (null != cap && cap.secure && !session.isSecure()) {
						session.activeSecretKey((byte[]) session.getAttribute("key"));
					}

					// 打包
					message = this.packetDialogue(cellet, primitive, session);

					if (null != message) {
						session.write(message);
					}
					else {
						Logger.e(this.getClass(), "Packet error");
					}
				}
			}
		}

		return (null != message);
	}

	/**
	 * 向指定 Tag 端发送原语方言。
	 * 
	 * @param targetTag 指定目标 Tag 。
	 * @param dialect 指定发送的原语方言。
	 * @param cellet 指定源 Cellet 。
	 * @param sandbox 指定校验用的安全沙箱实例。
	 * @return 如果发送原语方言成功返回 <code>true</code> ，否则返回 <code>false</code> 。
	 */
	public boolean notice(String targetTag, Dialect dialect, Cellet cellet, CelletSandbox sandbox) {
		Primitive primitive = dialect.reconstruct();
		if (null != primitive) {
			return this.notice(targetTag, primitive, cellet, sandbox);
		}

		return false;
	}

	/**
	 * 向指定的 Cellet 发起会话请求。
	 * 
	 * @param identifiers 指定要进行会话的 Cellet 标识名列表。
	 * @param address 指定服务器地址及端口。
	 * @return 返回是否成功发起请求。
	 */
	public boolean call(String[] identifiers, InetSocketAddress address) {
		ArrayList<String> list = new ArrayList<String>(identifiers.length);
		for (String identifier : identifiers) {
			list.add(identifier);
		}
		return this.call(list, address);
	}

	/**
	 * 向指定的 Cellet 发起会话请求。
	 * 
	 * @param identifiers 指定要进行会话的 Cellet 标识名列表。
	 * @param address 指定服务器地址及端口。
	 * @param capacity 指定能力协商。
	 * @return 返回是否成功发起请求。
	 */
	public boolean call(String[] identifiers, InetSocketAddress address, TalkCapacity capacity) {
		ArrayList<String> list = new ArrayList<String>(identifiers.length);
		for (String identifier : identifiers) {
			list.add(identifier);
		}
		return this.call(list, address, capacity);
	}

	/**
	 * 向指定的 Cellet 发起会话请求。
	 * 
	 * @param identifiers 指定要进行会话的 Cellet 标识名列表。
	 * @param address 指定服务器地址及端口。
	 * @return 返回是否成功发起请求。
	 */
	public boolean call(List<String> identifiers, InetSocketAddress address) {
		return this.call(identifiers, address, null, false);
	}

	/**
	 * 向指定的 Cellet 发起会话请求。
	 * 
	 * @param identifiers 指定要进行会话的 Cellet 标识名列表。
	 * @param address 指定服务器地址及端口。
	 * @param http 指定是否使用 HTTP 协议。
	 * @return 返回是否成功发起请求。
	 */
	public boolean call(List<String> identifiers, InetSocketAddress address, boolean http) {
		return this.call(identifiers, address, null, http);
	}

	/**
	 * 向指定的 Cellet 发起会话请求。
	 * 
	 * @param identifiers 指定要进行会话的 Cellet 标识名列表。
	 * @param address 指定服务器地址及端口。
	 * @param capacity 指定能力协商。
	 * @return 返回是否成功发起请求。
	 */
	public boolean call(List<String> identifiers, InetSocketAddress address, TalkCapacity capacity) {
		return this.call(identifiers, address, capacity, false);
	}

	/**
	 * 向指定的 Cellet 发起会话请求。
	 * 
	 * @param identifiers 指定要进行会话的 Cellet 标识名列表。
	 * @param address 指定服务器地址及端口。
	 * @param capacity 指定能力协商。
	 * @param http 指定是否使用 HTTP 协议。
	 * @return 返回是否成功发起请求。
	 */
	public synchronized boolean call(List<String> identifiers, InetSocketAddress address, TalkCapacity capacity, boolean http) {
		if (!http) {
			// 私有协议 Speaker

			if (null == this.speakers) {
				this.speakers = new Vector<Speaker>();
				this.speakerMap = new ConcurrentHashMap<String, Speaker>();
			}

			for (String identifier : identifiers) {
				if (this.speakerMap.containsKey(identifier)) {
					// 列表里已经有对应的 Cellet，不允许再次 Call
					return false;
				}
			}

			// 创建新的 Speaker
			Speaker speaker = new Speaker(address, this, this.block, capacity);
			this.speakers.add(speaker);

			// FIXME 28/11/14 原先在 call 检查 Speaker 是否是 Lost 状态，如果 lost 是 true，则置为 false
			// 复位 Speaker 参数
			// speaker.reset();

			for (String identifier : identifiers) {
				this.speakerMap.put(identifier, speaker);
			}

			// Call
			return speaker.call(identifiers);
		}
		else {
			// HTTP 协议 Speaker

			// TODO
			return false;
		}
	}

	/**
	 * 挂断 Cellet 会话服务。
	 * 
	 * @param identifiers 指定需挂断的 Cellet 标识符。
	 */
	public void hangUp(String[] identifiers) {
		ArrayList<String> list = new ArrayList<String>(identifiers.length);
		for (String id : identifiers) {
			list.add(id);
		}

		this.hangUp(list);
	}

	/**
	 * 挂断 Cellet 会话服务。
	 * 
	 * @param identifiers 指定需挂断的 Cellet 标识符。
	 */
	public void hangUp(List<String> identifiers) {
		if (null != this.speakerMap) {
			for (String identifier : identifiers) {
				if (this.speakerMap.containsKey(identifier)) {
					// 删除
					Speaker speaker = this.speakerMap.remove(identifier);

					// 删除其他关联的 speaker
					for (String celletIdentifier : speaker.getIdentifiers()) {
						this.speakerMap.remove(celletIdentifier);
					}

					this.speakers.remove(speaker);

					speaker.hangUp();
				}
			}
		}
	}

	/**
	 * 向指定 Cellet 发送原语。
	 * 
	 * @param identifier 指定目标 Cellet 的标识。
	 * @param primitive 指定需发送的原语。
	 * @return 返回是否成功处理了发送请求。
	 */
	public boolean talk(String identifier, Primitive primitive) {
		if (null != this.speakerMap) {
			Speaker speaker = this.speakerMap.get(identifier);
			if (null != speaker) {
				// Speaker
				return speaker.speak(identifier, primitive);
			}
		}

		return false;
	}

	/**
	 * 向指定 Cellet 发送方言。
	 * 
	 * @param identifier 指定目标 Cellet 的标识。
	 * @param dialect 指定需发送的方言。
	 * @return 返回是否成功处理了发送请求。
	 */
	public boolean talk(String identifier, Dialect dialect) {
		if (null == this.speakerMap && null == this.httpSpeakerMap) return false;

		// 通知委派
		if (null != this.delegate) {
			boolean ret = this.delegate.doTalk(identifier, dialect);
			if (!ret) {
				// 委派劫持，依然返回 true
				return true;
			}
		}

		// 方言转为原语
		Primitive primitive = dialect.reconstruct();

		if (null != primitive) {
			boolean ret = this.talk(identifier, primitive);

			// 发送成功，通知委派
			if (ret && null != this.delegate) {
				this.delegate.didTalk(identifier, dialect);
			}

			return ret;
		}

		return false;
	}

	/**
	 * 是否已经与 Cellet 建立服务。
	 * 
	 * @param identifier 指定待判断的 Cellet 标识。
	 * @return 如果已经建立服务返回 <code>true</code> 。
	 */
	public boolean isCalled(String identifier) {
		return this.isCalled(identifier, 0);
	}

	/**
	 * 是否已经与 Cellet 建立服务。
	 * 
	 * @param identifier 指定待判断的 Cellet 标识。
	 * @param millis 指定以毫秒为单位的执行网络状态判断的超时时间。
	 * @return 如果已经建立服务返回 <code>true</code> 。
	 */
	public boolean isCalled(String identifier, long millis) {
		boolean ret = false;

		if (null != this.speakerMap) {
			Speaker speaker = this.speakerMap.get(identifier);
			if (null != speaker) {
				ret = speaker.isCalled() && Network.isConnectedOrConnecting(Nucleus.getInstance().getAppContext());

				if (ret && millis > 0) {
					long time = System.currentTimeMillis();

					// 发送心跳
					if (!speaker.heartbeat()) {
						return false;
					}

					synchronized (speaker) {
						try {
							speaker.wait(millis);
						} catch (InterruptedException e) {
							// Nothing
						}
					}

					long d = speaker.heartbeatTime - time;
					if (d >= 0 && d <= millis) {
						ret = true;
					}
					else {
						if (d < 0) {
							if (!speaker.heartbeat()) {
								return false;
							}

							synchronized (speaker) {
								try {
									speaker.wait(millis + 1000L);
								} catch (InterruptedException e) {
									// Nothing
								}
							}

							d = speaker.heartbeatTime - time;
							return (d >= 0);
						}
						else {
							ret = true;
						}
					}
				}
			}
		}

		return ret;
	}

	/**
	 * 重置所有对话连接器的数据处理间隔。
	 * 
	 * @param interval 指定以毫秒为单位的时间间隔。
	 * @return 返回是否重置成功。
	 */
	public boolean resetInterval(long interval) {
		if (interval < 20) {
			return false;
		}

		if (null != this.speakers) {
			for (Speaker speaker : this.speakers) {
				speaker.resetInterval(interval);
			}

			return true;
		}

		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onDialogue(Speakable speaker, String identifier, Primitive primitive) {
		boolean delegated = (null != this.delegate && primitive.isDialectal());
		if (delegated) {
			boolean ret = this.delegate.doDialogue(identifier, primitive.getDialect());
			if (!ret) {
				// 劫持对话
				return;
			}
		}

		if (null != this.listeners) {
			synchronized (this.listeners) {
				for (int i = 0, size = this.listeners.size(); i < size; ++i) {
					this.listeners.get(i).dialogue(identifier, primitive);
				}
			}
		}

		if (delegated) {
			this.delegate.didDialogue(identifier, primitive.getDialect());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onTalked(Speakable speaker, String identifier, Primitive primitive) {
		if (null != this.listeners) {
			synchronized (this.listeners) {
				for (int i = 0, size = this.listeners.size(); i < size; ++i) {
					this.listeners.get(i).talked(identifier, primitive);
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onContacted(Speakable speaker, String identifier) {
		if (null == this.listeners) {
			return;
		}

		String tag = speaker.getRemoteTag();
		synchronized (this.listeners) {
			for (int i = 0, size = this.listeners.size(); i < size; ++i) {
				this.listeners.get(i).contacted(identifier, tag);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onQuitted(Speakable speaker, String identifier) {
		if (null == this.listeners) {
			return;
		}

		String tag = speaker.getRemoteTag();
		synchronized (this.listeners) {
			for (int i = 0, size = this.listeners.size(); i < size; ++i) {
				this.listeners.get(i).quitted(identifier, tag);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onFailed(Speakable speaker, TalkServiceFailure failure) {
		if (null == this.listeners) {
			return;
		}

		String tag = speaker.getRemoteTag();
		synchronized (this.listeners) {
			for (int i = 0, size = this.listeners.size(); i < size; ++i) {
				this.listeners.get(i).failed(tag, failure);
			}
		}
	}

	/**
	 * 启动对指定 Session 的管理。
	 * @param session
	 * @return
	 */
	protected synchronized Certificate openSession(Session session) {
		Long sid = session.getId();
		if (this.unidentifiedSessions.containsKey(sid)) {
			return this.unidentifiedSessions.get(sid);
		}

		Certificate cert = new Certificate();
		cert.session = session;
		cert.key = Utils.randomString(8);
		cert.plaintext = Utils.randomString(16);
		this.unidentifiedSessions.put(sid, cert);

		return cert;
	}

	/**
	 * 关闭对指定 Session 的管理。
	 */
	protected synchronized void closeSession(final Session session) {
		String tag = this.sessionTagMap.get(session.getId());
		if (null != tag) {
			TalkSessionContext ctx = this.tagContexts.get(tag);
			if (null != ctx) {
				if (!this.executor.isShutdown()) {
					// 关闭 Socket
					this.executor.execute(new Runnable() {
						@Override
						public void run() {
							acceptor.close(session);
						}
					});
				}

				// 先取出 tracker
				TalkTracker tracker = ctx.getTracker(session);

				// 从上下文移除 Session
				ctx.removeSession(session);

				if (ctx.numSessions() == 0) {
					Logger.i(this.getClass(), "Clear session: " + tag);

					// 清理上下文记录
					this.tagContexts.remove(tag);
					this.tagList.remove(tag);
				}

				// 进行回调
				if (null != tracker) {
					for (Cellet cellet : tracker.getCelletList()) {
						// quitted
						cellet.quitted(tag);
					}
				}
			}

			// 删除此条会话记录
			this.sessionTagMap.remove(session.getId());
		}
		else {
			Logger.d(this.getClass(), "Can NOT find tag with session: " + session.getAddress().getHostString());
		}

		// 清理未授权表
		this.unidentifiedSessions.remove(session.getId());
	}

	/**
	 * 允许指定 Session 连接。
	 * 
	 * @param session 指定待操作的 Session 。
	 * @param tag 指定 Session 对应内核标签。
	 */
	public synchronized void acceptSession(Session session, String tag) {
		Long sid = session.getId();
		this.unidentifiedSessions.remove(sid);

		// Session -> Tag
		this.sessionTagMap.put(session.getId(), tag);

		// Tag -> Context
		TalkSessionContext ctx = this.tagContexts.get(tag);
		if (null != ctx) {
			// 该 Tag 已存在则增加 session
			ctx.addSession(session);
		}
		else {
			// 创建新的上下文
			ctx = new TalkSessionContext(tag, session);
			ctx.dialogueTickTime = this.getTickTime();
			this.tagContexts.put(tag, ctx);
		}

		// 缓存 Tag
		if (!this.tagList.contains(tag)) {
			this.tagList.add(tag);
		}
	}

	/**
	 * 拒绝指定 Session 连接。
	 * 
	 * @param session 指定待操作的 Session 。
	 */
	public synchronized void rejectSession(Session session) {
		Long sid = session.getId();

		StringBuilder log = new StringBuilder();
		log.append("Talk service reject session (");
		log.append(sid);
		log.append("): ");
		log.append(session.getAddress().getAddress().getHostAddress());
		log.append(":");
		log.append(session.getAddress().getPort());
		Logger.w(TalkService.class, log.toString());
		log = null;

		this.unidentifiedSessions.remove(sid);

		// 删除 Tag context
		String tag = this.sessionTagMap.remove(sid);
		if (null != tag) {
			TalkSessionContext ctx = this.tagContexts.get(tag);
			if (null != ctx) {
				ctx.removeSession(session);
			}
		}

		this.acceptor.close(session);
	}

	/**
	 * 处理请求 Cellet 的操作。
	 * 
	 * @param session
	 * @param tag
	 * @param identifier
	 * @return
	 */
	public TalkTracker processRequest(Session session, String tag, String identifier) {
		TalkSessionContext ctx = this.tagContexts.get(tag);
		if (null == ctx) {
			return null;
		}

		final Cellet cellet = Nucleus.getInstance().getCellet(identifier, this.nucleusContext);

		TalkTracker tracker = null;

		if (null != cellet) {
//			TalkCapacity capacity = null;
			synchronized (ctx) {
				tracker = ctx.getTracker(session);
//				capacity = tracker.getCapacity();

				if (null != tracker && !tracker.hasCellet(cellet)) {
					tracker.addCellet(cellet);
				}

				// 回调 contacted
				cellet.contacted(tag);
			}
		}

		return tracker;
	}

	/**
	 * 处理能力协商
	 * @param session
	 * @param tag
	 * @param capacity
	 * @return
	 */
	public TalkCapacity processConsult(Session session, String tag, TalkCapacity capacity) {
		TalkSessionContext ctx = this.tagContexts.get(tag);
		if (null == ctx) {
			return null;
		}

		// 协商终端能力
		TalkTracker tracker = ctx.getTracker(session);
		if (null != tracker) {
			tracker.setCapacity(capacity);
		}
		else {
			Logger.e(this.getClass(), "Can not find talk tracker for session: " + session.getAddress().getHostString());
		}

		return capacity;
	}

	/**
	 * 处理对话 Cellet 数据。
	 * 
	 * @param session
	 * @param speakerTag
	 * @param targetIdentifier
	 * @param primitive
	 */
	public void processDialogue(Session session, String speakerTag, String targetIdentifier, Primitive primitive) {
		TalkSessionContext ctx = this.tagContexts.get(speakerTag);
		if (null != ctx) {
			ctx.dialogueTickTime = this.getTickTime();

			TalkTracker tracker = ctx.getTracker(session);
			if (null == tracker) {
				// 没有找到对应的追踪器
				return;
			}

			Cellet cellet = tracker.getCellet(targetIdentifier);
			if (null != cellet) {
				primitive.setCelletIdentifier(cellet.getFeature().getIdentifier());
				primitive.setCellet(cellet);

				if (null != this.callbackListener && primitive.isDialectal()) {
					boolean ret = this.callbackListener.doDialogue(cellet, speakerTag, primitive.getDialect());
					if (!ret) {
						// 被劫持，直接返回
						return;
					}
				}

				// 回调 Cellet
				cellet.dialogue(speakerTag, primitive);
			}
		}
	}

	/**
	 * 获得 Session 证书。
	 * 
	 * @param session 指定待查找的 Session 。
	 * @return 返回 Session 证书。
	 */
	public Certificate getCertificate(Session session) {
		return this.unidentifiedSessions.get(session.getId());
	}

	/**
	 * 处理未识别 Session 。
	 * 
	 * @param time 指定时间戳。
	 */
	protected void processUnidentifiedSessions(long time) {
		if (null == this.unidentifiedSessions || this.unidentifiedSessions.isEmpty()) {
			return;
		}

		// 存储超时的 Session
		ArrayList<Session> sessionList = null;

		Iterator<Map.Entry<Long, Certificate>> iter = this.unidentifiedSessions.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<Long, Certificate> e = iter.next();
			Certificate cert = e.getValue();
			if (false == cert.checked) {
				cert.checked = true;
				deliverChecking(cert.session, cert.plaintext, cert.key);
			}
			else {
				// 20 秒超时检测
				if (time - cert.time > 20000) {
					if (null == sessionList) {
						sessionList = new ArrayList<Session>();
					}

					sessionList.add(cert.session);
				}
			}
		}

		if (null != sessionList) {
			// 关闭所有超时的 Session
			Iterator<Session> siter = sessionList.iterator();
			while (siter.hasNext()) {
				Session session = siter.next();

				StringBuilder log = new StringBuilder();
				log.append("Talk service session timeout: ");
				log.append(session.getAddress().getAddress().getHostAddress());
				log.append(":");
				log.append(session.getAddress().getPort());
				Logger.i(TalkService.class, log.toString());
				log = null;

				// 从记录中删除
				this.unidentifiedSessions.remove(session.getId());

				// if (session instanceof HttpSession) {
				// // 删除 HTTP 的 Session
				// this.httpSessionManager.unmanage((HttpSession)session);
				// }
				// else if (session instanceof WebSocketSession) {
				// // 删除 WebSocket 的 Session
				// this.webSocketManager.close((WebSocketSession)session);
				// }
				// else {
				// // 关闭私有协议的 Session
				// this.acceptor.close(session);
				// }

				this.acceptor.close(session);
			}

			sessionList.clear();
			sessionList = null;
		}
	}

	/**
	 * 更新 Session 心跳。
	 * 
	 * @param session 指定需更新的 Session 。
	 */
	public void updateSessionHeartbeat(Session session) {
		String tag = this.sessionTagMap.get(session.getId());
		if (null == tag) {
			return;
		}

		TalkSessionContext ctx = this.tagContexts.get(tag);
		if (null != ctx) {
			ctx.updateSessionHeartbeat(session, this.getTickTime());

			if (Logger.isDebugLevel()) {
				Logger.d(this.getClass(),
						"Talk service heartbeat from " + session.getAddress().getAddress().getHostAddress() + ":" + session.getAddress().getPort());
			}
		}
	}

	/**
	 * 返回最近一次的执行任务的时间点。
	 */
	protected long getTickTime() {
		return (null != this.daemon) ? this.daemon.tickTime() : 0L;
	}

	/**
	 * 检查会话是否超时。
	 */
	protected void checkSessionHeartbeat() {
		if (null == this.tagContexts) {
			return;
		}

		long curtime = System.currentTimeMillis();

		LinkedList<Session> closeList = new LinkedList<Session>();

		for (Map.Entry<String, TalkSessionContext> entry : this.tagContexts.entrySet()) {
			TalkSessionContext ctx = entry.getValue();

			List<Session> sl = ctx.getSessions();
			for (Session s : sl) {
				long time = ctx.getSessionHeartbeat(s);
				if (time == 0) {
					continue;
				}

				if (curtime - time > this.sessionTimeout) {
					// 超时的 Session 添加到关闭列表
					closeList.add(s);
					Logger.d(this.getClass(), "Session timeout in heartbeat: " + s.getAddress().getAddress().getHostAddress());
				}
			}
		}

		for (Session session : closeList) {
			this.closeSession(session);
		}

		closeList.clear();
		closeList = null;
	}

	/**
	 * 向指定 Session 发送识别指令。
	 * 
	 * @param session 网络会话。
	 * @param text 原始明文。
	 * @param key 密钥。
	 */
	private void deliverChecking(Session session, String text, String key) {
		// 如果是来自 HTTP 协议的 Session 则直接返回
		// if (session instanceof HttpSession) {
		// return;
		// }

		// 是否是 WebSocket 的 Session
		// if (session instanceof WebSocketSession) {
		// byte[] ciphertext = Cryptology.getInstance().simpleEncrypt(text.getBytes(), key.getBytes());
		// JSONObject data = new JSONObject();
		// try {
		// JSONObject packet = new JSONObject();
		// // {"ciphertext": ciphertext, "key": key}
		// packet.put(HttpInterrogationHandler.Ciphertext, Cryptology.getInstance().encodeBase64(ciphertext));
		// packet.put(HttpInterrogationHandler.Key, key);
		//
		// data.put(WebSocketMessageHandler.TALK_PACKET_TAG, WebSocketMessageHandler.TPT_INTERROGATE);
		// data.put(WebSocketMessageHandler.TALK_PACKET, packet);
		// } catch (JSONException e) {
		// Logger.log(TalkService.class, e, LogLevel.ERROR);
		// }
		//
		// Message message = new Message(data.toString());
		// this.webSocketManager.write((WebSocketSession)session, message);
		// message = null;
		// return;
		// }

		// 包格式：密文|密钥

		byte[] ciphertext = Cryptology.getInstance().simpleEncrypt(text.getBytes(), key.getBytes());

		Packet packet = new Packet(TalkDefinition.TPT_INTERROGATE, 1, 2, 0);
		packet.appendSegment(ciphertext);
		packet.appendSegment(key.getBytes());

		byte[] data = Packet.pack(packet);
		if (null != data) {
			Message message = new Message(data);
			this.acceptor.write(session, message);
			message = null;
		}

		packet = null;
	}

	/**
	 * 打包对话原语。
	 * 
	 * @param cellet 指定源 Cellet 。
	 * @param primitive 指定原语数据。
	 * @param jsonFormat 指定是否使用 JSON 格式。
	 * @return 返回生成的消息对象。
	 */
	private Message packetDialogue(Cellet cellet, Primitive primitive, Session session) {
		Message message = null;

		if (null == session) {
			try {
				JSONObject primJson = new JSONObject();
				PrimitiveSerializer.write(primJson, primitive);
				JSONObject packet = new JSONObject();
				packet.put("primitive", primJson);
				packet.put("identifier", cellet.getFeature().getIdentifier());

				JSONObject data = new JSONObject();
				data.put("tpt", "dialogue");
				data.put("packet", packet);

				// 创建 message
				message = new Message(Utils.string2Bytes(data.toString()));
			}
			catch (JSONException e) {
				Logger.log(this.getClass(), e, LogLevel.ERROR);
			}
		}
		else {
			// 包格式：原语序列|Cellet

			// 序列化原语
			ByteArrayOutputStream stream = primitive.write();

			// 封装数据包
			Packet packet = new Packet(TalkDefinition.TPT_DIALOGUE, 99, 2, 0);
			packet.appendSegment(stream.toByteArray());
			packet.appendSegment(Utils.string2Bytes(cellet.getFeature().getIdentifier()));

			// 打包数据
			byte[] data = Packet.pack(packet);
			message = new Message(data);
		}

		return message;
	}

	/**
	 * 会话身份证书。
	 */
	public class Certificate {
		/** 相关 Session 。*/
		public Session session;
		/** 密钥 。*/
		public String key;
		/** 明文 。*/
		public String plaintext;
		/** 时间戳 。*/
		public long time;
		/** 是否已经发送校验请求 。*/
		public boolean checked;

		/**
		 * 构造函数。
		 */
		protected Certificate() {
			this.session = null;
			this.key = null;
			this.plaintext = null;
			this.time = System.currentTimeMillis();
			this.checked = false;
		}

	}

}
