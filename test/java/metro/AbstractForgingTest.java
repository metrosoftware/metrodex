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

import org.junit.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public abstract class AbstractForgingTest extends AbstractBlockchainTest {

    public static final String aliceSecretPhrase = "hope peace happen touch easy pretend worthless talk them indeed wheel state";
    protected static final int minStartHeight = 0;
    protected static final List<String> forgerAccountIds = Arrays.asList("MTR-SZKV-J8TH-AL2R-LKV6-ZW32-APYM","MTR-9KZM-KNYY-FCUM-TD8V-TFG3-5R5U","MTR-XK4R-7VJU-QY97-R335-MKW3-BRH9", "MTR-HW98-D36H-6ZUW-R8R3-8PH2-QW3H");
    protected static final String bobSecretPhrase2 = "rshw9abtpsa2";
    protected static final String chuckSecretPhrase = "eOdBVLMgySFvyiTy8xMuRXDTr45oTzB7L5J";
    protected static final String daveSecretPhrase = "t9G2ymCmDsQij7VtYinqrbGCOAtDDA3WiNr";
    protected static int startHeight;
    protected final static String testForgingSecretPhrase = "aSykrgKGZNlSVOMDxkZZgbTvQqJPGtsBggb";
    protected static Tester FORGY;
    protected static Tester ALICE;
    protected static Tester BOB;
    protected static Tester CHUCK;
    protected static Tester DAVE;

    protected static Properties newTestProperties() {
        Properties properties = AbstractBlockchainTest.newTestProperties();
        properties.setProperty("metro.isTestnet", "true");
        properties.setProperty("metro.isOffline", "true");
        properties.setProperty("metro.fakeForgingAccounts", "{\"rs\":[\"" + forgerAccountIds.get(0) + "\",\"" + forgerAccountIds.get(1) + "\",\"" + forgerAccountIds.get(2) + "\",\"" + forgerAccountIds.get(3) + "\"]}");
        return properties;
    }

    protected static void init(Properties properties) {
        AbstractBlockchainTest.init(properties);
        startHeight = blockchain.getHeight();
        Assert.assertTrue(startHeight >= minStartHeight);
        FORGY = new Tester(testForgingSecretPhrase);
        ALICE = new Tester(aliceSecretPhrase);
        BOB = new Tester(bobSecretPhrase2);
        CHUCK = new Tester(chuckSecretPhrase);
        DAVE = new Tester(daveSecretPhrase);
    }

    protected static void shutdown() {
        blockchainProcessor.popOffTo(startHeight);
        AbstractBlockchainTest.shutdown();
    }

}
