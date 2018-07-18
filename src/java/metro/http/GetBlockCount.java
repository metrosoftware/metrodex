package metro.http;

import metro.Metro;
import metro.MetroException;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class GetBlockCount extends APIServlet.APIRequestHandler {

    static final GetBlockCount instance = new GetBlockCount();

    private GetBlockCount() {
        super(new APITag[] {APITag.BLOCKS});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws MetroException {

        boolean isKeyblock = "true".equalsIgnoreCase(req.getParameter("isKeyBlock"));
        JSONObject response = new JSONObject();
        response.put("numberOfBlocks", Metro.getBlockchain().getBlockCount(isKeyblock));

        return response;
    }
}
