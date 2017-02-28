/*
-----------------------------------------------------------------------------
This source file is part of Cell Cloud.

Copyright (c) 2009-2016 Cell Cloud Team (www.cellcloud.net)

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
import java.util.concurrent.atomic.AtomicInteger;

import net.cellcloud.Version;
import net.cellcloud.common.LogLevel;
import net.cellcloud.common.Logger;
import net.cellcloud.core.Nucleus;
import net.cellcloud.core.NucleusConfig;
import net.cellcloud.exception.SingletonException;
import net.cellcloud.talk.Primitive;
import net.cellcloud.talk.TalkCapacity;
import net.cellcloud.talk.TalkFailureCode;
import net.cellcloud.talk.TalkListener;
import net.cellcloud.talk.TalkService;
import net.cellcloud.talk.TalkServiceFailure;
import net.cellcloud.talk.dialect.ActionDialect;
import net.cellcloud.talk.dialect.ChunkDialect;
import net.cellcloud.talk.stuff.AttributiveStuff;
import net.cellcloud.talk.stuff.ObjectiveStuff;
import net.cellcloud.talk.stuff.PredicateStuff;
import net.cellcloud.talk.stuff.SubjectStuff;
import net.cellcloud.util.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends Activity implements TalkListener {

	private final String address = "192.168.0.198";
	private final int port = 7000;
	private final String identifier = "Dummy";

	private Button btnReady;
	private Button btnStart;
	private Button btnStop;
	private EditText txtLog;

	private AtomicInteger counts = new AtomicInteger(0);
	private boolean running = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d("Lifecycle", "onCreate");

		setContentView(R.layout.activity_main);

		this.configView();

		this.startup();

		this.txtLog.append("Nucleus version " + Version.getNumbers() + "\n");
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

		Nucleus nucleus = null;
		try {
			nucleus = Nucleus.createInstance(config, this.getApplication());
		} catch (SingletonException e) {
			Logger.log(MainActivity.class, e, LogLevel.ERROR);
			nucleus = Nucleus.getInstance();
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

//				startChunkDemo();
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

		TalkCapacity capacity = new TalkCapacity(true, 3, 6000);
//		capacity.setBlocking(true);
		boolean ret = talkService.call(new String[]{this.identifier}, new InetSocketAddress(this.address, this.port), capacity);
		if (ret) {
			this.txtLog.append("Calling cellet '"+ this.identifier +"' ...\n");
		}
		else {
			this.txtLog.append("Call cellet '" + this.identifier + "' Failed.\n");
			talkService.hangUp(new String[]{this.identifier});
		}

		return ret;
	}

	protected void startChunkDemo() {
		if (this.running) {
			return;
		}

		this.txtLog.append("Start chunk demo ...\n");

		int num = 3;

		for (int i = 0; i < num; ++i) {
			byte[] data = new byte[50];
			for (int n = 0; n < data.length; ++n) {
				data[n] = (byte) Utils.randomInt();
			}
			ChunkDialect chunk = new ChunkDialect("demo", "Chunk007", num * data.length, i, num, data, data.length);
			TalkService.getInstance().talk(this.identifier, chunk);
		}
	}

	protected void startDemo() {
		if (this.running) {
			return;
		}

		this.txtLog.append("Start demo ...\n");

		this.counts.set(0);

		final int num = 5;

		// 创建测试用原语
		final ArrayList<Primitive> primList = new ArrayList<Primitive>(num);

		for (int i = 0; i < num; ++i) {
			Primitive primitive = new Primitive();
			primitive.commit(new SubjectStuff(i));
			primitive.commit(new PredicateStuff(Utils.randomString(1024)));
			primitive.commit(new ObjectiveStuff(Utils.randomInt() % 2 == 0 ? true : false));

			JSONObject json = new JSONObject();
			try {
				json.put("name", "徐江威");
				json.put("timestamp", System.currentTimeMillis());

				JSONObject phone = new JSONObject();
				phone.put("name", "iPhone");
				phone.put("vendor", "Apple");
				json.put("phone", phone);

				JSONObject c1 = new JSONObject();
				c1.put("name", "ThinkPad");
				c1.put("vendor", "Lenovo 联想");

				JSONObject c2 = new JSONObject();
				c2.put("name", "MacBook Pro");
				c2.put("vendor", "Apple");

				JSONArray computers = new JSONArray();
				computers.put(c1);
				computers.put(c2);

				json.put("computer", computers);
			} catch (JSONException e) {
				e.printStackTrace();
			}

			primitive.commit(new AttributiveStuff(json.toString()));
			primList.add(primitive);
		}

		// 创建测试用方言
		final ArrayList<ActionDialect> dialectList = new ArrayList<ActionDialect>(num);
		for (int i = 0; i < num; ++i) {
			ActionDialect ad = new ActionDialect();
			ad.setAction("test");

			JSONObject json = null;
			try {
				json = new JSONObject("{\"total\":26,\"authorized\":true,\"root\":[{\"id\":\"8a000217444e1a0c01444e26ebdc0001\",\"summary\":\"as\",\"servicelevelSymbol\":\"二级\",\"stateName\":\"已制定变更计划\",\"isMajor\":\"2\",\"isOvertime\":\"暂无\",\"code\":\"Incident00000005\",\"updatedOn\":\"2014-02-20 15:18:17\"},{\"id\":\"8a0002d24449830a0144498955970001\",\"summary\":\"bb\",\"servicelevelSymbol\":\"二级\",\"stateName\":\"已执行变更任务\",\"isMajor\":\"2\",\"isOvertime\":\"暂无\",\"code\":\"Incident00000004\",\"updatedOn\":\"2014-02-19 17:47:13\"},{\"id\":\"8a0002d2444977bd0144497c3caf0001\",\"summary\":\"vv\",\"servicelevelSymbol\":\"二级\",\"stateName\":\"已执行变更任务\",\"isMajor\":\"2\",\"isOvertime\":\"暂无\",\"code\":\"Incident00000003\",\"updatedOn\":\"2014-02-19 17:32:25\"},{\"id\":\"8a0002d2444971420144497263530001\",\"summary\":\"dd\",\"servicelevelSymbol\":\"二级\",\"stateName\":\"已执行变更任务\",\"isMajor\":\"2\",\"isOvertime\":\"暂无\",\"code\":\"Incident00000002\",\"updatedOn\":\"2014-02-19 17:21:51\"},{\"id\":\"8a0002d244496e050144496f21370001\",\"summary\":\"sss\",\"servicelevelSymbol\":\"二级\",\"stateName\":\"已执行变更任务\",\"isMajor\":\"2\",\"isOvertime\":\"暂无\",\"code\":\"Incident00000001\",\"updatedOn\":\"2014-02-19 17:18:10\"},{\"id\":\"8a0002bd446d12d301446d183d480001\",\"summary\":\"在郑州\",\"servicelevelSymbol\":\"二级\",\"stateName\":\"已关闭\",\"isMajor\":\"2\",\"isOvertime\":\"暂无\",\"code\":\"Incident00000012\",\"updatedOn\":\"2009-02-12 00:00:00\"}],\"success\":true}");
			} catch (JSONException e) {
				e.printStackTrace();
			}
			ad.appendParam("data", json.toString());

			dialectList.add(ad);
		}

		this.running = true;

		Thread t = new Thread() {
			@Override
			public void run() {
				while (running) {
					if (!primList.isEmpty()) {
						Primitive primitive = primList.remove(0);
						if (!TalkService.getInstance().talk(identifier, primitive)) {
							Logger.e(MainActivity.class, "Talk error");
						}
					}

					try {
						Thread.sleep(Utils.randomInt(1000, 1500));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					if (!dialectList.isEmpty()) {
						ActionDialect dialect = dialectList.remove(0);
						TalkService.getInstance().talk(identifier, dialect);
					}

					try {
						Thread.sleep(Utils.randomInt(1000, 1500));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

					if (primList.isEmpty() && dialectList.isEmpty()) {
						try {
							Thread.sleep(Utils.randomInt(1000, 2000));
						} catch (InterruptedException e) {
							e.printStackTrace();
						}

						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								stopDemo();
							}
						});

						break;
					}
				} // #while
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
		final int c = this.counts.addAndGet(1);

		Logger.i(MainActivity.class, "dialogue - [" + this.counts + "] " + primitive.subjects().get(0).getValueAsString());

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (primitive.isDialectal()) {
					if (primitive.getDialect() instanceof ActionDialect) {
						ActionDialect ad = (ActionDialect) primitive.getDialect();
						txtLog.append("dialogue - [" + c + "] " + ad.getAction() + " - " + ad.getParamAsString("data").length() + "\n");
					}
					else if (primitive.getDialect() instanceof ChunkDialect) {
						ChunkDialect cd = (ChunkDialect) primitive.getDialect();
						txtLog.append("dialogue - [" + c + "] " + cd.getSign() + " - " + cd.getChunkIndex() + "/" + cd.getChunkNum() + "\n");
					}
				}
				else {
					txtLog.append("dialogue (primitive) - [" + c + "] " + primitive.subjects().get(0).getValueAsString() + "\n");
				}
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
	public void quitted(final String identifier, String tag) {
		Logger.i(MainActivity.class, "quitted @" + identifier);

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				txtLog.append("contacted @" + identifier + "\n");
				btnStart.setEnabled(false);
			}
		});
	}

	@Override
	public void failed(String tag, final TalkServiceFailure failure) {
		Logger.w(MainActivity.class, "failed");
		if (failure.getCode() == TalkFailureCode.CALL_FAILED) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					btnReady.setEnabled(true);
					txtLog.append("Failed calls cellet 'Dummy'!\n");
				}
			});
		}
		else if (failure.getCode() == TalkFailureCode.TALK_LOST) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					btnReady.setEnabled(true);
					btnStart.setEnabled(false);
					btnStop.setEnabled(false);
					txtLog.append(failure.getDescription() + "\n");
				}
			});
		}
		else if (failure.getCode() == TalkFailureCode.NO_NETWORK) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					btnReady.setEnabled(true);
					btnStart.setEnabled(false);
					btnStop.setEnabled(false);
					txtLog.append(failure.getDescription() + "\n");
				}
			});
		}
		else if (failure.getCode() == TalkFailureCode.RETRY_END) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					btnReady.setEnabled(true);
					btnStart.setEnabled(false);
					btnStop.setEnabled(false);
					txtLog.append(failure.getDescription() + "\n");
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
					txtLog.append(failure.getDescription() + "\n");
				}
			});
		}
	}
}
