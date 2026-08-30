package io.gh.yourname.tizentubelite.data;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.*;

public class YoutubeRepository {
    public static final String TAG = "TizenTubeRepo";
    private static final String BASE = "https://yt-tv-proxy.dvt-kisu.workers.dev";
    private static final String YT_DIRECT = "https://www.youtube.com";
    private static final String ANDROID_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w";
    private static final String ANDROID_UA = "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip";
    private static final String ANDROID_VR_UA = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip";
    private static final String BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static String cachedVisitorData;
    private static long visitorDataFetchedAt;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();

    public static JSONObject stripAds(JSONObject r) {
        try {
            if (r.has("adPlacements")) r.put("adPlacements", new JSONArray());
            if (r.has("playerAds")) r.put("playerAds", false);
            if (r.has("adSlots")) r.put("adSlots", new JSONArray());
            // strip sectionList
            if (r.has("contents")) {
                JSONObject tvBrowse = r.optJSONObject("contents");
                if (tvBrowse != null) {
                    JSONObject tvBrowseRenderer = tvBrowse.optJSONObject("tvBrowseRenderer");
                    if (tvBrowseRenderer != null) {
                        JSONObject content = tvBrowseRenderer.optJSONObject("content");
                        if (content != null) {
                            JSONObject tvSurface = content.optJSONObject("tvSurfaceContentRenderer");
                            if (tvSurface != null) {
                                JSONObject c2 = tvSurface.optJSONObject("content");
                                if (c2 != null) {
                                    JSONObject sectionList = c2.optJSONObject("sectionListRenderer");
                                    if (sectionList != null) {
                                        JSONArray contents = sectionList.optJSONArray("contents");
                                        if (contents != null) stripShelves(contents);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (r.has("continuationContents")) {
                JSONObject cc = r.optJSONObject("continuationContents");
                if (cc != null) {
                    JSONObject secCont = cc.optJSONObject("sectionListContinuation");
                    if (secCont != null) {
                        JSONArray c = secCont.optJSONArray("contents");
                        if (c != null) stripShelves(c);
                    }
                }
            }
        } catch (Exception ignored) {}
        return r;
    }

    private static void stripShelves(JSONArray contents) {
        try {
            for (int i = contents.length() - 1; i >= 0; i--) {
                JSONObject elm = contents.optJSONObject(i);
                if (elm != null && elm.has("adSlotRenderer")) {
                    contents.remove(i);
                    continue;
                }
                JSONObject shelf = elm != null ? elm.optJSONObject("shelfRenderer") : null;
                if (shelf != null) {
                    JSONObject content = shelf.optJSONObject("content");
                    if (content != null) {
                        JSONObject horiz = content.optJSONObject("horizontalListRenderer");
                        if (horiz != null) {
                            JSONArray items = horiz.optJSONArray("items");
                            if (items != null) {
                                for (int j = items.length() - 1; j >= 0; j--) {
                                    JSONObject it = items.optJSONObject(j);
                                    if (it != null && it.has("adSlotRenderer")) items.remove(j);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private JSONObject post(String path, JSONObject body) throws IOException {
        Request req = new Request.Builder()
                .url(BASE + path)
                .post(RequestBody.create(body.toString(), JSON))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 11; AFTSS) AppleWebKit/537.36 CrKey/1.54 TV Cobalt/27.lts.2-qa")
                .build();
        try (Response resp = client.newCall(req).execute()) {
            String txt = resp.body() != null ? resp.body().string() : "{}";
            JSONObject j = new JSONObject(txt);
            return stripAds(j);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public List<Section> browse() throws IOException {
        JSONObject ctx = new JSONObject();
        try {
            ctx.put("context", new JSONObject().put("client", new JSONObject().put("clientName", "TVHTML5").put("clientVersion", "7.20240701.00.00")));
        } catch (Exception ignored) {}
        JSONObject res = post("/youtubei/v1/browse", ctx);
        // parse sections
        List<Section> out = new ArrayList<>();
        try {
            JSONObject contents = res.optJSONObject("contents");
            if (contents != null) {
                JSONObject tvBrowse = contents.optJSONObject("tvBrowseRenderer");
                if (tvBrowse != null) {
                    JSONObject content = tvBrowse.optJSONObject("content");
                    if (content != null) {
                        JSONObject tvSurface = content.optJSONObject("tvSurfaceContentRenderer");
                        if (tvSurface != null) {
                            JSONObject c2 = tvSurface.optJSONObject("content");
                            if (c2 != null) {
                                JSONObject sectionList = c2.optJSONObject("sectionListRenderer");
                                if (sectionList != null) {
                                    JSONArray arr = sectionList.optJSONArray("contents");
                                    if (arr != null) {
                                        for (int i = 0; i < arr.length(); i++) {
                                            JSONObject sec = arr.optJSONObject(i);
                                            if (sec == null) continue;
                                            JSONObject shelf = sec.optJSONObject("shelfRenderer");
                                            if (shelf == null) continue;
                                            String title = shelf.optJSONObject("headerRenderer") != null ? shelf.optJSONObject("headerRenderer").optString("title", "") : "";
                                            List<Video> vids = new ArrayList<>();
                                            JSONObject cont = shelf.optJSONObject("content");
                                            if (cont != null) {
                                                JSONObject horiz = cont.optJSONObject("horizontalListRenderer");
                                                if (horiz != null) {
                                                    JSONArray items = horiz.optJSONArray("items");
                                                    if (items != null) {
                                                        for (int j = 0; j < items.length(); j++) {
                                                            JSONObject it = items.optJSONObject(j);
                                                            if (it == null) continue;
                                                            JSONObject tile = it.optJSONObject("tileRenderer");
                                                            if (tile == null) tile = it.optJSONObject("gridVideoRenderer");
                                                            if (tile == null) continue;
                                                            String id = tile.optString("videoId", "");
                                                            String t = tile.optString("title", "");
                                                            String thumb = "";
                                                            try { thumb = tile.optJSONObject("thumbnail").optJSONArray("thumbnails").optJSONObject(0).optString("url",""); } catch(Exception ignored2){}
                                                            if (!id.isEmpty()) vids.add(new Video(id, t, thumb, 0));
                                                        }
                                                    }
                                                }
                                            }
                                            if (!vids.isEmpty()) out.add(new Section(title, vids));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    public List<Video> search(String q) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("context", new JSONObject().put("client", new JSONObject()
                    .put("clientName", "TVHTML5")
                    .put("clientVersion", "7.20240701.00.00")
                    .put("hl", "en").put("gl", "US").put("platform", "TV")));
            body.put("query", q);
        } catch (Exception ignored) {}
        JSONObject res = post("/youtubei/v1/search", body);
        return parseSearchResults(res);
    }

    public static List<Video> parseSearchResults(JSONObject res) {
        List<Video> out = new ArrayList<>();
        // TV search: contents.sectionListRenderer.contents[].shelfRenderer
        //            .content.horizontalListRenderer.items[].lockupViewModel (new)
        //            ... or items[].tileRenderer (legacy)
        try {
            JSONObject contents = res.optJSONObject("contents");
            if (contents != null) {
                JSONObject slr = contents.optJSONObject("sectionListRenderer");
                if (slr != null) {
                    JSONArray arr = slr.optJSONArray("contents");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject sec = arr.optJSONObject(i);
                            if (sec == null) continue;
                            JSONObject shelf = sec.optJSONObject("shelfRenderer");
                            if (shelf == null) continue;
                            JSONObject cont = shelf.optJSONObject("content");
                            if (cont != null) {
                                JSONObject horiz = cont.optJSONObject("horizontalListRenderer");
                                if (horiz == null) continue;
                                JSONArray items = horiz.optJSONArray("items");
                                if (items == null) continue;
                                for (int j = 0; j < items.length(); j++) {
                                    JSONObject item = items.optJSONObject(j);
                                    if (item == null) continue;
                                    JSONObject tile = item.optJSONObject("tileRenderer");
                                    JSONObject lockup = item.optJSONObject("lockupViewModel");
                                    if (tile == null && lockup == null) continue;
                                    String id = null, t = null, thumb = null;
                                    if (tile != null) {
                                        id = tile.optString("videoId", "");
                                        t = tile.optString("title", "");
                                        JSONObject th = tile.optJSONObject("thumbnail");
                                        try {
                                            if (th != null) thumb = th.optJSONArray("thumbnails").optJSONObject(0).optString("url", "");
                                        } catch (Exception ignored) {}
                                        if (id.isEmpty()) id = tile.optString("videoId", "");
                                    } else {
                                        try {
                                            id = lockup.optString("contentId", "");
                                            JSONObject contentImage = lockup.optJSONObject("contentImage");
                                            if (contentImage != null) {
                                                JSONObject tv = contentImage.optJSONObject("thumbnailViewModel");
                                                if (tv != null) {
                                                    JSONObject img = tv.optJSONObject("image");
                                                    if (img != null) {
                                                        JSONArray sources = img.optJSONArray("sources");
                                                        if (sources != null && sources.length() > 0) thumb = sources.optJSONObject(0).optString("url", "");
                                                    }
                                                }
                                            }
                                            JSONObject meta = lockup.optJSONObject("metadata");
                                            if (meta != null) {
                                                JSONObject lmd = meta.optJSONObject("lockupMetadataViewModel");
                                                if (lmd != null) {
                                                    JSONObject titleObj = lmd.optJSONObject("title");
                                                    if (titleObj != null) t = titleObj.optString("content", "");
                                                }
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                    if (id == null || id.isEmpty()) {
                                        // nested watchEndpoint command
                                        try {
                                            String s = searchForVideoId(item);
                                            if (s != null) id = s;
                                        } catch (Exception ignored) {}
                                    }
                                    // only real videos have 11-char ids (playlists/mixes use RDAT.. etc)
                                    if (id != null && id.length() == 11) out.add(new Video(id, t != null ? t : "", thumb != null ? thumb : "", 0));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String searchForVideoId(JSONObject obj) {
        try {
            if (obj.has("videoId") && obj.optString("videoId").length() > 5) return obj.optString("videoId");
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                Object v = obj.opt(k);
                if (v instanceof JSONObject) {
                    String r = searchForVideoId((JSONObject) v);
                    if (r != null) return r;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public String nextStreamUrl(String videoId) throws IOException {
        JSONObject ctx = new JSONObject();
        try {
            ctx.put("context", new JSONObject().put("client", new JSONObject()
                    .put("clientName", "TVHTML5")
                    .put("clientVersion", "7.20240701.00.00")
                    .put("hl", "en").put("gl", "US").put("platform", "TV")));
            ctx.put("videoId", videoId);
            ctx.put("contentCheckOk", true);
            ctx.put("racyCheckOk", true);
        } catch (Exception ignored) {}
        // Signed-out playback needs a valid visitorData in the player body,
        // otherwise YouTube answers LOGIN_REQUIRED ("not a bot").
        // Verified 2026-08: ANDROID / ANDROID_VR clients return OK + itag 18
        // (progressive, pot-exempt) with just visitorData — no cookies, no PoT.
        String vis = getVisitorData();
        if (!vis.isEmpty()) {
            String url = androidPlayerDirect(videoId, vis, ANDROID_UA, "ANDROID", "21.26.364", 30, "Linux; U; Android 11");
            if (url != null) return url;
            url = androidPlayerDirect(videoId, vis, ANDROID_VR_UA, "ANDROID_VR", "1.65.10", 32, "Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1");
            if (url != null) return url;
        }
        // Fallback through Worker proxy
        JSONObject res = post("/youtubei/v1/player", ctx);
        String url = pickStream(res);
        if (url == null) android.util.Log.d(TAG, "worker player also returned no stream");
        return url;
    }

    private String androidPlayerDirect(String videoId, String vis, String ua, String clientName, String clientVersion, int sdk, String osDesc) throws IOException {
        JSONObject body = new JSONObject();
        try {
            JSONObject client = new JSONObject()
                    .put("clientName", clientName)
                    .put("clientVersion", clientVersion)
                    .put("androidSdkVersion", sdk)
                    .put("osName", "Android")
                    .put("osVersion", osDesc.contains("12L") ? "12L" : "11")
                    .put("visitorData", vis)
                    .put("hl", "en").put("gl", "US");
            body.put("context", new JSONObject().put("client", client));
            body.put("videoId", videoId);
            body.put("contentCheckOk", true);
            body.put("racyCheckOk", true);
        } catch (Exception ignored) {}
        return postTo(YT_DIRECT + "/youtubei/v1/player", body, ua, ANDROID_KEY, clientName, clientVersion, osDesc);
    }

    private String getVisitorData() {
        long now = System.currentTimeMillis();
        if (cachedVisitorData != null && now - visitorDataFetchedAt < 6L * 3600 * 1000) return cachedVisitorData;
        try {
            Request req = new Request.Builder()
                    .url(YT_DIRECT + "/")
                    .header("User-Agent", BROWSER_UA)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build();
            String html;
            try (Response resp = client.newCall(req).execute()) {
                html = resp.body() != null ? resp.body().string() : "";
            }
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"visitorData\":\"([^\"]{10,})\"").matcher(html);
            if (m.find()) {
                cachedVisitorData = m.group(1);
                visitorDataFetchedAt = now;
                android.util.Log.d(TAG, "visitorData fetched len=" + cachedVisitorData.length());
                return cachedVisitorData;
            }
        } catch (Exception e) {
            android.util.Log.d(TAG, "visitorData fetch failed: " + e);
        }
        return cachedVisitorData != null ? cachedVisitorData : "";
    }

    private String postTo(String url, JSONObject body, String ua, String apiKey, String clientName, String clientVersion, String osDesc) throws IOException {
        Request.Builder rb = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), JSON))
                .header("User-Agent", ua)
                .header("Origin", "https://www.youtube.com");
        if (apiKey != null) rb.header("X-Goog-API-Key", apiKey);
        if (clientName != null) rb.header("X-YouTube-Client-Name", clientName.equals("ANDROID") ? "3" : "28");
        if (clientVersion != null) rb.header("X-YouTube-Client-Version", clientVersion);
        try (Response resp = client.newCall(rb.build()).execute()) {
            String txt = resp.body() != null ? resp.body().string() : "{}";
            String status = "?";
            try { status = new JSONObject(txt).optJSONObject("playabilityStatus").optString("status", "?"); } catch (Exception ignored) {}
            android.util.Log.d(TAG, "player " + clientName + " HTTP " + resp.code() + " len " + txt.length() + " status=" + status);
            JSONObject j = new JSONObject(txt);
            return pickStream(j);
        } catch (Exception e) {
            android.util.Log.d(TAG, "player direct failed: " + e);
            return null;
        }
    }

    private String pickStream(JSONObject res) {
        try {
            JSONObject streamingData = res.optJSONObject("streamingData");
            if (streamingData != null) {
                JSONArray formats = streamingData.optJSONArray("formats");
                // Progressive formats carry both audio+video for single-URL playback.
                // Prefer itag 22 (720p) > 18 (360p) > first progressive format.
                String fallback = null;
                if (formats != null) {
                    for (int i = 0; i < formats.length(); i++) {
                        JSONObject f = formats.optJSONObject(i);
                        if (f == null) continue;
                        int itag = f.optInt("itag", 0);
                        String url = f.optString("url", "");
                        if (url.isEmpty()) continue;
                        if (fallback == null) fallback = url;
                        if (itag == 22) return url;
                        if (itag == 18 && (fallback == null)) fallback = url;
                    }
                }
                if (fallback != null) return fallback;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
