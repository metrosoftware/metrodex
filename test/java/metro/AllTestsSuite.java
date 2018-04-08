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

import metro.crypto.Curve25519Test;
import metro.crypto.ReedSolomonTest;
import metro.http.AbstractHttpApiSuite;
import metro.http.LeaseTest;
import metro.http.MessageEncryptionTest;
import metro.http.SendMessageTest;
import metro.http.SendMoneyTest;
import metro.http.accountControl.PhasingOnlyTest;
import metro.http.shuffling.TestShuffling;
import metro.http.twophased.TestApproveTransaction;
import metro.http.twophased.TestCreateTwoPhased;
import metro.http.twophased.TestGetAccountPhasedTransactions;
import metro.http.twophased.TestGetAssetPhasedTransactions;
import metro.http.twophased.TestGetPhasingPoll;
import metro.http.twophased.TestGetVoterPhasedTransactions;
import metro.http.twophased.TestTrustlessAssetSwap;
import metro.http.votingsystem.TestCastVote;
import metro.http.votingsystem.TestCreatePoll;
import metro.http.votingsystem.TestGetPolls;
import metro.peer.HallmarkTest;
import metro.tools.PassphraseRecoveryTest;
import metro.util.APISetTest;
import metro.util.CountingStreamsTest;
import metro.util.EpochTimeTest;
import metro.util.JsonMessageTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        PassphraseRecoveryTest.class,
        Curve25519Test.class,
        ReedSolomonTest.class,
        PhasingOnlyTest.class,
        TestShuffling.class,
        //TestAutomatedShuffling.class,
        TestCreateTwoPhased.class,
        TestGetVoterPhasedTransactions.class,
        TestApproveTransaction.class,
        TestGetPhasingPoll.class,
        TestGetAccountPhasedTransactions.class,
        TestGetAssetPhasedTransactions.class,
        TestTrustlessAssetSwap.class,
        TestCreatePoll.class,
        TestCastVote.class,
        TestGetPolls.class,
        SendMoneyTest.class,
        SendMessageTest.class,
        LeaseTest.class,
        MessageEncryptionTest.class,
        HallmarkTest.class,
        APISetTest.class,
        CountingStreamsTest.class,
        EpochTimeTest.class,
        JsonMessageTest.class,
        TokenTest.class
})

public class AllTestsSuite extends AbstractHttpApiSuite {}
