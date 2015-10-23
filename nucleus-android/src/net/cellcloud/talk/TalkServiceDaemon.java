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
import net.cellcloud.talk.dialect.DialectEnumerator;

/** Talk Service 守护线程。
 * 
 * @author Jiangwei Xu
 */
public final class TalkServiceDaemon extends TimerTask {

	protected boolean running = false;
	private long tickTime = 0;
	private int speakerHeartbeatMod = 0;
	private int heartbeatCount = 0;

	public TalkServiceDaemon(int intervalInSeconds) {
		this.speakerHeartbeatMod = Math.round(180.0f / (float)intervalInSeconds);
	}

	/** 返回周期时间点。
	 */
	protected long getTickTime() {
		return this.tickTime;
	}

//	protected void resetSleepInterval(long interval) {
//		if (interval < 1000) {
//			return;
//		}
//
//		if (this.interval == interval) {
//			return;
//		}
//
//		this.interval = interval;
//
//		// 换算为 1 秒
//		int n = Math.round((float)interval / 1000.0f);
//		if (n <= 0) {
//			n = 1;
//		}
//		// 计算模数
//		this.speakerHeartbeatMod = Math.round(120.0f / (float)n);
//
//		Logger.d(this.getClass(), "Reset heartbeat mod: " + this.speakerHeartbeatMod);
//	}

	@Override
	public void run() {
		this.running = true;

		// 当前时间
		this.tickTime = System.currentTimeMillis();

		// 心跳计数
		++this.heartbeatCount;
		if (this.heartbeatCount > 100) {
			this.heartbeatCount = 0;
		}

		TalkService service = TalkService.getInstance();

		if (this.heartbeatCount % this.speakerHeartbeatMod == 0) {
			// 3分钟一次心跳
			if (null != service.speakers) {
				for (Speaker speaker : service.speakers) {
					speaker.heartbeat();
				}
			}
		}

		// 检查丢失连接的 Speaker
		if (null != service.speakers) {
			Iterator<Speaker> iter = service.speakers.iterator();
			while (iter.hasNext()) {
				Speaker speaker = iter.next();
				if (speaker.lost
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
							buf.append(speaker.getRemoteTag());
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
							buf.append(speaker.getRemoteTag());
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
		service.processUnidentifiedSessions(this.tickTime);

			// 10 分钟检查一次挂起状态下的会话器是否失效
//			if (heartbeatCount % 60 == 0) {
				// 检查并删除挂起的会话
//				service.checkAndDeleteSuspendedTalk();
//			}
	}

	public void stop() {
		TalkService service = TalkService.getInstance();

		// 关闭所有 Speaker
		if (null != service.speakers) {
			Iterator<Speaker> iter = service.speakers.iterator();
			while (iter.hasNext()) {
				Speaker speaker = iter.next();
				speaker.hangUp();
			}
			service.speakers.clear();
		}

		DialectEnumerator.getInstance().shutdownAll();

		Logger.i(this.getClass(), "Talk service daemon stop.");
		this.running = false;
	}
}
