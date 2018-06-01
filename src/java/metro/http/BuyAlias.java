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
import metro.Alias;
import metro.Attachment;
import metro.MetroException;
import org.apache.commons.lang3.tuple.Pair;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static metro.http.JSONResponses.INCORRECT_ALIAS_NOTFORSALE;


public final class BuyAlias extends CreateTransaction {

    static final BuyAlias instance = new BuyAlias();

    private BuyAlias() {
        super(new APITag[] {APITag.ALIASES, APITag.CREATE_TRANSACTION}, "alias", "aliasName", "amountMQT");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws MetroException {
        Account buyer = ParameterParser.getSenderAccount(req);
        Alias alias = ParameterParser.getAlias(req);
        long amountMQT = ParameterParser.getAmountMQT(req);
        if (Alias.getOffer(alias) == null) {
            return INCORRECT_ALIAS_NOTFORSALE;
        }
        long sellerId = alias.getAccountId();
        Account.FullId sellerFullId = Account.getAccount(sellerId).getFullId();
        Attachment attachment = new Attachment.MessagingAliasBuy(alias.getAliasName());
        return createTransaction(req, buyer, sellerFullId, amountMQT, attachment);
    }
}
