package metro;

import metro.util.Convert;

public class Miner {
    private static String publicKey = Convert.emptyToNull(Metro.getStringProperty("metro.mine.publicKey"));
    private static String passphrase = Convert.emptyToNull(Metro.getStringProperty("metro.daemon.passphrase"));

    public static String getPublicKey() {
        return publicKey;
    }

    public static String getPassphrase() {
        return passphrase;
    }
}
