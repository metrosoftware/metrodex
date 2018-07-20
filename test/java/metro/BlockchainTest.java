/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 * Copyright © 2018 metro.software
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package metro;

import metro.crypto.Crypto;
import metro.util.BitcoinJUtils;
import metro.util.Convert;
import metro.util.Logger;
import metro.util.Time;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static metro.Consensus.HASH_FUNCTION;

public abstract class BlockchainTest extends AbstractBlockchainTest {

    protected static Tester FORGY;
    protected static Tester ALICE;
    protected static Tester BOB;
    protected static Tester CHUCK;
    protected static Tester DAVE;
    protected static Tester ESAU;

    protected static int baseHeight;

    protected static String forgerSecretPhrase = "aSykrgKGZNlSVOMDxkZZgbTvQqJPGtsBggb";
    protected static final List<String> forgerAccountIds = Arrays.asList("MTR-9KZM-KNYY-FCUM-TD8V-TFG3-5R5U","MTR-XK4R-7VJU-QY97-R335-MKW3-BRH9", "MTR-HW98-D36H-6ZUW-R8R3-8PH2-QW3H");

    public static final String aliceSecretPhrase = "hope peace happen touch easy pretend worthless talk them indeed wheel state";
    private static final String bobSecretPhrase2 = "rshw9abtpsa2";
    private static final String chuckSecretPhrase = "eOdBVLMgySFvyiTy8xMuRXDTr45oTzB7L5J";
    private static final String daveSecretPhrase = "t9G2ymCmDsQij7VtYinqrbGCOAtDDA3WiNr";
    // Esau's MTR address: MTR-HW98-D36H-6ZUW-R8R3-8PH2-QW3H
    private static final String esauSecretPhrase = "myfuUrX4AKYbD7npSxCAHPypWdAg3SEbSG";

    protected static boolean isMetroInitialized = false;

    public static void initMetro() {
        if (!isMetroInitialized) {
            Properties properties = ManualForgingTest.newTestProperties();
            properties.setProperty("metro.isTestnet", "true");
            properties.setProperty("metro.isOffline", "true");
            properties.setProperty("metro.enableFakeForging", "true");
            properties.setProperty("metro.fakeForgingAccounts", "{\"rs\":[\"" + forgerAccountIds.get(0) + "\",\"" + forgerAccountIds.get(1) + "\",\"" + forgerAccountIds.get(2) + "\"]}");
            properties.setProperty("metro.testnetMaxWorkTarget", "1f00ffff");
            properties.setProperty("metro.testnetGuaranteedBalanceKeyblockConfirmations", "10");
            properties.setProperty("metro.testnetCoinbaseMaturityPeriodInKeyblocks", "2");
            properties.setProperty("metro.mine.publicKey", Convert.toHexString(Crypto.getPublicKey(aliceSecretPhrase)));
            properties.setProperty("metro.timeMultiplier", "1");
            properties.setProperty("metro.testnetGuaranteedBalanceConfirmations", "1");
            properties.setProperty("metro.testnetLeasingDelay", "1");
            properties.setProperty("metro.disableProcessTransactionsThread", "true");
            properties.setProperty("metro.deleteFinishedShufflings", "false");
            properties.setProperty("metro.disableSecurityPolicy", "true");
            properties.setProperty("metro.disableAdminPassword", "true");
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
        ESAU = new Tester(esauSecretPhrase);
    }

    @After
    public void destroy() {
        TransactionProcessorImpl.getInstance().clearUnconfirmedTransactions();
        blockchainProcessor.popOffTo(baseHeight);
        Metro.getBlockchain().forgetLastKeyBlock();
    }

    public static void generateBlock() {
        try {
            blockchainProcessor.generateBlock(forgerSecretPhrase, Metro.getEpochTime());
        } catch (BlockchainProcessor.BlockNotAcceptedException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    public static void generateBlockBy(Tester forger) {
        try {
            blockchainProcessor.generateBlock(forger.getSecretPhrase(), Metro.getEpochTime());
        } catch (BlockchainProcessor.BlockNotAcceptedException e) {
            e.printStackTrace();
            Assert.fail();
        }
    }

    public static void generateBlocks(int howMany) {
        for (int i = 0; i < howMany; i++) {
            generateBlock();
        }
    }

    // TODO get rid of this copy-paste from HashSolver
    public static Block mineBlock() throws MetroException {
        BlockImpl preparedBlock = Metro.getBlockchainProcessor().prepareKeyBlock(null);
        int currentNonce = 0, poolSize = 1, startingNonce = 0;
        ByteBuffer buffer = ByteBuffer.wrap(preparedBlock.bytes());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int noncePos = buffer.limit() - 4;
        while (!Thread.currentThread().isInterrupted()) {
            buffer.putInt(noncePos, currentNonce);
            byte[] hash = HASH_FUNCTION.hash(buffer.array());
            ArrayUtils.reverse(hash);
            if (new BigInteger(1, hash).compareTo(BitcoinJUtils.decodeCompactBits((int)preparedBlock.getBaseTarget())) < 0) {
                Logger.logDebugMessage("%s found solution Keccak nonce %d" +
                                " hash %s meets target",
                        Thread.currentThread().getName(), currentNonce,
                        Arrays.toString(hash));
                Block keyBlock = Metro.getBlockchainProcessor().composeKeyBlock(buffer.array(), preparedBlock.getTransactions());
                return Metro.getBlockchainProcessor().processKeyBlock(keyBlock) ? keyBlock : null;
            }
            currentNonce += poolSize;
            if (currentNonce > 256L * 256L * 256L * 256L) {
                Logger.logInfoMessage("%s solution not found for nonce within %d upto 2^32", Thread.currentThread().getName(), startingNonce);
            }
            if (((currentNonce - startingNonce) % (poolSize * 1000000)) == 0) {
                Logger.logInfoMessage("%s computed %d [MH]", Thread.currentThread().getName(), (currentNonce - startingNonce) / poolSize / 1000000);
            }
        }
        return null;
    }

    public byte[] txHashPrivateAccess(Transaction tx) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method fullHash = tx.getClass().getDeclaredMethod("fullHash");
        return (byte[])fullHash.invoke(tx);
    }
}
