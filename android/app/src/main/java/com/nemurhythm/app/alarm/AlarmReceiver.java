package com.nemurhythm.app.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** AlarmManager から指定時刻に呼ばれ、鳴動を開始する。 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String key = intent.getStringExtra(AlarmScheduler.EXTRA_KEY);
        if (key != null && key.startsWith("snooze:")) AlarmScheduler.clearSnooze(ctx);
        AlarmRinger.start(ctx,
            intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID),
            intent.getStringExtra(AlarmScheduler.EXTRA_LABEL));
    }
}
