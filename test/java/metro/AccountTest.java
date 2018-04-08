package metro;

import metro.http.APICall;
import metro.util.Logger;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import static metro.Consensus.GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS;

public class AccountTest extends BlockchainTest {
    @Test
    public void testGuaranteedBalanceBeforeFirstKeyBlock() {
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
    public void testGuaranteedBalanceInThe2ndCluster() throws MetroException {
        generateBlocks(3);
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 100 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlocks(2);
        //String keyBlockHeader = SubmitBlockSolution.generateHeaderFromTemplate(BlockImpl.getHeaderSize(true, false));
        //Block block1 = Metro.getBlockchain().composeKeyBlock(Convert.parseHexString(keyBlockHeader), ALICE.getPublicKey());
        //Metro.getBlockchainProcessor().processMinerBlock(block1, null);
        generateBlocks(4);
        Account bob = Account.getAccount(BOB.getId());
        Assert.assertEquals(1000000, bob.getEffectiveBalanceMTR());
    }

    @Test
    public void testGuaranteedBalanceInThe30thCluster() throws MetroException {
        generateBlocks(1);
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 100 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            generateBlocks(1);
            //String keyBlockHeader = SubmitBlockSolution.generateHeaderFromTemplate(BlockImpl.getHeaderSize(true, false));
            //Block block1 = Metro.getBlockchain().composeKeyBlock(Convert.parseHexString(keyBlockHeader), ALICE.getPublicKey());
            //Metro.getBlockchainProcessor().processMinerBlock(block1, null);
        }
        generateBlocks(1);
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
            //String keyBlockHeader = SubmitBlockSolution.generateHeaderFromTemplate(BlockImpl.getHeaderSize(true, false));
            //Block block1 = Metro.getBlockchain().composeKeyBlock(Convert.parseHexString(keyBlockHeader), ALICE.getPublicKey());
            //Metro.getBlockchainProcessor().processMinerBlock(block1, null);
        }
        Account bob = Account.getAccount(BOB.getId());
        Assert.assertEquals(1000100, bob.getEffectiveBalanceMTR());
    }

}
