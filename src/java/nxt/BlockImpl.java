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

package nxt;

import nxt.AccountLedger.LedgerEvent;
import nxt.crypto.Crypto;
import nxt.util.Convert;
import nxt.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class BlockImpl implements Block {

    private final short version;
    private final int timestamp;
    private final long previousBlockId;
    // TODO previousKeyBlockId and nonce should be 0 in POS blocks, validate it somewhere!
    private final long previousKeyBlockId;
    private final long nonce;
    private volatile byte[] generatorPublicKey;
    private final byte[] previousBlockHash;
    private final byte[] previousKeyBlockHash;
    private final byte[] posBlocksSummary;
    private final byte[] stakeMerkleRoot;
    private final long totalAmountNQT;
    private final long totalFeeNQT;
    private final int payloadLength;
    private final byte[] generationSignature;
    private final byte[] payloadHash;
    private volatile List<TransactionImpl> blockTransactions;

    private byte[] blockSignature;
    private BigInteger cumulativeDifficulty = BigInteger.ZERO;
    private long baseTarget = Constants.INITIAL_BASE_TARGET;
    private volatile long nextBlockId;
    private int height = -1;
    private int localHeight = -1;
    private volatile long id;
    private volatile String stringId = null;
    private volatile long generatorId;
    private volatile byte[] bytes = null;


    BlockImpl(byte[] generatorPublicKey, byte[] generationSignature) {
        // the Genesis block is POS with version 0x7FFF?
        this((short)0x7FFF, 0, Constants.INITIAL_BASE_TARGET, 0, 0, 0, 0, 0, 0, new byte[32], generatorPublicKey, generationSignature, new byte[64],
                new byte[32], new byte[32], new byte[32], new byte[32], Collections.emptyList());
        this.height = 0;
        this.localHeight = 0;
    }

    BlockImpl(short version, int timestamp, long previousBlockId, long previousKeyBlockId, long nonce, long totalAmountNQT, long totalFeeNQT, int payloadLength, byte[] payloadHash,
              byte[] generatorPublicKey, byte[] generationSignature, byte[] previousBlockHash, byte[] previousKeyBlockHash, byte[] posBlocksSummary, byte[] stakeMerkleRoot, List<TransactionImpl> transactions, String secretPhrase) {
        this(version, timestamp, 0, previousBlockId, previousKeyBlockId, nonce, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash,
                generatorPublicKey, generationSignature, null, previousBlockHash, previousKeyBlockHash, posBlocksSummary, stakeMerkleRoot, transactions);
        blockSignature = Crypto.sign(bytes(), secretPhrase);
        bytes = null;
    }

    BlockImpl(short version, int timestamp, long baseTarget, long previousBlockId, long previousKeyBlockId, long nonce, long totalAmountNQT, long totalFeeNQT, int payloadLength, byte[] payloadHash,
              byte[] generatorPublicKey, byte[] generationSignature, byte[] blockSignature, byte[] previousBlockHash, byte[] previousKeyBlockHash, byte[] posBlocksSummary, byte[] stakeMerkleRoot, List<TransactionImpl> transactions) {
        this.version = version;
        this.timestamp = timestamp;
        this.baseTarget = baseTarget;
        this.previousBlockId = previousBlockId;
        this.previousKeyBlockId = previousKeyBlockId;
        this.nonce = nonce;
        this.totalAmountNQT = totalAmountNQT;
        this.totalFeeNQT = totalFeeNQT;
        this.payloadLength = payloadLength;
        this.payloadHash = payloadHash;
        this.generatorPublicKey = generatorPublicKey;
        // 32 bytes actually, and not exactly a "signature"
        this.generationSignature = generationSignature;
        this.blockSignature = blockSignature;
        this.previousBlockHash = previousBlockHash;
        this.previousKeyBlockHash = previousKeyBlockHash;
        this.posBlocksSummary = posBlocksSummary;
        this.stakeMerkleRoot = stakeMerkleRoot;
        if (transactions != null) {
            this.blockTransactions = Collections.unmodifiableList(transactions);
        }
    }

    BlockImpl(short version, int timestamp, long previousBlockId, long previousKeyBlockId, long nonce, long totalAmountNQT, long totalFeeNQT, int payloadLength,
              byte[] payloadHash, long generatorId, byte[] generationSignature, byte[] blockSignature,
              byte[] previousBlockHash, byte[] previousKeyBlockHash, byte[] posBlocksSummary, byte[] stakeMerkleRoot,
              BigInteger cumulativeDifficulty, long baseTarget, long nextBlockId, int height, int localHeight, long id,
              List<TransactionImpl> blockTransactions) {
        this(version, timestamp, baseTarget, previousBlockId, previousKeyBlockId, nonce, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash,
                null, generationSignature, blockSignature, previousBlockHash, previousKeyBlockHash, posBlocksSummary, stakeMerkleRoot, null);
        this.cumulativeDifficulty = cumulativeDifficulty;
        this.nextBlockId = nextBlockId;
        this.height = height;
        this.localHeight = localHeight;
        this.id = id;
        this.generatorId = generatorId;
        this.blockTransactions = blockTransactions;
    }

    @Override
    public short getVersion() {
        return version;
    }

    @Override
    public boolean isKeyBlock() {
        return isKeyBlockVersion(this.version);
    }

    public static boolean isKeyBlockVersion(short version) {
        return Short.toUnsignedInt(version) > 0x8000;
    }

    @Override
    public int getTimestamp() {
        return timestamp;
    }

    @Override
    public long getPreviousBlockId() {
        return previousBlockId;
    }

    @Override
    public long getPreviousKeyBlockId() {
        return previousKeyBlockId;
    }

    @Override
    public long getNonce() {
        return nonce;
    }

    @Override
    public byte[] getGeneratorPublicKey() {
        if (generatorPublicKey == null) {
            generatorPublicKey = Account.getPublicKey(generatorId);
        }
        return generatorPublicKey;
    }

    @Override
    public byte[] getPreviousBlockHash() {
        return previousBlockHash;
    }

    @Override
    public byte[] getPreviousKeyBlockHash() {
        return previousKeyBlockHash;
    }

    @Override
    public byte[] getPosBlocksSummary() {
        return posBlocksSummary;
    }

    @Override
    public byte[] getStakeMerkleRoot() {
        return stakeMerkleRoot;
    }

    @Override
    public long getTotalAmountNQT() {
        return totalAmountNQT;
    }

    @Override
    public long getTotalFeeNQT() {
        return totalFeeNQT;
    }

    @Override
    public int getPayloadLength() {
        return payloadLength;
    }

    @Override
    public byte[] getPayloadHash() {
        return payloadHash;
    }

    @Override
    public byte[] getGenerationSignature() {
        return generationSignature;
    }

    @Override
    public byte[] getBlockSignature() {
        return blockSignature;
    }

    @Override
    public List<TransactionImpl> getTransactions() {
        if (this.blockTransactions == null) {
            List<TransactionImpl> transactions = Collections.unmodifiableList(TransactionDb.findBlockTransactions(getId()));
            for (TransactionImpl transaction : transactions) {
                transaction.setBlock(this);
            }
            this.blockTransactions = transactions;
        }
        return this.blockTransactions;
    }

    @Override
    public long getBaseTarget() {
        return baseTarget;
    }

    @Override
    public BigInteger getCumulativeDifficulty() {
        return cumulativeDifficulty;
    }

    @Override
    public long getNextBlockId() {
        return nextBlockId;
    }

    void setNextBlockId(long nextBlockId) {
        this.nextBlockId = nextBlockId;
    }

    @Override
    public int getHeight() {
        if (height == -1) {
            throw new IllegalStateException("Block height not yet set");
        }
        return height;
    }

    @Override
    public int getLocalHeight() {
        if (localHeight == -1) {
            throw new IllegalStateException("Block localHeight not yet set");
        }
        return localHeight;
    }

    @Override
    public long getId() {
        if (id == 0) {
            // TODO passing keyBlock signature over API?
            if (!isKeyBlock() && blockSignature == null) {
                throw new IllegalStateException("Block is not signed yet");
            }
            byte[] hash = Crypto.sha256().digest(bytes());
            BigInteger bigInteger = new BigInteger(1, new byte[] {hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0]});
            id = bigInteger.longValue();
            stringId = bigInteger.toString();
        }
        return id;
    }

    @Override
    public String getStringId() {
        if (stringId == null) {
            getId();
            if (stringId == null) {
                stringId = Long.toUnsignedString(id);
            }
        }
        return stringId;
    }

    @Override
    public long getGeneratorId() {
        if (generatorId == 0) {
            generatorId = Account.getId(getGeneratorPublicKey());
        }
        return generatorId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BlockImpl && this.getId() == ((BlockImpl)o).getId();
    }

    @Override
    public int hashCode() {
        return (int)(getId() ^ (getId() >>> 32));
    }

    @Override
    public JSONObject getJSONObject() {
        JSONObject json = new JSONObject();
        json.put("version", version);
        json.put("timestamp", timestamp);
        json.put("previousBlock", Long.toUnsignedString(previousBlockId));
        if (isKeyBlock()) {
            json.put("previousKeyBlock", Long.toUnsignedString(previousKeyBlockId));
            json.put("nonce", nonce);
            json.put("baseTarget", baseTarget);
            json.put("previousKeyBlockHash", Convert.toHexString(previousKeyBlockHash));
            json.put("posBlocksSummary", Convert.toHexString(posBlocksSummary));
            json.put("stakeMerkleRoot", Convert.toHexString(stakeMerkleRoot));
        } else {
            json.put("payloadLength", payloadLength);
            json.put("payloadHash", Convert.toHexString(payloadHash));
        }
        json.put("generationSignature", Convert.toHexString(generationSignature));
        json.put("totalAmountNQT", totalAmountNQT);
        json.put("totalFeeNQT", totalFeeNQT);
        json.put("generatorPublicKey", Convert.toHexString(getGeneratorPublicKey()));

        json.put("previousBlockHash", Convert.toHexString(previousBlockHash));
        json.put("blockSignature", Convert.toHexString(blockSignature));
        JSONArray transactionsData = new JSONArray();
        getTransactions().forEach(transaction -> transactionsData.add(transaction.getJSONObject()));
        json.put("transactions", transactionsData);
        return json;
    }

    static BlockImpl parseBlock(JSONObject blockData) throws NxtException.NotValidException {
        try {
            short version = ((Long) blockData.get("version")).shortValue();
            boolean keyBlock = isKeyBlockVersion(version);
            int timestamp = ((Long) blockData.get("timestamp")).intValue();
            long previousKeyBlock = 0, nonce = 0, baseTarget = 0;
            int payloadLength = 0;
            byte[] previousKeyBlockHash = null, posBlocksSummary = null, stakeMerkleRoot = null, payloadHash = null;
            if (keyBlock) {
                previousKeyBlock = Convert.parseUnsignedLong((String) blockData.get("previousKeyBlock"));
                nonce = Convert.parseLong(blockData.get("nonce"));
                baseTarget = Convert.parseLong(blockData.get("baseTarget"));
                previousKeyBlockHash = Convert.parseHexString((String) blockData.get("previousKeyBlockHash"));
                posBlocksSummary = Convert.parseHexString((String) blockData.get("posBlocksSummary"));
                stakeMerkleRoot = Convert.parseHexString((String) blockData.get("stakeMerkleRoot"));
                // TODO txMerkleRoot
                payloadHash = Convert.EMPTY_PAYLOAD_HASH;
            } else {
                payloadLength = ((Long) blockData.get("payloadLength")).intValue();
                payloadHash = Convert.parseHexString((String) blockData.get("payloadHash"));
            }
            byte[] generationSignature = Convert.parseHexString((String) blockData.get("generationSignature"));
            long previousBlock = Convert.parseUnsignedLong((String) blockData.get("previousBlock"));
            long totalAmountNQT = Convert.parseLong(blockData.get("totalAmountNQT"));
            long totalFeeNQT = Convert.parseLong(blockData.get("totalFeeNQT"));
            byte[] generatorPublicKey = Convert.parseHexString((String) blockData.get("generatorPublicKey"));
            byte[] blockSignature = Convert.parseHexString((String) blockData.get("blockSignature"));
            byte[] previousBlockHash = version == 1 ? null : Convert.parseHexString((String) blockData.get("previousBlockHash"));
            List<TransactionImpl> blockTransactions = new ArrayList<>();
            for (Object transactionData : (JSONArray) blockData.get("transactions")) {
                blockTransactions.add(TransactionImpl.parseTransaction((JSONObject) transactionData));
            }
            BlockImpl block = new BlockImpl(version, timestamp, baseTarget, previousBlock, previousKeyBlock, nonce, totalAmountNQT, totalFeeNQT, payloadLength, payloadHash, generatorPublicKey,
                    generationSignature, blockSignature, previousBlockHash, previousKeyBlockHash, posBlocksSummary, stakeMerkleRoot, blockTransactions);
            // TODO #144
            if (!keyBlock && !block.checkSignature()) {
                throw new NxtException.NotValidException("Invalid block signature");
            }
            return block;
        } catch (NxtException.NotValidException|RuntimeException e) {
            Logger.logDebugMessage("Failed to parse block: " + blockData.toJSONString());
            throw e;
        }
    }

    @Override
    public byte[] getBytes() {
        return Arrays.copyOf(bytes(), bytes.length);
    }

    byte[] bytes() {
        if (bytes == null) {
            final boolean isKeyBlock = isKeyBlock();
            ByteBuffer buffer = ByteBuffer.allocate(2 + 4 + 8 + 32*3 + (isKeyBlock ? (32*3 + 4 + 8) : 0) + (blockSignature != null ? 64 : 0));
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // Block.version: the most significant bit to differentiate between block (0x0001...) and keyblock (0x8001...)
            buffer.putShort(version);
            // For simplicity, all POS block header fields will be also part of keyblock header,
            // so we have only one "if (isKeyBlock)" at the end of this code block
            buffer.putInt(timestamp);

            buffer.putLong(totalFeeNQT);

            buffer.put(payloadHash);

            buffer.put(getGeneratorPublicKey());

            buffer.put(previousBlockHash);

            if (isKeyBlock) {
                // Key blocks (starting from the 2nd) have non-null previousKeyBlock reference
                buffer.put(previousKeyBlockHash);
                buffer.put(posBlocksSummary);
                buffer.put(stakeMerkleRoot);
                // only 4 bytes of target are needed for PoW
                buffer.putInt((int) (baseTarget & 0xffffffffL));
                // 8 rather than 4 bytes, so no "extranonce" needed
                buffer.putLong(nonce);
            }
            if (!isKeyBlock && blockSignature != null) {
                buffer.put(blockSignature);
            }
            bytes = buffer.array();
        }
        return bytes;
    }

    boolean verifyBlockSignature() {
        return checkSignature() && Account.setOrVerify(getGeneratorId(), getGeneratorPublicKey());
    }

    private volatile boolean hasValidSignature = false;

    private boolean checkSignature() {
        if (! hasValidSignature) {
            byte[] data = Arrays.copyOf(bytes(), bytes.length - 64);
            hasValidSignature = blockSignature != null && Crypto.verify(blockSignature, data, getGeneratorPublicKey());
        }
        return hasValidSignature;
    }

    boolean verifyGenerationSignature() throws BlockchainProcessor.BlockOutOfOrderException {

        try {

            BlockImpl previousBlock = BlockchainImpl.getInstance().getBlock(getPreviousBlockId());
            if (previousBlock == null) {
                throw new BlockchainProcessor.BlockOutOfOrderException("Can't verify signature because previous block is missing", this);
            }

            Account account = Account.getAccount(getGeneratorId());
            long effectiveBalance = account == null ? 0 : account.getEffectiveBalanceNXT();
            if (effectiveBalance <= 0) {
                return false;
            }

            MessageDigest digest = Crypto.sha256();
            digest.update(previousBlock.generationSignature);
            byte[] generationSignatureHash = digest.digest(getGeneratorPublicKey());
            if (!Arrays.equals(generationSignature, generationSignatureHash)) {
                return false;
            }

            BigInteger hit = new BigInteger(1, new byte[]{generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5], generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2], generationSignatureHash[1], generationSignatureHash[0]});

            return Generator.verifyHit(hit, BigInteger.valueOf(effectiveBalance), previousBlock, timestamp);

        } catch (RuntimeException e) {

            Logger.logMessage("Error verifying block generation signature", e);
            return false;

        }

    }

    void apply() {
        Account generatorAccount = Account.addOrGetAccount(getGeneratorId());
        generatorAccount.apply(getGeneratorPublicKey());
        long totalBackFees = 0;
        if (this.localHeight > 3) {
            long[] backFees = new long[3];
            for (TransactionImpl transaction : getTransactions()) {
                long[] fees = transaction.getBackFees();
                for (int i = 0; i < fees.length; i++) {
                    backFees[i] += fees[i];
                }
            }
            for (int i = 0; i < backFees.length; i++) {
                if (backFees[i] == 0) {
                    break;
                }
                totalBackFees += backFees[i];
                Account previousGeneratorAccount = Account.getAccount(BlockDb.findBlockAtLocalHeight(this.localHeight - i - 1, false).getGeneratorId());
                Logger.logDebugMessage("Back fees %f %s to forger at POS height %d", ((double)backFees[i])/Constants.ONE_NXT, Constants.COIN_SYMBOL, this.localHeight - i - 1);
                previousGeneratorAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.BLOCK_GENERATED, getId(), backFees[i]);
                previousGeneratorAccount.addToForgedBalanceNQT(backFees[i]);
            }
        }
        if (totalBackFees != 0) {
            Logger.logDebugMessage("Fee reduced by %f %s at POS height %d", ((double)totalBackFees)/Constants.ONE_NXT, Constants.COIN_SYMBOL, this.localHeight);
        }
        generatorAccount.addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent.BLOCK_GENERATED, getId(), totalFeeNQT - totalBackFees);
        generatorAccount.addToForgedBalanceNQT(totalFeeNQT - totalBackFees);
    }

    void setPrevious(BlockImpl block, BlockImpl posBlock) {
        if (block != null) {
            if (block.getId() != getPreviousBlockId()) {
                // shouldn't happen as previous id is already verified, but just in case
                throw new IllegalStateException("Previous block id doesn't match");
            }
            this.height = block.getHeight() + 1;
            if (isKeyBlock()) {
                // TODO we may use previousKeyBlockId here once it's set, to obtain last key localHeight
                this.localHeight = BlockDb.getMaxLocalHeightOfKeyBlocks() + 1;
                this.cumulativeDifficulty = block.cumulativeDifficulty.add(BigInteger.ONE);
            } else {
                this.localHeight = posBlock.getLocalHeight() + 1;
                this.calculateBaseTarget(block);
            }
        } else {
            this.height = 0;
            this.localHeight = 0;
        }
        short index = 0;
        for (TransactionImpl transaction : getTransactions()) {
            transaction.setBlock(this);
            transaction.setIndex(index++);
        }
    }

    void loadTransactions() {
        for (TransactionImpl transaction : getTransactions()) {
            transaction.bytes();
            transaction.getAppendages();
        }
    }

    private void calculateBaseTarget(BlockImpl previousBlock) {
        long prevBaseTarget = previousBlock.baseTarget;
        int blockchainHeight = previousBlock.localHeight;
        if (blockchainHeight > 2 && blockchainHeight % 2 == 0) {
            BlockImpl block = BlockDb.findBlockAtLocalHeight(blockchainHeight - 2, false);
            int blocktimeAverage = (this.timestamp - block.timestamp) / 3;
            if (blocktimeAverage > Constants.BLOCK_TIME) {
                baseTarget = (prevBaseTarget * Math.min(blocktimeAverage, Constants.MAX_BLOCKTIME_LIMIT)) / Constants.BLOCK_TIME;
            } else {
                baseTarget = prevBaseTarget - prevBaseTarget * Constants.BASE_TARGET_GAMMA
                        * (Constants.BLOCK_TIME - Math.max(blocktimeAverage, Constants.MIN_BLOCKTIME_LIMIT)) / (100 * Constants.BLOCK_TIME);
            }
            if (baseTarget < 0 || baseTarget > Constants.MAX_BASE_TARGET) {
                baseTarget = Constants.MAX_BASE_TARGET;
            }
            if (baseTarget < Constants.MIN_BASE_TARGET) {
                baseTarget = Constants.MIN_BASE_TARGET;
            }
        } else {
            baseTarget = prevBaseTarget;
        }
        // TODO PoW/Hybrid
        cumulativeDifficulty = isKeyBlock() ? previousBlock.cumulativeDifficulty : previousBlock.cumulativeDifficulty.add(Convert.two64.divide(BigInteger.valueOf(baseTarget)));
    }

}
