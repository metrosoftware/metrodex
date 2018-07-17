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
var MRS = (function (MRS, $, undefined) {
    $('body').on("click", ".show_transaction_modal_action", function (e) {
        e.preventDefault();

        var transactionId = $(this).data("transaction");
        var sharedKey = $(this).data("sharedkey");
        var infoModal = $('#transaction_info_modal');
        var isModalVisible = false;
        if (infoModal && infoModal.data('bs.modal')) {
            isModalVisible = infoModal.data('bs.modal').isShown;
        }
        if ($(this).data("back") == "true") {
            MRS.modalStack.pop(); // The forward modal
            MRS.modalStack.pop(); // the current modal
        }
        MRS.showTransactionModal(transactionId, isModalVisible, sharedKey);
    });

    MRS.showTransactionModal = function (transaction, isModalVisible, sharedKey) {
        if (MRS.fetchingModalData) {
            return;
        }

        MRS.fetchingModalData = true;

        $("#transaction_info_output_top, #transaction_info_output_bottom, #transaction_info_bottom").html("").hide();
        $("#transaction_info_callout").hide();
        var infoTable = $("#transaction_info_table");
        infoTable.hide();
        infoTable.find("tbody").empty();

        try {
            if (typeof transaction != "object") {
                MRS.sendRequest("getTransaction", {
                    "transaction": transaction
                }, function (response, input) {
                    response.transaction = input.transaction;
                    MRS.processTransactionModalData(response, isModalVisible, sharedKey);
                });
            } else {
                MRS.processTransactionModalData(transaction, isModalVisible, sharedKey);
            }
        } catch (e) {
            MRS.fetchingModalData = false;
            throw e;
        }
    };

    MRS.getPhasingDetails = function(phasingDetails, phasingParams) {
        var votingModel = MRS.getVotingModelName(parseInt(phasingParams.phasingVotingModel));
        phasingDetails.votingModel = $.t(votingModel);
        switch (votingModel) {
            case 'ASSET':
                MRS.sendRequest("getAsset", { "asset": phasingParams.phasingHolding }, function(response) {
                    phasingDetails.quorum = MRS.convertToQNTf(phasingParams.phasingQuorum, response.decimals);
                    phasingDetails.minBalance = MRS.convertToQNTf(phasingParams.phasingMinBalance, response.decimals);
                }, { isAsync: false });
                break;
            default:
                phasingDetails.quorum = phasingParams.phasingQuorum;
                phasingDetails.minBalance = phasingParams.phasingMinBalance;
        }
        var phasingTransactionLink = MRS.getTransactionLink(phasingParams.phasingHolding);
        if (MRS.constants.VOTING_MODELS[votingModel] == MRS.constants.VOTING_MODELS.ASSET) {
            phasingDetails.asset_formatted_html = phasingTransactionLink;
        }
        var minBalanceModel = MRS.getMinBalanceModelName(parseInt(phasingParams.phasingMinBalanceModel));
        phasingDetails.minBalanceModel = $.t(minBalanceModel);
        var rows = "";
        if (phasingParams.phasingWhitelist && phasingParams.phasingWhitelist.length > 0) {
            rows = "<table class='table table-striped'><thead><tr>" +
                "<th>" + $.t("Account") + "</th>" +
                "</tr></thead><tbody>";
            for (var i = 0; i < phasingParams.phasingWhitelist.length; i++) {
                var account = MRS.convertNumericToRSAccountFormat(phasingParams.phasingWhitelist[i]);
                rows += "<tr><td><a href='#' data-user='" + MRS.escapeRespStr(account) + "' class='show_account_modal_action'>" + MRS.getAccountTitle(account) + "</a></td></tr>";
            }
            rows += "</tbody></table>";
        } else {
            rows = "-";
        }
        phasingDetails.whitelist_formatted_html = rows;
        if (phasingParams.phasingLinkedFullHashes && phasingParams.phasingLinkedFullHashes.length > 0) {
            rows = "<table class='table table-striped'><tbody>";
            for (i = 0; i < phasingParams.phasingLinkedFullHashes.length; i++) {
                rows += "<tr><td>" + phasingParams.phasingLinkedFullHashes[i] + "</td></tr>";
            }
            rows += "</tbody></table>";
        } else {
            rows = "-";
        }
        phasingDetails.full_hash_formatted_html = rows;
        if (phasingParams.phasingHashedSecret) {
            phasingDetails.hashedSecret = phasingParams.phasingHashedSecret;
            phasingDetails.hashAlgorithm = MRS.getHashAlgorithm(phasingParams.phasingHashedSecretAlgorithm);
        }
    };

    MRS.processTransactionModalData = function (transaction, isModalVisible, sharedKey) {
        MRS.setBackLink();
        MRS.modalStack.push({ class: "show_transaction_modal_action", key: "transaction", value: transaction.transaction });
        try {
            var async = false;

            var transactionDetails = $.extend({}, transaction);
            delete transactionDetails.attachment;
            if (transactionDetails.referencedTransaction == "0") {
                delete transactionDetails.referencedTransaction;
            }
            delete transactionDetails.transaction;

            if (!transactionDetails.confirmations) {
                transactionDetails.confirmations = "/";
            }
            if (!transactionDetails.block) {
                transactionDetails.block = "unconfirmed";
            }
            if (transactionDetails.timestamp) {
                transactionDetails.transactionTime = MRS.formatTimestamp(transactionDetails.timestamp);
            }
            if (transactionDetails.blockTimestamp) {
                transactionDetails.blockGenerationTime = MRS.formatTimestamp(transactionDetails.blockTimestamp);
            }
            if (transactionDetails.height == MRS.constants.MAX_INT_JAVA) {
                transactionDetails.height = "unknown";
            } else {
                transactionDetails.height_formatted_html = MRS.getBlockLink(transactionDetails.height);
                delete transactionDetails.height;
            }
            $("#transaction_info_modal_transaction").html(MRS.escapeRespStr(transaction.transaction));

            $("#transaction_info_tab_link").tab("show");

            $("#transaction_info_details_table").find("tbody").empty().append(MRS.createInfoTable(transactionDetails, true));
            var infoTable = $("#transaction_info_table");
            infoTable.find("tbody").empty();

            var incorrect = false;
            if (transaction.senderRS == MRS.accountRS) {
                $("#transaction_info_modal_send_money").attr('disabled','disabled');
                $("#transaction_info_modal_send_message").attr('disabled','disabled');
            } else {
                $("#transaction_info_modal_send_money").removeAttr('disabled');
                $("#transaction_info_modal_send_message").removeAttr('disabled');
            }
            var accountButton;
            if (transaction.senderRS in MRS.contacts) {
                accountButton = MRS.contacts[transaction.senderRS].name.escapeHTML();
                $("#transaction_info_modal_add_as_contact").attr('disabled','disabled');
            } else {
                accountButton = transaction.senderRS;
                $("#transaction_info_modal_add_as_contact").removeAttr('disabled');
            }
            var approveTransactionButton = $("#transaction_info_modal_approve_transaction");
            if (!transaction.attachment || !transaction.block ||
                !transaction.attachment.phasingFinishHeight ||
                transaction.attachment.phasingFinishHeight <= MRS.lastBlockHeight) {
                approveTransactionButton.attr('disabled', 'disabled');
            } else {
                approveTransactionButton.removeAttr('disabled');
                approveTransactionButton.data("transaction", transaction.transaction);
                approveTransactionButton.data("fullhash", transaction.fullHash);
                approveTransactionButton.data("timestamp", transaction.timestamp);
                approveTransactionButton.data("minBalanceFormatted", "");
                approveTransactionButton.data("votingmodel", transaction.attachment.phasingVotingModel);
            }

            $("#transaction_info_actions").show();
            $("#transaction_info_actions_tab").find("button").data("account", accountButton);

            if (transaction.attachment && transaction.attachment.phasingFinishHeight) {
                var finishHeight = transaction.attachment.phasingFinishHeight;
                var phasingDetails = {};
                phasingDetails.finishHeight = finishHeight;
                phasingDetails.finishIn = ((finishHeight - MRS.lastBlockHeight) > 0) ? (finishHeight - MRS.lastBlockHeight) + " " + $.t("blocks") : $.t("finished");
                MRS.getPhasingDetails(phasingDetails, transaction.attachment);
                $("#phasing_info_details_table").find("tbody").empty().append(MRS.createInfoTable(phasingDetails, true));
                $("#phasing_info_details_link").show();
            } else {
                $("#phasing_info_details_link").hide();
            }
            // TODO Someday I'd like to replace it with if (MRS.isOfType(transaction, "OrdinaryPayment"))
            var data;
            var message;
            var fieldsToDecrypt = {};
            var i;
            if (transaction.type == 0) {
                switch (transaction.subtype) {
                    case 0:
                        data = {
                            "type": $.t("ordinary_payment"),
                            "amount": transaction.amountMQT,
                            "fee": transaction.feeMQT,
                            "recipient": transaction.recipientRS ? transaction.recipientRS : transaction.recipient,
                            "sender": transaction.senderRS ? transaction.senderRS : transaction.sender
                        };

                        infoTable.find("tbody").append(MRS.createInfoTable(data));
                        infoTable.show();

                        break;
                    default:
                        incorrect = true;
                        break;
                }
            } else if (transaction.type == 1) {
                switch (transaction.subtype) {
                    case 0:
                        var $output = $("#transaction_info_output_top");
                        if (transaction.attachment) {
                            if (transaction.attachment.message) {
                                if (!transaction.attachment["version.Message"] && !transaction.attachment["version.PrunablePlainMessage"]) {
                                    try {
                                        message = converters.hexStringToString(transaction.attachment.message);
                                    } catch (err) {
                                        //legacy
                                        if (transaction.attachment.message.indexOf("feff") === 0) {
                                            message = MRS.convertFromHex16(transaction.attachment.message);
                                        } else {
                                            message = MRS.convertFromHex8(transaction.attachment.message);
                                        }
                                    }
                                } else {
                                    if (transaction.attachment.messageIsText) {
                                        message = String(transaction.attachment.message);
                                    } else {
                                        message = $.t("binary_data");
                                    }
                                }
                                $output.html("<div style='color:#999999;padding-bottom:10px'><i class='fa fa-unlock'></i> " + $.t("public_message") + "</div><div style='padding-bottom:10px'>" + MRS.escapeRespStr(message).nl2br() + "</div>");
                            }

                            if (transaction.attachment.encryptedMessage || (transaction.attachment.encryptToSelfMessage && MRS.account == transaction.sender)) {
                                $output.append("" +
                                    "<div id='transaction_info_decryption_form'></div>" +
                                    "<div id='transaction_info_decryption_output' style='display:none;padding-bottom:10px;'></div>"
                                );
                                if (transaction.attachment.encryptedMessage) {
                                    fieldsToDecrypt.encryptedMessage = $.t("encrypted_message");
                                }
                                if (transaction.attachment.encryptToSelfMessage && MRS.account == transaction.sender) {
                                    fieldsToDecrypt.encryptToSelfMessage = $.t("note_to_self");
                                }
                                var options = {
                                    "noPadding": true,
                                    "formEl": "#transaction_info_decryption_form",
                                    "outputEl": "#transaction_info_decryption_output"
                                };
                                if (sharedKey) {
                                    options["sharedKey"] = sharedKey;
                                }
                                MRS.tryToDecrypt(transaction, fieldsToDecrypt, MRS.getAccountForDecryption(transaction), options);
                            }
                        } else {
                            $output.append("<div style='padding-bottom:10px'>" + $.t("message_empty") + "</div>");
                        }
                        var isCompressed = false;
                        if (transaction.attachment.encryptedMessage) {
                            isCompressed = transaction.attachment.encryptedMessage.isCompressed;
                        } else if (transaction.attachment.encryptToSelfMessage) {
                            isCompressed = transaction.attachment.encryptToSelfMessage.isCompressed;
                        }
                        var hash = transaction.attachment.messageHash || transaction.attachment.encryptedMessageHash;
                        var hashRow = hash ? ("<tr><td><strong>" + $.t("hash") + "</strong>:&nbsp;</td><td>" + hash + "</td></tr>") : "";
                        var downloadLink = "";
                        if (transaction.attachment.messageHash && !MRS.isTextMessage(transaction) && transaction.block) {
                            downloadLink = "<tr><td>" + MRS.getMessageDownloadLink(transaction.transaction, sharedKey) + "</td></tr>";
                        }
                        $output.append("<table>" +
                            "<tr><td><strong>" + $.t("from") + "</strong>:&nbsp;</td><td>" + MRS.getAccountLink(transaction, "sender") + "</td></tr>" +
                            "<tr><td><strong>" + $.t("to") + "</strong>:&nbsp;</td><td>" + MRS.getAccountLink(transaction, "recipient") + "</td></tr>" +
                            "<tr><td><strong>" + $.t("compressed") + "</strong>:&nbsp;</td><td>" + isCompressed + "</td></tr>" +
                            hashRow + downloadLink +
                        "</table>");
                        $output.show();
                        break;
                    case 1:
                        data = {
                            "type": $.t("alias_assignment"),
                            "alias": transaction.attachment.alias,
                            "data_formatted_html": transaction.attachment.uri.autoLink()
                        };
                        data["sender"] = transaction.senderRS ? transaction.senderRS : transaction.sender;
                        infoTable.find("tbody").append(MRS.createInfoTable(data));
                        infoTable.show();

                        break;
                    case 2:
                        data = {
                            "type": $.t("poll_creation"),
                            "name": transaction.attachment.name,
                            "description": transaction.attachment.description,
                            "finish_height": transaction.attachment.finishHeight,
                            "min_number_of_options": transaction.attachment.minNumberOfOptions,
                            "max_number_of_options": transaction.attachment.maxNumberOfOptions,
                            "min_range_value": transaction.attachment.minRangeValue,
                            "max_range_value": transaction.attachment.maxRangeValue,
                            "min_balance": transaction.attachment.minBalance,
                            "min_balance_model": transaction.attachment.minBalanceModel
                        };

                        if (transaction.attachment.votingModel == -1) {
                            data["voting_model"] = $.t("vote_by_none");
                        } else if (transaction.attachment.votingModel == 0) {
                            data["voting_model"] = $.t("vote_by_account");
                        } else if (transaction.attachment.votingModel == 1) {
                            data["voting_model"] = $.t("vote_by_balance");
                        } else if (transaction.attachment.votingModel == 2) {
                            data["voting_model"] = $.t("vote_by_asset");
                            data["asset_id"] = transaction.attachment.holding;
                        } else if (transaction.attachment.votingModel == 4) {
                            data["voting_model"] = $.t("vote_by_transaction");
                        } else if (transaction.attachment.votingModel == 5) {
                            data["voting_model"] = $.t("vote_by_hash");
                        } else {
                            data["voting_model"] = transaction.attachment.votingModel;
                        }


                        for (i = 0; i < transaction.attachment.options.length; i++) {
                            data["option_" + i] = transaction.attachment.options[i];
                        }

                        if (transaction.sender != MRS.account) {
                            data["sender"] = MRS.getAccountTitle(transaction, "sender");
                        }

                        data["sender"] = transaction.senderRS ? transaction.senderRS : transaction.sender;
                        infoTable.find("tbody").append(MRS.createInfoTable(data));
                        infoTable.show();

                        break;
                    case 3:
                        var vote = "";
                        var votes = transaction.attachment.vote;
                        if (votes && votes.length > 0) {
                            for (i = 0; i < votes.length; i++) {
                                if (votes[i] == -128) {
                                    vote += "N/A";
                                } else {
                                    vote += votes[i];
                                }
                                if (i < votes.length - 1) {
                                    vote += " , ";
                                }
                            }
                        }
                        data = {
                            "type": $.t("vote_casting"),
                            "poll_formatted_html": MRS.getTransactionLink(transaction.attachment.poll),
                            "vote": vote
                        };
                        data["sender"] = transaction.senderRS ? transaction.senderRS : transaction.sender;
                        infoTable.find("tbody").append(MRS.createInfoTable(data));
                        infoTable.show();

                        break;
                    case 4:
                        data = {
                            "type": $.t("hub_announcement")
                        };

                        infoTable.find("tbody").append(MRS.createInfoTable(data));
                        infoTable.show();

                        break;
                    case 5:
                        data = {
                            "type": $.t("account_info"),
                            "name": transaction.attachment.name,
                            "description": transaction.attachment.description
                        };

                        infoTable.find("tbody").append(MRS.createInfoTable(data));
                        infoTable.show();

                        break;
                    case 6:
                        var type = $.t("alias_sale");
                        if (transaction.attachment.priceMQT == "0") {
                            if (transaction.sender == transaction.recipient) {
                                type = $.t("alias_sale_cancellation");
                            } else {
                                type = $.t("alias_transfer");
                            }
                        }

                        data = {
                            "type": type,
                            "alias_name": transaction.attachment.alias
                        };

                        if (type == $.t("alias_sale")) {
                            data["price"] = transaction.attachment.priceMQT
                        }

                        if (type != $.t("alias_sale_cancellation")) {
                            data["recipient"] = transaction.recipientRS ? transaction.recipientRS : transaction.recipient;
                        }

                        data["sender"] = transaction.senderRS ? transaction.senderRS : transaction.sender;

                        if (type == $.t("alias_sale")) {
                            message = "";
                            var messageStyle = "info";

                            MRS.sendRequest("getAlias", {
                                "aliasName": transaction.attachment.alias
                            }, function (response) {
                                MRS.fetchingModalData = false;

                                if (!response.errorCode) {
                                    if (transaction.recipient != response.buyer || transaction.attachment.priceMQT != response.priceMQT) {
                                        message = $.t("alias_sale_info_outdated");
                                        messageStyle = "danger";
                                    } else if (transaction.recipient == MRS.account) {
                                        message = $.t("alias_sale_direct_offer", {
                                            "amount": MRS.formatAmount(transaction.attachment.priceMQT), "symbol": MRS.constants.COIN_SYMBOL
                                        }) + " <a href='#' data-alias='" + MRS.escapeRespStr(transaction.attachment.alias) + "' data-toggle='modal' data-target='#buy_alias_modal'>" + $.t("buy_it_q") + "</a>";
                                    } else if (typeof transaction.recipient == "undefined") {
                                        message = $.t("alias_sale_indirect_offer", {
                                            "amount": MRS.formatAmount(transaction.attachment.priceMQT), "symbol": MRS.constants.COIN_SYMBOL
                                        }) + " <a href='#' data-alias='" + MRS.escapeRespStr(transaction.attachment.alias) + "' data-toggle='modal' data-target='#buy_alias_modal'>" + $.t("buy_it_q") + "</a>";
                                    } else if (transaction.senderRS == MRS.accountRS) {
                                        if (transaction.attachment.priceMQT != "0") {
                                            message = $.t("your_alias_sale_offer") + " <a href='#' data-alias='" + MRS.escapeRespStr(transaction.attachment.alias) + "' data-toggle='modal' data-target='#cancel_alias_sale_modal'>" + $.t("cancel_sale_q") + "</a>";
                                        }
                                    } else {
                                        message = $.t("error_alias_sale_different_account");
                                    }
                                }
                            }, { isAsync: false });

                            if (message) {
                                $("#transaction_info_bottom").html("<div class='callout callout-bottom callout-" + messageStyle + "'>" + message + "</div>").show();
                            }
                        }

                        infoTable.find("tbody").append(MRS.createInfoTable(data));
                        infoTable.show();

                        break;
                    case 7:
                        data = {
                            "type": $.t("alias_buy"),
                            "alias_name": transaction.attachment.alias,
                            "price": transaction.amountMQT,
                            "recipient": transaction.recipientRS ? transaction.recipientRS : transaction.recipient,
                            "sender": transaction.senderRS ? transaction.senderRS : transaction.sender
                        };

                        infoTable.find("tbody").append(MRS.createInfoTable(data));
                        infoTable.show();

                        break;
                    case 8:
                        data = {
                            "type": $.t("alias_deletion"),
                            "alias_name": transaction.attachment.alias,
                            "sender": transaction.senderRS ? transaction.senderRS : transaction.sender
                        };

                        infoTable.find("tbody").append(MRS.createInfoTable(data));
                        infoTable.show();

                        break;
                    case 9:
                        data = {
                            "type": $.t("transaction_approval")
                        };
                        for (i = 0; i < transaction.attachment.transactionFullHashes.length; i++) {
                            var transactionBytes = converters.hexStringToByteArray(transaction.attachment.transactionFullHashes[i]);
                            var transactionId = converters.byteArrayToBigInteger(transactionBytes, 0).toString().escapeHTML();
                            data["transaction" + (i + 1) + "_formatted_html"] =
                                MRS.getTransactionLink(transactionId);
                        }

                        infoTable.find("tbody").append(MRS.createInfoTable(data));
                        infoTable.show();

                        break;
                    case 10:
                        data = {
                            "type": $.t("set_account_property"),
                            "property": transaction.attachment.property,
                            "value": transaction.attachment.value
                        };
                        infoTable.find("tbody").append(MRS.createInfoTable(data));
                        infoTable.show();

                        break;
                    case 11:
                        data = {
                            "type": $.t("delete_account_property"),
                            "property_formatted_html": MRS.getTransactionLink(transaction.attachment.property)
                        };
                        infoTable.find("tbody").append(MRS.createInfoTable(data));
                        infoTable.show();

                        break;
                    default:
                        incorrect = true;
                        break;
                }
            } else if (transaction.type == 5) {
                switch (transaction.subtype) {
                    case 0:
                        MRS.sendRequest("getAsset", {
                            "asset": transaction.transaction
                        }, function (asset) {
                            data = {
                                "type": $.t("asset_issuance"),
                                "name": transaction.attachment.name,
                                "decimals": transaction.attachment.decimals,
                                "description": transaction.attachment.description
                            };
                            if (asset) {
                                data["initial_quantity"] = [asset.initialQuantityQNT, transaction.attachment.decimals];
                                data["quantity"] = [asset.quantityQNT, transaction.attachment.decimals];
                            }
                            data["sender"] = transaction.senderRS ? transaction.senderRS : transaction.sender;
                            $("#transaction_info_callout").html("<a href='#' data-goto-asset='" + MRS.escapeRespStr(transaction.transaction) + "'>Click here</a> to view this asset in the Asset Exchange.").show();

                            infoTable.find("tbody").append(MRS.createInfoTable(data));
                            infoTable.show();
                        });

                        break;
                    case 1:
                        async = true;

                        MRS.sendRequest("getAsset", {
                            "asset": transaction.attachment.asset
                        }, function (asset) {
                            data = {
                                "type": $.t("asset_transfer"),
                                "asset_formatted_html": MRS.getTransactionLink(transaction.attachment.asset),
                                "asset_name": asset.name,
                                "quantity": [transaction.attachment.quantityQNT, asset.decimals]
                            };

                            data["sender"] = transaction.senderRS ? transaction.senderRS : transaction.sender;
                            data["recipient"] = transaction.recipientRS ? transaction.recipientRS : transaction.recipient;
                            // Setting recipient to genesis to delete shares was allowed between v1.6 and v1.7
                            if (data.recipient == MRS.constants.BURNING_RS) {
                                data.type = $.t("delete_asset_shares");
                            }
                            infoTable.find("tbody").append(MRS.createInfoTable(data));
                            infoTable.show();

                            $("#transaction_info_modal").modal("show");
                            MRS.fetchingModalData = false;
                        });

                        break;
                    case 2:
                    case 3:
                        async = true;
                        MRS.sendRequest("getAsset", {
                            "asset": transaction.attachment.asset
                        }, function (asset) {
                            MRS.formatAssetOrder(asset, transaction, isModalVisible)
                        });
                        break;
                    case 4:
                        async = true;

                        MRS.sendRequest("getTransaction", {
                            "transaction": transaction.attachment.order
                        }, function (transaction) {
                            if (transaction.attachment.asset) {
                                MRS.sendRequest("getAsset", {
                                    "asset": transaction.attachment.asset
                                }, function (asset) {
                                    data = {
                                        "type": $.t("ask_order_cancellation"),
                                        "order_formatted_html": MRS.getTransactionLink(transaction.transaction),
                                        "asset_formatted_html": MRS.getTransactionLink(transaction.attachment.asset),
                                        "asset_name": asset.name,
                                        "quantity": [transaction.attachment.quantityQNT, asset.decimals],
                                        "price_formatted_html": MRS.formatOrderPricePerWholeQNT(transaction.attachment.priceMQT, asset.decimals) + " " + MRS.constants.COIN_SYMBOL,
                                        "total_formatted_html": MRS.formatAmount(MRS.calculateOrderTotalMQT(transaction.attachment.quantityQNT, transaction.attachment.priceMQT)) + " " + MRS.constants.COIN_SYMBOL
                                    };
                                    data["sender"] = transaction.senderRS ? transaction.senderRS : transaction.sender;
                                    infoTable.find("tbody").append(MRS.createInfoTable(data));
                                    infoTable.show();
                                    $("#transaction_info_modal").modal("show");
                                    MRS.fetchingModalData = false;
                                });
                            } else {
                                MRS.fetchingModalData = false;
                            }
                        });
                        break;
                    case 5:
                        async = true;
                        MRS.sendRequest("getTransaction", {
                            "transaction": transaction.attachment.order
                        }, function (transaction) {
                            if (transaction.attachment.asset) {
                                MRS.sendRequest("getAsset", {
                                    "asset": transaction.attachment.asset
                                }, function (asset) {
                                    data = {
                                        "type": $.t("bid_order_cancellation"),
                                        "order_formatted_html": MRS.getTransactionLink(transaction.transaction),
                                        "asset_formatted_html": MRS.getTransactionLink(transaction.attachment.asset),
                                        "asset_name": asset.name,
                                        "quantity": [transaction.attachment.quantityQNT, asset.decimals],
                                        "price_formatted_html": MRS.formatOrderPricePerWholeQNT(transaction.attachment.priceMQT, asset.decimals) + " " + MRS.constants.COIN_SYMBOL,
                                        "total_formatted_html": MRS.formatAmount(MRS.calculateOrderTotalMQT(transaction.attachment.quantityQNT, transaction.attachment.priceMQT)) + " " + MRS.constants.COIN_SYMBOL
                                    };
                                    data["sender"] = transaction.senderRS ? transaction.senderRS : transaction.sender;
                                    infoTable.find("tbody").append(MRS.createInfoTable(data));
                                    infoTable.show();
                                    $("#transaction_info_modal").modal("show");
                                    MRS.fetchingModalData = false;
                                });
                            } else {
                                MRS.fetchingModalData = false;
                            }
                        });
                        break;
                    case 6:
                        async = true;

                        MRS.sendRequest("getTransaction", {
                            "transaction": transaction.transaction
                        }, function (transaction) {
                            if (transaction.attachment.asset) {
                                MRS.sendRequest("getAsset", {
                                    "asset": transaction.attachment.asset
                                }, function (asset) {
                                    data = {
                                        "type": $.t("dividend_payment"),
                                        "asset_formatted_html": MRS.getTransactionLink(transaction.attachment.asset),
                                        "asset_name": asset.name,
                                        "amount_per_share": MRS.formatOrderPricePerWholeQNT(transaction.attachment.amountMQTPerQNT, asset.decimals) + " " + MRS.constants.COIN_SYMBOL,
                                        "height": transaction.attachment.height
                                    };
                                    data["sender"] = transaction.senderRS ? transaction.senderRS : transaction.sender;
                                    infoTable.find("tbody").append(MRS.createInfoTable(data));
                                    infoTable.show();

                                    $("#transaction_info_modal").modal("show");
                                    MRS.fetchingModalData = false;
                                });
                            } else {
                                MRS.fetchingModalData = false;
                            }
                        });
                        break;
                    case 7:
                        async = true;

                        MRS.sendRequest("getAsset", {
                            "asset": transaction.attachment.asset
                        }, function (asset) {
                            data = {
                                "type": $.t("delete_asset_shares"),
                                "asset_formatted_html": MRS.getTransactionLink(transaction.attachment.asset),
                                "asset_name": asset.name,
                                "quantity": [transaction.attachment.quantityQNT, asset.decimals]
                            };

                            data["sender"] = transaction.senderRS ? transaction.senderRS : transaction.sender;
                            infoTable.find("tbody").append(MRS.createInfoTable(data));
                            infoTable.show();

                            $("#transaction_info_modal").modal("show");
                            MRS.fetchingModalData = false;
                        });
                        break;
                    default:
                        incorrect = true;
                        break;
                }
            } else if (transaction.type == 3) {
                switch (transaction.subtype) {
                    case 0:
                        data = {
                            "type": $.t("balance_leasing"),
                            "period": transaction.attachment.period,
                            "lessee": transaction.recipientRS ? transaction.recipientRS : transaction.recipient
                        };

                        infoTable.find("tbody").append(MRS.createInfoTable(data));
                        infoTable.show();

                        break;
                    case 1:
                        data = {
                            "type": $.t("phasing_only")
                        };

                        MRS.getPhasingDetails(data, transaction.attachment.phasingControlParams);

                        infoTable.find("tbody").append(MRS.createInfoTable(data));
                        infoTable.show();

                        break;

                    default:
                        incorrect = true;
                        break;
                }
            } else if (transaction.type == 4) {
                data = {

                };
                if (transaction.attachment && transaction.attachment.recipients && Object.keys(transaction.attachment.recipients).length > 0) {
                    var rows = "<table class='table table-striped'><thead><tr>" +
                        "<th>" + $.t("account") + "</th>" +
                        "<th>" + $.t("amount") + "</th>" +
                        "<tr></thead><tbody>";
                    for (var accountId in transaction.attachment.recipients) {
                        // rows += "<tr>" +
                        //     "<td>" + MRS.getAccountLink({entity: accountId}) + "<td>" +
                        //     "<td>" + $.t(transaction.attachment.recipients[accountId]) + "</td>" +
                        //     "</tr>";
                        rows += "<tr><td><a href='#' data-user='" + MRS.escapeRespStr(accountId) + "' class='show_account_modal_action'>"
                            + MRS.getAccountTitle(accountId) + "</a></td><td>" + MRS.formatAmount(new BigInteger(String(transaction.attachment.recipients[accountId]))) + " " + MRS.constants.COIN_SYMBOL + "</td></tr>";
                    }
                    //var account = MRS.convertNumericToRSAccountFormat(phasingParams.phasingWhitelist[i]);
                    data["reward_recipients_formatted_html"] = rows;
                } else {
                    data["reward_recipients"] = $.t("no_matching_recipients");
                }
                infoTable.find("tbody").append(MRS.createInfoTable(data));
                infoTable.show();
            } else if (MRS.isOfType(transaction, "ShufflingCreation")) {
                data = {
                    "type": $.t("shuffling_creation"),
                    "period": transaction.attachment.registrationPeriod,
                    "holdingType": transaction.attachment.holdingType
                };
                if (transaction.attachment.holding != "0") {
                    var requestType;
                    if (data.holdingType == 1) {
                        requestType = "getAsset";
                    }
                    MRS.sendRequest(requestType, {"asset": transaction.attachment.holding}, function (response) {
                        data.holding_formatted_html = MRS.getTransactionLink(transaction.attachment.holding);
                        data.amount_formatted_html = MRS.convertToQNTf(transaction.attachment.amount, response.decimals);
                    }, { isAsync: false });
                } else {
                    data.amount = transaction.attachment.amount;
                }
                MRS.sendRequest("getShufflingParticipants", { "shuffling": transaction.transaction }, function (response) {
                    if (response.participants && response.participants.length > 0) {
                        var rows = "<table class='table table-striped'><thead><tr>" +
                        "<th>" + $.t("participant") + "</th>" +
                        "<th>" + $.t("state") + "</th>" +
                        "<tr></thead><tbody>";
                        for (i = 0; i < response.participants.length; i++) {
                            var participant = response.participants[i];
                            rows += "<tr>" +
                            "<td>" + MRS.getAccountLink(participant, "account") + "<td>" +
                            "<td>" + $.t(MRS.getShufflingParticipantState(participant.state).toLowerCase()) + "</td>" +
                            "</tr>";
                        }
                        rows += "</tbody></table>";
                        data["participants_formatted_html"] = rows;
                    } else {
                        data["participants"] = $.t("no_matching_participants");
                    }
                }, { isAsync: false });
                MRS.sendRequest("getShufflers", {
                    "shufflingFullHash": transaction.fullHash,
                    "account": MRS.accountRS,
                    "adminPassword": MRS.getAdminPassword()
                }, function (response) {
                    if (response.shufflers && response.shufflers.length > 0) {
                        var shuffler = response.shufflers[0];
                        data["shuffler"] = "running";
                        data["shufflerRecipient_formatted_html"] = MRS.getAccountLink(shuffler, "recipient");
                        if (shuffler.failedTransaction) {
                            data["failedTransaction_formatted_html"] = MRS.getTransactionLink(shuffler.recipient);
                            data["failureCause"] = shuffler.failureCause;
                        }
                    } else {
                        if (response.errorCode) {
                            data["shuffler"] = $.t("unknown");
                        } else {
                            data["shuffler"] = $.t("not_started");
                        }
                    }
                }, { isAsync: false });
                MRS.sendRequest("getShuffling", {
                    "shuffling": transaction.transaction
                }, function (response) {
                    if (response.shuffling) {
                        data["stage_formatted_html"] = MRS.getShufflingStage(response.stage);
                        data["count"] = response.registrantCount + " / " + response.participantCount;
                        data["blocksRemaining"] = response.blocksRemaining;
                        data["issuer_formatted_html"] = MRS.getAccountLink(response, "issuer");
                        if (response.assignee) {
                            data["assignee_formatted_html"] = MRS.getAccountLink(response, "assignee");
                        }
                        data["shufflingStateHash"] = response.shufflingStateHash;
                        if (response.recipientPublicKeys && response.recipientPublicKeys.length > 0) {
                            data["recipients_formatted_html"] = listPublicKeys(response.recipientPublicKeys);
                        }
                    }
                }, { isAsync: false });
                infoTable.find("tbody").append(MRS.createInfoTable(data));
                infoTable.show();
            } else if (MRS.isOfType(transaction, "ShufflingRegistration")) {
                data = { "type": $.t("shuffling_registration") };
                MRS.mergeMaps(transaction.attachment, data, { "version.ShufflingRegistration": true });
                infoTable.find("tbody").append(MRS.createInfoTable(data));
                infoTable.show();
            } else if (MRS.isOfType(transaction, "ShufflingProcessing")) {
                data = { "type": $.t("shuffling_processing") };
                MRS.mergeMaps(transaction.attachment, data, { "version.ShufflingProcessing": true });
                infoTable.find("tbody").append(MRS.createInfoTable(data));
                infoTable.show();
            } else if (MRS.isOfType(transaction, "ShufflingRecipients")) {
                data = {
                    "type": $.t("shuffling_recipients"),
                    "shuffling_state_hash": transaction.attachment.shufflingStateHash
                };
                data["shuffling_formatted_html"] = MRS.getTransactionLink(transaction.attachment.shuffling);
                data["recipients_formatted_html"] = listPublicKeys(transaction.attachment.recipientPublicKeys);
                infoTable.find("tbody").append(MRS.createInfoTable(data));
                infoTable.show();
            } else if (MRS.isOfType(transaction, "ShufflingVerification")) {
                data = { "type": $.t("shuffling_verification") };
                MRS.mergeMaps(transaction.attachment, data, { "version.ShufflingVerification": true });
                infoTable.find("tbody").append(MRS.createInfoTable(data));
                infoTable.show();
            } else if (MRS.isOfType(transaction, "ShufflingCancellation")) {
                data = { "type": $.t("shuffling_cancellation") };
                MRS.mergeMaps(transaction.attachment, data, { "version.ShufflingCancellation": true });
                infoTable.find("tbody").append(MRS.createInfoTable(data));
                infoTable.show();
            }
            if (!(transaction.type == 1 && transaction.subtype == 0)) {
                if (transaction.attachment) {
                    var transactionInfoOutputBottom = $("#transaction_info_output_bottom");
                    if (transaction.attachment.message) {
                        if (!transaction.attachment["version.Message"] && !transaction.attachment["version.PrunablePlainMessage"]) {
                            try {
                                message = converters.hexStringToString(transaction.attachment.message);
                            } catch (err) {
                                //legacy
                                if (transaction.attachment.message.indexOf("feff") === 0) {
                                    message = MRS.convertFromHex16(transaction.attachment.message);
                                } else {
                                    message = MRS.convertFromHex8(transaction.attachment.message);
                                }
                            }
                        } else {
                            if (MRS.isTextMessage(transaction)) {
                                message = String(transaction.attachment.message);
                            } else {
                                message = $.t("binary_data")
                            }
                        }

                        transactionInfoOutputBottom.append("<div style='padding-left:5px;'><label><i class='fa fa-unlock'></i> " + $.t("public_message") + "</label><div>" + MRS.escapeRespStr(message).nl2br() + "</div></div>");
                    }

                    if (transaction.attachment.encryptedMessage || (transaction.attachment.encryptToSelfMessage && MRS.account == transaction.sender)) {
                        var account;
                        if (transaction.attachment.message) {
                            transactionInfoOutputBottom.append("<div style='height:5px'></div>");
                        }
                        if (transaction.attachment.encryptedMessage) {
                            fieldsToDecrypt.encryptedMessage = $.t("encrypted_message");
                            account = MRS.getAccountForDecryption(transaction);
                        }
                        if (transaction.attachment.encryptToSelfMessage && MRS.account == transaction.sender) {
                            fieldsToDecrypt.encryptToSelfMessage = $.t("note_to_self");
                            if (!account) {
                                account = transaction.sender;
                            }
                        }
                        MRS.tryToDecrypt(transaction, fieldsToDecrypt, account, {
                            "formEl": "#transaction_info_output_bottom",
                            "outputEl": "#transaction_info_output_bottom"
                        });
                    }

                    transactionInfoOutputBottom.show();
                }
            }

            if (incorrect) {
                $.growl($.t("error_unknown_transaction_type"), {
                    "type": "danger"
                });

                MRS.fetchingModalData = false;
                return;
            }

            if (!async) {
                if (!isModalVisible) {
                    $("#transaction_info_modal").modal("show");
                }
                MRS.fetchingModalData = false;
            }
        } catch (e) {
            MRS.fetchingModalData = false;
            throw e;
        }
    };

    MRS.formatAssetOrder = function (asset, transaction, isModalVisible) {
        var data = {
            "type": (transaction.subtype == 2 ? $.t("ask_order_placement") : $.t("bid_order_placement")),
            "asset_formatted_html": MRS.getTransactionLink(transaction.attachment.asset),
            "asset_name": asset.name,
            "quantity": [transaction.attachment.quantityQNT, asset.decimals],
            "price_formatted_html": MRS.formatOrderPricePerWholeQNT(transaction.attachment.priceMQT, asset.decimals) + " " + MRS.constants.COIN_SYMBOL,
            "total_formatted_html": MRS.formatAmount(MRS.calculateOrderTotalMQT(transaction.attachment.quantityQNT, transaction.attachment.priceMQT)) + " " + MRS.constants.COIN_SYMBOL
        };
        data["sender"] = transaction.senderRS ? transaction.senderRS : transaction.sender;
        var rows = "";
        var params;
        if (transaction.subtype == 2) {
            params = {"askOrder": transaction.transaction};
        } else {
            params = {"bidOrder": transaction.transaction};
        }
        var transactionField = (transaction.subtype == 2 ? "bidOrder" : "askOrder");
        MRS.sendRequest("getOrderTrades", params, function (response) {
            var tradeQuantity = BigInteger.ZERO;
            var tradeTotal = BigInteger.ZERO;
            if (response.trades && response.trades.length > 0) {
                rows = "<table class='table table-striped'><thead><tr>" +
                "<th>" + $.t("Date") + "</th>" +
                "<th>" + $.t("Quantity") + "</th>" +
                "<th>" + $.t("Price") + "</th>" +
                "<th>" + $.t("Total") + "</th>" +
                "<tr></thead><tbody>";
                for (var i = 0; i < response.trades.length; i++) {
                    var trade = response.trades[i];
                    tradeQuantity = tradeQuantity.add(new BigInteger(trade.quantityQNT));
                    tradeTotal = tradeTotal.add(new BigInteger(trade.quantityQNT).multiply(new BigInteger(trade.priceMQT)));
                    rows += "<tr>" +
                    "<td>" + MRS.getTransactionLink(trade[transactionField], MRS.formatTimestamp(trade.timestamp)) + "</td>" +
                    "<td>" + MRS.formatQuantity(trade.quantityQNT, asset.decimals) + "</td>" +
                    "<td>" + MRS.calculateOrderPricePerWholeQNT(trade.priceMQT, asset.decimals) + "</td>" +
                    "<td>" + MRS.formatAmount(MRS.calculateOrderTotalMQT(trade.quantityQNT, trade.priceMQT)) +
                    "</td>" +
                    "</tr>";
                }
                rows += "</tbody></table>";
                data["trades_formatted_html"] = rows;
            } else {
                data["trades"] = $.t("no_matching_trade");
            }
            data["quantity_traded"] = [tradeQuantity, asset.decimals];
            data["total_traded"] = MRS.formatAmount(tradeTotal, false, true) + " " + MRS.constants.COIN_SYMBOL;
        }, { isAsync: false });

        var infoTable = $("#transaction_info_table");
        infoTable.find("tbody").append(MRS.createInfoTable(data));
        infoTable.show();
        if (!isModalVisible) {
            $("#transaction_info_modal").modal("show");
        }
        MRS.fetchingModalData = false;
    };

    function listPublicKeys(publicKeys) {
        var rows = "<table class='table table-striped'><tbody>";
        for (var i = 0; i < publicKeys.length; i++) {
            var recipientPublicKey = publicKeys[i];
            var recipientAccount = {accountRS: MRS.getAccountIdFromPublicKey(recipientPublicKey, true)};
            rows += "<tr>" +
                "<td>" + MRS.getAccountLink(recipientAccount, "account") + "<td>" +
                "</tr>";
        }
        rows += "</tbody></table>";
        return rows;
    }

    $(document).on("click", ".approve_transaction_btn", function (e) {
        e.preventDefault();
        var approveTransactionModal = $('#approve_transaction_modal');
        approveTransactionModal.find('.at_transaction_full_hash_display').text($(this).data("transaction"));
        approveTransactionModal.find('.at_transaction_timestamp').text(MRS.formatTimestamp($(this).data("timestamp")));
        $("#approve_transaction_button").data("transaction", $(this).data("transaction"));
        approveTransactionModal.find('#at_transaction_full_hash').val($(this).data("fullhash"));

        var mbFormatted = $(this).data("minBalanceFormatted");
        var minBalanceWarning = $('#at_min_balance_warning');
        if (mbFormatted && mbFormatted != "") {
            minBalanceWarning.find('.at_min_balance_amount').html(mbFormatted);
            minBalanceWarning.show();
        } else {
            minBalanceWarning.hide();
        }
        var revealSecretDiv = $("#at_revealed_secret_div");
        if ($(this).data("votingmodel") == MRS.constants.VOTING_MODELS.HASH) {
            revealSecretDiv.show();
        } else {
            revealSecretDiv.hide();
        }
    });

    $("#approve_transaction_button").on("click", function () {
        $('.tr_transaction_' + $(this).data("transaction") + ':visible .approve_transaction_btn').attr('disabled', true);
    });

    $("#transaction_info_modal").on("hide.bs.modal", function () {
        MRS.removeDecryptionForm($(this));
        $("#transaction_info_output_bottom, #transaction_info_output_top, #transaction_info_bottom").html("").hide();
    });

    return MRS;
}(MRS || {}, jQuery));
