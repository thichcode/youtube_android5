package io.gh.yourname.tizentubelite;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.KeyEvent;
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
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(WebViewHelper.USER_AGENT);
        webView.addJavascriptInterface(new JsBridge(this), "TizenLite");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (userScript != null) view.evaluateJavascript(userScript, null);
            }
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                offlineView.setVisibility(android.view.View.VISIBLE);
                view.setVisibility(android.view.View.GONE);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
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
