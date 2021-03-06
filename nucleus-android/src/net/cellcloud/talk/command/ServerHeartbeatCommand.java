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

import net.cellcloud.common.Packet;
import net.cellcloud.common.Session;
import net.cellcloud.talk.TalkService;

/**
 * 对话 heartbeat 命令。
 * 
 * @author Ambrose Xu
 * 
 */
public final class ServerHeartbeatCommand extends ServerCommand {

	/**
	 * 构造函数。
	 * 
	 * @param service
	 */
	public ServerHeartbeatCommand(TalkService service) {
		super(service, null, null);
	}

	/**
	 * 构造函数。
	 * 
	 * @param service
	 * @param session
	 * @param packet
	 */
	public ServerHeartbeatCommand(TalkService service, Session session, Packet packet) {
		super(service, session, packet);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void execute() {
		this.service.updateSessionHeartbeat(this.session);
	}

}
