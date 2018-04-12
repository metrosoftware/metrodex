/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
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

import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.util.List;

public interface Block {

    public enum ValidationResult {
        OK, INSUFFICIENT_WORK, DIFFICULTY_TARGET_OUT_OF_RANGE, INCORRECT_DIFFICULTY, TX_MERKLE_ROOT_DISCREPANCY,
        FORGERS_MERKLE_ROOT_DISCREPANCY, INCORRECT_VERSION, UNKNOWN_ERROR
    }

    short getVersion();

    boolean isKeyBlock();

    long getId();

    String getStringId();

    int getHeight();

    int getLocalHeight();

    long getTimestamp();

    long getGeneratorId();

    byte[] getGeneratorPublicKey();

    long getPreviousBlockId();

    long getPreviousKeyBlockId();

    long getNonce();

    byte[] getPreviousBlockHash();

    byte[] getPreviousKeyBlockHash();

    public byte[] getTxMerkleRoot();

    byte[] getForgersMerkleRoot();

    long getNextBlockId();

    long getTotalAmountMQT();

    long getRewardMQT();

    int getPayloadLength();

    List<? extends Transaction> getTransactions();

    byte[] getGenerationSequence();

    byte[] getBlockSignature();

    long getBaseTarget();

    BigInteger getCumulativeDifficulty();

    BigInteger getStakeBatchDifficulty();

    byte[] getBytes();

    JSONObject getJSONObject();

    BigInteger getDifficultyTargetAsInteger();

    void sign(String secretPhrase);

}
