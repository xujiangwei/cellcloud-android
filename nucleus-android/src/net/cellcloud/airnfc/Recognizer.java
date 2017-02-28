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
import android.media.AudioFormat;
import android.media.AudioRecord;

public class Recognizer implements RecorderListener {

	private int sampleRate;
	private Recorder recorder;

	private Thread thread;

	private RecognizerListener listener;

	public Recognizer(RecognizerListener listener) {
		this.listener = listener;
		this.sampleRate = Const.DEFAULT_SAMPLE_RATE;
	}

	public void start() {
		if (null != this.recorder && this.recorder.isStarted()) {
			return;
		}

		int bufferSize = AudioRecord.getMinBufferSize(this.sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

		this.recorder = new Recorder(this.sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, this);

		this.thread = new Thread(new Runnable() {
			@Override
			public void run() {
				recorder.start();
			}
		});
		this.thread.start();
	}

	public void stop() {
		if (null != this.recorder) {
			this.recorder.stop();
			this.recorder = null;
		}

		this.thread = null;
	}

	@Override
	public void onStarted(Recorder recorder) {
		this.listener.onStarted(this);
	}

	@Override
	public void onStopped(Recorder recorder) {
		this.listener.onStopped(this);
	}

	@Override
	public void onRecording(Buffer buffer) {
		byte code = Decoder.decode(buffer.getData(), buffer.getFilledSize(), this.sampleRate);
		if (code > 0) {
			if (Logger.isDebugLevel()) {
				Logger.i(this.getClass(), "decode code: " + code);
			}

			this.listener.onRecognized(this, code);
		}
	}

	/*
	@Override
	public void onRecording(Data data) {
		int length = this.up2int(data.getFilledSize());
		short[] buf = new short[length];
		for (int i = 0; i < buf.length; ++i) {
			buf[i] = data.data[i];
		}

		Complex[] complexs = new Complex[length];
		for (int i = 0; i < length; ++i) {
			Short x = buf[i];
			complexs[i] = new Complex(x.doubleValue());
		}
		// 傅里叶变换
		FFT.fft(complexs, length);

		// find the peak magnitude and it's index
		double maxMag = Double.NEGATIVE_INFINITY;
		int maxInd = -1;
		for (int i = 0, size = complexs.length; i < size; ++i) {
			double re  = complexs[i].real;
			double im  = complexs[i].image;
			double mag = Math.sqrt(re * re + im * im);

			if (mag > maxMag) {
				maxMag = mag;
				maxInd = i;
			}
		}

		// calculate the frequency
		double frequency = (double)this.sampleRate * maxInd / (complexs.length);

		Logger.i(this.getClass(), "frequency:" + frequency);

		byte code = Decoder.decode(frequency);
		if (code > 0) {
			Logger.i(this.getClass(), "decode code: " + code);
			this.listener.onRecognized(code);
		}
	}

	private int up2int(int iint) {
		int ret = 1;
		while (ret <= iint) {
		    ret = ret << 1;
		}
		return ret >> 1;
	}*/
}
