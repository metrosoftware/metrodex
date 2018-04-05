package nxt;

import nxt.http.APICall;
import nxt.http.SubmitBlockSolution;
import nxt.util.Convert;
import nxt.util.Logger;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import static nxt.Consensus.GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS;

public class AccountTest extends BlockchainTest {
    @Test
    public void testGuaranteedBalanceBeforeFirstKeyBlock() {
        generateBlocks(2);
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountNQT", 100 * Constants.ONE_NXT).
                param("feeNQT", Constants.ONE_NXT).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlocks(2);
        Account bob = Account.getAccount(BOB.getId());
        Assert.assertEquals(1000000, bob.getEffectiveBalanceNXT());
        Assert.assertEquals(100010000000000l, bob.getBalanceNQT());
    }

    @Test
    public void testGuaranteedBalanceInThe2ndCluster() throws NxtException {
        generateBlocks(3);
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountNQT", 100 * Constants.ONE_NXT).
                param("feeNQT", Constants.ONE_NXT).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlocks(2);
        String keyBlockHeader = SubmitBlockSolution.generateHeaderFromTemplate(BlockImpl.getHeaderSize(true, false));
        Block block1 = Nxt.getBlockchain().composeKeyBlock(Convert.parseHexString(keyBlockHeader), ALICE.getPublicKey());
        Nxt.getBlockchainProcessor().processMinerBlock(block1, null);
        generateBlocks(4);
        Account bob = Account.getAccount(BOB.getId());
        Assert.assertEquals(1000000, bob.getEffectiveBalanceNXT());
    }

    @Test
    public void testGuaranteedBalanceInThe30thCluster() throws NxtException {
        generateBlocks(1);
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountNQT", 100 * Constants.ONE_NXT).
                param("feeNQT", Constants.ONE_NXT).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            generateBlocks(1);
            String keyBlockHeader = SubmitBlockSolution.generateHeaderFromTemplate(BlockImpl.getHeaderSize(true, false));
            Block block1 = Nxt.getBlockchain().composeKeyBlock(Convert.parseHexString(keyBlockHeader), ALICE.getPublicKey());
            Nxt.getBlockchainProcessor().processMinerBlock(block1, null);
        }
        generateBlocks(1);
        Account bob = Account.getAccount(BOB.getId());
        Assert.assertEquals(1000100, bob.getEffectiveBalanceNXT());
    }

    @Test
    public void testEmptyClusters() throws NxtException {
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountNQT", 100 * Constants.ONE_NXT).
                param("feeNQT", Constants.ONE_NXT).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlocks(2);
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            String keyBlockHeader = SubmitBlockSolution.generateHeaderFromTemplate(BlockImpl.getHeaderSize(true, false));
            Block block1 = Nxt.getBlockchain().composeKeyBlock(Convert.parseHexString(keyBlockHeader), ALICE.getPublicKey());
            Nxt.getBlockchainProcessor().processMinerBlock(block1, null);
        }
        Account bob = Account.getAccount(BOB.getId());
        Assert.assertEquals(1000100, bob.getEffectiveBalanceNXT());
    }

}
