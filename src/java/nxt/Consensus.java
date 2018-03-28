package nxt;

import nxt.crypto.HashFunction;
import nxt.util.BitcoinJUtils;

import java.math.BigInteger;

public class Consensus {
    public static final HashFunction HASH_FUNCTION = HashFunction.SHA3;

    public static final short GENESIS_BLOCK_VERSION = 0;

    public static final int DIFFICULTY_TRANSITION_INTERVAL = 2016;
    public static final int TARGET_TIMESPAN = 1209600;
    public static final BigInteger MAX_WORK_TARGET = Constants.isTestnet ? BitcoinJUtils.decodeCompactBits(Long.parseUnsignedLong(Nxt.getStringProperty("nxt.testnetMaxWorkTarget", "1d00ffff"), 16)) : BitcoinJUtils.decodeCompactBits(0x1d00ffffL);
    public static final int SUBSIDY_HALVING_INTERVAL = 210000;

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
