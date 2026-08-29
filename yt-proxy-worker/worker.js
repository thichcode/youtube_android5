export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const targetHostname = "www.youtube.com";
    const targetUrl = `https://${targetHostname}${url.pathname}${url.search}`;

    // Only proxy YouTube TV and API paths, let googlevideo go direct (not proxied)
    // If request is for worker root, redirect to /tv
    if (url.pathname === "/" || url.pathname === "") {
      return Response.redirect(`${url.origin}/tv`, 302);
    }

    // Prepare headers - spoof TV
    const headers = new Headers(request.headers);
    headers.set("Host", targetHostname);
    headers.set("User-Agent", "Mozilla/5.0 (Linux; Android 11; AFTSS Build/RTM2.230615.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.5993.48 Safari/537.36 CrKey/1.54 TV Cobalt/27.lts.2-qa");
    headers.set("Referer", `https://${targetHostname}/tv`);
    headers.set("Origin", `https://${targetHostname}`);
    // Remove Cloudflare headers that may flag bot
    headers.delete("cf-connecting-ip");
    headers.delete("cf-ray");
    headers.delete("x-forwarded-for");
    // Ensure cookies are passed
    headers.set("Accept", headers.get("Accept") || "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
    headers.set("Accept-Language", "en-US,en;q=0.9");

    // Handle preflight
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

    // Handle redirects - rewrite youtube.com redirects to worker origin
    if (response.status >= 300 && response.status < 400) {
      const loc = response.headers.get("Location");
      if (loc && loc.includes(targetHostname)) {
        const newLoc = loc.replace(`https://${targetHostname}`, url.origin);
        const h = new Headers(response.headers);
        h.set("Location", newLoc);
        return new Response(null, { status: response.status, headers: h });
      }
    }

    // Clone response to modify
    const contentType = response.headers.get("Content-Type") || "";
    let newHeaders = new Headers(response.headers);
    // CORS and remove security headers that block embedding
    newHeaders.set("Access-Control-Allow-Origin", "*");
    newHeaders.set("Access-Control-Allow-Credentials", "true");
    newHeaders.delete("Content-Security-Policy");
    newHeaders.delete("X-Frame-Options");
    newHeaders.delete("Clear-Site-Data");
    // Keep cookies
    // For HTML, rewrite absolute youtube.com URLs to worker origin to keep subsequent requests proxied
    if (contentType.includes("text/html")) {
      let text = await response.text();
      // Rewrite https://www.youtube.com -> worker origin
      text = text.replaceAll(`https://${targetHostname}`, url.origin);
      text = text.replaceAll(`https:\\/\\/www\\.youtube\\.com`, url.origin.replace("https://", "https:\\/\\/"));
      // Inject webdriver spoof early
      const spoof = `<script>try{Object.defineProperty(navigator,'webdriver',{get:()=>false});window.chrome={runtime:{}};}catch(e){}</script>`;
      text = text.replace("<head>", `<head>${spoof}`);
      newHeaders.delete("Content-Length");
      newHeaders.delete("Content-Encoding");
      return new Response(text, { status: response.status, headers: newHeaders });
    }
    if (contentType.includes("application/json") || contentType.includes("text/javascript") || contentType.includes("application/javascript")) {
      let text = await response.text();
      text = text.replaceAll(`https://${targetHostname}`, url.origin);
      newHeaders.delete("Content-Length");
      return new Response(text, { status: response.status, headers: newHeaders });
    }

    // For other content (images, etc), stream directly
    // Need to handle body as stream
    return new Response(response.body, { status: response.status, headers: newHeaders });
  }
}
