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

    function widgetVisibility(widget, depends) {
        if (MRS.isApiEnabled(depends)) {
            widget.show();
        } else {
            widget.hide();
        }
    }

    $(window).on('load', function() {
        widgetVisibility($("#header_send_money"), { apis: [MRS.constants.REQUEST_TYPES.sendMoney] });
        widgetVisibility($("#header_send_message"), { apis: [MRS.constants.REQUEST_TYPES.sendMessage] });
        if (!MRS.isFundingMonitorSupported()) {
            $("#funding_monitor_menu_item").hide();
        }
        if (!MRS.isExternalLinkVisible()) {
            $("#api_console_li").hide();
            $("#database_shell_li").hide();
        }
        if (!MRS.isWebWalletLinkVisible()) {
            $("#web_wallet_li").remove();
        }
    });

    $("#refreshSearchIndex").on("click", function() {
        MRS.sendRequest("luceneReindex", {
            adminPassword: MRS.getAdminPassword()
        }, function (response) {
            if (response.errorCode) {
                $.growl(MRS.escapeRespStr(response.errorDescription));
            } else {
                $.growl($.t("search_index_refreshed"));
            }
        })
    });

    $("#header_open_web_wallet").on("click", function() {
        if (java) {
            java.openBrowser(MRS.accountRS);
        }
    });

    $("#client_status_modal").on("show.bs.modal", function() {
        if (MRS.isMobileApp()) {
            $("#client_status_description").text($.t("mobile_client_description", { url: MRS.getRemoteNodeUrl() }));
            $("#client_status_set_peer").hide();
            $("#client_status_remote_peer_container").hide();
            $("#client_status_blacklist_peer").hide();
            return;
        } else if (MRS.state.isLightClient) {
            $("#client_status_description").text($.t("light_client_description"));
        } else {
            $("#client_status_description").text($.t("api_proxy_description"));
        }
        if (MRS.state.apiProxyPeer) {
            $("#client_status_remote_peer").val(String(MRS.state.apiProxyPeer).escapeHTML());
            $("#client_status_set_peer").prop('disabled', true);
            $("#client_status_blacklist_peer").prop('disabled', false);
        } else {
            $("#client_status_remote_peer").val("");
            $("#client_status_set_peer").prop('disabled', false);
            $("#client_status_blacklist_peer").prop('disabled', true);
        }
        MRS.updateConfirmationsTable();
    });

    $("#client_status_remote_peer").keydown(function() {
        if ($(this).val() == MRS.state.apiProxyPeer) {
            $("#client_status_set_peer").prop('disabled', true);
            $("#client_status_blacklist_peer").prop('disabled', false);
        } else {
            $("#client_status_set_peer").prop('disabled', false);
            $("#client_status_blacklist_peer").prop('disabled', true);
        }
    });

    MRS.forms.setAPIProxyPeer = function ($modal) {
        var data = MRS.getFormData($modal.find("form:first"));
        data.adminPassword = MRS.getAdminPassword();
        return {
            "data": data
        };
    };

    MRS.forms.setAPIProxyPeerComplete = function(response) {
        var announcedAddress = response.announcedAddress;
        if (announcedAddress) {
            MRS.state.apiProxyPeer = announcedAddress;
            $.growl($.t("remote_peer_updated", { peer: String(announcedAddress).escapeHTML() }));
        } else {
            $.growl($.t("remote_peer_selected_by_server"));
        }
        MRS.updateDashboardMessage();
    };

    MRS.forms.blacklistAPIProxyPeer = function ($modal) {
        var data = MRS.getFormData($modal.find("form:first"));
        data.adminPassword = MRS.getAdminPassword();
        return {
            "data": data
        };
    };

    MRS.forms.blacklistAPIProxyPeerComplete = function(response) {
        if (response.done) {
            MRS.state.apiProxyPeer = null;
            $.growl($.t("remote_peer_blacklisted"));
        }
        MRS.updateDashboardMessage();
    };

    $(".external-link").on('click', function(e) {
        if (!MRS.isMobileApp()) {
            return;
        }
        e.preventDefault();
        window.open($(this).attr('href'), '_system');
        return false;
    });

    $("#passphrase_validation_modal").on("show.bs.modal", function() {
        $("#passphrae_validation_account").val(MRS.accountRS);
    });

    MRS.forms.validatePassphrase = function($modal) {
        var data = MRS.getFormData($modal.find("form:first"));
        var secretPhrase = data.secretPhrase;
        var account = data.account;
        var calculatedAccount = MRS.getAccountId(secretPhrase, true);
        if (account == calculatedAccount) {
            $(".btn-passphrase-validation").removeClass("btn-danger").addClass("btn-success");
            return {
                "successMessage": $.t("correct_passphrase"),
                "stop": true
            };
        } else {
            return {
                "error": $.t("wrong_passphrase")
            };
        }
    };

    MRS.getPassphraseValidationLink = function() {
        return "<br/><a href='#' class='btn btn-xs btn-danger btn-passphrase-validation' data-toggle='modal' data-target='#passphrase_validation_modal'>" + $.t("validate_passphrase") + "</a>";
    };

    return MRS;
}(MRS || {}, jQuery));