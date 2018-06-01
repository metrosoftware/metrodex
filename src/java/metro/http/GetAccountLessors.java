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

import metro.Account;
import metro.Metro;
import metro.MetroException;
import metro.db.DbIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountLessors extends APIServlet.APIRequestHandler {

    static final GetAccountLessors instance = new GetAccountLessors();

    private GetAccountLessors() {
        super(new APITag[] {APITag.ACCOUNTS}, "account", "height");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws MetroException {

        Account account = ParameterParser.getAccount(req);
        int height = ParameterParser.getHeight(req);
        if (height < 0) {
            height = Metro.getBlockchain().getHeight();
        }

        JSONObject response = new JSONObject();
        JSONData.putAccount(response, "account", account.getFullId());
        response.put("height", height);
        JSONArray lessorsJSON = new JSONArray();

        try (DbIterator<Account> lessors = account.getLessors(height)) {
            if (lessors.hasNext()) {
                while (lessors.hasNext()) {
                    Account lessor = lessors.next();
                    JSONObject lessorJSON = new JSONObject();
                    JSONData.putAccount(lessorJSON, "lessor", lessor.getFullId());
                    // TODO #173
                    lessorJSON.put("guaranteedBalanceMQT", String.valueOf(lessor.getGuaranteedBalanceMQT(height - Metro.getBlockchain().getGuaranteedBalanceHeight(height), height)));
                    lessorsJSON.add(lessorJSON);
                }
            }
        }
        response.put("lessors", lessorsJSON);
        return response;

    }

}
