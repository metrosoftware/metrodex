/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
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

import metro.util.Logger;
import metro.util.Time;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Properties;

import static metro.Consensus.HASH_FUNCTION;

public abstract class BlockchainTest extends AbstractBlockchainTest {

    protected static Tester FORGY;
    protected static Tester ALICE;
    protected static Tester BOB;
    protected static Tester CHUCK;
    protected static Tester DAVE;

    protected static int baseHeight;

    protected static String forgerSecretPhrase = "aSykrgKGZNlSVOMDxkZZgbTvQqJPGtsBggb";
    protected static final String forgerAccountId = "MTR-9KZM-KNYY-QBXZ-5TD8V";

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
            properties.setProperty("metro.fakeForgingAccount", forgerAccountId);
            properties.setProperty("metro.testnetMaxWorkTarget", "1f00ffff");
            properties.setProperty("metro.testnetGuaranteedBalanceKeyblockConfirmations", "3");
            properties.setProperty("metro.mine.secretPhrase", aliceSecretPhrase);
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

    public static void generateBlocks(int howMany) {
        for (int i = 0; i < howMany; i++) {
            generateBlock();
        }
    }

    // TODO get rid of this copy-paste from HashSolver
    public static Block mineBlock() throws MetroException {
        BlockImpl preparedBlock = Metro.getBlockchainProcessor().prepareKeyBlock();
        int currentNonce = 0, poolSize = 1, startingNonce = 0;
        ByteBuffer buffer = ByteBuffer.wrap(preparedBlock.bytes());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        while (!Thread.currentThread().isInterrupted()) {
            buffer.putLong(buffer.limit() - 8, currentNonce);
            byte[] hash = HASH_FUNCTION.hash(buffer.array());
            if (new BigInteger(1, hash).compareTo(Consensus.MAX_WORK_TARGET) < 0) {
                Logger.logDebugMessage("%s found solution Keccak nonce %d" +
                                " hash %s meets target",
                        Thread.currentThread().getName(), currentNonce,
                        Arrays.toString(hash));
                Block keyBlock = Metro.getBlockchain().composeKeyBlock(buffer.array(), preparedBlock.getGeneratorPublicKey(), preparedBlock.getTransactions());
                return Metro.getBlockchainProcessor().processMinerBlock(keyBlock) ? keyBlock : null;
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
}
