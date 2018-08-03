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

import metro.http.APICall;
import metro.util.Logger;
import org.apache.commons.lang3.tuple.Pair;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static metro.Consensus.GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS;

public class ForgersMerkleTest extends BlockchainTest {
    @Ignore
    @Test
    public void testSpendingInOldApproachWithForcedReset() throws NoSuchFieldException, IllegalAccessException, MetroException {
        // Alice forges a block (to become a generator and get included)
        generateBlockBy(ALICE);
        Field generators = Generator.class.getDeclaredField("activeGenerators");
        generators.setAccessible(true);
        // initial MTR balances: Forgy: 500M, Alice: 1M
        Map<Account.FullId, Generator.ActiveGenerator> genValues = (Map<Account.FullId, Generator.ActiveGenerator>) generators.get(null);
        Assert.assertEquals(1, genValues.size());
        Generator.ActiveGenerator gen1 = genValues.values().iterator().next();
        Assert.assertEquals(1000000, gen1.getEffectiveBalance().longValue());
        // Alice sends Bob an odd thousand
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 1000 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        // Forgy includes tx into a fast block, getting 1 MTR and becoming 1st (by balance)
        generateBlock();
        // We need to get past initial EC to get effective balances different from genesisAccounts
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            Assert.assertNotNull(mineBlock());
        }
        Account alice = Account.getAccount(ALICE.getFullId());
        // balance now reduced by 1001
        Assert.assertEquals(101899900000000l, alice.getBalanceMQT());
        // 18000MTR subsidy has not matured yet at this point
        Assert.assertEquals(1000999, alice.getEffectiveBalanceMTR());
        // by resetting activeGenerators, we make sure Alice's and Forgy's balances get updated -
        // Forgy's balance increases immediately only if we have SQL: SELECT account_id, SUM (additions) AS additions
        // FROM account_guaranteed_balance, TABLE (id BIGINT=?) T WHERE account_id = T.id AND NOT coinbase AND ...
        // resulting MTR balances: Forgy: 500M + 1, Alice: 1M - 1001 + 20000subsidy
        genValues = (Map<Account.FullId, Generator.ActiveGenerator>) generators.get(null);
        Assert.assertEquals(2, genValues.size());
        Iterator<Generator.ActiveGenerator> iterator = genValues.values().iterator();
        // now it's Forgy (has the greatest effective balance)
        Assert.assertEquals(500000001, iterator.next().getEffectiveBalance().longValue());
        // none of the block subsidies had enough time to mature
        Assert.assertEquals(1000999, iterator.next().getEffectiveBalance().longValue());
    }

    /**
     * Does same things as testSpendingInOldApproachWithForcedReset, but using just 1 SQL SELECT
     * @throws MetroException
     */
    @Test
    public void testSpendingInNewApproach() throws MetroException {
        // Alice forges a block (to become a generator and get included)
        generateBlockBy(ALICE);
        Assert.assertNotNull(mineBlock());
        List<Pair<String, Integer>> forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(1, forgers.size());
        Assert.assertEquals(1000000, forgers.iterator().next().getRight().intValue());
//        byte[] branches = Metro.getBlockchainProcessor().getCurrentForgersMerkleBranches();
        // Alice sends Bob an odd thousand
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 1000 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        // Forgy includes tx into a fast block, getting 1 MTR and becoming 1st (by balance)
        generateBlock();
        // We need to get past initial EC to get effective balances different from genesisAccounts
        for (int i = 1; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            Assert.assertNotNull(mineBlock());
        }
        Account alice = Account.getAccount(ALICE.getFullId());
        // balance now reduced by 1001; diff between bal and eff bal due to forging power maturation delay
        Assert.assertEquals(101899900000000l, alice.getBalanceMQT());
        Assert.assertEquals(1000999, alice.getEffectiveBalanceMTR());
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        // resulting MTR balances: Forgy: 500M + 1, Alice: 1M - 1001 + 20000subsidy
        Assert.assertEquals(2, forgers.size());
        Iterator<Pair<String, Integer>> iterator = forgers.iterator();
        // so there was addition of 2000 and not 20000 MTR (9 out of 10 rewards not matured yet for effective balance)
        // `AND NOT coinbase' was removed from Account.getGuaranteedBalanceMQT() - so newly credited must mature
        // regardless of whether tx was coinbase
        Assert.assertEquals(1000999, iterator.next().getRight().intValue());
        // now it's Forgy (has the greatest effective balance) - 1 block was mined before the commission of 1MTR, still not matured here
        Assert.assertEquals(500000000, iterator.next().getRight().intValue());
//        branches = Metro.getBlockchainProcessor().getCurrentForgersMerkleBranches();
        Assert.assertNotNull(mineBlock());
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
//        branches = Metro.getBlockchainProcessor().getCurrentForgersMerkleBranches();
        Assert.assertEquals(2, forgers.size());
        iterator = forgers.iterator();
        // 1 more block subsidy matured here
        Assert.assertEquals(1002999, iterator.next().getRight().intValue());
        // 1 MTR commission from 'sendMoney' matured here
        Assert.assertEquals(500000001, iterator.next().getRight().intValue());
    }

    @Ignore
    @Test
    public void testIncome() throws NoSuchFieldException, IllegalAccessException, MetroException {
        // Alice forges a block (to become a generator and get included)
        generateBlockBy(ALICE);
        Field generators = Generator.class.getDeclaredField("activeGenerators");
        generators.setAccessible(true);
        // initial MTR balances: Forgy: 500M, Alice: 1M
        Map<Account.FullId, Generator.ActiveGenerator> genValues = (Map<Account.FullId, Generator.ActiveGenerator>) generators.get(null);
        Assert.assertEquals(1, genValues.size());
        Generator.ActiveGenerator gen1 = genValues.values().iterator().next();
        Assert.assertEquals(1000000, gen1.getEffectiveBalance().longValue());
        // We need to get past initial EC to get effective balances different from genesisAccounts
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            Assert.assertNotNull(mineBlock());
        }
        // This time Bob sends Alice an odd thousand, after some mining took place
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", BOB.getSecretPhrase()).
                param("recipient", ALICE.getStrId()).
                param("amountMQT", 1000 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        // Forgy includes tx into a fast block, getting 1 MTR and becoming 1st (by balance)
        generateBlock();
        Account alice = Account.getAccount(ALICE.getFullId());
        // balance now increased by 1000 (plus subsidies)
        Assert.assertEquals(102100000000000l, alice.getBalanceMQT());
        // In this version of reality, all 10 keyblock subsidies can do forging from now (they increase effective balance straight away -
        // after Account.getGuaranteedBalanceMQT() correction, we have only 1 subsidy that has matured here
        Assert.assertEquals(1002000, alice.getEffectiveBalanceMTR());
        // by resetting activeGenerators, we make sure Alice's and Forgy's balances get updated -
        // Forgy's balance increases immediately only if we have SQL: SELECT account_id, SUM (additions) AS additions
        // FROM account_guaranteed_balance, TABLE (id BIGINT=?) T WHERE account_id = T.id AND NOT coinbase AND ...
        // resulting MTR balances: Forgy: 500M + 1, Alice: 1M + 1000 + 20000subsidy
        genValues = (Map<Account.FullId, Generator.ActiveGenerator>) generators.get(null);
        Assert.assertEquals(2, genValues.size());
        Iterator<Generator.ActiveGenerator> iterator = genValues.values().iterator();
        // now it's Forgy (has the greatest effective balance)
        Assert.assertEquals(500000000, iterator.next().getEffectiveBalance().longValue());
        // 1 block subsidy has matured by now, but there was not enough time (in key blocks) for income of 1000MTR to mature
        Assert.assertEquals(1002000, iterator.next().getEffectiveBalance().longValue());
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            Assert.assertNotNull(mineBlock());
        }

        // key blocks should have reset activeGenerators, re-request private activeGenerators value now
        genValues = (Map<Account.FullId, Generator.ActiveGenerator>) generators.get(null);
        iterator = genValues.values().iterator();
        Assert.assertEquals(500000001, iterator.next().getEffectiveBalance().longValue());
        // 11 block subsidies + 1000MTR income
        Assert.assertEquals(1023000, iterator.next().getEffectiveBalance().longValue());
    }

    @Test
    public void testIncomeInNewApproach() throws MetroException {
        // Alice forges a block (to become a generator and get included)
        generateBlockBy(ALICE);
        Assert.assertNotNull(mineBlock());
        List<Pair<String, Integer>> forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(1, forgers.size());
        Assert.assertEquals(1000000, forgers.iterator().next().getRight().intValue());
        // We need to get past initial EC to get effective balances different from genesisAccounts
        for (int i = 1; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            Assert.assertNotNull(mineBlock());
        }
        // This time Bob sends Alice an odd thousand, after some mining took place
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", BOB.getSecretPhrase()).
                param("recipient", ALICE.getStrId()).
                param("amountMQT", 1000 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        // Forgy includes tx into a fast block, getting 1 MTR and becoming 1st (by balance)
        generateBlock();
        Account alice = Account.getAccount(ALICE.getFullId());
        // balance now increased by 1000 (plus subsidies)
        Assert.assertEquals(102100000000000l, alice.getBalanceMQT());
        Assert.assertEquals(1002000, alice.getEffectiveBalanceMTR());
        Assert.assertNotNull(mineBlock());
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        // resulting MTR balances: Forgy: 500M + 1, Alice: 1M + 1000 + 20000subsidy
        Assert.assertEquals(2, forgers.size());
        Iterator<Pair<String, Integer>> iterator = forgers.iterator();
        // so there was addition of 4000 and not 20000 MTR (8 out of 10 rewards not matured yet for effective balance)
        // balances are now listed in ascending order (as in hash)
        Assert.assertEquals(1004000, iterator.next().getRight().intValue());
        // TODO difference by 1 MTR here with the old way! due to 'coinbase income'
        Assert.assertEquals(500000000, iterator.next().getRight().intValue());

        for (int i = 1; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            Assert.assertNotNull(mineBlock());
        }

        // take them once again
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        iterator = forgers.iterator();
        Assert.assertEquals(2, forgers.size());
        // 10 more block subsidies + 1000MTR income from Bob has matured
        Assert.assertEquals(1023000, iterator.next().getRight().intValue());
        // 1 MTR has matured now
        Assert.assertEquals(500000001, iterator.next().getRight().intValue());
    }

    @Test
    public void testLessorDoesNotForge() throws MetroException {
        // Alice forges a block (to become a generator and get included)
        JSONObject response1 = new APICall.Builder("leaseBalance").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("period", 2).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("leaseBalance: " + response1);
        generateBlockBy(ALICE);
        Assert.assertNotNull(mineBlock());
        List<Pair<String, Integer>> forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(0, forgers.size());
//        Assert.assertEquals(BOB.getPublicKeyStr(), forgers.iterator().next().getLeft());
        Account alice = Account.getAccount(ALICE.getFullId());
        // balance now increased by 2000 (1 block subsidy)
        Assert.assertEquals(100200000000000l, alice.getBalanceMQT());
        // but they are not included into effective
        Assert.assertEquals(1000000, alice.getEffectiveBalanceMTR());
        Assert.assertNotNull(mineBlock());
        Assert.assertNotNull(mineBlock());
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        // after 3 blocks following Alice's fast block, Alice is forger again as her lease has expired by now
        Assert.assertEquals(1, forgers.size());
        Assert.assertEquals(999999, forgers.iterator().next().getRight().intValue());
    }

    @Test
    public void testLesseeBalanceVariationsWithExpiration() throws MetroException {
        // Esau leases 10000MTR to Alice, but loses 1 MTR from effective balance by receiving it back in fast block reward
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
        Assert.assertEquals(0, forgers.size());

        // Alice has 1000000 + 9999 MTR effective balance, and has mined two blocks so far
        generateBlockBy(ALICE);
        Assert.assertNotNull(mineBlock());
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(1, forgers.size());
        Assert.assertEquals(1014000, forgers.iterator().next().getRight().intValue());

        // Alice sends 999998 to Bob, paying 1 MTR comission, still has enough to remain in forgersMerkle
        response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 999998 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlockBy(ALICE);
        Assert.assertNotNull(mineBlock());
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(1, forgers.size());
        // 2 MTR forged, 2000 mined by Alice
        Assert.assertEquals(16002, forgers.iterator().next().getRight().intValue());
//        Assert.assertEquals(16002, ESAU.getAccount().getUnconfirmedBalanceMQT());

        // Esau sends 2001 to Bob, paying 1 MTR comission, his lessee Alice now should leave forgersMerkle
        response = new APICall.Builder("sendMoney").
                param("secretPhrase", ESAU.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 9002 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        // Alice has forged 2MTR and mined again, so send extra to Bob in order to test Esau's diminishing balance influence
        response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 2000 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlockBy(ALICE);
        Assert.assertNotNull(mineBlock());
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(0, forgers.size());

        // Bob sends 100 to Esau, Esau's lessee Alice now should be again in forgersMerkle - but only after income has matured;
        // while waiting for that, Esau's lease has expired, creating 2 forgers
        response = new APICall.Builder("sendMoney").
                param("secretPhrase", BOB.getSecretPhrase()).
                param("recipient", ESAU.getStrId()).
                param("amountMQT", 9005 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlockBy(ALICE);
        Assert.assertNotNull(mineBlock());
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(0, forgers.size());

        for (int i = 1; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            Assert.assertNotNull(mineBlock());
        }
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(2, forgers.size());
        // Alice got 1MTR commission 4 times
        Iterator<Pair<String, Integer>> iterator = forgers.iterator();
        // Esau's money
        Assert.assertEquals(10002, iterator.next().getRight().intValue());
        // Alice's money (forged and mined reward show up now)
        Assert.assertEquals(26004, iterator.next().getRight().intValue());
    }

    @Test
    public void testLesseeBalanceVariations() throws MetroException {
        // Esau leases 10000MTR to Alice, but loses 1 MTR from effective balance by receiving it back in fast block reward
        JSONObject response = new APICall.Builder("leaseBalance").
                param("secretPhrase", ESAU.getSecretPhrase()).
                param("recipient", ALICE.getStrId()).
                param("period", 25).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlockBy(ESAU);
        Assert.assertNotNull(mineBlock());
        List<Pair<String, Integer>> forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(0, forgers.size());

        // Alice has 1000000 + 9999 MTR effective balance
        generateBlockBy(ALICE);
        Assert.assertNotNull(mineBlock());
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(1, forgers.size());
        Assert.assertEquals(1009999, forgers.iterator().next().getRight().intValue());

        // Alice sends 999998 to Bob, paying 1 MTR comission, still has enough to remain in forgersMerkle
        response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 999998 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlockBy(ALICE);
        Assert.assertNotNull(mineBlock());
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(1, forgers.size());
        Assert.assertEquals(10000, forgers.iterator().next().getRight().intValue());

        // Esau sends 10 to Bob, paying 1 MTR comission, his lessee Alice now should leave forgersMerkle
        response = new APICall.Builder("sendMoney").
                param("secretPhrase", ESAU.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 10 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlockBy(ALICE);
        Assert.assertNotNull(mineBlock());
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(0, forgers.size());

        // Bob sends 100 to Esau, Esau's lessee Alice now should be again in forgersMerkle - but only after income has matured
        response = new APICall.Builder("sendMoney").
                param("secretPhrase", BOB.getSecretPhrase()).
                param("recipient", ESAU.getStrId()).
                param("amountMQT", 100 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlockBy(ALICE);
        Assert.assertNotNull(mineBlock());
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(0, forgers.size());

        for (int i = 1; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            Assert.assertNotNull(mineBlock());
        }
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(1, forgers.size());
        // Alice got 1MTR commission 4 times
        Iterator<Pair<String, Integer>> iterator = forgers.iterator();
        // Alice had 1000000 initially, spent 999999 on 'sendMoney to Bob' tx, has 1 MTR remaining,
        // earned 3MTR by forging (for 3 sendMoney tx), 14*2000 by mining (of which 10000 for 5 earliest blocks matured)
        // Esau's initial 10000 plus 100 from Bob minus 11 after sending to Bob; 1MTR commission paid to himself in the 1st fast block has now matured
        // total of (10000 + 89 of Esau's) + 1 (Alice remaining, from Genesis) + (3 + 10000 of Alice rewards)
        Assert.assertEquals(20093, iterator.next().getRight().intValue());
        Account alice = Account.getAccount(ALICE.getFullId());
        // balance w/out Esau's but with full subsidy amount 14*2000 and remaining Genesis + fast block rewards
        Assert.assertEquals(2800400000000l, alice.getBalanceMQT());
        // exactly as in forgersMerkle
        Assert.assertEquals(20093, alice.getEffectiveBalanceMTR());
    }

    @Test
    public void testTenThousandEnoughToForkVote() throws MetroException {
//        String unsigned = Long.toUnsignedString(Account.FullId.fromSecretPhrase("myfuUrX4AKYbD7npSxCAHPypWdAg3SEbSG").getLeft());
        Account esau = Account.getAccount(ESAU.getFullId());
        Assert.assertEquals(10000, esau.getEffectiveBalanceMTR());
        generateBlockBy(ESAU);
        Assert.assertNotNull(mineBlock());
        List<Pair<String, Integer>> forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(1, forgers.size());
        Assert.assertEquals(10000, forgers.iterator().next().getRight().intValue());
        // Now let's ensure 9999 is not enough, send 1mMTR and pay 1MTR commission
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ESAU.getSecretPhrase()).
                param("recipient", ALICE.getStrId()).
                param("amountMQT", 100000).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlockBy(ESAU);
        Assert.assertNotNull(mineBlock());
        forgers = Metro.getBlockchainProcessor().getCurrentForgers();
        Assert.assertEquals(0, forgers.size());
    }

    @Test
    public void testTwoGeneratorsSameEffectiveBalance() throws MetroException {

    }

    /**
     * We don't need testLeasingInNewApproach() anymore - testLesseeBalanceVariationsWithExpiration,
     * testLesseeBalanceVariations and testLessorDoesNotForge() are enough. Just implement this one to compare with the old way.
     * @throws MetroException
     */
    @Test
    public void testLeasingInOldApproach() throws MetroException {
        // TODO
    }

    @Test
    public void testLessorBalanceZeroed() throws MetroException {
        for (int i = 1; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS; i++) {
            Assert.assertNotNull(mineBlock());
        }
        // Esau leases 10000MTR to Alice...
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
        // Esau was the only forger, and now he hasn't enough funds to enter forgersMerkle, while Alice (lessee) hasn't forged yet
        Assert.assertEquals(0, forgers.size());
        Assert.assertEquals(0, ESAU.getAccount().getEffectiveBalanceMTR());
        Assert.assertEquals(1000000000000l, ESAU.getAccount().getGuaranteedBalanceMQT());
        Assert.assertEquals(999900000000l, ESAU.getAccount().getUnconfirmedBalanceMQT());
    }
}
