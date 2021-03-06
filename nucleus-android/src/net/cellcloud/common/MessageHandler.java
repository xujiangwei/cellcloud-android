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

/**
 * 消息服务处理监听器。
 * 
 * @author Ambrose Xu
 * 
 */
public interface MessageHandler {

	/**
	 * 创建连接会话。
	 * 
	 * @param session 创建的会话。
	 */
	public void sessionCreated(Session session);

	/**
	 * 销毁连接会话。
	 * 
	 * @param session 销毁的会话。
	 */
	public void sessionDestroyed(Session session);

	/**
	 * 开启连接会话。
	 * 
	 * @param session 开启的会话。
	 */
	public void sessionOpened(Session session);

	/**
	 * 关闭连接会话。
	 * 
	 * @param session 关闭的会话。
	 */
	public void sessionClosed(Session session);

	/**
	 * 接收到消息。
	 * 
	 * @param session 接收消息的会话。
	 * @param message 接收的消息。
	 */
	public void messageReceived(Session session, Message message);

	/**
	 * 消息已发送。
	 * 
	 * @param session 发送消息的目标会话。
	 * @param message 发送的消息。
	 */
	public void messageSent(Session session, Message message);

	/**
	 * 发生错误。
	 * 
	 * @param errorCode 错误码 {@link MessageErrorCode} 。
	 * @param session 发生错误的会话。
	 */
	public void errorOccurred(int errorCode, Session session, Message message);

}
