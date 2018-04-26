/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 * Copyright © 2018 metro.software
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package metro.http;

import java.util.Base64;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum APIEnum {
    //To preserve compatibility, please add new APIs to the end of the enum.
    //When an API is deleted, set its name to empty string and handler to null.
    APPROVE_TRANSACTION("approveTransaction", ApproveTransaction.instance),
    BROADCAST_TRANSACTION("broadcastTransaction", BroadcastTransaction.instance),
    CALCULATE_FULL_HASH("calculateFullHash", CalculateFullHash.instance),
    CANCEL_ASK_ORDER("cancelAskOrder", CancelAskOrder.instance),
    CANCEL_BID_ORDER("cancelBidOrder", CancelBidOrder.instance),
    CAST_VOTE("castVote", CastVote.instance),
    CREATE_POLL("createPoll", CreatePoll.instance),
    DECRYPT_FROM("decryptFrom", DecryptFrom.instance),
    DELETE_ASSET_SHARES("deleteAssetShares", DeleteAssetShares.instance),
    DECODE_HALLMARK("decodeHallmark", DecodeHallmark.instance),
    DECODE_TOKEN("decodeToken", DecodeToken.instance),
    DECODE_FILE_TOKEN("decodeFileToken", DecodeFileToken.instance),
    DECODE_Q_R_CODE("decodeQRCode", DecodeQRCode.instance),
    ENCODE_Q_R_CODE("encodeQRCode", EncodeQRCode.instance),
    ENCRYPT_TO("encryptTo", EncryptTo.instance),
    EVENT_REGISTER("eventRegister", EventRegister.instance),
    EVENT_WAIT("eventWait", EventWait.instance),
    GENERATE_TOKEN("generateToken", GenerateToken.instance),
    GENERATE_FILE_TOKEN("generateFileToken", GenerateFileToken.instance),
    GET_ACCOUNT("getAccount", GetAccount.instance),
    GET_ACCOUNT_BLOCK_COUNT("getAccountBlockCount", GetAccountBlockCount.instance),
    GET_ACCOUNT_BLOCK_IDS("getAccountBlockIds", GetAccountBlockIds.instance),
    GET_ACCOUNT_BLOCKS("getAccountBlocks", GetAccountBlocks.instance),
    GET_ACCOUNT_MINED_BLOCKS("getAccountMinedBlocks", GetAccountMinedBlocks.instance),
    GET_ACCOUNT_ID("getAccountId", GetAccountId.instance),
    GET_ACCOUNT_LEDGER("getAccountLedger", GetAccountLedger.instance),
    GET_ACCOUNT_LEDGER_ENTRY("getAccountLedgerEntry", GetAccountLedgerEntry.instance),
    GET_VOTER_PHASED_TRANSACTIONS("getVoterPhasedTransactions", GetVoterPhasedTransactions.instance),
    GET_LINKED_PHASED_TRANSACTIONS("getLinkedPhasedTransactions", GetLinkedPhasedTransactions.instance),
    GET_POLLS("getPolls", GetPolls.instance),
    GET_ACCOUNT_PHASED_TRANSACTIONS("getAccountPhasedTransactions", GetAccountPhasedTransactions.instance),
    GET_ACCOUNT_PHASED_TRANSACTION_COUNT("getAccountPhasedTransactionCount", GetAccountPhasedTransactionCount.instance),
    GET_ACCOUNT_PUBLIC_KEY("getAccountPublicKey", GetAccountPublicKey.instance),
    GET_ACCOUNT_LESSORS("getAccountLessors", GetAccountLessors.instance),
    GET_ACCOUNT_ASSETS("getAccountAssets", GetAccountAssets.instance),
    GET_ACCOUNT_ASSET_COUNT("getAccountAssetCount", GetAccountAssetCount.instance),
    GET_ACCOUNT_PROPERTIES("getAccountProperties", GetAccountProperties.instance),
    SELL_ALIAS("sellAlias", SellAlias.instance),
    BUY_ALIAS("buyAlias", BuyAlias.instance),
    GET_ALIAS("getAlias", GetAlias.instance),
    GET_ALIAS_COUNT("getAliasCount", GetAliasCount.instance),
    GET_ALIASES("getAliases", GetAliases.instance),
    GET_ALIASES_LIKE("getAliasesLike", GetAliasesLike.instance),
    GET_ALL_ASSETS("getAllAssets", GetAllAssets.instance),
    GET_ASSET("getAsset", GetAsset.instance),
    GET_ASSETS("getAssets", GetAssets.instance),
    GET_ASSET_IDS("getAssetIds", GetAssetIds.instance),
    GET_ASSETS_BY_ISSUER("getAssetsByIssuer", GetAssetsByIssuer.instance),
    GET_ASSET_ACCOUNTS("getAssetAccounts", GetAssetAccounts.instance),
    GET_ASSET_ACCOUNT_COUNT("getAssetAccountCount", GetAssetAccountCount.instance),
    GET_ASSET_PHASED_TRANSACTIONS("getAssetPhasedTransactions", GetAssetPhasedTransactions.instance),
    GET_BALANCE("getBalance", GetBalance.instance),
    GET_BLOCK("getBlock", GetBlock.instance),
    GET_BLOCK_ID("getBlockId", GetBlockId.instance),
    GET_BLOCKS("getBlocks", GetBlocks.instance),
    GET_BLOCKCHAIN_STATUS("getBlockchainStatus", GetBlockchainStatus.instance),
    GET_BLOCKCHAIN_TRANSACTIONS("getBlockchainTransactions", GetBlockchainTransactions.instance),
    GET_REFERENCING_TRANSACTIONS("getReferencingTransactions", GetReferencingTransactions.instance),
    GET_CONSTANTS("getConstants", GetConstants.instance),
    GET_GUARANTEED_BALANCE("getGuaranteedBalance", GetGuaranteedBalance.instance),
    GET_E_C_BLOCK("getECBlock", GetECBlock.instance),
    GET_INBOUND_PEERS("getInboundPeers", GetInboundPeers.instance),
    GET_PLUGINS("getPlugins", GetPlugins.instance),
    GET_MY_INFO("getMyInfo", GetMyInfo.instance),
    GET_PEER("getPeer", GetPeer.instance),
    GET_PEERS("getPeers", GetPeers.instance),
    GET_PHASING_POLL("getPhasingPoll", GetPhasingPoll.instance),
    GET_PHASING_POLLS("getPhasingPolls", GetPhasingPolls.instance),
    GET_PHASING_POLL_VOTES("getPhasingPollVotes", GetPhasingPollVotes.instance),
    GET_PHASING_POLL_VOTE("getPhasingPollVote", GetPhasingPollVote.instance),
    GET_POLL("getPoll", GetPoll.instance),
    GET_POLL_RESULT("getPollResult", GetPollResult.instance),
    GET_POLL_VOTES("getPollVotes", GetPollVotes.instance),
    GET_POLL_VOTE("getPollVote", GetPollVote.instance),
    GET_STATE("getState", GetState.instance),
    GET_TIME("getTime", GetTime.instance),
    GET_TRADES("getTrades", GetTrades.instance),
    GET_LAST_TRADES("getLastTrades", GetLastTrades.instance),
    GET_ALL_TRADES("getAllTrades", GetAllTrades.instance),
    GET_ASSET_TRANSFERS("getAssetTransfers", GetAssetTransfers.instance),
    GET_ASSET_DELETES("getAssetDeletes", GetAssetDeletes.instance),
    GET_EXPECTED_ASSET_TRANSFERS("getExpectedAssetTransfers", GetExpectedAssetTransfers.instance),
    GET_EXPECTED_ASSET_DELETES("getExpectedAssetDeletes", GetExpectedAssetDeletes.instance),
    GET_TRANSACTION("getTransaction", GetTransaction.instance),
    GET_TRANSACTION_BYTES("getTransactionBytes", GetTransactionBytes.instance),
    GET_UNCONFIRMED_TRANSACTION_IDS("getUnconfirmedTransactionIds", GetUnconfirmedTransactionIds.instance),
    GET_UNCONFIRMED_TRANSACTIONS("getUnconfirmedTransactions", GetUnconfirmedTransactions.instance),
    GET_EXPECTED_TRANSACTIONS("getExpectedTransactions", GetExpectedTransactions.instance),
    GET_ACCOUNT_CURRENT_ASK_ORDER_IDS("getAccountCurrentAskOrderIds", GetAccountCurrentAskOrderIds.instance),
    GET_ACCOUNT_CURRENT_BID_ORDER_IDS("getAccountCurrentBidOrderIds", GetAccountCurrentBidOrderIds.instance),
    GET_ACCOUNT_CURRENT_ASK_ORDERS("getAccountCurrentAskOrders", GetAccountCurrentAskOrders.instance),
    GET_ACCOUNT_CURRENT_BID_ORDERS("getAccountCurrentBidOrders", GetAccountCurrentBidOrders.instance),
    GET_ALL_OPEN_ASK_ORDERS("getAllOpenAskOrders", GetAllOpenAskOrders.instance),
    GET_ALL_OPEN_BID_ORDERS("getAllOpenBidOrders", GetAllOpenBidOrders.instance),
    GET_ASK_ORDER("getAskOrder", GetAskOrder.instance),
    GET_ASK_ORDER_IDS("getAskOrderIds", GetAskOrderIds.instance),
    GET_ASK_ORDERS("getAskOrders", GetAskOrders.instance),
    GET_BID_ORDER("getBidOrder", GetBidOrder.instance),
    GET_BID_ORDER_IDS("getBidOrderIds", GetBidOrderIds.instance),
    GET_BID_ORDERS("getBidOrders", GetBidOrders.instance),
    GET_EXPECTED_ASK_ORDERS("getExpectedAskOrders", GetExpectedAskOrders.instance),
    GET_EXPECTED_BID_ORDERS("getExpectedBidOrders", GetExpectedBidOrders.instance),
    GET_EXPECTED_ORDER_CANCELLATIONS("getExpectedOrderCancellations", GetExpectedOrderCancellations.instance),
    GET_ORDER_TRADES("getOrderTrades", GetOrderTrades.instance),
    GET_ALL_SHUFFLINGS("getAllShufflings", GetAllShufflings.instance),
    GET_ACCOUNT_SHUFFLINGS("getAccountShufflings", GetAccountShufflings.instance),
    GET_ASSIGNED_SHUFFLINGS("getAssignedShufflings", GetAssignedShufflings.instance),
    GET_HOLDING_SHUFFLINGS("getHoldingShufflings", GetHoldingShufflings.instance),
    GET_SHUFFLING("getShuffling", GetShuffling.instance),
    GET_SHUFFLING_PARTICIPANTS("getShufflingParticipants", GetShufflingParticipants.instance),
    GET_PRUNABLE_MESSAGE("getPrunableMessage", GetPrunableMessage.instance),
    GET_PRUNABLE_MESSAGES("getPrunableMessages", GetPrunableMessages.instance),
    GET_ALL_PRUNABLE_MESSAGES("getAllPrunableMessages", GetAllPrunableMessages.instance),
    VERIFY_PRUNABLE_MESSAGE("verifyPrunableMessage", VerifyPrunableMessage.instance),
    ISSUE_ASSET("issueAsset", IssueAsset.instance),
    LEASE_BALANCE("leaseBalance", LeaseBalance.instance),
    LONG_CONVERT("longConvert", LongConvert.instance),
    HEX_CONVERT("hexConvert", HexConvert.instance),
    MARK_HOST("markHost", MarkHost.instance),
    PARSE_TRANSACTION("parseTransaction", ParseTransaction.instance),
    PLACE_ASK_ORDER("placeAskOrder", PlaceAskOrder.instance),
    PLACE_BID_ORDER("placeBidOrder", PlaceBidOrder.instance),
    RS_CONVERT("rsConvert", RSConvert.instance),
    READ_MESSAGE("readMessage", ReadMessage.instance),
    SEND_MESSAGE("sendMessage", SendMessage.instance),
    SEND_MONEY("sendMoney", SendMoney.instance),
    SET_ACCOUNT_INFO("setAccountInfo", SetAccountInfo.instance),
    SET_ACCOUNT_PROPERTY("setAccountProperty", SetAccountProperty.instance),
    DELETE_ACCOUNT_PROPERTY("deleteAccountProperty", DeleteAccountProperty.instance),
    SET_ALIAS("setAlias", SetAlias.instance),
    SHUFFLING_CREATE("shufflingCreate", ShufflingCreate.instance),
    SHUFFLING_REGISTER("shufflingRegister", ShufflingRegister.instance),
    SHUFFLING_PROCESS("shufflingProcess", ShufflingProcess.instance),
    SHUFFLING_VERIFY("shufflingVerify", ShufflingVerify.instance),
    SHUFFLING_CANCEL("shufflingCancel", ShufflingCancel.instance),
    START_SHUFFLER("startShuffler", StartShuffler.instance),
    STOP_SHUFFLER("stopShuffler", StopShuffler.instance),
    GET_SHUFFLERS("getShufflers", GetShufflers.instance),
    DELETE_ALIAS("deleteAlias", DeleteAlias.instance),
    SIGN_TRANSACTION("signTransaction", SignTransaction.instance),
    START_FORGING("startForging", StartForging.instance),
    STOP_FORGING("stopForging", StopForging.instance),
    START_MINING("startMining", StartMining.instance),
    STOP_MINING("stopMining", StopMining.instance),
    GET_FORGING("getForging", GetForging.instance),
    GET_MINING("getMining", GetMining.instance),
    TRANSFER_ASSET("transferAsset", TransferAsset.instance),
    DIVIDEND_PAYMENT("dividendPayment", DividendPayment.instance),
    SEARCH_ASSETS("searchAssets", SearchAssets.instance),
    SEARCH_POLLS("searchPolls", SearchPolls.instance),
    SEARCH_ACCOUNTS("searchAccounts", SearchAccounts.instance),
    CLEAR_UNCONFIRMED_TRANSACTIONS("clearUnconfirmedTransactions", ClearUnconfirmedTransactions.instance),
    REQUEUE_UNCONFIRMED_TRANSACTIONS("requeueUnconfirmedTransactions", RequeueUnconfirmedTransactions.instance),
    REBROADCAST_UNCONFIRMED_TRANSACTIONS("rebroadcastUnconfirmedTransactions", RebroadcastUnconfirmedTransactions.instance),
    GET_ALL_WAITING_TRANSACTIONS("getAllWaitingTransactions", GetAllWaitingTransactions.instance),
    GET_ALL_BROADCASTED_TRANSACTIONS("getAllBroadcastedTransactions", GetAllBroadcastedTransactions.instance),
    FULL_RESET("fullReset", FullReset.instance),
    POP_OFF("popOff", PopOff.instance),
    SCAN("scan", Scan.instance),
    LUCENE_REINDEX("luceneReindex", LuceneReindex.instance),
    ADD_PEER("addPeer", AddPeer.instance),
    BLACKLIST_PEER("blacklistPeer", BlacklistPeer.instance),
    DUMP_PEERS("dumpPeers", DumpPeers.instance),
    GET_LOG("getLog", GetLog.instance),
    GET_STACK_TRACES("getStackTraces", GetStackTraces.instance),
    RETRIEVE_PRUNED_DATA("retrievePrunedData", RetrievePrunedData.instance),
    RETRIEVE_PRUNED_TRANSACTION("retrievePrunedTransaction", RetrievePrunedTransaction.instance),
    SET_LOGGING("setLogging", SetLogging.instance),
    SHUTDOWN("shutdown", Shutdown.instance),
    TRIM_DERIVED_TABLES("trimDerivedTables", TrimDerivedTables.instance),
    HASH("hash", Hash.instance),
    FULL_HASH_TO_ID("fullHashToId", FullHashToId.instance),
    SET_PHASING_ONLY_CONTROL("setPhasingOnlyControl", SetPhasingOnlyControl.instance),
    GET_PHASING_ONLY_CONTROL("getPhasingOnlyControl", GetPhasingOnlyControl.instance),
    GET_ALL_PHASING_ONLY_CONTROLS("getAllPhasingOnlyControls", GetAllPhasingOnlyControls.instance),
    START_FUNDING_MONITOR("startFundingMonitor", StartFundingMonitor.instance),
    STOP_FUNDING_MONITOR("stopFundingMonitor", StopFundingMonitor.instance),
    GET_FUNDING_MONITOR("getFundingMonitor", GetFundingMonitor.instance),
    DOWNLOAD_PRUNABLE_MESSAGE("downloadPrunableMessage", DownloadPrunableMessage.instance),
    GET_SHARED_KEY("getSharedKey", GetSharedKey.instance),
    SET_API_PROXY_PEER("setAPIProxyPeer", SetAPIProxyPeer.instance),
    SEND_TRANSACTION("sendTransaction", SendTransaction.instance),
    GET_ASSET_DIVIDENDS("getAssetDividends", GetAssetDividends.instance),
    BLACKLIST_API_PROXY_PEER("blacklistAPIProxyPeer", BlacklistAPIProxyPeer.instance),
    GET_NEXT_BLOCK_GENERATORS("getNextBlockGenerators", GetNextBlockGeneratorsTemp.instance),
    GET_SCHEDULED_TRANSACTIONS("getScheduledTransactions", GetScheduledTransactions.instance),
    DELETE_SCHEDULED_TRANSACTION("deleteScheduledTransaction", DeleteScheduledTransaction.instance),
    MINING_GET_WORK("getWork", GetWork.instance);

    private static final Map<String, APIEnum> apiByName = new HashMap<>();

    static {
        final EnumSet<APITag> tagsNotRequiringBlockchain = EnumSet.of(APITag.UTILS);
        for (APIEnum api : values()) {
            if (apiByName.put(api.getName(), api) != null) {
                AssertionError assertionError = new AssertionError("Duplicate API name: " + api.getName());
                assertionError.printStackTrace();
                throw assertionError;
            }

            final APIServlet.APIRequestHandler handler = api.getHandler();
            if (!Collections.disjoint(handler.getAPITags(), tagsNotRequiringBlockchain)
                    && handler.requireBlockchain()) {
                AssertionError assertionError = new AssertionError("API " + api.getName()
                        + " is not supposed to require blockchain");
                assertionError.printStackTrace();
                throw assertionError;
            }
        }
    }

    public static APIEnum fromName(String name) {
        return apiByName.get(name);
    }

    private final String name;
    private final APIServlet.APIRequestHandler handler;

    APIEnum(String name, APIServlet.APIRequestHandler handler) {
        this.name = name;
        this.handler = handler;
    }

    public String getName() {
        return name;
    }

    public APIServlet.APIRequestHandler getHandler() {
        return handler;
    }

    public static EnumSet<APIEnum> base64StringToEnumSet(String apiSetBase64) {
        byte[] decoded = Base64.getDecoder().decode(apiSetBase64);
        BitSet bs = BitSet.valueOf(decoded);
        EnumSet<APIEnum> result = EnumSet.noneOf(APIEnum.class);
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
            result.add(APIEnum.values()[i]);
            if (i == Integer.MAX_VALUE) {
                break; // or (i+1) would overflow
            }
        }
        return result;
    }

    public static String enumSetToBase64String(EnumSet<APIEnum> apiSet) {
        BitSet bitSet = new BitSet();
        for (APIEnum api: apiSet) {
            bitSet.set(api.ordinal());
        }
        return Base64.getEncoder().encodeToString(bitSet.toByteArray());
    }
}
