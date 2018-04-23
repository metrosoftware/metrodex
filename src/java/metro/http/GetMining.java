package metro.http;

import metro.Miner;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class GetMining extends APIServlet.APIRequestHandler {
    static final GetMining instance = new GetMining();

    private GetMining() {
        super(new APITag[] {APITag.MINING}, "secretPhrase", "adminPassword");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {
        JSONObject response = new JSONObject();
        fillMiningResponse(response);
        return response;
    }

    public void fillMiningResponse(JSONObject response) {
        long timeDiff = System.currentTimeMillis() - GetWork.instance.getLastGetWorkTime();
        if (timeDiff < 1000) {
            response.put("getworkIsQueried", true);
        } else {
            response.put("getworkIsQueried", false);
        }
        if (StringUtils.isNotEmpty(Miner.getSecretPhrase())) {
            response.put("secretPhrase", true);
        } else {
            response.put("secretPhrase", false);
        }
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
