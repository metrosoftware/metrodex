package metro;

import metro.util.Convert;
import org.apache.commons.lang3.StringUtils;

public class Miner {
    private static String secretPhrase = Convert.emptyToNull(Metro.getStringProperty("metro.mine.secretPhrase"));

    public static String getSecretPhrase() {
        return secretPhrase;
    }

    public static boolean startMining(String secretPhrase) {
        if (StringUtils.isEmpty(secretPhrase)) {
            return false;
        }
        Miner.secretPhrase = secretPhrase;
        return true;
    }

    public static void stopMining() {
        Miner.secretPhrase = null;
    }
}
