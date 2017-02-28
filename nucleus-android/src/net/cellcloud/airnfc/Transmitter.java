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

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class Transmitter {

	private AtomicBoolean started;
	private boolean spinning;

	private Thread thread;

	private AudioTrack audioTrack;

	private int sampleRate;
	private BlockingQueue blockingQueue;

	private TransmitterListener listener;

	public Transmitter(TransmitterListener listener, BlockingQueue blockingQueue) {
		this.started = new AtomicBoolean(false);
		this.sampleRate = Const.DEFAULT_SAMPLE_RATE;
		this.listener = listener;
		this.blockingQueue = blockingQueue;
	}

	public void start() {
		if (this.started.get()) {
			return;
		}

		this.started.set(true);

		int bufferSize = AudioTrack.getMinBufferSize(this.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
		// 设置缓存大小
		bufferSize += bufferSize;

		this.audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
				this.sampleRate,
				AudioFormat.CHANNEL_OUT_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				bufferSize,
				AudioTrack.MODE_STREAM);

		this.audioTrack.play();

		this.thread = new Thread(new Runnable() {
			@Override
			public void run() {
				Logger.d(Transmitter.class, "Transmitter startd");

				listener.onStarted(Transmitter.this);

				spinning = true;

				while (spinning) {
					// 出队
					byte c = blockingQueue.dequeue();
					if (0 == c) {
						try {
							Thread.sleep(10);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

						continue;
					}

					if (Logger.isDebugLevel()) {
						Logger.d(Transmitter.class, "Transmitter encode : " + c);
					}

					// 数据编码
					short[] data = Encoder.encode(c, sampleRate);
					audioTrack.write(data, 0, data.length);
					audioTrack.flush();

					listener.onTransmitted(Transmitter.this, c);

					blockingQueue.await(0);
				}

				spinning = false;
				started.set(false);

				Logger.d(Transmitter.class, "Transmitter stopped");

				listener.onStopped(Transmitter.this);
			}
		});
		this.thread.start();
	}

	public void stop() {
		this.spinning = false;

		this.blockingQueue.reset();

		if (null != this.audioTrack) {
			this.audioTrack.stop();
			this.audioTrack.release();
			this.audioTrack = null;
		}
	}

	public boolean isStarted() {
		return this.started.get();
	}
}
