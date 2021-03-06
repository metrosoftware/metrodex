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

package metro.http;

import metro.BlockchainTest;
import metro.Constants;
import metro.util.Logger;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public class SendMoneyTest extends BlockchainTest {

    @Test
    public void sendMoney() {
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 100 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        // Forger
        Assert.assertEquals(0, FORGY.getBalanceDiff());
        Assert.assertEquals(0, FORGY.getUnconfirmedBalanceDiff());
        // Sender
        Assert.assertEquals(0, ALICE.getBalanceDiff());
        Assert.assertEquals(-100 * Constants.ONE_MTR - Constants.ONE_MTR, ALICE.getUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(0, BOB.getBalanceDiff());
        Assert.assertEquals(0, BOB.getUnconfirmedBalanceDiff());
        generateBlock();
        // Forger
        Assert.assertEquals(Constants.ONE_MTR, FORGY.getBalanceDiff());
        // Coinbase is unspendable for 6 key blocks (feat #166)
        Assert.assertEquals(0, FORGY.getUnconfirmedBalanceDiff());
        // Sender
        Assert.assertEquals(-100 * Constants.ONE_MTR - Constants.ONE_MTR, ALICE.getBalanceDiff());
        Assert.assertEquals(-100 * Constants.ONE_MTR - Constants.ONE_MTR, ALICE.getUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(100 * Constants.ONE_MTR, BOB.getBalanceDiff());
        Assert.assertEquals(100 * Constants.ONE_MTR, BOB.getUnconfirmedBalanceDiff());

        // TODO check FORGY.getUnconfirmedBalanceDiff() after 6 (2 for this testnet) key blocks:
//        Assert.assertEquals(Constants.ONE_MTR, FORGY.getUnconfirmedBalanceDiff());
    }

    @Test
    public void sendTooMuchMoney() {
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", ALICE.getInitialBalance()).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        Assert.assertEquals((long)6, response.get("errorCode"));
    }

    @Test
    public void sendAndReturn() {
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 100 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney1: " + response);
        response = new APICall.Builder("sendMoney").
                param("secretPhrase", BOB.getSecretPhrase()).
                param("recipient", ALICE.getStrId()).
                param("amountMQT", 100 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney2: " + response);
        // Forger
        Assert.assertEquals(0, FORGY.getBalanceDiff());
        Assert.assertEquals(0, FORGY.getUnconfirmedBalanceDiff());
        // Sender
        Assert.assertEquals(0, ALICE.getBalanceDiff());
        Assert.assertEquals(-100 * Constants.ONE_MTR - Constants.ONE_MTR, ALICE.getUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(0, BOB.getBalanceDiff());
        Assert.assertEquals(-100 * Constants.ONE_MTR - Constants.ONE_MTR, BOB.getUnconfirmedBalanceDiff());
        generateBlock();
        // Forger
        Assert.assertEquals(2*Constants.ONE_MTR, FORGY.getBalanceDiff());
        // Coinbase is unspendable for 6 key blocks (feat #166)
        Assert.assertEquals(0, FORGY.getUnconfirmedBalanceDiff());
        // Sender
        Assert.assertEquals(-Constants.ONE_MTR, ALICE.getBalanceDiff());
        Assert.assertEquals(-Constants.ONE_MTR, ALICE.getUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(-Constants.ONE_MTR, BOB.getBalanceDiff());
        Assert.assertEquals(-Constants.ONE_MTR, BOB.getUnconfirmedBalanceDiff());
        // TODO after coinbase maturity:
//        Assert.assertEquals(2*Constants.ONE_MTR, FORGY.getUnconfirmedBalanceDiff());
    }

    @Test
    public void signAndBroadcastBytes() {
        JSONObject response = new APICall.Builder("sendMoney").
                param("publicKey", ALICE.getPublicKeyStr()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 100 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlock();
        // No change transaction not broadcast
        Assert.assertEquals(0, ALICE.getBalanceDiff());
        Assert.assertEquals(0, ALICE.getUnconfirmedBalanceDiff());
        Assert.assertEquals(0, BOB.getBalanceDiff());
        Assert.assertEquals(0, BOB.getUnconfirmedBalanceDiff());

        response = new APICall.Builder("signTransaction").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("unsignedTransactionBytes", (String)response.get("unsignedTransactionBytes")).
                build().invoke();
        Logger.logDebugMessage("signTransaction: " + response);

        response = new APICall.Builder("broadcastTransaction").
                param("transactionBytes", (String)response.get("transactionBytes")).
                build().invoke();
        Logger.logDebugMessage("broadcastTransaction: " + response);
        generateBlock();

        // Sender
        Assert.assertEquals(-100 * Constants.ONE_MTR - Constants.ONE_MTR, ALICE.getBalanceDiff());
        Assert.assertEquals(-100 * Constants.ONE_MTR - Constants.ONE_MTR, ALICE.getUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(100 * Constants.ONE_MTR, BOB.getBalanceDiff());
        Assert.assertEquals(100 * Constants.ONE_MTR, BOB.getUnconfirmedBalanceDiff());
    }

    @Test
    public void signAndBroadcastJSON() {
        JSONObject response = new APICall.Builder("sendMoney").
                param("publicKey", ALICE.getPublicKeyStr()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 100 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlock();
        // No change transaction not broadcast
        Assert.assertEquals(0, ALICE.getBalanceDiff());
        Assert.assertEquals(0, ALICE.getUnconfirmedBalanceDiff());
        Assert.assertEquals(0, BOB.getBalanceDiff());
        Assert.assertEquals(0, BOB.getUnconfirmedBalanceDiff());

        response = new APICall.Builder("signTransaction").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("unsignedTransactionJSON", response.get("transactionJSON").toString()).
                build().invoke();
        Logger.logDebugMessage("signTransaction: " + response);

        response = new APICall.Builder("broadcastTransaction").
                param("transactionBytes", (String)response.get("transactionBytes")).
                build().invoke();
        Logger.logDebugMessage("broadcastTransaction: " + response);
        generateBlock();

        // Sender
        Assert.assertEquals(-100 * Constants.ONE_MTR - Constants.ONE_MTR, ALICE.getBalanceDiff());
        Assert.assertEquals(-100 * Constants.ONE_MTR - Constants.ONE_MTR, ALICE.getUnconfirmedBalanceDiff());
        // Recipient
        Assert.assertEquals(100 * Constants.ONE_MTR, BOB.getBalanceDiff());
        Assert.assertEquals(100 * Constants.ONE_MTR, BOB.getUnconfirmedBalanceDiff());
    }

    @Test
    public void sendMoney_recipientNoPubKey_success() {


    }

    @Test
    public void sendMoney_recipientNoPubKey_fail() {

    }

    @Test
    public void sendMoney_recipientHasPubKey_success() {

    }
}
