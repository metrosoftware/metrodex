package metro;

import metro.crypto.HashFunction;
import metro.util.BitcoinJUtils;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Consensus {
    public static final HashFunction HASH_FUNCTION = HashFunction.SHA3;

    public static final short GENESIS_BLOCK_VERSION = 0;

    public static final int POW_TARGET_SPACING = 10 * 60 * 1000; //10 min
    public static final int POW_RETARGET_INTERVAL = 12;

    public static final int START_WORK_BITS = Constants.isTestnet ? Integer.parseUnsignedInt(Metro.getStringProperty("metro.testnetMaxWorkTarget", "1e00ffff"),16) : 0x1d000fff;
    public static final int STOP_WORK_BITS = 0x10000fff;
    public static final BigInteger MAX_WORK_TARGET = BitcoinJUtils.decodeCompactBits(START_WORK_BITS);
    public static final BigInteger STOP_WORK_TARGET = BitcoinJUtils.decodeCompactBits(STOP_WORK_BITS);

    public static final int DIFFICULTY_BITS = 0x1d00ffff;
    public static final BigInteger DIFFICULTY_MAX_TARGET = BitcoinJUtils.decodeCompactBits(DIFFICULTY_BITS);

    public static final boolean GENESIS_BALANCES_TIME_LOCK = !Constants.isTestnet || Metro.getBooleanProperty("metro.testnetGenesisBalancesTimeLock", false);

    // Subsidy is cut in half every 200,000 blocks which will occur approximately every 3 years and 10 months.
    public static final int SUBSIDY_HALVING_INTERVAL = 200000;
    public static final long INITIAL_SUBSIDY = 2000 * Constants.ONE_MTR;

    // these are fork voting related settings
    public static final int GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS = Constants.isTestnet ? Metro.getIntProperty("metro.testnetGuaranteedBalanceKeyblockConfirmations", 30) : 30;
    public static final int COINBASE_MATURITY_PERIOD = Constants.isTestnet ? Metro.getIntProperty("metro.testnetCoinbaseMaturityPeriodInKeyblocks", 6) : 6;
    public static final int MIN_FORKVOTING_AMOUNT_MTR = 10000;
    public static final int FORGER_ACTIVITY_SNAPSHOT_INTERVAL = 500000;    // ~ 15 days
    public static final int POSBLOCK_MAX_NUMBER_OF_TRANSACTIONS = 255;
    public static final int MIN_TRANSACTION_SIZE = 176;
    public static final int POSBLOCK_MAX_PAYLOAD_LENGTH = 100000;
    public static final int KEYBLOCK_MAX_PAYLOAD_LENGTH = 1000000;
    public static final long MAX_BALANCE_MTR = 1000000000;
    public static final int BLOCK_TIME = 3000;
    public static final int FORGERS_FIXATION_BLOCK = 50;

    public static final int QUARTER_TIMERATIO_IN_BLOCKS = 5;
    public static final int TIMERATIO_IN_BLOCKS = QUARTER_TIMERATIO_IN_BLOCKS * 4;
    public static final int BLOCKCHAIN_THREE_HOURS = Constants.BLOCKCHAIN_NXT_HALFDAY * QUARTER_TIMERATIO_IN_BLOCKS;
    // we use this instead of 1440
    public static final int BLOCKCHAIN_SIX_HOURS = Constants.BLOCKCHAIN_NXT_DAY * QUARTER_TIMERATIO_IN_BLOCKS;
    public static final int BLOCKCHAIN_HALFDAY = Constants.BLOCKCHAIN_NXT_HALFDAY * TIMERATIO_IN_BLOCKS;
    public static final int BLOCKCHAIN_DAY = Constants.BLOCKCHAIN_NXT_DAY * TIMERATIO_IN_BLOCKS;

    public static final int SOFT_FORK_1 = 4000;

    public static short getPosBlockVersion(int atHeight) {
        return 3;
    }

    public static final short INITIAL_BLOCK = (short) 0x8001;
    public static final short STRATUM_COMPATIBILITY_BLOCK = (short) 0x8002;

    public static short getPreferableKeyBlockVersion(int keyHeight) {
        if(keyHeight < SOFT_FORK_1) {
            return INITIAL_BLOCK;
        } else {
            return STRATUM_COMPATIBILITY_BLOCK;
        }
    }

    public static Set<Short> getPermissibleKeyBlockVersions(int keyHeight) {
        if(keyHeight < SOFT_FORK_1) {
            return Collections.singleton(INITIAL_BLOCK);
        } else if (keyHeight < SOFT_FORK_1 + 100) {
            Short[] versions = {INITIAL_BLOCK, STRATUM_COMPATIBILITY_BLOCK};
            return new HashSet<>(Arrays.asList(versions));
        } else {
            return Collections.singleton(STRATUM_COMPATIBILITY_BLOCK);
        }
    }

    public static int getTransactionVersion(int atHeight) {
        return 1;
    }

    public static long getBlockSubsidy(int atLocalHeight) {
        int halvings = atLocalHeight / SUBSIDY_HALVING_INTERVAL;
        if (halvings > 37)
            return 0;
        if (halvings > 34)
            return (INITIAL_SUBSIDY >> halvings) + 5;
        return INITIAL_SUBSIDY >> halvings;
    }

    public static void main(String[] args) {
        long sum = 0;
        for (int i = 0; i < 200000 * 40; i++) {
            sum+=getBlockSubsidy(i);
        }
        System.out.println(sum);
    }

    private static Long[] badBlocks = {0L, 7707638518140123801L};
    public static Set<Long> badBlockSet = new HashSet<>();

    static {
        badBlockSet.addAll(Arrays.asList(badBlocks));
    }

}
