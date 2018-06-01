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
import metro.MetroException;
import metro.db.DbIterator;
import metro.util.Convert;
import org.apache.commons.lang3.tuple.Pair;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountProperties extends APIServlet.APIRequestHandler {

    static final GetAccountProperties instance = new GetAccountProperties();

    private GetAccountProperties() {
        super(new APITag[] {APITag.ACCOUNTS}, "recipient", "property", "setter", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws MetroException {

        Account.FullId recipientId = ParameterParser.getAccountFullId(req, "recipient", false);
        Account.FullId setterId = ParameterParser.getAccountFullId(req, "setter", false);
        if (recipientId == null && setterId == null) {
            return JSONResponses.missing("recipient", "setter");
        }
        String property = Convert.emptyToNull(req.getParameter("property"));
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONObject response = new JSONObject();
        JSONArray propertiesJSON = new JSONArray();
        response.put("properties", propertiesJSON);
        if (recipientId != null) {
            JSONData.putAccount(response, "recipient", recipientId);
        }
        if (setterId != null) {
            JSONData.putAccount(response, "setter", setterId);
        }
        try (DbIterator<Account.AccountProperty> iterator = Account.getProperties(recipientId, setterId, property, firstIndex, lastIndex)) {
            while (iterator.hasNext()) {
                propertiesJSON.add(JSONData.accountProperty(iterator.next(), recipientId == null, setterId == null));
            }
        }
        return response;

    }

}
