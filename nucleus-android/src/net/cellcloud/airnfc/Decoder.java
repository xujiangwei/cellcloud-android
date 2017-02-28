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

import org.jtransforms.fft.DoubleFFT_1D;

/**
 * 
 * @author Ambrose Xu
 */
public class Decoder {

	private static int DELTA_STEP = 50;

	private static double[] Window;

	protected static byte decode(short[] data, int length, int sampleRate) {
		short[] raw = new short[length];
		System.arraycopy(data, 0, raw, 0, length);

		// Extract frequency with FFT
		double frequency = extractFrequency(raw, sampleRate);

		int freq = (int) Math.round(frequency);
		int cur = -1;
		int index = 0;

		// Search code in code book
		for (int i = 1; i < Const.CODE_BOOK.length; ++i) {
			if (Const.CODE_BOOK[i - 1] < freq && Const.CODE_BOOK[i] >= freq) {
				cur = Const.CODE_BOOK[i];
				index = i;
				break;
			}
		}

		if (cur == -1) {
			return 0;
		}

		if (Math.abs(cur - freq) > DELTA_STEP) {
			cur = Const.CODE_BOOK[index - 1];
			index -= 1;
		}

		byte code = Const.CODE_BOOK_INDEX[index];
		return code;
	}

	/**
	 * Extract the dominant frequency from 16bit PCM data.
	 * @param sampleData an array containing the raw 16bit PCM data.
	 * @param sampleRate the sample rate (in HZ) of sampleData
	 * @return an approximation of the dominant frequency in sampleData
	 */
	private static double extractFrequency(short[] sampleData, int sampleRate) {
		// sampleData + zero padding
		DoubleFFT_1D fft = new DoubleFFT_1D(sampleData.length + 24 * sampleData.length);
		double[] a = new double[(sampleData.length + 24 * sampleData.length) * 2];

		System.arraycopy(applyWindow(sampleData), 0, a, 0, sampleData.length);
		fft.realForward(a);

		// find the peak magnitude and it's index
		double maxMag = Double.NEGATIVE_INFINITY;
		int maxInd = -1;

		for (int i = 0, size = a.length / 2; i < size; ++i) {
			double re  = a[2*i];
			double im  = a[2*i+1];
			double mag = Math.sqrt(re * re + im * im);

			if (mag > maxMag) {
				maxMag = mag;
				maxInd = i;
			}
		}

		// calculate the frequency
		return (double)sampleRate * maxInd / (a.length / 2);
	}

	/**
	 * Build a Hamming window filter for samples of a given size
	 * See http://www.labbookpages.co.uk/audio/firWindowing.html#windows
	 * @param size the sample size for which the filter will be created
	 */
	private static void buildHammWindow(int size) {
		if (Window != null && Window.length == size) {
			return;
		}

		Window = new double[size];
		for (int i = 0; i < size; ++i) {
			Window[i] = .54 - .46 * Math.cos(2 * Math.PI * i / (size - 1.0));
		}
	}

	/**
	 * Apply a Hamming window filter to raw input data
	 * @param input an array containing unfiltered input data
	 * @return a double array containing the filtered data
	 */
	private static double[] applyWindow(short[] input) {
		double[] res = new double[input.length];

		buildHammWindow(input.length);

		for (int i = 0; i < input.length; ++i) {
			res[i] = (double)input[i] * Window[i];
		}
		return res;
	}
}
