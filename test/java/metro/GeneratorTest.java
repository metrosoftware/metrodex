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
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;

import static metro.Consensus.GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS;

public class GeneratorTest extends BlockchainTest {

    /**
     * Simulate the forging process calculations
     */
    @Ignore
    @Test
    public void forge() {
        byte[] publicKey = ALICE.getPublicKey();
        BlockImpl lastBlock = blockchain.getLastBlock();
        BigInteger hit = Generator.getHit(publicKey, lastBlock);
        Account account = Account.getAccount(publicKey);
        BigInteger effectiveBalance = BigInteger.valueOf(account == null || account.getEffectiveBalanceMTR() <= 0 ? 0 : account.getEffectiveBalanceMTR());
        long hitTime = Generator.getHitTime(effectiveBalance, hit, lastBlock);
        long deadline = hitTime - lastBlock.getTimestamp();
        Generator generator = Generator.startForging(ALICE.getSecretPhrase(), -1);
        int i=1;
        try {
            while (i<deadline) {
                Assert.assertFalse(generator.forge(lastBlock, lastBlock.getTimestamp() + i));
                i += 100;
            }
            Assert.assertEquals(true, generator.forge(lastBlock, (int)hitTime + 1));
        } catch (BlockchainProcessor.BlockNotAcceptedException e) {
            e.printStackTrace();
        }

        // Now the block is broadcast to all peers
        // This is what the peer which receives the block does
        lastBlock = blockchain.getLastBlock();
        Assert.assertEquals(hitTime + 1, lastBlock.getTimestamp());
        try {
            Assert.assertTrue(lastBlock.verifyGenerationSequence());
        } catch (BlockchainProcessor.BlockOutOfOrderException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testForgersMerkleDuringScan() throws MetroException {
        // TODO #207 more complex testing of forgers Merkle
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            generateBlockBy(ALICE);
            Assert.assertNotNull(mineBlock());
        }
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            generateBlock();
            Assert.assertNotNull(mineBlock());
        }
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            generateBlockBy(ALICE);
            Assert.assertNotNull(mineBlock());
        }
        blockchainProcessor.scan(blockchain.getHeight() - GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS * 3, true);
        Assert.assertEquals("scan failed to preserve block records", 18, blockchain.getHeight());
    }
}
