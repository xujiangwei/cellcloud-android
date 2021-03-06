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

import net.cellcloud.core.Cellet;
import net.cellcloud.talk.Primitive;

/**
 * 原语方言。
 * 
 * @author Ambrose Xu
 * 
 */
public abstract class Dialect {

	/** 方言名。 */
	private String name;
	/** 追踪器。 */
	protected String tracker;
	/** 标签。 */
	protected String tag;
	/** Cellet 标识。 */
	protected String celletIdentifier;
	/** Cellet 实例引用。 */
	protected Cellet cellet;

	/**
	 * 构造函数。
	 * 
	 * @param name 指定方言名。
	 */
	public Dialect(String name) {
		this.name = name;
		this.tracker = "none";
	}

	/**
	 * 构造函数。
	 * 
	 * @param name 指定方言名。
	 * @param tracker 指定追踪器。
	 */
	public Dialect(String name, String tracker) {
		this.name = name;
		this.tracker = tracker;
	}

	/**
	 * 获得方言名。
	 * 
	 * @return 返回方言名。
	 */
	public final String getName() {
		return this.name;
	}

	/**
	 * 获得方言追踪名。
	 * 
	 * @return 返回方言追踪名。
	 */
	public final String getTracker() {
		return this.tracker;
	}

	/**
	 * 设置源标签。
	 * 
	 * @param tag 指定源标签。
	 */
	public final void setOwnerTag(String tag) {
		this.tag = tag;
	}

	/**
	 * 获得源标签。
	 * 
	 * @return 返回源标签。
	 */
	public final String getOwnerTag() {
		return this.tag;
	}

	/**
	 * 设置 Cellet 标识。
	 * 
	 * @param identifier 指定 Cellet 标识。
	 */
	public final void setCelletIdentifier(String identifier) {
		this.celletIdentifier = identifier;
	}

	/**
	 * 获得 Cellet 标识。
	 * 
	 * @return 返回 Cellet 标识。
	 */
	public final String getCelletIdentifier() {
		return this.celletIdentifier;
	}

	/**
	 * 设置 Cellet 。
	 * 
	 * @param cellet 指定 Cellet 实例引用。
	 */
	public final void setCellet(Cellet cellet) {
		this.cellet = cellet;
	}

	/**
	 * 获得 Cellet 实例引用。
	 * 
	 * @return 返回 Cellet 实例引用。
	 */
	public final Cellet getCellet() {
		return this.cellet;
	}

	/**
	 * 将原语重构为方言。
	 */
	abstract public Primitive reconstruct();

	/**
	 * 从原语构建方言。
	 */
	abstract public void construct(Primitive primitive);

}
