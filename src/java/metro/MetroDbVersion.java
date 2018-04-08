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

import metro.db.DbVersion;

class MetroDbVersion extends DbVersion {

    protected void update(int nextUpdate) {
        switch (nextUpdate) {
            case 1:
                // TODO #144 block_signature BINARY(64) NOT NULL
                // alter table block alter column block_signature drop not null (if you have old DB) - in order to test key blocks
                apply("CREATE TABLE IF NOT EXISTS block (db_id IDENTITY, id BIGINT NOT NULL, version SMALLINT NOT NULL, "
                        + "timestamp BIGINT NOT NULL, previous_block_id BIGINT, previous_key_block_id BIGINT, "
                        + "total_amount BIGINT NOT NULL, "
                        + "total_fee BIGINT NOT NULL, payload_length INT, "
                        + "previous_block_hash BINARY(32), previous_key_block_hash BINARY(32), "
                        + "cumulative_difficulty VARBINARY NOT NULL, stake_batch_difficulty VARBINARY NOT NULL, base_target BIGINT NOT NULL, "
                        + "next_block_id BIGINT, "
                        + "nonce BIGINT, forgers_merkle_root BINARY(32), tx_merkle_root BINARY(32), "
                        + "height INT NOT NULL, local_height INT NOT NULL, generation_sequence BINARY(32) NOT NULL, "
                        + "block_signature BINARY(64), payload_hash BINARY(32), generator_id BIGINT NOT NULL)");
            case 2:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS block_id_idx ON block (id)");
            case 3:
                apply("CREATE TABLE IF NOT EXISTS transaction (db_id IDENTITY, id BIGINT NOT NULL, "
                        + "deadline SMALLINT NOT NULL, recipient_id BIGINT, transaction_index SMALLINT NOT NULL, "
                        + "amount BIGINT NOT NULL, fee BIGINT NOT NULL, full_hash BINARY(32) NOT NULL, "
                        + "height INT NOT NULL, block_id BIGINT NOT NULL, FOREIGN KEY (block_id) REFERENCES block (id) ON DELETE CASCADE, "
                        + "signature BINARY(64) NOT NULL, timestamp BIGINT NOT NULL, type TINYINT NOT NULL, subtype TINYINT NOT NULL, "
                        + "sender_id BIGINT NOT NULL, block_timestamp BIGINT NOT NULL, referenced_transaction_full_hash BINARY(32), "
                        + "phased BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "attachment_bytes VARBINARY, version TINYINT NOT NULL, has_message BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "has_encrypted_message BOOLEAN NOT NULL DEFAULT FALSE, has_public_key_announcement BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "ec_block_height INT DEFAULT NULL, ec_block_id BIGINT DEFAULT NULL, has_encrypttoself_message BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "has_prunable_message BOOLEAN NOT NULL DEFAULT FALSE, has_prunable_encrypted_message BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "has_prunable_attachment BOOLEAN NOT NULL DEFAULT FALSE)");
            case 4:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS transaction_id_idx ON transaction (id)");
            case 5:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS block_height_idx ON block (height)");
            case 6:
                apply("CREATE INDEX IF NOT EXISTS block_generator_id_idx ON block (generator_id)");
            case 7:
                apply("CREATE INDEX IF NOT EXISTS transaction_sender_id_idx ON transaction (sender_id)");
            case 8:
                apply("CREATE INDEX IF NOT EXISTS transaction_recipient_id_idx ON transaction (recipient_id)");
            case 9:
                apply("CREATE TABLE IF NOT EXISTS peer (address VARCHAR PRIMARY KEY, last_updated BIGINT, services BIGINT)");
            case 10:
                apply("CREATE INDEX IF NOT EXISTS transaction_block_timestamp_idx ON transaction (block_timestamp DESC)");
            case 11:
                apply("CREATE TABLE IF NOT EXISTS alias (db_id IDENTITY, id BIGINT NOT NULL, "
                        + "account_id BIGINT NOT NULL, alias_name VARCHAR NOT NULL, "
                        + "alias_name_lower VARCHAR AS LOWER (alias_name) NOT NULL, "
                        + "alias_uri VARCHAR NOT NULL, timestamp BIGINT NOT NULL, "
                        + "height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 12:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS alias_id_height_idx ON alias (id, height DESC)");
            case 13:
                apply("CREATE INDEX IF NOT EXISTS alias_account_id_idx ON alias (account_id, height DESC)");
            case 14:
                apply("CREATE INDEX IF NOT EXISTS alias_name_lower_idx ON alias (alias_name_lower)");
            case 15:
                apply("CREATE TABLE IF NOT EXISTS alias_offer (db_id IDENTITY, id BIGINT NOT NULL, "
                        + "price BIGINT NOT NULL, buyer_id BIGINT, "
                        + "height INT NOT NULL, latest BOOLEAN DEFAULT TRUE NOT NULL)");
            case 16:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS alias_offer_id_height_idx ON alias_offer (id, height DESC)");
            case 17:
                apply("CREATE TABLE IF NOT EXISTS asset (db_id IDENTITY, id BIGINT NOT NULL, account_id BIGINT NOT NULL, "
                        + "name VARCHAR NOT NULL, description VARCHAR, quantity BIGINT NOT NULL, decimals TINYINT NOT NULL, "
                        + "initial_quantity BIGINT NOT NULL, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 18:
                apply("CREATE INDEX IF NOT EXISTS asset_account_id_idx ON asset (account_id)");
            case 19:
                apply("CREATE TABLE IF NOT EXISTS trade (db_id IDENTITY, asset_id BIGINT NOT NULL, block_id BIGINT NOT NULL, "
                        + "ask_order_id BIGINT NOT NULL, bid_order_id BIGINT NOT NULL, ask_order_height INT NOT NULL, "
                        + "bid_order_height INT NOT NULL, seller_id BIGINT NOT NULL, buyer_id BIGINT NOT NULL, "
                        + "is_buy BOOLEAN NOT NULL, "
                        + "quantity BIGINT NOT NULL, price BIGINT NOT NULL, timestamp BIGINT NOT NULL, height INT NOT NULL)");
            case 20:
                apply("CREATE INDEX IF NOT EXISTS trade_asset_id_idx ON trade (asset_id, height DESC)");
            case 21:
                apply("CREATE INDEX IF NOT EXISTS trade_seller_id_idx ON trade (seller_id, height DESC)");
            case 22:
                apply("CREATE INDEX IF NOT EXISTS trade_buyer_id_idx ON trade (buyer_id, height DESC)");
            case 23:
                apply("CREATE TABLE IF NOT EXISTS ask_order (db_id IDENTITY, id BIGINT NOT NULL, account_id BIGINT NOT NULL, "
                        + "asset_id BIGINT NOT NULL, price BIGINT NOT NULL, transaction_index SMALLINT NOT NULL, "
                        + "transaction_height INT NOT NULL, "
                        + "quantity BIGINT NOT NULL, creation_height INT NOT NULL, height INT NOT NULL, "
                        + "latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 24:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS ask_order_id_height_idx ON ask_order (id, height DESC)");
            case 25:
                apply("CREATE INDEX IF NOT EXISTS ask_order_account_id_idx ON ask_order (account_id, height DESC)");
            case 26:
                apply("CREATE INDEX IF NOT EXISTS ask_order_asset_id_price_idx ON ask_order (asset_id, price)");
            case 27:
                apply("CREATE TABLE IF NOT EXISTS bid_order (db_id IDENTITY, id BIGINT NOT NULL, account_id BIGINT NOT NULL, "
                        + "asset_id BIGINT NOT NULL, price BIGINT NOT NULL, transaction_index SMALLINT NOT NULL, "
                        + "transaction_height INT NOT NULL, "
                        + "quantity BIGINT NOT NULL, creation_height INT NOT NULL, height INT NOT NULL, "
                        + "latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 28:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS bid_order_id_height_idx ON bid_order (id, height DESC)");
            case 29:
                apply("CREATE INDEX IF NOT EXISTS bid_order_account_id_idx ON bid_order (account_id, height DESC)");
            case 30:
                apply("CREATE INDEX IF NOT EXISTS bid_order_asset_id_price_idx ON bid_order (asset_id, price DESC)");
            case 31:
                apply("CREATE TABLE IF NOT EXISTS account (db_id IDENTITY, id BIGINT NOT NULL, "
                        + "balance BIGINT NOT NULL, unconfirmed_balance BIGINT NOT NULL, has_control_phasing BOOLEAN NOT NULL DEFAULT FALSE, "
                        + "forged_balance BIGINT NOT NULL, active_lessee_id BIGINT, height INT NOT NULL, "
                        + "latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 32:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_id_height_idx ON account (id, height DESC)");
            case 33:
                apply("CREATE TABLE IF NOT EXISTS account_asset (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "asset_id BIGINT NOT NULL, quantity BIGINT NOT NULL, unconfirmed_quantity BIGINT NOT NULL, height INT NOT NULL, "
                        + "latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 34:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_asset_id_height_idx ON account_asset (account_id, asset_id, height DESC)");
            case 35:
                apply("CREATE TABLE IF NOT EXISTS account_guaranteed_balance (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "additions BIGINT NOT NULL, height INT NOT NULL)");
            case 36:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_guaranteed_balance_id_height_idx ON account_guaranteed_balance "
                        + "(account_id, height DESC)");
            case 37:
                apply("CREATE TABLE IF NOT EXISTS unconfirmed_transaction (db_id IDENTITY, id BIGINT NOT NULL, expiration BIGINT NOT NULL, "
                        + "transaction_height INT NOT NULL, fee_per_byte BIGINT NOT NULL, arrival_timestamp BIGINT NOT NULL, "
                        + "transaction_bytes VARBINARY NOT NULL, prunable_json VARCHAR, height INT NOT NULL)");
            case 38:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS unconfirmed_transaction_id_idx ON unconfirmed_transaction (id)");
            case 39:
                apply("CREATE TABLE IF NOT EXISTS asset_transfer (db_id IDENTITY, id BIGINT NOT NULL, asset_id BIGINT NOT NULL, "
                        + "sender_id BIGINT NOT NULL, recipient_id BIGINT NOT NULL, quantity BIGINT NOT NULL, timestamp BIGINT NOT NULL, "
                        + "height INT NOT NULL)");
            case 40:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS asset_transfer_id_idx ON asset_transfer (id)");
            case 41:
                apply("CREATE INDEX IF NOT EXISTS asset_transfer_asset_id_idx ON asset_transfer (asset_id, height DESC)");
            case 42:
                apply("CREATE INDEX IF NOT EXISTS asset_transfer_sender_id_idx ON asset_transfer (sender_id, height DESC)");
            case 43:
                apply("CREATE INDEX IF NOT EXISTS asset_transfer_recipient_id_idx ON asset_transfer (recipient_id, height DESC)");
            case 44:
                apply("CREATE INDEX IF NOT EXISTS account_asset_quantity_idx ON account_asset (quantity DESC)");
            case 45:
                apply("CREATE INDEX IF NOT EXISTS ask_order_creation_idx ON ask_order (creation_height DESC)");
            case 46:
                apply("CREATE INDEX IF NOT EXISTS bid_order_creation_idx ON bid_order (creation_height DESC)");
            case 47:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS block_timestamp_idx ON block (timestamp DESC)");
            case 48:
                apply("CREATE INDEX IF NOT EXISTS unconfirmed_transaction_height_fee_timestamp_idx ON unconfirmed_transaction "
                        + "(transaction_height ASC, fee_per_byte DESC, arrival_timestamp ASC)");
            case 49:
                apply("CREATE TABLE IF NOT EXISTS scan (rescan BOOLEAN NOT NULL DEFAULT FALSE, height INT NOT NULL DEFAULT 0, "
                        + "validate BOOLEAN NOT NULL DEFAULT FALSE)");
            case 50:
                apply("INSERT INTO scan (rescan, height, validate) VALUES (false, 0, false)");
            case 51:
                apply("CREATE TABLE IF NOT EXISTS public_key (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "public_key BINARY(32), height INT NOT NULL, FOREIGN KEY (height) REFERENCES block (height) ON DELETE CASCADE, "
                        + "latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 52:
                apply("CREATE INDEX IF NOT EXISTS account_guaranteed_balance_height_idx ON account_guaranteed_balance(height)");
            case 53:
                apply("CREATE INDEX IF NOT EXISTS asset_transfer_height_idx ON asset_transfer(height)");
            case 54:
                apply("CREATE INDEX IF NOT EXISTS trade_height_idx ON trade(height)");
            case 55:
                apply("CREATE TABLE IF NOT EXISTS vote (db_id IDENTITY, id BIGINT NOT NULL, " +
                        "poll_id BIGINT NOT NULL, voter_id BIGINT NOT NULL, vote_bytes VARBINARY NOT NULL, height INT NOT NULL)");
            case 56:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS vote_id_idx ON vote (id)");
            case 57:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS vote_poll_id_idx ON vote (poll_id, voter_id)");
            case 58:
                apply("CREATE TABLE IF NOT EXISTS poll (db_id IDENTITY, id BIGINT NOT NULL, "
                        + "account_id BIGINT NOT NULL, name VARCHAR NOT NULL, "
                        + "description VARCHAR, options ARRAY NOT NULL, min_num_options TINYINT, max_num_options TINYINT, "
                        + "min_range_value TINYINT, max_range_value TINYINT, timestamp BIGINT NOT NULL, "
                        + "finish_height INT NOT NULL, voting_model TINYINT NOT NULL, min_balance BIGINT, "
                        + "min_balance_model TINYINT, holding_id BIGINT, height INT NOT NULL)");
            case 59:
                apply("CREATE TABLE IF NOT EXISTS poll_result (db_id IDENTITY, poll_id BIGINT NOT NULL, "
                        + "result BIGINT, weight BIGINT NOT NULL, height INT NOT NULL)");
            case 60:
                apply("CREATE TABLE IF NOT EXISTS phasing_poll (db_id IDENTITY, id BIGINT NOT NULL, "
                        + "account_id BIGINT NOT NULL, whitelist_size TINYINT NOT NULL DEFAULT 0, "
                        + "finish_height INT NOT NULL, voting_model TINYINT NOT NULL, quorum BIGINT, "
                        + "min_balance BIGINT, holding_id BIGINT, min_balance_model TINYINT, "
                        + "hashed_secret VARBINARY, algorithm TINYINT, height INT NOT NULL)");
            case 61:
                apply("CREATE TABLE IF NOT EXISTS phasing_vote (db_id IDENTITY, vote_id BIGINT NOT NULL, "
                        + "transaction_id BIGINT NOT NULL, voter_id BIGINT NOT NULL, "
                        + "height INT NOT NULL)");
            case 62:
                apply("CREATE TABLE IF NOT EXISTS phasing_poll_voter (db_id IDENTITY, "
                        + "transaction_id BIGINT NOT NULL, voter_id BIGINT NOT NULL, "
                        + "height INT NOT NULL)");
            case 63:
                apply("CREATE INDEX IF NOT EXISTS vote_height_idx ON vote(height)");
            case 64:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS poll_id_idx ON poll(id)");
            case 65:
                apply("CREATE INDEX IF NOT EXISTS poll_height_idx ON poll(height)");
            case 66:
                apply("CREATE INDEX IF NOT EXISTS poll_account_idx ON poll(account_id)");
            case 67:
                apply("CREATE INDEX IF NOT EXISTS poll_finish_height_idx ON poll(finish_height DESC)");
            case 68:
                apply("CREATE INDEX IF NOT EXISTS poll_result_poll_id_idx ON poll_result(poll_id)");
            case 69:
                apply("CREATE INDEX IF NOT EXISTS poll_result_height_idx ON poll_result(height)");
            case 70:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS phasing_poll_id_idx ON phasing_poll(id)");
            case 71:
                apply("CREATE INDEX IF NOT EXISTS phasing_poll_height_idx ON phasing_poll(height)");
            case 72:
                apply("CREATE INDEX IF NOT EXISTS phasing_poll_account_id_idx ON phasing_poll(account_id, height DESC)");
            case 73:
                apply("CREATE INDEX IF NOT EXISTS phasing_poll_holding_id_idx ON phasing_poll(holding_id, height DESC)");
            case 74:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS phasing_vote_transaction_voter_idx ON phasing_vote(transaction_id, voter_id)");
            case 75:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS phasing_poll_voter_transaction_voter_idx ON phasing_poll_voter(transaction_id, voter_id)");
            case 76:
                apply("CREATE TABLE IF NOT EXISTS phasing_poll_result (db_id IDENTITY, id BIGINT NOT NULL, "
                        + "result BIGINT NOT NULL, approved BOOLEAN NOT NULL, height INT NOT NULL)");
            case 77:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS phasing_poll_result_id_idx ON phasing_poll_result(id)");
            case 78:
                apply("CREATE INDEX IF NOT EXISTS phasing_poll_result_height_idx ON phasing_poll_result(height)");
            case 79:
                apply("CREATE INDEX IF NOT EXISTS phasing_poll_voter_height_idx ON phasing_poll_voter(height)");
            case 80:
                apply("CREATE INDEX IF NOT EXISTS phasing_vote_height_idx ON phasing_vote(height)");
            case 81:
                apply("CREATE INDEX IF NOT EXISTS trade_ask_idx ON trade (ask_order_id, height DESC)");
            case 82:
                apply("CREATE INDEX IF NOT EXISTS trade_bid_idx ON trade (bid_order_id, height DESC)");
            case 83:
                apply("CREATE TABLE IF NOT EXISTS account_info (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "name VARCHAR, description VARCHAR, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 84:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_info_id_height_idx ON account_info (account_id, height DESC)");
            case 85:
                apply("CREATE TABLE IF NOT EXISTS prunable_message (db_id IDENTITY, id BIGINT NOT NULL, sender_id BIGINT NOT NULL, "
                        + "recipient_id BIGINT, message VARBINARY, message_is_text BOOLEAN NOT NULL, is_compressed BOOLEAN NOT NULL, "
                        + "encrypted_message VARBINARY, encrypted_is_text BOOLEAN DEFAULT FALSE, "
                        + "block_timestamp BIGINT NOT NULL, transaction_timestamp BIGINT NOT NULL, height INT NOT NULL, "
                        + "FOREIGN KEY (height) REFERENCES block (height) ON DELETE CASCADE)");
            case 86:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS prunable_message_id_idx ON prunable_message (id)");
            case 87:
                apply("CREATE INDEX IF NOT EXISTS prunable_message_transaction_timestamp_idx ON prunable_message (transaction_timestamp DESC)");
            case 88:
                apply("CREATE INDEX IF NOT EXISTS prunable_message_sender_idx ON prunable_message (sender_id)");
            case 89:
                apply("CREATE INDEX IF NOT EXISTS prunable_message_recipient_idx ON prunable_message (recipient_id)");
            case 90:
                apply("CREATE INDEX IF NOT EXISTS prunable_message_block_timestamp_dbid_idx ON prunable_message (block_timestamp DESC, db_id DESC)");
            case 91:
                apply("CREATE INDEX IF NOT EXISTS account_active_lessee_id_idx ON account (active_lessee_id)");
            case 92:
                apply("CREATE TABLE IF NOT EXISTS account_lease (db_id IDENTITY, lessor_id BIGINT NOT NULL, "
                        + "current_leasing_height_from INT, current_leasing_height_to INT, current_lessee_id BIGINT, "
                        + "next_leasing_height_from INT, next_leasing_height_to INT, next_lessee_id BIGINT, "
                        + "height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 93:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_lease_lessor_id_height_idx ON account_lease (lessor_id, height DESC)");
            case 94:
                apply("CREATE INDEX IF NOT EXISTS account_lease_current_leasing_height_from_idx ON account_lease (current_leasing_height_from)");
            case 95:
                apply("CREATE INDEX IF NOT EXISTS account_lease_current_leasing_height_to_idx ON account_lease (current_leasing_height_to)");
            case 96:
                apply("CREATE INDEX IF NOT EXISTS account_lease_height_id_idx ON account_lease (height, lessor_id)");
            case 97:
                apply("CREATE INDEX IF NOT EXISTS account_asset_asset_id_idx ON account_asset (asset_id)");
            case 98:
                apply("CREATE INDEX IF NOT EXISTS unconfirmed_transaction_expiration_idx ON unconfirmed_transaction (expiration DESC)");
            case 99:
                apply("CREATE INDEX IF NOT EXISTS account_height_id_idx ON account (height, id)");
            case 100:
                apply("CREATE INDEX IF NOT EXISTS account_asset_height_id_idx ON account_asset (height, account_id, asset_id)");
            case 101:
                apply("CREATE INDEX IF NOT EXISTS alias_height_id_idx ON alias (height, id)");
            case 102:
                apply("CREATE INDEX IF NOT EXISTS alias_offer_height_id_idx ON alias_offer (height, id)");
            case 103:
                apply("CREATE INDEX IF NOT EXISTS ask_order_height_id_idx ON ask_order (height, id)");
            case 104:
                apply("CREATE INDEX IF NOT EXISTS bid_order_height_id_idx ON bid_order (height, id)");
            case 105:
                apply("CREATE INDEX IF NOT EXISTS account_info_height_id_idx ON account_info (height, account_id)");
            case 106:
                apply("CREATE INDEX IF NOT EXISTS trade_height_db_id_idx ON trade (height DESC, db_id DESC)");
            case 107:
                apply("CREATE TABLE IF NOT EXISTS account_ledger (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "event_type TINYINT NOT NULL, event_id BIGINT NOT NULL, holding_type TINYINT NOT NULL, "
                        + "holding_id BIGINT, change BIGINT NOT NULL, balance BIGINT NOT NULL, "
                        + "block_id BIGINT NOT NULL, height INT NOT NULL, timestamp BIGINT NOT NULL)");
            case 108:
                apply("CREATE INDEX IF NOT EXISTS account_ledger_id_idx ON account_ledger(account_id, db_id)");
            case 109:
                apply("CREATE INDEX IF NOT EXISTS account_ledger_height_idx ON account_ledger(height)");
            case 110:
                metro.db.FullTextTrigger.init();
                apply(null);
            case 111:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS asset_id_height_idx ON asset (id, height DESC)");
            case 112:
                apply("CREATE INDEX IF NOT EXISTS asset_height_id_idx ON asset (height, id)");
            case 113:
                apply("CREATE TABLE IF NOT EXISTS shuffling (db_id IDENTITY, id BIGINT NOT NULL, holding_id BIGINT NULL, holding_type TINYINT NOT NULL, "
                        + "issuer_id BIGINT NOT NULL, amount BIGINT NOT NULL, participant_count TINYINT NOT NULL, blocks_remaining SMALLINT NULL, "
                        + "stage TINYINT NOT NULL, assignee_account_id BIGINT NULL, registrant_count TINYINT NOT NULL, "
                        + "recipient_public_keys ARRAY, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 114:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS shuffling_id_height_idx ON shuffling (id, height DESC)");
            case 115:
                apply("CREATE INDEX IF NOT EXISTS shuffling_holding_id_height_idx ON shuffling (holding_id, height DESC)");
            case 116:
                apply("CREATE INDEX IF NOT EXISTS shuffling_assignee_account_id_height_idx ON shuffling (assignee_account_id, height DESC)");
            case 117:
                apply("CREATE INDEX IF NOT EXISTS shuffling_height_id_idx ON shuffling (height, id)");
            case 118:
                apply("CREATE TABLE IF NOT EXISTS shuffling_participant (db_id IDENTITY, shuffling_id BIGINT NOT NULL, "
                        + "account_id BIGINT NOT NULL, next_account_id BIGINT NULL, participant_index TINYINT NOT NULL, "
                        + "state TINYINT NOT NULL, blame_data ARRAY, key_seeds ARRAY, data_transaction_full_hash BINARY(32), "
                        + "height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 119:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS shuffling_participant_shuffling_id_account_id_idx ON shuffling_participant "
                        + "(shuffling_id, account_id, height DESC)");
            case 120:
                apply("CREATE INDEX IF NOT EXISTS shuffling_participant_height_idx ON shuffling_participant (height, shuffling_id, account_id)");
            case 121:
                apply("CREATE TABLE IF NOT EXISTS shuffling_data (db_id IDENTITY, shuffling_id BIGINT NOT NULL, account_id BIGINT NOT NULL, "
                        + "data ARRAY, transaction_timestamp BIGINT NOT NULL, height INT NOT NULL, "
                        + "FOREIGN KEY (height) REFERENCES block (height) ON DELETE CASCADE)");
            case 122:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS shuffling_data_id_height_idx ON shuffling_data (shuffling_id, height DESC)");
            case 123:
                apply("CREATE INDEX shuffling_data_transaction_timestamp_idx ON shuffling_data (transaction_timestamp DESC)");
            case 124:
                apply("CREATE TABLE IF NOT EXISTS phasing_poll_linked_transaction (db_id IDENTITY, "
                        + "transaction_id BIGINT NOT NULL, linked_full_hash BINARY(32) NOT NULL, linked_transaction_id BIGINT NOT NULL, "
                        + "height INT NOT NULL)");
            case 125:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS phasing_poll_linked_transaction_id_link_idx "
                        + "ON phasing_poll_linked_transaction (transaction_id, linked_transaction_id)");
            case 126:
                apply("CREATE INDEX IF NOT EXISTS phasing_poll_linked_transaction_height_idx ON phasing_poll_linked_transaction (height)");
            case 127:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS phasing_poll_linked_transaction_link_id_idx "
                        + "ON phasing_poll_linked_transaction (linked_transaction_id, transaction_id)");
            case 128:
                apply("CREATE TABLE IF NOT EXISTS account_control_phasing (db_id IDENTITY, account_id BIGINT NOT NULL, "
                        + "whitelist ARRAY, voting_model TINYINT NOT NULL, quorum BIGINT, min_balance BIGINT, "
                        + "holding_id BIGINT, min_balance_model TINYINT, max_fees BIGINT, min_duration SMALLINT, max_duration SMALLINT, "
                        + "height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 129:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_control_phasing_id_height_idx ON account_control_phasing (account_id, height DESC)");
            case 130:
                apply("CREATE INDEX IF NOT EXISTS account_control_phasing_height_id_idx ON account_control_phasing (height, account_id)");
            case 131:
                apply("CREATE TABLE IF NOT EXISTS account_property (db_id IDENTITY, id BIGINT NOT NULL, recipient_id BIGINT NOT NULL, setter_id BIGINT, "
                        + "property VARCHAR NOT NULL, value VARCHAR, height INT NOT NULL, latest BOOLEAN NOT NULL DEFAULT TRUE)");
            case 132:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS account_property_id_height_idx ON account_property (id, height DESC)");
            case 133:
                apply("CREATE INDEX IF NOT EXISTS account_property_height_id_idx ON account_property (height, id)");
            case 134:
                apply("CREATE INDEX IF NOT EXISTS account_property_recipient_height_idx ON account_property (recipient_id, height DESC)");
            case 135:
                apply("CREATE INDEX IF NOT EXISTS account_property_setter_recipient_idx ON account_property (setter_id, recipient_id)");
            case 136:
                apply("CREATE TABLE IF NOT EXISTS asset_delete (db_id IDENTITY, id BIGINT NOT NULL, asset_id BIGINT NOT NULL, "
                        + "account_id BIGINT NOT NULL, quantity BIGINT NOT NULL, timestamp BIGINT NOT NULL, height INT NOT NULL)");
            case 137:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS asset_delete_id_idx ON asset_delete (id)");
            case 138:
                apply("CREATE INDEX IF NOT EXISTS asset_delete_asset_id_idx ON asset_delete (asset_id, height DESC)");
            case 139:
                apply("CREATE INDEX IF NOT EXISTS asset_delete_account_id_idx ON asset_delete (account_id, height DESC)");
            case 140:
                apply("CREATE INDEX IF NOT EXISTS asset_delete_height_idx ON asset_delete (height)");
            case 141:
                apply("CREATE TABLE IF NOT EXISTS referenced_transaction (db_id IDENTITY, transaction_id BIGINT NOT NULL, "
                        + "FOREIGN KEY (transaction_id) REFERENCES transaction (id) ON DELETE CASCADE, "
                        + "referenced_transaction_id BIGINT NOT NULL)");
            case 142:
                apply("CREATE INDEX IF NOT EXISTS referenced_transaction_referenced_transaction_id_idx ON referenced_transaction (referenced_transaction_id)");
            case 143:
                apply("CREATE INDEX IF NOT EXISTS shuffling_blocks_remaining_height_idx ON shuffling (blocks_remaining, height DESC)");
            case 144:
                apply("CREATE TABLE IF NOT EXISTS asset_dividend (db_id IDENTITY, id BIGINT NOT NULL, asset_id BIGINT NOT NULL, "
                        + "amount BIGINT NOT NULL, dividend_height INT NOT NULL, total_dividend BIGINT NOT NULL, "
                        + "num_accounts BIGINT NOT NULL, timestamp BIGINT NOT NULL, height INT NOT NULL)");
            case 145:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS asset_dividend_id_idx ON asset_dividend (id)");
            case 146:
                apply("CREATE INDEX IF NOT EXISTS asset_dividend_asset_id_idx ON asset_dividend (asset_id, height DESC)");
            case 147:
                apply("CREATE INDEX IF NOT EXISTS asset_dividend_height_idx ON asset_dividend (height)");
            case 148:
                apply("CREATE UNIQUE INDEX IF NOT EXISTS public_key_account_id_height_idx ON public_key (account_id, height DESC)");
            case 149:
                apply("CREATE INDEX IF NOT EXISTS block_local_height_idx ON block (local_height)");
            case 150:
                return;
            default:
                throw new RuntimeException("Blockchain database inconsistent with code, at update " + nextUpdate
                        + ", probably trying to run older code on newer database");
        }
    }
}
