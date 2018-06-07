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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

public class FakeForgingTest extends AbstractForgingTest {

    @Before
    public void init() {
        Properties properties = AbstractForgingTest.newTestProperties();
        properties.setProperty("metro.disableGenerateBlocksThread", "false");
        properties.setProperty("metro.enableFakeForging", "true");
        properties.setProperty("metro.timeMultiplier", "1");
        properties.setProperty("metro.fakeForgingAccounts", "{\"rs\":[\"MTR-9KZM-KNYY-FCUM-TD8V-TFG3-5R5U\",\"MTR-SZKV-J8TH-AL2R-LKV6-ZW32-APYM\"]}");

        AbstractForgingTest.init(properties);
        Assert.assertTrue("metro.fakeForgingAccounts must be defined in metro.properties", Metro.getStringProperty("metro.fakeForgingAccounts") != null);
    }

    @Test
    public void fakeForgingTest() {
        forgeTo(startHeight + 10, testForgingSecretPhrase);
    }

    @After
    public void destroy() {
        AbstractForgingTest.shutdown();
    }

}
