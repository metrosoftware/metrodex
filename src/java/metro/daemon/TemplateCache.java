package metro.daemon;

import java.util.List;

public class TemplateCache extends HistoryCache<List<Long>> {
    private static int CACHE_SIZE = 30;
    public static TemplateCache instance = new TemplateCache(CACHE_SIZE);
    private TemplateCache(int cacheSize) {
        super(cacheSize);
    }
}
