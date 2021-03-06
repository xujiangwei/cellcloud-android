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

package net.cellcloud.talk;

/**
 * 会话故障码。
 * 
 * @author Ambrose Xu
 * 
 */
public enum TalkFailureCode {

	/**
	 * 未找到指定的 Cellet 。
	 * 此错误不触发自动重连。
	 */
	NOT_FOUND(1000),

	/**
	 * Call 请求失败。
	 */
	CALL_FAILED(1100),

	/**
	 * 会话连接被断开。
	 */
	TALK_LOST(2000),

	/**
	 * 会话网络断开。
	 */
	NETWORK_NOT_AVAILABLE(2100),

	/**
	 * 数据异常。
	 * 此错误不触发自动重连。
	 */
	INCORRECT_DATA(4000),

	/**
	 * 重试次数达到上限，重试结束。
	 * 此错误不触发自动重连。
	 */
	RETRY_END(4100),

	/**
	 * 无可用网络。
	 * 此错误不触发自动重连。
	 */
	NO_NETWORK(4200);

	/** 错误码。 */
	private int code;

	/**
	 * 构造函数。
	 * 
	 * @param code 指定错误码。
	 */
	private TalkFailureCode(int code) {
		this.code = code;
	}

	/**
	 * 获得此错误的错误码。
	 * 
	 * @return 返回错误码。
	 */
	public int getCode() {
		return this.code;
	}

}
