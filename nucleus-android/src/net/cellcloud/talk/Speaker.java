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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ExecutorService;

import net.cellcloud.common.BlockingConnector;
import net.cellcloud.common.Cryptology;
import net.cellcloud.common.Logger;
import net.cellcloud.common.Message;
import net.cellcloud.common.MessageConnector;
import net.cellcloud.common.NonblockingConnector;
import net.cellcloud.common.Packet;
import net.cellcloud.common.Session;
import net.cellcloud.core.Nucleus;
import net.cellcloud.talk.dialect.ChunkDialect;
import net.cellcloud.util.CachedQueueExecutor;
import net.cellcloud.util.Utils;

/**
 * 原语对话者。
 * 
 * @author Ambrose Xu
 *
 */
public class Speaker implements Speakable {

	/** 内核标签。 */
	private byte[] nucleusTag;

	/** 访问地址。 */
	private InetSocketAddress address;
	/** 对话者事件委派。 */
	private SpeakerDelegate delegate;
	/** 用于建立连接的阻塞连接器。 */
	private BlockingConnector blockingConnector;
	/** 用于建立连接的非阻塞连接器。 */
	private NonblockingConnector nonblockingConnector;
	/** 数据缓存区大小。 */
	private int block;

	/** 此对话者请求的 Cellet 标识清单。 */
	private Vector<String> identifierList;

	/** 对话者协商的能力描述。 */
	protected TalkCapacity capacity;

	/** 从服务器获得密钥。 */
	private byte[] secretKey = null;

	/** 服务器端的内核标签。 */
	protected String remoteTag;

	/** 是否已经验证成功，成功与服务器握手。 */
	private boolean authenticated = false;
	/** 状态。 */
	protected volatile int state = SpeakerState.HANGUP;

	/** 是否需要重新连接。 */
	private boolean lost = false;
	/** 上一次重连的时间戳。 */
	protected long retryTimestamp = 0;
	/** 重连次数。 */
	protected int retryCount = 0;
	/** 是否已经达到最大重连次数，重连结束。 */
	protected boolean retryEnd = false;

	/** 协议握手超时控制定时器。 */
	private Timer contactedTimer = null;

	/** 用于读取阻塞连接器数据的线程执行器。 */
	private ExecutorService executor;

	/** 最近一次心跳时间戳。 */
	protected long heartbeatTime = 0L;
	/** 心跳是否活跃。 */
	protected boolean isHeartbeatAvailable = false;


	/**
	 * 构造函数。
	 * 
	 * @param address 指定访问地址。
	 * @param delegate 指定事件委派。
	 * @param block 指定缓存区大小。
	 * @param executor 指定线程执行器。
	 */
	public Speaker(InetSocketAddress address, SpeakerDelegate delegate, int block) {
		this.nucleusTag = Nucleus.getInstance().getTagAsString().getBytes();
		this.address = address;
		this.delegate = delegate;
		this.block = block;
		this.executor = CachedQueueExecutor.newCachedQueueThreadPool(4);
		this.identifierList = new Vector<String>(2);
	}

	/**
	 * 构造函数。
	 * 
	 * @param address 指定访问地址。
	 * @param delegate 指定事件委派。
	 * @param block 指定缓存区大小。
	 * @param capacity 指定协商能力。
	 * @param executor 指定线程执行器。
	 */
	public Speaker(InetSocketAddress address, SpeakerDelegate delegate, int block, TalkCapacity capacity) {
		this.nucleusTag = Nucleus.getInstance().getTagAsString().getBytes();
		this.address = address;
		this.delegate = delegate;
		this.block = block;
		this.capacity = capacity;
		this.executor = CachedQueueExecutor.newCachedQueueThreadPool(4);
		this.identifierList = new Vector<String>(2);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<String> getIdentifiers() {
		return this.identifierList;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getRemoteTag() {
		return this.remoteTag;
	}

	/**
	 * 获得连接地址。
	 * 
	 * @return 返回连接地址。
	 */
	public InetSocketAddress getAddress() {
		return this.address;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean call(List<String> identifiers) {
		if (SpeakerState.CALLING == this.state) {
			// 正在 Call 返回 false
			return false;
		}

		// 将状态置为 Calling
		this.state = SpeakerState.CALLING;

		if (null != identifiers) {
			for (String identifier : identifiers) {
				if (this.identifierList.contains(identifier)) {
					continue;
				}

				this.identifierList.add(identifier.toString());
			}
		}

		if (this.identifierList.isEmpty()) {
			Logger.w(Speaker.class, "Can not find any cellets to call in param 'identifiers'.");
			// 重置状态
			this.state = SpeakerState.HANGUP;
			return false;
		}

		if (null != this.capacity && this.capacity.blocking) {
			if (null == this.blockingConnector) {
				this.blockingConnector = new BlockingConnector(Nucleus.getInstance().getAppContext(), this.executor);
				this.blockingConnector.setBlockSize(this.block);
				this.blockingConnector.setConnectTimeout(this.capacity.connectTimeout);

				byte[] headMark = {0x20, 0x10, 0x11, 0x10};
				byte[] tailMark = {0x19, 0x78, 0x10, 0x04};
				this.blockingConnector.defineDataMark(headMark, tailMark);

				this.blockingConnector.setHandler(new SpeakerConnectorHandler(this));
			}
			else {
				if (this.blockingConnector.isConnected()) {
					this.blockingConnector.disconnect();
				}
			}
		}
		else {
			if (null == this.nonblockingConnector) {
				this.nonblockingConnector = new NonblockingConnector(Nucleus.getInstance().getAppContext());
				this.nonblockingConnector.setBlockSize(this.block);

				if (null != this.capacity) {
					this.nonblockingConnector.setConnectTimeout(this.capacity.connectTimeout);
				}

				byte[] headMark = {0x20, 0x10, 0x11, 0x10};
				byte[] tailMark = {0x19, 0x78, 0x10, 0x04};
				this.nonblockingConnector.defineDataMark(headMark, tailMark);

				this.nonblockingConnector.setHandler(new SpeakerConnectorHandler(this));
			}
			else {
				if (this.nonblockingConnector.isConnected()) {
					this.nonblockingConnector.disconnect();
				}
			}
		}

		// 设置状态
		this.authenticated = false;
		this.lost = false;
		this.heartbeatTime = 0L;

		(new Thread(new Runnable() {
			@Override
			public void run() {
				retryTimestamp = 0;

				// 进行连接
				MessageConnector connector = (null != nonblockingConnector) ? nonblockingConnector : blockingConnector;
				boolean ret = connector.connect(address);
				if (ret) {
					// 开始进行调用
					state = SpeakerState.CALLING;
				}
				else {
					lost = true;
					// 设置为挂断状态
					state = SpeakerState.HANGUP;
					TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.NETWORK_NOT_AVAILABLE, Speaker.class,
							address.getHostString(), address.getPort());
					failure.setSourceDescription("Connector failed");
					failure.setSourceCelletIdentifiers(identifierList);
					delegate.onFailed(Speaker.this, failure);
				}
			}
		})).start();

		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void hangUp() {
		this.state = SpeakerState.HANGUP;

		if (null != this.contactedTimer) {
			this.contactedTimer.cancel();
			this.contactedTimer.purge();
			this.contactedTimer = null;
		}

		if (null != this.nonblockingConnector) {
			Session session = this.nonblockingConnector.getSession();
			if (null != session) {
				session.deactiveSecretKey();
			}

			this.nonblockingConnector.resetInterval(500);
			this.nonblockingConnector.disconnect();
			this.nonblockingConnector = null;
		}
		else if (null != this.blockingConnector) {
			Session session = this.blockingConnector.getSession();
			if (null != session) {
				session.deactiveSecretKey();
			}

			this.blockingConnector.disconnect();
			this.blockingConnector = null;
		}

		this.heartbeatTime = 0L;
		this.lost = false;
		this.authenticated = false;
		this.identifierList.clear();
	}

	/**
	 * 连接是否已经断开。
	 * 
	 * @return 如果已经断开返回 <code>true</code> 。
	 */
	protected boolean isLost() {
		return this.lost;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean speak(String identifier, Primitive primitive) {
		MessageConnector connector = (null != this.blockingConnector) ? this.blockingConnector : this.nonblockingConnector;
		if (null == connector
			|| !connector.isConnected()
			|| this.state != SpeakerState.CALLED) {
			return false;
		}

		// 序列化原语
		ByteArrayOutputStream stream = primitive.write();

		// 封装数据包
		Packet packet = new Packet(TalkDefinition.TPT_DIALOGUE, 99, 2, 0);
		packet.appendSegment(stream.toByteArray());
		packet.appendSegment(this.nucleusTag);
		packet.appendSegment(Utils.string2Bytes(identifier));

		// 发送数据
		byte[] data = Packet.pack(packet);
		Message message = new Message(data);
		if (null != this.blockingConnector) {
			if (primitive.isDialectal() && primitive.getDialect().getName().equals(ChunkDialect.DIALECT_NAME)) {
				this.blockingConnector.write(message, BlockingConnector.BlockingConnectorQueuePriority.Low);
			}
			else {
				this.blockingConnector.write(message, BlockingConnector.BlockingConnectorQueuePriority.High);
			}
		}
		else {
			this.nonblockingConnector.write(message);
		}

		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isCalled() {
		if (null != this.nonblockingConnector) {
			return (this.state == SpeakerState.CALLED) && this.nonblockingConnector.isConnected();
		}
		else {
			return (this.state == SpeakerState.CALLED) && this.blockingConnector.isConnected();
		}
	}

	/**
	 * 进入休眠模式。
	 */
	protected void sleep() {
		if (null != this.nonblockingConnector) {
			this.nonblockingConnector.resetInterval(5000);
		}
		else if (null != this.blockingConnector) {
			this.blockingConnector.resetInterval(5000);
		}
	}

	/**
	 * 从休眠模式唤醒。
	 */
	protected void wakeup() {
		if (null != this.nonblockingConnector) {
			this.nonblockingConnector.resetInterval(1000);
		}
		else if (null != this.blockingConnector) {
			this.blockingConnector.resetInterval(1000);
		}
	}

	/**
	 * 重置线程执行间隔。
	 * 
	 * @param interval 指定以毫秒为单位的时间间隔。
	 */
	protected void resetInterval(long interval) {
		if (null != this.blockingConnector) {
			this.blockingConnector.resetInterval(interval);
		}
		else if (null != this.nonblockingConnector) {
			this.nonblockingConnector.resetInterval(interval);
		}
	}

	/**
	 * 重置状态数据。
	 */
	protected void reset() {
		this.retryTimestamp = 0;
		this.retryCount = 0;
		this.retryEnd = false;
	}

	/**
	 * 记录服务端标签。
	 * 
	 * @param tag 指定服务端标签。
	 */
	protected void recordTag(String tag) {
		this.remoteTag = tag;
		// 标记为已验证
		this.authenticated = true;
	}

	/**
	 * 发送心跳。
	 * 
	 * @return 数据成功写入发送队列返回 <code>true</code> 。
	 */
	protected boolean heartbeat() {
		MessageConnector connector = (null != this.nonblockingConnector) ? this.nonblockingConnector : this.blockingConnector;
		if (this.authenticated && !this.lost && connector.isConnected()) {
			Packet packet = new Packet(TalkDefinition.TPT_HEARTBEAT, 9, 2, 0);
			byte[] data = Packet.pack(packet);
			Message message = new Message(data);

			if (null != this.nonblockingConnector) {
				this.nonblockingConnector.write(message);
			}
			else {
				this.blockingConnector.write(message);
			}
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * 通知会话被关闭，更新内部状态。
	 */
	protected void notifySessionClosed() {
		// 判断是否为异常网络中断
		if (SpeakerState.CALLING == this.state) {
			TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.CALL_FAILED, this.getClass(),
					this.address.getHostString(), this.address.getPort());
			failure.setSourceDescription("No network device");
			failure.setSourceCelletIdentifiers(this.identifierList);
			this.fireFailed(failure);

			// 标记为丢失
			this.lost = true;
		}
		else if (SpeakerState.CALLED == this.state) {
			TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.TALK_LOST, this.getClass(),
					this.address.getHostString(), this.address.getPort());
			failure.setSourceDescription("Network fault, connection closed");
			failure.setSourceCelletIdentifiers(this.identifierList);
			this.fireFailed(failure);

			// 标记为丢失
			this.lost = true;
		}
		else {
			TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.NETWORK_NOT_AVAILABLE, this.getClass(),
					this.address.getHostString(), this.address.getPort());
			failure.setSourceDescription("Session has closed");
			failure.setSourceCelletIdentifiers(this.identifierList);
			this.fireFailed(failure);

			// 标记为丢失
			this.lost = true;
		}

		this.authenticated = false;
		this.state = SpeakerState.HANGUP;

		// 通知退出
		ArrayList<String> identifiers = new ArrayList<String>(this.identifierList.size());
		identifiers.addAll(this.identifierList);
		for (String identifier : identifiers) {
			this.fireQuitted(identifier);
		}
		identifiers.clear();
		identifiers = null;
	}

	/**
	 * 触发 Dialogue 回调。
	 * 
	 * @param celletIdentifier 指定 Cellet 标识。
	 * @param primitive 指定接收到的原语数据。
	 */
	protected void fireDialogue(String celletIdentifier, Primitive primitive) {
		this.delegate.onDialogue(this, celletIdentifier, primitive);
	}

	/**
	 * 触发 Contacted 回调。
	 * 
	 * @param celletIdentifier 指定 Cellet 标识。
	 */
	private void fireContacted(String celletIdentifier) {
		if (null == this.contactedTimer) {
			this.contactedTimer = new Timer();
		}
		else {
			this.contactedTimer.cancel();
			this.contactedTimer.purge();
			this.contactedTimer = null;
			this.contactedTimer = new Timer();
		}

		this.contactedTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				MessageConnector connector = (null != nonblockingConnector) ? nonblockingConnector : blockingConnector;
				// 请求成功，激活链路加密
				if (capacity.secure) {
					if (!connector.getSession().isSecure()) {
						boolean ret = connector.getSession().activeSecretKey(secretKey);
						if (ret) {
							Logger.i(Speaker.class, "Active secret key for server: " + address.getAddress().getHostAddress() + ":" + address.getPort());
						}
					}
				}
				else {
					connector.getSession().deactiveSecretKey();
				}

				// 更新心跳
				heartbeatTime = System.currentTimeMillis();

				for (String cid : identifierList) {
					delegate.onContacted(Speaker.this, cid);
				}

				(new Thread(new Runnable() {
					@Override
					public void run() {
						if (null != contactedTimer) {
							contactedTimer.cancel();
							contactedTimer.purge();
							contactedTimer = null;
						}
					}
				})).start();
			}
		}, 50);
	}

	/**
	 * 触发 Quitted 回调。
	 * 
	 * @param celletIdentifier 指定 Cellet 标识。
	 */
	private void fireQuitted(String celletIdentifier) {
		if (null != this.contactedTimer) {
			this.contactedTimer.cancel();
			this.contactedTimer.purge();
			this.contactedTimer = null;
		}

		this.delegate.onQuitted(this, celletIdentifier);

		// 吊销密钥
		MessageConnector connector = (null != this.nonblockingConnector) ? this.nonblockingConnector : this.blockingConnector;
		if (null != connector) {
			Session session = connector.getSession();
			if (null != session) {
				session.deactiveSecretKey();
			}
		}
	}

	/**
	 * 触发 Failed 回调。
	 * 
	 * @param failure 指定会话服务错误描述。
	 */
	protected void fireFailed(TalkServiceFailure failure) {
		if (failure.getCode() == TalkFailureCode.NOT_FOUND
			|| failure.getCode() == TalkFailureCode.INCORRECT_DATA
			|| failure.getCode() == TalkFailureCode.RETRY_END) {
			this.delegate.onFailed(this, failure);
		}
		else {
			this.state = SpeakerState.HANGUP;
			this.delegate.onFailed(this, failure);
			this.lost = true;
			this.heartbeatTime = 0L;
		}
	}

	/**
	 * 触发重试连接结束。
	 */
	protected void fireRetryEnd() {
		TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.RETRY_END, this.getClass(),
				this.address.getHostString(), this.address.getPort());
		failure.setSourceCelletIdentifiers(this.identifierList);
		this.fireFailed(failure);
	}

	/**
	 * 触发重试连接错误。
	 */
	protected void fireRetryError() {
		TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.NETWORK_NOT_AVAILABLE, this.getClass(),
				this.address.getHostString(), this.address.getPort());
		failure.setSourceCelletIdentifiers(this.identifierList);
		this.fireFailed(failure);
	}

	/**
	 * 应答 Check 校验数据进行握手。
	 * 
	 * @param packet 指定接收到的 INTERROGATE 包。
	 * @param session 指定会话。
	 */
	protected void respondCheck(Packet packet, Session session) {
		// 包格式：密文|密钥

		byte[] ciphertext = packet.getSegment(0);
		byte[] key = packet.getSegment(1);

		// 写密钥
		this.secretKey = new byte[key.length];
		System.arraycopy(key, 0, this.secretKey, 0, key.length);

		// 解密
		byte[] plaintext = Cryptology.getInstance().simpleDecrypt(ciphertext, key);

		// 发送响应数据
		Packet response = new Packet(TalkDefinition.TPT_CHECK, 2, 2, 0);
		response.appendSegment(plaintext);
		response.appendSegment(this.nucleusTag);
		// 数据打包
		byte[] data = Packet.pack(response);
		Message message = new Message(data);
		session.write(message);
	}

	/**
	 * 应答能力协商。
	 */
	protected void respondConsult() {
		// 协商能力
		if (null == this.capacity) {
			this.capacity = new TalkCapacity();
		}

		// 包格式：源标签|能力描述序列化数据
		Packet packet = new Packet(TalkDefinition.TPT_CONSULT, 4, 2, 0);
		packet.appendSegment(this.nucleusTag);
		packet.appendSegment(TalkCapacity.serialize(this.capacity));

		byte[] data = Packet.pack(packet);
		if (null != data) {
			Message message = new Message(data);

			if (null != this.nonblockingConnector) {
				this.nonblockingConnector.write(message);
			}
			else {
				this.blockingConnector.write(message);
			}
		}
	}

	/**
	 * 请求 Cellet 服务。
	 * 
	 * @param session 指定会话。
	 */
	protected void requestCellets(Session session) {
		// 包格式：Cellet标识串|标签

		for (String celletIdentifier : this.identifierList) {
			Packet packet = new Packet(TalkDefinition.TPT_REQUEST, 3, 2, 0);
			packet.appendSegment(celletIdentifier.getBytes());
			packet.appendSegment(this.nucleusTag);

			byte[] data = Packet.pack(packet);
			Message message = new Message(data);
			session.write(message);

			try {
				Thread.sleep(5L);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 执行协商操作。
	 * 
	 * @param packet 指定服务器发送过来的协商数据包。
	 * @param session 指定会话。
	 */
	protected void doConsult(Packet packet, Session session) {
		// 包格式：源标签(即自己的内核标签)|能力描述序列化串

		TalkCapacity newCapacity = TalkCapacity.deserialize(packet.getSegment(1));
		if (null == newCapacity) {
			return;
		}

		// 更新能力
		if (null == this.capacity) {
			this.capacity = newCapacity;
		}
		else {
			this.capacity.secure = newCapacity.secure;
			this.capacity.retry = newCapacity.retry;
			this.capacity.retryDelay = newCapacity.retryDelay;
		}

		if (Logger.isDebugLevel() && null != this.capacity) {
			StringBuilder buf = new StringBuilder();
			buf.append("Update talk capacity from '");
			buf.append(this.remoteTag);
			buf.append("' : secure=");
			buf.append(this.capacity.secure);
			buf.append(" attempts=");
			buf.append(this.capacity.retry);
			buf.append(" delay=");
			buf.append(this.capacity.retryDelay);

			Logger.d(Speaker.class, buf.toString());

			buf = null;
		}
	}

	/**
	 * 执行来自服务器的请求 Cellet 应答。
	 * 
	 * @param packet 指定来自服务器的请求应答包。
	 * @param session 指定会话。
	 */
	protected void doRequest(Packet packet, Session session) {
		// 包格式：
		// 成功：请求方标签|成功码|Cellet识别串|Cellet版本
		// 失败：请求方标签|失败码

		byte[] code = packet.getSegment(1);
		if (code[0] == TalkDefinition.SC_SUCCESS[0]
			&& code[1] == TalkDefinition.SC_SUCCESS[1]
			&& code[2] == TalkDefinition.SC_SUCCESS[2]
			&& code[3] == TalkDefinition.SC_SUCCESS[3]) {
			// 变更状态
			this.state = SpeakerState.CALLED;

			String celletIdentifier = Utils.bytes2String(packet.getSegment(2));

			StringBuilder buf = new StringBuilder();
			buf.append("Cellet '");
			buf.append(celletIdentifier);
			buf.append("' has called at ");
			buf.append(this.getAddress().getAddress().getHostAddress());
			buf.append(":");
			buf.append(this.getAddress().getPort());
			Logger.i(Speaker.class, buf.toString());
			buf = null;

			// 回调事件
			this.fireContacted(celletIdentifier);
		}
		else {
			// 变更状态
			this.state = SpeakerState.HANGUP;

			// 回调事件
			TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.NOT_FOUND, Speaker.class,
					this.address.getHostString(), this.address.getPort());
			failure.setSourceCelletIdentifiers(this.identifierList);
			this.fireFailed(failure);

			MessageConnector connector = (null != this.nonblockingConnector) ? this.nonblockingConnector : this.blockingConnector;
			connector.disconnect();
		}
	}

	/**
	 * 执行来自服务器的对话数据包。
	 * 
	 * @param packet 指定来自服务器的数据包。
	 * @param session 指定会话。
	 */
	protected void doDialogue(Packet packet, Session session) {
		// 包格式：序列化的原语|Cellet

		byte[] pridata = packet.getSegment(0);
		ByteArrayInputStream stream = new ByteArrayInputStream(pridata);
		String celletIdentifier = Utils.bytes2String(packet.getSegment(1));

		// 反序列化原语
		Primitive primitive = new Primitive(this.remoteTag);
		primitive.setCelletIdentifier(celletIdentifier);
		primitive.read(stream);

		this.fireDialogue(celletIdentifier, primitive);
	}

	/**
	 * 以快速握手方式应答服务器的握手询问。
	 * 
	 * @param packet 指定来自服务的询问包。
	 * @param session 指定会话。
	 */
	protected void respondQuick(Packet packet, Session session) {
		byte[] ciphertext = packet.getSegment(0);
		byte[] key = packet.getSegment(1);

		// 写密钥
		this.secretKey = new byte[key.length];
		System.arraycopy(key, 0, this.secretKey, 0, key.length);

		// 解密
		byte[] plaintext = Cryptology.getInstance().simpleDecrypt(ciphertext, key);

		// 协商能力
		if (null == this.capacity) {
			this.capacity = new TalkCapacity();
		}

		// 包格式：明文|源标签|能力描述序列化数据|CelletIdentifiers
		// 发送响应数据
		Packet response = new Packet(TalkDefinition.TPT_QUICK, 2, 2, 0);
		response.appendSegment(plaintext);
		response.appendSegment(this.nucleusTag);
		response.appendSegment(TalkCapacity.serialize(this.capacity));
		for (String celletIdentifier : this.identifierList) {
			response.appendSegment(celletIdentifier.getBytes());
		}

		byte[] data = Packet.pack(response);
		Message message = new Message(data);
		session.write(message);
		message = null;

		response = null;
	}

	/**
	 * 执行来自服务器的快速握手应答。
	 * 
	 * @param packet 指定来自服务器的快速握手回包。
	 * @param session 指定会话。
	 */
	protected void doQuick(Packet packet, Session session) {
		// 包格式：状态码|源标签|能力描述序列化数据|CelletIdentifiers

		byte[] code = packet.getSegment(0);
		if (code[0] == TalkDefinition.SC_SUCCESS[0]
			&& code[1] == TalkDefinition.SC_SUCCESS[1]
			&& code[2] == TalkDefinition.SC_SUCCESS[2]
			&& code[3] == TalkDefinition.SC_SUCCESS[3]) {
			// 记录标签
			byte[] rtag = packet.getSegment(1);
			this.recordTag(Utils.bytes2String(rtag));

			// 更新能力
			TalkCapacity newCapacity = TalkCapacity.deserialize(packet.getSegment(2));
			if (null != newCapacity) {
				if (null == this.capacity) {
					this.capacity = newCapacity;
				}
				else {
					this.capacity.secure = newCapacity.secure;
					this.capacity.retry = newCapacity.retry;
					this.capacity.retryDelay = newCapacity.retryDelay;
				}
			}

			// 变更状态
			this.state = SpeakerState.CALLED;

			for (int i = 3, size = packet.numSegments(); i < size; ++i) {
				String celletIdentifier = Utils.bytes2String(packet.getSegment(i));

				StringBuilder buf = new StringBuilder();
				buf.append("Cellet '");
				buf.append(celletIdentifier);
				buf.append("' has called at ");
				buf.append(this.getAddress().getAddress().getHostAddress());
				buf.append(":");
				buf.append(this.getAddress().getPort());
				Logger.i(Speaker.class, buf.toString());
				buf = null;

				// 回调事件
				this.fireContacted(celletIdentifier);
			}
		}
		else {
			// 变更状态
			this.state = SpeakerState.HANGUP;

			// 回调事件
			TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.NOT_FOUND, Speaker.class,
					this.address.getHostString(), this.address.getPort());
			failure.setSourceCelletIdentifiers(this.identifierList);
			this.fireFailed(failure);

			MessageConnector connector = (null != this.nonblockingConnector) ? this.nonblockingConnector : this.blockingConnector;
			connector.disconnect();
		}
	}

}
