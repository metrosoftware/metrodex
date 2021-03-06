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

import metro.db.DbUtils;
import metro.util.Logger;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import static metro.Consensus.GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS;

final class BlockDb {

    /** Block cache */
    static final int POS_BLOCK_CACHE_SIZE = 10;
    static final int BLOCK_CACHE_TOTAL_SIZE = POS_BLOCK_CACHE_SIZE + GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS;
    static final Map<Long, BlockImpl> blockCache = new HashMap<>();
    static final SortedMap<Integer, BlockImpl> heightMap = new TreeMap<>();
    static final SortedMap<Integer, BlockImpl> posLocalHeightMap = new TreeMap<>();
    static final SortedMap<Integer, BlockImpl> keyLocalHeightMap = new TreeMap<>();
    static final Map<Long, TransactionImpl> transactionCache = new HashMap<>();
    static final Blockchain blockchain = Metro.getBlockchain();
    static {
        Metro.getBlockchainProcessor().addListener((block) -> {
            synchronized (blockCache) {
                int height = block.getHeight();
                int localHeight = block.getLocalHeight();
                Iterator<BlockImpl> it = blockCache.values().iterator();
                while (it.hasNext()) {
                    boolean remove = false;
                    Block cacheBlock = it.next();
                    if (cacheBlock.isKeyBlock()) {
                        int cacheKeyHeight = cacheBlock.getLocalHeight();
                        if (cacheKeyHeight <= localHeight - GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS || cacheKeyHeight >= localHeight) {
                            cacheBlock.getTransactions().forEach((tx) -> transactionCache.remove(tx.getId()));
                            heightMap.remove(cacheBlock.getHeight());
                            keyLocalHeightMap.remove(cacheKeyHeight);
                            remove = true;
                        }
                    } else {
                        int cachePOSHeight = cacheBlock.getLocalHeight();
                        if (cachePOSHeight <= localHeight - POS_BLOCK_CACHE_SIZE || cachePOSHeight >= localHeight) {
                            cacheBlock.getTransactions().forEach((tx) -> transactionCache.remove(tx.getId()));
                            heightMap.remove(cacheBlock.getHeight());
                            posLocalHeightMap.remove(cachePOSHeight);
                            remove = true;
                        }
                    }
                    if (remove) {
                        it.remove();
                    }
                }
                block.getTransactions().forEach((tx) -> transactionCache.put(tx.getId(), (TransactionImpl)tx));
                heightMap.put(height, (BlockImpl)block);
                if (block.isKeyBlock()) {
                    keyLocalHeightMap.put(localHeight, (BlockImpl) block);
                } else {
                    posLocalHeightMap.put(localHeight, (BlockImpl) block);
                }
                blockCache.put(block.getId(), (BlockImpl)block);
                if (blockCache.size() > BLOCK_CACHE_TOTAL_SIZE + 1) {
                    Logger.logErrorMessage("BLOCK CACHE OVERFLOW: size=" + blockCache.size());
                }
            }
        }, BlockchainProcessor.Event.BLOCK_PUSHED);
    }

    static private void clearBlockCache() {
        synchronized (blockCache) {
            blockCache.clear();
            heightMap.clear();
            keyLocalHeightMap.clear();
            posLocalHeightMap.clear();
            transactionCache.clear();
        }
    }

    static BlockImpl findBlock(long blockId) {
        // Check the block cache
        synchronized (blockCache) {
            BlockImpl block = blockCache.get(blockId);
            if (block != null) {
                return block;
            }
        }
        // Search the database
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE id = ?")) {
            pstmt.setLong(1, blockId);
            try (ResultSet rs = pstmt.executeQuery()) {
                BlockImpl block = null;
                if (rs.next()) {
                    block = loadBlock(con, rs);
                }
                return block;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static boolean hasBlock(long blockId) {
        return hasBlock(blockId, Integer.MAX_VALUE);
    }

    static boolean hasBlock(long blockId, int height) {
        // Check the block cache
        synchronized(blockCache) {
            BlockImpl block = blockCache.get(blockId);
            if (block != null) {
                return block.getHeight() <= height;
            }
        }
        // Search the database
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT height FROM block WHERE id = ? AND (next_block_id <> 0 OR next_block_id IS NULL)")) {
            pstmt.setLong(1, blockId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt("height") <= height;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static long findBlockIdAtHeight(int height) {
        // Check the cache
        synchronized(blockCache) {
            BlockImpl block = heightMap.get(height);
            if (block != null) {
                return block.getId();
            }
        }
        // Search the database
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT id FROM block WHERE height = ?")) {
            pstmt.setInt(1, height);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    throw new RuntimeException("Block at height " + height + " not found in database!");
                }
                return rs.getLong("id");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static BlockImpl findBlockAtHeight(int height) {
        // Check the cache
        synchronized(blockCache) {
            BlockImpl block = heightMap.get(height);
            if (block != null) {
                return block;
            }
        }
        // Search the database
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE height = ?")) {
            pstmt.setInt(1, height);
            try (ResultSet rs = pstmt.executeQuery()) {
                BlockImpl block;
                if (rs.next()) {
                    block = loadBlock(con, rs);
                } else {
                    throw new RuntimeException("Block at height " + height + " not found in database!");
                }
                return block;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static BlockImpl findBlockAtLocalHeight(int height, boolean isKeyBlock) {
        // Check the cache
        synchronized(blockCache) {
            BlockImpl block = isKeyBlock ? keyLocalHeightMap.get(height) : posLocalHeightMap.get(height);
            if (block != null) {
                return block;
            }
        }
        // Search the database
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE local_height = ? AND version " + (isKeyBlock ? "< 0" : "> 0"))) {
            pstmt.setInt(1, height);
            try (ResultSet rs = pstmt.executeQuery()) {
                BlockImpl block;
                if (rs.next()) {
                    block = loadBlock(con, rs);
                } else {
                    throw new RuntimeException("Block at local_height " + height + " and isKeyBlock=" + isKeyBlock + " not found in database!");
                }
                return block;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static BlockImpl findLastBlock() {
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE next_block_id <> 0 OR next_block_id IS NULL ORDER BY timestamp DESC LIMIT 1")) {
            BlockImpl block = null;
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    block = loadBlock(con, rs);
                }
            }
            return block;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static BlockImpl findLastKeyBlock(int height) {
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE version < 0 AND (next_block_id <> 0 OR next_block_id IS NULL) AND height <= ? ORDER BY height DESC LIMIT 1")) {
            pstmt.setInt(1, height);
            BlockImpl block = null;
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    block = loadBlock(con, rs);
                }
            }
            return block;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static BlockImpl findLastPosBlock(int height) {
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE version >= 0 AND (next_block_id <> 0 OR next_block_id IS NULL) AND height <= ? ORDER BY height DESC LIMIT 1")) {
            pstmt.setInt(1, height);
            BlockImpl block = null;
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    block = loadBlock(con, rs);
                }
            }
            return block;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static BlockImpl findLastKeyBlock(long timestamp) {
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE version < 0 AND timestamp <= ? ORDER BY timestamp DESC LIMIT 1")) {
            pstmt.setLong(1, timestamp);
            BlockImpl block = null;
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    block = loadBlock(con, rs);
                }
            }
            return block;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static BlockImpl findLastBlock(long timestamp) {
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT * FROM block WHERE timestamp <= ? ORDER BY timestamp DESC LIMIT 1")) {
            pstmt.setLong(1, timestamp);
            BlockImpl block = null;
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    block = loadBlock(con, rs);
                }
            }
            return block;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static Set<Long> getBlockGenerators(int startHeight) {
        Set<Long> generators = new HashSet<>();
        try (Connection con = Db.db.getConnection();
                PreparedStatement pstmt = con.prepareStatement(
                        "SELECT generator_id, COUNT(generator_id) AS count FROM block WHERE height >= ? GROUP BY generator_id")) {
            pstmt.setInt(1, startHeight);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    if (rs.getInt("count") > 1) {
                        generators.add(rs.getLong("generator_id"));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return generators;
    }

    static BlockImpl loadBlock(Connection con, ResultSet rs) {
        return loadBlock(con, rs, false);
    }

    static BlockImpl loadBlock(Connection con, ResultSet rs, boolean loadTransactions) {
        try {
            short version = rs.getShort("version");
            long timestamp = rs.getLong("timestamp");
            long previousBlockId = rs.getLong("previous_block_id");
            Long previousKeyBlockId = rs.getLong("previous_key_block_id");
            if (previousKeyBlockId == 0 && rs.wasNull()) {
                previousKeyBlockId = null;
            }
            int nonce = rs.getInt("nonce");
            long totalAmountMQT = rs.getLong("total_amount");
            long rewardMQT = rs.getLong("reward");
            int payloadLength = rs.getInt("payload_length");
            long generatorId = rs.getLong("generator_id");
            byte[] previousBlockHash = rs.getBytes("previous_block_hash");
            byte[] forgersMerkleRoot = rs.getBytes("forgers_merkle_root");
            BigInteger cumulativeDifficulty = new BigInteger(rs.getBytes("cumulative_difficulty"));
            BigInteger stakeBatchDifficulty = new BigInteger(rs.getBytes("stake_batch_difficulty"));
            long baseTarget = rs.getLong("base_target");
            long nextBlockId = rs.getLong("next_block_id");
            if (nextBlockId == 0 && !rs.wasNull()) {
                throw new IllegalStateException("Attempting to load invalid block");
            }
            int height = rs.getInt("height");
            int localHeight = rs.getInt("local_height");
            byte[] generationSequence = rs.getBytes("generation_sequence");
            byte[] blockSignature = rs.getBytes("block_signature");
            byte[] txMerkleRoot = rs.getBytes("tx_merkle_root");
            long id = rs.getLong("id");
            return new BlockImpl(version, timestamp, previousBlockId, previousKeyBlockId, nonce, totalAmountMQT, rewardMQT, payloadLength, txMerkleRoot,
                    generatorId, generationSequence, blockSignature, previousBlockHash, forgersMerkleRoot,
                    cumulativeDifficulty, stakeBatchDifficulty, baseTarget, nextBlockId, height, localHeight, id, loadTransactions ? TransactionDb.findBlockTransactions(con, id) : null);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static void saveBlock(Connection con, BlockImpl block) {
        try {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO block (id, version, timestamp, previous_block_id, previous_key_block_id, nonce, forgers_merkle_root, "
                    + "total_amount, reward, payload_length, previous_block_hash, next_block_id, cumulative_difficulty, stake_batch_difficulty, base_target, "
                    + "height, local_height, generation_sequence, block_signature, tx_merkle_root, generator_id) "
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, block.getId());
                pstmt.setShort(++i, block.getVersion());
                pstmt.setLong(++i, block.getTimestamp());
                DbUtils.setLongZeroToNull(pstmt, ++i, block.getPreviousBlockId());
                DbUtils.setLong(pstmt, ++i, block.getPreviousKeyBlockId());
                if (block.isKeyBlock()) {
                    pstmt.setLong(++i, block.getNonce());
                } else {
                    pstmt.setNull(++i, Types.BIGINT);
                }
                DbUtils.setBytes(pstmt, ++i, block.getForgersMerkleBranches());
                pstmt.setLong(++i, block.getTotalAmountMQT());
                pstmt.setLong(++i, block.getRewardMQT());
                pstmt.setInt(++i, block.getPayloadLength());
                pstmt.setBytes(++i, block.getPreviousBlockHash());
                pstmt.setLong(++i, 0L); // next_block_id set to 0 at first
                pstmt.setBytes(++i, block.getCumulativeDifficulty().toByteArray());
                pstmt.setBytes(++i, block.getStakeBatchDifficulty().toByteArray());
                pstmt.setLong(++i, block.getBaseTarget());
                pstmt.setInt(++i, block.getHeight());
                pstmt.setInt(++i, block.getLocalHeight());
                pstmt.setBytes(++i, block.getGenerationSequence());
                pstmt.setBytes(++i, block.getBlockSignature());
                pstmt.setBytes(++i, block.getTxMerkleRoot());

                pstmt.setLong(++i, block.getGeneratorId());
                pstmt.executeUpdate();
                TransactionDb.saveTransactions(con, block.getTransactions());
            }
            if (block.getPreviousBlockId() != 0) {
                try (PreparedStatement pstmt = con.prepareStatement("UPDATE block SET next_block_id = ? WHERE id = ?")) {
                    pstmt.setLong(1, block.getId());
                    pstmt.setLong(2, block.getPreviousBlockId());
                    pstmt.executeUpdate();
                }
                BlockImpl previousBlock;
                synchronized (blockCache) {
                    previousBlock = blockCache.get(block.getPreviousBlockId());
                }
                if (previousBlock != null) {
                    previousBlock.setNextBlockId(block.getId());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    //set next_block_id to null instead of 0 to indicate successful block push
    static void commit(Block block) {
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("UPDATE block SET next_block_id = NULL WHERE id = ?")) {
            pstmt.setLong(1, block.getId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static void deleteBlocksFromHeight(int height) {
        long blockId;
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT id FROM block WHERE height = ?")) {
            pstmt.setInt(1, height);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return;
                }
                blockId = rs.getLong("id");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        Logger.logDebugMessage("Deleting blocks starting from height %s", height);
        BlockDb.deleteBlocksFrom(blockId);
    }

    // relying on cascade triggers in the database to delete the transactions and public keys for all deleted blocks
    static BlockImpl deleteBlocksFrom(long blockId) {
        if (!Db.db.isInTransaction()) {
            BlockImpl lastBlock;
            try {
                Db.db.beginTransaction();
                lastBlock = deleteBlocksFrom(blockId);
                Db.db.commitTransaction();
            } catch (Exception e) {
                Db.db.rollbackTransaction();
                throw e;
            } finally {
                Db.db.endTransaction();
            }
            return lastBlock;
        }
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmtSelect = con.prepareStatement("SELECT db_id FROM block WHERE timestamp >= "
                     + "IFNULL ((SELECT timestamp FROM block WHERE id = ?), " + Long.MAX_VALUE + ") ORDER BY timestamp DESC");
             PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM block WHERE db_id = ?")) {
            try {
                pstmtSelect.setLong(1, blockId);
                try (ResultSet rs = pstmtSelect.executeQuery()) {
                    Db.db.commitTransaction();
                    while (rs.next()) {
        	            pstmtDelete.setLong(1, rs.getLong("db_id"));
            	        pstmtDelete.executeUpdate();
                        Db.db.commitTransaction();
                    }
	            }
                BlockImpl lastBlock = findLastBlock();
                lastBlock.setNextBlockId(0);
                try (PreparedStatement pstmt = con.prepareStatement("UPDATE block SET next_block_id = NULL WHERE id = ?")) {
                    pstmt.setLong(1, lastBlock.getId());
                    pstmt.executeUpdate();
                }
                Db.db.commitTransaction();
                return lastBlock;
            } catch (SQLException e) {
                Db.db.rollbackTransaction();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } finally {
            clearBlockCache();
        }
    }

    static void deleteAll() {
        if (!Db.db.isInTransaction()) {
            try {
                Db.db.beginTransaction();
                deleteAll();
                Db.db.commitTransaction();
            } catch (Exception e) {
                Db.db.rollbackTransaction();
                throw e;
            } finally {
                Db.db.endTransaction();
            }
            return;
        }
        Logger.logMessage("Deleting blockchain...");
        try (Connection con = Db.db.getConnection();
             Statement stmt = con.createStatement()) {
            try {
                stmt.executeUpdate("SET REFERENTIAL_INTEGRITY FALSE");
                stmt.executeUpdate("TRUNCATE TABLE transaction");
                stmt.executeUpdate("TRUNCATE TABLE block");
                BlockchainProcessorImpl.getInstance().getDerivedTables().forEach(table -> {
                    try {
                        stmt.executeUpdate("TRUNCATE TABLE " + table.toString());
                    } catch (SQLException ignore) {}
                });
                stmt.executeUpdate("SET REFERENTIAL_INTEGRITY TRUE");
                Db.db.commitTransaction();
            } catch (SQLException e) {
                Db.db.rollbackTransaction();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } finally {
            clearBlockCache();
        }
    }

}
