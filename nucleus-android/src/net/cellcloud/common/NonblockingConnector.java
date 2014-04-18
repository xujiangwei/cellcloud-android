/*
-----------------------------------------------------------------------------
This source file is part of Cell Cloud.

Copyright (c) 2009-2013 Cell Cloud Team (www.cellcloud.net)

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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import net.cellcloud.util.Utils;
import android.content.Context;


/** 非阻塞式网络连接器。
 * 
 * @author Jiangwei Xu
 */
public class NonblockingConnector extends MessageService implements MessageConnector {

	// 缓冲块大小
	private int block = 8192;

	private InetSocketAddress address;
	private long connectTimeout;
	private SocketChannel channel;
	private Selector selector;

	private Session session;

	private Thread handleThread;
	private boolean spinning = false;
	private boolean running = false;

	private ByteBuffer readBuffer;
	private ByteBuffer writeBuffer;
	// 待发送消息列表
	private Vector<Message> messages;

	private boolean closed = false;

	private Context androidContext;

	public NonblockingConnector(Context androidContext) {
		this.androidContext = androidContext;
		this.connectTimeout = 15000;
		this.readBuffer = ByteBuffer.allocate(this.block);
		this.writeBuffer = ByteBuffer.allocate(this.block);
		this.messages = new Vector<Message>();
	}

	/** 返回连接地址。
	 */
	public InetSocketAddress getAddress() {
		return this.address;
	}

	@Override
	public boolean connect(final InetSocketAddress address) {
		if (this.channel != null && this.channel.isConnected()) {
			Logger.w(NonblockingConnector.class, "Connector has connected to " + address.getAddress().getHostAddress());
			return false;
		}

		// For Android 2.2
		System.setProperty("java.net.preferIPv6Addresses", "false");

		// 判断是否有网络连接
		if (!Utils.isWifiConnected(this.androidContext)) {
			if (!Utils.isMobileConnected(this.androidContext)) {
				this.fireErrorOccurred(MessageErrorCode.NO_NETWORK);
				return false;
			}
		}

		if (this.running && null != this.channel) {
			this.spinning = false;

			try {
				if (this.channel.isOpen()) {
					this.channel.close();
				}

				if (null != this.selector) {
					this.selector.close();
				}
			} catch (IOException e) {
				Logger.log(NonblockingConnector.class, e, LogLevel.DEBUG);
			}

			while (this.running) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					Logger.log(NonblockingConnector.class, e, LogLevel.DEBUG);
					break;
				}
			}
		}

		// 状态初始化
		this.readBuffer.clear();
		this.writeBuffer.clear();
		this.messages.clear();
		this.address = address;

		boolean done = false;
		SelectionKey skey = null;
		try {
			this.channel = SocketChannel.open();//SelectorProvider.provider().openSocketChannel();
			this.channel.configureBlocking(false);

			// 设置 Socket 参数
			this.channel.socket().setSoTimeout(30000);
			this.channel.socket().setKeepAlive(true);
			this.channel.socket().setReceiveBufferSize(this.block + 1024);
			this.channel.socket().setSendBufferSize(this.block + 1024);

			this.selector = Selector.open();//SelectorProvider.provider().openSelector();

			// 注册事件
			skey = this.channel.register(this.selector, SelectionKey.OP_CONNECT);

			done = true;
		} catch (Exception e) {
			Logger.log(NonblockingConnector.class, e, LogLevel.WARNING);

			// 回调错误
			this.fireErrorOccurred(MessageErrorCode.SOCKET_FAILED);
		} finally {
			if (!done && null != this.selector) {
				try {
					this.selector.close();
				} catch (IOException e) {
					// Nothing
				}
			}
			if (!done && null != this.selector) {
				try {
					this.channel.close();
				} catch (IOException e) {
					// Nothing
				}
			}

			if (!done) {
				return false;
			}
		}

		final SelectionKey key = skey;

		// 创建 Session
		this.session = new Session(this, this.address);

		this.handleThread = new Thread() {

			@Override
			public void run() {
				running = true;

				if (Logger.isDebugLevel()) {
					Logger.d(NonblockingConnector.class, this.getName());
				}

				// 通知 Session 创建。
				fireSessionCreated();

				// 进行连接
				SocketChannel channel = (SocketChannel) key.channel();
				Selector selector = key.selector();
				key.interestOps(key.interestOps() | SelectionKey.OP_CONNECT);

				int keys = 0;
				try {
					// 尝试连接
					channel.connect(address);

					if (!key.isConnectable()) {
						keys = selector.select(connectTimeout);
					}

					if (keys == 0) {
						fireErrorOccurred(MessageErrorCode.CONNECT_TIMEOUT);
						// 清理
						cleanup();
						running = false;
						// 通知 Session 销毁。
						fireSessionDestroyed();
						return;
					}

					if (!doConnect(key)) {
						Logger.e(NonblockingConnector.class, "Can not connect remote host");
					}
				} catch (Exception e) {
					Logger.log(NonblockingConnector.class, e, LogLevel.ERROR);
					// 清理
					cleanup();
					running = false;
					// 通知 Session 销毁。
					fireSessionDestroyed();
					return;
				} finally {
					if (key.isValid()) {
						key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
						key.interestOps(key.interestOps() | SelectionKey.OP_READ | SelectionKey.OP_WRITE);
					}
				}

				// 连接成功，打开 Session
				fireSessionOpened();

				try {
					loopDispatch();
				} catch (Exception e) {
					spinning = false;
					Logger.log(NonblockingConnector.class, e, LogLevel.DEBUG);
				}

				// 连接断开，关闭 Session
				fireSessionClosed();

				// 通知 Session 销毁。
				fireSessionDestroyed();

				running = false;

				try {
					if (selector.isOpen())
						selector.close();
					if (channel.isOpen())
						channel.close();
				} catch (IOException e) {
					// Nothing
				}
			}
		};
		this.handleThread.setName(new StringBuilder("NonblockingConnector[").append(this.handleThread).append("]@")
				.append(this.address.getAddress().getHostAddress()).append(":").append(this.address.getPort()).toString());
		// 启动线程
		this.handleThread.start();

		return true;
	}

	private void cleanup() {
		try {
			if (null != selector)
				this.selector.close();

			if (null != this.channel)
				this.channel.close();
		} catch (IOException e) {
			// Nothing
		}
	}

	@Override
	public void disconnect() {
		this.spinning = false;

		if (null != this.channel) {
			if (this.channel.isConnected()) {
				fireSessionClosed();
			}

			try {
				if (this.channel.isOpen()) {
					this.channel.close();
				}
			} catch (Exception e) {
				Logger.log(NonblockingConnector.class, e, LogLevel.DEBUG);
			}

			try {
				this.channel.socket().close();
			} catch (Exception e) {
				Logger.log(NonblockingConnector.class, e, LogLevel.DEBUG);
			}
		}

		if (null != this.selector && this.selector.isOpen()) {
			try {
				this.selector.wakeup();
				this.selector.close();
			} catch (Exception e) {
				Logger.log(NonblockingConnector.class, e, LogLevel.DEBUG);
			}
		}

		int count = 0;
		while (this.running) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Logger.log(NonblockingConnector.class, e, LogLevel.DEBUG);
			}

			if (++count >= 200) {
				this.handleThread.interrupt();
				this.running = false;
			}
		}
	}

	@Override
	public void setConnectTimeout(long timeout) {
		this.connectTimeout = timeout;
	}

	public long getConnectTimeout() {
		return this.connectTimeout;
	}

	@Override
	public void setBlockSize(int size) {
		if (this.block == size) {
			return;
		}

		this.block = size;
		this.readBuffer = ByteBuffer.allocate(this.block);
		this.writeBuffer = ByteBuffer.allocate(this.block);

		if (null != this.channel) {
			try {
				this.channel.socket().setReceiveBufferSize(this.block + 1024);
				this.channel.socket().setSendBufferSize(this.block + 1024);
			} catch (Exception e) {
				// ignore
			}
		}
	}

	public int getBlockSize() {
		return this.block;
	}

	/** 是否已连接。
	 */
	public boolean isConnected() {
		return (null != this.channel && this.channel.isConnected());
	}

	@Override
	public Session getSession() {
		return this.session;
	}

	public void write(Message message) {
		this.write(null, message);
	}

	@Override
	public void write(Session session, Message message) {
		this.messages.add(message);
	}

	@Override
	public void read(Message message, Session session) {
		// Nothing
	}

	private void fireSessionCreated() {
		if (null != this.handler) {
			this.handler.sessionCreated(this.session);
		}
	}
	private void fireSessionOpened() {
		if (null != this.handler) {
			this.closed = false;
			this.handler.sessionOpened(this.session);
		}
	}
	private void fireSessionClosed() {
		if (null != this.handler) {
			if (!this.closed) {
				this.closed = true;
				this.handler.sessionClosed(this.session);
			}
		}
	}
	private void fireSessionDestroyed() {
		if (null != this.handler) {
			this.handler.sessionDestroyed(this.session);
		}
	}
	private void fireErrorOccurred(int errorCode) {
		if (null != this.handler) {
			this.handler.errorOccurred(errorCode, this.session);
		}
	}

	/** 事件循环。 */
	private void loopDispatch() throws Exception {
		// 自旋
		this.spinning = true;

		while (this.spinning) {
			// 等待事件
			this.selector.select();

			Set<SelectionKey> keys = this.selector.selectedKeys();
			Iterator<SelectionKey> it = keys.iterator();
			while (it.hasNext()) {
				SelectionKey key = (SelectionKey) it.next();
				it.remove();

				if (!key.isValid()) {
                    continue;
                }
				if (key.isReadable()) {
					this.receive(key);
				}
				if (key.isWritable()) {
					this.send(key);
				}
			} //# while

			if (!this.spinning) {
				return;
			}

			// thread sleep
			Thread.sleep(2);
		} //# while
	}

	private boolean doConnect(SelectionKey key) {
		// 获取创建通道选择器事件键的套接字通道
		SocketChannel channel = (SocketChannel) key.channel();

		// 判断此通道上是否正在进行连接操作。  
        // 完成套接字通道的连接过程。
		if (channel.isConnectionPending()) {
			try {
				channel.finishConnect();
			} catch (IOException e) {
				Logger.log(NonblockingConnector.class, e, LogLevel.WARNING);

				// 清理
				this.cleanup();

				// 连接失败
				fireErrorOccurred(MessageErrorCode.CONNECT_TIMEOUT);

				return false;
			}
		}

		return true;
	}

	private void receive(SelectionKey key) {
		SocketChannel channel = (SocketChannel) key.channel();

		if (!channel.isConnected()) {
			return;
		}

		int read = 0;
		do {
			try {
				read = channel.read(this.readBuffer);
			} catch (IOException e) {
				fireSessionClosed();

				// 清理
				this.cleanup();
				// 不能继续进行数据接收
				this.spinning = false;
				return;
			}

			if (read == 0) {
				break;
			}
			else if (read == -1) {
				fireSessionClosed();

				// 清理
				this.cleanup();
				// 不能继续进行数据接收
				this.spinning = false;
				return;
			}

			this.readBuffer.flip();

			// TODO 优化数据读取
			byte[] array = new byte[read];
			this.readBuffer.get(array);

			// 处理数据
			process(array);

			this.readBuffer.clear();

			array = null;
		} while (read > 0);

		if (key.isValid()) {
			key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		}
	}

	private void send(SelectionKey key) {
		SocketChannel channel = (SocketChannel) key.channel();

		if (!channel.isConnected()) {
			return;
		}

		try {
			if (!this.messages.isEmpty()) {
				// 有消息，进行发送

				Message message = null;
				for (int i = 0, len = this.messages.size(); i < len; ++i) {
					message = this.messages.remove(0);

					if (this.existDataMark()) {
						byte[] data = message.get();
						byte[] head = this.getHeadMark();
						byte[] tail = this.getTailMark();
						byte[] pd = new byte[data.length + head.length + tail.length];
						System.arraycopy(head, 0, pd, 0, head.length);
						System.arraycopy(data, 0, pd, head.length, data.length);
						System.arraycopy(tail, 0, pd, head.length + data.length, tail.length);
						this.writeBuffer.put(pd);
					}
					else {
						this.writeBuffer.put(message.get());
					}

					this.writeBuffer.flip();

					channel.write(this.writeBuffer);

					this.writeBuffer.clear();

					if (null != this.handler) {
						this.handler.messageSent(this.session, message);
					}
				}
			}
		} catch (IOException e) {
			Logger.log(NonblockingConnector.class, e, LogLevel.WARNING);
		} finally {
			if (key.isValid()) {
				key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
			}
		}
	}

	private void process(byte[] data) {
		// 根据数据标志获取数据
		if (this.existDataMark()) {
			byte[] headMark = this.getHeadMark();
			byte[] tailMark = this.getTailMark();

			int cursor = 0;
			int length = data.length;
			boolean head = false;
			boolean tail = false;
			byte[] buf = new byte[this.block];
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
					if (null != this.handler) {
						this.handler.messageReceived(this.session, message);
					}

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
			if (null != this.handler) {
				this.handler.messageReceived(this.session, message);
			}
		}
	}
}
