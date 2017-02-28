package net.cellcloud.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import net.cellcloud.common.Logger;

/**
 * 时间广播
 *
 * @author workerinchina@163.com
 */
public class TimeReceiver extends BroadcastReceiver {
	private TimeListener timeListener;
	private boolean isRegister = false;

	@Override
	public void onReceive(Context context, Intent intent) {
		Logger.i(TimeReceiver.class, "action: " + intent.getAction());
		Logger.i(TimeReceiver.class, "intent : ");
		Bundle bundle = intent.getExtras();
		for (String key : bundle.keySet()) {
			Logger.i(TimeReceiver.class, key + " : " + bundle.get(key));
		}
		if (Intent.ACTION_TIME_TICK.equals(intent.getAction())) {
			if (timeListener != null) {
				timeListener.onTimeTick();
			}
		}
	}

	public void registerReceiver(Context context, TimeListener timeListener) {
		if (!isRegister) {
			synchronized (TimeReceiver.this) {
				if (!isRegister) {
					try {
						IntentFilter filter = new IntentFilter();
						filter.addAction(Intent.ACTION_TIME_TICK);
						filter.setPriority(Integer.MAX_VALUE);
						context.registerReceiver(this, filter);
						this.timeListener = timeListener;
					}
					catch (Exception e) {
						e.printStackTrace();
					}
					isRegister = true;
				}
			}
		}
	}

	public void unRegisterReceiver(Context context) {
		if (isRegister) {
			synchronized (TimeReceiver.this) {
				if (isRegister) {
					try {
						context.unregisterReceiver(this);
					}
					catch (Exception e) {
						e.printStackTrace();
					}
					isRegister = false;
				}
			}
		}
	}

	public static interface TimeListener {
		/**
		 * 每分钟调用
		 */
		public void onTimeTick();
	}
}
