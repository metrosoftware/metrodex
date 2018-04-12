/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
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

import metro.Account.ControlType;
import metro.AccountLedger.LedgerEvent;
import metro.Attachment.AbstractAttachment;
import metro.MetroException.ValidationException;
import metro.VoteWeighting.VotingModel;
import metro.util.Convert;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;


public abstract class TransactionType {

    private static final byte TYPE_PAYMENT = 0;
    private static final byte TYPE_MESSAGING = 1;
    private static final byte TYPE_COLORED_COINS = 2;
    static final byte TYPE_SHUFFLING = 3;
    private static final byte TYPE_ACCOUNT_CONTROL = 4;
    private static final byte TYPE_COINBASE = 5;

    private static final int[] KEYBLOCK_TRANSACTION_TYPES = {TYPE_PAYMENT, TYPE_MESSAGING, TYPE_COLORED_COINS, TYPE_SHUFFLING,
            TYPE_ACCOUNT_CONTROL, TYPE_COINBASE};
    private static final int[] POSBLOCK_TRANSACTION_TYPES = {};

    private static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;

    private static final byte SUBTYPE_MESSAGING_ARBITRARY_MESSAGE = 0;
    private static final byte SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT = 1;
    private static final byte SUBTYPE_MESSAGING_POLL_CREATION = 2;
    private static final byte SUBTYPE_MESSAGING_VOTE_CASTING = 3;
    private static final byte SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT = 4;
    private static final byte SUBTYPE_MESSAGING_ACCOUNT_INFO = 5;
    private static final byte SUBTYPE_MESSAGING_ALIAS_SELL = 6;
    private static final byte SUBTYPE_MESSAGING_ALIAS_BUY = 7;
    private static final byte SUBTYPE_MESSAGING_ALIAS_DELETE = 8;
    private static final byte SUBTYPE_MESSAGING_PHASING_VOTE_CASTING = 9;
    private static final byte SUBTYPE_MESSAGING_ACCOUNT_PROPERTY = 10;
    private static final byte SUBTYPE_MESSAGING_ACCOUNT_PROPERTY_DELETE = 11;

    private static final byte SUBTYPE_COLORED_COINS_ASSET_ISSUANCE = 0;
    private static final byte SUBTYPE_COLORED_COINS_ASSET_TRANSFER = 1;
    private static final byte SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT = 2;
    private static final byte SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT = 3;
    private static final byte SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION = 4;
    private static final byte SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION = 5;
    private static final byte SUBTYPE_COLORED_COINS_DIVIDEND_PAYMENT = 6;
    private static final byte SUBTYPE_COLORED_COINS_ASSET_DELETE = 7;

    private static final byte SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING = 0;
    private static final byte SUBTYPE_ACCOUNT_CONTROL_PHASING_ONLY = 1;

    private static final byte SUBTYPE_COINBASE_ORDINARY_COINBASE = 0;

    public static TransactionType findTransactionType(byte type, byte subtype) {
        switch (type) {
            case TYPE_PAYMENT:
                switch (subtype) {
                    case SUBTYPE_PAYMENT_ORDINARY_PAYMENT:
                        return Payment.ORDINARY;
                    default:
                        return null;
                }
            case TYPE_MESSAGING:
                switch (subtype) {
                    case SUBTYPE_MESSAGING_ARBITRARY_MESSAGE:
                        return Messaging.ARBITRARY_MESSAGE;
                    case SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT:
                        return Messaging.ALIAS_ASSIGNMENT;
                    case SUBTYPE_MESSAGING_POLL_CREATION:
                        return Messaging.POLL_CREATION;
                    case SUBTYPE_MESSAGING_VOTE_CASTING:
                        return Messaging.VOTE_CASTING;
                    case SUBTYPE_MESSAGING_HUB_ANNOUNCEMENT:
                        throw new IllegalArgumentException("Hub Announcement no longer supported");
                    case SUBTYPE_MESSAGING_ACCOUNT_INFO:
                        return Messaging.ACCOUNT_INFO;
                    case SUBTYPE_MESSAGING_ALIAS_SELL:
                        return Messaging.ALIAS_SELL;
                    case SUBTYPE_MESSAGING_ALIAS_BUY:
                        return Messaging.ALIAS_BUY;
                    case SUBTYPE_MESSAGING_ALIAS_DELETE:
                        return Messaging.ALIAS_DELETE;
                    case SUBTYPE_MESSAGING_PHASING_VOTE_CASTING:
                        return Messaging.PHASING_VOTE_CASTING;
                    case SUBTYPE_MESSAGING_ACCOUNT_PROPERTY:
                        return Messaging.ACCOUNT_PROPERTY;
                    case SUBTYPE_MESSAGING_ACCOUNT_PROPERTY_DELETE:
                        return Messaging.ACCOUNT_PROPERTY_DELETE;
                    default:
                        return null;
                }
            case TYPE_COLORED_COINS:
                switch (subtype) {
                    case SUBTYPE_COLORED_COINS_ASSET_ISSUANCE:
                        return ColoredCoins.ASSET_ISSUANCE;
                    case SUBTYPE_COLORED_COINS_ASSET_TRANSFER:
                        return ColoredCoins.ASSET_TRANSFER;
                    case SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT:
                        return ColoredCoins.ASK_ORDER_PLACEMENT;
                    case SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT:
                        return ColoredCoins.BID_ORDER_PLACEMENT;
                    case SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION:
                        return ColoredCoins.ASK_ORDER_CANCELLATION;
                    case SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION:
                        return ColoredCoins.BID_ORDER_CANCELLATION;
                    case SUBTYPE_COLORED_COINS_DIVIDEND_PAYMENT:
                        return ColoredCoins.DIVIDEND_PAYMENT;
                    case SUBTYPE_COLORED_COINS_ASSET_DELETE:
                        return ColoredCoins.ASSET_DELETE;
                    default:
                        return null;
                }
            case TYPE_ACCOUNT_CONTROL:
                switch (subtype) {
                    case SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING:
                        return TransactionType.AccountControl.EFFECTIVE_BALANCE_LEASING;
                    case SUBTYPE_ACCOUNT_CONTROL_PHASING_ONLY:
                        return TransactionType.AccountControl.SET_PHASING_ONLY;
                    default:
                        return null;
                }
            case TYPE_SHUFFLING:
                return ShufflingTransaction.findTransactionType(subtype);
            case TYPE_COINBASE:
                switch (subtype) {
                    case SUBTYPE_COINBASE_ORDINARY_COINBASE:
                        return Coinbase.ORDINARY;
                    default:
                        return null;
                }
            default:
                return null;
        }
    }


    TransactionType() {}

    public abstract byte getType();

    public abstract byte getSubtype();

    public abstract LedgerEvent getLedgerEvent();

    public boolean isCoinbase() {
        return getType() == TYPE_COINBASE;
    }

    public boolean isKeyBlockTransaction() { return IntStream.of(KEYBLOCK_TRANSACTION_TYPES).anyMatch(value -> value == getType()); }
    public boolean isPoSBlockTransaction() { return IntStream.of(POSBLOCK_TRANSACTION_TYPES).anyMatch(value -> value == getType()); }

    abstract Attachment.AbstractAttachment parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException;

    abstract Attachment.AbstractAttachment parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException;

    abstract void validateAttachment(Transaction transaction) throws MetroException.ValidationException;

    // return false iff double spending
    final boolean applyUnconfirmed(TransactionImpl tx, Account senderAccount) {
        long spendingAmountMQT = tx.getType().isCoinbase() ? 0 : tx.getAmountMQT();
        long feeMQT = tx.getFeeMQT();
        if (tx.referencedTransactionFullHash() != null) {
            feeMQT = Math.addExact(feeMQT, Constants.UNCONFIRMED_POOL_DEPOSIT_MQT);
        }
        long totalAmountMQT = Math.addExact(spendingAmountMQT, feeMQT);
        if (senderAccount.getUnconfirmedBalanceMQT() < totalAmountMQT) {
            return false;
        }
        senderAccount.addToUnconfirmedBalanceMQT(getLedgerEvent(), tx.getId(), -spendingAmountMQT, -feeMQT);
        if (!applyAttachmentUnconfirmed(tx, senderAccount)) {
            senderAccount.addToUnconfirmedBalanceMQT(getLedgerEvent(), tx.getId(), spendingAmountMQT, feeMQT);
            return false;
        }
        return true;
    }

    abstract boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

    final void apply(TransactionImpl tx, Account senderAccount, Account recipientAccount) {
        long spendingAmountMQT = tx.getType().isCoinbase() ? 0 : tx.getAmountMQT();
        long transactionId = tx.getId();
        if (!tx.attachmentIsPhased()) {
            senderAccount.addToBalanceMQT(getLedgerEvent(), transactionId, -spendingAmountMQT, -tx.getFeeMQT());
        } else {
            senderAccount.addToBalanceMQT(getLedgerEvent(), transactionId, -spendingAmountMQT);
        }
        if (recipientAccount != null) {
            recipientAccount.addToBalanceAndUnconfirmedBalanceMQT(getLedgerEvent(), transactionId, tx.getAmountMQT());
        }
        applyAttachment(tx, senderAccount, recipientAccount);
    }

    abstract void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount);

    final void undoUnconfirmed(TransactionImpl transaction, Account senderAccount) {
        undoAttachmentUnconfirmed(transaction, senderAccount);
        senderAccount.addToUnconfirmedBalanceMQT(getLedgerEvent(), transaction.getId(),
                transaction.getAmountMQT(), transaction.getFeeMQT());
        if (transaction.referencedTransactionFullHash() != null) {
            senderAccount.addToUnconfirmedBalanceMQT(getLedgerEvent(), transaction.getId(), 0,
                    Constants.UNCONFIRMED_POOL_DEPOSIT_MQT);
        }
    }

    abstract void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount);

    boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        return false;
    }

    // isBlockDuplicate and isDuplicate share the same duplicates map, but isBlockDuplicate check is done first
    boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        return false;
    }

    boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
        return false;
    }

    static boolean isDuplicate(TransactionType uniqueType, String key, Map<TransactionType, Map<String, Integer>> duplicates, boolean exclusive) {
        return isDuplicate(uniqueType, key, duplicates, exclusive ? 0 : Integer.MAX_VALUE);
    }

    static boolean isDuplicate(TransactionType uniqueType, String key, Map<TransactionType, Map<String, Integer>> duplicates, int maxCount) {
        Map<String,Integer> typeDuplicates = duplicates.get(uniqueType);
        if (typeDuplicates == null) {
            typeDuplicates = new HashMap<>();
            duplicates.put(uniqueType, typeDuplicates);
        }
        Integer currentCount = typeDuplicates.get(key);
        if (currentCount == null) {
            typeDuplicates.put(key, maxCount > 0 ? 1 : 0);
            return false;
        }
        if (currentCount == 0) {
            return true;
        }
        if (currentCount < maxCount) {
            typeDuplicates.put(key, currentCount + 1);
            return false;
        }
        return true;
    }

    boolean isPruned(long transactionId) {
        return false;
    }

    public abstract boolean canHaveRecipient();

    public boolean mustHaveRecipient() {
        return canHaveRecipient();
    }

    public abstract boolean isPhasingSafe();

    public boolean isPhasable() {
        return true;
    }

    Fee getBaselineFee(Transaction transaction) {
        return Fee.DEFAULT_FEE;
    }

    Fee getNextFee(Transaction transaction) {
        return getBaselineFee(transaction);
    }

    int getBaselineFeeHeight() {
        return 1;
    }

    int getNextFeeHeight() {
        return Integer.MAX_VALUE;
    }

    long[] getBackFees(Transaction transaction) {
        return Convert.EMPTY_LONG;
    }

    public abstract String getName();

    @Override
    public final String toString() {
        return getName() + " type: " + getType() + ", subtype: " + getSubtype();
    }

    public static abstract class Payment extends TransactionType {

        private Payment() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_PAYMENT;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            if (recipientAccount == null) {
                Account.getAccount(Genesis.CREATOR_ID).addToBalanceAndUnconfirmedBalanceMQT(getLedgerEvent(),
                        transaction.getId(), transaction.getAmountMQT());
            }
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        public final boolean canHaveRecipient() {
            return true;
        }

        @Override
        public final boolean isPhasingSafe() {
            return true;
        }

        public static final TransactionType ORDINARY = new Payment() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_PAYMENT_ORDINARY_PAYMENT;
            }

            @Override
            public final LedgerEvent getLedgerEvent() {
                return LedgerEvent.ORDINARY_PAYMENT;
            }

            @Override
            public String getName() {
                return "OrdinaryPayment";
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return Attachment.ORDINARY_PAYMENT;
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return Attachment.ORDINARY_PAYMENT;
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                if (transaction.getAmountMQT() <= 0 || transaction.getAmountMQT() >= Constants.MAX_BALANCE_MQT) {
                    throw new MetroException.NotValidException("Invalid ordinary payment");
                }
            }

        };

    }

    public static abstract class Coinbase extends TransactionType {

        private Coinbase() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_COINBASE;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            Attachment.CoinbaseRecipientsAttachment attachment = (Attachment.CoinbaseRecipientsAttachment) transaction.getAttachment();
            for (Long recipientId: attachment.getRecipients().keySet()) {
                Account recipient = Account.getAccount(recipientId);
                recipient.addToBalanceAndUnconfirmedBalanceMQT(AccountLedger.LedgerEvent.BLOCK_GENERATED, transaction.getBlockId(), attachment.getRecipients().get(recipientId));
            }
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        @Override
        public final boolean canHaveRecipient() {
            return true;
        }

        @Override
        public final boolean mustHaveRecipient() {
            return true;
        }

        @Override
        public final boolean isPhasingSafe() {
            return false;
        }

        public static final TransactionType ORDINARY = new Coinbase() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_COINBASE_ORDINARY_COINBASE;
            }

            @Override
            public final LedgerEvent getLedgerEvent() {
                return LedgerEvent.ORDINARY_COINBASE;
            } //???

            @Override
            public String getName() {
                return "OrdinaryCoinbase";
            }

            @Override
            Attachment.CoinbaseRecipientsAttachment parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.CoinbaseRecipientsAttachment(buffer);
            }

            @Override
            Attachment.CoinbaseRecipientsAttachment parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.CoinbaseRecipientsAttachment(attachmentData);
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                if (transaction.getAmountMQT() != 0) {
                    throw new MetroException.NotValidException("Invalid ordinary coinbase ammount");
                }
            }

        };

    }

    public static abstract class Messaging extends TransactionType {

        private Messaging() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_MESSAGING;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        public final static TransactionType ARBITRARY_MESSAGE = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ARBITRARY_MESSAGE;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ARBITRARY_MESSAGE;
            }

            @Override
            public String getName() {
                return "ArbitraryMessage";
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return Attachment.ARBITRARY_MESSAGE;
            }

            @Override
            Attachment.EmptyAttachment parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return Attachment.ARBITRARY_MESSAGE;
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                Attachment attachment = transaction.getAttachment();
                if (transaction.getAmountMQT() != 0) {
                    throw new MetroException.NotValidException("Invalid arbitrary message: " + attachment.getJSONObject());
                }
                if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                    throw new MetroException.NotValidException("Sending messages to Genesis not allowed.");
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

            @Override
            public boolean mustHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return false;
            }

        };

        public static final TransactionType ALIAS_ASSIGNMENT = new Messaging() {

            private final Fee ALIAS_FEE = new Fee.SizeBasedFee(2 * Constants.ONE_MTR, 2 * Constants.ONE_MTR, 32) {
                @Override
                public int getSize(TransactionImpl transaction, Appendix appendage) {
                    Attachment.MessagingAliasAssignment attachment = (Attachment.MessagingAliasAssignment) transaction.getAttachment();
                    return attachment.getAliasName().length() + attachment.getAliasURI().length();
                }
            };

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ALIAS_ASSIGNMENT;
            }

            @Override
            public String getName() {
                return "AliasAssignment";
            }

            @Override
            Fee getBaselineFee(Transaction transaction) {
                return ALIAS_FEE;
            }

            @Override
            Attachment.MessagingAliasAssignment parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.MessagingAliasAssignment(buffer);
            }

            @Override
            Attachment.MessagingAliasAssignment parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.MessagingAliasAssignment(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingAliasAssignment attachment = (Attachment.MessagingAliasAssignment) transaction.getAttachment();
                Alias.addOrUpdateAlias(transaction, attachment);
            }

            @Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.MessagingAliasAssignment attachment = (Attachment.MessagingAliasAssignment) transaction.getAttachment();
                return isDuplicate(Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
            }

            @Override
            boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                return Alias.getAlias(((Attachment.MessagingAliasAssignment) transaction.getAttachment()).getAliasName()) == null
                        && isDuplicate(Messaging.ALIAS_ASSIGNMENT, "", duplicates, true);
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                Attachment.MessagingAliasAssignment attachment = (Attachment.MessagingAliasAssignment) transaction.getAttachment();
                if (attachment.getAliasName().length() == 0
                        || attachment.getAliasName().length() > Constants.MAX_ALIAS_LENGTH
                        || attachment.getAliasURI().length() > Constants.MAX_ALIAS_URI_LENGTH) {
                    throw new MetroException.NotValidException("Invalid alias assignment: " + attachment.getJSONObject());
                }
                String normalizedAlias = attachment.getAliasName().toLowerCase();
                for (int i = 0; i < normalizedAlias.length(); i++) {
                    if (Constants.ALPHABET.indexOf(normalizedAlias.charAt(i)) < 0) {
                        throw new MetroException.NotValidException("Invalid alias name: " + normalizedAlias);
                    }
                }
                Alias alias = Alias.getAlias(normalizedAlias);
                if (alias != null && alias.getAccountId() != transaction.getSenderId()) {
                    throw new MetroException.NotCurrentlyValidException("Alias already owned by another account: " + normalizedAlias);
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return false;
            }

        };

        public static final TransactionType ALIAS_SELL = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ALIAS_SELL;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ALIAS_SELL;
            }
            @Override
            public String getName() {
                return "AliasSell";
            }

            @Override
            Attachment.MessagingAliasSell parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.MessagingAliasSell(buffer);
            }

            @Override
            Attachment.MessagingAliasSell parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.MessagingAliasSell(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingAliasSell attachment = (Attachment.MessagingAliasSell) transaction.getAttachment();
                Alias.sellAlias(transaction, attachment);
            }

            @Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.MessagingAliasSell attachment = (Attachment.MessagingAliasSell) transaction.getAttachment();
                // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
                return isDuplicate(Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                if (transaction.getAmountMQT() != 0) {
                    throw new MetroException.NotValidException("Invalid sell alias transaction: " +
                            transaction.getJSONObject());
                }
                final Attachment.MessagingAliasSell attachment =
                        (Attachment.MessagingAliasSell) transaction.getAttachment();
                final String aliasName = attachment.getAliasName();
                if (aliasName == null || aliasName.length() == 0) {
                    throw new MetroException.NotValidException("Missing alias name");
                }
                long priceMQT = attachment.getPriceMQT();
                if (priceMQT < 0 || priceMQT > Constants.MAX_BALANCE_MQT) {
                    throw new MetroException.NotValidException("Invalid alias sell price: " + priceMQT);
                }
                if (priceMQT == 0) {
                    if (Genesis.CREATOR_ID == transaction.getRecipientId()) {
                        throw new MetroException.NotValidException("Transferring aliases to Genesis account not allowed");
                    } else if (transaction.getRecipientId() == 0) {
                        throw new MetroException.NotValidException("Missing alias transfer recipient");
                    }
                }
                final Alias alias = Alias.getAlias(aliasName);
                if (alias == null) {
                    throw new MetroException.NotCurrentlyValidException("No such alias: " + aliasName);
                } else if (alias.getAccountId() != transaction.getSenderId()) {
                    throw new MetroException.NotCurrentlyValidException("Alias doesn't belong to sender: " + aliasName);
                }
                if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                    throw new MetroException.NotValidException("Selling alias to Genesis not allowed");
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

            @Override
            public boolean mustHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return false;
            }

        };

        public static final TransactionType ALIAS_BUY = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ALIAS_BUY;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ALIAS_BUY;
            }

            @Override
            public String getName() {
                return "AliasBuy";
            }

            @Override
            Attachment.MessagingAliasBuy parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.MessagingAliasBuy(buffer);
            }

            @Override
            Attachment.MessagingAliasBuy parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.MessagingAliasBuy(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                final Attachment.MessagingAliasBuy attachment =
                        (Attachment.MessagingAliasBuy) transaction.getAttachment();
                final String aliasName = attachment.getAliasName();
                Alias.changeOwner(transaction.getSenderId(), aliasName);
            }

            @Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.MessagingAliasBuy attachment = (Attachment.MessagingAliasBuy) transaction.getAttachment();
                // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
                return isDuplicate(Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                final Attachment.MessagingAliasBuy attachment =
                        (Attachment.MessagingAliasBuy) transaction.getAttachment();
                final String aliasName = attachment.getAliasName();
                final Alias alias = Alias.getAlias(aliasName);
                if (alias == null) {
                    throw new MetroException.NotCurrentlyValidException("No such alias: " + aliasName);
                } else if (alias.getAccountId() != transaction.getRecipientId()) {
                    throw new MetroException.NotCurrentlyValidException("Alias is owned by account other than recipient: "
                            + Long.toUnsignedString(alias.getAccountId()));
                }
                Alias.Offer offer = Alias.getOffer(alias);
                if (offer == null) {
                    throw new MetroException.NotCurrentlyValidException("Alias is not for sale: " + aliasName);
                }
                if (transaction.getAmountMQT() < offer.getPriceMQT()) {
                    String msg = "Price is too low for: " + aliasName + " ("
                            + transaction.getAmountMQT() + " < " + offer.getPriceMQT() + ")";
                    throw new MetroException.NotCurrentlyValidException(msg);
                }
                if (offer.getBuyerId() != 0 && offer.getBuyerId() != transaction.getSenderId()) {
                    throw new MetroException.NotCurrentlyValidException("Wrong buyer for " + aliasName + ": "
                            + Long.toUnsignedString(transaction.getSenderId()) + " expected: "
                            + Long.toUnsignedString(offer.getBuyerId()));
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

            @Override
            public boolean isPhasingSafe() {
                return false;
            }

        };

        public static final TransactionType ALIAS_DELETE = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ALIAS_DELETE;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ALIAS_DELETE;
            }

            @Override
            public String getName() {
                return "AliasDelete";
            }

            @Override
            Attachment.MessagingAliasDelete parseAttachment(final ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.MessagingAliasDelete(buffer);
            }

            @Override
            Attachment.MessagingAliasDelete parseAttachment(final JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.MessagingAliasDelete(attachmentData);
            }

            @Override
            void applyAttachment(final Transaction transaction, final Account senderAccount, final Account recipientAccount) {
                final Attachment.MessagingAliasDelete attachment =
                        (Attachment.MessagingAliasDelete) transaction.getAttachment();
                Alias.deleteAlias(attachment.getAliasName());
            }

            @Override
            boolean isDuplicate(final Transaction transaction, final Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.MessagingAliasDelete attachment = (Attachment.MessagingAliasDelete) transaction.getAttachment();
                // not a bug, uniqueness is based on Messaging.ALIAS_ASSIGNMENT
                return isDuplicate(Messaging.ALIAS_ASSIGNMENT, attachment.getAliasName().toLowerCase(), duplicates, true);
            }

            @Override
            void validateAttachment(final Transaction transaction) throws MetroException.ValidationException {
                final Attachment.MessagingAliasDelete attachment =
                        (Attachment.MessagingAliasDelete) transaction.getAttachment();
                final String aliasName = attachment.getAliasName();
                if (aliasName == null || aliasName.length() == 0) {
                    throw new MetroException.NotValidException("Missing alias name");
                }
                final Alias alias = Alias.getAlias(aliasName);
                if (alias == null) {
                    throw new MetroException.NotCurrentlyValidException("No such alias: " + aliasName);
                } else if (alias.getAccountId() != transaction.getSenderId()) {
                    throw new MetroException.NotCurrentlyValidException("Alias doesn't belong to sender: " + aliasName);
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return false;
            }

        };

        public final static TransactionType POLL_CREATION = new Messaging() {

            private final Fee POLL_OPTIONS_FEE = new Fee.SizeBasedFee(10 * Constants.ONE_MTR, Constants.ONE_MTR, 1) {
                @Override
                public int getSize(TransactionImpl transaction, Appendix appendage) {
                    int numOptions = ((Attachment.MessagingPollCreation)appendage).getPollOptions().length;
                    return numOptions <= 19 ? 0 : numOptions - 19;
                }
            };

            private final Fee POLL_SIZE_FEE = new Fee.SizeBasedFee(0, 2 * Constants.ONE_MTR, 32) {
                @Override
                public int getSize(TransactionImpl transaction, Appendix appendage) {
                    Attachment.MessagingPollCreation attachment = (Attachment.MessagingPollCreation)appendage;
                    int size = attachment.getPollName().length() + attachment.getPollDescription().length();
                    for (String option : ((Attachment.MessagingPollCreation)appendage).getPollOptions()) {
                        size += option.length();
                    }
                    return size <= 288 ? 0 : size - 288;
                }
            };

            private final Fee POLL_FEE = (transaction, appendage) ->
                    POLL_OPTIONS_FEE.getFee(transaction, appendage) + POLL_SIZE_FEE.getFee(transaction, appendage);

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_POLL_CREATION;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.POLL_CREATION;
            }

            @Override
            public String getName() {
                return "PollCreation";
            }

            @Override
            Fee getBaselineFee(Transaction transaction) {
                return POLL_FEE;
            }

            @Override
            Attachment.MessagingPollCreation parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.MessagingPollCreation(buffer);
            }

            @Override
            Attachment.MessagingPollCreation parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.MessagingPollCreation(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingPollCreation attachment = (Attachment.MessagingPollCreation) transaction.getAttachment();
                Poll.addPoll(transaction, attachment);
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {

                Attachment.MessagingPollCreation attachment = (Attachment.MessagingPollCreation) transaction.getAttachment();

                int optionsCount = attachment.getPollOptions().length;

                if (attachment.getPollName().length() > Constants.MAX_POLL_NAME_LENGTH
                        || attachment.getPollName().isEmpty()
                        || attachment.getPollDescription().length() > Constants.MAX_POLL_DESCRIPTION_LENGTH
                        || optionsCount > Constants.MAX_POLL_OPTION_COUNT
                        || optionsCount == 0) {
                    throw new MetroException.NotValidException("Invalid poll attachment: " + attachment.getJSONObject());
                }

                if (attachment.getMinNumberOfOptions() < 1
                        || attachment.getMinNumberOfOptions() > optionsCount) {
                    throw new MetroException.NotValidException("Invalid min number of options: " + attachment.getJSONObject());
                }

                if (attachment.getMaxNumberOfOptions() < 1
                        || attachment.getMaxNumberOfOptions() < attachment.getMinNumberOfOptions()
                        || attachment.getMaxNumberOfOptions() > optionsCount) {
                    throw new MetroException.NotValidException("Invalid max number of options: " + attachment.getJSONObject());
                }

                for (int i = 0; i < optionsCount; i++) {
                    if (attachment.getPollOptions()[i].length() > Constants.MAX_POLL_OPTION_LENGTH
                            || attachment.getPollOptions()[i].isEmpty()) {
                        throw new MetroException.NotValidException("Invalid poll options length: " + attachment.getJSONObject());
                    }
                }

                if (attachment.getMinRangeValue() < Constants.MIN_VOTE_VALUE || attachment.getMaxRangeValue() > Constants.MAX_VOTE_VALUE
                        || attachment.getMaxRangeValue() < attachment.getMinRangeValue()) {
                    throw new MetroException.NotValidException("Invalid range: " + attachment.getJSONObject());
                }

                if (attachment.getFinishHeight() <= attachment.getFinishValidationHeight(transaction) + 1
                        || attachment.getFinishHeight() >= attachment.getFinishValidationHeight(transaction) + Constants.MAX_POLL_DURATION) {
                    throw new MetroException.NotCurrentlyValidException("Invalid finishing height" + attachment.getJSONObject());
                }

                if (! attachment.getVoteWeighting().acceptsVotes() || attachment.getVoteWeighting().getVotingModel() == VoteWeighting.VotingModel.HASH) {
                    throw new MetroException.NotValidException("VotingModel " + attachment.getVoteWeighting().getVotingModel() + " not valid for regular polls");
                }

                attachment.getVoteWeighting().validate();

            }

            @Override
            boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                return isDuplicate(Messaging.POLL_CREATION, getName(), duplicates, true);
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return false;
            }

        };

        public final static TransactionType VOTE_CASTING = new Messaging() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_VOTE_CASTING;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.VOTE_CASTING;
            }

            @Override
            public String getName() {
                return "VoteCasting";
            }

            @Override
            Attachment.MessagingVoteCasting parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.MessagingVoteCasting(buffer);
            }

            @Override
            Attachment.MessagingVoteCasting parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.MessagingVoteCasting(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingVoteCasting attachment = (Attachment.MessagingVoteCasting) transaction.getAttachment();
                Vote.addVote(transaction, attachment);
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {

                Attachment.MessagingVoteCasting attachment = (Attachment.MessagingVoteCasting) transaction.getAttachment();
                if (attachment.getPollId() == 0 || attachment.getPollVote() == null
                        || attachment.getPollVote().length > Constants.MAX_POLL_OPTION_COUNT) {
                    throw new MetroException.NotValidException("Invalid vote casting attachment: " + attachment.getJSONObject());
                }

                long pollId = attachment.getPollId();

                Poll poll = Poll.getPoll(pollId);
                if (poll == null) {
                    throw new MetroException.NotCurrentlyValidException("Invalid poll: " + Long.toUnsignedString(attachment.getPollId()));
                }

                if (Vote.getVote(pollId, transaction.getSenderId()) != null) {
                    throw new MetroException.NotCurrentlyValidException("Double voting attempt");
                }

                if (poll.getFinishHeight() <= attachment.getFinishValidationHeight(transaction)) {
                    throw new MetroException.NotCurrentlyValidException("Voting for this poll finishes at " + poll.getFinishHeight());
                }

                byte[] votes = attachment.getPollVote();
                int positiveCount = 0;
                for (byte vote : votes) {
                    if (vote != Constants.NO_VOTE_VALUE && (vote < poll.getMinRangeValue() || vote > poll.getMaxRangeValue())) {
                        throw new MetroException.NotValidException(String.format("Invalid vote %d, vote must be between %d and %d",
                                vote, poll.getMinRangeValue(), poll.getMaxRangeValue()));
                    }
                    if (vote != Constants.NO_VOTE_VALUE) {
                        positiveCount++;
                    }
                }

                if (positiveCount < poll.getMinNumberOfOptions() || positiveCount > poll.getMaxNumberOfOptions()) {
                    throw new MetroException.NotValidException(String.format("Invalid num of choices %d, number of choices must be between %d and %d",
                            positiveCount, poll.getMinNumberOfOptions(), poll.getMaxNumberOfOptions()));
                }
            }

            @Override
            boolean isDuplicate(final Transaction transaction, final Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.MessagingVoteCasting attachment = (Attachment.MessagingVoteCasting) transaction.getAttachment();
                String key = Long.toUnsignedString(attachment.getPollId()) + ":" + Long.toUnsignedString(transaction.getSenderId());
                return isDuplicate(Messaging.VOTE_CASTING, key, duplicates, true);
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return false;
            }

        };

        public static final TransactionType PHASING_VOTE_CASTING = new Messaging() {

            private final Fee PHASING_VOTE_FEE = (transaction, appendage) -> {
                Attachment.MessagingPhasingVoteCasting attachment = (Attachment.MessagingPhasingVoteCasting) transaction.getAttachment();
                return attachment.getTransactionFullHashes().size() * Constants.ONE_MTR;
            };

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_PHASING_VOTE_CASTING;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.PHASING_VOTE_CASTING;
            }

            @Override
            public String getName() {
                return "PhasingVoteCasting";
            }

            @Override
            Fee getBaselineFee(Transaction transaction) {
                return PHASING_VOTE_FEE;
            }

            @Override
            Attachment.MessagingPhasingVoteCasting parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.MessagingPhasingVoteCasting(buffer);
            }

            @Override
            Attachment.MessagingPhasingVoteCasting parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.MessagingPhasingVoteCasting(attachmentData);
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {

                Attachment.MessagingPhasingVoteCasting attachment = (Attachment.MessagingPhasingVoteCasting) transaction.getAttachment();
                byte[] revealedSecret = attachment.getRevealedSecret();
                if (revealedSecret.length > Constants.MAX_PHASING_REVEALED_SECRET_LENGTH) {
                    throw new MetroException.NotValidException("Invalid revealed secret length " + revealedSecret.length);
                }
                byte[] hashedSecret = null;
                byte algorithm = 0;

                List<byte[]> hashes = attachment.getTransactionFullHashes();
                if (hashes.size() > Constants.MAX_PHASING_VOTE_TRANSACTIONS) {
                    throw new MetroException.NotValidException("No more than " + Constants.MAX_PHASING_VOTE_TRANSACTIONS + " votes allowed for two-phased multi-voting");
                }

                long voterId = transaction.getSenderId();
                for (byte[] hash : hashes) {
                    long phasedTransactionId = Convert.fullHashToId(hash);
                    if (phasedTransactionId == 0) {
                        throw new MetroException.NotValidException("Invalid phased transactionFullHash " + Convert.toHexString(hash));
                    }

                    PhasingPoll poll = PhasingPoll.getPoll(phasedTransactionId);
                    if (poll == null) {
                        throw new MetroException.NotCurrentlyValidException("Invalid phased transaction " + Long.toUnsignedString(phasedTransactionId)
                                + ", or phasing is finished");
                    }
                    if (! poll.getVoteWeighting().acceptsVotes()) {
                        throw new MetroException.NotValidException("This phased transaction does not require or accept voting");
                    }
                    long[] whitelist = poll.getWhitelist();
                    if (whitelist.length > 0 && Arrays.binarySearch(whitelist, voterId) < 0) {
                        throw new MetroException.NotValidException("Voter is not in the phased transaction whitelist");
                    }
                    if (revealedSecret.length > 0) {
                        if (poll.getVoteWeighting().getVotingModel() != VoteWeighting.VotingModel.HASH) {
                            throw new MetroException.NotValidException("Phased transaction " + Long.toUnsignedString(phasedTransactionId) + " does not accept by-hash voting");
                        }
                        if (hashedSecret != null && !Arrays.equals(poll.getHashedSecret(), hashedSecret)) {
                            throw new MetroException.NotValidException("Phased transaction " + Long.toUnsignedString(phasedTransactionId) + " is using a different hashedSecret");
                        }
                        if (algorithm != 0 && algorithm != poll.getAlgorithm()) {
                            throw new MetroException.NotValidException("Phased transaction " + Long.toUnsignedString(phasedTransactionId) + " is using a different hashedSecretAlgorithm");
                        }
                        if (hashedSecret == null && ! poll.verifySecret(revealedSecret)) {
                            throw new MetroException.NotValidException("Revealed secret does not match phased transaction hashed secret");
                        }
                        hashedSecret = poll.getHashedSecret();
                        algorithm = poll.getAlgorithm();
                    } else if (poll.getVoteWeighting().getVotingModel() == VoteWeighting.VotingModel.HASH) {
                        throw new MetroException.NotValidException("Phased transaction " + Long.toUnsignedString(phasedTransactionId) + " requires revealed secret for approval");
                    }
                    if (!Arrays.equals(poll.getFullHash(), hash)) {
                        throw new MetroException.NotCurrentlyValidException("Phased transaction hash does not match hash in voting transaction");
                    }
                    if (poll.getFinishHeight() <= attachment.getFinishValidationHeight(transaction) + 1) {
                        throw new MetroException.NotCurrentlyValidException(String.format("Phased transaction finishes at height %d which is not after approval transaction height %d",
                                poll.getFinishHeight(), attachment.getFinishValidationHeight(transaction) + 1));
                    }
                }
            }

            @Override
            final void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingPhasingVoteCasting attachment = (Attachment.MessagingPhasingVoteCasting) transaction.getAttachment();
                List<byte[]> hashes = attachment.getTransactionFullHashes();
                for (byte[] hash : hashes) {
                    PhasingVote.addVote(transaction, senderAccount, Convert.fullHashToId(hash));
                }
            }

            @Override
            public boolean isPhasingSafe() {
                return true;
            }

        };

        public static final Messaging ACCOUNT_INFO = new Messaging() {

            private final Fee ACCOUNT_INFO_FEE = new Fee.SizeBasedFee(Constants.ONE_MTR, 2 * Constants.ONE_MTR, 32) {
                @Override
                public int getSize(TransactionImpl transaction, Appendix appendage) {
                    Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo) transaction.getAttachment();
                    return attachment.getName().length() + attachment.getDescription().length();
                }
            };

            @Override
            public byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ACCOUNT_INFO;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ACCOUNT_INFO;
            }

            @Override
            public String getName() {
                return "AccountInfo";
            }

            @Override
            Fee getBaselineFee(Transaction transaction) {
                return ACCOUNT_INFO_FEE;
            }

            @Override
            Attachment.MessagingAccountInfo parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.MessagingAccountInfo(buffer);
            }

            @Override
            Attachment.MessagingAccountInfo parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.MessagingAccountInfo(attachmentData);
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo)transaction.getAttachment();
                if (attachment.getName().length() > Constants.MAX_ACCOUNT_NAME_LENGTH
                        || attachment.getDescription().length() > Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH) {
                    throw new MetroException.NotValidException("Invalid account info issuance: " + attachment.getJSONObject());
                }
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingAccountInfo attachment = (Attachment.MessagingAccountInfo) transaction.getAttachment();
                senderAccount.setAccountInfo(attachment.getName(), attachment.getDescription());
            }

            @Override
            boolean isBlockDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                return isDuplicate(Messaging.ACCOUNT_INFO, getName(), duplicates, true);
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return true;
            }

        };

        public static final Messaging ACCOUNT_PROPERTY = new Messaging() {

            private final Fee ACCOUNT_PROPERTY_FEE = new Fee.SizeBasedFee(Constants.ONE_MTR, Constants.ONE_MTR, 32) {
                @Override
                public int getSize(TransactionImpl transaction, Appendix appendage) {
                    Attachment.MessagingAccountProperty attachment = (Attachment.MessagingAccountProperty) transaction.getAttachment();
                    return attachment.getValue().length();
                }
            };

            @Override
            public byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ACCOUNT_PROPERTY;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ACCOUNT_PROPERTY;
            }

            @Override
            public String getName() {
                return "AccountProperty";
            }

            @Override
            Fee getBaselineFee(Transaction transaction) {
                return ACCOUNT_PROPERTY_FEE;
            }

            @Override
            Attachment.MessagingAccountProperty parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.MessagingAccountProperty(buffer);
            }

            @Override
            Attachment.MessagingAccountProperty parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.MessagingAccountProperty(attachmentData);
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                Attachment.MessagingAccountProperty attachment = (Attachment.MessagingAccountProperty)transaction.getAttachment();
                if (attachment.getProperty().length() > Constants.MAX_ACCOUNT_PROPERTY_NAME_LENGTH
                        || attachment.getProperty().length() == 0
                        || attachment.getValue().length() > Constants.MAX_ACCOUNT_PROPERTY_VALUE_LENGTH) {
                    throw new MetroException.NotValidException("Invalid account property: " + attachment.getJSONObject());
                }
                if (transaction.getAmountMQT() != 0) {
                    throw new MetroException.NotValidException("Account property transaction cannot be used to send " + Constants.COIN_SYMBOL);
                }
                if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                    throw new MetroException.NotValidException("Setting Genesis account properties not allowed");
                }
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingAccountProperty attachment = (Attachment.MessagingAccountProperty) transaction.getAttachment();
                recipientAccount.setProperty(transaction, senderAccount, attachment.getProperty(), attachment.getValue());
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

            @Override
            public boolean isPhasingSafe() {
                return true;
            }

        };

        public static final Messaging ACCOUNT_PROPERTY_DELETE = new Messaging() {

            @Override
            public byte getSubtype() {
                return TransactionType.SUBTYPE_MESSAGING_ACCOUNT_PROPERTY_DELETE;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ACCOUNT_PROPERTY_DELETE;
            }

            @Override
            public String getName() {
                return "AccountPropertyDelete";
            }

            @Override
            Attachment.MessagingAccountPropertyDelete parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.MessagingAccountPropertyDelete(buffer);
            }

            @Override
            Attachment.MessagingAccountPropertyDelete parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.MessagingAccountPropertyDelete(attachmentData);
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                Attachment.MessagingAccountPropertyDelete attachment = (Attachment.MessagingAccountPropertyDelete)transaction.getAttachment();
                Account.AccountProperty accountProperty = Account.getProperty(attachment.getPropertyId());
                if (accountProperty == null) {
                    throw new MetroException.NotCurrentlyValidException("No such property " + Long.toUnsignedString(attachment.getPropertyId()));
                }
                if (accountProperty.getRecipientId() != transaction.getSenderId() && accountProperty.getSetterId() != transaction.getSenderId()) {
                    throw new MetroException.NotValidException("Account " + Long.toUnsignedString(transaction.getSenderId())
                            + " cannot delete property " + Long.toUnsignedString(attachment.getPropertyId()));
                }
                if (accountProperty.getRecipientId() != transaction.getRecipientId()) {
                    throw new MetroException.NotValidException("Account property " + Long.toUnsignedString(attachment.getPropertyId())
                            + " does not belong to " + Long.toUnsignedString(transaction.getRecipientId()));
                }
                if (transaction.getAmountMQT() != 0) {
                    throw new MetroException.NotValidException("Account property transaction cannot be used to send " + Constants.COIN_SYMBOL);
                }
                if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                    throw new MetroException.NotValidException("Deleting Genesis account properties not allowed");
                }
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.MessagingAccountPropertyDelete attachment = (Attachment.MessagingAccountPropertyDelete) transaction.getAttachment();
                senderAccount.deleteProperty(attachment.getPropertyId());
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

            @Override
            public boolean isPhasingSafe() {
                return true;
            }

        };

    }

    public static abstract class ColoredCoins extends TransactionType {

        private ColoredCoins() {}

        @Override
        public final byte getType() {
            return TransactionType.TYPE_COLORED_COINS;
        }

        public static final TransactionType ASSET_ISSUANCE = new ColoredCoins() {

            private final Fee SINGLETON_ASSET_FEE = new Fee.SizeBasedFee(Constants.ONE_MTR, Constants.ONE_MTR, 32) {
                public int getSize(TransactionImpl transaction, Appendix appendage) {
                    Attachment.ColoredCoinsAssetIssuance attachment = (Attachment.ColoredCoinsAssetIssuance) transaction.getAttachment();
                    return attachment.getDescription().length();
                }
            };

            private final Fee ASSET_ISSUANCE_FEE = (transaction, appendage) -> isSingletonIssuance(transaction) ?
                    SINGLETON_ASSET_FEE.getFee(transaction, appendage) : 1000 * Constants.ONE_MTR;

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_COLORED_COINS_ASSET_ISSUANCE;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ASSET_ISSUANCE;
            }

            @Override
            public String getName() {
                return "AssetIssuance";
            }

            @Override
            Fee getBaselineFee(Transaction transaction) {
                return ASSET_ISSUANCE_FEE;
            }

            @Override
            long[] getBackFees(Transaction transaction) {
                if (isSingletonIssuance(transaction)) {
                    return Convert.EMPTY_LONG;
                }
                long feeMQT = transaction.getFeeMQT();
                return new long[] {feeMQT * 3 / 10, feeMQT * 2 / 10, feeMQT / 10};
            }

            @Override
            Attachment.ColoredCoinsAssetIssuance parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.ColoredCoinsAssetIssuance(buffer);
            }

            @Override
            Attachment.ColoredCoinsAssetIssuance parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.ColoredCoinsAssetIssuance(attachmentData);
            }

            @Override
            boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                return true;
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.ColoredCoinsAssetIssuance attachment = (Attachment.ColoredCoinsAssetIssuance) transaction.getAttachment();
                long assetId = transaction.getId();
                Asset.addAsset(transaction, attachment);
                senderAccount.addToAssetAndUnconfirmedAssetBalanceQNT(getLedgerEvent(), assetId, assetId, attachment.getQuantityQNT());
            }

            @Override
            void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                Attachment.ColoredCoinsAssetIssuance attachment = (Attachment.ColoredCoinsAssetIssuance)transaction.getAttachment();
                if (attachment.getName().length() < Constants.MIN_ASSET_NAME_LENGTH
                        || attachment.getName().length() > Constants.MAX_ASSET_NAME_LENGTH
                        || attachment.getDescription().length() > Constants.MAX_ASSET_DESCRIPTION_LENGTH
                        || attachment.getDecimals() < 0 || attachment.getDecimals() > 8
                        || attachment.getQuantityQNT() <= 0
                        || attachment.getQuantityQNT() > Constants.MAX_ASSET_QUANTITY_QNT
                        ) {
                    throw new MetroException.NotValidException("Invalid asset issuance: " + attachment.getJSONObject());
                }
                String normalizedName = attachment.getName().toLowerCase();
                for (int i = 0; i < normalizedName.length(); i++) {
                    if (Constants.ALPHABET.indexOf(normalizedName.charAt(i)) < 0) {
                        throw new MetroException.NotValidException("Invalid asset name: " + normalizedName);
                    }
                }
            }

            @Override
            boolean isBlockDuplicate(final Transaction transaction, final Map<TransactionType, Map<String, Integer>> duplicates) {
                return !isSingletonIssuance(transaction) && isDuplicate(ColoredCoins.ASSET_ISSUANCE, getName(), duplicates, true);
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return true;
            }

            private boolean isSingletonIssuance(Transaction transaction) {
                Attachment.ColoredCoinsAssetIssuance attachment = (Attachment.ColoredCoinsAssetIssuance)transaction.getAttachment();
                return attachment.getQuantityQNT() == 1 && attachment.getDecimals() == 0
                        && attachment.getDescription().length() <= Constants.MAX_SINGLETON_ASSET_DESCRIPTION_LENGTH;
            }

        };

        public static final TransactionType ASSET_TRANSFER = new ColoredCoins() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_COLORED_COINS_ASSET_TRANSFER;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ASSET_TRANSFER;
            }

            @Override
            public String getName() {
                return "AssetTransfer";
            }

            @Override
            Attachment.ColoredCoinsAssetTransfer parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.ColoredCoinsAssetTransfer(buffer);
            }

            @Override
            Attachment.ColoredCoinsAssetTransfer parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.ColoredCoinsAssetTransfer(attachmentData);
            }

            @Override
            boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                Attachment.ColoredCoinsAssetTransfer attachment = (Attachment.ColoredCoinsAssetTransfer) transaction.getAttachment();
                long unconfirmedAssetBalance = senderAccount.getUnconfirmedAssetBalanceQNT(attachment.getAssetId());
                if (unconfirmedAssetBalance >= 0 && unconfirmedAssetBalance >= attachment.getQuantityQNT()) {
                    senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                            attachment.getAssetId(), -attachment.getQuantityQNT());
                    return true;
                }
                return false;
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.ColoredCoinsAssetTransfer attachment = (Attachment.ColoredCoinsAssetTransfer) transaction.getAttachment();
                senderAccount.addToAssetBalanceQNT(getLedgerEvent(), transaction.getId(), attachment.getAssetId(),
                        -attachment.getQuantityQNT());
                if (recipientAccount.getId() == Genesis.CREATOR_ID) {
                    Asset.deleteAsset(transaction, attachment.getAssetId(), attachment.getQuantityQNT());
                } else {
                    recipientAccount.addToAssetAndUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                            attachment.getAssetId(), attachment.getQuantityQNT());
                    AssetTransfer.addAssetTransfer(transaction, attachment);
                }
            }

            @Override
            void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                Attachment.ColoredCoinsAssetTransfer attachment = (Attachment.ColoredCoinsAssetTransfer) transaction.getAttachment();
                senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                        attachment.getAssetId(), attachment.getQuantityQNT());
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                Attachment.ColoredCoinsAssetTransfer attachment = (Attachment.ColoredCoinsAssetTransfer)transaction.getAttachment();
                if (transaction.getAmountMQT() != 0 || attachment.getAssetId() == 0) {
                    throw new MetroException.NotValidException("Invalid asset transfer amount or asset: " + attachment.getJSONObject());
                }
                if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                    throw new MetroException.NotValidException("Asset transfer to Genesis not allowed, "
                            + "use asset delete attachment instead");
                }
                Asset asset = Asset.getAsset(attachment.getAssetId());
                if (attachment.getQuantityQNT() <= 0 || (asset != null && attachment.getQuantityQNT() > asset.getInitialQuantityQNT())) {
                    throw new MetroException.NotValidException("Invalid asset transfer asset or quantity: " + attachment.getJSONObject());
                }
                if (asset == null) {
                    throw new MetroException.NotCurrentlyValidException("Asset " + Long.toUnsignedString(attachment.getAssetId()) +
                            " does not exist yet");
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

            @Override
            public boolean isPhasingSafe() {
                return true;
            }

        };

        public static final TransactionType ASSET_DELETE = new ColoredCoins() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_COLORED_COINS_ASSET_DELETE;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ASSET_DELETE;
            }

            @Override
            public String getName() {
                return "AssetDelete";
            }

            @Override
            Attachment.ColoredCoinsAssetDelete parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.ColoredCoinsAssetDelete(buffer);
            }

            @Override
            Attachment.ColoredCoinsAssetDelete parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.ColoredCoinsAssetDelete(attachmentData);
            }

            @Override
            boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                Attachment.ColoredCoinsAssetDelete attachment = (Attachment.ColoredCoinsAssetDelete)transaction.getAttachment();
                long unconfirmedAssetBalance = senderAccount.getUnconfirmedAssetBalanceQNT(attachment.getAssetId());
                if (unconfirmedAssetBalance >= 0 && unconfirmedAssetBalance >= attachment.getQuantityQNT()) {
                    senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                            attachment.getAssetId(), -attachment.getQuantityQNT());
                    return true;
                }
                return false;
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.ColoredCoinsAssetDelete attachment = (Attachment.ColoredCoinsAssetDelete)transaction.getAttachment();
                senderAccount.addToAssetBalanceQNT(getLedgerEvent(), transaction.getId(), attachment.getAssetId(),
                        -attachment.getQuantityQNT());
                Asset.deleteAsset(transaction, attachment.getAssetId(), attachment.getQuantityQNT());
            }

            @Override
            void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                Attachment.ColoredCoinsAssetDelete attachment = (Attachment.ColoredCoinsAssetDelete)transaction.getAttachment();
                senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                        attachment.getAssetId(), attachment.getQuantityQNT());
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                Attachment.ColoredCoinsAssetDelete attachment = (Attachment.ColoredCoinsAssetDelete)transaction.getAttachment();
                if (attachment.getAssetId() == 0) {
                    throw new MetroException.NotValidException("Invalid asset identifier: " + attachment.getJSONObject());
                }
                Asset asset = Asset.getAsset(attachment.getAssetId());
                if (attachment.getQuantityQNT() <= 0 || (asset != null && attachment.getQuantityQNT() > asset.getInitialQuantityQNT())) {
                    throw new MetroException.NotValidException("Invalid asset delete asset or quantity: " + attachment.getJSONObject());
                }
                if (asset == null) {
                    throw new MetroException.NotCurrentlyValidException("Asset " + Long.toUnsignedString(attachment.getAssetId()) +
                            " does not exist yet");
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return true;
            }

        };

        abstract static class ColoredCoinsOrderPlacement extends ColoredCoins {

            @Override
            final void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                Attachment.ColoredCoinsOrderPlacement attachment = (Attachment.ColoredCoinsOrderPlacement)transaction.getAttachment();
                if (attachment.getPriceMQT() <= 0 || attachment.getPriceMQT() > Constants.MAX_BALANCE_MQT
                        || attachment.getAssetId() == 0) {
                    throw new MetroException.NotValidException("Invalid asset order placement: " + attachment.getJSONObject());
                }
                Asset asset = Asset.getAsset(attachment.getAssetId());
                if (attachment.getQuantityQNT() <= 0 || (asset != null && attachment.getQuantityQNT() > asset.getInitialQuantityQNT())) {
                    throw new MetroException.NotValidException("Invalid asset order placement asset or quantity: " + attachment.getJSONObject());
                }
                if (asset == null) {
                    throw new MetroException.NotCurrentlyValidException("Asset " + Long.toUnsignedString(attachment.getAssetId()) +
                            " does not exist yet");
                }
            }

            @Override
            public final boolean canHaveRecipient() {
                return false;
            }

            @Override
            public final boolean isPhasingSafe() {
                return true;
            }

        }

        public static final TransactionType ASK_ORDER_PLACEMENT = new ColoredCoins.ColoredCoinsOrderPlacement() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ASSET_ASK_ORDER_PLACEMENT;
            }

            @Override
            public String getName() {
                return "AskOrderPlacement";
            }

            @Override
            Attachment.ColoredCoinsAskOrderPlacement parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.ColoredCoinsAskOrderPlacement(buffer);
            }

            @Override
            Attachment.ColoredCoinsAskOrderPlacement parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.ColoredCoinsAskOrderPlacement(attachmentData);
            }

            @Override
            boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                Attachment.ColoredCoinsAskOrderPlacement attachment = (Attachment.ColoredCoinsAskOrderPlacement) transaction.getAttachment();
                long unconfirmedAssetBalance = senderAccount.getUnconfirmedAssetBalanceQNT(attachment.getAssetId());
                if (unconfirmedAssetBalance >= 0 && unconfirmedAssetBalance >= attachment.getQuantityQNT()) {
                    senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                            attachment.getAssetId(), -attachment.getQuantityQNT());
                    return true;
                }
                return false;
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.ColoredCoinsAskOrderPlacement attachment = (Attachment.ColoredCoinsAskOrderPlacement) transaction.getAttachment();
                Order.Ask.addOrder(transaction, attachment);
            }

            @Override
            void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                Attachment.ColoredCoinsAskOrderPlacement attachment = (Attachment.ColoredCoinsAskOrderPlacement) transaction.getAttachment();
                senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                        attachment.getAssetId(), attachment.getQuantityQNT());
            }

        };

        public final static TransactionType BID_ORDER_PLACEMENT = new ColoredCoins.ColoredCoinsOrderPlacement() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ASSET_BID_ORDER_PLACEMENT;
            }

            @Override
            public String getName() {
                return "BidOrderPlacement";
            }

            @Override
            Attachment.ColoredCoinsBidOrderPlacement parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.ColoredCoinsBidOrderPlacement(buffer);
            }

            @Override
            Attachment.ColoredCoinsBidOrderPlacement parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.ColoredCoinsBidOrderPlacement(attachmentData);
            }

            @Override
            boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                Attachment.ColoredCoinsBidOrderPlacement attachment = (Attachment.ColoredCoinsBidOrderPlacement) transaction.getAttachment();
                if (senderAccount.getUnconfirmedBalanceMQT() >= Math.multiplyExact(attachment.getQuantityQNT(), attachment.getPriceMQT())) {
                    senderAccount.addToUnconfirmedBalanceMQT(getLedgerEvent(), transaction.getId(),
                            -Math.multiplyExact(attachment.getQuantityQNT(), attachment.getPriceMQT()));
                    return true;
                }
                return false;
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.ColoredCoinsBidOrderPlacement attachment = (Attachment.ColoredCoinsBidOrderPlacement) transaction.getAttachment();
                Order.Bid.addOrder(transaction, attachment);
            }

            @Override
            void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                Attachment.ColoredCoinsBidOrderPlacement attachment = (Attachment.ColoredCoinsBidOrderPlacement) transaction.getAttachment();
                senderAccount.addToUnconfirmedBalanceMQT(getLedgerEvent(), transaction.getId(),
                        Math.multiplyExact(attachment.getQuantityQNT(), attachment.getPriceMQT()));
            }

        };

        abstract static class ColoredCoinsOrderCancellation extends ColoredCoins {

            @Override
            final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                return true;
            }

            @Override
            final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            }

            @Override
            boolean isUnconfirmedDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.ColoredCoinsOrderCancellation attachment = (Attachment.ColoredCoinsOrderCancellation) transaction.getAttachment();
                return TransactionType.isDuplicate(ColoredCoins.ASK_ORDER_CANCELLATION, Long.toUnsignedString(attachment.getOrderId()), duplicates, true);
            }

            @Override
            public final boolean canHaveRecipient() {
                return false;
            }

            @Override
            public final boolean isPhasingSafe() {
                return true;
            }

        }

        public static final TransactionType ASK_ORDER_CANCELLATION = new ColoredCoins.ColoredCoinsOrderCancellation() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ASSET_ASK_ORDER_CANCELLATION;
            }

            @Override
            public String getName() {
                return "AskOrderCancellation";
            }

            @Override
            Attachment.ColoredCoinsAskOrderCancellation parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.ColoredCoinsAskOrderCancellation(buffer);
            }

            @Override
            Attachment.ColoredCoinsAskOrderCancellation parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.ColoredCoinsAskOrderCancellation(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.ColoredCoinsAskOrderCancellation attachment = (Attachment.ColoredCoinsAskOrderCancellation) transaction.getAttachment();
                Order order = Order.Ask.getAskOrder(attachment.getOrderId());
                Order.Ask.removeOrder(attachment.getOrderId());
                if (order != null) {
                    senderAccount.addToUnconfirmedAssetBalanceQNT(getLedgerEvent(), transaction.getId(),
                            order.getAssetId(), order.getQuantityQNT());
                }
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                Attachment.ColoredCoinsAskOrderCancellation attachment = (Attachment.ColoredCoinsAskOrderCancellation) transaction.getAttachment();
                Order ask = Order.Ask.getAskOrder(attachment.getOrderId());
                if (ask == null) {
                    throw new MetroException.NotCurrentlyValidException("Invalid ask order: " + Long.toUnsignedString(attachment.getOrderId()));
                }
                if (ask.getAccountId() != transaction.getSenderId()) {
                    throw new MetroException.NotValidException("Order " + Long.toUnsignedString(attachment.getOrderId()) + " was created by account "
                            + Long.toUnsignedString(ask.getAccountId()));
                }
            }

        };

        public static final TransactionType BID_ORDER_CANCELLATION = new ColoredCoins.ColoredCoinsOrderCancellation() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ASSET_BID_ORDER_CANCELLATION;
            }

            @Override
            public String getName() {
                return "BidOrderCancellation";
            }

            @Override
            Attachment.ColoredCoinsBidOrderCancellation parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.ColoredCoinsBidOrderCancellation(buffer);
            }

            @Override
            Attachment.ColoredCoinsBidOrderCancellation parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.ColoredCoinsBidOrderCancellation(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.ColoredCoinsBidOrderCancellation attachment = (Attachment.ColoredCoinsBidOrderCancellation) transaction.getAttachment();
                Order order = Order.Bid.getBidOrder(attachment.getOrderId());
                Order.Bid.removeOrder(attachment.getOrderId());
                if (order != null) {
                    senderAccount.addToUnconfirmedBalanceMQT(getLedgerEvent(), transaction.getId(),
                            Math.multiplyExact(order.getQuantityQNT(), order.getPriceMQT()));
                }
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                Attachment.ColoredCoinsBidOrderCancellation attachment = (Attachment.ColoredCoinsBidOrderCancellation) transaction.getAttachment();
                Order bid = Order.Bid.getBidOrder(attachment.getOrderId());
                if (bid == null) {
                    throw new MetroException.NotCurrentlyValidException("Invalid bid order: " + Long.toUnsignedString(attachment.getOrderId()));
                }
                if (bid.getAccountId() != transaction.getSenderId()) {
                    throw new MetroException.NotValidException("Order " + Long.toUnsignedString(attachment.getOrderId()) + " was created by account "
                            + Long.toUnsignedString(bid.getAccountId()));
                }
            }

        };

        public static final TransactionType DIVIDEND_PAYMENT = new ColoredCoins() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_COLORED_COINS_DIVIDEND_PAYMENT;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ASSET_DIVIDEND_PAYMENT;
            }

            @Override
            public String getName() {
                return "DividendPayment";
            }

            @Override
            Attachment.ColoredCoinsDividendPayment parseAttachment(ByteBuffer buffer) {
                return new Attachment.ColoredCoinsDividendPayment(buffer);
            }

            @Override
            Attachment.ColoredCoinsDividendPayment parseAttachment(JSONObject attachmentData) {
                return new Attachment.ColoredCoinsDividendPayment(attachmentData);
            }

            @Override
            boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                Attachment.ColoredCoinsDividendPayment attachment = (Attachment.ColoredCoinsDividendPayment)transaction.getAttachment();
                long assetId = attachment.getAssetId();
                Asset asset = Asset.getAsset(assetId, attachment.getHeight());
                if (asset == null) {
                    return true;
                }
                long quantityQNT = asset.getQuantityQNT() - senderAccount.getAssetBalanceQNT(assetId, attachment.getHeight());
                long totalDividendPayment = Math.multiplyExact(attachment.getAmountMQTPerQNT(), quantityQNT);
                if (senderAccount.getUnconfirmedBalanceMQT() >= totalDividendPayment) {
                    senderAccount.addToUnconfirmedBalanceMQT(getLedgerEvent(), transaction.getId(), -totalDividendPayment);
                    return true;
                }
                return false;
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.ColoredCoinsDividendPayment attachment = (Attachment.ColoredCoinsDividendPayment)transaction.getAttachment();
                senderAccount.payDividends(transaction.getId(), attachment);
            }

            @Override
            void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
                Attachment.ColoredCoinsDividendPayment attachment = (Attachment.ColoredCoinsDividendPayment)transaction.getAttachment();
                long assetId = attachment.getAssetId();
                Asset asset = Asset.getAsset(assetId, attachment.getHeight());
                if (asset == null) {
                    return;
                }
                long quantityQNT = asset.getQuantityQNT() - senderAccount.getAssetBalanceQNT(assetId, attachment.getHeight());
                long totalDividendPayment = Math.multiplyExact(attachment.getAmountMQTPerQNT(), quantityQNT);
                senderAccount.addToUnconfirmedBalanceMQT(getLedgerEvent(), transaction.getId(), totalDividendPayment);
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                Attachment.ColoredCoinsDividendPayment attachment = (Attachment.ColoredCoinsDividendPayment)transaction.getAttachment();
                if (attachment.getHeight() > Metro.getBlockchain().getHeight()) {
                    throw new MetroException.NotCurrentlyValidException("Invalid dividend payment height: " + attachment.getHeight()
                            + ", must not exceed current blockchain height " + Metro.getBlockchain().getHeight());
                }
                if (attachment.getHeight() <= attachment.getFinishValidationHeight(transaction) - Constants.MAX_DIVIDEND_PAYMENT_ROLLBACK) {
                    throw new MetroException.NotCurrentlyValidException("Invalid dividend payment height: " + attachment.getHeight()
                            + ", must be less than " + Constants.MAX_DIVIDEND_PAYMENT_ROLLBACK
                            + " blocks before " + attachment.getFinishValidationHeight(transaction));
                }
                Asset asset = Asset.getAsset(attachment.getAssetId(), attachment.getHeight());
                if (asset == null) {
                    throw new MetroException.NotCurrentlyValidException("Asset " + Long.toUnsignedString(attachment.getAssetId())
                            + " for dividend payment doesn't exist yet");
                }
                if (asset.getAccountId() != transaction.getSenderId() || attachment.getAmountMQTPerQNT() <= 0) {
                    throw new MetroException.NotValidException("Invalid dividend payment sender or amount " + attachment.getJSONObject());
                }
                AssetDividend lastDividend = AssetDividend.getLastDividend(attachment.getAssetId());
                if (lastDividend != null && lastDividend.getHeight() > Metro.getBlockchain().getHeight() - 60) {
                    throw new MetroException.NotCurrentlyValidException("Last dividend payment for asset " + Long.toUnsignedString(attachment.getAssetId())
                            + " was less than 60 blocks ago at " + lastDividend.getHeight() + ", current height is " + Metro.getBlockchain().getHeight()
                            + ", limit is one dividend per 60 blocks");
                }
            }

            @Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                Attachment.ColoredCoinsDividendPayment attachment = (Attachment.ColoredCoinsDividendPayment) transaction.getAttachment();
                return isDuplicate(ColoredCoins.DIVIDEND_PAYMENT, Long.toUnsignedString(attachment.getAssetId()), duplicates, true);
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }

            @Override
            public boolean isPhasingSafe() {
                return false;
            }

        };

    }

    public static abstract class AccountControl extends TransactionType {

        private AccountControl() {
        }

        @Override
        public final byte getType() {
            return TransactionType.TYPE_ACCOUNT_CONTROL;
        }

        @Override
        final boolean applyAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
            return true;
        }

        @Override
        final void undoAttachmentUnconfirmed(Transaction transaction, Account senderAccount) {
        }

        public static final TransactionType EFFECTIVE_BALANCE_LEASING = new AccountControl() {

            @Override
            public final byte getSubtype() {
                return TransactionType.SUBTYPE_ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ACCOUNT_CONTROL_EFFECTIVE_BALANCE_LEASING;
            }

            @Override
            public String getName() {
                return "EffectiveBalanceLeasing";
            }

            @Override
            Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
                return new Attachment.AccountControlEffectiveBalanceLeasing(buffer);
            }

            @Override
            Attachment.AccountControlEffectiveBalanceLeasing parseAttachment(JSONObject attachmentData) throws MetroException.NotValidException {
                return new Attachment.AccountControlEffectiveBalanceLeasing(attachmentData);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.AccountControlEffectiveBalanceLeasing attachment = (Attachment.AccountControlEffectiveBalanceLeasing) transaction.getAttachment();
                Account.getAccount(transaction.getSenderId()).leaseEffectiveBalance(transaction.getRecipientId(), attachment.getPeriod());
            }

            @Override
            void validateAttachment(Transaction transaction) throws MetroException.ValidationException {
                Attachment.AccountControlEffectiveBalanceLeasing attachment = (Attachment.AccountControlEffectiveBalanceLeasing)transaction.getAttachment();
                if (transaction.getSenderId() == transaction.getRecipientId()) {
                    throw new MetroException.NotValidException("Account cannot lease balance to itself");
                }
                if (transaction.getAmountMQT() != 0) {
                    throw new MetroException.NotValidException("Transaction amount must be 0 for effective balance leasing");
                }
                if (attachment.getPeriod() < Constants.LEASING_DELAY || attachment.getPeriod() > 65535) {
                    throw new MetroException.NotValidException("Invalid effective balance leasing period: " + attachment.getPeriod());
                }
                byte[] recipientPublicKey = Account.getPublicKey(transaction.getRecipientId());
                if (recipientPublicKey == null) {
                    throw new MetroException.NotCurrentlyValidException("Invalid effective balance leasing: "
                            + " recipient account " + Long.toUnsignedString(transaction.getRecipientId()) + " not found or no public key published");
                }
                if (transaction.getRecipientId() == Genesis.CREATOR_ID) {
                    throw new MetroException.NotValidException("Leasing to Genesis account not allowed");
                }
            }

            @Override
            public boolean canHaveRecipient() {
                return true;
            }

            @Override
            public boolean isPhasingSafe() {
                return true;
            }

        };

        public static final TransactionType SET_PHASING_ONLY = new AccountControl() {

            @Override
            public byte getSubtype() {
                return SUBTYPE_ACCOUNT_CONTROL_PHASING_ONLY;
            }

            @Override
            public LedgerEvent getLedgerEvent() {
                return LedgerEvent.ACCOUNT_CONTROL_PHASING_ONLY;
            }

            @Override
            AbstractAttachment parseAttachment(ByteBuffer buffer) {
                return new Attachment.SetPhasingOnly(buffer);
            }

            @Override
            AbstractAttachment parseAttachment(JSONObject attachmentData) {
                return new Attachment.SetPhasingOnly(attachmentData);
            }

            @Override
            void validateAttachment(Transaction transaction) throws ValidationException {
                Attachment.SetPhasingOnly attachment = (Attachment.SetPhasingOnly)transaction.getAttachment();
                VotingModel votingModel = attachment.getPhasingParams().getVoteWeighting().getVotingModel();
                attachment.getPhasingParams().validate();
                if (votingModel == VotingModel.NONE) {
                    Account senderAccount = Account.getAccount(transaction.getSenderId());
                    if (senderAccount == null || !senderAccount.getControls().contains(ControlType.PHASING_ONLY)) {
                        throw new MetroException.NotCurrentlyValidException("Phasing only account control is not currently enabled");
                    }
                } else if (votingModel == VotingModel.TRANSACTION || votingModel == VotingModel.HASH) {
                    throw new MetroException.NotValidException("Invalid voting model " + votingModel + " for account control");
                }
                long maxFees = attachment.getMaxFees();
                long maxFeesLimit = (attachment.getPhasingParams().getVoteWeighting().isBalanceIndependent() ? 3 : 22) * Constants.ONE_MTR;
                if (maxFees < 0 || (maxFees > 0 && maxFees < maxFeesLimit) || maxFees > Constants.MAX_BALANCE_MQT) {
                    throw new MetroException.NotValidException(String.format("Invalid max fees %f %s", ((double)maxFees)/Constants.ONE_MTR, Constants.COIN_SYMBOL));
                }
                short minDuration = attachment.getMinDuration();
                if (minDuration < 0 || (minDuration > 0 && minDuration < 3) || minDuration >= Constants.MAX_PHASING_DURATION) {
                    throw new MetroException.NotValidException("Invalid min duration " + attachment.getMinDuration());
                }
                short maxDuration = attachment.getMaxDuration();
                if (maxDuration < 0 || (maxDuration > 0 && maxDuration < 3) || maxDuration >= Constants.MAX_PHASING_DURATION) {
                    throw new MetroException.NotValidException("Invalid max duration " + maxDuration);
                }
                if (minDuration > maxDuration) {
                    throw new MetroException.NotValidException(String.format("Min duration %d cannot exceed max duration %d ",
                            minDuration, maxDuration));
                }
            }

            @Override
            boolean isDuplicate(Transaction transaction, Map<TransactionType, Map<String, Integer>> duplicates) {
                return TransactionType.isDuplicate(SET_PHASING_ONLY, Long.toUnsignedString(transaction.getSenderId()), duplicates, true);
            }

            @Override
            void applyAttachment(Transaction transaction, Account senderAccount, Account recipientAccount) {
                Attachment.SetPhasingOnly attachment = (Attachment.SetPhasingOnly)transaction.getAttachment();
                AccountRestrictions.PhasingOnly.set(senderAccount, attachment);
            }

            @Override
            public boolean canHaveRecipient() {
                return false;
            }

            @Override
            public String getName() {
                return "SetPhasingOnly";
            }

            @Override
            public boolean isPhasingSafe() {
                return false;
            }

        };

    }

}
