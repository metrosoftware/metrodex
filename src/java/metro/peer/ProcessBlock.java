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

package metro.peer;

import metro.Metro;
import metro.MetroException;
import metro.util.JSON;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

final class ProcessBlock extends PeerServlet.PeerRequestHandler {

    static final ProcessBlock instance = new ProcessBlock();

    private ProcessBlock() {}

    @Override
    JSONStreamAware processRequest(final JSONObject request, final Peer peer) {
        Peers.peersService.submit(() -> {
            try {
                Metro.getBlockchainProcessor().processPeerBlock(request, peer);
            } catch (MetroException | RuntimeException e) {
                if (peer != null) {
                    peer.blacklist(e);
                }
            }
        });

        return JSON.emptyJSON;
    }

    @Override
    boolean rejectWhileDownloading() {
        return true;
    }

}
