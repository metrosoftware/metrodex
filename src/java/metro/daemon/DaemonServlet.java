package metro.daemon;

import metro.util.JSON;
import metro.util.Logger;
import org.eclipse.jetty.server.Request;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.parser.ParseException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.util.stream.Collectors;

public class DaemonServlet extends HttpServlet {

    public static final DaemonServlet instance = new DaemonServlet();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try (Writer writer = resp.getWriter()) {
            JSON.writeJSONString(processRequest(req), writer);
        }
    }

    public JSONStreamAware processRequest(HttpServletRequest req) throws ServletException, IOException {
        //super.doPost(req, resp);
        JSONObject response = new JSONObject();
        String content = "";
        try {
            if (req.getReader() != null) {
                content = req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
                Logger.logDebugMessage("Daemon:" + content);
            }
        } catch (IOException e) {
            response.put("error", e.getMessage());
        }
        if (content.length() > 0) {
            try {
                DaemonRequest dReq = DaemonRequest.init(content);
                return processRequest(dReq, ((Request) req).getMetaData().getURI().getHost());
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return JSON.prepare(response);
    }

    protected JSONStreamAware processRequest(DaemonRequest dReq, String host) throws ServletException, IOException {
        JSONObject response = new JSONObject();
        if (dReq.getMethod().equals("getaccountaddress")) {
            return GetAccountAddress.instance.process(dReq);
        } else if (dReq.getMethod().equals("validateaddress")) {
            return ValidateAddress.instance.process(dReq);
        } else if (dReq.getMethod().equals("getwork")) {
            return metro.daemon.GetWork.instance.process(dReq, host);
        } else if (dReq.getMethod().equals("getinfo")) {
            return GetInfo.instance.process(dReq);
        } else if (dReq.getMethod().equals("getblocktemplate")) {
            return GetBlockTemplate.instance.process(dReq);
        } else if (dReq.getMethod().equals("submitblock")) {
            return SubmitBlock.instance.process(dReq);
        } else if (dReq.getMethod().equals("getblock")) {
            return GetBlock.instance.process(dReq);
        } else {
            response.put("error", "Method " + dReq.getMethod() + " not supported");
            response.put("id", dReq.getId());
            return JSON.prepare(response);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doGet(req, resp);
    }
}
