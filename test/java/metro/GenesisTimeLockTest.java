package metro;

import metro.crypto.Crypto;
import metro.http.APICall;
import metro.util.Convert;
import metro.util.Logger;
import metro.util.Time;
import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static metro.BlockchainTest.mineBlock;

public class GenesisTimeLockTest extends AbstractBlockchainTest {

    protected static Tester FORGY;
    protected static Tester ALICE;
    protected static Tester BOB;
    protected static Tester CHUCK;
    protected static Tester DAVE;

    protected static int baseHeight;

    protected static String forgerSecretPhrase = "aSykrgKGZNlSVOMDxkZZgbTvQqJPGtsBggb";
    protected static final List<String> forgerAccountIds = Arrays.asList("MTR-9KZM-KNYY-QBXZ-5TD8V","MTR-XK4R-7VJU-6EQG-7R335");

    public static final String aliceSecretPhrase = "hope peace happen touch easy pretend worthless talk them indeed wheel state";
    private static final String bobSecretPhrase2 = "rshw9abtpsa2";
    private static final String chuckSecretPhrase = "eOdBVLMgySFvyiTy8xMuRXDTr45oTzB7L5J";
    private static final String daveSecretPhrase = "t9G2ymCmDsQij7VtYinqrbGCOAtDDA3WiNr";

    protected static boolean isMetroInitialized = false;

    public static void initMetro() {
        if (!isMetroInitialized) {
            Properties properties = ManualForgingTest.newTestProperties();
            properties.setProperty("metro.isTestnet", "true");
            properties.setProperty("metro.isOffline", "true");
            properties.setProperty("metro.enableFakeForging", "true");
            properties.setProperty("metro.fakeForgingAccounts", "{\"rs\":[\"" + forgerAccountIds.get(0) + "\",\"" + forgerAccountIds.get(1) + "\"]}");
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
            AbstractBlockchainTest.init(properties);
            isMetroInitialized = true;
        }
    }

    @BeforeClass
    public static void init() {
        initMetro();
        Metro.setTime(new Time.CounterTime(Metro.getEpochTime()));
        baseHeight = blockchain.getHeight();
        Logger.logMessage("baseHeight: " + baseHeight);
        FORGY = new Tester(forgerSecretPhrase);
        ALICE = new Tester(aliceSecretPhrase);
        BOB = new Tester(bobSecretPhrase2);
        CHUCK = new Tester(chuckSecretPhrase);
        DAVE = new Tester(daveSecretPhrase);
    }

    @After
    public void destroy() {
        TransactionProcessorImpl.getInstance().clearUnconfirmedTransactions();
        blockchainProcessor.popOffTo(baseHeight);
        Metro.getBlockchain().forgetLastKeyBlock();
        Generator.resetActiveGenerators();
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

    public static void generateBlock() {
        try {
            blockchainProcessor.generateBlock(forgerSecretPhrase, Metro.getEpochTime());
        } catch (BlockchainProcessor.BlockNotAcceptedException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

}
