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

package net.cellcloud.common;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

import android.util.Log;

/**
 * 日志管理器。
 * 
 * @author Ambrose Xu
 * 
 */
public final class LogManager {

	private final static LogManager instance = new LogManager();

	public final static SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA);

	private ArrayList<LogHandle> handles;
	private byte level;

	private LogManager() {
		this.handles = new ArrayList<LogHandle>();
		this.level = LogLevel.DEBUG;

		this.handles.add(createAndroidHandle());
	}

	/**
	 * 
	 * @return
	 */
	public static LogManager getInstance() {
		return instance;
	}

	/**
	 * 设置日志等级。
	 * 
	 * @param level
	 */
	public void setLevel(byte level) {
		this.level = level;
	}

	/**
	 * 返回日志等级。
	 * 
	 * @return
	 */
	public byte getLevel() {
		return this.level;
	}

	/**
	 * 返回句柄数量。
	 * 
	 * @return
	 */
	public int numHandles() {
		return this.handles.size();
	}

	/**
	 * 记录日志。
	 * 
	 * @param level
	 * @param tag
	 * @param log
	 */
	public void log(byte level, String tag, String log) {
		synchronized (this) {
			if (this.handles.isEmpty()) {
				System.err.println("No log handler in logger manager.");
				return;
			}

			if (this.level > level) {
				return;
			}

			for (LogHandle handle : this.handles) {
				switch (level) {
				case LogLevel.DEBUG:
					handle.logDebug(tag, log);
					break;
				case LogLevel.INFO:
					handle.logInfo(tag, log);
					break;
				case LogLevel.WARNING:
					handle.logWarning(tag, log);
					break;
				case LogLevel.ERROR:
					handle.logError(tag, log);
					break;
				default:
					break;
				}
			}
		}
	}

	/**
	 * 添加日志内容处理器。
	 * 
	 * @param handle
	 */
	public void addHandle(LogHandle handle) {
		synchronized (this) {
			if (this.handles.contains(handle)) {
				return;
			}

			for (LogHandle h : this.handles) {
				if (handle.getName().equals(h.getName())) {
					return;
				}
			}

			this.handles.add(handle);
		}
	}

	/**
	 * 移除日志内容处理器。
	 * 
	 * @param handle
	 */
	public void removeHandle(LogHandle handle) {
		synchronized (this) {
			this.handles.remove(handle);
		}
	}

	/**
	 * 移除所有日志内容处理器。
	 */
	public void removeAllHandles() {
		synchronized (this) {
			this.handles.clear();
		}
	}

	/**
	 * 创建 Android 日志处理器。
	 * 
	 * @return
	 */
	public static LogHandle createAndroidHandle() {
		return new LogHandle() {

			private String name = "CellAndroidLog";

			@Override
			public String getName() {
				return this.name;
			}

			@Override
			public void logDebug(String tag, String log) {
				Log.d(tag, log);
			}

			@Override
			public void logInfo(String tag, String log) {
				Log.i(tag, log);
			}

			@Override
			public void logWarning(String tag, String log) {
				Log.w(tag, log);
			}

			@Override
			public void logError(String tag, String log) {
				Log.e(tag, log);
			}
		};
	}

}
