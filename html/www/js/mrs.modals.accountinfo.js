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
	$("#account_info_modal").on("show.bs.modal", function(e) {
		$("#account_info_name").val(MRS.accountInfo.name);
		$("#account_info_description").val(MRS.accountInfo.description);
	});

	MRS.forms.setAccountInfoComplete = function(response, data) {
		var name = $.trim(String(data.name));
		if (name) {
			$("#account_name").html(name.escapeHTML()).removeAttr("data-i18n");
		} else {
			$("#account_name").html($.t("no_name_set")).attr("data-i18n", "no_name_set");
		}

		var description = $.trim(String(data.description));

		setTimeout(function() {
			MRS.accountInfo.description = description;
			MRS.accountInfo.name = name;
		}, 1000);
	}

	return MRS;
}(MRS || {}, jQuery));