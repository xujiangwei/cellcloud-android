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

import java.util.List;

import net.cellcloud.talk.Primitive;
import net.cellcloud.talk.stuff.SubjectStuff;

/**
 * 块数据方言。
 * 
 * @author Ambrose Xu
 * 
 */
public class ChunkDialect extends Dialect {

	/**
	 * 数据块方言的方言名。
	 */
	public final static String DIALECT_NAME = "ChunkDialect";

	/**
	 * 默认数据块大小。
	 */
	public final static int CHUNK_SIZE = 2048;

	/** 整块记号。用于标记整个块。 */
	protected String sign = null;
	/** 整块总长度。 */
	protected long totalLength = 0;
	/** 整块总数量。 */
	protected int chunkNum = 0;
	/** 当前块索引。 */
	protected int chunkIndex = 0;
	/** 当前块数据。 */
	protected byte[] data = null;
	/** 当前块长度。 */
	protected int length = 0;

	/**
	 * 用于标识该区块是否能写入缓存队列。
	 * 如果为 true ，表示已经“污染”，不能进入队列，必须直接发送。
	 */
	protected boolean infectant = false;

	/** 事件监听器。 */
	private ChunkListener listener;

	/** 顺序读操作的索引。 */
	private int readIndex = 0;

	/** 数据传输速率。 */
	protected int speedInKB = 20;

	/**
	 * 构造函数。
	 */
	protected ChunkDialect() {
		super(ChunkDialect.DIALECT_NAME);
	}

	/**
	 * 构造函数。
	 * 
	 * @param tracker 指定追踪器。
	 */
	public ChunkDialect(String tracker) {
		super(ChunkDialect.DIALECT_NAME, tracker);
	}

	/**
	 * 构造函数。
	 * 
	 * @param sign 指定整块的记号。
	 * @param totalLength 指定整块的总长度。
	 * @param chunkIndex 指定当前块索引。
	 * @param chunkNum 指定总块数量。
	 * @param data 指定当前块数据。
	 * @param length 指定当前块的数据长度。
	 */
	public ChunkDialect(String sign, long totalLength, int chunkIndex, int chunkNum, byte[] data, int length) {
		super(ChunkDialect.DIALECT_NAME);
		this.sign = sign;
		this.totalLength = totalLength;
		this.chunkIndex = chunkIndex;
		this.chunkNum = chunkNum;
		this.data = new byte[length];
		System.arraycopy(data, 0, this.data, 0, length);
		this.length = length;
	}

	/**
	 * 构造函数。
	 * 
	 * @param tracker 指定追踪器。
	 * @param sign 指定整块的记号。
	 * @param totalLength 指定整块的总长度。
	 * @param chunkIndex 指定当前块索引。
	 * @param chunkNum 指定总块数量。
	 * @param data 指定当前块数据。
	 * @param length 指定当前块的数据长度。
	 */
	public ChunkDialect(String tracker, String sign, long totalLength, int chunkIndex, int chunkNum, byte[] data, int length) {
		super(ChunkDialect.DIALECT_NAME, tracker);
		this.sign = sign;
		this.totalLength = totalLength;
		this.chunkIndex = chunkIndex;
		this.chunkNum = chunkNum;
		this.data = new byte[length];
		System.arraycopy(data, 0, this.data, 0, length);
		this.length = length;
	}

	/**
	 * 获得整个数据区块记号。
	 * 
	 * @return 返回区块记号。
	 */
	public String getSign() {
		return this.sign;
	}

	/**
	 * 获得整个数据块的数据总长度。
	 * 
	 * @return 返回数据块数据总长度。
	 */
	public long getTotalLength() {
		return this.totalLength;
	}

	/**
	 * 获得该区块索引。
	 * 
	 * @return 返回该区块索引。
	 */
	public int getChunkIndex() {
		return this.chunkIndex;
	}

	/**
	 * 获得区块总数量。
	 * 
	 * @return 返回区块总数量。
	 */
	public int getChunkNum() {
		return this.chunkNum;
	}

	/**
	 * 获得该区块数据长度。
	 * 
	 * @return 返回该区块数据长度。
	 */
	public int getLength() {
		return this.length;
	}

	/**
	 * 设置监听器。
	 * 
	 * @param listener 指定监听器。
	 */
	public void setListener(ChunkListener listener) {
		this.listener = listener;
	}

	/**
	 * 设置发送数据速率。
	 * 
	 * @param speed 指定以 KB/S 为单位的传输数据速率。
	 */
	public void setSpeed(int speed) {
		this.speedInKB = speed;
	}

	/**
	 * 获得发送数据的速率。
	 * 
	 * @return 返回以 KB/S 为单位的发送数据的速率。
	 */
	public int getSpeed() {
		return this.speedInKB;
	}

	/**
	 * 触发正在处理数据回调。
	 * 
	 * @param target 发送的目标，Cellet 的标识或者客户端的内核标签。
	 */
	protected void fireProgress(String target) {
		if (null != this.listener) {
			this.listener.onProgress(target, this);
			if (this.chunkIndex + 1 < this.chunkNum) {
				this.listener = null;
			}
		}
	}

	/**
	 * 触发数据处理完成回调。
	 * 
	 * @param target 发送的目标，Cellet 的标识或者客户端的内核标签。
	 */
	protected void fireCompleted(String target) {
		if (null != this.listener) {
			this.listener.onCompleted(target, this);
			this.listener = null;
		}
	}

	/**
	 * 触发数据处理失败回调。
	 * 
	 * @param target 发送的目标，Cellet 的标识或者客户端的内核标签。
	 */
	protected void fireFailed(String target) {
		if (null != this.listener) {
			this.listener.onFailed(target, this);
			this.listener = null;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Primitive reconstruct() {
		Primitive primitive = new Primitive(this);

		primitive.commit(new SubjectStuff(this.sign));
		primitive.commit(new SubjectStuff(this.chunkIndex));
		primitive.commit(new SubjectStuff(this.chunkNum));
		primitive.commit(new SubjectStuff(this.data));
		primitive.commit(new SubjectStuff(this.length));
		primitive.commit(new SubjectStuff(this.totalLength));

		return primitive;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void construct(Primitive primitive) {
		List<SubjectStuff> list = primitive.subjects();
		this.sign = list.get(0).getValueAsString();
		this.chunkIndex = list.get(1).getValueAsInt();
		this.chunkNum = list.get(2).getValueAsInt();
		this.data = list.get(3).getValue();
		this.length = list.get(4).getValueAsInt();
		this.totalLength = list.get(5).getValueAsLong();
	}

	@Override
	public boolean equals(Object obj) {
		if (null != obj && obj instanceof ChunkDialect) {
			ChunkDialect cd = (ChunkDialect) obj;
			if (null != this.sign && null != cd.sign && cd.sign.equals(this.sign)
				&& cd.chunkIndex == this.chunkIndex) {
				return true;
			}
		}

		return false;
	}

	/**
	 * 取消发送当前记号的所有区块。
	 */
	public List<ChunkDialect> cancel() {
		ChunkDialectFactory fact = (ChunkDialectFactory) DialectEnumerator.getInstance().getFactory(ChunkDialect.DIALECT_NAME);
		return fact.cancel(this.sign);
	}

	/**
	 * 此块所属记号的区块是否全部接收完毕。
	 * 
	 * @return 如果整个区块数据接收完毕返回 <code>true</code> 。
	 */
	public boolean hasCompleted() {
		ChunkDialectFactory fact = (ChunkDialectFactory) DialectEnumerator.getInstance().getFactory(ChunkDialect.DIALECT_NAME);
		return fact.checkCompleted(this.sign);
	}

	/**
	 * 此区块是否是最后一个区块。
	 * 
	 * @return 如果是最后一个区块返回 <code>true</code> 。
	 */
	protected boolean isLast() {
		return (this.chunkIndex + 1 == this.chunkNum);
	}

	/**
	 * 读取指定块的数据。
	 * 
	 * @param index 指定读取区块的索引。
	 * @param buffer 指定接收读取数据的数组。
	 * @return 返回读取数据的长度，如果读取失败返回 <code>-1</code> 。
	 */
	public int read(int index, byte[] buffer) {
		ChunkDialectFactory fact = (ChunkDialectFactory) DialectEnumerator.getInstance().getFactory(ChunkDialect.DIALECT_NAME);
		return fact.read(this.sign, index, buffer);
	}

	/**
	 * 自动计数方式依次读取区块数据。
	 * 
	 * @param buffer 指定接收读取数据的数组。
	 * @return 返回读取数据的长度，如果读取失败返回 <code>-1</code> 。
	 */
	public int read(byte[] buffer) {
		if (this.readIndex >= this.chunkNum) {
			return -1;
		}

		ChunkDialectFactory fact = (ChunkDialectFactory) DialectEnumerator.getInstance().getFactory(ChunkDialect.DIALECT_NAME);
		int length = fact.read(this.sign, this.readIndex, buffer);
		++this.readIndex;
		return length;
	}

	/**
	 * 重置读数据索引。
	 */
	public void resetRead() {
		this.readIndex = 0;
	}

	/**
	 * 清空此区块所属记号的整块数据。
	 */
	public void clearAll() {
		ChunkDialectFactory fact = (ChunkDialectFactory) DialectEnumerator.getInstance().getFactory(ChunkDialect.DIALECT_NAME);
		fact.clear(this.sign);
	}

}
