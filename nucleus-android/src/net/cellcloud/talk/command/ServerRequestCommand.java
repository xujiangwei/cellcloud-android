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

package net.cellcloud.talk.command;

import net.cellcloud.common.Message;
import net.cellcloud.common.Packet;
import net.cellcloud.common.Session;
import net.cellcloud.core.Cellet;
import net.cellcloud.talk.TalkDefinition;
import net.cellcloud.talk.TalkService;
import net.cellcloud.talk.TalkTracker;
import net.cellcloud.util.Utils;

/**
 * 对话 request cellet 命令。
 * 
 * @author Ambrose Xu
 * 
 */
public final class ServerRequestCommand extends ServerCommand {

	/**
	 * 构造函数。
	 * 
	 * @param service
	 * @param session
	 * @param packet
	 */
	public ServerRequestCommand(TalkService service, Session session, Packet packet) {
		super(service, session, packet);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute() {
		// 包格式：Cellet标识串|请求方标签

		byte[] identifier = this.packet.getSegment(0);
		byte[] talkTag = this.packet.getSegment(1);

		// 包格式：
		// 成功：请求方标签|成功码|Cellet识别串|Cellet版本
		// 失败：请求方标签|失败码

		Packet packet = new Packet(TalkDefinition.TPT_REQUEST, 3, 2, 0);
		// 请求方标签
		packet.appendSegment(talkTag);

		// 请求 Cellet
		TalkTracker tracker = this.service.processRequest(this.session,
				Utils.bytes2String(talkTag), Utils.bytes2String(identifier));

		if (null != tracker) {
			Cellet cellet = tracker.getCellet(Utils.bytes2String(identifier));

			// 成功码
			packet.appendSegment(TalkDefinition.SC_SUCCESS);
			// Cellet识别串
			packet.appendSegment(identifier);
			// Cellet版本
			packet.appendSegment(Utils.string2Bytes(cellet.getFeature().getVersion().toString()));
		}
		else {
			// 失败码
			packet.appendSegment(TalkDefinition.SC_FAILURE_NOCELLET);
		}

		// 打包数据
		byte[] data = Packet.pack(packet);
		if (null != data) {
			Message message = new Message(data);
			this.session.write(message);
		}
	}

}
