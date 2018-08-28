package metro.daemon;

import metro.Block;
import metro.BlockImpl;
import metro.BlockchainImpl;
import metro.BlockchainProcessorImpl;
import metro.Consensus;
import metro.Metro;
import metro.Target;
import metro.TransactionImpl;
import metro.util.Convert;
import metro.util.JSON;
import org.apache.commons.lang3.ArrayUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.nio.ByteBuffer;

import static metro.Consensus.HASH_FUNCTION;

public class GetBlockTemplate implements DaemonRequestHandler {

    static GetBlockTemplate instance = new GetBlockTemplate();

    private GetBlockTemplate() {
    }

    @Override
    public JSONStreamAware process(DaemonRequest dReq) {
        BlockchainImpl blockchain = BlockchainImpl.getInstance();
        BlockchainProcessorImpl blockchainProcessor = BlockchainProcessorImpl.getInstance();
        JSONObject result = new JSONObject();

        int height = blockchain.getHeight();
        BlockImpl previousBlock = blockchain.getLastBlock();
        BlockImpl previousKeyBlock = blockchain.getLastKeyBlock();
        long previousBlockId = previousBlock.getId();
        long previousKeyBlockId = previousKeyBlock == null ? 0 : previousKeyBlock.getId();
        long time = Metro.getEpochTime();
        Block ecBlock = BlockchainImpl.getInstance().getECBlock(time);
        byte[] forgersMerkleRoot = HASH_FUNCTION.hash(ArrayUtils.addAll(HASH_FUNCTION.hash(previousBlock.getBytes()), blockchainProcessor.getForgersMerkleAtLastKeyBlock()));

        result.put("height", height);
        result.put("version", Consensus.getKeyBlockVersion(height));
        result.put("curtime", time);
        result.put("workid", time);
        result.put("bits", Convert.toHexString(Convert.toBytes(Target.nextTarget(previousKeyBlock))));
        result.put("coinbaseaux", new JSONObject());
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(previousBlockId);
        buffer.putLong(previousKeyBlockId);
        result.put("previousblockhash", Convert.toHexString(buffer.array()));
        result.put("coinbasevalue", Consensus.getBlockSubsidy(previousKeyBlock == null ? 0 : previousKeyBlock.getLocalHeight()));
        JSONArray txs = new JSONArray();
        for (TransactionImpl transaction: Metro.getBlockchainProcessor().prepareKeyBlockTransactions(previousBlock)) {
            JSONObject tx = new JSONObject();
            tx.put("hash",Convert.toHexString(transaction.fullHash()));
            txs.add(tx);
        }
        result.put("extradata", forgersMerkleRoot);
        result.put("ecblockheight", ecBlock.getHeight());
        result.put("ecblockid", ecBlock.getId());
        result.put("transactions", txs);
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
