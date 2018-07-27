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
 */
var MRS = (function(MRS, $) {
	MRS.blocksPageType = null;
	MRS.tempBlocks = [];
	var trackBlockchain = false;
	MRS.averageBlockGenerationTime = 3000;

	MRS.getBlock = function(id, callback, pageRequest) {
		MRS.sendRequest("getBlock" + (pageRequest ? "+" : ""), {
			"block": id
		}, function(response) {
			if (response.errorCode && response.errorCode == -1) {
				MRS.logConsole("getBlock request failed, setTimeout for retry");
				setTimeout(function() {
					MRS.getBlock(id, callback, pageRequest);
				}, 2500);
			} else {
				callback(response);
			}
		}, { noProxy: true });
	};

	MRS.handleInitialBlocks = function(response) {
		MRS.blocks.push(response);
		if (MRS.blocks.length < 10 && response.previousBlock) {
			MRS.getBlock(response.previousBlock, MRS.handleInitialBlocks);
		} else {
			MRS.checkBlockHeight(MRS.blocks[0].height);
			if (MRS.state) {
				//if no new blocks in 6 hours, show blockchain download progress..
				var timeDiff = MRS.state.time - MRS.blocks[0].timestamp;
				if (timeDiff > 60 * 60 * 18 * 1000) {
					if (timeDiff > 60 * 60 * 24 * 14 * 1000) {
						MRS.setStateInterval(3);
					} else if (timeDiff > 60 * 60 * 24 * 7 * 1000) {
						//second to last week
						MRS.setStateInterval(2);
					} else {
						//last week
						MRS.setStateInterval(1);
					}
					$("#mrs_update_explanation").find("span").hide();
					$("#mrs_update_explanation_wait").attr("style", "display: none !important");
					$("#downloading_blockchain, #mrs_update_explanation_blockchain_sync").show();
					$("#show_console").hide();
					MRS.updateBlockchainDownloadProgress();
				} else {
					//continue with faster state intervals if we still haven't reached current block from within 1 hour
					if (timeDiff < 60 * 60  * 1000) {
						MRS.setStateInterval(3);
						trackBlockchain = false;
					} else {
						MRS.setStateInterval(1);
						trackBlockchain = true;
					}
				}
			}
			if (!MRS.state.apiProxy) {
				MRS.updateDashboardLastBlock(MRS.blocks[0]);
			}

		}
	};

	MRS.handleNewBlocks = function(response) {
		if (MRS.downloadingBlockchain) {
			//new round started...
			if (MRS.tempBlocks.length == 0 && MRS.getLastBlock() != response.block) {
				return;
			}
		}

		//we have all blocks
		if (response.height - 1 == MRS.lastBlockHeight || MRS.tempBlocks.length == 99) {
			var newBlocks = [];

			//there was only 1 new block (response)
			if (MRS.tempBlocks.length == 0) {
				//remove oldest block, add newest block
				MRS.blocks.unshift(response);
				newBlocks.push(response);
			} else {
				MRS.tempBlocks.push(response);
				//remove oldest blocks, add newest blocks
				[].unshift.apply(MRS.blocks, MRS.tempBlocks);
				newBlocks = MRS.tempBlocks;
				MRS.tempBlocks = [];
			}

			if (MRS.blocks.length > 100) {
				MRS.blocks = MRS.blocks.slice(0, 100);
			}
			MRS.checkBlockHeight(MRS.blocks[0].height);
			MRS.incoming.updateDashboardBlocks(newBlocks.length);
			if (!MRS.state.apiProxy) {
				MRS.updateDashboardLastBlock(MRS.blocks[0]);
			}
		} else if (response.height - 1 > MRS.lastBlockHeight) {
			MRS.tempBlocks.push(response);
			MRS.getBlock(response.previousBlock, MRS.handleNewBlocks);
		}
	};

	MRS.checkBlockHeight = function(blockHeight) {
		if (blockHeight) {
			if (MRS.state && MRS.state.apiProxy) {
				MRS.lastLocalBlockHeight = blockHeight;
			} else {
				MRS.lastBlockHeight = blockHeight;
			}
		}
	};

	MRS.updateDashboardLastBlock = function(block) {
		$("#mrs_current_block_time").empty().append(MRS.formatTimestamp(block.timestamp));
		$(".mrs_current_block").empty().append(MRS.escapeRespStr(block.height));
	};

	//we always update the dashboard page..
	MRS.incoming.updateDashboardBlocks = function(newBlocksCount) {
        var timeDiff;
		if (MRS.downloadingBlockchain) {
			if (MRS.state) {
				timeDiff = MRS.state.time - MRS.blocks[0].timestamp;
				if (timeDiff < 60 * 60 * 18 * 1000) {
					if (timeDiff < 60 * 60 * 1000) {
						MRS.setStateInterval(3);
					} else {
						MRS.setStateInterval(1);
						trackBlockchain = true;
					}
					$("#dashboard_message").hide();
					$("#downloading_blockchain, #mrs_update_explanation_blockchain_sync").hide();
					$("#mrs_update_explanation_wait").removeAttr("style");
					if (MRS.settings["console_log"]) {
						$("#show_console").show();
					}
					//todo: update the dashboard blocks!
					$.growl($.t("success_blockchain_up_to_date"), {
						"type": "success"
					});
					MRS.checkAliasVersions();
					MRS.checkIfOnAFork();
				} else {
					if (timeDiff > 60 * 60 * 24 * 14 * 1000) {
						MRS.setStateInterval(3);
					} else if (timeDiff > 60 * 60 * 24 * 7 * 1000) {
						//second to last week
						MRS.setStateInterval(2);
					} else {
						//last week
						MRS.setStateInterval(1);
					}

					MRS.updateBlockchainDownloadProgress();
				}
			}
		} else if (trackBlockchain) {
			//continue with faster state intervals if we still haven't reached current block from within 1 hour
            timeDiff = MRS.state.time - MRS.blocks[0].timestamp;
			if (timeDiff < 60 * 60 * 1000) {
				MRS.setStateInterval(3);
				trackBlockchain = false;
			} else {
				MRS.setStateInterval(1);
			}
		}

		//update number of confirmations... perhaps we should also update it in tne MRS.transactions array
		$("#dashboard_table").find("tr.confirmed td.confirmations").each(function() {
			if ($(this).data("incoming")) {
				$(this).removeData("incoming");
				return true;
			}
			var confirmations = parseInt($(this).data("confirmations"), 10);
			var nrConfirmations = confirmations + newBlocksCount;
			if (confirmations <= 10) {
				$(this).data("confirmations", nrConfirmations);
				$(this).attr("data-content", $.t("x_confirmations", {
					"x": MRS.formatAmount(nrConfirmations, false, true)
				}));

				if (nrConfirmations > 10) {
					nrConfirmations = '10+';
				}
				$(this).html(nrConfirmations);
			} else {
				$(this).attr("data-content", $.t("x_confirmations", {
					"x": MRS.formatAmount(nrConfirmations, false, true)
				}));
			}
		});
		var blockLink = $("#sidebar_block_link");
		if (blockLink.length > 0) {
			blockLink.html(MRS.getBlockLink(MRS.lastBlockHeight, MRS.state.keyHeight + "(" + MRS.lastBlockHeight + ")"));
		}
	};

	MRS.pages.blocks = function() {
		if (MRS.blocksPageType == "forged_blocks") {
			$("#forged_fees_total_box, #forged_blocks_total_box").show();
			$("#blocks_transactions_per_hour_box, #blocks_generation_time_box").hide();
            $("#key_height_column_header").hide();
            $("#key_height_column").hide();
			$("#forged_blocks_total_box").show();
            $("#mined_blocks_total_box").hide();

			MRS.sendRequest("getAccountBlocks+", {
				"account": MRS.account,
				"firstIndex": MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage,
				"lastIndex": MRS.pageNumber * MRS.itemsPerPage
			}, function(response) {
				if (response.blocks && response.blocks.length) {
					if (response.blocks.length > MRS.itemsPerPage) {
						MRS.hasMorePages = true;
						response.blocks.pop();
					}
					MRS.blocksPageLoaded(response.blocks);
				} else {
					MRS.blocksPageLoaded([]);
				}
			});
		} else if (MRS.blocksPageType == "mined_blocks") {
            $("#forged_fees_total_box, #mined_blocks_total_box").show();
            $("#key_height_column_header").show();
            $("#key_height_column").show();
            $("#blocks_transactions_per_hour_box, #blocks_generation_time_box, #forged_blocks_total_box").hide();
            MRS.sendRequest("getAccountMinedBlocks+", {
                "account": MRS.account,
                "firstIndex": MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage,
                "lastIndex": MRS.pageNumber * MRS.itemsPerPage
            }, function(response) {
                if (response.blocks && response.blocks.length) {
                    if (response.blocks.length > MRS.itemsPerPage) {
                        MRS.hasMorePages = true;
                        response.blocks.pop();
                    }
                    MRS.blocksPageLoaded(response.blocks);
                } else {
                    MRS.blocksPageLoaded([]);
                }
            });
		} else if (MRS.blocksPageType == "all_mined_blocks") {
            $("#forged_fees_total_box, #mined_blocks_total_box").show();
            $("#key_height_column_header").show();
            $("#key_height_column").show();
            $("#blocks_transactions_per_hour_box, #blocks_generation_time_box, #forged_blocks_total_box").hide();
            MRS.sendRequest("getMinedBlocks+", {
                "account": MRS.account,
                "firstIndex": MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage,
                "lastIndex": MRS.pageNumber * MRS.itemsPerPage
            }, function(response) {
                if (response.blocks && response.blocks.length) {
                    if (response.blocks.length > MRS.itemsPerPage) {
                        MRS.hasMorePages = true;
                        response.blocks.pop();
                    }
                    MRS.blocksPageLoaded(response.blocks);
                } else {
                    MRS.blocksPageLoaded([]);
                }
            });
        } else {
			$("#forged_fees_total_box, #forged_blocks_total_box, #mined_blocks_total_box").hide();
			$("#key_height_column_header").hide();
            $("#key_height_column").hide();
			$("#blocks_transactions_per_hour_box, #blocks_generation_time_box").show();
			MRS.sendRequest("getBlocks+", {
				"firstIndex": MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage,
				"lastIndex": MRS.pageNumber * MRS.itemsPerPage
			}, function(response) {
				if (response.blocks && response.blocks.length) {
					if (response.blocks.length > MRS.itemsPerPage) {
						MRS.hasMorePages = true;
						response.blocks.pop();
					}
					MRS.blocksPageLoaded(response.blocks);
				} else {
					MRS.blocksPageLoaded([]);
				}
			});
		}
	};

	MRS.incoming.blocks = function() {
		MRS.loadPage("blocks");
	};

	MRS.blocksPageLoaded = function(blocks) {
		var rows = "";
		var totalAmount = new BigInteger("0");
		var reward = new BigInteger("0");
		var totalTransactions = 0;

		for (var i = 0; i < blocks.length; i++) {
			var block = blocks[i];
			totalAmount = totalAmount.add(new BigInteger(block.totalAmountMQT));
			reward = reward.add(new BigInteger(block.rewardMQT));
			totalTransactions += block.numberOfTransactions;
            var localHeightStr = "<td><a href='#' data-block='" + MRS.escapeRespStr(block.height) + "' data-blockid='" + MRS.escapeRespStr(block.block) + "' class='block show_block_modal_action'" + (block.numberOfTransactions > 0 ? " style='font-weight:bold'" : "") + ">" + MRS.escapeRespStr(block.local_height) + "</a></td>";
			rows += "<tr>" +
                "<td><a href='#' data-block='" + MRS.escapeRespStr(block.height) + "' data-blockid='" + MRS.escapeRespStr(block.block) + "' class='block show_block_modal_action'" + (block.numberOfTransactions > 0 ? " style='font-weight:bold'" : "") + ">" + MRS.escapeRespStr(block.height) + "</a></td>" +
				(MRS.blocksPageType == "all_mined_blocks" || MRS.blocksPageType == "mined_blocks" ? localHeightStr : "") +
                "<td>" + MRS.formatTimestamp(block.timestamp) + "</td>" +
                "<td>" + MRS.formatAmount(block.totalAmountMQT) + "</td>" +
				"<td>" + MRS.formatAmount(block.rewardMQT) + "</td>" +
                "<td>" + MRS.formatAmount(block.numberOfTransactions) + "</td>" +
                "<td>" + MRS.getAccountLink(block, "generator") + "</td>" +
                "<td>" + MRS.formatVolume(block.payloadLength) + "</td>" +
				"<td>" + MRS.baseTargetPercent(block).pad(4) + " %</td>" +
            "</tr>";
		}

        var blocksAverageAmount = $("#blocks_average_amount");
        var blockAvgFee = $("#blocks_average_fee");
        var blockForgedBlocks = $("#forged_blocks_total");
        var blockForgedFees = $("#forged_fees_total");
        if (MRS.blocksPageType == "forged_blocks") {
			MRS.sendRequest("getAccountBlockCount+", {
				"account": MRS.account,
				"isKeyBlock": false
			}, function(response) {
				if (response.numberOfBlocks && response.numberOfBlocks > 0) {
					$("#forged_blocks_total").html(response.numberOfBlocks).removeClass("loading_dots");
                    var avgFee = new Big(MRS.accountInfo.forgedBalanceMQT).div(response.numberOfBlocks).div(new Big("100000000")).toFixed(2);
                    $("#blocks_average_fee").html(MRS.formatStyledAmount(MRS.convertToMQT(avgFee))).removeClass("loading_dots");
				} else {
					$("#forged_blocks_total").html(0).removeClass("loading_dots");
					$("#blocks_average_fee").html(0).removeClass("loading_dots");
				}
			});
			$("#forged_fees_total").html(MRS.formatStyledAmount(MRS.accountInfo.forgedBalanceMQT)).removeClass("loading_dots");
			blocksAverageAmount.removeClass("loading_dots");
			blocksAverageAmount.parent().parent().css('visibility', 'hidden');

            blocksAverageAmount.removeClass("loading_dots");
            blocksAverageAmount.parent().parent().css('visibility', 'hidden');

            blockAvgFee.removeClass("loading_dots");
            blockAvgFee.parent().parent().css('visibility', 'visible');

            blockForgedBlocks.removeClass("loading_dots");
            blockForgedBlocks.parent().parent().css('visibility', 'visible');

            blockForgedFees.removeClass("loading_dots");
            blockForgedFees.parent().parent().css('visibility', 'visible');
            $("#blocks_page").find(".ion-stats-bars").parent().css('visibility', 'hidden');
            $("#blocks_page").find(".ion-paperclip").parent().css('visibility', 'visible');
            $("#blocks_page").find(".fa-bars").parent().css('visibility', 'visible');
            $("#blocks_page").find(".fa-briefcase").parent().css('visibility', 'visible');
		} else if (MRS.blocksPageType == "mined_blocks") {
            MRS.sendRequest("getAccountBlockCount+", {
                "account": MRS.account,
				"isKeyBlock": true
            }, function(response) {
                if (response.numberOfBlocks && response.numberOfBlocks > 0) {
                    $("#mined_blocks_total").html(response.numberOfBlocks).removeClass("loading_dots");
                } else {
                    $("#mined_blocks_total").html(0).removeClass("loading_dots");
                }
            });
            $("#blocks_page").find(".ion-stats-bars").parent().css('visibility', 'hidden');
            $("#blocks_page").find(".ion-paperclip").parent().css('visibility', 'hidden');
            $("#blocks_page").find(".fa-bars").parent().css('visibility', 'visible');
            $("#blocks_page").find(".fa-briefcase").parent().css('visibility', 'hidden');
            blocksAverageAmount.removeClass("loading_dots");
            blocksAverageAmount.parent().parent().css('visibility', 'hidden');
            blockAvgFee.removeClass("loading_dots");
            blockAvgFee.parent().parent().css('visibility', 'hidden');
            blockForgedBlocks.parent().parent().css('visibility', 'visible');
            blockForgedFees.removeClass("loading_dots");
            blockForgedFees.parent().parent().css('visibility', 'hidden');
		} else if (MRS.blocksPageType == "all_mined_blocks") {
            MRS.sendRequest("getBlockCount+", {
                "isKeyBlock": true
            }, function(response) {
                if (response.numberOfBlocks && response.numberOfBlocks > 0) {
                    $("#mined_blocks_total").html(response.numberOfBlocks).removeClass("loading_dots");
                } else {
                    $("#mined_blocks_total").html(0).removeClass("loading_dots");
                }
            });
            $("#blocks_page").find(".ion-stats-bars").parent().css('visibility', 'hidden');
            $("#blocks_page").find(".ion-paperclip").parent().css('visibility', 'hidden');
            $("#blocks_page").find(".fa-bars").parent().css('visibility', 'visible');
            $("#blocks_page").find(".fa-briefcase").parent().css('visibility', 'hidden');
            blocksAverageAmount.removeClass("loading_dots");
            blocksAverageAmount.parent().parent().css('visibility', 'hidden');
            blockAvgFee.removeClass("loading_dots");
            blockAvgFee.parent().parent().css('visibility', 'hidden');
            blockForgedBlocks.parent().parent().css('visibility', 'visible');
            blockForgedFees.removeClass("loading_dots");
            blockForgedFees.parent().parent().css('visibility', 'hidden');
        } else {
			var time;
            if (blocks.length) {
				var startingTime = blocks[blocks.length - 1].timestamp;
				var endingTime = blocks[0].timestamp;
				time = endingTime - startingTime;
			} else {
				time = 0;
			}
            var averageFee = 0;
            var averageAmount = 0;
			if (blocks.length) {
				averageFee = new Big(reward.toString()).div(new Big("100000000")).div(new Big(String(blocks.length))).toFixed(2);
				averageAmount = new Big(totalAmount.toString()).div(new Big("100000000")).div(new Big(String(blocks.length))).toFixed(2);
			}
			averageFee = MRS.convertToMQT(averageFee);
			averageAmount = MRS.convertToMQT(averageAmount);
			if (time == 0) {
				$("#blocks_transactions_per_hour").html("0").removeClass("loading_dots");
			} else {
				$("#blocks_transactions_per_hour").html(Math.round(totalTransactions / (time / 60000) * 60)).removeClass("loading_dots");
			}
			$("#blocks_average_generation_time").html(Math.round(time / MRS.itemsPerPage) + "ms").removeClass("loading_dots");
			$("#blocks_average_fee").html(MRS.formatStyledAmount(averageFee)).removeClass("loading_dots");
			blocksAverageAmount.parent().parent().css('visibility', 'visible');
			$("#blocks_page").find(".ion-stats-bars").parent().css('visibility', 'visible');
			blocksAverageAmount.html(MRS.formatStyledAmount(averageAmount)).removeClass("loading_dots");
            blockAvgFee.removeClass("loading_dots");
            blockAvgFee.parent().parent().css('visibility', 'visible');
            $("#blocks_page").find(".ion-paperclip").parent().css('visibility', 'visible');
		}
		MRS.dataLoaded(rows);
	};

	MRS.blockchainDownloadingMessage = function() {
		if (MRS.state.apiProxy) {
			return $.t(MRS.state.isLightClient ? "status_light_client_proxy" : "status_blockchain_downloading_proxy",
					{ peer: MRS.getPeerLink(MRS.state.apiProxyPeer) }) +
				" <a href='#' class='btn btn-xs' data-toggle='modal' data-target='#client_status_modal'>" + $.t("proxy_info_link") + "</a>";
		} else if(MRS.state.isLightClient) {
			$.t("status_light_client_proxy");
		} else {
			return $.t("status_blockchain_downloading");
		}
	};

	$("#blocks_page_type").find(".btn").click(function(e) {
		e.preventDefault();
		MRS.blocksPageType = $(this).data("type");
		$("#blocks_average_amount, #blocks_average_fee, #blocks_transactions_per_hour, #blocks_average_generation_time, #forged_blocks_total, #forged_fees_total").html("<span>.</span><span>.</span><span>.</span></span>").addClass("loading_dots");
        var blocksTable = $("#blocks_table");
        blocksTable.find("tbody").empty();
		blocksTable.parent().addClass("data-loading").removeClass("data-empty");
		MRS.loadPage("blocks");
	});

	return MRS;
}(MRS || {}, jQuery));