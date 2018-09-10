package metro.daemon;

import metro.Blockchain;
import metro.Consensus;
import metro.Constants;
import metro.Metro;
import metro.Transaction;
import metro.util.JSON;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

public class GetTransaction implements DaemonRequestHandler {

    static GetTransaction instance = new GetTransaction();

    private GetTransaction() {
    }
    
    @Override
    public JSONStreamAware process(DaemonRequest dReq) {
        //byte[] hash = Convert.parseHexString((String) dReq.getParams().get(0));
        //ArrayUtils.reverse(hash);

        JSONObject result = new JSONObject();

        Blockchain blockchain = Metro.getBlockchain();
        Transaction transaction = blockchain.getTransactionByFullHash((String) dReq.getParams().get(0));

        if (transaction.getType().isCoinbase()) {
            result.put("confirmations", blockchain.getLastKeyBlock().getLocalHeight() - transaction.getBlock().getLocalHeight());
            JSONObject fields = new JSONObject();
            fields.put("amount", Consensus.getBlockSubsidy(transaction.getBlock().getLocalHeight()) / Constants.ONE_MTR);
            fields.put("category", "generate");
            JSONArray details = new JSONArray();
            details.add(fields);
            result.put("details", details);
        }
        JSONObject response = new JSONObject();
        response.put("result", result);
        response.put("error", null);
        response.put("id", dReq.getId());
        return JSON.prepare(response);
    }

    @Override
    public JSONStreamAware process(DaemonRequest request, Object options) {
        return process(request);
    }
}
