package com.nemurhythm.app.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 端末の再起動やアプリ更新で消えた AlarmManager の予約を復元する。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        AlarmScheduler.restore(ctx);
    }
}
