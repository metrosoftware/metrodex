package metro;

import metro.db.DbUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UncleDb {

    static Uncle loadUncle(Connection con, ResultSet rs) {
        try {
            short version = rs.getShort("version");
            long timestamp = rs.getLong("timestamp");
            long previousBlockId = rs.getLong("previous_block_id");
            long previousKeyBlockId = rs.getLong("previous_key_block_id");
            long nonce = rs.getLong("nonce");
            long generatorId = rs.getLong("generator_id");
            byte[] previousBlockHash = rs.getBytes("previous_block_hash");
            byte[] previousKeyBlockHash = rs.getBytes("previous_key_block_hash");
            byte[] forgersMerkleRoot = rs.getBytes("forgers_merkle_root");
            long baseTarget = rs.getLong("base_target");
            int height = rs.getInt("height");
            int localHeight = rs.getInt("local_height");
            byte[] blockSignature = rs.getBytes("block_signature");
            byte[] txMerkleRoot = rs.getBytes("tx_merkle_root");
            long id = rs.getLong("id");

            long uncleMerkleId = rs.getLong("uncle_merkle_id");
            short clusterSize = rs.getShort("cluster_size");

            return new Uncle(version, timestamp, previousBlockId, previousKeyBlockId, nonce, txMerkleRoot,
                    generatorId, blockSignature, previousBlockHash, previousKeyBlockHash, forgersMerkleRoot,
                    baseTarget, height, localHeight, id,
                    uncleMerkleId, clusterSize);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static void saveUncle(Connection con, Uncle uncle) {
        try {
            try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO block (id, version, timestamp, previous_block_id, previous_key_block_id, nonce, "
                    + "previous_key_block_hash, forgers_merkle_root, previous_block_hash, base_target, "
                    + "height, local_height, block_signature, tx_merkle_root, generator_id) "
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, uncle.getId());
                pstmt.setShort(++i, uncle.getVersion());
                pstmt.setLong(++i, uncle.getTimestamp());
                DbUtils.setLongZeroToNull(pstmt, ++i, uncle.getPreviousBlockId());
                DbUtils.setLongZeroToNull(pstmt, ++i, uncle.getPreviousKeyBlockId());
                pstmt.setLong(++i, uncle.getNonce());
                DbUtils.setBytes(pstmt, ++i, uncle.getPreviousKeyBlockHash());
                DbUtils.setBytes(pstmt, ++i, uncle.getForgersMerkleRoot());
                pstmt.setBytes(++i, uncle.getPreviousBlockHash());
                pstmt.setLong(++i, uncle.getBaseTarget());
                pstmt.setInt(++i, uncle.getHeight());
                pstmt.setInt(++i, uncle.getLocalHeight());
                pstmt.setBytes(++i, uncle.getBlockSignature());
                pstmt.setBytes(++i, uncle.getTxMerkleRoot());

                pstmt.setLong(++i, uncle.getGeneratorId());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

}
