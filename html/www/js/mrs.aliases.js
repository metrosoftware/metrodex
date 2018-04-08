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
var MRS = (function (MRS, $, undefined) {
    MRS.pages.aliases = function () {
        var alias_count;
        MRS.sendRequest("getAliasCount+", {"account": MRS.account}, function (response) {
            alias_count = response.numberOfAliases;
        });

        MRS.sendRequest("getAliases+", {
            "account": MRS.account,
            "firstIndex": MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage,
            "lastIndex": MRS.pageNumber * MRS.itemsPerPage
        }, function (response) {
            var aliasesTable = $("#aliases_table");
            if (response.aliases && response.aliases.length) {
                if (response.aliases.length > MRS.itemsPerPage) {
                    MRS.hasMorePages = true;
                    response.aliases.pop();
                }

                var aliases = response.aliases;
                aliases.sort(function (a, b) {
                    if (a.aliasName.toLowerCase() > b.aliasName.toLowerCase()) {
                        return 1;
                    } else if (a.aliasName.toLowerCase() < b.aliasName.toLowerCase()) {
                        return -1;
                    } else {
                        return 0;
                    }
                });

                var rows = "";
                for (var i = 0; i < aliases.length; i++) {
                    var alias = aliases[i];
                    alias.status = $.t("registered");
                    if (!alias.aliasURI) {
                        alias.aliasURI = "";
                    }

                    if (alias.aliasURI.length > 100) {
                        alias.shortAliasURI = alias.aliasURI.substring(0, 100) + "...";
                        alias.shortAliasURI = MRS.escapeRespStr(alias.shortAliasURI);
                    } else {
                        alias.shortAliasURI = MRS.escapeRespStr(alias.aliasURI);
                    }
                    alias.aliasURI = MRS.escapeRespStr(alias.aliasURI);

                    var allowCancel = false;
                    if ("priceMQT" in alias) {
                        if (alias.priceMQT == "0") {
                            if (alias.buyer == MRS.account) {
                                alias.status = $.t("cancelling_sale");
                            } else {
                                alias.status = $.t("transfer_in_progress");
                            }
                        } else {
                            allowCancel = true;
                            if (typeof alias.buyer != "undefined") {
                                alias.status = $.t("for_sale_direct");
                            } else {
                                alias.status = $.t("for_sale_indirect");
                            }
                        }
                    }

                    if (alias.status != "/") {
                        alias.status = "<span class='label label-small label-info'>" + alias.status + "</span>";
                    }
                    rows += "<tr data-alias='" + MRS.unescapeRespStr(alias.aliasName).toLowerCase().escapeHTML() + "'><td class='alias'>" + MRS.escapeRespStr(alias.aliasName) + "</td><td class='uri'>" + (alias.aliasURI.indexOf("http") === 0 ? "<a href='" + alias.aliasURI + "' target='_blank'>" + alias.shortAliasURI + "</a>" : alias.shortAliasURI) + "</td><td class='status'>" + alias.status + "</td><td style='white-space:nowrap'><a class='btn btn-xs btn-default' href='#' data-toggle='modal' data-target='#register_alias_modal' data-alias='" + MRS.escapeRespStr(alias.aliasName) + "'>" + $.t("edit") + "</a> <a class='btn btn-xs btn-default' href='#' data-toggle='modal' data-target='#transfer_alias_modal' data-alias='" + MRS.escapeRespStr(alias.aliasName) + "'>" + $.t("transfer") + "</a> <a class='btn btn-xs btn-default' href='#' data-toggle='modal' data-target='#sell_alias_modal' data-alias='" + MRS.escapeRespStr(alias.aliasName) + "'>" + $.t("sell") + "</a>" + (allowCancel ? " <a class='btn btn-xs btn-default cancel_alias_sale' href='#' data-toggle='modal' data-target='#cancel_alias_sale_modal' data-alias='" + MRS.escapeRespStr(alias.aliasName) + "'>" + $.t("cancel_sale") + "</a>" : "") + " <a class='btn btn-xs btn-default' href='#' data-toggle='modal' data-target='#delete_alias_modal' data-alias='" + MRS.escapeRespStr(alias.aliasName) + "'>" + $.t("delete") + "</a></td></tr>";
                }

                aliasesTable.find("tbody").empty().append(rows);
                MRS.dataLoadFinished(aliasesTable);
                $("#alias_count").html(alias_count).removeClass("loading_dots");
            } else {
                aliasesTable.find("tbody").empty();
                MRS.dataLoadFinished(aliasesTable);
                $("#alias_count").html("0").removeClass("loading_dots");
            }

            MRS.pageLoaded();
        });
    };

    MRS.setup.aliases = function () {
        var options = {
            "id": 'sidebar_aliases',
            "titleHTML": '<i class="fa fa-bookmark"></i> <span data-i18n="aliases">Aliases</span>',
            "page": 'aliases',
            "desiredPosition": 100,
            "depends": { tags: [ MRS.constants.API_TAGS.ALIASES ] }
        };
        MRS.addSimpleSidebarMenuItem(options);
    };

    $("#transfer_alias_modal, #sell_alias_modal, #cancel_alias_sale_modal, #delete_alias_modal").on("show.bs.modal", function (e) {
        var $invoker = $(e.relatedTarget);

        var alias = String($invoker.data("alias"));

        $(this).find("input[name=aliasName]").val(alias.escapeHTML());
        $(this).find(".alias_name_display").html(alias.escapeHTML());

        if ($(this).attr("id") == "sell_alias_modal") {
            $(this).find("ul.nav-pills li").removeClass("active");
            $(this).find("ul.nav-pills li:first-child").addClass("active");
            $("#sell_alias_recipient_div").show();
        }
    });

    MRS.forms.sellAlias = function ($modal) {
        var data = MRS.getFormData($modal.find("form:first"));

        var successMessage = "";
        var errorMessage = "";

        if (data.modal == "cancel_alias_sale") {
            data.priceMTR = "0";
            data.recipient = MRS.accountRS;

            successMessage = $.t("success_cancel_alias");
            errorMessage = $.t("error_cancel_alias");
        } else if (data.modal == "transfer_alias") {
            data.priceMTR = "0";

            successMessage = $.t("success_transfer_alias");
            errorMessage = $.t("error_transfer_alias");
        } else {
            if (!data.recipient) {
                return {
                    "error": $.t("error_not_specified", {
                        "name": $.t("recipient").toLowerCase()
                    }).capitalize()
                };
            }

            successMessage = $.t("success_sell_alias");
            errorMessage = $.t("error_sell_alias");

            if (data.recipient == MRS.constants.GENESIS_RS) {
                if (!data.priceMTR || data.priceMTR == "0") {
                    return {
                        "error": $.t("error_not_specified", {
                            "name": $.t("price").toLowerCase()
                        }).capitalize()
                    };
                }

                delete data.add_message;
                delete data.encrypt_message;
                delete data.permanent_message;
                delete data.message;
                delete data.recipient;
            }
        }

        delete data.modal;

        return {
            "data": data,
            "successMessage": successMessage,
            "errorMessage": errorMessage
        };
    };

    MRS.forms.sellAliasComplete = function (response, data) {
        if (response.alreadyProcessed) {
            return;
        }
        data.aliasName = String(data.aliasName).escapeHTML();
        if (data.priceMQT == "0") {
            if (data.recipient == MRS.account) {
                $.growl(
                    $.t("cancelling_sale", {
                        "alias_name": data.aliasName
                    }), {
                        "type": "success"
                    }
                );
            } else {
                $.growl(
                    $.t("success_alias_transfer", {
                        "alias_name": data.aliasName
                    }), {
                        "type": "success"
                    }
                );
            }
        } else {
            $.growl(
                $.t("success_alias_sell", {
                    "alias_name": String(data.aliasName).escapeHTML(),
                    "price": MRS.convertToMTR(data.priceMQT).escapeHTML()
                }), {
                    "type": "success"
                }
            );
        }
    };

    MRS.forms.deleteAlias = function ($modal) {
        var data = MRS.getFormData($modal.find("form:first"));
        var successMessage = $.t("success_delete_alias");
        var errorMessage = $.t("error_delete_alias");
        delete data.modal;

        return {
            "data": data,
            "successMessage": successMessage,
            "errorMessage": errorMessage
        };
    };

    MRS.forms.deleteAliasComplete = function (response, data) {
        if (response.alreadyProcessed) {
            return;
        }
        data.aliasName = String(data.aliasName).escapeHTML();
        $.growl(
            $.t("success_alias_delete", {
                "alias_name": data.aliasName
            }), {
                "type": "success"
            }
        );
    };

    $("#sell_alias_to_specific_account, #sell_alias_to_anyone").on("click", function (e) {
        e.preventDefault();

        $(this).closest("ul").find("li").removeClass("active");
        $(this).parent().addClass("active");

        var $modal = $(this).closest(".modal");

        if ($(this).attr("id") == "sell_alias_to_anyone") {
            $modal.find("input[name=recipient]").val(MRS.constants.GENESIS_RS);
            $("#sell_alias_recipient_div").hide();
            $modal.find(".add_message_container, .optional_message").hide();
        } else {
            $modal.find("input[name=recipient]").val("");
            $("#sell_alias_recipient_div").show();
            $modal.find(".add_message_container").show();

            if ($("#sell_alias_add_message").is(":checked")) {
                $modal.find(".optional_message").show();
            } else {
                $modal.find(".optional_message").hide();
            }
        }

        $modal.find("input[name=converted_account_id]").val("");
        $modal.find(".callout").hide();
    });

    $("#buy_alias_modal").on("show.bs.modal", function (e) {
        var $modal = $(this);

        var $invoker = $(e.relatedTarget);
        var alias = String($invoker.data("alias"));

        MRS.sendRequest("getAlias", {
            "aliasName": alias
        }, function (response) {
            if (response.errorCode) {
                e.preventDefault();
                $.growl($.t("error_alias_not_found"), {
                    "type": "danger"
                });
            } else {
                if (!("priceMQT" in response)) {
                    e.preventDefault();
                    $.growl($.t("error_alias_not_for_sale"), {
                        "type": "danger"
                    });
                } else if (typeof response.buyer != "undefined" && response.buyer != MRS.account) {
                    e.preventDefault();
                    $.growl($.t("error_alias_sale_different_account"), {
                        "type": "danger"
                    });
                } else {
                    $modal.find("input[name=recipient]").val(MRS.escapeRespStr(response.accountRS));
                    $modal.find("input[name=aliasName]").val(alias.escapeHTML());
                    $modal.find(".alias_name_display").html(alias.escapeHTML());
                    $modal.find("input[name=amountMTR]").val(MRS.convertToMTR(response.priceMQT)).prop("readonly", true);
                }
            }
        }, { isAsync: false });
    });

    MRS.forms.buyAliasError = function () {
        $("#buy_alias_modal").find("input[name=priceMTR]").prop("readonly", false);
    };

    MRS.forms.buyAliasComplete = function (response, data) {
        if (response.alreadyProcessed) {
            return;
        }
        data.aliasName = String(data.aliasName).escapeHTML();
        $.growl(
            $.t("success_alias_buy", {
                "alias_name": data.aliasName,
                "price": MRS.convertToMTR(data.amountMQT).escapeHTML()
            }), {
                "type": "success"
            }
        );
    };

    $("#register_alias_modal").on("show.bs.modal", function (e) {
        var $invoker = $(e.relatedTarget);

        var alias = $invoker.data("alias");
        var registerAliasModal = $("#register_alias_modal");

        if (alias) {
            alias = String(alias);
            MRS.sendRequest("getAlias", {
                "aliasName": alias
            }, function (response) {
                if (response.errorCode) {
                    e.preventDefault();
                    $.growl($.t("error_alias_not_found"), {
                        "type": "danger"
                    });
                } else {
                    var aliasURI = [];
                    if (/http:\/\//i.test(response.aliasURI)) {
                        setAliasType("uri", response.aliasURI);
                    } else if ((aliasURI = /acct:(.*)@metro/.exec(response.aliasURI)) || (aliasURI = /nacc:(.*)/.exec(response.aliasURI))) {
                        setAliasType("account", response.aliasURI);
                        response.aliasURI = String(aliasURI[1]).toUpperCase();
                    } else {
                        setAliasType("general", response.aliasURI);
                    }

                    registerAliasModal.find("h4.modal-title").html($.t("update_alias"));
                    registerAliasModal.find(".btn-primary").html($.t("update"));
                    $("#register_alias_alias").val(alias.escapeHTML()).hide();
                    $("#register_alias_alias_noneditable").html(alias.escapeHTML()).show();
                    $("#register_alias_alias_update").val(1);
                }
            }, { isAsync: false });
        } else {
            registerAliasModal.find("h4.modal-title").html($.t("register_alias"));
            registerAliasModal.find(".btn-primary").html($.t("register"));

            var prefill = $invoker.data("prefill-alias");

            if (prefill) {
                $("#register_alias_alias").val(prefill).show();
            } else {
                $("#register_alias_alias").val("").show();
            }
            $("#register_alias_alias_noneditable").html("").hide();
            $("#register_alias_alias_update").val(0);
            setAliasType("uri", "");
        }
    });

    MRS.incoming.aliases = function (transactions) {
        if (MRS.hasTransactionUpdates(transactions)) {
            MRS.loadPage("aliases");
        }
    };

    MRS.forms.setAlias = function ($modal) {
        var data = MRS.getFormData($modal.find("form:first"));

        data.aliasURI = $.trim(data.aliasURI).toLowerCase();

        if (data.type == "account") {
            if (!(/acct:(.*)@metro/.test(data.aliasURI)) && !(/nacc:(.*)/.test(data.aliasURI))) {
                if (MRS.isRsAccount(data.aliasURI)) {
                    var address = new MetroAddress();

                    if (!address.set(data.aliasURI)) {
                        return {
                            "error": $.t("error_invalid_account_id")
                        };
                    } else {
                        data.aliasURI = "acct:" + data.aliasURI + "@metro";
                    }
                } else if (MRS.isNumericAccount(data.aliasURI)) {
                    return {
                        "error": $.t("error_numeric_ids_not_allowed")
                    };
                } else {
                    return {
                        "error": $.t("error_invalid_account_id")
                    };
                }
            }
        }

        delete data["type"];

        return {
            "data": data
        };
    };

    function setAliasType(type, uri) {
        $("#register_alias_type").val(type);

        var registerAliasUri = $("#register_alias_uri");
        if (type == "uri") {
            registerAliasUri.unmask();
            $("#register_alias_uri_label").html($.t("uri"));
            registerAliasUri.prop("placeholder", $.t("uri"));
            if (uri) {
                if (uri == MRS.accountRS) {
                    registerAliasUri.val("http://");
                } else if (!/https?:\/\//i.test(uri)) {
                    registerAliasUri.val("http://" + uri);
                } else {
                    registerAliasUri.val(uri);
                }
            } else {
                registerAliasUri.val("http://");
            }
            $("#register_alias_help").hide();
        } else if (type == "account") {
            $("#register_alias_uri_label").html($.t("account_id"));
            registerAliasUri.prop("placeholder", $.t("account_id"));
            registerAliasUri.val("").mask(MRS.getAccountMask("*"));

            if (uri) {
                var match = uri.match(/acct:(.*)@metro/i);
                if (!match) {
                    match = uri.match(/nacc:(.*)/i);
                }

                if (match && match[1]) {
                    uri = match[1];
                }

                if (MRS.isNumericAccount(uri)) {
                    var address = new MetroAddress();

                    if (address.set(uri)) {
                        uri = address.toString();
                    } else {
                        uri = "";
                    }
                } else if (!MRS.isRsAccount(uri)) {
                    uri = MRS.accountRS;
                }

                uri = uri.toUpperCase();

                registerAliasUri.val(uri);
            } else {
                registerAliasUri.val(MRS.accountRS);
            }
            $("#register_alias_help").html($.t("alias_account_help")).show();
        } else {
            registerAliasUri.unmask();
            $("#register_alias_uri_label").html($.t("data"));
            registerAliasUri.prop("placeholder", $.t("data"));
            if (uri) {
                if (uri == MRS.accountRS) {
                    registerAliasUri.val("");
                } else if (uri == "http://") {
                    registerAliasUri.val("");
                } else {
                    registerAliasUri.val(uri);
                }
            }
            $("#register_alias_help").html($.t("alias_data_help")).show();
        }
    }

    $("#register_alias_type").on("change", function () {
        var type = $(this).val();
        setAliasType(type, $("#register_alias_uri").val());
    });

    MRS.forms.setAliasError = function (response, data) {
        if (response && response.errorCode && response.errorCode == 8) {
            var errorDescription = MRS.escapeRespStr(response.errorDescription);

            MRS.sendRequest("getAlias", {
                "aliasName": data.aliasName
            }, function (response) {
                var message;

                if (!response.errorCode) {
                    if ("priceMQT" in response) {
                        if (response.buyer == MRS.account) {
                            message = $.t("alias_sale_direct_offer", {
                                "amount": MRS.formatAmount(response.priceMQT), "symbol": MRS.constants.COIN_SYMBOL
                            }) + " <a href='#' data-alias='" + MRS.escapeRespStr(response.aliasName) + "' data-toggle='modal' data-target='#buy_alias_modal'>" + $.t("buy_it_q") + "</a>";
                        } else if (typeof response.buyer == "undefined") {
                            message = $.t("alias_sale_indirect_offer", {
                                "amount": MRS.formatAmount(response.priceMQT), "symbol": MRS.constants.COIN_SYMBOL
                            }) + " <a href='#' data-alias='" + MRS.escapeRespStr(response.aliasName) + "' data-toggle='modal' data-target='#buy_alias_modal'>" + $.t("buy_it_q") + "</a>";
                        } else {
                            message = $.t("error_alias_sale_different_account");
                        }
                    } else {
                        message = "<a href='#' class='show_account_modal_action' data-user='" + MRS.getAccountFormatted(response, "account") + "'>" + $.t("view_owner_info_q") + "</a>";
                    }

                    $("#register_alias_modal").find(".error_message").html(errorDescription + ". " + message);
                }
            }, { isAsync: false });
        }
    };

    MRS.forms.setAliasComplete = function (response, data) {
        if (response.alreadyProcessed) {
            return;
        }
        data.aliasName = String(data.aliasName).escapeHTML();
        if (MRS.currentPage == "aliases") {
            var aliasesTable = $("#aliases_table");
            var $table = aliasesTable.find("tbody");
            var $row = $table.find("tr[data-alias=" + data.aliasName.toLowerCase() + "]");
            if ($row.length) {
                $.growl(
                    $.t("success_alias_update", {
                        "alias_name": data.aliasName
                    }), {
                        "type": "success"
                    }
                );
            } else {
                $.growl(
                    $.t("success_alias_register", {
                        "alias_name": data.aliasName
                    }), {
                        "type": "success"
                    }
                );
            }
        }
    };

    $("#alias_search").on("submit", function (e) {
        e.preventDefault();
        var alias = $.trim($("#alias_search").find("input[name=q]").val());

        $("#alias_info_table").find("tbody").empty();

        MRS.sendRequest("getAlias", {
            "aliasName": alias
        }, function (response) {
            if (response.errorCode) {
                $.growl($.t("error_alias_not_found") + " <a href='#' data-toggle='modal' data-target='#register_alias_modal' data-prefill-alias='" + String(alias).escapeHTML() + "'>" + $.t("register_q") + "</a>", {
                    "type": "danger"
                });
            } else {
                $("#alias_info_modal_alias").html(MRS.escapeRespStr(response.aliasName));

                var data = {
                    "account": MRS.getAccountTitle(response, "account"),
                    "last_updated": MRS.formatTimestamp(response.timestamp),
                    "data_formatted_html": String(response.aliasURI).autoLink()
                };

                if ("priceMQT" in response) {
                    if (response.buyer == MRS.account) {
                        $("#alias_sale_callout").html($.t("alias_sale_direct_offer", {
                            "amount": MRS.formatAmount(response.priceMQT), "symbol": MRS.constants.COIN_SYMBOL
                        }) + " <a href='#' data-alias='" + MRS.escapeRespStr(response.aliasName) + "' data-toggle='modal' data-target='#buy_alias_modal'>" + $.t("buy_it_q") + "</a>").show();
                    } else if (typeof response.buyer == "undefined") {
                        $("#alias_sale_callout").html($.t("alias_sale_indirect_offer", {
                            "amount": MRS.formatAmount(response.priceMQT), "symbol": MRS.constants.COIN_SYMBOL
                        }) + " <a href='#' data-alias='" + MRS.escapeRespStr(response.aliasName) + "' data-toggle='modal' data-target='#buy_alias_modal'>" + $.t("buy_it_q") + "</a>").show();
                    } else {
                        $("#alias_sale_callout").html($.t("error_alias_sale_different_account")).show();
                    }
                } else {
                    $("#alias_sale_callout").hide();
                }

                $("#alias_info_table").find("tbody").append(MRS.createInfoTable(data));

                $("#alias_info_modal").modal("show");
            }
        });
    });

    return MRS;
}(MRS || {}, jQuery));