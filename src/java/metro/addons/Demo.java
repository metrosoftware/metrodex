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

package metro.addons;

import metro.Account;
import metro.BlockchainProcessor;
import metro.Metro;
import metro.util.Convert;
import metro.util.Logger;

public final class Demo implements AddOn {

    @Override
    public void init() {
        Metro.getBlockchainProcessor().addListener(block -> Logger.logInfoMessage("Block " + block.getStringId()
                + " has been forged by account " + block.getGeneratorFullId().toString() + " having effective balance of "
                + Account.getAccount(block.getGeneratorFullId()).getEffectiveBalanceMTR()),
                BlockchainProcessor.Event.BEFORE_BLOCK_APPLY);
    }

    @Override
    public void shutdown() {
        Logger.logInfoMessage("Goodbye!");
    }

}
