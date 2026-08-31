// TizenTube Lite - ad-block + Worker proxy + bot-check bypass (WebView Android 5)
(function() {
    'use strict';

    // ===== BOT-CHECK BYPASS (must run first) =====
    // Override navigator.webdriver repeatedly (YouTube may re-inject)
    (function(){
        try {
            Object.defineProperty(navigator, 'webdriver', {get: function(){return false}, configurable: true});
            if (!window.chrome) window.chrome = {};
            if (!window.chrome.runtime) window.chrome.runtime = {connect: function(){}, sendMessage: function(){}};
            // Override toString to hide native code
            var fakeStr = function(){return 'function toString() { [native code] }'};
            Object.defineProperty(navigator.__proto__, 'webdriver', {toString: fakeStr});
        } catch(e) {}
    })();

    // Bot check detection + auto-reload with cooldown
    var _botCheckCount = 0;
    var _lastBotReload = 0;
    function detectBotCheck() {
        var txt = document.body ? document.body.innerText : '';
        var html = document.documentElement ? document.documentElement.innerHTML : '';
        // YouTube bot check patterns
        var isBot = txt.indexOf('not a bot') !== -1
            || txt.indexOf('Sign in to confirm') !== -1
            || txt.indexOf('confirm you') !== -1
            || txt.indexOf('unusual traffic') !== -1
            || txt.indexOf('are not a robot') !== -1
            || txt.indexOf('verify you are human') !== -1
            || html.indexOf('sb-captcha-container') !== -1
            || html.indexOf('captcha') !== -1;
        if (isBot) {
            var now = Date.now();
            _botCheckCount++;
            console.log('[TizenTubeLite] BOT CHECK detected (#' + _botCheckCount + ')');
            // Try to bypass: click any verify/captcha button
            var btns = document.querySelectorAll('button, [role="button"], input[type="submit"]');
            for (var i = 0; i < btns.length; i++) {
                var b = btns[i];
                var t = (b.textContent || b.value || '').toLowerCase();
                if (t.indexOf('verify') !== -1 || t.indexOf('confirm') !== -1 || t.indexOf('continue') !== -1 || t.indexOf('next') !== -1) {
                    console.log('[TizenTubeLite] clicking bot-check button: ' + t);
                    b.click();
                    return;
                }
            }
            // If too many bot checks, reload with different URL
            if (_botCheckCount > 2 && (now - _lastBotReload) > 10000) {
                _lastBotReload = now;
                _botCheckCount = 0;
                console.log('[TizenTubeLite] auto-reloading to bypass bot check');
                // Try m.youtube.com first (less likely to bot check)
                if (location.hostname !== 'm.youtube.com') {
                    location.href = 'https://m.youtube.com/?noapp=1';
                } else {
                    location.reload();
                }
            }
        }
    }

    // ===== PROXY youtubei via Worker - DISABLED for residential IP (direct works) =====
    // (proxied requests were causing blank feed; re-enable only if datacenter IP)
    /* disabled
    (function(){
        var PROXY = "https://yt-tv-proxy.dvt-kisu.workers.dev";
        var ORIG = "https://www.youtube.com";
        function proxyUrl(u){
            if(typeof u!=="string") return u;
            if(u.indexOf(ORIG+"/youtubei/")===0) return u.replace(ORIG, PROXY);
            if(u.indexOf("/youtubei/")===0) return PROXY + u;
            if(u.indexOf("youtubei/")===0) return PROXY + "/" + u;
            return u;
        }
        try {
            var origFetch = window.fetch;
            window.fetch = function(input, init){
                try {
                    var url = typeof input==="string" ? input : (input && input.url ? input.url : null);
                    var proxied = proxyUrl(url);
                    if(proxied!==url) console.log("[TizenTubeLite] proxy fetch "+url+" -> "+proxied);
                    if (typeof input==="string") input = proxied;
                    else if (input && input.url && proxied!==input.url) input = new Request(proxied, input);
                } catch(e){}
                return origFetch.call(this, input, init);
            };
            var origOpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url, async, user, pass){
                try{ var pu=proxyUrl(url); if(pu!==url) console.log("[TizenTubeLite] proxy XHR "+url+" -> "+pu); url=pu; }catch(e){}
                return origOpen.call(this, method, url, async, user, pass);
            };
            console.log("[TizenTubeLite] fetch/XHR proxy enabled -> "+PROXY);
        } catch(e){ console.log("[TizenTubeLite] proxy hook failed "+e); }
    })();
    */

    // ===== AD STRIPPING (JSON.parse hook) =====
    var origParse = JSON.parse;
    JSON.parse = function() {
        var r = origParse.apply(this, arguments);
        try {
            if (r && r.adPlacements) r.adPlacements = [];
            if (r && r.playerAds) r.playerAds = false;
            if (r && r.adSlots) r.adSlots = [];
            // Strip masthead ad from home
            if (r && r.contents && r.contents.tvBrowseRenderer && r.contents.tvBrowseRenderer.content
                && r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer
                && r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content
                && r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content.sectionListRenderer
                && r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content.sectionListRenderer.contents) {
                r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content.sectionListRenderer.contents =
                    r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content.sectionListRenderer.contents.filter(
                        function(elm) { return !elm.adSlotRenderer; }
                    );
            }
            // Remove shorts ads
            if (r && !Array.isArray(r) && r.entries) {
                r.entries = r.entries.filter(function(elm) {
                    return !(elm && elm.command && elm.command.reelWatchEndpoint && elm.command.reelWatchEndpoint.adClientParams && elm.command.reelWatchEndpoint.adClientParams.isAd);
                });
            }
            // Strip adSlotRenderer from shelves
            function stripShelves(shelves) {
                if (!shelves) return;
                for (var i = shelves.length - 1; i >= 0; i--) {
                    var sh = shelves[i];
                    if (sh && sh.shelfRenderer && sh.shelfRenderer.content && sh.shelfRenderer.content.horizontalListRenderer && sh.shelfRenderer.content.horizontalListRenderer.items) {
                        var items = sh.shelfRenderer.content.horizontalListRenderer.items;
                        for (var j = items.length - 1; j >= 0; j--) {
                            if (items[j] && items[j].adSlotRenderer) items.splice(j, 1);
                        }
                    }
                }
            }
            if (r && r.contents && r.contents.tvBrowseRenderer && r.contents.tvBrowseRenderer.content && r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer && r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content && r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content.sectionListRenderer && r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content.sectionListRenderer.contents) {
                stripShelves(r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content.sectionListRenderer.contents);
            }
            if (r && r.contents && r.contents.sectionListRenderer && r.contents.sectionListRenderer.contents) {
                stripShelves(r.contents.sectionListRenderer.contents);
            }
            if (r && r.continuationContents && r.continuationContents.sectionListContinuation && r.continuationContents.sectionListContinuation.contents) {
                stripShelves(r.continuationContents.sectionListContinuation.contents);
            }
            if (r && r.continuationContents && r.continuationContents.horizontalListContinuation && r.continuationContents.horizontalListContinuation.items) {
                var hItems = r.continuationContents.horizontalListContinuation.items;
                for (var k = hItems.length - 1; k >= 0; k--) {
                    if (hItems[k] && hItems[k].adSlotRenderer) hItems.splice(k, 1);
                }
            }
        } catch(e) {}
        return r;
    };
    window.JSON.parse = JSON.parse;

    // ===== DOM-LEVEL AD SKIP =====
    function skipAdDOM() {
        var video = document.querySelector('video');
        var ad = document.querySelector('.ad-showing');
        if (ad && video) {
            try { video.currentTime = video.duration || 9999; } catch(e) {}
            var btn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern');
            if (btn) btn.click();
        }
        document.querySelectorAll('.ytp-ad-overlay-container, .ytp-ad-image-overlay, .ad-container').forEach(function(e){ e.style.display='none'; });
    }

    // ===== BYPASS CAST SCREEN =====
    function bypassCast() {
        var txt = document.body ? document.body.innerText : '';
        if (txt.indexOf('Ready to cast') !== -1 && txt.indexOf('NO DEBUG ACCESS') !== -1) {
            console.log('[TizenTubeLite] bypassing cast screen');
            try { history.pushState({}, '', '/tv/browse'); location.href = '/tv/browse'; } catch(e) {}
            var btn = document.querySelector('[data-action="browse"], a[href*="/tv/browse"]');
            if (btn) btn.click();
        }
        // Hide red debug bar
        document.querySelectorAll('*').forEach(function(el){
            if (el.textContent && el.textContent.indexOf('NO DEBUG ACCESS') !== -1) {
                var p = el;
                while(p && p.tagName !== 'BODY') { p = p.parentElement; if(p && p.style) p.style.display='none'; break; }
            }
        });
    }

    // ===== OBSERVER =====
    var obs = new MutationObserver(function(){ skipAdDOM(); bypassCast(); detectBotCheck(); });
    try { obs.observe(document.documentElement, {childList:true, subtree:true}); } catch(e) {}
    setInterval(skipAdDOM, 700);
    setInterval(bypassCast, 1500);
    setInterval(detectBotCheck, 2000);

    // ===== CAPTCHA AUTO-SOLVE: Click verify buttons, hide captcha overlay =====
    function autoCaptcha() {
        // Hide captcha containers
        document.querySelectorAll('[id*="captcha"], [class*="captcha"], [id*="recaptcha"], [class*="recaptcha"]').forEach(function(e){
            e.style.display = 'none';
        });
        // Click verify buttons
        var btns = document.querySelectorAll('button, [role="button"], input[type="submit"], a');
        for (var i = 0; i < btns.length; i++) {
            var t = (btns[i].textContent || btns[i].value || '').toLowerCase();
            if (t.indexOf('i\'m not a robot') !== -1 || t.indexOf('verify') !== -1 || t.indexOf('continue') !== -1) {
                btns[i].click();
            }
        }
    }
    setInterval(autoCaptcha, 3000);

    // Limit to 720p on low RAM
    setInterval(function(){
        try {
            var p = document.querySelector('#movie_player');
            if (p && p.setPlaybackQuality) p.setPlaybackQuality('medium');
        } catch(e) {}
    }, 4000);

    console.log('[TizenTubeLite] v3 bot-bypass + ad-block injected');
})();
