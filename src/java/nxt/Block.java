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

package nxt;

import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.util.List;

public interface Block {

    short getVersion();

    boolean isKeyBlock();

    long getId();

    String getStringId();

    int getHeight();

    int getLocalHeight();

    int getTimestamp();

    long getGeneratorId();

    byte[] getGeneratorPublicKey();

    long getPreviousBlockId();

    long getPreviousKeyBlockId();

    long getNonce();

    byte[] getPreviousBlockHash();

    byte[] getPreviousKeyBlockHash();

    byte[] getPosBlocksSummary();

    byte[] getStakeMerkleRoot();

    long getNextBlockId();

    long getTotalAmountNQT();

    long getTotalFeeNQT();

    int getPayloadLength();

    byte[] getPayloadHash();

    List<? extends Transaction> getTransactions();

    byte[] getGenerationSignature();

    byte[] getBlockSignature();

    long getBaseTarget();

    BigInteger getCumulativeDifficulty();

    byte[] getBytes();

    JSONObject getJSONObject();

}