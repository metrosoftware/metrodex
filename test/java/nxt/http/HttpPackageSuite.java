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

package nxt.http;

import nxt.http.accountControl.PhasingOnlyTest;
import nxt.http.shuffling.TestAutomatedShuffling;
import nxt.http.shuffling.TestShuffling;
import nxt.http.twophased.TestApproveTransaction;
import nxt.http.twophased.TestCreateTwoPhased;
import nxt.http.twophased.TestGetAccountPhasedTransactions;
import nxt.http.twophased.TestGetAssetPhasedTransactions;
import nxt.http.twophased.TestGetPhasingPoll;
import nxt.http.twophased.TestGetVoterPhasedTransactions;
import nxt.http.twophased.TestTrustlessAssetSwap;
import nxt.http.votingsystem.TestCastVote;
import nxt.http.votingsystem.TestCreatePoll;
import nxt.http.votingsystem.TestGetPolls;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
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
        MessageEncryptionTest.class
})

public class HttpPackageSuite extends AbstractHttpApiSuite {}
