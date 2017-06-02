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

package net.cellcloud.talk.dialect;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import net.cellcloud.core.Cellet;

/**
 * 动作方言工厂。
 * 
 * @author Ambrose Xu
 * 
 */
public final class ActionDialectFactory extends DialectFactory {

	/** 方言的元描述。 */
	private DialectMetaData metaData;

	/** 线程池执行器。 */
	private ExecutorService executor;
	/** 最大并发线程数量。 */
	private int maxThreadNum;
	/** 线程数量计数。 */
	private AtomicInteger threadCount;

	/** 待处理的方言和委派列表列表。 */
	private LinkedList<Pair> pairList;

	/** 特定的动作处理队列。 */
	private ConcurrentHashMap<String, Vector<Pair>> specificQueueMap;
	private AtomicInteger specificSize;

	/**
	 * 构造函数。
	 * 
	 * @param executor 指定线程池执行器。
	 */
	public ActionDialectFactory(ExecutorService executor) {
		this.metaData = new DialectMetaData(ActionDialect.DIALECT_NAME, "Action Dialect");
		this.executor = executor;
		this.maxThreadNum = 2;
		this.threadCount = new AtomicInteger(0);
		this.pairList = new LinkedList<Pair>();
		this.specificQueueMap = new ConcurrentHashMap<String, Vector<Pair>>();
		this.specificSize = new AtomicInteger(0);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DialectMetaData getMetaData() {
		return this.metaData;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Dialect create(String tracker) {
		return new ActionDialect(tracker);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void shutdown() {
		synchronized (this.metaData) {
			this.pairList.clear();
		}

		this.specificQueueMap.clear();
		this.specificSize.set(0);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void sleep() {
		// Nothing
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void wakeup() {
		// Nothing
	}

	/**
	 * 启用指定动作名的顺序队列。
	 * 
	 * @param action
	 */
	public void openOrderedQueue(String action) {
		if (this.specificQueueMap.containsKey(action)) {
			return;
		}

		this.specificQueueMap.put(action.toString(), new Vector<Pair>());
	}

	/**
	 * 停用指定动作名的顺序队列。
	 * 
	 * @param action
	 */
	public void closeOrderedQueue(String action) {
		this.specificQueueMap.remove(action);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean onTalk(String identifier, Dialect dialect) {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean onDialogue(String identifier, Dialect dialect) {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean onTalk(Cellet cellet, String targetTag, Dialect dialect) {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean onDialogue(Cellet cellet, String sourceTag, Dialect dialect) {
		return true;
	}

	/**
	 * 执行动作。
	 * 
	 * @param dialect 执行动作的方言。
	 * @param delegate 指定动作的委派。
	 */
	protected void doAction(ActionDialect dialect, ActionDelegate delegate) {
		String action = dialect.getAction();
		if (this.specificQueueMap.containsKey(action)) {
			Vector<Pair> queue = this.specificQueueMap.get(action);
			queue.add(new Pair(dialect, delegate));
			this.specificSize.incrementAndGet();
		}
		else {
			synchronized (this.metaData) {
				this.pairList.add(new Pair(dialect, delegate));
			}
		}

		if (this.threadCount.get() < this.maxThreadNum) {
			// 线程数量未达到最大线程数，启动新线程

			// 更新计数
			this.threadCount.incrementAndGet();

			this.executor.execute(new Runnable() {
				@Override
				public void run() {
					ActionDelegate adg = null;
					ActionDialect adl = null;
					Iterator<Vector<Pair>> iter = null;

					while (!pairList.isEmpty() || 0 != specificSize.get()) {
						synchronized (metaData) {
							if (!pairList.isEmpty()) {
								Pair pair = pairList.removeFirst();
								adg = pair.delegate;
								adl = pair.dialect;
							}
						}

						// Do action
						if (null != adg) {
							adg.doAction(adl);
						}

						// reset
						adg = null;
						adl = null;

						if (0 == specificSize.get()) {
							// 特定队列无数据
							continue;
						}

						iter = specificQueueMap.values().iterator();
						while (iter.hasNext()) {
							Vector<Pair> list = iter.next();
							synchronized (list) {
								if (!list.isEmpty()) {
									Pair pair = list.remove(0);
									specificSize.decrementAndGet();
									adg = pair.delegate;
									adl = pair.dialect;
								}

								// Do action
								if (null != adg) {
									adg.doAction(adl);
								}

								// reset
								adg = null;
								adl = null;
							}
						}
					}

					// 更新计数
					threadCount.decrementAndGet();
				}
			});
		}
	}

	/**
	 * 数据对。
	 */
	private class Pair {
		private ActionDialect dialect;
		private ActionDelegate delegate;

		public Pair(ActionDialect dialect, ActionDelegate delegate) {
			this.dialect = dialect;
			this.delegate = delegate;
		}
	}

}
