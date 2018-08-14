package metro.http;

import metro.Block;
import metro.Consensus;
import metro.Metro;
import metro.MetroException;
import metro.db.DbIterator;
import metro.util.BitcoinJUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;

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
                JSONObject blockObject = JSONData.block(block, includeTransactions, includeExecutedPhased);
                if (block.isKeyBlock()) {
                    blockObject.put("keyBlockDifficulty", new BigDecimal(Consensus.DIFFICULTY_MAX_TARGET).divide(new BigDecimal(BitcoinJUtils.decodeCompactBits((int) block.getBaseTarget())),
                            3, RoundingMode.DOWN));
                }
                blocks.add(blockObject);
            }
        }

        JSONObject response = new JSONObject();
        response.put("blocks", blocks);

        return response;
    }

}
