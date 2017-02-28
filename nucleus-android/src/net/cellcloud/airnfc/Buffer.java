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

public class Buffer {

	private short[] data;

	private int maxSize;
	private int filledSize;

	public Buffer(int maxSize) {
		this.data = new short[maxSize];
		this.maxSize = maxSize;
		this.filledSize = 0;
	}

	public int getMaxSize() {
		return this.maxSize;
	}

	public int getFilledSize() {
		return this.filledSize;
	}

	public boolean checkFull(int appendSize) {
		return (this.filledSize + appendSize > this.maxSize);
	}

	public int append(short[] data, int length) {
		if (this.filledSize >= this.maxSize) {
			return -1;
		}

		System.arraycopy(data, 0, this.data, this.filledSize, length);
		this.filledSize += length;
		return length;
	}

	public short[] getData() {
		return this.data;
	}

	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder("Data ");
		buf.append("filled/max : ");
		buf.append(this.filledSize);
		buf.append("/");
		buf.append(this.maxSize);
		return buf.toString();
	}
}
