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

package net.cellcloud.airnfc;

import java.nio.charset.Charset;
import java.util.List;

import net.cellcloud.common.Logger;

public final class Packet {

	protected static final int MaxLength = 8;

	private byte source;
	private Channel channel;
	private byte[] body;

	protected Packet() {
	}

	protected byte getSource() {
		return this.source;
	}

	protected Channel getChannel() {
		return this.channel;
	}

	protected byte[] getBody() {
		return this.body;
	}

	protected String getBodyAsString() {
		String ret = new String(this.body, Charset.forName("UTF-8"));
		return ret;
	}

	protected static byte[] packCompeteChannel(byte source) {
		return null;
	}

	protected static byte[] packReleaseChannel(byte source) {
		return null;
	}

	/**
	 * 
	 * @param channel
	 * @param source
	 * @param text
	 * @return
	 */
	protected static byte[] packPost(byte channel, byte source, String text) {
		// 0 : channel
		// 1 : source
		// 2 : Raw Length
		// 3 - N : Text HEX
		// N + 1 : Tail Flag - '/'

		byte[] data = text.getBytes(Charset.forName("UTF-8"));

		if (data.length > MaxLength) {
			return null;
		}

		byte[] p = new byte[3 + data.length + data.length + 1];

		p[0] = channel;
		p[1] = source;
		p[2] = Packet.packLength(data.length);

		byte[] body = Packet.parse(data);
		if (null == body) {
			Logger.w(Packet.class, "Packet error: " + channel + " # " + source + " - " + text);
			return null;
		}

		for (int i = 0; i < body.length; ++i) {
			p[i + 3] = body[i];
		}

		p[p.length - 1] = '/';

		// 剔除连续重码
		byte[] output = Packet.displaceRepetition(p);
		return output;
	}

	protected static Packet unpack(List<Byte> fuzzyList) {
		if (fuzzyList.size() >= 5 && fuzzyList.get(0).byteValue() != '/') {
			byte[] raw = new byte[fuzzyList.size()];
			int i = 0;
			for (Byte b : fuzzyList) {
				raw[i] = b.byteValue();
				++i;
			}

			// 恢复重码
			byte[] data = Packet.resumeRepetition(raw);
			raw = null;

//			Logger.d(Packet.class, "Raw data: " + new String(data));

			// 解析 Raw Length
			int length = Packet.unpackLength(data[2]);
			// 判断包是否收完
			if ((length + length) <= data.length - 3) {
				// 包接收完成，清空模糊列表
				fuzzyList.clear();
				// 解包
				return unpackPost(data);
			}
			else {
				data = null;
			}
		}

		return null;
	}

	private static Packet unpackPost(byte[] data) {
		Packet ret = new Packet();

		ret.channel = Channel.parse(data[0]);
		ret.source = data[1];

		int length = data.length - 3;
		if (data[data.length - 1] == '/') {
			length -= 1;
		}

		ret.body = Packet.unparse(data, 3, length);

		return ret;
	}

	private static byte[] parse(byte[] data) {
		byte[] ret = new byte[data.length + data.length];

		int counts = 0;
		for (byte d : data) {
			int index = Packet.index(d);
			if (index == -1) {
				return null;
			}

			byte[] array = Const.PACKET_BOOK[index];
			ret[counts] = array[0];
			ret[counts + 1] = array[1];

			counts += 2;
		}

		return ret;
	}

	private static byte[] unparse(byte[] data, int offset, int length) {
		byte[] raw = new byte[length / 2];
		int rawIndex = 0;
		int index = offset;
		do {
			byte b1 = data[index];
			if (b1 == '/') {
				break;
			}

			// 下一位
			++index;
			byte b2 = data[index];
			if (b2 == '/') {
				break;
			}

			for (int i = 0, size = Const.PACKET_BOOK.length; i < size; ++i) {
				byte[] p = Const.PACKET_BOOK[i];
				if (p[0] == b1 && p[1] == b2) {
					raw[rawIndex] = Const.PACKET_BOOK_INDEX[i];
					++rawIndex;
				}
			}

			// 下一位
			++index;
		} while (index < data.length);

		return raw;
	}

	private static int index(byte b) {
		for (int i = 0; i < Const.PACKET_BOOK_INDEX.length; ++i) {
			if (Const.PACKET_BOOK_INDEX[i] == b) {
				return i;
			}
		}

		return -1;
	}

	private static byte packLength(int length) {
		switch (length) {
		case 1:
			return '1';
		case 2:
			return '2';
		case 3:
			return '3';
		case 4:
			return '4';
		case 5:
			return '5';
		case 6:
			return '6';
		case 7:
			return '7';
		case 8:
			return '8';
		case 9:
			return '9';
		default:
			return 0;
		}
	}

	private static int unpackLength(byte length) {
		switch (length) {
		case '1':
			return 1;
		case '2':
			return 2;
		case '3':
			return 3;
		case '4':
			return 4;
		case '5':
			return 5;
		case '6':
			return 6;
		case '7':
			return 7;
		case '8':
			return 8;
		case '9':
			return 9;
		default:
			return 0;
		}
	}

	private static byte[] displaceRepetition(byte[] input) {
		byte[] output = new byte[input.length];
		byte last = -1;

		for (int i = 0; i < input.length; ++i) {
			byte b = input[i];

			if (last > 0 && b == last) {
				b = '#';
				last = -1;
			}
			else {
				last = b;
			}

			output[i] = b;
		}

		return output;
	}

	private static byte[] resumeRepetition(byte[] input) {
		byte[] output = new byte[input.length];
		byte last = -1;

		for (int i = 0; i < input.length; ++i) {
			byte b = input[i];

			if (last > 0 && b == '#') {
				b = last;
				last = -1;
			}
			else {
				last = b;
			}

			output[i] = b;
		}

		return output;
	}

//	private static byte compress4bit(byte high, byte low) {
//		byte h = (byte)((high << 4) & 0xF0);
//		byte l = (byte)(low & 0x0F);
//		return (byte)(h | l);
//	}

//	private static byte[] uncompress4bit(byte longbit) {
//		byte h = (byte) ((longbit >> 4) & 0x0F);
//		byte l = (byte) (longbit & 0x0F);
//		return new byte[] { h, l };
//	}
}
