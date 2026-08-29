package io.gh.yourname.tizentubelite;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class WebViewHelper {
    public static final String YT_TV_URL = "https://yt-tv-proxy.dvt-kisu.workers.dev/tv";
    // TV UA with Cobalt/CrKey to look like real Google TV (bypass bot check better than plain Chrome)
    public static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 11; AFTSS Build/RTM2.230615.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.5993.48 Safari/537.36 CrKey/1.54 TV Cobalt/27.lts.2-qa";
    public static final String FALLBACK_URL = "https://m.youtube.com/?noapp=1";
    // JS to spoof webdriver and chrome object (bot detection)
    public static final String BOT_SPOOF_JS = "(function(){try{Object.defineProperty(navigator,'webdriver',{get:()=>false});window.chrome={runtime:{}};Object.defineProperty(navigator,'plugins',{get:()=>[1,2]});Object.defineProperty(navigator,'languages',{get:()=>['en-US','en']});}catch(e){}})();";

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
        return getWebViewMajorVersion(ctx) > 0 && getWebViewMajorVersion(ctx) < 90;
    }
}
