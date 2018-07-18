package metro.http;

import metro.Block;
import metro.Metro;
import metro.MetroException;
import metro.db.DbIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

public class GetMinedBlocks extends APIServlet.APIRequestHandler {

    static final GetMinedBlocks instance = new GetMinedBlocks();

    private GetMinedBlocks() {
        super(new APITag[] {APITag.BLOCKS}, "firstIndex", "lastIndex", "timestamp", "includeTransactions", "includeExecutedPhased");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws MetroException {

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        boolean includeTransactions = "true".equalsIgnoreCase(req.getParameter("includeTransactions"));
        boolean includeExecutedPhased = "true".equalsIgnoreCase(req.getParameter("includeExecutedPhased"));

        JSONArray blocks = new JSONArray();
        try (DbIterator<? extends Block> iterator = Metro.getBlockchain().getBlocks(firstIndex, lastIndex, true)) {
            while (iterator.hasNext()) {
                Block block = iterator.next();
                blocks.add(JSONData.block(block, includeTransactions, includeExecutedPhased));
            }
        }

        JSONObject response = new JSONObject();
        response.put("blocks", blocks);

        return response;
    }

}
