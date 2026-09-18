(function() {
    if (window.__aetherNetNotifInstalled) return;
    window.__aetherNetNotifInstalled = true;
    var _seq = 0;
    var _pending = {};
    function AetherNetNotification(title, options) {
        if (!(this instanceof AetherNetNotification)) return;
        options = options || {};
        AetherNetNotificationBridge.postNotification(
            String(title || ''),
            String(options.body || ''),
            String(options.tag || ''),
            String(window.location.hostname || '')
        );
    }
    AetherNetNotification.prototype.close = function() {};
    Object.defineProperty(AetherNetNotification, 'permission', {
        get: function() {
            return AetherNetNotificationBridge.getPermissionState(String(window.location.hostname || ''));
        },
        configurable: true
    });
    AetherNetNotification.requestPermission = function(callback) {
        var id = String(++_seq);
        return new Promise(function(resolve) {
            _pending[id] = function(result) {
                delete _pending[id];
                if (typeof callback === 'function') callback(result);
                resolve(result);
            };
            AetherNetNotificationBridge.requestPermission(id, String(window.location.hostname || ''));
        });
    };
    window._AetherNetResolvePermission = function(id, result) {
        var cb = _pending[String(id)];
        if (cb) cb(result);
    };
    window.Notification = AetherNetNotification;
})();
