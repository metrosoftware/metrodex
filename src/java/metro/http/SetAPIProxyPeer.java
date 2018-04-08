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

import metro.MetroException;
import metro.peer.Peer;
import metro.peer.Peers;
import metro.util.Convert;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static metro.http.JSONResponses.API_PROXY_NO_OPEN_API_PEERS;
import static metro.http.JSONResponses.PEER_NOT_CONNECTED;
import static metro.http.JSONResponses.PEER_NOT_OPEN_API;
import static metro.http.JSONResponses.UNKNOWN_PEER;

public class SetAPIProxyPeer extends APIServlet.APIRequestHandler {

    static final SetAPIProxyPeer instance = new SetAPIProxyPeer();

    private SetAPIProxyPeer() {
        super(new APITag[] {APITag.NETWORK}, "peer");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request) throws MetroException {
        String peerAddress = Convert.emptyToNull(request.getParameter("peer"));
        if (peerAddress == null) {
            Peer peer = APIProxy.getInstance().setForcedPeer(null);
            if (peer == null) {
                return API_PROXY_NO_OPEN_API_PEERS;
            }
            return JSONData.peer(peer);
        }
        Peer peer = Peers.findOrCreatePeer(peerAddress, false);
        if (peer == null) {
            return UNKNOWN_PEER;
        }
        if (peer.getState() != Peer.State.CONNECTED ) {
            return PEER_NOT_CONNECTED;
        }
        if (!peer.isOpenAPI()) {
            return PEER_NOT_OPEN_API;
        }
        APIProxy.getInstance().setForcedPeer(peer);
        return JSONData.peer(peer);
    }

    @Override
    protected boolean requirePost() {
        return true;
    }

    @Override
    protected boolean requirePassword() {
        return true;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }


}
