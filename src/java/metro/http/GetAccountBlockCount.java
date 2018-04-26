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

package metro.http;

import metro.Metro;
import metro.MetroException;
import metro.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class GetAccountBlockCount extends APIServlet.APIRequestHandler {

    static final GetAccountBlockCount instance = new GetAccountBlockCount();

    private GetAccountBlockCount() {
        super(new APITag[] {APITag.ACCOUNTS}, "account");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws MetroException {

        long accountId = ParameterParser.getAccountId(req, true);
        boolean isKeyblock = "true".equalsIgnoreCase(req.getParameter("isKeyBlock"));
        JSONObject response = new JSONObject();
        response.put("numberOfBlocks", Metro.getBlockchain().getBlockCount(accountId, isKeyblock));

        return response;
    }

}
