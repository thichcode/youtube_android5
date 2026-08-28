# TizenTube Lite — YouTube cho Android 5 (API 21)

[![Build](https://github.com/thichcode/youtube_android5/actions/workflows/build.yml/badge.svg)](https://github.com/thichcode/youtube_android5/actions)
[![Release](https://img.shields.io/github/v/release/thichcode/youtube_android5?label=release)](https://github.com/thichcode/youtube_android5/releases)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen)](https://developer.android.com)
[![ABI](https://img.shields.io/badge/ABI-armeabi--v7a-blue)](#)

WebView wrapper **siêu nhẹ (~7-9MB)** cho box TV **Android 5.0/5.1 (Amlogic S905/S912, 1GB RAM)**. Chỉ giữ **chặn quảng cáo**, bỏ SponsorBlock/DeArrow/speed để chạy mượt. Fork ý tưởng từ [TizenTube](https://github.com/reisxd/TizenTube) + [TizenTubeCobalt](https://github.com/reisxd/TizenTubeCobalt).

> APK tên theo tag: `TizenTubeLite-v1.0.1-lite-debug.apk` / `TizenTubeLite-v1.0.1-lite-release.apk` — khớp `versionName` trong `app/build.gradle`.

## ✨ Tính năng

- 🛑 **Chặn quảng cáo** YouTube TV (patch `JSON.parse` `adPlacements`/`adSlots`/`playerAds` + DOM skip)
- 📺 Giao diện **YouTube TV leanback** (`https://www.youtube.com/tv`) — điều khiển remote D-Pad/Back/Media
- 🪶 APK `armeabi-v7a` only, `minSdk 21 targetSdk 34`, RAM ~150-200MB, play 720p mượt trên box 1GB
- 🔔 Cảnh báo **WebView quá cũ** (`<90`) → hướng dẫn update
- ❌ Không SponsorBlock, không DeArrow, không chỉnh tốc độ (đã cắt theo yêu cầu tối giản)

## 📥 Tải

**Latest Release:** https://github.com/thichcode/youtube_android5/releases/latest

| File | Mô tả |
|------|-------|
| `TizenTubeLite-v1.0.1-lite-debug.apk` | Debug, cài nhanh qua ADB |
| `TizenTubeLite-v1.0.1-lite-release.apk` | Release (minify+shrink, nhỏ hơn ~10%) |

## 📱 Yêu cầu

- Android **5.0+ (API 21)** — test trên API 21 emulator 512MB + box S905 5.1.1
- ABI `armeabi-v7a` (không hỗ trợ arm64/x86)
- **Android System WebView ≥90** khuyến nghị (≥118 tốt nhất). Box cũ WebView 44 sẽ bị YouTube bắt `Sign in to confirm you're not a bot` (`yt.be/activate`).

## 🚀 Cài đặt

**Qua ADB (box + PC cùng mạng):**
```bash
adb connect <BOX_IP>:5555
adb install -r TizenTubeLite-v1.0.1-lite-release.apk
adb shell am start -n io.gh.yourname.tizentubelite/.MainActivity
```

**Qua USB:** copy APK vào USB → mở File Manager trên TV → cài.

**Cập nhật WebView (nếu bị bot check):**
1. Tải `Android System WebView` 118+ cho `armeabi-v7a` từ APKMirror
2. `adb install -r com.google.android.webview_118*.apk`
3. Mở lại app. Hoặc đăng nhập 1 lần qua `yt.be/activate` (mã `NCLD-JPBH` như ảnh) để lưu cookie.

## 🛠️ Build từ source

**Yêu cầu:** Android Studio Hedgehog+ , JDK 17, Android SDK 34

```bash
git clone https://github.com/thichcode/youtube_android5.git
cd youtube_android5
# Android Studio sẽ tự tải gradle wrapper 8.7
./gradlew :app:assembleDebug      # -> app/build/outputs/apk/debug/TizenTubeLite-v*-debug.apk
./gradlew :app:assembleRelease    # -> app/build/outputs/apk/release/TizenTubeLite-v*-release.apk

# Verify
$ANDROID_HOME/build-tools/34.0.0/aapt dump badging app/build/outputs/apk/debug/*.apk | grep sdkVersion
# sdkVersion:'21'

# Check size
ls -lh app/build/outputs/apk/debug/*.apk
# ~7-9MB
```

**Đổi version:**
```gradle
// app/build.gradle
versionCode 3
versionName "1.0.2-lite" // -> APK TizenTubeLite-v1.0.2-lite-*.apk + tag v1.0.2-lite
```
```bash
git commit -am "bump 1.0.2-lite"
git push origin master
git tag v1.0.2-lite && git push origin tag v1.0.2-lite # -> GitHub Action tự build + Release
```

## 🤖 GitHub Actions

`.github/workflows/build.yml` tự động:
- `push` lên `master` → build debug+release, upload artifact
- `tag v*` hoặc `workflow_dispatch` → build + tạo **Release** đính kèm `TizenTubeLite-v*.apk`
- Job `auto-tag` tự tạo tag từ `versionName` nếu chưa có

Xem log: https://github.com/thichcode/youtube_android5/actions

## ❓ Bot check "Sign in to confirm you're not a bot"

Đây là check **server-side của YouTube**, không bypass 100% bằng JS. App đã giảm tỉ lệ bằng:
- UA `Chrome/118 TV` (thay vì 44 cổ) — `WebViewHelper.java:9`
- Bật `CookieManager` + `setAcceptThirdPartyCookies` + `setDatabaseEnabled` — `MainActivity.java:41`
- Cảnh báo WebView `<90`

Nếu vẫn hiện: update WebView như trên hoặc đăng nhập 1 lần qua QR `yt.be/activate`.

## 📂 Cấu trúc

```
app/
├── build.gradle                         # minSdk 21, abiFilters armeabi-v7a, outputFileName TizenTubeLite-v*.apk
├── src/main/
│   ├── AndroidManifest.xml              # leanback, 3 perms only
│   ├── java/.../MainActivity.java       # WebView + inject userScript.js
│   ├── java/.../WebViewHelper.java      # UA 118, version check <90
│   ├── java/.../JsBridge.java           # @JavascriptInterface
│   ├── res/layout/activity_main.xml
│   └── assets/userScript.js             # ad-block only (strip từ TizenTube 1.7.0 mods/adblock.js)
gradle/wrapper/gradle-wrapper.properties # 8.7
.github/workflows/build.yml
```

## 🙏 Credits

- [reisxd/TizenTube](https://github.com/reisxd/TizenTube) — adblock core (`mods/adblock.js`)
- [reisxd/TizenTubeCobalt](https://github.com/reisxd/TizenTubeCobalt) — Cobalt idea (lite dùng WebView thay Cobalt để nhẹ)
- Design spec: `docs/superpowers/specs/2026-08-28-tizentube-lite-android5-design.md`

## 📄 License

GPL-3.0 (theo TizenTube gốc)
