# Cobalt Android 5 Patch (API 21) for TizenTubeCobalt

Branch `cobalt` này chứa patch để build **TizenTubeCobalt (Cobalt 27 LTS)** trên **Android 5.0/5.1 (API 21)**.

Lite WebView ở branch `master` (~7MB, bypass bot check kém) → Cobalt branch này **giữ YouTube TV leanback gốc, được Google whitelist → ít bị `Sign in to confirm you're not a bot` trên BlueStacks/box 1GB**.

## Patch chính

**File:** `build/config/android/config.gni` (trong repo `reisxd/TizenTubeCobalt`, ~562k files)
```gn
# trước (Cobalt Android 7)
if (is_cobalt) {
  default_min_sdk_version = 24
}

# sau (Android 5)
if (is_cobalt) {
  default_min_sdk_version = 21  # hạ từ 24 -> 21
}
# min_supported_sdk_version = 21 đã có sẵn -> build pass
# android_ndk_api_level = default_min_sdk_version -> tự về 21
```

**File:** `cobalt/shell/android/shell_apk/AndroidManifest.xml.jinja2` — nếu build fail do `FOREGROUND_SERVICE`/`profileable` (API 28+/29+), guard bằng `Build.VERSION.SDK_INT` hoặc xóa permission đó cho API 21.

## Cách dùng

### Option A: Build via GitHub Action (không cần clone 562k files locally)
Action `cobalt-build.yml` ở branch này sẽ:
1. `git clone --depth 1 --filter=blob:none https://github.com/reisxd/TizenTubeCobalt`
2. Apply patch `cobalt-patch/android5.patch`
3. `gn gen out/cobalt --args='target_os="android" is_cobalt=true is_debug=false'`
4. `ninja -C out/cobalt cobalt_apk`

### Option B: Build local
```bash
git clone https://github.com/reisxd/TizenTubeCobalt
cd TizenTubeCobalt
patch -p1 < /path/to/youtube_android5/cobalt-patch/android5.patch
gn gen out/cobalt --args='target_os="android" is_cobalt=true'
ninja -C out/cobalt cobalt_apk
# APK: out/cobalt/apks/Cobalt.apk
```

## Trade-offs

|  | WebView Lite (master) | Cobalt 27 LTS + patch 21 (cobalt) |
|---|---|---|
| APK | 7-9MB armeabi-v7a | 80-120MB |
| RAM idle | 150MB | 350-500MB |
| Bot check | Có (UA 118 + cookies) | Ít (whitelist) |
| Build | 3 phút | 4-6h + 20GB disk |
| Box 1GB | Mượt 720p | Lag/OOM nếu 1080p |

## Lưu ý

- Repo `youtube/cobalt` không có tag `22.lts` public → dùng `reisxd/TizenTubeCobalt` (27 LTS) patch về 21 là thực tế nhất.
- Box Android 5 thật 1GB RAM nên test kỹ OOM — khuyến nghị chỉ play 720p.
