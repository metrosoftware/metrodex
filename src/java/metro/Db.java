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

import metro.db.BasicDb;
import metro.db.TransactionalDb;

public final class Db {

    public static final String PREFIX = Constants.isTestnet ? "metro.testDb" : "metro.db";
    public static final TransactionalDb db = new TransactionalDb(new BasicDb.DbProperties()
            .maxCacheSize(Metro.getIntProperty("metro.dbCacheKB"))
            .dbUrl(Metro.getStringProperty(PREFIX + "Url"))
            .dbType(Metro.getStringProperty(PREFIX + "Type"))
            .dbDir(Metro.getStringProperty(PREFIX + "Dir"))
            .dbParams(Metro.getStringProperty(PREFIX + "Params"))
            .dbUsername(Metro.getStringProperty(PREFIX + "Username"))
            .dbPassword(Metro.getStringProperty(PREFIX + "Password", null, true))
            .maxConnections(Metro.getIntProperty("metro.maxDbConnections"))
            .loginTimeout(Metro.getIntProperty("metro.dbLoginTimeout"))
            .defaultLockTimeout(Metro.getIntProperty("metro.dbDefaultLockTimeout") * 1000)
            .maxMemoryRows(Metro.getIntProperty("metro.dbMaxMemoryRows"))
    );

    public static void init() {
        db.init(new MetroDbVersion());
    }

    static void shutdown() {
        db.shutdown();
    }

    private Db() {} // never

}
