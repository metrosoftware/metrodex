package metro.daemon;

import metro.Miner;
import metro.util.JSON;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

public class GetAccountAddress implements DaemonRequestHandler {

    static GetAccountAddress instance = new GetAccountAddress();

    private GetAccountAddress() {
    }
    
    @Override
    public JSONStreamAware process(DaemonRequest dReq) {
        JSONObject response = new JSONObject();
        response.put("result", Miner.getPublicKey());
        response.put("error", null);
        response.put("id", dReq.getId());
        return JSON.prepare(response);
    }

    @Override
    public JSONStreamAware process(DaemonRequest request, Object options) {
        return process(request);
    }
}
