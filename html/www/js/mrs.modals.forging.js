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
 * @depends {mrs.modals.js}
 */
var MRS = (function(MRS, $) {
	MRS.forms.startForgingComplete = function(response, data) {
		if ("deadline" in response) {
            setForgingIndicatorStatus(MRS.constants.FORGING);
			forgingIndicator.find("span").html($.t(MRS.constants.FORGING)).attr("data-i18n", "forging");
			MRS.forgingStatus = MRS.constants.FORGING;
            MRS.isAccountForging = true;
			$.growl($.t("success_start_forging"), {
				type: "success"
			});
		} else {
            MRS.isAccountForging = false;
			$.growl($.t("error_start_forging"), {
				type: 'danger'
			});
		}
	};

	MRS.forms.stopForgingComplete = function(response, data) {
		if ($("#stop_forging_modal").find(".show_logout").css("display") == "inline") {
			MRS.logout();
			return;
		}
        if (response.foundAndStopped || (response.stopped && response.stopped > 0)) {
            MRS.isAccountForging = false;
            if (!response.forgersCount || response.forgersCount == 0) {
                setForgingIndicatorStatus(MRS.constants.NOT_FORGING);
                forgingIndicator.find("span").html($.t(MRS.constants.NOT_FORGING)).attr("data-i18n", "forging");
            }
            $.growl($.t("success_stop_forging"), {
				type: 'success'
			});
		} else {
			$.growl($.t("error_stop_forging"), {
				type: 'danger'
			});
		}
	};

	var forgingIndicator = $("#forging_indicator");
	forgingIndicator.click(function(e) {
		e.preventDefault();

        if (MRS.state.isLightClient) {
            $.growl($.t("error_forging_light_client"), {
                "type": "danger"
            });
        } else if (MRS.downloadingBlockchain) {
			$.growl($.t("error_forging_blockchain_downloading"), {
				"type": "danger"
			});
		} else if (MRS.state.isScanning) {
			$.growl($.t("error_forging_blockchain_rescanning"), {
				"type": "danger"
			});
		} else if (!MRS.accountInfo.publicKey) {
			$.growl($.t("error_forging_no_public_key"), {
				"type": "danger"
			});
		} else if (MRS.accountInfo.effectiveBalanceMTR == 0) {
			if (MRS.lastBlockHeight >= MRS.accountInfo.currentLeasingHeightFrom && MRS.lastBlockHeight <= MRS.accountInfo.currentLeasingHeightTo) {
				$.growl($.t("error_forging_lease"), {
					"type": "danger"
				});
			} else {
				$.growl($.t("error_forging_effective_balance"), {
					"type": "danger"
				});
			}
		} else if (MRS.isAccountForging) {
			$("#stop_forging_modal").modal("show");
		} else {
			$("#start_forging_modal").modal("show");
		}
	});

	forgingIndicator.hover(
		function() {
            MRS.updateForgingStatus();
        }
	);

    MRS.getForgingTooltip = function(data) {
        if (!data || data.account == MRS.accountInfo.account) {
            MRS.isAccountForging = true;
            return $.t("forging_tooltip", {"balance": MRS.accountInfo.effectiveBalanceMTR, "symbol": MRS.constants.COIN_SYMBOL});
        }
        return $.t("forging_another_account_tooltip", {"accountRS": data.accountRS });
    };

    MRS.updateForgingTooltip = function(tooltip) {
        $("#forging_indicator").attr('title', tooltip).tooltip('fixTitle');
    };

    function setForgingIndicatorStatus(status) {
        var forgingIndicator = $("#forging_indicator");
        forgingIndicator.removeClass(MRS.constants.FORGING);
        forgingIndicator.removeClass(MRS.constants.NOT_FORGING);
        forgingIndicator.removeClass(MRS.constants.UNKNOWN);
        forgingIndicator.addClass(status);
    }

    MRS.updateForgingStatus = function(secretPhrase) {
        var forgingIndicator = $("#forging_indicator");
        if (!MRS.isForgingSupported()) {
            forgingIndicator.hide();
            return;
        }
        var status = MRS.forgingStatus;
        var tooltip = forgingIndicator.attr('title');
        if (MRS.state.isLightClient) {
            status = MRS.constants.NOT_FORGING;
            tooltip = $.t("error_forging_light_client");
        } else if (!MRS.accountInfo.publicKey) {
            status = MRS.constants.NOT_FORGING;
            tooltip = $.t("error_forging_no_public_key");
        } else if (MRS.isLeased) {
            status = MRS.constants.NOT_FORGING;
            tooltip = $.t("error_forging_lease");
        } else if (MRS.accountInfo.effectiveBalanceMTR == 0) {
            status = MRS.constants.NOT_FORGING;
            tooltip = $.t("error_forging_effective_balance");
        } else if (MRS.downloadingBlockchain) {
            status = MRS.constants.NOT_FORGING;
            tooltip = $.t("error_forging_blockchain_downloading");
        } else if (MRS.state.isScanning) {
            status = MRS.constants.NOT_FORGING;
            tooltip = $.t("error_forging_blockchain_rescanning");
        } else if (MRS.needsAdminPassword && MRS.getAdminPassword() == "" && (!secretPhrase || !MRS.isForgingSafe())) {
            // do not change forging status
        } else {
            var params = {};
            if (MRS.needsAdminPassword && MRS.getAdminPassword() != "") {
                params["adminPassword"] = MRS.getAdminPassword();
            }
            if (secretPhrase && MRS.needsAdminPassword && MRS.getAdminPassword() == "") {
                params["secretPhrase"] = secretPhrase;
            }
            MRS.sendRequest("getForging", params, function (response) {
                MRS.isAccountForging = false;
                if ("account" in response) {
                    status = MRS.constants.FORGING;
                    tooltip = MRS.getForgingTooltip(response);
                    MRS.isAccountForging = true;
                } else if ("generators" in response) {
                    if (response.generators.length == 0) {
                        status = MRS.constants.NOT_FORGING;
                        tooltip = $.t("not_forging_not_started_tooltip");
                    } else {
                        status = MRS.constants.FORGING;
                        if (response.generators.length == 1) {
                            tooltip = MRS.getForgingTooltip(response.generators[0]);
                        } else {
                            tooltip = $.t("forging_more_than_one_tooltip", { "generators": response.generators.length });
                            for (var i=0; i< response.generators.length; i++) {
                                if (response.generators[i].account == MRS.accountInfo.account) {
                                    MRS.isAccountForging = true;
                                }
                            }
                            if (MRS.isAccountForging) {
                                tooltip += ", " + $.t("forging_current_account_true");
                            } else {
                                tooltip += ", " + $.t("forging_current_account_false");
                            }
                        }
                    }
                } else {
                    status = MRS.constants.UNKNOWN;
                    tooltip = MRS.escapeRespStr(response.errorDescription);
                }
                MRS.setMiningStatusFromResponse(response);
                MRS.setMiningIndicatorStatus(MRS.miningStatus);
                if (MRS.miningStatus == MRS.constants.NOT_MINING) {
                    MRS.isSecretEntered = false;
                }
                var miningIndicator = $("#mining_indicator");
                miningIndicator.find("span").html($.t(MRS.miningStatus)).attr("data-i18n", MRS.miningStatus);
                miningIndicator.show();
                var miningTooltip = MRS.getMiningTooltip(response);
                MRS.updateMiningTooltip(miningTooltip);
            }, { isAsync: false });
        }
        setForgingIndicatorStatus(status);
        if (status == MRS.constants.NOT_FORGING) {
            MRS.isAccountForging = false;
        }
        forgingIndicator.find("span").html($.t(status)).attr("data-i18n", status);
        forgingIndicator.show();
        MRS.forgingStatus = status;
        MRS.updateForgingTooltip(tooltip);
    };

	return MRS;
}(MRS || {}, jQuery));