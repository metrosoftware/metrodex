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

package metro.http.accountControl;

import metro.Constants;
import metro.Tester;
import metro.http.APICall;
import metro.util.Logger;
import org.json.simple.JSONObject;
import org.junit.Assert;

public class ACTestUtils {

    public static class Builder extends APICall.Builder {

        public Builder(String requestType, String secretPhrase) {
            super(requestType);
            secretPhrase(secretPhrase);
            feeMQT(0);
        }
    }

    public static class AssetBuilder extends APICall.Builder {

        public AssetBuilder(String secretPhrase, String assetName) {
            super("issueAsset");
            param("name", assetName);
            param("description", "Unit tests asset");
            param("quantityQNT", 10000);
            param("decimals", 4);
            secretPhrase(secretPhrase);
            feeMQT(Constants.ONE_MTR*1000);
        }

    }

    public static long issueTestAsset(Tester tester) {
        APICall.Builder builder = new ACTestUtils.AssetBuilder(tester.getSecretPhrase(), "TestAsset");
        return Long.parseUnsignedLong((String) ACTestUtils.assertTransactionSuccess(builder).get("transaction"));
    }

    public static JSONObject transferAsset(Tester sender, Tester receiver, long assetId, long quantity) {
         return new APICall.Builder("transferAsset").
                param("secretPhrase", sender.getSecretPhrase()).
                param("recipient", receiver.getStrId()).
                param("asset", Long.toUnsignedString(assetId)).
                param("quantityQNT", quantity).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
    }

    public static JSONObject assertTransactionSuccess(APICall.Builder builder) {
        JSONObject response = builder.build().invoke();
        
        Logger.logMessage(builder.getParam("requestType") + " response: " + response.toJSONString());
        Assert.assertNull(response.get("error"));
        String result = (String) response.get("transaction");
        Assert.assertNotNull(result);
        return response;
    }
    
    public static void assertTransactionBlocked(APICall.Builder builder) {
        JSONObject response = builder.build().invoke();
        
        Logger.logMessage(builder.getParam("requestType") + " response: " + response.toJSONString());
        
        //Assert.assertNotNull("Transaction wasn't even created", response.get("transaction"));
        
        String errorMsg = (String) response.get("error");
        Assert.assertNotNull("Transaction should fail, but didn't", errorMsg);
        Assert.assertTrue(errorMsg.contains("metro.MetroException$AccountControlException"));
    }
    
    public static long getAccountBalance(String account, String balance) {
        APICall.Builder builder = new APICall.Builder("getBalance").param("account", account);
        JSONObject response = builder.build().invoke();
        
        Logger.logMessage("getBalance response: " + response.toJSONString());
        
        return Long.parseLong(((String)response.get(balance)));
    }
}
