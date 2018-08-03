package metro;

import metro.crypto.Crypto;
import metro.http.APICall;
import metro.util.Convert;
import metro.util.Logger;
import metro.util.Time;
import org.apache.commons.lang3.tuple.Pair;
import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Properties;

import static metro.BlockchainTest.generateBlockBy;
import static metro.BlockchainTest.mineBlock;

public class GenesisTimeLockTest extends AbstractForgingTest {

    protected static int baseHeight;

    protected static String forgerSecretPhrase = "aSykrgKGZNlSVOMDxkZZgbTvQqJPGtsBggb";

    protected static boolean isMetroInitialized = false;

    public static void initMetro() {
        if (!isMetroInitialized) {
            Properties properties = AbstractForgingTest.newTestProperties();
            properties.setProperty("metro.isTestnet", "true");
            properties.setProperty("metro.isOffline", "true");
            properties.setProperty("metro.enableFakeForging", "true");
            properties.setProperty("metro.testnetMaxWorkTarget", "1f00ffff");
            properties.setProperty("metro.testnetGuaranteedBalanceKeyblockConfirmations", "10");
            properties.setProperty("metro.testnetCoinbaseMaturityPeriodInKeyblocks", "2");
            properties.setProperty("metro.mine.publicKey", Convert.toHexString(Crypto.getPublicKey(chuckSecretPhrase)));
            properties.setProperty("metro.timeMultiplier", "1");
            properties.setProperty("metro.testnetGuaranteedBalanceConfirmations", "1");
            properties.setProperty("metro.testnetLeasingDelay", "1");
            properties.setProperty("metro.disableProcessTransactionsThread", "true");
            properties.setProperty("metro.deleteFinishedShufflings", "false");
            properties.setProperty("metro.disableSecurityPolicy", "true");
            properties.setProperty("metro.disableAdminPassword", "true");
            properties.setProperty("metro.testnetGenesisBalancesTimeLock", "true");
            AbstractForgingTest.init(properties);
            isMetroInitialized = true;
        }
    }

    @BeforeClass
    public static void init() {
        initMetro();
        Metro.setTime(new Time.CounterTime(Metro.getEpochTime()));
        baseHeight = blockchain.getHeight();
        Logger.logMessage("baseHeight: " + baseHeight);
    }

    @After
    public void destroy() {
        TransactionProcessorImpl.getInstance().clearUnconfirmedTransactions();
        blockchainProcessor.popOffTo(baseHeight);
        Metro.getBlockchain().forgetLastKeyBlock();
    }

    @Test
    public void sendBeforeKeyBlockFailTest() throws MetroException {
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 100 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlock();
        Assert.assertEquals(0, BOB.getBalanceDiff());
        Assert.assertEquals(0, ALICE.getBalanceDiff());
    }

    @Test
    public void sendAfterKeyBlock() throws MetroException {
        mineBlock();
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 5 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);

        generateBlock();
        Assert.assertEquals(0, BOB.getBalanceDiff());
        Assert.assertEquals(0, ALICE.getBalanceDiff());

        response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 3 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlock();
        Assert.assertEquals(3 * Constants.ONE_MTR, BOB.getBalanceDiff());
        Assert.assertEquals((-1 - 3) * Constants.ONE_MTR, ALICE.getBalanceDiff());
    }

    @Test
    public void testLesseeBalanceVariationsWithExpiration() throws MetroException {
        // Esau tries to lease 10000MTR to Alice, but doesn't have enough readily available funds to pay 1MTR commission
        JSONObject response = new APICall.Builder("leaseBalance").
                param("secretPhrase", ESAU.getSecretPhrase()).
                param("recipient", ALICE.getStrId()).
                param("period", 15).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("leaseBalance: " + response);
        generateBlockBy(ESAU);
        Assert.assertNotNull(mineBlock());
        List<Pair<String, Integer>> forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        // he is forger, since he still has unleased min amount of 10000MTR:
        Assert.assertEquals(1, forgers.size());
        // Bob helps him with 2MTR
        response = new APICall.Builder("sendMoney").
                param("secretPhrase", BOB.getSecretPhrase()).
                param("recipient", ESAU.getStrId()).
                param("amountMQT", 2 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlockBy(ESAU);
        response = new APICall.Builder("leaseBalance").
                param("secretPhrase", ESAU.getSecretPhrase()).
                param("recipient", ALICE.getStrId()).
                param("period", 15).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("leaseBalance: " + response);
        generateBlockBy(ESAU);
        // balance is now leased to Alice, but she hasn't forged yet; Esau doesn't have 10000 anymore
        Assert.assertNotNull(mineBlock());
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(0, forgers.size());
        Assert.assertEquals(10000, ESAU.getAccount().getEffectiveBalanceMTR());
        Assert.assertEquals(1000100000000l, ESAU.getAccount().getGuaranteedBalanceMQT());
        // 1.1MTR?
        Assert.assertEquals(110000000, ESAU.getAccount().getUnconfirmedBalanceMQT());
    }

    public static void generateBlock() {
        try {
            blockchainProcessor.generateBlock(forgerSecretPhrase, Metro.getEpochTime());
        } catch (BlockchainProcessor.BlockNotAcceptedException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

}
