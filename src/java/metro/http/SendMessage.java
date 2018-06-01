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
import metro.MetroException;
import org.apache.commons.lang3.tuple.Pair;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public final class SendMessage extends CreateTransaction {

    static final SendMessage instance = new SendMessage();

    private SendMessage() {
        super(new APITag[] {APITag.MESSAGES, APITag.CREATE_TRANSACTION}, "recipient");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws MetroException {
        Account.FullId recipientId = ParameterParser.getAccountFullId(req, "recipient", false);
        Account account = ParameterParser.getSenderAccount(req);
        return createTransaction(req, account, recipientId, 0, Attachment.ARBITRARY_MESSAGE);
    }

}
