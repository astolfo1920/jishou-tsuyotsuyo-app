package com.nemurhythm.app.alarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.nemurhythm.app.MainActivity;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 鳴動の開始・停止。
 * 「止まらない通知」（FLAG_INSISTENT + 常駐）でアラーム音を鳴らし続け、
 * 全画面インテントでロック画面の上にアプリ（クイズ）を表示する。
 * 音はクイズに正解して stop() が呼ばれるまで止まらない。
 */
public final class AlarmRinger {
    static final String CHANNEL_ID = "alarm_ring_v1";
    private static final int NOTIFICATION_ID = 4201;
    private static final String KEY_RINGING = "ringing";

    private AlarmRinger() {}

    public static void start(Context ctx, String alarmId, String label) {
        JSONObject r = new JSONObject();
        try {
            r.put("alarmId", alarmId).put("label", label).put("since", System.currentTimeMillis());
        } catch (JSONException ignored) {}
        AlarmScheduler.prefs(ctx).edit().putString(KEY_RINGING, r.toString()).commit();

        ensureChannel(ctx);
        Intent open = new Intent(ctx, MainActivity.class)
            .putExtra(MainActivity.EXTRA_ALARM, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 1, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ " + (label == null || label.isEmpty() ? "アラーム" : label))
            .setContentText("クイズに正解するとアラームが止まります")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setSound(alarmSound(), android.media.AudioManager.STREAM_ALARM) // Android 7 以下用
            .build();
        n.flags |= Notification.FLAG_INSISTENT | Notification.FLAG_NO_CLEAR; // 止めるまで音を繰り返す

        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, n);
    }

    public static void stop(Context ctx) {
        AlarmScheduler.prefs(ctx).edit().remove(KEY_RINGING).commit();
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }

    /** 鳴動中なら {alarmId, label, since}、そうでなければ null。 */
    public static JSONObject ringing(Context ctx) {
        String s = AlarmScheduler.prefs(ctx).getString(KEY_RINGING, null);
        if (s == null) return null;
        try { return new JSONObject(s); } catch (JSONException e) { return null; }
    }

    static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "アラーム", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("起床アラームの鳴動");
        // USAGE_ALARM にすると、マナーモードでもアラーム音量で鳴る
        ch.setSound(alarmSound(), new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build());
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 600, 400, 600, 400});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    private static Uri alarmSound() {
        Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        return u;
    }
}
