# ネムリズム（睡眠・起床管理アプリ）

クイズに正解しないと止まらないアラームと、睡眠記録・スケジュール管理のアプリ。

- **Web 版**: `睡眠管理アプリ_モックアップ/`（ビルド不要の HTML/CSS/JS）。Vercel で公開
- **Android 版**: 同じ Web コードを Capacitor でアプリ化（`android/`）

## Android 版の特徴

Web 版はスマホの画面を消すとアラームが鳴りませんが、Android 版は次のように動きます。

- 画面OFF・アプリ終了中・再起動後でも、指定時刻に鳴る（`AlarmManager.setAlarmClock`）
- ロック画面の上にクイズ画面を表示し、画面を自動で点灯
- アラーム音量で鳴る（マナーモードでも鳴る）。クイズに正解するまで止まらない
- スヌーズ中に画面を消しても再鳴動する

ネイティブ側のコード: `android/app/src/main/java/com/nemurhythm/app/alarm/`
JS 側の連携: `js/app.js` の「Android アプリ連携」（`isNative` のときだけ有効）

## Android 版のビルド

必要なもの: Node.js、Android Studio（Android SDK）、**JDK 21**
（Android Studio 同梱の Java 25 では Gradle 8.14 が動かないため）

```powershell
npm install
npm run sync                      # Web コードを www/ にコピーして android/ に反映
$env:JAVA_HOME = "<JDK 21 のパス>"
cd android
.\gradlew.bat assembleDebug       # → android/app/build/outputs/apk/debug/app-debug.apk
```

Android Studio で開く場合は `npm run open:android`。
Settings → Build Tools → Gradle → Gradle JDK を JDK 21 にしてください。

Web 側（`睡眠管理アプリ_モックアップ/`）を変更したら、毎回 `npm run sync` を実行してからビルドします。

### スマホへのインストール

1. スマホの「設定 → デバイス情報 → ビルド番号」を7回タップして開発者向けオプションを有効にし、USB デバッグをオンにする
2. USB でつないで `adb install -r android/app/build/outputs/apk/debug/app-debug.apk`
   （または APK ファイルをスマホに送ってタップしてインストール）
3. 初回起動時に「通知を許可」を選ぶ。アラーム画面に設定の案内が出たら、それに従ってオンにする
