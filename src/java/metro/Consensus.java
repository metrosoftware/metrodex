package metro;

import metro.crypto.HashFunction;
import metro.util.BitcoinJUtils;

import java.math.BigInteger;

public class Consensus {
    public static final HashFunction HASH_FUNCTION = HashFunction.SHA3;

    public static final short GENESIS_BLOCK_VERSION = 0;

    public static final int DIFFICULTY_TRANSITION_INTERVAL = 2016;
    public static final int DIFFICULTY_CALCULATION_INTERVAL = 2016;

    public static final int POW_TARGET_TIMESPAN = 24 * 60 * 60 * 1000; // Dash: 1 day;
    public static final int POW_TARGET_SPACING = 10 * 60 * 1000; //10 min
    public static final int POW_RETARGET_INTERVAL = 24; //10 min

    public static final long TARGET_TIMESPAN = 1209600000L;

    public static final int MAX_WORK_BITS = Constants.isTestnet ? Integer.parseUnsignedInt(Metro.getStringProperty("metro.testnetMaxWorkTarget", "1e00ffff"),16) : 0x1f00ffff;
    public static final BigInteger MAX_WORK_TARGET = BitcoinJUtils.decodeCompactBits(MAX_WORK_BITS);
    public static final int SUBSIDY_HALVING_INTERVAL = 200000;
    public static final long INITIAL_SUBSIDY = 2000 * Constants.ONE_MTR;

    // these are fork voting related settings
    public static final int GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS = Constants.isTestnet ? Metro.getIntProperty("metro.testnetGuaranteedBalanceKeyblockConfirmations", 30) : 30;
    public static final int COINBASE_MATURITY_PERIOD = Constants.isTestnet ? Metro.getIntProperty("metro.testnetCoinbaseMaturityPeriodInKeyblocks", 6) : 6;
    public static final int MIN_FORKVOTING_AMOUNT_MTR = 10000;
    // ~ 15 days
    public static final int FORGER_ACTIVITY_SNAPSHOT_INTERVAL = 500000;
    public static final int POSBLOCK_MAX_NUMBER_OF_TRANSACTIONS = 255;
    public static final int MIN_TRANSACTION_SIZE = 176;
    public static final int POSBLOCK_MAX_PAYLOAD_LENGTH = 100000;
    public static final int KEYBLOCK_MAX_PAYLOAD_LENGTH = 1000000;
    public static final long MAX_BALANCE_MTR = 1000000000;
    public static final int BLOCK_TIME = 3000;

    public static short getPosBlockVersion(int atHeight) {
        return 3;
    }

    public static short getKeyBlockVersion(int atHeight) {
        return (short)0x8001;
    }

    public static int getTransactionVersion(int atHeight) {
        return 1;
    }

    //TODO ticket #192 take uncle subsidy in consideration
    public static long getBlockSubsidy(int atLocalHeight) {
        int halvings = atLocalHeight / SUBSIDY_HALVING_INTERVAL;
        if (halvings >= 64)
            return 0;
        // Subsidy is cut in half every 200,000 blocks which will occur approximately every 3 years and 10 months.
        return INITIAL_SUBSIDY >> halvings;
    }
}
