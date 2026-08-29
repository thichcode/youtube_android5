package io.gh.yourname.tizentubelite;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
import io.gh.yourname.tizentubelite.data.YoutubeRepository;
public class YoutubeRepositoryTest {
    @Test
    public void testStripAdPlacements() throws Exception {
        String json = "{\"adPlacements\":[{\"a\":1}],\"playerAds\":true,\"contents\":{\"tvBrowseRenderer\":{}}}";
        JSONObject patched = YoutubeRepository.stripAds(new JSONObject(json));
        assertEquals(0, patched.getJSONArray("adPlacements").length());
        assertFalse(patched.getBoolean("playerAds"));
    }
}
