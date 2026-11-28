package com.nemurhythm.app.alarm;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.nemurhythm.app.MainActivity;

import org.json.JSONException;
import org.json.JSONObject;

/** JS（js/app.js）から呼ぶネイティブアラーム機能。 */
@CapacitorPlugin(name = "NemuAlarm")
public class NemuAlarmPlugin extends Plugin {

    /** alarms: [{id, at(ms), label}] で予約を丸ごと置き換える。 */
    @PluginMethod
    public void setAlarms(PluginCall call) {
        JSArray alarms = call.getArray("alarms", new JSArray());
        AlarmScheduler.setAll(getContext(), alarms);
        call.resolve();
    }

    @PluginMethod
    public void snooze(PluginCall call) {
        Context ctx = getContext();
        AlarmRinger.stop(ctx);
        AlarmScheduler.snooze(ctx, call.getString("alarmId", ""), call.getString("label", ""),
            call.getLong("at", System.currentTimeMillis() + 5 * 60_000L));
        releaseLockScreen(); // 再鳴動時は全画面通知でまたロック画面の上に出る
        call.resolve();
    }

    /** クイズ正解で鳴動を止める。予約済みのスヌーズも取り消す。 */
    @PluginMethod
    public void stop(PluginCall call) {
        AlarmRinger.stop(getContext());
        AlarmScheduler.clearSnooze(getContext());
        releaseLockScreen();
        call.resolve();
    }

    /** 鳴動が終わったら、ロック画面の上にアプリを出したままにしない。 */
    private void releaseLockScreen() {
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).releaseShowOverLock();
    }

    @PluginMethod
    public void getRinging(PluginCall call) {
        JSONObject r = AlarmRinger.ringing(getContext());
        JSObject ret = new JSObject();
        if (r != null) {
            ret.put("alarmId", r.optString("alarmId"));
            ret.put("label", r.optString("label"));
            ret.put("since", r.optLong("since"));
        }
        call.resolve(ret);
    }

    /** 確実に鳴らすために必要な設定がそろっているか。 */
    @PluginMethod
    public void checkSetup(PluginCall call) {
        Context ctx = getContext();
        JSObject ret = new JSObject();
        ret.put("notifications", NotificationManagerCompat.from(ctx).areNotificationsEnabled());
        ret.put("exactAlarm", AlarmScheduler.canScheduleExact(ctx));
        boolean fullScreen = true;
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            fullScreen = nm != null && nm.canUseFullScreenIntent();
        }
        ret.put("fullScreen", fullScreen);
        call.resolve(ret);
    }

    /** type: notifications | exactAlarm | fullScreen の設定画面を開く。 */
    @PluginMethod
    public void openSettings(PluginCall call) {
        Context ctx = getContext();
        String type = call.getString("type", "notifications");
        Uri pkg = Uri.parse("package:" + ctx.getPackageName());
        Intent i;
        if ("notifications".equals(type)) {
            if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && getActivity() != null) {
                getActivity().requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4202);
                call.resolve();
                return;
            }
            i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
        } else if ("exactAlarm".equals(type) && Build.VERSION.SDK_INT >= 31) {
            i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg);
        } else if ("fullScreen".equals(type) && Build.VERSION.SDK_INT >= 34) {
            i = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg);
        } else {
            i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg);
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
        call.resolve();
    }
}
