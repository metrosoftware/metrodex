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

import metro.crypto.Crypto;
import metro.db.DbIterator;
import metro.util.Convert;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class Tester {
    private final String secretPhrase;
    private final byte[] privateKey;
    private final byte[] publicKey;
    private final String publicKeyStr;
    private final long id;
    private final String strId;
    private final String rsAccount;
    private final long initialBalance;
    private final long initialUnconfirmedBalance;
    private final long initialEffectiveBalance;
    private final Map<Long, Long> initialAssetQuantity = new HashMap<>();
    private final Map<Long, Long> initialUnconfirmedAssetQuantity = new HashMap<>();

    public Tester(String secretPhrase) {
        this.secretPhrase = secretPhrase;
        this.privateKey = Crypto.getPrivateKey(secretPhrase);
        this.publicKey = Crypto.getPublicKey(secretPhrase);
        this.publicKeyStr = Convert.toHexString(publicKey);
        this.id = Account.getId(publicKey);
        this.strId = Long.toUnsignedString(id);
        this.rsAccount = Convert.rsAccount(id);
        Account account = Account.getAccount(publicKey);
        if (account != null) {
            this.initialBalance = account.getBalanceMQT();
            this.initialUnconfirmedBalance = account.getUnconfirmedBalanceMQT();
            this.initialEffectiveBalance = account.getEffectiveBalanceMTR();
            DbIterator<Account.AccountAsset> assets = account.getAssets(0, -1);
            for (Account.AccountAsset accountAsset : assets) {
                initialAssetQuantity.put(accountAsset.getAssetId(), accountAsset.getQuantityQNT());
                initialUnconfirmedAssetQuantity.put(accountAsset.getAssetId(), accountAsset.getUnconfirmedQuantityQNT());
            }
        } else {
            initialBalance = 0;
            initialUnconfirmedBalance = 0;
            initialEffectiveBalance = 0;
        }
    }

    public String getSecretPhrase() {
        return secretPhrase;
    }

    public byte[] getPrivateKey() {
        return privateKey;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public String getPublicKeyStr() {
        return publicKeyStr;
    }

    public Account getAccount() {
        return Account.getAccount(publicKey);
    }

    public long getId() {
        return id;
    }

    public String getStrId() {
        return strId;
    }

    public String getRsAccount() {
        return rsAccount;
    }

    public long getBalanceDiff() {
        return Account.getAccount(id).getBalanceMQT() - initialBalance;
    }

    public long getUnconfirmedBalanceDiff() {
        return Account.getAccount(id).getUnconfirmedBalanceMQT() - initialUnconfirmedBalance;
    }

    public long getInitialBalance() {
        return initialBalance;
    }

    public long getBalance() {
        return getAccount().getBalanceMQT();
    }

    public long getAssetQuantityDiff(long assetId) {
        return Account.getAccount(id).getAssetBalanceQNT(assetId) - getInitialAssetQuantity(assetId);
    }

    public long getUnconfirmedAssetQuantityDiff(long assetId) {
        return Account.getAccount(id).getUnconfirmedAssetBalanceQNT(assetId) - getInitialAssetQuantity(assetId);
    }

    public long getInitialUnconfirmedBalance() {
        return initialUnconfirmedBalance;
    }

    public long getInitialEffectiveBalance() {
        return initialEffectiveBalance;
    }

    public long getInitialAssetQuantity(long assetId) {
        return Convert.nullToZero(initialAssetQuantity.get(assetId));
    }

    public long getInitialUnconfirmedAssetQuantity(long assetId) {
        return Convert.nullToZero(initialUnconfirmedAssetQuantity.get(assetId));
    }

}