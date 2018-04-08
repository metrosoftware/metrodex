/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 * Copyright © 2016-2017 Jelurida IP B.V.                                     *
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
var MRS = (function (MRS, $) {
    var level = 1;
    var java;

    MRS.logConsole = function (msg, isDateIncluded, isDisplayTimeExact) {
        if (window.console) {
            try {
                var prefix = "";
                if (!isDateIncluded) {
                    prefix = new Date().toISOString() + " ";
                }
                var postfix = "";
                if (isDisplayTimeExact) {
                    postfix = " (" + MRS.timeExact() + ")";
                }
                var line = prefix + msg + postfix;
                if (java) {
                    java.log(line);
                } else {
                    console.log(line);
                }
            } catch (e) {
                // IE11 when running in compatibility mode
            }

        }
    };

    MRS.logException = function(e) {
        MRS.logConsole(e.message);
        if (e.stack) {
            MRS.logConsole(e.stack);
        }
    };

    MRS.isLogConsole = function (msgLevel) {
        return msgLevel <= level;
    };

    MRS.setLogConsoleLevel = function (logLevel) {
        level = logLevel;
    };

    MRS.logProperty = function(property) {
        MRS.logConsole(property + " = " + eval(property.escapeHTML()));
    };

    MRS.logArrayContent = function(array) {
        var data = '[';
        for (var i=0; i<array.length; i++) {
            data += array[i];
            if (i < array.length - 1) {
                data += ", ";
            }
        }
        data += ']';
        MRS.logConsole(data);
    };

    MRS.timeExact = function () {
        return window.performance.now() ||
            window.performance.mozNow() ||
            window.performance.msNow() ||
            window.performance.oNow() ||
            window.performance.webkitNow() ||
            Date.now; // none found - fallback to browser default
    };

    MRS.showConsole = function () {
        MRS.console = window.open("", "console", "width=750,height=400,menubar=no,scrollbars=yes,status=no,toolbar=no,resizable=yes");
        $(MRS.console.document.head).html("<title>" + $.t("console") + "</title><style type='text/css'>body { background:black; color:white; font-family:courier-new,courier;font-size:14px; } pre { font-size:14px; } #console { padding-top:15px; }</style>");
        $(MRS.console.document.body).html("<div style='position:fixed;top:0;left:0;right:0;padding:5px;background:#efefef;color:black;'>" + $.t("console_opened") + "<div style='float:right;text-decoration:underline;color:blue;font-weight:bold;cursor:pointer;' onclick='document.getElementById(\"console\").innerHTML=\"\"'>clear</div></div><div id='console'></div>");
    };

    MRS.addToConsole = function (url, type, data, response, error) {
        if (!MRS.console) {
            return;
        }

        if (!MRS.console.document || !MRS.console.document.body) {
            MRS.console = null;
            return;
        }

        url = url.replace(/&random=[\.\d]+/, "", url);

        MRS.addToConsoleBody(url + " (" + type + ") " + new Date().toString(), "url");

        if (data) {
            if (typeof data == "string") {
                var d = MRS.queryStringToObject(data);
                MRS.addToConsoleBody(JSON.stringify(d, null, "\t"), "post");
            } else {
                MRS.addToConsoleBody(JSON.stringify(data, null, "\t"), "post");
            }
        }

        if (error) {
            MRS.addToConsoleBody(response, "error");
        } else {
            MRS.addToConsoleBody(JSON.stringify(response, null, "\t"), (response.errorCode ? "error" : ""));
        }
    };

    MRS.addToConsoleBody = function (text, type) {
        var color = "";

        switch (type) {
            case "url":
                color = "#29FD2F";
                break;
            case "post":
                color = "lightgray";
                break;
            case "error":
                color = "red";
                break;
        }
        if (MRS.isLogConsole(10)) {
            MRS.logConsole(text, true);
        }
        $(MRS.console.document.body).find("#console").append("<pre" + (color ? " style='color:" + color + "'" : "") + ">" + text.escapeHTML() + "</pre>");
    };

    MRS.queryStringToObject = function (qs) {
        qs = qs.split("&");

        if (!qs) {
            return {};
        }

        var obj = {};

        for (var i = 0; i < qs.length; ++i) {
            var p = qs[i].split('=');

            if (p.length != 2) {
                continue;
            }

            obj[p[0]] = decodeURIComponent(p[1].replace(/\+/g, " "));
        }

        if ("secretPhrase" in obj) {
            obj.secretPhrase = "***";
        }

        return obj;
    };

    return MRS;
}(isNode ? client : MRS || {}, jQuery));

if (isNode) {
    module.exports = MRS;
}
