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
	var INCOMING = "incoming";

	MRS.pages.account_properties = function() {
        MRS.renderAccountProperties($("#account_properties_page_type").find(".active").data("type"));
	};

    MRS.renderAccountProperties = function(type) {
        MRS.hasMorePages = false;
        var view = MRS.simpleview.get('account_properties_section', {
            errorMessage: null,
            isLoading: true,
            isEmpty: false,
            properties: []
        });
        var params = {
            "firstIndex": MRS.pageNumber * MRS.itemsPerPage - MRS.itemsPerPage,
            "lastIndex": MRS.pageNumber * MRS.itemsPerPage
        };
        if (type == INCOMING) {
            params.recipient = MRS.account;
        } else {
            params.setter = MRS.account;
        }
        MRS.sendRequest("getAccountProperties+", params,
            function(response) {
                if (response.properties.length > MRS.itemsPerPage) {
                    MRS.hasMorePages = true;
                    response.properties.pop();
                }
                view.properties.length = 0;
                response.properties.forEach(
                    function (propertiesJson) {
                        view.properties.push( MRS.jsondata.properties(propertiesJson, type) );
                    }
                );
                view.render({
                    isLoading: false,
                    isEmpty: view.properties.length == 0,
                    header: type == INCOMING ? $.t("setter") : $.t("recipient")
                });
                MRS.pageLoaded();
            }
        );
    };

    MRS.normalizePropertyValue = function(value) {
        if (value == null) {
            return "";
        } else if (typeof value === "object") {
            return JSON.stringify(value);
        }
        return MRS.escapeRespStr(value);
    };

    MRS.jsondata.properties = function (response, type) {
        var updateAction = "";
        var deleteAction = "";
        var recipientToken = "";
        if (response.recipientRS) {
            recipientToken = "data-recipient='" + response.recipientRS + "' ";
        }
        var value = MRS.normalizePropertyValue(response.value);
        if (type == INCOMING) {
            deleteAction = "<a href='#' class='btn btn-xs' data-toggle='modal' data-target='#delete_account_property_modal' " +
            "data-setter='" + response.setterRS + "' " +
            "data-recipient='" + MRS.accountRS + "' " +
            "data-property='" + MRS.escapeRespStr(response.property) + "'>" + $.t("delete") + "</a>";

            if (response.setterRS == MRS.accountRS) {
                updateAction = "<a href='#' class='btn btn-xs' data-toggle='modal' data-target='#set_account_property_modal' " +
                "data-recipient='" + MRS.accountRS + "' " +
                "data-property='" + MRS.escapeRespStr(response.property) + "' " +
                "data-value='" + value + "'>" + $.t("update") + "</a>";
            }
        } else {
            deleteAction = "<a href='#' class='btn btn-xs' data-toggle='modal' data-target='#delete_account_property_modal' " +
            "data-setter='" + MRS.accountRS + "' " +
            recipientToken +
            "data-property='" + MRS.escapeRespStr(response.property) + "'>" + $.t("delete") + "</a>";

            updateAction = "<a href='#' class='btn btn-xs' data-toggle='modal' data-target='#set_account_property_modal' " +
            recipientToken +
            "data-property='" + MRS.escapeRespStr(response.property) + "' " +
            "data-value='" + value + "'>" + $.t("update") + "</a>";
        }

        return {
            accountFormatted: type == INCOMING ? MRS.getAccountLink(response, "setter") : MRS.getAccountLink(response, "recipient"),
            property: MRS.escapeRespStr(response.property),
            value: value,
            action_update: updateAction,
            action_delete: deleteAction
        };
    };

	MRS.incoming.account_properties = function() {
		MRS.loadPage("account_properties");
	};

    $("#account_properties_page_type").find(".btn").click(function (e) {
        e.preventDefault();
        var propertiesTable = $("#account_properties_table");
        propertiesTable.find("tbody").empty();
        propertiesTable.parent().addClass("data-loading").removeClass("data-empty");
        MRS.renderAccountProperties($(this).data("type"));
    });

    $("#set_account_property_modal").on("show.bs.modal", function(e) {
        var $invoker = $(e.relatedTarget);
        var recipient = $invoker.data("recipient");
        var recipientInput = $("#set_account_property_recipient");
        var recipientButton = $(".recipient_selector").find(".btn");
        if (recipient) {
            recipientInput.val(recipient);
            recipientInput.prop('readonly', true);
            recipientButton.prop('disabled', true);
        } else {
            recipientInput.prop('readonly', false);
            recipientButton.prop('disabled', false);
        }
        var property = $invoker.data("property");
        var propertyInput = $("#set_account_property_property");
        if (property) {
            propertyInput.val(property);
            propertyInput.prop('readonly', true);
        } else {
            propertyInput.prop('readonly', false);
        }
        $("#set_account_property_value").val(MRS.normalizePropertyValue($invoker.data("value")));
    });

    $("#delete_account_property_modal").on("show.bs.modal", function(e) {
        var $invoker = $(e.relatedTarget);
        var setter = $invoker.data("setter");
        if (setter) {
            var setterInput = $("#delete_account_property_setter");
            setterInput.val(setter);
        }
        var recipient = $invoker.data("recipient");
        if (recipient) {
            var recipientInput = $("#delete_account_property_recipient");
            recipientInput.val(recipient);
        }
        var property = $invoker.data("property");
        if (property) {
            var propertyInput = $("#delete_account_property_property");
            propertyInput.val(property);
        }
    });

	return MRS;
}(MRS || {}, jQuery));