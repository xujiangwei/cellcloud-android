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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Vector;

/**
 * 非阻塞网络接收器工作线程。
 * 
 * @author Ambrose Xu
 * 
 */
public final class NonblockingAcceptorWorker extends Thread {

	// 控制线程生命周期的条件变量
	private byte[] mutex = new byte[0];
	// 是否处于自旋
	private boolean spinning = false;
	// 是否正在工作
	private boolean working = false;

	private NonblockingAcceptor acceptor;

	// 需要执行接收数据任务的 Session 列表
	private Vector<NonblockingAcceptorSession> receiveSessions = new Vector<NonblockingAcceptorSession>();
	// 需要执行发送数据任务的 Session 列表
	private Vector<NonblockingAcceptorSession> sendSessions = new Vector<NonblockingAcceptorSession>();

	/**
	 * 构造函数。
	 * 
	 * @param acceptor
	 */
	public NonblockingAcceptorWorker(NonblockingAcceptor acceptor) {
		this.acceptor = acceptor;
		this.setName("NonblockingAcceptorWorker@" + this.toString());
	}

	@Override
	public void run() {
		this.working = true;
		this.spinning = true;
		NonblockingAcceptorSession session = null;

		while (this.spinning) {
			// 如果没有任务，则线程 wait
			synchronized (this.mutex) {
				if (this.receiveSessions.isEmpty()
					&& this.sendSessions.isEmpty()
					&& this.spinning) {
					try {
						this.mutex.wait();
					} catch (InterruptedException e) {
						Logger.log(NonblockingAcceptorWorker.class, e, LogLevel.DEBUG);
					}
				}
			}

			if (!this.receiveSessions.isEmpty()) {
				// 执行接收数据任务，并移除已执行的 Session
				session = this.receiveSessions.remove(0);
				if (null != session.socket) {
					processReceive(session);
				}
			}

			if (!this.sendSessions.isEmpty()) {
				// 执行发送数据任务，并移除已执行的 Session
				session = this.sendSessions.remove(0);
				if (null != session.socket) {
					processSend(session);
				}
			}

			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// Nothing
			}

			Thread.yield();
		}

		this.working = false;
	}

	/**
	 * 停止自旋。
	 * 
	 * @param blockingCheck
	 */
	protected void stopSpinning(boolean blockingCheck) {
		this.spinning = false;

		synchronized (this.mutex) {
			this.mutex.notifyAll();
		}

		if (blockingCheck) {
			while (this.working) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					Logger.log(NonblockingAcceptorWorker.class, e, LogLevel.DEBUG);
				}
			}
		}
	}

	/**
	 * 返回线程是否正在工作。
	 * 
	 * @return
	 */
	protected boolean isWorking() {
		return this.working;
	}

	/**
	 * 返回当前未处理的接收任务 Session 数量。
	 * 
	 * @return
	 */
	protected int getReceiveSessionNum() {
		return this.receiveSessions.size();
	}

	/**
	 * 返回当前未处理的发送任务 Session 数量。
	 * 
	 * @return
	 */
	protected int getSendSessionNum() {
		return this.sendSessions.size();
	}

	/**
	 * 添加执行接收数据的 Session 。
	 * 
	 * @param session
	 */
	protected void pushReceiveSession(NonblockingAcceptorSession session) {
		if (!this.spinning) {
			return;
		}

		this.receiveSessions.add(session);

		synchronized (this.mutex) {
			this.mutex.notifyAll();
		}
	}

	/**
	 * 添加执行发送数据的 Session 。
	 * 
	 * @param session
	 */
	protected void pushSendSession(NonblockingAcceptorSession session) {
		if (!this.spinning) {
			return;
		}

		if (session.messages.isEmpty()) {
			return;
		}

		this.sendSessions.add(session);

		synchronized (this.mutex) {
			this.mutex.notifyAll();
		}
	}

	/**
	 * 从所有列表中移除指定的 Session 。
	 * 
	 * @param session
	 */
	private void removeSession(NonblockingAcceptorSession session) {
		boolean exist = this.receiveSessions.remove(session);
		while (exist) {
			exist = this.receiveSessions.remove(session);
		}

		exist = this.sendSessions.remove(session);
		while (exist) {
			exist = this.sendSessions.remove(session);
		}
	}

	/**
	 * 处理接收。
	 * 
	 * @param session
	 */
	private void processReceive(NonblockingAcceptorSession session) {
		SocketChannel channel = (SocketChannel) session.selectionKey.channel();

		if (!channel.isConnected()) {
			return;
		}

		int read = 0;
		do {
			// 创建读缓存。
			ByteBuffer buf = ByteBuffer.allocate(session.getBlock());

			synchronized (session) {
				try {
					if (channel.isOpen())
						read = channel.read(buf);
					else
						read = -1;
				} catch (IOException e) {
					if (Logger.isDebugLevel()) {
						Logger.d(this.getClass(), "Remote host has closed the connection.");
					}

					if (null != session.socket) {
						this.acceptor.fireSessionClosed(session);
					}

					try {
						if (channel.isOpen())
							channel.close();
					} catch (IOException ioe) {
						Logger.log(NonblockingAcceptorWorker.class, ioe, LogLevel.DEBUG);
					}

					// 移除 Session
					this.acceptor.eraseSession(session);
					this.removeSession(session);

					session.selectionKey.cancel();

					buf = null;

					return;
				}

				if (read == 0) {
					buf = null;
					break;
				}
				else if (read == -1) {
					if (null != session.socket) {
						this.acceptor.fireSessionClosed(session);
					}

					try {
						if (channel.isOpen())
							channel.close();
					} catch (IOException ioe) {
						Logger.log(NonblockingAcceptorWorker.class, ioe, LogLevel.DEBUG);
					}

					// 移除 Session
					this.acceptor.eraseSession(session);
					this.removeSession(session);

					session.selectionKey.cancel();

					buf = null;

					return;
				}

				buf.flip();

				byte[] array = new byte[read];
				buf.get(array);

				// 解析数据
				this.parse(session, array);

				buf = null;
			}
		} while (read > 0);
	}

	/**
	 * 处理发送。
	 * 
	 * @param session
	 */
	private void processSend(NonblockingAcceptorSession session) {
		SocketChannel channel = (SocketChannel) session.selectionKey.channel();

		if (!channel.isConnected()) {
			return;
		}

		if (!session.messages.isEmpty()) {
			// 有消息，进行发送
			Message message = null;

			synchronized (session) {
				while (!session.messages.isEmpty()) {
					try {
						message = session.messages.remove(0);
					} catch (IndexOutOfBoundsException e) {
						break;
					}

					// 创建写缓存
					ByteBuffer buf = null;

					// 根据是否有数据掩码组装数据包
					if (this.acceptor.existDataMark()) {
						byte[] data = message.get();
						byte[] head = this.acceptor.getHeadMark();
						byte[] tail = this.acceptor.getTailMark();
						byte[] pd = new byte[data.length + head.length + tail.length];
						System.arraycopy(head, 0, pd, 0, head.length);
						System.arraycopy(data, 0, pd, head.length, data.length);
						System.arraycopy(tail, 0, pd, head.length + data.length, tail.length);
						buf = ByteBuffer.wrap(pd);
					}
					else {
						buf = ByteBuffer.wrap(message.get());
					}

					try {
						channel.write(buf);
					} catch (IOException e) {
						Logger.log(NonblockingAcceptorWorker.class, e, LogLevel.WARNING);
					}

					buf = null;

					// 回调事件
					this.acceptor.fireMessageSent(session, message);
				}
			} //# synchronized
		}
	}

	/**
	 * 
	 * @param session
	 * @param data
	 */
	private void parse(NonblockingAcceptorSession session, byte[] data) {
		// 根据数据标志获取数据
		if (this.acceptor.existDataMark()) {
			ArrayList<byte[]> out = new ArrayList<byte[]>(2);
			// 进行递归提取
			this.extract(out, session, data);

			if (!out.isEmpty()) {
				for (byte[] bytes : out) {
					Message message = new Message(bytes);
					this.acceptor.fireMessageReceived(session, message);
				}

				out.clear();
			}
			out = null;
		}
		else {
			Message message = new Message(data);
			this.acceptor.fireMessageReceived(session, message);
		}
	}

	/**
	 * 解析数据格式。
	 * 
	 * @deprecated
	 */
	protected void parseData(NonblockingAcceptorSession session, byte[] data) {
		// 根据数据标志获取数据
		if (this.acceptor.existDataMark()) {
			byte[] headMark = this.acceptor.getHeadMark();
			byte[] tailMark = this.acceptor.getTailMark();

			int cursor = 0;
			int length = data.length;
			boolean head = false;
			boolean tail = false;
			byte[] buf = new byte[this.acceptor.block];
			int bufIndex = 0;

			while (cursor < length) {
				head = true;
				tail = true;

				byte b = data[cursor];

				// 判断是否是头标识
				if (b == headMark[0]) {
					for (int i = 1, len = headMark.length; i < len; ++i) {
						if (data[cursor + i] != headMark[i]) {
							head = false;
							break;
						}
					}
				}
				else {
					head = false;
				}

				// 判断是否是尾标识
				if (b == tailMark[0]) {
					for (int i = 1, len = tailMark.length; i < len; ++i) {
						if (data[cursor + i] != tailMark[i]) {
							tail = false;
							break;
						}
					}
				}
				else {
					tail = false;
				}

				if (head) {
					// 遇到头标识，开始记录数据
					cursor += headMark.length;
					bufIndex = 0;
					buf[bufIndex] = data[cursor];
				}
				else if (tail) {
					// 遇到尾标识，提取 buf 内数据
					byte[] pdata = new byte[bufIndex + 1];
					System.arraycopy(buf, 0, pdata, 0, bufIndex + 1);
					Message message = new Message(pdata);
					this.acceptor.fireMessageReceived(session, message);

					cursor += tailMark.length;
					// 后面要移动到下一个字节因此这里先减1
					cursor -= 1;
				}
				else {
					++bufIndex;
					buf[bufIndex] = b;
				}

				// 下一个字节
				++cursor;
			}

			buf = null;
		}
		else {
			Message message = new Message(data);
			this.acceptor.fireMessageReceived(session, message);
		}
	}

	/**
	 * 数据提取并输出。
	 * 
	 * @param out
	 * @param session
	 * @param data
	 */
	private void extract(final ArrayList<byte[]> out, final NonblockingAcceptorSession session, final byte[] data) {
		final byte[] headMark = this.acceptor.getHeadMark();
		final byte[] tailMark = this.acceptor.getTailMark();

		// 当数据小于标签长度时直接缓存
//		if (data.length < headMark.length) {
//			if (session.cacheCursor + data.length > session.getCacheSize()) {
//				// 重置 cache 大小
//				session.resetCacheSize(session.cacheCursor + data.length);
//			}
//			System.arraycopy(data, 0, session.cache, session.cacheCursor, data.length);
//			session.cacheCursor += data.length;
//			return;
//		}

		byte[] real = data;
		if (session.cacheCursor > 0) {
			real = new byte[session.cacheCursor + data.length];
			System.arraycopy(session.cache, 0, real, 0, session.cacheCursor);
			System.arraycopy(data, 0, real, session.cacheCursor, data.length);
			session.cacheCursor = 0;
		}

		// 当数据小于标签长度时直接缓存
		if (real.length < headMark.length) {
			if (session.cacheCursor + real.length > session.getCacheSize()) {
				// 重置 cache 大小
				session.resetCacheSize(session.cacheCursor + real.length);
			}
			System.arraycopy(real, 0, session.cache, session.cacheCursor, real.length);
			session.cacheCursor += real.length;
			return;
		}

		int index = 0;
		int len = real.length;
		int headPos = -1;
		int tailPos = -1;

		if (compareBytes(headMark, 0, real, index, headMark.length)) {
			// 有头标签
			index += headMark.length;
			// 记录数据位置头
			headPos = index;
			// 判断是否有尾标签
			while (index < len) {
//				if (real[index] == tailMark[0]) {
					if (compareBytes(tailMark, 0, real, index, tailMark.length)) {
						// 找到尾标签
						tailPos = index;
						break;
					}
					else {
						++index;
					}
//				}
//				else {
//					++index;
//				}
			}

			if (headPos > 0 && tailPos > 0) {
				byte[] outBytes = new byte[tailPos - headPos];
				System.arraycopy(real, headPos, outBytes, 0, tailPos - headPos);
				out.add(outBytes);

				int newLen = len - tailPos - tailMark.length;
				if (newLen > 0) {
					byte[] newBytes = new byte[newLen];
					System.arraycopy(real, tailPos + tailMark.length, newBytes, 0, newLen);

					// 递归
					extract(out, session, newBytes);
				}
			}
			else {
				// 没有尾标签
				// 仅进行缓存
				if (len + session.cacheCursor > session.getCacheSize()) {
					// 缓存扩容
					session.resetCacheSize(len + session.cacheCursor);
				}

				System.arraycopy(real, 0, session.cache, session.cacheCursor, len);
				session.cacheCursor += len;
			}
		}
		else {
			// 没有头标签
			// 尝试找到头标签
			byte[] markBuf = new byte[headMark.length];
			int searchIndex = 0;
			int searchCounts = 0;
			do {
				// 判断数据是否越界
				if (searchIndex + headMark.length > len) {
					// 越界，删除索引之前的所有数据
					byte[] newReal = new byte[len - searchIndex];
					System.arraycopy(real, searchIndex, newReal, 0, newReal.length);

					if (session.cacheCursor + newReal.length > session.getCacheSize()) {
						// 重置 cache 大小
						session.resetCacheSize(session.cacheCursor + newReal.length);
					}
					System.arraycopy(newReal, 0, session.cache, session.cacheCursor, newReal.length);
					session.cacheCursor += newReal.length;
					// 退出循环
					break;
				}

				// 复制数据到待测试缓存
				System.arraycopy(real, searchIndex, markBuf, 0, headMark.length);

				for (int i = 0; i < markBuf.length; ++i) {
					if (markBuf[i] == headMark[i]) {
						++searchCounts;
					}
					else {
						break;
					}
				}

				if (searchCounts == headMark.length) {
					// 找到 head mark
					byte[] newReal = new byte[len - searchIndex];
					System.arraycopy(real, searchIndex, newReal, 0, newReal.length);
					extract(out, session, newReal);
					return;
				}

				// 更新索引
				++searchIndex;

				// 重置计数
				searchCounts = 0;
			} while (searchIndex < len);
		}

//		byte[] newBytes = new byte[len - headMark.length];
//		System.arraycopy(real, headMark.length, newBytes, 0, newBytes.length);
//		extract(out, session, newBytes);
	}

	private boolean compareBytes(byte[] b1, int offsetB1, byte[] b2, int offsetB2, int length) {
		for (int i = 0; i < length; ++i) {
			// FIXME XJW 2015-12-30 判断数组越界
			if (offsetB1 + i >= b1.length || offsetB2 + i >= b2.length) {
				return false;
			}

			if (b1[offsetB1 + i] != b2[offsetB2 + i]) {
				return false;
			}
		}
		return true;
	}

}
