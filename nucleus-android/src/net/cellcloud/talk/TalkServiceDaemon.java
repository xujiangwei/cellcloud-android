/*
-----------------------------------------------------------------------------
This source file is part of Cell Cloud.

Copyright (c) 2009-2012 Cell Cloud Team (www.cellcloud.net)

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

package net.cellcloud.talk;

import java.util.Iterator;
import java.util.TimerTask;

import net.cellcloud.common.Logger;

/** Talk Service 守护线程。
 * 
 * @author Jiangwei Xu
 */
public final class TalkServiceDaemon extends TimerTask {

	private long tickTime = 0;
	private int speakerHeartbeatMod = 0;
	private int heartbeatCount = 0;

	public TalkServiceDaemon(int intervalInSeconds) {
		this.speakerHeartbeatMod = Math.round(300.0f / (float)intervalInSeconds);
	}

	@Override
	public void run() {
		// 当前时间
		this.tickTime = System.currentTimeMillis();

		// 心跳计数
		++this.heartbeatCount;
		if (this.heartbeatCount >= Integer.MAX_VALUE) {
			this.heartbeatCount = 0;
		}

		TalkService service = TalkService.getInstance();

		if (this.heartbeatCount % this.speakerHeartbeatMod == 0) {
			// 5分钟一次心跳
			if (null != service.speakers) {
				for (Speaker speaker : service.speakers) {
					if (speaker.heartbeat()) {
						Logger.i(TalkServiceDaemon.class,
								"Talk service heartbeat to " + speaker.getAddress().getAddress().getHostAddress()
								+ ":" + speaker.getAddress().getPort());
					}
				}
			}
		}

		// 检查丢失连接的 Speaker
		if (null != service.speakers) {
			Iterator<Speaker> iter = service.speakers.iterator();
			while (iter.hasNext()) {
				Speaker speaker = iter.next();
				if (speaker.isLost()
					&& null != speaker.capacity
					&& speaker.capacity.retryAttempts > 0) {
					if (speaker.retryTimestamp == 0) {
						// 建立时间戳
						speaker.retryTimestamp = this.tickTime;
						continue;
					}

					// 判断是否达到最大重试次数
					if (speaker.retryCounts >= speaker.capacity.retryAttempts) {
						if (!speaker.retryEnd) {
							speaker.retryEnd = true;
							speaker.fireRetryEnd();
						}
						continue;
					}

					if (this.tickTime - speaker.retryTimestamp >= speaker.capacity.retryDelay) {
						// 重连
						speaker.retryTimestamp = this.tickTime;
						speaker.retryCounts++;
						// 执行 call
						if (speaker.call(null)) {
							StringBuilder buf = new StringBuilder();
							buf.append("Retry call cellet '");
							buf.append(speaker.getIdentifiers().get(0));
							buf.append("' at ");
							buf.append(speaker.getAddress().getAddress().getHostAddress());
							buf.append(":");
							buf.append(speaker.getAddress().getPort());
							Logger.i(TalkServiceDaemon.class, buf.toString());
							buf = null;
						}
						else {
							StringBuilder buf = new StringBuilder();
							buf.append("Failed retry call cellet '");
							buf.append(speaker.getIdentifiers().get(0));
							buf.append("' at ");
							buf.append(speaker.getAddress().getAddress().getHostAddress());
							buf.append(":");
							buf.append(speaker.getAddress().getPort());
							Logger.w(TalkServiceDaemon.class, buf.toString());
							buf = null;

							// 回调重试结束
							speaker.fireRetryError();
						}
					}
				}
			}
		}

		// 处理未识别 Session
//		service.processUnidentifiedSessions(this.tickTime);
	}
}
