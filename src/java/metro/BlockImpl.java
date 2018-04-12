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

import metro.crypto.Crypto;
import metro.util.BitcoinJUtils;
import metro.util.Convert;
import metro.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static metro.util.Convert.HASH_SIZE;
import static org.bouncycastle.pqc.math.linearalgebra.IntegerFunctions.squareRoot;

public final class BlockImpl implements Block {

    private final short version;
    private final long timestamp;
    private final long previousBlockId;
    private final long previousKeyBlockId;
    private final long nonce;
    private volatile byte[] generatorPublicKey;
    private final byte[] previousBlockHash;
    private final byte[] previousKeyBlockHash;
    private final byte[] txMerkleRoot;
    private final byte[] forgersMerkleRoot;
    private final long totalAmountMQT;
    private final long rewardMQT;
    private final int payloadLength;
    private final byte[] generationSequence;
    private volatile List<TransactionImpl> blockTransactions;

    private byte[] blockSignature;
    private BigInteger cumulativeDifficulty = BigInteger.ZERO;
    private BigInteger stakeBatchDifficulty = null;
    // MTR style initial POS target
    private long baseTarget = Constants.INITIAL_BASE_TARGET;

    private volatile long nextBlockId;
    private int height = -1;
    private int localHeight = -1;
    private volatile long id;
    private volatile String stringId = null;
    private volatile long generatorId;
    private volatile byte[] bytes = null;

    /**
     * Special constructor for Genesis block only
     *
     */
    BlockImpl(byte[] generatorPublicKey, byte[] generationSequence) {
        // the Genesis block is POS with version 0x0000
        this(Consensus.GENESIS_BLOCK_VERSION, 0, Constants.INITIAL_BASE_TARGET, 0, 0, 0, 0, 0, 0, new byte[HASH_SIZE], generatorPublicKey, generationSequence, new byte[HASH_SIZE * 2],
                Convert.EMPTY_HASH, null, null, Collections.emptyList());
        this.height = 0;
        this.localHeight = 0;
        this.stakeBatchDifficulty = Convert.two64.divide(BigInteger.valueOf(Constants.INITIAL_BASE_TARGET));
    }

    /**
     * Constructs and signs a new block, by passing a secretPhrase
     *
     */
    BlockImpl(short version, long timestamp, long previousBlockId, long previousKeyBlockId, long nonce, long totalAmountMQT, long rewardMQT, int payloadLength, byte[] txMerkleRoot,
              byte[] generatorPublicKey, byte[] generationSequence, byte[] previousBlockHash, byte[] previousKeyBlockHash, byte[] forgersMerkleRoot, List<TransactionImpl> transactions, String secretPhrase) {
        this(version, timestamp, 0, previousBlockId, previousKeyBlockId, nonce, totalAmountMQT, rewardMQT, payloadLength, txMerkleRoot,
                generatorPublicKey, generationSequence, null, previousBlockHash, previousKeyBlockHash, forgersMerkleRoot, transactions);
        blockSignature = Crypto.sign(bytes(), secretPhrase);
        bytes = null;
    }

    /**
     * Typical constructor called for a block not yet in DB
     *
     */
    BlockImpl(short version, long timestamp, long baseTarget, long previousBlockId, long previousKeyBlockId, long nonce, long totalAmountMQT, long rewardMQT, int payloadLength, byte[] txMerkleRoot,
              byte[] generatorPublicKey, byte[] generationSequence, byte[] blockSignature, byte[] previousBlockHash, byte[] previousKeyBlockHash, byte[] forgersMerkleRoot, List<TransactionImpl> transactions) {
        this.version = version;
        this.timestamp = timestamp;
        this.baseTarget = baseTarget;
        this.previousBlockId = previousBlockId;
        this.previousKeyBlockId = previousKeyBlockId;
        this.nonce = nonce;
        this.totalAmountMQT = totalAmountMQT;
        this.rewardMQT = rewardMQT;
        this.payloadLength = payloadLength;
        this.txMerkleRoot = txMerkleRoot;
        this.generatorPublicKey = generatorPublicKey;
        this.generationSequence = generationSequence;
        this.blockSignature = blockSignature;
        this.previousBlockHash = previousBlockHash;
        this.previousKeyBlockHash = previousKeyBlockHash;
        this.forgersMerkleRoot = forgersMerkleRoot;
        if (transactions != null) {
            this.blockTransactions = Collections.unmodifiableList(transactions);
        }
    }

    /**
     * Constructor used after existing block is loaded from DB
     *
     */
    BlockImpl(short version, long timestamp, long previousBlockId, long previousKeyBlockId, long nonce, long totalAmountMQT, long rewardMQT, int payloadLength,
              byte[] txMerkleRoot, long generatorId, byte[] generationSequence, byte[] blockSignature,
              byte[] previousBlockHash, byte[] previousKeyBlockHash, byte[] forgersMerkleRoot,
              BigInteger cumulativeDifficulty, BigInteger stakeBatchDifficulty, long baseTarget, long nextBlockId, int height, int localHeight, long id,
              List<TransactionImpl> blockTransactions) {
        this(version, timestamp, baseTarget, previousBlockId, previousKeyBlockId, nonce, totalAmountMQT, rewardMQT, payloadLength, txMerkleRoot,
                null, generationSequence, blockSignature, previousBlockHash, previousKeyBlockHash, forgersMerkleRoot, null);
        this.cumulativeDifficulty = cumulativeDifficulty;
        this.stakeBatchDifficulty = stakeBatchDifficulty;
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
    public long getTimestamp() {
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
    public byte[] getForgersMerkleRoot() {
        return forgersMerkleRoot;
    }

    @Override
    public long getTotalAmountMQT() {
        return totalAmountMQT;
    }

    @Override
    public long getRewardMQT() {
        return rewardMQT;
    }

    @Override
    public int getPayloadLength() {
        return payloadLength;
    }

    @Override
    public byte[] getTxMerkleRoot() {
        return txMerkleRoot;
    }

    @Override
    public byte[] getGenerationSequence() {
        return generationSequence;
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
    public BigInteger getStakeBatchDifficulty() {
        if (stakeBatchDifficulty == null) {
            throw new IllegalStateException("Block stakeBatchDifficulty not yet calculated (before call to calculateBaseTarget)");
        }
        return stakeBatchDifficulty;
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
            // TODO #144
            if (!isKeyBlock() && blockSignature == null) {
                throw new IllegalStateException("Block is not signed yet");
            }
            byte[] hash = Consensus.HASH_FUNCTION.hash(bytes());
            BigInteger bigInteger = Convert.fullHashToBigInteger(hash);
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
            json.put("forgersMerkleRoot", Convert.toHexString(forgersMerkleRoot));
        } else {
            json.put("payloadLength", payloadLength);
        }
        json.put("txMerkleRoot", Convert.toHexString(txMerkleRoot));
        json.put("generationSequence", Convert.toHexString(generationSequence));
        json.put("totalAmountMQT", totalAmountMQT);
        json.put("rewardMQT", rewardMQT);
        json.put("generatorPublicKey", Convert.toHexString(getGeneratorPublicKey()));

        json.put("previousBlockHash", Convert.toHexString(previousBlockHash));
        json.put("blockSignature", Convert.toHexString(blockSignature));
        JSONArray transactionsData = new JSONArray();
        getTransactions().forEach(transaction -> transactionsData.add(transaction.getJSONObject()));
        json.put("transactions", transactionsData);
        return json;
    }

    static BlockImpl parseBlock(JSONObject blockData) throws MetroException.NotValidException {
        try {
            short version = ((Long) blockData.get("version")).shortValue();
            boolean keyBlock = isKeyBlockVersion(version);
            long timestamp = ((Long) blockData.get("timestamp")).longValue();
            long previousKeyBlock = 0, nonce = 0, baseTarget = 0;
            int payloadLength = 0;
            byte[] previousKeyBlockHash = null, forgersMerkleRoot = null, txMerkleRoot;
            if (keyBlock) {
                previousKeyBlock = Convert.parseUnsignedLong((String) blockData.get("previousKeyBlock"));
                nonce = Convert.parseLong(blockData.get("nonce"));
                baseTarget = Convert.parseLong(blockData.get("baseTarget"));
                previousKeyBlockHash = Convert.parseHexString((String) blockData.get("previousKeyBlockHash"));
                forgersMerkleRoot = Convert.parseHexString((String) blockData.get("forgersMerkleRoot"));
            } else {
                payloadLength = ((Long) blockData.get("payloadLength")).intValue();
            }
            txMerkleRoot = Convert.parseHexString((String) blockData.get("txMerkleRoot"));
            byte[] generationSequence = Convert.parseHexString((String) blockData.get("generationSequence"));
            long previousBlock = Convert.parseUnsignedLong((String) blockData.get("previousBlock"));
            long totalAmountMQT = Convert.parseLong(blockData.get("totalAmountMQT"));
            long rewardMQT = Convert.parseLong(blockData.get("rewardMQT"));
            byte[] generatorPublicKey = Convert.parseHexString((String) blockData.get("generatorPublicKey"));
            byte[] blockSignature = Convert.parseHexString((String) blockData.get("blockSignature"));
            byte[] previousBlockHash = version == 1 ? null : Convert.parseHexString((String) blockData.get("previousBlockHash"));
            List<TransactionImpl> blockTransactions = new ArrayList<>();
            for (Object transactionData : (JSONArray) blockData.get("transactions")) {
                blockTransactions.add(TransactionImpl.parseTransaction((JSONObject) transactionData));
            }
            BlockImpl block = new BlockImpl(version, timestamp, baseTarget, previousBlock, previousKeyBlock, nonce, totalAmountMQT, rewardMQT, payloadLength, txMerkleRoot, generatorPublicKey,
                    generationSequence, blockSignature, previousBlockHash, previousKeyBlockHash, forgersMerkleRoot, blockTransactions);
            // TODO #144
            if (!keyBlock && !block.checkSignature()) {
                throw new MetroException.NotValidException("Invalid block signature");
            }
            return block;
        } catch (MetroException.NotValidException|RuntimeException e) {
            Logger.logDebugMessage("Failed to parse block: " + blockData.toJSONString());
            throw e;
        }
    }

    @Override
    public byte[] getBytes() {
        return Arrays.copyOf(bytes(), bytes.length);
    }

    public static int getHeaderSize(boolean keyBlock, boolean signed) {
        return 2 + 8 + 32*2 + (keyBlock ? (32*2 + 4 + 8) : 8 + 56) + (signed ? 64 : 0);
    }

    /**
     * blockSignature MUST be in the last 64 bytes, if present
     * otherwise checkSignature() will be BROKEN
     *
     // POS block may follow keyblock and refer to it in it's previousBlockId
     // or may just follow another POS block in which case previousBlockId points to POS[N-1] where N is height
     // POW block (a.k.a. keyBlock) needs to have both: it has ALWAYS a POS/POW immediately preceding it (in previousBlockId)
     //  and POW[L-1] where L is POW local height
     * @return byte array containing the header of POS or key block
     */
    byte[] bytes() {
        if (bytes == null) {
            final boolean isKeyBlock = isKeyBlock();
            final boolean isSignedPosBlock = !isKeyBlock && blockSignature != null;
            ByteBuffer buffer = ByteBuffer.allocate(getHeaderSize(isKeyBlock, isSignedPosBlock));
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            // Block.version: the most significant bit to differentiate between block (0x0001...) and keyblock (0x8001...)
            buffer.putShort(version);
            // Block.timestamp: 8 bytes, milliseconds since MetroEpoch
            buffer.putLong(timestamp);
            buffer.put(txMerkleRoot);
            buffer.put(previousBlockHash);

            if (isKeyBlock) {
                // Key blocks (starting from the 2nd) have non-null previousKeyBlock reference
                buffer.put(previousKeyBlockHash);
                buffer.put(forgersMerkleRoot);
                // only 4 bytes of target are needed for PoW
                buffer.putInt((int) (baseTarget & 0xffffffffL));
                // 8 rather than 4 bytes, so no "extranonce" needed
                buffer.putLong(nonce);
            } else {
                buffer.putLong(rewardMQT);
                // keep previousBlockId here to preserve compatibility with MTR original POS -
                // previousBlockId is just 8 contiguous bytes from previousBlockHash
                buffer.putLong(previousBlockId);
                // these three seem to be necessary because of MTR pruning concept
                buffer.putInt(getTransactions().size());
                buffer.putLong(totalAmountMQT);
                buffer.putInt(payloadLength);
                buffer.put(getGeneratorPublicKey());
            }
            if (isSignedPosBlock) {
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

    boolean verifyGenerationSequence() throws BlockchainProcessor.BlockOutOfOrderException {
        try {
            BlockImpl previousBlock = BlockchainImpl.getInstance().getBlock(getPreviousBlockId());
            if (previousBlock == null) {
                throw new BlockchainProcessor.BlockOutOfOrderException("Can't verify signature because previous block is missing", this);
            }
            byte[] generationSequenceHash = Convert.generationSequence(previousBlock.generationSequence, getGeneratorPublicKey());
            if (!Arrays.equals(generationSequence, generationSequenceHash)) {
                return false;
            }
            if (!isKeyBlock()) {
                Account account = Account.getAccount(getGeneratorId());
                long effectiveBalance = account == null ? 0 : account.getEffectiveBalanceMTR();
                if (effectiveBalance <= 0) {
                    return false;
                }
                BigInteger hit = Convert.fullHashToBigInteger(generationSequenceHash);
                return Generator.verifyHit(hit, BigInteger.valueOf(effectiveBalance), previousBlock, timestamp);
            }
            return true;
        } catch (RuntimeException e) {
            Logger.logMessage("Error verifying block generation signature", e);
            return false;
        }
    }

    void apply() {
        Account generatorAccount = Account.addOrGetAccount(getGeneratorId());
        generatorAccount.apply(getGeneratorPublicKey());
    }

    void setPrevious(BlockImpl posBlock, BlockImpl keyBlock) {
        BlockImpl prevBlock;
        if (posBlock == null) {
            throw new IllegalArgumentException("Previous pos block is null");
        }

        boolean prevBlockIsKeyBlock = keyBlock != null && keyBlock.getHeight() > posBlock.getHeight();
        prevBlock = prevBlockIsKeyBlock ? keyBlock : posBlock;

        this.height = prevBlock.getHeight() + 1;

        if (prevBlock.getId() != getPreviousBlockId()) {
            // shouldn't happen as previous id is already verified, but just in case
            throw new IllegalStateException("Previous block id doesn't match");
        }

        if (isKeyBlock()) {
            this.localHeight = keyBlock == null ? 0 : keyBlock.getLocalHeight() + 1;
            this.stakeBatchDifficulty = Convert.two64.divide(BigInteger.valueOf(posBlock.baseTarget));
        } else {
            this.localHeight = posBlock.getLocalHeight() + 1;
            this.calculateBaseTarget(prevBlockIsKeyBlock, posBlock);
        }
        // N is height of previous key block; K is size of stakeBatch (number of POS blocks since N)
        // CD[N + K] = CD[N] + sqrt(WD[N]*sum(SD[i])i=N..N+K)
        // CD = cumulative hybrid; WD = work difficulty of one block; SD = stake difficulty of one block
        BigInteger currentBatch = isKeyBlock() ? prevBlock.stakeBatchDifficulty : stakeBatchDifficulty;
        BigInteger keyBlockWork;
        if (isKeyBlock()) {
            keyBlockWork = getWork();
        } else {
            keyBlockWork = keyBlock != null ? keyBlock.getWork() : BigInteger.ONE;
        }
        BigInteger prevCumulativeDifficulty = keyBlock != null ? keyBlock.cumulativeDifficulty : BigInteger.ZERO;

        this.cumulativeDifficulty = prevCumulativeDifficulty.add(squareRoot(keyBlockWork.multiply(currentBatch)));

    }

    void setPreceding() {
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

    private void calculateBaseTarget(boolean prevIsKeyBlock, BlockImpl posBlock) {
        long prevBaseTarget = posBlock.baseTarget;
        int blockchainHeight = posBlock.localHeight;
        if (blockchainHeight > 2 && blockchainHeight % 2 == 0) {
            BlockImpl block = BlockDb.findBlockAtLocalHeight(blockchainHeight - 2, false);
            long blocktimeAverage = (this.timestamp - block.timestamp) / 3;
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

        stakeBatchDifficulty = Convert.two64.divide(BigInteger.valueOf(baseTarget));
        // after each key block, stakeBatchDifficulty restarts from zero
        if (!prevIsKeyBlock) {
            stakeBatchDifficulty = stakeBatchDifficulty.add(posBlock.stakeBatchDifficulty);
        }
    }

    /**
     * Returns the difficulty target as a 256 bit value that can be compared to a SHA-256 hash. Inside a block the
     * target is represented using a compact form. If this form decodes to a value that is out of bounds, an exception
     * is thrown.
     *
     * difficultyTarget of the Genesis block is set to 0x1d07fff8L in org.bitcoinj.core.Block "Special case constructor"
     */
    @Override
    public BigInteger getDifficultyTargetAsInteger() {
        BigInteger target = BitcoinJUtils.decodeCompactBits(baseTarget);
        if (target.signum() <= 0 || target.compareTo(Consensus.MAX_WORK_TARGET) > 0)
            throw new IllegalStateException("Difficulty target is bad: " + target.toString());
        return target;
    }

    /**
     * Returns the work represented by this block.<p>
     *
     * Work is defined as the number of tries needed to solve a block in the
     * average case. Consider a difficulty target that covers 5% of all possible
     * hash values. Then the work of the block will be 20. As the target gets
     * lower, the amount of work goes up.
     */
    public BigInteger getWork() {
        BigInteger target = getDifficultyTargetAsInteger();
        return Convert.LARGEST_HASH.divide(target.add(BigInteger.ONE));
    }

    @Override
    public void sign(String secretPhrase) {
        if (!isKeyBlock()) {
            throw new IllegalStateException("Only key block could be signed.");
        }
        if (blockSignature != null) {
            throw new IllegalStateException("Key block already signed.");
        } else {
            blockSignature = Crypto.sign(bytes(), secretPhrase);
            bytes = null;
        }
    }
}
