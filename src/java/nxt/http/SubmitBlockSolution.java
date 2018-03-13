package nxt.http;

import nxt.Block;
import nxt.Nxt;
import nxt.NxtException;
import nxt.crypto.Crypto;
import nxt.util.Convert;
import nxt.util.JSON;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static nxt.http.JSONResponses.REPLACEMENT_BLOCK_IGNORED;

public class SubmitBlockSolution extends APIServlet.APIRequestHandler {

    static final SubmitBlockSolution instance = new SubmitBlockSolution();

    private SubmitBlockSolution() {
        super(new APITag[] {APITag.MINING}, "blockHeader", "blockSignature");
    }

    private static final byte[] headerTemplate = Convert.parseHexString("0000000000000000e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b8551259ec21d31a30898d7cd1609f80d9668b4778e3d97e941044b39f0c44d2e51b");

    private static final String headerTemplatePart2 = "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000F39F29090a00000000000000";

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
        if (blockHeader == null) {
            // auto-fill with data from last blocks
            Block lastBlock = Nxt.getBlockchain().getLastBlock();
            Block lastKeyBlock = Nxt.getBlockchain().getLastKeyBlock();
            ByteBuffer buf = ByteBuffer.allocate(218 - 76);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            // version
            buf.putShort((short)-32767);

            // timestamp
            buf.putInt(Nxt.getEpochTime());

            // template part #1
            buf.put(headerTemplate);
            // prev block hashes
            byte[] previousBH = Crypto.sha256().digest(lastBlock.getBytes());
            buf.put(previousBH);
            if (lastKeyBlock.getNextBlockId() != 0) {
                buf.put(Nxt.getBlockchain().getBlock(lastKeyBlock.getNextBlockId()).getPreviousBlockHash());
            } else {
                buf.put(previousBH);
            }

            // template part #2 (Merkle roots, baseTarget, nonce)
            blockHeader = Convert.toHexString(buf.array()) + headerTemplatePart2;
        }
        if (blockHeader.length() != 436) {
            JSONObject response = new JSONObject();
            response.put("error", "Wrong block header length, was " + blockHeader.length() / 2 + " rather than 218 bytes");
            return JSON.prepare(response);
        }
        byte[] headerBytes = Convert.parseHexString(blockHeader.toLowerCase());
        String blockSignature = Convert.emptyToNull(request.getParameter("blockSignature"));
        // TODO #144 validate length of the signature
        byte[] signatureBytes = Convert.parseHexString(blockSignature != null ? blockSignature.toLowerCase() : null);
        Block extra = Nxt.getBlockchain().parseBlockHeader(headerBytes);
        boolean blockAccepted = Nxt.getBlockchainProcessor().processMinerBlock(extra, signatureBytes);
        // Return JSON block representation or "Replacement block failed to be accepted" error
        return blockAccepted ? extra.getJSONObject() : REPLACEMENT_BLOCK_IGNORED;
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
    0180694782010000000000000000e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b8551259ec21d31a30898d7cd1609f80d9668b4778e3d97e941044b39f0c44d2e51be437cbb0b9216e2df0fa0b5b97b5697d25f7d642d151717678a32a69ca12cdc9259c7fed3feeb0b860a7db2145a9b5921f8409f310a14780c1c7ccf23c44e1e400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000F39F29090a00000000000000

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
