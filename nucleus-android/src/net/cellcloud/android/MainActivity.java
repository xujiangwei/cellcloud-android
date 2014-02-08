/*
-----------------------------------------------------------------------------
This source file is part of Cell Cloud.

Copyright (c) 2009-2012 Cell Cloud Team (cellcloudproject@gmail.com)

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

package net.cellcloud.android;

import java.net.InetSocketAddress;
import java.util.ArrayList;

import net.cellcloud.common.LogLevel;
import net.cellcloud.common.Logger;
import net.cellcloud.common.MessageErrorCode;
import net.cellcloud.core.Nucleus;
import net.cellcloud.core.NucleusConfig;
import net.cellcloud.exception.SingletonException;
import net.cellcloud.talk.Primitive;
import net.cellcloud.talk.TalkListener;
import net.cellcloud.talk.TalkService;
import net.cellcloud.talk.TalkServiceFailure;
import net.cellcloud.talk.stuff.ObjectiveStuff;
import net.cellcloud.talk.stuff.PredicateStuff;
import net.cellcloud.talk.stuff.SubjectStuff;
import net.cellcloud.util.Utils;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends Activity implements TalkListener {

	private final String address = "192.168.2.3";
	private final String identifier = "Dummy";

	private Button btnReady;
	private Button btnStart;
	private Button btnStop;
	private EditText txtLog;

	private boolean running = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d("Lifecycle", "onCreate");

		setContentView(R.layout.activity_main);

		this.configView();

		this.startup();
	}

	@Override
	protected void onStart() {
		super.onStart();
		Log.d("Lifecycle", "onStart");
	}

	@Override
	protected void onStop() {
		super.onStop();
		Log.d("Lifecycle", "onStop");
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d("Lifecycle", "onResume");
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.d("Lifecycle", "onPause");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.d("Lifecycle", "onDestroy");

		this.shutdown();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	private boolean startup() {
		NucleusConfig config = new NucleusConfig();
		config.role = NucleusConfig.Role.CONSUMER;
		config.device = NucleusConfig.Device.PHONE;

		Nucleus nucleus = Nucleus.getInstance();
		if (null == nucleus) {
			try {
				nucleus = new Nucleus(config, this.getApplication());
			} catch (SingletonException e) {
				Logger.log(MainActivity.class, e, LogLevel.ERROR);
			}
		}

		return nucleus.startup();
	}

	private void shutdown() {
		this.stopDemo();

		Nucleus nucleus = Nucleus.getInstance();
		nucleus.shutdown();
	}

	/**
	 * 配置 View
	 */
	private void configView() {
		this.btnReady = (Button) this.getWindow().getDecorView().findViewById(R.id.button_ready);
		this.btnStart = (Button) this.getWindow().getDecorView().findViewById(R.id.button_start);
		this.btnStop = (Button) this.getWindow().getDecorView().findViewById(R.id.button_stop);
		this.txtLog = (EditText) this.getWindow().getDecorView().findViewById(R.id.text_log);

		this.btnStart.setEnabled(false);
		this.btnStop.setEnabled(false);

		this.btnReady.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (ready()) {
					btnReady.setEnabled(false);
				}
			}
		});

		this.btnStart.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				btnStop.setEnabled(true);
				btnStart.setEnabled(false);
				startDemo();
			}
		});

		this.btnStop.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				btnStart.setEnabled(true);
				btnStop.setEnabled(false);
				stopDemo();
			}
		});
	}

	private boolean ready() {
		TalkService talkService = Nucleus.getInstance().getTalkService();
		if (!talkService.hasListener(this)) {
			talkService.addListener(this);
		}

		boolean ret = talkService.call(this.identifier, new InetSocketAddress(this.address, 7000));
		if (ret) {
			this.txtLog.append("Calling cellet 'Dummy' ...\n");
		}
		else {
			this.txtLog.append("Call cellet 'Dummy' Failed.\n");
		}

		return ret;
	}

	private void startDemo() {
		if (this.running) {
			return;
		}

		this.txtLog.append("Start demo ...\n");

		// 创建测试用原语
		final int num = 10;
		final ArrayList<Primitive> list = new ArrayList<Primitive>(num);

		for (int i = 0; i < num; ++i) {
			Primitive primitive = new Primitive();
			primitive.commit(new SubjectStuff(Utils.randomString(32)));
			primitive.commit(new PredicateStuff(Utils.randomInt()));
			primitive.commit(new ObjectiveStuff(Utils.randomInt() % 2 == 0 ? true : false));
			list.add(primitive);
		}

		this.running = true;

		Thread t = new Thread() {
			@Override
			public void run() {
				while (running) {
					Primitive primitive = list.remove(0);
					TalkService.getInstance().talk(identifier, primitive);

					if (list.isEmpty()) {
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								stopDemo();
							}
						});

						break;
					}

					try {
						Thread.sleep(Utils.randomInt(200, 500));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} // #while

				// 清空列表
				list.clear();
			}
		};
		t.start();
	}

	private void stopDemo() {
		if (!this.running) {
			return;
		}

		this.txtLog.append("Stop demo ...\n");

		this.btnStart.setEnabled(false);

		this.running = false;

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				btnStart.setEnabled(true);
				btnStop.setEnabled(false);
			}
		});
	}

	@Override
	public void dialogue(String identifier, final Primitive primitive) {
		Logger.i(MainActivity.class, "dialogue - " + primitive.subjects().get(0).getValueAsString());
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				txtLog.append("dialogue - " + primitive.subjects().get(0).getValueAsString() + "\n");
			}
		});
	}

	@Override
	public void contacted(final String identifier, String tag) {
		Logger.i(MainActivity.class, "contacted @" + identifier);

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				txtLog.append("contacted @" + identifier + "\n");
				btnStart.setEnabled(true);
			}
		});
	}

	@Override
	public void quitted(String identifier, String tag) {
		Logger.i(MainActivity.class, "quitted @" + identifier);
	}

	@Override
	public void suspended(String identifier, String tag, long timestamp,
			int mode) {
		Logger.i(MainActivity.class, "suspended");
	}

	@Override
	public void resumed(String identifier, String tag, long timestamp,
			Primitive primitive) {
		Logger.i(MainActivity.class, "resumed");
	}

	@Override
	public void failed(String identifier, String tag, TalkServiceFailure failure) {
		Logger.w(MainActivity.class, "failed");
		if (failure.getCode() == MessageErrorCode.CONNECT_TIMEOUT
			|| failure.getCode() == MessageErrorCode.CONNECT_FAILED) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					btnReady.setEnabled(true);
				}
			});
		}
		else {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					btnReady.setEnabled(true);
					btnStart.setEnabled(false);
					btnStop.setEnabled(false);
				}
			});
		}
	}
}
