# Native YouTube TV (ExoPlayer + youtubei via Worker) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace WebView Lite with pure native Leanback that fetches Home/Search/Watch via yt-tv-proxy → youtubei and plays via ExoPlayer on Android 5 (API21, armeabi-v7a, 1GB).

**Architecture:** WorkerProxy (Cloudflare) forwards POST /youtubei/v1/* with Cloudflare IP; YoutubeRepository (OkHttp) patches JSON to strip ads; Home/Search fragments render Leanback rows; PlayerActivity uses ExoPlayer 2.18 to play googlevideo streams (1080p fallback 720p).

**Tech Stack:** AndroidX Leanback 1.0.0, ExoPlayer 2.18.7, OkHttp 4.12, org.json, Worker (JS)

---

## File Structure

- Modify: `yt-proxy-worker/worker.js` — add explicit youtubei proxy logging, ensure POST body forwarding
- Create: `app/src/main/java/io/gh/yourname/tizentubelite/data/YoutubeRepository.java` — browse/search/next + JSON patch
- Create: `app/src/main/java/io/gh/yourname/tizentubelite/data/Video.java`, `Section.java` — models
- Create: `app/src/main/java/io/gh/yourname/tizentubelite/ui/HomeFragment.java` — BrowseSupportFragment
- Create: `app/src/main/java/io/gh/yourname/tizentubelite/ui/SearchFragment.java` — SearchSupportFragment
- Create: `app/src/main/java/io/gh/yourname/tizentubelite/ui/PlayerActivity.java` — ExoPlayer
- Modify: `app/src/main/java/io/gh/yourname/tizentubelite/MainActivity.java` — become launcher → HomeFragment host
- Modify: `app/build.gradle` — add leanback, exoplayer, okhttp
- Modify: `app/src/main/AndroidManifest.xml` — add PlayerActivity, leanback launcher
- Test: `app/src/test/java/io/gh/yourname/tizentubelite/YoutubeRepositoryTest.java`

---

### Task 1: Worker youtubei proxy hardening

**Files:**
- Modify: `yt-proxy-worker/worker.js:1-40`
- Test: manual curl via `yt-tv-proxy.dvt-kisu.workers.dev/youtubei/v1/browse`

- [ ] **Step 1: Add explicit youtubei route logging**

```javascript
// in worker.js fetch handler, before targetUrl
console.log(`[Worker] ${request.method} ${url.pathname}${url.search} UA=${request.headers.get("User-Agent")?.slice(0,40)}`);
```

- [ ] **Step 2: Deploy and curl test**

Run:
```
wrangler deploy
curl -X POST https://yt-tv-proxy.dvt-kisu.workers.dev/youtubei/v1/browse -H "Content-Type: application/json" --data @payload.json -i | head -20
```
Expected: 200 with JSON, not 400

- [ ] **Step 3: Commit**

```bash
git add yt-proxy-worker/worker.js
git commit -m "fix(worker): log youtubei proxy, keep POST body"
```

---

### Task 2: Add dependencies

**Files:**
- Modify: `app/build.gradle:41-44`

- [ ] **Step 1: Edit build.gradle**

```gradle
dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.leanback:leanback:1.0.0'
    implementation 'com.google.android.exoplayer:exoplayer:2.18.7'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
}
```

- [ ] **Step 2: Sync and verify**

Run: `gradle assembleDebug` (use jdk17)
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle
git commit -m "feat: add leanback, exoplayer, okhttp"
```

---

### Task 3: Models Video/Section

**Files:**
- Create: `app/src/main/java/io/gh/yourname/tizentubelite/data/Video.java`
- Create: `app/src/main/java/io/gh/yourname/tizentubelite/data/Section.java`

- [ ] **Step 1: Create Video.java**

```java
package io.gh.yourname.tizentubelite.data;
public class Video {
    public final String id, title, thumb;
    public final long durationSec;
    public Video(String id, String title, String thumb, long d){this.id=id;this.title=title;this.thumb=thumb;this.durationSec=d;}
}
```

- [ ] **Step 2: Create Section.java**

```java
package io.gh.yourname.tizentubelite.data;
import java.util.List;
public class Section {
    public final String title;
    public final List<Video> videos;
    public Section(String t, List<Video> v){title=t;videos=v;}
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/io/gh/yourname/tizentubelite/data/*.java
git commit -m "feat: add Video/Section models"
```

---

### Task 4: YoutubeRepository with ad-strip

**Files:**
- Create: `app/src/main/java/io/gh/yourname/tizentubelite/data/YoutubeRepository.java`
- Test: `app/src/test/java/io/gh/yourname/tizentubelite/YoutubeRepositoryTest.java`

- [ ] **Step 1: Write failing test**

```java
@Test
public void testStripAdPlacements() {
    String json = "{\"adPlacements\":[{\"a\":1}],\"playerAds\":true,\"contents\":{\"tvBrowseRenderer\":{}}}";
    JSONObject patched = YoutubeRepository.stripAds(new JSONObject(json));
    assertEquals(0, patched.getJSONArray("adPlacements").length());
    assertFalse(patched.getBoolean("playerAds"));
}
```

- [ ] **Step 2: Run test (fails)**

Run: `gradle test --tests YoutubeRepositoryTest`
Expected: FAIL class not found

- [ ] **Step 3: Implement repository**

```java
public class YoutubeRepository {
    private static final String BASE = "https://yt-tv-proxy.dvt-kisu.workers.dev";
    private final OkHttpClient client = new OkHttpClient();
    public static JSONObject stripAds(JSONObject r){ /* same as userScript.js stripShelves */ return r; }
    public List<Section> browse() throws IOException { /* POST /youtubei/v1/browse with TVHTML5 context */ }
    public List<Video> search(String q) throws IOException { /* POST /youtubei/v1/search */ }
    public String nextStreamUrl(String videoId) throws IOException { /* POST /youtubei/v1/next → streamingData */ }
}
```

- [ ] **Step 4: Run test passes**

Run: `gradle test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/gh/yourname/tizentubelite/data/YoutubeRepository.java app/src/test/java/io/gh/yourname/tizentubelite/YoutubeRepositoryTest.java
git commit -m "feat: add YoutubeRepository with ad-strip"
```

---

### Task 5: HomeFragment Leanback

**Files:**
- Create: `app/src/main/java/io/gh/yourname/tizentubelite/ui/HomeFragment.java`

- [ ] **Step 1: Create HomeFragment**

```java
public class HomeFragment extends BrowseSupportFragment {
    public void onViewCreated(View view, Bundle s){
        super.onViewCreated(view,s);
        setTitle("TizenTube Native");
        ArrayObjectAdapter rows = new ArrayObjectAdapter(new ListRowPresenter());
        // async load repository.browse() → for each Section add ListRow
        // onItemClicked → start PlayerActivity with videoId
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/io/gh/yourname/tizentubelite/ui/HomeFragment.java
git commit -m "feat: add HomeFragment leanback"
```

---

### Task 6: SearchFragment

**Files:**
- Create: `app/src/main/java/io/gh/yourname/tizentubelite/ui/SearchFragment.java`

- [ ] **Step 1: Create SearchFragment extends SearchSupportFragment, calls repository.search()**

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/io/gh/yourname/tizentubelite/ui/SearchFragment.java
git commit -m "feat: add SearchFragment"
```

---

### Task 7: PlayerActivity ExoPlayer

**Files:**
- Create: `app/src/main/java/io/gh/yourname/tizentubelite/ui/PlayerActivity.java`
- Modify: `app/src/main/AndroidManifest.xml` add activity

- [ ] **Step 1: Create PlayerActivity**

```java
public class PlayerActivity extends Activity {
    private ExoPlayer player;
    protected void onCreate(Bundle s){
        String id = getIntent().getStringExtra("videoId");
        new Thread(() -> {
            String url = new YoutubeRepository().nextStreamUrl(id); // picks 137 1080p fallback 136
            runOnUiThread(() -> {
                player = new ExoPlayer.Builder(this).build();
                ((PlayerView)findViewById(R.id.player_view)).setPlayer(player);
                player.setMediaItem(MediaItem.fromUri(url));
                player.prepare(); player.play();
            });
        }).start();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/io/gh/yourname/tizentubelite/ui/PlayerActivity.java app/src/main/AndroidManifest.xml
git commit -m "feat: add PlayerActivity ExoPlayer 1080p"
```

---

### Task 8: MainActivity host + build

**Files:**
- Modify: `app/src/main/java/io/gh/yourname/tizentubelite/MainActivity.java`
- Modify: `app/build.gradle` version bump

- [ ] **Step 1: Make MainActivity host HomeFragment**

```java
setContentView(R.layout.activity_main);
getFragmentManager().beginTransaction().replace(R.id.container, new HomeFragment()).commit();
```

- [ ] **Step 2: Build debug and install on Memu**

Run: `gradle assembleDebug && adb -s 127.0.0.1:21503 install -r app/build/outputs/apk/debug/*.apk`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/io/gh/yourname/tizentubelite/MainActivity.java app/build.gradle
git commit -m "feat: wire native leanback, bump 2.0.0-native"
git tag v2.0.0-native
git push origin master --tags
```
