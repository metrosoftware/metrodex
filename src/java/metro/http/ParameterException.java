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

import metro.MetroException;
import org.json.simple.JSONStreamAware;

public final class ParameterException extends MetroException {

    private final JSONStreamAware errorResponse;

    ParameterException(JSONStreamAware errorResponse) {
        this.errorResponse = errorResponse;
    }

    JSONStreamAware getErrorResponse() {
        return errorResponse;
    }

}
