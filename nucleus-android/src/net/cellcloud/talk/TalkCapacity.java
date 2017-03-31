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

package net.cellcloud.talk;

import java.nio.charset.Charset;

import net.cellcloud.Version;

/** 会话能力描述类。
 * 
 * @author Jiangwei Xu
 */
public final class TalkCapacity {

	/// 版本
	private int version = 2;

	/// 是否为加密会话
	protected boolean secure = false;

	/// 重复尝试连接的次数
	protected int retry = 0;
	/// 两次连接中间隔时间，单位毫秒
	protected long retryDelay = 5000L;

	/// 连接超时时间
	protected long connectTimeout = 10000L;

	/// 是否是阻塞方式
	protected boolean blocking = true;

	/** 内核的版本串号。 */
	private int versionNumber = Version.VERSION_NUMBER;

	public TalkCapacity() {
	}

	/**
	 * 构造函数。
	 * @param retry
	 * @param retryDelay
	 */
	public TalkCapacity(int retry, long retryDelay) {
		this(false, retry, retryDelay);
	}

	/**
	 * 构造函数。
	 * @param secure
	 * @param retryAttempts
	 * @param retryDelay
	 */
	public TalkCapacity(boolean secure, int retry, long retryDelay) {
		this.secure = secure;
		this.retry = retry;
		this.retryDelay = retryDelay;

		if (this.retry == Integer.MAX_VALUE) {
			this.retry -= 1;
		}
	}

	public void setConnectTimeout(long connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public void setBlocking(boolean blocking) {
		this.blocking = blocking;
	}

	protected void resetVersion(int version) {
		this.version = version;

		if (version == 1) {
			this.versionNumber = 130;
		}
		else if (version == 2 || version == 3) {
			this.versionNumber = Version.VERSION_NUMBER;
		}
	}

	public final static byte[] serialize(TalkCapacity capacity) {
		StringBuilder buf = new StringBuilder();
		if (capacity.version == 1) {
			buf.append(1);
			buf.append("|");
			buf.append(capacity.secure ? "Y" : "N");
			buf.append("|");
			buf.append(capacity.retry);
			buf.append("|");
			buf.append(capacity.retryDelay);
		}
		else if (capacity.version == 2) {
			buf.append(2);
			buf.append("|");
			buf.append(capacity.secure ? "Y" : "N");
			buf.append("|");
			buf.append(capacity.retry);
			buf.append("|");
			buf.append(capacity.retryDelay);
			buf.append("|");
			buf.append(capacity.versionNumber);
		}

		byte[] bytes = buf.toString().getBytes();
		buf = null;

		return bytes;
	}

	public final static TalkCapacity deserialize(byte[] bytes) {
		String str = new String(bytes, Charset.forName("UTF-8"));
		String[] array = str.split("\\|");
		if (array.length < 4) {
			return null;
		}

		TalkCapacity cap = new TalkCapacity();
		cap.version = Integer.parseInt(array[0]);
		if (cap.version == 1) {
			cap.secure = array[1].equalsIgnoreCase("Y") ? true : false;
			cap.retry = Integer.parseInt(array[2]);
			cap.retryDelay = Integer.parseInt(array[3]);
		}
		else if (cap.version == 2) {
			cap.secure = array[1].equalsIgnoreCase("Y") ? true : false;
			cap.retry = Integer.parseInt(array[2]);
			cap.retryDelay = Integer.parseInt(array[3]);
			cap.versionNumber = Integer.parseInt(array[4]);
		}
		return cap;
	}
}
