package metro.http;

import metro.Account;
import metro.Consensus;
import metro.Constants;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;

public final class GetCirculationSupply extends APIServlet.APIRequestHandler {

    static final GetCirculationSupply instance = new GetCirculationSupply();

    private GetCirculationSupply() {
        super(new APITag[] {APITag.ACCOUNTS});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        JSONObject response = new JSONObject();
        long circulationSupplyMQT = Account.getCirculationSupply();
        response.put("circulationMQT", circulationSupplyMQT);
        response.put("circulationMTR", BigDecimal.valueOf(circulationSupplyMQT).divide(BigDecimal.valueOf(Constants.ONE_MTR), 2, BigDecimal.ROUND_DOWN));
        return response;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return true;
    }
}
