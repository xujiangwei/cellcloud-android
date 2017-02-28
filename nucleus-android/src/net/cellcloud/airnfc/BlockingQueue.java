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

import java.util.LinkedList;

public class BlockingQueue {

	private LinkedList<Byte> queue;

	public BlockingQueue() {
		this.queue = new LinkedList<Byte>();
	}

	protected void await(long duration) {
		synchronized (this) {
			try {
				Thread.sleep(duration);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public byte dequeue() {
		synchronized (this) {
			if (this.queue.isEmpty()) {
				try {
					this.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		synchronized (this) {
			if (this.queue.isEmpty()) {
				return 0;
			}

			Byte ret = this.queue.poll().byteValue();

			if (this.queue.isEmpty()) {
				this.notify();
			}

			return ret.byteValue();
		}
	}

	public void enqueue(byte data) {
		synchronized (this) {
			if (!this.queue.isEmpty()) {
				try {
					this.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			// 添加新数据
			this.queue.offer(data);

			this.notify();
		}
	}

	public void reset() {
		synchronized (this) {
			this.notify();
		}
	}

	public void clear() {
		synchronized (this) {
			this.queue.clear();

			this.notify();
		}
	}
}
