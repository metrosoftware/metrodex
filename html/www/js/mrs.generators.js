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
var MRS = (function(MRS) {

    var TIME_DRIFT = 20000;
    var timer = null;
    var generators = [];
    var lastBlockTime;
    var timeFormatted;
    var heightFormatted;
    var activeCount;

    MRS.pages.generators = function() {
        MRS.renderGenerators(false);
	};

    MRS.renderGenerators = function(isRefresh) {
        var view;
        if (isRefresh) {
            generators.forEach(
                function(generator) {
                    generator.remaining = generator.deadline - (MRS.toEpochTime() - lastBlockTime) + TIME_DRIFT;
                    generator.remainingFormatted = Math.round(generator.remaining / 1000);
                }
            );
            view = MRS.simpleview.get('generators_page', {
                errorMessage: null,
                infoMessage: MRS.getGeneratorAccuracyWarning(),
                isLoading: false,
                isEmpty: false,
                generators: generators,
                timeFormatted: timeFormatted,
                heightFormatted: heightFormatted,
                activeCount: activeCount
            });
            view.render({});
            return;
        }
        if (timer) {
            clearInterval(timer);
            timer = null;
        }
        MRS.hasMorePages = false;
        view = MRS.simpleview.get('generators_page', {
            errorMessage: null,
            infoMessage: MRS.getGeneratorAccuracyWarning(),
            isLoading: true,
            isEmpty: false,
            generators: [],
            timeFormatted: "<span>.</span><span>.</span><span>.</span></span>",
            heightFormatted: "<span>.</span><span>.</span><span>.</span></span>",
            activeCount: "<span>.</span><span>.</span><span>.</span></span>",
            loadingDotsClass: "loading_dots"
        });
        var params = {
            "limit": 10
        };
        MRS.sendRequest("getNextBlockGenerators+", params,
            function(response) {
                view.generators.length = 0;
                lastBlockTime = response.timestamp;
                if (!response.generators) {
                    view.render({
                        isLoading: false,
                        isEmpty: true,
                        errorMessage: MRS.getErrorMessage(response)
                    });
                    return;
                }
                response.generators.forEach(
                    function(generatorsJson) {
                        view.generators.push(MRS.jsondata.generators(generatorsJson));
                    }
                );
                timeFormatted = MRS.formatTimestamp(response.timestamp);
                heightFormatted = String(response.height).escapeHTML();
                activeCount = String(response.activeCount).escapeHTML();
                view.render({
                    isLoading: false,
                    isEmpty: view.generators.length == 0,
                    timeFormatted: timeFormatted,
                    heightFormatted: heightFormatted,
                    activeCount: activeCount,
                    loadingDotsClass: ""
                });
                MRS.pageLoaded();
                if (MRS.currentPage == "generators") {
                    generators = view.generators;
                    timer = setInterval(function() {
                        if (MRS.currentPage != "generators") {
                            clearInterval(timer);
                        } else {
                            MRS.renderGenerators(true);
                        }
                    }, 1000);
                }
            }
        );
    };

    MRS.jsondata.generators = function(generator) {
        var remaining = generator.deadline - (MRS.toEpochTime() - lastBlockTime) + TIME_DRIFT;
        return {
            accountFormatted: MRS.getAccountLink(generator, "account"),
            balanceFormatted: MRS.formatAmount(generator.effectiveBalanceMTR),
            hitTimeFormatted: MRS.formatTimestamp(generator.hitTime),
            deadline: generator.deadline,
            remaining: remaining,
            deadlineFormatted: Math.round(generator.deadline / 1000),
            remainingFormatted : Math.round(remaining / 1000)
        };
    };

    MRS.incoming.generators = function() {
        MRS.renderGenerators(false);
    };

	return MRS;
}(MRS || {}, jQuery));