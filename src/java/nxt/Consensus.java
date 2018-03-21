package nxt;

public class Consensus {
    public static final short GENESIS_BLOCK_VERSION = 0;
    public static final short KEY_BLOCK_VERSION = (short)0x8001;
    public static short getPosBlockVersion(int atHeight) {
        return 3;
    }

    public static short getKeyBlockVersion(int atHeight) {
        return (short)0x8001;
    }

    public static int getTransactionVersion(int atHeight) {
        return 1;
    }

}
