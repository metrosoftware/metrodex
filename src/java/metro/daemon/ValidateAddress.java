package metro.daemon;

import metro.Miner;
import metro.util.JSON;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

public class ValidateAddress implements DaemonRequestHandler {

    static ValidateAddress instance = new ValidateAddress();

    private ValidateAddress() {
    }

    @Override
    public JSONStreamAware process(DaemonRequest dReq) {
        JSONObject response = new JSONObject();
        String publicKey = (String) dReq.getParams().get(0);
        boolean valid = publicKey.length() == 64;
        boolean ismine = Miner.getPublicKey().equals(publicKey);
        JSONObject result = new JSONObject();
        result.put("isvalid", valid);
        result.put("address", publicKey);
        result.put("ismine", ismine);
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
