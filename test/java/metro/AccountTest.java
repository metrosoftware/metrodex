package metro;

import metro.http.APICall;
import metro.util.Logger;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

import static metro.Consensus.GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS;

public class AccountTest extends BlockchainTest {

    @Test
    public void testFreshlyMinedCannotBeSpent() throws MetroException {
//        Assert.assertEquals(1000000, alice.getEffectiveBalanceMTR());
        Assert.assertNotNull(mineBlock());
        generateBlock();
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 1001000 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        // double spending error expected
        // TODO API should have not accepted this tx
        try {
            blockchainProcessor.generateBlock(forgerSecretPhrase, Metro.getEpochTime());
        } catch (BlockchainProcessor.BlockNotAcceptedException e) {
            e.printStackTrace();
        }

        // all balance should still be there since we lacked available balance for the transfer:
        Account alice = Account.getAccount(ALICE.getId());
        Assert.assertEquals(100200000000000l, alice.getBalanceMQT());
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            Assert.assertNotNull(mineBlock());
        }

        // TODO should it really appear in guaranteed balance immediately - once we have 30 clusters?
        // since forgers' coinbases are time-locked as well, it's probably OK to forge with the freshly mined stake...
        alice = Account.getAccount(ALICE.getId());
        Assert.assertEquals(1022000, alice.getEffectiveBalanceMTR());
        Assert.assertEquals(102200000000000l, alice.getBalanceMQT());
        // 2000*(11-2) of them can be spent now (2 key blocks ago)
        Assert.assertEquals(101799999999995l, alice.getUnconfirmedBalanceMQT());
    }

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
        Account bob = Account.getAccount(BOB.getId());
        Assert.assertEquals(1000000, bob.getEffectiveBalanceMTR());
        Assert.assertEquals(100010000000000l, bob.getBalanceMQT());
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
        Account bob = Account.getAccount(BOB.getId());
        Assert.assertEquals(1000000, bob.getEffectiveBalanceMTR());
    }

    @Test
    public void testEffectiveBalanceInThe31stCluster() throws MetroException {
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
        Account bob = Account.getAccount(BOB.getId());
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
        Account bob = Account.getAccount(BOB.getId());
        Assert.assertEquals(1000100, bob.getEffectiveBalanceMTR());
    }

    @Test
    public void testLessorsContribution() throws MetroException {
        JSONObject response = new APICall.Builder("leaseBalance").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("period", "2").
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("leaseBalance: " + response);
        response = new APICall.Builder("leaseBalance").
                param("secretPhrase", CHUCK.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("period", "3").
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("leaseBalance: " + response);
        generateBlock();
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            Assert.assertNotNull(mineBlock());
        }
        Account bob = Account.getAccount(BOB.getId());
        // TODO why Actual 1000000 now?
        Assert.assertEquals(1999999, bob.getEffectiveBalanceMTR());
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
        Account bob = Account.getAccount(BOB.getId());
        Assert.assertEquals(1000100, bob.getEffectiveBalanceMTR());
        blockchainProcessor.scan(0, true);
        Assert.assertEquals("scan failed to preserve block records", 23, blockchain.getHeight());
        blockchainProcessor.scan(11, true);
        Assert.assertEquals("scan #2 failed to preserve block records", 23, blockchain.getHeight());
    }
}
