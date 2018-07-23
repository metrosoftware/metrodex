// Copyright © 2009-2010 Satoshi Nakamoto
// Copyright © 2009-2015 The Bitcoin Core developers
// Copyright © 2018 metro.software
//
// Distributed under the MIT software license, see the accompanying
// file COPYING or http://www.opensource.org/licenses/mit-license.php.
package metro;


import metro.util.BitcoinJUtils;

import java.math.BigInteger;

public class Target {

    private static int TARGET_FIXATION_HEIGHT = 50;
    /* current difficulty formula, dash - DarkGravity v3, written by Evan Duffield - evan@dash.org */
    public static int nextTarget(Block lastKeyBlock) {
        // make sure we have at least (npastBlocks + 1) blocks, otherwise just return powLimit
        if (lastKeyBlock == null || lastKeyBlock.getLocalHeight() < Consensus.POW_RETARGET_INTERVAL) {
            return BitcoinJUtils.encodeCompactBits(Consensus.MAX_WORK_TARGET);
        }
        if (lastKeyBlock.getLocalHeight() > TARGET_FIXATION_HEIGHT && lastKeyBlock.getLocalHeight() < TARGET_FIXATION_HEIGHT + Consensus.POW_RETARGET_INTERVAL) {
            return BitcoinJUtils.encodeCompactBits(Consensus.MAX_WORK_TARGET);
        }
        Block block = lastKeyBlock;

        BigInteger bnPastTargetAvg = null;

        for (int nCountBlocks = 1; nCountBlocks <= Consensus.POW_RETARGET_INTERVAL; nCountBlocks++) {
            BigInteger bnTarget = BitcoinJUtils.decodeCompactBits((int)block.getBaseTarget());

            if (nCountBlocks > 1) {
                bnPastTargetAvg = ((bnTarget.subtract(bnPastTargetAvg)).divide(BigInteger.valueOf(nCountBlocks + 1))).add(bnPastTargetAvg);
            } else {
                bnPastTargetAvg = bnTarget;
            }

            if (nCountBlocks != Consensus.POW_RETARGET_INTERVAL) {
                block = Metro.getBlockchain().getBlock(block.getPreviousKeyBlockId());
            }
        }

        BigInteger result = bnPastTargetAvg;

        long nActualTimespan = lastKeyBlock.getTimestamp() - block.getTimestamp();
        // NOTE: is this accurate? nActualTimespan counts it for (npastBlocks - 1) blocks only...
        long nTargetTimespan = Consensus.POW_RETARGET_INTERVAL * Consensus.POW_TARGET_SPACING;

        if (nActualTimespan < nTargetTimespan / 3)
            nActualTimespan = nTargetTimespan / 3;
        if (nActualTimespan > nTargetTimespan * 3)
            nActualTimespan = nTargetTimespan * 3;

        // Retarget
        result = result.multiply(BigInteger.valueOf(nActualTimespan)).divide(BigInteger.valueOf(nTargetTimespan));

        if (result.compareTo(Consensus.MAX_WORK_TARGET) > 0) {
            result = Consensus.MAX_WORK_TARGET;
        }

        return BitcoinJUtils.encodeCompactBits(result);
    }

}
