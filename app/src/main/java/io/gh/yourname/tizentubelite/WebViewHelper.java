package io.gh.yourname.tizentubelite;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class WebViewHelper {
    public static final String YT_TV_URL = "https://www.youtube.com";
    public static final String PROXY_BASE = "https://yt-tv-proxy.dvt-kisu.workers.dev";
    // Chrome 95 = last WebView for Android 5 (API21) - supports ?. / ?? for m.youtube.com
    public static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 11; AFTSS Build/RTM2.230615.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.74 Safari/537.36";
    public static final String FALLBACK_URL = "https://m.youtube.com/?noapp=1";
    // JS to spoof webdriver and chrome object (bot detection)
    public static final String BOT_SPOOF_JS = "(function(){try{Object.defineProperty(navigator,'webdriver',{get:()=>false});window.chrome={runtime:{}};Object.defineProperty(navigator,'plugins',{get:()=>[1,2]});Object.defineProperty(navigator,'languages',{get:()=>['en-US','en']});}catch(e){}})();";
    public static final String FETCH_PROXY_JS = "(function(){try{var PROXY='https://yt-tv-proxy.dvt-kisu.workers.dev',ORIG='https://www.youtube.com';function p(u){if(typeof u!=='string')return u;if(u.indexOf(ORIG+'/youtubei/')===0)return u.replace(ORIG,PROXY);if(u.indexOf('/youtubei/')===0)return PROXY+u;if(u.indexOf('youtubei/')===0)return PROXY+'/'+u;return u;}var of=window.fetch;window.fetch=function(i,init){try{var url=typeof i==='string'?i:(i&&i.url?i.url:null);var pu=p(url);if(pu!==url)console.log('[TizenTubeLite] proxy fetch '+url+' -> '+pu);if(typeof i==='string')i=pu;else if(i&&i.url&&pu!==i.url)i=new Request(pu,i);}catch(e){}return of.call(this,i,init);};var oo=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(m,u,a,b,c){try{var pu=p(u);if(pu!==u)console.log('[TizenTubeLite] proxy XHR '+u+' -> '+pu);u=pu;}catch(e){}return oo.call(this,m,u,a,b,c);};console.log('[TizenTubeLite] fetch/XHR proxy enabled');}catch(e){}})();";

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
