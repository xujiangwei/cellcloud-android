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

import net.cellcloud.common.LogLevel;
import net.cellcloud.common.Logger;
import net.cellcloud.common.Message;
import net.cellcloud.common.MessageErrorCode;
import net.cellcloud.common.MessageHandler;
import net.cellcloud.common.Packet;
import net.cellcloud.common.Session;
import net.cellcloud.util.Utils;

/**
 * Speaker 连接器处理器。
 * 
 * @author Ambrose Xu
 * 
 */
public final class SpeakerConnectorHandler implements MessageHandler {

	/** 关联的对话者。 */
	private Speaker speaker;

	/**
	 * 构造函数。
	 * 
	 * @param speaker 指定会话者实例。
	 */
	public SpeakerConnectorHandler(Speaker speaker) {
		this.speaker = speaker;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sessionCreated(Session session) {
		// Nothing
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sessionDestroyed(Session session) {
		// Nothing
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sessionOpened(Session session) {
		// Nothing
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sessionClosed(Session session) {
		this.speaker.notifySessionClosed();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void messageReceived(Session session, Message message) {
		// 解包
		try {
			Packet packet = Packet.unpack(message.get());
			if (null != packet) {
				// 处理数据包
				this.process(session, packet);
			}
			else {
				Logger.d(this.getClass(), "Error packet format");
			}
		} catch (NumberFormatException e) {
			Logger.log(this.getClass(), e, LogLevel.WARNING);
		} catch (ArrayIndexOutOfBoundsException e) {
			Logger.log(this.getClass(), e, LogLevel.WARNING);
		} catch (Exception e) {
			Logger.log(this.getClass(), e, LogLevel.WARNING);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void messageSent(Session session, Message message) {
		Object context = message.getContext();
		if (null != context && context instanceof Primitive) {
			this.speaker.doTalked((Primitive) context);
			message.setContext(null);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void errorOccurred(int errorCode, Session session, Message message) {
		if (Logger.isDebugLevel()) {
			Logger.d(SpeakerConnectorHandler.class, "errorOccurred : " + errorCode);
		}

		if (errorCode == MessageErrorCode.CONNECT_TIMEOUT
			|| errorCode == MessageErrorCode.CONNECT_FAILED) {
			// 一般性连接错误
			TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.CALL_FAILED
					, this.getClass(), this.speaker.getAddress().getHostString(), this.speaker.getAddress().getPort());
			failure.setSourceDescription("Attempt to connect to host timed out");
			failure.setSourceCelletIdentifiers(this.speaker.getIdentifiers());
			this.speaker.fireFailed(failure);
		}
		else if (errorCode == MessageErrorCode.NO_NETWORK) {
			// 无网络错误
			TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.NO_NETWORK, this.getClass(), null, 0);
			failure.setSourceCelletIdentifiers(this.speaker.getIdentifiers());
			this.speaker.fireFailed(failure);
		}
		else if (errorCode == MessageErrorCode.WRITE_OUTOFBOUNDS) {
			// 数据错误
			TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.INCORRECT_DATA
					, this.getClass(), this.speaker.getAddress().getHostString(), this.speaker.getAddress().getPort());
			failure.setSourceCelletIdentifiers(this.speaker.getIdentifiers());
			if (null != message) {
				Object ctx = message.getContext();
				if (null != ctx && ctx instanceof Primitive) {
					failure.setSourcePrimitive((Primitive) ctx);
				}
			}
			this.speaker.fireFailed(failure);
		}
		else {
			// 其他错误
			TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.NETWORK_NOT_AVAILABLE
					, this.getClass(), this.speaker.getAddress().getHostString(), this.speaker.getAddress().getPort());
			failure.setSourceDescription("Network is not available, error : " + errorCode);
			failure.setSourceCelletIdentifiers(this.speaker.getIdentifiers());
			if (null != message) {
				Object ctx = message.getContext();
				if (null != ctx && ctx instanceof Primitive) {
					failure.setSourcePrimitive((Primitive) ctx);
				}
			}
			this.speaker.fireFailed(failure);
		}
	}

	/**
	 * 进行数据处理。
	 * 
	 * @param session 指定数据相关的会话。
	 * @param packet 指定数据包。
	 */
	private void process(Session session, Packet packet) {
		// 处理包

		byte[] tag = packet.getTag();

		if (TalkDefinition.TPT_DIALOGUE[2] == tag[2]
			&& TalkDefinition.TPT_DIALOGUE[3] == tag[3]) {
			this.speaker.doDialogue(packet, session);
		}
		else if (TalkDefinition.TPT_HEARTBEAT[2] == tag[2]
			&& TalkDefinition.TPT_HEARTBEAT[3] == tag[3]) {
			// 更新时间戳
			this.speaker.heartbeatTime = System.currentTimeMillis();

			Logger.d(this.getClass(), "Speaker " + this.speaker.getAddress().toString() + " heartbeat");

			synchronized (this.speaker) {
				this.speaker.notifyAll();
			}
		}
		else if (TalkDefinition.TPT_QUICK[2] == tag[2]
			&& TalkDefinition.TPT_QUICK[3] == tag[3]) {
			this.speaker.doQuick(packet, session);
		}
		else if (TalkDefinition.TPT_REQUEST[2] == tag[2]
			&& TalkDefinition.TPT_REQUEST[3] == tag[3]) {
			// 完成 Cellet 请求
			this.speaker.doRequest(packet, session);
		}
		else if (TalkDefinition.TPT_CONSULT[2] == tag[2]
			&& TalkDefinition.TPT_CONSULT[3] == tag[3]) {
			// 执行协商
			this.speaker.doConsult(packet, session);

			// 请求 Cellet
			this.speaker.requestCellets(session);
		}
		else if (TalkDefinition.TPT_CHECK[2] == tag[2]
			&& TalkDefinition.TPT_CHECK[3] == tag[3]) {

			// 记录标签
			byte[] rtag = packet.getSegment(1);
			this.speaker.recordTag(Utils.bytes2String(rtag));

			// 请求进行协商
			this.speaker.respondConsult();
		}
		else if (TalkDefinition.TPT_INTERROGATE[2] == tag[2]
			&& TalkDefinition.TPT_INTERROGATE[3] == tag[3]) {

			if (packet.getMajorVersion() >= 2
				|| (packet.getMajorVersion() == 1 && packet.getMinorVersion() >= 1)) {
				if (Logger.isDebugLevel()) {
					Logger.d(this.getClass(), "Use 'QUICK' handshake");
				}

				// 使用 QUICK 进行握手
				this.speaker.respondQuick(packet, session);
			}
			else {
				// 请求进行校验会话
				this.speaker.respondCheck(packet, session);
			}

			// 重置重试参数
			if (null != this.speaker.capacity) {
				this.speaker.retryTimestamp = 0;
				this.speaker.retryCount = 0;
				this.speaker.retryEnd = false;
			}
		}
	}

}
