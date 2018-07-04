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

import metro.crypto.Crypto;
import metro.util.Convert;
import metro.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import static metro.Consensus.HASH_FUNCTION;

public final class Genesis {

    private static final byte[] CREATOR_PUBLIC_KEY;
    public static final Account.FullId CREATOR_ID;
    public static final long EPOCH_BEGINNING;
    public static final byte[] SPECIAL_SIGNATURE;
    public static final String TIME_CAPSULE;
    static {
        try (InputStream is = ClassLoader.getSystemResourceAsStream("data/genesisParameters.json")) {
            JSONObject genesisParameters = (JSONObject)JSONValue.parseWithException(new InputStreamReader(is));
            CREATOR_PUBLIC_KEY = Convert.parseHexString((String)genesisParameters.get("genesisPublicKey"));
            CREATOR_ID = Account.FullId.fromPublicKey(CREATOR_PUBLIC_KEY);
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
            EPOCH_BEGINNING = dateFormat.parse((String) genesisParameters.get("epochBeginning")).getTime();
            ByteBuffer buffer = ByteBuffer.allocate(64);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            JSONObject epochProof = (JSONObject)genesisParameters.get("epochProof");
            buffer.putLong((Long)epochProof.get("nxtBlockId"));
            TIME_CAPSULE = (String)epochProof.get("timeCapsule");
            buffer.put((byte)TIME_CAPSULE.length());
            buffer.put(Convert.encodeTimeCapsuleWithoutLength(TIME_CAPSULE));
            SPECIAL_SIGNATURE = buffer.array();
        } catch (IOException|ParseException|java.text.ParseException e) {
            throw new RuntimeException("Failed to load genesis parameters", e);
        }
    }

    private static JSONObject genesisAccountsJSON = null;

    private static byte[] loadGenesisAccountsJSON() {
        MessageDigest digest = HASH_FUNCTION.messageDigest();
        try (InputStreamReader is = new InputStreamReader(new DigestInputStream(
                ClassLoader.getSystemResourceAsStream("data/genesisAccounts" + (Constants.isTestnet ? "-testnet.json" : ".json")), digest))) {
            genesisAccountsJSON = (JSONObject) JSONValue.parseWithException(is);
        } catch (IOException|ParseException e) {
            throw new RuntimeException("Failed to process genesis recipients accounts", e);
        }
        digest.update((byte)(Constants.isTestnet ? 1 : 0));
        digest.update(Convert.toBytes(EPOCH_BEGINNING));
        return digest.digest();
    }

    static BlockImpl newGenesisBlock() {
        return new BlockImpl(CREATOR_PUBLIC_KEY, loadGenesisAccountsJSON());
    }

    static void apply() {
        if (genesisAccountsJSON == null) {
            loadGenesisAccountsJSON();
        }
        int count = 0;
        Map<Long,Account.FullId> idMap = new HashMap<>();
        JSONArray publicKeys = (JSONArray) genesisAccountsJSON.get("publicKeys");
        Logger.logDebugMessage("Loading public keys");
        for (Object jsonPublicKey : publicKeys) {
            byte[] publicKey = Convert.parseHexString((String)jsonPublicKey);
            Account account = Account.addOrGetAccount(Account.FullId.fromPublicKey(publicKey));
            idMap.put(account.getId(),account.getFullId());
            account.apply(publicKey);
            if (count++ % 100 == 0) {
                Db.db.commitTransaction();
            }
        }
        Logger.logDebugMessage("Loaded " + publicKeys.size() + " public keys");
        count = 0;
        JSONObject balances = (JSONObject) genesisAccountsJSON.get("balances");
        Logger.logDebugMessage("Loading genesis amounts");
        long total = 0;
        for (Map.Entry<String, Long> entry : ((Map<String, Long>)balances).entrySet()) {
            long id = Long.parseUnsignedLong(entry.getKey());
            Account.FullId fullId = idMap.containsKey(id) ? idMap.get(id) : new Account.FullId(id, 0);
            Account account = Account.addOrGetAccount(fullId);
            account.addToBalanceAndUnconfirmedBalanceMQT(null, 0, entry.getValue());
            total += entry.getValue();
            if (count++ % 100 == 0) {
                Db.db.commitTransaction();
            }
        }
        if (total > Constants.MAX_BALANCE_MQT) {
            throw new RuntimeException("Total balance " + total + " exceeds maximum allowed " + Constants.MAX_BALANCE_MQT);
        }
        Logger.logDebugMessage("Total balance %f %s", (double)total / Constants.ONE_MTR, Constants.COIN_SYMBOL);
        Account creatorAccount = Account.addOrGetAccount(Genesis.CREATOR_ID);
        creatorAccount.apply(Genesis.CREATOR_PUBLIC_KEY);
        creatorAccount.addToBalanceAndUnconfirmedBalanceMQT(null, 0, -total);
        genesisAccountsJSON = null;
    }

    private Genesis() {} // never

}
