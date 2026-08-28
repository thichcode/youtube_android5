# TizenTube Lite Android 5 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build APK 7-9MB WebView wrapper chạy trên Android 5.1 API 21 (armeabi-v7a) chặn quảng cáo YouTube TV leanback, điều khiển remote, fallback khi WebView cũ.

**Architecture:** Single-module Android Studio app, 1 Activity fullscreen WebView + JsBridge inject ad-block-only userScript.js (strip từ tizen01/TizenTube). Không NDK/Cobalt, chỉ SDK 34 + androidx.webkit:1.6.1. Permissions tối thiểu.

**Tech Stack:** Android Studio Hedgehog+, Gradle 8, compileSdk 34, minSdk 21 targetSdk 34, androidx.webkit:webkit:1.6.1, appcompat 1.6.1, Java 17

---

### File Structure

- `D:\pupeteer\tizentube\app\build.gradle` - module build config (minSdk 21, abiFilters armeabi-v7a, shrink)
- `D:\pupeteer\tizentube\app\src\main\AndroidManifest.xml` - leanback launcher, permissions
- `D:\pupeteer\tizentube\app\src\main\java\io\gh\yourname\tizentubelite\MainActivity.java` - WebView lifecycle, UA, fullscreen
- `D:\pupeteer\tizentube\app\src\main\java\io\gh\yourname\tizentubelite\JsBridge.java` - @JavascriptInterface D-pad bridge
- `D:\pupeteer\tizentube\app\src\main\java\io\gh\yourname\tizentubelite\WebViewHelper.java` - version check, error reload
- `D:\pupeteer\tizentube\app\src\main\res\layout\activity_main.xml` - WebView match_parent
- `D:\pupeteer\tizentube\app\src\main\assets\userScript.js` - ad-block only stripped
- `D:\pupeteer\tizentube\app\proguard-rules.pro` - keep JsBridge
- `D:\pupeteer\tizentube\settings.gradle`, `build.gradle` (root), `gradle.properties`
- Test: manual via `adb install` + `emulator -avd api21_512`

---

### Task 1: Scaffold Android Studio Project

**Files:**
- Create: `D:\pupeteer\tizentube\settings.gradle`
- Create: `D:\pupeteer\tizentube\build.gradle`
- Create: `D:\pupeteer\tizentube\gradle.properties`
- Create: `D:\pupeteer\tizentube\app\build.gradle`
- Create: `D:\pupeteer\tizentube\app\proguard-rules.pro`

- [ ] **Step 1: Create settings.gradle**

```gradle
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "TizenTubeLite"
include ':app'
```

- [ ] **Step 2: Create root build.gradle**

```gradle
plugins {
    id 'com.android.application' version '8.2.0' apply false
}
task clean(type: Delete) {
    delete rootProject.buildDir
}
```

- [ ] **Step 3: Create gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=false
```

- [ ] **Step 4: Create app/build.gradle (core of lite)**

```gradle
plugins {
    id 'com.android.application'
}

android {
    namespace 'io.gh.yourname.tizentubelite'
    compileSdk 34
    defaultConfig {
        applicationId "io.gh.yourname.tizentubelite"
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
        debug {
            minifyEnabled false
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.webkit:webkit:1.6.1'
}
```

- [ ] **Step 5: Create proguard-rules.pro**

```proguard
-keepclassmembers class io.gh.yourname.tizentubelite.JsBridge {
    public *;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
```

- [ ] **Step 6: Verify scaffold sync**

Run: `cd D:\pupeteer\tizentube && .\gradlew tasks --all` (requires Android SDK 34 installed)
Expected: `BUILD SUCCESSFUL` (no errors about minSdk)

- [ ] **Step 7: Commit**

```bash
git add settings.gradle build.gradle gradle.properties app/build.gradle app/proguard-rules.pro
git commit -m "feat: scaffold Android Studio project for lite API21 armeabi-v7a"
```

---

### Task 2: Ad-block Only userScript.js

**Files:**
- Create: `D:\pupeteer\tizentube\app\src\main\assets\userScript.js`
- Modify: `D:\pupeteer\tizen01\TizenTube\dist\userScript.js` (read-only source)

- [ ] **Step 1: Write failing check - verify assets dir missing**

Run: `Test-Path D:\pupeteer\tizentube\app\src\main\assets\userScript.js`
Expected: `False` (file not exists yet)

- [ ] **Step 2: Create stripped ad-block script**

Copy source then strip. Read `D:\pupeteer\tizen01\TizenTube\dist\userScript.js`, keep only adBlock section.

Create `D:\pupeteer\tizentube\app\src\main\assets\userScript.js`:
```javascript
// TizenTube Lite - ad-block only (stripped from TizenTube 1.7.0)
// Removed: sponsorBlock, deArrow, videoSpeed
(function() {
    'use strict';
    function skipAd() {
        const video = document.querySelector('video');
        const ad = document.querySelector('.ad-showing');
        if (ad && video) {
            video.currentTime = video.duration || 9999;
            const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern');
            if (skipBtn) skipBtn.click();
        }
        const overlay = document.querySelector('.ytp-ad-overlay-container, .ytp-ad-image-overlay');
        if (overlay) overlay.style.display = 'none';
    }
    function hideOverlays() {
        document.querySelectorAll('.ytp-ad-overlay-container, .ytp-ad-image-overlay, .ad-container').forEach(e => e.style.display='none');
    }
    const observer = new MutationObserver(() => { skipAd(); hideOverlays(); });
    observer.observe(document.documentElement, {childList:true, subtree:true});
    setInterval(skipAd, 500);
    // limit quality to 720p on low RAM boxes
    setInterval(() => {
        try {
            const player = document.querySelector('#movie_player');
            if (player && player.setPlaybackQuality) player.setPlaybackQuality('medium');
        } catch(e) {}
    }, 3000);
    console.log('[TizenTubeLite] ad-block injected');
})();
```

- [ ] **Step 3: Verify stripped content**

Run: `Select-String -Path D:\pupeteer\tizentube\app\src\main\assets\userScript.js -Pattern "sponsorBlock|deArrow|videoSpeed" | Measure-Object`
Expected: `Count 0` (no remaining heavy features)

Run: `Select-String -Path D:\pupeteer\tizentube\app\src\main\assets\userScript.js -Pattern "skipAd|ad-showing"`
Expected: `Count >=2`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/assets/userScript.js
git commit -m "feat: add ad-block-only userScript.js stripped from TizenTube 1.7.0"
```

---

### Task 3: AndroidManifest + Layout

**Files:**
- Create: `D:\pupeteer\tizentube\app\src\main\AndroidManifest.xml`
- Create: `D:\pupeteer\tizentube\app\src\main\res\layout\activity_main.xml`
- Create: `D:\pupeteer\tizentube\app\src\main\res\values\strings.xml`
- Create: `D:\pupeteer\tizentube\app\src\main\res\values\styles.xml`

- [ ] **Step 1: Write AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="io.gh.yourname.tizentubelite">

    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    <uses-permission android:name="android.permission.WAKE_LOCK"/>

    <uses-feature android:name="android.software.leanback" android:required="false"/>
    <uses-feature android:name="android.hardware.touchscreen" android:required="false"/>
    <uses-feature android:glEsVersion="0x00020000" android:required="true"/>

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:banner="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:hardwareAccelerated="true"
        android:theme="@style/AppTheme.Fullscreen">
        <activity
            android:name=".MainActivity"
            android:launchMode="singleTask"
            android:configChanges="keyboard|keyboardHidden|navigation|orientation|screenSize|smallestScreenSize|screenLayout|uiMode"
            android:hardwareAccelerated="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
                <category android:name="android.intent.category.LEANBACK_LAUNCHER"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 2: Write activity_main.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">
    <WebView
        android:id="@+id/webview"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>
    <TextView
        android:id="@+id/offlineView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center"
        android:text="No network - press BACK to retry"
        android:textColor="#FFFFFF"
        android:visibility="gone"/>
</FrameLayout>
```

- [ ] **Step 3: Write strings.xml**

```xml
<resources>
    <string name="app_name">TizenTube Lite</string>
    <string name="webview_too_old">WebView too old (v%1$d). Please update Android System WebView via APKMirror.</string>
</resources>
```

- [ ] **Step 4: Write styles.xml**

```xml
<resources>
    <style name="AppTheme.Fullscreen" parent="android:Theme.NoTitleBar.Fullscreen">
        <item name="android:windowBackground">@android:color/black</item>
    </style>
</resources>
```

- [ ] **Step 5: Verify manifest merges**

Run: `cd D:\pupeteer\tizentube && .\gradlew :app:processDebugMainManifest`
Expected: `BUILD SUCCESSFUL`, check `app\build\intermediates\merged_manifest\...AndroidManifest.xml` contains `LEANBACK_LAUNCHER`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/layout/activity_main.xml app/src/main/res/values/strings.xml app/src/main/res/values/styles.xml
git commit -m "feat: add manifest and layout for leanback WebView"
```

---

### Task 4: WebViewHelper + JsBridge

**Files:**
- Create: `D:\pupeteer\tizentube\app\src\main\java\io\gh\yourname\tizentubelite\WebViewHelper.java`
- Create: `D:\pupeteer\tizentube\app\src\main\java\io\gh\yourname\tizentubelite\JsBridge.java`

- [ ] **Step 1: Write WebViewHelper.java**

```java
package io.gh.yourname.tizentubelite;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class WebViewHelper {
    public static final String YT_TV_URL = "https://www.youtube.com/tv";
    public static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 5.1; AFTM Build/LMY47M) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.133 Safari/537.36";

    public static int getWebViewMajorVersion(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            String[] pkgs = {"com.google.android.webview", "com.android.webview"};
            for (String p : pkgs) {
                try {
                    PackageInfo pi = pm.getPackageInfo(p, 0);
                    String v = pi.versionName; // e.g. 44.0.2403.119
                    if (v != null) return Integer.parseInt(v.split("\\.")[0]);
                } catch (PackageManager.NameNotFoundException ignored) {}
            }
        } catch (Exception ignored) {}
        return 0;
    }

    public static boolean isWebViewTooOld(Context ctx) {
        return getWebViewMajorVersion(ctx) > 0 && getWebViewMajorVersion(ctx) < 50;
    }
}
```

- [ ] **Step 2: Write JsBridge.java**

```java
package io.gh.yourname.tizentubelite;

import android.webkit.JavascriptInterface;
import android.widget.Toast;
import android.content.Context;

public class JsBridge {
    private Context ctx;
    public JsBridge(Context ctx) { this.ctx = ctx; }

    @JavascriptInterface
    public void onAdBlocked(String msg) {
        // no-op, just for logging via JS
    }

    @JavascriptInterface
    public void showToast(String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }
}
```

- [ ] **Step 3: Run unit check (compile)**

Run: `cd D:\pupeteer\tizentube && .\gradlew :app:compileDebugJavaWithJavac`
Expected: `BUILD SUCCESSFUL` (no missing JavascriptInterface)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/io/gh/yourname/tizentubelite/WebViewHelper.java app/src/main/java/io/gh/yourname/tizentubelite/JsBridge.java
git commit -m "feat: add WebViewHelper version check and JsBridge"
```

---

### Task 5: MainActivity (WebView lifecycle + injection)

**Files:**
- Create: `D:\pupeteer\tizentube\app\src\main\java\io\gh\yourname\tizentubelite\MainActivity.java`

- [ ] **Step 1: Write MainActivity.java**

```java
package io.gh.yourname.tizentubelite;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    private WebView webView;
    private TextView offlineView;
    private String userScript;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);
        offlineView = findViewById(R.id.offlineView);

        if (WebViewHelper.isWebViewTooOld(this)) {
            int v = WebViewHelper.getWebViewMajorVersion(this);
            new AlertDialog.Builder(this)
                .setTitle("WebView too old")
                .setMessage(getString(R.string.webview_too_old, v))
                .setPositiveButton("Continue", (d,w)-> load())
                .setNegativeButton("Exit", (d,w)-> finish())
                .setCancelable(false).show();
        } else {
            load();
        }
    }

    private void load() {
        userScript = loadAsset("userScript.js");
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(WebViewHelper.USER_AGENT);
        webView.addJavascriptInterface(new JsBridge(this), "TizenLite");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (userScript != null) view.evaluateJavascript(userScript, null);
            }
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                offlineView.setVisibility(android.view.View.VISIBLE);
                view.setVisibility(android.view.View.GONE);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl(WebViewHelper.YT_TV_URL);
        webView.requestFocus();
    }

    private String loadAsset(String name) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open(name)))) {
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack(); return true;
        }
        if (offlineView.getVisibility()==android.view.View.VISIBLE) {
            offlineView.setVisibility(android.view.View.GONE);
            webView.setVisibility(android.view.View.VISIBLE);
            webView.reload(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
```

- [ ] **Step 2: Fix missing R import check**

Run: `cd D:\pupeteer\tizentube && .\gradlew :app:compileDebugJavaWithJavac`
Expected: `BUILD SUCCESSFUL` - if fails due to R not found, add `import io.gh.yourname.tizentubelite.R;` (generated) and re-run.

- [ ] **Step 3: Verify APK builds**

Run: `cd D:\pupeteer\tizentube && .\gradlew :app:assembleDebug`
Expected: `app\build\outputs\apk\debug\app-debug.apk` exists, size ~7-10MB

Run: `Get-Item D:\pupeteer\tizentube\app\build\outputs\apk\debug\app-debug.apk | Select Length`
Expected: `Length < 15MB`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/io/gh/yourname/tizentubelite/MainActivity.java
git commit -m "feat: add MainActivity WebView with ad-block injection and remote handling"
```

---

### Task 6: Release Build + Manual Test

**Files:**
- Modify: `D:\pupeteer\tizentube\app\build.gradle` (verify release)

- [ ] **Step 1: Build release APK**

Run: `cd D:\pupeteer\tizentube && .\gradlew :app:assembleRelease`
Expected: `app\build\outputs\apk\release\app-release.apk` (unsigned, or with debug keystore if no signingConfig). Check `aapt dump badging` shows `sdkVersion:'21' supports-screens`.

- [ ] **Step 2: Test on emulator API 21**

Run:
```bash
emulator -avd api21_512 -no-snapshot -wipe-data &
adb wait-for-device
adb install -r D:\pupeteer\tizentube\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n io.gh.yourname.tizentubelite/.MainActivity
adb logcat -s "TizenTubeLite" "WebView" "chromium"
```
Expected: Activity launches, log shows `[TizenTubeLite] ad-block injected`, YouTube TV loads, ad overlay hidden.

- [ ] **Step 3: Test on real S905 box (if available)**

Run: `adb connect <box-ip>:5555; adb install -r app-debug.apk`
Manual checks: DPAD navigation, play video 720p, trigger ad (search popular video), verify skip <1s, no SponsorBlock UI, memory: `adb shell dumpsys meminfo io.gh.yourname.tizentubelite` PSS <200MB.

- [ ] **Step 4: Commit + tag**

```bash
git tag v1.0.0-lite
git log --oneline -5
```

---

## Self-Review

**Spec coverage:** All 3 sections mapped - arch (Task1+3), components/data flow (Task2+4+5), build/error/test (Task6). Permissions, abiFilters, userScript stripping all have tasks.

**Placeholder scan:** No TBD/TODO, all code blocks complete with exact paths, commands include expected outputs.

**Type consistency:** Package `io.gh.yourname.tizentubelite` consistent across manifest, build.gradle, Java. `WebViewHelper.YT_TV_URL`/`USER_AGENT` used in MainActivity. `JsBridge` kept via proguard.

