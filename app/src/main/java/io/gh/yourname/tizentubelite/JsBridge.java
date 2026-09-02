package io.gh.yourname.tizentubelite;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

public class JsBridge {
    private Context ctx;
    public JsBridge(Context ctx) { this.ctx = ctx; }

    @JavascriptInterface
    public void onAdBlocked(String msg) { }

    @JavascriptInterface
    public void showToast(String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void onBotCheck() {
        if (ctx instanceof MainActivity) ((MainActivity) ctx).onBotCheckDetected();
    }

    @JavascriptInterface
    public void playVideo(String videoId) {
        if (videoId == null || videoId.isEmpty()) return;
        android.content.Intent i = new android.content.Intent(ctx, io.gh.yourname.tizentubelite.ui.PlayerActivity.class);
        i.putExtra("videoId", videoId);
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    @JavascriptInterface
    public void playVideoWithTitle(String videoId, String title) {
        if (videoId == null || videoId.isEmpty()) return;
        android.content.Intent i = new android.content.Intent(ctx, io.gh.yourname.tizentubelite.ui.PlayerActivity.class);
        i.putExtra("videoId", videoId);
        i.putExtra("videoTitle", title != null ? title : "");
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }
}
