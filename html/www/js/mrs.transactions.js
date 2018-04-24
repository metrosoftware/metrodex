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
var MRS = (function(MRS, $, undefined) {

	MRS.lastTransactions = "";
	MRS.unconfirmedTransactions = [];
	MRS.unconfirmedTransactionIds = "";
	MRS.unconfirmedTransactionsChange = true;

	MRS.handleIncomingTransactions = function(transactions, confirmedTransactionIds) {
		var oldBlock = (confirmedTransactionIds === false); //we pass false instead of an [] in case there is no new block..

		if (typeof confirmedTransactionIds != "object") {
			confirmedTransactionIds = [];
		}

		if (confirmedTransactionIds.length) {
			MRS.lastTransactions = confirmedTransactionIds.toString();
		}

		if (confirmedTransactionIds.length || MRS.unconfirmedTransactionsChange) {
			transactions.sort(MRS.sortArray);
		}
		//Bug with popovers staying permanent when being open
		$('div.popover').hide();
		$('.td_transaction_phasing div.show_popover').popover('hide');

		//always refresh peers and unconfirmed transactions..
		if (MRS.currentPage == "peers") {
			MRS.incoming.peers();
		} else if (MRS.currentPage == "transactions"
            && $('#transactions_type_navi').find('li.active a').attr('data-transaction-type') == "unconfirmed") {
			MRS.incoming.transactions();
		} else {
			if (MRS.currentPage != 'messages' && (!oldBlock || MRS.unconfirmedTransactionsChange)) {
				if (MRS.incoming[MRS.currentPage]) {
					MRS.incoming[MRS.currentPage](transactions);
				}
			}
		}
		if (!oldBlock || MRS.unconfirmedTransactionsChange) {
			// always call incoming for messages to enable message notifications
			MRS.incoming['messages'](transactions);
			MRS.updateNotifications();
			MRS.setPhasingNotifications();
            MRS.setShufflingNotifications();
		}
	};

	MRS.getUnconfirmedTransactions = function(callback) {
		MRS.sendRequest("getUnconfirmedTransactions", {
			"account": MRS.account,
            "firstIndex": 0,
            "lastIndex": MRS.itemsPerPage
		}, function(response) {
			if (response.unconfirmedTransactions && response.unconfirmedTransactions.length) {
				var unconfirmedTransactions = [];
				var unconfirmedTransactionIds = [];

				response.unconfirmedTransactions.sort(function(x, y) {
					if (x.timestamp < y.timestamp) {
						return 1;
					} else if (x.timestamp > y.timestamp) {
						return -1;
					} else {
						return 0;
					}
				});

				for (var i = 0; i < response.unconfirmedTransactions.length; i++) {
					var unconfirmedTransaction = response.unconfirmedTransactions[i];
					unconfirmedTransaction.confirmed = false;
					unconfirmedTransaction.unconfirmed = true;
					unconfirmedTransaction.confirmations = "/";

					if (unconfirmedTransaction.attachment) {
						for (var key in unconfirmedTransaction.attachment) {
							if (!unconfirmedTransaction.attachment.hasOwnProperty(key)) {
								continue;
							}
							if (!unconfirmedTransaction.hasOwnProperty(key)) {
								unconfirmedTransaction[key] = unconfirmedTransaction.attachment[key];
							}
						}
					}
					unconfirmedTransactions.push(unconfirmedTransaction);
					unconfirmedTransactionIds.push(unconfirmedTransaction.transaction);
				}
				MRS.unconfirmedTransactions = unconfirmedTransactions;
				var unconfirmedTransactionIdString = unconfirmedTransactionIds.toString();
				if (unconfirmedTransactionIdString != MRS.unconfirmedTransactionIds) {
					MRS.unconfirmedTransactionsChange = true;
					MRS.setUnconfirmedNotifications();
					MRS.unconfirmedTransactionIds = unconfirmedTransactionIdString;
				} else {
					MRS.unconfirmedTransactionsChange = false;
				}

				if (callback) {
					callback(unconfirmedTransactions);
				}
			} else {
				MRS.unconfirmedTransactions = [];
				if (MRS.unconfirmedTransactionIds) {
					MRS.unconfirmedTransactionsChange = true;
					MRS.setUnconfirmedNotifications();
				} else {
					MRS.unconfirmedTransactionsChange = false;
				}

				MRS.unconfirmedTransactionIds = "";
				if (callback) {
					callback([]);
				}
			}
		});
	};

	MRS.getInitialTransactions = function() {
		MRS.sendRequest("getBlockchainTransactions", {
			"account": MRS.account,
			"firstIndex": 0,
			"lastIndex": 9
		}, function(response) {
			if (response.transactions && response.transactions.length) {
				var transactions = [];
				var transactionIds = [];

				for (var i = 0; i < response.transactions.length; i++) {
					var transaction = response.transactions[i];
					transaction.confirmed = true;
					transactions.push(transaction);
					transactionIds.push(transaction.transaction);
				}
				MRS.getUnconfirmedTransactions(function() {
					MRS.loadPage('dashboard');
				});
			} else {
				MRS.getUnconfirmedTransactions(function() {
					MRS.loadPage('dashboard');
				});
			}
		});
	};

	MRS.getNewTransactions = function() {
		//check if there is a new transaction..
		if (!MRS.blocks[0]) {
			return;
		}
        MRS.sendRequest("getBlockchainTransactions", {
			"account": MRS.account,
			"timestamp": Math.max(MRS.blocks[0].timestamp + 1, 0),
			"firstIndex": 0,
			"lastIndex": 0
		}, function(response) {
			//if there is, get latest 10 transactions
			if (response.transactions && response.transactions.length) {
				MRS.sendRequest("getBlockchainTransactions", {
					"account": MRS.account,
					"firstIndex": 0,
					"lastIndex": 9
				}, function(response) {
					if (response.transactions && response.transactions.length) {
						var transactionIds = [];

						$.each(response.transactions, function(key, transaction) {
							transactionIds.push(transaction.transaction);
							response.transactions[key].confirmed = true;
						});

						MRS.getUnconfirmedTransactions(function(unconfirmedTransactions) {
							MRS.handleIncomingTransactions(response.transactions.concat(unconfirmedTransactions), transactionIds);
						});
					} else {
						MRS.getUnconfirmedTransactions(function(unconfirmedTransactions) {
							MRS.handleIncomingTransactions(unconfirmedTransactions);
						});
					}
				});
			} else {
				MRS.getUnconfirmedTransactions(function(unconfirmedTransactions) {
					MRS.handleIncomingTransactions(unconfirmedTransactions);
				});
			}
		});
	};

	MRS.addUnconfirmedTransaction = function(transactionId, callback) {
		MRS.sendRequest("getTransaction", {
			"transaction": transactionId
		}, function(response) {
			if (!response.errorCode) {
				response.transaction = transactionId;
				response.confirmations = "/";
				response.confirmed = false;
				response.unconfirmed = true;

				if (response.attachment) {
					for (var key in response.attachment) {
                        if (!response.attachment.hasOwnProperty(key)) {
                            continue;
                        }
						if (!response.hasOwnProperty(key)) {
							response[key] = response.attachment[key];
						}
					}
				}
				var alreadyProcessed = false;
				try {
					var regex = new RegExp("(^|,)" + transactionId + "(,|$)");
					if (regex.exec(MRS.lastTransactions)) {
						alreadyProcessed = true;
					} else {
						$.each(MRS.unconfirmedTransactions, function(key, unconfirmedTransaction) {
							if (unconfirmedTransaction.transaction == transactionId) {
								alreadyProcessed = true;
								return false;
							}
						});
					}
				} catch (e) {
                    MRS.logConsole(e.message);
                }

				if (!alreadyProcessed) {
					MRS.unconfirmedTransactions.unshift(response);
				}
				if (callback) {
					callback(alreadyProcessed);
				}
				if (MRS.currentPage == 'transactions' || MRS.currentPage == 'dashboard') {
					$('div.popover').hide();
					$('.td_transaction_phasing div.show_popover').popover('hide');
					MRS.incoming[MRS.currentPage]();
				}

				MRS.getAccountInfo();
			} else if (callback) {
				callback(false);
			}
		});
	};

	MRS.sortArray = function(a, b) {
		return b.timestamp - a.timestamp;
	};

	MRS.getTransactionIconHTML = function(type, subtype) {
		var iconHTML = MRS.transactionTypes[type]['iconHTML'] + " " + MRS.transactionTypes[type]['subTypes'][subtype]['iconHTML'];
		var tooltip = $.t(MRS.transactionTypes[type].subTypes[subtype].i18nKeyTitle);
		return '<span title="' + tooltip + '" class="label label-primary" style="font-size:12px;">' + iconHTML + '</span>';
	};

	MRS.addPhasedTransactionHTML = function(t) {
		var $tr = $('.tr_transaction_' + t.transaction + ':visible');
		var $tdPhasing = $tr.find('.td_transaction_phasing');
		var $approveBtn = $tr.find('.td_transaction_actions .approve_transaction_btn');

		if (t.attachment && t.attachment["version.Phasing"] && t.attachment.phasingVotingModel != undefined) {
			MRS.sendRequest("getPhasingPoll", {
				"transaction": t.transaction,
				"countVotes": true
			}, function(responsePoll) {
				if (responsePoll.transaction) {
					MRS.sendRequest("getPhasingPollVote", {
						"transaction": t.transaction,
						"account": MRS.accountRS
					}, function(responseVote) {
						var attachment = t.attachment;
						var vm = attachment.phasingVotingModel;
						var minBalance = parseFloat(attachment.phasingMinBalance);
						var mbModel = attachment.phasingMinBalanceModel;

						if ($approveBtn) {
							var disabled = false;
							var unconfirmedTransactions = MRS.unconfirmedTransactions;
							if (unconfirmedTransactions) {
								for (var i = 0; i < unconfirmedTransactions.length; i++) {
									var ut = unconfirmedTransactions[i];
									if (ut.attachment && ut.attachment["version.PhasingVoteCasting"] && ut.attachment.transactionFullHashes && ut.attachment.transactionFullHashes.length > 0) {
										if (ut.attachment.transactionFullHashes[0] == t.fullHash) {
											disabled = true;
											$approveBtn.attr('disabled', true);
										}
									}
								}
							}
							if (!disabled) {
								if (responseVote.transaction) {
									$approveBtn.attr('disabled', true);
								} else {
									$approveBtn.attr('disabled', false);
								}
							}
						}

						if (!responsePoll.result) {
							responsePoll.result = 0;
						}

						var state = "";
						var color = "";
						var icon = "";
						var minBalanceFormatted = "";
                        var finished = attachment.phasingFinishHeight <= MRS.lastBlockHeight;
						var finishHeightFormatted = String(attachment.phasingFinishHeight);
						var percentageFormatted = attachment.phasingQuorum > 0 ? MRS.calculatePercentage(responsePoll.result, attachment.phasingQuorum, 0) + "%" : "";
						var percentageProgressBar = attachment.phasingQuorum > 0 ? Math.round(responsePoll.result * 100 / attachment.phasingQuorum) : 0;
						var progressBarWidth = Math.round(percentageProgressBar / 2);
                        var approvedFormatted;
						if (responsePoll.approved || attachment.phasingQuorum == 0) {
							approvedFormatted = "Yes";
						} else {
							approvedFormatted = "No";
						}

						if (finished) {
							if (responsePoll.approved) {
								state = "success";
								color = "#00a65a";
							} else {
								state = "danger";
								color = "#f56954";
							}
						} else {
							state = "warning";
							color = "#f39c12";
						}

						var $popoverTable = $("<table class='table table-striped'></table>");
						var $popoverTypeTR = $("<tr><td></td><td></td></tr>");
						var $popoverVotesTR = $("<tr><td>" + $.t('votes', 'Votes') + ":</td><td></td></tr>");
						var $popoverPercentageTR = $("<tr><td>" + $.t('percentage', 'Percentage') + ":</td><td></td></tr>");
						var $popoverFinishTR = $("<tr><td>" + $.t('finish_height', 'Finish Height') + ":</td><td></td></tr>");
						var $popoverApprovedTR = $("<tr><td>" + $.t('approved', 'Approved') + ":</td><td></td></tr>");

						$popoverTypeTR.appendTo($popoverTable);
						$popoverVotesTR.appendTo($popoverTable);
						$popoverPercentageTR.appendTo($popoverTable);
						$popoverFinishTR.appendTo($popoverTable);
						$popoverApprovedTR.appendTo($popoverTable);

						$popoverPercentageTR.find("td:last").html(percentageFormatted);
						$popoverFinishTR.find("td:last").html(finishHeightFormatted);
						$popoverApprovedTR.find("td:last").html(approvedFormatted);

						var template = '<div class="popover" style="min-width:260px;"><div class="arrow"></div><div class="popover-inner">';
						template += '<h3 class="popover-title"></h3><div class="popover-content"><p></p></div></div></div>';

						var popoverConfig = {
							"html": true,
							"trigger": "hover",
							"placement": "top",
							"template": template
						};

						if (vm == -1) {
							icon = '<i class="fa ion-load-a"></i>';
						}
						if (vm == 0) {
							icon = '<i class="fa fa-group"></i>';
						}
						if (vm == 1) {
							icon = '<i class="fa fa-money"></i>';
						}
						if (vm == 2) {
							icon = '<i class="fa fa-signal"></i>';
						}
						if (vm == 3) {
							icon = '<i class="fa fa-bank"></i>';
						}
						if (vm == 4) {
							icon = '<i class="fa fa-thumbs-up"></i>';
						}
						if (vm == 5) {
							icon = '<i class="fa fa-question"></i>';
						}
						var phasingDiv = "";
						phasingDiv += '<div class="show_popover" style="display:inline-block;min-width:94px;text-align:left;border:1px solid #e2e2e2;background-color:#fff;padding:3px;" ';
	 				 	phasingDiv += 'data-toggle="popover" data-container="body">';
						phasingDiv += "<div class='label label-" + state + "' style='display:inline-block;margin-right:5px;'>" + icon + "</div>";

						if (vm == -1) {
							phasingDiv += '<span style="color:' + color + '">' + $.t("none") + '</span>';
						} else if (vm == 0) {
							phasingDiv += '<span style="color:' + color + '">' + String(responsePoll.result) + '</span> / <span>' + String(attachment.phasingQuorum) + '</span>';
						} else {
							phasingDiv += '<div class="progress" style="display:inline-block;height:10px;width: 50px;">';
	    					phasingDiv += '<div class="progress-bar progress-bar-' + state + '" role="progressbar" aria-valuenow="' + percentageProgressBar + '" ';
	    					phasingDiv += 'aria-valuemin="0" aria-valuemax="100" style="height:10px;width: ' + progressBarWidth + 'px;">';
	      					phasingDiv += '<span class="sr-only">' + percentageProgressBar + '% Complete</span>';
	    					phasingDiv += '</div>';
	  						phasingDiv += '</div> ';
	  					}
						phasingDiv += "</div>";
						var $phasingDiv = $(phasingDiv);
						popoverConfig["content"] = $popoverTable;
						$phasingDiv.popover(popoverConfig);
						$phasingDiv.appendTo($tdPhasing);
                        var votesFormatted;
						if (vm == 0) {
							$popoverTypeTR.find("td:first").html($.t('accounts', 'Accounts') + ":");
							$popoverTypeTR.find("td:last").html(String(attachment.phasingWhitelist ? attachment.phasingWhitelist.length : ""));
							votesFormatted = String(responsePoll.result) + " / " + String(attachment.phasingQuorum);
							$popoverVotesTR.find("td:last").html(votesFormatted);
						}
						if (vm == 1) {
							$popoverTypeTR.find("td:first").html($.t('accounts', 'Accounts') + ":");
							$popoverTypeTR.find("td:last").html(String(attachment.phasingWhitelist ? attachment.phasingWhitelist.length : ""));
							votesFormatted = MRS.convertToMTR(responsePoll.result) + " / " + MRS.convertToMTR(attachment.phasingQuorum) + " " + MRS.constants.COIN_SYMBOL;
							$popoverVotesTR.find("td:last").html(votesFormatted);
						}
						if (mbModel == 1) {
							if (minBalance > 0) {
								minBalanceFormatted = MRS.convertToMTR(minBalance) + " " + MRS.constants.COIN_SYMBOL;
								$approveBtn.data('minBalanceFormatted', minBalanceFormatted.escapeHTML());
							}
						}
						if (vm == 2 || mbModel == 2) {
							MRS.sendRequest("getAsset", {
								"asset": attachment.phasingHolding
							}, function(phResponse) {
								if (phResponse && phResponse.asset) {
									if (vm == 2) {
										$popoverTypeTR.find("td:first").html($.t('asset', 'Asset') + ":");
										$popoverTypeTR.find("td:last").html(String(phResponse.name));
										var votesFormatted = MRS.convertToQNTf(responsePoll.result, phResponse.decimals) + " / ";
										votesFormatted += MRS.convertToQNTf(attachment.phasingQuorum, phResponse.decimals) + " QNT";
										$popoverVotesTR.find("td:last").html(votesFormatted);
									}
									if (mbModel == 2) {
										if (minBalance > 0) {
											minBalanceFormatted = MRS.convertToQNTf(minBalance, phResponse.decimals) + " QNT (" + phResponse.name + ")";
											$approveBtn.data('minBalanceFormatted', minBalanceFormatted.escapeHTML());
										}
									}
								}
							}, { isAsync: false });
						}
					});
				} else {
					$tdPhasing.html("&nbsp;");
				}
			}, { isAsync: false });
		} else {
			$tdPhasing.html("&nbsp;");
		}
	};

	MRS.addPhasingInfoToTransactionRows = function(transactions) {
		for (var i = 0; i < transactions.length; i++) {
			var transaction = transactions[i];
			MRS.addPhasedTransactionHTML(transaction);
		}
	};

    MRS.getTransactionRowHTML = function(t, actions, decimals, isScheduled) {
		var transactionType = $.t(MRS.transactionTypes[t.type]['subTypes'][t.subtype]['i18nKeyTitle']);

		if (t.type == 1 && t.subtype == 6 && t.attachment.priceMQT == "0") {
			if (t.sender == MRS.account && t.recipient == MRS.account) {
				transactionType = $.t("alias_sale_cancellation");
			} else {
				transactionType = $.t("alias_transfer");
			}
		}

		var amount = "";
		var sign = 0;
		var fee = new BigInteger(t.feeMQT);
		var feeColor = "";
		var receiving = t.recipient == MRS.account && !(t.sender == MRS.account);
		if (receiving) {
			if (t.amountMQT != "0") {
				amount = new BigInteger(t.amountMQT);
				sign = 1;
			}
			feeColor = "color:black;";
		} else {
			if (t.sender != t.recipient) {
				if (t.amountMQT != "0") {
					amount = new BigInteger(t.amountMQT);
					amount = amount.negate();
					sign = -1;
				}
			} else {
				if (t.amountMQT != "0") {
					amount = new BigInteger(t.amountMQT); // send to myself
				}
			}
			feeColor = "color:red;";
		}
		var formattedAmount = "";
		if (amount != "") {
			formattedAmount = MRS.formatAmount(amount, false, false, decimals.amount);
		}
		var formattedFee = MRS.formatAmount(fee, false, false, decimals.fee);
		var amountColor = (sign == 1 ? "color:green;" : (sign == -1 ? "color:red;" : "color:black;"));
		var hasMessage = false;

		if (t.attachment) {
			if (t.attachment.encryptedMessage || t.attachment.message) {
				hasMessage = true;
			} else if (t.sender == MRS.account && t.attachment.encryptToSelfMessage) {
				hasMessage = true;
			}
		}
		var html = "";
		html += "<tr class='tr_transaction_" + t.transaction + "'>";
		html += "<td style='vertical-align:middle;'>";
		if (isScheduled) {
            html += "<a href='#' onclick='MRS.showTransactionModal(" + JSON.stringify(t) + ");'>" + MRS.formatTimestamp(t.timestamp) + "</a>";
		}  else {
            html += "<a class='show_transaction_modal_action' href='#' data-timestamp='" + MRS.escapeRespStr(t.timestamp) + "' ";
            html += "data-transaction='" + MRS.escapeRespStr(t.transaction) + "'>";
            html += MRS.formatTimestamp(t.timestamp) + "</a>";
		}
  		html += "</td>";
  		html += "<td style='vertical-align:middle;text-align:center;'>" + (hasMessage ? "&nbsp; <i class='fa fa-envelope-o'></i>&nbsp;" : "&nbsp;") + "</td>";
		html += '<td style="vertical-align:middle;">';
		html += MRS.getTransactionIconHTML(t.type, t.subtype) + '&nbsp; ';
		html += '<span style="font-size:11px;display:inline-block;margin-top:5px;">' + transactionType + '</span>';
		html += '</td>';
        html += "<td style='vertical-align:middle;text-align:right;" + amountColor + "'>" + formattedAmount + "</td>";
        html += "<td style='vertical-align:middle;text-align:right;" + feeColor + "'>" + formattedFee + "</td>";
		html += "<td style='vertical-align:middle;'>" + ((MRS.getAccountLink(t, "sender") == "/" && t.type == 2) ? "Asset Exchange" : MRS.getAccountLink(t, "sender")) + " ";
		html += "<i class='fa fa-arrow-circle-right' style='color:#777;'></i> " + ((MRS.getAccountLink(t, "recipient") == "/" && t.type == 2) ? "Asset Exchange" : MRS.getAccountLink(t, "recipient")) + "</td>";
		if (!isScheduled) {
            html += "<td class='td_transaction_phasing' style='min-width:100px;vertical-align:middle;text-align:center;'></td>";
            html += "<td style='vertical-align:middle;text-align:center;'>" + (t.confirmed ? MRS.getBlockLink(t.height, null, true) : "-") + "</td>";
            html += "<td class='confirmations' style='vertical-align:middle;text-align:center;font-size:12px;'>";
            html += "<span class='show_popover' data-content='" + (t.confirmed ? MRS.formatAmount(t.confirmations) + " " + $.t("confirmations") : $.t("unconfirmed_transaction")) + "' ";
            html += "data-container='body' data-placement='left'>";
            html += (!t.confirmed ? "-" : (t.confirmations > 1440 ? (MRS.formatAmount('144000000000') + "+") : MRS.formatAmount(t.confirmations))) + "</span></td>";
        }
		if (actions && actions.length != undefined) {
			html += '<td class="td_transaction_actions" style="vertical-align:middle;text-align:right;">';
			if (actions.indexOf('approve') > -1) {
                html += "<a class='btn btn-xs btn-default approve_transaction_btn' href='#' data-toggle='modal' data-target='#approve_transaction_modal' ";
				html += "data-transaction='" + MRS.escapeRespStr(t.transaction) + "' data-fullhash='" + MRS.escapeRespStr(t.fullHash) + "' ";
				html += "data-timestamp='" + t.timestamp + "' " + "data-votingmodel='" + t.attachment.phasingVotingModel + "' ";
				html += "data-fee='1' data-min-balance-formatted=''>" + $.t('approve') + "</a>";
			}
			if (actions.indexOf('delete') > -1) {
                html += "<a class='btn btn-xs btn-default' href='#' data-toggle='modal' data-target='#delete_scheduled_transaction_modal' ";
				html += "data-transaction='" + MRS.escapeRespStr(t.transaction) + "'>" + $.t("delete") + "</a>";
			}
			html += "</td>";
		}
		html += "</tr>";
		return html;
	};

    MRS.getLedgerEntryRow = function(entry, decimalParams) {
        var linkClass;
        var dataToken;
        if (entry.isTransactionEvent) {
            linkClass = "show_transaction_modal_action";
            dataToken = "data-transaction='" + MRS.escapeRespStr(entry.event) + "'";
        } else {
            linkClass = "show_block_modal_action";
            dataToken = "data-id='1' data-block='" + MRS.escapeRespStr(entry.event)+ "'";
        }
        var change = entry.change;
        var balance = entry.balance;
        var balanceType = "metro";
        var balanceEntity = "MTR";
        var holdingIcon = "";
        if (change < 0) {
            change = String(change).substring(1);
        }
        if (/ASSET_BALANCE/i.test(entry.holdingType)) {
            MRS.sendRequest("getAsset", {"asset": entry.holding}, function (response) {
                balanceType = "asset";
                balanceEntity = response.name;
                change = MRS.formatQuantity(change, response.decimals, false, decimalParams.holdingChangeDecimals);
                balance = MRS.formatQuantity(balance, response.decimals, false, decimalParams.holdingBalanceDecimals);
                holdingIcon = "<i class='fa fa-signal'></i> ";
            }, { isAsync: false });
        } else {
            change = MRS.formatAmount(change, false, false, decimalParams.changeDecimals);
            balance = MRS.formatAmount(balance, false, false, decimalParams.balanceDecimals);
        }
        var sign = "";
		var color = "";
        if (entry.change > 0) {
			color = "color:green;";
		} else if (entry.change < 0) {
			color = "color:red;";
			sign = "-";
        }
        var eventType = MRS.escapeRespStr(entry.eventType);
        if (eventType.indexOf("ASSET") == 0) {
            eventType = eventType.substring(eventType.indexOf("_") + 1);
        }
        eventType = $.t(eventType.toLowerCase());
        var html = "";
		html += "<tr>";
		html += "<td style='vertical-align:middle;'>";
  		html += "<a class='show_ledger_modal_action' href='#' data-entry='" + MRS.escapeRespStr(entry.ledgerId) +"'";
        html += "data-change='" + (entry.change < 0 ? ("-" + change) : change) + "' data-balance='" + balance + "'>";
  		html += MRS.formatTimestamp(entry.timestamp) + "</a>";
  		html += "</td>";
		html += '<td style="vertical-align:middle;">';
        html += '<span style="font-size:11px;display:inline-block;margin-top:5px;">' + eventType + '</span>';
        html += "<a class='" + linkClass + "' href='#' data-timestamp='" + MRS.escapeRespStr(entry.timestamp) + "' " + dataToken + ">";
        html += " <i class='fa fa-info'></i></a>";
		html += '</td>';
		if (balanceType == "metro") {
            html += "<td style='vertical-align:middle;" + color + "' class='numeric'>" + sign + change + "</td>";
            html += "<td style='vertical-align:middle;' class='numeric'>" + balance + "</td>";
            html += "<td></td>";
            html += "<td></td>";
            html += "<td></td>";
        } else {
            html += "<td></td>";
            html += "<td></td>";
            html += "<td>" + holdingIcon + balanceEntity + "</td>";
            html += "<td style='vertical-align:middle;" + color + "' class='numeric'>" + sign + change + "</td>";
            html += "<td style='vertical-align:middle;' class='numeric'>" + balance + "</td>";
        }
		return html;
	};

	MRS.buildTransactionsTypeNavi = function() {
		var html = '';
		html += '<li role="presentation" class="active"><a href="#" data-transaction-type="" ';
		html += 'data-toggle="popover" data-placement="top" data-content="All" data-container="body" data-i18n="[data-content]all">';
		html += '<span data-i18n="all">All</span></a></li>';
        var typeNavi = $('#transactions_type_navi');
        typeNavi.append(html);

		$.each(MRS.transactionTypes, function(typeIndex, typeDict) {
			var titleString = $.t(typeDict.i18nKeyTitle);
			html = '<li role="presentation"><a href="#" data-transaction-type="' + typeIndex + '" ';
			html += 'data-toggle="popover" data-placement="top" data-content="' + titleString + '" data-container="body">';
			html += typeDict.iconHTML + '</a></li>';
			$('#transactions_type_navi').append(html);
		});

		html  = '<li role="presentation"><a href="#" data-transaction-type="unconfirmed" ';
		html += 'data-toggle="popover" data-placement="top" data-content="Unconfirmed (Account)" data-container="body" data-i18n="[data-content]unconfirmed_account">';
		html += '<i class="fa fa-circle-o"></i>&nbsp; <span data-i18n="unconfirmed">Unconfirmed</span></a></li>';
		typeNavi.append(html);

		html  = '<li role="presentation"><a href="#" data-transaction-type="phasing" ';
		html += 'data-toggle="popover" data-placement="top" data-content="Phasing (Pending)" data-container="body" data-i18n="[data-content]phasing_pending">';
		html += '<i class="fa fa-gavel"></i>&nbsp; <span data-i18n="phasing">Phasing</span></a></li>';
		typeNavi.append(html);

		html  = '<li role="presentation"><a href="#" data-transaction-type="all_unconfirmed" ';
		html += 'data-toggle="popover" data-placement="top" data-content="Unconfirmed (Everyone)" data-container="body" data-i18n="[data-content]unconfirmed_everyone">';
		html += '<i class="fa fa-circle-o"></i>&nbsp; <span data-i18n="all_unconfirmed">Unconfirmed (Everyone)</span></a></li>';
		typeNavi.append(html);

        typeNavi.find('a[data-toggle="popover"]').popover({
			"trigger": "hover"
		});
        typeNavi.find("[data-i18n]").i18n();
	};

	MRS.buildTransactionsSubTypeNavi = function() {
        var subtypeNavi = $('#transactions_sub_type_navi');
        subtypeNavi.empty();
		var html  = '<li role="presentation" class="active"><a href="#" data-transaction-sub-type="">';
		html += '<span>' + $.t("all_types") + '</span></a></li>';
		subtypeNavi.append(html);

		var typeIndex = $('#transactions_type_navi').find('li.active a').attr('data-transaction-type');
		if (typeIndex && typeIndex != "unconfirmed" && typeIndex != "all_unconfirmed" && typeIndex != "phasing") {
			var typeDict = MRS.transactionTypes[typeIndex];
			$.each(typeDict["subTypes"], function(subTypeIndex, subTypeDict) {
				var subTitleString = $.t(subTypeDict.i18nKeyTitle);
				html = '<li role="presentation"><a href="#" data-transaction-sub-type="' + subTypeIndex + '">';
				html += subTypeDict.iconHTML + ' ' + subTitleString + '</a></li>';
				$('#transactions_sub_type_navi').append(html);
			});
		}
	};

    MRS.displayUnconfirmedTransactions = function(account) {
        var params = {
            "firstIndex": MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage,
            "lastIndex": MRS.pageNumber * MRS.itemsPerPage
        };
        if (account != "") {
            params["account"] = account;
        }
        MRS.sendRequest("getUnconfirmedTransactions", params, function(response) {
			var rows = "";
			if (response.unconfirmedTransactions && response.unconfirmedTransactions.length) {
				var decimals = MRS.getTransactionsAmountDecimals(response.unconfirmedTransactions);
				for (var i = 0; i < response.unconfirmedTransactions.length; i++) {
                    rows += MRS.getTransactionRowHTML(response.unconfirmedTransactions[i], false, decimals);
				}
			}
			MRS.dataLoaded(rows);
		});
	};

	MRS.displayPhasedTransactions = function() {
		var params = {
			"account": MRS.account,
			"firstIndex": MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage,
			"lastIndex": MRS.pageNumber * MRS.itemsPerPage
		};
		MRS.sendRequest("getAccountPhasedTransactions", params, function(response) {
			var rows = "";
			if (response.transactions && response.transactions.length) {
				var decimals = MRS.getTransactionsAmountDecimals(response.transactions);
				for (var i = 0; i < response.transactions.length; i++) {
					var t = response.transactions[i];
					t.confirmed = true;
					rows += MRS.getTransactionRowHTML(t, false, decimals);
				}
				MRS.dataLoaded(rows);
				MRS.addPhasingInfoToTransactionRows(response.transactions);
			} else {
				MRS.dataLoaded(rows);
			}
		});
	};

    MRS.pages.dashboard = function() {
        var rows = "";
        var params = {
            "account": MRS.account,
            "firstIndex": 0,
            "lastIndex": 9,
			"excludeCoinbase": true
        };
        var unconfirmedTransactions = MRS.unconfirmedTransactions;
		var decimals = MRS.getTransactionsAmountDecimals(unconfirmedTransactions);
        if (unconfirmedTransactions) {
            for (var i = 0; i < unconfirmedTransactions.length; i++) {
                rows += MRS.getTransactionRowHTML(unconfirmedTransactions[i], false, decimals);
            }
        }

        MRS.sendRequest("getBlockchainTransactions+", params, function(response) {
            if (response.transactions && response.transactions.length) {
				var decimals = MRS.getTransactionsAmountDecimals(response.transactions);
                for (var i = 0; i < response.transactions.length; i++) {
                    var transaction = response.transactions[i];
                    transaction.confirmed = true;
                    rows += MRS.getTransactionRowHTML(transaction, false, decimals);
                }

                MRS.dataLoaded(rows);
                MRS.addPhasingInfoToTransactionRows(response.transactions);
            } else {
                MRS.dataLoaded(rows);
            }
        });
    };

	MRS.incoming.dashboard = function() {
		MRS.loadPage("dashboard");
	};

	var isHoldingEntry = function (entry){
		return /ASSET_BALANCE/i.test(entry.holdingType);
	};

    MRS.getLedgerNumberOfDecimals = function (entries){
		var decimalParams = {};
		decimalParams.changeDecimals = MRS.getNumberOfDecimals(entries, "change", function(entry) {
			if (isHoldingEntry(entry)) {
				return "";
			}
			return MRS.formatAmount(entry.change);
		});
		decimalParams.holdingChangeDecimals = MRS.getNumberOfDecimals(entries, "change", function(entry) {
			if (isHoldingEntry(entry)) {
				return MRS.formatQuantity(entry.change, entry.holdingInfo.decimals);
			}
			return "";
		});
		decimalParams.balanceDecimals = MRS.getNumberOfDecimals(entries, "balance", function(entry) {
			if (isHoldingEntry(entry)) {
				return "";
			}
			return MRS.formatAmount(entry.balance);
		});
		decimalParams.holdingBalanceDecimals = MRS.getNumberOfDecimals(entries, "balance", function(entry) {
			if (isHoldingEntry(entry)) {
				return MRS.formatQuantity(entry.balance, entry.holdingInfo.decimals);
			}
			return "";
		});
		return decimalParams;
	};

    MRS.pages.ledger = function() {
		var rows = "";
        var params = {
            "account": MRS.account,
            "includeHoldingInfo": true,
            "firstIndex": MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage,
            "lastIndex": MRS.pageNumber * MRS.itemsPerPage
        };

        MRS.sendRequest("getAccountLedger+", params, function(response) {
            if (response.entries && response.entries.length) {
                if (response.entries.length > MRS.itemsPerPage) {
                    MRS.hasMorePages = true;
                    response.entries.pop();
                }
				var decimalParams = MRS.getLedgerNumberOfDecimals(response.entries);
                for (var i = 0; i < response.entries.length; i++) {
                    var entry = response.entries[i];
                    rows += MRS.getLedgerEntryRow(entry, decimalParams);
                }
            }
            MRS.dataLoaded(rows);
			if (MRS.ledgerTrimKeep > 0) {
				var ledgerMessage = $("#account_ledger_message");
                ledgerMessage.text($.t("account_ledger_message", { blocks: MRS.ledgerTrimKeep }));
				ledgerMessage.show();
			}
        });
	};

	MRS.pages.transactions = function(callback, subpage) {
        var typeNavi = $('#transactions_type_navi');
        if (typeNavi.children().length == 0) {
			MRS.buildTransactionsTypeNavi();
			MRS.buildTransactionsSubTypeNavi();
		}

		if (subpage) {
			typeNavi.find('li a[data-transaction-type="' + subpage + '"]').click();
			return;
		}

		var selectedType = typeNavi.find('li.active a').attr('data-transaction-type');
		var selectedSubType = $('#transactions_sub_type_navi').find('li.active a').attr('data-transaction-sub-type');
		if (!selectedSubType) {
			selectedSubType = "";
		}
		if (selectedType == "unconfirmed") {
			MRS.displayUnconfirmedTransactions(MRS.account);
			return;
		}
		if (selectedType == "phasing") {
			MRS.displayPhasedTransactions();
			return;
		}
		if (selectedType == "all_unconfirmed") {
			MRS.displayUnconfirmedTransactions("");
			return;
		}

		var rows = "";
		var params = {
			"account": MRS.account,
			"firstIndex": MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage,
			"lastIndex": MRS.pageNumber * MRS.itemsPerPage
		};
        var unconfirmedTransactions;
		if (selectedType) {
			params.type = selectedType;
			params.subtype = selectedSubType;
			unconfirmedTransactions = MRS.getUnconfirmedTransactionsFromCache(params.type, (params.subtype ? params.subtype : []));
		} else {
			unconfirmedTransactions = MRS.unconfirmedTransactions;
		}
		var decimals = MRS.getTransactionsAmountDecimals(unconfirmedTransactions);
		if (unconfirmedTransactions) {
			for (var i = 0; i < unconfirmedTransactions.length; i++) {
				rows += MRS.getTransactionRowHTML(unconfirmedTransactions[i], false, decimals);
			}
		}

		MRS.sendRequest("getBlockchainTransactions+", params, function(response) {
			if (response.transactions && response.transactions.length) {
				if (response.transactions.length > MRS.itemsPerPage) {
					MRS.hasMorePages = true;
					response.transactions.pop();
				}
				var decimals = MRS.getTransactionsAmountDecimals(response.transactions);
				for (var i = 0; i < response.transactions.length; i++) {
					var transaction = response.transactions[i];
					transaction.confirmed = true;
					rows += MRS.getTransactionRowHTML(transaction, false, decimals);
				}

				MRS.dataLoaded(rows);
				MRS.addPhasingInfoToTransactionRows(response.transactions);
			} else {
				MRS.dataLoaded(rows);
			}
		});
	};

	MRS.updateApprovalRequests = function() {
		var params = {
			"account": MRS.account,
			"firstIndex": 0,
			"lastIndex": 20
		};
		MRS.sendRequest("getVoterPhasedTransactions", params, function(response) {
			var $badge = $('#dashboard_link').find('.sm_treeview_submenu a[data-page="approval_requests_account"] span.badge');
			if (response.transactions && response.transactions.length) {
				if (response.transactions.length == 0) {
					$badge.hide();
				} else {
                    var length;
					if (response.transactions.length == 21) {
						length = "20+";
					} else {
						length = String(response.transactions.length);
					}
					$badge.text(length);
					$badge.show();
				}
			} else {
				$badge.hide();
			}
		});
		if (MRS.currentPage == 'approval_requests_account') {
			MRS.loadPage(MRS.currentPage);
		}
	};

	MRS.pages.approval_requests_account = function() {
		var params = {
			"account": MRS.account,
			"firstIndex": MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage,
			"lastIndex": MRS.pageNumber * MRS.itemsPerPage
		};
		MRS.sendRequest("getVoterPhasedTransactions", params, function(response) {
			var rows = "";

			if (response.transactions && response.transactions.length) {
				if (response.transactions.length > MRS.itemsPerPage) {
					MRS.hasMorePages = true;
					response.transactions.pop();
				}
				var decimals = MRS.getTransactionsAmountDecimals(response.transactions);
				for (var i = 0; i < response.transactions.length; i++) {
					var t = response.transactions[i];
					t.confirmed = true;
					rows += MRS.getTransactionRowHTML(t, ['approve'], decimals);
				}
			}
			MRS.dataLoaded(rows);
			MRS.addPhasingInfoToTransactionRows(response.transactions);
		});
	};

    MRS.pages.scheduled_transactions = function(callback, subpage) {
        MRS.sendRequest("getScheduledTransactions+", {
        	account: MRS.accountRS,
			adminPassword: MRS.getAdminPassword()
		}, function(response) {
            var errorMessage = $("#scheduled_transactions_error_message");
            if (response.errorCode) {
        		errorMessage.text(MRS.unescapeRespStr(response.errorDescription));
        		errorMessage.show();
			} else {
                errorMessage.hide();
                errorMessage.text("");
			}
			var rows = "";
            if (response.scheduledTransactions && response.scheduledTransactions.length) {
                if (response.scheduledTransactions.length > MRS.itemsPerPage) {
                    MRS.hasMorePages = true;
                    response.scheduledTransactions.pop();
                }
                var decimals = MRS.getTransactionsAmountDecimals(response.scheduledTransactions);
                for (var i = 0; i < response.scheduledTransactions.length; i++) {
                    var transaction = response.scheduledTransactions[i];
					rows += MRS.getTransactionRowHTML(transaction, ["delete"], decimals, true);
                }
            }
            MRS.dataLoaded(rows);
        });
    };

    $("#delete_scheduled_transaction_modal").on("show.bs.modal", function(e) {
        var $invoker = $(e.relatedTarget);
        var transaction = $invoker.data("transaction");
		$("#delete_scheduled_transaction_id").val(transaction);
    });

    MRS.forms.deleteScheduledTransaction = function($modal) {
    	var data = MRS.getFormData($modal.find("form:first"));
    	data.adminPassword = MRS.getAdminPassword();
		return { data: data };
    };

    MRS.forms.deleteScheduledTransactionComplete = function() {
    	MRS.goToPage("scheduled_transactions");
	};

    MRS.incoming.transactions = function() {
		MRS.loadPage("transactions");
	};

	MRS.setup.transactions = function() {
		var sidebarId = 'dashboard_link';
		var options = {
			"id": sidebarId,
			"titleHTML": '<i class="fa fa-dashboard"></i> <span data-i18n="dashboard">Dashboard</span>',
			"page": 'dashboard',
			"desiredPosition": 10
		};
		MRS.addTreeviewSidebarMenuItem(options);
		options = {
			"titleHTML": '<span data-i18n="dashboard">Dashboard</span>',
			"type": 'PAGE',
			"page": 'dashboard'
		};
		MRS.appendMenuItemToTSMenuItem(sidebarId, options);
		options = {
			"titleHTML": '<span data-i18n="account_ledger">Account Ledger</span>',
			"type": 'PAGE',
			"page": 'ledger'
		};
		MRS.appendMenuItemToTSMenuItem(sidebarId, options);
		options = {
			"titleHTML": '<span data-i18n="account_properties">Account Properties</span>',
			"type": 'PAGE',
			"page": 'account_properties'
		};
		MRS.appendMenuItemToTSMenuItem(sidebarId, options);
		options = {
			"titleHTML": '<span data-i18n="my_transactions">My Transactions</span>',
			"type": 'PAGE',
			"page": 'transactions'
		};
		MRS.appendMenuItemToTSMenuItem(sidebarId, options);
		options = {
			"titleHTML": '<span data-i18n="approval_requests">Approval Requests</span>',
			"type": 'PAGE',
			"page": 'approval_requests_account'
		};
		MRS.appendMenuItemToTSMenuItem(sidebarId, options);
	};

	$(document).on("click", "#transactions_type_navi li a", function(e) {
		e.preventDefault();
		$('#transactions_type_navi').find('li.active').removeClass('active');
  		$(this).parent('li').addClass('active');
  		MRS.buildTransactionsSubTypeNavi();
  		MRS.pageNumber = 1;
		MRS.loadPage("transactions");
	});

	$(document).on("click", "#transactions_sub_type_navi li a", function(e) {
		e.preventDefault();
		$('#transactions_sub_type_navi').find('li.active').removeClass('active');
  		$(this).parent('li').addClass('active');
  		MRS.pageNumber = 1;
		MRS.loadPage("transactions");
	});

	$(document).on("click", "#transactions_sub_type_show_hide_btn", function(e) {
		e.preventDefault();
        var subTypeNaviBox = $('#transactions_sub_type_navi_box');
        if (subTypeNaviBox.is(':visible')) {
			subTypeNaviBox.hide();
			$(this).text($.t('show_type_menu', 'Show Type Menu'));
		} else {
			subTypeNaviBox.show();
			$(this).text($.t('hide_type_menu', 'Hide Type Menu'));
		}
	});

	return MRS;
}(MRS || {}, jQuery));
