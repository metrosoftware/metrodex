package metro.http;

import metro.Block;
import metro.Metro;
import metro.MetroException;
import metro.db.DbIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class GetAccountMinedBlocks extends APIServlet.APIRequestHandler {

    static final GetAccountMinedBlocks instance = new GetAccountMinedBlocks();

    private GetAccountMinedBlocks() {
        super(new APITag[] {APITag.ACCOUNTS}, "account", "timestamp", "firstIndex", "lastIndex", "includeTransactions");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws MetroException {

        long accountId = ParameterParser.getAccountId(req, true);
        long timestamp = ParameterParser.getTimestamp(req);
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        boolean includeTransactions = "true".equalsIgnoreCase(req.getParameter("includeTransactions"));

        JSONArray blocks = new JSONArray();
        try (DbIterator<? extends Block> iterator = Metro.getBlockchain().getBlocks(accountId, timestamp, firstIndex, lastIndex, true)) {
            while (iterator.hasNext()) {
                Block block = iterator.next();
                blocks.add(JSONData.block(block, includeTransactions, false));
            }
        }

        JSONObject response = new JSONObject();
        response.put("blocks", blocks);

        return response;
    }
}
