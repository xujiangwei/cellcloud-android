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

public class AirSender implements TransmitterListener {

	private byte self;
	private Channel channel;

	private LinkedList<byte[]> queue;

	private Transmitter transmitter;
	private BlockingQueue trsQueue;

	private Thread thread;
	private boolean spinning;

	public AirSender(Channel channel) {
		this.channel = channel;

		this.queue = new LinkedList<byte[]>();

		this.trsQueue = new BlockingQueue();
		this.transmitter = new Transmitter(this, this.trsQueue);
	}

	public void setId(byte id) {
		this.self = id;
	}

	public void start() {
		this.spinning = true;

		this.transmitter.start();

		this.thread = new Thread() {
			@Override
			public void run() {
				while (spinning) {
					synchronized (AirSender.this) {
						if (queue.isEmpty()) {
							try {
								AirSender.this.wait();
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}

						byte[] data = queue.poll();
						if (null != data) {
							for (byte d : data) {
								trsQueue.enqueue(d);
								trsQueue.enqueue(d);

								try {
									Thread.sleep(1000);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							}
						}
					}
				}
			}
		};
		this.thread.start();
	}

	public void stop() {
		this.spinning = false;

		this.trsQueue.clear();
		this.queue.clear();

		this.transmitter.stop();

		synchronized (this) {
			this.notify();
		}
	}

	public void send(String text) {
		byte[] packet = Packet.packPost(this.channel.getCode(), this.self, text);

		synchronized (this) {
			this.queue.offer(packet);

			this.notify();
		}
	}

	@Override
	public void onStarted(Transmitter transmitter) {
		
	}

	@Override
	public void onStopped(Transmitter transmitter) {
		
	}

	@Override
	public void onTransmitted(Transmitter transmitter, byte data) {
		Logger.d(this.getClass(), "transmitted: " + data);
	}
}
