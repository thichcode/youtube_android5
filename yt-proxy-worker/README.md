# YouTube TV Proxy (Cloudflare Worker)

Bypass YouTube `Sign in to confirm you're not a bot` trên WebView/Memu bằng cách proxy `www.youtube.com/tv` qua Cloudflare IP whitelisted.

## Deploy (1 lần)

```bash
cd yt-proxy-worker
npx wrangler login   # mở browser đăng nhập Cloudflare
npx wrangler deploy  # -> https://yt-tv-proxy.<your-subdomain>.workers.dev
```

Free tier: 100k req/ngày (đủ cho ~200 session 2h), chỉ proxy HTML/API, video `*.googlevideo.com` vẫn direct.

## Lite đổi URL

Sau khi deploy, sửa `app/src/main/java/.../WebViewHelper.java`:

```java
public static final String YT_TV_URL = "https://yt-tv-proxy.<your-subdomain>.workers.dev/tv";
```

Build lại `TizenTubeLite-v1.0.4-lite`.

## Test

```bash
curl https://yt-tv-proxy.<sub>.workers.dev/tv -H "User-Agent: TV Cobalt" | head -20
```
