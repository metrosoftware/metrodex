package metro.http;

import metro.MetroException;
import metro.daemon.DaemonServlet;
import metro.util.JSON;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;


public final class GetWork extends APIServlet.APIRequestHandler {

    public static final GetWork instance = new GetWork();

    private GetWork() {
        super(new APITag[]{APITag.MINING});
    }

    public long getLastGetWorkTime() {
        return metro.daemon.GetWork.instance.getLastGetWorkTime();
    }

    @Override
    public JSONStreamAware processRequest(HttpServletRequest request) throws MetroException {
        JSONObject response = new JSONObject();
        try {
            return DaemonServlet.instance.processRequest(request);
        } catch (ServletException | IOException e) {
            response.put("error", e.getMessage());
            return JSON.prepare(response);
        }
    }

}
