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

import net.cellcloud.common.Logger;

public class Encoder {

	protected static final short AMP = Short.MAX_VALUE;

	private static final double PI2 = 2.0f * Math.PI;

	protected static short[] encode(byte data, int sampleRate) {
		int index = -1;
		for (int i = 0, size = Const.CODE_BOOK_INDEX.length; i < size; ++i) {
			if (Const.CODE_BOOK_INDEX[i] == data) {
				index = i;
				break;
			}
		}

		if (index == -1) {
			Logger.e(Encoder.class, "Code book index overflow");
			return null;
		}

		int frequency = Const.CODE_BOOK[index];
		return Encoder.generate(frequency, sampleRate);
	}

	private static short[] generate(int frequency, int sampleRateInHz) {
		int offset = 0;
		double rate = sampleRateInHz + 0.0f;

		double x = PI2 * (frequency + 0.0f) / rate;

		short[] wave = new short[sampleRateInHz];

		// 生成正玄波
		for (int i = 0; i < sampleRateInHz; ++i) {
			wave[i] = (short) (AMP * Math.sin(x * (i + offset)));
		}

		return wave;
	}
}
