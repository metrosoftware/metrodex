package metro.http;

import metro.Miner;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class StopMining extends APIServlet.APIRequestHandler {
    static final StopMining instance = new StopMining();

    private StopMining() {
        super(new APITag[] {APITag.FORGING}, "adminPassword");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        Miner.stopMining();
        JSONObject response = new JSONObject();
//        if (secretPhrase != null) {
//            Generator generator = Generator.stopForging(secretPhrase);
//            response.put("foundAndStopped", generator != null);
//            response.put("forgersCount", Generator.getGeneratorCount());
//        } else {
//            API.verifyPassword(req);
//            int count = Generator.stopForging();
//            response.put("stopped", count);
//        }
        response.put("stopped", "true");
        return response;
    }

    @Override
    protected boolean requirePost() {
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
