package metro;

import metro.db.DbUtils;
import metro.util.Convert;
import metro.util.Logger;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static metro.Db.db;

public class GeneratorLessorsTest extends BlockchainTest {

    private static int HEIGHT = 1;

    @Test
    public void testGeneratorLessorsTest() {

        try (Connection con = db.getConnection()) {

            for (int j = 0; j < 7; j++) {
                try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO account (id, id2, balance, unconfirmed_balance, has_control_phasing," +
                        " forged_balance, height, active_lessee_id, latest, last_forged_height) VALUES (?, ?, ?, ?, FALSE, ?, ?, ?, ?, ?)")) {
                    int i = 0;
                    pstmt.setLong(++i, j);
                    pstmt.setInt(++i, j);
                    pstmt.setLong(++i, 50 * (j + 1));
                    pstmt.setLong(++i, j);
                    pstmt.setLong(++i, 0);
                    pstmt.setInt(++i, 0);
                    Long a = j == 3 ? new Long(4): ((j < 2) ? new Long(2) : null);
                    DbUtils.setLong(pstmt, ++i, a);
                    pstmt.setBoolean(++i, true);
                    pstmt.setLong(++i, (j != 6) ? j+1 : 0);
                    pstmt.execute();
                }

                try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO public_key (account_id, " +
                        "public_key, height, latest) VALUES (?, ?, ?, TRUE)")) {
                    int i = 0;
                    pstmt.setLong(++i, j);
                    pstmt.setBytes(++i, new byte[] {});
                    pstmt.setLong(++i, 0);
                    pstmt.execute();
                }
            }

            for (int j = 0; j < 8; j++) {
                try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO account_guaranteed_balance (account_id, additions, height, coinbase) "
                        + " VALUES (?, ?, ?, ?)")) {
                    int i = 0;
                    pstmt.setLong(++i, j / 2);
                    pstmt.setLong(++i, 10  + j);
                    pstmt.setInt(++i, j % 2 + 1);
                    pstmt.setBoolean(++i, true);
                    pstmt.execute();
                }
            }

            List<Pair<String, Long>> generators = new ArrayList<>();
            try (PreparedStatement pstmt = con.prepareStatement(BlockchainProcessorImpl.SELECT_FORGERS_SQL)) {
                pstmt.setInt(1, 0);
                pstmt.setInt(2, 8);
                pstmt.setInt(3, 0);
                pstmt.setInt(4, 8);
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
            }
            System.out.println(generators.size());
            Assert.assertTrue(generators.size() == 3);
            Assert.assertEquals(225, generators.get(0).getRight().longValue());
            Assert.assertEquals(300, generators.get(1).getRight().longValue());
            Assert.assertEquals(417, generators.get(2).getRight().longValue());
        } catch (SQLException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
}
