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

import metro.db.DbIterator;
import metro.db.DbUtils;
import metro.util.BitcoinJUtils;
import metro.util.Convert;
import metro.util.Filter;
import metro.util.Logger;
import metro.util.ReadWriteUpdateLock;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static metro.Consensus.GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS;
import static metro.Consensus.HASH_FUNCTION;

final class BlockchainImpl implements Blockchain {

    private static final BlockchainImpl instance = new BlockchainImpl();

    static BlockchainImpl getInstance() {
        return instance;
    }

    private BlockchainImpl() {}

    private final ReadWriteUpdateLock lock = new ReadWriteUpdateLock();
    private final AtomicReference<BlockImpl> lastBlock = new AtomicReference<>();
    private final AtomicReference<BlockImpl> lastKeyBlock = new AtomicReference<>();

    @Override
    public void readLock() {
        lock.readLock().lock();
    }

    @Override
    public void readUnlock() {
        lock.readLock().unlock();
    }

    @Override
    public void updateLock() {
        lock.updateLock().lock();
    }

    @Override
    public void updateUnlock() {
        lock.updateLock().unlock();
    }

    void writeLock() {
        lock.writeLock().lock();
    }

    void writeUnlock() {
        lock.writeLock().unlock();
    }

    @Override
    public BlockImpl getLastBlock() {
        return lastBlock.get();
    }

    void setLastBlock(BlockImpl block) {
        lastBlock.set(block);
        if (block.isKeyBlock()) {
            lastKeyBlock.set(block);
        }
    }

    @Override
    public void forgetLastKeyBlock() {
        lastKeyBlock.set(null);
    }

    @Override
    public int getHeight() {
        BlockImpl last = lastBlock.get();
        return last == null ? 0 : last.getHeight();
    }

    @Override
    public int getGuaranteedBalanceHeight(int height) {
        if (height < 2) {
            // we have only Genesis in the DB
            return height;
        }
        BlockImpl keyHead = height == getHeight() ? getLastKeyBlock() : BlockDb.findLastKeyBlock(height);
        if (keyHead != null) {
            int pastLocalHeight = Math.max(keyHead.getLocalHeight() - GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS + 1, 0);
            BlockImpl guaranteedMileStone = BlockDb.findBlockAtLocalHeight(pastLocalHeight, true);
            if (guaranteedMileStone.getLocalHeight() == 0 && keyHead.getLocalHeight() < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS - 1) {
                // ignore additions in the 1st cluster before seeing the 30th key block
                return 0;
            }
            return guaranteedMileStone.getHeight();
        } else {
            return 0;
        }
    }

    @Override
    public long getLastBlockTimestamp() {
        BlockImpl last = lastBlock.get();
        return last == null ? 0 : last.getTimestamp();
    }

    @Override
    public BlockImpl getLastBlock(long timestamp) {
        BlockImpl block = lastBlock.get();
        if (timestamp >= block.getTimestamp()) {
            return block;
        }
        return BlockDb.findLastBlock(timestamp);
    }

    @Override
    public BlockImpl getLastKeyBlock() {
        if (lastKeyBlock.get() == null) {
            lastKeyBlock.set(BlockDb.findLastKeyBlock(lastBlock.get().getHeight()));
        }
        return lastKeyBlock.get();
    }

    @Override
    public BlockImpl getLastPosBlock() {
        BlockImpl lastBlock = getLastBlock();
        while (lastBlock != null && lastBlock.isKeyBlock()) {
            lastBlock = getBlock(lastBlock.getPreviousBlockId());
        }
        return lastBlock;
    }

    @Override
    public BlockImpl getBlock(long blockId) {
        BlockImpl block = lastBlock.get();
        if (block.getId() == blockId) {
            return block;
        }
        return BlockDb.findBlock(blockId);
    }

    @Override
    public boolean hasBlock(long blockId) {
        return lastBlock.get().getId() == blockId || BlockDb.hasBlock(blockId);
    }

    @Override
    public DbIterator<BlockImpl> getAllBlocks() {
        Connection con = null;
        try {
            con = Db.db.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block ORDER BY db_id ASC");
            return getBlocks(con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DbIterator<BlockImpl> getBlocks(int from, int to) {
        Connection con = null;
        try {
            con = Db.db.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE height <= ? AND height >= ? ORDER BY height DESC");
            int blockchainHeight = getHeight();
            pstmt.setInt(1, blockchainHeight - from);
            pstmt.setInt(2, blockchainHeight - to);
            return getBlocks(con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DbIterator<BlockImpl> getBlocks(long accountId, long timestamp) {
        return getBlocks(accountId, timestamp, 0, -1);
    }

    @Override
    public DbIterator<BlockImpl> getBlocks(long accountId, long timestamp, int from, int to) {
        Connection con = null;
        try {
            con = Db.db.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE generator_id = ? "
                    + (timestamp > 0 ? " AND timestamp >= ? " : " ") + "ORDER BY height DESC"
                    + DbUtils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, accountId);
            if (timestamp > 0) {
                pstmt.setLong(++i, timestamp);
            }
            DbUtils.setLimits(++i, pstmt, from, to);
            return getBlocks(con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public int getBlockCount(long accountId) {
        try (Connection con = Db.db.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM block WHERE generator_id = ?")) {
            pstmt.setLong(1, accountId);
            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DbIterator<BlockImpl> getBlocks(Connection con, PreparedStatement pstmt) {
        return new DbIterator<>(con, pstmt, BlockDb::loadBlock);
    }

    @Override
    public List<Long> getBlockIdsAfter(long blockId, int limit) {
        // Check the block cache
        List<Long> result = new ArrayList<>(BlockDb.BLOCK_CACHE_TOTAL_SIZE);
        synchronized(BlockDb.blockCache) {
            BlockImpl block = BlockDb.blockCache.get(blockId);
            if (block != null) {
                Collection<BlockImpl> cacheMap = BlockDb.heightMap.tailMap(block.getHeight() + 1).values();
                for (BlockImpl cacheBlock : cacheMap) {
                    if (result.size() >= limit) {
                        break;
                    }
                    result.add(cacheBlock.getId());
                }
                return result;
            }
        }
        // Search the database
        try (Connection con = Db.db.getConnection();
                PreparedStatement pstmt = con.prepareStatement("SELECT id FROM block "
                            + "WHERE db_id > IFNULL ((SELECT db_id FROM block WHERE id = ?), " + Long.MAX_VALUE + ") "
                            + "ORDER BY db_id ASC LIMIT ?")) {
            pstmt.setLong(1, blockId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getLong("id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    @Override
    public List<BlockImpl> getBlocksAfter(long blockId, int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        // Check the block cache
        List<BlockImpl> result = new ArrayList<>(BlockDb.BLOCK_CACHE_TOTAL_SIZE);
        synchronized(BlockDb.blockCache) {
            BlockImpl block = BlockDb.blockCache.get(blockId);
            if (block != null) {
                Collection<BlockImpl> cacheMap = BlockDb.heightMap.tailMap(block.getHeight() + 1).values();
                for (BlockImpl cacheBlock : cacheMap) {
                    if (result.size() >= limit) {
                        break;
                    }
                    result.add(cacheBlock);
                }
                return result;
            }
        }
        // Search the database
        try (Connection con = Db.db.getConnection();
                PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block "
                        + "WHERE db_id > IFNULL ((SELECT db_id FROM block WHERE id = ?), " + Long.MAX_VALUE + ") "
                        + "ORDER BY db_id ASC LIMIT ?")) {
            pstmt.setLong(1, blockId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(BlockDb.loadBlock(con, rs, true));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    @Override
    public List<BlockImpl> getBlocksAfter(long blockId, List<Long> blockList) {
        if (blockList.isEmpty()) {
            return Collections.emptyList();
        }
        // Check the block cache
        List<BlockImpl> result = new ArrayList<>(BlockDb.BLOCK_CACHE_TOTAL_SIZE);
        synchronized(BlockDb.blockCache) {
            BlockImpl block = BlockDb.blockCache.get(blockId);
            if (block != null) {
                Collection<BlockImpl> cacheMap = BlockDb.heightMap.tailMap(block.getHeight() + 1).values();
                int index = 0;
                for (BlockImpl cacheBlock : cacheMap) {
                    if (result.size() >= blockList.size() || cacheBlock.getId() != blockList.get(index++)) {
                        break;
                    }
                    result.add(cacheBlock);
                }
                return result;
            }
        }
        // Search the database
        try (Connection con = Db.db.getConnection();
                PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block "
                        + "WHERE db_id > IFNULL ((SELECT db_id FROM block WHERE id = ?), " + Long.MAX_VALUE + ") "
                        + "ORDER BY db_id ASC LIMIT ?")) {
            pstmt.setLong(1, blockId);
            pstmt.setInt(2, blockList.size());
            try (ResultSet rs = pstmt.executeQuery()) {
                int index = 0;
                while (rs.next()) {
                    BlockImpl block = BlockDb.loadBlock(con, rs, true);
                    if (block.getId() != blockList.get(index++)) {
                        break;
                    }
                    result.add(block);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return result;
    }

    @Override
    public long getBlockIdAtHeight(int height) {
        Block block = lastBlock.get();
        if (height > block.getHeight()) {
            throw new IllegalArgumentException("Invalid height " + height + ", current blockchain is at " + block.getHeight());
        }
        if (height == block.getHeight()) {
            return block.getId();
        }
        return BlockDb.findBlockIdAtHeight(height);
    }

    @Override
    public BlockImpl getBlockAtHeight(int height) {
        BlockImpl block = lastBlock.get();
        if (height > block.getHeight()) {
            throw new IllegalArgumentException("Invalid height " + height + ", current blockchain is at " + block.getHeight());
        }
        if (height == block.getHeight()) {
            return block;
        }
        return BlockDb.findBlockAtHeight(height);
    }

    @Override
    public BlockImpl getECBlock(long timestamp) {
        Block block = getLastBlock(timestamp);
        if (block == null) {
            return getBlockAtHeight(0);
        }
        return BlockDb.findBlockAtHeight(Math.max(block.getHeight() - 720, 0));
    }

    @Override
    public Block composeKeyBlock(byte[] headerData, byte[] generatorPublicKey, List<TransactionImpl> transactions) {
        ByteBuffer header = ByteBuffer.wrap(headerData);
        header.order(ByteOrder.LITTLE_ENDIAN);
        short version = header.getShort();
        if (!BlockImpl.isKeyBlockVersion(version)) {
            throw new IllegalArgumentException("Wrong block version: 0x" + Integer.toUnsignedString(Short.toUnsignedInt(version), 16));
        }
        long timestamp = header.getLong();
        final int hashSize = Convert.HASH_SIZE;
        byte[] txMerkleRoot = new byte[hashSize];
        header.get(txMerkleRoot);

        long previousBlockId = header.getLong();
        BlockImpl previousBlock = BlockDb.findBlock(previousBlockId);
        if (previousBlock == null) {
            throw new IllegalArgumentException("Wrong prev block id: " + previousBlockId);
        } else if (previousBlock.getGenerationSequence() == null) {
            throw new IllegalStateException("Generation sequence is not yet set in block " + previousBlockId + " given as previous");
        }
        byte[] previousBlockHash = HASH_FUNCTION.hash(previousBlock.getBytes());

        byte[] generationSignature = Convert.generationSequence(previousBlock.getGenerationSequence(), generatorPublicKey);

        long previousKeyBlockId = header.getLong();
        byte[] previousKeyBlockHash;
        if (previousKeyBlockId != 0) {
            BlockImpl previousKeyBlock = BlockDb.findBlock(previousKeyBlockId);
            if (previousKeyBlock == null) {
                throw new IllegalArgumentException("Wrong prev key block id: " + previousKeyBlockId);
            }
            previousKeyBlockHash = HASH_FUNCTION.hash(previousKeyBlock.getBytes());
        } else {
            previousKeyBlockHash = Convert.EMPTY_HASH;
        }

        byte[] forgersMerkleRoot = new byte[hashSize];
        header.get(forgersMerkleRoot);
        byte[] forgersMerkleBranches = Generator.getCurrentForgersMerkleBranches();
        if (!Arrays.equals(forgersMerkleRoot, HASH_FUNCTION.hash(forgersMerkleBranches))) {
            throw new IllegalArgumentException("Forgers root: " + Convert.toHexString(forgersMerkleRoot) + ", not matching branches: " + Convert.toHexString(forgersMerkleBranches));
        }

        long baseTarget = header.getInt();
        long nonce = header.getLong();
        long rewardMQT = 0L;
        TransactionImpl coinbase = transactions.get(0);
        Attachment.CoinbaseRecipientsAttachment attachment = (Attachment.CoinbaseRecipientsAttachment)coinbase.getAttachment();
        Map<Long, Long> coinbaseRewards = attachment.getRecipients();
        for (long id: coinbaseRewards.keySet()) {
            rewardMQT += coinbaseRewards.get(id);
        }

        return new BlockImpl(version, timestamp, baseTarget, previousBlockId, previousKeyBlockId, nonce,
                0, rewardMQT, 0, txMerkleRoot, generatorPublicKey,
                generationSignature, null, previousBlockHash, previousKeyBlockHash, forgersMerkleBranches, transactions);
    }

    @Override
    public TransactionImpl getTransaction(long transactionId) {
        return TransactionDb.findTransaction(transactionId);
    }

    @Override
    public TransactionImpl getTransactionByFullHash(String fullHash) {
        return TransactionDb.findTransactionByFullHash(Convert.parseHexString(fullHash));
    }

    @Override
    public boolean hasTransaction(long transactionId) {
        return TransactionDb.hasTransaction(transactionId);
    }

    @Override
    public boolean hasTransactionByFullHash(String fullHash) {
        return TransactionDb.hasTransactionByFullHash(Convert.parseHexString(fullHash));
    }

    @Override
    public int getTransactionCount() {
        try (Connection con = Db.db.getConnection(); PreparedStatement pstmt = con.prepareStatement("SELECT COUNT(*) FROM transaction");
             ResultSet rs = pstmt.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DbIterator<TransactionImpl> getAllTransactions() {
        Connection con = null;
        try {
            con = Db.db.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM transaction ORDER BY db_id ASC");
            return getTransactions(con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DbIterator<TransactionImpl> getTransactions(long accountId, byte type, byte subtype, long blockTimestamp,
                                                       boolean includeExpiredPrunable) {
        return getTransactions(accountId, 0, type, subtype, blockTimestamp, false, false, false, 0, -1, includeExpiredPrunable, false);
    }

    @Override
    public DbIterator<TransactionImpl> getTransactions(long accountId, int numberOfConfirmations, byte type, byte subtype,
                                                       long blockTimestamp, boolean withMessage, boolean phasedOnly, boolean nonPhasedOnly,
                                                       int from, int to, boolean includeExpiredPrunable, boolean executedOnly) {
        if (phasedOnly && nonPhasedOnly) {
            throw new IllegalArgumentException("At least one of phasedOnly or nonPhasedOnly must be false");
        }
        int height = numberOfConfirmations > 0 ? getHeight() - numberOfConfirmations : Integer.MAX_VALUE;
        if (height < 0) {
            throw new IllegalArgumentException("Number of confirmations required " + numberOfConfirmations
                    + " exceeds current blockchain height " + getHeight());
        }
        Connection con = null;
        try {
            StringBuilder buf = new StringBuilder();
            buf.append("SELECT transaction.* FROM transaction ");
            if (executedOnly && !nonPhasedOnly) {
                buf.append(" LEFT JOIN phasing_poll_result ON transaction.id = phasing_poll_result.id ");
            }
            buf.append("WHERE recipient_id = ? AND sender_id <> ? ");
            if (blockTimestamp > 0) {
                buf.append("AND block_timestamp >= ? ");
            }
            if (type >= 0) {
                buf.append("AND type = ? ");
                if (subtype >= 0) {
                    buf.append("AND subtype = ? ");
                }
            }
            if (height < Integer.MAX_VALUE) {
                buf.append("AND transaction.height <= ? ");
            }
            if (withMessage) {
                buf.append("AND (has_message = TRUE OR has_encrypted_message = TRUE ");
                buf.append("OR ((has_prunable_message = TRUE OR has_prunable_encrypted_message = TRUE) AND timestamp > ?)) ");
            }
            if (phasedOnly) {
                buf.append("AND phased = TRUE ");
            } else if (nonPhasedOnly) {
                buf.append("AND phased = FALSE ");
            }
            if (executedOnly && !nonPhasedOnly) {
                buf.append("AND (phased = FALSE OR approved = TRUE) ");
            }
            buf.append("UNION ALL SELECT transaction.* FROM transaction ");
            if (executedOnly && !nonPhasedOnly) {
                buf.append(" LEFT JOIN phasing_poll_result ON transaction.id = phasing_poll_result.id ");
            }
            buf.append("WHERE sender_id = ? ");
            if (blockTimestamp > 0) {
                buf.append("AND block_timestamp >= ? ");
            }
            if (type >= 0) {
                buf.append("AND type = ? ");
                if (subtype >= 0) {
                    buf.append("AND subtype = ? ");
                }
            }
            if (height < Integer.MAX_VALUE) {
                buf.append("AND transaction.height <= ? ");
            }
            if (withMessage) {
                buf.append("AND (has_message = TRUE OR has_encrypted_message = TRUE OR has_encrypttoself_message = TRUE ");
                buf.append("OR ((has_prunable_message = TRUE OR has_prunable_encrypted_message = TRUE) AND timestamp > ?)) ");
            }
            if (phasedOnly) {
                buf.append("AND phased = TRUE ");
            } else if (nonPhasedOnly) {
                buf.append("AND phased = FALSE ");
            }
            if (executedOnly && !nonPhasedOnly) {
                buf.append("AND (phased = FALSE OR approved = TRUE) ");
            }

            buf.append("ORDER BY block_timestamp DESC, transaction_index DESC");
            buf.append(DbUtils.limitsClause(from, to));
            con = Db.db.getConnection();
            PreparedStatement pstmt;
            int i = 0;
            pstmt = con.prepareStatement(buf.toString());
            pstmt.setLong(++i, accountId);
            pstmt.setLong(++i, accountId);
            if (blockTimestamp > 0) {
                pstmt.setLong(++i, blockTimestamp);
            }
            if (type >= 0) {
                pstmt.setByte(++i, type);
                if (subtype >= 0) {
                    pstmt.setByte(++i, subtype);
                }
            }
            if (height < Integer.MAX_VALUE) {
                pstmt.setInt(++i, height);
            }
            long prunableExpiration = Math.max(0, Constants.INCLUDE_EXPIRED_PRUNABLE && includeExpiredPrunable ?
                                        Metro.getEpochTime() - Constants.MAX_PRUNABLE_LIFETIME :
                                        Metro.getEpochTime() - Constants.MIN_PRUNABLE_LIFETIME);
            if (withMessage) {
                pstmt.setLong(++i, prunableExpiration);
            }
            pstmt.setLong(++i, accountId);
            if (blockTimestamp > 0) {
                pstmt.setLong(++i, blockTimestamp);
            }
            if (type >= 0) {
                pstmt.setByte(++i, type);
                if (subtype >= 0) {
                    pstmt.setByte(++i, subtype);
                }
            }
            if (height < Integer.MAX_VALUE) {
                pstmt.setInt(++i, height);
            }
            if (withMessage) {
                pstmt.setLong(++i, prunableExpiration);
            }
            DbUtils.setLimits(++i, pstmt, from, to);
            return getTransactions(con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DbIterator<TransactionImpl> getReferencingTransactions(long transactionId, int from, int to) {
        Connection con = null;
        try {
            con = Db.db.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT transaction.* FROM transaction, referenced_transaction "
                    + "WHERE referenced_transaction.referenced_transaction_id = ? "
                    + "AND referenced_transaction.transaction_id = transaction.id "
                    + "ORDER BY transaction.block_timestamp DESC, transaction.transaction_index DESC "
                    + DbUtils.limitsClause(from, to));
            int i = 0;
            pstmt.setLong(++i, transactionId);
            DbUtils.setLimits(++i, pstmt, from, to);
            return getTransactions(con, pstmt);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public DbIterator<TransactionImpl> getTransactions(Connection con, PreparedStatement pstmt) {
        return new DbIterator<>(con, pstmt, TransactionDb::loadTransaction);
    }

    @Override
    public List<TransactionImpl> getExpectedTransactions(Filter<Transaction> filter) {
        Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
        BlockchainProcessorImpl blockchainProcessor = BlockchainProcessorImpl.getInstance();
        List<TransactionImpl> result = new ArrayList<>();
        readLock();
        try {
            try (DbIterator<TransactionImpl> phasedTransactions = PhasingPoll.getFinishingTransactions(getHeight() + 1)) {
                for (TransactionImpl phasedTransaction : phasedTransactions) {
                    try {
                        phasedTransaction.validate();
                        if (!phasedTransaction.attachmentIsDuplicate(duplicates, false) && filter.ok(phasedTransaction)) {
                            result.add(phasedTransaction);
                        }
                    } catch (MetroException.ValidationException ignore) {
                    }
                }
            }
            blockchainProcessor.selectUnconfirmedTransactions(duplicates, getLastBlock(), -1).forEach(
                    unconfirmedTransaction -> {
                        TransactionImpl transaction = unconfirmedTransaction.getTransaction();
                        if (transaction.getPhasing() == null && filter.ok(transaction)) {
                            result.add(transaction);
                        }
                    }
            );
        } finally {
            readUnlock();
        }
        return result;
    }

    @Override
    public BigInteger getNextTarget() {
        return getTarget(getLastKeyBlock());
    }

    @Override
    public BigInteger getTargetAtLocalHeight(int localHeight) throws IllegalArgumentException {
        if (localHeight == 0) {
            return getTarget(null);
        }
        if (getLastKeyBlock().getLocalHeight() < localHeight - 1) {
            throw new IllegalArgumentException("Too big key local heght.");
        }
        return getTarget(BlockDb.findBlockAtLocalHeight(localHeight - 1, true));
    }

    private BigInteger getTarget(Block lastKeyBlock) {

        if (lastKeyBlock == null || lastKeyBlock.getLocalHeight() < Consensus.DIFFICULTY_CALCULATION_INTERVAL) {
            // before first target transit point - initial target
            return Consensus.MAX_WORK_TARGET;
        }

        int nextLocalHight = lastKeyBlock.getLocalHeight() + 1;

        if ((nextLocalHight % Consensus.DIFFICULTY_TRANSITION_INTERVAL) > 0) {
            //Not difficulty transition point
            return lastKeyBlock.getDifficultyTargetAsInteger();
        }

        BlockImpl intervalAgoBlock = BlockDb.findBlockAtLocalHeight(nextLocalHight - Consensus.DIFFICULTY_CALCULATION_INTERVAL, true);

        if (intervalAgoBlock == null) {
            Logger.logErrorMessage("We have enough blocks for target transit - block should not be null!!!");
            return Consensus.MAX_WORK_TARGET;
        }
        Logger.logDebugMessage("intervalAgoBlock found=" + Convert.toHexString(intervalAgoBlock.bytes()));

        long lastTimestamp = lastKeyBlock.getTimestamp();
        long oldTimestamp = intervalAgoBlock.getTimestamp();
        long timeSpan = lastTimestamp - oldTimestamp;
        // Limit the adjustment step
        if (timeSpan < Consensus.TARGET_TIMESPAN / 4) {
            timeSpan = Consensus.TARGET_TIMESPAN / 4;
        }
        if (timeSpan < Consensus.TARGET_TIMESPAN / 4L) {
            timeSpan = Consensus.TARGET_TIMESPAN / 4L;
        }

        BigInteger newTarget = lastKeyBlock.getDifficultyTargetAsInteger();
        newTarget = newTarget.multiply(BigInteger.valueOf(timeSpan));
        newTarget = newTarget.divide(BigInteger.valueOf(Consensus.TARGET_TIMESPAN));

        if (newTarget.compareTo(Consensus.MAX_WORK_TARGET) > 0) {
            newTarget = Consensus.MAX_WORK_TARGET;
        }

        long newTargetCompact = BitcoinJUtils.encodeCompactBits(newTarget);
        return BitcoinJUtils.decodeCompactBits(newTargetCompact);
    }
}
