// TizenTube Lite - ad-block only (stripped from TizenTube 1.7.0 mods/adblock.js)
// Removed: heavy features (branding, segments, thumbnails, chapters)
// Always enabled - no configRead dependency
(function() {
    'use strict';
    const origParse = JSON.parse;
    JSON.parse = function() {
        const r = origParse.apply(this, arguments);
        // core adPlacements / playerAds / adSlots stripping (uBlock rule)
        if (r && r.adPlacements) r.adPlacements = [];
        if (r && r.playerAds) r.playerAds = false;
        if (r && r.adSlots) r.adSlots = [];
        // Drop masthead ad from home
        if (r && r.contents && r.contents.tvBrowseRenderer && r.contents.tvBrowseRenderer.content
            && r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer
            && r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content
            && r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content.sectionListRenderer
            && r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content.sectionListRenderer.contents) {
            try {
                r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content.sectionListRenderer.contents =
                    r.contents.tvBrowseRenderer.content.tvSurfaceContentRenderer.content.sectionListRenderer.contents.filter(
                        function(elm) { return !elm.adSlotRenderer; }
                    );
            } catch(e) {}
        }
        // Remove shorts ads
        if (r && !Array.isArray(r) && r.entries) {
            try {
                r.entries = r.entries.filter(function(elm) {
                    return !(elm && elm.command && elm.command.reelWatchEndpoint && elm.command.reelWatchEndpoint.adClientParams && elm.command.reelWatchEndpoint.adClientParams.isAd);
                });
            } catch(e) {}
        }
        // Remove tile adSlotRenderer items in shelves
        function stripShelves(shelves) {
            if (!shelves) return;
            for (var i = shelves.length - 1; i >= 0; i--) {
                var shelve = shelves[i];
                if (shelve && shelve.shelfRenderer && shelve.shelfRenderer.content && shelve.shelfRenderer.content.horizontalListRenderer && shelve.shelfRenderer.content.horizontalListRenderer.items) {
                    var items = shelve.shelfRenderer.content.horizontalListRenderer.items;
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
        return r;
    };
    window.JSON.parse = JSON.parse;
    for (var key in window._yttv) {
        if (window._yttv[key] && window._yttv[key].JSON && window._yttv[key].JSON.parse) {
            window._yttv[key].JSON.parse = JSON.parse;
        }
    }
    // DOM-level fallback: skip ad if JSON patch missed
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
    var obs = new MutationObserver(function(){ skipAdDOM(); });
    try { obs.observe(document.documentElement, {childList:true, subtree:true}); } catch(e) {}
    setInterval(skipAdDOM, 700);
    // limit to 720p on low RAM
    setInterval(function(){
        try {
            var p = document.querySelector('#movie_player');
            if (p && p.setPlaybackQuality) p.setPlaybackQuality('medium');
        } catch(e) {}
    }, 4000);
    console.log('[TizenTubeLite] ad-block injected');
})();
