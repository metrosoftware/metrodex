package metro.http;

import metro.Block;
import metro.Constants;
import metro.Metro;
import metro.MetroException;
import metro.db.DbIterator;
import metro.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class GetPOWDifficulty extends APIServlet.APIRequestHandler {

    static final GetPOWDifficulty instance = new GetPOWDifficulty();

    private GetPOWDifficulty() {
        super(new APITag[] {APITag.MINING, APITag.INFO}, "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws MetroException {
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);
        final long timestamp = ParameterParser.getTimestamp(req);
        JSONArray blocks = new JSONArray();
        try (DbIterator<? extends Block> iterator = Metro.getBlockchain().getBlocks(firstIndex, lastIndex, true)) {
            while (iterator.hasNext()) {
                Block block = iterator.next();
                if (block.getTimestamp() < timestamp) {
                    break;
                }
                JSONObject object = new JSONObject();
                object.put("height", block.getHeight());
                object.put("powHeight", block.getLocalHeight());
                object.put("powDifficulty", BigDecimal.valueOf(Constants.MAX_BASE_TARGET).divide(BigDecimal.valueOf(block.getBaseTarget()), 4, RoundingMode.CEILING));
                blocks.add(object);
            }
        }

        JSONObject response = new JSONObject();
        Block lastKeyBlock = Metro.getBlockchain().getLastKeyBlock();
        response.put("currentPOWHeight", lastKeyBlock == null ? 0 : lastKeyBlock.getLocalHeight());
        response.put("blocks", blocks);
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
