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
    function isErrorResponse(response) {
        return response.errorCode || response.errorDescription || response.errorMessage || response.error;
    }

    MRS.jsondata = MRS.jsondata||{};

    MRS.jsondata.shuffling = function (response, shufflers, amountDecimals) {
        var isShufflerActive = false;
        var recipient;
        var state;
        var error;
        if (shufflers && shufflers.shufflers) {
            for (var i = 0; i < shufflers.shufflers.length; i++) {
                var shuffler = shufflers.shufflers[i];
                if (response.shufflingFullHash == shuffler.shufflingFullHash) {
                    isShufflerActive = true;
                    recipient = shuffler.recipientRS;
                    if (shuffler.participantState != undefined) {
                        state = $.t(MRS.getShufflingParticipantState(shuffler.participantState).toLowerCase());
                    }
                    if (shuffler.failureCause) {
                        error = MRS.escapeRespStr(response.failureCause)
                    }
                    break;
                }
            }
        }
        var shufflerStatus = $.t("unknown");
        var shufflerColor = "gray";

        if (shufflers && shufflers.shufflers) {
            if (isShufflerActive) {
                if (error) {
                    shufflerStatus = error;
                    shufflerColor = "red";
                } else {
                    shufflerStatus = $.t("active");
                    shufflerColor = "green";
                }
            } else {
                shufflerStatus = $.t("inactive");
                shufflerColor = "red";
            }
        }

        var shufflerIndicatorFormatted = "";
        var startShufflerLinkFormatted = "";
        var shufflerStage = "";
        if (response.stage == 4) {
            if (response.participantCount != response.registrantCount) {
                shufflerStage = $.t("expired");
            } else {
                shufflerStage = $.t("failed");
            }
        } else {
            shufflerStage = $.t(MRS.getShufflingStage(response.stage).toLowerCase())
        }
        if (response.stage < 4) {
            shufflerIndicatorFormatted = "<i class='fa fa-circle' style='color:" + shufflerColor + ";'></i>";
            if (!isShufflerActive) {
                startShufflerLinkFormatted = "<a href='#' class='btn btn-xs' data-toggle='modal' data-target='#m_shuffler_start_modal' " +
                    "data-shuffling='" + response.shuffling + "' " +
                    "data-shufflingfullhash='" + response.shufflingFullHash + "'>" + $.t("start") + "</a>";
            }
        } else {
            shufflerStatus = "";
        }
        return {
            status:
                (function () {
                    if (response.stage > 0) {
                        return "<span>" + $.t("in_progress") + "</span>";
                    }
                    if (!isShufflerActive) {
                        return "<a href='#' class='btn btn-xs btn-default' data-toggle='modal' " +
                            "data-target='#m_shuffler_start_modal' " +
                            "data-shuffling='" + response.shuffling + "' " +
                            "data-shufflingfullhash='" + response.shufflingFullHash + "'>" + $.t("join") + "</a>";
                    }
                    return "<span>" + $.t("already_joined") + "</span>";
                })(),
            shufflingFormatted: MRS.getTransactionLink(response.shuffling),
            stageLabel: shufflerStage,
            shufflerStatus: shufflerStatus,
            shufflerIndicatorFormatted: shufflerIndicatorFormatted,
            startShufflerLinkFormatted: startShufflerLinkFormatted,
            recipientFormatted: recipient,
            stateLabel: state,
            assigneeFormatted: MRS.getAccountLink(response, "assignee"),
            issuerFormatted: MRS.getAccountLink(response, "issuer"),
            amountFormatted: (function () {
                switch (response.holdingType) {
                    case 0: return MRS.formatAmount(response.amount, false, false, amountDecimals);
                    case 1:
                    case 2: return MRS.formatQuantity(response.amount, response.holdingInfo.decimals, false, amountDecimals);
                }
            })(),
            holdingFormatted: (function () {
                switch (response.holdingType) {
                    case 0: return 'MTR';
                    case 1: return MRS.getTransactionLink(response.holding) + " (" + $.t('asset') + ")";
                }
            })(),
            participants: MRS.escapeRespStr(response.registrantCount) + " / " + MRS.escapeRespStr(response.participantCount),
            blocks: response.blocksRemaining,
            shuffling: response.shuffling,
            shufflingFullHash: response.shufflingFullHash
        };
    };

    MRS.pages.shuffling = function () {};

    MRS.setup.shuffling = function() {
        if (!MRS.isShufflingSupported()) {
            return;
        }
        var sidebarId = 'sidebar_shuffling';
        MRS.addSimpleSidebarMenuItem({
            "id": sidebarId,
            "titleHTML": '<i class="fa fa-random"></i> <span data-i18n="shuffling">Shuffling</span>',
            "page": 'active_shufflings',
            "desiredPosition": 80,
            "depends": { tags: [ MRS.constants.API_TAGS.SHUFFLING ] }
        });

        $('#m_shuffling_create_holding_type').change();
    };

    /**
     * Create shuffling modal holding type onchange listener.
     * Hides holding field unless type is asset
     */
    $('#m_shuffling_create_holding_type').change(function () {
        var holdingType = $("#m_shuffling_create_holding_type");
        if (holdingType.val() == "0") {
            $("#shuffling_asset_id_group").css("display", "none");
            $('#m_shuffling_create_unit').html($.t('amount'));
            $('#m_shuffling_create_amount').attr('name', 'shufflingAmountMTR');
        } else if(holdingType.val() == "1") {
			$("#shuffling_asset_id_group").css("display", "inline");
            $('#m_shuffling_create_unit').html($.t('quantity'));
            $('#m_shuffling_create_amount').attr('name', 'amountQNTf');
		}
    });

    MRS.forms.shufflingCreate = function($modal) {
        var data = MRS.getFormData($modal.find("form:first"));
        switch (data.holdingType) {
            case '0':
                delete data.holding;
                break;
            case '1':
                break;
            case '2':
                break;
        }
        if (data.finishHeight) {
            data.registrationPeriod = parseInt(data.finishHeight) - MRS.lastBlockHeight;
            delete data.finishHeight;
        }
        return {
            "data": data
        }
    };

    MRS.incoming.active_shufflings = function() {
        MRS.loadPage("active_shufflings");
    };

    MRS.incoming.my_shufflings = function() {
        MRS.loadPage("my_shufflings");
    };

    function getShufflers(callback) {
        MRS.sendRequest("getShufflers", {"account": MRS.account, "adminPassword": MRS.getAdminPassword(), "includeParticipantState": true},
            function (shufflers) {
                if (isErrorResponse(shufflers)) {
                    $.growl($.t("cannot_check_shufflers_status") + " " + shufflers.errorDescription.escapeHTML());
                    callback(null, undefined);
                } else {
                    callback(null, shufflers);
                }
            }
        )
    }

    MRS.pages.finished_shufflings = function() {
        MRS.finished_shufflings("finished_shufflings_full", true);
    };

    MRS.pages.active_shufflings = function () {
        MRS.finished_shufflings("finished_shufflings",false);
        async.waterfall([
            function(callback) {
                getShufflers(callback);
            },
            function(shufflers, callback) {
                MRS.hasMorePages = false;
                var view = MRS.simpleview.get('active_shufflings', {
                    errorMessage: null,
                    isLoading: true,
                    isEmpty: false,
                    shufflings: []
                });
                var params = {
                    "firstIndex": MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage,
                    "lastIndex": MRS.pageNumber * MRS.itemsPerPage,
                    "includeHoldingInfo": "true"
                };
                MRS.sendRequest("getAllShufflings", params,
                    function (response) {
                        if (isErrorResponse(response)) {
                            view.render({
                                errorMessage: MRS.getErrorMessage(response),
                                isLoading: false,
                                isEmpty: false
                            });
                            return;
                        }
                        if (response.shufflings.length > MRS.itemsPerPage) {
                            MRS.hasMorePages = true;
                            response.shufflings.pop();
                        }
                        view.shufflings.length = 0;
                        var amountDecimals = MRS.getNumberOfDecimals(response.shufflings, "amount", function(shuffling) {
                            switch (shuffling.holdingType) {
                                case 0: return MRS.formatAmount(shuffling.amount);
                                case 1:
                                case 2: return MRS.formatQuantity(shuffling.amount, shuffling.holdingInfo.decimals);
                                default: return "";
                            }
                        });
                        response.shufflings.forEach(
                            function (shufflingJson) {
                                view.shufflings.push(MRS.jsondata.shuffling(shufflingJson, shufflers, amountDecimals))
                            }
                        );
                        view.render({
                            isLoading: false,
                            isEmpty: view.shufflings.length == 0
                        });
                        MRS.pageLoaded();
                        callback(null);
                    }
                );
            }
        ], function (err, result) {});
    };

    MRS.pages.my_shufflings = function () {
        async.waterfall([
            function(callback) {
                getShufflers(callback);
            },
            function(shufflers, callback) {
                MRS.hasMorePages = false;
                var view = MRS.simpleview.get('my_shufflings', {
                    errorMessage: null,
                    isLoading: true,
                    isEmpty: false,
                    shufflings: []
                });
                var params = {
                    "firstIndex": MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage,
                    "lastIndex": MRS.pageNumber * MRS.itemsPerPage,
                    "account": MRS.account,
                    "includeFinished": "true",
                    "includeHoldingInfo": "true"
                };
                MRS.sendRequest("getAccountShufflings", params,
                    function(response) {
                        if (isErrorResponse(response)) {
                            view.render({
                                errorMessage: MRS.getErrorMessage(response),
                                isLoading: false,
                                isEmpty: false
                            });
                            return;
                        }
                        if (response.shufflings.length > MRS.itemsPerPage) {
                            MRS.hasMorePages = true;
                            response.shufflings.pop();
                        }
                        view.shufflings.length = 0;
                        var amountDecimals = MRS.getNumberOfDecimals(response.shufflings, "amount", function(shuffling) {
                            switch (shuffling.holdingType) {
                                case 0: return MRS.formatAmount(shuffling.amount);
                                case 1:
                                case 2: return MRS.formatQuantity(shuffling.amount, shuffling.holdingInfo.decimals);
                                default: return "";
                            }
                        });
                        response.shufflings.forEach(
                            function (shufflingJson) {
                                view.shufflings.push( MRS.jsondata.shuffling(shufflingJson, shufflers, amountDecimals) );
                            }
                        );
                        view.render({
                            isLoading: false,
                            isEmpty: view.shufflings.length == 0
                        });
                        MRS.pageLoaded();
                        callback(null);
                    }
                );
            }
        ], function (err, result) {});
    };

    $("#m_shuffling_create_modal").on("show.bs.modal", function() {

   		context = {
   			labelText: "Asset",
   			labelI18n: "asset",
   			inputIdName: "holding",
   			inputDecimalsName: "shuffling_asset_decimals",
   			helpI18n: "add_asset_modal_help"
   		};
   		MRS.initModalUIElement($(this), '.shuffling_holding_asset', 'add_asset_modal_ui_element', context);

   		context = {
   			labelText: "Registration Finish",
   			labelI18n: "registration_finish",
   			helpI18n: "shuffling_registration_height_help",
   			inputName: "finishHeight",
   			initBlockHeight: MRS.lastBlockHeight + 1440,
   			changeHeightBlocks: 500
   		};
   		MRS.initModalUIElement($(this), '.shuffling_finish_height', 'block_height_modal_ui_element', context);
        // Activating context help popovers - from some reason this code is activated
        // after the same event in mrs.modals.js which doesn't happen for create pool thus it's necessary
        // to explicitly enable the popover here. strange ...
		$(function () {
            $("[data-toggle='popover']").popover({
            	"html": true
            });
        });

   	});

    var shufflerStartModal = $("#m_shuffler_start_modal");
    shufflerStartModal.on("show.bs.modal", function(e) {
        var $invoker = $(e.relatedTarget);
        var shufflingId = $invoker.data("shuffling");
        if (shufflingId) {
            $("#shuffler_start_shuffling_id").html(shufflingId);
        }
        var shufflingFullHash = $invoker.data("shufflingfullhash");
        if (shufflingFullHash) {
            $("#shuffler_start_shuffling_full_hash").val(shufflingFullHash);
        }
    });

    $('#m_shuffler_start_recipient_secretphrase').on("change", function () {
        var secretPhraseValue = $('#m_shuffler_start_recipient_secretphrase').val();
        var recipientAccount = $('#m_shuffler_start_recipient_account');
        if (secretPhraseValue == "") {
            recipientAccount.val("");
            return;
        }
        recipientAccount.val(MRS.getAccountId(secretPhraseValue, true));
    });

    MRS.forms.startShuffler = function ($modal) {
        var data = MRS.getFormData($modal.find("form:first"));
        if (data.recipientSecretPhrase) {
            data.recipientPublicKey = MRS.getPublicKey(converters.stringToHexString(data.recipientSecretPhrase));
            delete data.recipientSecretPhrase;
        }
        return {
            "data": data
        };
    };

    MRS.forms.shufflingCreateComplete = function(response) {
        $.growl($.t("shuffling_created"));
        // After shuffling created we show the start shuffler modal
        $("#shuffler_start_shuffling_id").html(response.transaction);
        $("#shuffler_start_shuffling_full_hash").val(response.fullHash);
        $('#m_shuffler_start_modal').modal("show");
    };

    MRS.forms.startShufflerComplete = function() {
        $.growl($.t("shuffler_started"));
        MRS.loadPage(MRS.currentPage);
    };

    MRS.finished_shufflings = function (table,full) {
        var finishedShufflingsTable = $("#" + table + "_table");
        finishedShufflingsTable.find("tbody").empty();
        finishedShufflingsTable.parent().addClass("data-loading").removeClass("data-empty");
        async.waterfall([
            function(callback) {
                getShufflers(callback);
            },
            function(shufflers, callback) {
                MRS.hasMorePages = false;
                var view = MRS.simpleview.get(table, {
                    errorMessage: null,
                    isLoading: true,
                    isEmpty: false,
                    data: []
                });
                var params = {
                    "account": MRS.account,
                    "finishedOnly": "true",
                    "includeHoldingInfo": "true"
                };
                if (full) {
                    params["firstIndex"] = MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage;
                    params["lastIndex"] = MRS.pageNumber * MRS.itemsPerPage;
                } else {
                    params["firstIndex"] = 0;
                    params["lastIndex"] = 9;
                }
                MRS.sendRequest("getAllShufflings", params,
                    function (response) {
                        if (isErrorResponse(response)) {
                            view.render({
                                errorMessage: MRS.getErrorMessage(response),
                                isLoading: false,
                                isEmpty: false
                            });
                            return;
                        }
                        if (response.shufflings.length > MRS.itemsPerPage) {
                            MRS.hasMorePages = true;
                            response.shufflings.pop();
                        }
                        view.data.length = 0;
                        var amountDecimals = MRS.getNumberOfDecimals(response.shufflings, "amount", function(shuffling) {
                            switch (shuffling.holdingType) {
                                case 0: return MRS.formatAmount(shuffling.amount);
                                case 1:
                                case 2: return MRS.formatQuantity(shuffling.amount, shuffling.holdingInfo.decimals);
                                default: return "";
                            }
                        });
                        response.shufflings.forEach(
                            function (shufflingJson) {
                                view.data.push(MRS.jsondata.shuffling(shufflingJson, shufflers, amountDecimals))
                            }
                        );
                        view.render({
                            isLoading: false,
                            isEmpty: view.data.length == 0
                        });
                        MRS.pageLoaded();
                        callback(null);
                    }
                );
            }
        ], function (err, result) {});
    };

    return MRS;

}(MRS || {}, jQuery));