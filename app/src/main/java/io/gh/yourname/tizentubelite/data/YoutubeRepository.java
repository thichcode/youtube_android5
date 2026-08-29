package io.gh.yourname.tizentubelite.data;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.*;

public class YoutubeRepository {
    private static final String BASE = "https://yt-tv-proxy.dvt-kisu.workers.dev";
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
            body.put("context", new JSONObject().put("client", new JSONObject().put("clientName", "TVHTML5").put("clientVersion", "7.20240701.00.00")));
            body.put("query", q);
        } catch (Exception ignored) {}
        JSONObject res = post("/youtubei/v1/search", body);
        List<Video> out = new ArrayList<>();
        // simplified parse
        try {
            JSONObject contents = res.optJSONObject("contents");
            if (contents != null) {
                JSONObject slr = contents.optJSONObject("sectionListRenderer");
                if (slr != null) {
                    JSONArray arr = slr.optJSONArray("contents");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject itemSec = arr.optJSONObject(i);
                            if (itemSec == null) continue;
                            JSONObject itemSecRenderer = itemSec.optJSONObject("itemSectionRenderer");
                            if (itemSecRenderer == null) continue;
                            JSONArray contents2 = itemSecRenderer.optJSONArray("contents");
                            if (contents2 == null) continue;
                            for (int j = 0; j < contents2.length(); j++) {
                                JSONObject vr = contents2.optJSONObject(j).optJSONObject("tileRenderer");
                                if (vr == null) vr = contents2.optJSONObject(j).optJSONObject("videoRenderer");
                                if (vr == null) continue;
                                String id = vr.optString("videoId","");
                                String t = vr.optString("title","");
                                if (!id.isEmpty()) out.add(new Video(id, t, "", 0));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    public String nextStreamUrl(String videoId) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("context", new JSONObject().put("client", new JSONObject().put("clientName", "TVHTML5").put("clientVersion", "7.20240701.00.00")));
            body.put("videoId", videoId);
        } catch (Exception ignored) {}
        JSONObject res = post("/youtubei/v1/next", body);
        try {
            JSONObject streamingData = res.optJSONObject("streamingData");
            if (streamingData != null) {
                JSONArray formats = streamingData.optJSONArray("formats");
                JSONArray adaptive = streamingData.optJSONArray("adaptiveFormats");
                // pick 137 (1080p) else 136
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
                if (formats != null && formats.length() > 0) return formats.optJSONObject(0).optString("url","");
            }
        } catch (Exception ignored) {}
        return null;
    }
}
