package nxt.http;

import nxt.Block;
import nxt.BlockImpl;
import nxt.Consensus;
import nxt.Nxt;
import nxt.NxtException;
import nxt.crypto.Crypto;
import nxt.util.BitcoinJUtils;
import nxt.util.Convert;
import nxt.util.JSON;
import nxt.util.Logger;
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
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class GetWork extends APIServlet.APIRequestHandler {

    static final GetWork instance = new GetWork();

    private final static String secretPhrase = Convert.emptyToNull(Nxt.getStringProperty("nxt.mine.secretPhrase"));

    private GetWork() {
        super(new APITag[]{APITag.MINING});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request) throws NxtException {

        //TODO ticket #180
        if (Nxt.getBlockchainProcessor().isDownloading()) {
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
                        Block extra = Nxt.getBlockchain().composeKeyBlock(blockHeaderBytes, generatorPublicKey);
                        boolean blockAccepted = Nxt.getBlockchainProcessor().processMinerBlock(extra);
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

        Block block = Nxt.getBlockchainProcessor().prepareMinerBlock();
        byte[] blockBytes = reverseEvery4Bytes(padZeroValuesSpecialAndSize(block.getBytes()));
        JSONObject response = new JSONObject();
        JSONObject result = new JSONObject();
        response.put("result", result);
        result.put("data", Convert.toHexString(blockBytes));

        ByteBuffer buffer;
        Block lastKeyBlock = Nxt.getBlockchain().getLastKeyBlock();
        String targetString;
        if (lastKeyBlock == null) {
            targetString = targetToLittleEndianString(Consensus.MAX_WORK_TARGET);
        } else {
            //TODO calculate target correctly, ticket #149
            buffer = ByteBuffer.allocate(32);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(BitcoinJUtils.decodeCompactBits(lastKeyBlock.getBaseTarget()).toByteArray());
            targetString = Convert.toHexString(buffer.array());
        }
        result.put("target", lastKeyBlock == null ? targetToLittleEndianString(Consensus.MAX_WORK_TARGET)
                : targetString);
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
