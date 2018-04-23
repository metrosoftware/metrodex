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
var MRS = (function (MRS, $) {
    MRS.constants = {
        'DB_VERSION': 2,

        'PLUGIN_VERSION': 1,
        'MAX_SHORT_JAVA': 32767,
        'MAX_UNSIGNED_SHORT_JAVA': 65535,
        'MAX_INT_JAVA': 2147483647,
        'MIN_PRUNABLE_MESSAGE_LENGTH': 28,
        'DISABLED_API_ERROR_CODE': 16,

        //Plugin launch status numbers
        'PL_RUNNING': 1,
        'PL_PAUSED': 2,
        'PL_DEACTIVATED': 3,
        'PL_HALTED': 4,

        //Plugin validity status codes
        'PV_VALID': 100,
        'PV_NOT_VALID': 300,
        'PV_UNKNOWN_MANIFEST_VERSION': 301,
        'PV_INCOMPATIBLE_MANIFEST_VERSION': 302,
        'PV_INVALID_MANIFEST_FILE': 303,
        'PV_INVALID_MISSING_FILES': 304,
        'PV_INVALID_JAVASCRIPT_FILE': 305,

        //Plugin MRS compatibility status codes
        'PNC_COMPATIBLE': 100,
        'PNC_COMPATIBILITY_MINOR_RELEASE_DIFF': 101,
        'PNC_COMPATIBILITY_WARNING': 200,
        'PNC_COMPATIBILITY_MAJOR_RELEASE_DIFF': 202,
        'PNC_NOT_COMPATIBLE': 300,
        'PNC_COMPATIBILITY_UNKNOWN': 301,
        'PNC_COMPATIBILITY_CLIENT_VERSION_TOO_OLD': 302,

        'VOTING_MODELS': {},
        'MIN_BALANCE_MODELS': {},
        "HASH_ALGORITHMS": {},
        "PHASING_HASH_ALGORITHMS": {},
        "MINTING_HASH_ALGORITHMS": {},
        "REQUEST_TYPES": {},
        "API_TAGS": {},

        'SERVER': {},
        'MAX_TAGGED_DATA_DATA_LENGTH': 0,
        'MAX_PRUNABLE_MESSAGE_LENGTH': 0,
        'GENESIS': '',
        'GENESIS_RS': '',
        'EPOCH_BEGINNING': 0,
        'FORGING': 'forging',
        'NOT_FORGING': 'not_forging',
        'UNKNOWN': 'unknown',
        'MINING_ALLOWED': 'mining_allowed',
        'MINING' : 'mining',
        'NOT_MINING' : 'not_mining',
        'LAST_KNOWN_BLOCK': { id: "0", height: "0" },
        'LAST_KNOWN_TESTNET_BLOCK': { id: "0", height: "0" },
        'INITIAL_BASE_TARGET': 153722867,
        'SCHEDULE_PREFIX': "schedule"
    };

    MRS.loadAlgorithmList = function (algorithmSelect, isPhasingHash) {
        var hashAlgorithms;
        if (isPhasingHash) {
            hashAlgorithms = MRS.constants.PHASING_HASH_ALGORITHMS;
        } else {
            hashAlgorithms = MRS.constants.HASH_ALGORITHMS;
        }
        for (var key in hashAlgorithms) {
            if (hashAlgorithms.hasOwnProperty(key)) {
                algorithmSelect.append($("<option />").val(hashAlgorithms[key]).text(key));
            }
        }
    };

    MRS.getRsAccountRegex = function(accountPrefix, withoutSeparator) {
        if (withoutSeparator) {
            return new RegExp("^" + accountPrefix + "-[A-Z0-9]{17}", "i");
        }
        return new RegExp(MRS.constants.ACCOUNT_REGEX_STR, "i");
    };

    MRS.getNumericAccountRegex = function() {
        return new RegExp("^\\d+$");
    };

    MRS.processConstants = function(response, resolve) {
        if (response.genesisAccountId) {
            MRS.constants.SERVER = response;
            MRS.constants.VOTING_MODELS = response.votingModels;
            MRS.constants.MIN_BALANCE_MODELS = response.minBalanceModels;
            MRS.constants.HASH_ALGORITHMS = response.hashAlgorithms;
            MRS.constants.PHASING_HASH_ALGORITHMS = response.phasingHashAlgorithms;
            MRS.constants.MAX_PRUNABLE_MESSAGE_LENGTH = response.maxPrunableMessageLength;
            MRS.constants.GENESIS = response.genesisAccountId;
            MRS.constants.EPOCH_BEGINNING = response.epochBeginning;
            MRS.constants.REQUEST_TYPES = response.requestTypes;
            MRS.constants.API_TAGS = response.apiTags;
            MRS.constants.SHUFFLING_STAGES = response.shufflingStages;
            MRS.constants.SHUFFLING_PARTICIPANTS_STATES = response.shufflingParticipantStates;
            MRS.constants.DISABLED_APIS = response.disabledAPIs;
            MRS.constants.DISABLED_API_TAGS = response.disabledAPITags;
            MRS.constants.PEER_STATES = response.peerStates;
            MRS.constants.LAST_KNOWN_BLOCK.id = response.genesisBlockId;
            MRS.loadTransactionTypeConstants(response);
            MRS.constants.PROXY_NOT_FORWARDED_REQUESTS = response.proxyNotForwardedRequests;
            MRS.constants.COIN_SYMBOL = response.coinSymbol;
            $(".coin-symbol").html(response.coinSymbol);
            MRS.constants.ACCOUNT_PREFIX = response.accountPrefix;
            MRS.constants.PROJECT_NAME = response.projectName;
            MRS.constants.ACCOUNT_REGEX_STR = "^" + response.accountPrefix + "-[A-Z0-9_]{4}-[A-Z0-9_]{4}-[A-Z0-9_]{4}-[A-Z0-9_]{5}";
            MRS.constants.ACCOUNT_RS_MATCH = MRS.getRsAccountRegex(response.accountPrefix);
            MRS.constants.ACCOUNT_NUMERIC_MATCH = MRS.getNumericAccountRegex();
            MRS.constants.ACCOUNT_MASK_ASTERIX = response.accountPrefix + "-****-****-****-*****";
            MRS.constants.ACCOUNT_MASK_UNDERSCORE = response.accountPrefix + "-____-____-____-_____";
            MRS.constants.ACCOUNT_MASK_PREFIX = response.accountPrefix + "-";
            MRS.constants.GENESIS_RS = converters.convertNumericToRSAccountFormat(response.genesisAccountId);
            MRS.constants.INITIAL_BASE_TARGET = parseInt(response.initialBaseTarget);
            console.log("done loading server constants");
            if (resolve) {
                resolve();
            }
        }
    };

    MRS.loadServerConstants = function(resolve) {
        function processConstants(response) {
            MRS.processConstants(response, resolve);
        }
        if (MRS.isMobileApp()) {
            jQuery.ajaxSetup({ async: false });
            $.getScript("js/data/constants.js" );
            jQuery.ajaxSetup({async: true});
            processConstants(MRS.constants.SERVER);
        } else {
            if (isNode) {
                client.sendRequest("getConstants", {}, processConstants, false);
            } else {
                MRS.sendRequest("getConstants", {}, processConstants, false);
            }
        }
    };

    function getKeyByValue(map, value) {
        for (var key in map) {
            if (map.hasOwnProperty(key)) {
                if (value === map[key]) {
                    return key;
                }
            }
        }
        return null;
    }

    MRS.getVotingModelName = function (code) {
        return getKeyByValue(MRS.constants.VOTING_MODELS, code);
    };

    MRS.getVotingModelCode = function (name) {
        return MRS.constants.VOTING_MODELS[name];
    };

    MRS.getMinBalanceModelName = function (code) {
        return getKeyByValue(MRS.constants.MIN_BALANCE_MODELS, code);
    };

    MRS.getMinBalanceModelCode = function (name) {
        return MRS.constants.MIN_BALANCE_MODELS[name];
    };

    MRS.getHashAlgorithm = function (code) {
        return getKeyByValue(MRS.constants.HASH_ALGORITHMS, code);
    };

    MRS.getShufflingStage = function (code) {
        return getKeyByValue(MRS.constants.SHUFFLING_STAGES, code);
    };

    MRS.getShufflingParticipantState = function (code) {
        return getKeyByValue(MRS.constants.SHUFFLING_PARTICIPANTS_STATES, code);
    };

    MRS.getPeerState = function (code) {
        return getKeyByValue(MRS.constants.PEER_STATES, code);
    };

    MRS.isRequireBlockchain = function(requestType) {
        if (!MRS.constants.REQUEST_TYPES[requestType]) {
            // For requests invoked before the getConstants request returns,
            // we implicitly assume that they do not require the blockchain
            return false;
        }
        return true == MRS.constants.REQUEST_TYPES[requestType].requireBlockchain;
    };

    MRS.isRequireFullClient = function(requestType) {
        if (!MRS.constants.REQUEST_TYPES[requestType]) {
            // For requests invoked before the getConstants request returns,
            // we implicitly assume that they do not require full client
            return false;
        }
        return true == MRS.constants.REQUEST_TYPES[requestType].requireFullClient;
    };

    MRS.isRequestForwardable = function(requestType) {
        return MRS.isRequireBlockchain(requestType) &&
            !MRS.isRequireFullClient(requestType) &&
            (!(MRS.constants.PROXY_NOT_FORWARDED_REQUESTS instanceof Array) ||
            MRS.constants.PROXY_NOT_FORWARDED_REQUESTS.indexOf(requestType) < 0);
    };

    MRS.isRequirePost = function(requestType) {
        if (!MRS.constants.REQUEST_TYPES[requestType]) {
            // For requests invoked before the getConstants request returns
            // we implicitly assume that they can use GET
            return false;
        }
        return true == MRS.constants.REQUEST_TYPES[requestType].requirePost;
    };

    MRS.isRequestTypeEnabled = function(requestType) {
        if ($.isEmptyObject(MRS.constants.REQUEST_TYPES)) {
            return true;
        }
        if (requestType.indexOf("+") > 0) {
            requestType = requestType.substring(0, requestType.indexOf("+"));
        }
        return !!MRS.constants.REQUEST_TYPES[requestType];
    };

    MRS.isSubmitPassphrase = function (requestType) {
        return requestType == "startForging" ||
            requestType == "stopForging" ||
            requestType == "startMining" ||
            requestType == "startShuffler" ||
            requestType == "getForging" ||
            requestType == "markHost" ||
            requestType == "startFundingMonitor";
    };

    MRS.isScheduleRequest = function (requestType) {
        var keyword = MRS.constants.SCHEDULE_PREFIX;
        return requestType && requestType.length >= keyword.length && requestType.substring(0, keyword.length) == keyword;
    };

    MRS.getFileUploadConfig = function (requestType, data) {
        var config = {};
        if (requestType == "sendMessage") {
            config.selector = "#upload_file_message";
            if (data.encrypt_message) {
                config.requestParam = "encryptedMessageFile";
            } else {
                config.requestParam = "messageFile";
            }
            config.errorDescription = "error_message_too_big";
            config.maxSize = MRS.constants.MAX_PRUNABLE_MESSAGE_LENGTH;
            return config;
        }
        return null;
    };

    MRS.isApiEnabled = function(depends) {
        if (!depends) {
            return true;
        }
        var tags = depends.tags;
        if (tags) {
            for (var i=0; i < tags.length; i++) {
                if (tags[i] && !tags[i].enabled) {
                    return false;
                }
            }
        }
        var apis = depends.apis;
        if (apis) {
            for (i=0; i < apis.length; i++) {
                if (apis[i] && !apis[i].enabled) {
                    return false;
                }
            }
        }
        return true;
    };

    return MRS;
}(isNode ? client : MRS || {}, jQuery));

if (isNode) {
    module.exports = MRS;
}