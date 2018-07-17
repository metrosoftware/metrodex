package metro;

import metro.crypto.Crypto;
import metro.db.DbUtils;
import metro.util.Convert;
import metro.util.Logger;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static metro.Db.db;
import static metro.util.Convert.HASH_SIZE;

public class SelectAccountTest extends BlockchainTest {

    private String startBytes ="abcdef00abcdef";
    private byte[] bytes = Crypto.sha3().digest(Convert.parseHexString(startBytes));

    private byte[] nextBytes() {
        bytes = Crypto.sha3().digest(bytes);
        return bytes;
    }

    int BLOCKS_COUNT = 2500000;

    @Test
    public void testSelect() {
        try (Connection con = db.getConnection())
        {
            int GENERATORS = 50;
            int ACCOUNTS = 200000;
            int ACCOUNT_RECORDS = 1500000;

            for (int j = 0; j < ACCOUNT_RECORDS; j++) {
                try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO account (id, id2, balance, unconfirmed_balance, has_control_phasing," +
                        " forged_balance, height, active_lessee_id, latest, last_forged_height) VALUES (?, ?, ?, ?, FALSE, ?, ?, ?, ?, ?)")) {
                    int i = 0;
                    pstmt.setLong(++i, j % ACCOUNTS);
                    pstmt.setInt(++i, j % ACCOUNTS);
                    pstmt.setLong(++i, j);
                    pstmt.setLong(++i, j);
                    pstmt.setLong(++i, j);
                    pstmt.setInt(++i, BLOCKS_COUNT - ACCOUNT_RECORDS + j);
                    DbUtils.setLong(pstmt, ++i, (j % ACCOUNTS) % 2 == 0 ? null : new Long(j % ACCOUNTS - 1));
                    pstmt.setBoolean(++i, j > ACCOUNT_RECORDS - ACCOUNTS ? true : false);
                    pstmt.setInt(++i, j % ACCOUNTS < GENERATORS ? BLOCKS_COUNT - ACCOUNT_RECORDS + j : 0);
                    pstmt.execute();
                }

                try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO public_key (account_id, " +
                        "public_key, height, latest) VALUES (?, ?, ?, TRUE)")) {
                    int i = 0;
                    pstmt.setLong(++i, j);
                    pstmt.setInt(++i, j);
                    pstmt.setLong(++i, 0);
                    pstmt.execute();
                }
            }

            for (int j = 0; j < 50000; j++) {
                try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO account_guaranteed_balance (account_id, additions, height, coinbase) "
                        + " VALUES (?, ?, ?, ?)")) {
                    int i = 0;
                    pstmt.setLong(++i, j % 100);
                    pstmt.setLong(++i, j % 100);
                    pstmt.setInt(++i, j + BLOCKS_COUNT - 50000);
                    pstmt.setBoolean(++i, j% 10 == 9 ? true : false);
                    pstmt.execute();
                }
            }

            long start = System.currentTimeMillis();
            getCurrentForgersMerkleBranches(1);
            System.out.println("time:" + (System.currentTimeMillis() - start));
        } catch (SQLException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    public byte[] getCurrentForgersMerkleBranches(int height) {
        Metro.getBlockchain().readLock();
        byte[] forgersMerkle;
        try {
            List<Pair<String, Long>> generators = new ArrayList<>();
            try (Connection con = Db.db.getConnection();
                 PreparedStatement pstmt = con.prepareStatement(BlockchainProcessorImpl.SELECT_FORGERS_SQL)) {
                BlockImpl lastKeyBlock = BlockDb.findLastKeyBlock(height);
                pstmt.setInt(1, BLOCKS_COUNT - 10000);
                pstmt.setInt(2, BLOCKS_COUNT);
                pstmt.setInt(3, BLOCKS_COUNT- 500000);
                pstmt.setInt(4, BLOCKS_COUNT);
                Logger.logDebugMessage("Starting getBlockGenerators");
                try (ResultSet rs = pstmt.executeQuery()) {
                    Logger.logDebugMessage("SELECT getBlockGenerators complete, iterating");
                    while (rs.next()) {
                        long amount = rs.getLong("effective");
                        if (amount > 0) {
                            generators.add(new ImmutablePair<>(Convert.toHexString(rs.getBytes("public_key")), amount));
                        }
                    }
                }
                Logger.logDebugMessage("Finishing getBlockGenerators");
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
            // TODO get public keys
            byte[] forgersMerkleVotersBranch = new byte[0];
            // TODO #211
            byte[] forgersMerkleOutfeedersBranch = Convert.EMPTY_HASH;
            forgersMerkle = new byte[HASH_SIZE * 2];
            if (forgersMerkleVotersBranch.length > 0) {
                System.arraycopy(forgersMerkleVotersBranch, 0, forgersMerkle, 0, HASH_SIZE);
                System.arraycopy(forgersMerkleOutfeedersBranch, 0, forgersMerkle, HASH_SIZE, HASH_SIZE);
            }
            System.out.println(generators.size());
            return forgersMerkle;
        } finally {
            Metro.getBlockchain().readUnlock();
        }
    }
}
