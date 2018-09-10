package metro.daemon;

import metro.Block;
import metro.BlockImpl;
import metro.BlockchainImpl;
import metro.Consensus;
import metro.Metro;
import metro.Transaction;
import metro.TransactionImpl;
import metro.util.BitcoinJUtils;
import metro.util.Convert;
import metro.util.JSON;
import org.apache.commons.lang3.ArrayUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class GetBlock implements DaemonRequestHandler {

    static GetBlock instance = new GetBlock();

    private GetBlock() {
    }
    
    @Override
    public JSONStreamAware process(DaemonRequest dReq) {
        byte[] hash = Convert.parseHexString((String) dReq.getParams().get(0));
        ArrayUtils.reverse(hash);
        long id = Convert.fullHashToId(hash);
        BlockchainImpl blockchain = BlockchainImpl.getInstance();
        Block block = blockchain.getBlock(id);
        long time = Metro.getEpochTime();

        JSONObject result = new JSONObject();
        result.put("hash", block.getHash());
        result.put("confirmations", blockchain.getLastKeyBlock().getLocalHeight() - block.getLocalHeight());
        result.put("strippedsize", 1024);
        result.put("size", 1024);
        result.put("weight", 1024);
        result.put("height", block.getLocalHeight());
        result.put("version", block.getVersion());
        result.put("versionHex", Integer.toHexString(block.getVersion()));
        result.put("merkleroot", Convert.toHexString(block.getTxMerkleRoot()));
        result.put("time", time);
        result.put("nonce", block.getNonce());
        result.put("bits", Convert.toHexString(Convert.toBytes(block.getBaseTarget())));
        result.put("difficulty", new BigDecimal(Consensus.DIFFICULTY_MAX_TARGET).
                divide(new BigDecimal(BitcoinJUtils.decodeCompactBits((int) block.getBaseTarget())),
                        3, RoundingMode.DOWN));
        result.put("previousblockhash", block.getPreviousBlockHash());
        if (block.getNextBlockId() != 0) {
            BlockImpl next = blockchain.getBlock(block.getNextBlockId());
            result.put("nextblockhash", next.getHash());
        }
        JSONArray txs = new JSONArray();
        for (Transaction transaction: block.getTransactions()) {
            txs.add(Convert.toHexString(((TransactionImpl)transaction).fullHash()));
        }
        result.put("tx", txs);

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
