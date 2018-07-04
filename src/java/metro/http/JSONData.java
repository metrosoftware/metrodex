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

import metro.Account;
import metro.AccountLedger;
import metro.AccountLedger.LedgerEntry;
import metro.AccountRestrictions;
import metro.Alias;
import metro.Appendix;
import metro.Asset;
import metro.AssetDelete;
import metro.AssetDividend;
import metro.AssetTransfer;
import metro.Attachment;
import metro.Block;
import metro.Consensus;
import metro.Constants;
import metro.FundingMonitor;
import metro.Generator;
import metro.HoldingType;
import metro.Metro;
import metro.Order;
import metro.PhasingPoll;
import metro.PhasingVote;
import metro.Poll;
import metro.PrunableMessage;
import metro.Shuffler;
import metro.Shuffling;
import metro.ShufflingParticipant;
import metro.Token;
import metro.Trade;
import metro.Transaction;
import metro.Vote;
import metro.VoteWeighting;
import metro.crypto.Crypto;
import metro.crypto.EncryptedData;
import metro.db.DbIterator;
import metro.peer.Hallmark;
import metro.peer.Peer;
import metro.util.Convert;
import metro.util.Filter;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class JSONData {

    static JSONObject alias(Alias alias) {
        JSONObject json = new JSONObject();
        //FIXME #220 optimize
        putAccount(json, "account", Account.getAccount(alias.getAccountId()).getFullId());
        json.put("aliasName", alias.getAliasName());
        json.put("aliasURI", alias.getAliasURI());
        json.put("timestamp", alias.getTimestamp());
        json.put("alias", Long.toUnsignedString(alias.getId()));
        Alias.Offer offer = Alias.getOffer(alias);
        if (offer != null) {
            json.put("priceMQT", String.valueOf(offer.getPriceMQT()));
            if (offer.getBuyerId() != 0) {
                json.put("buyer", Long.toUnsignedString(offer.getBuyerId()));
            }
        }
        return json;
    }

    static JSONObject accountBalance(Account account, boolean includeEffectiveBalance) {
        return accountBalance(account, includeEffectiveBalance, Metro.getBlockchain().getHeight());
    }

    static JSONObject accountBalance(Account account, boolean includeEffectiveBalance, int height) {
        JSONObject json = new JSONObject();
        if (account == null) {
            json.put("balanceMQT", "0");
            json.put("unconfirmedBalanceMQT", "0");
            json.put("forgedBalanceMQT", "0");
            if (includeEffectiveBalance) {
                json.put("effectiveBalanceMTR", "0");
                json.put("guaranteedBalanceMQT", "0");
            }
        } else {
            json.put("balanceMQT", String.valueOf(account.getBalanceMQT()));
            json.put("unconfirmedBalanceMQT", String.valueOf(account.getUnconfirmedBalanceMQT()));
            json.put("forgedBalanceMQT", String.valueOf(account.getForgedBalanceMQT()));
            if (includeEffectiveBalance) {
                json.put("effectiveBalanceMTR", account.getEffectiveBalanceMTR(height));
                json.put("guaranteedBalanceMQT", String.valueOf(account.getGuaranteedBalanceMQT(height - Metro.getBlockchain().getGuaranteedBalanceHeight(height), height)));
            }
        }
        return json;
    }

    static JSONObject lessor(Account account, boolean includeEffectiveBalance) {
        JSONObject json = new JSONObject();
        Account.AccountLease accountLease = account.getAccountLease();
        if (accountLease.getCurrentLesseeId() != 0) {
            //FIXME #220 optimize
            putAccount(json, "currentLessee", Account.getAccount(accountLease.getCurrentLesseeId()).getFullId());
            json.put("currentHeightFrom", String.valueOf(accountLease.getCurrentLeasingHeightFrom()));
            json.put("currentHeightTo", String.valueOf(accountLease.getCurrentLeasingHeightTo()));
            if (includeEffectiveBalance) {
                json.put("effectiveBalanceMTR", String.valueOf(account.getGuaranteedBalanceMQT() / Constants.ONE_MTR));
            }
        }
        if (accountLease.getNextLesseeId() != 0) {
            //FIXME #220 optimize
            putAccount(json, "nextLessee", Account.getAccount(accountLease.getNextLesseeId()).getFullId());
            json.put("nextHeightFrom", String.valueOf(accountLease.getNextLeasingHeightFrom()));
            json.put("nextHeightTo", String.valueOf(accountLease.getNextLeasingHeightTo()));
        }
        return json;
    }

    static JSONObject asset(Asset asset, boolean includeCounts) {
        JSONObject json = new JSONObject();
        //FIXME #220 optimize
        putAccount(json, "account", Account.getAccount(asset.getAccountId()).getFullId());
        json.put("name", asset.getName());
        json.put("description", asset.getDescription());
        json.put("decimals", asset.getDecimals());
        json.put("initialQuantityQNT", String.valueOf(asset.getInitialQuantityQNT()));
        json.put("quantityQNT", String.valueOf(asset.getQuantityQNT()));
        json.put("asset", Long.toUnsignedString(asset.getId()));
        if (includeCounts) {
            json.put("numberOfTrades", Trade.getTradeCount(asset.getId()));
            json.put("numberOfTransfers", AssetTransfer.getTransferCount(asset.getId()));
            json.put("numberOfAccounts", Account.getAssetAccountCount(asset.getId()));
        }
        return json;
    }

    static JSONObject accountAsset(Account.AccountAsset accountAsset, boolean includeAccount, boolean includeAssetInfo) {
        JSONObject json = new JSONObject();
        if (includeAccount) {
            //FIXME #220 optimize
            putAccount(json, "account", Account.getAccount(accountAsset.getAccountId()).getFullId());
        }
        json.put("asset", Long.toUnsignedString(accountAsset.getAssetId()));
        json.put("quantityQNT", String.valueOf(accountAsset.getQuantityQNT()));
        json.put("unconfirmedQuantityQNT", String.valueOf(accountAsset.getUnconfirmedQuantityQNT()));
        if (includeAssetInfo) {
            putAssetInfo(json, accountAsset.getAssetId());
        }
        return json;
    }

    static JSONObject accountProperty(Account.AccountProperty accountProperty, boolean includeAccount, boolean includeSetter) {
        //FIXME #use in iterator need to optimize
        //FIXME #220 optimize
        JSONObject json = new JSONObject();
        if (includeAccount) {
            Account.FullId recipientId = Account.getAccount(accountProperty.getRecipientId()).getFullId();
            putAccount(json, "recipient", recipientId);
        }
        if (includeSetter) {
            Account.FullId setterId = Account.getAccount(accountProperty.getSetterId()).getFullId();
            putAccount(json, "setter", setterId);
        }
        json.put("property", accountProperty.getProperty());
        json.put("value", accountProperty.getValue());
        return json;
    }

    static JSONObject askOrder(Order.Ask order) {
        JSONObject json = order(order);
        json.put("type", "ask");
        return json;
    }

    static JSONObject bidOrder(Order.Bid order) {
        JSONObject json = order(order);
        json.put("type", "bid");
        return json;
    }

    private static JSONObject order(Order order) {
        JSONObject json = new JSONObject();
        json.put("order", Long.toUnsignedString(order.getId()));
        json.put("asset", Long.toUnsignedString(order.getAssetId()));
        //FIXME #220 optimize
        putAccount(json, "account", Account.getAccount(order.getAccountId()).getFullId());
        json.put("quantityQNT", String.valueOf(order.getQuantityQNT()));
        json.put("priceMQT", String.valueOf(order.getPriceMQT()));
        json.put("height", order.getHeight());
        json.put("transactionIndex", order.getTransactionIndex());
        json.put("transactionHeight", order.getTransactionHeight());
        return json;
    }

    static JSONObject expectedAskOrder(Transaction transaction) {
        JSONObject json = expectedOrder(transaction);
        json.put("type", "ask");
        return json;
    }

    static JSONObject expectedBidOrder(Transaction transaction) {
        JSONObject json = expectedOrder(transaction);
        json.put("type", "bid");
        return json;
    }

    private static JSONObject expectedOrder(Transaction transaction) {
        JSONObject json = new JSONObject();
        Attachment.ColoredCoinsOrderPlacement attachment = (Attachment.ColoredCoinsOrderPlacement)transaction.getAttachment();
        json.put("order", transaction.getStringId());
        json.put("asset", Long.toUnsignedString(attachment.getAssetId()));
        putAccount(json, "account", transaction.getSenderFullId());
        json.put("quantityQNT", String.valueOf(attachment.getQuantityQNT()));
        json.put("priceMQT", String.valueOf(attachment.getPriceMQT()));
        putExpectedTransaction(json, transaction);
        return json;
    }

    static JSONObject expectedOrderCancellation(Transaction transaction) {
        JSONObject json = new JSONObject();
        Attachment.ColoredCoinsOrderCancellation attachment = (Attachment.ColoredCoinsOrderCancellation)transaction.getAttachment();
        json.put("order", Long.toUnsignedString(attachment.getOrderId()));
        putAccount(json, "account", transaction.getSenderFullId());
        putExpectedTransaction(json, transaction);
        return json;
    }

    static JSONObject shuffling(Shuffling shuffling, boolean includeHoldingInfo) {
        JSONObject json = new JSONObject();
        json.put("shuffling", Long.toUnsignedString(shuffling.getId()));
        //FIXME #220 optimize
        putAccount(json, "issuer", Account.getAccount(shuffling.getIssuerId()).getFullId());
        json.put("holding", Long.toUnsignedString(shuffling.getHoldingId()));
        json.put("holdingType", shuffling.getHoldingType().getCode());
        if (shuffling.getAssigneeAccountId() != 0) {
            //FIXME #220 optimize
            putAccount(json, "assignee", Account.getAccount(shuffling.getAssigneeAccountId()).getFullId());
        }
        json.put("amount", String.valueOf(shuffling.getAmount()));
        json.put("blocksRemaining", shuffling.getBlocksRemaining());
        json.put("participantCount", shuffling.getParticipantCount());
        json.put("registrantCount", shuffling.getRegistrantCount());
        json.put("stage", shuffling.getStage().getCode());
        json.put("shufflingStateHash", Convert.toHexString(shuffling.getStateHash()));
        json.put("shufflingFullHash", Convert.toHexString(shuffling.getFullHash()));
        JSONArray recipientPublicKeys = new JSONArray();
        for (byte[] recipientPublicKey : shuffling.getRecipientPublicKeys()) {
            recipientPublicKeys.add(Convert.toHexString(recipientPublicKey));
        }
        if (recipientPublicKeys.size() > 0) {
            json.put("recipientPublicKeys", recipientPublicKeys);
        }
        if (includeHoldingInfo && shuffling.getHoldingType() != HoldingType.MTR) {
            JSONObject holdingJson = new JSONObject();
            if (shuffling.getHoldingType() == HoldingType.ASSET) {
                putAssetInfo(holdingJson, shuffling.getHoldingId());
            }
            json.put("holdingInfo", holdingJson);
        }
        return json;
    }

    static JSONObject participant(ShufflingParticipant participant) {
        JSONObject json = new JSONObject();
        json.put("shuffling", Long.toUnsignedString(participant.getShufflingId()));
        //FIXME #220 optimize
        putAccount(json, "account", Account.getAccountFullId(participant.getAccountId()));
        putAccount(json, "nextAccount", Account.getAccountFullId(participant.getNextAccountId()));
        json.put("state", participant.getState().getCode());
        return json;
    }

    static JSONObject shuffler(Shuffler shuffler, boolean includeParticipantState) {
        JSONObject json = new JSONObject();
        //FIXME #220 optimize
        putAccount(json, "account", Account.getAccount(shuffler.getAccountId()).getFullId());
        putAccount(json, "recipient", Account.FullId.fromPublicKey(shuffler.getRecipientPublicKey()));
        json.put("shufflingFullHash", Convert.toHexString(shuffler.getShufflingFullHash()));
        json.put("shuffling", Long.toUnsignedString(Convert.fullHashToId(shuffler.getShufflingFullHash())));
        if (shuffler.getFailedTransaction() != null) {
            json.put("failedTransaction", unconfirmedTransaction(shuffler.getFailedTransaction()));
            json.put("failureCause", shuffler.getFailureCause().getMessage());
        }
        if (includeParticipantState) {
            ShufflingParticipant participant = ShufflingParticipant.getParticipant(Convert.fullHashToId(shuffler.getShufflingFullHash()), shuffler.getAccountId());
            if (participant != null) {
                json.put("participantState", participant.getState().getCode());
            }
        }
        return json;
    }

    static JSONObject block(Block block, boolean includeTransactions, boolean includeExecutedPhased) {
        JSONObject json = new JSONObject();
        json.put("block", block.getStringId());
        json.put("height", block.getHeight());
        putAccount(json, "generator", block.getGeneratorFullId());
        json.put("generatorPublicKey", Convert.toHexString(block.getGeneratorPublicKey()));
        json.put("timestamp", block.getTimestamp());
        json.put("numberOfTransactions", block.getTransactions().size());
        json.put("totalAmountMQT", String.valueOf(block.getTotalAmountMQT()));
        json.put("rewardMQT", String.valueOf(block.getRewardMQT()));
        json.put("payloadLength", block.getPayloadLength());
        json.put("version", block.getVersion());
        json.put("baseTarget", Long.toUnsignedString(block.getBaseTarget()));
        json.put("cumulativeDifficulty", block.getCumulativeDifficulty().toString());
        if (block.getPreviousBlockId() != 0) {
            json.put("previousBlock", Long.toUnsignedString(block.getPreviousBlockId()));
        }
        if (block.getNextBlockId() != 0) {
            json.put("nextBlock", Long.toUnsignedString(block.getNextBlockId()));
        }
        // TODO #145 if (block.isKeyblock()) { ... } report key block fields for UI
        json.put("txMerkleRoot", Convert.toHexString(block.getTxMerkleRoot()));
        json.put("generationSequence", Convert.toHexString(block.getGenerationSequence()));
        json.put("previousBlockHash", Convert.toHexString(block.getPreviousBlockHash()));
        json.put("blockSignature", Convert.toHexString(block.getBlockSignature()));
        JSONArray transactions = new JSONArray();
        if (includeTransactions) {
            block.getTransactions().forEach(transaction -> transactions.add(transaction(transaction)));
        } else {
            block.getTransactions().forEach(transaction -> transactions.add(transaction.getStringId()));
        }
        json.put("transactions", transactions);
        if (includeExecutedPhased) {
            JSONArray phasedTransactions = new JSONArray();
            try (DbIterator<PhasingPoll.PhasingPollResult> phasingPollResults = PhasingPoll.getApproved(block.getHeight())) {
                for (PhasingPoll.PhasingPollResult phasingPollResult : phasingPollResults) {
                    long phasedTransactionId = phasingPollResult.getId();
                    if (includeTransactions) {
                        phasedTransactions.add(transaction(Metro.getBlockchain().getTransaction(phasedTransactionId)));
                    } else {
                        phasedTransactions.add(Long.toUnsignedString(phasedTransactionId));
                    }
                }
            }
            json.put("executedPhasedTransactions", phasedTransactions);
        }
        return json;
    }

    static JSONObject encryptedData(EncryptedData encryptedData) {
        JSONObject json = new JSONObject();
        json.put("data", Convert.toHexString(encryptedData.getData()));
        json.put("nonce", Convert.toHexString(encryptedData.getNonce()));
        return json;
    }

    static JSONObject hallmark(Hallmark hallmark) {
        JSONObject json = new JSONObject();
        putAccount(json, "account", Account.FullId.fromPublicKey(hallmark.getPublicKey()));
        json.put("host", hallmark.getHost());
        json.put("port", hallmark.getPort());
        json.put("weight", hallmark.getWeight());
        String dateString = Hallmark.formatDate(hallmark.getDate());
        json.put("date", dateString);
        json.put("valid", hallmark.isValid());
        return json;
    }

    static JSONObject token(Token token) {
        JSONObject json = new JSONObject();
        putAccount(json, "account", Account.FullId.fromPublicKey(token.getPublicKey()));
        json.put("timestamp", token.getTimestamp());
        json.put("valid", token.isValid());
        return json;
    }

    static JSONObject peer(Peer peer) {
        JSONObject json = new JSONObject();
        json.put("address", peer.getHost());
        json.put("port", peer.getPort());
        json.put("state", peer.getState().ordinal());
        json.put("announcedAddress", peer.getAnnouncedAddress());
        json.put("shareAddress", peer.shareAddress());
        if (peer.getHallmark() != null) {
            json.put("hallmark", peer.getHallmark().getHallmarkString());
        }
        json.put("weight", peer.getWeight());
        json.put("downloadedVolume", peer.getDownloadedVolume());
        json.put("uploadedVolume", peer.getUploadedVolume());
        json.put("application", peer.getApplication());
        json.put("version", peer.getVersion());
        json.put("platform", peer.getPlatform());
        if (peer.getApiPort() != 0) {
            json.put("apiPort", peer.getApiPort());
        }
        if (peer.getApiSSLPort() != 0) {
            json.put("apiSSLPort", peer.getApiSSLPort());
        }
        json.put("blacklisted", peer.isBlacklisted());
        json.put("lastUpdated", peer.getLastUpdated());
        json.put("lastConnectAttempt", peer.getLastConnectAttempt());
        json.put("inbound", peer.isInbound());
        json.put("inboundWebSocket", peer.isInboundWebSocket());
        json.put("outboundWebSocket", peer.isOutboundWebSocket());
        if (peer.isBlacklisted()) {
            json.put("blacklistingCause", peer.getBlacklistingCause());
        }
        JSONArray servicesArray = new JSONArray();
        for (Peer.Service service : Peer.Service.values()) {
            if (peer.providesService(service)) {
                servicesArray.add(service.name());
            }
        }
        json.put("services", servicesArray);
        json.put("blockchainState", peer.getBlockchainState());
        return json;
    }

    static JSONObject poll(Poll poll) {
        JSONObject json = new JSONObject();
        //FIXME #220 optimize
        putAccount(json, "account", Account.getAccount(poll.getAccountId()).getFullId());
        json.put("poll", Long.toUnsignedString(poll.getId()));
        json.put("name", poll.getName());
        json.put("description", poll.getDescription());
        JSONArray options = new JSONArray();
        Collections.addAll(options, poll.getOptions());
        json.put("options", options);
        json.put("finishHeight", poll.getFinishHeight());
        json.put("minNumberOfOptions", poll.getMinNumberOfOptions());
        json.put("maxNumberOfOptions", poll.getMaxNumberOfOptions());
        json.put("minRangeValue", poll.getMinRangeValue());
        json.put("maxRangeValue", poll.getMaxRangeValue());
        putVoteWeighting(json, poll.getVoteWeighting());
        json.put("finished", poll.isFinished());
        json.put("timestamp", poll.getTimestamp());
        return json;
    }

    static JSONObject pollResults(Poll poll, List<Poll.OptionResult> results, VoteWeighting voteWeighting) {
        JSONObject json = new JSONObject();
        json.put("poll", Long.toUnsignedString(poll.getId()));
        if (voteWeighting.getMinBalanceModel() == VoteWeighting.MinBalanceModel.ASSET) {
            json.put("decimals", Asset.getAsset(voteWeighting.getHoldingId()).getDecimals());
        }
        putVoteWeighting(json, voteWeighting);
        json.put("finished", poll.isFinished());
        JSONArray options = new JSONArray();
        Collections.addAll(options, poll.getOptions());
        json.put("options", options);

        JSONArray resultsJson = new JSONArray();
        for (Poll.OptionResult option : results) {
            JSONObject optionJSON = new JSONObject();
            if (option != null) {
                optionJSON.put("result", String.valueOf(option.getResult()));
                optionJSON.put("weight", String.valueOf(option.getWeight()));
            } else {
                optionJSON.put("result", "");
                optionJSON.put("weight", "0");
            }
            resultsJson.add(optionJSON);
        }
        json.put("results", resultsJson);
        return json;
    }

    interface VoteWeighter {
        long calcWeight(long voterId);
    }
    static JSONObject vote(Vote vote, VoteWeighter weighter) {
        JSONObject json = new JSONObject();
        //FIXME #220 optimize
        putAccount(json, "voter", Account.getAccount(vote.getVoterId()).getFullId());
        json.put("transaction", Long.toUnsignedString(vote.getId()));
        JSONArray votesJson = new JSONArray();
        for (byte v : vote.getVoteBytes()) {
            if (v == Constants.NO_VOTE_VALUE) {
                votesJson.add("");
            } else {
                votesJson.add(Byte.toString(v));
            }
        }
        json.put("votes", votesJson);
        if (weighter != null) {
            json.put("weight", String.valueOf(weighter.calcWeight(vote.getVoterId())));
        }
        return json;
    }

    static JSONObject phasingPoll(PhasingPoll poll, boolean countVotes) {
        JSONObject json = new JSONObject();
        json.put("transaction", Long.toUnsignedString(poll.getId()));
        json.put("transactionFullHash", Convert.toHexString(poll.getFullHash()));
        json.put("finishHeight", poll.getFinishHeight());
        json.put("quorum", String.valueOf(poll.getQuorum()));
        //FIXME #220 optimize
        putAccount(json, "account", Account.getAccount(poll.getAccountId()).getFullId());
        JSONArray whitelistJson = new JSONArray();
        for (Account.FullId accountId : poll.getWhitelist()) {
            JSONObject whitelisted = new JSONObject();
            putAccount(whitelisted, "whitelisted", accountId);
            whitelistJson.add(whitelisted);
        }
        json.put("whitelist", whitelistJson);
        List<byte[]> linkedFullHashes = poll.getLinkedFullHashes();
        if (linkedFullHashes.size() > 0) {
            JSONArray linkedFullHashesJSON = new JSONArray();
            for (byte[] hash : linkedFullHashes) {
                linkedFullHashesJSON.add(Convert.toHexString(hash));
            }
            json.put("linkedFullHashes", linkedFullHashesJSON);
        }
        if (poll.getHashedSecret() != null) {
            json.put("hashedSecret", Convert.toHexString(poll.getHashedSecret()));
        }
        putVoteWeighting(json, poll.getVoteWeighting());
        PhasingPoll.PhasingPollResult phasingPollResult = PhasingPoll.getResult(poll.getId());
        json.put("finished", phasingPollResult != null);
        if (phasingPollResult != null) {
            json.put("approved", phasingPollResult.isApproved());
            json.put("result", String.valueOf(phasingPollResult.getResult()));
            json.put("executionHeight", phasingPollResult.getHeight());
        } else if (countVotes) {
            json.put("result", String.valueOf(poll.countVotes()));
        }
        return json;
    }

    static JSONObject phasingPollResult(PhasingPoll.PhasingPollResult phasingPollResult) {
        JSONObject json = new JSONObject();
        json.put("transaction", Long.toUnsignedString(phasingPollResult.getId()));
        json.put("approved", phasingPollResult.isApproved());
        json.put("result", String.valueOf(phasingPollResult.getResult()));
        json.put("executionHeight", phasingPollResult.getHeight());
        return json;
    }

    static JSONObject phasingPollVote(PhasingVote vote) {
        JSONObject json = new JSONObject();
        //FIXME #220 optimize
        JSONData.putAccount(json, "voter", Account.getAccount(vote.getVoterId()).getFullId());
        json.put("transaction", Long.toUnsignedString(vote.getVoteId()));
        return json;
    }

    private static void putVoteWeighting(JSONObject json, VoteWeighting voteWeighting) {
        json.put("votingModel", voteWeighting.getVotingModel().getCode());
        json.put("minBalance", String.valueOf(voteWeighting.getMinBalance()));
        json.put("minBalanceModel", voteWeighting.getMinBalanceModel().getCode());
        if (voteWeighting.getHoldingId() != 0) {
            json.put("holding", Long.toUnsignedString(voteWeighting.getHoldingId()));
        }
    }

    static JSONObject phasingOnly(AccountRestrictions.PhasingOnly phasingOnly) {
        JSONObject json = new JSONObject();
        //FIXME #220 optimize
        putAccount(json, "account", Account.getAccount(phasingOnly.getAccountId()).getFullId());
        json.put("quorum", String.valueOf(phasingOnly.getPhasingParams().getQuorum()));
        JSONArray whitelistJson = new JSONArray();
        for (Account.FullId accountId : phasingOnly.getPhasingParams().getWhitelist()) {
            JSONObject whitelisted = new JSONObject();
            putAccount(whitelisted, "whitelisted", accountId);
            whitelistJson.add(whitelisted);
        }
        json.put("whitelist", whitelistJson);
        json.put("maxFees", String.valueOf(phasingOnly.getMaxFees()));
        json.put("minDuration", phasingOnly.getMinDuration());
        json.put("maxDuration", phasingOnly.getMaxDuration());
        putVoteWeighting(json, phasingOnly.getPhasingParams().getVoteWeighting());
        return json;
    }

    static JSONObject trade(Trade trade, boolean includeAssetInfo) {
        JSONObject json = new JSONObject();
        json.put("timestamp", trade.getTimestamp());
        json.put("quantityQNT", String.valueOf(trade.getQuantityQNT()));
        json.put("priceMQT", String.valueOf(trade.getPriceMQT()));
        json.put("asset", Long.toUnsignedString(trade.getAssetId()));
        json.put("askOrder", Long.toUnsignedString(trade.getAskOrderId()));
        json.put("bidOrder", Long.toUnsignedString(trade.getBidOrderId()));
        json.put("askOrderHeight", trade.getAskOrderHeight());
        json.put("bidOrderHeight", trade.getBidOrderHeight());
        //FIXME #220 optimize
        putAccount(json, "seller", Account.getAccount(trade.getSellerId()).getFullId());
        putAccount(json, "buyer", Account.getAccount(trade.getBuyerId()).getFullId());
        json.put("block", Long.toUnsignedString(trade.getBlockId()));
        json.put("height", trade.getHeight());
        json.put("tradeType", trade.isBuy() ? "buy" : "sell");
        if (includeAssetInfo) {
            putAssetInfo(json, trade.getAssetId());
        }
        return json;
    }

    static JSONObject assetTransfer(AssetTransfer assetTransfer, boolean includeAssetInfo) {
        JSONObject json = new JSONObject();
        json.put("assetTransfer", Long.toUnsignedString(assetTransfer.getId()));
        json.put("asset", Long.toUnsignedString(assetTransfer.getAssetId()));
        //FIXME #220 optimize
        putAccount(json, "sender", Account.getAccount(assetTransfer.getSenderId()).getFullId());
        putAccount(json, "recipient", Account.getAccount(assetTransfer.getRecipientId()).getFullId());
        json.put("quantityQNT", String.valueOf(assetTransfer.getQuantityQNT()));
        json.put("height", assetTransfer.getHeight());
        json.put("timestamp", assetTransfer.getTimestamp());
        if (includeAssetInfo) {
            putAssetInfo(json, assetTransfer.getAssetId());
        }
        return json;
    }

    static JSONObject expectedAssetTransfer(Transaction transaction, boolean includeAssetInfo) {
        JSONObject json = new JSONObject();
        Attachment.ColoredCoinsAssetTransfer attachment = (Attachment.ColoredCoinsAssetTransfer)transaction.getAttachment();
        json.put("assetTransfer", transaction.getStringId());
        json.put("asset", Long.toUnsignedString(attachment.getAssetId()));
        putAccount(json, "sender", transaction.getSenderFullId());
        putAccount(json, "recipient", transaction.getRecipientFullId());
        json.put("quantityQNT", String.valueOf(attachment.getQuantityQNT()));
        if (includeAssetInfo) {
            putAssetInfo(json, attachment.getAssetId());
        }
        putExpectedTransaction(json, transaction);
        return json;
    }

    static JSONObject assetDelete(AssetDelete assetDelete, boolean includeAssetInfo) {
        JSONObject json = new JSONObject();
        json.put("assetDelete", Long.toUnsignedString(assetDelete.getId()));
        json.put("asset", Long.toUnsignedString(assetDelete.getAssetId()));
        //FIXME #220 optimize
        putAccount(json, "account", Account.getAccount(assetDelete.getAccountId()).getFullId());
        json.put("quantityQNT", String.valueOf(assetDelete.getQuantityQNT()));
        json.put("height", assetDelete.getHeight());
        json.put("timestamp", assetDelete.getTimestamp());
        if (includeAssetInfo) {
            putAssetInfo(json, assetDelete.getAssetId());
        }
        return json;
    }

    static JSONObject expectedAssetDelete(Transaction transaction, boolean includeAssetInfo) {
        JSONObject json = new JSONObject();
        Attachment.ColoredCoinsAssetDelete attachment = (Attachment.ColoredCoinsAssetDelete)transaction.getAttachment();
        json.put("assetDelete", transaction.getStringId());
        json.put("asset", Long.toUnsignedString(attachment.getAssetId()));
        putAccount(json, "account", transaction.getSenderFullId());
        json.put("quantityQNT", String.valueOf(attachment.getQuantityQNT()));
        if (includeAssetInfo) {
            putAssetInfo(json, attachment.getAssetId());
        }
        putExpectedTransaction(json, transaction);
        return json;
    }

    static JSONObject assetDividend(AssetDividend assetDividend) {
        JSONObject json = new JSONObject();
        json.put("assetDividend", Long.toUnsignedString(assetDividend.getId()));
        json.put("asset", Long.toUnsignedString(assetDividend.getAssetId()));
        json.put("amountMQTPerQNT", String.valueOf(assetDividend.getAmountMQTPerQNT()));
        json.put("totalDividend", String.valueOf(assetDividend.getTotalDividend()));
        json.put("dividendHeight", assetDividend.getDividendHeight());
        json.put("numberOfAccounts", assetDividend.getNumAccounts());
        json.put("height", assetDividend.getHeight());
        json.put("timestamp", assetDividend.getTimestamp());
        return json;
    }

    static JSONObject unconfirmedTransaction(Transaction transaction) {
        return unconfirmedTransaction(transaction, null);
    }

    static JSONObject unconfirmedTransaction(Transaction transaction, Filter<Appendix> filter) {
        JSONObject json = new JSONObject();
        json.put("type", transaction.getType().getType());
        json.put("subtype", transaction.getType().getSubtype());
        json.put("phased", transaction.getPhasing() != null);
        json.put("timestamp", transaction.getTimestamp());
        json.put("deadline", transaction.getDeadline());
        json.put("senderPublicKey", Convert.toHexString(transaction.getSenderPublicKey()));
        if (transaction.getRecipientId() != 0) {
            putAccount(json, "recipient", transaction.getRecipientFullId());
        }
        json.put("amountMQT", String.valueOf(transaction.getAmountMQT()));
        json.put("feeMQT", String.valueOf(transaction.getFeeMQT()));
        String referencedTransactionFullHash = transaction.getReferencedTransactionFullHash();
        if (referencedTransactionFullHash != null) {
            json.put("referencedTransactionFullHash", referencedTransactionFullHash);
        }
        byte[] signature = Convert.emptyToNull(transaction.getSignature());
        if (signature != null) {
            json.put("signature", Convert.toHexString(signature));
            json.put("signatureHash", Convert.toHexString(Consensus.HASH_FUNCTION.hash(signature)));
            json.put("fullHash", transaction.getFullHash());
        }
        if (signature != null || transaction.getType().isCoinbase()) {
            json.put("transaction", transaction.getStringId());
        }
        JSONObject attachmentJSON = new JSONObject();
        if (filter == null) {
            for (Appendix appendage : transaction.getAppendages(true)) {
                attachmentJSON.putAll(appendage.getJSONObject());
            }
        } else {
            for (Appendix appendage : transaction.getAppendages(filter, true)) {
                attachmentJSON.putAll(appendage.getJSONObject());
            }
        }
        if (! attachmentJSON.isEmpty()) {
            for (Map.Entry entry : (Iterable<Map.Entry>) attachmentJSON.entrySet()) {
                if (entry.getValue() instanceof Long) {
                    entry.setValue(String.valueOf(entry.getValue()));
                }
            }
            json.put("attachment", attachmentJSON);
        }
        putAccount(json, "sender", transaction.getSenderFullId());
        json.put("height", transaction.getHeight());
        json.put("version", transaction.getVersion());
        json.put("ecBlockId", Long.toUnsignedString(transaction.getECBlockId()));
        json.put("ecBlockHeight", transaction.getECBlockHeight());

        return json;
    }

    static JSONObject transaction(Transaction transaction) {
        return transaction(transaction, false);
    }

    static JSONObject transaction(Transaction transaction, boolean includePhasingResult) {
        JSONObject json = transaction(transaction, null);
        if (includePhasingResult && transaction.getPhasing() != null) {
            PhasingPoll.PhasingPollResult phasingPollResult = PhasingPoll.getResult(transaction.getId());
            if (phasingPollResult != null) {
                json.put("approved", phasingPollResult.isApproved());
                json.put("result", String.valueOf(phasingPollResult.getResult()));
                json.put("executionHeight", phasingPollResult.getHeight());
            }
        }
        return json;
    }

    static JSONObject transaction(Transaction transaction, Filter<Appendix> filter) {
        JSONObject json = unconfirmedTransaction(transaction, filter);
        json.put("block", Long.toUnsignedString(transaction.getBlockId()));
        json.put("confirmations", Metro.getBlockchain().getHeight() - transaction.getHeight());
        json.put("blockTimestamp", transaction.getBlockTimestamp());
        json.put("transactionIndex", transaction.getIndex());
        return json;
    }

    static JSONObject generator(Generator generator, long elapsedTime) {
        JSONObject response = new JSONObject();
        long deadline = generator.getDeadline();
        putAccount(response, "account", generator.getAccountFullId());
        response.put("deadline", deadline);
        response.put("hitTime", generator.getHitTime());
        response.put("remaining", Math.max(deadline - elapsedTime, 0));
        return response;
    }

    static JSONObject accountMonitor(FundingMonitor monitor, boolean includeMonitoredAccounts) {
        JSONObject json = new JSONObject();
        json.put("holdingType", monitor.getHoldingType().getCode());
        json.put("account", monitor.getAccountId().toString());
        json.put("accountRS", monitor.getAccountName());
        json.put("holding", Long.toUnsignedString(monitor.getHoldingId()));
        json.put("property", monitor.getProperty());
        json.put("amount", String.valueOf(monitor.getAmount()));
        json.put("threshold", String.valueOf(monitor.getThreshold()));
        json.put("interval", monitor.getInterval());
        if (includeMonitoredAccounts) {
            JSONArray jsonAccounts = new JSONArray();
            List<FundingMonitor.MonitoredAccount> accountList = FundingMonitor.getMonitoredAccounts(monitor);
            accountList.forEach(account -> jsonAccounts.add(JSONData.monitoredAccount(account)));
            json.put("monitoredAccounts", jsonAccounts);
        }
        return json;
    }

    static JSONObject monitoredAccount(FundingMonitor.MonitoredAccount account) {
        JSONObject json = new JSONObject();
        json.put("account", account.getAccountId().toString());
        json.put("accountRS", account.getAccountName());
        json.put("amount", String.valueOf(account.getAmount()));
        json.put("threshold", String.valueOf(account.getThreshold()));
        json.put("interval", account.getInterval());
        return json;
    }

    static JSONObject prunableMessage(PrunableMessage prunableMessage, String secretPhrase, byte[] sharedKey) {
        JSONObject json = new JSONObject();
        json.put("transaction", Long.toUnsignedString(prunableMessage.getId()));
        if (prunableMessage.getMessage() == null || prunableMessage.getEncryptedData() == null) {
            json.put("isText", prunableMessage.getMessage() != null ? prunableMessage.messageIsText() : prunableMessage.encryptedMessageIsText());
        }
        //FIXME #220 optimize
        putAccount(json, "sender", Account.getAccount(prunableMessage.getSenderId()).getFullId());
        if (prunableMessage.getRecipientId() != 0) {
            //FIXME #220 optimize
            putAccount(json, "recipient", Account.getAccount(prunableMessage.getRecipientId()).getFullId());
        }
        json.put("transactionTimestamp", prunableMessage.getTransactionTimestamp());
        json.put("blockTimestamp", prunableMessage.getBlockTimestamp());
        EncryptedData encryptedData = prunableMessage.getEncryptedData();
        if (encryptedData != null) {
            json.put("encryptedMessage", encryptedData(prunableMessage.getEncryptedData()));
            json.put("encryptedMessageIsText", prunableMessage.encryptedMessageIsText());
            byte[] decrypted = null;
            try {
                if (secretPhrase != null) {
                    decrypted = prunableMessage.decrypt(secretPhrase);
                } else if (sharedKey != null && sharedKey.length > 0) {
                    decrypted = prunableMessage.decrypt(sharedKey);
                }
                if (decrypted != null) {
                    json.put("decryptedMessage", Convert.toString(decrypted, prunableMessage.encryptedMessageIsText()));
                }
            } catch (RuntimeException e) {
                putException(json, e, "Decryption failed");
            }
            json.put("isCompressed", prunableMessage.isCompressed());
        }
        if (prunableMessage.getMessage() != null) {
            json.put("message", Convert.toString(prunableMessage.getMessage(), prunableMessage.messageIsText()));
            json.put("messageIsText", prunableMessage.messageIsText());
        }
        return json;
    }

    static JSONObject apiRequestHandler(APIServlet.APIRequestHandler handler) {
        JSONObject json = new JSONObject();
        json.put("allowRequiredBlockParameters", handler.allowRequiredBlockParameters());
        if (handler.getFileParameter() != null) {
            json.put("fileParameter", handler.getFileParameter());
        }
        json.put("requireBlockchain", handler.requireBlockchain());
        json.put("requirePost", handler.requirePost());
        json.put("requirePassword", handler.requirePassword());
        json.put("requireFullClient", handler.requireFullClient());
        return json;
    }

    static void putPrunableAttachment(JSONObject json, Transaction transaction) {
        JSONObject prunableAttachment = transaction.getPrunableAttachmentJSON();
        if (prunableAttachment != null) {
            json.put("prunableAttachmentJSON", prunableAttachment);
        }
    }

    static void putException(JSONObject json, Exception e) {
        putException(json, e, "");
    }

    static void putException(JSONObject json, Exception e, String error) {
        json.put("errorCode", 4);
        if (error.length() > 0) {
            error += ": ";
        }
        json.put("error", e.toString());
        json.put("errorDescription", error + e.getMessage());
    }

    static void putAccount(JSONObject json, String name, Account.FullId accountId) {
        json.put(name, accountId != null ? accountId.toString() : "0");
        json.put(name + "RS", accountId != null ? accountId.toRS() : "");
    }

    static void putAccount(JSONObject json, String name, long accountId, int extraId) {
        putAccount(json,name,new Account.FullId(accountId, extraId));
    }

    private static void putAssetInfo(JSONObject json, long assetId) {
        Asset asset = Asset.getAsset(assetId);
        json.put("name", asset.getName());
        json.put("decimals", asset.getDecimals());
    }

    private static void putExpectedTransaction(JSONObject json, Transaction transaction) {
        json.put("height", Metro.getBlockchain().getHeight() + 1);
        json.put("phased", transaction.getPhasing() != null);
        if (transaction.getBlockId() != 0) { // those values may be wrong for unconfirmed transactions
            json.put("transactionHeight", transaction.getHeight());
            json.put("confirmations", Metro.getBlockchain().getHeight() - transaction.getHeight());
        }
    }

    static void ledgerEntry(JSONObject json, LedgerEntry entry, boolean includeTransactions, boolean includeHoldingInfo) {
        //FIXME #220 optimize
        putAccount(json, "account", Account.getAccount(entry.getAccountId()).getFullId());
        json.put("ledgerId", Long.toUnsignedString(entry.getLedgerId()));
        json.put("block", Long.toUnsignedString(entry.getBlockId()));
        json.put("height", entry.getHeight());
        json.put("timestamp", entry.getTimestamp());
        json.put("eventType", entry.getEvent().name());
        json.put("event", Long.toUnsignedString(entry.getEventId()));
        json.put("isTransactionEvent", entry.getEvent().isTransaction());
        json.put("change", String.valueOf(entry.getChange()));
        json.put("balance", String.valueOf(entry.getBalance()));
        AccountLedger.LedgerHolding ledgerHolding = entry.getHolding();
        if (ledgerHolding != null) {
            json.put("holdingType", ledgerHolding.name());
            if (entry.getHoldingId() != null) {
                json.put("holding", Long.toUnsignedString(entry.getHoldingId()));
            }
            if (includeHoldingInfo) {
                JSONObject holdingJson = null;
                if (ledgerHolding == AccountLedger.LedgerHolding.ASSET_BALANCE
                        || ledgerHolding == AccountLedger.LedgerHolding.UNCONFIRMED_ASSET_BALANCE) {
                    holdingJson = new JSONObject();
                    putAssetInfo(holdingJson, entry.getHoldingId());
                }
                if (holdingJson != null) {
                    json.put("holdingInfo", holdingJson);
                }
            }
        }
        if (includeTransactions && entry.getEvent().isTransaction()) {
            Transaction transaction = Metro.getBlockchain().getTransaction(entry.getEventId());
            json.put("transaction", JSONData.transaction(transaction));
        }
    }

    private JSONData() {} // never

}
