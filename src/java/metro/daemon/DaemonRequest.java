package metro.daemon;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.Reader;

public class DaemonRequest {
    private String method;
    private String id;
    private JSONArray params;


    public static DaemonRequest init(Reader inputReader) throws ParseException, IOException {
        return new DaemonRequest((JSONObject) JSONValue.parseWithException(inputReader));
    }

    public static DaemonRequest init(String requestString) throws ParseException {
        return new DaemonRequest((JSONObject) (new JSONParser()).parse(requestString));
    }

    public DaemonRequest(JSONObject requestJSON) {
        if (!requestJSON.containsKey("method") |
                !requestJSON.containsKey("id") |
                !requestJSON.containsKey("params")) {
            throw new IllegalArgumentException("Should contain method, id and params");
        }
        this.method = (String) requestJSON.get("method");
        Object id = requestJSON.get("id");
        this.id = id instanceof String ? (String)id : id instanceof Number ? id.toString() : null;
        this.params = (JSONArray) requestJSON.get("params");
    }

    public DaemonRequest(String method, String id, JSONArray params) {
        this.method = method;
        this.id = id;
        this.params = params;
    }

    public String getMethod() {
        return method;
    }

    public String getId() {
        return id;
    }

    public JSONArray getParams() {
        return params;
    }
}

