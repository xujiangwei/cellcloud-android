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

package net.cellcloud.core;


/**
 * 内核参数配置描述。
 * 
 * @author Ambrose Xu
 * 
 */
public final class NucleusConfig {

	/// 自定义内核标签
	public String tag = null;

	/// 角色
	public Role role = Role.CONSUMER;

	/// 设备
	public Device device = Device.MOBILE;

	/// Talk Service 配置
	public TalkConfig talk;

	public NucleusConfig() {
		this.talk = new TalkConfig();
	}

	/**
	 * 会话服务器配置项。
	 */
	public final class TalkConfig {
		/// 是否启用 Talk 服务
		public boolean enable = true;

		/// Talk 服务端口
		public int port = 7000;

		/// Block 设置
		public int block = 32768;

		private TalkConfig() {
		}
	}

}
