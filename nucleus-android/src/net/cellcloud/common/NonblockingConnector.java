/*
-----------------------------------------------------------------------------
This source file is part of Cell Cloud.

Copyright (c) 2009-2016 Cell Cloud Team (www.cellcloud.net)

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
import java.util.LinkedList;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import net.cellcloud.util.Utils;
import android.content.Context;


/** 非阻塞式网络连接器。
 * 
 * @author Jiangwei Xu
 */
public class NonblockingConnector extends MessageService implements MessageConnector {

	// 缓冲块大小
	private int block = 32768;
	private final int writeLimit = 16384;

	private InetSocketAddress address;
	private long connectTimeout;
	private SocketChannel channel;
	private Selector selector;

	private Session session;

	private Timer handleTimer = null;
	private Runnable endingCallback = null;
	private long timerInterval = 500;
	private volatile boolean running = false;

	// 待发送消息列表
	private Vector<Message> messages;

	private boolean closed = false;

	private Context androidContext;

	public NonblockingConnector(Context androidContext) {
		this.androidContext = androidContext;
		this.connectTimeout = 15000;
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
			return true;
		}

		// For Android 2.2
		System.setProperty("java.net.preferIPv6Addresses", "false");

		// 判断是否有网络连接
		if (!Utils.isNetworkConnected(this.androidContext)) {
			this.fireErrorOccurred(MessageErrorCode.NO_NETWORK);
			return false;
		}

		if (this.running && null != this.channel) {
			// 停止循环
			this.stopLoop();

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
					Thread.yield();
				} catch (InterruptedException e) {
					Logger.log(NonblockingConnector.class, e, LogLevel.DEBUG);
					break;
				}
			}
		}

		// 状态初始化
		this.messages.clear();
		this.address = address;

		try {
			this.channel = SocketChannel.open();//SelectorProvider.provider().openSocketChannel();
			this.channel.configureBlocking(false);

			// 设置 Socket 参数
			this.channel.socket().setSoTimeout(5000);
			this.channel.socket().setKeepAlive(true);
			this.channel.socket().setReceiveBufferSize(this.block);
			this.channel.socket().setSendBufferSize(this.block);

			this.selector = Selector.open();//SelectorProvider.provider().openSelector();

			// 注册事件
			this.channel.register(this.selector, SelectionKey.OP_CONNECT);

			// 连接
			this.channel.connect(this.address);
		} catch (IOException e) {
			Logger.log(NonblockingConnector.class, e, LogLevel.WARNING);

			// 回调错误
			this.fireErrorOccurred(MessageErrorCode.SOCKET_FAILED);

			try {
				if (null != this.channel) {
					this.channel.close();
				}
			} catch (Exception ce) {
				// Nothing
			}
			try {
				if (null != this.selector) {
					this.selector.close();
				}
			} catch (Exception se) {
				// Nothing
			}

			return false;
		} catch (Exception e) {
			Logger.log(NonblockingConnector.class, e, LogLevel.WARNING);
			return false;
		}

		// 创建 Session
		this.session = new Session(this, this.address);

		Thread boot = new Thread() {
			@Override
			public void run() {
				running = true;

				if (Logger.isDebugLevel()) {
					Logger.d(NonblockingConnector.class, this.getName());
				}

				// 通知 Session 创建。
				fireSessionCreated();

				try {
					loopDispatch(new Runnable() {
						@Override
						public void run() {
							// 通知 Session 销毁。
							fireSessionDestroyed();

							running = false;

							try {
								if (null != selector && selector.isOpen())
									selector.close();
								if (null != channel && channel.isOpen())
									channel.close();
							} catch (IOException e) {
								// Nothing
							}
						}
					});
				} catch (Exception e) {
					// 终止循环
					stopLoop();
					Logger.log(NonblockingConnector.class, e, LogLevel.DEBUG);
				}
			}
		};
		boot.setName(new StringBuilder("NonblockingConnector[").append(boot).append("]@")
				.append(this.address.getAddress().getHostAddress()).append(":").append(this.address.getPort()).toString());
		// 启动线程
		boot.start();

		return true;
	}

	private void cleanup() {
		try {
			if (null != selector) {
				this.selector.close();
			}

			if (null != this.channel) {
				this.channel.socket().shutdownInput();
				this.channel.socket().shutdownOutput();
				this.channel.socket().close();
				this.channel.close();
			}
		} catch (IOException e) {
			// Nothing
		}
	}

	@Override
	public void disconnect() {
		// 终止循环
		this.stopLoop();

		if (null != this.channel) {
			if (this.channel.isConnected()) {
				fireSessionClosed();
			}

			try {
				this.channel.socket().shutdownInput();
				this.channel.socket().shutdownOutput();
				this.channel.socket().close();
			} catch (Exception e) {
				Logger.log(NonblockingConnector.class, e, LogLevel.DEBUG);
			}

			try {
				if (this.channel.isOpen()) {
					this.channel.close();
				}
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
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Logger.log(NonblockingConnector.class, e, LogLevel.DEBUG);
			}

			if (++count >= 30) {
				this.handleTimer = null;
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
		if (size < 2048) {
			return;
		}

		if (this.block == size) {
			return;
		}

		this.block = size;

		if (null != this.channel) {
			try {
				this.channel.socket().setReceiveBufferSize(this.block);
				this.channel.socket().setSendBufferSize(this.block);
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
		if (null == this.channel || false == this.channel.isConnected()) {
			this.fireErrorOccurred(MessageErrorCode.CONNECT_FAILED);
			return;
		}

		if (message.length() > this.writeLimit) {
			this.fireErrorOccurred(MessageErrorCode.WRITE_OUTOFBOUNDS);
			return;
		}

		this.messages.add(message);
	}

	/**
	 * 重置休眠间隔。
	 * @param value
	 */
	public void resetInterval(long value) {
		if (this.timerInterval == value) {
			return;
		}

		this.timerInterval = value;

		if (null != this.handleTimer) {
			this.handleTimer.cancel();
			this.handleTimer.purge();
			this.handleTimer = null;
		}

		this.handleTimer = new Timer();
		// 启动新任务
		this.handleTimer.scheduleAtFixedRate(this.createTask(), Math.min(value, 1000), value);
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
	private void loopDispatch(Runnable endingCallback) throws Exception {
		this.endingCallback = endingCallback;

		if (null != this.handleTimer) {
			return;
		}

		this.handleTimer = new Timer();
		// 启动定时任务
		this.handleTimer.scheduleAtFixedRate(this.createTask(), 1000, this.timerInterval);
	}

	private TimerTask createTask() {
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				if (!selector.isOpen()) {
					return;
				}

				try {
					if (selector.select(channel.isConnected() ? 0 : connectTimeout) > 0) {
						Set<SelectionKey> keys = selector.selectedKeys();
						Iterator<SelectionKey> it = keys.iterator();
						while (it.hasNext()) {
							SelectionKey key = (SelectionKey) it.next();
							it.remove();

							// 当前通道选择器产生连接已经准备就绪事件，并且客户端套接字通道尚未连接到服务端套接字通道
							if (key.isConnectable()) {
								if (!doConnect(key)) {
									// 停止循环
									stopLoop();
									return;
								}
								else {
									// 连接成功，打开 Session
									fireSessionOpened();
								}
							}
							if (key.isValid() && key.isReadable()) {
								receive(key);
							}
							if (key.isValid() && key.isWritable()) {
								send(key);
							}
						} //# while
					}
				} catch (Exception exception) {
					stopLoop();
					Logger.log(handleTimer.getClass(), exception, LogLevel.DEBUG);
				}
			}
		};
		return task;
	}

	private void stopLoop() {
		if (null != this.handleTimer) {
			this.handleTimer.cancel();
			this.handleTimer.purge();
			this.handleTimer = null;
		}

		// 关闭会话
		this.fireSessionClosed();

		if (null != this.endingCallback) {
			this.endingCallback.run();
		}
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
				Logger.log(NonblockingConnector.class, e, LogLevel.DEBUG);

				// 清理
				this.cleanup();

				// 连接失败
				fireErrorOccurred(MessageErrorCode.CONNECT_TIMEOUT);

				return false;
			}
		}

		if (key.isValid()) {
			key.interestOps(key.interestOps() & ~SelectionKey.OP_CONNECT);
			key.interestOps(key.interestOps() | SelectionKey.OP_READ | SelectionKey.OP_WRITE);
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
			ByteBuffer readBuffer = ByteBuffer.allocate(this.block);
			try {
				read = channel.read(readBuffer);
			} catch (IOException e) {
				fireSessionClosed();

				// 清理
				this.cleanup();
				// 不能继续进行数据接收
				this.stopLoop();
				readBuffer = null;
				return;
			}

			if (read == 0) {
				readBuffer = null;
				break;
			}
			else if (read == -1) {
				fireSessionClosed();

				// 清理
				this.cleanup();
				// 不能继续进行数据接收
				this.stopLoop();
				readBuffer = null;
				return;
			}

			readBuffer.flip();

			// 数据读取
			byte[] array = new byte[read];
			readBuffer.get(array);

			// 处理数据
			try {
				this.process(array);
			} catch (ArrayIndexOutOfBoundsException e) {
				this.session.cacheCursor = 0;
				Logger.log(NonblockingConnector.class, e, LogLevel.WARNING);
			} catch (Exception e) {
				Logger.log(NonblockingConnector.class, e, LogLevel.WARNING);
			}

			readBuffer = null;
		} while (read > 0);

		if (key.isValid()) {
			key.interestOps(key.interestOps() | SelectionKey.OP_READ);
		}
	}

	private void send(SelectionKey key) {
		SocketChannel channel = (SocketChannel) key.channel();

		if (!channel.isConnected()) {
			fireSessionClosed();
			return;
		}

		try {
			if (!this.messages.isEmpty()) {
				// 有消息，进行发送

				Message message = null;
				for (int i = 0, len = this.messages.size(); i < len; ++i) {
					try {
						message = this.messages.remove(0);
					} catch (IndexOutOfBoundsException e) {
						break;
					}

					byte[] skey = this.session.getSecretKey();
					if (null != skey) {
						this.encryptMessage(message, skey);
					}

					ByteBuffer writeBuffer = null;
					if (this.existDataMark()) {
						byte[] data = message.get();
						byte[] head = this.getHeadMark();
						byte[] tail = this.getTailMark();
						byte[] pd = new byte[data.length + head.length + tail.length];
						System.arraycopy(head, 0, pd, 0, head.length);
						System.arraycopy(data, 0, pd, head.length, data.length);
						System.arraycopy(tail, 0, pd, head.length + data.length, tail.length);
						writeBuffer = ByteBuffer.wrap(pd);
					}
					else {
						writeBuffer = ByteBuffer.wrap(message.get());
					}

					channel.write(writeBuffer);

					writeBuffer = null;

					if (null != this.handler) {
						this.handler.messageSent(this.session, message);
					}
				}
			}
		} catch (IOException e) {
			Logger.log(NonblockingConnector.class, e, LogLevel.WARNING);
		}

		if (key.isValid()) {
			key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
		}
	}

	private void process(byte[] data) {
		// 根据数据标志获取数据
		if (this.existDataMark()) {
			LinkedList<byte[]> out = new LinkedList<byte[]>();
			// 数据递归提取
			this.extract(out, data);

			if (!out.isEmpty()) {
				for (byte[] bytes : out) {
					Message message = new Message(bytes);

					byte[] skey = this.session.getSecretKey();
					if (null != skey) {
						this.decryptMessage(message, skey);
					}

					if (null != this.handler) {
						this.handler.messageReceived(this.session, message);
					}
				}

				out.clear();
			}
			out = null;
		}
		else {
			Message message = new Message(data);

			byte[] skey = this.session.getSecretKey();
			if (null != skey) {
				this.decryptMessage(message, skey);
			}

			if (null != this.handler) {
				this.handler.messageReceived(this.session, message);
			}
		}
	}

	/**
	 * @deprecated
	 */
	protected void processData(byte[] data) {
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

	/**
	 * 数据提取并输出。
	 */
	private void extract(final LinkedList<byte[]> out, final byte[] data) {
		final byte[] headMark = this.getHeadMark();
		final byte[] tailMark = this.getTailMark();

		// 当数据小于标签长度时直接缓存
//		if (data.length < headMark.length) {
//			if (this.session.cacheCursor + data.length > this.session.getCacheSize()) {
//				// 重置 cache 大小
//				this.session.resetCacheSize(this.session.cacheCursor + data.length);
//			}
//			System.arraycopy(data, 0, this.session.cache, this.session.cacheCursor, data.length);
//			this.session.cacheCursor += data.length;
//			return;
//		}

		byte[] real = data;
		if (this.session.cacheCursor > 0) {
			real = new byte[this.session.cacheCursor + data.length];
			System.arraycopy(this.session.cache, 0, real, 0, this.session.cacheCursor);
			System.arraycopy(data, 0, real, this.session.cacheCursor, data.length);
			this.session.cacheCursor = 0;
		}

		// 当数据小于标签长度时直接缓存
		if (real.length < headMark.length) {
			if (this.session.cacheCursor + real.length > this.session.getCacheSize()) {
				// 重置 cache 大小
				this.session.resetCacheSize(this.session.cacheCursor + real.length);
			}
			System.arraycopy(real, 0, this.session.cache, this.session.cacheCursor, real.length);
			this.session.cacheCursor += real.length;
			return;
		}

		int index = 0;
		int len = real.length;
		int headPos = -1;
		int tailPos = -1;

		if (0 == compareBytes(headMark, 0, real, index, headMark.length)) {
			// 有头标签
			index += headMark.length;
			// 记录数据位置头
			headPos = index;
			// 判断是否有尾标签，依次计数
			int ret = -1;
			while (index < len) {
				if (real[index] == tailMark[0]) {
					ret = compareBytes(tailMark, 0, real, index, tailMark.length);
					if (0 == ret) {
						// 找到尾标签
						tailPos = index;
						break;
					}
					else if (1 == ret) {
						// 越界
						break;
					}
					else {
						// 未找到尾标签
						++index;
					}
				}
				else {
					++index;
				}
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
					extract(out, newBytes);
				}
			}
			else {
				// 没有尾标签
				// 仅进行缓存
				if (len + this.session.cacheCursor > this.session.getCacheSize()) {
					// 缓存扩容
					this.session.resetCacheSize(len + this.session.cacheCursor);
				}

				System.arraycopy(real, 0, this.session.cache, this.session.cacheCursor, len);
				this.session.cacheCursor += len;
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

					if (this.session.cacheCursor + newReal.length > this.session.getCacheSize()) {
						// 重置 cache 大小
						this.session.resetCacheSize(this.session.cacheCursor + newReal.length);
					}
					System.arraycopy(newReal, 0, this.session.cache, this.session.cacheCursor, newReal.length);
					this.session.cacheCursor += newReal.length;
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
					extract(out, newReal);
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
//		extract(out, newBytes);
	}

	/**
	 * 
	 * @param b1
	 * @param offsetB1
	 * @param b2
	 * @param offsetB2
	 * @param length
	 * @return 0 表示匹配，-1 表示不匹配，1 表示越界
	 */
	private int compareBytes(byte[] b1, int offsetB1, byte[] b2, int offsetB2, int length) {
		for (int i = 0; i < length; ++i) {
			// FIXME XJW 2015-12-30 判断数组越界
			if (offsetB1 + i >= b1.length || offsetB2 + i >= b2.length) {
				return 1;
			}

			if (b1[offsetB1 + i] != b2[offsetB2 + i]) {
				return -1;
			}
		}

		return 0;
	}

	private void encryptMessage(Message message, byte[] key) {
		byte[] plaintext = message.get();
		byte[] ciphertext = Cryptology.getInstance().simpleEncrypt(plaintext, key);
		message.set(ciphertext);
	}

	private void decryptMessage(Message message, byte[] key) {
		byte[] ciphertext = message.get();
		byte[] plaintext = Cryptology.getInstance().simpleDecrypt(ciphertext, key);
		message.set(plaintext);
	}
}
