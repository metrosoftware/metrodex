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

import metro.Generator;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


public final class StartForging extends APIServlet.APIRequestHandler {

    static final StartForging instance = new StartForging();

    private StartForging() {
        super(new APITag[] {APITag.FORGING}, "secretPhrase", "blockCount");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        String secretPhrase = ParameterParser.getSecretPhrase(req, true);
        int blocks = ParameterParser.getInt(req, "blockCount", 1, Integer.MAX_VALUE, false);
        // -1 means unlimited number (the default)
        Generator generator = Generator.startForging(secretPhrase, blocks > 0 ? blocks : -1);

        JSONObject response = new JSONObject();
        response.put("deadline", generator.getDeadline());
        response.put("hitTime", generator.getHitTime());
        return response;

    }

    @Override
    protected boolean requirePost() {
        return true;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireFullClient() {
        return true;
    }

}
