/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 * Copyright © 2018 metro.software
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package metro;

import metro.util.Convert;

import java.math.BigInteger;

public final class Constants {

    public static final boolean isTestnet = Metro.getBooleanProperty("metro.isTestnet");
    public static final boolean isOffline = Metro.getBooleanProperty("metro.isOffline");
    public static final boolean isLightClient = Metro.getBooleanProperty("metro.isLightClient");
    public static final String customLoginWarning = Metro.getStringProperty("metro.customLoginWarning", null, false, "UTF-8");

    public static final String COIN_SYMBOL = "MTR";
    public static final String ACCOUNT_PREFIX = "MTR";
    public static final String PROJECT_NAME = "Metro";

    public static final long ONE_MTR = 100000000;
    public static final long MAX_BALANCE_MQT = Consensus.MAX_BALANCE_MTR * ONE_MTR;

    public static final long INITIAL_BASE_TARGET = Convert.toLong(BigInteger.valueOf(2).pow(63).divide(BigInteger.valueOf(Consensus.BLOCK_TIME * Consensus.MAX_BALANCE_MTR))); //153722867;
    public static final long MAX_BASE_TARGET = INITIAL_BASE_TARGET * (isTestnet ? Consensus.MAX_BALANCE_MTR : 50);
    public static final long MIN_BASE_TARGET = INITIAL_BASE_TARGET * 9 / 10;
    public static final int MIN_BLOCKTIME_LIMIT = Consensus.BLOCK_TIME - 350;
    public static final int MAX_BLOCKTIME_LIMIT = Consensus.BLOCK_TIME + 350;
    public static final int BASE_TARGET_GAMMA = 64;
    public static final int LEASING_DELAY = isTestnet ? Metro.getIntProperty("metro.testnetLeasingDelay", 1440) : 1440;
    public static final long MIN_FORGING_BALANCE_MQT = 1000 * ONE_MTR;

    public static final int MAX_TIMEDRIFT = 15000; // allow up to 15 s clock difference
    public static final int FORGING_DELAY = Metro.getIntProperty("metro.forgingDelay");
    public static final int FORGING_SPEEDUP = Metro.getIntProperty("metro.forgingSpeedup");
    public static final int BATCH_COMMIT_SIZE = Metro.getIntProperty("metro.batchCommitSize", Integer.MAX_VALUE);

    public static final byte MAX_PHASING_VOTE_TRANSACTIONS = 10;
    public static final byte MAX_PHASING_WHITELIST_SIZE = 10;
    public static final byte MAX_PHASING_LINKED_TRANSACTIONS = 10;
    public static final int MAX_PHASING_DURATION = 14 * 1440;
    public static final int MAX_PHASING_REVEALED_SECRET_LENGTH = 100;

    public static final int MAX_ALIAS_URI_LENGTH = 1000;
    public static final int MAX_ALIAS_LENGTH = 100;

    public static final int MAX_ARBITRARY_MESSAGE_LENGTH = 160;
    public static final int MAX_ENCRYPTED_MESSAGE_LENGTH = 160 + 16;

    public static final int MAX_PRUNABLE_MESSAGE_LENGTH = 42 * 1024;
    public static final int MAX_PRUNABLE_ENCRYPTED_MESSAGE_LENGTH = 42 * 1024;

    public static final long MIN_PRUNABLE_LIFETIME = isTestnet ? 1440 * 60 * 1000 : 14 * 1440 * 60 * 1000;
    public static final long MAX_PRUNABLE_LIFETIME;
    public static final boolean ENABLE_PRUNING;
    public static final long MAX_MESSAGE_DATA_LENGTH = 42 * 1024;

    static {
        int maxPrunableLifetime = Metro.getIntProperty("metro.maxPrunableLifetime");
        ENABLE_PRUNING = maxPrunableLifetime >= 0;
        MAX_PRUNABLE_LIFETIME = ENABLE_PRUNING ? Math.max(maxPrunableLifetime, MIN_PRUNABLE_LIFETIME) : Long.MAX_VALUE;
    }
    public static final boolean INCLUDE_EXPIRED_PRUNABLE = Metro.getBooleanProperty("metro.includeExpiredPrunable");

    public static final int MAX_ACCOUNT_NAME_LENGTH = 100;
    public static final int MAX_ACCOUNT_DESCRIPTION_LENGTH = 1000;

    public static final int MAX_ACCOUNT_PROPERTY_NAME_LENGTH = 32;
    public static final int MAX_ACCOUNT_PROPERTY_VALUE_LENGTH = 160;

    public static final long MAX_ASSET_QUANTITY_QNT = 1000000000L * 100000000L;
    public static final int MIN_ASSET_NAME_LENGTH = 3;
    public static final int MAX_ASSET_NAME_LENGTH = 10;
    public static final int MAX_ASSET_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_SINGLETON_ASSET_DESCRIPTION_LENGTH = 160;
    public static final int MAX_ASSET_TRANSFER_COMMENT_LENGTH = 1000;
    public static final int MAX_DIVIDEND_PAYMENT_ROLLBACK = 1441;

    public static final int MAX_POLL_NAME_LENGTH = 100;
    public static final int MAX_POLL_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_POLL_OPTION_LENGTH = 100;
    public static final int MAX_POLL_OPTION_COUNT = 100;
    public static final int MAX_POLL_DURATION = 14 * 1440;

    public static final byte MIN_VOTE_VALUE = -92;
    public static final byte MAX_VOTE_VALUE = 92;
    public static final byte NO_VOTE_VALUE = Byte.MIN_VALUE;

    public static final byte MIN_NUMBER_OF_SHUFFLING_PARTICIPANTS = 3;
    public static final byte MAX_NUMBER_OF_SHUFFLING_PARTICIPANTS = 30; // max possible at current block payload limit is 51
    public static final short MAX_SHUFFLING_REGISTRATION_PERIOD = (short)1440 * 7;
    public static final short SHUFFLING_PROCESSING_DEADLINE = (short)(isTestnet ? 10 : 100);

    public static final long MAX_REFERENCED_TRANSACTION_TIMESPAN = 60 * 1440 * 60 * 1000;
    public static final int CHECKSUM_BLOCK_1 = Integer.MAX_VALUE;

    public static final int LAST_CHECKSUM_BLOCK = 0;
    // LAST_KNOWN_BLOCK must also be set in html/www/js/mrs.constants.js
    public static final int LAST_KNOWN_BLOCK = isTestnet ? 0 : 0;

    public static final int[] MIN_VERSION = new int[] {1, 0};
    public static final int[] MIN_PROXY_VERSION = new int[] {1, 0};

    static final long UNCONFIRMED_POOL_DEPOSIT_MQT = (isTestnet ? 50 : 100) * ONE_MTR;
    public static final long SHUFFLING_DEPOSIT_MQT = (isTestnet ? 7 : 1000) * ONE_MTR;

    public static final boolean correctInvalidFees = Metro.getBooleanProperty("metro.correctInvalidFees");

    public static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
    public static final String ALLOWED_CURRENCY_CODE_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";


    public static final int UI_GENERATORS_HISTORY_BLOCKS = 10000;
    public static final byte[] TWO_BRANCHES_EMPTY_MERKLE_ROOT = new byte[Convert.HASH_SIZE * 2];
    private Constants() {} // never

}
