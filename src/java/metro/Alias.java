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

import metro.db.DbClause;
import metro.db.DbIterator;
import metro.db.DbKey;
import metro.db.DbUtils;
import metro.db.VersionedEntityDbTable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class Alias {

    public static class Offer {

        private long priceMQT;
        private long buyerId;
        private final long aliasId;
        private final DbKey dbKey;

        private Offer(long aliasId, long priceMQT, long buyerId) {
            this.priceMQT = priceMQT;
            this.buyerId = buyerId;
            this.aliasId = aliasId;
            this.dbKey = offerDbKeyFactory.newKey(this.aliasId);
        }

        private Offer(ResultSet rs, DbKey dbKey) throws SQLException {
            this.aliasId = rs.getLong("id");
            this.dbKey = dbKey;
            this.priceMQT = rs.getLong("price");
            this.buyerId  = rs.getLong("buyer_id");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO alias_offer (id, price, buyer_id, "
                    + "height) KEY (id, height) VALUES (?, ?, ?, ?)")) {
                int i = 0;
                pstmt.setLong(++i, this.aliasId);
                pstmt.setLong(++i, this.priceMQT);
                DbUtils.setLongZeroToNull(pstmt, ++i, this.buyerId);
                pstmt.setInt(++i, Metro.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getId() {
            return aliasId;
        }

        public long getPriceMQT() {
            return priceMQT;
        }

        public long getBuyerId() {
            return buyerId;
        }

    }

    private static final DbKey.LongKeyFactory<Alias> aliasDbKeyFactory = new DbKey.LongKeyFactory<Alias>("id") {

        @Override
        public DbKey newKey(Alias alias) {
            return alias.dbKey;
        }

    };

    private static final VersionedEntityDbTable<Alias> aliasTable = new VersionedEntityDbTable<Alias>("alias", aliasDbKeyFactory) {

        @Override
        protected Alias load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new Alias(rs, dbKey);
        }

        @Override
        protected void save(Connection con, Alias alias) throws SQLException {
            alias.save(con);
        }

        @Override
        protected String defaultSort() {
            return " ORDER BY alias_name_lower ";
        }

    };

    private static final DbKey.LongKeyFactory<Offer> offerDbKeyFactory = new DbKey.LongKeyFactory<Offer>("id") {

        @Override
        public DbKey newKey(Offer offer) {
            return offer.dbKey;
        }

    };

    private static final VersionedEntityDbTable<Offer> offerTable = new VersionedEntityDbTable<Offer>("alias_offer", offerDbKeyFactory) {

        @Override
        protected Offer load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new Offer(rs, dbKey);
        }

        @Override
        protected void save(Connection con, Offer offer) throws SQLException {
            offer.save(con);
        }

    };

    public static int getCount() {
        return aliasTable.getCount();
    }

    public static int getAccountAliasCount(long accountId) {
        return aliasTable.getCount(new DbClause.LongClause("account_id", accountId));
    }

    public static DbIterator<Alias> getAliasesByOwner(long accountId, int from, int to) {
        return aliasTable.getManyBy(new DbClause.LongClause("account_id", accountId), from, to);
    }

    public static Alias getAlias(String aliasName) {
        return aliasTable.getBy(new DbClause.StringClause("alias_name_lower", aliasName.toLowerCase()));
    }

    public static DbIterator<Alias> getAliasesLike(String aliasName, int from, int to) {
        return aliasTable.getManyBy(new DbClause.LikeClause("alias_name_lower", aliasName.toLowerCase()), from, to);
    }

    public static Alias getAlias(long id) {
        return aliasTable.get(aliasDbKeyFactory.newKey(id));
    }

    public static Offer getOffer(Alias alias) {
        return offerTable.getBy(new DbClause.LongClause("id", alias.getId()).and(new DbClause.LongClause("price", DbClause.Op.NE, Long.MAX_VALUE)));
    }

    static void deleteAlias(final String aliasName) {
        final Alias alias = getAlias(aliasName);
        final Offer offer = Alias.getOffer(alias);
        if (offer != null) {
            offer.priceMQT = Long.MAX_VALUE;
            offerTable.delete(offer);
        }
        aliasTable.delete(alias);
    }

    static void addOrUpdateAlias(Transaction transaction, Attachment.MessagingAliasAssignment attachment) {
        Alias alias = getAlias(attachment.getAliasName());
        if (alias == null) {
            alias = new Alias(transaction, attachment);
        } else {
            alias.accountId = transaction.getSenderId();
            alias.aliasURI = attachment.getAliasURI();
            alias.timestamp = Metro.getBlockchain().getLastBlockTimestamp();
        }
        aliasTable.insert(alias);
    }

    static void sellAlias(Transaction transaction, Attachment.MessagingAliasSell attachment) {
        final String aliasName = attachment.getAliasName();
        final long priceMQT = attachment.getPriceMQT();
        final long buyerId = transaction.getRecipientId();
        if (priceMQT > 0) {
            Alias alias = getAlias(aliasName);
            Offer offer = getOffer(alias);
            if (offer == null) {
                offerTable.insert(new Offer(alias.id, priceMQT, buyerId));
            } else {
                offer.priceMQT = priceMQT;
                offer.buyerId = buyerId;
                offerTable.insert(offer);
            }
        } else {
            changeOwner(buyerId, aliasName);
        }

    }

    static void changeOwner(long newOwnerId, String aliasName) {
        Alias alias = getAlias(aliasName);
        alias.accountId = newOwnerId;
        alias.timestamp = Metro.getBlockchain().getLastBlockTimestamp();
        aliasTable.insert(alias);
        Offer offer = getOffer(alias);
        if (offer != null) {
            offer.priceMQT = Long.MAX_VALUE;
            offerTable.delete(offer);
        }
    }

    static void init() {}


    private long accountId;
    private final long id;
    private final DbKey dbKey;
    private final String aliasName;
    private String aliasURI;
    private long timestamp;

    private Alias(Transaction transaction, Attachment.MessagingAliasAssignment attachment) {
        this.id = transaction.getId();
        this.dbKey = aliasDbKeyFactory.newKey(this.id);
        this.accountId = transaction.getSenderId();
        this.aliasName = attachment.getAliasName();
        this.aliasURI = attachment.getAliasURI();
        this.timestamp = Metro.getBlockchain().getLastBlockTimestamp();
    }

    private Alias(ResultSet rs, DbKey dbKey) throws SQLException {
        this.id = rs.getLong("id");
        this.dbKey = dbKey;
        this.accountId = rs.getLong("account_id");
        this.aliasName = rs.getString("alias_name");
        this.aliasURI = rs.getString("alias_uri");
        this.timestamp = rs.getLong("timestamp");
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO alias (id, account_id, alias_name, "
                + "alias_uri, timestamp, height) KEY (id, height) "
                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.accountId);
            pstmt.setString(++i, this.aliasName);
            pstmt.setString(++i, this.aliasURI);
            pstmt.setLong(++i, this.timestamp);
            pstmt.setInt(++i, Metro.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    public long getId() {
        return id;
    }

    public String getAliasName() {
        return aliasName;
    }

    public String getAliasURI() {
        return aliasURI;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getAccountId() {
        return accountId;
    }

}