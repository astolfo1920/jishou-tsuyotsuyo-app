package com.nemurhythm.app.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.nemurhythm.app.MainActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * AlarmManager への予約・取り消しと、予約内容の保存（再起動後の復元用）を担当する。
 * 予約は setAlarmClock を使うので、Doze 中や画面OFFでも指定時刻に発火する。
 */
public final class AlarmScheduler {
    private static final String TAG = "NemuAlarm";
    static final String PREFS = "nemu_alarm";
    private static final String KEY_SCHEDULED = "scheduled"; // 通常アラームの予約一覧
    private static final String KEY_SNOOZE = "snooze";       // スヌーズの予約（1件）
    static final String EXTRA_KEY = "key";
    static final String EXTRA_ALARM_ID = "alarmId";
    static final String EXTRA_LABEL = "label";

    private AlarmScheduler() {}

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 通常アラームをまとめて置き換える。以前の予約はすべて取り消す。 */
    public static void setAll(Context ctx, JSONArray alarms) {
        JSONArray old = readArray(ctx, KEY_SCHEDULED);
        for (int i = 0; i < old.length(); i++) {
            JSONObject o = old.optJSONObject(i);
            if (o != null) cancel(ctx, o.optString("key"));
        }
        JSONArray saved = new JSONArray();
        long now = System.currentTimeMillis();
        for (int i = 0; i < alarms.length(); i++) {
            JSONObject a = alarms.optJSONObject(i);
            if (a == null) continue;
            long at = a.optLong("at");
            if (at <= now) continue;
            String id = a.optString("id");
            JSONObject entry = entry("alarm:" + id, id, a.optString("label"), at);
            schedule(ctx, entry);
            saved.put(entry);
        }
        prefs(ctx).edit().putString(KEY_SCHEDULED, saved.toString()).apply();
    }

    /** スヌーズを1件予約する（前のスヌーズは取り消す）。 */
    public static void snooze(Context ctx, String alarmId, String label, long at) {
        clearSnooze(ctx);
        JSONObject entry = entry("snooze:" + alarmId, alarmId, label, at);
        schedule(ctx, entry);
        prefs(ctx).edit().putString(KEY_SNOOZE, entry.toString()).apply();
    }

    public static void clearSnooze(Context ctx) {
        String s = prefs(ctx).getString(KEY_SNOOZE, null);
        if (s == null) return;
        try { cancel(ctx, new JSONObject(s).optString("key")); } catch (JSONException ignored) {}
        prefs(ctx).edit().remove(KEY_SNOOZE).apply();
    }

    /** 端末の再起動後に、保存してある予約を復元する。 */
    public static void restore(Context ctx) {
        long now = System.currentTimeMillis();
        JSONArray list = readArray(ctx, KEY_SCHEDULED);
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o != null && o.optLong("at") > now) schedule(ctx, o);
        }
        String s = prefs(ctx).getString(KEY_SNOOZE, null);
        if (s != null) {
            try {
                JSONObject o = new JSONObject(s);
                if (o.optLong("at") > now) schedule(ctx, o);
            } catch (JSONException ignored) {}
        }
    }

    /** 正確なアラームを予約できるか（Android 12 以降は権限が必要）。 */
    public static boolean canScheduleExact(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        return am != null && am.canScheduleExactAlarms();
    }

    // ---------------------------------------------------------------

    private static JSONObject entry(String key, String alarmId, String label, long at) {
        JSONObject o = new JSONObject();
        try {
            o.put("key", key).put("alarmId", alarmId).put("label", label).put("at", at);
        } catch (JSONException ignored) {}
        return o;
    }

    private static void schedule(Context ctx, JSONObject e) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        String key = e.optString("key");
        long at = e.optLong("at");
        PendingIntent fire = firePendingIntent(ctx, key, e.optString("alarmId"), e.optString("label"));
        // ステータスバーの時計アイコンからアプリを開けるようにする
        PendingIntent show = PendingIntent.getActivity(ctx, 0,
            new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), fire);
        } catch (SecurityException ex) {
            // 正確なアラームの権限が無い場合は、多少ずれても鳴る方法で予約する
            Log.w(TAG, "exact alarm not permitted, falling back", ex);
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
        }
    }

    private static void cancel(Context ctx, String key) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null || key == null || key.isEmpty()) return;
        am.cancel(firePendingIntent(ctx, key, "", ""));
    }

    private static PendingIntent firePendingIntent(Context ctx, String key, String alarmId, String label) {
        Intent i = new Intent(ctx, AlarmReceiver.class)
            .setAction("com.nemurhythm.app.ALARM:" + key) // key ごとに別の PendingIntent にする
            .putExtra(EXTRA_KEY, key)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_LABEL, label);
        return PendingIntent.getBroadcast(ctx, key.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static JSONArray readArray(Context ctx, String key) {
        try {
            return new JSONArray(prefs(ctx).getString(key, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
}
