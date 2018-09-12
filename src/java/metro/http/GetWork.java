package metro.http;

import metro.MetroException;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;


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
        return metro.daemon.GetWork.instance.processGetWork(request);
    }

}
