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

    public static int nextTarget(Block lastKeyBlock) {
        return darkGravityWave(lastKeyBlock);
    }

    //FIXME remove. not needed
    private static int kimotoGravityWell(Block lastKeyBlock) {
        Block blockReading = lastKeyBlock;
        long pastBlocksMass = 0L;
        long pastRateActualSeconds = 0L;
        long pastRateTargetSeconds = 0L;
        double pastRateAdjustmentRatio;
        BigInteger pastDifficultyAverage = null;
        BigInteger pastDifficultyAveragePrev = null;
        double eventHorizonDeviation;
        double eventHorizonDeviationFast;
        double eventHorizonDeviationSlow;
        long pastSecondsMin = (long) (Consensus.POW_TARGET_TIMESPAN * 0.025);
        long pastSecondsMax = Consensus.POW_TARGET_TIMESPAN * 7;
        long pastBlocksMin = pastSecondsMin / Consensus.POW_TARGET_SPACING;
        long pastBlocksMax = pastSecondsMax / Consensus.POW_TARGET_SPACING;

        if (lastKeyBlock == null || lastKeyBlock.getLocalHeight() == 0 || (long)lastKeyBlock.getLocalHeight() < pastBlocksMin) {
            return Consensus.MAX_WORK_BITS;
        }

        for (int i = 1; blockReading != null; i++){
            if (pastBlocksMax > 0 && i > pastBlocksMax) {
                break;
            }
            pastBlocksMass++;
            pastDifficultyAverage = BitcoinJUtils.decodeCompactBits((int)blockReading.getBaseTarget());
            if (i > 1) {
                pastDifficultyAverage = ((pastDifficultyAverage.subtract(pastDifficultyAveragePrev)).divide(BigInteger.valueOf(i))).add(pastDifficultyAveragePrev);
            }
            pastDifficultyAveragePrev = pastDifficultyAverage;

            pastRateActualSeconds = (lastKeyBlock.getTimestamp() - blockReading.getTimestamp()) / 1000;
            pastRateTargetSeconds = Consensus.POW_TARGET_SPACING * pastBlocksMass / 1000;
            pastRateAdjustmentRatio = 1.0;

            if (pastRateActualSeconds < 0) {
                pastRateActualSeconds = 0; 
            }

            if (pastRateActualSeconds != 0 && pastRateTargetSeconds != 0) {
                pastRateAdjustmentRatio = ((double) pastRateTargetSeconds) / ((double)pastRateActualSeconds);
            }

            eventHorizonDeviation = 1 + (0.7084 * Math.pow((((double)pastBlocksMass)/28.2), -1.228));
            eventHorizonDeviationFast = eventHorizonDeviation;
            eventHorizonDeviationSlow = 1 / eventHorizonDeviation;

            if (pastBlocksMass >= pastBlocksMin) {
                if ((pastRateAdjustmentRatio <= eventHorizonDeviationSlow) || (pastRateAdjustmentRatio >= eventHorizonDeviationFast))
                {
                    break;
                }
            }
            blockReading = blockReading.getHeight() > 0 ? Metro.getBlockchain().getBlock(blockReading.getPreviousKeyBlockId()) : null;
        }

        BigInteger result = pastDifficultyAverage;

        if (pastRateActualSeconds != 0 && pastRateTargetSeconds != 0) {
            result = result.multiply(BigInteger.valueOf(pastRateActualSeconds)).divide(BigInteger.valueOf(pastRateTargetSeconds));
        }

        if (result.compareTo(Consensus.MAX_WORK_TARGET) > 0) {
            result = Consensus.MAX_WORK_TARGET;
        }

        return BitcoinJUtils.encodeCompactBits(result);
    }

    /* current difficulty formula, dash - DarkGravity v3, written by Evan Duffield - evan@dash.org */
    private static int darkGravityWave(Block lastKeyBlock) {
        // make sure we have at least (npastBlocks + 1) blocks, otherwise just return powLimit
        if (lastKeyBlock == null || lastKeyBlock.getLocalHeight() < Consensus.POW_RETARGET_INTERVAL) {
            return BitcoinJUtils.encodeCompactBits(Consensus.MAX_WORK_TARGET);
        }
        Block block = lastKeyBlock;

        BigInteger bnPastTargetAvg = null;

        for (int nCountBlocks = 1; nCountBlocks <= Consensus.POW_RETARGET_INTERVAL; nCountBlocks++) {
            BigInteger bnTarget = BitcoinJUtils.decodeCompactBits((int)block.getBaseTarget());
            bnPastTargetAvg = bnTarget;
            if (nCountBlocks > 1) {
                // NOTE: that's not an average really...
                bnPastTargetAvg = ((bnTarget.subtract(bnPastTargetAvg)).divide(BigInteger.valueOf(nCountBlocks + 1))).add(bnPastTargetAvg);
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
