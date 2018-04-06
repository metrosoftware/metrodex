package nxt.crypto;

import org.junit.Test;

public class KeccakTest {

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    @Test
    public void testKeccak() {
        //String s = "00b036391e00ffff52e64378f8cc3b46c273a488c318dc7d98cc053494af2871e495e17f5c7c246055e46af3000000000000000000000000000000000000000000000000000000000000000000000070";
        String s = "700000000000000000000000000000000000000000000000000000000000000000000000f36ae45560247c5c7fe195e47128af943405cc987ddc18c388a473c2463bccf87843e652ffff001e3936b000";
        //String s = "";
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        byte[]  hash1 = HashFunction.SHA3.hash(data);
        System.out.println(bytesToHex(hash1));
        hash1 = HashFunction.SHA256.hash(data);
        System.out.println(bytesToHex(hash1));
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
