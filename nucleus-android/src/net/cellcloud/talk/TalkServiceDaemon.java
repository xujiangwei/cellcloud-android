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

import net.cellcloud.common.LogLevel;
import net.cellcloud.common.Logger;
import net.cellcloud.talk.dialect.DialectEnumerator;

/** Talk Service 守护线程。
 * 
 * @author Jiangwei Xu
 */
public final class TalkServiceDaemon extends Thread {

	private boolean spinning = false;
	protected boolean running = false;
	private long tickTime = 0;
	private long interval = 0;
	private int speakerHeartbeatMod = 0;

	public TalkServiceDaemon() {
		super("TalkServiceDaemon");
		this.resetSleepInterval(2000);
	}

	/** 返回周期时间点。
	 */
	protected long getTickTime() {
		return this.tickTime;
	}

	protected void resetSleepInterval(long interval) {
		if (interval < 1000) {
			return;
		}

		if (this.interval == interval) {
			return;
		}

		this.interval = interval;

		// 换算为 1 秒
		int n = (int)interval / 1000;
		if (n <= 0) {
			n = 1;
		}
		// 计算模数
		this.speakerHeartbeatMod = 120 / n;
	}

	@Override
	public void run() {
		this.running = true;
		this.spinning = true;

		TalkService service = TalkService.getInstance();

		int heartbeatCount = 0;

		do {
			// 当前时间
			this.tickTime = System.currentTimeMillis();

			// 心跳计数
			++heartbeatCount;
			if (heartbeatCount > 100) {
				heartbeatCount = 0;
			}

			// HTTP 客户端管理
//			if (heartbeatCount % 5 == 0) {
				// 每 5 秒一次计数
				// TODO
//				if (null != service.httpSpeakers) {
//					for (HttpSpeaker speaker : service.httpSpeakers) {
//						speaker.tick();
//					}
//				}
//			}

			// HTTP 服务器 Session 管理
//			if (heartbeatCount % 60 == 0) {
//				service.checkHttpSessionHeartbeat();
//			}

			if (heartbeatCount % this.speakerHeartbeatMod == 0) {
				// 2分钟一次心跳
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
							if (Logger.isDebugLevel()) {
								StringBuilder buf = new StringBuilder();
								buf.append("Retry call cellet '");
								buf.append(speaker.getRemoteTag());
								buf.append("' at ");
								buf.append(speaker.getAddress().getAddress().getHostAddress());
								buf.append(":");
								buf.append(speaker.getAddress().getPort());
								Logger.d(TalkServiceDaemon.class, buf.toString());
								buf = null;
							}

							// 重连
							speaker.retryTimestamp = this.tickTime;
							speaker.retryCounts++;
							// 执行 call
							speaker.call(null);
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

			// 休眠
			try {
				Thread.sleep(this.interval);
			} catch (InterruptedException e) {
				Logger.log(TalkServiceDaemon.class, e, LogLevel.ERROR);
			}

		} while (this.spinning);

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

		Logger.i(this.getClass(), "Talk service daemon quit.");
		this.running = false;
	}

	public void stopSpinning() {
		this.spinning = false;
	}
}
