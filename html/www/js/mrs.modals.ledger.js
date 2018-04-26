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
var MRS = (function(MRS, $) {
	$("body").on("click", ".show_ledger_modal_action", function(event) {
		event.preventDefault();
		if (MRS.fetchingModalData) {
			return;
		}
		MRS.fetchingModalData = true;
        var ledgerId, change, balance;
        if (typeof $(this).data("entry") == "object") {
            var dataObject = $(this).data("entry");
            ledgerId = dataObject["entry"];
            change = dataObject["change"];
            balance = dataObject["balance"];
        } else {
            ledgerId = $(this).data("entry");
            change = $(this).data("change");
            balance = $(this).data("balance");
        }
        if ($(this).data("back") == "true") {
            MRS.modalStack.pop(); // The forward modal
            MRS.modalStack.pop(); // The current modal
        }
        MRS.sendRequest("getAccountLedgerEntry+", { ledgerId: ledgerId }, function(response) {
			MRS.showLedgerEntryModal(response, change, balance);
		});
	});

	MRS.showLedgerEntryModal = function(entry, change, balance) {
        try {
            MRS.setBackLink();
    		MRS.modalStack.push({ class: "show_ledger_modal_action", key: "entry", value: { entry: entry.ledgerId, change: change, balance: balance }});
            $("#ledger_info_modal_entry").html(entry.ledgerId);
            var entryDetails = $.extend({}, entry);
            entryDetails.eventType = $.t(entryDetails.eventType.toLowerCase());
            entryDetails.holdingType = $.t(entryDetails.holdingType.toLowerCase());
            if (entryDetails.timestamp) {
                entryDetails.entryTime = MRS.formatTimestamp(entryDetails.timestamp);
            }
            if (entryDetails.holding) {
                entryDetails.holding_formatted_html = MRS.getTransactionLink(entry.holding);
                delete entryDetails.holding;
            }
            entryDetails.height_formatted_html = MRS.getBlockLink(entry.height);
            delete entryDetails.block;
            delete entryDetails.height;
            if (entryDetails.isTransactionEvent) {
                entryDetails.transaction_formatted_html = MRS.getTransactionLink(entry.event);
            }
            delete entryDetails.event;
            delete entryDetails.isTransactionEvent;
            entryDetails.change_formatted_html = change;
            delete entryDetails.change;
            entryDetails.balance_formatted_html = balance;
            delete entryDetails.balance;
            var detailsTable = $("#ledger_info_details_table");
            detailsTable.find("tbody").empty().append(MRS.createInfoTable(entryDetails));
            detailsTable.show();
            $("#ledger_info_modal").modal("show");
        } finally {
            MRS.fetchingModalData = false;
        }
	};

	return MRS;
}(MRS || {}, jQuery));