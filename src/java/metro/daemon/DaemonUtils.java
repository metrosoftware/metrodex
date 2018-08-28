package metro.daemon;

import metro.Miner;
import metro.util.Convert;

public class DaemonUtils {
    static String minerBase58Address() {
        byte[] generatorPublicKey = Convert.parseHexString(Miner.getPublicKey());
        return Base58.encode(generatorPublicKey);
    }


}
