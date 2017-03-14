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
import net.cellcloud.util.Utils;

/**
 * 对话者。
 * 
 * @author Jiangwei Xu
 *
 */
public class Speaker implements Speakable {
	private byte[] nucleusTag;

	private InetSocketAddress address;
	private SpeakerDelegate delegate;
	private BlockingConnector blockingConnector;
	private NonblockingConnector nonblockingConnector;
	private int block;

	private List<String> identifierList;

	protected TalkCapacity capacity;

	private byte[] secretKey = null;

	protected String remoteTag;

	private boolean authenticated = false;
	private volatile int state = SpeakerState.HANGUP;

	// 是否需要重新连接
	private boolean lost = false;
	protected long retryTimestamp = 0;
	protected int retryCounts = 0;
	protected boolean retryEnd = false;

	private Timer contactedTimer = null;

	private ExecutorService executor;

	protected long heartbeatTime = 0L;
	protected boolean isHeartbeatAvailable = false;
	

	/** 构造函数。
	 */
	public Speaker(InetSocketAddress address, SpeakerDelegate delegate, int block, ExecutorService executor) {
		this.nucleusTag = Nucleus.getInstance().getTagAsString().getBytes();
		this.address = address;
		this.delegate = delegate;
		this.block = block;
		this.executor = executor;
		this.identifierList = new ArrayList<String>(2);
		this.heartbeatTime = System.currentTimeMillis();
	}

	/** 构造函数。
	 */
	public Speaker(InetSocketAddress address, SpeakerDelegate delegate, int block, TalkCapacity capacity, ExecutorService executor) {
		this.nucleusTag = Nucleus.getInstance().getTagAsString().getBytes();
		this.address = address;
		this.delegate = delegate;
		this.block = block;
		this.capacity = capacity;
		this.executor = executor;
		this.identifierList = new ArrayList<String>(2);
		this.heartbeatTime = System.currentTimeMillis();
	}

	/** 返回 Cellet Identifier 列表。
	 */
	@Override
	public List<String> getIdentifiers() {
		return this.identifierList;
	}

	@Override
	public String getRemoteTag() {
		return this.remoteTag;
	}

	/** 返回连接地址。
	 */
	public InetSocketAddress getAddress() {
		return this.address;
	}

	/** 向指定地址发起请求 Cellet 服务。
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
					TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.NETWORK_NOT_AVAILABLE, Speaker.class);
					failure.setSourceDescription("Connector failed");
					delegate.onFailed(Speaker.this, failure);
				}
			}
		})).start();

		return true;
	}

	/** 挂断与 Cellet 的服务。
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
			this.nonblockingConnector.resetInterval(500);
			this.nonblockingConnector.disconnect();
			this.nonblockingConnector = null;
		}
		else if (null != this.blockingConnector) {
			this.blockingConnector.disconnect();
			this.blockingConnector = null;
		}

		this.lost = false;
		this.authenticated = false;
		this.identifierList.clear();
	}

	protected boolean isLost() {
		return this.lost;
	}

	/** 向 Cellet 发送原语数据。
	 */
	@Override
	public synchronized boolean speak(String identifier, Primitive primitive) {
		MessageConnector connector = (null != this.nonblockingConnector) ? this.nonblockingConnector : this.blockingConnector;
		if (null == connector
			|| !connector.isConnected()
			|| this.state != SpeakerState.CALLED) {
			return false;
		}

		// 序列化原语
		ByteArrayOutputStream stream = primitive.write();

		// 封装数据包
		Packet packet = new Packet(TalkDefinition.TPT_DIALOGUE, 99, 1, 0);
		packet.appendSubsegment(stream.toByteArray());
		packet.appendSubsegment(this.nucleusTag);
		packet.appendSubsegment(Utils.string2Bytes(identifier));

		// 发送数据
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

	/** 是否已经与 Cellet 建立服务。
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

	protected void sleep() {
		if (null != this.nonblockingConnector) {
			this.nonblockingConnector.resetInterval(6000);
		}
		else if (null != this.blockingConnector) {
			this.blockingConnector.resetInterval(6000);
		}
	}

	protected void wakeup() {
		if (null != this.nonblockingConnector) {
			this.nonblockingConnector.resetInterval(1000);
		}
		else if (null != this.blockingConnector) {
			this.blockingConnector.resetInterval(1000);
		}
	}

	protected boolean resetInterval(long interval) {
		if (null != this.nonblockingConnector) {
			this.nonblockingConnector.resetInterval(interval);
			return true;
		}
		else if (null != this.blockingConnector) {
			this.blockingConnector.resetInterval(interval);
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * 重置状态数据。
	 */
	protected void reset() {
		this.retryTimestamp = 0;
		this.retryCounts = 0;
		this.retryEnd = false;
	}

	/** 记录服务端 Tag */
	protected void recordTag(String tag) {
		this.remoteTag = tag;
		// 标记为已验证
		this.authenticated = true;
	}

	/** 发送心跳。 */
	protected boolean heartbeat() {
		MessageConnector connector = (null != this.nonblockingConnector) ? this.nonblockingConnector : this.blockingConnector;
		if (this.authenticated && !this.lost && connector.isConnected()) {
			Packet packet = new Packet(TalkDefinition.TPT_HEARTBEAT, 9, 1, 0);
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

	protected void notifySessionClosed() {
		// 判断是否为异常网络中断
		if (SpeakerState.CALLING == this.state) {
			TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.CALL_FAILED, this.getClass());
			failure.setSourceDescription("No network device");
			failure.setSourceCelletIdentifiers(this.identifierList);
			this.fireFailed(failure);

			// 标记为丢失
			this.lost = true;
		}
		else if (SpeakerState.CALLED == this.state) {
			TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.TALK_LOST, this.getClass());
			failure.setSourceDescription("Network fault, connection closed");
			failure.setSourceCelletIdentifiers(this.identifierList);
			this.fireFailed(failure);

			// 标记为丢失
			this.lost = true;
		}
		else {
			TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.NETWORK_NOT_AVAILABLE, this.getClass());
			failure.setSourceDescription("Session has closed");
			failure.setSourceCelletIdentifiers(this.identifierList);
			this.fireFailed(failure);

			// 标记为丢失
			this.lost = true;
		}

		this.authenticated = false;
		this.state = SpeakerState.HANGUP;

		// 通知退出
		for (String identifier : this.identifierList) {
			this.fireQuitted(identifier);
		}
	}

	protected void fireDialogue(String celletIdentifier, Primitive primitive) {
		this.delegate.onDialogue(this, celletIdentifier, primitive);
	}

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
		}, 300);
	}

	private void fireQuitted(String celletIdentifier) {
		if (null != this.contactedTimer) {
			this.contactedTimer.cancel();
			this.contactedTimer.purge();
			this.contactedTimer = null;
		}

		this.delegate.onQuitted(this, celletIdentifier);

		// 吊销密钥
		MessageConnector connector = (null != this.nonblockingConnector) ? this.nonblockingConnector : this.blockingConnector;
		Session session = connector.getSession();
		if (null != session) {
			session.deactiveSecretKey();
		}
	}

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
		}
	}

	protected void fireRetryEnd() {
		TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.RETRY_END, this.getClass());
		failure.setSourceCelletIdentifiers(this.identifierList);
		this.fireFailed(failure);
	}

	protected void fireRetryError() {
		TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.NETWORK_NOT_AVAILABLE, this.getClass());
		failure.setSourceCelletIdentifiers(this.identifierList);
		this.fireFailed(failure);
	}

	protected void requestCheck(Packet packet, Session session) {
		// 包格式：密文|密钥

		byte[] ciphertext = packet.getSubsegment(0);
		byte[] key = packet.getSubsegment(1);

		// 写密钥
		this.secretKey = new byte[key.length];
		System.arraycopy(key, 0, this.secretKey, 0, key.length);

		// 解密
		byte[] plaintext = Cryptology.getInstance().simpleDecrypt(ciphertext, key);

		// 发送响应数据
		Packet response = new Packet(TalkDefinition.TPT_CHECK, 2, 1, 0);
		response.appendSubsegment(plaintext);
		response.appendSubsegment(this.nucleusTag);
		// 数据打包
		byte[] data = Packet.pack(response);
		Message message = new Message(data);
		session.write(message);
	}

	protected void requestConsult() {
		// 协商能力
		if (null == this.capacity) {
			this.capacity = new TalkCapacity();
		}

		// 包格式：源标签|能力描述序列化数据
		Packet packet = new Packet(TalkDefinition.TPT_CONSULT, 4, 1, 0);
		packet.appendSubsegment(this.nucleusTag);
		packet.appendSubsegment(TalkCapacity.serialize(this.capacity));

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

	protected void requestCellets(Session session) {
		// 包格式：Cellet标识串|标签

		for (String celletIdentifier : this.identifierList) {
			Packet packet = new Packet(TalkDefinition.TPT_REQUEST, 3, 1, 0);
			packet.appendSubsegment(celletIdentifier.getBytes());
			packet.appendSubsegment(this.nucleusTag);

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

	protected void doConsult(Packet packet, Session session) {
		// 包格式：源标签(即自己的内核标签)|能力描述序列化串

		TalkCapacity newCapacity = TalkCapacity.deserialize(packet.getSubsegment(1));
		if (null == newCapacity) {
			return;
		}

		// 更新能力
		if (null == this.capacity) {
			this.capacity = newCapacity;
		}
		else {
			this.capacity.secure = newCapacity.secure;
			this.capacity.retryAttempts = newCapacity.retryAttempts;
			this.capacity.retryDelay = newCapacity.retryDelay;
		}

		if (Logger.isDebugLevel() && null != this.capacity) {
			StringBuilder buf = new StringBuilder();
			buf.append("Update talk capacity from '");
			buf.append(this.remoteTag);
			buf.append("' : secure=");
			buf.append(this.capacity.secure);
			buf.append(" attempts=");
			buf.append(this.capacity.retryAttempts);
			buf.append(" delay=");
			buf.append(this.capacity.retryDelay);

			Logger.d(Speaker.class, buf.toString());

			buf = null;
		}
	}

	protected void doRequest(Packet packet, Session session) {
		// 包格式：
		// 成功：请求方标签|成功码|Cellet识别串|Cellet版本
		// 失败：请求方标签|失败码

		byte[] code = packet.getSubsegment(1);
		if (code[0] == TalkDefinition.SC_SUCCESS[0]
			&& code[1] == TalkDefinition.SC_SUCCESS[1]
			&& code[2] == TalkDefinition.SC_SUCCESS[2]
			&& code[3] == TalkDefinition.SC_SUCCESS[3]) {
			// 变更状态
			this.state = SpeakerState.CALLED;

			String celletIdentifier = Utils.bytes2String(packet.getSubsegment(2));

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
			TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.NOT_FOUND, Speaker.class);
			failure.setSourceCelletIdentifiers(this.identifierList);
			this.fireFailed(failure);

			MessageConnector connector = (null != this.nonblockingConnector) ? this.nonblockingConnector : this.blockingConnector;
			connector.disconnect();
		}
	}

	protected void doDialogue(Packet packet, Session session) {
		// 包格式：序列化的原语|Cellet

		byte[] pridata = packet.getSubsegment(0);
		ByteArrayInputStream stream = new ByteArrayInputStream(pridata);
		String celletIdentifier = Utils.bytes2String(packet.getSubsegment(1));

		// 反序列化原语
		Primitive primitive = new Primitive(this.remoteTag);
		primitive.setCelletIdentifier(celletIdentifier);
		primitive.read(stream);

		this.fireDialogue(celletIdentifier, primitive);
	}

	protected void requestQuick(Packet packet, Session session) {
		byte[] ciphertext = packet.getSubsegment(0);
		byte[] key = packet.getSubsegment(1);

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
		Packet response = new Packet(TalkDefinition.TPT_QUICK, 2, 1, 0);
		response.appendSubsegment(plaintext);
		response.appendSubsegment(this.nucleusTag);
		response.appendSubsegment(TalkCapacity.serialize(this.capacity));
		for (String celletIdentifier : this.identifierList) {
			response.appendSubsegment(celletIdentifier.getBytes());
		}

		byte[] data = Packet.pack(response);
		Message message = new Message(data);
		session.write(message);
		message = null;

		response = null;
	}

	protected void doQuick(Packet packet, Session session) {
		// 包格式：状态码|源标签|能力描述序列化数据|CelletIdentifiers

		byte[] code = packet.getSubsegment(0);
		if (code[0] == TalkDefinition.SC_SUCCESS[0]
			&& code[1] == TalkDefinition.SC_SUCCESS[1]
			&& code[2] == TalkDefinition.SC_SUCCESS[2]
			&& code[3] == TalkDefinition.SC_SUCCESS[3]) {
			// 记录标签
			byte[] rtag = packet.getSubsegment(1);
			this.recordTag(Utils.bytes2String(rtag));

			// 更新能力
			TalkCapacity newCapacity = TalkCapacity.deserialize(packet.getSubsegment(2));
			if (null != newCapacity) {
				if (null == this.capacity) {
					this.capacity = newCapacity;
				}
				else {
					this.capacity.secure = newCapacity.secure;
					this.capacity.retryAttempts = newCapacity.retryAttempts;
					this.capacity.retryDelay = newCapacity.retryDelay;
				}
			}

			// 变更状态
			this.state = SpeakerState.CALLED;

			for (int i = 3, size = packet.getSubsegmentCount(); i < size; ++i) {
				String celletIdentifier = Utils.bytes2String(packet.getSubsegment(i));

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
			TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.NOT_FOUND, Speaker.class);
			failure.setSourceCelletIdentifiers(this.identifierList);
			this.fireFailed(failure);

			MessageConnector connector = (null != this.nonblockingConnector) ? this.nonblockingConnector : this.blockingConnector;
			connector.disconnect();
		}
	}

	/*private void smartSleepInterval() {
		if (null != this.timer) {
			return;
		}

		this.timer = new Timer();
		this.timer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (null != connector) {
					connector.resetSleepInterval(1000);
				}

				if (Logger.isDebugLevel()) {
					Logger.d(Speaker.class, "Reset sleep interval: 1000");
				}

				timer = null;
			}
		}, 60000);

		this.connector.resetSleepInterval(500);
	}*/
}
