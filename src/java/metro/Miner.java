package metro;

import metro.util.Convert;

public class Miner {
    private static String publicKey = Convert.emptyToNull(Metro.getStringProperty("metro.mine.publicKey"));

    public static String getPublicKey() {
        return publicKey;
    }
}
