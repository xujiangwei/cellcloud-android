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

import net.cellcloud.common.Logger;

public class AirReceiver implements RecognizerListener {

	private Recognizer recognizer;

	private byte lastByte;

	private LinkedList<Byte> data;

	private Channel channel;

	private AirReceiverListener listener;

	public AirReceiver(Channel channel, AirReceiverListener listener) {
		this.channel = channel;
		this.listener = listener;
		this.lastByte = -1;
		this.recognizer = new Recognizer(this);
		this.data = new LinkedList<Byte>();
	}

	public void start() {
		this.recognizer.start();
	}

	public void stop() {
		this.recognizer.stop();
	}

	@Override
	public void onStarted(Recognizer recognizer) {
		this.listener.onStarted(this);
	}

	@Override
	public void onStopped(Recognizer recognizer) {
		this.listener.onStopped(this);
	}

	@Override
	public void onRecognized(Recognizer recognizer, byte code) {
		if (this.lastByte == code) {
			return;
		}

		this.data.add(code);

		this.lastByte = code;

		Packet packet = Packet.unpack(this.data);

		if (null == packet) {
			return;
		}

		if (packet.getChannel() == this.channel) {
			Logger.d(this.getClass(), "Receiver info: " + packet.getBodyAsString() + " @ " + this.channel.toString());

			this.listener.onReceived(this.channel, packet.getSource(), packet.getBodyAsString());
		}
	}
}
