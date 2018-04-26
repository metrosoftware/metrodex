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
var MRS = (function(MRS, $, undefined) {
	$("body").on("click", ".show_block_modal_action", function(event) {
		event.preventDefault();
		if (MRS.fetchingModalData) {
			return;
		}
		MRS.fetchingModalData = true;
        if ($(this).data("back") == "true") {
            MRS.modalStack.pop(); // The forward modal
            MRS.modalStack.pop(); // The current modal
        }
		var block = $(this).data("block");
        var isBlockId = $(this).data("id");
        var params = {
            "includeTransactions": "true",
            "includeExecutedPhased": "true"
        };
        if (isBlockId) {
            params["block"] = block;
        } else {
            params["height"] = block;
        }
        MRS.sendRequest("getBlock+", params, function(response) {
			MRS.showBlockModal(response);
		});
	});

	MRS.showBlockModal = function(block) {
        MRS.setBackLink();
        MRS.modalStack.push({ class: "show_block_modal_action", key: "block", value: block.height });
        try {
            $("#block_info_modal_block").html(MRS.escapeRespStr(block.block));
            $("#block_info_transactions_tab_link").tab("show");

            var blockDetails = $.extend({}, block);
            delete blockDetails.transactions;
            blockDetails.generator_formatted_html = MRS.getAccountLink(blockDetails, "generator");
            delete blockDetails.generator;
            delete blockDetails.generatorRS;
            if (blockDetails.previousBlock) {
                blockDetails.previous_block_formatted_html = MRS.getBlockLink(blockDetails.height - 1, blockDetails.previousBlock);
                delete blockDetails.previousBlock;
            }
            if (blockDetails.nextBlock) {
                blockDetails.next_block_formatted_html = MRS.getBlockLink(blockDetails.height + 1, blockDetails.nextBlock);
                delete blockDetails.nextBlock;
            }
            if (blockDetails.timestamp) {
                blockDetails.blockGenerationTime = MRS.formatTimestamp(blockDetails.timestamp);
            }
            var detailsTable = $("#block_info_details_table");
            detailsTable.find("tbody").empty().append(MRS.createInfoTable(blockDetails));
            detailsTable.show();
            var transactionsTable = $("#block_info_transactions_table");
            if (block.transactions.length) {
                $("#block_info_transactions_none").hide();
                transactionsTable.show();
                var rows = "";
                for (var i = 0; i < block.transactions.length; i++) {
                    var transaction = block.transactions[i];
                    if (transaction.amountMQT) {
                        transaction.amount = new BigInteger(transaction.amountMQT);
                        transaction.fee = new BigInteger(transaction.feeMQT);
                        rows += "<tr>" +
                        "<td>" + transaction.transactionIndex + (transaction.phased ? "&nbsp<i class='fa fa-gavel' title='" + $.t("phased") + "'></i>" : "") + "</td>" +
                        "<td>" + MRS.getTransactionLink(transaction.transaction, MRS.formatTimestamp(transaction.timestamp)) + "</td>" +
                        "<td>" + MRS.getTransactionIconHTML(transaction.type, transaction.subtype) + "</td>" +
                        "<td>" + MRS.formatAmount(transaction.amount) + "</td>" +
                        "<td>" + MRS.formatAmount(transaction.fee) + "</td>" +
                        "<td>" + MRS.getAccountLink(transaction, "sender") + "</td>" +
                        "<td>" + MRS.getAccountLink(transaction, "recipient") + "</td>" +
                        "</tr>";
                    }
                }
                transactionsTable.find("tbody").empty().append(rows);
            } else {
                $("#block_info_transactions_none").show();
                transactionsTable.hide();
            }
            var executedPhasedTable = $("#block_info_executed_phased_table");
            if (block.executedPhasedTransactions.length) {
                $("#block_info_executed_phased_none").hide();
                executedPhasedTable.show();
                rows = "";
                for (i = 0; i < block.executedPhasedTransactions.length; i++) {
                    transaction = block.executedPhasedTransactions[i];
                    rows += "<tr>" +
                        "<td>" + MRS.getTransactionLink(transaction.transaction, MRS.formatTimestamp(transaction.timestamp)) + "</td>" +
                        "<td>" + MRS.getTransactionIconHTML(transaction.type, transaction.subtype) + "</td>" +
                        "<td>" + MRS.getBlockLink(transaction.height) + "</td>" +
                        "<td>" + (transaction.attachment.phasingFinishHeight == block.height ? $.t("finished") : $.t("approved")) + "</td>";
                }
                executedPhasedTable.find("tbody").empty().append(rows);
            } else {
                $("#block_info_executed_phased_none").show();
                executedPhasedTable.hide();
            }
            var blockInfoModal = $('#block_info_modal');
            if (!blockInfoModal.data('bs.modal') || !blockInfoModal.data('bs.modal').isShown) {
                blockInfoModal.modal("show");
            }
        } finally {
            MRS.fetchingModalData = false;
        }
	};

	return MRS;
}(MRS || {}, jQuery));