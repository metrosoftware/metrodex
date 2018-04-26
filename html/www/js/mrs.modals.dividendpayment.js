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

    MRS.forms.dividendPayment = function($modal) {
        var data = MRS.getFormData($modal.find("form:first"));
        data.asset = MRS.getCurrentAsset().asset;
        if (!data.amountMTRPerShare) {
            return {
                "error": $.t("error_amount_per_share_required")
            }
        } else {
            data.amountMQTPerQNT = MRS.calculatePricePerWholeQNT(
                MRS.convertToMQT(data.amountMTRPerShare),
                MRS.getCurrentAsset().decimals);
        }
        if (!/^\d+$/.test(data.height)) {
            return {
                "error": $.t("error_invalid_dividend_height")
            }
        }
        var isDividendHeightBeforeAssetHeight;
        MRS.sendRequest("getTransaction", { transaction: data.asset }, function(response) {
            if (response.height > data.height) {
                isDividendHeightBeforeAssetHeight = true;
            }
        }, { isAsync: false });
        if (isDividendHeightBeforeAssetHeight) {
            return {
                "error": $.t("dividend_height_asset_height")
            };
        }
        delete data.amountMTRPerShare;
        return {
            "data": data
        };
    };

    $("#dividend_payment_modal").on("hidden.bs.modal", function() {
        $(this).find(".dividend_payment_info").first().hide();
    });

    $("#dividend_payment_amount_per_share").keydown(function(e) {
        var decimals = MRS.getCurrentAsset().decimals;
        var charCode = !e.charCode ? e.which : e.charCode;
        if (MRS.isControlKey(charCode) || e.ctrlKey || e.metaKey) {
            return;
        }
        return MRS.validateDecimals(8-decimals, charCode, $(this).val(), e);
   	});

    $("#dividend_payment_amount_per_share, #dividend_payment_height").on("blur", function() {
        var $modal = $(this).closest(".modal");
        var amountMTRPerShare = $modal.find("#dividend_payment_amount_per_share").val();
        var height = $modal.find("#dividend_payment_height").val();
        var $callout = $modal.find(".dividend_payment_info").first();
        var classes = "callout-info callout-danger callout-warning";
        if (amountMTRPerShare && /^\d+$/.test(height)) {
            MRS.getAssetAccounts(MRS.getCurrentAsset().asset, height,
                function (response) {
                    var accountAssets = response.accountAssets;
                    var qualifiedDividendRecipients = accountAssets.filter(
                        function(accountAsset) {
                            return accountAsset.accountRS !== MRS.getCurrentAsset().accountRS
                                && accountAsset.accountRS !== MRS.constants.GENESIS_RS;
                        });
                    var totalQuantityQNT = new BigInteger("0");
                    qualifiedDividendRecipients.forEach(
                        function (accountAsset) {
                            totalQuantityQNT = totalQuantityQNT.add(new BigInteger(accountAsset.quantityQNT));
                        }
                    );
                    var priceMQT = new BigInteger(MRS.calculatePricePerWholeQNT(MRS.convertToMQT(amountMTRPerShare), MRS.getCurrentAsset().decimals));
                    var totalMTR = MRS.calculateOrderTotal(totalQuantityQNT, priceMQT);
                    $callout.html($.t("dividend_payment_info_preview_success", {
                        "amount": totalMTR,
                        "symbol": MRS.constants.COIN_SYMBOL,
                        "totalQuantity": MRS.formatQuantity(totalQuantityQNT, MRS.getCurrentAsset().decimals),
                        "recipientCount": qualifiedDividendRecipients.length
                    }));
                    $callout.removeClass(classes).addClass("callout-info").show();
                },
                function (response) {
                    var displayString;
                    if (response.errorCode == 4 || response.errorCode == 8) {
                        displayString = $.t("error_invalid_dividend_height");
                    } else {
                        displayString = $.t("dividend_payment_info_preview_error", {"errorCode": response.errorCode});
                    }
                    $callout.html(displayString);
                    $callout.removeClass(classes).addClass("callout-warning").show();
                }
            );
        }
    });

    return MRS;
}(MRS || {}, jQuery));
