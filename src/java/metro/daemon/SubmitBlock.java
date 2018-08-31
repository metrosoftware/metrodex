package metro.daemon;

import metro.Block;
import metro.Metro;
import metro.MetroException;
import metro.TransactionImpl;
import metro.util.Convert;
import metro.util.JSON;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SubmitBlock implements DaemonRequestHandler {

    static SubmitBlock instance = new SubmitBlock();

    private SubmitBlock() {
    }

    private JSONObject jsonError(int code, String message) {
        JSONObject result = new JSONObject();
        result.put("code", code);
        result.put("message", message);
        return result;
    }

    @Override
    public JSONStreamAware process(DaemonRequest dReq) {
        JSONObject response = new JSONObject();
        String block = (String) dReq.getParams().get(0);
        byte[] blockHeaderBytes = Convert.parseHexString(block.substring(0, 196).toLowerCase(Locale.ROOT));
        List<TransactionImpl> txs = new ArrayList<>();
        try {
            TransactionImpl.BuilderImpl builder = TransactionImpl.newTransactionBuilder(Convert.parseHexString(block.substring(198, 626).toLowerCase(Locale.ROOT)));
            TransactionImpl coinbase = builder.build();
            txs.add(coinbase);
        } catch (MetroException.NotValidException e) {
            response.put("result", null);
            response.put("error", jsonError(-1, "Coinbase not valid. " + e.getMessage()));
            response.put("id", dReq.getId());
            return JSON.prepare(response);
        }
        long time = getTimestamp(blockHeaderBytes);
        List<Long> txIds = TemplateCache.instance.get(time);
        if (txIds == null) {
            response.put("result", null);
            response.put("error", jsonError(-1, "Time roll not allowed"));
            response.put("id", dReq.getId());
            return JSON.prepare(response);
        }
        for (Long txId: txIds) {
            txs.add((TransactionImpl)Metro.getTransactionProcessor().getUnconfirmedTransaction(txId));
        }
        Block extra = Metro.getBlockchainProcessor().composeKeyBlock(blockHeaderBytes, txs);
        boolean blockAccepted;
        try {
            blockAccepted = Metro.getBlockchainProcessor().processMyKeyBlock(extra);
        } catch (MetroException e) {
            response.put("result", null);
            response.put("error", jsonError(-1, e.getMessage()));
            response.put("id", dReq.getId());
            return JSON.prepare(response);
        }
        response.put("result", null);
        response.put("error", blockAccepted ? null : jsonError(-1, "Block not accepted."));
        response.put("id", dReq.getId());
        return JSON.prepare(response);
    }

    private long getTimestamp(byte[] blockHeaderBytes) {
        ByteBuffer buffer = ByteBuffer.wrap(blockHeaderBytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(82);
        return buffer.getLong();
    }

    @Override
    public JSONStreamAware process(DaemonRequest request, Object options) {
        return process(request);
    }
}
