package metro.daemon;

import metro.Block;
import metro.Consensus;
import metro.Metro;
import metro.MetroException;
import metro.Miner;
import metro.TransactionImpl;
import metro.util.Convert;
import metro.util.JSON;
import metro.util.Logger;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jetty.server.Request;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.parser.ParseException;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static metro.daemon.DaemonUtils.awareError;
import static metro.daemon.DaemonUtils.awareResult;

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
            return processGetWork(dReq, host);
        } catch (MetroException e) {
            JSONObject response = new JSONObject();
            response.put("error", e.getMessage());
            response.put("id", dReq.getId());
            return JSON.prepare(response);
        }
    }

    public JSONStreamAware processGetWork(HttpServletRequest req) throws MetroException {
        JSONObject response = new JSONObject();
        String content = "";
        try {
            if (req.getReader() != null) {
                content = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
            }
        } catch (IOException e) {
            return awareError(e.getMessage(), null);
        }
        try {
            DaemonRequest dReq;
            if (content.length() > 0) {
                dReq = DaemonRequest.init(content);
            } else {
                dReq = new DaemonRequest("getwork", null, new JSONArray());
            }
            return process(dReq, ((Request) req).getMetaData().getURI().getHost());
        } catch (ParseException e) {
            e.printStackTrace();
            return awareError(e.getMessage(), null);
        }
    }

    public JSONStreamAware processGetWork(DaemonRequest dReq, String host) throws MetroException {
        JSONObject response = new JSONObject();
        if (Miner.getPublicKey() == null) {
            return awareError("Set metro.mine.publicKey property in conf/metro.properties on " + host, dReq.getId());
        }
        lastGetWorkTime = System.currentTimeMillis();
        if (Metro.getBlockchainProcessor().isDownloading() || Metro.getBlockchainProcessor().isScanning()) {
            return awareError("Blockchain scan or download in progress", dReq.getId());
        }

        if (dReq.getParams() != null && !dReq.getParams().isEmpty()) {
            return processWorkSubmit(dReq.getParams(), dReq.getId());
        }
        return processWorkGet(dReq.getId());
    }

    private JSONStreamAware processWorkGet(String id) {
        JSONObject response = new JSONObject();
        synchronized (this) {
            long lastBlockId = Metro.getBlockchain().getLastBlock().getId();
            long currentTime = System.currentTimeMillis();
            if (cachedLastBlockId != lastBlockId || currentTime - lastTxTime > Math.min(transactionsCacheDuration, 10000)) {
                lastTxTime = currentTime;
                Block block = Metro.getBlockchainProcessor().prepareKeyBlockTemplate(null);
                cache = block.getBytes();
                cachedLastBlockId = lastBlockId;
                cachedTarget = block.getDifficultyTargetAsInteger();
                int i = index.getAndUpdate(j -> (j + 1) % CACHE_SIZE);
                transactions[i] = (List<TransactionImpl>) block.getTransactions();
                merkles[i] = Convert.toHexString(block.getTxMerkleRoot());
                lastTimestamp = currentTime;
            } else if (currentTime - lastTimestamp > Math.min(blockCacheDuration, 3000)) {
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
        result.put("data", Convert.toHexString(blockBytes));
        String targetString = targetToLittleEndianString(cachedTarget);
        result.put("target", targetString);
        return DaemonUtils.awareResult(result, id);

    }

    private JSONStreamAware processWorkSubmit(JSONArray params, String id) throws MetroException {
        synchronized (this) {
            JSONObject response = new JSONObject();

            Logger.logDebugMessage("getwork params:" + params.toString());
            String blockHeader = (String) params.get(0);
            short version = getVersion(Convert.parseHexString(blockHeader));
            String merkle = version < Consensus.STRATUM_COMPATIBILITY_BLOCK ? blockHeader.substring(20, 84) : blockHeader.substring(36, 100);
            byte[] blockHeaderBytes = Convert.parseHexString(blockHeader.toLowerCase(Locale.ROOT));
            List<TransactionImpl> txs = findTxsByMerkle(merkle);
            if (txs == null) {
                Logger.logErrorMessage("Too small get work cache.");
                return awareError("Too small get work cache.", id);
            }
            Block extra = Metro.getBlockchainProcessor().composeKeyBlock(blockHeaderBytes, txs);
            boolean blockAccepted = Metro.getBlockchainProcessor().processMyKeyBlock(extra);
            Logger.logDebugMessage("Solution found. Block Accepted:" + blockAccepted);
            return awareResult(blockAccepted, id);
        }
    }

    private short getVersion(byte[] blockHeaderBytes) {
        ByteBuffer buffer = ByteBuffer.wrap(blockHeaderBytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        return buffer.getShort();
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
