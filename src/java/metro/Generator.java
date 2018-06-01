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
import metro.util.Listener;
import metro.util.Listeners;
import metro.util.Logger;
import metro.util.ThreadPool;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static metro.Consensus.HASH_FUNCTION;
import static metro.Consensus.MIN_FORKVOTING_AMOUNT_MTR;
import static metro.util.Convert.HASH_SIZE;

public final class Generator implements Comparable<Generator> {

    public enum Event {
        GENERATION_DEADLINE, START_FORGING, STOP_FORGING
    }

    private static final int MAX_FORGERS = Metro.getIntProperty("metro.maxNumberOfForgers");
    private static Set<String> fakeForgingPublicKeys;

    private static final Listeners<Generator,Event> listeners = new Listeners<>();

    private static final ConcurrentMap<String, Generator> generators = new ConcurrentHashMap<>();
    private static final Collection<Generator> allGenerators = Collections.unmodifiableCollection(generators.values());
    private static volatile List<Generator> sortedForgers = null;
    private static long lastBlockId;
    private static int delayTime = Constants.FORGING_DELAY;

    public static Set<String> getFakeForgingPublicKeys() {
        if (fakeForgingPublicKeys == null) {
            fakeForgingPublicKeys = Collections.EMPTY_SET;
            if (Metro.getBooleanProperty("metro.enableFakeForging")) {
                JSONObject fakeForgersJSON = (JSONObject) JSONValue.parse(Metro.getStringProperty("metro.fakeForgingAccounts"));
                JSONArray arrayRs = (JSONArray)fakeForgersJSON.get("rs");
                if (arrayRs != null) {
                    fakeForgingPublicKeys = new HashSet<>(arrayRs.size());
                    Iterator iter = arrayRs.iterator();
                    while (iter.hasNext()) {
                        fakeForgingPublicKeys.add(Convert.toHexString(Account.getPublicKey(Account.FullId.fromStrId(iter.next().toString()).getLeft())));
                    }
                }
            }
        }
        return fakeForgingPublicKeys;
    }

    private static final Runnable generateBlocksThread = new Runnable() {

        private volatile boolean logged;

        @Override
        public void run() {

            try {
                try {
                    BlockchainImpl.getInstance().updateLock();
                    try {
                        Block lastBlock = Metro.getBlockchain().getLastBlock();
                        if (lastBlock == null || lastBlock.getHeight() < Constants.LAST_KNOWN_BLOCK) {
                            return;
                        }
                        final long generationLimit = Metro.getEpochTime() - delayTime;
                        if (lastBlock.getId() != lastBlockId || sortedForgers == null) {
                            lastBlockId = lastBlock.getId();
                            if (lastBlock.getTimestamp() > Metro.getEpochTime() - 600) {
                                Block previousBlock = Metro.getBlockchain().getBlock(lastBlock.getPreviousBlockId());
                                for (Generator generator : generators.values()) {
                                    generator.setLastBlock(previousBlock);
                                    long timestamp = generator.getTimestamp(generationLimit);
                                    if (!lastBlock.isKeyBlock() && timestamp != generationLimit && generator.getHitTime() > 0 && timestamp < lastBlock.getTimestamp()) {
                                        Logger.logDebugMessage("Pop off: " + generator.toString() + " will pop off last block " + lastBlock.getStringId());
                                        List<BlockImpl> poppedOffBlock = BlockchainProcessorImpl.getInstance().popOffTo(previousBlock);
                                        for (BlockImpl block : poppedOffBlock) {
                                            TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
                                        }
                                        lastBlock = previousBlock;
                                        lastBlockId = previousBlock.getId();
                                        break;
                                    }
                                }
                            }
                            List<Generator> forgers = new ArrayList<>();
                            for (Generator generator : generators.values()) {
                                generator.setLastBlock(lastBlock);
                                if (generator.effectiveBalance.signum() > 0) {
                                    forgers.add(generator);
                                }
                            }
                            Collections.sort(forgers);
                            sortedForgers = Collections.unmodifiableList(forgers);
                            logged = false;
                        }
                        if (!logged) {
                            for (Generator generator : sortedForgers) {
                                if (generator.getHitTime() - generationLimit > 60) {
                                    break;
                                }
                                Logger.logDebugMessage(generator.toString());
                                logged = true;
                            }
                        }
                        for (Generator generator : sortedForgers) {
                            if (generator.getHitTime() > generationLimit || generator.forge(lastBlock, generationLimit)) {
                                return;
                            }
                        }
                    } finally {
                        BlockchainImpl.getInstance().updateUnlock();
                    }
                } catch (Exception e) {
                    Logger.logMessage("Error in block generation thread", e);
                }
            } catch (Throwable t) {
                Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
                t.printStackTrace();
                System.exit(1);
            }

        }

    };

    static {
        if (!Constants.isLightClient) {
            ThreadPool.scheduleThread("GenerateBlocks", generateBlocksThread, 500, TimeUnit.MILLISECONDS);
        }
    }

    static void init() {}

    public static boolean addListener(Listener<Generator> listener, Event eventType) {
        return listeners.addListener(listener, eventType);
    }

    public static boolean removeListener(Listener<Generator> listener, Event eventType) {
        return listeners.removeListener(listener, eventType);
    }

    public static Generator startForging(String secretPhrase) {
        if (generators.size() >= MAX_FORGERS) {
            throw new RuntimeException("Cannot forge with more than " + MAX_FORGERS + " accounts on the same node");
        }
        Generator generator = new Generator(secretPhrase);
        Generator old = generators.putIfAbsent(secretPhrase, generator);
        if (old != null) {
            Logger.logDebugMessage(old + " is already forging");
            return old;
        }
        listeners.notify(generator, Event.START_FORGING);
        Logger.logDebugMessage(generator + " started");
        return generator;
    }

    public static Generator stopForging(String secretPhrase) {
        Generator generator = generators.remove(secretPhrase);
        if (generator != null) {
            Metro.getBlockchain().updateLock();
            try {
                sortedForgers = null;
            } finally {
                Metro.getBlockchain().updateUnlock();
            }
            Logger.logDebugMessage(generator + " stopped");
            listeners.notify(generator, Event.STOP_FORGING);
        }
        return generator;
    }

    public static int stopForging() {
        int count = generators.size();
        Iterator<Generator> iter = generators.values().iterator();
        while (iter.hasNext()) {
            Generator generator = iter.next();
            iter.remove();
            Logger.logDebugMessage(generator + " stopped");
            listeners.notify(generator, Event.STOP_FORGING);
        }
        Metro.getBlockchain().updateLock();
        try {
            sortedForgers = null;
        } finally {
            Metro.getBlockchain().updateUnlock();
        }
        return count;
    }

    public static Generator getGenerator(String secretPhrase) {
        return generators.get(secretPhrase);
    }

    public static int getGeneratorCount() {
        return generators.size();
    }

    public static Collection<Generator> getAllGenerators() {
        return allGenerators;
    }

    public static List<Generator> getSortedForgers() {
        List<Generator> forgers = sortedForgers;
        return forgers == null ? Collections.emptyList() : forgers;
    }

    public static long getNextHitTime(long lastBlockId, long curTime) {
        BlockchainImpl.getInstance().readLock();
        try {
            if (lastBlockId == Generator.lastBlockId && sortedForgers != null) {
                for (Generator generator : sortedForgers) {
                    if (generator.getHitTime() >= curTime - Constants.FORGING_DELAY) {
                        return generator.getHitTime();
                    }
                }
            }
            return 0;
        } finally {
            BlockchainImpl.getInstance().readUnlock();
        }
    }

    static void setDelay(int delay) {
        Generator.delayTime = delay;
    }

    static boolean verifyHit(BigInteger hit, BigInteger effectiveBalance, Block previousBlock, long timestamp) {
        long elapsedTime = timestamp - previousBlock.getTimestamp();
        if (elapsedTime <= 0) {
            return false;
        }
        BigInteger effectiveBaseTarget = BigInteger.valueOf(previousBlock.getBaseTarget()).multiply(effectiveBalance);
        BigInteger prevTarget = effectiveBaseTarget.multiply(BigInteger.valueOf(elapsedTime - 1));
        BigInteger target = prevTarget.add(effectiveBaseTarget);
        return hit.compareTo(target) < 0
                && (hit.compareTo(prevTarget) >= 0
                || (Constants.isTestnet ? elapsedTime > 300 : elapsedTime > 3600)
                || Constants.isOffline);
    }

    static boolean allowsFakeForging(byte[] publicKey) {
        return Constants.isTestnet && publicKey != null && getFakeForgingPublicKeys().contains(Convert.toHexString(publicKey));
    }

    static BigInteger getHit(byte[] publicKey, Block block) {
        if (allowsFakeForging(publicKey)) {
            return BigInteger.ZERO;
        }
        return Convert.fullHashToBigInteger(Convert.generationSequence(block.getGenerationSequence(), publicKey));
    }

    static long getHitTime(BigInteger effectiveBalance, BigInteger hit, Block block) {
        return block.getTimestamp()
                + hit.divide(BigInteger.valueOf(block.getBaseTarget()).multiply(effectiveBalance)).longValue();
    }


    private final Account.FullId accountFullId;
    private final String secretPhrase;
    private final byte[] publicKey;
    private volatile long hitTime;
    private volatile BigInteger hit;
    private volatile BigInteger effectiveBalance;
    private volatile long deadline;

    private Generator(String secretPhrase) {
        this.secretPhrase = secretPhrase;
        this.publicKey = Crypto.getPublicKey(secretPhrase);
        this.accountFullId = Account.FullId.fromPublicKey(publicKey);
        Metro.getBlockchain().updateLock();
        try {
            if (Metro.getBlockchain().getHeight() >= Constants.LAST_KNOWN_BLOCK) {
                setLastBlock(Metro.getBlockchain().getLastBlock());
            }
            sortedForgers = null;
        } finally {
            Metro.getBlockchain().updateUnlock();
        }
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public Account.FullId getAccountFullId() {
        return accountFullId;
    }

    public long getDeadline() {
        return deadline;
    }

    public long getHitTime() {
        return hitTime;
    }

    @Override
    public int compareTo(Generator g) {
        int i = this.hit.multiply(g.effectiveBalance).compareTo(g.hit.multiply(this.effectiveBalance));
        if (i != 0) {
            return i;
        }
        return Long.compare(accountFullId.getLeft(), g.accountFullId.getLeft());
    }

    @Override
    public String toString() {
        return "Forger " + Long.toUnsignedString(accountFullId.getLeft()) + " deadline " + getDeadline() + " hit " + hitTime;
    }

    private void setLastBlock(Block lastBlock) {
        int height = lastBlock.getHeight();
        Account account = Account.getAccount(accountFullId, height);
        if (account == null) {
            effectiveBalance = BigInteger.ZERO;
        } else {
            effectiveBalance = BigInteger.valueOf(Math.max(account.getEffectiveBalanceMTR(height), 0));
        }
        if (effectiveBalance.signum() == 0) {
            hitTime = 0;
            hit = BigInteger.ZERO;
            return;
        }
        hit = getHit(publicKey, lastBlock);
        hitTime = getHitTime(effectiveBalance, hit, lastBlock);
        deadline = Math.max(hitTime - lastBlock.getTimestamp(), 0);
        listeners.notify(this, Event.GENERATION_DEADLINE);
    }

    boolean forge(Block lastBlock, long generationLimit) throws BlockchainProcessor.BlockNotAcceptedException {
        long timestamp = getTimestamp(generationLimit);
        if (!verifyHit(hit, effectiveBalance, lastBlock, timestamp)) {
            Logger.logWarningMessage(this.toString() + " failed to forge at " + timestamp + " height " + lastBlock.getHeight() + " last timestamp " + lastBlock.getTimestamp());
            return false;
        }
        long start = Metro.getEpochTime();
        while (true) {
            try {
                BlockchainProcessorImpl.getInstance().generateBlock(secretPhrase, timestamp);
                setDelay(Constants.FORGING_DELAY);
                return true;
            } catch (BlockchainProcessor.TransactionNotAcceptedException e) {
                // the bad transaction has been expunged, try again
                if (Metro.getEpochTime() - start > 10) { // give up after trying for 10 s
                    throw e;
                }
            }
        }
    }

    private long getTimestamp(long generationLimit) {
        return (generationLimit - hitTime > 3600000) ? generationLimit : (long)hitTime + 1;
    }

    /** Active block identifier */
    private static long activeBlockId;

    /** Map generatorId->ActiveGenerator of generators for the next block */
    private static final Map<Account.FullId, ActiveGenerator> activeGenerators = new HashMap<>();

    /** Generator list has been initialized */
    private static boolean generatorsInitialized = false;

    /** Forgers Merkle has been calculated for this height, reset on each block push/pop */
    private static byte[] forgersMerkle;

    static {
        Metro.getBlockchainProcessor().addListener(block -> {
            if (block.isKeyBlock()) {
                // on a key block, the effective balance might have changed
                activeGenerators.values().forEach(gen -> gen.recalculateEffectiveBalance(block.getHeight()));
                return;
            }
            Account.FullId generatorId = Account.FullId.fromPublicKey(block.getGeneratorPublicKey());
            synchronized(activeGenerators) {
                ActiveGenerator generator = activeGenerators.get(generatorId);
                if (generator == null) {
                    activeGenerators.put(generatorId, new ActiveGenerator(Account.getAccount(generatorId).getFullId(), 1));
                } else {
                    generator.rollBackOrForward(1);
                }
                forgersMerkle = null;
            }
        }, BlockchainProcessor.Event.AFTER_BLOCK_ACCEPT);
        Metro.getBlockchainProcessor().addListener(block -> {
            if (block.isKeyBlock()) {
                activeGenerators.values().forEach(gen -> gen.recalculateEffectiveBalance(block.getHeight() - 1));
                return;
            }
            Account.FullId generatorId = Account.FullId.fromPublicKey(block.getGeneratorPublicKey());
            synchronized(activeGenerators) {
                ActiveGenerator activeGenerator = activeGenerators.get(generatorId);
                if (activeGenerator != null && activeGenerator.rollBackOrForward(-1)) {
                    activeGenerators.remove(generatorId);
                }
                forgersMerkle = null;
            }
        }, BlockchainProcessor.Event.BLOCK_POPPED);

        Metro.getBlockchainProcessor().addListener(block -> resetActiveGenerators(), BlockchainProcessor.Event.RESCAN_BEGIN);
    }
    /**
     * Return a list of generators for the next block.  The caller must hold the blockchain
     * read lock to ensure the integrity of the returned list.
     *
     * @return                      List of generator account identifiers
     */
    public static List<ActiveGenerator> getNextGenerators() {
        List<ActiveGenerator> generatorList = null;
        Blockchain blockchain = Metro.getBlockchain();
        synchronized(activeGenerators) {
            if (!generatorsInitialized) {
                BlockDb.getBlockGenerators(Math.max(1, blockchain.getHeight() - Consensus.FORGER_ACTIVITY_SNAPSHOT_INTERVAL), blockchain.getHeight()).forEach(genIdAndCount -> {
                    activeGenerators.put(genIdAndCount.getLeft(),
                            new ActiveGenerator(genIdAndCount.getLeft(), genIdAndCount.getRight()));
                });
                Logger.logDebugMessage(activeGenerators.size() + " block generators found");
                generatorsInitialized = true;
            }
            Block lastBlock = blockchain.getLastBlock();
            generatorList = new ArrayList<>(activeGenerators.size());
            for (ActiveGenerator generator : activeGenerators.values()) {
                generator.setLastBlock(lastBlock);
                generatorList.add(generator);
            }
            Collections.sort(generatorList);
        }
        return generatorList;
    }

    /**
     * On-demand calculation of forgersMerkle, called from GetWork and validateKeyBlock
     * @return root of the hash tree
     */
    public static byte[] getCurrentForgersMerkleBranches() {
        Metro.getBlockchain().readLock();
        try {
            synchronized(activeGenerators) {
                if (forgersMerkle != null) {
                    return forgersMerkle;
                }
                MessageDigest mdg = HASH_FUNCTION.messageDigest();
                byte[] forgersMerkleVotersBranch = Generator.getNextGenerators().stream().filter(gen -> gen.getEffectiveBalance() >= MIN_FORKVOTING_AMOUNT_MTR).
                        sorted(Comparator.comparingLong(Generator.ActiveGenerator::getEffectiveBalance)).
                        map(Generator.ActiveGenerator::getMerkleNode).reduce(
                        new byte[0],
                        (acc, val) -> {
                            mdg.update(acc);
                            return mdg.digest(val);
                        }
                );
                // TODO #211
                byte[] forgersMerkleOutfeedersBranch = Convert.EMPTY_HASH;
                forgersMerkle = new byte[HASH_SIZE * 2];
                if (forgersMerkleVotersBranch.length > 0) {
                    System.arraycopy(forgersMerkleVotersBranch, 0, forgersMerkle, 0, HASH_SIZE);
                    System.arraycopy(forgersMerkleOutfeedersBranch, 0, forgersMerkle, HASH_SIZE, HASH_SIZE);
                }
                return forgersMerkle;
            }
        } finally {
            Metro.getBlockchain().readUnlock();
        }
    }

    public static void resetActiveGenerators() {
        synchronized(activeGenerators) {
            generatorsInitialized = false;
            activeGenerators.clear();
            forgersMerkle = null;
        }
    }
    /**
     * Active generator
     */
    public static class ActiveGenerator implements Comparable<ActiveGenerator> {
        // TODO #188
        private final Account.FullId accountFullId;
        private Account account;
        private long hitTime;
        private Long effectiveBalanceMTR;
        private byte[] publicKey;
        private int blockCounter;

        public ActiveGenerator(Account.FullId accountFullId, int blockCounter) {
            this.accountFullId = accountFullId;
            this.hitTime = Long.MAX_VALUE;
            this.blockCounter = blockCounter;
        }

        public Account.FullId getAccountFullId() {
            return accountFullId;
        }

        public boolean rollBackOrForward(int blocks) {
            this.blockCounter += blocks;
            return this.blockCounter <= 0;
        }

        public Long getEffectiveBalance() {
            return effectiveBalanceMTR;
        }

        public void recalculateEffectiveBalance(int height) {
            account = Account.getAccount(accountFullId, height);
            if (account != null) {
                effectiveBalanceMTR = Math.max(account.getEffectiveBalanceMTR(height), 0);
            }
        }

        public long getHitTime() {
            return hitTime;
        }

        private void setLastBlock(Block lastBlock) {
            if (publicKey == null) {
                publicKey = Account.getPublicKey(accountFullId.getLeft());
                if (publicKey == null) {
                    hitTime = Long.MAX_VALUE;
                    return;
                }
            }
            if (effectiveBalanceMTR == null) {
                recalculateEffectiveBalance(lastBlock.getHeight());
            }
            if (account == null || effectiveBalanceMTR == 0) {
                hitTime = Long.MAX_VALUE;
                return;
            }
            BigInteger effectiveBalance = BigInteger.valueOf(effectiveBalanceMTR);
            BigInteger hit = Generator.getHit(publicKey, lastBlock);
            hitTime = Generator.getHitTime(effectiveBalance, hit, lastBlock);
        }

        @Override
        public int hashCode() {
            return Long.hashCode(accountFullId.getLeft());
        }

        @Override
        public boolean equals(Object obj) {
            return (obj != null && (obj instanceof ActiveGenerator) && accountFullId == ((ActiveGenerator)obj).accountFullId);
        }

        @Override
        public int compareTo(ActiveGenerator obj) {
            return (hitTime < obj.hitTime ? -1 : (hitTime > obj.hitTime ? 1 : 0));
        }

        public byte[] getMerkleNode() {
            ByteBuffer node = ByteBuffer.allocate(32 + 4);
            node.order(ByteOrder.LITTLE_ENDIAN);
            node.put(publicKey);
            node.putInt(effectiveBalanceMTR.intValue());
            return node.array();
        }
    }
}
