package io.gh.yourname.tizentubelite;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

public class JsBridge {
    private Context ctx;
    public JsBridge(Context ctx) { this.ctx = ctx; }

    @JavascriptInterface
    public void onAdBlocked(String msg) {
        // no-op, just for logging via JS
    }

    @JavascriptInterface
    public void showToast(String msg) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    }
}
