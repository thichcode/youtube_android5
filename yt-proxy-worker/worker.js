export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    console.log(`[Worker] ${request.method} ${url.pathname}${url.search} UA=${(request.headers.get("User-Agent")||"").slice(0,40)}`);
    const targetHostname = "www.youtube.com";
    const targetUrl = `https://${targetHostname}${url.pathname}${url.search}`;

    if (url.pathname === "/" || url.pathname === "") {
      return Response.redirect(`${url.origin}/tv`, 302);
    }

    const headers = new Headers(request.headers);
    headers.set("Host", targetHostname);
    headers.set("User-Agent", "Mozilla/5.0 (Linux; Android 11; AFTSS Build/RTM2.230615.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.5993.48 Safari/537.36 CrKey/1.54 TV Cobalt/27.lts.2-qa");
    headers.set("Referer", `https://${targetHostname}/tv`);
    headers.set("Origin", `https://${targetHostname}`);
    headers.delete("cf-connecting-ip");
    headers.delete("cf-ray");
    headers.delete("x-forwarded-for");
    headers.set("Accept", headers.get("Accept") || "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
    headers.set("Accept-Language", "en-US,en;q=0.9");

    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
          "Access-Control-Allow-Headers": request.headers.get("Access-Control-Request-Headers") || "*",
        }
      });
    }

    let body = undefined;
    if (request.method !== "GET" && request.method !== "HEAD") {
      body = await request.arrayBuffer();
    }

    const proxyRequest = new Request(targetUrl, {
      method: request.method,
      headers: headers,
      body: body,
      redirect: "manual",
    });

    let response;
    try {
      response = await fetch(proxyRequest);
    } catch (e) {
      return new Response(`Proxy error: ${e.message}`, { status: 502 });
    }

    if (response.status >= 300 && response.status < 400) {
      const loc = response.headers.get("Location");
      if (loc && loc.includes(targetHostname)) {
        const newLoc = loc.replace(`https://${targetHostname}`, url.origin);
        const h = new Headers(response.headers);
        h.set("Location", newLoc);
        return new Response(null, { status: response.status, headers: h });
      }
    }

    const contentType = response.headers.get("Content-Type") || "";
    let newHeaders = new Headers(response.headers);
    newHeaders.set("Access-Control-Allow-Origin", "*");
    newHeaders.set("Access-Control-Allow-Credentials", "true");
    newHeaders.delete("Content-Security-Policy");
    newHeaders.delete("X-Frame-Options");
    newHeaders.delete("Clear-Site-Data");
    // FIX: Rewrite Set-Cookie Domain=.youtube.com -> no Domain (so it works on workers.dev)
    // Otherwise cookies with Domain youtube.com are ignored on workers.dev and YouTube shows "NO DEBUG ACCESS"
    const cookies = response.headers.getSetCookie ? response.headers.getSetCookie() : [];
    // Fallback for older runtime
    if (cookies.length > 0) {
      newHeaders.delete("Set-Cookie");
      for (let c of cookies) {
        // Remove Domain attribute
        let fixed = c.replace(/Domain=\.?youtube\.com;?/gi, "").replace(/Domain=\.?google\.com;?/gi, "").replace(/;;/g, ";").trim();
        newHeaders.append("Set-Cookie", fixed);
      }
    } else {
      // Single Set-Cookie header fallback
      const sc = response.headers.get("Set-Cookie");
      if (sc && sc.includes("Domain=.youtube.com")) {
        newHeaders.set("Set-Cookie", sc.replace(/Domain=\.?youtube\.com;?/gi, ""));
      }
    }

    if (contentType.includes("text/html")) {
      let text = await response.text();
      // Do NOT rewrite youtube.com URLs - keep original so YouTube JS sees correct domain for localStorage
      // Only inject webdriver spoof
      const spoof = `<script>try{Object.defineProperty(navigator,'webdriver',{get:()=>false});window.chrome={runtime:{}};}catch(e){}</script>`;
      text = text.replace("<head>", `<head>${spoof}`);
      newHeaders.delete("Content-Length");
      newHeaders.delete("Content-Encoding");
      return new Response(text, { status: response.status, headers: newHeaders });
    }
    if (contentType.includes("application/json") || contentType.includes("text/javascript") || contentType.includes("application/javascript")) {
      let text = await response.text();
      // Don't rewrite for API - keep youtube.com URLs, WebView will intercept via shouldInterceptRequest? Actually keep as is for now
      newHeaders.delete("Content-Length");
      return new Response(text, { status: response.status, headers: newHeaders });
    }

    return new Response(response.body, { status: response.status, headers: newHeaders });
  }
}
