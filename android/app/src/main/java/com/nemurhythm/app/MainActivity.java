package com.nemurhythm.app;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;
import com.nemurhythm.app.alarm.AlarmRinger;
import com.nemurhythm.app.alarm.NemuAlarmPlugin;

public class MainActivity extends BridgeActivity {
    public static final String EXTRA_ALARM = "nemu_alarm";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NemuAlarmPlugin.class);
        super.onCreate(savedInstanceState);
        handleAlarmIntent(getIntent());

        // Android 13 以降は通知の許可が無いとアラームが鳴らない
        if (Build.VERSION.SDK_INT >= 33
            && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4201);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAlarmIntent(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        // 鳴動中でなくなったら、ロック画面上への表示をやめる
        if (AlarmRinger.ringing(this) == null) setShowOverLock(false);
    }

    /** アラームで起動されたときは、ロック画面の上に表示して画面をONにする。 */
    private void handleAlarmIntent(Intent intent) {
        boolean fromAlarm = intent != null && intent.getBooleanExtra(EXTRA_ALARM, false);
        if (fromAlarm || AlarmRinger.ringing(this) != null) setShowOverLock(true);
    }

    /**
     * 鳴動が終わったとき（クイズ正解・スヌーズ）に呼ぶ。ロック画面上への表示と画面ONの維持をやめる。
     * これをしないと、onResume が来るまでアプリ全体が PIN なしで見られる状態のまま残る。
     */
    public void releaseShowOverLock() {
        runOnUiThread(() -> setShowOverLock(false));
    }

    private void setShowOverLock(boolean on) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(on);
            setTurnScreenOn(on);
            if (on) {
                KeyguardManager km = getSystemService(KeyguardManager.class);
                // スワイプだけのロックは外す。PIN・指紋などのロックは解除しない（クイズはロック画面の上で解ける）
                if (km != null && !km.isKeyguardSecure()) km.requestDismissKeyguard(this, null);
            }
        } else {
            int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
            if (on) getWindow().addFlags(flags); else getWindow().clearFlags(flags);
        }
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}
