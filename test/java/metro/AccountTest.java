package metro;

import metro.http.APICall;
import metro.util.Logger;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import static metro.Consensus.GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS;

public class AccountTest extends BlockchainTest {
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
        Assert.assertEquals(1999999, bob.getEffectiveBalanceMTR());
    }
}
