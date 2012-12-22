/*
-----------------------------------------------------------------------------
This source file is part of Cell Cloud.

Copyright (c) 2009-2013 Cell Cloud Team (cellcloudproject@gmail.com)

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

package net.cellcloud.storage;

import net.cellcloud.exception.StorageException;
import net.cellcloud.util.Properties;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

/** SQLite 存储器。
 * 
 * @author Jiangwei Xu
 */
public class SQLiteStorage implements RDBStorage {

	public final static String TYPE_NAME = "SQLiteStorage";

	private String instanceName;

	private SQLiteStorageProperties properties;
	private DBHelper dbHelper;
	private SQLiteDatabase db;

	public SQLiteStorage(final String instanceName) {
		this.instanceName = instanceName;
	}

	@Override
	public String getName() {
		return this.instanceName;
	}

	@Override
	public String getTypeName() {
		return SQLiteStorage.TYPE_NAME;
	}

	@Override
	public boolean open(Properties properties) throws StorageException {
		if (!(properties instanceof SQLiteStorageProperties)) {
			return false;
		}

		this.properties = (SQLiteStorageProperties) properties; 

		if (null != this.db && this.db.isOpen()) {
			this.db.close();
		}

		String dbFile = this.properties.getDBFile();
		if (null == this.dbHelper)
			this.dbHelper = new DBHelper((Context) properties.getProperty("").getValue()
					, dbFile, null, 3);

		this.db = this.dbHelper.getReadableDatabase();

		return true;
	}

	@Override
	public void close() throws StorageException {
		if (null != this.db) {
			this.db.close();
		}
	}

	@Override
	public ResultSet store(String statement) throws StorageException {
		return null;
	}

	@Override
	public ResultSet store(Schema schema) throws StorageException {
		return null;
	}

	/** SQLite 操作辅助类。
	 * 
	 * @author Jiangwei Xu
	 */
	protected final class DBHelper extends SQLiteOpenHelper {

		public DBHelper(Context context, String name, CursorFactory factory,
				int version) {
			super(context, name, factory, version);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			
		}
	}
}
