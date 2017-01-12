/*
-----------------------------------------------------------------------------
This source file is part of Cell Cloud.

Copyright (c) 2009-2014 Cell Cloud Team (www.cellcloud.net)

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import net.cellcloud.util.Utils;
import android.content.Context;

/** 阻塞式网络连接器。
 * 
 * @author Jiangwei Xu
 *
 */
public class BlockingConnector extends MessageService implements MessageConnector {

	// 缓冲块大小
	private int block = 65536;
	private final int writeLimit = 16384;

	// 超时时间
	private int soTimeout = 3000;
	private long connTimeout = 15000L;

	private long timerInterval = 1000L;

	private Socket socket = null;

	private Thread handleThread;
	private boolean spinning = false;
	private boolean running = false;
	private boolean activeClose = false;

	private Session session;

	private Context androidContext;

	private ExecutorService executor;
	private AtomicBoolean writing;
	private LinkedList<Message> messageQueue;

	public BlockingConnector(Context androidContext, ExecutorService executor) {
		this.androidContext = androidContext;
		this.executor = executor;
		this.writing = new AtomicBoolean(false);
		this.messageQueue = new LinkedList<Message>();
	}

	/**
	 * 返回已连接的对端地址。
	 * @return
	 */
	public InetSocketAddress getAddress() {
		return (null != this.session) ? this.session.getAddress() : null;
	}

	@Override
	public boolean connect(final InetSocketAddress address) {
		if (null != this.socket || this.running) {
			return false;
		}

//		if (this.socket.isConnected()) {
//			Logger.w(BlockingConnector.class, "Connector has connected to " + address.getAddress().getHostAddress());
//			return false;
//		}

		// For Android 2.2
		System.setProperty("java.net.preferIPv6Addresses", "false");

		// 重置主动关闭
		this.activeClose = false;

		// 判断是否有网络连接
		if (!Utils.isNetworkConnected(this.androidContext)) {
			this.fireErrorOccurred(MessageErrorCode.NO_NETWORK);
			return false;
		}

		// 创建新 Socket
		this.socket = new Socket();

		try {
			this.socket.setTcpNoDelay(true);
			this.socket.setKeepAlive(true);
			this.socket.setSoTimeout(this.soTimeout);
//			this.socket.setSendBufferSize(this.block);
//			this.socket.setReceiveBufferSize(this.block);
		} catch (SocketException e) {
			Logger.log(BlockingConnector.class, e, LogLevel.WARNING);
		}

		this.session = new Session(this, address);

		this.handleThread = new Thread() {
			@Override
			public void run() {
				running = true;

				try {
					socket.connect(address, (int)connTimeout);
				} catch (IOException e) {
					Logger.log(BlockingConnector.class, e, LogLevel.ERROR);
					fireErrorOccurred(MessageErrorCode.SOCKET_FAILED);
					running = false;
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
				} catch (SocketException e) {
					spinning = false;
					Logger.i(BlockingConnector.class, "Socket closed");
				} catch (Exception e) {
					spinning = false;
					Logger.log(BlockingConnector.class, e, LogLevel.ERROR);
				}

				fireSessionClosed();

				fireSessionDestroyed();

				running = false;
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
				fireErrorOccurred(MessageErrorCode.CONNECT_TIMEOUT);
				this.disconnect();
				return false;
			}
		}

		if (null == this.socket) {
			return false;
		}

		return true;
	}

	@Override
	public void disconnect() {
		this.activeClose = true;

		this.spinning = false;

		synchronized (this.messageQueue) {
			this.messageQueue.clear();
		}

		if (null != this.socket) {
			try {
				this.socket.close();
			} catch (IOException e) {
				Logger.log(BlockingConnector.class, e, LogLevel.DEBUG);
			}

			this.socket = null;
		}
	}

	@Override
	public boolean isConnected() {
		return (null != this.socket && this.socket.isConnected());
	}

	@Override
	public void setConnectTimeout(long timeout) {
		this.connTimeout = (int)timeout;
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
	}

	@Override
	public Session getSession() {
		return this.session;
	}

	public void write(Message message) {
		if (null == this.session) {
			return;
		}

		this.write(this.session, message);
	}

	@Override
	public void write(Session session, Message message) {
		if (null == this.socket) {
			return;
		}

		if (this.socket.isClosed() || !this.socket.isConnected()) {
			this.fireErrorOccurred(MessageErrorCode.SOCKET_FAILED);
			return;
		}

		if (message.length() > this.writeLimit) {
			this.fireErrorOccurred(MessageErrorCode.WRITE_OUTOFBOUNDS);
			return;
		}

		// 判断是否进行加密
		byte[] skey = session.getSecretKey();
		if (null != skey) {
			this.encryptMessage(message, skey);
		}

		synchronized (this.messageQueue) {
			this.messageQueue.add(message);
		}

		if (!this.writing.get()) {
			this.writing.set(true);

			this.executor.execute(new Runnable() {
				@Override
				public void run() {
					flushMessage();
				}
			});
		}
	}

	private void flushMessage() {
		if (null == this.socket) {
			this.writing.set(false);
			return;
		}

		Message message = null;
		synchronized (this.messageQueue) {
			if (this.messageQueue.isEmpty()) {
				this.writing.set(false);
				return;
			}

			message = this.messageQueue.removeFirst();
		}

		try {
			OutputStream os = this.socket.getOutputStream();

			if (this.existDataMark()) {
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
		} catch (IOException e) {
			this.fireErrorOccurred(MessageErrorCode.WRITE_FAILED);
		}

		synchronized (this.messageQueue) {
			if (this.messageQueue.isEmpty()) {
				this.writing.set(false);
				return;
			}
		}

		this.writing.set(true);
		this.executor.execute(new Runnable() {
			@Override
			public void run() {
				flushMessage();
			}
		});
	}

	public void resetInterval(long value) {
		if (this.timerInterval == value) {
			return;
		}

		this.timerInterval = value;
	}

	private void fireSessionCreated() {
		if (null != this.handler) {
			this.handler.sessionCreated(this.session);
		}
	}
	private void fireSessionOpened() {
		if (null != this.handler) {
			this.handler.sessionOpened(this.session);
		}
	}
	private void fireSessionClosed() {
		if (null != this.handler) {
			this.handler.sessionClosed(this.session);
		}
	}
	private void fireSessionDestroyed() {
		if (null != this.handler) {
			this.handler.sessionDestroyed(this.session);
		}

		if (null != this.socket) {
			this.socket = null;
		}
	}
	private void fireErrorOccurred(int errorCode) {
		if (this.activeClose) {
			// 主动关闭时，不回调错误
			return;
		}

		if (null != this.handler) {
			this.handler.errorOccurred(errorCode, this.session);
		}
	}

	/** 事件循环。 */
	private void loopDispatch() throws SocketException, Exception {
		// 自旋
		this.spinning = true;
		final long time = System.currentTimeMillis();

		InputStream inputStream = this.socket.getInputStream();

		while (this.spinning) {
			if (null == this.socket) {
				this.spinning = false;
				break;
			}

			if (this.socket.isClosed()) {
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

			if (!this.socket.isConnected()) {
				try {
					Thread.sleep(100L);
				} catch (Exception e) {
					// Nothing
				}

				continue;
			}

			ByteBuffer bytes = ByteBuffer.allocate(this.block);
			byte[] buf = new byte[8192];

			// 读取数据
			int length = -1;
			int total = 0;
			try {
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
							int newCapacity = this.estimateCapacity(bytes.capacity(), total + length, Math.round(this.block * 0.5f));
							ByteBuffer newBytes = ByteBuffer.allocate(newCapacity);
							// 替换
							if (bytes.position() != 0) {
								bytes.flip();
							}
							newBytes.put(bytes);
							bytes.clear();
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
			}

			if (total == 0) {
				// 超时未读取到数据
				bytes = null;
				buf = null;

				try {
					Thread.sleep(this.timerInterval);
				} catch (Exception e) {
					// Nothing;
				}

				Thread.yield();

				continue;
			}

			// 缓存就绪
			bytes.flip();

			byte[] data = new byte[total];
			System.arraycopy(bytes.array(), 0, data, 0, total);

			this.process(data);

			bytes.clear();
			bytes = null;
			data = null;
			buf = null;
		}

		this.spinning = false;
	}

	private int estimateCapacity(int currentValue, int minValue, int step) {
		int newValue = currentValue + step;
		while (newValue < minValue) {
			newValue += step;
		}
		return newValue;
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
	 * 数据提取并输出。
	 */
	private void extract(final LinkedList<byte[]> out, final byte[] data) {
		final byte[] headMark = this.getHeadMark();
		final byte[] tailMark = this.getTailMark();

		// 当数据小于标签长度时直接缓存
		/*if (data.length < headMark.length) {
			if (this.session.cacheCursor + data.length > this.session.getCacheSize()) {
				// 重置 cache 大小
				this.session.resetCacheSize(this.session.cacheCursor + data.length);
			}
			System.arraycopy(data, 0, this.session.cache, this.session.cacheCursor, data.length);
			this.session.cacheCursor += data.length;
			return;
		}*/

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
