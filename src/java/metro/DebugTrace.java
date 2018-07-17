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

package metro;

import metro.db.DbIterator;
import metro.util.Logger;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DebugTrace {

    static final String QUOTE = Metro.getStringProperty("metro.debugTraceQuote", "\"");
    static final String SEPARATOR = Metro.getStringProperty("metro.debugTraceSeparator", "\t");
    static final boolean LOG_UNCONFIRMED = Metro.getBooleanProperty("metro.debugLogUnconfirmed");

    static void init() {
        List<String> accountIdStrings = Metro.getStringListProperty("metro.debugTraceAccounts");
        String logName = Metro.getStringProperty("metro.debugTraceLog");
        if (accountIdStrings.isEmpty() || logName == null) {
            return;
        }
        Set<Long> accountIds = new HashSet<>();
        for (String accountId : accountIdStrings) {
            if ("*".equals(accountId)) {
                accountIds.clear();
                break;
            }
            accountIds.add(Account.FullId.fromStrId(accountId).getLeft());
        }
        final DebugTrace debugTrace = addDebugTrace(accountIds, logName);
        Metro.getBlockchainProcessor().addListener(block -> debugTrace.resetLog(), BlockchainProcessor.Event.RESCAN_BEGIN);
        Logger.logDebugMessage("Debug tracing of " + (accountIdStrings.contains("*") ? "ALL"
                : String.valueOf(accountIds.size())) + " accounts enabled");
    }

    public static DebugTrace addDebugTrace(Set<Long> accountIds, String logName) {
        final DebugTrace debugTrace = new DebugTrace(accountIds, logName);
        Trade.addListener(debugTrace::trace, Trade.Event.TRADE);
        Account.addListener(account -> debugTrace.trace(account, false), Account.Event.BALANCE);
        if (LOG_UNCONFIRMED) {
            Account.addListener(account -> debugTrace.trace(account, true), Account.Event.UNCONFIRMED_BALANCE);
        }
        Account.addAssetListener(accountAsset -> debugTrace.trace(accountAsset, false), Account.Event.ASSET_BALANCE);
        if (LOG_UNCONFIRMED) {
            Account.addAssetListener(accountAsset -> debugTrace.trace(accountAsset, true), Account.Event.UNCONFIRMED_ASSET_BALANCE);
        }
        Account.addLeaseListener(accountLease -> debugTrace.trace(accountLease, true), Account.Event.LEASE_STARTED);
        Account.addLeaseListener(accountLease -> debugTrace.trace(accountLease, false), Account.Event.LEASE_ENDED);
        Metro.getBlockchainProcessor().addListener(debugTrace::traceBeforeAccept, BlockchainProcessor.Event.BEFORE_BLOCK_ACCEPT);
        Metro.getBlockchainProcessor().addListener(debugTrace::trace, BlockchainProcessor.Event.BEFORE_BLOCK_APPLY);
        Metro.getTransactionProcessor().addListener(transactions -> debugTrace.traceRelease(transactions.get(0)), TransactionProcessor.Event.RELEASE_PHASED_TRANSACTION);
        Shuffling.addListener(debugTrace::traceShufflingDistribute, Shuffling.Event.SHUFFLING_DONE);
        Shuffling.addListener(debugTrace::traceShufflingCancel, Shuffling.Event.SHUFFLING_CANCELLED);
        return debugTrace;
    }

    //NOTE: first and last columns should not have a blank entry in any row, otherwise VerifyTrace fails to parse the line
    private static final String[] columns = {"height", "event", "account", "asset", "balance", "unconfirmed balance",
            "asset balance", "unconfirmed asset balance",
            "transaction amount", "transaction fee", "generation fee", "effective balance", "dividend",
            "order", "order price", "order quantity", "order cost",
            "offer", "buy rate", "sell rate", "buy units", "sell units", "buy cost", "sell cost",
            "trade price", "trade quantity", "trade cost",
            "asset quantity", "transaction", "lessee", "lessor guaranteed balance",
            "purchase", "purchase price", "purchase quantity", "purchase cost", "discount", "refund",
            "shuffling",
            "sender", "recipient", "block", "timestamp"};

    private static final Map<String,String> headers = new HashMap<>();
    static {
        for (String entry : columns) {
            headers.put(entry, entry);
        }
    }

    private final Set<Long> accountIds;
    private final String logName;
    private PrintWriter log;

    private DebugTrace(Set<Long> accountIds, String logName) {
        this.accountIds = accountIds;
        this.logName = logName;
        resetLog();
    }

    void resetLog() {
        if (log != null) {
            log.close();
        }
        try {
            log = new PrintWriter((new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logName)))), true);
        } catch (IOException e) {
            Logger.logDebugMessage("Debug tracing to " + logName + " not possible", e);
            throw new RuntimeException(e);
        }
        this.log(headers);
    }

    private boolean include(long accountId) {
        return accountId != 0 && (accountIds.isEmpty() || accountIds.contains(accountId));
    }

    private boolean include(Account.FullId accountId) {
        return accountId != null && accountId.getLeft() != 0 && (accountIds.isEmpty() || accountIds.contains(accountId.getLeft()));
    }

    // Note: Trade events occur before the change in account balances
    private void trace(Trade trade) {
        long askAccountId = Order.Ask.getAskOrder(trade.getAskOrderId()).getAccountId();
        long bidAccountId = Order.Bid.getBidOrder(trade.getBidOrderId()).getAccountId();
        if (include(askAccountId)) {
            log(getValues(askAccountId, trade, true));
        }
        if (include(bidAccountId)) {
            log(getValues(bidAccountId, trade, false));
        }
    }

    private void trace(Account account, boolean unconfirmed) {
        if (include(account.getFullId())) {
            log(getValues(account.getFullId(), unconfirmed));
        }
    }

    private void trace(Account.AccountAsset accountAsset, boolean unconfirmed) {
        if (! include(accountAsset.getAccountId())) {
            return;
        }
        log(getValues(accountAsset.getAccountId(), accountAsset, unconfirmed));
    }

    private void trace(Account.AccountLease accountLease, boolean start) {
        if (! include(accountLease.getCurrentLesseeId()) && ! include(accountLease.getLessorId())) {
            return;
        }
        log(getValues(accountLease.getLessorId(), accountLease, start));
    }

    private void traceBeforeAccept(Block block) {
        Account.FullId generatorId = block.getGeneratorFullId();
        if (include(generatorId)) {
            log(getValues(generatorId, block));
        }
        for (long accountId : accountIds) {
            Account account = Account.getAccount(accountId);
            if (account != null) {
                try (DbIterator<Account> lessors = account.getLessors()) {
                    while (lessors.hasNext()) {
                        log(lessorGuaranteedBalance(lessors.next(), accountId));
                    }
                }
            }
        }
    }

    private void trace(Block block) {
        for (Transaction transaction : block.getTransactions()) {
            Account.FullId senderFullId = transaction.getSenderFullId();
            if (((TransactionImpl)transaction).attachmentIsPhased()) {
                if (include(senderFullId)) {
                    log(getValues(senderFullId, transaction, false, true, false));
                }
                continue;
            }
            if (include(senderFullId)) {
                log(getValues(senderFullId, transaction, false, true, true));
                log(getValues(senderFullId, transaction, transaction.getAttachment(), false));
            }
            Account.FullId recipientId = transaction.getRecipientFullId();
            if (transaction.getAmountMQT() > 0 && recipientId == null) {
                recipientId = Genesis.BURNING_ACCOUNT_ID;
            }
            if (include(recipientId)) {
                log(getValues(recipientId, transaction, true, true, true));
                log(getValues(recipientId, transaction, transaction.getAttachment(), true));
            }
        }
    }

    private void traceRelease(Transaction transaction) {
        Account.FullId senderId = transaction.getSenderFullId();
        if (include(senderId)) {
            log(getValues(senderId, transaction, false, false, true));
            log(getValues(senderId, transaction, transaction.getAttachment(), false));
        }
        Account.FullId recipientId = transaction.getRecipientFullId();
        if (include(recipientId)) {
            log(getValues(recipientId, transaction, true, false, true));
            log(getValues(recipientId, transaction, transaction.getAttachment(), true));
        }
    }

    private void traceShufflingDistribute(Shuffling shuffling) {
        ShufflingParticipant.getParticipants(shuffling.getId()).forEach(shufflingParticipant -> {
            if (include(shufflingParticipant.getAccountId())) {
                log(getValues(shufflingParticipant.getAccountId(), shuffling, false));
            }
        });
        for (byte[] recipientPublicKey : shuffling.getRecipientPublicKeys()) {
            Account.FullId recipientId = Account.FullId.fromPublicKey(recipientPublicKey);
            if (include(recipientId)) {
                log(getValues(recipientId.getLeft(), shuffling, true));
            }
        }
    }

    private void traceShufflingCancel(Shuffling shuffling) {
        long blamedAccountId = shuffling.getAssigneeAccountId();
        if (blamedAccountId != 0 && include(blamedAccountId)) {
            Map<String,String> map = getValues(blamedAccountId, false);
            map.put("transaction fee", String.valueOf(-Constants.SHUFFLING_DEPOSIT_MQT));
            map.put("event", "shuffling blame");
            log(map);
            long fee = Constants.SHUFFLING_DEPOSIT_MQT / 4;
            int height = Metro.getBlockchain().getHeight();
            for (int i = 0; i < 3; i++) {
                Account.FullId generatorId = BlockDb.findBlockAtHeight(height - i - 1).getGeneratorFullId();
                if (include(generatorId)) {
                    Map<String, String> generatorMap = getValues(generatorId, false);
                    generatorMap.put("generation fee", String.valueOf(fee));
                    generatorMap.put("event", "shuffling blame");
                    log(generatorMap);
                }
            }
            fee = Constants.SHUFFLING_DEPOSIT_MQT - 3 * fee;
            Account.FullId generatorId = Metro.getBlockchain().getLastBlock().getGeneratorFullId();
            if (include(generatorId)) {
                Map<String,String> generatorMap = getValues(generatorId, false);
                generatorMap.put("generation fee", String.valueOf(fee));
                generatorMap.put("event", "shuffling blame");
                log(generatorMap);
            }
        }
    }

    private Map<String,String> lessorGuaranteedBalance(Account account, long lesseeId) {
        Map<String,String> map = new HashMap<>();
        map.put("account", Long.toUnsignedString(account.getId()));
        map.put("lessor guaranteed balance", String.valueOf(account.getGuaranteedBalanceMQT()));
        map.put("lessee", Long.toUnsignedString(lesseeId));
        map.put("timestamp", String.valueOf(Metro.getBlockchain().getLastBlock().getTimestamp()));
        map.put("height", String.valueOf(Metro.getBlockchain().getHeight()));
        map.put("event", "lessor guaranteed balance");
        return map;
    }

    private Map<String,String> getValues(long accountId, boolean unconfirmed) {
        return getValues(Account.getAccount(accountId), unconfirmed);
    }

    private Map<String,String> getValues(Account.FullId accountFullId, boolean unconfirmed) {
        return getValues(Account.getAccount(accountFullId), unconfirmed);
    }

    private Map<String,String> getValues(Account account, boolean unconfirmed) {
        Map<String,String> map = new HashMap<>();
        map.put("account", account != null ? account.getFullId().toString() : "0");
        map.put("balance", String.valueOf(account != null ? account.getBalanceMQT() : 0));
        map.put("unconfirmed balance", String.valueOf(account != null ? account.getUnconfirmedBalanceMQT() : 0));
        map.put("timestamp", String.valueOf(Metro.getBlockchain().getLastBlock().getTimestamp()));
        map.put("height", String.valueOf(Metro.getBlockchain().getHeight()));
        map.put("event", unconfirmed ? "unconfirmed balance" : "balance");
        return map;
    }

    private Map<String,String> getValues(long accountId, Trade trade, boolean isAsk) {
        Map<String,String> map = getValues(accountId, false);
        map.put("asset", Long.toUnsignedString(trade.getAssetId()));
        map.put("trade quantity", String.valueOf(isAsk ? - trade.getQuantityQNT() : trade.getQuantityQNT()));
        map.put("trade price", String.valueOf(trade.getPriceMQT()));
        long tradeCost = Math.multiplyExact(trade.getQuantityQNT(), trade.getPriceMQT());
        map.put("trade cost", String.valueOf((isAsk ? tradeCost : - tradeCost)));
        map.put("event", "trade");
        return map;
    }

    private Map<String,String> getValues(long accountId, Shuffling shuffling, boolean isRecipient) {
        Map<String,String> map = getValues(accountId, false);
        map.put("shuffling", Long.toUnsignedString(shuffling.getId()));
        String amount = String.valueOf(isRecipient ? shuffling.getAmount() : -shuffling.getAmount());
        String deposit = String.valueOf(isRecipient ? Constants.SHUFFLING_DEPOSIT_MQT : -Constants.SHUFFLING_DEPOSIT_MQT);
        if (shuffling.getHoldingType() == HoldingType.MTR) {
            map.put("transaction amount", amount);
        } else if (shuffling.getHoldingType() == HoldingType.ASSET) {
            map.put("asset quantity", amount);
            map.put("asset", Long.toUnsignedString(shuffling.getHoldingId()));
            map.put("transaction amount", deposit);
        } else {
            throw new RuntimeException("Unsupported holding type " + shuffling.getHoldingType());
        }
        map.put("event", "shuffling distribute");
        return map;
    }

    private Map<String,String> getValues(Account.FullId accountId, Transaction transaction, boolean isRecipient, boolean logFee, boolean logAmount) {
        long amount = transaction.getAmountMQT();
        long fee = transaction.getFeeMQT();
        if (isRecipient) {
            fee = 0; // fee doesn't affect recipient account
        } else {
            // for sender the amounts are subtracted
            amount = - amount;
            fee = - fee;
        }
        if (fee == 0 && amount == 0) {
            return Collections.emptyMap();
        }
        Map<String,String> map = getValues(accountId, false);
        if (logAmount) {
            map.put("transaction amount", String.valueOf(amount));
        }
        if (logFee) {
            map.put("transaction fee", String.valueOf(fee));
        }
        map.put("transaction", transaction.getStringId());
        if (isRecipient) {
            map.put("sender", transaction.getSenderFullId().toString());
        } else {
            map.put("recipient", transaction.getRecipientFullId().toString());
        }
        map.put("event", "transaction");
        return map;
    }

    private Map<String,String> getValues(Account.FullId accountId, Block block) {
        long reward = block.getRewardMQT();
        if (reward == 0) {
            return Collections.emptyMap();
        }
        long totalBackFees = 0;
        if (block.getHeight() > 3) {
            long[] backFees = new long[3];
            for (Transaction transaction : block.getTransactions()) {
                long[] fees = ((TransactionImpl)transaction).getBackFees();
                for (int i = 0; i < fees.length; i++) {
                    backFees[i] += fees[i];
                }
            }
            for (int i = 0; i < backFees.length; i++) {
                if (backFees[i] == 0) {
                    break;
                }
                totalBackFees += backFees[i];
                long previousGeneratorId = BlockDb.findBlockAtHeight(block.getHeight() - i - 1).getGeneratorId();
                if (include(previousGeneratorId)) {
                    Map<String,String> map = getValues(previousGeneratorId, false);
                    map.put("effective balance", String.valueOf(Account.getAccount(previousGeneratorId).getEffectiveBalanceMTR()));
                    map.put("generation reward", String.valueOf(backFees[i]));
                    map.put("block", block.getStringId());
                    map.put("event", "block");
                    map.put("timestamp", String.valueOf(block.getTimestamp()));
                    map.put("height", String.valueOf(block.getHeight()));
                    log(map);
                }
            }
        }
        Map<String,String> map = getValues(accountId.getLeft(), false);
        map.put("effective balance", String.valueOf(Account.getAccount(accountId).getEffectiveBalanceMTR()));
        map.put("generation reward", String.valueOf(reward - totalBackFees));
        map.put("block", block.getStringId());
        map.put("event", "block");
        map.put("timestamp", String.valueOf(block.getTimestamp()));
        map.put("height", String.valueOf(block.getHeight()));
        return map;
    }

    private Map<String,String> getValues(long accountId, Account.AccountAsset accountAsset, boolean unconfirmed) {
        Map<String,String> map = new HashMap<>();
        map.put("account", Long.toUnsignedString(accountId));
        map.put("asset", Long.toUnsignedString(accountAsset.getAssetId()));
        if (unconfirmed) {
            map.put("unconfirmed asset balance", String.valueOf(accountAsset.getUnconfirmedQuantityQNT()));
        } else {
            map.put("asset balance", String.valueOf(accountAsset.getQuantityQNT()));
        }
        map.put("timestamp", String.valueOf(Metro.getBlockchain().getLastBlock().getTimestamp()));
        map.put("height", String.valueOf(Metro.getBlockchain().getHeight()));
        map.put("event", "asset balance");
        return map;
    }

    private Map<String,String> getValues(long accountId, Account.AccountLease accountLease, boolean start) {
        Map<String,String> map = new HashMap<>();
        map.put("account", Long.toUnsignedString(accountId));
        map.put("event", start ? "lease begin" : "lease end");
        map.put("timestamp", String.valueOf(Metro.getBlockchain().getLastBlock().getTimestamp()));
        map.put("height", String.valueOf(Metro.getBlockchain().getHeight()));
        map.put("lessee", Long.toUnsignedString(accountLease.getCurrentLesseeId()));
        return map;
    }

    private Map<String,String> getValues(Account.FullId accountId, Transaction transaction, Attachment attachment, boolean isRecipient) {
        Map<String,String> map = getValues(accountId, false);
        if (attachment instanceof Attachment.ColoredCoinsOrderPlacement) {
            if (isRecipient) {
                return Collections.emptyMap();
            }
            Attachment.ColoredCoinsOrderPlacement orderPlacement = (Attachment.ColoredCoinsOrderPlacement)attachment;
            boolean isAsk = orderPlacement instanceof Attachment.ColoredCoinsAskOrderPlacement;
            map.put("asset", Long.toUnsignedString(orderPlacement.getAssetId()));
            map.put("order", transaction.getStringId());
            map.put("order price", String.valueOf(orderPlacement.getPriceMQT()));
            long quantity = orderPlacement.getQuantityQNT();
            if (isAsk) {
                quantity = - quantity;
            }
            map.put("order quantity", String.valueOf(quantity));
            BigInteger orderCost = BigInteger.valueOf(orderPlacement.getPriceMQT()).multiply(BigInteger.valueOf(orderPlacement.getQuantityQNT()));
            if (! isAsk) {
                orderCost = orderCost.negate();
            }
            map.put("order cost", orderCost.toString());
            String event = (isAsk ? "ask" : "bid") + " order";
            map.put("event", event);
        } else if (attachment instanceof Attachment.ColoredCoinsAssetIssuance) {
            if (isRecipient) {
                return Collections.emptyMap();
            }
            Attachment.ColoredCoinsAssetIssuance assetIssuance = (Attachment.ColoredCoinsAssetIssuance)attachment;
            map.put("asset", transaction.getStringId());
            map.put("asset quantity", String.valueOf(assetIssuance.getQuantityQNT()));
            map.put("event", "asset issuance");
        } else if (attachment instanceof Attachment.ColoredCoinsAssetTransfer) {
            Attachment.ColoredCoinsAssetTransfer assetTransfer = (Attachment.ColoredCoinsAssetTransfer)attachment;
            map.put("asset", Long.toUnsignedString(assetTransfer.getAssetId()));
            long quantity = assetTransfer.getQuantityQNT();
            if (! isRecipient) {
                quantity = - quantity;
            }
            map.put("asset quantity", String.valueOf(quantity));
            map.put("event", "asset transfer");
        } else if (attachment instanceof Attachment.ColoredCoinsAssetDelete) {
            if (isRecipient) {
                return Collections.emptyMap();
            }
            Attachment.ColoredCoinsAssetDelete assetDelete = (Attachment.ColoredCoinsAssetDelete)attachment;
            map.put("asset", Long.toUnsignedString(assetDelete.getAssetId()));
            long quantity = assetDelete.getQuantityQNT();
            map.put("asset quantity", String.valueOf(-quantity));
            map.put("event", "asset delete");
        } else if (attachment instanceof Attachment.ColoredCoinsOrderCancellation) {
            Attachment.ColoredCoinsOrderCancellation orderCancellation = (Attachment.ColoredCoinsOrderCancellation)attachment;
            map.put("order", Long.toUnsignedString(orderCancellation.getOrderId()));
            map.put("event", "order cancel");
        } else if (attachment == Attachment.ARBITRARY_MESSAGE) {
            map = new HashMap<>();
            map.put("account", accountId.toString());
            map.put("timestamp", String.valueOf(Metro.getBlockchain().getLastBlock().getTimestamp()));
            map.put("height", String.valueOf(Metro.getBlockchain().getHeight()));
            map.put("event", attachment == Attachment.ARBITRARY_MESSAGE ? "message" : "encrypted message");
            if (isRecipient) {
                map.put("sender", transaction.getSenderFullId().toString());
            } else {
                map.put("recipient", transaction.getRecipientFullId().toString());
            }
        } else if (attachment instanceof Attachment.ColoredCoinsDividendPayment) {
            Attachment.ColoredCoinsDividendPayment dividendPayment = (Attachment.ColoredCoinsDividendPayment)attachment;
            long totalDividend = 0;
            String assetId = Long.toUnsignedString(dividendPayment.getAssetId());
            try (DbIterator<Account.AccountAsset> iterator = Account.getAssetAccounts(dividendPayment.getAssetId(), dividendPayment.getHeight(), 0, -1)) {
                while (iterator.hasNext()) {
                    Account.AccountAsset accountAsset = iterator.next();
                    if (accountAsset.getAccountId() != accountId.getLeft() && accountAsset.getQuantityQNT() != 0) {
                        long dividend = Math.multiplyExact(accountAsset.getQuantityQNT(), dividendPayment.getAmountMQTPerQNT());
                        Map recipient = getValues(accountAsset.getAccountId(), false);
                        recipient.put("dividend", String.valueOf(dividend));
                        recipient.put("asset", assetId);
                        recipient.put("event", "dividend");
                        totalDividend += dividend;
                        log(recipient);
                    }
                }
            }
            map.put("dividend", String.valueOf(-totalDividend));
            map.put("asset", assetId);
            map.put("event", "dividend");
        } else {
            return Collections.emptyMap();
        }
        return map;
    }

    private void log(Map<String,String> map) {
        if (map.isEmpty()) {
            return;
        }
        StringBuilder buf = new StringBuilder();
        for (String column : columns) {
            if (!LOG_UNCONFIRMED && column.startsWith("unconfirmed")) {
                continue;
            }
            String value = map.get(column);
            if (value != null) {
                buf.append(QUOTE).append(value).append(QUOTE);
            }
            buf.append(SEPARATOR);
        }
        log.println(buf.toString());
    }

}
