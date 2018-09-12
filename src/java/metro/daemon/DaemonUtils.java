package metro.daemon;

import metro.Miner;
import metro.util.Convert;
import metro.util.JSON;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

public class DaemonUtils {
    static String minerBase58Address() {
        byte[] generatorPublicKey = Convert.parseHexString(Miner.getPublicKey());
        return Base58.encode(generatorPublicKey);
    }

    static JSONStreamAware awareError(String error, String id) {
        JSONObject response = new JSONObject();
        response.put("result", null);
        response.put("error", error);
        response.put("id", id);
        return JSON.prepare(response);
    }

    static JSONStreamAware awareResult(Object result, String id) {
        JSONObject response = new JSONObject();
        response.put("result", result);
        response.put("error", null);
        response.put("id", id);
        return JSON.prepare(response);
    }

    static JSONStreamAware awareError(int code, String message, String id) {
        JSONObject response = new JSONObject();
        response.put("result", null);
        response.put("error", jsonError(code, message));
        response.put("id", id);
        return JSON.prepare(response);
    }

    private static JSONObject jsonError(int code, String message) {
        JSONObject result = new JSONObject();
        result.put("code", code);
        result.put("message", message);
        return result;
    }
}
