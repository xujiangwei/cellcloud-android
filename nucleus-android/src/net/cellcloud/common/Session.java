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

import net.cellcloud.util.Utils;

/**
 * 消息会话描述类。
 * 
 * @author Ambrose Xu
 * 
 */
public class Session {

	private Long id;
	private MessageService service;
	private InetSocketAddress address;

	private byte[] secretKey;

	protected byte[] cache;
	protected int cacheCursor;

	public Session(MessageService service, InetSocketAddress address) {
		this.id = Math.abs(Utils.randomLong());
		this.service = service;
		this.address = address;
		this.secretKey = null;

		this.cache = new byte[2048];
		this.cacheCursor = 0;
	}

	public Session(long id, MessageService service, InetSocketAddress address) {
		this.id = id;
		this.service = service;
		this.address = address;
		this.secretKey = null;

		this.cache = new byte[2048];
		this.cacheCursor = 0;
	}

	/**
	 * 返回会话 ID 。
	 */
	public Long getId() {
		return this.id;
	}

	/**
	 * 返回消息服务实例。
	 */
	public MessageService getService() {
		return this.service;
	}

	/**
	 * 返回会话的网络地址。
	 */
	public InetSocketAddress getAddress() {
		return this.address;
	}

	/**
	 * 返回是否是安全连接。
	 * @return
	 */
	public boolean isSecure() {
		return (null != this.secretKey);
	}

	/**
	 * 激活密钥。
	 * @param key
	 * @return
	 */
	public boolean activeSecretKey(byte[] key) {
		if (null == key) {
			this.secretKey = null;
			return false;
		}

		if (key.length < 8) {
			return false;
		}

		this.secretKey = new byte[8];
		System.arraycopy(key, 0, this.secretKey, 0, 8);
		return true;
	}

	/**
	 * 吊销密钥。
	 */
	public void deactiveSecretKey() {
		this.secretKey = null;
	}

	/**
	 * 返回安全密钥。
	 * @return
	 */
	public byte[] getSecretKey() {
		return this.secretKey;
	}

	/** 向该会话写消息。
	 */
	public void write(Message message) {
		this.service.write(this, message);
	}

	@Override
	public boolean equals(Object obj) {
		if (null != obj && obj instanceof Session) {
			Session other = (Session) obj;
			return this.id.longValue() == other.id.longValue();
		}

		return false;
	}

	@Override
	public int hashCode() {
		return this.id.intValue();
	}

	protected int getCacheSize() {
		return this.cache.length;
	}

	protected void resetCacheSize(int newSize) {
		if (newSize <= this.cache.length) {
			return;
		}

		if (this.cacheCursor > 0) {
			byte[] cur = new byte[this.cacheCursor];
			System.arraycopy(this.cache, 0, cur, 0, this.cacheCursor);
			this.cache = new byte[newSize];
			System.arraycopy(cur, 0, this.cache, 0, this.cacheCursor);
			cur = null;
		}
		else {
			this.cache = new byte[newSize];
		}
	}

}
