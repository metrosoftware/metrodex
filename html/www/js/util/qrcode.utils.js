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

var MRS = (function (MRS) {

    MRS.scanQRCode = function(readerId, callback) {
        if (!MRS.isScanningAllowed()) {
            $.growl($.t("scanning_not_allowed"));
            return;
        }
        if (MRS.isCordovaScanningEnabled()) {
            if (MRS.isCameraPermissionRequired()) {
                MRS.logConsole("request camera permission");
                cordova.plugins.permissions.hasPermission(cordova.plugins.permissions.CAMERA, function(status) {
                    cordovaCheckCameraPermission(status, callback)
                }, null);
            } else {
                MRS.logConsole("scan without requesting camera permission");
                cordovaScan(callback);
            }
        } else {
            MRS.logConsole("scan using desktop browser");
            html5Scan(readerId, callback);
        }
    };

    function cordovaCheckCameraPermission(status, callback) {
        if(!status.hasPermission) {
            var errorCallback = function() {
                MRS.logConsole('Camera permission not granted');
            };

            MRS.logConsole('Request camera permission');
            cordova.plugins.permissions.requestPermission(cordova.plugins.permissions.CAMERA, function(status) {
                if(!status.hasPermission) {
                    MRS.logConsole('Camera status has no permission');
                    errorCallback();
                    return;
                }
                cordovaScan(callback);
            }, errorCallback);
            return;
        }
        MRS.logConsole('Camera already has permission');
        cordovaScan(callback);
    }

    function cordovaScan(callback) {
        try {
            MRS.logConsole("before scan");
            cordova.plugins.barcodeScanner.scan(function(result) {
                cordovaScanQRDone(result, callback)
            }, function (error) {
                MRS.logConsole(error);
            });
        } catch (e) {
            MRS.logConsole(e.message);
        }
    }

    function cordovaScanQRDone(result, callback) {
        MRS.logConsole("Scan result format: " + result.format);
        if (!result.cancelled && result.format == "QR_CODE") {
            MRS.logConsole("Scan complete, send result to callback");
            callback(result.text);
        } else {
            MRS.logConsole("Scan cancelled");
        }
    }

    function html5Scan(readerId, callback) {
        var reader = $("#" + readerId);
        if (reader.is(':visible')) {
            reader.fadeOut();
            if (reader.data('stream')) {
                reader.html5_qrcode_stop();
            }
            return;
        }
        reader.empty();
        reader.fadeIn();
        reader.html5_qrcode(
            function (data) {
                MRS.logConsole(data);
                callback(data);
                reader.hide();
                reader.html5_qrcode_stop();
            },
            function (error) {},
            function (videoError, localMediaStream) {
                MRS.logConsole(videoError);
                reader.hide();
                if (!localMediaStream) {
                    $.growl($.t("video_not_supported"));
                }
                if (reader.data('stream')) {
                    reader.html5_qrcode_stop();
                }
            }
        );
    }

    return MRS;
}(MRS || {}));
