package metro.daemon;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class HistoryCache<T> {

    private final int cachSize;
    private Map<Long, T> elements = new HashMap<>();
    private Queue<Long> ids = new LinkedList<>();


    public HistoryCache(int cachSize) {
        this.cachSize = cachSize;
    }

    public void put(Long id, T element) {
        if (ids.size() >= cachSize) {
            Long remove = ids.poll();
            elements.remove(remove);
        }
        ids.add(id);
        elements.put(id, element);
    }

    public T get(Long id) {
        return elements.get(id);
    }

}
