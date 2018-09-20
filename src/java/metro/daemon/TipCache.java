package metro.daemon;

import metro.BlockImpl;

import java.util.List;

public class TipCache extends HistoryCache<List<BlockImpl>> {
    private static int CACHE_SIZE = 30;
    public static TipCache instance = new TipCache(CACHE_SIZE);
    private TipCache(int cacheSize) {
        super(cacheSize);
    }
}
