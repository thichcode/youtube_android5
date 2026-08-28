package io.gh.yourname.tizentubelite;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class WebViewHelper {
    public static final String YT_TV_URL = "https://www.youtube.com/tv";
    public static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 5.1; AFTM Build/LMY47M) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.133 Safari/537.36";

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
        return getWebViewMajorVersion(ctx) > 0 && getWebViewMajorVersion(ctx) < 50;
    }
}
