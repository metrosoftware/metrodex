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

import metro.crypto.Crypto;
import metro.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface Attachment extends Appendix {

    TransactionType getTransactionType();

    abstract class AbstractAttachment extends Appendix.AbstractAppendix implements Attachment {

        private AbstractAttachment(ByteBuffer buffer) {
            super(buffer);
        }

        private AbstractAttachment(JSONObject attachmentData) {
            super(attachmentData);
        }

        private AbstractAttachment(int version) {
            super(version);
        }

        private AbstractAttachment() {}

        @Override
        final String getAppendixName() {
            return getTransactionType().getName();
        }

        @Override
        final void validate(Transaction transaction) throws MetroException.ValidationException {
            getTransactionType().validateAttachment(transaction);
        }

        @Override
        final void apply(Transaction transaction, Account senderAccount, Account recipientAccount) {
            getTransactionType().apply((TransactionImpl) transaction, senderAccount, recipientAccount);
        }

        @Override
        public final Fee getBaselineFee(Transaction transaction) {
            return getTransactionType().getBaselineFee(transaction);
        }

        @Override
        public final Fee getNextFee(Transaction transaction) {
            return getTransactionType().getNextFee(transaction);
        }

        @Override
        public final int getBaselineFeeHeight() {
            return getTransactionType().getBaselineFeeHeight();
        }

        @Override
        public final int getNextFeeHeight() {
            return getTransactionType().getNextFeeHeight();
        }

        @Override
        final boolean isPhasable() {
            return !(this instanceof Prunable) && getTransactionType().isPhasable();
        }

        final int getFinishValidationHeight(Transaction transaction) {
            return isPhased(transaction) ? transaction.getPhasing().getFinishHeight() - 1 : Metro.getBlockchain().getHeight();
        }

    }

    abstract class EmptyAttachment extends AbstractAttachment {

        private EmptyAttachment() {
            super(0);
        }

        @Override
        final int getMySize() {
            return 0;
        }

        @Override
        final void putMyBytes(ByteBuffer buffer) {
        }

        @Override
        final void putMyJSON(JSONObject json) {
        }

        @Override
        final boolean verifyVersion() {
            return getVersion() == 0;
        }

    }

    EmptyAttachment ORDINARY_PAYMENT = new EmptyAttachment() {

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Payment.ORDINARY;
        }

    };


    final class CoinbaseRecipientsAttachment extends AbstractAttachment {

        private final Map<Account.FullId, Long> recipients;

        CoinbaseRecipientsAttachment(ByteBuffer buffer) throws MetroException.NotValidException {
            super(buffer);
            recipients = new HashMap<>();
            byte size = buffer.get();
            for (byte i = 0; i < size; i++) {
                long accountId1 = buffer.getLong();
                int accountId2 = buffer.getInt();
                long amount = buffer.getLong();
                recipients.put(new Account.FullId(accountId1, accountId2), amount);
            }
        }

        CoinbaseRecipientsAttachment(JSONObject attachmentData) {
            super(attachmentData);
            recipients = new HashMap<>();
            JSONObject recipientsField = (JSONObject) attachmentData.get("recipients");
            recipientsField.forEach((accountId, amount) -> recipients.put(Account.FullId.fromStrId((String) accountId), (Long) amount));
        }

        public CoinbaseRecipientsAttachment(Map<Account.FullId, Long> recipients, boolean fakeParameter) {
            if (recipients.size() < 256) {
                this.recipients = recipients;
            } else {
                this.recipients = new HashMap<>();
            }
        }

        @Override
        int getMySize() {
            return 1 + recipients.size() * 16;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.put((byte)recipients.size());
            for (Account.FullId recipient: recipients.keySet()) {
                buffer.putLong(recipient.getLeft());
                buffer.putInt(recipient.getRight());
                buffer.putLong(recipients.get(recipient));
            }
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            JSONObject recipients = new JSONObject();
            attachment.put("recipients", recipients);
            for (Account.FullId recipient: this.recipients.keySet()) {
                recipients.put(recipient.toString(), this.recipients.get(recipient));
            }
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Coinbase.ORDINARY;
        }

        public Map<Account.FullId, Long> getRecipients() {
            return recipients;
        }
    }

    // the message payload is in the Appendix
    EmptyAttachment ARBITRARY_MESSAGE = new EmptyAttachment() {

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ARBITRARY_MESSAGE;
        }

    };

    final class MessagingAliasAssignment extends AbstractAttachment {

        private final String aliasName;
        private final String aliasURI;

        MessagingAliasAssignment(ByteBuffer buffer) throws MetroException.NotValidException {
            super(buffer);
            aliasName = Convert.readString(buffer, buffer.get(), Constants.MAX_ALIAS_LENGTH).trim();
            aliasURI = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ALIAS_URI_LENGTH).trim();
        }

        MessagingAliasAssignment(JSONObject attachmentData) {
            super(attachmentData);
            aliasName = Convert.nullToEmpty((String) attachmentData.get("alias")).trim();
            aliasURI = Convert.nullToEmpty((String) attachmentData.get("uri")).trim();
        }

        public MessagingAliasAssignment(String aliasName, String aliasURI) {
            this.aliasName = aliasName.trim();
            this.aliasURI = aliasURI.trim();
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(aliasName).length + 2 + Convert.toBytes(aliasURI).length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] alias = Convert.toBytes(this.aliasName);
            byte[] uri = Convert.toBytes(this.aliasURI);
            buffer.put((byte)alias.length);
            buffer.put(alias);
            buffer.putShort((short) uri.length);
            buffer.put(uri);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("alias", aliasName);
            attachment.put("uri", aliasURI);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ALIAS_ASSIGNMENT;
        }

        public String getAliasName() {
            return aliasName;
        }

        public String getAliasURI() {
            return aliasURI;
        }
    }

    final class MessagingAliasSell extends AbstractAttachment {

        private final String aliasName;
        private final long priceMQT;

        MessagingAliasSell(ByteBuffer buffer) throws MetroException.NotValidException {
            super(buffer);
            this.aliasName = Convert.readString(buffer, buffer.get(), Constants.MAX_ALIAS_LENGTH);
            this.priceMQT = buffer.getLong();
        }

        MessagingAliasSell(JSONObject attachmentData) {
            super(attachmentData);
            this.aliasName = Convert.nullToEmpty((String) attachmentData.get("alias"));
            this.priceMQT = Convert.parseLong(attachmentData.get("priceMQT"));
        }

        public MessagingAliasSell(String aliasName, long priceMQT) {
            this.aliasName = aliasName;
            this.priceMQT = priceMQT;
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ALIAS_SELL;
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(aliasName).length + 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] aliasBytes = Convert.toBytes(aliasName);
            buffer.put((byte)aliasBytes.length);
            buffer.put(aliasBytes);
            buffer.putLong(priceMQT);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("alias", aliasName);
            attachment.put("priceMQT", priceMQT);
        }

        public String getAliasName(){
            return aliasName;
        }

        public long getPriceMQT(){
            return priceMQT;
        }
    }

    final class MessagingAliasBuy extends AbstractAttachment {

        private final String aliasName;

        MessagingAliasBuy(ByteBuffer buffer) throws MetroException.NotValidException {
            super(buffer);
            this.aliasName = Convert.readString(buffer, buffer.get(), Constants.MAX_ALIAS_LENGTH);
        }

        MessagingAliasBuy(JSONObject attachmentData) {
            super(attachmentData);
            this.aliasName = Convert.nullToEmpty((String) attachmentData.get("alias"));
        }

        public MessagingAliasBuy(String aliasName) {
            this.aliasName = aliasName;
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ALIAS_BUY;
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(aliasName).length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] aliasBytes = Convert.toBytes(aliasName);
            buffer.put((byte) aliasBytes.length);
            buffer.put(aliasBytes);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("alias", aliasName);
        }

        public String getAliasName(){
            return aliasName;
        }
    }

    final class MessagingAliasDelete extends AbstractAttachment {

        private final String aliasName;

        MessagingAliasDelete(final ByteBuffer buffer) throws MetroException.NotValidException {
            super(buffer);
            this.aliasName = Convert.readString(buffer, buffer.get(), Constants.MAX_ALIAS_LENGTH);
        }

        MessagingAliasDelete(final JSONObject attachmentData) {
            super(attachmentData);
            this.aliasName = Convert.nullToEmpty((String) attachmentData.get("alias"));
        }

        public MessagingAliasDelete(final String aliasName) {
            this.aliasName = aliasName;
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ALIAS_DELETE;
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(aliasName).length;
        }

        @Override
        void putMyBytes(final ByteBuffer buffer) {
            byte[] aliasBytes = Convert.toBytes(aliasName);
            buffer.put((byte)aliasBytes.length);
            buffer.put(aliasBytes);
        }

        @Override
        void putMyJSON(final JSONObject attachment) {
            attachment.put("alias", aliasName);
        }

        public String getAliasName(){
            return aliasName;
        }
    }

    final class MessagingPollCreation extends AbstractAttachment {

        public final static class PollBuilder {
            private final String pollName;
            private final String pollDescription;
            private final String[] pollOptions;

            private final int finishHeight;
            private final byte votingModel;

            private long minBalance = 0;
            private byte minBalanceModel;

            private final byte minNumberOfOptions;
            private final byte maxNumberOfOptions;

            private final byte minRangeValue;
            private final byte maxRangeValue;

            private long holdingId;

            public PollBuilder(final String pollName, final String pollDescription, final String[] pollOptions,
                               final int finishHeight, final byte votingModel,
                               byte minNumberOfOptions, byte maxNumberOfOptions,
                               byte minRangeValue, byte maxRangeValue) {
                this.pollName = pollName;
                this.pollDescription = pollDescription;
                this.pollOptions = pollOptions;

                this.finishHeight = finishHeight;
                this.votingModel = votingModel;
                this.minNumberOfOptions = minNumberOfOptions;
                this.maxNumberOfOptions = maxNumberOfOptions;
                this.minRangeValue = minRangeValue;
                this.maxRangeValue = maxRangeValue;

                this.minBalanceModel = VoteWeighting.VotingModel.get(votingModel).getMinBalanceModel().getCode();
            }

            public PollBuilder minBalance(byte minBalanceModel, long minBalance) {
                this.minBalanceModel = minBalanceModel;
                this.minBalance = minBalance;
                return this;
            }

            public PollBuilder holdingId(long holdingId) {
                this.holdingId = holdingId;
                return this;
            }

            public MessagingPollCreation build() {
                return new MessagingPollCreation(this);
            }
        }

        private final String pollName;
        private final String pollDescription;
        private final String[] pollOptions;

        private final int finishHeight;

        private final byte minNumberOfOptions;
        private final byte maxNumberOfOptions;
        private final byte minRangeValue;
        private final byte maxRangeValue;
        private final VoteWeighting voteWeighting;

        MessagingPollCreation(ByteBuffer buffer) throws MetroException.NotValidException {
            super(buffer);
            this.pollName = Convert.readString(buffer, buffer.getShort(), Constants.MAX_POLL_NAME_LENGTH);
            this.pollDescription = Convert.readString(buffer, buffer.getShort(), Constants.MAX_POLL_DESCRIPTION_LENGTH);

            this.finishHeight = buffer.getInt();

            int numberOfOptions = buffer.get();
            if (numberOfOptions > Constants.MAX_POLL_OPTION_COUNT) {
                throw new MetroException.NotValidException("Invalid number of poll options: " + numberOfOptions);
            }

            this.pollOptions = new String[numberOfOptions];
            for (int i = 0; i < numberOfOptions; i++) {
                this.pollOptions[i] = Convert.readString(buffer, buffer.getShort(), Constants.MAX_POLL_OPTION_LENGTH);
            }

            byte votingModel = buffer.get();

            this.minNumberOfOptions = buffer.get();
            this.maxNumberOfOptions = buffer.get();

            this.minRangeValue = buffer.get();
            this.maxRangeValue = buffer.get();

            long minBalance = buffer.getLong();
            byte minBalanceModel = buffer.get();
            long holdingId = buffer.getLong();
            this.voteWeighting = new VoteWeighting(votingModel, holdingId, minBalance, minBalanceModel);
        }

        MessagingPollCreation(JSONObject attachmentData) {
            super(attachmentData);

            this.pollName = ((String) attachmentData.get("name")).trim();
            this.pollDescription = ((String) attachmentData.get("description")).trim();
            this.finishHeight = ((Long) attachmentData.get("finishHeight")).intValue();

            JSONArray options = (JSONArray) attachmentData.get("options");
            this.pollOptions = new String[options.size()];
            for (int i = 0; i < pollOptions.length; i++) {
                this.pollOptions[i] = ((String) options.get(i)).trim();
            }
            byte votingModel = ((Long) attachmentData.get("votingModel")).byteValue();

            this.minNumberOfOptions = ((Long) attachmentData.get("minNumberOfOptions")).byteValue();
            this.maxNumberOfOptions = ((Long) attachmentData.get("maxNumberOfOptions")).byteValue();
            this.minRangeValue = ((Long) attachmentData.get("minRangeValue")).byteValue();
            this.maxRangeValue = ((Long) attachmentData.get("maxRangeValue")).byteValue();

            long minBalance = Convert.parseLong(attachmentData.get("minBalance"));
            byte minBalanceModel = ((Long) attachmentData.get("minBalanceModel")).byteValue();
            long holdingId = Convert.parseUnsignedLong((String) attachmentData.get("holding"));
            this.voteWeighting = new VoteWeighting(votingModel, holdingId, minBalance, minBalanceModel);
        }

        private MessagingPollCreation(PollBuilder builder) {
            this.pollName = builder.pollName;
            this.pollDescription = builder.pollDescription;
            this.pollOptions = builder.pollOptions;
            this.finishHeight = builder.finishHeight;
            this.minNumberOfOptions = builder.minNumberOfOptions;
            this.maxNumberOfOptions = builder.maxNumberOfOptions;
            this.minRangeValue = builder.minRangeValue;
            this.maxRangeValue = builder.maxRangeValue;
            this.voteWeighting = new VoteWeighting(builder.votingModel, builder.holdingId, builder.minBalance, builder.minBalanceModel);
        }

        @Override
        int getMySize() {
            int size = 2 + Convert.toBytes(pollName).length + 2 + Convert.toBytes(pollDescription).length + 1;
            for (String pollOption : pollOptions) {
                size += 2 + Convert.toBytes(pollOption).length;
            }

            size += 4 + 1 + 1 + 1 + 1 + 1 + 8 + 1 + 8;

            return size;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] name = Convert.toBytes(this.pollName);
            byte[] description = Convert.toBytes(this.pollDescription);
            byte[][] options = new byte[this.pollOptions.length][];
            for (int i = 0; i < this.pollOptions.length; i++) {
                options[i] = Convert.toBytes(this.pollOptions[i]);
            }

            buffer.putShort((short) name.length);
            buffer.put(name);
            buffer.putShort((short) description.length);
            buffer.put(description);
            buffer.putInt(finishHeight);
            buffer.put((byte) options.length);
            for (byte[] option : options) {
                buffer.putShort((short) option.length);
                buffer.put(option);
            }
            buffer.put(this.voteWeighting.getVotingModel().getCode());

            buffer.put(this.minNumberOfOptions);
            buffer.put(this.maxNumberOfOptions);
            buffer.put(this.minRangeValue);
            buffer.put(this.maxRangeValue);

            buffer.putLong(this.voteWeighting.getMinBalance());
            buffer.put(this.voteWeighting.getMinBalanceModel().getCode());
            buffer.putLong(this.voteWeighting.getHoldingId());
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("name", this.pollName);
            attachment.put("description", this.pollDescription);
            attachment.put("finishHeight", this.finishHeight);
            JSONArray options = new JSONArray();
            if (this.pollOptions != null) {
                Collections.addAll(options, this.pollOptions);
            }
            attachment.put("options", options);


            attachment.put("minNumberOfOptions", this.minNumberOfOptions);
            attachment.put("maxNumberOfOptions", this.maxNumberOfOptions);

            attachment.put("minRangeValue", this.minRangeValue);
            attachment.put("maxRangeValue", this.maxRangeValue);

            attachment.put("votingModel", this.voteWeighting.getVotingModel().getCode());

            attachment.put("minBalance", this.voteWeighting.getMinBalance());
            attachment.put("minBalanceModel", this.voteWeighting.getMinBalanceModel().getCode());
            attachment.put("holding", Long.toUnsignedString(this.voteWeighting.getHoldingId()));
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.POLL_CREATION;
        }

        public String getPollName() {
            return pollName;
        }

        public String getPollDescription() {
            return pollDescription;
        }

        public int getFinishHeight() {
            return finishHeight;
        }

        public String[] getPollOptions() {
            return pollOptions;
        }

        public byte getMinNumberOfOptions() {
            return minNumberOfOptions;
        }

        public byte getMaxNumberOfOptions() {
            return maxNumberOfOptions;
        }

        public byte getMinRangeValue() {
            return minRangeValue;
        }

        public byte getMaxRangeValue() {
            return maxRangeValue;
        }

        public VoteWeighting getVoteWeighting() {
            return voteWeighting;
        }

    }

    final class MessagingVoteCasting extends AbstractAttachment {

        private final long pollId;
        private final byte[] pollVote;

        public MessagingVoteCasting(ByteBuffer buffer) throws MetroException.NotValidException {
            super(buffer);
            pollId = buffer.getLong();
            int numberOfOptions = buffer.get();
            if (numberOfOptions > Constants.MAX_POLL_OPTION_COUNT) {
                throw new MetroException.NotValidException("More than " + Constants.MAX_POLL_OPTION_COUNT + " options in a vote");
            }
            pollVote = new byte[numberOfOptions];
            buffer.get(pollVote);
        }

        public MessagingVoteCasting(JSONObject attachmentData) {
            super(attachmentData);
            pollId = Convert.parseUnsignedLong((String) attachmentData.get("poll"));
            JSONArray vote = (JSONArray) attachmentData.get("vote");
            pollVote = new byte[vote.size()];
            for (int i = 0; i < pollVote.length; i++) {
                pollVote[i] = ((Long) vote.get(i)).byteValue();
            }
        }

        public MessagingVoteCasting(long pollId, byte[] pollVote) {
            this.pollId = pollId;
            this.pollVote = pollVote;
        }

        @Override
        int getMySize() {
            return 8 + 1 + this.pollVote.length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(this.pollId);
            buffer.put((byte) this.pollVote.length);
            buffer.put(this.pollVote);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("poll", Long.toUnsignedString(this.pollId));
            JSONArray vote = new JSONArray();
            if (this.pollVote != null) {
                for (byte aPollVote : this.pollVote) {
                    vote.add(aPollVote);
                }
            }
            attachment.put("vote", vote);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.VOTE_CASTING;
        }

        public long getPollId() {
            return pollId;
        }

        public byte[] getPollVote() {
            return pollVote;
        }
    }

    final class MessagingPhasingVoteCasting extends AbstractAttachment {

        private final List<byte[]> transactionFullHashes;
        private final byte[] revealedSecret;

        MessagingPhasingVoteCasting(ByteBuffer buffer) throws MetroException.NotValidException {
            super(buffer);
            byte length = buffer.get();
            transactionFullHashes = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                byte[] hash = new byte[32];
                buffer.get(hash);
                transactionFullHashes.add(hash);
            }
            int secretLength = buffer.getInt();
            if (secretLength > Constants.MAX_PHASING_REVEALED_SECRET_LENGTH) {
                throw new MetroException.NotValidException("Invalid revealed secret length " + secretLength);
            }
            if (secretLength > 0) {
                revealedSecret = new byte[secretLength];
                buffer.get(revealedSecret);
            } else {
                revealedSecret = Convert.EMPTY_BYTE;
            }
        }

        MessagingPhasingVoteCasting(JSONObject attachmentData) {
            super(attachmentData);
            JSONArray hashes = (JSONArray) attachmentData.get("transactionFullHashes");
            transactionFullHashes = new ArrayList<>(hashes.size());
            hashes.forEach(hash -> transactionFullHashes.add(Convert.parseHexString((String) hash)));
            String revealedSecret = Convert.emptyToNull((String) attachmentData.get("revealedSecret"));
            this.revealedSecret = revealedSecret != null ? Convert.parseHexString(revealedSecret) : Convert.EMPTY_BYTE;
        }

        public MessagingPhasingVoteCasting(List<byte[]> transactionFullHashes, byte[] revealedSecret) {
            this.transactionFullHashes = transactionFullHashes;
            this.revealedSecret = revealedSecret;
        }

        @Override
        int getMySize() {
            return 1 + 32 * transactionFullHashes.size() + 4 + revealedSecret.length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.put((byte) transactionFullHashes.size());
            transactionFullHashes.forEach(buffer::put);
            buffer.putInt(revealedSecret.length);
            buffer.put(revealedSecret);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            JSONArray jsonArray = new JSONArray();
            transactionFullHashes.forEach(hash -> jsonArray.add(Convert.toHexString(hash)));
            attachment.put("transactionFullHashes", jsonArray);
            if (revealedSecret.length > 0) {
                attachment.put("revealedSecret", Convert.toHexString(revealedSecret));
            }
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.PHASING_VOTE_CASTING;
        }

        public List<byte[]> getTransactionFullHashes() {
            return transactionFullHashes;
        }

        public byte[] getRevealedSecret() {
            return revealedSecret;
        }
    }

    final class MessagingAccountInfo extends AbstractAttachment {

        private final String name;
        private final String description;

        MessagingAccountInfo(ByteBuffer buffer) throws MetroException.NotValidException {
            super(buffer);
            this.name = Convert.readString(buffer, buffer.get(), Constants.MAX_ACCOUNT_NAME_LENGTH);
            this.description = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ACCOUNT_DESCRIPTION_LENGTH);
        }

        MessagingAccountInfo(JSONObject attachmentData) {
            super(attachmentData);
            this.name = Convert.nullToEmpty((String) attachmentData.get("name"));
            this.description = Convert.nullToEmpty((String) attachmentData.get("description"));
        }

        public MessagingAccountInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(name).length + 2 + Convert.toBytes(description).length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] name = Convert.toBytes(this.name);
            byte[] description = Convert.toBytes(this.description);
            buffer.put((byte)name.length);
            buffer.put(name);
            buffer.putShort((short) description.length);
            buffer.put(description);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("name", name);
            attachment.put("description", description);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ACCOUNT_INFO;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

    }

    final class MessagingAccountProperty extends AbstractAttachment {

        private final String property;
        private final String value;

        MessagingAccountProperty(ByteBuffer buffer) throws MetroException.NotValidException {
            super(buffer);
            this.property = Convert.readString(buffer, buffer.get(), Constants.MAX_ACCOUNT_PROPERTY_NAME_LENGTH).trim();
            this.value = Convert.readString(buffer, buffer.get(), Constants.MAX_ACCOUNT_PROPERTY_VALUE_LENGTH).trim();
        }

        MessagingAccountProperty(JSONObject attachmentData) {
            super(attachmentData);
            this.property = Convert.nullToEmpty((String) attachmentData.get("property")).trim();
            this.value = Convert.nullToEmpty((String) attachmentData.get("value")).trim();
        }

        public MessagingAccountProperty(String property, String value) {
            this.property = property.trim();
            this.value = Convert.nullToEmpty(value).trim();
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(property).length + 1 + Convert.toBytes(value).length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] property = Convert.toBytes(this.property);
            byte[] value = Convert.toBytes(this.value);
            buffer.put((byte)property.length);
            buffer.put(property);
            buffer.put((byte)value.length);
            buffer.put(value);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("property", property);
            attachment.put("value", value);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ACCOUNT_PROPERTY;
        }

        public String getProperty() {
            return property;
        }

        public String getValue() {
            return value;
        }

    }

    final class MessagingAccountPropertyDelete extends AbstractAttachment {

        private final long propertyId;

        MessagingAccountPropertyDelete(ByteBuffer buffer) {
            super(buffer);
            this.propertyId = buffer.getLong();
        }

        MessagingAccountPropertyDelete(JSONObject attachmentData) {
            super(attachmentData);
            this.propertyId = Convert.parseUnsignedLong((String)attachmentData.get("property"));
        }

        public MessagingAccountPropertyDelete(long propertyId) {
            this.propertyId = propertyId;
        }

        @Override
        int getMySize() {
            return 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(propertyId);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("property", Long.toUnsignedString(propertyId));
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.Messaging.ACCOUNT_PROPERTY_DELETE;
        }

        public long getPropertyId() {
            return propertyId;
        }

    }

    final class ColoredCoinsAssetIssuance extends AbstractAttachment {

        private final String name;
        private final String description;
        private final long quantityQNT;
        private final byte decimals;

        ColoredCoinsAssetIssuance(ByteBuffer buffer) throws MetroException.NotValidException {
            super(buffer);
            this.name = Convert.readString(buffer, buffer.get(), Constants.MAX_ASSET_NAME_LENGTH);
            this.description = Convert.readString(buffer, buffer.getShort(), Constants.MAX_ASSET_DESCRIPTION_LENGTH);
            this.quantityQNT = buffer.getLong();
            this.decimals = buffer.get();
        }

        ColoredCoinsAssetIssuance(JSONObject attachmentData) {
            super(attachmentData);
            this.name = (String) attachmentData.get("name");
            this.description = Convert.nullToEmpty((String) attachmentData.get("description"));
            this.quantityQNT = Convert.parseLong(attachmentData.get("quantityQNT"));
            this.decimals = ((Long) attachmentData.get("decimals")).byteValue();
        }

        public ColoredCoinsAssetIssuance(String name, String description, long quantityQNT, byte decimals) {
            this.name = name;
            this.description = Convert.nullToEmpty(description);
            this.quantityQNT = quantityQNT;
            this.decimals = decimals;
        }

        @Override
        int getMySize() {
            return 1 + Convert.toBytes(name).length + 2 + Convert.toBytes(description).length + 8 + 1;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            byte[] name = Convert.toBytes(this.name);
            byte[] description = Convert.toBytes(this.description);
            buffer.put((byte)name.length);
            buffer.put(name);
            buffer.putShort((short) description.length);
            buffer.put(description);
            buffer.putLong(quantityQNT);
            buffer.put(decimals);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("name", name);
            attachment.put("description", description);
            attachment.put("quantityQNT", quantityQNT);
            attachment.put("decimals", decimals);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.ColoredCoins.ASSET_ISSUANCE;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public long getQuantityQNT() {
            return quantityQNT;
        }

        public byte getDecimals() {
            return decimals;
        }
    }

    final class ColoredCoinsAssetTransfer extends AbstractAttachment {

        private final long assetId;
        private final long quantityQNT;

        ColoredCoinsAssetTransfer(ByteBuffer buffer) throws MetroException.NotValidException {
            super(buffer);
            this.assetId = buffer.getLong();
            this.quantityQNT = buffer.getLong();
        }

        ColoredCoinsAssetTransfer(JSONObject attachmentData) {
            super(attachmentData);
            this.assetId = Convert.parseUnsignedLong((String) attachmentData.get("asset"));
            this.quantityQNT = Convert.parseLong(attachmentData.get("quantityQNT"));
        }

        public ColoredCoinsAssetTransfer(long assetId, long quantityQNT) {
            this.assetId = assetId;
            this.quantityQNT = quantityQNT;
        }

        @Override
        int getMySize() {
            return 8 + 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(assetId);
            buffer.putLong(quantityQNT);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("asset", Long.toUnsignedString(assetId));
            attachment.put("quantityQNT", quantityQNT);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.ColoredCoins.ASSET_TRANSFER;
        }

        public long getAssetId() {
            return assetId;
        }

        public long getQuantityQNT() {
            return quantityQNT;
        }

    }

    final class ColoredCoinsAssetDelete extends AbstractAttachment {

        private final long assetId;
        private final long quantityQNT;

        ColoredCoinsAssetDelete(ByteBuffer buffer) {
            super(buffer);
            this.assetId = buffer.getLong();
            this.quantityQNT = buffer.getLong();
        }

        ColoredCoinsAssetDelete(JSONObject attachmentData) {
            super(attachmentData);
            this.assetId = Convert.parseUnsignedLong((String)attachmentData.get("asset"));
            this.quantityQNT = Convert.parseLong(attachmentData.get("quantityQNT"));
        }

        public ColoredCoinsAssetDelete(long assetId, long quantityQNT) {
            this.assetId = assetId;
            this.quantityQNT = quantityQNT;
        }

        @Override
        int getMySize() {
            return 8 + 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(assetId);
            buffer.putLong(quantityQNT);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("asset", Long.toUnsignedString(assetId));
            attachment.put("quantityQNT", quantityQNT);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.ColoredCoins.ASSET_DELETE;
        }

        public long getAssetId() {
            return assetId;
        }

        public long getQuantityQNT() {
            return quantityQNT;
        }

    }

    abstract class ColoredCoinsOrderPlacement extends AbstractAttachment {

        private final long assetId;
        private final long quantityQNT;
        private final long priceMQT;

        private ColoredCoinsOrderPlacement(ByteBuffer buffer) {
            super(buffer);
            this.assetId = buffer.getLong();
            this.quantityQNT = buffer.getLong();
            this.priceMQT = buffer.getLong();
        }

        private ColoredCoinsOrderPlacement(JSONObject attachmentData) {
            super(attachmentData);
            this.assetId = Convert.parseUnsignedLong((String) attachmentData.get("asset"));
            this.quantityQNT = Convert.parseLong(attachmentData.get("quantityQNT"));
            this.priceMQT = Convert.parseLong(attachmentData.get("priceMQT"));
        }

        private ColoredCoinsOrderPlacement(long assetId, long quantityQNT, long priceMQT) {
            this.assetId = assetId;
            this.quantityQNT = quantityQNT;
            this.priceMQT = priceMQT;
        }

        @Override
        int getMySize() {
            return 8 + 8 + 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(assetId);
            buffer.putLong(quantityQNT);
            buffer.putLong(priceMQT);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("asset", Long.toUnsignedString(assetId));
            attachment.put("quantityQNT", quantityQNT);
            attachment.put("priceMQT", priceMQT);
        }

        public long getAssetId() {
            return assetId;
        }

        public long getQuantityQNT() {
            return quantityQNT;
        }

        public long getPriceMQT() {
            return priceMQT;
        }
    }

    final class ColoredCoinsAskOrderPlacement extends ColoredCoinsOrderPlacement {

        ColoredCoinsAskOrderPlacement(ByteBuffer buffer) {
            super(buffer);
        }

        ColoredCoinsAskOrderPlacement(JSONObject attachmentData) {
            super(attachmentData);
        }

        public ColoredCoinsAskOrderPlacement(long assetId, long quantityQNT, long priceMQT) {
            super(assetId, quantityQNT, priceMQT);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.ColoredCoins.ASK_ORDER_PLACEMENT;
        }

    }

    final class ColoredCoinsBidOrderPlacement extends ColoredCoinsOrderPlacement {

        ColoredCoinsBidOrderPlacement(ByteBuffer buffer) {
            super(buffer);
        }

        ColoredCoinsBidOrderPlacement(JSONObject attachmentData) {
            super(attachmentData);
        }

        public ColoredCoinsBidOrderPlacement(long assetId, long quantityQNT, long priceMQT) {
            super(assetId, quantityQNT, priceMQT);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.ColoredCoins.BID_ORDER_PLACEMENT;
        }

    }

    abstract class ColoredCoinsOrderCancellation extends AbstractAttachment {

        private final long orderId;

        private ColoredCoinsOrderCancellation(ByteBuffer buffer) {
            super(buffer);
            this.orderId = buffer.getLong();
        }

        private ColoredCoinsOrderCancellation(JSONObject attachmentData) {
            super(attachmentData);
            this.orderId = Convert.parseUnsignedLong((String) attachmentData.get("order"));
        }

        private ColoredCoinsOrderCancellation(long orderId) {
            this.orderId = orderId;
        }

        @Override
        int getMySize() {
            return 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(orderId);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("order", Long.toUnsignedString(orderId));
        }

        public long getOrderId() {
            return orderId;
        }
    }

    final class ColoredCoinsAskOrderCancellation extends ColoredCoinsOrderCancellation {

        ColoredCoinsAskOrderCancellation(ByteBuffer buffer) {
            super(buffer);
        }

        ColoredCoinsAskOrderCancellation(JSONObject attachmentData) {
            super(attachmentData);
        }

        public ColoredCoinsAskOrderCancellation(long orderId) {
            super(orderId);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.ColoredCoins.ASK_ORDER_CANCELLATION;
        }

    }

    final class ColoredCoinsBidOrderCancellation extends ColoredCoinsOrderCancellation {

        ColoredCoinsBidOrderCancellation(ByteBuffer buffer) {
            super(buffer);
        }

        ColoredCoinsBidOrderCancellation(JSONObject attachmentData) {
            super(attachmentData);
        }

        public ColoredCoinsBidOrderCancellation(long orderId) {
            super(orderId);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.ColoredCoins.BID_ORDER_CANCELLATION;
        }

    }

    final class ColoredCoinsDividendPayment extends AbstractAttachment {

        private final long assetId;
        private final int height;
        private final long amountMQTPerQNT;

        ColoredCoinsDividendPayment(ByteBuffer buffer) {
            super(buffer);
            this.assetId = buffer.getLong();
            this.height = buffer.getInt();
            this.amountMQTPerQNT = buffer.getLong();
        }

        ColoredCoinsDividendPayment(JSONObject attachmentData) {
            super(attachmentData);
            this.assetId = Convert.parseUnsignedLong((String)attachmentData.get("asset"));
            this.height = ((Long)attachmentData.get("height")).intValue();
            this.amountMQTPerQNT = Convert.parseLong(attachmentData.get("amountMQTPerQNT"));
        }

        public ColoredCoinsDividendPayment(long assetId, int height, long amountMQTPerQNT) {
            this.assetId = assetId;
            this.height = height;
            this.amountMQTPerQNT = amountMQTPerQNT;
        }

        @Override
        int getMySize() {
            return 8 + 4 + 8;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(assetId);
            buffer.putInt(height);
            buffer.putLong(amountMQTPerQNT);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("asset", Long.toUnsignedString(assetId));
            attachment.put("height", height);
            attachment.put("amountMQTPerQNT", amountMQTPerQNT);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.ColoredCoins.DIVIDEND_PAYMENT;
        }

        public long getAssetId() {
            return assetId;
        }

        public int getHeight() {
            return height;
        }

        public long getAmountMQTPerQNT() {
            return amountMQTPerQNT;
        }

    }

    final class AccountControlEffectiveBalanceLeasing extends AbstractAttachment {

        private final int period;

        AccountControlEffectiveBalanceLeasing(ByteBuffer buffer) {
            super(buffer);
            this.period = Short.toUnsignedInt(buffer.getShort());
        }

        AccountControlEffectiveBalanceLeasing(JSONObject attachmentData) {
            super(attachmentData);
            this.period = ((Long) attachmentData.get("period")).intValue();
        }

        public AccountControlEffectiveBalanceLeasing(int period) {
            this.period = period;
        }

        @Override
        int getMySize() {
            return 2;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putShort((short)period);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("period", period);
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.AccountControl.EFFECTIVE_BALANCE_LEASING;
        }

        public int getPeriod() {
            return period;
        }
    }


    final class ShufflingCreation extends AbstractAttachment {

        private final long holdingId;
        private final HoldingType holdingType;
        private final long amount;
        private final byte participantCount;
        private final short registrationPeriod;

        ShufflingCreation(ByteBuffer buffer) {
            super(buffer);
            this.holdingId = buffer.getLong();
            this.holdingType = HoldingType.get(buffer.get());
            this.amount = buffer.getLong();
            this.participantCount = buffer.get();
            this.registrationPeriod = buffer.getShort();
        }

        ShufflingCreation(JSONObject attachmentData) {
            super(attachmentData);
            this.holdingId = Convert.parseUnsignedLong((String) attachmentData.get("holding"));
            this.holdingType = HoldingType.get(((Long)attachmentData.get("holdingType")).byteValue());
            this.amount = Convert.parseLong(attachmentData.get("amount"));
            this.participantCount = ((Long)attachmentData.get("participantCount")).byteValue();
            this.registrationPeriod = ((Long)attachmentData.get("registrationPeriod")).shortValue();
        }

        public ShufflingCreation(long holdingId, HoldingType holdingType, long amount, byte participantCount, short registrationPeriod) {
            this.holdingId = holdingId;
            this.holdingType = holdingType;
            this.amount = amount;
            this.participantCount = participantCount;
            this.registrationPeriod = registrationPeriod;
        }

        @Override
        int getMySize() {
            return 8 + 1 + 8 + 1 + 2;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(holdingId);
            buffer.put(holdingType.getCode());
            buffer.putLong(amount);
            buffer.put(participantCount);
            buffer.putShort(registrationPeriod);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("holding", Long.toUnsignedString(holdingId));
            attachment.put("holdingType", holdingType.getCode());
            attachment.put("amount", amount);
            attachment.put("participantCount", participantCount);
            attachment.put("registrationPeriod", registrationPeriod);
        }

        @Override
        public TransactionType getTransactionType() {
            return ShufflingTransaction.SHUFFLING_CREATION;
        }

        public long getHoldingId() {
            return holdingId;
        }

        public HoldingType getHoldingType() {
            return holdingType;
        }

        public long getAmount() {
            return amount;
        }

        public byte getParticipantCount() {
            return participantCount;
        }

        public short getRegistrationPeriod() {
            return registrationPeriod;
        }
    }

    interface ShufflingAttachment extends Attachment {

        long getShufflingId();

        byte[] getShufflingStateHash();

    }

    abstract class AbstractShufflingAttachment extends AbstractAttachment implements ShufflingAttachment {

        private final long shufflingId;
        private final byte[] shufflingStateHash;

        private AbstractShufflingAttachment(ByteBuffer buffer) {
            super(buffer);
            this.shufflingId = buffer.getLong();
            this.shufflingStateHash = new byte[32];
            buffer.get(this.shufflingStateHash);
        }

        private AbstractShufflingAttachment(JSONObject attachmentData) {
            super(attachmentData);
            this.shufflingId = Convert.parseUnsignedLong((String) attachmentData.get("shuffling"));
            this.shufflingStateHash = Convert.parseHexString((String) attachmentData.get("shufflingStateHash"));
        }

        private AbstractShufflingAttachment(long shufflingId, byte[] shufflingStateHash) {
            this.shufflingId = shufflingId;
            this.shufflingStateHash = shufflingStateHash;
        }

        @Override
        int getMySize() {
            return 8 + 32;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putLong(shufflingId);
            buffer.put(shufflingStateHash);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("shuffling", Long.toUnsignedString(shufflingId));
            attachment.put("shufflingStateHash", Convert.toHexString(shufflingStateHash));
        }

        @Override
        public final long getShufflingId() {
            return shufflingId;
        }

        @Override
        public final byte[] getShufflingStateHash() {
            return shufflingStateHash;
        }

    }

    final class ShufflingRegistration extends AbstractAttachment implements ShufflingAttachment {

        private final byte[] shufflingFullHash;

        ShufflingRegistration(ByteBuffer buffer) {
            super(buffer);
            this.shufflingFullHash = new byte[32];
            buffer.get(this.shufflingFullHash);
        }

        ShufflingRegistration(JSONObject attachmentData) {
            super(attachmentData);
            this.shufflingFullHash = Convert.parseHexString((String) attachmentData.get("shufflingFullHash"));
        }

        public ShufflingRegistration(byte[] shufflingFullHash) {
            this.shufflingFullHash = shufflingFullHash;
        }

        @Override
        public TransactionType getTransactionType() {
            return ShufflingTransaction.SHUFFLING_REGISTRATION;
        }

        @Override
        int getMySize() {
            return 32;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.put(shufflingFullHash);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            attachment.put("shufflingFullHash", Convert.toHexString(shufflingFullHash));
        }

        @Override
        public long getShufflingId() {
            return Convert.fullHashToId(shufflingFullHash);
        }

        @Override
        public byte[] getShufflingStateHash() {
            return shufflingFullHash;
        }

    }

    final class ShufflingProcessing extends AbstractShufflingAttachment implements Prunable {

        private static final byte[] emptyDataHash = Crypto.sha256().digest();

        static ShufflingProcessing parse(JSONObject attachmentData) {
            if (!Appendix.hasAppendix(ShufflingTransaction.SHUFFLING_PROCESSING.getName(), attachmentData)) {
                return null;
            }
            return new ShufflingProcessing(attachmentData);
        }

        private volatile byte[][] data;
        private final byte[] hash;

        ShufflingProcessing(ByteBuffer buffer) {
            super(buffer);
            this.hash = new byte[32];
            buffer.get(hash);
            this.data = Arrays.equals(hash, emptyDataHash) ? Convert.EMPTY_BYTES : null;
        }

        ShufflingProcessing(JSONObject attachmentData) {
            super(attachmentData);
            JSONArray jsonArray = (JSONArray)attachmentData.get("data");
            if (jsonArray != null) {
                this.data = new byte[jsonArray.size()][];
                for (int i = 0; i < this.data.length; i++) {
                    this.data[i] = Convert.parseHexString((String) jsonArray.get(i));
                }
                this.hash = null;
            } else {
                this.hash = Convert.parseHexString(Convert.emptyToNull((String)attachmentData.get("hash")));
                this.data = Arrays.equals(hash, emptyDataHash) ? Convert.EMPTY_BYTES : null;
            }
        }

        ShufflingProcessing(long shufflingId, byte[][] data, byte[] shufflingStateHash) {
            super(shufflingId, shufflingStateHash);
            this.data = data;
            this.hash = null;
        }

        @Override
        int getMyFullSize() {
            int size = super.getMySize();
            if (data != null) {
                size += 1;
                for (byte[] bytes : data) {
                    size += 4;
                    size += bytes.length;
                }
            }
            return size / 2; // just lie
        }

        @Override
        int getMySize() {
            return super.getMySize() + 32;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            super.putMyBytes(buffer);
            buffer.put(getHash());
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            super.putMyJSON(attachment);
            if (data != null) {
                JSONArray jsonArray = new JSONArray();
                attachment.put("data", jsonArray);
                for (byte[] bytes : data) {
                    jsonArray.add(Convert.toHexString(bytes));
                }
            }
            attachment.put("hash", Convert.toHexString(getHash()));
        }

        @Override
        public TransactionType getTransactionType() {
            return ShufflingTransaction.SHUFFLING_PROCESSING;
        }

        @Override
        public byte[] getHash() {
            if (hash != null) {
                return hash;
            } else if (data != null) {
                MessageDigest digest = Crypto.sha256();
                for (byte[] bytes : data) {
                    digest.update(bytes);
                }
                return digest.digest();
            } else {
                throw new IllegalStateException("Both hash and data are null");
            }
        }

        public byte[][] getData() {
            return data;
        }

        @Override
        void loadPrunable(Transaction transaction, boolean includeExpiredPrunable) {
            if (data == null && shouldLoadPrunable(transaction, includeExpiredPrunable)) {
                data = ShufflingParticipant.getData(getShufflingId(), transaction.getSenderId());
            }
        }

        @Override
        public boolean hasPrunableData() {
            return data != null;
        }

        @Override
        public void restorePrunableData(Transaction transaction, long blockTimestamp, int height) {
            ShufflingParticipant.restoreData(getShufflingId(), transaction.getSenderId(), getData(), transaction.getTimestamp(), height);
        }

    }

    final class ShufflingRecipients extends AbstractShufflingAttachment {

        private final byte[][] recipientPublicKeys;

        ShufflingRecipients(ByteBuffer buffer) throws MetroException.NotValidException {
            super(buffer);
            int count = buffer.get();
            if (count > Constants.MAX_NUMBER_OF_SHUFFLING_PARTICIPANTS || count < 0) {
                throw new MetroException.NotValidException("Invalid data count " + count);
            }
            this.recipientPublicKeys = new byte[count][];
            for (int i = 0; i < count; i++) {
                this.recipientPublicKeys[i] = new byte[32];
                buffer.get(this.recipientPublicKeys[i]);
            }
        }

        ShufflingRecipients(JSONObject attachmentData) {
            super(attachmentData);
            JSONArray jsonArray = (JSONArray)attachmentData.get("recipientPublicKeys");
            this.recipientPublicKeys = new byte[jsonArray.size()][];
            for (int i = 0; i < this.recipientPublicKeys.length; i++) {
                this.recipientPublicKeys[i] = Convert.parseHexString((String)jsonArray.get(i));
            }
        }

        ShufflingRecipients(long shufflingId, byte[][] recipientPublicKeys, byte[] shufflingStateHash) {
            super(shufflingId, shufflingStateHash);
            this.recipientPublicKeys = recipientPublicKeys;
        }

        @Override
        int getMySize() {
            int size = super.getMySize();
            size += 1;
            size += 32 * recipientPublicKeys.length;
            return size;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            super.putMyBytes(buffer);
            buffer.put((byte)recipientPublicKeys.length);
            for (byte[] bytes : recipientPublicKeys) {
                buffer.put(bytes);
            }
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            super.putMyJSON(attachment);
            JSONArray jsonArray = new JSONArray();
            attachment.put("recipientPublicKeys", jsonArray);
            for (byte[] bytes : recipientPublicKeys) {
                jsonArray.add(Convert.toHexString(bytes));
            }
        }

        @Override
        public TransactionType getTransactionType() {
            return ShufflingTransaction.SHUFFLING_RECIPIENTS;
        }

        public byte[][] getRecipientPublicKeys() {
            return recipientPublicKeys;
        }

    }

    final class ShufflingVerification extends AbstractShufflingAttachment {

        ShufflingVerification(ByteBuffer buffer) {
            super(buffer);
        }

        ShufflingVerification(JSONObject attachmentData) {
            super(attachmentData);
        }

        public ShufflingVerification(long shufflingId, byte[] shufflingStateHash) {
            super(shufflingId, shufflingStateHash);
        }

        @Override
        public TransactionType getTransactionType() {
            return ShufflingTransaction.SHUFFLING_VERIFICATION;
        }

    }

    final class ShufflingCancellation extends AbstractShufflingAttachment {

        private final byte[][] blameData;
        private final byte[][] keySeeds;
        private final long cancellingAccountId;

        ShufflingCancellation(ByteBuffer buffer) throws MetroException.NotValidException {
            super(buffer);
            int count = buffer.get();
            if (count > Constants.MAX_NUMBER_OF_SHUFFLING_PARTICIPANTS || count <= 0) {
                throw new MetroException.NotValidException("Invalid data count " + count);
            }
            this.blameData = new byte[count][];
            for (int i = 0; i < count; i++) {
                int size = buffer.getInt();
                if (size > Consensus.POSBLOCK_MAX_PAYLOAD_LENGTH) {
                    throw new MetroException.NotValidException("Invalid data size " + size);
                }
                this.blameData[i] = new byte[size];
                buffer.get(this.blameData[i]);
            }
            count = buffer.get();
            if (count > Constants.MAX_NUMBER_OF_SHUFFLING_PARTICIPANTS || count <= 0) {
                throw new MetroException.NotValidException("Invalid keySeeds count " + count);
            }
            this.keySeeds = new byte[count][];
            for (int i = 0; i < count; i++) {
                this.keySeeds[i] = new byte[32];
                buffer.get(this.keySeeds[i]);
            }
            this.cancellingAccountId = buffer.getLong();
        }

        ShufflingCancellation(JSONObject attachmentData) {
            super(attachmentData);
            JSONArray jsonArray = (JSONArray)attachmentData.get("blameData");
            this.blameData = new byte[jsonArray.size()][];
            for (int i = 0; i < this.blameData.length; i++) {
                this.blameData[i] = Convert.parseHexString((String)jsonArray.get(i));
            }
            jsonArray = (JSONArray)attachmentData.get("keySeeds");
            this.keySeeds = new byte[jsonArray.size()][];
            for (int i = 0; i < this.keySeeds.length; i++) {
                this.keySeeds[i] = Convert.parseHexString((String)jsonArray.get(i));
            }
            this.cancellingAccountId = Convert.parseUnsignedLong((String) attachmentData.get("cancellingAccount"));
        }

        ShufflingCancellation(long shufflingId, byte[][] blameData, byte[][] keySeeds, byte[] shufflingStateHash, long cancellingAccountId) {
            super(shufflingId, shufflingStateHash);
            this.blameData = blameData;
            this.keySeeds = keySeeds;
            this.cancellingAccountId = cancellingAccountId;
        }

        @Override
        public TransactionType getTransactionType() {
            return ShufflingTransaction.SHUFFLING_CANCELLATION;
        }

        @Override
        int getMySize() {
            int size = super.getMySize();
            size += 1;
            for (byte[] bytes : blameData) {
                size += 4;
                size += bytes.length;
            }
            size += 1;
            size += 32 * keySeeds.length;
            size += 8;
            return size;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            super.putMyBytes(buffer);
            buffer.put((byte) blameData.length);
            for (byte[] bytes : blameData) {
                buffer.putInt(bytes.length);
                buffer.put(bytes);
            }
            buffer.put((byte) keySeeds.length);
            for (byte[] bytes : keySeeds) {
                buffer.put(bytes);
            }
            buffer.putLong(cancellingAccountId);
        }

        @Override
        void putMyJSON(JSONObject attachment) {
            super.putMyJSON(attachment);
            JSONArray jsonArray = new JSONArray();
            attachment.put("blameData", jsonArray);
            for (byte[] bytes : blameData) {
                jsonArray.add(Convert.toHexString(bytes));
            }
            jsonArray = new JSONArray();
            attachment.put("keySeeds", jsonArray);
            for (byte[] bytes : keySeeds) {
                jsonArray.add(Convert.toHexString(bytes));
            }
            if (cancellingAccountId != 0) {
                attachment.put("cancellingAccount", Long.toUnsignedString(cancellingAccountId));
            }
        }

        public byte[][] getBlameData() {
            return blameData;
        }

        public byte[][] getKeySeeds() {
            return keySeeds;
        }

        public long getCancellingAccountId() {
            return cancellingAccountId;
        }

        byte[] getHash() {
            MessageDigest digest = Crypto.sha256();
            for (byte[] bytes : blameData) {
                digest.update(bytes);
            }
            return digest.digest();
        }

    }

    final class SetPhasingOnly extends AbstractAttachment {

        private final PhasingParams phasingParams;
        private final long maxFees;
        private final short minDuration;
        private final short maxDuration;

        public SetPhasingOnly(PhasingParams params, long maxFees, short minDuration, short maxDuration) {
            phasingParams = params;
            this.maxFees = maxFees;
            this.minDuration = minDuration;
            this.maxDuration = maxDuration;
        }

        SetPhasingOnly(ByteBuffer buffer) {
            super(buffer);
            phasingParams = new PhasingParams(buffer);
            maxFees = buffer.getLong();
            minDuration = buffer.getShort();
            maxDuration = buffer.getShort();
        }

        SetPhasingOnly(JSONObject attachmentData) {
            super(attachmentData);
            JSONObject phasingControlParams = (JSONObject) attachmentData.get("phasingControlParams");
            phasingParams = new PhasingParams(phasingControlParams);
            maxFees = Convert.parseLong(attachmentData.get("controlMaxFees"));
            minDuration = ((Long)attachmentData.get("controlMinDuration")).shortValue();
            maxDuration = ((Long)attachmentData.get("controlMaxDuration")).shortValue();
        }

        @Override
        public TransactionType getTransactionType() {
            return TransactionType.AccountControl.SET_PHASING_ONLY;
        }

        @Override
        int getMySize() {
            return phasingParams.getMySize() + 8 + 2 + 2;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            phasingParams.putMyBytes(buffer);
            buffer.putLong(maxFees);
            buffer.putShort(minDuration);
            buffer.putShort(maxDuration);
        }

        @Override
        void putMyJSON(JSONObject json) {
            JSONObject phasingControlParams = new JSONObject();
            phasingParams.putMyJSON(phasingControlParams);
            json.put("phasingControlParams", phasingControlParams);
            json.put("controlMaxFees", maxFees);
            json.put("controlMinDuration", minDuration);
            json.put("controlMaxDuration", maxDuration);
        }

        public PhasingParams getPhasingParams() {
            return phasingParams;
        }

        public long getMaxFees() {
            return maxFees;
        }

        public short getMinDuration() {
            return minDuration;
        }

        public short getMaxDuration() {
            return maxDuration;
        }

    }
}
