package metro;

import metro.http.APICall;
import metro.util.Logger;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;

import static metro.Consensus.GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS;

public class AccountTest extends BlockchainTest {

    @Test
    public void testFreshlyMinedCannotBeSpent() throws MetroException {
        Assert.assertNotNull(mineBlock());
        generateBlock();
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 1001000 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        // double spending error expected, transaction rejected
        Assert.assertEquals("Not enough funds", response.get("errorDescription"));
        Assert.assertEquals(6L, response.get("errorCode"));
        // block is forged successfully
        blockchainProcessor.generateBlock(forgerSecretPhrase, Metro.getEpochTime());

        // all balance should still be there since we lacked available balance for the transfer:
        Account alice = Account.getAccount(ALICE.getFullId());
        Assert.assertEquals(100200000000000l, alice.getBalanceMQT());
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            Assert.assertNotNull(mineBlock());
        }

        // TODO should it really appear in guaranteed balance immediately - once we have 30 clusters?
        // since forgers' coinbases are time-locked as well, it's probably OK to forge with the freshly mined stake...
        alice = Account.getAccount(ALICE.getFullId());
        Assert.assertEquals(1004000, alice.getEffectiveBalanceMTR());
        Assert.assertEquals(102200000000000L, alice.getBalanceMQT());
        // 2000*(11-2) of them can be spent now (2 key blocks ago)
        Assert.assertEquals(101800000000000L, alice.getUnconfirmedBalanceMQT());
    }

    /**
     * This test and following 3 were added when (due to some bug now fixed) spending unavailable funds resulted in rollback.
     * Disabling them until such situation occurs again.
     *
     * @throws MetroException
     */
    @Ignore
    @Test
    public void testRollbackToKeyBlockAndResumeMining() throws MetroException {
        Assert.assertNotNull(mineBlock());
        Assert.assertNotNull(mineBlock());
        Assert.assertNotNull(mineBlock());
        generateBlock();
        // Alice tries to spend a time-locked block subsidy
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 900000 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        try {
            blockchainProcessor.generateBlock(forgerSecretPhrase, Metro.getEpochTime());
        } catch (BlockchainProcessor.BlockNotAcceptedException e) {
            e.printStackTrace();
        }
        Assert.assertNotNull(mineBlock());
    }

    @Ignore
    @Test
    public void testRollbackToKeyBlockAndResumeMining2() throws MetroException {
        generateBlock();
        Assert.assertNotNull(mineBlock());
        // Alice tries to spend a time-locked block subsidy
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 1002000 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        System.out.println("blockId_1=" + Metro.getBlockchain().getLastBlock().getId());
        System.out.println("blockCount_1=" + BlockDb.blockCount());
        try {
            blockchainProcessor.generateBlock(forgerSecretPhrase, Metro.getEpochTime());
        } catch (BlockchainProcessor.BlockNotAcceptedException e) {
            e.printStackTrace();
        }
        System.out.println("blockId_3=" + Metro.getBlockchain().getLastBlock().getId());
        System.out.println("blockCount_3=" + BlockDb.blockCount());
        Assert.assertNotNull(mineBlock());
    }

    @Ignore
    @Test
    public void testNonRollbackToKeyBlockAndResumeMining() throws MetroException {
        Assert.assertNotNull(mineBlock());
        // Alice tries to spend a time-locked block subsidy
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 1003000 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        try {
            blockchainProcessor.generateBlock(forgerSecretPhrase, Metro.getEpochTime());
        } catch (BlockchainProcessor.BlockNotAcceptedException e) {
            e.printStackTrace();
        }
        Assert.assertNotNull(mineBlock());
    }

    @Ignore
    @Test
    public void testNonRollbackToGenesisAndStartMining() throws MetroException {
        // Alice tries to spend too much
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 1001000 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        try {
            blockchainProcessor.generateBlock(forgerSecretPhrase, Metro.getEpochTime());
        } catch (BlockchainProcessor.BlockNotAcceptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testEffectiveBalanceBeforeFirstKeyBlock() {
        generateBlocks(2);
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 100 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlocks(2);
        Account bob = Account.getAccount(BOB.getFullId());
        Assert.assertEquals(1000000, bob.getEffectiveBalanceMTR());
        Assert.assertEquals(100010000000000L, bob.getBalanceMQT());
    }

    @Test
    public void testEffectiveBalanceInThe2ndCluster() throws MetroException {
        generateBlocks(3);
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 100 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlocks(2);
        Assert.assertNotNull(mineBlock());
        generateBlocks(4);
        Account bob = Account.getAccount(BOB.getFullId());
        Assert.assertEquals(1000000, bob.getEffectiveBalanceMTR());
    }

    /**
     * NB: We reduced constant GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS from 30 to 10 for these tests
     *
     * @throws MetroException
     */
    @Test
    public void testEffectiveBalanceInThe11thCluster() throws MetroException {
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 100 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            generateBlocks(3);
            Assert.assertNotNull(mineBlock());
        }
        generateBlocks(3);
        Account bob = Account.getAccount(BOB.getFullId());
        Assert.assertEquals(1000100, bob.getEffectiveBalanceMTR());
    }

    @Test
    public void testEmptyClusters() throws MetroException {
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 100 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlocks(2);
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            Assert.assertNotNull(mineBlock());
        }
        Account bob = Account.getAccount(BOB.getFullId());
        Assert.assertEquals(1000100, bob.getEffectiveBalanceMTR());
    }

    @Test
    public void testLessorsContribution() throws MetroException {
        Account dave = Account.getAccount(DAVE.getFullId());
        Assert.assertEquals(1000000, dave.getEffectiveBalanceMTR());
        JSONObject response = new APICall.Builder("leaseBalance").
                param("secretPhrase", DAVE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("period", Integer.toString(GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS - 1, 10)).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("leaseBalance: " + response);
        Account chuck = Account.getAccount(CHUCK.getFullId());
        Assert.assertEquals(1000000, chuck.getEffectiveBalanceMTR());
        response = new APICall.Builder("leaseBalance").
                param("secretPhrase", CHUCK.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("period", Integer.toString(GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS + 1, 10)).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("leaseBalance: " + response);
        generateBlock();
        Account bob = Account.getAccount(BOB.getFullId());
        Assert.assertEquals(1000000, bob.getEffectiveBalanceMTR());
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            Assert.assertNotNull(mineBlock());
        }
        bob = Account.getAccount(BOB.getFullId());
        Assert.assertEquals(1999999, bob.getEffectiveBalanceMTR());
        // Chuck's lease has expired
        generateBlocks(2);
        Assert.assertEquals(1000000, bob.getEffectiveBalanceMTR());
    }

    @Test
    public void testRescan() throws MetroException {
        // TODO #207 more complex testing of forgers Merkle
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 100 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlocks(2);
        Block lastMined = null;
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            lastMined = mineBlock();
            Assert.assertNotNull(lastMined);
        }
        byte[] forgersMerkle1 = lastMined.getForgersMerkleRoot();
        generateBlockBy(ALICE);
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            lastMined = mineBlock();
            Assert.assertNotNull(lastMined);
        }
        Assert.assertFalse("forgersMerkle must change (from the initial one) after the 3rd key block", Arrays.equals(forgersMerkle1, lastMined.getForgersMerkleRoot()));
        Account bob = Account.getAccount(BOB.getFullId());
        Assert.assertEquals(1000100, bob.getEffectiveBalanceMTR());
        blockchainProcessor.scan(0, true);
        Assert.assertEquals("scan failed to preserve block records", 23, blockchain.getHeight());
        blockchainProcessor.scan(11, true);
        Assert.assertEquals("scan #2 failed to preserve block records", 23, blockchain.getHeight());
    }
}
