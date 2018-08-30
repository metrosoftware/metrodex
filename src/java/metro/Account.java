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

import metro.AccountLedger.LedgerEntry;
import metro.AccountLedger.LedgerEvent;
import metro.AccountLedger.LedgerHolding;
import metro.crypto.Crypto;
import metro.crypto.EncryptedData;
import metro.db.DbClause;
import metro.db.DbIterator;
import metro.db.DbKey;
import metro.db.DbUtils;
import metro.db.DerivedDbTable;
import metro.db.VersionedEntityDbTable;
import metro.db.VersionedPersistentDbTable;
import metro.util.Convert;
import metro.util.Listener;
import metro.util.Listeners;
import metro.util.Logger;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static metro.Consensus.COINBASE_MATURITY_PERIOD;
import static metro.Db.db;

@SuppressWarnings({"UnusedDeclaration", "SuspiciousNameCombination"})
public final class Account {

    public enum Event {
        BALANCE, UNCONFIRMED_BALANCE, ASSET_BALANCE, UNCONFIRMED_ASSET_BALANCE,
        LEASE_SCHEDULED, LEASE_STARTED, LEASE_ENDED, SET_PROPERTY, DELETE_PROPERTY
    }

    public enum ControlType {
        PHASING_ONLY
    }

    public static final String ACCOUNT_TABLE_NAME = "account";

    public static final class AccountAsset {

        private final long accountId;
        private final long assetId;
        private final DbKey dbKey;
        private long quantityQNT;
        private long unconfirmedQuantityQNT;

        private AccountAsset(long accountId, long assetId, long quantityQNT, long unconfirmedQuantityQNT) {
            this.accountId = accountId;
            this.assetId = assetId;
            this.dbKey = accountAssetDbKeyFactory.newKey(this.accountId, this.assetId);
            this.quantityQNT = quantityQNT;
            this.unconfirmedQuantityQNT = unconfirmedQuantityQNT;
        }

        private AccountAsset(ResultSet rs, DbKey dbKey) throws SQLException {
            this.accountId = rs.getLong("account_id");
            this.assetId = rs.getLong("asset_id");
            this.dbKey = dbKey;
            this.quantityQNT = rs.getLong("quantity");
            this.unconfirmedQuantityQNT = rs.getLong("unconfirmed_quantity");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_asset "
                    + "(account_id, asset_id, quantity, unconfirmed_quantity, height, latest) "
                    + "KEY (account_id, asset_id, height) VALUES (?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.accountId);
                pstmt.setLong(++i, this.assetId);
                pstmt.setLong(++i, this.quantityQNT);
                pstmt.setLong(++i, this.unconfirmedQuantityQNT);
                pstmt.setInt(++i, Metro.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getAccountId() {
            return accountId;
        }

        public long getAssetId() {
            return assetId;
        }

        public long getQuantityQNT() {
            return quantityQNT;
        }

        public long getUnconfirmedQuantityQNT() {
            return unconfirmedQuantityQNT;
        }

        private void save() {
            checkBalance(this.accountId, this.quantityQNT, this.unconfirmedQuantityQNT);
            if (this.quantityQNT > 0 || this.unconfirmedQuantityQNT > 0) {
                accountAssetTable.insert(this);
            } else {
                accountAssetTable.delete(this);
            }
        }

        @Override
        public String toString() {
            return "AccountAsset account_id: " + Long.toUnsignedString(accountId) + " asset_id: " + Long.toUnsignedString(assetId)
                    + " quantity: " + quantityQNT + " unconfirmedQuantity: " + unconfirmedQuantityQNT;
        }

    }

    public static final class AccountLease {

        private final long lessorId;
        private final DbKey dbKey;
        private long currentLesseeId;
        private int currentLeasingHeightFrom;
        private int currentLeasingHeightTo;
        private long nextLesseeId;
        private int nextLeasingHeightFrom;
        private int nextLeasingHeightTo;

        private AccountLease(long lessorId,
                             int currentLeasingHeightFrom, int currentLeasingHeightTo, long currentLesseeId) {
            this.lessorId = lessorId;
            this.dbKey = accountLeaseDbKeyFactory.newKey(this.lessorId);
            this.currentLeasingHeightFrom = currentLeasingHeightFrom;
            this.currentLeasingHeightTo = currentLeasingHeightTo;
            this.currentLesseeId = currentLesseeId;
        }

        private AccountLease(ResultSet rs, DbKey dbKey) throws SQLException {
            this.lessorId = rs.getLong("lessor_id");
            this.dbKey = dbKey;
            this.currentLeasingHeightFrom = rs.getInt("current_leasing_height_from");
            this.currentLeasingHeightTo = rs.getInt("current_leasing_height_to");
            this.currentLesseeId = rs.getLong("current_lessee_id");
            this.nextLeasingHeightFrom = rs.getInt("next_leasing_height_from");
            this.nextLeasingHeightTo = rs.getInt("next_leasing_height_to");
            this.nextLesseeId = rs.getLong("next_lessee_id");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_lease "
                    + "(lessor_id, current_leasing_height_from, current_leasing_height_to, current_lessee_id, "
                    + "next_leasing_height_from, next_leasing_height_to, next_lessee_id, height, latest) "
                    + "KEY (lessor_id, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.lessorId);
                DbUtils.setIntZeroToNull(pstmt, ++i, this.currentLeasingHeightFrom);
                DbUtils.setIntZeroToNull(pstmt, ++i, this.currentLeasingHeightTo);
                DbUtils.setLongZeroToNull(pstmt, ++i, this.currentLesseeId);
                DbUtils.setIntZeroToNull(pstmt, ++i, this.nextLeasingHeightFrom);
                DbUtils.setIntZeroToNull(pstmt, ++i, this.nextLeasingHeightTo);
                DbUtils.setLongZeroToNull(pstmt, ++i, this.nextLesseeId);
                pstmt.setInt(++i, Metro.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getLessorId() {
            return lessorId;
        }

        public long getCurrentLesseeId() {
            return currentLesseeId;
        }

        public int getCurrentLeasingHeightFrom() {
            return currentLeasingHeightFrom;
        }

        public int getCurrentLeasingHeightTo() {
            return currentLeasingHeightTo;
        }

        public long getNextLesseeId() {
            return nextLesseeId;
        }

        public int getNextLeasingHeightFrom() {
            return nextLeasingHeightFrom;
        }

        public int getNextLeasingHeightTo() {
            return nextLeasingHeightTo;
        }

    }

    public static final class AccountInfo {

        private final long accountId;
        private final DbKey dbKey;
        private String name;
        private String description;

        private AccountInfo(long accountId, String name, String description) {
            this.accountId = accountId;
            this.dbKey = accountInfoDbKeyFactory.newKey(this.accountId);
            this.name = name;
            this.description = description;
        }

        private AccountInfo(ResultSet rs, DbKey dbKey) throws SQLException {
            this.accountId = rs.getLong("account_id");
            this.dbKey = dbKey;
            this.name = rs.getString("name");
            this.description = rs.getString("description");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_info "
                    + "(account_id, name, description, height, latest) "
                    + "KEY (account_id, height) VALUES (?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.accountId);
                DbUtils.setString(pstmt, ++i, this.name);
                DbUtils.setString(pstmt, ++i, this.description);
                pstmt.setInt(++i, Metro.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getAccountId() {
            return accountId;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        private void save() {
            if (this.name != null || this.description != null) {
                accountInfoTable.insert(this);
            } else {
                accountInfoTable.delete(this);
            }
        }

    }

    public static final class AccountProperty {

        private final long id;
        private final DbKey dbKey;
        private final long recipientId;
        private final long setterId;
        private String property;
        private String value;

        private AccountProperty(long id, long recipientId, long setterId, String property, String value) {
            this.id = id;
            this.dbKey = accountPropertyDbKeyFactory.newKey(this.id);
            this.recipientId = recipientId;
            this.setterId = setterId;
            this.property = property;
            this.value = value;
        }

        private AccountProperty(ResultSet rs, DbKey dbKey) throws SQLException {
            this.id = rs.getLong("id");
            this.dbKey = dbKey;
            this.recipientId = rs.getLong("recipient_id");
            long setterId = rs.getLong("setter_id");
            this.setterId = setterId == 0 ? recipientId : setterId;
            this.property = rs.getString("property");
            this.value = rs.getString("value");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_property "
                    + "(id, recipient_id, setter_id, property, value, height, latest) "
                    + "KEY (id, height) VALUES (?, ?, ?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.id);
                pstmt.setLong(++i, this.recipientId);
                DbUtils.setLongZeroToNull(pstmt, ++i, this.setterId != this.recipientId ? this.setterId : 0);
                DbUtils.setString(pstmt, ++i, this.property);
                DbUtils.setString(pstmt, ++i, this.value);
                pstmt.setInt(++i, Metro.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getId() {
            return id;
        }

        public long getRecipientId() {
            return recipientId;
        }

        public long getSetterId() {
            return setterId;
        }

        public String getProperty() {
            return property;
        }

        public String getValue() {
            return value;
        }

    }

    public static final class PublicKey {

        private final long accountId;
        private final DbKey dbKey;
        private byte[] publicKey;
        private int height;

        private PublicKey(long accountId, byte[] publicKey) {
            this.accountId = accountId;
            this.dbKey = publicKeyDbKeyFactory.newKey(accountId);
            this.publicKey = publicKey;
            this.height = Metro.getBlockchain().getHeight();
        }

        private PublicKey(ResultSet rs, DbKey dbKey) throws SQLException {
            this.accountId = rs.getLong("account_id");
            this.dbKey = dbKey;
            this.publicKey = rs.getBytes("public_key");
            this.height = rs.getInt("height");
        }

        private void save(Connection con) throws SQLException {
            height = Metro.getBlockchain().getHeight();
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO public_key (account_id, public_key, height, latest) "
                    + "KEY (account_id, height) VALUES (?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, accountId);
                DbUtils.setBytes(pstmt, ++i, publicKey);
                pstmt.setInt(++i, height);
                pstmt.executeUpdate();
            }
        }

        public long getAccountId() {
            return accountId;
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

        public int getHeight() {
            return height;
        }

    }

    static class DoubleSpendingException extends RuntimeException {

        DoubleSpendingException(String message, long accountId, long confirmed, long unconfirmed) {
            super(message + " account: " + Long.toUnsignedString(accountId) + " confirmed: " + confirmed + " unconfirmed: " + unconfirmed);
        }

    }

    private static final DbKey.PairKeyFactory<Account> accountDbKeyFactory = new DbKey.PairKeyFactory<Account>("id", "id2") {

        @Override
        public DbKey newKey(Account account) {
            return account.dbKey == null ? newKey(account.id, account.id2) : account.dbKey;
        }

        @Override
        public Account newEntity(DbKey dbKey) {
            return new Account(new Account.FullId(((DbKey.PairKey)dbKey).getIdA(), ((DbKey.PairKey)dbKey).getIdB()));
        }

    };


    private static final VersionedEntityDbTable<Account> accountTable = new VersionedEntityDbTable<Account>(ACCOUNT_TABLE_NAME, accountDbKeyFactory) {

        @Override
        protected Account load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new Account(rs, dbKey);
        }

        @Override
        protected void save(Connection con, Account account) throws SQLException {
            account.save(con);
        }

        @Override
        public void trim(int height) {
            if (Metro.getBlockchain().getGuaranteedBalanceHeight(height) == 0) {
                return;
            }
            VersionedEntityDbTable.trim(db, table, height, dbKeyFactory, 1);
        }

        @Override
        public void checkAvailable(int height) {
            if (height > Metro.getBlockchain().getHeight()) {
                throw new IllegalArgumentException("Height " + height + " exceeds blockchain height " + Metro.getBlockchain().getHeight());
            } else if (height > 0) {
                super.checkAvailable(height);
                return;
            }
        }

    };

    private static final DbKey.LongKeyFactory<AccountInfo> accountInfoDbKeyFactory = new DbKey.LongKeyFactory<AccountInfo>("account_id") {

        @Override
        public DbKey newKey(AccountInfo accountInfo) {
            return accountInfo.dbKey;
        }

    };

    private static final DbKey.LongKeyFactory<AccountLease> accountLeaseDbKeyFactory = new DbKey.LongKeyFactory<AccountLease>("lessor_id") {

        @Override
        public DbKey newKey(AccountLease accountLease) {
            return accountLease.dbKey;
        }

    };

    private static final VersionedEntityDbTable<AccountLease> accountLeaseTable = new VersionedEntityDbTable<AccountLease>("account_lease",
            accountLeaseDbKeyFactory) {

        @Override
        protected AccountLease load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new AccountLease(rs, dbKey);
        }

        @Override
        protected void save(Connection con, AccountLease accountLease) throws SQLException {
            accountLease.save(con);
        }

    };

    private static final VersionedEntityDbTable<AccountInfo> accountInfoTable = new VersionedEntityDbTable<AccountInfo>("account_info",
            accountInfoDbKeyFactory, "name,description") {

        @Override
        protected AccountInfo load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new AccountInfo(rs, dbKey);
        }

        @Override
        protected void save(Connection con, AccountInfo accountInfo) throws SQLException {
            accountInfo.save(con);
        }

    };

    private static final DbKey.LongKeyFactory<PublicKey> publicKeyDbKeyFactory = new DbKey.LongKeyFactory<PublicKey>("account_id") {

        @Override
        public DbKey newKey(PublicKey publicKey) {
            return publicKey.dbKey;
        }

        @Override
        public PublicKey newEntity(DbKey dbKey) {
            return new PublicKey(((DbKey.LongKey)dbKey).getId(), null);
        }

    };

    private static final VersionedPersistentDbTable<PublicKey> publicKeyTable = new VersionedPersistentDbTable<PublicKey>("public_key", publicKeyDbKeyFactory) {

        @Override
        protected PublicKey load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new PublicKey(rs, dbKey);
        }

        @Override
        protected void save(Connection con, PublicKey publicKey) throws SQLException {
            publicKey.save(con);
        }

    };

    private static final DbKey.LinkKeyFactory<AccountAsset> accountAssetDbKeyFactory = new DbKey.LinkKeyFactory<AccountAsset>("account_id", "asset_id") {

        @Override
        public DbKey newKey(AccountAsset accountAsset) {
            return accountAsset.dbKey;
        }

    };

    private static final VersionedEntityDbTable<AccountAsset> accountAssetTable = new VersionedEntityDbTable<AccountAsset>("account_asset", accountAssetDbKeyFactory) {

        @Override
        protected AccountAsset load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new AccountAsset(rs, dbKey);
        }

        @Override
        protected void save(Connection con, AccountAsset accountAsset) throws SQLException {
            accountAsset.save(con);
        }

        @Override
        public void trim(int height) {
            super.trim(Math.max(0, height - Constants.MAX_DIVIDEND_PAYMENT_ROLLBACK));
        }

        @Override
        public void checkAvailable(int height) {
            if (height + Constants.MAX_DIVIDEND_PAYMENT_ROLLBACK < Metro.getBlockchainProcessor().getLowestPossibleHeightForRollback()) {
                throw new IllegalArgumentException("Historical data as of height " + height +" not available.");
            }
            if (height > Metro.getBlockchain().getHeight()) {
                throw new IllegalArgumentException("Height " + height + " exceeds blockchain height " + Metro.getBlockchain().getHeight());
            }
        }

        @Override
        protected String defaultSort() {
            return " ORDER BY quantity DESC, account_id, asset_id ";
        }

    };

    private static final DerivedDbTable accountGuaranteedBaseBalanceTable = new DerivedDbTable("account_guaranteed_balance") {

        @Override
        public void trim(int height) {
            try (Connection con = db.getConnection();
                 PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM account_guaranteed_balance "
                         + "WHERE height < ? AND height >= 0 LIMIT " + Constants.BATCH_COMMIT_SIZE)) {
                pstmtDelete.setInt(1, Metro.getBlockchain().getGuaranteedBalanceHeight(height));
                int count;
                do {
                    count = pstmtDelete.executeUpdate();
                    db.commitTransaction();
                } while (count >= Constants.BATCH_COMMIT_SIZE);
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }
    };

    private static final DbKey.LongKeyFactory<AccountProperty> accountPropertyDbKeyFactory = new DbKey.LongKeyFactory<AccountProperty>("id") {

        @Override
        public DbKey newKey(AccountProperty accountProperty) {
            return accountProperty.dbKey;
        }

    };

    private static final VersionedEntityDbTable<AccountProperty> accountPropertyTable = new VersionedEntityDbTable<AccountProperty>("account_property", accountPropertyDbKeyFactory) {

        @Override
        protected AccountProperty load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new AccountProperty(rs, dbKey);
        }

        @Override
        protected void save(Connection con, AccountProperty accountProperty) throws SQLException {
            accountProperty.save(con);
        }

    };

    private static final ConcurrentMap<DbKey, byte[]> publicKeyCache = Metro.getBooleanProperty("metro.enablePublicKeyCache") ?
            new ConcurrentHashMap<>() : null;

    private static final Listeners<Account,Event> listeners = new Listeners<>();

    private static final Listeners<AccountAsset,Event> assetListeners = new Listeners<>();

    private static final Listeners<AccountLease,Event> leaseListeners = new Listeners<>();

    private static final Listeners<AccountProperty,Event> propertyListeners = new Listeners<>();

    public static boolean addListener(Listener<Account> listener, Event eventType) {
        return listeners.addListener(listener, eventType);
    }

    public static boolean removeListener(Listener<Account> listener, Event eventType) {
        return listeners.removeListener(listener, eventType);
    }

    public static boolean addAssetListener(Listener<AccountAsset> listener, Event eventType) {
        return assetListeners.addListener(listener, eventType);
    }

    public static boolean removeAssetListener(Listener<AccountAsset> listener, Event eventType) {
        return assetListeners.removeListener(listener, eventType);
    }

    public static boolean addLeaseListener(Listener<AccountLease> listener, Event eventType) {
        return leaseListeners.addListener(listener, eventType);
    }

    public static boolean removeLeaseListener(Listener<AccountLease> listener, Event eventType) {
        return leaseListeners.removeListener(listener, eventType);
    }

    public static boolean addPropertyListener(Listener<AccountProperty> listener, Event eventType) {
        return propertyListeners.addListener(listener, eventType);
    }

    public static boolean removePropertyListener(Listener<AccountProperty> listener, Event eventType) {
        return propertyListeners.removeListener(listener, eventType);
    }

    public static int getCount() {
        return publicKeyTable.getCount();
    }

    public static int getAssetAccountCount(long assetId) {
        return accountAssetTable.getCount(new DbClause.LongClause("asset_id", assetId));
    }

    public static int getAssetAccountCount(long assetId, int height) {
        return accountAssetTable.getCount(new DbClause.LongClause("asset_id", assetId), height);
    }

    public static int getAccountAssetCount(long accountId) {
        return accountAssetTable.getCount(new DbClause.LongClause("account_id", accountId));
    }

    public static int getAccountAssetCount(long accountId, int height) {
        return accountAssetTable.getCount(new DbClause.LongClause("account_id", accountId), height);
    }

    public static int getAccountLeaseCount() {
        return accountLeaseTable.getCount();
    }

    public static int getActiveLeaseCount() {
        return accountTable.getCount(new DbClause.NotNullClause("active_lessee_id"));
    }

    public static AccountProperty getProperty(long propertyId) {
        return accountPropertyTable.get(accountPropertyDbKeyFactory.newKey(propertyId));
    }

    public static DbIterator<AccountProperty> getProperties(FullId recipientId, FullId setterId, String property, int from, int to) {
        if (recipientId == null && setterId == null) {
            throw new IllegalArgumentException("At least one of recipientId and setterId must be specified");
        }
        DbClause dbClause = null;
        if (recipientId != null && setterId != null && setterId.equals(recipientId)) {
            dbClause = new DbClause.NullClause("setter_id");
        } else if (setterId != null) {
            dbClause = new DbClause.LongClause("setter_id", setterId.getLeft());
        }
        if (recipientId != null) {
            if (dbClause != null) {
                dbClause = dbClause.and(new DbClause.LongClause("recipient_id", recipientId.getLeft()));
            } else {
                dbClause = new DbClause.LongClause("recipient_id", recipientId.getLeft());
            }
        }
        if (property != null) {
            dbClause = dbClause.and(new DbClause.StringClause("property", property));
        }
        return accountPropertyTable.getManyBy(dbClause, from, to, " ORDER BY property ");
    }

    public static AccountProperty getProperty(long recipientId, String property) {
        return getProperty(recipientId, property, recipientId);
    }

    public static AccountProperty getProperty(long recipientId, String property, long setterId) {
        if (recipientId == 0 || setterId == 0) {
            throw new IllegalArgumentException("Both recipientId and setterId must be specified");
        }
        DbClause dbClause = new DbClause.LongClause("recipient_id", recipientId);
        dbClause = dbClause.and(new DbClause.StringClause("property", property));
        if (setterId != recipientId) {
            dbClause = dbClause.and(new DbClause.LongClause("setter_id", setterId));
        } else {
            dbClause = dbClause.and(new DbClause.NullClause("setter_id"));
        }
        return accountPropertyTable.getBy(dbClause);
    }

    public static Account getAccount(long id) {
        DbClause dbClause = new DbClause.LongClause("id", id);
        Account account = accountTable.getBy(dbClause);
        if (account == null) {
            Map<DbKey, Object> cache = db.getCache(accountTable.toString());
            for (DbKey key: cache.keySet()) {
                DbKey.PairKey fullId = (DbKey.PairKey)key;
                if (fullId.getIdA() == id) {
                    return (Account) cache.get(key);
                }
            }
        }
        return account;
    }

    public static Account.FullId getAccountFullId(long id) {
        DbClause dbClause = new DbClause.LongClause("id", id);
        Account account = accountTable.getBy(dbClause);
        return account != null ? account.getFullId() : null;
    }

    public static Account getAccount(FullId fullId) {
        if (fullId == null) {
            return null;
        }
        DbKey dbKey = accountDbKeyFactory.newKey(fullId.getLeft(), fullId.getRight());
        Account account = accountTable.get(dbKey);
        if (account == null) {
            PublicKey publicKey = publicKeyTable.get(publicKeyDbKeyFactory.newKey(fullId.getLeft()));
            if (publicKey != null) {
                account = accountTable.newEntity(dbKey);
                account.publicKey = publicKey;
            }
        }
        if (account != null && account.balanceMQT == 0) {
            DbKey genesisDbKey = accountDbKeyFactory.newKey(fullId.getLeft(), 0);
            Account genesisAccount = accountTable.get(genesisDbKey);
            if (genesisAccount != null) {
                account.balanceMQT = genesisAccount.balanceMQT;
                account.unconfirmedBalanceMQT = genesisAccount.unconfirmedBalanceMQT;
                account.haveAnotherGenesisAccount = true;
            }
        }
        return account;
    }

    public static Account getAccountMandatory(FullId fullId) {
        if (fullId == null) {
            return null;
        }
        DbKey dbKey = accountDbKeyFactory.newKey(fullId.getLeft(), fullId.getRight());
        return accountTable.get(dbKey);
    }

    //FIXME should we use it
    public static Account getAccount(long id, int height) {
        DbClause dbClause = new DbClause.LongClause("id", id);
        return accountTable.getBy(dbClause, height);
    }

    public static Account getAccount(FullId id, int height) {
        DbKey dbKey = accountDbKeyFactory.newKey(id.getLeft(), id.getRight());
        Account account = accountTable.get(dbKey, height);
        if (account == null) {
            PublicKey publicKey = publicKeyTable.get(publicKeyDbKeyFactory.newKey(id.getLeft()), height);
            if (publicKey != null) {
                account = new Account(id);
                account.publicKey = publicKey;
            }
        }
        return account;
    }

    public static Account getAccount(byte[] publicKey) {
        FullId fullId = FullId.fromPublicKey(publicKey);
        Account account = getAccount(fullId);
        if (account == null) {
            return null;
        }
        if (account.publicKey == null) {
            account.publicKey = publicKeyTable.get(publicKeyDbKeyFactory.newKey(account.getId()));
        }
        if (account.publicKey == null || account.publicKey.publicKey == null || Arrays.equals(account.publicKey.publicKey, publicKey)) {
            return account;
        }
        throw new RuntimeException("DUPLICATE KEY for account " + Long.toUnsignedString(fullId.getLeft())
                + " existing key " + Convert.toHexString(account.publicKey.publicKey) + " new key " + Convert.toHexString(publicKey));
    }

    public static long getId(byte[] publicKey) {
        return FullId.fromPublicKey(publicKey).getLeft();
    }

    public String getFullIdAsString() {
        return getFullId().toString();
    }

    public static byte[] getPublicKey(long id) {
        DbKey dbKey = publicKeyDbKeyFactory.newKey(id);
        byte[] key = null;
        if (publicKeyCache != null) {
            key = publicKeyCache.get(dbKey);
        }
        if (key == null) {
            PublicKey publicKey = publicKeyTable.get(dbKey);
            if (publicKey == null || (key = publicKey.publicKey) == null) {
                return null;
            }
            if (publicKeyCache != null) {
                publicKeyCache.put(dbKey, key);
            }
        }
        return key;
    }


    static Account addOrGetAccount(FullId fullId) {
        if (fullId == null) {
            throw new IllegalArgumentException("Invalid accountFullId null");
        }
        DbKey accDbKey = accountDbKeyFactory.newKey(fullId.getLeft(), fullId.getRight());
        Account account = accountTable.get(accDbKey);
        if (account == null) {
            account = accountTable.newEntity(accDbKey);
            DbKey keyDbKey = publicKeyDbKeyFactory.newKey(fullId.getLeft());
            PublicKey publicKey = publicKeyTable.get(keyDbKey);
            if (publicKey == null) {
                publicKey = publicKeyTable.newEntity(keyDbKey);
                publicKeyTable.insert(publicKey);
            }
            account.publicKey = publicKey;
        }
        return account;
    }

    private static DbIterator<AccountLease> getLeaseChangingAccounts(final int height) {
        Connection con = null;
        try {
            con = db.getConnection();
            PreparedStatement pstmt = con.prepareStatement(
                    "SELECT * FROM account_lease WHERE current_leasing_height_from = ? AND latest = TRUE "
                            + "UNION ALL SELECT * FROM account_lease WHERE current_leasing_height_to = ? AND latest = TRUE "
                            + "ORDER BY current_lessee_id, lessor_id");
            int i = 0;
            pstmt.setInt(++i, height);
            pstmt.setInt(++i, height);
            return accountLeaseTable.getManyBy(con, pstmt, true);
        } catch (SQLException e) {
            DbUtils.close(con);
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static DbIterator<AccountAsset> getAccountAssets(long accountId, int from, int to) {
        return accountAssetTable.getManyBy(new DbClause.LongClause("account_id", accountId), from, to);
    }

    public static DbIterator<AccountAsset> getAccountAssets(long accountId, int height, int from, int to) {
        return accountAssetTable.getManyBy(new DbClause.LongClause("account_id", accountId), height, from, to);
    }

    public static AccountAsset getAccountAsset(long accountId, long assetId) {
        return accountAssetTable.get(accountAssetDbKeyFactory.newKey(accountId, assetId));
    }

    public static AccountAsset getAccountAsset(long accountId, long assetId, int height) {
        return accountAssetTable.get(accountAssetDbKeyFactory.newKey(accountId, assetId), height);
    }

    public static DbIterator<AccountAsset> getAssetAccounts(long assetId, int from, int to) {
        return accountAssetTable.getManyBy(new DbClause.LongClause("asset_id", assetId), from, to, " ORDER BY quantity DESC, account_id ");
    }

    public static DbIterator<AccountAsset> getAssetAccounts(long assetId, int height, int from, int to) {
        return accountAssetTable.getManyBy(new DbClause.LongClause("asset_id", assetId), height, from, to, " ORDER BY quantity DESC, account_id ");
    }

    public static long getAssetBalanceQNT(long accountId, long assetId, int height) {
        AccountAsset accountAsset = accountAssetTable.get(accountAssetDbKeyFactory.newKey(accountId, assetId), height);
        return accountAsset == null ? 0 : accountAsset.quantityQNT;
    }

    public static long getAssetBalanceQNT(long accountId, long assetId) {
        AccountAsset accountAsset = accountAssetTable.get(accountAssetDbKeyFactory.newKey(accountId, assetId));
        return accountAsset == null ? 0 : accountAsset.quantityQNT;
    }

    public static long getUnconfirmedAssetBalanceQNT(long accountId, long assetId) {
        AccountAsset accountAsset = accountAssetTable.get(accountAssetDbKeyFactory.newKey(accountId, assetId));
        return accountAsset == null ? 0 : accountAsset.unconfirmedQuantityQNT;
    }

    public static DbIterator<AccountInfo> searchAccounts(String query, int from, int to) {
        return accountInfoTable.search(query, DbClause.EMPTY_CLAUSE, from, to);
    }

    static {

        Metro.getBlockchainProcessor().addListener(block -> {
            int height = block.getHeight();
            List<AccountLease> changingLeases = new ArrayList<>();
            try (DbIterator<AccountLease> leases = getLeaseChangingAccounts(height)) {
                while (leases.hasNext()) {
                    changingLeases.add(leases.next());
                }
            }
            for (AccountLease lease : changingLeases) {
                Account lessor = Account.getAccount(lease.lessorId);
                if (height == lease.currentLeasingHeightFrom) {
                    lessor.activeLesseeId = lease.currentLesseeId;
                    leaseListeners.notify(lease, Event.LEASE_STARTED);
                } else if (height == lease.currentLeasingHeightTo) {
                    leaseListeners.notify(lease, Event.LEASE_ENDED);
                    lessor.activeLesseeId = 0;
                    if (lease.nextLeasingHeightFrom == 0) {
                        lease.currentLeasingHeightFrom = 0;
                        lease.currentLeasingHeightTo = 0;
                        lease.currentLesseeId = 0;
                        accountLeaseTable.delete(lease);
                    } else {
                        lease.currentLeasingHeightFrom = lease.nextLeasingHeightFrom;
                        lease.currentLeasingHeightTo = lease.nextLeasingHeightTo;
                        lease.currentLesseeId = lease.nextLesseeId;
                        lease.nextLeasingHeightFrom = 0;
                        lease.nextLeasingHeightTo = 0;
                        lease.nextLesseeId = 0;
                        accountLeaseTable.insert(lease);
                        if (height == lease.currentLeasingHeightFrom) {
                            lessor.activeLesseeId = lease.currentLesseeId;
                            leaseListeners.notify(lease, Event.LEASE_STARTED);
                        }
                    }
                }
                lessor.save();
            }
        }, BlockchainProcessor.Event.AFTER_BLOCK_APPLY);

        if (publicKeyCache != null) {

            Metro.getBlockchainProcessor().addListener(block -> {
                publicKeyCache.remove(publicKeyDbKeyFactory.newKey(FullId.fromPublicKey(block.getGeneratorPublicKey()).getLeft()));
                block.getTransactions().forEach(transaction -> {
                    publicKeyCache.remove(publicKeyDbKeyFactory.newKey(transaction.getSenderId()));
                    if (!transaction.getAppendages(appendix -> (appendix instanceof Appendix.PublicKeyAnnouncement), false).isEmpty()) {
                        publicKeyCache.remove(publicKeyDbKeyFactory.newKey(transaction.getRecipientId()));
                    }
                    if (transaction.getType() == ShufflingTransaction.SHUFFLING_RECIPIENTS) {
                        Attachment.ShufflingRecipients shufflingRecipients = (Attachment.ShufflingRecipients) transaction.getAttachment();
                        for (byte[] publicKey : shufflingRecipients.getRecipientPublicKeys()) {
                            publicKeyCache.remove(publicKeyDbKeyFactory.newKey(FullId.fromPublicKey(publicKey).getLeft()));
                        }
                    }
                });
            }, BlockchainProcessor.Event.BLOCK_POPPED);

            Metro.getBlockchainProcessor().addListener(block -> publicKeyCache.clear(), BlockchainProcessor.Event.RESCAN_BEGIN);

        }

    }

    static void init() {}


    private final long id;
    private final int id2;
    private final DbKey dbKey;
    private PublicKey publicKey;
    private long balanceMQT;
    private long unconfirmedBalanceMQT;
    private long forgedBalanceMQT;
    private int lastForgedHeight;
    private long activeLesseeId;
    private Set<ControlType> controls;
    private boolean haveAnotherGenesisAccount;

    private Account(FullId id) {
        if (!Crypto.rsDecode(Crypto.rsEncode(id)).equals(id)) {
            Logger.logMessage("CRITICAL ERROR: Reed-Solomon encoding fails for " + id);
        }
        this.id = id.getLeft();
        this.id2 = id.getRight();
        this.dbKey = accountDbKeyFactory.newKey(this.id, this.id2);
        this.controls = Collections.emptySet();
    }

    private Account(ResultSet rs, DbKey dbKey) throws SQLException {
        this.id = rs.getLong("id");
        this.id2 = rs.getInt("id2");
        this.dbKey = dbKey;
        this.balanceMQT = rs.getLong("balance");
        this.unconfirmedBalanceMQT = rs.getLong("unconfirmed_balance");
        this.forgedBalanceMQT = rs.getLong("forged_balance");
        this.lastForgedHeight = rs.getInt("last_forged_height");
        this.activeLesseeId = rs.getLong("active_lessee_id");
        if (rs.getBoolean("has_control_phasing")) {
            controls = Collections.unmodifiableSet(EnumSet.of(ControlType.PHASING_ONLY));
        } else {
            controls = Collections.emptySet();
        }
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account (id, id2, "
                + "balance, unconfirmed_balance, forged_balance, last_forged_height, "
                + "active_lessee_id, has_control_phasing, height, latest) "
                + "KEY (id, height) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setInt(++i, this.id2);
            pstmt.setLong(++i, this.balanceMQT);
            pstmt.setLong(++i, this.unconfirmedBalanceMQT);
            pstmt.setLong(++i, this.forgedBalanceMQT);
            pstmt.setInt(++i, this.lastForgedHeight);
            DbUtils.setLongZeroToNull(pstmt, ++i, this.activeLesseeId);
            pstmt.setBoolean(++i, controls.contains(ControlType.PHASING_ONLY));
            pstmt.setInt(++i, Metro.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    private void save() {
        if (balanceMQT == 0 && unconfirmedBalanceMQT == 0 && forgedBalanceMQT == 0 && activeLesseeId == 0 && controls.isEmpty()) {
            accountTable.delete(this, true);
        } else {
            if (haveAnotherGenesisAccount) {
                Account genesis = Account.getAccountMandatory(new Account.FullId(id,0));
                genesis.replace(getFullId());
                haveAnotherGenesisAccount = false;
            }
            accountTable.insert(this);

        }
    }

    public void replace(FullId fullId) {
        if (this.id2 != 0 || !fullId.getLeft().equals(this.id)) {
            throw new IllegalStateException(String.format("Can not replace full id for %s to %s", getFullId(), fullId));
        }
        accountTable.delete(this, true);
    }

    public void setLastForgedHeight(int height) {
        this.lastForgedHeight = height;
        save();
    }

    public long getId() {
        return id;
    }

    public int getId2() {
        return id2;
    }

    public FullId getFullId() {
        return new Account.FullId(id,id2);
    }

    public AccountInfo getAccountInfo() {
        return accountInfoTable.get(accountInfoDbKeyFactory.newKey(this.getId()));
    }

    void setAccountInfo(String name, String description) {
        name = Convert.emptyToNull(name.trim());
        description = Convert.emptyToNull(description.trim());
        AccountInfo accountInfo = getAccountInfo();
        if (accountInfo == null) {
            accountInfo = new AccountInfo(id, name, description);
        } else {
            accountInfo.name = name;
            accountInfo.description = description;
        }
        accountInfo.save();
    }

    public AccountLease getAccountLease() {
        return accountLeaseTable.get(accountLeaseDbKeyFactory.newKey(this.getId()));
    }

    public EncryptedData encryptTo(byte[] data, String senderSecretPhrase, boolean compress) {
        byte[] key = getPublicKey(this.id);
        if (key == null) {
            throw new IllegalArgumentException("Recipient account doesn't have a public key set");
        }
        return Account.encryptTo(key, data, senderSecretPhrase, compress);
    }

    public static EncryptedData encryptTo(byte[] publicKey, byte[] data, String senderSecretPhrase, boolean compress) {
        if (compress && data.length > 0) {
            data = Convert.compress(data);
        }
        return EncryptedData.encrypt(data, senderSecretPhrase, publicKey);
    }

    public byte[] decryptFrom(EncryptedData encryptedData, String recipientSecretPhrase, boolean uncompress) {
        byte[] key = getPublicKey(this.id);
        if (key == null) {
            throw new IllegalArgumentException("Sender account doesn't have a public key set");
        }
        return Account.decryptFrom(key, encryptedData, recipientSecretPhrase, uncompress);
    }

    public static byte[] decryptFrom(byte[] publicKey, EncryptedData encryptedData, String recipientSecretPhrase, boolean uncompress) {
        byte[] decrypted = encryptedData.decrypt(recipientSecretPhrase, publicKey);
        if (uncompress && decrypted.length > 0) {
            decrypted = Convert.uncompress(decrypted);
        }
        return decrypted;
    }

    public long getBalanceMQT() {
        return balanceMQT;
    }

    public long getTimeLockedGenesisBalance() {
        if (!Consensus.GENESIS_BALANCES_TIME_LOCK) {
            return 0;
        }

        return getLockedGenesisSum(getGenesisAccountBalanceMQT());
    }

    public static long getLockedGenesisSum(long lockedSumMQT) {
        int h = Metro.getBlockchain().getLastKeyBlock() != null ? Metro.getBlockchain().getLastKeyBlock().getLocalHeight() : -1;
        int i = Consensus.SUBSIDY_HALVING_INTERVAL;
        BigInteger lockedSum = BigInteger.valueOf(lockedSumMQT).multiply(BigInteger.valueOf(i - 1 - h)).divide(BigInteger.valueOf(i));
        return Convert.toLong(lockedSum);
    }

    public long getUnconfirmedBalanceMQT() {
        int currentHeight = Metro.getBlockchain().getHeight();
        int guaranteedBalanceHeight = Metro.getBlockchain().getAvailableBalanceHeight(currentHeight, COINBASE_MATURITY_PERIOD);
        long rawUnlockedBalance = Math.max(Math.subtractExact(unconfirmedBalanceMQT, getTimeLockedGenesisBalance()), 0);
        try (Connection con = db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT SUM (additions) AS additions "
                     + "FROM account_guaranteed_balance WHERE account_id = ? AND coinbase AND height > ? AND height <= ?")) {
            pstmt.setLong(1, this.id);
            pstmt.setInt(2, guaranteedBalanceHeight);
            pstmt.setInt(3, currentHeight);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return rawUnlockedBalance;
                }
                return Math.max(Math.subtractExact(rawUnlockedBalance, rs.getLong("additions")), 0);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public static long getCirculationSupply() {
        if (Metro.getBlockchain().getLastKeyBlock() == null) {
            return 0;
        }
        int currentHeight = Metro.getBlockchain().getHeight();
        long genesisBalanceSum;
        try (Connection con = db.getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT SUM (balance) AS genesis_balance_sum " +
                     " FROM account WHERE height = 0 and id <> " + Genesis.BURNING_ACCOUNT_ID.getLeft())) {
            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                genesisBalanceSum = rs.getLong("genesis_balance_sum");
            }

        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }

        int h = Metro.getBlockchain().getLastKeyBlock() != null ? Metro.getBlockchain().getLastKeyBlock().getLocalHeight() : -1;
        int i = Consensus.SUBSIDY_HALVING_INTERVAL;
        long unlockedGenesisBalance = genesisBalanceSum - getLockedGenesisSum(genesisBalanceSum);
        int matureHeight = Metro.getBlockchain().getLastKeyBlock().getLocalHeight() - Consensus.COINBASE_MATURITY_PERIOD >0 ?
                Metro.getBlockchain().getLastKeyBlock().getLocalHeight() - Consensus.COINBASE_MATURITY_PERIOD : 0;

        return unlockedGenesisBalance + Consensus.INITIAL_SUBSIDY * matureHeight;
    }

    public long getForgedBalanceMQT() {
        return forgedBalanceMQT;
    }

    public long getEffectiveBalanceMTR() {
        return getEffectiveBalanceMTR(Metro.getBlockchain().getHeight());
    }

    private long getGenesisAccountBalanceMQT() {
        try {
            Account genesisAccount = getAccount(id, 0);
            return genesisAccount == null ? 0 : genesisAccount.getBalanceMQT();
        } catch (IllegalArgumentException iae) {
            return 0;
        }
    }

    private long getGenesisAccountBalanceMTR() {
        return getGenesisAccountBalanceMQT() / Constants.ONE_MTR;
    }

    public long getEffectiveBalanceMTR(int height) {
        if (this.publicKey == null) {
            DbKey keyDbKey = publicKeyDbKeyFactory.newKey(id);
            this.publicKey = publicKeyTable.get(keyDbKey);
        }
        if (this.publicKey == null || this.publicKey.publicKey == null) {
            return 0;
        }
        int guaranteedBalanceHeight = Metro.getBlockchain().getGuaranteedBalanceHeight(height);
        int confirmations = height - guaranteedBalanceHeight;
        if (height <= guaranteedBalanceHeight || guaranteedBalanceHeight == 0) {
            return getGenesisAccountBalanceMTR();
        }
        if (height - this.publicKey.height <= confirmations) {
            return 0; // Accounts with the public key revealed less than 30 block clusters ago are not allowed to generate blocks
        }
        Metro.getBlockchain().readLock();
        try {
            long effectiveBalanceMQT = getLessorsGuaranteedBalanceMQT(height, guaranteedBalanceHeight);
            if (activeLesseeId == 0) {
                effectiveBalanceMQT += getGuaranteedBalanceMQT(confirmations, height);
            }
	        return effectiveBalanceMQT < Constants.MIN_FORGING_BALANCE_MQT ? 0 : effectiveBalanceMQT / Constants.ONE_MTR;
        } finally {
            Metro.getBlockchain().readUnlock();
        }
    }

    private long getLessorsGuaranteedBalanceMQT(int height, int guaranteedBalanceHeight) {
        List<Account> lessors = new ArrayList<>();
        try (DbIterator<Account> iterator = getLessors(height)) {
            while (iterator.hasNext()) {
                lessors.add(iterator.next());
            }
        }
        Long[] lessorIds = new Long[lessors.size()];
        long[] balances = new long[lessors.size()];
        for (int i = 0; i < lessors.size(); i++) {
            lessorIds[i] = lessors.get(i).getId();
            balances[i] = lessors.get(i).getBalanceMQT();
        }
        int blockchainHeight = Metro.getBlockchain().getHeight();
        try (Connection con = db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("SELECT account_id, SUM (additions) AS additions "
                     + "FROM account_guaranteed_balance, TABLE (id BIGINT=?) T WHERE account_id = T.id AND NOT coinbase AND height > ? "
                     + (height < blockchainHeight ? " AND height <= ? " : "")
                     + " GROUP BY account_id ORDER BY account_id")) {
            pstmt.setObject(1, lessorIds);
            pstmt.setInt(2, guaranteedBalanceHeight);
            if (height < blockchainHeight) {
                pstmt.setInt(3, height);
            }
            long total = 0;
            int i = 0;
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    long accountId = rs.getLong("account_id");
                    while (lessorIds[i] < accountId && i < lessorIds.length) {
                        total += balances[i++];
                    }
                    if (lessorIds[i] == accountId) {
                        total += Math.max(balances[i++] - rs.getLong("additions"), 0);
                    }
                }
            }
            while (i < balances.length) {
                total += balances[i++];
            }
            return total;
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    public DbIterator<Account> getLessors() {
        return accountTable.getManyBy(new DbClause.LongClause("active_lessee_id", id), 0, -1, " ORDER BY id ASC ");
    }

    public DbIterator<Account> getLessors(int height) {
        return accountTable.getManyBy(new DbClause.LongClause("active_lessee_id", id), height, 0, -1, " ORDER BY id ASC ");
    }

    public long getGuaranteedBalanceMQT() {
        int height = Metro.getBlockchain().getHeight();
        return getGuaranteedBalanceMQT(height - Metro.getBlockchain().getGuaranteedBalanceHeight(height), height);
    }

    public long getGuaranteedBalanceMQT(final int numberOfConfirmations, final int currentHeight) {
        Metro.getBlockchain().readLock();
        try {
            int height = currentHeight - numberOfConfirmations;
            if (currentHeight < Metro.getBlockchainProcessor().getLowestPossibleHeightForRollback()
                    || height > Metro.getBlockchain().getHeight()) {
                throw new IllegalArgumentException("Height " + height + " not available for guaranteed balance calculation");
            }
            try (Connection con = db.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("SELECT SUM (additions) AS additions "
                         + "FROM account_guaranteed_balance WHERE account_id = ? AND NOT coinbase AND height > ? AND height <= ?")) {
                pstmt.setLong(1, this.id);
                pstmt.setInt(2, height);
                pstmt.setInt(3, currentHeight);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (!rs.next()) {
                        return balanceMQT;
                    }
                    return Math.max(Math.subtractExact(balanceMQT, rs.getLong("additions")), 0);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        } finally {
            Metro.getBlockchain().readUnlock();
        }
    }

    public DbIterator<AccountAsset> getAssets(int from, int to) {
        return accountAssetTable.getManyBy(new DbClause.LongClause("account_id", this.id), from, to);
    }

    public DbIterator<AccountAsset> getAssets(int height, int from, int to) {
        return accountAssetTable.getManyBy(new DbClause.LongClause("account_id", this.id), height, from, to);
    }

    public DbIterator<Trade> getTrades(int from, int to) {
        return Trade.getAccountTrades(this.id, from, to);
    }

    public DbIterator<AssetTransfer> getAssetTransfers(int from, int to) {
        return AssetTransfer.getAccountAssetTransfers(this.id, from, to);
    }

    public AccountAsset getAsset(long assetId) {
        return accountAssetTable.get(accountAssetDbKeyFactory.newKey(this.id, assetId));
    }

    public AccountAsset getAsset(long assetId, int height) {
        return accountAssetTable.get(accountAssetDbKeyFactory.newKey(this.id, assetId), height);
    }

    public long getAssetBalanceQNT(long assetId) {
        return getAssetBalanceQNT(this.id, assetId);
    }

    public long getAssetBalanceQNT(long assetId, int height) {
        return getAssetBalanceQNT(this.id, assetId, height);
    }

    public long getUnconfirmedAssetBalanceQNT(long assetId) {
        return getUnconfirmedAssetBalanceQNT(this.id, assetId);
    }

    public Set<ControlType> getControls() {
        return controls;
    }

    void leaseEffectiveBalance(long lesseeId, int period) {
        int height = Metro.getBlockchain().getHeight();
        AccountLease accountLease = accountLeaseTable.get(accountLeaseDbKeyFactory.newKey(this.getId()));
        if (accountLease == null) {
            accountLease = new AccountLease(id,
                    height + Constants.LEASING_DELAY,
                    height + Constants.LEASING_DELAY + period,
                    lesseeId);
        } else if (accountLease.currentLesseeId == 0) {
            accountLease.currentLeasingHeightFrom = height + Constants.LEASING_DELAY;
            accountLease.currentLeasingHeightTo = height + Constants.LEASING_DELAY + period;
            accountLease.currentLesseeId = lesseeId;
        } else {
            accountLease.nextLeasingHeightFrom = height + Constants.LEASING_DELAY;
            if (accountLease.nextLeasingHeightFrom < accountLease.currentLeasingHeightTo) {
                accountLease.nextLeasingHeightFrom = accountLease.currentLeasingHeightTo;
            }
            accountLease.nextLeasingHeightTo = accountLease.nextLeasingHeightFrom + period;
            accountLease.nextLesseeId = lesseeId;
        }
        accountLeaseTable.insert(accountLease);
        leaseListeners.notify(accountLease, Event.LEASE_SCHEDULED);
    }

    void addControl(ControlType control) {
        if (controls.contains(control)) {
            return;
        }
        EnumSet<ControlType> newControls = EnumSet.of(control);
        newControls.addAll(controls);
        controls = Collections.unmodifiableSet(newControls);
        accountTable.insert(this);
    }

    void removeControl(ControlType control) {
        if (!controls.contains(control)) {
            return;
        }
        EnumSet<ControlType> newControls = EnumSet.copyOf(controls);
        newControls.remove(control);
        controls = Collections.unmodifiableSet(newControls);
        save();
    }

    void setProperty(Transaction transaction, Account setterAccount, String property, String value) {
        value = Convert.emptyToNull(value);
        AccountProperty accountProperty = getProperty(this.id, property, setterAccount.id);
        if (accountProperty == null) {
            accountProperty = new AccountProperty(transaction.getId(), this.id, setterAccount.id, property, value);
        } else {
            accountProperty.value = value;
        }
        accountPropertyTable.insert(accountProperty);
        listeners.notify(this, Event.SET_PROPERTY);
        propertyListeners.notify(accountProperty, Event.SET_PROPERTY);
    }

    void deleteProperty(long propertyId) {
        AccountProperty accountProperty = accountPropertyTable.get(accountPropertyDbKeyFactory.newKey(propertyId));
        if (accountProperty == null) {
            return;
        }
        if (accountProperty.getSetterId() != this.id && accountProperty.getRecipientId() != this.id) {
            throw new RuntimeException("Property " + Long.toUnsignedString(propertyId) + " cannot be deleted by " + Long.toUnsignedString(this.id));
        }
        accountPropertyTable.delete(accountProperty);
        listeners.notify(this, Event.DELETE_PROPERTY);
        propertyListeners.notify(accountProperty, Event.DELETE_PROPERTY);
    }

    static boolean setOrVerify(long accountId, byte[] key) {
        DbKey dbKey = publicKeyDbKeyFactory.newKey(accountId);
        PublicKey publicKey = publicKeyTable.get(dbKey);
        if (publicKey == null) {
            publicKey = publicKeyTable.newEntity(dbKey);
        }
        if (publicKey.publicKey == null) {
            publicKey.publicKey = key;
            publicKey.height = Metro.getBlockchain().getHeight();
            return true;
        }
        return Arrays.equals(publicKey.publicKey, key);
    }

    void apply(byte[] key) {
        DbKey keyDbKey = publicKeyDbKeyFactory.newKey(id);
        PublicKey publicKey = publicKeyTable.get(keyDbKey);
        if (publicKey == null) {
            publicKey = publicKeyTable.newEntity(keyDbKey);
        }
        if (publicKey.publicKey == null) {
            publicKey.publicKey = key;
            publicKeyTable.insert(publicKey);
        } else if (! Arrays.equals(publicKey.publicKey, key)) {
            throw new IllegalStateException("Public key mismatch");
        } else if (publicKey.height >= Metro.getBlockchain().getHeight() - 1) {
            PublicKey dbPublicKey = publicKeyTable.get(keyDbKey, false);
            if (dbPublicKey == null || dbPublicKey.publicKey == null) {
                publicKeyTable.insert(publicKey);
            }
        }
        if (publicKeyCache != null) {
            publicKeyCache.put(keyDbKey, key);
        }
        this.publicKey = publicKey;
    }

    void addToAssetBalanceQNT(LedgerEvent event, long eventId, long assetId, long quantityQNT) {
        if (quantityQNT == 0) {
            return;
        }
        AccountAsset accountAsset;
        accountAsset = accountAssetTable.get(accountAssetDbKeyFactory.newKey(this.id, assetId));
        long assetBalance = accountAsset == null ? 0 : accountAsset.quantityQNT;
        assetBalance = Math.addExact(assetBalance, quantityQNT);
        if (accountAsset == null) {
            accountAsset = new AccountAsset(this.id, assetId, assetBalance, 0);
        } else {
            accountAsset.quantityQNT = assetBalance;
        }
        accountAsset.save();
        listeners.notify(this, Event.ASSET_BALANCE);
        assetListeners.notify(accountAsset, Event.ASSET_BALANCE);
        if (AccountLedger.mustLogEntry(this.id, false)) {
            AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id, LedgerHolding.ASSET_BALANCE, assetId,
                    quantityQNT, assetBalance));
        }
    }

    void addToUnconfirmedAssetBalanceQNT(LedgerEvent event, long eventId, long assetId, long quantityQNT) {
        if (quantityQNT == 0) {
            return;
        }
        AccountAsset accountAsset;
        accountAsset = accountAssetTable.get(accountAssetDbKeyFactory.newKey(this.id, assetId));
        long unconfirmedAssetBalance = accountAsset == null ? 0 : accountAsset.unconfirmedQuantityQNT;
        unconfirmedAssetBalance = Math.addExact(unconfirmedAssetBalance, quantityQNT);
        if (accountAsset == null) {
            accountAsset = new AccountAsset(this.id, assetId, 0, unconfirmedAssetBalance);
        } else {
            accountAsset.unconfirmedQuantityQNT = unconfirmedAssetBalance;
        }
        accountAsset.save();
        listeners.notify(this, Event.UNCONFIRMED_ASSET_BALANCE);
        assetListeners.notify(accountAsset, Event.UNCONFIRMED_ASSET_BALANCE);
        if (event == null) {
            return;
        }
        if (AccountLedger.mustLogEntry(this.id, true)) {
            AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id,
                    LedgerHolding.UNCONFIRMED_ASSET_BALANCE, assetId,
                    quantityQNT, unconfirmedAssetBalance));
        }
    }

    void addToAssetAndUnconfirmedAssetBalanceQNT(LedgerEvent event, long eventId, long assetId, long quantityQNT) {
        if (quantityQNT == 0) {
            return;
        }
        AccountAsset accountAsset;
        accountAsset = accountAssetTable.get(accountAssetDbKeyFactory.newKey(this.id, assetId));
        long assetBalance = accountAsset == null ? 0 : accountAsset.quantityQNT;
        assetBalance = Math.addExact(assetBalance, quantityQNT);
        long unconfirmedAssetBalance = accountAsset == null ? 0 : accountAsset.unconfirmedQuantityQNT;
        unconfirmedAssetBalance = Math.addExact(unconfirmedAssetBalance, quantityQNT);
        if (accountAsset == null) {
            accountAsset = new AccountAsset(this.id, assetId, assetBalance, unconfirmedAssetBalance);
        } else {
            accountAsset.quantityQNT = assetBalance;
            accountAsset.unconfirmedQuantityQNT = unconfirmedAssetBalance;
        }
        accountAsset.save();
        listeners.notify(this, Event.ASSET_BALANCE);
        listeners.notify(this, Event.UNCONFIRMED_ASSET_BALANCE);
        assetListeners.notify(accountAsset, Event.ASSET_BALANCE);
        assetListeners.notify(accountAsset, Event.UNCONFIRMED_ASSET_BALANCE);
        if (AccountLedger.mustLogEntry(this.id, true)) {
            AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id,
                    LedgerHolding.UNCONFIRMED_ASSET_BALANCE, assetId,
                    quantityQNT, unconfirmedAssetBalance));
        }
        if (AccountLedger.mustLogEntry(this.id, false)) {
            AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id,
                    LedgerHolding.ASSET_BALANCE, assetId,
                    quantityQNT, assetBalance));
        }
    }

    void addToBalanceMQT(LedgerEvent event, long eventId, long amountMQT) {
        addToBalanceMQT(event, eventId, amountMQT, 0);
    }

    void addToBalanceMQT(LedgerEvent event, long eventId, long amountMQT, long feeMQT) {
        if (amountMQT == 0 && feeMQT == 0) {
            return;
        }
        long totalAmountMQT = Math.addExact(amountMQT, feeMQT);
        this.balanceMQT = Math.addExact(this.balanceMQT, totalAmountMQT);
        addToGuaranteedBalanceMQT(totalAmountMQT, false);
        checkBalance(this.id, this.balanceMQT, this.unconfirmedBalanceMQT);
        save();
        listeners.notify(this, Event.BALANCE);
        if (AccountLedger.mustLogEntry(this.id, false)) {
            if (feeMQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.METRO_BALANCE, null, feeMQT, this.balanceMQT - amountMQT));
            }
            if (amountMQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id,
                        LedgerHolding.METRO_BALANCE, null, amountMQT, this.balanceMQT));
            }
        }
    }

    void addToUnconfirmedBalanceMQT(LedgerEvent event, long eventId, long amountMQT) {
        addToUnconfirmedBalanceMQT(event, eventId, amountMQT, 0);
    }

    void addToUnconfirmedBalanceMQT(LedgerEvent event, long eventId, long amountMQT, long feeMQT) {
        if (amountMQT == 0 && feeMQT == 0) {
            return;
        }
        long totalAmountMQT = Math.addExact(amountMQT, feeMQT);
        this.unconfirmedBalanceMQT = Math.addExact(this.unconfirmedBalanceMQT, totalAmountMQT);
        checkBalance(this.id, this.balanceMQT, this.unconfirmedBalanceMQT);
        save();
        listeners.notify(this, Event.UNCONFIRMED_BALANCE);
        if (event == null) {
            return;
        }
        if (AccountLedger.mustLogEntry(this.id, true)) {
            if (feeMQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_METRO_BALANCE, null, feeMQT, this.unconfirmedBalanceMQT - amountMQT));
            }
            if (amountMQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_METRO_BALANCE, null, amountMQT, this.unconfirmedBalanceMQT));
            }
        }
    }

    void addToBalanceAndUnconfirmedBalanceMQT(LedgerEvent event, long eventId, long amountMQT) {
        addToBalanceAndUnconfirmedBalanceMQT(event, eventId, amountMQT, 0);
    }

    void addToBalanceAndUnconfirmedBalanceMQT(LedgerEvent event, long eventId, long amountMQT, long feeMQT) {
        if (amountMQT == 0 && feeMQT == 0) {
            return;
        }
        long totalAmountMQT = Math.addExact(amountMQT, feeMQT);
        this.balanceMQT = Math.addExact(this.balanceMQT, totalAmountMQT);
        this.unconfirmedBalanceMQT = Math.addExact(this.unconfirmedBalanceMQT, totalAmountMQT);
        addToGuaranteedBalanceMQT(totalAmountMQT, event == LedgerEvent.ORDINARY_COINBASE || event == LedgerEvent.INFEED_COINBASE);
        checkBalance(this.id, this.balanceMQT, this.unconfirmedBalanceMQT);
        save();
        listeners.notify(this, Event.BALANCE);
        listeners.notify(this, Event.UNCONFIRMED_BALANCE);
        if (event == null) {
            return;
        }
        if (AccountLedger.mustLogEntry(this.id, true)) {
            if (feeMQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_METRO_BALANCE, null, feeMQT, this.unconfirmedBalanceMQT - amountMQT));
            }
            if (amountMQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_METRO_BALANCE, null, amountMQT, this.unconfirmedBalanceMQT));
            }
        }
        if (AccountLedger.mustLogEntry(this.id, false)) {
            if (feeMQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.METRO_BALANCE, null, feeMQT, this.balanceMQT - amountMQT));
            }
            if (amountMQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id,
                        LedgerHolding.METRO_BALANCE, null, amountMQT, this.balanceMQT));
            }
        }
    }

    void addToForgedBalanceMQT(long amountMQT) {
        if (amountMQT == 0) {
            return;
        }
        this.forgedBalanceMQT = Math.addExact(this.forgedBalanceMQT, amountMQT);
        save();
    }

    private static void checkBalance(long accountId, long confirmed, long unconfirmed) {
        if (accountId == Genesis.BURNING_ACCOUNT_ID.getLeft()) {
            return;
        }
        if (confirmed < 0) {
            throw new DoubleSpendingException("Negative balance or quantity: ", accountId, confirmed, unconfirmed);
        }
        if (unconfirmed < 0) {
            throw new DoubleSpendingException("Negative unconfirmed balance or quantity: ", accountId, confirmed, unconfirmed);
        }
        if (unconfirmed > confirmed) {
            throw new DoubleSpendingException("Unconfirmed exceeds confirmed balance or quantity: ", accountId, confirmed, unconfirmed);
        }
    }

    private void addToGuaranteedBalanceMQT(long amountMQT, boolean isCoinbase) {
        if (amountMQT <= 0) {
            return;
        }
        int blockchainHeight = Metro.getBlockchain().getHeight();
        try (Connection con = db.getConnection();
             // TODO need to know whether it was coinbase
             PreparedStatement pstmtSelect = con.prepareStatement("SELECT additions FROM account_guaranteed_balance "
                     + "WHERE account_id = ? AND coinbase = ? AND height = ?");
             PreparedStatement pstmtUpdate = con.prepareStatement("MERGE INTO account_guaranteed_balance (account_id, "
                     + " additions, height, coinbase) KEY (account_id, height, coinbase) VALUES(?, ?, ?, ?)")) {
            pstmtSelect.setLong(1, this.id);
            pstmtSelect.setBoolean(2, isCoinbase);
            pstmtSelect.setInt(3, blockchainHeight);
            try (ResultSet rs = pstmtSelect.executeQuery()) {
                long additions = amountMQT;
                if (rs.next()) {
                    additions = Math.addExact(additions, rs.getLong("additions"));
                }
                pstmtUpdate.setLong(1, this.id);
                pstmtUpdate.setLong(2, additions);
                pstmtUpdate.setInt(3, blockchainHeight);
                pstmtUpdate.setBoolean(4, isCoinbase);
                pstmtUpdate.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    void payDividends(final long transactionId, Attachment.ColoredCoinsDividendPayment attachment) {
        long totalDividend = 0;
        List<AccountAsset> accountAssets = new ArrayList<>();
        try (DbIterator<AccountAsset> iterator = getAssetAccounts(attachment.getAssetId(), attachment.getHeight(), 0, -1)) {
            while (iterator.hasNext()) {
                accountAssets.add(iterator.next());
            }
        }
        final long amountMQTPerQNT = attachment.getAmountMQTPerQNT();
        long numAccounts = 0;
        for (final AccountAsset accountAsset : accountAssets) {
            if (accountAsset.getAccountId() != this.id && accountAsset.getQuantityQNT() != 0) {
                long dividend = Math.multiplyExact(accountAsset.getQuantityQNT(), amountMQTPerQNT);
                Account.getAccount(accountAsset.getAccountId())
                        .addToBalanceAndUnconfirmedBalanceMQT(LedgerEvent.ASSET_DIVIDEND_PAYMENT, transactionId, dividend);
                totalDividend += dividend;
                numAccounts += 1;
            }
        }
        this.addToBalanceMQT(LedgerEvent.ASSET_DIVIDEND_PAYMENT, transactionId, -totalDividend);
        AssetDividend.addAssetDividend(transactionId, attachment, totalDividend, numAccounts);
    }

    @Override
    public String toString() {
        return "Account " + Long.toUnsignedString(getId());
    }


    public static class FullId extends Pair<Long, Integer> {
        public static final int BYTES_SIZE = 12;
        private static final BigInteger TWO64 = BigInteger.valueOf((long)Math.pow(2,32)).multiply(BigInteger.valueOf((long)Math.pow(2,32)));
        private final long id1;
        private final int id2;

        public FullId(long id1, int id2) {
            this.id1 = id1;
            this.id2 = id2;
        }

        public FullId(BigInteger bigId) {
            this.id2 = bigId.divide(TWO64).intValue();
            this.id1 = bigId.subtract(bigId.divide(TWO64).multiply(TWO64)).longValue();
        }

        public static FullId fromPublicKey(byte[] publicKey) {
            byte[] publicKeyHash = Crypto.sha256().digest(publicKey);
            return FullId.fromFullHash(publicKeyHash);
        }

        public static FullId fromSecretPhrase(String secretPhrase) {
            return FullId.fromPublicKey(Crypto.getPublicKey(secretPhrase));
        }

        public static FullId fromFullHash(byte[] hash) {
            if (hash == null || hash.length < 12) {
                throw new IllegalArgumentException("Invalid hash: " + Arrays.toString(hash));
            }
            return new FullId(new BigInteger(1, new byte[]{hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0]}).longValue(),
                    new BigInteger(1, new byte[]{hash[11], hash[10], hash[9], hash[8]}).intValue());
        }

        public void putMyBytes(ByteBuffer buffer) {
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putLong(id1);
            buffer.putInt(id2);
        }

        public byte[] getBytes() {
            ByteBuffer buffer = ByteBuffer.allocate(BYTES_SIZE);
            putMyBytes(buffer);
            return buffer.array();
        }

        public static FullId fromBytes(byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            long id1 = buffer.getLong();
            int id2 = buffer.getInt();
            return new FullId(id1, id2);
        }

        public static FullId fromStrId(String account) {
            if (account == null || (account = account.trim()).isEmpty()) {
                return null;
            }
            account = account.toUpperCase(Locale.ROOT);
            int prefixEnd = account.indexOf('-');
            if (prefixEnd > 0) {
                return Crypto.rsDecode(account.substring(prefixEnd + 1));
            } else if (prefixEnd == 0) {
                //TODO there was convertion for signed id. Should we have this case?
                return new FullId(new BigInteger(account));
            } else {
                return new FullId(new BigInteger(account));
            }
        }

        public BigInteger toNumber() {
            BigInteger big = TWO64.multiply(new BigInteger(Integer.toUnsignedString(getRight())));
            return big.add(new BigInteger(Long.toUnsignedString(getLeft())));
        }

        public String toString() {
            return toNumber().toString();
        }

        public String toRS() {
            return Constants.ACCOUNT_PREFIX + "-" + Crypto.rsEncode(this);
        }

        @Override
        public Long getLeft() {
            return id1;
        }

        @Override
        public Integer getRight() {
            return id2;
        }

        @Override
        public Integer setValue(Integer value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean equals(Object obj) {
            if (! (obj instanceof FullId)) {
                return false;
            }
            FullId id = (FullId) obj;
            return (this.id1 == id.id1) && (this.id2 == id.id2);
        }
    }
}
