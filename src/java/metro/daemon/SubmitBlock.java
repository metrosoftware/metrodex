package metro.daemon;

import metro.Block;
import metro.Metro;
import metro.MetroException;
import metro.TransactionImpl;
import metro.util.Convert;
import metro.util.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static metro.daemon.DaemonUtils.awareError;
import static metro.daemon.DaemonUtils.awareResult;

public class SubmitBlock implements DaemonRequestHandler {

    static SubmitBlock instance = new SubmitBlock();

    private SubmitBlock() {
    }


    @Override
    public JSONStreamAware process(DaemonRequest dReq) {

        String block = (String) dReq.getParams().get(0);
        byte[] blockHeaderBytes = Convert.parseHexString(block.substring(0, 196).toLowerCase(Locale.ROOT));
        List<TransactionImpl> txs = new ArrayList<>();
        try {
            TransactionImpl.BuilderImpl builder = TransactionImpl.newTransactionBuilder(Convert.parseHexString(block.substring(198, 626).toLowerCase(Locale.ROOT)));
            TransactionImpl coinbase = builder.build();
            txs.add(coinbase);
        } catch (MetroException.NotValidException e) {
            Logger.logErrorMessage("Block rejected", e);
            return awareError(-1, "Coinbase not valid. " + e.getMessage(), dReq.getId());
        }
        long time = getTimestamp(blockHeaderBytes);
        List<Long> txIds = TemplateCache.instance.get(time);
        if (txIds == null) {
            Logger.logErrorMessage("Block rejected. Time roll not allowed");
            return awareError(-1, "Time roll not allowed", dReq.getId());
        }
        for (Long txId: txIds) {
            txs.add((TransactionImpl)Metro.getTransactionProcessor().getUnconfirmedTransaction(txId));
        }
        Block extra = Metro.getBlockchainProcessor().composeKeyBlock(blockHeaderBytes, txs);
        boolean blockAccepted;
        try {
            blockAccepted = Metro.getBlockchainProcessor().processMyKeyBlock(extra);
        } catch (MetroException e) {
            Logger.logErrorMessage("Block rejected", e);
            return awareError(-1, e.getMessage(), dReq.getId());
        }
        if (!blockAccepted) {
            Logger.logErrorMessage("Block rejected. Blockhave not accepted");
            return awareError(-1, "Block have not accepted.", dReq.getId());
        }
        return awareResult((JSONObject) null, dReq.getId());
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
