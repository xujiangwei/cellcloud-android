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

import java.util.concurrent.atomic.AtomicBoolean;

import net.cellcloud.common.Logger;

import android.media.AudioRecord;
import android.media.MediaRecorder;

public class Recorder {

	private int sampleRate;
	private int channel;
	private int audioEncoding;
	private int bufferSize;

	private AtomicBoolean started;

	private boolean spinning;

	private RecorderListener listener;

	private Buffer buffer;

	public Recorder(int sampleRate, int channel, int audioEncoding, int bufferSize, RecorderListener listener) {
		this.sampleRate = sampleRate;
		this.channel = channel;
		this.audioEncoding = audioEncoding;
		this.bufferSize = bufferSize;

		this.started = new AtomicBoolean(false);
		this.spinning = true;

		this.listener = listener;

		this.buffer = new Buffer(bufferSize * 2);
	}

	public void start() {
		if (this.started.get()) {
			return;
		}

		this.started.set(true);

		AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.MIC, this.sampleRate, this.channel, this.audioEncoding, this.bufferSize);

		// 启动录制
		record.startRecording();

		Logger.d(this.getClass(), "Record startd");

		this.listener.onStarted(this);

		while (this.spinning) {
			short[] data = new short[this.bufferSize];
			int bufferReadResult = record.read(data, 0, this.bufferSize);

			if (bufferReadResult > 0) {
				if (this.buffer.checkFull(bufferReadResult)) {
					// 缓存区数据记录完毕
					this.listener.onRecording(this.buffer);

					// 创建新的缓存
					int maxSize = this.buffer.getMaxSize();
					this.buffer = null;
					this.buffer = new Buffer(maxSize);
					this.buffer.append(data, bufferReadResult);
				}
				else {
					this.buffer.append(data, bufferReadResult);
				}
			}
			else {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		record.stop();
		record.release();

		this.started.set(false);
		this.spinning = false;

		Logger.d(this.getClass(), "Record stopped");

		this.listener.onStopped(this);
	}

	public void stop() {
		this.spinning = false;
	}

	public boolean isStarted() {
		return this.started.get();
	}
}
