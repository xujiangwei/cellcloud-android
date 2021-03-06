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

/**
 * 消息接收器接口。
 * 
 * @author Ambrose Xu
 * 
 */
public interface MessageAcceptor {

	/**
	 * 绑定消息接收服务到指定端口。
	 * 
	 * @param port 指定绑定端口。
	 * @return 如果绑定成功返回 <code>true</code> 。
	 */
	public boolean bind(int port);

	/**
	 * 绑定消息接收服务到指定地址。
	 * 
	 * @param address 指定绑定地址。
	 * @return 如果绑定成功返回 <code>true</code> 。
	 */
	public boolean bind(InetSocketAddress address);
	
	/**
	 * 解绑当前绑定的服务地址，并关闭所有连接。
	 */
	public void unbind();

	/**
	 * 关闭指定会话。
	 * 
	 * @param session 指定的会话。
	 */
	public void close(Session session);

}
