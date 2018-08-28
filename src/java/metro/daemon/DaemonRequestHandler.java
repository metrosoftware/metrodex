package metro.daemon;

import org.json.simple.JSONStreamAware;

public interface DaemonRequestHandler {
    JSONStreamAware process(DaemonRequest request);
    JSONStreamAware process(DaemonRequest request, Object options);
}
