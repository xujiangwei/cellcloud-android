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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import net.cellcloud.util.Utils;
import android.content.Context;

/**
 * 阻塞式网络连接器。
 * 
 * @author Ambrose Xu
 *
 */
public class BlockingConnector extends MessageService implements MessageConnector {

	/**
	 * 数据发送队列优先级定义。
	 */
	public enum BlockingConnectorQueuePriority {
		/** 高优先级队列。 */
		High,
		/** 低优先级队列。 */
		Low
	}

	/** 缓冲块大小。 */
	private int block = 65536;
	/** 单次写数据大小限制（字节），默认 16 KB 。 */
	private final int writeLimit = 16384;

	/** Socket 超时时间。 */
	private int soTimeout = 1000;
	/** 连接超时时间。 */
	private long connTimeout = 10000L;

	/** 两次阻塞操作之间的时间间隔。 */
	private long interval = 1000L;

	/** Socket 句柄。 */
	private Socket socket = null;

	/** 数据处理线程。 */
	private Thread handleThread;
	/** 线程是否自悬。 */
	private boolean spinning = false;
	/** 线程是否正在运行。 */
	private AtomicBoolean running = new AtomicBoolean(false);
	/** 主动关闭标识。 */
	private boolean activeClose = false;

	/** Session 会话实例。 */
	private Session session;

	/** Android 上下文。 */
	private Context androidContext;

	/** 线程池执行器。 */
	private ExecutorService executor;
	/** 当前高优先级队列是否正在写数据。 */
	private AtomicBoolean writingHP;
	/** 当前低优先级队列是否正在写数据。 */
	private AtomicBoolean writingLP;
	/** 高优先级数据写队列。 */
	private LinkedList<Message> messageQueueHP;
	/** 低优先级数据写队列。 */
	private LinkedList<Message> messageQueueLP;
	/** 写数据间隔。 */
	private long writingInterval = 10L;

	/** 接收数据清单。 */
	private ConcurrentLinkedQueue<byte[]> receivedQueue;

	/**
	 * 构造函数。
	 * 
	 * @param androidContext Android 上下文对象。
	 * @param executor 指定线程池执行器。
	 */
	public BlockingConnector(Context androidContext, ExecutorService executor) {
		this.androidContext = androidContext;
		this.executor = executor;
		this.writingHP = new AtomicBoolean(false);
		this.messageQueueHP = new LinkedList<Message>();
		this.writingLP = new AtomicBoolean(false);
		this.messageQueueLP = new LinkedList<Message>();
		this.receivedQueue = new ConcurrentLinkedQueue<byte[]>();
	}

	/**
	 * 获得已连接的地址。
	 * 
	 * @return 返回已连接的地址。
	 */
	public InetSocketAddress getAddress() {
		return (null != this.session) ? this.session.getAddress() : null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean connect(final InetSocketAddress address) {
		if (null != this.socket || this.running.get() || null == address) {
			return false;
		}

		// For Android 2.2
		System.setProperty("java.net.preferIPv6Addresses", "false");

		// 重置主动关闭
		this.activeClose = false;

		// 判断是否有网络连接
		if (!Utils.isNetworkConnected(this.androidContext)) {
			this.fireErrorOccurred(MessageErrorCode.NO_NETWORK, null);
			return false;
		}

		// 创建新 Socket
		this.socket = new Socket();

		try {
			this.socket.setTcpNoDelay(true);
			this.socket.setKeepAlive(true);
			this.socket.setSoTimeout(this.soTimeout);
			this.socket.setSendBufferSize(this.block);
			this.socket.setReceiveBufferSize(this.block);
		} catch (SocketException e) {
			Logger.log(BlockingConnector.class, e, LogLevel.WARNING);
		} catch (Exception e) {
			Logger.log(BlockingConnector.class, e, LogLevel.WARNING);
		}

		this.session = new Session(this, address);

		this.handleThread = new Thread() {
			@Override
			public void run() {
				running.set(true);

				try {
					if (null != socket) {
						socket.connect(address, (int)connTimeout);
					}
					else {
						running.set(false);
						return;
					}
				} catch (IOException e) {
					Logger.log(BlockingConnector.class, e, LogLevel.ERROR);
					fireErrorOccurred(MessageErrorCode.SOCKET_FAILED, null);
					running.set(false);
					socket = null;
					return;
				} catch (Exception e) {
					Logger.log(BlockingConnector.class, e, LogLevel.ERROR);
					running.set(false);
					socket = null;
					return;
				}

				if (Logger.isDebugLevel()) {
					Logger.d(BlockingConnector.class, this.getName());
				}

				fireSessionCreated();

				fireSessionOpened();

				try {
					loopDispatch();
				} catch (Exception e) {
					spinning = false;
					Logger.log(BlockingConnector.class, e, LogLevel.ERROR);
				}

				fireSessionClosed();

				fireSessionDestroyed();

				running.set(false);

				synchronized (receivedQueue) {
					receivedQueue.clear();
				}

				synchronized (messageQueueHP) {
					messageQueueHP.clear();
				}
				synchronized (messageQueueLP) {
					messageQueueLP.clear();
				}
				writingHP.set(false);
				writingLP.set(false);
			}
		};

		try {
			// Wifi 网络连接正常，但无法获得正确的网络路由信息时 getHostAddress() 会出错
			String addr = null;
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
				addr = address.getHostString();
			}
			else {
				addr = address.getAddress().getHostAddress();
			}
			this.handleThread.setName(new StringBuilder("BlockingConnector[").append(this.handleThread).append("]@")
					.append(addr).append(":").append(address.getPort()).toString());
		} catch (Exception e) {
			Logger.log(BlockingConnector.class, e, LogLevel.WARNING);
			this.handleThread.setName("BlockingConnector[" + this.handleThread + "]");
		}

		// 启动线程
		this.handleThread.setDaemon(true);
		this.handleThread.start();

		// 判断是否连接成功
		long duration = 0;
		while (null != this.socket && !this.socket.isConnected()) {
			try {
				Thread.sleep(10L);
			} catch (InterruptedException e) {
				// Nothing
			}

			duration += 10L;
			if (duration >= this.connTimeout) {
				Logger.w(this.getClass(), "Connect " + address.toString() + " timeout.");
				this.disconnect();
				fireErrorOccurred(MessageErrorCode.CONNECT_TIMEOUT, null);
				return false;
			}
		}

		if (null == this.socket) {
			return false;
		}

		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void disconnect() {
		this.activeClose = true;

		this.spinning = false;

		synchronized (this.messageQueueHP) {
			this.messageQueueHP.clear();
		}
		synchronized (this.messageQueueLP) {
			this.messageQueueLP.clear();
		}

		this.writingHP.set(false);
		this.writingLP.set(false);

		synchronized (this.receivedQueue) {
			this.receivedQueue.clear();
		}

		if (null != this.socket) {
			try {
				this.socket.close();
			} catch (Exception e) {
				Logger.log(BlockingConnector.class, e, LogLevel.DEBUG);
			}

			this.socket = null;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isConnected() {
		return (null != this.socket && this.socket.isConnected() && this.running.get() && this.spinning);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setConnectTimeout(long timeout) {
		this.connTimeout = (int)timeout;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setBlockSize(int size) {
		if (size < 2048) {
			return;
		}

		if (this.block == size) {
			return;
		}

		this.block = size;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Session getSession() {
		return this.session;
	}

	/**
	 * 向当前连接的会话写入消息数据。
	 * 
	 * @param message 指定消息。
	 */
	public boolean write(final Message message) {
		if (!this.isConnected()) {
			this.executor.execute(new Runnable() {
				@Override
				public void run() {
					fireErrorOccurred(MessageErrorCode.SOCKET_FAILED, message);
				}
			});

			return false;
		}

		return this.write(this.session, message);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean write(Session session, Message message) {
		return this.write(session, message, this.messageQueueHP, this.writingHP);
	}

	/**
	 * 写入数据到 Socket 。可指定消息使用的队列。
	 * 
	 * @param message 指定待写入的消息。
	 * @param queuePriority 指定使用的队列。
	 */
	public boolean write(final Message message, BlockingConnectorQueuePriority queuePriority) {
		if (null == this.session || !this.isConnected()) {
			Thread thread = new Thread() {
				@Override
				public void run() {
					fireErrorOccurred(MessageErrorCode.SOCKET_FAILED, message);
				}
			};
			thread.setName("ErrorOccurred");
			thread.start();
			return false;
		}

		if (queuePriority == BlockingConnectorQueuePriority.High) {
			return this.write(this.session, message, this.messageQueueHP, this.writingHP);
		}
		else {
			return this.write(this.session, message, this.messageQueueLP, this.writingLP);
		}
	}

	private boolean write(Session session, final Message message, final LinkedList<Message> messageQueue, final AtomicBoolean writing) {
		synchronized (this) {
			if (null == this.socket) {
				Thread thread = new Thread() {
					@Override
					public void run() {
						fireErrorOccurred(MessageErrorCode.CONNECT_FAILED, message);
					}
				};
				thread.setName("ErrorOccurred");
				thread.start();
				return false;
			}

			if (this.socket.isClosed() || !this.socket.isConnected()
				|| this.socket.isInputShutdown() || this.socket.isOutputShutdown()) {
				Thread thread = new Thread() {
					@Override
					public void run() {
						fireErrorOccurred(MessageErrorCode.SOCKET_FAILED, message);
					}
				};
				thread.setName("ErrorOccurred");
				thread.start();
				return false;
			}

			if (message.length() > this.writeLimit) {
				Thread thread = new Thread() {
					@Override
					public void run() {
						fireErrorOccurred(MessageErrorCode.WRITE_OUTOFBOUNDS, message);
					}
				};
				thread.setName("ErrorOccurred");
				thread.start();
				return false;
			}
		}

		// 判断是否进行加密
		byte[] skey = session.getSecretKey();
		if (null != skey) {
			this.encryptMessage(message, skey);
		}

		synchronized (messageQueue) {
			messageQueue.add(message);
		}

		if (!writing.get()) {
			writing.set(true);

			this.executor.execute(new Runnable() {
				@Override
				public void run() {
					flushMessage(messageQueue, writing);
				}
			});
		}

		return true;
	}

	/**
	 * 将队列里消息写入到 Socket 。
	 * 该方法会尝试启动数据管理线程将队列的所有消息依次写入 Socket 。
	 */
	private void flushMessage(final LinkedList<Message> messageQueue, final AtomicBoolean writing) {
		if (!this.isConnected()) {
			writing.set(false);
			this.executor.execute(new Runnable() {
				@Override
				public void run() {
					fireErrorOccurred(MessageErrorCode.CONNECT_FAILED, null);
				}
			});
			return;
		}

		Message message = null;

		while (this.isConnected() && writing.get()) {
			message = null;

			synchronized (messageQueue) {
				if (messageQueue.isEmpty()) {
					writing.set(false);
					break;
				}

				message = messageQueue.removeFirst();
			} // #synchronized

			if (null == message) {
				writing.set(false);
				continue;
			}

			try {
				OutputStream os = this.socket.getOutputStream();

				if (this.hasDataMark()) {
					byte[] data = message.get();
					byte[] head = this.getHeadMark();
					byte[] tail = this.getTailMark();
					byte[] pd = new byte[data.length + head.length + tail.length];
					System.arraycopy(head, 0, pd, 0, head.length);
					System.arraycopy(data, 0, pd, head.length, data.length);
					System.arraycopy(tail, 0, pd, head.length + data.length, tail.length);

					os.write(pd);
					os.flush();

					if (null != this.handler) {
						this.handler.messageSent(this.session, message);
					}
				}
				else {
					os.write(message.get());
					os.flush();

					if (null != this.handler) {
						this.handler.messageSent(this.session, message);
					}
				}

				// sleep
				Thread.sleep(this.writingInterval);
			} catch (SocketException e) {
				writing.set(false);
				Logger.log(this.getClass(), e, LogLevel.INFO);

				final Message srcMessage = message;
				this.executor.execute(new Runnable() {
					@Override
					public void run() {
						// 关闭连接
						disconnect();

						fireErrorOccurred(MessageErrorCode.WRITE_FAILED, srcMessage);
					}
				});
			} catch (IOException e) {
				writing.set(false);
				Logger.log(this.getClass(), e, LogLevel.WARNING);

				final Message srcMessage = message;
				this.executor.execute(new Runnable() {
					@Override
					public void run() {
						fireErrorOccurred(MessageErrorCode.WRITE_FAILED, srcMessage);
					}
				});
			} catch (Exception e) {
				writing.set(false);
				Logger.log(this.getClass(), e, LogLevel.ERROR);

				final Message srcMessage = message;
				this.executor.execute(new Runnable() {
					@Override
					public void run() {
						fireErrorOccurred(MessageErrorCode.WRITE_FAILED, srcMessage);
					}
				});
			}
		}

		if (!messageQueue.isEmpty()) {
			writing.set(true);
			this.executor.execute(new Runnable() {
				@Override
				public void run() {
					flushMessage(messageQueue, writing);
				}
			});
		}
	}

	/**
	 * 重置守护线程空闲间隔时间，单位：毫秒。
	 * 
	 * @param value 设置间隔。
	 */
	public void resetInterval(long value) {
		if (this.interval == value) {
			return;
		}

		this.interval = value;

		if (Logger.isDebugLevel()) {
			Logger.d(this.getClass(), "Reset interval : " + value);
		}
	}

	/**
	 * 回调 {@link MessageHandler#sessionCreated(Session)} 。
	 */
	private void fireSessionCreated() {
		if (null != this.handler) {
			this.handler.sessionCreated(this.session);
		}
	}
	/**
	 * 回调 {@link MessageHandler#sessionOpened(Session)} 。
	 */
	private void fireSessionOpened() {
		if (null != this.handler) {
			this.handler.sessionOpened(this.session);
		}
	}
	/**
	 * 回调 {@link MessageHandler#sessionClosed(Session)} 。
	 */
	private void fireSessionClosed() {
		if (null != this.handler) {
			this.handler.sessionClosed(this.session);
		}
	}
	/**
	 * 回调 {@link MessageHandler#sessionDestroyed(Session)} ，并进行数据清理。
	 */
	private void fireSessionDestroyed() {
		if (null != this.handler) {
			this.handler.sessionDestroyed(this.session);
		}

		if (null != this.socket) {
			try {
				this.socket.close();
			} catch (Exception e) {
				Logger.d(this.getClass(), "Process 'session destroyed' exception");
			}
			this.socket = null;
		}

		synchronized (this.messageQueueHP) {
			this.messageQueueHP.clear();
		}
		synchronized (this.messageQueueLP) {
			this.messageQueueLP.clear();
		}
		this.writingHP.set(false);
		this.writingLP.set(false);
	}
	/**
	 * 回调 {@link MessageHandler#errorOccurred(int, Session)} 。
	 */
	private void fireErrorOccurred(int errorCode, Message message) {
		if (this.activeClose) {
			// 主动关闭时，不回调错误
			return;
		}

		if (null != this.handler) {
			this.handler.errorOccurred(errorCode, this.session, message);
		}
	}

	/**
	 * 事件处理循环。
	 * 
	 * @throws SocketException
	 * @throws Exception
	 */
	private void loopDispatch() throws Exception {
		// 自旋
		this.spinning = true;
		final long time = System.currentTimeMillis();

		Socket socket = this.socket;

		ByteBuffer bytes = ByteBuffer.allocate(this.block);
		byte[] buf = new byte[8192];

		while (this.spinning) {
			if (null == this.socket) {
				this.spinning = false;
				break;
			}

			if (socket.isClosed()) {
				if (System.currentTimeMillis() - time < this.connTimeout) {
					try {
						Thread.sleep(100L);
					} catch (Exception e) {
						// Nothing
					}

					continue;
				}
				else {
					// 超时未连通
					this.spinning = false;
					break;
				}
			}

			if (!socket.isConnected()) {
				if (System.currentTimeMillis() - time > this.connTimeout + this.connTimeout) {
					Logger.d(this.getClass(), "socket connected is false");
					this.spinning = false;
					break;
				}

				try {
					Thread.sleep(100L);
				} catch (Exception e) {
					// Nothing
				}

				continue;
			}

			// 清空缓存
			bytes.clear();

			// 读取数据
			int length = -1;
			int total = 0;
			try {
				InputStream inputStream = socket.getInputStream();
				length = inputStream.read(buf);
				if (length > 0) {
					// 写入
					bytes.put(buf, 0, length);
					total += length;
				}

				while (inputStream.available() > 0) {
					length = inputStream.read(buf);
					if (length > 0) {
						if (total + length > bytes.capacity()) {
							// 数据超过容量，进行扩容
							int newCapacity = this.estimateCapacity(bytes.capacity(), total + length, 1024);
							ByteBuffer newBytes = ByteBuffer.allocate(newCapacity);
							// 替换
							if (bytes.position() != 0) {
								bytes.flip();
							}
							newBytes.put(bytes);
							bytes = null;
							// 新缓存
							bytes = newBytes;
						}

						// 写入
						bytes.put(buf, 0, length);
						total += length;
					}
				}
			} catch (SocketTimeoutException e) {
				// Nothing
			} catch (SocketException e) {
				spinning = false;
				Logger.i(BlockingConnector.class, "Socket closed");
				break;
			} catch (Exception e) {
				spinning = false;
				break;
			}

			if (total == 0) {
				// 超时未读取到数据
				try {
					Thread.sleep(this.interval);
				} catch (Exception e) {
					// Nothing;
				}

				continue;
			}

			// 缓存就绪
			bytes.flip();

			byte[] data = new byte[total];
			System.arraycopy(bytes.array(), 0, data, 0, total);

			this.process(data);

			data = null;
			bytes.clear();
		}

		this.spinning = false;

		bytes.clear();
		bytes = null;
		buf = null;

		Logger.i(this.getClass(), "Quit loop dispatch");
	}

	/**
	 * 评估指定容量所需要的扩容数据。
	 * 新的容量会按照步长进行线性增长。
	 * 
	 * @param currentValue 当前容量。
	 * @param minValue 期望得到的最小容量。
	 * @param step 增容步长。
	 * @return 返回计算后的新容量大小。
	 */
	private int estimateCapacity(int currentValue, int minValue, int step) {
		int newValue = currentValue + step;
		while (newValue < minValue) {
			newValue += step;
		}
		return newValue;
	}

	/**
	 * 执行数据解析操作。
	 * 
	 * @param data 指定数据数组。
	 */
	private void process(byte[] data) {
		// 根据数据标志获取数据
		if (this.hasDataMark()) {
			// 数据递归提取
			this.extract(this.receivedQueue, data);

			if (!this.receivedQueue.isEmpty()) {
				this.executor.execute(new Runnable() {
					@Override
					public void run() {
						synchronized (receivedQueue) {
							byte[] bytes = null;
							while ((bytes = receivedQueue.poll()) != null) {
								Message message = new Message(bytes);

								byte[] skey = session.getSecretKey();
								if (null != skey) {
									decryptMessage(message, skey);
								}

								if (null != handler) {
									handler.messageReceived(session, message);
								}
							}
						} // #synchronized
					}
				});
			}
		}
		else {
			final Message message = new Message(data);

			byte[] skey = this.session.getSecretKey();
			if (null != skey) {
				this.decryptMessage(message, skey);
			}

			this.executor.execute(new Runnable() {
				@Override
				public void run() {
					synchronized (receivedQueue) {
						if (null != handler) {
							handler.messageReceived(session, message);
						}
					}
				}
			});
		}
	}

	/**
	 * 进行数据提取并输出。
	 * 
	 * @param out 输出的数据列表。
	 * @param data 源数据。
	 */
	private void extract(final Queue<byte[]> output, final byte[] data) {
		final byte[] headMark = this.getHeadMark();
		final byte[] tailMark = this.getTailMark();

		byte[] real = data;
		if (this.session.cacheCursor > 0) {
			real = new byte[this.session.cacheCursor + data.length];
			System.arraycopy(this.session.cache, 0, real, 0, this.session.cacheCursor);
			System.arraycopy(data, 0, real, this.session.cacheCursor, data.length);
			// 重置缓存
			this.session.resetCache();
		}

		int index = 0;
		final int len = real.length;
		int headPos = -1;
		int tailPos = -1;
		int ret = -1;

		ret = compareBytes(headMark, 0, real, index, headMark.length);
		if (0 == ret) {
			// 有头标签
			index = headMark.length;
			// 记录数据位置头
			headPos = index;
			// 判断是否有尾标签，依次计数
			ret = -1;
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
				System.arraycopy(real, headPos, outBytes, 0, outBytes.length);
				output.add(outBytes);

				int newLen = len - tailPos - tailMark.length;
				if (newLen > 0) {
					byte[] newBytes = new byte[newLen];
					System.arraycopy(real, tailPos + tailMark.length, newBytes, 0, newLen);

					// 递归
					extract(output, newBytes);
				}
			}
			else {
				// 没有尾标签，仅进行缓存
				if (len + this.session.cacheCursor > this.session.getCacheSize()) {
					// 缓存扩容
					this.session.resetCacheSize(len + this.session.cacheCursor);
				}

				System.arraycopy(real, 0, this.session.cache, this.session.cacheCursor, len);
				this.session.cacheCursor += len;
			}
		}
		else if (-1 == ret){
			// 没有头标签
			// 尝试查找
			ret = -1;
			while (index < len) {
				if (real[index] == headMark[0]) {
					ret = compareBytes(headMark, 0, real, index, headMark.length);
					if (0 == ret) {
						// 找到头标签
						headPos = index;
						break;
					}
					else if (1 == ret) {
						// 越界
						break;
					}
					else {
						// 未找到头标签
						++index;
					}
				}
				else {
					++index;
				}
			}

			if (headPos > 0) {
				// 找到头标签
				byte[] newBytes = new byte[len - headPos];
				System.arraycopy(real, headPos, newBytes, 0, newBytes.length);

				// 递归
				extract(output, newBytes);
			}
			else {
				// 没有找到头标签，尝试判断结束位置
				byte backwardOne = real[len - 1];
				byte backwardTwo = real[len - 2];
				byte backwardThree = real[len - 3];
				int pos = -1;
				int cplen = 0;
				if (headMark[0] == backwardOne) {
					pos = len - 1;
					cplen = 1;
				}
				else if (headMark[0] == backwardTwo && headMark[1] == backwardOne) {
					pos = len - 2;
					cplen = 2;
				}
				else if (headMark[0] == backwardThree && headMark[1] == backwardTwo && headMark[2] == backwardOne) {
					pos = len - 3;
					cplen = 3;
				}

				if (pos >= 0) {
					// 有可能是数据头，进行缓存
					if (cplen + this.session.cacheCursor > this.session.getCacheSize()) {
						// 缓存扩容
						this.session.resetCacheSize(cplen + this.session.cacheCursor);
					}

					System.arraycopy(real, pos, this.session.cache, this.session.cacheCursor, cplen);
					this.session.cacheCursor += cplen;
				}
			}

			/*
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
			*/
		}
		else {
			// 数据越界，直接缓存
			if (this.session.cacheCursor + real.length > this.session.getCacheSize()) {
				// 重置 cache 大小
				this.session.resetCacheSize(this.session.cacheCursor + real.length);
			}
			System.arraycopy(real, 0, this.session.cache, this.session.cacheCursor, real.length);
			this.session.cacheCursor += real.length;
		}
	}
	/*private void extract(final LinkedList<byte[]> out, final byte[] data) {
		final byte[] headMark = this.getHeadMark();
		final byte[] tailMark = this.getTailMark();

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
	}*/

	/**
	 * 比较两个字节数组内容。
	 * 
	 * @param b1 待比较数组1
	 * @param offsetB1 待比较数组1的数据位置偏移。
	 * @param b2 待比较数组2
	 * @param offsetB2 待比较数组2的数据位置偏移。
	 * @param length 比较操作的数据长度。
	 * @return 返回 <code>0</code> 表示匹配，<code>-1</code> 表示不匹配，<code>1</code> 表示越界
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

	/**
	 * 使用密钥加密数据。
	 * 
	 * @param message 待加密的消息。
	 * @param key 加密操作使用的密钥。
	 */
	private void encryptMessage(Message message, byte[] key) {
		byte[] plaintext = message.get();
		byte[] ciphertext = Cryptology.getInstance().simpleEncrypt(plaintext, key);
		message.set(ciphertext);
	}

	/**
	 * 使用密钥解密数据。
	 * 
	 * @param message 待解密的消息。
	 * @param key 解密操作使用的密钥。
	 */
	private void decryptMessage(Message message, byte[] key) {
		byte[] ciphertext = message.get();
		byte[] plaintext = Cryptology.getInstance().simpleDecrypt(ciphertext, key);
		message.set(plaintext);
	}

}
