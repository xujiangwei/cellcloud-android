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
import java.util.LinkedList;

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

	// 超时时间
	private int soTimeout = 3000;
	private long connTimeout = 15000;

	private long timerInterval = 1000;

	private Socket socket = null;

	private Thread handleThread;
	private boolean spinning = false;
	private boolean running = false;

	private InputStream inputStream;

	private Session session;

	private Context androidContext;

	public BlockingConnector(Context androidContext) {
		this.androidContext = androidContext;
	}

	/**
	 * 返回已连接的对端地址。
	 * @return
	 */
	public InetSocketAddress getAddress() {
		return (null != this.session) ? this.session.getAddress() : null;//(InetSocketAddress) this.socket.getRemoteSocketAddress();
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
			this.socket.setSendBufferSize(this.block);
			this.socket.setReceiveBufferSize(this.block);
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

					inputStream = socket.getInputStream();
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
				} catch (Exception e) {
					spinning = false;
					Logger.log(BlockingConnector.class, e, LogLevel.ERROR);
				}

				fireSessionClosed();

				fireSessionDestroyed();

				running = false;
			}
		};
		this.handleThread.setName(new StringBuilder("BlockingConnector[").append(this.handleThread).append("]@")
				.append(address.getAddress().getHostAddress()).append(":").append(address.getPort()).toString());
		this.handleThread.start();

		return true;
	}

	@Override
	public void disconnect() {
		this.spinning = false;

		if (null != this.socket) {
			try {
				this.socket.close();
			} catch (IOException e) {
				Logger.log(BlockingConnector.class, e, LogLevel.ERROR);
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
		this.connTimeout = timeout;
	}

	@Override
	public void setBlockSize(int size) {
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

		// 判断是否进行加密
		byte[] skey = session.getSecretKey();
		if (null != skey) {
			this.encryptMessage(message, skey);
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
	}

	@Override
	public void read(Message message, Session session) {
		// Nothing
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
		if (null != this.handler) {
			this.handler.errorOccurred(errorCode, this.session);
		}
	}

	/** 事件循环。 */
	private void loopDispatch() throws Exception {
		// 自旋
		this.spinning = true;
		long time = System.currentTimeMillis();

		while (this.spinning) {
			if (null == this.socket || this.socket.isClosed()) {
				break;
			}

			if (!this.socket.isConnected()) {
				if (System.currentTimeMillis() - time > this.connTimeout) {
					break;
				}
				else {
					Thread.sleep(100);
					continue;
				}
			}

			byte[] buf = new byte[this.block];

			// 读取数据
			int length = -1;
			try {
				time = System.currentTimeMillis();
				length = inputStream.read(buf);
			} catch (SocketTimeoutException e) {
				// Nothing
			}

			if (length <= 0) {
				buf = null;

				if (System.currentTimeMillis() - time <= 500) {
					break;
				}

				try {
					Thread.sleep(this.timerInterval);
				} catch (Exception e) {
					// Nothing;
				}

				Thread.yield();

				continue;
			}

			byte[] data = new byte[length];
			System.arraycopy(buf, 0, data, 0, length);

			this.process(data);

			data = null;
			buf = null;
		}

		this.spinning = false;
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
		if (data.length < headMark.length) {
			System.arraycopy(data, 0, this.session.cache, this.session.cacheCursor, data.length);
			this.session.cacheCursor += data.length;
			return;
		}

		byte[] real = data;
		if (this.session.cacheCursor > 0) {
			real = new byte[this.session.cacheCursor + data.length];
			System.arraycopy(this.session.cache, 0, real, 0, this.session.cacheCursor);
			System.arraycopy(data, 0, real, this.session.cacheCursor, data.length);
			this.session.cacheCursor = 0;
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
					extract(out, newBytes);
				}
			}
			else {
				// 没有尾标签
				// 仅进行缓存
				if (len + this.session.cacheCursor > this.session.cacheSize) {
					// 缓存扩容
					this.session.resetCacheSize(len + this.session.cacheCursor);
				}

				System.arraycopy(real, 0, this.session.cache, this.session.cacheCursor, len);
				this.session.cacheCursor += len;
			}

			return;
		}

		byte[] newBytes = new byte[len - headMark.length];
		System.arraycopy(real, headMark.length, newBytes, 0, newBytes.length);
		extract(out, newBytes);
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
