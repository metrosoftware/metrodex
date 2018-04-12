package metro.http;

import metro.Block;
import metro.BlockImpl;
import metro.Metro;
import metro.MetroException;
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

    private final static String secretPhrase = Convert.emptyToNull(Metro.getStringProperty("metro.mine.secretPhrase"));
    private AtomicReference<List<TransactionImpl>> transactions = new AtomicReference<>();

    private GetWork() {
        super(new APITag[]{APITag.MINING});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request) throws MetroException {

        //TODO ticket #180
        if (Metro.getBlockchainProcessor().isDownloading()) {
            JSONObject response = new JSONObject();
            response.put("error", "Blockchain download in progress");
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
                        int keyBlockHeaderSize = BlockImpl.getHeaderSize(true, false);
                        if (keyBlockHeaderSize != blockHeaderBytes.length) {
                            throw new IllegalStateException("Invalid block header length:" + blockHeader);
                        }
                        //TODO ticket #177 read secretPhrase as forging do
                        byte[] generatorPublicKey = Crypto.getPublicKey(secretPhrase);
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

        byte[] blockBytes = reverseEvery4Bytes(padZeroValuesSpecialAndSize(block.getBytes()));
        JSONObject response = new JSONObject();
        JSONObject result = new JSONObject();
        response.put("result", result);
        result.put("data", Convert.toHexString(blockBytes));
        String targetString = targetToLittleEndianString(block.getDifficultyTargetAsInteger());
        result.put("target", targetString);
        return JSON.prepare(response);
    }

    private byte[] padZeroValuesSpecialAndSize(byte[] blockBytes) {
        byte[] result = Arrays.copyOf(blockBytes, 256);
        int blockBitsSize =  blockBytes.length*8;
        byte[] blockBitsSizeBytes = ByteBuffer.allocate(8).putLong(blockBitsSize).array();
        for (int i = 0; i< blockBitsSizeBytes.length; i++) {
            result[result.length-1-i] = blockBitsSizeBytes[blockBitsSizeBytes.length-1-i];
        }
        return result;
    }

    public static byte[] reverseEvery4Bytes(byte[] b) {
        for(int i=0; i < b.length/4; i++) {
            byte temp = b[i*4];
            b[i*4] = b[i*4+3];
            b[i*4+3] = temp;
            temp = b[i*4+1];
            b[i*4+1] = b[i*4+2];
            b[i*4+2] = temp;
        }
        return b;
    }

    private String targetToLittleEndianString(BigInteger value) {
        byte[] bytes = value.toByteArray();
        ArrayUtils.reverse(bytes);
        return Convert.toHexString(bytes);
    }

}
