(function() {
    if (window.__aetherNetLinkTrackerInstalled) return;
    window.__aetherNetLinkTrackerInstalled = true;
    window.__aetherNetLastTouchedLinkText = '';
    document.addEventListener('touchstart', function(e) {
        var el = e.target;
        while (el) {
            if (el.tagName === 'A') {
                window.__aetherNetLastTouchedLinkText = (el.textContent || '').trim().replace(/\s+/g, ' ').substring(0, 200);
                return;
            }
            el = el.parentElement;
        }
    }, true);
})();
