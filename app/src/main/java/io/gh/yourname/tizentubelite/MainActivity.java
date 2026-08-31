package io.gh.yourname.tizentubelite;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    private static final String TAG = "TizenLite";
    private WebView webView;
    private TextView offlineView;
    private String userScript;
    private int interceptCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);
        offlineView = findViewById(R.id.offlineView);

        if (WebViewHelper.isWebViewTooOld(this)) {
            int v = WebViewHelper.getWebViewMajorVersion(this);
            new AlertDialog.Builder(this)
                .setTitle("WebView too old")
                .setMessage(getString(R.string.webview_too_old, v))
                .setPositiveButton("Continue", (d,w)-> load())
                .setNegativeButton("Exit", (d,w)-> finish())
                .setCancelable(false).show();
        } else {
            load();
        }
    }

    private void load() {
        userScript = loadAsset("userScript.js");
        Log.d(TAG, "=== DIAG load() ===");
        Log.d(TAG, "DIAG WebView UA=" + WebViewHelper.USER_AGENT);
        Log.d(TAG, "DIAG YT_URL=" + WebViewHelper.YT_TV_URL);
        Log.d(TAG, "DIAG PROXY_BASE=" + WebViewHelper.PROXY_BASE);
        Log.d(TAG, "DIAG WebView version=" + WebViewHelper.getWebViewMajorVersion(this));
        // Cookie + storage to avoid bot check (YouTube needs cookies)
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) {
            cm.setAcceptThirdPartyCookies(webView, true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        Log.d(TAG, "DIAG cookies before load=" + cm.getCookie("https://www.youtube.com"));
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(WebViewHelper.USER_AGENT);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.addJavascriptInterface(new JsBridge(this), "TizenLite");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, android.webkit.WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains("youtube.com/youtubei/") || url.contains("youtube.com/api/lounge/")) {
                    interceptCount++;
                    Log.d(TAG, "DIAG intercept #" + interceptCount + " url=" + url + " method=" + request.getMethod());
                }
                return super.shouldInterceptRequest(view, request);
            }
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                Log.d(TAG, "DIAG onPageStarted url=" + url);
                // Inject bot-spoof IMMEDIATELY before any page JS runs
                view.evaluateJavascript(WebViewHelper.BOT_SPOOF_JS, null);
                view.evaluateJavascript(WebViewHelper.FETCH_PROXY_JS, null);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "DIAG onPageFinished url=" + url + " title=" + view.getTitle() + " interceptCount=" + interceptCount);
                Log.d(TAG, "DIAG cookies after load=" + CookieManager.getInstance().getCookie("https://www.youtube.com"));
                // Re-inject bot-spoof (YouTube may have cleared it)
                view.evaluateJavascript(WebViewHelper.BOT_SPOOF_JS, null);
                // Inject userScript (ad-block + proxy + bot-bypass)
                if (userScript != null) view.evaluateJavascript(userScript, null);
                // Diagnostics
                view.evaluateJavascript("(function(){var t=document.documentElement?document.documentElement.innerHTML:'';var txt=document.body?document.body.innerText:'';return JSON.stringify({len:t.length,txt:txt.substring(0,500),url:location.href,title:document.title,hasBot:txt.indexOf('not a bot')!==-1||txt.indexOf('Sign in to confirm')!==-1,hasCast:txt.indexOf('Ready to cast')!==-1});})();", value -> {
                    Log.d(TAG, "DIAG pageContent=" + value);
                });
            }
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "DIAG onReceivedError code=" + errorCode + " desc=" + description + " url=" + failingUrl);
                offlineView.setVisibility(android.view.View.VISIBLE);
                view.setVisibility(android.view.View.GONE);
            }
            @Override
            public void onReceivedHttpError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceResponse errorResponse) {
                Log.w(TAG, "DIAG onReceivedHttpError url=" + request.getUrl() + " code=" + errorResponse.getStatusCode());
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                Log.d(TAG, "DIAG console [" + m.messageLevel() + "] " + m.message() + " -- " + m.sourceId() + ":" + m.lineNumber());
                return super.onConsoleMessage(m);
            }
        });
        if (Build.VERSION.SDK_INT >= 19) WebView.setWebContentsDebuggingEnabled(true);
        Log.d(TAG, "DIAG calling loadUrl=" + WebViewHelper.YT_TV_URL);
        webView.loadUrl(WebViewHelper.YT_TV_URL);
        webView.requestFocus();
    }

    private String loadAsset(String name) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open(name)))) {
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack(); return true;
        }
        if (offlineView.getVisibility()==android.view.View.VISIBLE) {
            offlineView.setVisibility(android.view.View.GONE);
            webView.setVisibility(android.view.View.VISIBLE);
            webView.reload(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
