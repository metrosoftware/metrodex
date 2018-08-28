package metro.daemon;

import metro.Block;
import metro.Metro;
import metro.MetroException;
import metro.Miner;
import metro.TransactionImpl;
import metro.util.Convert;
import metro.util.JSON;
import metro.util.Logger;
import org.apache.commons.lang3.ArrayUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class GetWork implements DaemonRequestHandler {

    public static final GetWork instance = new GetWork();

    private static final int CACHE_SIZE = 15;

    private final long transactionsCacheDuration = Metro.getIntProperty("metro.transactionsCacheDuration");
    private final long blockCacheDuration = Metro.getIntProperty("metro.blockCacheDuration");

    private long lastGetWorkTime;
    private long lastTxTime;

    private volatile List<TransactionImpl>[] transactions = new List[CACHE_SIZE];
    private volatile String[] merkles = new String[CACHE_SIZE];
    private AtomicInteger index = new AtomicInteger(0);

    private long lastTimestamp;
    private byte[] cache;
    private long cachedLastBlockId;
    private BigInteger cachedTarget;

    public long getLastGetWorkTime() {
        return lastGetWorkTime;
    }

    private GetWork() {
    }
    
    @Override
    public JSONStreamAware process(DaemonRequest dReq) {
        return process(dReq, null);
    }

    @Override
    public JSONStreamAware process(DaemonRequest dReq, Object options) {
        try {
            String host = (options == null || !(options instanceof String)) ? null : (String) options;
            JSONObject response = processGetWork(dReq, host);
            response.put("error", null);
            response.put("id", dReq.getId());
            return JSON.prepare(response);
        } catch (MetroException e) {
            JSONObject response = new JSONObject();
            response.put("error", e.getMessage());
            response.put("id", dReq.getId());
            return JSON.prepare(response);
        }
    }

    public JSONObject processGetWork(DaemonRequest dReq, String host) throws MetroException {
        JSONObject response = new JSONObject();
        if (Miner.getPublicKey() == null) {
            response.put("error", "Set metro.mine.publicKey property in conf/metro.properties on " + host);
            return response;
        }
        lastGetWorkTime = System.currentTimeMillis();
        if (Metro.getBlockchainProcessor().isDownloading() || Metro.getBlockchainProcessor().isScanning()) {
            response.put("error", "Blockchain scan or download in progress");
            return response;
        }

        if (dReq.getParams() != null && !dReq.getParams().isEmpty()) {
            return processWorkSubmit(dReq.getParams());
        }
        return processWorkGet();
    }

    private JSONObject processWorkGet() {
        JSONObject response = new JSONObject();
        synchronized (this) {
            long lastBlockId = Metro.getBlockchain().getLastBlock().getId();
            long currentTime = System.currentTimeMillis();
            if (cachedLastBlockId != lastBlockId || currentTime - lastTxTime > Math.min(transactionsCacheDuration, 5000)) {
                lastTxTime = currentTime;
                Block block = Metro.getBlockchainProcessor().prepareKeyBlockTemplate(null);
                cache = block.getBytes();
                cachedLastBlockId = lastBlockId;
                cachedTarget = block.getDifficultyTargetAsInteger();
                int i = index.getAndUpdate(j -> (j + 1) % CACHE_SIZE);
                transactions[i] = (List<TransactionImpl>) block.getTransactions();
                merkles[i] = Convert.toHexString(block.getTxMerkleRoot());
                lastTimestamp = currentTime;
            } else if (currentTime - lastTimestamp > Math.min(blockCacheDuration, 1000)) {
                int i = index.get();
                Block block = Metro.getBlockchainProcessor().prepareKeyBlockTemplate(transactions[i]);
                i = index.getAndUpdate(j -> (j + 1) % CACHE_SIZE);
                transactions[i] = (List<TransactionImpl>) block.getTransactions();
                merkles[i] = Convert.toHexString(block.getTxMerkleRoot());
                cache = block.getBytes();
                lastTimestamp = currentTime;
            } else {
                //Logger.logInfoMessage("Return cached work");
            }
        }
        byte[] blockBytes = padZeroValuesSpecialAndSize(cache);
        JSONObject result = new JSONObject();
        response.put("result", result);
        result.put("data", Convert.toHexString(blockBytes));
        String targetString = targetToLittleEndianString(cachedTarget);
        result.put("target", targetString);
        return response;

    }

    private JSONObject processWorkSubmit(JSONArray params) throws MetroException {
        synchronized (this) {
            JSONObject response = new JSONObject();

            Logger.logDebugMessage("getwork params:" + params.toString());
            String blockHeader = (String) params.get(0);
            String merkle = blockHeader.substring(20, 84);
            byte[] blockHeaderBytes = Convert.parseHexString(blockHeader.toLowerCase(Locale.ROOT));
            List<TransactionImpl> txs = findTxsByMerkle(merkle);
            if (txs == null) {
                Logger.logErrorMessage("Too small get work cache.");
                response.put("error", "Too small get work cache.");
                return response;
            }
            Block extra = Metro.getBlockchainProcessor().composeKeyBlock(blockHeaderBytes, txs);
            boolean blockAccepted = Metro.getBlockchainProcessor().processMyKeyBlock(extra);
            Logger.logDebugMessage("Solution found. Block Accepted:" + blockAccepted);
            response.put("result", blockAccepted);
            return response;
        }
    }

    private List<TransactionImpl> findTxsByMerkle(String merkle) {
        int i = index.get();
        for (int j = i + CACHE_SIZE; j > i; j--) {
            if (merkles[j % CACHE_SIZE] != null && merkles[j % CACHE_SIZE].equals(merkle)) {
                return transactions[j % CACHE_SIZE];
            }
        }
        return null;
    }

    private byte[] padZeroValuesSpecialAndSize(byte[] blockBytes) {
        byte[] result = Arrays.copyOf(blockBytes, 128);
        int blockBitsSize = blockBytes.length * 8;
        byte[] blockBitsSizeBytes = ByteBuffer.allocate(8).putLong(blockBitsSize).array();
        for (int i = 0; i < blockBitsSizeBytes.length; i++) {
            result[result.length - 1 - i] = blockBitsSizeBytes[blockBitsSizeBytes.length - 1 - i];
        }
        return result;
    }

    private String targetToLittleEndianString(BigInteger value) {
        byte[] bytes = value.toByteArray();
        ArrayUtils.reverse(bytes);
        byte[] target = Arrays.copyOf(bytes, 32);
        return Convert.toHexString(target);
    }
}
