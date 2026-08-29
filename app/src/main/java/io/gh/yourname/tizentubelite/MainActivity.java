package io.gh.yourname.tizentubelite;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class MainActivity extends Activity {
    private WebView webView;
    private TextView offlineView;
    private String userScript;

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
        // Cookie + storage to avoid bot check (YouTube needs cookies)
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) {
            cm.setAcceptThirdPartyCookies(webView, true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(WebViewHelper.USER_AGENT);
        // Enable mixed content & file access for leanback
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.addJavascriptInterface(new JsBridge(this), "TizenLite");
        // Spoof webdriver before any page loads
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, android.webkit.WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Proxy only YouTube API via Worker to bypass bot IP check, keep HTML on youtube.com domain
                if (url.contains("youtube.com/youtubei/v1/") || url.contains("youtube.com/api/")) {
                    try {
                        String proxied = url.replace("https://www.youtube.com", WebViewHelper.PROXY_BASE);
                        java.net.URL u = new java.net.URL(proxied);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
                        conn.setRequestMethod(request.getMethod());
                        // Copy headers
                        for (String h : request.getRequestHeaders().keySet()) {
                            conn.setRequestProperty(h, request.getRequestHeaders().get(h));
                        }
                        conn.setRequestProperty("User-Agent", WebViewHelper.USER_AGENT);
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(10000);
                        conn.connect();
                        String mime = conn.getContentType();
                        if (mime == null) mime = "application/json";
                        String enc = conn.getContentEncoding();
                        return new android.webkit.WebResourceResponse(mime.split(";")[0], enc != null ? enc : "utf-8", conn.getInputStream());
                    } catch (Exception e) {
                        // fallback to original
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                view.evaluateJavascript(WebViewHelper.BOT_SPOOF_JS, null);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(WebViewHelper.BOT_SPOOF_JS, null);
                if (userScript != null) view.evaluateJavascript(userScript, null);
                // Detect bot check page and offer fallback
                view.evaluateJavascript("(function(){var t=document.body?document.body.innerText:'';if(t.includes('not a bot')||t.includes('Sign in to confirm')){return 'BOT_DETECTED';}return 'OK';})();", value -> {
                    if (value != null && value.contains("BOT_DETECTED")) {
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("YouTube bot check")
                            .setMessage("YouTube yêu cầu xác minh. Thử mở m.youtube.com (ít bị check hơn) hoặc quét QR yt.be/activate trên điện thoại?")
                            .setPositiveButton("Thử m.youtube.com", (d,w)-> view.loadUrl(WebViewHelper.FALLBACK_URL))
                            .setNegativeButton("Thử lại TV", (d,w)-> view.reload())
                            .setNeutralButton("Đóng", null)
                            .show();
                    }
                });
            }
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                offlineView.setVisibility(android.view.View.VISIBLE);
                view.setVisibility(android.view.View.GONE);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        // Enable debugging for inspecting bot check
        if (Build.VERSION.SDK_INT >= 19) WebView.setWebContentsDebuggingEnabled(true);
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
