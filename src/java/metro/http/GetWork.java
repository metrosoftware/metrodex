package metro.http;

import metro.Block;
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
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class GetWork extends APIServlet.APIRequestHandler {

    static final GetWork instance = new GetWork();

    private final long transactionsCacheDuration = Metro.getIntProperty("metro.transactionsCacheDuration");
    private final long blockCacheDuration = Metro.getIntProperty("metro.blockCacheDuration");

    private long lastGetWorkTime;
    private long lastTxTime;

    private AtomicReference<TransactionImpl> coinbase = new AtomicReference<>();
    private AtomicReference<List<TransactionImpl>> transactions = new AtomicReference<>();

    private long lastTimestamp;
    private byte[] cache;
    private long cachedLastBlockId;
    private BigInteger cachedTarget;


    private GetWork() {
        super(new APITag[]{APITag.MINING});
    }

    public long getLastGetWorkTime() {
        return lastGetWorkTime;
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request) throws MetroException {
        JSONObject response = new JSONObject();
        if (Miner.getPublicKey() == null) {
            response.put("error", "Set metro.mine.publicKey property in conf/metro.properties on "+ ((Request) request).getMetaData().getURI().getHost());
            return JSON.prepare(response);
        }
        lastGetWorkTime = System.currentTimeMillis();
        if (Metro.getBlockchainProcessor().isDownloading() || Metro.getBlockchainProcessor().isScanning()) {
            response.put("error", "Blockchain scan or download in progress");
            return JSON.prepare(response);
        }

        String content = "";
        try {
            if (request.getReader() != null) {
                content = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
                Logger.logDebugMessage("getwork:" + content);
            }
        } catch (IOException e) {
            response.put("error", e.getMessage());
            return JSON.prepare(response);
        }
        try {
            if (content.length() > 0) {
                JSONObject requestJSON = (JSONObject) (new JSONParser()).parse(content);
                if (requestJSON.containsKey("params")) {
                    JSONArray params = (JSONArray) requestJSON.get("params");
                    if (!params.isEmpty()) {
                        String blockHeader = (String) params.get(0);
                        byte[] blockHeaderBytes = Convert.parseHexString(blockHeader.toLowerCase());
                        //TODO ticket #177 read secretPhrase as forging do
                        byte[] generatorPublicKey = Convert.parseHexString(Miner.getPublicKey());
                        List<TransactionImpl> fullTxList = new ArrayList<>();
                        fullTxList.add(coinbase.get());
                        fullTxList.addAll(transactions.get());
                        Block extra = Metro.getBlockchain().composeKeyBlock(blockHeaderBytes, generatorPublicKey, fullTxList);
                        boolean blockAccepted = Metro.getBlockchainProcessor().processKeyBlock(extra);
                        Logger.logDebugMessage("Solution found. Block Accepted:" + blockAccepted);
                        response.put("result", blockAccepted);
                        return JSON.prepare(response);
                    }
                }
            }
        } catch (ParseException e) {
            Logger.logErrorMessage("Parse request error:", e);
        }

        long lastBlockId = Metro.getBlockchain().getLastBlock().getId();
        long currentTime = System.currentTimeMillis();
        if (cachedLastBlockId != lastBlockId || currentTime - lastTxTime > Math.min(transactionsCacheDuration, 5000)) {
            lastTxTime = currentTime;
            Block block = Metro.getBlockchainProcessor().prepareKeyBlock(null);
            cache = block.getBytes();
            cachedLastBlockId = lastBlockId;
            cachedTarget = block.getDifficultyTargetAsInteger();
            coinbase.set((TransactionImpl) block.getTransactions().get(0));
            transactions.set((List<TransactionImpl>)block.getTransactions().subList(1, block.getTransactions().size()));
            lastTimestamp = currentTime;
        } else if (currentTime - lastTimestamp > Math.min(blockCacheDuration, 1000)) {
            Block block = Metro.getBlockchainProcessor().prepareKeyBlock(transactions.get());
            coinbase.set((TransactionImpl) block.getTransactions().get(0));
            cache = block.getBytes();
            lastTimestamp = currentTime;
        } else {
            System.out.println("Return cached work");
        }

        byte[] blockBytes = padZeroValuesSpecialAndSize(cache);
        JSONObject result = new JSONObject();
        response.put("result", result);
        result.put("data", Convert.toHexString(blockBytes));
        //TODO is this correct block instead of cached block version?
        String targetString = targetToLittleEndianString(cachedTarget);
        result.put("target", targetString);
        return JSON.prepare(response);
    }

    private byte[] padZeroValuesSpecialAndSize(byte[] blockBytes) {
        byte[] result = Arrays.copyOf(blockBytes, 128);
        int blockBitsSize =  blockBytes.length*8;
        byte[] blockBitsSizeBytes = ByteBuffer.allocate(8).putLong(blockBitsSize).array();
        for (int i = 0; i< blockBitsSizeBytes.length; i++) {
            result[result.length-1-i] = blockBitsSizeBytes[blockBitsSizeBytes.length-1-i];
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
