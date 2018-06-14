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
var MRS = (function (MRS, $, undefined) {
    var _password;

    MRS.setServerPassword = function (password) {
        _password = password;
    };

    MRS.sendRequest = function (requestType, data, callback, options) {
        if (!options) {
            options = {};
        }
        if (requestType == undefined) {
            MRS.logConsole("Undefined request type");
            return;
        }
        if (!MRS.isRequestTypeEnabled(requestType)) {
            callback({
                "errorCode": 1,
                "errorDescription": $.t("request_of_type", {
                    type: requestType
                })
            });
            return;
        }
        if (data == undefined) {
            MRS.logConsole("Undefined data for " + requestType);
            return;
        }
        if (callback == undefined) {
            MRS.logConsole("Undefined callback function for " + requestType);
            return;
        }

        $.each(data, function (key, val) {
            if (key != "secretPhrase") {
                if (typeof val == "string") {
                    data[key] = $.trim(val);
                }
            }
        });
        //convert MTR to MQT...
        var field = "N/A";
        try {
            var mtrFields = [
                ["feeMTR", "feeMQT"],
                ["amountMTR", "amountMQT"],
                ["priceMTR", "priceMQT"],
                ["refundMTR", "refundMQT"],
                ["discountMTR", "discountMQT"],
                ["phasingQuorumMTR", "phasingQuorum"],
                ["phasingMinBalanceMTR", "phasingMinBalance"],
                ["controlQuorumMTR", "controlQuorum"],
                ["controlMinBalanceMTR", "controlMinBalance"],
                ["controlMaxFeesMTR", "controlMaxFees"],
                ["minBalanceMTR", "minBalance"],
                ["shufflingAmountMTR", "amount"],
                ["monitorAmountMTR", "amount"],
                ["monitorThresholdMTR", "threshold"]
            ];

            for (i = 0; i < mtrFields.length; i++) {
                var mtrField = mtrFields[i][0];
                var mqtField = mtrFields[i][1];
                if (mtrField in data) {
                    data[mqtField] = MRS.convertToMQT(data[mtrField]);
                    delete data[mtrField];
                }
            }
        } catch (err) {
            callback({
                "errorCode": 1,
                "errorDescription": err + " (" + $.t(field) + ")"
            });
            return;
        }
        // convert asset decimal amount to base unit
        try {
            var currencyFields = [
                ["phasingQuorumQNTf", "phasingHoldingDecimals"],
                ["phasingMinBalanceQNTf", "phasingHoldingDecimals"],
                ["controlQuorumQNTf", "controlHoldingDecimals"],
                ["controlMinBalanceQNTf", "controlHoldingDecimals"],
                ["minBalanceQNTf", "create_poll_asset_decimals"],
                ["minBalanceQNTf", "create_poll_ms_decimals"],
                ["amountQNTf", "shuffling_asset_decimals"],
                ["amountQNTf", "shuffling_ms_decimals"]
            ];
            var toDelete = [];
            for (i = 0; i < currencyFields.length; i++) {
                var decimalUnitField = currencyFields[i][0];
                var decimalsField = currencyFields[i][1];
                field = decimalUnitField.replace("QNTf", "");

                if (decimalUnitField in data && decimalsField in data) {
                    data[field] = MRS.convertToQNT(parseFloat(data[decimalUnitField]), parseInt(data[decimalsField]));
                    toDelete.push(decimalUnitField);
                    toDelete.push(decimalsField);
                }
            }
            for (var i = 0; i < toDelete.length; i++) {
                delete data[toDelete[i]];
            }
        } catch (err) {
            callback({
                "errorCode": 1,
                "errorDescription": err + " (" + $.t(field) + ")"
            });
            return;
        }

        //Fill phasing parameters when mandatory approval is enabled
        if (requestType != "approveTransaction"
            && MRS.accountInfo.accountControls && $.inArray('PHASING_ONLY', MRS.accountInfo.accountControls) > -1
                && MRS.accountInfo.phasingOnly
                && MRS.accountInfo.phasingOnly.votingModel >= 0) {

            var phasingControl = MRS.accountInfo.phasingOnly;
            var maxFees = new BigInteger(phasingControl.maxFees);
            if (maxFees > 0 && new BigInteger(data.feeMQT).compareTo(new BigInteger(phasingControl.maxFees)) > 0) {
                callback({
                    "errorCode": 1,
                    "errorDescription": $.t("error_fee_exceeds_max_account_control_fee", {
                        "maxFee": MRS.convertToMTR(phasingControl.maxFees), "symbol": MRS.constants.COIN_SYMBOL
                    })
                });
                return;
            }
            var phasingDuration = parseInt(data.phasingFinishHeight) - MRS.lastBlockHeight;
            var minDuration = parseInt(phasingControl.minDuration) > 0 ? parseInt(phasingControl.minDuration) : 0;
            var maxDuration = parseInt(phasingControl.maxDuration) > 0 ? parseInt(phasingControl.maxDuration) : MRS.constants.SERVER.maxPhasingDuration;

            if (phasingDuration < minDuration || phasingDuration > maxDuration) {
                callback({
                    "errorCode": 1,
                    "errorDescription": $.t("error_finish_height_out_of_account_control_interval", {
                        "min": MRS.lastBlockHeight + minDuration,
                        "max": MRS.lastBlockHeight + maxDuration
                    })
                });
                return;
            }

            var phasingParams = MRS.phasingControlObjectToPhasingParams(phasingControl);
            $.extend(data, phasingParams);
            data.phased = true;

            delete data.phasingHashedSecret;
            delete data.phasingHashedSecretAlgorithm;
            delete data.phasingLinkedFullHash;
        }

        if (!data.recipientPublicKey) {
            delete data.recipientPublicKey;
        }
        if (!data.referencedTransactionFullHash) {
            delete data.referencedTransactionFullHash;
        }

        //gets account id from passphrase client side, used only for login.
        var accountId;
        if (requestType == "getAccountId") {
            accountId = MRS.getAccountId(data.secretPhrase);

            var metroAddress = new MetroAddress();
            var accountRS = "";
            if (metroAddress.set(accountId)) {
                accountRS = metroAddress.toString();
            }
            callback({
                "account": accountId,
                "accountRS": accountRS
            });
            return;
        }
        //check to see if secretPhrase supplied matches logged in account, if not - show error.
        if ("secretPhrase" in data) {
            accountId = MRS.getAccountId(MRS.rememberPassword ? _password : data.secretPhrase);
            if (accountId != MRS.account && !data.calculateFee) {
                callback({
                    "errorCode": 1,
                    "errorDescription": $.t("error_passphrase_incorrect")
                });
            } else {
                //ok, accountId matches..continue with the real request.
                MRS.processAjaxRequest(requestType, data, callback, options);
            }
        } else {
            MRS.processAjaxRequest(requestType, data, callback, options);
        }
    };

    function isVolatileRequest(doNotSign, type, requestType, secretPhrase) {
        if (secretPhrase && MRS.isMobileApp()) {
            return true;
        }
        return (MRS.isPassphraseAtRisk() || doNotSign) && type == "POST" && !MRS.isSubmitPassphrase(requestType);
    }

    MRS.processAjaxRequest = function (requestType, data, callback, options) {
        var extra = null;
        if (data["_extra"]) {
            extra = data["_extra"];
            delete data["_extra"];
        }
        var currentPage = null;
        var currentSubPage = null;

        //means it is a page request, not a global request.. Page requests can be aborted.
        if (requestType.slice(-1) == "+") {
            requestType = requestType.slice(0, -1);
            currentPage = MRS.currentPage;
        } else {
            //not really necessary... we can just use the above code..
            var plusCharacter = requestType.indexOf("+");

            if (plusCharacter > 0) {
                requestType = requestType.substr(0, plusCharacter);
                currentPage = MRS.currentPage;
            }
        }

        if (currentPage && MRS.currentSubPage) {
            currentSubPage = MRS.currentSubPage;
        }

        var httpMethod = (MRS.isRequirePost(requestType) || "secretPhrase" in data || "doNotSign" in data || "adminPassword" in data ? "POST" : "GET");
        if (httpMethod == "GET") {
            if (typeof data == "string") {
                data += "&random=" + Math.random();
            } else {
                data.random = Math.random();
            }
        }

        if ((MRS.isRequirePost(requestType) || "secretPhrase" in data) &&
            MRS.isRequireBlockchain(requestType) && MRS.accountInfo.errorCode && MRS.accountInfo.errorCode == 5) {
            callback({
                "errorCode": 2,
                "errorDescription": $.t("error_new_account")
            }, data);
            return;
        }

        if (data.referencedTransactionFullHash) {
            if (!/^[a-z0-9]{64}$/.test(data.referencedTransactionFullHash)) {
                callback({
                    "errorCode": -1,
                    "errorDescription": $.t("error_invalid_referenced_transaction_hash")
                }, data);
                return;
            }
        }

        var secretPhrase = "";
        var isVolatile = isVolatileRequest(data.doNotSign, httpMethod, requestType, data.secretPhrase);
        if (MRS.isScheduleRequest(requestType)) {
            data.adminPassword = MRS.getAdminPassword();
            if (!extra) {
                extra = {};
            }
            extra.isSchedule = true;
            if (isVolatile || data.calculateFee) {
                // remove the schedule prefix from the request
                var keywordLength = MRS.constants.SCHEDULE_PREFIX.length;
                if (extra && extra.isSchedule && requestType.length >= keywordLength + 1) {
                    requestType = requestType.substring(keywordLength, keywordLength + 1).toLowerCase() + requestType.substring(keywordLength + 1);
                }
            }
        }
        if (isVolatile) {
            if (MRS.rememberPassword) {
                secretPhrase = _password;
            } else {
                secretPhrase = data.secretPhrase;
            }

            delete data.secretPhrase;

            if (MRS.accountInfo && MRS.accountInfo.publicKey) {
                data.publicKey = MRS.accountInfo.publicKey;
            } else if (!data.doNotSign && secretPhrase) {
                data.publicKey = MRS.generatePublicKey(secretPhrase);
                MRS.accountInfo.publicKey = data.publicKey;
            }
            var ecBlock = MRS.constants.LAST_KNOWN_BLOCK;
            data.ecBlockId = ecBlock.id;
            data.ecBlockHeight = ecBlock.height;
        } else if (httpMethod == "POST" && MRS.rememberPassword) {
            data.secretPhrase = _password;
        }

        $.support.cors = true;
        // Used for passing row query string which is too long for a GET request
        if (data.querystring) {
            data = data.querystring;
            httpMethod = "POST";
        }
        var contentType;
        var processData;
        var formData = null;

        var config = MRS.getFileUploadConfig(requestType, data);
        if (config && $(config.selector)[0] && $(config.selector)[0].files[0]) {
            // inspired by http://stackoverflow.com/questions/5392344/sending-multipart-formdata-with-jquery-ajax
            contentType = false;
            processData = false;
            formData = new FormData();
            var file;
            if (data.messageFile) {
                file = data.messageFile;
                delete data.messageFile;
                delete data.encrypt_message;
            } else {
                file = $(config.selector)[0].files[0];
            }
            if (file && file.size > config.maxSize) {
                callback({
                    "errorCode": 3,
                    "errorDescription": $.t(config.errorDescription, {
                        "size": file.size,
                        "allowed": config.maxSize
                    })
                }, data);
                return;
            }
            httpMethod = "POST";
            formData.append(config.requestParam, file);
            for (var key in data) {
                if (!data.hasOwnProperty(key)) {
                    continue;
                }
                if (data[key] instanceof Array) {
                    for (var i = 0; i < data[key].length; i++) {
                        formData.append(key, data[key][i]);
                    }
                } else {
                    formData.append(key, data[key]);
                }
            }
        } else {
            // JQuery defaults
            contentType = "application/x-www-form-urlencoded; charset=UTF-8";
            processData = true;
        }
        var url;
        if (options.remoteNode) {
            url = options.remoteNode.getUrl() + "/metro";
        } else {
            url = MRS.getRequestPath(options.noProxy);
        }
        url += "?requestType=" + requestType;
        MRS.logConsole("Send request " + requestType + " to url " + url);

        $.ajax({
            url: url,
            crossDomain: true,
            dataType: "json",
            type: httpMethod,
            timeout: (options.timeout === undefined ? 30000 : options.timeout),
            async: (options.isAsync === undefined ? true : options.isAsync),
            currentPage: currentPage,
            currentSubPage: currentSubPage,
            shouldRetry: (httpMethod == "GET" ? 2 : undefined),
            traditional: true,
            data: (formData != null ? formData : data),
            contentType: contentType,
            processData: processData
        }).done(function (response) {
            if (typeof data == "string") {
                data = { "querystring": data };
                if (extra) {
                    data["_extra"] = extra;
                }
            }
            if (!options.remoteNode && MRS.isConfirmResponse() &&
                !(response.errorCode || response.errorDescription || response.errorMessage || response.error)) {
                var requestRemoteNode = MRS.isMobileApp() ? MRS.getRemoteNode() : {address: "localhost", announcedAddress: "localhost"}; //TODO unify getRemoteNode with apiProxyPeer
                MRS.confirmResponse(requestType, data, response, requestRemoteNode);
            }
            if (!options.doNotEscape) {
                MRS.escapeResponseObjStrings(response);
            }
            if (MRS.console) {
                MRS.addToConsole(this.url, this.type, this.data, response);
            }
            addAddressData(data);
            if (secretPhrase && response.unsignedTransactionBytes && !data.doNotSign && !response.errorCode && !response.error && !data.calculateFee)  {
                var publicKey = MRS.generatePublicKey(secretPhrase);
                var signature = MRS.signBytes(response.unsignedTransactionBytes, converters.stringToHexString(secretPhrase));

                if (!MRS.verifySignature(signature, response.unsignedTransactionBytes, publicKey, callback)) {
                    return;
                }
                addMissingData(data);
                if (file) {
                    var r = new FileReader();
                    r.onload = function (e) {
                        data.filebytes = e.target.result;
                        data.filename = file.name;
                        MRS.verifyAndSignTransactionBytes(response.unsignedTransactionBytes, signature, requestType, data, callback, response, extra, isVolatile);
                    };
                    r.readAsArrayBuffer(file);
                } else {
                    MRS.verifyAndSignTransactionBytes(response.unsignedTransactionBytes, signature, requestType, data, callback, response, extra, isVolatile);
                }
            } else {
                if (response.errorCode || response.errorDescription || response.errorMessage || response.error) {
                    response.errorDescription = MRS.translateServerError(response);
                    delete response.fullHash;
                    if (!response.errorCode) {
                        response.errorCode = -1;
                    }
                    callback(response, data);
                } else {
                    if (response.broadcasted == false && !data.calculateFee && !MRS.isScheduleRequest(requestType)) {
                        async.waterfall([
                            function (callback) {
                                addMissingData(data);
                                if (!response.unsignedTransactionBytes) {
                                    callback(null);
                                }
                                if (file) {
                                    var r = new FileReader();
                                    r.onload = function (e) {
                                        data.filebytes = e.target.result;
                                        data.filename = file.name;
                                        callback(null);
                                    };
                                    r.readAsArrayBuffer(file);
                                } else {
                                    callback(null);
                                }
                            },
                            function (callback) {
                                if (response.unsignedTransactionBytes &&
                                    !MRS.verifyTransactionBytes(converters.hexStringToByteArray(response.unsignedTransactionBytes), requestType, data, response.transactionJSON.attachment, isVolatile)) {
                                    callback({
                                        "errorCode": 1,
                                        "errorDescription": $.t("error_bytes_validation_server")
                                    }, data);
                                    return;
                                }
                                callback(null);
                            }
                        ], function () {
                            MRS.showRawTransactionModal(response);
                        });
                    } else {
                        if (extra) {
                            data["_extra"] = extra;
                        }
                        callback(response, data);
                        if (data.referencedTransactionFullHash && !response.errorCode) {
                            $.growl($.t("info_referenced_transaction_hash", {
                                "symbol": MRS.constants.COIN_SYMBOL
                            }), {
                                "type": "info"
                            });
                        }
                    }
                }
            }
        }).fail(function (xhr, textStatus, error) {
            MRS.logConsole("Node " + (options.remoteNode ? options.remoteNode.getUrl() : MRS.getRemoteNodeUrl()) + " received an error for request type " + requestType +
                " status " + textStatus + " error " + error);
            if (MRS.console) {
                MRS.addToConsole(this.url, this.type, this.data, error, true);
            }

            if ((error == "error" || textStatus == "error") && (xhr.status == 404 || xhr.status == 0)) {
                if (httpMethod == "POST") {
                    MRS.connectionError();
                }
            }

            if (error != "abort") {
                if (options.remoteNode) {
                    options.remoteNode.blacklist();
                } else {
                    MRS.resetRemoteNode(true);
                }
                if (error == "timeout") {
                    error = $.t("error_request_timeout");
                }
                callback({
                    "errorCode": -1,
                    "errorDescription": error
                }, {});
            }
        });
    };

    MRS.verifyAndSignTransactionBytes = function (transactionBytes, signature, requestType, data, callback, response, extra, isVerifyECBlock) {
        var byteArray = converters.hexStringToByteArray(transactionBytes);
        if (!MRS.verifyTransactionBytes(byteArray, requestType, data, response.transactionJSON.attachment, isVerifyECBlock)) {
            callback({
                "errorCode": 1,
                "errorDescription": $.t("error_bytes_validation_server")
            }, data);
            return;
        }
        var isSchedule = false;
        if (extra) {
            data["_extra"] = extra;
            if (extra.isSchedule) {
                isSchedule = true;
            }
        }
        var payload = transactionBytes.substr(0, 208) + signature + transactionBytes.substr(336);
        if (data.broadcast == "false" && !isSchedule) {
            response.transactionBytes = payload;
            response.transactionJSON.signature = signature;
            MRS.showRawTransactionModal(response);
        } else {
            if (extra) {
                data["_extra"] = extra;
            }
            MRS.broadcastTransactionBytes(payload, callback, response, data, isSchedule, requestType);
        }
    };

    MRS.verifyTransactionBytes = function (byteArray, requestType, data, attachment, isVerifyECBlock) {
        var transaction = {};
        transaction.type = byteArray[0];
        transaction.version = (byteArray[1] & 0xF0) >> 4;
        transaction.subtype = byteArray[1] & 0x0F;
        transaction.timestamp = String(converters.byteArrayToBigInteger(byteArray, 2));
        transaction.deadline = String(converters.byteArrayToSignedShort(byteArray, 10));
        transaction.publicKey = converters.byteArrayToHexString(byteArray.slice(12, 44));
        transaction.recipient = String(converters.byteArrayToVeryBigInteger(byteArray, 44));
        transaction.amountMQT = String(converters.byteArrayToBigInteger(byteArray, 56));
        transaction.feeMQT = String(converters.byteArrayToBigInteger(byteArray, 64));

        var refHash = byteArray.slice(72, 104);
        transaction.referencedTransactionFullHash = converters.byteArrayToHexString(refHash);
        if (transaction.referencedTransactionFullHash == "0000000000000000000000000000000000000000000000000000000000000000") {
            transaction.referencedTransactionFullHash = "";
        }
        transaction.flags = 0;
        if (transaction.version > 0) {
            transaction.flags = converters.byteArrayToSignedInt32(byteArray, 168);
            transaction.ecBlockHeight = String(converters.byteArrayToSignedInt32(byteArray, 172));
            transaction.ecBlockId = String(converters.byteArrayToBigInteger(byteArray, 176));
            if (isVerifyECBlock) {
                var ecBlock = MRS.constants.LAST_KNOWN_BLOCK;
                if (ecBlock.id != "0") {
                    if (transaction.ecBlockHeight != ecBlock.height) {
                        return false;
                    }
                    if (transaction.ecBlockId != ecBlock.id) {
                        return false;
                    }
                }
            }
        }

        if (transaction.publicKey != MRS.accountInfo.publicKey && transaction.publicKey != data.publicKey) {
            return false;
        }

        if (transaction.deadline !== data.deadline) {
            return false;
        }

        if (transaction.recipient !== data.recipient) {
            if ((data.recipient == MRS.constants.GENESIS || data.recipient == "") && transaction.recipient == "0") {
                //ok
            } else {
                return false;
            }
        }

        if (transaction.amountMQT !== data.amountMQT) {
            return false;
        }

        if ("referencedTransactionFullHash" in data) {
            if (transaction.referencedTransactionFullHash !== data.referencedTransactionFullHash) {
                return false;
            }
        } else if (transaction.referencedTransactionFullHash !== "") {
            return false;
        }
        var pos;
        if (transaction.version > 0) {
            //has empty attachment, so no attachmentVersion byte...
            if (requestType == "sendMoney" || requestType == "sendMessage") {
                pos = 184;
            } else {
                pos = 185;
            }
        } else {
            pos = 168;
        }
        return MRS.verifyTransactionTypes(byteArray, transaction, requestType, data, pos, attachment);
    };

    MRS.verifyTransactionTypes = function (byteArray, transaction, requestType, data, pos, attachment) {
        var length = 0;
        var i=0;
        var serverHash, sha256, utfBytes, isText, hashWords, calculatedHash;
        switch (requestType) {
            case "sendMoney":
                if (transaction.type !== 0 || transaction.subtype !== 0) {
                    return false;
                }
                break;
            case "sendMessage":
                if (transaction.type !== 1 || transaction.subtype !== 0) {
                    return false;
                }
                break;
            case "setAlias":
                if (transaction.type !== 1 || transaction.subtype !== 1) {
                    return false;
                }
                length = parseInt(byteArray[pos], 10);
                pos++;
                transaction.aliasName = converters.byteArrayToString(byteArray, pos, length);
                pos += length;
                length = converters.byteArrayToSignedShort(byteArray, pos);
                pos += 2;
                transaction.aliasURI = converters.byteArrayToString(byteArray, pos, length);
                pos += length;
                if (transaction.aliasName !== data.aliasName || transaction.aliasURI !== data.aliasURI) {
                    return false;
                }
                break;
            case "createPoll":
                if (transaction.type !== 1 || transaction.subtype !== 2) {
                    return false;
                }
                length = converters.byteArrayToSignedShort(byteArray, pos);
                pos += 2;
                transaction.name = converters.byteArrayToString(byteArray, pos, length);
                pos += length;
                length = converters.byteArrayToSignedShort(byteArray, pos);
                pos += 2;
                transaction.description = converters.byteArrayToString(byteArray, pos, length);
                pos += length;
                transaction.finishHeight = converters.byteArrayToSignedInt32(byteArray, pos);
                pos += 4;
                var nr_options = byteArray[pos];
                pos++;

                for (i = 0; i < nr_options; i++) {
                    var optionLength = converters.byteArrayToSignedShort(byteArray, pos);
                    pos += 2;
                    transaction["option" + (i < 10 ? "0" + i : i)] = converters.byteArrayToString(byteArray, pos, optionLength);
                    pos += optionLength;
                }
                transaction.votingModel = String(byteArray[pos]);
                pos++;
                transaction.minNumberOfOptions = String(byteArray[pos]);
                pos++;
                transaction.maxNumberOfOptions = String(byteArray[pos]);
                pos++;
                transaction.minRangeValue = String(byteArray[pos]);
                pos++;
                transaction.maxRangeValue = String(byteArray[pos]);
                pos++;
                transaction.minBalance = String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;
                transaction.minBalanceModel = String(byteArray[pos]);
                pos++;
                transaction.holding = String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;

                if (transaction.name !== data.name || transaction.description !== data.description ||
                    transaction.minNumberOfOptions !== data.minNumberOfOptions || transaction.maxNumberOfOptions !== data.maxNumberOfOptions) {
                    return false;
                }

                for (i = 0; i < nr_options; i++) {
                    if (transaction["option" + (i < 10 ? "0" + i : i)] !== data["option" + (i < 10 ? "0" + i : i)]) {
                        return false;
                    }
                }

                if (("option" + (i < 10 ? "0" + i : i)) in data) {
                    return false;
                }
                break;
            case "castVote":
                if (transaction.type !== 1 || transaction.subtype !== 3) {
                    return false;
                }
                transaction.poll = String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;
                var voteLength = byteArray[pos];
                pos++;
                transaction.votes = [];

                for (i = 0; i < voteLength; i++) {
                    transaction["vote" + (i < 10 ? "0" + i : i)] = byteArray[pos];
                    pos++;
                }
                if (transaction.poll !== data.poll) {
                    return false;
                }
                break;
            case "hubAnnouncement":
                if (transaction.type !== 1 || transaction.subtype != 4) {
                    return false;
                }
                return false;
                break;
            case "setAccountInfo":
                if (transaction.type !== 1 || transaction.subtype != 5) {
                    return false;
                }
                length = parseInt(byteArray[pos], 10);
                pos++;
                transaction.name = converters.byteArrayToString(byteArray, pos, length);
                pos += length;
                length = converters.byteArrayToSignedShort(byteArray, pos);
                pos += 2;
                transaction.description = converters.byteArrayToString(byteArray, pos, length);
                pos += length;
                if (transaction.name !== data.name || transaction.description !== data.description) {
                    return false;
                }
                break;
            case "sellAlias":
                if (transaction.type !== 1 || transaction.subtype !== 6) {
                    return false;
                }
                length = parseInt(byteArray[pos], 10);
                pos++;
                transaction.alias = converters.byteArrayToString(byteArray, pos, length);
                pos += length;
                transaction.priceMQT = String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;
                if (transaction.alias !== data.aliasName || transaction.priceMQT !== data.priceMQT) {
                    return false;
                }
                break;
            case "buyAlias":
                if (transaction.type !== 1 && transaction.subtype !== 7) {
                    return false;
                }
                length = parseInt(byteArray[pos], 10);
                pos++;
                transaction.alias = converters.byteArrayToString(byteArray, pos, length);
                pos += length;
                if (transaction.alias !== data.aliasName) {
                    return false;
                }
                break;
            case "deleteAlias":
                if (transaction.type !== 1 && transaction.subtype !== 8) {
                    return false;
                }
                length = parseInt(byteArray[pos], 10);
                pos++;
                transaction.alias = converters.byteArrayToString(byteArray, pos, length);
                pos += length;
                if (transaction.alias !== data.aliasName) {
                    return false;
                }
                break;
            case "approveTransaction":
                if (transaction.type !== 1 && transaction.subtype !== 9) {
                    return false;
                }
                var fullHashesLength = byteArray[pos];
                if (fullHashesLength !== 1) {
                    return false;
                }
                pos++;
                transaction.transactionFullHash = converters.byteArrayToHexString(byteArray.slice(pos, pos + 32));
                pos += 32;
                if (transaction.transactionFullHash !== data.transactionFullHash) {
                    return false;
                }
                transaction.revealedSecretLength = converters.byteArrayToSignedInt32(byteArray, pos);
                pos += 4;
                if (transaction.revealedSecretLength > 0) {
                    transaction.revealedSecret = converters.byteArrayToHexString(byteArray.slice(pos, pos + transaction.revealedSecretLength));
                    pos += transaction.revealedSecretLength;
                }
                if (transaction.revealedSecret !== data.revealedSecret &&
                    transaction.revealedSecret !== converters.byteArrayToHexString(MRS.getUtf8Bytes(data.revealedSecretText))) {
                    return false;
                }
                break;
            case "setAccountProperty":
                if (transaction.type !== 1 && transaction.subtype !== 10) {
                    return false;
                }
                length = byteArray[pos];
                pos++;
                if (converters.byteArrayToString(byteArray, pos, length) !== data.property) {
                    return false;
                }
                pos += length;
                length = byteArray[pos];
                pos++;
                if (converters.byteArrayToString(byteArray, pos, length) !== data.value) {
                    return false;
                }
                pos += length;
                break;
            case "deleteAccountProperty":
                if (transaction.type !== 1 && transaction.subtype !== 11) {
                    return false;
                }
                // no way to validate the property id, just skip it
                String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;
                break;
            case "issueAsset":
                if (transaction.type !== 2 || transaction.subtype !== 0) {
                    return false;
                }
                length = byteArray[pos];
                pos++;
                transaction.name = converters.byteArrayToString(byteArray, pos, length);
                pos += length;
                length = converters.byteArrayToSignedShort(byteArray, pos);
                pos += 2;
                transaction.description = converters.byteArrayToString(byteArray, pos, length);
                pos += length;
                transaction.quantityQNT = String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;
                transaction.decimals = String(byteArray[pos]);
                pos++;
                if (transaction.name !== data.name || transaction.description !== data.description || transaction.quantityQNT !== data.quantityQNT || transaction.decimals !== data.decimals) {
                    return false;
                }
                break;
            case "transferAsset":
                if (transaction.type !== 2 || transaction.subtype !== 1) {
                    return false;
                }
                transaction.asset = String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;
                transaction.quantityQNT = String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;
                if (transaction.asset !== data.asset || transaction.quantityQNT !== data.quantityQNT) {
                    return false;
                }
                break;
            case "placeAskOrder":
            case "placeBidOrder":
                if (transaction.type !== 2) {
                    return false;
                } else if (requestType == "placeAskOrder" && transaction.subtype !== 2) {
                    return false;
                } else if (requestType == "placeBidOrder" && transaction.subtype !== 3) {
                    return false;
                }
                transaction.asset = String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;
                transaction.quantityQNT = String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;
                transaction.priceMQT = String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;
                if (transaction.asset !== data.asset || transaction.quantityQNT !== data.quantityQNT || transaction.priceMQT !== data.priceMQT) {
                    return false;
                }
                break;
            case "cancelAskOrder":
            case "cancelBidOrder":
                if (transaction.type !== 2) {
                    return false;
                } else if (requestType == "cancelAskOrder" && transaction.subtype !== 4) {
                    return false;
                } else if (requestType == "cancelBidOrder" && transaction.subtype !== 5) {
                    return false;
                }
                transaction.order = String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;
                if (transaction.order !== data.order) {
                    return false;
                }
                break;
            case "deleteAssetShares":
                if (transaction.type !== 2 || transaction.subtype !== 7) {
                    return false;
                }
                transaction.asset = String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;
                transaction.quantityQNT = String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;
                if (transaction.asset !== data.asset || transaction.quantityQNT !== data.quantityQNT) {
                    return false;
                }
                break;
            case "dividendPayment":
                if (transaction.type !== 2 || transaction.subtype !== 6) {
                    return false;
                }
                transaction.asset = String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;
                transaction.height = String(converters.byteArrayToSignedInt32(byteArray, pos));
                pos += 4;
                transaction.amountMQTPerQNT = String(converters.byteArrayToBigInteger(byteArray, pos));
                pos += 8;
                if (transaction.asset !== data.asset ||
                    transaction.height !== data.height ||
                    transaction.amountMQTPerQNT !== data.amountMQTPerQNT) {
                    return false;
                }
                break;
            case "leaseBalance":
                if (transaction.type !== 4 && transaction.subtype !== 0) {
                    return false;
                }
                transaction.period = String(converters.byteArrayToSignedShort(byteArray, pos));
                pos += 2;
                if (transaction.period !== data.period) {
                    return false;
                }
                break;
            case "setPhasingOnlyControl":
                if (transaction.type !== 4 && transaction.subtype !== 1) {
                    return false;
                }
                return validateCommonPhasingData(byteArray, pos, data, "control") != -1;
                break;
            case "shufflingCreate":
                if (transaction.type !== 7 && transaction.subtype !== 0) {
                    return false;
                }
                var holding = String(converters.byteArrayToBigInteger(byteArray, pos));
                if (holding !== "0" && holding !== data.holding ||
                    holding === "0" && data.holding !== undefined && data.holding !== "" && data.holding !== "0") {
                    return false;
                }
                pos += 8;
                var holdingType = String(byteArray[pos]);
                if (holdingType !== "0" && holdingType !== data.holdingType ||
                    holdingType === "0" && data.holdingType !== undefined && data.holdingType !== "" && data.holdingType !== "0") {
                    return false;
                }
                pos++;
                var amount = String(converters.byteArrayToBigInteger(byteArray, pos));
                if (amount !== data.amount) {
                    return false;
                }
                pos += 8;
                var participantCount = String(byteArray[pos]);
                if (participantCount !== data.participantCount) {
                    return false;
                }
                pos++;
                var registrationPeriod = converters.byteArrayToSignedShort(byteArray, pos);
                if (registrationPeriod !== data.registrationPeriod) {
                    return false;
                }
                pos += 2;
                break;
            default:
                //invalid requestType..
                return false;
        }

        var position = 1;
        var attachmentVersion;
        //non-encrypted message
        if ((transaction.flags & position) != 0 ||
            ((requestType == "sendMessage" && data.message && !(data.messageIsPrunable === "true")))) {
            attachmentVersion = byteArray[pos];
            if (attachmentVersion < 0 || attachmentVersion > 2) {
                return false;
            }
            pos++;
            var messageLength = converters.byteArrayToSignedInt32(byteArray, pos);
            transaction.messageIsText = messageLength < 0; // ugly hack??
            if (messageLength < 0) {
                messageLength &= MRS.constants.MAX_INT_JAVA;
            }
            pos += 4;
            if (transaction.messageIsText) {
                transaction.message = converters.byteArrayToString(byteArray, pos, messageLength);
            } else {
                var slice = byteArray.slice(pos, pos + messageLength);
                transaction.message = converters.byteArrayToHexString(slice);
            }
            pos += messageLength;
            var messageIsText = (transaction.messageIsText ? "true" : "false");
            if (messageIsText != data.messageIsText) {
                return false;
            }
            if (transaction.message !== data.message) {
                return false;
            }
        } else if (data.message && !(data.messageIsPrunable === "true")) {
            return false;
        }

        position <<= 1;

        //encrypted note
        if ((transaction.flags & position) != 0) {
            attachmentVersion = byteArray[pos];
            if (attachmentVersion < 0 || attachmentVersion > 2) {
                return false;
            }
            pos++;
            var encryptedMessageLength = converters.byteArrayToSignedInt32(byteArray, pos);
            transaction.messageToEncryptIsText = encryptedMessageLength < 0;
            if (encryptedMessageLength < 0) {
                encryptedMessageLength &= MRS.constants.MAX_INT_JAVA;
            }
            pos += 4;
            transaction.encryptedMessageData = converters.byteArrayToHexString(byteArray.slice(pos, pos + encryptedMessageLength));
            pos += encryptedMessageLength;
            transaction.encryptedMessageNonce = converters.byteArrayToHexString(byteArray.slice(pos, pos + 32));
            pos += 32;
            var messageToEncryptIsText = (transaction.messageToEncryptIsText ? "true" : "false");
            if (messageToEncryptIsText != data.messageToEncryptIsText) {
                return false;
            }
            if (transaction.encryptedMessageData !== data.encryptedMessageData || transaction.encryptedMessageNonce !== data.encryptedMessageNonce) {
                return false;
            }
        } else if (data.encryptedMessageData && !(data.encryptedMessageIsPrunable === "true")) {
            return false;
        }

        position <<= 1;

        if ((transaction.flags & position) != 0) {
            attachmentVersion = byteArray[pos];
            if (attachmentVersion < 0 || attachmentVersion > 2) {
                return false;
            }
            pos++;
            var recipientPublicKey = converters.byteArrayToHexString(byteArray.slice(pos, pos + 32));
            if (recipientPublicKey != data.recipientPublicKey) {
                return false;
            }
            pos += 32;
        } else if (data.recipientPublicKey) {
            return false;
        }

        position <<= 1;

        if ((transaction.flags & position) != 0) {
            attachmentVersion = byteArray[pos];
            if (attachmentVersion < 0 || attachmentVersion > 2) {
                return false;
            }
            pos++;
            var encryptedToSelfMessageLength = converters.byteArrayToSignedInt32(byteArray, pos);
            transaction.messageToEncryptToSelfIsText = encryptedToSelfMessageLength < 0;
            if (encryptedToSelfMessageLength < 0) {
                encryptedToSelfMessageLength &= MRS.constants.MAX_INT_JAVA;
            }
            pos += 4;
            transaction.encryptToSelfMessageData = converters.byteArrayToHexString(byteArray.slice(pos, pos + encryptedToSelfMessageLength));
            pos += encryptedToSelfMessageLength;
            transaction.encryptToSelfMessageNonce = converters.byteArrayToHexString(byteArray.slice(pos, pos + 32));
            pos += 32;
            var messageToEncryptToSelfIsText = (transaction.messageToEncryptToSelfIsText ? "true" : "false");
            if (messageToEncryptToSelfIsText != data.messageToEncryptToSelfIsText) {
                return false;
            }
            if (transaction.encryptToSelfMessageData !== data.encryptToSelfMessageData || transaction.encryptToSelfMessageNonce !== data.encryptToSelfMessageNonce) {
                return false;
            }
        } else if (data.encryptToSelfMessageData) {
            return false;
        }

        position <<= 1;

        if ((transaction.flags & position) != 0) {
            attachmentVersion = byteArray[pos];
            if (attachmentVersion < 0 || attachmentVersion > 2) {
                return false;
            }
            pos++;
            if (String(converters.byteArrayToSignedInt32(byteArray, pos)) !== data.phasingFinishHeight) {
                return false;
            }
            pos += 4;
            pos = validateCommonPhasingData(byteArray, pos, data, "phasing");
            if (pos == -1) {
                return false;
            }
            var linkedFullHashesLength = byteArray[pos];
            pos++;
            for (i = 0; i < linkedFullHashesLength; i++) {
                var fullHash = converters.byteArrayToHexString(byteArray.slice(pos, pos + 32));
                pos += 32;
                if (fullHash !== data.phasingLinkedFullHash[i]) {
                    return false;
                }
            }
            var hashedSecretLength = byteArray[pos];
            pos++;
            if (hashedSecretLength > 0 && converters.byteArrayToHexString(byteArray.slice(pos, pos + hashedSecretLength)) !== data.phasingHashedSecret) {
                return false;
            }
            pos += hashedSecretLength;
            var algorithm = String(byteArray[pos]);
            if (algorithm !== "0" && algorithm !== data.phasingHashedSecretAlgorithm) {
                return false;
            }
            pos++;
        }

        position <<= 1;

        if ((transaction.flags & position) != 0) {
            attachmentVersion = byteArray[pos];
            if (attachmentVersion < 0 || attachmentVersion > 2) {
                return false;
            }
            pos++;
            serverHash = converters.byteArrayToHexString(byteArray.slice(pos, pos + 32));
            pos += 32;
            sha256 = CryptoJS.algo.SHA256.create();
            isText = [];
            if (data.messageIsText == "true") {
                isText.push(1);
            } else {
                isText.push(0);
            }
            sha256.update(converters.byteArrayToWordArrayEx(isText));
            if (data.filebytes) {
                utfBytes = new Int8Array(data.filebytes);
            } else {
                utfBytes = MRS.getUtf8Bytes(data.message);
            }
            sha256.update(converters.byteArrayToWordArrayEx(utfBytes));
            hashWords = sha256.finalize();
            calculatedHash = converters.wordArrayToByteArrayEx(hashWords);
            if (serverHash !== converters.byteArrayToHexString(calculatedHash)) {
                return false;
            }
        }
        position <<= 1;

        if ((transaction.flags & position) != 0) {
            attachmentVersion = byteArray[pos];
            if (attachmentVersion < 0 || attachmentVersion > 2) {
                return false;
            }
            pos++;
            serverHash = converters.byteArrayToHexString(byteArray.slice(pos, pos + 32));
            sha256 = CryptoJS.algo.SHA256.create();
            if (data.messageToEncryptIsText == "true") {
                sha256.update(converters.byteArrayToWordArrayEx([1]));
            } else {
                sha256.update(converters.byteArrayToWordArrayEx([0]));
            }
            sha256.update(converters.byteArrayToWordArrayEx([1])); // compression
            if (data.filebytes) {
                utfBytes = new Int8Array(data.filebytes);
            } else {
                utfBytes = converters.hexStringToByteArray(data.encryptedMessageData);
            }
            sha256.update(converters.byteArrayToWordArrayEx(utfBytes));
            sha256.update(converters.byteArrayToWordArrayEx(converters.hexStringToByteArray(data.encryptedMessageNonce)));
            hashWords = sha256.finalize();
            calculatedHash = converters.wordArrayToByteArrayEx(hashWords);
            if (serverHash !== converters.byteArrayToHexString(calculatedHash)) {
                return false;
            }
        }

        return true;
    };

    MRS.broadcastTransactionBytes = function (transactionData, callback, originalResponse, originalData, isSchedule, requestType) {
        var data = {
            "transactionBytes": transactionData,
            "prunableAttachmentJSON": JSON.stringify(originalResponse.transactionJSON.attachment),
            "adminPassword": MRS.getAdminPassword()
        };
        if (isSchedule) {
            requestType = MRS.constants.SCHEDULE_PREFIX + requestType.substring(0, 1).toUpperCase() + requestType.substring(1);
            data.offerIssuer = originalData.offerIssuer; // Specific to currency buy
        } else {
            requestType = MRS.state.apiProxy ? "sendTransaction": "broadcastTransaction";
        }
        $.ajax({
            url: MRS.getRequestPath() + "?requestType=" + requestType,
            crossDomain: true,
            dataType: "json",
            type: "POST",
            timeout: 30000,
            async: true,
            data: data
        }).done(function (response) {
            MRS.escapeResponseObjStrings(response);
            if (MRS.console) {
                MRS.addToConsole(this.url, this.type, this.data, response);
            }

            if (response.errorCode) {
                if (!response.errorDescription) {
                    response.errorDescription = (response.errorMessage ? response.errorMessage : "Unknown error occurred.");
                }
                callback(response, originalData);
            } else if (response.error) {
                response.errorCode = 1;
                response.errorDescription = response.error;
                callback(response, originalData);
            } else {
                if ("transactionBytes" in originalResponse) {
                    delete originalResponse.transactionBytes;
                }
                originalResponse.broadcasted = true;
                originalResponse.transaction = response.transaction;
                originalResponse.fullHash = response.fullHash;
                callback(originalResponse, originalData);
                if (originalData.referencedTransactionFullHash) {
                    $.growl($.t("info_referenced_transaction_hash", {
                        "symbol": MRS.constants.COIN_SYMBOL
                    }), {
                        "type": "info"
                    });
                }
            }
        }).fail(function (xhr, textStatus, error) {
            MRS.logConsole("request failed, status: " + textStatus + ", error: " + error);
            if (MRS.console) {
                MRS.addToConsole(this.url, this.type, this.data, error, true);
            }
            MRS.resetRemoteNode(true);
            if (error == "timeout") {
                error = $.t("error_request_timeout");
            }
            callback({
                "errorCode": -1,
                "errorDescription": error
            }, {});
        });
    };

    MRS.generateQRCode = function(target, qrCodeData, minType, cellSize) {
        var type = minType ? minType : 2;
        while (type <= 40) {
            try {
                var qr = qrcode(type, 'M');
                qr.addData(qrCodeData);
                qr.make();
                var img = qr.createImgTag(cellSize);
                MRS.logConsole("Encoded QR code of type " + type);
                if (target) {
                    $(target).empty().append(img);
                }
                return img;
            } catch (e) {
                type++;
            }
        }
        $(target).empty().html($.t("cannot_encode_message", qrCodeData.length));
    };

    function addAddressData(data) {
        if (typeof data == "object" && ("recipient" in data)) {
            var address = new MetroAddress();
            if (MRS.isRsAccount(data.recipient)) {
                data.recipientRS = data.recipient;
                if (address.set(data.recipient)) {
                    data.recipient = address.account_id();
                }
            } else {
                if (address.set(data.recipient)) {
                    data.recipientRS = address.toString();
                }
            }
        }
    }

    function addMissingData(data) {
        if (!("amountMQT" in data)) {
            data.amountMQT = "0";
        }
        if (!("recipient" in data)) {
            data.recipient = MRS.constants.GENESIS;
            data.recipientRS = MRS.constants.GENESIS_RS;
        }
    }

    function validateCommonPhasingData(byteArray, pos, data, prefix) {
        if (byteArray[pos] != (parseInt(data[prefix + "VotingModel"]) & 0xFF)) {
            return -1;
        }
        pos++;
        var quorum = String(converters.byteArrayToBigInteger(byteArray, pos));
        if (quorum !== "0" && quorum !== String(data[prefix + "Quorum"])) {
            return -1;
        }
        pos += 8;
        var minBalance = String(converters.byteArrayToBigInteger(byteArray, pos));
        if (minBalance !== "0" && minBalance !== data[prefix + "MinBalance"]) {
            return -1;
        }
        pos += 8;
        var whiteListLength = byteArray[pos];
        pos++;
        for (var i = 0; i < whiteListLength; i++) {
            var accountId = converters.byteArrayToBigInteger(byteArray, pos);
            var accountRS = MRS.convertNumericToRSAccountFormat(accountId);
            pos += 8;
            if (String(accountId) !== data[prefix + "Whitelisted"][i] && String(accountRS) !== data[prefix + "Whitelisted"][i]) {
                return -1;
            }
        }
        var holdingId = String(converters.byteArrayToBigInteger(byteArray, pos));
        if (holdingId !== "0" && holdingId !== data[prefix + "Holding"]) {
            return -1;
        }
        pos += 8;
        var minBalanceModel = String(byteArray[pos]);
        if (minBalanceModel !== "0" && minBalanceModel !== String(data[prefix + "MinBalanceModel"])) {
            return -1;
        }
        pos++;
        return pos;
    }

    return MRS;
}(isNode ? client : MRS || {}, jQuery));

if (isNode) {
    module.exports = MRS;
}
