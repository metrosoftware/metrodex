package nxt.http;

import nxt.Block;
import nxt.BlockImpl;
import nxt.Consensus;
import nxt.Nxt;
import nxt.NxtException;
import nxt.util.Convert;
import nxt.util.JSON;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static nxt.http.JSONResponses.REPLACEMENT_BLOCK_IGNORED;
import static nxt.util.Convert.HASH_SIZE;

public class SubmitBlockSolution extends APIServlet.APIRequestHandler {

    static final SubmitBlockSolution instance = new SubmitBlockSolution();

    private SubmitBlockSolution() {
        super(new APITag[] {APITag.MINING}, "blockHeader", "blockSignature", "generatorPublicKey", "generationSequence");
    }

    private static final byte[] headerTemplate = Convert.parseHexString("0000000000000000e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    private static final byte[] testGeneratorPublicKey = Convert.parseHexString("1259ec21d31a30898d7cd1609f80d9668b4778e3d97e941044b39f0c44d2e51b");
    private static final String headerTemplatePart2 = "f39f29090a00000000000000";

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest request)
            throws NxtException {
        // TODO what if the BlockchainProcessor is in "scanning" or "processing block" mode?
        if (Nxt.getBlockchainProcessor().isDownloading()) {
            JSONObject response = new JSONObject();
            response.put("error", "Blockchain download in progress");
            return JSON.prepare(response);
        }
        String blockHeader = Convert.emptyToNull(request.getParameter("blockHeader"));
        String generatorPublicKeyHex = Convert.emptyToNull(request.getParameter("generatorPublicKey"));
        int keyBlockHeaderSize = BlockImpl.getHeaderSize(true, false);
        byte[] generatorPublicKey;
        if (blockHeader == null) {
            blockHeader = generateHeaderFromTemplate(keyBlockHeaderSize);
            generatorPublicKey = testGeneratorPublicKey;
        } else {
            if (generatorPublicKeyHex == null) {
                return JSONResponses.MISSING_BLOCK_GENERATOR;
            } else if (generatorPublicKeyHex.length() != HASH_SIZE * 2) {
                JSONObject response = new JSONObject();
                response.put("error", "Incorrect generatorPublicKey string, was " + generatorPublicKeyHex.length() + " but must be " + HASH_SIZE * 2 + " characters");
                return JSON.prepare(response);
            }
            generatorPublicKey = Convert.parseHexString(generatorPublicKeyHex);
        }
        if (blockHeader.length() != keyBlockHeaderSize * 2) {
            JSONObject response = new JSONObject();
            if (blockHeader.length() % 2 == 0) {
                response.put("error", "Wrong block header length, was " + blockHeader.length() / 2 + " rather than " + keyBlockHeaderSize + " bytes");
            } else {
                response.put("error", "Incorrect hex string length, was " + blockHeader.length() + " but must be even");
            }
            return JSON.prepare(response);
        }
        byte[] headerBytes = Convert.parseHexString(blockHeader.toLowerCase());
        String blockSignature = Convert.emptyToNull(request.getParameter("blockSignature"));
        // TODO #144 validate length of the signature
        byte[] signatureBytes = Convert.parseHexString(blockSignature != null ? blockSignature.toLowerCase() : null);
        Block extra = Nxt.getBlockchain().composeKeyBlock(headerBytes, generatorPublicKey);
        boolean blockAccepted = Nxt.getBlockchainProcessor().processMinerBlock(extra, signatureBytes);
        // Return JSON block representation or "Replacement block failed to be accepted" error
        return blockAccepted ? extra.getJSONObject() : REPLACEMENT_BLOCK_IGNORED;
    }

    public static String generateHeaderFromTemplate(int keyBlockHeaderSize) {
        // auto-fill with data from last blocks
        Block lastBlock = Nxt.getBlockchain().getLastBlock();
        Block lastKeyBlock = Nxt.getBlockchain().getLastKeyBlock();
        // 218 is full header length; headerTemplatePart2 will be appended by String concatenation
        ByteBuffer buffer = ByteBuffer.allocate(keyBlockHeaderSize - headerTemplatePart2.length() / 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        // version
        buffer.putShort(Consensus.getKeyBlockVersion(lastBlock.getHeight()));

        // timestamp
        buffer.putLong(Nxt.getEpochTime());

        // template part #1
        buffer.put(headerTemplate);
        // prev block hashes
        byte[] previousBlockHash = Consensus.HASH_FUNCTION.hash(lastBlock.getBytes());
        buffer.put(previousBlockHash);
        if (lastKeyBlock != null) {
            buffer.put(Consensus.HASH_FUNCTION.hash(lastKeyBlock.getBytes()));
        } else {
            buffer.put(Convert.EMPTY_HASH);
        }

        // template part #2 (Merkle roots, baseTarget, nonce)
        return Convert.toHexString(buffer.array()) + headerTemplatePart2;
    }
    /* example hex for a keyBlock:
    0180710879010000000000000000
    e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
    1259ec21d31a30898d7cd1609f80d9668b4778e3d97e941044b39f0c44d2e51b
    4e52c06e1fd42d403492fd3f23d4556ac6088ed2867d92a4d4f02975b732ada1
    2e46f6ffdf11ae63645938a59d000e08fdcb307c69727a57efa4b90b556a16ed
    0000000000000000000000000000000000000000000000000000000000000000
    0000000000000000000000000000000000000000000000000000000000000000
    F39F29090a00000000000000
    in one line:
    018026858F010000000000000000e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b8551259ec21d31a30898d7cd1609f80d9668b4778e3d97e941044b39f0c44d2e51b5f7f66720a4d19c9478306f8fdb2f0860afe373fb91e9661344cfc0d133fed905f7f66720a4d19c9478306f8fdb2f0860afe373fb91e9661344cfc0d133fed9000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000F39F29090a00000000000000
    0180d6cd94010000000000000000e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b85572a38beca125651562f8e5e30ec277def090bb7bafbc0b36c1d9b2e8930c65de69626bd6740c9a57dc63b138a4e6f6d7be0233d1b8e0e9968b2ca20bc156ac57000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000f39f29090a00000000000000
    0180ca3af43c060000000000000000000000e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855de47e98d9f5500082dc8f86fb7272f8f86da95f6e6b9743577ed11c819f80e3fee4731c76f8855694124670c46b1f0a167f1557edce003c1bd8cbade984df59e0000000000000000000000000000000000000000000000000000000000000000f39f29090a00000000000000
     */

    @Override
    protected final boolean requirePost() {
        return true;
    }

    @Override
    protected boolean requirePassword() {
        return true;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireFullClient() {
        return true;
    }

}
