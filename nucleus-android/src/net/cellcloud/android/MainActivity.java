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

import net.cellcloud.common.LogLevel;
import net.cellcloud.common.Logger;
import net.cellcloud.core.Nucleus;
import net.cellcloud.core.NucleusConfig;
import net.cellcloud.exception.SingletonException;
import net.cellcloud.talk.Primitive;
import net.cellcloud.talk.TalkListener;
import net.cellcloud.talk.TalkService;
import net.cellcloud.talk.TalkServiceFailure;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MainActivity extends Activity implements TalkListener {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d("Lifecycle", "onCreate");

		setContentView(R.layout.activity_main);

		configView();

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
		Nucleus nucleus = Nucleus.getInstance();
		nucleus.shutdown();
	}

	private void configView() {
		Button btnReady = (Button) this.getWindow().getDecorView().findViewById(R.id.button_ready);
		btnReady.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				ready();
			}
		});
	}

	private void ready() {
		TalkService talkService = Nucleus.getInstance().getTalkService();
		if (talkService.hasListener(this)) {
			talkService.addListener(this);
		}

		boolean ret = talkService.call("Dummy", new InetSocketAddress("192.168.1.109", 7000));

		Logger.i(MainActivity.class, "ready : " + (ret ? "yes" : "no"));
	}

	@Override
	public void dialogue(String identifier, Primitive primitive) {
		Logger.i(MainActivity.class, "dialogue");
	}

	@Override
	public void contacted(String identifier, String tag) {
		Logger.i(MainActivity.class, "contacted");
	}

	@Override
	public void quitted(String identifier, String tag) {
		Logger.i(MainActivity.class, "quitted");
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
	}
}
