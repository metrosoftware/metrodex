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
import metro.Transaction;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static metro.http.JSONResponses.PRUNED_TRANSACTION;
import static metro.http.JSONResponses.UNKNOWN_TRANSACTION;

public class RetrievePrunedTransaction extends APIServlet.APIRequestHandler {

    static final RetrievePrunedTransaction instance = new RetrievePrunedTransaction();

    private RetrievePrunedTransaction() {
        super(new APITag[] {APITag.TRANSACTIONS}, "transaction");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        long transactionId = ParameterParser.getUnsignedLong(req, "transaction", true);
        Transaction transaction = Metro.getBlockchain().getTransaction(transactionId);
        if (transaction == null) {
            return UNKNOWN_TRANSACTION;
        }
        transaction = Metro.getBlockchainProcessor().restorePrunedTransaction(transactionId);
        if (transaction == null) {
            return PRUNED_TRANSACTION;
        }
        return JSONData.transaction(transaction);
    }

    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected final boolean allowRequiredBlockParameters() {
        return false;
    }

}
