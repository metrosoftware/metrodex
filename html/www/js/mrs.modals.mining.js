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
 * @depends {mrs.modals.js}
 */
var MRS = (function (MRS, $) {
    MRS.forms.startMiningComplete = function (response, data) {
        var status = MRS.miningStatus;
        var tooltip = miningIndicator.attr('title');
        if ("getworkIsQueried" in response && response.getworkIsQueried && "secretPhrase" in response && response.secretPhrase) {
            status = MRS.constants.MINING;
            tooltip = MRS.getMiningTooltip(response);
            $.growl($.t("success_start_mining"), {
                type: "success"
            });
        } else if ("getworkIsQueried" in response && !response.getworkIsQueried && "secretPhrase" in response && response.secretPhrase) {
            status = MRS.constants.MINING_ALLOWED;
            tooltip = MRS.getMiningTooltip(response);
        } else if ("getworkIsQueried" in response && !response.getworkIsQueried || "secretPhrase" in response && !response.secretPhrase) {
            status = MRS.constants.NOT_MINING;
            tooltip = $.t("not_mining_not_started_tooltip");
            $.growl($.t("error_start_mining"), {
                type: 'danger'
            });
        } else {
            status = MRS.constants.UNKNOWN;
            tooltip = MRS.escapeRespStr(response.errorDescription);
            $.growl($.t("error_start_mining"), {
                type: 'danger'
            });
        }
        miningIndicator.find("span").html($.t(MRS.miningStatus)).attr("data-i18n", status);
        MRS.setMiningIndicatorStatus(status);
        MRS.updateMiningTooltip(tooltip);
    };


    var miningIndicator = $("#mining_indicator");
    miningIndicator.hover(
        function () {
            MRS.updateMiningStatus();
        }
    );

    MRS.getMiningTooltip = function (data) {
        return $.t("mining_tooltip", {
           "time": data.lastTimeGetWork
        });
    };

    MRS.updateMiningTooltip = function (tooltip) {
        $("#mining_indicator").attr('title', tooltip).tooltip('fixTitle');
    };

    MRS.setMiningStatusFromResponse = function (response) {
        if ("getworkIsQueried" in response && response.getworkIsQueried && "publicKeyAssigned" in response && response.publicKeyAssigned) {
            MRS.miningStatus = MRS.constants.MINING;
            tooltip = MRS.getMiningTooltip(response);
        } else if ("getworkIsQueried" in response && !response.getworkIsQueried && "publicKeyAssigned" in response && response.publicKeyAssigned) {
            MRS.miningStatus = MRS.constants.MINING_ALLOWED;
            tooltip = MRS.getMiningTooltip(response);
        } else if ("getworkIsQueried" in response && !response.getworkIsQueried || "publicKeyAssigned" in response && !response.publicKeyAssigned) {
            MRS.miningStatus = MRS.constants.NOT_MINING;
            tooltip = $.t("not_mining_not_started_tooltip");
        } else {
            MRS.miningStatus = MRS.constants.UNKNOWN;
            tooltip = MRS.escapeRespStr(response.errorDescription);
        }
    }

    MRS.setMiningIndicatorStatus = function (status) {
        var miningIndicator = $("#mining_indicator");
        miningIndicator.removeClass(MRS.constants.MINING);
        miningIndicator.removeClass(MRS.constants.NOT_MINING);
        miningIndicator.removeClass(MRS.constants.UNKNOWN);
        miningIndicator.removeClass(MRS.constants.MINING_ALLOWED);
        miningIndicator.addClass(status);
    }

    MRS.updateMiningStatus = function () {

        var miningIndicator = $("#mining_indicator");
        var status = MRS.miningStatus;
        var tooltip = miningIndicator.attr('title');

        var params = {};
        MRS.sendRequest("getMining", params, function (response) {
            MRS.setMiningStatusFromResponse(response);
        }, {isAsync: false});
        // }
        MRS.setMiningIndicatorStatus(status);
        miningIndicator.find("span").html($.t(status)).attr("data-i18n", status);
        miningIndicator.show();
        MRS.miningStatus = status;
        MRS.updateMiningTooltip(tooltip);
    };

    return MRS;
}(MRS || {}, jQuery));