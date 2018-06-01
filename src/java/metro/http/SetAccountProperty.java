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
import metro.Attachment;
import metro.Constants;
import metro.MetroException;
import metro.util.Convert;
import org.apache.commons.lang3.tuple.Pair;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static metro.http.JSONResponses.INCORRECT_ACCOUNT_PROPERTY_NAME_LENGTH;
import static metro.http.JSONResponses.INCORRECT_ACCOUNT_PROPERTY_VALUE_LENGTH;

public final class SetAccountProperty extends CreateTransaction {

    static final SetAccountProperty instance = new SetAccountProperty();

    private SetAccountProperty() {
        super(new APITag[] {APITag.ACCOUNTS, APITag.CREATE_TRANSACTION}, "recipient", "property", "value");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws MetroException {

        Account senderAccount = ParameterParser.getSenderAccount(req);
        Account.FullId recipientId = ParameterParser.getAccountFullId(req, "recipient", false);
        if (recipientId == null) {
            recipientId = senderAccount.getFullId();
        }
        String property = Convert.nullToEmpty(req.getParameter("property")).trim();
        String value = Convert.nullToEmpty(req.getParameter("value")).trim();

        if (property.length() > Constants.MAX_ACCOUNT_PROPERTY_NAME_LENGTH || property.length() == 0) {
            return INCORRECT_ACCOUNT_PROPERTY_NAME_LENGTH;
        }

        if (value.length() > Constants.MAX_ACCOUNT_PROPERTY_VALUE_LENGTH) {
            return INCORRECT_ACCOUNT_PROPERTY_VALUE_LENGTH;
        }

        Attachment attachment = new Attachment.MessagingAccountProperty(property, value);
        return createTransaction(req, senderAccount, recipientId, 0, attachment);

    }

}
