(function() {
    if (window.__aetherNetTracked) return;
    window.__aetherNetTracked = true;
    document.addEventListener('scroll', function(e) {
        var t = e.target;
        var isRoot = !t || t === document || t === document.documentElement || t === document.body;
        var nested = !isRoot && (t.scrollTop > 0 || t.scrollLeft > 0);
        NestedScrollBridge.onNestedScroll(nested);
    }, true);
})();
