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

package net.cellcloud.common;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.util.Vector;

/**
 * 非阻塞网络接收器会话。
 * 
 * @author Ambrose Xu
 * 
 */
public class NonblockingAcceptorSession extends Session {

	private int block;

	// 待发送消息列表
	protected Vector<Message> messages = new Vector<Message>();

	protected SelectionKey selectionKey = null;
	protected Socket socket = null;

	// 所属的工作线程
	protected NonblockingAcceptorWorker worker = null;

	/**
	 * 构造函数。
	 * 
	 * @param service
	 * @param address
	 * @param block
	 */
	public NonblockingAcceptorSession(MessageService service, InetSocketAddress address, int block) {
		super(service, address);
		this.block = block;
	}

	/**
	 * 返回缓存大小。
	 * 
	 * @return
	 */
	public int getBlock() {
		return this.block;
	}

}
