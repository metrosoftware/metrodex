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

import metro.Metro;
import metro.Transaction;
import metro.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static metro.http.JSONResponses.INCORRECT_TRANSACTION;
import static metro.http.JSONResponses.MISSING_TRANSACTION;
import static metro.http.JSONResponses.UNKNOWN_TRANSACTION;

public final class GetTransactionBytes extends APIServlet.APIRequestHandler {

    static final GetTransactionBytes instance = new GetTransactionBytes();

    private GetTransactionBytes() {
        super(new APITag[] {APITag.TRANSACTIONS}, "transaction");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        String transactionValue = req.getParameter("transaction");
        if (transactionValue == null) {
            return MISSING_TRANSACTION;
        }

        long transactionId;
        Transaction transaction;
        try {
            transactionId = Convert.parseUnsignedLong(transactionValue);
        } catch (RuntimeException e) {
            return INCORRECT_TRANSACTION;
        }

        transaction = Metro.getBlockchain().getTransaction(transactionId);
        JSONObject response = new JSONObject();
        if (transaction == null) {
            transaction = Metro.getTransactionProcessor().getUnconfirmedTransaction(transactionId);
            if (transaction == null) {
                return UNKNOWN_TRANSACTION;
            }
        } else {
            response.put("confirmations", Metro.getBlockchain().getHeight() - transaction.getHeight());
        }
        response.put("transactionBytes", Convert.toHexString(transaction.getBytes()));
        response.put("unsignedTransactionBytes", Convert.toHexString(transaction.getUnsignedBytes()));
        JSONData.putPrunableAttachment(response, transaction);
        return response;

    }

}
