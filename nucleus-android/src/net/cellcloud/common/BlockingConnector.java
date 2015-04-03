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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;

import net.cellcloud.util.Utils;
import android.content.Context;

/** 阻塞式网络连接器。
 * 
 * @author Jiangwei Xu
 *
 */
public class BlockingConnector extends MessageService implements MessageConnector {

	// 缓冲块大小
	private int block = 8192;

	// 超时时间
	private long timeout = 15000;

	private Socket socket;

	private Thread handleThread;
	private boolean spinning = false;
	private boolean running = false;

	private ByteBuffer readBuffer;
	private ByteBuffer writeBuffer;

	private Session session;

	private Context androidContext;

	public BlockingConnector(Context androidContext) {
		this.androidContext = androidContext;
		this.socket = new Socket();
	}

	/**
	 * 返回已连接的对端地址。
	 * @return
	 */
	public InetSocketAddress getAddress() {
		return (InetSocketAddress) this.socket.getRemoteSocketAddress();
	}

	@Override
	public boolean connect(final InetSocketAddress address) {
		if (this.socket.isConnected()) {
			Logger.w(BlockingConnector.class, "Connector has connected to " + address.getAddress().getHostAddress());
			return false;
		}

		// TODO 完善逻辑
		if (this.running) {
			this.spinning = false;
		}

		// For Android 2.2
		System.setProperty("java.net.preferIPv6Addresses", "false");

		// 判断是否有网络连接
		if (!Utils.isNetworkConnected(this.androidContext)) {
			this.fireErrorOccurred(MessageErrorCode.NO_NETWORK);
			return false;
		}
//		if (!Utils.isWifiConnected(this.androidContext)) {
//			if (!Utils.isMobileConnected(this.androidContext)) {
//				this.fireErrorOccurred(MessageErrorCode.NO_NETWORK);
//				return false;
//			}
//		}

		try {
			this.socket.setTcpNoDelay(true);
			this.socket.setKeepAlive(true);
			this.socket.setSendBufferSize(this.block);
			this.socket.setReceiveBufferSize(this.block);
		} catch (SocketException e) {
			Logger.log(BlockingConnector.class, e, LogLevel.WARNING);
		}

		// 初始化 Buffer
		if (null == this.readBuffer)
			this.readBuffer = ByteBuffer.allocate(this.block);
		else
			this.readBuffer.reset();

		if (null == this.writeBuffer)
			this.writeBuffer = ByteBuffer.allocate(this.block);
		else
			this.writeBuffer.reset();

		this.session = new Session(this, address);

		try {
			this.socket.connect(address, (int)this.timeout);
		} catch (IOException e) {
			Logger.log(BlockingConnector.class, e, LogLevel.ERROR);
		}

		if (!this.socket.isConnected()) {
			try {
				this.socket.close();
			} catch (IOException e) {
				// Nothing
			}
			this.fireErrorOccurred(MessageErrorCode.CONNECT_TIMEOUT);
			return false;
		}

		this.handleThread = new Thread() {
			@Override
			public void run() {
				running = true;

				if (Logger.isDebugLevel()) {
					Logger.d(BlockingConnector.class, this.getName());
				}

				fireSessionCreated();

				fireSessionOpened();

				try {
					loopDispatch();
				} catch (Exception e) {
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
	}

	@Override
	public boolean isConnected() {
		return this.socket.isConnected();
	}

	@Override
	public void setConnectTimeout(long timeout) {
		this.timeout = timeout;
	}

	@Override
	public void setBlockSize(int size) {
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
	}

	@Override
	public void read(Message message, Session session) {
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

		byte[] buf = new byte[this.block];

		while (this.spinning) {
			if (!this.socket.isConnected()) {
				Thread.sleep(100);
				continue;
			}

			InputStream in = this.socket.getInputStream();
			int len = 0;
			while ((len = in.read(buf)) > 0) {
				this.readBuffer.put(buf, 0, len);
			}

			this.readBuffer.flip();

			byte[] data = new byte[this.readBuffer.limit()];
			this.readBuffer.get(data);

			this.process(data);

			this.readBuffer.clear();

			data = null;
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
