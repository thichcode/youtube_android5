# Native YouTube TV (ExoPlayer + youtubei via Worker) — Design Spec

Date: 2026-08-29
Status: Approved
Context: Android TV 5 (API 21, armeabi-v7a, 1GB RAM, D-pad) + Memu emulator. Prior Lite WebView approach failed after 6+ fixes: m.youtube.com SyntaxError on Chrome 68 (Unrecognized token . at c3_base:83), youtube.com/tv leanback 400 on youtubei/v1/browse via both direct and Worker (logs v1.0.17), WebView update on Memu blocked by multi-arch system Chrome. Decision: pure native leanback via Worker proxy.

## 1. Architecture
```
[TV Box API21] → [App Native Leanback]
  ├─ UI: HomeFragment (Browse) + SearchFragment + PlayerActivity (ExoPlayer)
  ├─ Data: YoutubeRepository → WorkerProxy (https://yt-tv-proxy.dvt-kisu.workers.dev/youtubei/v1/{browse,search,next,guide})
  │     └─ Worker → youtube.com (Cloudflare IP, bypass bot) + strip adPlacements/playerAds at edge or client
  └─ Player: ExoPlayer 2.18 (API21, 1080p, fallback 720p)
```
- Single module `app`, `minSdk 21`, `armeabi-v7a` only, deps: `androidx.leanback:1.0.0`, `exoplayer:2.18.7`, `okhttp:4.12`, `moshi` or `org.json`.
- No WebView/Cobalt. Worker reuse existing `yt-tv-proxy.dvt-kisu.workers.dev`.

## 2. Components
- **WorkerProxy** (`yt-proxy-worker/worker.js` extended): proxy `POST /youtubei/v1/*`, forward `Host: www.youtube.com`, `User-Agent: TVHTML5`, `Referer/Origin`, `Cookie`, strip `Domain=.youtube.com` on Set-Cookie, `Access-Control-Allow-Origin: *`, return JSON as-is. CORS preflight handled.
- **YoutubeRepository**: `browse()`, `search(query)`, `next(videoId)`, `guide()`. Uses OkHttp, builds `{"context":{"client":{"clientName":"TVHTML5","clientVersion":"7.20240701.00.00","visitorData":...}}}` + `browseId/searchQuery/videoId`. Applies JSON.parse patch (strip `adPlacements`, `adSlots`, `playerAds`, `adSlotRenderer` in `sectionListRenderer`/`horizontalListRenderer`) — same logic as `app/src/main/assets/userScript.js:6`.
- **HomeFragment**: `BrowseSupportFragment` + `ArrayObjectAdapter` of `ListRow`. Loads `browse()` onCreate, D-pad focus, click → `PlayerActivity` with `videoId`.
- **SearchFragment**: `SearchSupportFragment`, debounced input → `search()`.
- **PlayerActivity**: `PlayerView` + ExoPlayer. Calls `next(videoId)` → `streamingData` → picks `itag 137 (1080p)` → fallback `136 (720p)` → `135 (480p)` if `onPlayerError`. Hides ad via JSON patch + `player.currentTime` seek if `ad-showing`.

## 3. Data Flow
- Home: `HomeFragment.onViewCreated → repository.browse() → Worker POST /youtubei/v1/browse → youtube.com → JSON → patch → sectionListRenderer.contents → Adapter`
- Search: `query → repository.search() → /youtubei/v1/search → patch → results`
- Watch: `click videoId → repository.next() → streamingData.formats → ExoPlayer MediaItem.fromUri(googlevideo) → play`. googlevideo URLs go direct, not via Worker (bandwidth).

## 4. Error Handling
- Network/offline: OkHttp retry 1, show `offlineView`, BACK reloads.
- Worker 400/bot: log `TizenLite` tag (`DIAG` style), retry with alternate UA. No fallback to WebView.
- Parse breakage: try/catch around `tvBrowseRenderer`/`sectionListRenderer`; empty state + Retry button.
- Player error: fallback downscale, toast.

## 5. Testing
- Unit: `YoutubeRepositoryTest` with mocked Worker JSON asserts adPlacements stripped, shelves filtered.
- Device: Memu x86 (API 28) + real box API21 arm via `adb logcat -s TizenLite`, D-pad navigation test.
- Perf: image cache limited, ExoPlayer LoadControl tuned for 1GB.

## 6. Non-Goals (v1)
- No login/subscriptions/history/playlists. No Cobalt/WebView.

## 7. Open Questions
- VisitorData generation: reuse `VISITOR_INFO1_LIVE` cookie or generate via `youtubei` bootstrap? v1 uses static TVHTML5 context without visitorData, Worker adds.

## Self-Review
- No TBD/TODO. Architecture matches Home/Search/Watch scope. No contradictions (no WebView). Single plan scope. No ambiguity in API via Worker.
