/*
-----------------------------------------------------------------------------
This source file is part of Cell Cloud.

Copyright (c) 2009-2017 Cell Cloud Team (www.cellcloud.net)

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
import java.util.Timer;
import java.util.TimerTask;

import net.cellcloud.common.LogLevel;
import net.cellcloud.common.Logger;
import net.cellcloud.util.TimeReceiver.TimeListener;

/**
 * Talk Service 守护线程。
 * 
 * @author Ambrose Xu
 * 
 */
public final class TalkServiceDaemon extends TimerTask implements TimeListener {

	/** 每次执行任务的时间戳。 */
	private long tickTime = 0;

	/** 最近一次执行心跳的时间戳。 */
	private long lastHeartbeatTime = 0L;
	/** 心跳周期。 */
	private long speakerHeartbeatInterval= 7L * 60L * 1000L;

	/** 是否使用轮询。 */
	private boolean polling;

	/** 轮询使用的定时器。 */
	private Timer timer;

	/**
	 * 构造函数。
	 * 
	 * @param polling 是否启用轮询。
	 */
	protected TalkServiceDaemon(boolean polling) {
		this.polling = polling;
	}

	/**
	 * 获得 Tick 时间戳。
	 * 
	 * @return 返回 Tick 时间戳。
	 */
	public long tickTime() {
		return this.tickTime;
	}

	/**
	 * 将任务置为睡眠状态。
	 */
	public void sleep() {
		if (this.polling) {
			synchronized (this) {
				try {
					if (null != this.timer) {
						this.timer.cancel();
						this.timer.purge();
						this.timer = null;
					}
				} catch (Exception e) {
					Logger.log(this.getClass(), e, LogLevel.WARNING);
				}
			}
		}
	}

	/**
	 * 将任务从睡眠状态唤醒。
	 */
	public void wakeup() {
		if (this.polling) {
			synchronized (this) {
				try {
					if (null != this.timer) {
						this.timer.cancel();
						this.timer.purge();
						this.timer = null;
					}

					this.timer = new Timer();

					// 间隔 30 秒
					this.timer.schedule(this, 10000L, 30000L);
				} catch (Exception e) {
					Logger.log(this.getClass(), e, LogLevel.WARNING);
				}
			}
		}
	}

	/**
	 * 停止任务执行。
	 */
	public void stop() {
		synchronized (this) {
			if (null != this.timer) {
				this.timer.cancel();
				this.timer.purge();
				this.timer = null;
			}
		}
	}

	@Override
	public void onTimeTick() {
		// Tick 进行心跳
		this.tick();
	}

	@Override
	public void run() {
		this.tick();
	}

	/**
	 * Tick 执行。
	 */
	private void tick() {
		// 当前时间
		this.tickTime = System.currentTimeMillis();

		TalkService service = TalkService.getInstance();

		// 执行心跳逻辑
		if (this.tickTime - this.lastHeartbeatTime >= this.speakerHeartbeatInterval) {
			// 更新最近心跳时间
			this.lastHeartbeatTime = this.tickTime;

			if (null != service.speakers) {
				for (Speaker speaker : service.speakers) {
					if (speaker.heartbeat()) {
						Logger.i(TalkServiceDaemon.class,
								"Talk service heartbeat to " + speaker.getAddress().getAddress().getHostAddress() + ":" + speaker.getAddress().getPort());
					}
				}

				for (int i = 0; i < service.speakers.size(); ++i) {
					final Speaker speaker = service.speakers.get(i);
					if (speaker.heartbeatTime > 0L && this.tickTime - speaker.heartbeatTime >= 900000L) {
						Thread thread = new Thread() {
							@Override
							public void run() {
								TalkServiceFailure failure = new TalkServiceFailure(TalkFailureCode.TALK_LOST, this.getClass(),
										speaker.getAddress().getHostString(), speaker.getAddress().getPort());
								failure.setSourceCelletIdentifiers(speaker.getIdentifiers());
								speaker.fireFailed(failure);
							}
						};
						thread.start();
					}
				}
			}
		}

		// 检查丢失连接的 Speaker
		if (null != service.speakers) {
			Iterator<Speaker> iter = service.speakers.iterator();
			while (iter.hasNext()) {
				Speaker speaker = iter.next();
				if (speaker.isLost() && null != speaker.capacity && speaker.capacity.retry > 0) {
					if (speaker.retryTimestamp == 0) {
						// 建立时间戳
						speaker.retryTimestamp = this.tickTime;
						continue;
					}

					// 判断是否达到最大重试次数
					if (speaker.retryCount >= speaker.capacity.retry) {
						if (!speaker.retryEnd) {
							speaker.retryEnd = true;
							speaker.fireRetryEnd();
						}
						continue;
					}

					if (this.tickTime - speaker.retryTimestamp >= speaker.capacity.retryDelay) {
						// 重连
						speaker.retryTimestamp = this.tickTime;
						speaker.retryCount++;
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
		// service.processUnidentifiedSessions(this.tickTime);
	}

}
