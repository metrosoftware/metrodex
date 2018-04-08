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

import metro.crypto.Crypto;
import metro.util.Time;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class ManualForgingTest extends AbstractForgingTest {

    @Test
    public void manualForgingTest() {
        Properties properties = ManualForgingTest.newTestProperties();
        properties.setProperty("metro.enableFakeForging", "true");
        properties.setProperty("metro.timeMultiplier", "1");
        properties.setProperty("metro.fakeForgingAccount", "MTR-9KZM-KNYY-QBXZ-5TD8V");
        AbstractForgingTest.init(properties);
        Assert.assertTrue("metro.fakeForgingAccount must be defined in metro.properties", Metro.getStringProperty("metro.fakeForgingAccount") != null);
        final byte[] testPublicKey = Crypto.getPublicKey(testForgingSecretPhrase);
        Metro.setTime(new Time.CounterTime(Metro.getEpochTime()));
        try {
            for (int i = 0; i < 10; i++) {
                blockchainProcessor.generateBlock(testForgingSecretPhrase, Metro.getEpochTime());
                Assert.assertArrayEquals(testPublicKey, blockchain.getLastBlock().getGeneratorPublicKey());
            }
        } catch (BlockchainProcessor.BlockNotAcceptedException e) {
            throw new RuntimeException(e.toString(), e);
        }
        Assert.assertEquals(startHeight + 10, blockchain.getHeight());
        AbstractForgingTest.shutdown();
    }

}
