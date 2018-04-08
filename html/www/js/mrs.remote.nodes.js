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
var MRS = (function(MRS) {
    var requestConfirmations = [];

    MRS.updateRemoteNodes = function() {
        console.log("Updating remote nodes");
        var data = {state: "CONNECTED", includePeerInfo: true};
        MRS.sendRequest("getPeers", data, function (response) {
            if (response.peers) {
                MRS.remoteNodesMgr.nodes = {};
                MRS.remoteNodesMgr.addRemoteNodes(response.peers);
            }
            console.log("remote nodes updated");
        });
    };

    MRS.initRemoteNodesMgr = function (isTestnet, resolve, reject) {
        MRS.remoteNodesMgr = new RemoteNodesManager(isTestnet);
        if (MRS.isMobileApp()) {
            if (MRS.mobileSettings.remote_node_address == "") {
                MRS.remoteNodesMgr.addBootstrapNodes(resolve, reject);
            } else {
                MRS.remoteNodesMgr.addBootstrapNode(resolve, reject);
            }
        } else if (MRS.isUpdateRemoteNodes()) {
            if (MRS.isRemoteNodeConnectionAllowed()) {
                MRS.updateRemoteNodes();
            } else {
                $.growl($.t("https_client_cannot_connect_remote_nodes"));
            }
        }
    };

    MRS.requestNeedsConfirmation = function (requestType) {
        if (MRS.remoteNodesMgr) {
            var plusIndex = requestType.indexOf("+");
            if (plusIndex > 0) {
                requestType = requestType.substring(0, plusIndex);
            }
            return !MRS.isRequirePost(requestType) && MRS.isRequestForwardable(requestType)
        }
        return false;
    };

    var prunableAttachments = [
        "PrunablePlainMessage", "PrunableEncryptedMessage", "UnencryptedPrunableEncryptedMessage", "ShufflingProcessing"
    ];

    function normalizePrunableAttachment(transaction) {
        var attachment = transaction.attachment;
        if (attachment) {
            // Check if prunable attachment
            var isPrunableAttachment = false;
            for (var key in attachment) {
                if (!attachment.hasOwnProperty(key) || !key.startsWith("version.")) {
                    continue;
                }
                key = key.substring("version.".length);
                for (var i=0; i<prunableAttachments.length; i++) {
                    if (key == prunableAttachments[i]) {
                        isPrunableAttachment = true;
                    }
                }
            }
            if (!isPrunableAttachment) {
                return;
            }
            for (key in attachment) {
                if (!attachment.hasOwnProperty(key)) {
                    continue;
                }
                if (key.length < 4 || !(key.substring(key.length - 4, key.length).toLowerCase() == "hash")) {
                    delete attachment[key];
                }
            }
        }
    }

    MRS.isPeerListSimilar = function(peers1, peers2) {
        if (!peers1.peers && !peers2.peers) {
            return true;
        }
        if (!peers1.peers) {
            return false;
        }
        if (!peers2.peers) {
            return false;
        }
        var sharedPeers = MRS.countCommonElements(peers1.peers, peers2.peers);
        return 100*sharedPeers / Math.min(peers1.peers.length, peers2.peers.length) > 70;
    };

    MRS.compareLedgerEntries = function(obj1, obj2) {
        if (!obj1.entries && !obj2.entries) {
            return true;
        }
        if (!obj1.entries || !obj2.entries) {
            return false;
        }
        if (obj1.entries instanceof Array && obj2.entries instanceof Array) {
            for (var i = 0; i < obj1.entries.length && i < obj2.entries.length; i++) {
                var str1 = JSON.stringify(obj1.entries[i]);
                var str2 = JSON.stringify(obj2.entries[i]);
                if (str1 != str2) {
                    return false;
                }
            }
            return true;
        }
        return false;
    };

    MRS.countCommonElements = function(a1, a2) {
        var count = 0;
        for (var i = 0; i < a1.length; i++) {
            if (a2.indexOf(a1[i]) >= 0) {
                count++;
            }
        }
        return count;
    };

    MRS.getComparableResponse = function(origResponse, requestType) {
        if (requestType == "getBlockchainStatus") {
            var response = {
                application: origResponse.application,
                isTestnet: origResponse.isTestnet
            };
            return JSON.stringify(response);
        }
        if (requestType == "getState") {
            return requestType; // no point to compare getState responses
        }

        delete origResponse.requestProcessingTime;
        delete origResponse.confirmations;
        if (requestType == "getBlock") {
            delete origResponse.nextBlock;
        } else if (origResponse.transactions) {
            var transactions = origResponse.transactions;
            for (var i=0; i<transactions.length; i++) {
                var transaction = transactions[i];
                delete transaction.confirmations;
                normalizePrunableAttachment(transaction);
            }
        } else if (requestType == "getAccountLedger") {
            for (var i=0; i<origResponse.entries.length; i++) {
                var entry = origResponse.entries[i];
                delete entry.ledgerId;
            }
        }
        return JSON.stringify(origResponse);
    };

    MRS.confirmResponse = function(requestType, data, expectedResponse, requestRemoteNode) {
        if (MRS.requestNeedsConfirmation(requestType)) {
            try {
                // First clone the response so that we do not change it
                var expectedResponseStr = JSON.stringify(expectedResponse);
                expectedResponse = JSON.parse(expectedResponseStr);

                // Now remove all variable parts
                expectedResponseStr = MRS.getComparableResponse(expectedResponse, requestType);
            } catch(e) {
                MRS.logConsole("Cannot parse JSON response for request " + requestType);
                return;
            }
            var ignoredAddresses = [];
            if (requestRemoteNode) {
                ignoredAddresses.push(requestRemoteNode.address);
            }
            var nodes = MRS.remoteNodesMgr.getRandomNodes(MRS.mobileSettings.validators_count, ignoredAddresses);
            var now = new Date();
            var confirmationReport = {processing: [], confirmingNodes: [], rejectingNodes: [],
                requestType: requestType, requestTime: now};

            requestConfirmations.unshift(confirmationReport);

            var minRequestTime = new Date(now);
            //keep history since 1 minute and 15 seconds ago
            minRequestTime.setMinutes(minRequestTime.getMinutes() - 1);
            minRequestTime.setSeconds(minRequestTime.getSeconds() - 15);

            var idx = requestConfirmations.length - 1;
            while (idx > 0){
                if (minRequestTime > requestConfirmations[idx].requestTime) {
                    requestConfirmations.pop();
                } else {
                    break;
                }
                idx--;
            }

            if (requestConfirmations.length > 50) {
                requestConfirmations.pop();
            }
            function onConfirmation(response) {
                var fromNode = this;
                var index = confirmationReport.processing.indexOf(fromNode.announcedAddress);
                confirmationReport.processing.splice(index, 1);

                if (!response.errorCode) {
                    // here it's Ok to modify the response since it is only being used for comparison
                    var node = data["_extra"].node;
                    var type = data["_extra"].requestType;
                    MRS.logConsole("Confirm request " + type + " with node " + node.announcedAddress);
                    var responseStr = MRS.getComparableResponse(response, type);
                    if (responseStr == expectedResponseStr
                        || (type == "getPeers" && MRS.isPeerListSimilar(response, expectedResponse))
                        || (type == "getAccountLedger" && MRS.compareLedgerEntries(response, expectedResponse))) {
                        confirmationReport.confirmingNodes.push(node);
                    } else {
                        MRS.logConsole(node.announcedAddress + " response defers from " + requestRemoteNode.announcedAddress + " response for " + type);
                        MRS.logConsole("Expected Response: " + expectedResponseStr);
                        MRS.logConsole("Actual   Response: " + responseStr);
                        confirmationReport.rejectingNodes.push(node);
                        MRS.updateConfirmationsIndicator();
                    }

                    if (confirmationReport.processing.length == 0) {
                        MRS.logConsole("onConfirmation:Request " + type +
                            " confirmations " + confirmationReport.confirmingNodes.length +
                            " rejections " + confirmationReport.rejectingNodes.length);
                        MRS.updateConfirmationsIndicator();
                    }
                } else {
                    // Confirmation request received error
                    MRS.logConsole("Confirm request error " + response.errorDescription);
                }
            }

            for (var i=0; i<nodes.length; i++) {
                var node = nodes[i];
                if (node.isBlacklisted()) {
                    continue;
                }
                confirmationReport.processing.push(node.announcedAddress);
                ignoredAddresses.push(node.address);
                if (typeof data == "string") {
                    data = { "querystring": data };
                }
                data["_extra"] = { node: node, requestType: requestType };
                MRS.sendRequest(requestType, data, onConfirmation, { noProxy: true, remoteNode: node, doNotEscape: true });
            }
        }
    };

    MRS.updateConfirmationsIndicator = function () {
        var color = (62 << 16) | (169 << 8) | 64;
        var rejections = 0;
        var confirmations = 0;
        var hasRejections = false;
        for (var i=0; i<requestConfirmations.length; i++) {
            confirmations++; //the main remote node counts as 1 vote
            confirmations += requestConfirmations[i].confirmingNodes.length;
            rejections += requestConfirmations[i].rejectingNodes.length;
        }
        if (confirmations > 0) {
            var rejectionsRatio = rejections * 2 / confirmations; // It can't get worse than 1:1 ratio
            if (rejectionsRatio > 1) {
                rejectionsRatio = 1;
            }
            if (rejectionsRatio > 0) {
                var gradientStart = 0xeccc31;
                var gradientEnd = 0xa94442;
                var red = (gradientStart >> 16) * (1 - rejectionsRatio) + (gradientEnd >> 16) * rejectionsRatio;
                var green = ((gradientStart >> 8) & 0xff) * (1 - rejectionsRatio) + ((gradientEnd >> 8) & 0xff) * rejectionsRatio;
                var blue = (gradientStart & 0xff) * (1 - rejectionsRatio) + (gradientEnd & 0xff) * rejectionsRatio;
                color = (red << 16) | (green << 8) | blue;
                hasRejections = true;
            }
        }
        var indicator = $("#confirmation_rate_indicator");
        var indicatorIcon = indicator.find("i");
        if (hasRejections) {
            indicatorIcon.removeClass('fa-bolt');
            indicatorIcon.addClass('fa-exclamation');
        } else {
            indicatorIcon.addClass('fa-bolt');
            indicatorIcon.removeClass('fa-exclamation');
        }
        indicator.css({'background-color': "#" + color.toString(16)});
        MRS.updateConfirmationsTable();
    };

    MRS.printRemoteAddresses = function (nodesList) {
        var result = "";
        for (var i=0; i<nodesList.length; i++) {
            result += '<a target="_blank" href="' + nodesList[i].getUrl() + '">' + nodesList[i].announcedAddress + '</a><br/>';
        }
        return result;
    };

    MRS.updateConfirmationsTable = function () {
        var requestConfirmationsInfoTable = $("#request_confirmations_info_table");
        var rows = "";

        for (var i=0; i<requestConfirmations.length; i++) {
            var confirmation = requestConfirmations[i];
            rows += "<tr>" +
                        "<td>" + MRS.formatTimestamp(confirmation.requestTime) + "<br/>"
                            + String(confirmation.requestType).escapeHTML() + "</td>" +
                        "<td>" + MRS.printRemoteAddresses(confirmation.confirmingNodes) + "</td>" +
                        "<td>" + MRS.printRemoteAddresses(confirmation.rejectingNodes) + "</td>" +
                    "</tr>";
        }
        requestConfirmationsInfoTable.find("tbody").empty().append(rows);
    };
	return MRS;
}(MRS || {}, jQuery));