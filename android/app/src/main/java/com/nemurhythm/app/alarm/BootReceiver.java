package com.nemurhythm.app.alarm;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 端末の再起動やアプリ更新で消えた AlarmManager の予約を復元する。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        // exported なので他のアプリからも明示的に呼べてしまう。
        // システムだけが送れるアクション（protected broadcast）のときだけ処理する。
        String action = intent.getAction();
        if (action == null) return;
        switch (action) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_LOCKED_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
            case AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED:
                AlarmScheduler.restore(ctx);
                break;
            default:
                break;
        }
    }
}
