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
	var mrsModal = $("#mrs_modal");
    mrsModal.on("shown.bs.modal", function() {
		if (MRS.fetchingModalData) {
			return;
		}

		MRS.fetchingModalData = true;
        MRS.spinner.spin(mrsModal[0]);
		MRS.sendRequest("getState", {
			"includeCounts": true,
            "adminPassword": MRS.getAdminPassword()
		}, function(state) {
			for (var key in state) {
				if (!state.hasOwnProperty(key)) {
					continue;
				}
				var el = $("#mrs_node_state_" + key);
				if (el.length) {
					if (key.indexOf("number") != -1) {
						el.html(MRS.formatAmount(state[key]));
					} else if (key.indexOf("Memory") != -1) {
						el.html(MRS.formatVolume(state[key]));
					} else if (key == "time") {
						el.html(MRS.formatTimestamp(state[key]));
					} else {
						el.html(MRS.escapeRespStr(state[key]));
					}
				}
			}

			$("#mrs_update_explanation").show();
			$("#mrs_modal_state").show();
            MRS.spinner.stop();
			MRS.fetchingModalData = false;
		});
	});

	mrsModal.on("hide.bs.modal", function() {
		$("body").off("dragover.mrs, drop.mrs");

		$("#mrs_update_drop_zone, #mrs_update_result, #mrs_update_hashes, #mrs_update_hash_progress").hide();

		$(this).find("ul.nav li.active").removeClass("active");
		$("#mrs_modal_state_nav").addClass("active");

		$(".mrs_modal_content").hide();
	});

	mrsModal.find("ul.nav li").click(function(e) {
		e.preventDefault();

		var tab = $(this).data("tab");

		$(this).siblings().removeClass("active");
		$(this).addClass("active");

		$(".mrs_modal_content").hide();

		var content = $("#mrs_modal_" + tab);

		content.show();
	});

	return MRS;
}(MRS || {}, jQuery));