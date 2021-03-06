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

import java.util.concurrent.ConcurrentHashMap;

import net.cellcloud.core.Cellet;
import net.cellcloud.talk.CelletCallbackListener;
import net.cellcloud.talk.TalkDelegate;

/**
 * 方言枚举器。
 * 
 * @author Ambrose Xu
 * 
 */
public final class DialectEnumerator implements TalkDelegate, CelletCallbackListener {

	private static final DialectEnumerator instance = new DialectEnumerator();

	/**
	 * 方言工厂映射。
	 */
	private ConcurrentHashMap<String, DialectFactory> factories;

	private DialectEnumerator() {
		this.factories = new ConcurrentHashMap<String, DialectFactory>();
	}

	/**
	 * 返回方言工厂的单例。
	 */
	public static DialectEnumerator getInstance() {
		return instance;
	}

	/**
	 * 创建方言。
	 * 
	 * @param name 指定方言名称。
	 * @param tracker 指定追踪器。
	 * @return 如果没有找到指定名称的方言工厂则无法创建方言，返回 <code>null</code> 值。
	 */
	public Dialect createDialect(String name, String tracker) {
		DialectFactory fact = this.factories.get(name);
		if (null != fact) {
			return fact.create(tracker);
		}

		return null;
	}

	/**
	 * 添加方言工厂。
	 * 
	 * @param fact 指定方言工厂。
	 */
	public void addFactory(DialectFactory fact) {
		this.factories.put(fact.getMetaData().name, fact);
	}

	/**
	 * 删除方言工厂。
	 * 
	 * @param fact 指定方言工厂。
	 */
	public void removeFactory(DialectFactory fact) {
		if (this.factories.containsKey(fact.getMetaData().name)) {
			this.factories.remove(fact.getMetaData().name);
		}
	}

	/**
	 * 获取指定名称的方言工厂。
	 * 
	 * @param name 指定方言名称。
	 * @return 返回指定名称的方言工厂。
	 */
	public DialectFactory getFactory(String name) {
		return this.factories.get(name);
	}

	/**
	 * 启动所有方言工厂。
	 */
	public void startupAll() {
		for (DialectFactory fact : this.factories.values()) {
			fact.startup();
		}
	}

	/**
	 * 关闭所有方言工厂。
	 */
	public void shutdownAll() {
		for (DialectFactory fact : this.factories.values()) {
			fact.shutdown();
		}
	}

	/**
	 * 休眠所有方言工厂。
	 */
	public void sleepAll() {
		for (DialectFactory fact : this.factories.values()) {
			fact.sleep();
		}
	}

	/**
	 * 唤醒所有方言工厂。
	 */
	public void wakeupAll() {
		for (DialectFactory fact : this.factories.values()) {
			fact.wakeup();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean doTalk(String identifier, Dialect dialect) {
		DialectFactory fact = this.factories.get(dialect.getName());
		if (null == fact) {
			// 返回 true ，不劫持
			return true;
		}

		return fact.onTalk(identifier, dialect);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void didTalk(String identifier, Dialect dialect) {
		// Nothing
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean doDialogue(String identifier, Dialect dialect) {
		DialectFactory fact = this.factories.get(dialect.getName());
		if (null == fact) {
			// 返回 true ，不劫持
			return true;
		}

		return fact.onDialogue(identifier, dialect);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void didDialogue(String identifier, Dialect dialect) {
		// Nothing
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean doTalk(Cellet cellet, String targetTag, Dialect dialect) {
		DialectFactory fact = this.factories.get(dialect.getName());
		if (null == fact) {
			// 返回 true ，不劫持
			return true;
		}

		return fact.onTalk(cellet, targetTag, dialect);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean doDialogue(Cellet cellet, String sourceTag, Dialect dialect) {
		DialectFactory fact = this.factories.get(dialect.getName());
		if (null == fact) {
			// 返回 true ，不劫持
			return true;
		}

		return fact.onDialogue(cellet, sourceTag, dialect);
	}

}
