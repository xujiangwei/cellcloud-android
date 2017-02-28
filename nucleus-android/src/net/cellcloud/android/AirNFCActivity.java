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

import net.cellcloud.Version;
import net.cellcloud.airnfc.AirReceiver;
import net.cellcloud.airnfc.AirReceiverListener;
import net.cellcloud.airnfc.AirSender;
import net.cellcloud.airnfc.BlockingQueue;
import net.cellcloud.airnfc.Channel;
import net.cellcloud.airnfc.Recognizer;
import net.cellcloud.airnfc.RecognizerListener;
import net.cellcloud.airnfc.Transmitter;
import net.cellcloud.airnfc.TransmitterListener;
import net.cellcloud.common.LogLevel;
import net.cellcloud.common.Logger;
import net.cellcloud.core.Device;
import net.cellcloud.core.Nucleus;
import net.cellcloud.core.NucleusConfig;
import net.cellcloud.core.Role;
import net.cellcloud.exception.SingletonException;
import net.cellcloud.talk.Primitive;
import net.cellcloud.talk.TalkFailureCode;
import net.cellcloud.talk.TalkListener;
import net.cellcloud.talk.TalkServiceFailure;
import net.cellcloud.talk.dialect.ActionDialect;
import net.cellcloud.talk.dialect.ChunkDialect;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class AirNFCActivity extends Activity implements TalkListener {

	private Button btnReceiver;
	private Button btnSender;
	private Button btnStop;
	private EditText txtLog;

	private Recognizer recognizer;
	private Transmitter transmitter;
	private BlockingQueue queue;

	private AirReceiver receiver;
	private AirSender sender;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d("Lifecycle", "onCreate");

		setContentView(R.layout.activity_airnfc);

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
		config.role = Role.CONSUMER;
		config.device = Device.MOBILE;

		Nucleus nucleus = null;
		try {
			nucleus = Nucleus.createInstance(config, this.getApplication());
		} catch (SingletonException e) {
			Logger.log(AirNFCActivity.class, e, LogLevel.ERROR);
			nucleus = Nucleus.getInstance();
		}

		this.queue = new BlockingQueue();

		return nucleus.startup();
	}

	private void shutdown() {
		this.stopAll();

		Nucleus nucleus = Nucleus.getInstance();
		if (null != nucleus) {
			nucleus.shutdown();
		}
	}

	/**
	 * 配置 View
	 */
	private void configView() {
		this.btnReceiver = (Button) this.getWindow().getDecorView().findViewById(R.id.button_receiver);
		this.btnSender = (Button) this.getWindow().getDecorView().findViewById(R.id.button_sender);
		this.btnStop = (Button) this.getWindow().getDecorView().findViewById(R.id.button_stop);
		this.txtLog = (EditText) this.getWindow().getDecorView().findViewById(R.id.text_log);

		this.btnReceiver.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				startReceiver();
//				startRecognizer();
			}
		});

		this.btnSender.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				startSender();
//				startTransmitter();
			}
		});

		this.btnStop.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				stopAll();
			}
		});
	}

	protected void startReceiver() {
		if (null != this.receiver) {
			return;
		}

		this.receiver = new AirReceiver(Channel.Supersonic, new AirReceiverListener() {
			@Override
			public void onStarted(AirReceiver receiver) {
				
			}

			@Override
			public void onStopped(AirReceiver receiver) {
				
			}

			@Override
			public void onReceived(Channel channel, byte source, String text) {
				
			}
		});
		this.receiver.start();
	}

	protected void startSender() {
		if (null != this.sender) {
			return;
		}

		this.sender = new AirSender(Channel.Supersonic);
		this.sender.setId((byte)'1');
		this.sender.start();

		this.sender.send("5A");
	}

	protected boolean startRecognizer() {
		boolean ret = true;

		if (null == this.recognizer) {
			this.recognizer = new Recognizer(new RecognizerListener() {
				@Override
				public void onStarted(Recognizer recognizer) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							txtLog.append("Recognizer started\n");
						}
					});
				}

				@Override
				public void onStopped(Recognizer recognizer) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							txtLog.append("Recognizer stopped\n");
						}
					});
				}

				@Override
				public void onRecognized(Recognizer recognizer, final byte code) {
					Logger.i(this.getClass(), "Recognized: " + code);

					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							txtLog.append("Recognized: " + String.valueOf((char)code) + "\n");
						}
					});
				}
			});

			this.recognizer.start();
		}

		return ret;
	}

	protected void startTransmitter() {
		if (null == this.transmitter) {
			this.transmitter = new Transmitter(new TransmitterListener() {

				@Override
				public void onTransmitted(Transmitter transmitter, final byte data) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							txtLog.append("Transmitted: " + String.valueOf((char)data) + "\n");
						}
					});
				}

				@Override
				public void onStarted(Transmitter transmitter) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							txtLog.append("Transmitter started\n");
						}
					});
				}

				@Override
				public void onStopped(Transmitter transmitter) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							txtLog.append("Transmitter stopped\n");
						}
					});
				}
			}, this.queue);

			this.transmitter.start();
		}

		Thread thread = new Thread() {
			@Override
			public void run() {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						txtLog.append("write data to queue\n");
					}
				});

				queue.enqueue((byte)'1');
				queue.enqueue((byte)'1');
				queue.enqueue((byte)'1');

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				queue.enqueue((byte)'2');
				queue.enqueue((byte)'2');
				queue.enqueue((byte)'2');

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				queue.enqueue((byte)'3');
				queue.enqueue((byte)'3');
				queue.enqueue((byte)'3');
			}
		};
		thread.start();
	}

	protected void stopAll() {
		this.txtLog.append("Stop all ...\n");

		if (null != this.recognizer) {
			this.recognizer.stop();
			this.recognizer = null;
		}

		if (null != this.transmitter) {
			this.transmitter.stop();
			this.transmitter = null;
		}

		if (null != this.receiver) {
			this.receiver.stop();
			this.receiver = null;
		}

		if (null != this.sender) {
			this.sender.stop();
			this.sender = null;
		}

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
			}
		});
	}

	@Override
	public void dialogue(String identifier, final Primitive primitive) {

		final int c = 0;
//		Logger.i(MainActivity.class, "dialogue - [" + this.counts + "] " + primitive.subjects().get(0).getValueAsString());

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
		Logger.i(AirNFCActivity.class, "contacted @" + identifier);

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				txtLog.append("contacted @" + identifier + "\n");
			}
		});
	}

	@Override
	public void quitted(final String identifier, String tag) {
		Logger.i(AirNFCActivity.class, "quitted @" + identifier);

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				txtLog.append("contacted @" + identifier + "\n");
			}
		});
	}

	@Override
	public void failed(String tag, final TalkServiceFailure failure) {
		Logger.w(AirNFCActivity.class, "failed");
		if (failure.getCode() == TalkFailureCode.CALL_FAILED) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					txtLog.append("Failed calls cellet 'Dummy'!\n");
				}
			});
		}
		else if (failure.getCode() == TalkFailureCode.TALK_LOST) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					txtLog.append(failure.getDescription() + "\n");
				}
			});
		}
		else if (failure.getCode() == TalkFailureCode.NO_NETWORK) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					txtLog.append(failure.getDescription() + "\n");
				}
			});
		}
		else if (failure.getCode() == TalkFailureCode.RETRY_END) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					txtLog.append(failure.getDescription() + "\n");
				}
			});
		}
		else {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					txtLog.append(failure.getDescription() + "\n");
				}
			});
		}
	}
}
