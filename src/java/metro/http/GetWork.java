package metro.http;

import metro.Block;
import metro.Metro;
import metro.MetroException;
import metro.Miner;
import metro.TransactionImpl;
import metro.crypto.Crypto;
import metro.util.Convert;
import metro.util.JSON;
import metro.util.Logger;
import org.apache.commons.lang3.ArrayUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class GetWork extends APIServlet.APIRequestHandler {

    static final GetWork instance = new GetWork();

    private long lastGetWorkTime;

    private AtomicReference<List<TransactionImpl>> transactions = new AtomicReference<>();

    private long lastTimestamp;
    byte[] cache;

    private GetWork() {
        super(new APITag[]{APITag.MINING});
    }

    public long getLastGetWorkTime() {
        return lastGetWorkTime;
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request) throws MetroException {
        lastGetWorkTime = System.currentTimeMillis();
        if (Metro.getBlockchainProcessor().isDownloading() || Metro.getBlockchainProcessor().isScanning()) {
            JSONObject response = new JSONObject();
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
            JSONObject response = new JSONObject();
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
                        byte[] generatorPublicKey = Crypto.getPublicKey(Miner.getSecretPhrase());
                        Block extra = Metro.getBlockchain().composeKeyBlock(blockHeaderBytes, generatorPublicKey, transactions.get());
                        boolean blockAccepted = Metro.getBlockchainProcessor().processMinerBlock(extra);
                        Logger.logDebugMessage("Solution found. Block Accepted:" + blockAccepted);
                        JSONObject response = new JSONObject();
                        response.put("result", blockAccepted);
                        return JSON.prepare(response);
                    }
                }
            }
        } catch (ParseException e) {
            Logger.logErrorMessage("Parse request error:", e);
        }

        Block block = Metro.getBlockchainProcessor().prepareKeyBlock();
        transactions.set((List<TransactionImpl>)block.getTransactions());
        //FIXME need to update cache on txlist or prev block change
        if (block.getTimestamp() - lastTimestamp > 1000) {
            cache = block.getBytes();
            lastTimestamp = block.getTimestamp();
        }
        byte[] blockBytes = padZeroValuesSpecialAndSize(cache);
        JSONObject response = new JSONObject();
        JSONObject result = new JSONObject();
        response.put("result", result);
        result.put("data", Convert.toHexString(blockBytes));
        String targetString = targetToLittleEndianString(block.getDifficultyTargetAsInteger());
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
