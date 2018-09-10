package metro.daemon;

import metro.Miner;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import static metro.daemon.DaemonUtils.awareResult;

public class ValidateAddress implements DaemonRequestHandler {

    static ValidateAddress instance = new ValidateAddress();

    private ValidateAddress() {
    }

    @Override
    public JSONStreamAware process(DaemonRequest dReq) {
        String publicKey = (String) dReq.getParams().get(0);
        boolean valid = publicKey.length() == 64;
        boolean ismine = Miner.getPublicKey().equals(publicKey);
        JSONObject result = new JSONObject();
        result.put("isvalid", valid);
        result.put("address", publicKey);
        result.put("ismine", ismine);
        return awareResult(result, dReq.getId());
    }

    @Override
    public JSONStreamAware process(DaemonRequest request, Object options) {
        return process(request);
    }
}
