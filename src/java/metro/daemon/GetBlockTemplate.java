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
import org.apache.commons.lang3.ArrayUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static metro.Consensus.HASH_FUNCTION;
import static metro.daemon.DaemonUtils.awareResult;

public class GetBlockTemplate implements DaemonRequestHandler {

    static final GetBlockTemplate instance = new GetBlockTemplate();

    private final long blockCacheDuration = Metro.getIntProperty("metro.blockCacheDuration");

    private JSONObject result = null;
    private long lastRequest = 0;

    private GetBlockTemplate() {
    }

    @Override
    public JSONStreamAware process(DaemonRequest dReq) {
        synchronized (instance) {
            if (lastRequest + Math.min(blockCacheDuration, 10000) > Calendar.getInstance().getTimeInMillis()) {
                return awareResult(result, dReq.getId());
            }
            BlockchainImpl blockchain = BlockchainImpl.getInstance();
            BlockchainProcessorImpl blockchainProcessor = BlockchainProcessorImpl.getInstance();
            BlockImpl previousBlock = blockchain.getLastBlock();
            BlockImpl previousKeyBlock = blockchain.getLastKeyBlock();
            int keyHeight = previousKeyBlock != null ? previousKeyBlock.getLocalHeight() + 1 : 0;
            long previousBlockId = previousBlock.getId();
            long previousKeyBlockId = previousKeyBlock == null ? 0 : previousKeyBlock.getId();
            long time = Metro.getEpochTime();
            Block ecBlock = BlockchainImpl.getInstance().getECBlock(time);
            byte[] forgersMerkleRoot = HASH_FUNCTION.hash(ArrayUtils.addAll(HASH_FUNCTION.hash(previousBlock.getBytes()), blockchainProcessor.getForgersMerkleAtLastKeyBlock()));
            byte[] bits = Convert.toBytes(Target.nextTarget(previousKeyBlock));
            ArrayUtils.reverse(bits);

            result = new JSONObject();
            result.put("height", keyHeight);
            result.put("version", Consensus.getPreferableKeyBlockVersion(keyHeight + 1));
            result.put("curtime", time);
            result.put("workid", time);
            result.put("bits", Convert.toHexString(bits));
            result.put("coinbaseaux", new JSONObject());
            ByteBuffer buffer = ByteBuffer.allocate(16);
            buffer.putLong(previousKeyBlockId);
            buffer.putLong(previousBlockId);
            result.put("previousblockhash", Convert.toHexString(buffer.array()));
            result.put("coinbasevalue", Consensus.getBlockSubsidy(previousKeyBlock == null ? 0 : previousKeyBlock.getLocalHeight()));
            JSONArray txs = new JSONArray();
            List<Long> template = new ArrayList<>();
            for (TransactionImpl transaction : Metro.getBlockchainProcessor().prepareKeyBlockTransactions(previousBlock)) {
                JSONObject tx = new JSONObject();
                tx.put("hash", Convert.toHexString(transaction.fullHash()));
                txs.add(tx);
                template.add(transaction.getId());
            }
            TemplateCache.instance.put(time, template);
            result.put("extradata", Convert.toHexString(forgersMerkleRoot));
            result.put("ecblockheight", ecBlock.getHeight());
            result.put("ecblockid", ecBlock.getId());
            result.put("transactions", txs);
            lastRequest = Calendar.getInstance().getTimeInMillis();
            return awareResult(result, dReq.getId());
        }
    }

    @Override
    public JSONStreamAware process(DaemonRequest request, Object options) {
        return process(request);
    }
}
