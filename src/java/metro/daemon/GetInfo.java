package metro.daemon;

import metro.Account;
import metro.BlockImpl;
import metro.BlockchainImpl;
import metro.Consensus;
import metro.Constants;
import metro.Metro;
import metro.Miner;
import metro.peer.Peers;
import metro.util.BitcoinJUtils;
import metro.util.Convert;
import metro.util.JSON;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class GetInfo implements DaemonRequestHandler {

    static GetInfo instance = new GetInfo();

    private GetInfo() {
    }
    
    @Override
    public JSONStreamAware process(DaemonRequest dReq) {
        BlockchainImpl blockchain = BlockchainImpl.getInstance();
        BlockImpl lastKeyBlock = blockchain.getLastKeyBlock();
        Account account = Account.getAccount(Convert.parseHexString(Miner.getPublicKey()));
        JSONObject result = new JSONObject();
        result.put("version", Metro.VERSION);
        result.put("balance", account == null ? 0 : account.getBalanceMQT() / Constants.ONE_MTR);
        result.put("blocks", lastKeyBlock.getLocalHeight() + 1);
        result.put("connections", Peers.getActivePeers().size());
        result.put("difficulty", new BigDecimal(Consensus.DIFFICULTY_MAX_TARGET).
                divide(new BigDecimal(BitcoinJUtils.decodeCompactBits((int) blockchain.getLastKeyBlock().getBaseTarget())),
                        3, RoundingMode.DOWN));
        result.put("paytxfee", 1.0);
        result.put("coinbasevalue", Consensus.getBlockSubsidy(blockchain.getLastKeyBlock().getLocalHeight()));
        JSONObject response = new JSONObject();
        response.put("result", result);
        response.put("error", null);
        response.put("id", dReq.getId());
        return JSON.prepare(response);
    }

    @Override
    public JSONStreamAware process(DaemonRequest request, Object options) {
        return process(request);
    }
}
