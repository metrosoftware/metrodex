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
	MRS.userInfoModal = {
		"user": 0
	};

    var target = document.getElementById('user_info_modal_transactions_table');


	var body = $("body");
    body.on("click", ".show_account_modal_action, a[data-user].user_info", function(e) {
		e.preventDefault();
		var account = $(this).data("user");
        if ($(this).data("back") == "true") {
            MRS.modalStack.pop(); // The forward modal
            MRS.modalStack.pop(); // The current modal
        }
		MRS.showAccountModal(account);
	});

	MRS.showAccountModal = function(account) {
		if (MRS.fetchingModalData) {
			return;
		}

		if (typeof account == "object") {
			MRS.userInfoModal.user = account.account;
		} else {
			MRS.userInfoModal.user = account;
			MRS.fetchingModalData = true;
		}
        MRS.setBackLink();
		MRS.modalStack.push({ class: "show_account_modal_action", key: "user", value: account});

		$("#user_info_modal_account").html(MRS.getAccountFormatted(MRS.userInfoModal.user));
		var accountButton;
		if (MRS.userInfoModal.user in MRS.contacts) {
			accountButton = MRS.contacts[MRS.userInfoModal.user].name.escapeHTML();
			$("#user_info_modal_add_as_contact").hide();
		} else {
			accountButton = MRS.userInfoModal.user;
			$("#user_info_modal_add_as_contact").show();
		}

		$("#user_info_modal_actions").find("button").data("account", accountButton);

		if (MRS.fetchingModalData) {
            MRS.spinner.spin(target);
			MRS.sendRequest("getAccount", {
				"account": MRS.userInfoModal.user
            }, function(response) {
				MRS.processAccountModalData(response);
				MRS.fetchingModalData = false;
			});
		} else {
			MRS.spinner.spin(target);
			MRS.processAccountModalData(account);
		}
		$("#user_info_modal_transactions").show();
		MRS.userInfoModal.transactions();
	};

	MRS.processAccountModalData = function(account) {
		if (account.unconfirmedBalanceMQT == "0") {
			$("#user_info_modal_account_balance").html("0");
		} else {
			$("#user_info_modal_account_balance").html(MRS.formatAmount(account.unconfirmedBalanceMQT) + " " + MRS.constants.COIN_SYMBOL);
		}

		if (account.name) {
			$("#user_info_modal_account_name").html(MRS.escapeRespStr(account.name));
			$("#user_info_modal_account_name_container").show();
		} else {
			$("#user_info_modal_account_name_container").hide();
		}

		if (account.description) {
			$("#user_info_description").show();
			$("#user_info_modal_description").html(MRS.escapeRespStr(account.description).nl2br());
		} else {
			$("#user_info_description").hide();
		}
		var switchAccount = $("#user_info_switch_account");
        if (MRS.accountRS != account.accountRS) {
			switchAccount.html("<a class='btn btn-info btn-xs switch-account' data-account='" + account.accountRS + "'>" + $.t("switch_account") + "</a>");
			switchAccount.show();
		} else {
			switchAccount.hide();
		}

        var userInfoModal = $("#user_info_modal");
        if (!userInfoModal.data('bs.modal') || !userInfoModal.data('bs.modal').isShown) {
            userInfoModal.modal("show");
        }
        MRS.spinner.stop(target);
	};

	body.on("click", ".switch-account", function() {
		var account = $(this).data("account");
		MRS.closeModal($("#user_info_modal"));
		MRS.switchAccount(account);
	});

	var userInfoModal = $("#user_info_modal");
    userInfoModal.on("hidden.bs.modal", function() {
		$(this).find(".user_info_modal_content").hide();
		$(this).find(".user_info_modal_content table tbody").empty();
		$(this).find(".user_info_modal_content:not(.data-loading,.data-never-loading)").addClass("data-loading");
		$(this).find("ul.nav li.active").removeClass("active");
		$("#user_info_transactions").addClass("active");
		MRS.userInfoModal.user = 0;
	});

	userInfoModal.find("ul.nav li").click(function(e) {
		e.preventDefault();
		var tab = $(this).data("tab");
		$(this).siblings().removeClass("active");
		$(this).addClass("active");
		$(".user_info_modal_content").hide();

		var content = $("#user_info_modal_" + tab);
		content.show();
		if (content.hasClass("data-loading")) {
			MRS.userInfoModal[tab]();
		}
	});

    function getTransactionType(transaction) {
        var transactionType = $.t(MRS.transactionTypes[transaction.type].subTypes[transaction.subtype].i18nKeyTitle);
        if (transaction.type == MRS.subtype.AliasSell.type && transaction.subtype == MRS.subtype.AliasSell.subtype) {
            if (transaction.attachment.priceMQT == "0") {
                if (transaction.sender == transaction.recipient) {
                    transactionType = $.t("alias_sale_cancellation");
                } else {
                    transactionType = $.t("alias_transfer");
                }
            } else {
                transactionType = $.t("alias_sale");
            }
        }
        return transactionType;
    }

    MRS.userInfoModal.transactions = function() {
        MRS.sendRequest("getBlockchainTransactions", {
			"account": MRS.userInfoModal.user,
			"firstIndex": 0,
			"lastIndex": 100
		}, function(response) {
            var infoModalTransactionsTable = $("#user_info_modal_transactions_table");
			if (response.transactions && response.transactions.length) {
				var rows = "";
				var amountDecimals = MRS.getNumberOfDecimals(response.transactions, "amountMQT", function(val) {
					return MRS.formatAmount(val.amountMQT);
				});
				var feeDecimals = MRS.getNumberOfDecimals(response.transactions, "fee", function(val) {
					return MRS.formatAmount(val.fee);
				});
				for (var i = 0; i < response.transactions.length; i++) {
					var transaction = response.transactions[i];
                    var transactionType = getTransactionType(transaction);
                    var receiving;
					if (MRS.isRsAccount(String(MRS.userInfoModal.user))) {
						receiving = (transaction.recipientRS == MRS.userInfoModal.user);
					} else {
						receiving = (transaction.recipient == MRS.userInfoModal.user);
					}

					if (transaction.amountMQT) {
						transaction.amount = new BigInteger(transaction.amountMQT);
						transaction.fee = new BigInteger(transaction.feeMQT);
					}
					var account = (receiving ? "sender" : "recipient");
					rows += "<tr>" +
						"<td>" + MRS.getTransactionLink(transaction.transaction, MRS.formatTimestamp(transaction.timestamp)) + "</td>" +
						"<td>" + MRS.getTransactionIconHTML(transaction.type, transaction.subtype) + "&nbsp" + transactionType + "</td>" +
						"<td class='numeric'  " + (transaction.type == 0 && receiving ? " style='color:#006400;'" : (!receiving && transaction.amount > 0 ? " style='color:red'" : "")) + ">" + (!receiving && transaction.amount > 0 ? "-" : "")  + "" + MRS.formatAmount(transaction.amount, false, false, amountDecimals) + "</td>" +
						"<td class='numeric' " + (!receiving ? " style='color:red'" : "") + ">" + MRS.formatAmount(transaction.fee, false, false, feeDecimals) + "</td>" +
						"<td>" + MRS.getAccountLink(transaction, account) + "</td>" +
					"</tr>";
				}

				infoModalTransactionsTable.find("tbody").empty().append(rows);
				MRS.dataLoadFinished(infoModalTransactionsTable);
			} else {
				infoModalTransactionsTable.find("tbody").empty();
				MRS.dataLoadFinished(infoModalTransactionsTable);
			}
		});
	};

    MRS.userInfoModal.ledger = function() {
        MRS.sendRequest("getAccountLedger", {
            "account": MRS.userInfoModal.user,
            "includeHoldingInfo": true,
            "firstIndex": 0,
            "lastIndex": 100
        }, function (response) {
            var infoModalLedgerTable = $("#user_info_modal_ledger_table");
            if (response.entries && response.entries.length) {
                var rows = "";
				var decimalParams = MRS.getLedgerNumberOfDecimals(response.entries);
				for (var i = 0; i < response.entries.length; i++) {
                    var entry = response.entries[i];
                    rows += MRS.getLedgerEntryRow(entry, decimalParams);
                }
                infoModalLedgerTable.find("tbody").empty().append(rows);
                MRS.dataLoadFinished(infoModalLedgerTable);
            } else {
                infoModalLedgerTable.find("tbody").empty();
                MRS.dataLoadFinished(infoModalLedgerTable);
            }
        });
	};

	MRS.userInfoModal.aliases = function() {
		MRS.sendRequest("getAliases", {
			"account": MRS.userInfoModal.user,
			"firstIndex": 0,
			"lastIndex": 100
		}, function(response) {
			var rows = "";
			if (response.aliases && response.aliases.length) {
				var aliases = response.aliases;
				aliases.sort(function(a, b) {
					if (a.aliasName.toLowerCase() > b.aliasName.toLowerCase()) {
						return 1;
					} else if (a.aliasName.toLowerCase() < b.aliasName.toLowerCase()) {
						return -1;
					} else {
						return 0;
					}
				});
				for (var i = 0; i < aliases.length; i++) {
					var alias = aliases[i];
					rows += "<tr data-alias='" + MRS.escapeRespStr(String(alias.aliasName).toLowerCase()) + "'><td class='alias'>" + MRS.escapeRespStr(alias.aliasName) + "</td><td class='uri'>" + (alias.aliasURI.indexOf("http") === 0 ? "<a href='" + MRS.escapeRespStr(alias.aliasURI) + "' target='_blank'>" + MRS.escapeRespStr(alias.aliasURI) + "</a>" : MRS.escapeRespStr(alias.aliasURI)) + "</td></tr>";
				}
			}
            var infoModalAliasesTable = $("#user_info_modal_aliases_table");
            infoModalAliasesTable.find("tbody").empty().append(rows);
			MRS.dataLoadFinished(infoModalAliasesTable);
		});
	};

	MRS.userInfoModal.assets = function() {
		MRS.sendRequest("getAccount", {
			"account": MRS.userInfoModal.user,
            "includeAssets": true
        }, function(response) {
			if (response.assetBalances && response.assetBalances.length) {
				var assets = {};
				var nrAssets = 0;
				var ignoredAssets = 0; // Optimization to reduce number of getAsset calls
				for (var i = 0; i < response.assetBalances.length; i++) {
					if (response.assetBalances[i].balanceQNT == "0") {
						ignoredAssets++;
						if (nrAssets + ignoredAssets == response.assetBalances.length) {
							MRS.userInfoModal.addIssuedAssets(assets);
						}
						continue;
					}

					MRS.sendRequest("getAsset", {
						"asset": response.assetBalances[i].asset,
						"_extra": {
							"balanceQNT": response.assetBalances[i].balanceQNT
						}
					}, function(asset, input) {
						asset.asset = input.asset;
						asset.balanceQNT = input["_extra"].balanceQNT;
						assets[asset.asset] = asset;
						nrAssets++;
                        // This will work since eventually the condition below or in the previous
                        // if statement would be met
						//noinspection JSReferencingMutableVariableFromClosure
                        if (nrAssets + ignoredAssets == response.assetBalances.length) {
							MRS.userInfoModal.addIssuedAssets(assets);
						}
					});
				}
			} else {
				MRS.userInfoModal.addIssuedAssets({});
			}
		});
	};

	MRS.userInfoModal.trade_history = function() {
		MRS.sendRequest("getTrades", {
			"account": MRS.userInfoModal.user,
			"includeAssetInfo": true,
			"firstIndex": 0,
			"lastIndex": 100
		}, function(response) {
			var rows = "";
			var quantityDecimals = MRS.getNumberOfDecimals(response.trades, "quantityQNT", function(val) {
				return MRS.formatQuantity(val.quantityQNT, val.decimals);
			});
			var priceDecimals = MRS.getNumberOfDecimals(response.trades, "priceMQT", function(val) {
				return MRS.formatOrderPricePerWholeQNT(val.priceMQT, val.decimals);
			});
			var amountDecimals = MRS.getNumberOfDecimals(response.trades, "totalMQT", function(val) {
				return MRS.formatAmount(MRS.calculateOrderTotalMQT(val.quantityQNT, val.priceMQT));
			});
			if (response.trades && response.trades.length) {
				var trades = response.trades;
				for (var i = 0; i < trades.length; i++) {
					trades[i].priceMQT = new BigInteger(trades[i].priceMQT);
					trades[i].quantityQNT = new BigInteger(trades[i].quantityQNT);
					trades[i].totalMQT = new BigInteger(MRS.calculateOrderTotalMQT(trades[i].priceMQT, trades[i].quantityQNT));
					var type = (trades[i].buyerRS == MRS.userInfoModal.user ? "buy" : "sell");
					rows += "<tr><td><a href='#' data-goto-asset='" + MRS.escapeRespStr(trades[i].asset) + "'>" + MRS.escapeRespStr(trades[i].name) + "</a></td><td>" + MRS.formatTimestamp(trades[i].timestamp) + "</td><td style='color:" + (type == "buy" ? "green" : "red") + "'>" + $.t(type) + "</td><td class='numeric'>" + MRS.formatQuantity(trades[i].quantityQNT, trades[i].decimals, false, quantityDecimals) + "</td><td class='asset_price numeric'>" + MRS.formatOrderPricePerWholeQNT(trades[i].priceMQT, trades[i].decimals, priceDecimals) + "</td><td class='numeric' style='color:" + (type == "buy" ? "red" : "green") + "'>" + MRS.formatAmount(trades[i].totalMQT, false, false, amountDecimals) + "</td></tr>";
				}
			}
            var infoModalTradeHistoryTable = $("#user_info_modal_trade_history_table");
            infoModalTradeHistoryTable.find("tbody").empty().append(rows);
			MRS.dataLoadFinished(infoModalTradeHistoryTable);
		});
	};

	MRS.userInfoModal.addIssuedAssets = function(assets) {
		MRS.sendRequest("getAssetsByIssuer", {
			"account": MRS.userInfoModal.user
		}, function(response) {
			if (response.assets && response.assets[0] && response.assets[0].length) {
				$.each(response.assets[0], function(key, issuedAsset) {
					if (assets[issuedAsset.asset]) {
						assets[issuedAsset.asset].issued = true;
					} else {
						issuedAsset.balanceQNT = "0";
						issuedAsset.issued = true;
						assets[issuedAsset.asset] = issuedAsset;
					}
				});
				MRS.userInfoModal.assetsLoaded(assets);
			} else if (!$.isEmptyObject(assets)) {
				MRS.userInfoModal.assetsLoaded(assets);
			} else {
                var infoModalAssetsTable = $("#user_info_modal_assets_table");
                infoModalAssetsTable.find("tbody").empty();
				MRS.dataLoadFinished(infoModalAssetsTable);
			}
		});
	};

	MRS.userInfoModal.assetsLoaded = function(assets) {
		var assetArray = [];
		var rows = "";
		$.each(assets, function(key, asset) {
			assetArray.push(asset);
		});
		assetArray.sort(function(a, b) {
			if (a.issued && b.issued) {
				if (a.name.toLowerCase() > b.name.toLowerCase()) {
					return 1;
				} else if (a.name.toLowerCase() < b.name.toLowerCase()) {
					return -1;
				} else {
					return 0;
				}
			} else if (a.issued) {
				return -1;
			} else if (b.issued) {
				return 1;
			} else {
				if (a.name.toLowerCase() > b.name.toLowerCase()) {
					return 1;
				} else if (a.name.toLowerCase() < b.name.toLowerCase()) {
					return -1;
				} else {
					return 0;
				}
			}
		});
		var quantityDecimals = MRS.getNumberOfDecimals(assetArray, "balanceQNT", function(val) {
			return MRS.formatQuantity(val.balanceQNT, val.decimals);
		});
		var totalDecimals = MRS.getNumberOfDecimals(assetArray, "quantityQNT", function(val) {
			return MRS.formatQuantity(val.quantityQNT, val.decimals);
		});
		for (var i = 0; i < assetArray.length; i++) {
			var asset = assetArray[i];
			var percentageAsset = MRS.calculatePercentage(asset.balanceQNT, asset.quantityQNT);
			rows += "<tr" + (asset.issued ? " class='asset_owner'" : "") + "><td><a href='#' data-goto-asset='" + MRS.escapeRespStr(asset.asset) + "'" + (asset.issued ? " style='font-weight:bold'" : "") + ">" + MRS.escapeRespStr(asset.name) + "</a></td><td class='quantity numeric'>" + MRS.formatQuantity(asset.balanceQNT, asset.decimals, false, quantityDecimals) + "</td><td class='numeric'>" + MRS.formatQuantity(asset.quantityQNT, asset.decimals, false, totalDecimals) + "</td><td>" + percentageAsset + "%</td></tr>";
		}

        var infoModalAssetsTable = $("#user_info_modal_assets_table");
        infoModalAssetsTable.find("tbody").empty().append(rows);
		MRS.dataLoadFinished(infoModalAssetsTable);
	};

	return MRS;
}(MRS || {}, jQuery));