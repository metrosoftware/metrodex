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
var MRS = (function(MRS, $) {
    var currentMonitor;

    function isErrorResponse(response) {
        return response.errorCode || response.errorDescription || response.errorMessage || response.error;
    }

    function getErrorMessage(response) {
        return response.errorDescription || response.errorMessage || response.error;
    } 

    MRS.jsondata = MRS.jsondata||{};

    MRS.jsondata.monitors = function (response) {
        return {
            accountFormatted: MRS.getAccountLink(response, "account"),
            property: MRS.escapeRespStr(response.property),
            amountFormatted: MRS.formatAmount(response.amount),
            thresholdFormatted: MRS.formatAmount(response.threshold),
            interval: MRS.escapeRespStr(response.interval),
            statusLinkFormatted: "<a href='#' class='btn btn-xs' " +
                        "onclick='MRS.goToMonitor(" + JSON.stringify(response) + ");'>" +
                         $.t("status") + "</a>",
            stopLinkFormatted: "<a href='#' class='btn btn-xs' data-toggle='modal' data-target='#stop_funding_monitor_modal' " +
                        "data-account='" + MRS.escapeRespStr(response.accountRS) + "' " +
                        "data-property='" + MRS.escapeRespStr(response.property) + "'>" + $.t("stop") + "</a>"
        };
    };

    MRS.jsondata.monitoredAccount = function (response) {
        try {
            var value = JSON.parse(response.value);
        } catch (e) {
            MRS.logConsole(e.message);
        }
        return {
            accountFormatted: MRS.getAccountLink(response, "recipient"),
            property: MRS.escapeRespStr(response.property),
            amountFormatted: (value && value.amount) ? "<b>" + MRS.formatAmount(value.amount) : MRS.formatAmount(currentMonitor.amount),
            thresholdFormatted: (value && value.threshold) ? "<b>" + MRS.formatAmount(value.threshold) : MRS.formatAmount(currentMonitor.threshold),
            intervalFormatted: (value && value.interval) ? "<b>" + MRS.escapeRespStr(value.interval) : MRS.escapeRespStr(currentMonitor.interval),
            removeLinkFormatted: "<a href='#' class='btn btn-xs' data-toggle='modal' data-target='#remove_monitored_account_modal' " +
                        "data-recipient='" + MRS.escapeRespStr(response.recipientRS) + "' " +
                        "data-property='" + MRS.escapeRespStr(response.property) + "' " +
                        "data-value='" + MRS.normalizePropertyValue(response.value) + "'>" + $.t("remove") + "</a>"
        };
    };

    MRS.incoming.funding_monitors = function() {
        MRS.loadPage("funding_monitors");
    };

    MRS.pages.funding_monitors = function () {
        MRS.hasMorePages = false;
        var view = MRS.simpleview.get('funding_monitors_page', {
            errorMessage: null,
            isLoading: true,
            isEmpty: false,
            monitors: []
        });
        var params = {
            "account": MRS.accountRS,
            "adminPassword": MRS.getAdminPassword(),
            "firstIndex": MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage,
            "lastIndex": MRS.pageNumber * MRS.itemsPerPage
        };
        MRS.sendRequest("getFundingMonitor", params,
            function (response) {
                if (isErrorResponse(response)) {
                    view.render({
                        errorMessage: getErrorMessage(response),
                        isLoading: false,
                        isEmpty: false
                    });
                    return;
                }
                if (response.monitors.length > MRS.itemsPerPage) {
                    MRS.hasMorePages = true;
                    response.monitors.pop();
                }
                view.monitors.length = 0;
                response.monitors.forEach(
                    function (monitorJson) {
                        view.monitors.push(MRS.jsondata.monitors(monitorJson))
                    }
                );
                view.render({
                    isLoading: false,
                    isEmpty: view.monitors.length == 0
                });
                MRS.pageLoaded();
            }
        )
    };

    MRS.forms.startFundingMonitorComplete = function() {
        $.growl($.t("monitor_started"));
        MRS.loadPage("funding_monitors");
    };

    $("#stop_funding_monitor_modal").on("show.bs.modal", function(e) {
        var $invoker = $(e.relatedTarget);
        var account = $invoker.data("account");
        if (account) {
            $("#stop_monitor_account").val(account);
        }
        var property = $invoker.data("property");
        if (property) {
            $("#stop_monitor_property").val(property);
        }
        if (MRS.getAdminPassword()) {
            $("#stop_monitor_admin_password").val(MRS.getAdminPassword());
        }
    });

    MRS.forms.stopFundingMonitorComplete = function() {
        $.growl($.t("monitor_stopped"));
        MRS.loadPage("funding_monitors");
    };

    MRS.goToMonitor = function(monitor) {
   		MRS.goToPage("funding_monitor_status", function() {
            return monitor;
        });
   	};

    MRS.incoming.funding_monitors_status = function() {
        MRS.loadPage("funding_monitor_status");
    };

    MRS.pages.funding_monitor_status = function (callback) {
        currentMonitor = callback();
        $("#monitor_funding_account").html(MRS.escapeRespStr(currentMonitor.account));
        $("#monitor_control_property").html(MRS.escapeRespStr(currentMonitor.property));
        MRS.hasMorePages = false;
        var view = MRS.simpleview.get('funding_monitor_status_page', {
            errorMessage: null,
            isLoading: true,
            isEmpty: false,
            monitoredAccount: []
        });
        var params = {
            "setter": currentMonitor.account,
            "property": currentMonitor.property,
            "firstIndex": MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage,
            "lastIndex": MRS.pageNumber * MRS.itemsPerPage
        };
        MRS.sendRequest("getAccountProperties", params,
            function (response) {
                if (isErrorResponse(response)) {
                    view.render({
                        errorMessage: getErrorMessage(response),
                        isLoading: false,
                        isEmpty: false
                    });
                    return;
                }
                if (response.properties.length > MRS.itemsPerPage) {
                    MRS.hasMorePages = true;
                    response.properties.pop();
                }
                view.monitoredAccount.length = 0;
                response.properties.forEach(
                    function (propertiesJson) {
                        view.monitoredAccount.push(MRS.jsondata.monitoredAccount(propertiesJson))
                    }
                );
                view.render({
                    isLoading: false,
                    isEmpty: view.monitoredAccount.length == 0,
                    fundingAccountFormatted: MRS.getAccountLink(currentMonitor, "account"),
                    controlProperty: currentMonitor.property
                });
                MRS.pageLoaded();
            }
        )
    };

    $("#add_monitored_account_modal").on("show.bs.modal", function() {
        $("#add_monitored_account_property").val(currentMonitor.property);
        $("#add_monitored_account_amount").val(MRS.convertToMTR(currentMonitor.amount));
        $("#add_monitored_account_threshold").val(MRS.convertToMTR(currentMonitor.threshold));
        $("#add_monitored_account_interval").val(currentMonitor.interval);
        $("#add_monitored_account_value").val("");
    });

    $(".add_monitored_account_value").on('change', function() {
        if (!currentMonitor) {
            return;
        }
        var value = {};
        var amount = MRS.convertToMQT($("#add_monitored_account_amount").val());
        if (currentMonitor.amount != amount) {
            value.amount = amount;
        }
        var threshold = MRS.convertToMQT($("#add_monitored_account_threshold").val());
        if (currentMonitor.threshold != threshold) {
            value.threshold = threshold;
        }
        var interval = $("#add_monitored_account_interval").val();
        if (currentMonitor.interval != interval) {
            value.interval = interval;
        }
        if (jQuery.isEmptyObject(value)) {
            value = "";
        } else {
            value = JSON.stringify(value);
        }
        $("#add_monitored_account_value").val(value);
    });

    $("#remove_monitored_account_modal").on("show.bs.modal", function(e) {
        var $invoker = $(e.relatedTarget);
        $("#remove_monitored_account_recipient").val($invoker.data("recipient"));
        $("#remove_monitored_account_property").val($invoker.data("property"));
        $("#remove_monitored_account_value").val(MRS.normalizePropertyValue($invoker.data("value")));
    });

    return MRS;

}(MRS || {}, jQuery));