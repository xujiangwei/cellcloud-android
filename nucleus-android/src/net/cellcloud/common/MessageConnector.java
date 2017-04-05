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
 * 消息连接器。
 * 
 * @author Ambrose Xu
 * 
 */
public interface MessageConnector {

	/**
	 * 连接远端的消息接收器。
	 * 
	 * @param address 指定连接目标地址。
	 * @return 如果成功启动连接任务返回 <code>true</code> 。
	 */
	public boolean connect(InetSocketAddress address);

	/**
	 * 关闭已建立的连接。
	 */
	public void disconnect();

	/**
	 * 是否已经建立连接。
	 * 
	 * @return 如果该连接器已经建立连接返回 <code>true</code> 。
	 */
	public boolean isConnected();

	/**
	 * 设置连接超时值。
	 * 
	 * @param timeout 指定以毫秒为单位的超时时长。
	 */
	public void setConnectTimeout(long timeout);

	/**
	 * 设置数据缓存块大小。
	 * 
	 * @param size 指定数据块大小。
	 */
	public void setBlockSize(int size);

	/**
	 * 获得会话实例。
	 * 
	 * @return 返回会话实例。
	 */
	public Session getSession();

}
