package io.gh.yourname.tizentubelite.data;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.*;

public class YoutubeRepository {
    private static final String BASE = "https://yt-tv-proxy.dvt-kisu.workers.dev";
    private static final String YT_DIRECT = "https://www.youtube.com";
    private static final String ANDROID_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w";
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
        // 1) Direct residential player (best for home box IP reputation)
        String url = directPlayer(videoId, ctx);
        if (url != null) return url;
        // 2) Android client + API key direct
        url = androidPlayerDirect(videoId);
        if (url != null) return url;
        // 3) Fallback through Worker proxy
        JSONObject res = post("/youtubei/v1/player", ctx);
        return pickStream(res);
    }

    private String directPlayer(String videoId, JSONObject ctx) throws IOException {
        return postTo(YT_DIRECT + "/youtubei/v1/player", ctx,
                "Mozilla/5.0 (Linux; Android 11; AFTSS) AppleWebKit/537.36 CrKey/1.54 TV Cobalt/27.lts.2-qa",
                null);
    }

    private String androidPlayerDirect(String videoId) throws IOException {
        JSONObject body = new JSONObject();
        try {
            JSONObject client = new JSONObject()
                    .put("clientName", "ANDROID")
                    .put("clientVersion", "19.09.37")
                    .put("androidSdkVersion", 28)
                    .put("deviceModel", "Pixel 7")
                    .put("osName", "Android")
                    .put("osVersion", "13")
                    .put("hl", "en").put("gl", "US");
            body.put("context", new JSONObject().put("client", client));
            body.put("videoId", videoId);
            body.put("contentCheckOk", true);
            body.put("racyCheckOk", true);
        } catch (Exception ignored) {}
        return postTo(YT_DIRECT + "/youtubei/v1/player", body,
                "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip",
                ANDROID_KEY);
    }

    private String postTo(String url, JSONObject body, String ua, String apiKey) throws IOException {
        Request.Builder rb = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), JSON))
                .header("User-Agent", ua)
                .header("Origin", "https://www.youtube.com");
        if (apiKey != null) rb.header("X-Goog-API-Key", apiKey);
        try (Response resp = client.newCall(rb.build()).execute()) {
            String txt = resp.body() != null ? resp.body().string() : "{}";
            JSONObject j = new JSONObject(txt);
            return pickStream(j);
        } catch (Exception e) {
            return null;
        }
    }

    private String pickStream(JSONObject res) {
        try {
            JSONObject streamingData = res.optJSONObject("streamingData");
            if (streamingData != null) {
                JSONArray formats = streamingData.optJSONArray("formats");
                JSONArray adaptive = streamingData.optJSONArray("adaptiveFormats");
                String best = null;
                if (adaptive != null) {
                    for (int i = 0; i < adaptive.length(); i++) {
                        JSONObject f = adaptive.optJSONObject(i);
                        if (f == null) continue;
                        int itag = f.optInt("itag", 0);
                        String url = f.optString("url", "");
                        if (itag == 137 && !url.isEmpty()) return url;
                        if (itag == 136 && best == null && !url.isEmpty()) best = url;
                    }
                }
                if (best != null) return best;
                if (formats != null && formats.length() > 0) return formats.optJSONObject(0).optString("url", "");
            }
        } catch (Exception ignored) {}
        return null;
    }
}
