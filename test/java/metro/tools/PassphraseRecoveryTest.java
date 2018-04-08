package metro.tools;

import metro.Account;
import metro.BlockchainTest;
import metro.util.Convert;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class PassphraseRecoveryTest extends BlockchainTest {

    private static Map<Long, byte[]> publicKeys;

    @BeforeClass
    public static void loadPublicKeys() {
        /*
        Properties testProperties = new Properties();
        testProperties.setProperty("metro.isTestnet", "true");
        Metro.init(testProperties);
        Runtime.getRuntime().addShutdownHook(new Thread(Metro::shutdown));
        */
        publicKeys = PassphraseRecovery.getPublicKeys();
    }

    @Test
    public void searchAnyPassphrase() {
        char[] wildcard = BlockchainTest.aliceSecretPhrase.toCharArray();
        int[] positions = {9, 18};
        for (Integer position : positions) {
            wildcard[position] = '*';
        }
        PassphraseRecovery.Scanner scanner = new PassphraseRecovery.Scanner(publicKeys, positions, wildcard, PassphraseRecovery.getDefaultDictionary());
        PassphraseRecovery.Solution solution = scanner.scan();
        Assert.assertEquals("MTR-XK4R-7VJU-6EQG-7R335", solution.getRsAccount());
    }

    @Test
    public void searchSpecificPassphrase() {
        char[] wildcard = BlockchainTest.aliceSecretPhrase.toCharArray();
        int[] positions = {27, 9};
        for (Integer position : positions) {
            wildcard[position] = '*';
        }
        String rsAccount = "MTR-XK4R-7VJU-6EQG-7R335";
        long id = Convert.parseAccountId(rsAccount);
        byte[] publicKey = Account.getPublicKey(id);
        Map<Long, byte[]> publicKeys = new HashMap<>();
        publicKeys.put(id, publicKey);
        PassphraseRecovery.Scanner scanner = new PassphraseRecovery.Scanner(publicKeys, positions, wildcard, PassphraseRecovery.getDefaultDictionary());
        PassphraseRecovery.Solution solution = scanner.scan();
        Assert.assertEquals("MTR-XK4R-7VJU-6EQG-7R335", solution.getRsAccount());
    }

    @Test
    public void searchSingleTypo() {
        char[] wildcard = BlockchainTest.aliceSecretPhrase.toCharArray();
        wildcard[18] = '*';
        PassphraseRecovery.Scanner scanner = new PassphraseRecovery.Scanner(publicKeys, new int[0], wildcard, PassphraseRecovery.getDefaultDictionary());
        PassphraseRecovery.Solution solution = scanner.scan();
        Assert.assertEquals("MTR-XK4R-7VJU-6EQG-7R335", solution.getRsAccount());
    }

}
