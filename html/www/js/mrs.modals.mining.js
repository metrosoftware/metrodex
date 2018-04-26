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
        MRS.isSecretEntered = "secretPhrase" in response && response.secretPhrase;
        miningIndicator.find("span").html($.t(MRS.miningStatus)).attr("data-i18n", status);
        MRS.setMiningIndicatorStatus(status);
        MRS.updateMiningTooltip(tooltip);
    };

    MRS.forms.stopMiningComplete = function (response, data) {
        var status = MRS.miningStatus;
        var tooltip = miningIndicator.attr('title');
        if ("stopped" in response && response.stopped) {
            status = MRS.constants.NOT_MINING;
            tooltip = $.t("not_mining_not_started_tooltip");
            miningIndicator.find("span").html($.t(MRS.constants.NOT_MINING)).attr("data-i18n", "mining");
            $.growl($.t("success_stop_mining"), {
                type: 'success'
            });
        } else {
            status = MRS.constants.UNKNOWN;
            tooltip = MRS.escapeRespStr(response.errorDescription);
            $.growl($.t("error_stop_mining"), {
                type: 'danger'
            });
        }
        MRS.isSecretEntered = "secretPhrase" in response && response.secretPhrase;
        MRS.setMiningIndicatorStatus(status);
        MRS.updateMiningTooltip(tooltip);
    };

    var miningIndicator = $("#mining_indicator");
    miningIndicator.click(function (e) {
        e.preventDefault();

        if (MRS.isSecretEntered) {
            $("#stop_mining_modal").modal("show");
        } else {
            $("#start_mining_modal").modal("show");
        }
    });

    miningIndicator.hover(
        function () {
            MRS.updateMiningStatus();
        }
    );

    MRS.getMiningTooltip = function (data) {
        if (!data.secretPhrase) {
            return $.t("mining_no_secretphrase_entered_tooltip");
        } else {
            return $.t("mining_tooltip", {
                "time": data.lastTimeGetWork
            });
        }
    };

    MRS.updateMiningTooltip = function (tooltip) {
        $("#mining_indicator").attr('title', tooltip).tooltip('fixTitle');
    };

    MRS.setMiningStatusFromResponse = function (response) {
        MRS.isSecretEntered = false;
        if ("getworkIsQueried" in response && response.getworkIsQueried && "secretPhrase" in response && response.secretPhrase) {
            MRS.miningStatus = MRS.constants.MINING;
            tooltip = MRS.getMiningTooltip(response);
        } else if ("getworkIsQueried" in response && !response.getworkIsQueried && "secretPhrase" in response && response.secretPhrase) {
            MRS.miningStatus = MRS.constants.MINING_ALLOWED;
            tooltip = MRS.getMiningTooltip(response);
        } else if ("getworkIsQueried" in response && !response.getworkIsQueried || "secretPhrase" in response && !response.secretPhrase) {
            MRS.miningStatus = MRS.constants.NOT_MINING;
            tooltip = $.t("not_mining_not_started_tooltip");
        } else {
            MRS.miningStatus = MRS.constants.UNKNOWN;
            tooltip = MRS.escapeRespStr(response.errorDescription);
        }
        MRS.isSecretEntered = "secretPhrase" in response && response.secretPhrase;
    }

    MRS.setMiningIndicatorStatus = function (status) {
        var miningIndicator = $("#mining_indicator");
        miningIndicator.removeClass(MRS.constants.MINING);
        miningIndicator.removeClass(MRS.constants.NOT_MINING);
        miningIndicator.removeClass(MRS.constants.UNKNOWN);
        miningIndicator.removeClass(MRS.constants.MINING_ALLOWED);
        miningIndicator.addClass(status);
    }

    MRS.updateMiningStatus = function (secretPhrase) {
        var miningIndicator = $("#mining_indicator");
        if (!MRS.isMiningSupported()) {
            miningIndicator.hide();
            return;
        }
        var status = MRS.miningStatus;
        var tooltip = miningIndicator.attr('title');
        // if (!secretPhrase) {
        //
        // }
        // if (MRS.state.isLightClient) {
        //     status = MRS.constants.NOT_FORGING;
        //     tooltip = $.t("error_forging_light_client");
        // } else if (!MRS.accountInfo.publicKey) {
        //     status = MRS.constants.NOT_FORGING;
        //     tooltip = $.t("error_forging_no_public_key");
        // } else if (MRS.isLeased) {
        //     status = MRS.constants.NOT_FORGING;
        //     tooltip = $.t("error_forging_lease");
        // } else if (MRS.accountInfo.effectiveBalanceMTR == 0) {
        //     status = MRS.constants.NOT_FORGING;
        //     tooltip = $.t("error_forging_effective_balance");
        // } else if (MRS.downloadingBlockchain) {
        //     status = MRS.constants.NOT_FORGING;
        //     tooltip = $.t("error_forging_blockchain_downloading");
        // } else if (MRS.state.isScanning) {
        //     status = MRS.constants.NOT_FORGING;
        //     tooltip = $.t("error_forging_blockchain_rescanning");
        // } else if (MRS.needsAdminPassword && MRS.getAdminPassword() == "" && (!secretPhrase || !MRS.isForgingSafe())) {
        //     // do not change forging status
        // } else {
        var params = {};
        if (MRS.needsAdminPassword && MRS.getAdminPassword() != "") {
            params["adminPassword"] = MRS.getAdminPassword();
        }
        if (secretPhrase && MRS.needsAdminPassword && MRS.getAdminPassword() == "") {
            params["secretPhrase"] = secretPhrase;
        }
        MRS.sendRequest("getMining", params, function (response) {
            MRS.setMiningStatusFromResponse(response);
        }, {isAsync: false});
        // }
        MRS.setMiningIndicatorStatus(status);
        if (status == MRS.constants.NOT_MINING) {
            MRS.isSecretEntered = false;
        }
        miningIndicator.find("span").html($.t(status)).attr("data-i18n", status);
        miningIndicator.show();
        MRS.miningStatus = status;
        MRS.updateMiningTooltip(tooltip);
    };

    return MRS;
}(MRS || {}, jQuery));