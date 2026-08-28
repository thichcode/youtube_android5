# TizenTube Lite - Super Lightweight for Android 5 (API 21) - Design Doc

**Date:** 2026-08-28
**Author:** brainstorming with user
**Status:** Approved
**Target:** Box 32-bit Amlogic S905/S912, 1GB RAM, Android 5.1 (API 21-22), only ad-block

## 1. Overview
Fork ý tưởng nhưng không fork code Cobalt. Xây APK WebView wrapper siêu nhẹ thay vì build lại Cobalt 27 LTS (quá nặng cho 1GB RAM). Tận dụng `tizen01/TizenTube/dist/userScript.js` sẵn có, chỉ giữ đoạn chặn quảng cáo.

Mục tiêu: APK <12MB, RAM idle <200MB, install qua ADB/USB, play YouTube TV 720p mượt trên Android 5.1.

## 2. Goals / Non-Goals

**Goals:**
- Chạy được trên Android 5.0-5.1 (API 21/22), abi `armeabi-v7a` only
- Chặn quảng cáo YouTube (preroll/midroll/bypass)
- Điều khiển bằng remote TV (DPAD, Media keys)
- Fallback khi WebView hệ thống quá cũ

**Non-Goals (đã cắt theo yêu cầu "tối giản hết cỡ"):**
- Không SponsorBlock, không DeArrow, không speed control
- Không 4K/1080p60, limit 720p
- Không DRM L1, không cast DIAL phức tạp (chỉ YouTube TV leanback)
- Không hỗ trợ 64-bit, không hỗ trợ Android 6+ optimizations đặc biệt

## 3. Architecture

```
app/
├── src/main/
│   ├── AndroidManifest.xml  (leanback launcher only)
│   ├── java/io/gh/yourname/tizentubelite/
│   │   ├── MainActivity.java      // single Activity, fullscreen WebView
│   │   ├── JsBridge.java          // @JavascriptInterface for keys
│   │   └── WebViewHelper.java     // version check, UA, error reload
│   ├── res/layout/activity_main.xml (WebView match_parent)
│   └── assets/
│       └── userScript.js          // ad-block only, ~15KB stripped
├── build.gradle (minSdk 21, compileSdk 34, androidx.webkit:webkit:1.6.1)
└── proguard-rules.pro (keep JsBridge)
```

Package: `io.gh.yourname.tizentube.lite` (tránh conflict với `io.gh.reisxd.tizentube.cobalt` gốc)
Single-module, không NDK, không Chromium build, build time <2 phút.

## 4. Components

### 4.1 MainActivity
- Theme: `@android:style/Theme.NoTitleBar.Fullscreen` + `android:hardwareAccelerated="true"`
- `android:supportsPictureInPicture="false"` (API 21 chưa có, tránh crash)
- `WebView` with:
  ```java
  setJavaScriptEnabled(true)
  setDomStorageEnabled(true)
  setMediaPlaybackRequiresUserGesture(false)
  setMixedContentMode(MIXED_ALWAYS_ALLOW) // for http localhost DIAL if needed
  ```
- `WebViewClient.shouldOverrideUrlLoading` -> stay in WebView
- `WebChromeClient.onShowCustomView` -> fullscreen video
- `onKeyDown` -> handle DPAD_UP/DOWN/LEFT/RIGHT/CENTER/BACK/MEDIA_PLAY_PAUSE

### 4.2 JS Injection (ad-block only)
- Source: copy `D:\pupeteer\tizen01\TizenTube\dist\userScript.js` then strip:
  - Keep: `adBlock` / `skipAd` / `hideAdOverlay` logic
  - Remove: `sponsorBlock`, `deArrow`, `videoSpeed` blocks (search and delete ~60% file)
- Injection point: `webView.evaluateJavascript()` in `onPageFinished` + `onProgressChanged 100%`
- Additional JS: `navigator.userAgent` spoof is done natively via `webView.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 5.1; AFTM Build/LMY47M) AppleWebKit/537.36 Chrome/44.0.2403.133 Safari/537.36")` to force leanback

### 4.3 WebView Version Check
```java
PackageInfo pi = pm.getPackageInfo("com.google.android.webview", 0);
int version = parseMajor(pi.versionName); // e.g. "44.0.2403.119" -> 44
if (version < 50) showDialog("WebView quá cũ, cài WebView 60+ từ APKMirror để YouTube không báo lỗi")
```
Fallback: if WebView not found, try `com.android.webview`.

### 4.4 Remote Handling
- `keys` in `package.json` style mapped to Android KeyEvent: `KEYCODE_DPAD_CENTER`, `MEDIA_PLAY_PAUSE` etc.
- Focus handling: `webView.requestFocus()` on start

## 5. Build Config

```gradle
android {
  compileSdk 34
  defaultConfig {
    applicationId "io.gh.yourname.tizentube.lite"
    minSdk 21
    targetSdk 34
    versionCode 1
    versionName "1.0.0-lite"
    ndk { abiFilters "armeabi-v7a" }
  }
  buildTypes {
    release {
      minifyEnabled true
      shrinkResources true
      proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
  }
}
dependencies {
  implementation 'androidx.webkit:webkit:1.6.1' // supports API 21
  implementation 'androidx.appcompat:appcompat:1.6.1'
}
```

Manifest permissions minimal:
```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
<uses-feature android:name="android.software.leanback" android:required="false"/>
<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>
```
Removed: CAMERA, RECORD_AUDIO, ACCESS_COARSE_LOCATION, WRITE_EXTERNAL_STORAGE (không cần cho lite).

Size estimate: `aab` 6MB, `apk` universal 7-9MB, `armeabi-v7a` split 7MB.

## 6. Data Flow

1. Launcher -> MainActivity.onCreate -> WebViewHelper.checkVersion() -> if old show dialog else loadUrl("https://www.youtube.com/tv?app=desktop")
2. WebViewClient.onPageStarted -> inject loading spinner
3. onPageFinished -> evaluateJavascript(userScript.js ad-block only)
4. User presses DPAD -> Activity dispatches to WebView -> JS handles YouTube leanback focus
5. Video starts -> ad-block JS skips ad (mutationObserver on .ad-showing) -> hide overlay -> play main content

No native networking beyond WebView; no service DIAL for lite v1 (có thể thêm sau).

## 7. Error Handling

- **WebView crash** (`onRenderProcessGone` API 26+ not available on 21, so use `onReceivedError`): `webView.clearCache(true); webView.reload();` + toast
- **Network lost**: `onReceivedError` -> show offline layout with retry button
- **YouTube 1080p giật**: JS inject `player.setPlaybackQuality('medium')` // 720p, if RAM low detection via `ActivityManager.getMemoryInfo()`
- **WebView too old**: blocking dialog with link to `https://www.apkmirror.com/apk/google-inc/android-system-webview/`

## 8. Testing

- Emulator: AVD `android-21`, `armeabi-v7a`, RAM 512MB, heap 64m
- Real device: Amlogic S905 box Android 5.1.1 (1GB) via ADB `adb install tizentube-lite.apk`
- Manual cases: cold start <3s, play 720p no drop >10min, ad skip <1s, DPAD navigation, back exits gracefully
- No unit tests for v1 (lite), only manual + `adb logcat | grep -i webview`

## 9. Alternatives Considered

- A: Debloated Cobalt - rejected: too heavy (120MB+), OOM on 1GB
- C: Cobalt LTS 22/23 - rejected: still 60MB, engine outdated, maintenance burden

## 10. Rollout

1. Phase 1: Build debug APK, test on emulator API21
2. Phase 2: Test on real S905 box, measure RAM via `adb shell dumpsys meminfo`
3. Phase 3: Release `v1.0.0-lite` GitHub release with `lite-armeabi-v7a.apk` only
4. Future: if WebView 44 fails leanback, bundle minimal WebView via `androidx.webkit` update prompt

## 11. Risks

- System WebView 37-44 may be deprecated by YouTube TV (google may require Chrome 60+). Mitigation: guide user to update WebView APK.
- Ad-block JS may break when YouTube changes DOM. Mitigation: keep userScript.js syncable via GitHub raw fetch (optional v1.1).

---
Approved by user: 2026-08-28 (chose Approach B, approved 3/3 design sections)
