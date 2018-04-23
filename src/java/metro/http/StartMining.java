package metro.http;

import metro.Generator;
import metro.MetroException;
import metro.Miner;
import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class StartMining extends APIServlet.APIRequestHandler {
    static final StartMining instance = new StartMining();

    protected StartMining() {
        super(new APITag[]{APITag.MINING}, "secretPhrase");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        String secretPhrase = ParameterParser.getSecretPhrase(req, true);
        boolean result = Miner.startMining(secretPhrase);

        JSONObject response = new JSONObject();
        long timeDiff = System.currentTimeMillis() - GetWork.instance.getLastGetWorkTime();
        if (timeDiff < 1000) {
            response.put("getworkIsQueried", true);
        } else {
            response.put("getworkIsQueried", false);
        }
        response.put("secretPhrase", result);
        return response;

    }
}
