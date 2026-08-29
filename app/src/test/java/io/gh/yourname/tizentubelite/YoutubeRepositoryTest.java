package io.gh.yourname.tizentubelite;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import io.gh.yourname.tizentubelite.data.YoutubeRepository;
import io.gh.yourname.tizentubelite.data.Video;
import java.io.InputStream;
import java.util.List;
public class YoutubeRepositoryTest {
    @Test
    public void testStripAdPlacements() throws Exception {
        String json = "{\"adPlacements\":[{\"a\":1}],\"playerAds\":true,\"contents\":{\"tvBrowseRenderer\":{}}}";
        JSONObject patched = YoutubeRepository.stripAds(new JSONObject(json));
        assertEquals(0, patched.getJSONArray("adPlacements").length());
        assertFalse(patched.getBoolean("playerAds"));
    }

    @Test
    public void testParseRealSearchResponse() throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream("search_fixture.json");
        assertNotNull("fixture missing", in);
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
        in.close();
        List<Video> vids = YoutubeRepository.parseSearchResults(new JSONObject(sb.toString()));
        assertTrue("expected at least 5 videos, got " + vids.size(), vids.size() >= 5);
        for (Video v : vids) {
            assertTrue("videoId empty", v.id.length() == 11);
            assertFalse("title empty", v.title.isEmpty());
        }
    }
}
