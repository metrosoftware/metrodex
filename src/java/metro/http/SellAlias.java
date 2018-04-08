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

import metro.Account;
import metro.Alias;
import metro.Attachment;
import metro.Constants;
import metro.MetroException;
import metro.util.Convert;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static metro.http.JSONResponses.INCORRECT_ALIAS_OWNER;
import static metro.http.JSONResponses.INCORRECT_RECIPIENT;


public final class SellAlias extends CreateTransaction {

    static final SellAlias instance = new SellAlias();

    private SellAlias() {
        super(new APITag[] {APITag.ALIASES, APITag.CREATE_TRANSACTION}, "alias", "aliasName", "recipient", "priceMQT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws MetroException {
        Alias alias = ParameterParser.getAlias(req);
        Account owner = ParameterParser.getSenderAccount(req);

        long priceMQT = ParameterParser.getLong(req, "priceMQT", 0L, Constants.MAX_BALANCE_MQT, true);

        String recipientValue = Convert.emptyToNull(req.getParameter("recipient"));
        long recipientId = 0;
        if (recipientValue != null) {
            try {
                recipientId = Convert.parseAccountId(recipientValue);
            } catch (RuntimeException e) {
                return INCORRECT_RECIPIENT;
            }
            if (recipientId == 0) {
                return INCORRECT_RECIPIENT;
            }
        }

        if (alias.getAccountId() != owner.getId()) {
            return INCORRECT_ALIAS_OWNER;
        }

        Attachment attachment = new Attachment.MessagingAliasSell(alias.getAliasName(), priceMQT);
        return createTransaction(req, owner, recipientId, 0, attachment);
    }
}
