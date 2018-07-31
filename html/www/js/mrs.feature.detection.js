/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 * Copyright © 2016-2017 Jelurida IP B.V.                                     *
 * Copyright © 2018 metro.software                                            *
 *                                                                            *
 * See the LICENSE.txt file at the top-level directory of this distribution   *
 * for licensing information.                                                 *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,*
 * no part of the Nxt software, including this file, may be copied, modified, *
 * propagated, or distributed except according to the terms contained in the  *
 * LICENSE.txt file.                                                          *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

/**
 * @depends {mrs.js}
 */
var MRS = (function (MRS) {
    var isDesktopApplication = navigator.userAgent.indexOf("JavaFX") >= 0;
    var isPromiseSupported = (typeof Promise !== "undefined" && Promise.toString().indexOf("[native code]") !== -1);
    var isMobileDevice = window["cordova"] !== undefined;
    var isLocalHost = false;
    var remoteNode = null;
    var isLoadedOverHttps = ("https:" == window.location.protocol);

    MRS.isPrivateIP = function (ip) {
        if (!/^\d+\.\d+\.\d+\.\d+$/.test(ip)) {
            return false;
        }
        var parts = ip.split('.');
        return parts[0] === '10' || parts[0] == '127' || parts[0] === '172' && (parseInt(parts[1], 10) >= 16 && parseInt(parts[1], 10) <= 31) || parts[0] === '192' && parts[1] === '168';
    };

    if (window.location && window.location.hostname) {
        var hostName = window.location.hostname.toLowerCase();
        isLocalHost = hostName == "localhost" || hostName == "127.0.0.1" || MRS.isPrivateIP(hostName);
    }

    MRS.isIndexedDBSupported = function() {
        return window.indexedDB !== undefined;
    };

    MRS.isExternalLinkVisible = function() {
        // When using JavaFX add a link to a web wallet except on Linux since on Ubuntu it sometimes hangs
        if (MRS.isMobileApp()) {
            return false;
        }
        return !(isDesktopApplication && navigator.userAgent.indexOf("Linux") >= 0);
    };

    MRS.isWebWalletLinkVisible = function() {
        if (MRS.isMobileApp()) {
            return false;
        }
        return isDesktopApplication && navigator.userAgent.indexOf("Linux") == -1;
    };

    MRS.isMobileApp = function () {
        return isMobileDevice || (MRS.mobileSettings && MRS.mobileSettings.is_simulate_app);
    };

    MRS.isEnableMobileAppSimulation = function () {
        return !isMobileDevice;
    };

    MRS.isRequireCors = function () {
        return !isMobileDevice;
    };

    MRS.isPollGetState = function() {
        // When using JavaFX do not poll the server unless it's a working as a proxy
        return !isDesktopApplication || MRS.state && MRS.state.apiProxy;
    };

    MRS.isUpdateRemoteNodes = function() {
        return MRS.state && MRS.state.apiProxy;
    };

    MRS.isRemoteNodeConnectionAllowed = function() {
        // The client always connects to remote nodes over Http since most Https nodes use a test certificate and
        // therefore cannot be used.
        // However, if the client itself is loaded over Https, it cannot connect to nodes over Http since this will
        // result in a mixed content error.
        return !isLoadedOverHttps;
    };

    MRS.isExportContactsAvailable = function() {
        return !isDesktopApplication; // When using JavaFX you cannot export the contact list
    };

    MRS.isFileEncryptionSupported = function() {
        return !isDesktopApplication; // When using JavaFX you cannot read the file to encrypt
    };

    MRS.isShowDummyCheckbox = function() {
        return isDesktopApplication && navigator.userAgent.indexOf("Linux") >= 0; // Correct rendering problem of checkboxes on Linux
    };

    MRS.isDecodePeerHallmark = function() {
        return isPromiseSupported;
    };

    MRS.getRemoteNodeUrl = function() {
        if (!MRS.isMobileApp()) {
            if (!isNode) {
                return "";
            }
            return MRS.getModuleConfig().url;
        }
        if (remoteNode) {
            return remoteNode.getUrl();
        }
        remoteNode = MRS.remoteNodesMgr.getRandomNode();
        if (remoteNode) {
            var url = remoteNode.getUrl();
            MRS.logConsole("Remote node url: " + url);
            return url;
        } else {
            MRS.logConsole("No available remote nodes");
            $.growl($.t("no_available_remote_nodes"));
        }
    };

    MRS.getRemoteNode = function () {
        return remoteNode;
    };

    MRS.resetRemoteNode = function(blacklist) {
        if (remoteNode && blacklist) {
            remoteNode.blacklist();
        }
        remoteNode = null;
    };

    MRS.getDownloadLink = function(url, link) {
        if (MRS.isMobileApp()) {
            var script = "MRS.openMobileBrowser(\"" + url + "\");";
            if (link) {
                link.attr("onclick", script);
                return;
            }
            return "<a onclick='" + script +"' class='btn btn-xs btn-default'>" + $.t("download") + "</a>";
        } else {
            if (link) {
                link.attr("href", url);
                return;
            }
            return "<a href='" + url + "' class='btn btn-xs btn-default'>" + $.t("download") + "</a>";
        }
    };

    MRS.openMobileBrowser = function(url) {
        try {
            // Works on Android 6.0 (does not work in 5.1)
            cordova.InAppBrowser.open(url, '_system');
        } catch(e) {
            MRS.logConsole(e.message);
        }
    };

    MRS.isCordovaScanningEnabled = function () {
        return isMobileDevice;
    };

    MRS.isScanningAllowed = function () {
        return isMobileDevice || isLocalHost || MRS.isTestNet;
    };

    MRS.isCameraPermissionRequired = function () {
        return device && device.platform == "Android" && device.version >= "6.0.0";
    };

    MRS.isForgingSupported = function() {
        return !MRS.isMobileApp() && !(MRS.state && MRS.state.apiProxy);
    };

    MRS.isFundingMonitorSupported = function() {
        return !MRS.isMobileApp() && !(MRS.state && MRS.state.apiProxy);
    };

    MRS.isShufflingSupported = function() {
        return !MRS.isMobileApp() && !(MRS.state && MRS.state.apiProxy);
    };

    MRS.isConfirmResponse = function() {
        return MRS.isMobileApp() || (MRS.state && MRS.state.apiProxy);
    };

    MRS.isDisplayOptionalDashboardTiles = function() {
        return !MRS.isMobileApp();
    };

    MRS.isShowClientOptionsLink = function() {
        return MRS.isMobileApp() || (MRS.state && MRS.state.apiProxy);
    };

    MRS.getGeneratorAccuracyWarning = function() {
        if (isDesktopApplication) {
            return "";
        }
        return $.t("generator_timing_accuracy_warning");
    };

    MRS.isInitializePlugins = function() {
        return !MRS.isMobileApp();
    };

    MRS.isShowRemoteWarning = function() {
        return !isLocalHost;
    };

    MRS.isForgingSafe = function() {
        return isLocalHost;
    };

    MRS.isPassphraseAtRisk = function() {
        return !isLocalHost || MRS.state && MRS.state.apiProxy || MRS.isMobileApp();
    };

    MRS.isWindowPrintSupported = function() {
        return !isDesktopApplication && !isMobileDevice;
    };

    MRS.isDisableScheduleRequest = function() {
        return MRS.isMobileApp() || (MRS.state && MRS.state.apiProxy);
    };

    MRS.getAdminPassword = function() {
        if (window.java) {
            return window.java.getAdminPassword();
        }
        if (isNode) {
            return MRS.getModuleConfig().adminPassword;
        }
        return MRS.settings.admin_password;
    };

    return MRS;
}(isNode ? client : MRS || {}, jQuery));

if (isNode) {
    module.exports = MRS;
}
