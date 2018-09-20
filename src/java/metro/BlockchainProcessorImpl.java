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
import metro.daemon.TipCache;
import metro.db.DbIterator;
import metro.db.DerivedDbTable;
import metro.db.FilteringIterator;
import metro.db.FullTextTrigger;
import metro.peer.Peer;
import metro.peer.Peers;
import metro.util.BitcoinJUtils;
import metro.util.Convert;
import metro.util.JSON;
import metro.util.Listener;
import metro.util.Listeners;
import metro.util.Logger;
import metro.util.ThreadPool;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static metro.Consensus.HASH_FUNCTION;
import static metro.Consensus.STRATUM_COMPATIBILITY_BLOCK;
import static metro.Consensus.getPermissibleKeyBlockVersions;
import static metro.Consensus.getPosBlockVersion;
import static metro.Consensus.getPreferableKeyBlockVersion;
import static metro.Consensus.getTransactionVersion;
import static metro.util.Convert.HASH_SIZE;

public final class BlockchainProcessorImpl implements BlockchainProcessor {

    private static final byte[] CHECKSUM_1 = Constants.isTestnet ?
            null : Convert.parseHexString("4e3fc0fcb9350472ac1a7f2d510b4a806671a517e0ede5c4a2f26499c84a2ce7");

    private static final BlockchainProcessorImpl instance = new BlockchainProcessorImpl();

    public static final String SELECT_FORGERS_SQL = "SELECT S.super_id, PK.public_key, S.effective FROM (" +
            "SELECT IFNULL(A.active_lessee_id,A.id) super_id, SUM(CASEWHEN(A.active_lessee_id IS NULL, 1, 0)) generator, SUM(A.balance) - SUM(IFNULL(B.additions,0)) effective FROM Account A " +
            "LEFT JOIN (SELECT account_id, SUM (additions) AS additions FROM account_guaranteed_balance WHERE height > ? AND height <= ? AND NOT coinbase GROUP BY account_id) B " +
            "on (A.id = B.account_id) LEFT JOIN (SELECT active_lessee_id id, SUM(balance) balance FROM account " +
            "WHERE latest GROUP BY active_lessee_id) L ON (L.id = A.id) " +
            "WHERE A.latest AND ((A.last_forged_height > ? AND A.last_forged_height <= ?) OR A.active_lessee_id IS NOT NULL) GROUP BY super_id HAVING generator > 0 ORDER BY effective, super_id) S " +
            "JOIN public_key PK ON PK.account_id = S.super_id WHERE PK.latest";

    public static BlockchainProcessorImpl getInstance() {
        return instance;
    }

    private final BlockchainImpl blockchain = BlockchainImpl.getInstance();

    private final ExecutorService networkService = Executors.newCachedThreadPool();
    private final List<DerivedDbTable> derivedTables = new CopyOnWriteArrayList<>();
    private final boolean trimDerivedTables = Metro.getBooleanProperty("metro.trimDerivedTables");
    private final int defaultNumberOfForkConfirmations = Metro.getIntProperty(Constants.isTestnet
            ? "metro.testnetNumberOfForkConfirmations" : "metro.numberOfForkConfirmations");
    private final boolean simulateEndlessDownload = Metro.getBooleanProperty("metro.simulateEndlessDownload");

    private int initialScanHeight;
    private volatile int lastTrimHeight;
    private volatile long lastRestoreTime = 0;
    private final Set<Long> prunableTransactions = new HashSet<>();

    private final Listeners<Block, Event> blockListeners = new Listeners<>();
    private volatile Peer lastBlockchainFeeder;
    private volatile int lastBlockchainFeederHeight;
    private volatile boolean getMoreBlocks = true;

    private volatile byte[] lastKeyBlockForgersMerkleBranches;

    public byte[] getLastKeyBlockForgersMerkleBranches() {
        return lastKeyBlockForgersMerkleBranches;
    }

    private volatile boolean isTrimming;
    private volatile boolean isScanning;
    private volatile boolean isDownloading;
    private volatile boolean isProcessingBlock;
    private volatile boolean isRestoring;
    private volatile boolean alreadyInitialized = false;
    private volatile long genesisBlockId;

    private final Runnable getMoreBlocksThread = new Runnable() {

        private final JSONStreamAware getCumulativeDifficultyRequest;

        {
            JSONObject request = new JSONObject();
            request.put("requestType", "getCumulativeDifficulty");
            getCumulativeDifficultyRequest = JSON.prepareRequest(request);
        }

        private boolean peerHasMore;
        private List<Peer> connectedPublicPeers;
        private List<Long> chainBlockIds;
        private long totalTime = 1;
        private int totalBlocks;

        @Override
        public void run() {
            try {
                //
                // Download blocks until we are up-to-date
                //
                while (true) {
                    if (!getMoreBlocks) {
                        return;
                    }
                    int chainHeight = blockchain.getHeight();
                    downloadPeer();
                    if (blockchain.getHeight() == chainHeight) {
                        if (isDownloading && !simulateEndlessDownload) {
                            Logger.logMessage("Finished blockchain download");
                            isDownloading = false;
                        }
                        break;
                    }
                }
                //
                // Restore prunable data
                //
                long now = Metro.getEpochTime();
                if (!isRestoring && !prunableTransactions.isEmpty() && now - lastRestoreTime > 60 * 60) {
                    isRestoring = true;
                    lastRestoreTime = now;
                    networkService.submit(new RestorePrunableDataTask());
                }
            } catch (InterruptedException e) {
                Logger.logDebugMessage("Blockchain download thread interrupted");
            } catch (Throwable t) {
                Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString(), t);
                System.exit(1);
            }
        }

        private void downloadPeer() throws InterruptedException {
            try {
                long startTime = System.currentTimeMillis();
                int numberOfForkConfirmations = blockchain.getHeight() > Constants.LAST_CHECKSUM_BLOCK - Consensus.BLOCKCHAIN_THREE_HOURS ?
                        defaultNumberOfForkConfirmations : Math.min(1, defaultNumberOfForkConfirmations);
                connectedPublicPeers = Peers.getPublicPeers(Peer.State.CONNECTED, true);
                if (connectedPublicPeers.size() <= numberOfForkConfirmations) {
                    return;
                }
                peerHasMore = true;
                final Peer peer = Peers.getWeightedPeer(connectedPublicPeers);
                if (peer == null) {
                    return;
                }
                JSONObject response = peer.send(getCumulativeDifficultyRequest);
                if (response == null) {
                    return;
                }
                BigInteger curCumulativeDifficulty = blockchain.getLastBlock().getCumulativeDifficulty();
                String peerCumulativeDifficulty = (String) response.get("cumulativeDifficulty");
                if (peerCumulativeDifficulty == null) {
                    return;
                }
                BigInteger betterCumulativeDifficulty = new BigInteger(peerCumulativeDifficulty);
                if (betterCumulativeDifficulty.compareTo(curCumulativeDifficulty) < 0) {
                    return;
                }
                if (response.get("blockchainHeight") != null) {
                    lastBlockchainFeeder = peer;
                    lastBlockchainFeederHeight = ((Long) response.get("blockchainHeight")).intValue();
                }
                if (betterCumulativeDifficulty.equals(curCumulativeDifficulty)) {
                    return;
                }

                long commonMilestoneBlockId = genesisBlockId;

                if (blockchain.getHeight() > 0) {
                    commonMilestoneBlockId = getCommonMilestoneBlockId(peer);
                }
                if (commonMilestoneBlockId == 0 || !peerHasMore) {
                    return;
                }

                chainBlockIds = getBlockIdsAfterCommon(peer, commonMilestoneBlockId, false);
                if (chainBlockIds.size() < 2 || !peerHasMore) {
                    if (commonMilestoneBlockId == genesisBlockId) {
                        Logger.logInfoMessage(String.format("Cannot load blocks after genesis block %d from peer %s, perhaps using different Genesis block",
                                commonMilestoneBlockId, peer.getAnnouncedAddress()));
                    }
                    return;
                }

                final long commonBlockId = chainBlockIds.get(0);
                final Block commonBlock = blockchain.getBlock(commonBlockId);
                if (commonBlock == null || blockchain.getHeight() - commonBlock.getHeight() >= Consensus.BLOCKCHAIN_THREE_HOURS) {
                    if (commonBlock != null) {
                        Logger.logDebugMessage(peer + " advertised chain with better difficulty, but the last common block is at height " + commonBlock.getHeight());
                    }
                    return;
                }
                if (simulateEndlessDownload) {
                    isDownloading = true;
                    return;
                }
                if (!isDownloading && lastBlockchainFeederHeight - commonBlock.getHeight() > 10) {
                    Logger.logMessage("Blockchain download in progress");
                    isDownloading = true;
                }

                blockchain.updateLock();
                try {
                    if (betterCumulativeDifficulty.compareTo(blockchain.getLastBlock().getCumulativeDifficulty()) <= 0) {
                        return;
                    }
                    long lastBlockId = blockchain.getLastBlock().getId();
                    downloadBlockchain(peer, commonBlock, commonBlock.getHeight());
                    if (blockchain.getHeight() - commonBlock.getHeight() <= 10) {
                        return;
                    }

                    int confirmations = 0;
                    for (Peer otherPeer : connectedPublicPeers) {
                        if (confirmations >= numberOfForkConfirmations) {
                            break;
                        }
                        if (peer.getHost().equals(otherPeer.getHost())) {
                            continue;
                        }
                        chainBlockIds = getBlockIdsAfterCommon(otherPeer, commonBlockId, true);
                        if (chainBlockIds.isEmpty()) {
                            continue;
                        }
                        long otherPeerCommonBlockId = chainBlockIds.get(0);
                        if (otherPeerCommonBlockId == blockchain.getLastBlock().getId()) {
                            confirmations++;
                            continue;
                        }
                        Block otherPeerCommonBlock = blockchain.getBlock(otherPeerCommonBlockId);
                        if (blockchain.getHeight() - otherPeerCommonBlock.getHeight() >= Consensus.BLOCKCHAIN_THREE_HOURS) {
                            continue;
                        }
                        String otherPeerCumulativeDifficulty;
                        JSONObject otherPeerResponse = peer.send(getCumulativeDifficultyRequest);
                        if (otherPeerResponse == null || (otherPeerCumulativeDifficulty = (String) response.get("cumulativeDifficulty")) == null) {
                            continue;
                        }
                        if (new BigInteger(otherPeerCumulativeDifficulty).compareTo(blockchain.getLastBlock().getCumulativeDifficulty()) <= 0) {
                            continue;
                        }
                        Logger.logDebugMessage("Found a peer with better difficulty");
                        downloadBlockchain(otherPeer, otherPeerCommonBlock, commonBlock.getHeight());
                    }
                    Logger.logDebugMessage("Got " + confirmations + " confirmations");

                    if (blockchain.getLastBlock().getId() != lastBlockId) {
                        long time = System.currentTimeMillis() - startTime;
                        totalTime += time;
                        int numBlocks = blockchain.getHeight() - commonBlock.getHeight();
                        totalBlocks += numBlocks;
                        Logger.logMessage("Downloaded " + numBlocks + " blocks in "
                                + time / 1000 + " s, " + (totalBlocks * 1000) / totalTime + " per s, "
                                + totalTime * (lastBlockchainFeederHeight - blockchain.getHeight()) / ((long) totalBlocks * 1000 * 60) + " min left");
                    } else {
                        Logger.logDebugMessage("Did not accept peer's blocks, back to our own fork");
                    }
                } finally {
                    blockchain.updateUnlock();
                }

            } catch (MetroException.StopException e) {
                Logger.logMessage("Blockchain download stopped: " + e.getMessage());
                throw new InterruptedException("Blockchain download stopped");
            } catch (Exception e) {
                Logger.logMessage("Error in blockchain download thread", e);
            }
        }

        private long getCommonMilestoneBlockId(Peer peer) {

            String lastMilestoneBlockId = null;

            while (true) {
                JSONObject milestoneBlockIdsRequest = new JSONObject();
                milestoneBlockIdsRequest.put("requestType", "getMilestoneBlockIds");
                if (lastMilestoneBlockId == null) {
                    milestoneBlockIdsRequest.put("lastBlockId", blockchain.getLastBlock().getStringId());
                } else {
                    milestoneBlockIdsRequest.put("lastMilestoneBlockId", lastMilestoneBlockId);
                }

                JSONObject response = peer.send(JSON.prepareRequest(milestoneBlockIdsRequest));
                if (response == null) {
                    return 0;
                }
                JSONArray milestoneBlockIds = (JSONArray) response.get("milestoneBlockIds");
                if (milestoneBlockIds == null) {
                    return 0;
                }
                if (milestoneBlockIds.isEmpty()) {
                    return genesisBlockId;
                }
                // prevent overloading with blockIds
                if (milestoneBlockIds.size() > 20) {
                    Logger.logWarningMessage("Obsolete or rogue peer " + peer.getHost() + " sends too many milestoneBlockIds, blacklisting");
                    peer.blacklist("Too many milestoneBlockIds");
                    return 0;
                }
                if (Boolean.TRUE.equals(response.get("last"))) {
                    peerHasMore = false;
                }
                for (Object milestoneBlockId : milestoneBlockIds) {
                    long blockId = Convert.parseUnsignedLong((String) milestoneBlockId);
                    if (BlockDb.hasBlock(blockId)) {
                        if (lastMilestoneBlockId == null && milestoneBlockIds.size() > 1) {
                            peerHasMore = false;
                        }
                        return blockId;
                    }
                    lastMilestoneBlockId = (String) milestoneBlockId;
                }
            }

        }

        private List<Long> getBlockIdsAfterCommon(final Peer peer, final long startBlockId, final boolean countFromStart) {
            long matchId = startBlockId;
            List<Long> blockList = new ArrayList<>(Consensus.BLOCKCHAIN_THREE_HOURS);
            boolean matched = false;
            int limit = countFromStart ? Consensus.BLOCKCHAIN_THREE_HOURS : Consensus.BLOCKCHAIN_SIX_HOURS;
            while (true) {
                JSONObject request = new JSONObject();
                request.put("requestType", "getNextBlockIds");
                request.put("blockId", Long.toUnsignedString(matchId));
                request.put("limit", limit);
                JSONObject response = peer.send(JSON.prepareRequest(request));
                if (response == null) {
                    return Collections.emptyList();
                }
                JSONArray nextBlockIds = (JSONArray) response.get("nextBlockIds");
                if (nextBlockIds == null || nextBlockIds.size() == 0) {
                    break;
                }
                // prevent overloading with blockIds
                if (nextBlockIds.size() > limit) {
                    Logger.logWarningMessage("Obsolete or rogue peer " + peer.getHost() + " sends too many nextBlockIds, blacklisting");
                    peer.blacklist("Too many nextBlockIds");
                    return Collections.emptyList();
                }
                boolean matching = true;
                int count = 0;
                for (Object nextBlockId : nextBlockIds) {
                    long blockId = Convert.parseUnsignedLong((String)nextBlockId);
                    if (matching) {
                        if (BlockDb.hasBlock(blockId)) {
                            matchId = blockId;
                            matched = true;
                        } else {
                            blockList.add(matchId);
                            blockList.add(blockId);
                            matching = false;
                        }
                    } else {
                        blockList.add(blockId);
                        if (blockList.size() >= Consensus.BLOCKCHAIN_THREE_HOURS) {
                            break;
                        }
                    }
                    if (countFromStart && ++count >= Consensus.BLOCKCHAIN_THREE_HOURS) {
                        break;
                    }
                }
                if (!matching || countFromStart) {
                    break;
                }
            }
            if (blockList.isEmpty() && matched) {
                blockList.add(matchId);
            }
            return blockList;
        }

        /**
         * Download the block chain
         *
         * @param   feederPeer              Peer supplying the blocks list
         * @param   commonBlock             Common block
         * @throws  InterruptedException    Download interrupted
         */
        private void downloadBlockchain(final Peer feederPeer, final Block commonBlock, final int startHeight) throws InterruptedException {
            Map<Long, PeerBlock> blockMap = new HashMap<>();
            //
            // Break the download into multiple segments.  The first block in each segment
            // is the common block for that segment.
            //
            List<GetNextBlocks> getList = new ArrayList<>();
            int segSize = 36;
            int stop = chainBlockIds.size() - 1;
            for (int start = 0; start < stop; start += segSize) {
                getList.add(new GetNextBlocks(chainBlockIds, start, Math.min(start + segSize, stop)));
            }
            int nextPeerIndex = ThreadLocalRandom.current().nextInt(connectedPublicPeers.size());
            long maxResponseTime = 0;
            Peer slowestPeer = null;
            //
            // Issue the getNextBlocks requests and get the results.  We will repeat
            // a request if the peer didn't respond or returned a partial block list.
            // The download will be aborted if we are unable to get a segment after
            // retrying with different peers.
            //
            download: while (!getList.isEmpty()) {
                //
                // Submit threads to issue 'getNextBlocks' requests.  The first segment
                // will always be sent to the feeder peer.  Subsequent segments will
                // be sent to the feeder peer if we failed trying to download the blocks
                // from another peer.  We will stop the download and process any pending
                // blocks if we are unable to download a segment from the feeder peer.
                //
                for (GetNextBlocks nextBlocks : getList) {
                    Peer peer;
                    if (nextBlocks.getRequestCount() > 1) {
                        break download;
                    }
                    if (nextBlocks.getStart() == 0 || nextBlocks.getRequestCount() != 0) {
                        peer = feederPeer;
                    } else {
                        if (nextPeerIndex >= connectedPublicPeers.size()) {
                            nextPeerIndex = 0;
                        }
                        peer = connectedPublicPeers.get(nextPeerIndex++);
                    }
                    if (nextBlocks.getPeer() == peer) {
                        break download;
                    }
                    nextBlocks.setPeer(peer);
                    Future<List<BlockImpl>> future = networkService.submit(nextBlocks);
                    nextBlocks.setFuture(future);
                }
                //
                // Get the results.  A peer is on a different fork if a returned
                // block is not in the block identifier list.
                //
                Iterator<GetNextBlocks> it = getList.iterator();
                while (it.hasNext()) {
                    GetNextBlocks nextBlocks = it.next();
                    List<BlockImpl> blockList;
                    try {
                        blockList = nextBlocks.getFuture().get();
                    } catch (ExecutionException exc) {
                        throw new RuntimeException(exc.getMessage(), exc);
                    }
                    if (blockList == null) {
                        nextBlocks.getPeer().deactivate();
                        continue;
                    }
                    Peer peer = nextBlocks.getPeer();
                    int index = nextBlocks.getStart() + 1;
                    for (BlockImpl block : blockList) {
                        if (block.getId() != chainBlockIds.get(index)) {
                            break;
                        }
                        blockMap.put(block.getId(), new PeerBlock(peer, block));
                        index++;
                    }
                    if (index > nextBlocks.getStop()) {
                        it.remove();
                    } else {
                        nextBlocks.setStart(index - 1);
                    }
                    if (nextBlocks.getResponseTime() > maxResponseTime) {
                        maxResponseTime = nextBlocks.getResponseTime();
                        slowestPeer = nextBlocks.getPeer();
                    }
                }

            }
            if (slowestPeer != null && connectedPublicPeers.size() >= Peers.maxNumberOfConnectedPublicPeers && chainBlockIds.size() > Consensus.BLOCKCHAIN_THREE_HOURS / 2) {
                Logger.logDebugMessage(slowestPeer.getHost() + " took " + maxResponseTime + " ms, disconnecting");
                slowestPeer.deactivate();
            }
            //
            // Add the new blocks to the blockchain.  We will stop if we encounter
            // a missing block (this will happen if an invalid block is encountered
            // when downloading the blocks)
            //
            blockchain.writeLock();
            try {
                List<BlockImpl> forkBlocks = new ArrayList<>();
                for (int index = 1; index < chainBlockIds.size() && blockchain.getHeight() - startHeight < Consensus.BLOCKCHAIN_THREE_HOURS; index++) {
                    PeerBlock peerBlock = blockMap.get(chainBlockIds.get(index));
                    if (peerBlock == null) {
                        break;
                    }
                    BlockImpl block = peerBlock.getBlock();
                    if (blockchain.getLastBlock().getId() == block.getPreviousBlockId()) {
                        try {
                            pushBlock(block);
                        } catch (BlockNotAcceptedException e) {
                            peerBlock.getPeer().blacklist(e);
                        }
                    } else {
                        forkBlocks.add(block);
                    }
                }
                //
                // Process a fork
                //
                int myForkSize = blockchain.getHeight() - startHeight;
                if (!forkBlocks.isEmpty() && myForkSize < Consensus.BLOCKCHAIN_THREE_HOURS) {
                    Logger.logWarningMessage("Will process a fork of " + forkBlocks.size() + " blocks, mine is " + myForkSize);
                    processFork(feederPeer, forkBlocks, commonBlock);
                }
            } finally {
                blockchain.writeUnlock();
            }

        }


    };

    private boolean processFork(final Peer peer, final List<BlockImpl> forkBlocks, final Block commonBlock) {
        BigInteger curCumulativeDifficulty = blockchain.getLastBlock().getCumulativeDifficulty();

        List<BlockImpl> myPoppedOffBlocks = popOffTo(commonBlock);

        int pushedForkBlocks = 0;
        if (blockchain.getLastBlock().getId() == commonBlock.getId()) {
            for (BlockImpl block : forkBlocks) {
                if (blockchain.getLastBlock().getId() == block.getPreviousBlockId()) {
                    try {
                        pushBlock(block);
                        pushedForkBlocks += 1;
                    } catch (BlockNotAcceptedException e) {
                        if (peer != null) {
                            peer.blacklist(e);
                        }
                        break;
                    }
                }
            }
        }

        if (pushedForkBlocks > 0 && blockchain.getLastBlock().getCumulativeDifficulty().compareTo(curCumulativeDifficulty) < 0) {
            if (peer != null) {
                Logger.logDebugMessage("Pop off caused by peer " + peer.getHost() + ", blacklisting");
                peer.blacklist("Pop off");
            }
            List<BlockImpl> peerPoppedOffBlocks = popOffTo(commonBlock);
            pushedForkBlocks = 0;
            for (BlockImpl block : peerPoppedOffBlocks) {
                TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
            }
        }

        if (pushedForkBlocks == 0) {
            Logger.logDebugMessage("Didn't accept any blocks, pushing back my previous blocks");
            for (int i = myPoppedOffBlocks.size() - 1; i >= 0; i--) {
                BlockImpl block = myPoppedOffBlocks.remove(i);
                try {
                    pushBlock(block);
                } catch (BlockNotAcceptedException e) {
                    Logger.logErrorMessage("Popped off block no longer acceptable: " + block.getJSONObject().toJSONString(), e);
                    break;
                }
            }
            return false;
        } else {
            Logger.logDebugMessage("Switched to peer's fork");
            for (BlockImpl block : myPoppedOffBlocks) {
                TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
            }
            return true;
        }
    }

    /**
     * Callable method to get the next block segment from the selected peer
     */
    private static class GetNextBlocks implements Callable<List<BlockImpl>> {

        /** Callable future */
        private Future<List<BlockImpl>> future;

        /** Peer */
        private Peer peer;

        /** Block identifier list */
        private final List<Long> blockIds;

        /** Start index */
        private int start;

        /** Stop index */
        private int stop;

        /** Request count */
        private int requestCount;

        /** Time it took to return getNextBlocks */
        private long responseTime;

        /**
         * Create the callable future
         *
         * @param   blockIds            Block identifier list
         * @param   start               Start index within the list
         * @param   stop                Stop index within the list
         */
        public GetNextBlocks(List<Long> blockIds, int start, int stop) {
            this.blockIds = blockIds;
            this.start = start;
            this.stop = stop;
            this.requestCount = 0;
        }

        /**
         * Return the result
         *
         * @return                      List of blocks or null if an error occurred
         */
        @Override
        public List<BlockImpl> call() {
            requestCount++;
            //
            // Build the block request list
            //
            JSONArray idList = new JSONArray();
            for (int i = start + 1; i <= stop; i++) {
                idList.add(Long.toUnsignedString(blockIds.get(i)));
            }
            JSONObject request = new JSONObject();
            request.put("requestType", "getNextBlocks");
            request.put("blockIds", idList);
            request.put("blockId", Long.toUnsignedString(blockIds.get(start)));
            long startTime = System.currentTimeMillis();
            JSONObject response = peer.send(JSON.prepareRequest(request), 10 * 1024 * 1024);
            responseTime = System.currentTimeMillis() - startTime;
            if (response == null) {
                return null;
            }
            //
            // Get the list of blocks.  We will stop parsing blocks if we encounter
            // an invalid block.  We will return the valid blocks and reset the stop
            // index so no more blocks will be processed.
            //
            List<JSONObject> nextBlocks = (List<JSONObject>)response.get("nextBlocks");
            if (nextBlocks == null)
                return null;
            if (nextBlocks.size() > 36) {
                Logger.logWarningMessage("Obsolete or rogue peer " + peer.getHost() + " sends too many nextBlocks, blacklisting");
                peer.blacklist("Too many nextBlocks");
                return null;
            }
            List<BlockImpl> blockList = new ArrayList<>(nextBlocks.size());
            try {
                int count = stop - start;
                for (JSONObject blockData : nextBlocks) {
                    blockList.add(BlockImpl.parseBlock(blockData, false));
                    if (--count <= 0)
                        break;
                }
            } catch (RuntimeException | MetroException.NotValidException e) {
                Logger.logDebugMessage("Failed to parse block: " + e.toString(), e);
                peer.blacklist(e);
                stop = start + blockList.size();
            }
            return blockList;
        }

        /**
         * Return the callable future
         *
         * @return                      Callable future
         */
        public Future<List<BlockImpl>> getFuture() {
            return future;
        }

        /**
         * Set the callable future
         *
         * @param   future              Callable future
         */
        public void setFuture(Future<List<BlockImpl>> future) {
            this.future = future;
        }

        /**
         * Return the peer
         *
         * @return                      Peer
         */
        public Peer getPeer() {
            return peer;
        }

        /**
         * Set the peer
         *
         * @param   peer                Peer
         */
        public void setPeer(Peer peer) {
            this.peer = peer;
        }

        /**
         * Return the start index
         *
         * @return                      Start index
         */
        public int getStart() {
            return start;
        }

        /**
         * Set the start index
         *
         * @param   start               Start index
         */
        public void setStart(int start) {
            this.start = start;
        }

        /**
         * Return the stop index
         *
         * @return                      Stop index
         */
        public int getStop() {
            return stop;
        }

        /**
         * Return the request count
         *
         * @return                      Request count
         */
        public int getRequestCount() {
            return requestCount;
        }

        /**
         * Return the response time
         *
         * @return                      Response time
         */
        public long getResponseTime() {
            return responseTime;
        }
    }

    /**
     * Block returned by a peer
     */
    private static class PeerBlock {

        /** Peer */
        private final Peer peer;

        /** Block */
        private final BlockImpl block;

        /**
         * Create the peer block
         *
         * @param   peer                Peer
         * @param   block               Block
         */
        public PeerBlock(Peer peer, BlockImpl block) {
            this.peer = peer;
            this.block = block;
        }

        /**
         * Return the peer
         *
         * @return                      Peer
         */
        public Peer getPeer() {
            return peer;
        }

        /**
         * Return the block
         *
         * @return                      Block
         */
        public BlockImpl getBlock() {
            return block;
        }
    }

    /**
     * Task to restore prunable data for downloaded blocks
     */
    private class RestorePrunableDataTask implements Runnable {

        @Override
        public void run() {
            Peer peer = null;
            try {
                //
                // Locate an archive peer
                //
                List<Peer> peers = Peers.getPeers(chkPeer -> chkPeer.providesService(Peer.Service.PRUNABLE) &&
                        !chkPeer.isBlacklisted() && chkPeer.getAnnouncedAddress() != null);
                while (!peers.isEmpty()) {
                    Peer chkPeer = peers.get(ThreadLocalRandom.current().nextInt(peers.size()));
                    if (chkPeer.getState() != Peer.State.CONNECTED) {
                        Peers.connectPeer(chkPeer);
                    }
                    if (chkPeer.getState() == Peer.State.CONNECTED) {
                        peer = chkPeer;
                        break;
                    }
                }
                if (peer == null) {
                    Logger.logDebugMessage("Cannot find any archive peers");
                    return;
                }
                Logger.logDebugMessage("Connected to archive peer " + peer.getHost());
                //
                // Make a copy of the prunable transaction list so we can remove entries
                // as we process them while still retaining the entry if we need to
                // retry later using a different archive peer
                //
                Set<Long> processing;
                synchronized (prunableTransactions) {
                    processing = new HashSet<>(prunableTransactions.size());
                    processing.addAll(prunableTransactions);
                }
                Logger.logDebugMessage("Need to restore " + processing.size() + " pruned data");
                //
                // Request transactions in batches of 100 until all transactions have been processed
                //
                while (!processing.isEmpty()) {
                    //
                    // Get the pruned transactions from the archive peer
                    //
                    JSONObject request = new JSONObject();
                    JSONArray requestList = new JSONArray();
                    synchronized (prunableTransactions) {
                        Iterator<Long> it = processing.iterator();
                        while (it.hasNext()) {
                            long id = it.next();
                            requestList.add(Long.toUnsignedString(id));
                            it.remove();
                            if (requestList.size() == 100)
                                break;
                        }
                    }
                    request.put("requestType", "getTransactions");
                    request.put("transactionIds", requestList);
                    JSONObject response = peer.send(JSON.prepareRequest(request), 10 * 1024 * 1024);
                    if (response == null) {
                        return;
                    }
                    //
                    // Restore the prunable data
                    //
                    JSONArray transactions = (JSONArray)response.get("transactions");
                    if (transactions == null || transactions.isEmpty()) {
                        return;
                    }
                    List<Transaction> processed = Metro.getTransactionProcessor().restorePrunableData(transactions);
                    //
                    // Remove transactions that have been successfully processed
                    //
                    synchronized (prunableTransactions) {
                        processed.forEach(transaction -> prunableTransactions.remove(transaction.getId()));
                    }
                }
                Logger.logDebugMessage("Done retrieving prunable transactions from " + peer.getHost());
            } catch (MetroException.ValidationException e) {
                Logger.logErrorMessage("Peer " + peer.getHost() + " returned invalid prunable transaction", e);
                peer.blacklist(e);
            } catch (RuntimeException e) {
                Logger.logErrorMessage("Unable to restore prunable data", e);
            } finally {
                isRestoring = false;
                Logger.logDebugMessage("Remaining " + prunableTransactions.size() + " pruned transactions");
            }
        }
    }

    private final Listener<Block> checksumListener = block -> {
        if (block.getHeight() == Constants.CHECKSUM_BLOCK_1) {
            if (! verifyChecksum(CHECKSUM_1, 0, Constants.CHECKSUM_BLOCK_1)) {
                popOffTo(0);
            }
        }
    };

    private BlockchainProcessorImpl() {
        final int trimFrequency = Metro.getIntProperty("metro.trimFrequency");
        blockListeners.addListener(block -> {
            if (block.getHeight() % 5000 == 0) {
                Logger.logMessage("processed block " + block.getHeight());
            }
            if (trimDerivedTables && block.getHeight() % trimFrequency == 0) {
                doTrimDerivedTables();
            }
        }, Event.BLOCK_SCANNED);

        blockListeners.addListener(block -> {
            if (trimDerivedTables && block.getHeight() % trimFrequency == 0 && !isTrimming) {
                isTrimming = true;
                networkService.submit(() -> {
                    trimDerivedTables();
                    isTrimming = false;
                });
            }
            if (block.getHeight() % 5000 == 0) {
                Logger.logMessage("received block " + block.getHeight());
                if (!isDownloading || block.getHeight() % 50000 == 0) {
                    networkService.submit(Db.db::analyzeTables);
                }
            }
        }, Event.BLOCK_PUSHED);

        blockListeners.addListener(checksumListener, Event.BLOCK_PUSHED);

        blockListeners.addListener(block -> Db.db.analyzeTables(), Event.RESCAN_END);

        ThreadPool.runBeforeStart(() -> {
            alreadyInitialized = true;
            addGenesisBlock();
            int minBadBlockHeight = Integer.MAX_VALUE;
            for (Long id: Consensus.badBlockSet) {
                BlockImpl badBlock = BlockDb.findBlock(id);
                if (badBlock != null) {
                    minBadBlockHeight = Math.min(badBlock.getHeight(), minBadBlockHeight);
                    break;
                }
            }
            if (minBadBlockHeight < Integer.MAX_VALUE) {
                popOffTo(Math.max(0, minBadBlockHeight - 1));
                Block keyBlock = BlockchainImpl.getInstance().getLastKeyBlock();
                if (keyBlock != null) {
                    scan(keyBlock.getHeight(), false);
                } else {
                    resetForgersMerkle();
                }
            } else if (Metro.getBooleanProperty("metro.forceScan")) {
                scan(0, Metro.getBooleanProperty("metro.forceValidate"));
            } else {
                boolean rescan;
                boolean validate;
                int height;
                try (Connection con = Db.db.getConnection();
                     Statement stmt = con.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT * FROM scan")) {
                    rs.next();
                    rescan = rs.getBoolean("rescan");
                    validate = rs.getBoolean("validate");
                    height = rs.getInt("height");
                } catch (SQLException e) {
                    throw new RuntimeException(e.toString(), e);
                }
                if (rescan) {
                    scan(height, validate);
                }
            }
            // find last key block with ANY timestamp
            Block lastKeyBlock = BlockDb.findLastKeyBlock(Long.MAX_VALUE);
            if (lastKeyBlock != null && Arrays.equals(lastKeyBlockForgersMerkleBranches, Constants.TWO_BRANCHES_EMPTY_MERKLE_ROOT)) {
                // our previous scan, if any, was insufficient to initialize the forgersMerkle; rescan starting from last key block
                scan(lastKeyBlock.getHeight(), false);
            }
        }, false);

        // filled by zeroes during the 1st cluster (between Genesis and key block)
        resetForgersMerkle();
        if (!Constants.isLightClient && !Constants.isOffline) {
            ThreadPool.scheduleThread("GetMoreBlocks", getMoreBlocksThread, 1);
        }
    }

    @Override
    public boolean addListener(Listener<Block> listener, BlockchainProcessor.Event eventType) {
        return blockListeners.addListener(listener, eventType);
    }

    @Override
    public boolean removeListener(Listener<Block> listener, Event eventType) {
        return blockListeners.removeListener(listener, eventType);
    }

    @Override
    public void registerDerivedTable(DerivedDbTable table) {
        if (alreadyInitialized) {
            throw new IllegalStateException("Too late to register table " + table + ", must have done it in Metro.Init");
        }
        derivedTables.add(table);
    }

    @Override
    public void trimDerivedTables() {
        try {
            Db.db.beginTransaction();
            doTrimDerivedTables();
            Db.db.commitTransaction();
        } catch (Exception e) {
            Logger.logMessage(e.toString(), e);
            Db.db.rollbackTransaction();
            throw e;
        } finally {
            Db.db.endTransaction();
        }
    }

    private void doTrimDerivedTables() {
        lastTrimHeight = blockchain.getGuaranteedBalanceHeight(blockchain.getHeight());
        if (lastTrimHeight > 0) {
            for (DerivedDbTable table : derivedTables) {
                blockchain.readLock();
                try {
                    table.trim(lastTrimHeight);
                    Db.db.commitTransaction();
                } finally {
                    blockchain.readUnlock();
                }
            }
        }
    }

    List<DerivedDbTable> getDerivedTables() {
        return derivedTables;
    }

    @Override
    public Peer getLastBlockchainFeeder() {
        return lastBlockchainFeeder;
    }

    @Override
    public int getLastBlockchainFeederHeight() {
        return lastBlockchainFeederHeight;
    }

    @Override
    public boolean isScanning() {
        return isScanning;
    }

    @Override
    public int getInitialScanHeight() {
        return initialScanHeight;
    }

    @Override
    public boolean isDownloading() {
        return isDownloading;
    }

    @Override
    public boolean isProcessingBlock() {
        return isProcessingBlock;
    }

    @Override
    public int getLowestPossibleHeightForRollback() {
        return trimDerivedTables ? (lastTrimHeight > 0 ? lastTrimHeight : blockchain.getGuaranteedBalanceHeight(blockchain.getHeight())) : 0;
    }

    @Override
    public long getGenesisBlockId() {
        return genesisBlockId;
    }

    private boolean processKeyBlockInternal(BlockImpl block, Peer peer) throws MetroException {
        BlockImpl lastBlock = blockchain.getLastBlock();
        if (block.getPreviousBlockId() == lastBlock.getId()) {
            pushBlock(block);
            return true;
        }

        BlockImpl lastKeyBlock = blockchain.getLastKeyBlock();
        BlockImpl common = blockchain.getBlock(block.getPreviousBlockId());

        if (common == null) {
            Logger.logInfoMessage("Have not found prev block. Can not add new key block.");
            return false;
        }

        if (lastKeyBlock != null && lastKeyBlock.getHeight() >= common.getHeight()) {
            Logger.logInfoMessage("Can not add key block in prev cluster.");
            return false;
        }

        boolean added = processFork(peer, Collections.singletonList(block), common);
        if (added) {
            try {
                Logger.logWarningMessage("Block " + lastBlock.getStringId() + " at height " + lastBlock.getHeight() +
                        " was replaced by key block " + block.getStringId() + " height " + block.getHeight());
            } catch (IllegalStateException ex) {
                Logger.logWarningMessage("Block " + lastBlock.getStringId() + " was replaced by key block " + block.getStringId());
            }
        }
        return added;
    }

    @Override
    public boolean processKeyBlockFork(BlockImpl block) {
        blockchain.writeLock();
        try {
            List<BlockImpl> tip = TipCache.instance.get(block.getPreviousBlockId());
            tip.add(block);
            BlockImpl common = blockchain.getBlock(tip.get(0).getPreviousBlockId());
            boolean added = processFork(null, tip, common);
            if (added) {
                try {
                    Logger.logWarningMessage("Some blocks replaced by key block " + block.getStringId() + " height " + block.getHeight());
                } catch (IllegalStateException ex) {
                    Logger.logWarningMessage("Some blocks replaced by key block " + block.getStringId());
                }
            }
            return added;
        } finally {
            blockchain.writeUnlock();
        }
    }

    @Override
    public boolean processMyKeyBlock(Block blk) throws MetroException {
        if (!(blk instanceof BlockImpl)) {
            throw new BlockNotAcceptedException("Unknown block class " + blk.getClass().getName() + ", should not happen", new BlockImpl(new byte[0], null));
        }
        BlockImpl block = (BlockImpl)blk;

        blockchain.writeLock();
        try {
            return processKeyBlockInternal(block, null);
        } finally {
            blockchain.writeUnlock();
        }
    }

    /**
     * The method to process a block submitted by a peer.
     * Key block parsed from JSON lacks generationSequence: we must restore them here from our tip,
     * before passing the new block to pushBlock()
     *
     * @param request
     * @return true if this block was accepted, false if ignored
     * @throws MetroException
     */
    @Override
    public void processPeerBlock(JSONObject request, Peer peer) throws MetroException {
        BlockImpl block = BlockImpl.parseBlock(request, false);
        blockchain.writeLock();
        try {
            if (block.isKeyBlock()) {
                if (block.getGenerationSequence() == null) {
                    // received from JSON, prevBlockId checked, we can restore hash from DB or block cache
                    BlockImpl previousBlock = blockchain.getBlock(block.getPreviousBlockId());
                    if (previousBlock == null) {
                        // block points at inexistent previous block - ignore
                        return;
                    }
                    byte[] generationSequenceHash = BlockImpl.advanceGenerationSequenceInKeyBlock(previousBlock);
                    request.put("generationSequence", Convert.toHexString(generationSequenceHash));
                    block = BlockImpl.parseBlock(request, true);
                }
                processKeyBlockInternal(block, peer);
                return;
            }

            BlockImpl lastBlock = blockchain.getLastBlock();
            final boolean isBlockchainContinuation = block.getPreviousBlockId() == lastBlock.getId();
            final boolean isOneFastBlockReplacement = !lastBlock.isKeyBlock() && block.getPreviousBlockId() == lastBlock.getPreviousBlockId() && block.getTimestamp() < lastBlock.getTimestamp();

            if (isBlockchainContinuation) {
                pushBlock(block);
            } else if (isOneFastBlockReplacement) {
                if (lastBlock.getId() != blockchain.getLastBlock().getId()) {
                    // blockchain changed, ignore the block
                    return;
                }
                BlockImpl previousBlock = blockchain.getBlock(lastBlock.getPreviousBlockId());
                lastBlock = popOffTo(previousBlock).get(0);
                try {
                    pushBlock(block);
                    TransactionProcessorImpl.getInstance().processLater(lastBlock.getTransactions());
                    Logger.logWarningMessage("Last block " + lastBlock.getStringId() + " was replaced by " + block.getStringId());
                } catch (BlockNotAcceptedException e) {
                    Logger.logWarningMessage("Replacement block failed to be accepted, pushing back our last block");
                    pushBlock(lastBlock);
                    TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
                }
            }
            // else ignore the block
        } finally {
            blockchain.writeUnlock();
        }
    }

    @Override
    public List<BlockImpl> popOffTo(int height) {
        if (height <= 0) {
            fullReset();
        } else if (height < blockchain.getHeight()) {
            return popOffTo(blockchain.getBlockAtHeight(height));
        }
        return Collections.emptyList();
    }

    @Override
    public void fullReset() {
        blockchain.writeLock();
        try {
            try {
                setGetMoreBlocks(false);
                //BlockDb.deleteBlock(Genesis.GENESIS_BLOCK_ID); // fails with stack overflow in H2
                BlockDb.deleteAll();
                addGenesisBlock();
                resetForgersMerkle();
            } finally {
                setGetMoreBlocks(true);
            }
        } finally {
            blockchain.writeUnlock();
        }
    }

    @Override
    public void setGetMoreBlocks(boolean getMoreBlocks) {
        this.getMoreBlocks = getMoreBlocks;
    }

    @Override
    public int restorePrunedData() {
        Db.db.beginTransaction();
        try (Connection con = Db.db.getConnection()) {
            long now = Metro.getEpochTime();
            long minTimestamp = Math.max(1, now - Constants.MAX_PRUNABLE_LIFETIME);
            long maxTimestamp = Math.max(minTimestamp, now - Constants.MIN_PRUNABLE_LIFETIME) - 1;
            List<TransactionDb.PrunableTransaction> transactionList =
                    TransactionDb.findPrunableTransactions(con, minTimestamp, maxTimestamp);
            transactionList.forEach(prunableTransaction -> {
                long id = prunableTransaction.getId();
                if ((prunableTransaction.hasPrunableAttachment() && prunableTransaction.getTransactionType().isPruned(id)) ||
                        PrunableMessage.isPruned(id, prunableTransaction.hasPrunablePlainMessage(), prunableTransaction.hasPrunableEncryptedMessage())) {
                    synchronized (prunableTransactions) {
                        prunableTransactions.add(id);
                    }
                }
            });
            if (!prunableTransactions.isEmpty()) {
                lastRestoreTime = 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } finally {
            Db.db.endTransaction();
        }
        synchronized (prunableTransactions) {
            return prunableTransactions.size();
        }
    }

    @Override
    public Transaction restorePrunedTransaction(long transactionId) {
        TransactionImpl transaction = TransactionDb.findTransaction(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found");
        }
        boolean isPruned = false;
        for (Appendix.AbstractAppendix appendage : transaction.getAppendages(true)) {
            if ((appendage instanceof Appendix.Prunable) &&
                    !((Appendix.Prunable)appendage).hasPrunableData()) {
                isPruned = true;
                break;
            }
        }
        if (!isPruned) {
            return transaction;
        }
        List<Peer> peers = Peers.getPeers(chkPeer -> chkPeer.providesService(Peer.Service.PRUNABLE) &&
                !chkPeer.isBlacklisted() && chkPeer.getAnnouncedAddress() != null);
        if (peers.isEmpty()) {
            Logger.logDebugMessage("Cannot find any archive peers");
            return null;
        }
        JSONObject json = new JSONObject();
        JSONArray requestList = new JSONArray();
        requestList.add(Long.toUnsignedString(transactionId));
        json.put("requestType", "getTransactions");
        json.put("transactionIds", requestList);
        JSONStreamAware request = JSON.prepareRequest(json);
        for (Peer peer : peers) {
            if (peer.getState() != Peer.State.CONNECTED) {
                Peers.connectPeer(peer);
            }
            if (peer.getState() != Peer.State.CONNECTED) {
                continue;
            }
            Logger.logDebugMessage("Connected to archive peer " + peer.getHost());
            JSONObject response = peer.send(request);
            if (response == null) {
                continue;
            }
            JSONArray transactions = (JSONArray)response.get("transactions");
            if (transactions == null || transactions.isEmpty()) {
                continue;
            }
            try {
                List<Transaction> processed = Metro.getTransactionProcessor().restorePrunableData(transactions);
                if (processed.isEmpty()) {
                    continue;
                }
                synchronized (prunableTransactions) {
                    prunableTransactions.remove(transactionId);
                }
                return processed.get(0);
            } catch (MetroException.NotValidException e) {
                Logger.logErrorMessage("Peer " + peer.getHost() + " returned invalid prunable transaction", e);
                peer.blacklist(e);
            }
        }
        return null;
    }

    void shutdown() {
        ThreadPool.shutdownExecutor("networkService", networkService, 5);
    }

    private void addBlock(BlockImpl block) {
        try (Connection con = Db.db.getConnection()) {
            BlockDb.saveBlock(con, block);
            blockchain.setLastBlock(block);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    private void addGenesisBlock() {
        BlockImpl lastBlock = BlockDb.findLastBlock();

        if (lastBlock != null) {
            Logger.logMessage("Genesis block already in database");
            blockchain.setLastBlock(lastBlock);
            BlockDb.deleteBlocksFromHeight(lastBlock.getHeight() + 1);
            popOffTo(lastBlock);
            genesisBlockId = BlockDb.findBlockIdAtHeight(0);
            Logger.logMessage("Last block height: " + lastBlock.getHeight());
            return;
        }
        Logger.logMessage("Genesis block not in database, starting from scratch");
        try (Connection con = Db.db.beginTransaction()) {
            BlockImpl genesisBlock = Genesis.newGenesisBlock();
            addBlock(genesisBlock);
            genesisBlockId = genesisBlock.getId();
            Genesis.apply();
            for (DerivedDbTable table : derivedTables) {
                table.createSearchIndex(con);
            }
            BlockDb.commit(genesisBlock);
            Db.db.commitTransaction();
        } catch (SQLException e) {
            Db.db.rollbackTransaction();
            Logger.logMessage(e.getMessage());
            throw new RuntimeException(e.toString(), e);
        } finally {
            Db.db.endTransaction();
        }
    }

    private void pushBlock(final BlockImpl block) throws BlockNotAcceptedException {

        long curTime = Metro.getEpochTime();

        blockchain.writeLock();

        if (block.isKeyBlock() && block.getGenerationSequence() == null &&
                block.getPreviousBlockId() == blockchain.getLastBlock().getId()) {
            BlockImpl previousBlock = blockchain.getLastBlock();
            byte[] generationSequence = BlockImpl.advanceGenerationSequenceInKeyBlock(previousBlock);
            blockchain.writeUnlock();
            pushBlock(new BlockImpl(block, generationSequence));
            return;
        }

        try {
            BlockImpl previousBlock = null;
            try {
                Db.db.beginTransaction();
                previousBlock = blockchain.getLastBlock();

                if (previousBlock == null) {
                    throw new IllegalArgumentException("Should not use pushBlock for genesis block");
                }

                BlockImpl previousKeyBlock = blockchain.getLastKeyBlock();
                BlockImpl previousPosBlock = blockchain.getLastPosBlock();

                if (previousPosBlock.getId() != previousBlock.getId() &&
                        (previousKeyBlock == null || previousKeyBlock.getId() != previousBlock.getId())) {
                    throw new IllegalStateException("Last block should be pos or key block");
                }
                if (previousBlock.getHeight() != Math.max(previousPosBlock.getHeight(), previousKeyBlock != null ? previousKeyBlock.getHeight() : -1)) {
                    // Last key block doesn't correspond to Last block atomic reference, let's reinitialize both from DB to be safe
                    blockchain.forgetLastKeyBlock();
                    blockchain.setLastBlock(BlockDb.findLastBlock());
                    previousBlock = blockchain.getLastBlock();
                    previousKeyBlock = blockchain.getLastKeyBlock();
                    previousPosBlock = blockchain.getLastPosBlock();
                }
                block.setPrevious(previousPosBlock, previousKeyBlock);
                validate(block, previousBlock, previousKeyBlock, curTime);

                long nextHitTime = Generator.getNextHitTime(previousBlock.getId(), curTime);
                if (!block.isKeyBlock() && nextHitTime > 0 && block.getTimestamp() > nextHitTime + 1) {
                    String msg = "Rejecting block " + block.getStringId() + " at height " + previousBlock.getHeight()
                            + " block timestamp " + block.getTimestamp() + " next hit time " + nextHitTime
                            + " current time " + curTime;
                    Logger.logWarningMessage(msg);
                    Generator.setDelay(-Constants.FORGING_SPEEDUP);
                    throw new BlockOutOfOrderException(msg, block);
                }

                Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
                List<TransactionImpl> validPhasedTransactions = new ArrayList<>();
                List<TransactionImpl> invalidPhasedTransactions = new ArrayList<>();
                validatePhasedTransactions(previousBlock.getHeight(), validPhasedTransactions, invalidPhasedTransactions, duplicates);
                validateTransactions(block, previousBlock, curTime, duplicates, previousBlock.getHeight() >= Constants.LAST_CHECKSUM_BLOCK);

                block.setPreceding();
                blockListeners.notify(block, Event.BEFORE_BLOCK_ACCEPT);
                TransactionProcessorImpl.getInstance().requeueAllUnconfirmedTransactions();
                Logger.logInfoMessage("adding/storing/accepting new block=" + Convert.toHexString(block.getBytes()));
                addBlock(block);
                accept(block, validPhasedTransactions, invalidPhasedTransactions, duplicates);
                BlockDb.commit(block);
                Db.db.commitTransaction();
            } catch (Exception e) {
                Db.db.rollbackTransaction();
                popOffTo(previousBlock);
                blockchain.setLastBlock(previousBlock);
                Logger.logErrorMessage("Block have not accepted", e);
                throw e;
            } finally {
                Db.db.endTransaction();
            }
            blockListeners.notify(block, Event.AFTER_BLOCK_ACCEPT);
        } finally {
            blockchain.writeUnlock();
        }

        if (block.getTimestamp() >= curTime - 600000) {
            Peers.sendToSomePeers(block);
        }

        blockListeners.notify(block, Event.BLOCK_PUSHED);

    }

    private void validatePhasedTransactions(int height, List<TransactionImpl> validPhasedTransactions, List<TransactionImpl> invalidPhasedTransactions,
                                            Map<TransactionType, Map<String, Integer>> duplicates) {
        try (DbIterator<TransactionImpl> phasedTransactions = PhasingPoll.getFinishingTransactions(height + 1)) {
            for (TransactionImpl phasedTransaction : phasedTransactions) {
                if (PhasingPoll.getResult(phasedTransaction.getId()) != null) {
                    continue;
                }
                try {
                    phasedTransaction.validate();
                    if (!phasedTransaction.attachmentIsDuplicate(duplicates, false)) {
                        validPhasedTransactions.add(phasedTransaction);
                    } else {
                        Logger.logDebugMessage("At height " + height + " phased transaction " + phasedTransaction.getStringId() + " is duplicate, will not apply");
                        invalidPhasedTransactions.add(phasedTransaction);
                    }
                } catch (MetroException.ValidationException e) {
                    Logger.logDebugMessage("At height " + height + " phased transaction " + phasedTransaction.getStringId() + " no longer passes validation: "
                            + e.getMessage() + ", will not apply");
                    invalidPhasedTransactions.add(phasedTransaction);
                }
            }
        }
    }

    private void validate(BlockImpl block, BlockImpl previousLastBlock, BlockImpl previousLastKeyBlock, long curTime) throws BlockNotAcceptedException {

        boolean keyBlock = block.isKeyBlock();
        if (Consensus.badBlockSet.contains(block.getId())) {
            throw new BlockNotAcceptedException("Forbidden block id", block);
        }
        if (previousLastBlock.getId() != block.getPreviousBlockId()) {
            throw new BlockOutOfOrderException("Previous block id doesn't match", block);
        }
        int keyHeight = previousLastKeyBlock != null ? previousLastKeyBlock.getLocalHeight() + 1 : 0;
        if ((!block.isKeyBlock() && block.getVersion() != getPosBlockVersion(keyHeight))
            || (block.isKeyBlock() && !getPermissibleKeyBlockVersions(keyHeight).contains(block.getVersion()))) {
            throw new BlockNotAcceptedException("Invalid version " + block.getVersion(), block);
        }
        if (block.getTimestamp() > curTime + Constants.MAX_TIMEDRIFT) {
            Logger.logWarningMessage("Received block " + block.getStringId() + " from the future, timestamp " + block.getTimestamp()
                    + " generator " + block.getGeneratorFullId().toString() + " current time " + curTime + ", system clock may be off");
            throw new BlockOutOfOrderException("Invalid timestamp: " + block.getTimestamp()
                    + " current time is " + curTime, block);
        }
        if (block.getTimestamp() <= previousLastBlock.getTimestamp()) {
            throw new BlockNotAcceptedException("Block timestamp " + block.getTimestamp() + " is before previous block timestamp "
                    + previousLastBlock.getTimestamp(), block);
        }
        if (!Arrays.equals(previousLastBlock.getHash(), block.getPreviousBlockHash())) {
            throw new BlockNotAcceptedException("Previous block hash doesn't match", block);
        }
        if (keyBlock) {
            if (previousLastKeyBlock == null) {
                if (block.getPreviousKeyBlockId() != null) {
                    throw new BlockNotAcceptedException("Incorrect previousKeyBlockId in the first ever keyBlock, must be null", block);
                }
            } else {
                if (!block.getPreviousKeyBlockId().equals(previousLastKeyBlock.getId())) {
                    throw new BlockNotAcceptedException("Previous keyBlock hashId doesn't match", block);
                }
            }
            Block.ValidationResult status = validateKeyBlock(block);
            if (status != Block.ValidationResult.OK) {
                throw new BlockNotAcceptedException("Special keyBlock validation failed: " + status, block);
            }
        } else if (block.getNonce() != 0) {
            throw new BlockNotAcceptedException("Non-zero nonce is incompatible with POS block", block);
        }

        if (block.getId() == 0L || BlockDb.hasBlock(block.getId(), previousLastBlock.getHeight())) {
            throw new BlockNotAcceptedException("Duplicate block or invalid id", block);
        }
        if (!keyBlock && block.getTransactions().size() > Consensus.POSBLOCK_MAX_NUMBER_OF_TRANSACTIONS) {
            throw new BlockNotAcceptedException("Invalid block transaction count " + block.getTransactions().size(), block);
        }
        if (block.getPayloadLength() < 0) {
            throw new BlockNotAcceptedException("Invalid block payload length " + block.getPayloadLength(), block);
        }
        if (keyBlock) {
            if (block.getPayloadLength() > Consensus.KEYBLOCK_MAX_PAYLOAD_LENGTH) {
                throw new BlockNotAcceptedException("Invalid block payload length " + block.getPayloadLength(), block);
            }
        } else {
            if (block.getPayloadLength() > Consensus.POSBLOCK_MAX_PAYLOAD_LENGTH) {
                throw new BlockNotAcceptedException("Invalid block payload length " + block.getPayloadLength(), block);
            }
        }
        if (!block.verifyGenerationSequence() && !Generator.allowsFakeForging(block.getGeneratorPublicKey())) {
            Account generatorAccount = Account.getAccount(block.getGeneratorFullId());
            long generatorBalance = generatorAccount == null ? 0 : generatorAccount.getEffectiveBalanceMTR();
            throw new BlockNotAcceptedException("Generation sequence verification failed, effective balance " + generatorBalance, block);
        }
        if (!block.verifyBlockSignature()) {
            throw new BlockNotAcceptedException("Block signature verification failed", block);
        }
    }

    private Block.ValidationResult validateKeyBlock(Block keyBlock) {
        if (keyBlock.getLocalHeight() >= Consensus.FORGERS_FIXATION_BLOCK && !Arrays.equals(keyBlock.getForgersMerkleBranches(), lastKeyBlockForgersMerkleBranches)) {
            return Block.ValidationResult.FORGERS_MERKLE_ROOT_DISCREPANCY;
        }

        BigInteger target = keyBlock.getDifficultyTargetAsInteger();
        if (target.signum() <= 0 || target.compareTo(Consensus.MAX_WORK_TARGET) > 0) {
            return Block.ValidationResult.DIFFICULTY_TARGET_OUT_OF_RANGE;
        }
        byte[] hashBytes = HASH_FUNCTION.hash(keyBlock.getBytes());
        ArrayUtils.reverse(hashBytes);
        BigInteger hash = new BigInteger(1, hashBytes);
        if (hash.compareTo(target) > 0) {
            return Block.ValidationResult.INSUFFICIENT_WORK;
        }
        try {
            if (keyBlock.getBaseTarget() != BitcoinJUtils.encodeCompactBits(Metro.getBlockchain().getTargetAtLocalHeight(keyBlock.getLocalHeight()))) {
                return Block.ValidationResult.INCORRECT_DIFFICULTY;
            }
        } catch (IllegalArgumentException ex) {
            return Block.ValidationResult.UNKNOWN_ERROR;
        }
        return Block.ValidationResult.OK;
    }


    private void validateCoinbaseTx(TransactionImpl tx, Block block) throws TransactionNotAcceptedException {
        if (!tx.getType().isCoinbase()) {
            throw new TransactionNotAcceptedException("First tx should be coinbase", tx);
        }
        if (!tx.getSenderFullId().equals(block.getGeneratorFullId()) || tx.getSenderId() != tx.getRecipientId()) {
            throw new TransactionNotAcceptedException("Coinbase sender and recipient should be equal to block generator", tx);
        }

        Map<Account.FullId, Long> recipients = coinbaseRecipients(block.getGeneratorPublicKey(), block.getTransactions(), block.isKeyBlock(), block.getLocalHeight());
        Attachment.CoinbaseRecipientsAttachment attachment = (Attachment.CoinbaseRecipientsAttachment)tx.getAttachment();
        if (attachment.isHaveNonce() && block.getVersion() < STRATUM_COMPATIBILITY_BLOCK) {
            throw new TransactionNotAcceptedException("Coinbase can not have nounce before key block " + Consensus.SOFT_FORK_1, tx);
        }
        for (Account.FullId recipient: attachment.getRecipients().keySet()) {
            if (!recipients.containsKey(recipient)) {
                throw new TransactionNotAcceptedException("Coinbase recipient " + recipient + " is absent.", tx);
            }
            if (!recipients.get(recipient).equals(attachment.getRecipients().get(recipient))) {
                throw new TransactionNotAcceptedException("Coinbase amount is" + attachment.getRecipients().get(recipient) +
                        " instead of " + recipients.get(recipient) + " for recipient " + recipient, tx);
            }
            recipients.remove(recipient);
        }
        if (recipients.keySet().size() > 0) {
            throw new TransactionNotAcceptedException("Coinbase extra recipient " + recipients.keySet().iterator().next(), tx);
        }
    }

    private void validateTransactions(BlockImpl block, BlockImpl previousLastBlock, long curTime, Map<TransactionType, Map<String, Integer>> duplicates,
                                      boolean fullValidation) throws BlockNotAcceptedException {
        long payloadLength = 0;
        long calculatedTotalAmount = 0;
        long calculatedReward = 0;
        boolean hasPrunedTransactions = false, isKeyBlock = block.isKeyBlock();
        byte[] txMerkleRoot = Convert.EMPTY_HASH;
        if (block.getTransactions().size() > 0) {

            List<byte[]> txids = new ArrayList<>();
            for (int i = 0; i < block.getTransactions().size(); i++) {
                TransactionImpl transaction = block.getTransactions().get(i);
                txids.add(transaction.fullHash());
                if (transaction.getType().isCoinbase() && i > 0) {
                    throw new TransactionNotAcceptedException("Coinbase should be only first transaction in block", transaction);
                }

                if (transaction.getTimestamp() > curTime + Constants.MAX_TIMEDRIFT) {
                    throw new BlockOutOfOrderException("Invalid transaction timestamp: " + transaction.getTimestamp()
                            + ", current time is " + curTime, block);
                }
                if (!transaction.verifySignature()) {
                    throw new TransactionNotAcceptedException("Transaction signature verification failed at height " + previousLastBlock.getHeight(), transaction);
                }
                if (fullValidation) {
                    if (transaction.getTimestamp() > block.getTimestamp() + Constants.MAX_TIMEDRIFT
                            || transaction.getExpiration() < block.getTimestamp()) {
                        throw new TransactionNotAcceptedException("Invalid transaction timestamp " + transaction.getTimestamp()
                                + ", current time is " + curTime + ", block timestamp is " + block.getTimestamp(), transaction);
                    }
                    if (TransactionDb.hasTransaction(transaction.getId(), previousLastBlock.getHeight())) {
                        throw new TransactionNotAcceptedException("Transaction is already in the blockchain", transaction);
                    }
                    if (transaction.referencedTransactionFullHash() != null && !hasAllReferencedTransactions(transaction, transaction.getTimestamp(), 0)) {
                        throw new TransactionNotAcceptedException("Missing or invalid referenced transaction "
                                + transaction.getReferencedTransactionFullHash(), transaction);
                    }
                    if (transaction.getVersion() != getTransactionVersion(previousLastBlock.getHeight())) {
                        throw new TransactionNotAcceptedException("Invalid transaction version " + transaction.getVersion()
                                + " at height " + previousLastBlock.getHeight(), transaction);
                    }
                    if (transaction.getId() == 0L) {
                        throw new TransactionNotAcceptedException("Invalid transaction id 0", transaction);
                    }
                    try {
                        transaction.validate();
                    } catch (MetroException.ValidationException e) {
                        throw new TransactionNotAcceptedException(e.getMessage(), transaction);
                    }
                }
                if (transaction.attachmentIsDuplicate(duplicates, true)) {
                    throw new TransactionNotAcceptedException("Transaction is a duplicate", transaction);
                }
                if (!hasPrunedTransactions) {
                    for (Appendix.AbstractAppendix appendage : transaction.getAppendages()) {
                        if ((appendage instanceof Appendix.Prunable) && !((Appendix.Prunable) appendage).hasPrunableData()) {
                            hasPrunedTransactions = true;
                            break;
                        }
                    }
                }
                if (!isKeyBlock) {
                    calculatedTotalAmount += transaction.getAmountMQT();
                    payloadLength += transaction.getFullSize();
                }
                calculatedReward += transaction.getFeeMQT();
            }
            List<byte[]> tree = BitcoinJUtils.buildMerkleTree(txids);
            txMerkleRoot = tree.get(tree.size()-1);
            validateCoinbaseTx(block.getTransactions().get(0), block);
        }
        if ((calculatedReward + (isKeyBlock ? Consensus.getBlockSubsidy(block.getLocalHeight()): 0)) != block.getRewardMQT()) {
            throw new BlockNotAcceptedException("Reward doesn't match transaction totals", block);
        }
        if (!isKeyBlock) {
            if (calculatedTotalAmount != block.getTotalAmountMQT()) {
                throw new BlockNotAcceptedException("Total amount doesn't match transaction totals", block);
            }
            if (!Arrays.equals(txMerkleRoot, block.getTxMerkleRoot())) {
                throw new BlockNotAcceptedException("Tx Merkle root doesn't match block transactions", block);
            }
            if (hasPrunedTransactions ? payloadLength > block.getPayloadLength() : payloadLength != block.getPayloadLength()) {
                throw new BlockNotAcceptedException("Transaction payload length " + payloadLength + " does not match block payload length "
                        + block.getPayloadLength(), block);
            }
        }
    }

    private void accept(BlockImpl block, List<TransactionImpl> validPhasedTransactions, List<TransactionImpl> invalidPhasedTransactions,
                        Map<TransactionType, Map<String, Integer>> duplicates) throws TransactionNotAcceptedException {
        try {
            isProcessingBlock = true;
            for (TransactionImpl transaction : block.getTransactions()) {
                if (! transaction.applyUnconfirmed()) {
                    throw new TransactionNotAcceptedException("Double spending", transaction);
                }
            }
            blockListeners.notify(block, Event.BEFORE_BLOCK_APPLY);
            block.apply();
            validPhasedTransactions.forEach(transaction -> transaction.getPhasing().countVotes(transaction));
            invalidPhasedTransactions.forEach(transaction -> transaction.getPhasing().reject(transaction));
            long fromTimestamp = Metro.getEpochTime() - Constants.MAX_PRUNABLE_LIFETIME;
            for (TransactionImpl transaction : block.getTransactions()) {
                try {
                    transaction.apply();
                    if (transaction.getTimestamp() > fromTimestamp) {
                        for (Appendix.AbstractAppendix appendage : transaction.getAppendages(true)) {
                            if ((appendage instanceof Appendix.Prunable) &&
                                        !((Appendix.Prunable)appendage).hasPrunableData()) {
                                synchronized (prunableTransactions) {
                                    prunableTransactions.add(transaction.getId());
                                }
                                lastRestoreTime = 0;
                                break;
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    Logger.logErrorMessage(e.toString(), e);
                    throw new BlockchainProcessor.TransactionNotAcceptedException(e, transaction);
                }
            }
            SortedSet<TransactionImpl> possiblyApprovedTransactions = new TreeSet<>(finishingTransactionsComparator);
            block.getTransactions().forEach(transaction -> {
                PhasingPoll.getLinkedPhasedTransactions(transaction.fullHash()).forEach(phasedTransaction -> {
                    if (phasedTransaction.getPhasing().getFinishHeight() > block.getHeight()) {
                        possiblyApprovedTransactions.add((TransactionImpl)phasedTransaction);
                    }
                });
                if (transaction.getType() == TransactionType.Messaging.PHASING_VOTE_CASTING && !transaction.attachmentIsPhased()) {
                    Attachment.MessagingPhasingVoteCasting voteCasting = (Attachment.MessagingPhasingVoteCasting)transaction.getAttachment();
                    voteCasting.getTransactionFullHashes().forEach(hash -> {
                        PhasingPoll phasingPoll = PhasingPoll.getPoll(Convert.fullHashToId(hash));
                        if (phasingPoll.allowEarlyFinish() && phasingPoll.getFinishHeight() > block.getHeight()) {
                            possiblyApprovedTransactions.add(TransactionDb.findTransaction(phasingPoll.getId()));
                        }
                    });
                }
            });
            validPhasedTransactions.forEach(phasedTransaction -> {
                if (phasedTransaction.getType() == TransactionType.Messaging.PHASING_VOTE_CASTING) {
                    PhasingPoll.PhasingPollResult result = PhasingPoll.getResult(phasedTransaction.getId());
                    if (result != null && result.isApproved()) {
                        Attachment.MessagingPhasingVoteCasting phasingVoteCasting = (Attachment.MessagingPhasingVoteCasting) phasedTransaction.getAttachment();
                        phasingVoteCasting.getTransactionFullHashes().forEach(hash -> {
                            PhasingPoll phasingPoll = PhasingPoll.getPoll(Convert.fullHashToId(hash));
                            if (phasingPoll.allowEarlyFinish() && phasingPoll.getFinishHeight() > block.getHeight()) {
                                possiblyApprovedTransactions.add(TransactionDb.findTransaction(phasingPoll.getId()));
                            }
                        });
                    }
                }
            });
            possiblyApprovedTransactions.forEach(transaction -> {
                if (PhasingPoll.getResult(transaction.getId()) == null) {
                    try {
                        transaction.validate();
                        transaction.getPhasing().tryCountVotes(transaction, duplicates);
                    } catch (MetroException.ValidationException e) {
                        Logger.logDebugMessage("At height " + block.getHeight() + " phased transaction " + transaction.getStringId()
                                + " no longer passes validation: " + e.getMessage() + ", cannot finish early");
                    }
                }
            });
            blockListeners.notify(block, Event.AFTER_BLOCK_APPLY);
            if (block.getTransactions().size() > 0) {
                TransactionProcessorImpl.getInstance().notifyListeners(block.getTransactions(), TransactionProcessor.Event.ADDED_CONFIRMED_TRANSACTIONS);
            }
            AccountLedger.commitEntries();
            // Account records are all updated by now, so we can recalculate the forgersMerkle here
            if (block.isKeyBlock()) {
                Logger.logInfoMessage(String.format("LastKeyBlockForgersMerkleBranches changed from %s to %s",
                        Convert.toHexString(lastKeyBlockForgersMerkleBranches), Convert.toHexString(getCurrentForgersMerkleBranches())));
                lastKeyBlockForgersMerkleBranches = getCurrentForgersMerkleBranches();
            }
        } finally {
            isProcessingBlock = false;
            AccountLedger.clearEntries();
        }
    }

    private static final Comparator<Transaction> finishingTransactionsComparator = Comparator
            .comparingInt(Transaction::getHeight)
            .thenComparingInt(Transaction::getIndex)
            .thenComparingLong(Transaction::getId);

    List<BlockImpl> popOffTo(Block commonBlock) {
        blockchain.writeLock();
        try {
            if (!Db.db.isInTransaction()) {
                try {
                    Db.db.beginTransaction();
                    return popOffTo(commonBlock);
                } finally {
                    Db.db.endTransaction();
                }
            }
            if (commonBlock.getHeight() < getLowestPossibleHeightForRollback()) {
                Logger.logMessage("Rollback to height " + commonBlock.getHeight() + " not supported, will do a full rescan");
                popOffWithRescan(commonBlock.getHeight() + 1);
                return Collections.emptyList();
            }
            if (! blockchain.hasBlock(commonBlock.getId())) {
                Logger.logDebugMessage("Block " + commonBlock.getStringId() + " not found in blockchain, nothing to pop off");
                return Collections.emptyList();
            }
            List<BlockImpl> poppedOffBlocks = new ArrayList<>();
            try {
                BlockImpl block = blockchain.getLastBlock();
                block.loadTransactions();
                Logger.logWarningMessage("Rollback from block " + block.getStringId() + " at height " + block.getHeight()
                        + " to " + commonBlock.getStringId() + " at " + commonBlock.getHeight());
                while (block.getId() != commonBlock.getId() && block.getHeight() > 0) {
                    poppedOffBlocks.add(block);
                    block = popLastBlock();
                }
                for (DerivedDbTable table : derivedTables) {
                    table.rollback(commonBlock.getHeight());
                }
                Db.db.clearCache();
                Db.db.commitTransaction();
            } catch (RuntimeException e) {
                Logger.logErrorMessage("Error popping off to " + commonBlock.getHeight() + ", " + e.toString());
                Db.db.rollbackTransaction();
                BlockImpl lastBlock = BlockDb.findLastBlock();
                blockchain.setLastBlock(lastBlock);
                popOffTo(lastBlock);
                throw e;
            }
            return poppedOffBlocks;
        } finally {
            blockchain.writeUnlock();
        }
    }

    private BlockImpl popLastBlock() {
        blockchain.forgetLastKeyBlock();
        BlockImpl block = blockchain.getLastBlock();
        if (block.getHeight() == 0) {
            throw new RuntimeException("Cannot pop off genesis block");
        }
        BlockImpl previousBlock = BlockDb.deleteBlocksFrom(block.getId());
        previousBlock.loadTransactions();
        blockchain.setLastBlock(previousBlock);
        if (block.isKeyBlock()) {
            Logger.logInfoMessage(String.format("LastKeyBlockForgersMerkleBranches changed from %s to %s",
                    Convert.toHexString(lastKeyBlockForgersMerkleBranches), Convert.toHexString(block.getForgersMerkleBranches())));
            lastKeyBlockForgersMerkleBranches = block.getForgersMerkleBranches();
        }
        blockListeners.notify(block, Event.BLOCK_POPPED);
        return previousBlock;
    }

    private void popOffWithRescan(int height) {
        blockchain.writeLock();
        try {
            try {
                scheduleScan(0, false);
                BlockImpl lastBlock = BlockDb.deleteBlocksFrom(BlockDb.findBlockIdAtHeight(height));
                blockchain.setLastBlock(lastBlock);
                Logger.logDebugMessage("Deleted blocks starting from height %s", height);
            } finally {
                scan(0, false);
            }
        } finally {
            blockchain.writeUnlock();
        }
    }

    private void resetForgersMerkle() {
        Logger.logInfoMessage(String.format("LastKeyBlockForgersMerkleBranches changed from %s to %s",
                Convert.toHexString(lastKeyBlockForgersMerkleBranches), Convert.toHexString(Constants.TWO_BRANCHES_EMPTY_MERKLE_ROOT)));
        lastKeyBlockForgersMerkleBranches = Constants.TWO_BRANCHES_EMPTY_MERKLE_ROOT;
    }

    private boolean verifyChecksum(byte[] validChecksum, int fromHeight, int toHeight) {
        MessageDigest digest = Crypto.sha256();
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement(
                     "SELECT * FROM transaction WHERE height > ? AND height <= ? ORDER BY id ASC, timestamp ASC")) {
            pstmt.setInt(1, fromHeight);
            pstmt.setInt(2, toHeight);
            try (DbIterator<TransactionImpl> iterator = blockchain.getTransactions(con, pstmt)) {
                while (iterator.hasNext()) {
                    digest.update(iterator.next().bytes());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        byte[] checksum = digest.digest();
        if (validChecksum == null) {
            Logger.logMessage("Checksum calculated:\n" + Arrays.toString(checksum));
            return true;
        } else if (!Arrays.equals(checksum, validChecksum)) {
            Logger.logErrorMessage("Checksum failed at block " + blockchain.getHeight() + ": " + Arrays.toString(checksum));
            return false;
        } else {
            Logger.logMessage("Checksum passed at block " + blockchain.getHeight());
            return true;
        }
    }

    SortedSet<UnconfirmedTransaction> selectUnconfirmedTransactions(Map<TransactionType, Map<String, Integer>> duplicates, Block previousBlock, long blockTimestamp) {
        List<UnconfirmedTransaction> orderedUnconfirmedTransactions = new ArrayList<>();
        try (FilteringIterator<UnconfirmedTransaction> unconfirmedTransactions = new FilteringIterator<>(
                TransactionProcessorImpl.getInstance().getAllPoSUnconfirmedTransactions(),
                transaction -> hasAllReferencedTransactions(transaction.getTransaction(), transaction.getTimestamp(), 0))) {
            for (UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
                orderedUnconfirmedTransactions.add(unconfirmedTransaction);
            }
        }
        SortedSet<UnconfirmedTransaction> sortedTransactions = new TreeSet<>(transactionArrivalComparator);
        int payloadLength = 0;
        while (payloadLength <= Consensus.POSBLOCK_MAX_PAYLOAD_LENGTH && sortedTransactions.size() <= Consensus.POSBLOCK_MAX_NUMBER_OF_TRANSACTIONS) {
            int prevNumberOfNewTransactions = sortedTransactions.size();
            for (UnconfirmedTransaction unconfirmedTransaction : orderedUnconfirmedTransactions) {
                int transactionLength = unconfirmedTransaction.getTransaction().getFullSize();
                if (sortedTransactions.contains(unconfirmedTransaction) || payloadLength + transactionLength > Consensus.POSBLOCK_MAX_PAYLOAD_LENGTH) {
                    continue;
                }
                if (unconfirmedTransaction.getVersion() != getTransactionVersion(previousBlock.getHeight())) {
                    continue;
                }
                if (blockTimestamp > 0 && (unconfirmedTransaction.getTimestamp() > blockTimestamp + Constants.MAX_TIMEDRIFT
                        || unconfirmedTransaction.getExpiration() < blockTimestamp)) {
                    continue;
                }
                try {
                    unconfirmedTransaction.getTransaction().validate();
                } catch (MetroException.ValidationException e) {
                    continue;
                }
                if (unconfirmedTransaction.getTransaction().attachmentIsDuplicate(duplicates, true)) {
                    continue;
                }
                sortedTransactions.add(unconfirmedTransaction);
                payloadLength += transactionLength;
            }
            if (sortedTransactions.size() == prevNumberOfNewTransactions) {
                break;
            }
        }
        return sortedTransactions;
    }


    private Map<Account.FullId, Long> coinbaseRecipients(byte[] publicKey, List<? extends Transaction> blockTransactions,
                                               boolean isKeyBlock, int localHeight) {
        Map<Account.FullId, Long> recipients = new HashMap<>();
        long totalReward = isKeyBlock ? Consensus.getBlockSubsidy(localHeight) : 0;
        long totalBackFees = 0;
        long[] backFees = new long[3];
        boolean oneRecipient = true;
        for (Transaction transaction : blockTransactions) {
            if (transaction == null) {
                continue;
            }
            totalReward += transaction.getFeeMQT();
            if (!isKeyBlock) {
                if (localHeight > 3) {
                    long[] fees = transaction.getBackFees();
                    oneRecipient &= (fees.length == 0);
                    for (int i = 0; i < fees.length; i++) {
                        backFees[i] += fees[i];
                    }
                }
            }
            if (totalBackFees != 0) {
                Logger.logDebugMessage("Fee reduced by %f %s at POS height %d", ((double) totalBackFees) / Constants.ONE_MTR, Constants.COIN_SYMBOL, localHeight);
            }
        }
        if (!oneRecipient) {
            for (int i = 0; i < backFees.length; i++) {
                if (backFees[i] == 0) {
                    break;
                }
                totalBackFees += backFees[i];
                Account.FullId previousGenerator = BlockDb.findBlockAtLocalHeight(localHeight - i - 1, false).getGeneratorFullId();
                Logger.logDebugMessage("Back fees %f %s to forger at POS height %d", ((double) backFees[i]) / Constants.ONE_MTR, Constants.COIN_SYMBOL, localHeight - i - 1);
                long reward = backFees[i];
                if (recipients.containsKey(previousGenerator)) {
                    reward += recipients.get(previousGenerator);
                }
                recipients.put(previousGenerator, reward);
                //previousGeneratorAccount.addToForgedBalanceMQT(backFees[i]);
            }
        }
        Account.FullId generator = Account.FullId.fromPublicKey(publicKey);
        recipients.put(generator, totalReward + (recipients.containsKey(generator) ? recipients.get(generator) : 0) - totalBackFees);
        return recipients;
    }



    private TransactionImpl buildCoinbase(byte[] publicKey, long timestamp, List<TransactionImpl> blockTransactions,
                                          boolean isKeyBlock, int localHeight, Long nonce) {
        byte[] publicKeyHash = Crypto.sha256().digest(publicKey);
        Account.FullId generatorId = Account.FullId.fromFullHash(publicKeyHash);
        short COINBASE_DEADLINE = 1;
        Map<Account.FullId, Long> recipients = coinbaseRecipients(publicKey, blockTransactions, isKeyBlock, localHeight);
        Transaction.Builder builder = Metro.newTransactionBuilder(publicKey, 0, 0L, COINBASE_DEADLINE, new Attachment.CoinbaseRecipientsAttachment(recipients, nonce));
        builder.timestamp(timestamp);
        builder.recipientFullId(generatorId);
        try {
            return (TransactionImpl)builder.build();
        } catch (MetroException.NotValidException e) {
            throw new RuntimeException("Generated coinbase transaction not valid");
        }
    }


    private static final Comparator<UnconfirmedTransaction> transactionArrivalComparator = Comparator
            .comparingLong(UnconfirmedTransaction::getArrivalTimestamp)
            .thenComparingInt(UnconfirmedTransaction::getHeight)
            .thenComparingLong(UnconfirmedTransaction::getId);

    void generateBlock(String secretPhrase, long blockTimestamp) throws BlockNotAcceptedException {

        Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
        try (DbIterator<TransactionImpl> phasedTransactions = PhasingPoll.getFinishingTransactions(blockchain.getHeight() + 1)) {
            for (TransactionImpl phasedTransaction : phasedTransactions) {
                try {
                    phasedTransaction.validate();
                    phasedTransaction.attachmentIsDuplicate(duplicates, false); // pre-populate duplicates map
                } catch (MetroException.ValidationException ignore) {
                }
            }
        }

        BlockImpl previousBlock = blockchain.getLastBlock();
        BlockImpl prevPosBlock = blockchain.getLastPosBlock();
        TransactionProcessorImpl.getInstance().processWaitingTransactions();
        SortedSet<UnconfirmedTransaction> sortedTransactions = selectUnconfirmedTransactions(duplicates, previousBlock, blockTimestamp);
        List<TransactionImpl> blockTransactions = new ArrayList<>();
        int payloadLength = 0;
        final byte[] publicKey = Crypto.getPublicKey(secretPhrase);

        byte[] txMerkleRoot = Convert.EMPTY_HASH;

        if (sortedTransactions.size() > 0) {
            blockTransactions.add(null);
            for (UnconfirmedTransaction unconfirmedTransaction : sortedTransactions) {
                TransactionImpl transaction = unconfirmedTransaction.getTransaction();
                blockTransactions.add(transaction);
                payloadLength += transaction.getFullSize();
            }
            TransactionImpl coinbase = buildCoinbase(publicKey, blockTimestamp, blockTransactions, false, prevPosBlock.getLocalHeight() + 1, null);
            blockTransactions.set(0, coinbase);
            payloadLength += coinbase.getFullSize();
            List<byte[]> txids = new ArrayList<>();
            for (TransactionImpl transaction : blockTransactions) {
                txids.add(transaction.fullHash());
            }
            List<byte[]> tree = BitcoinJUtils.buildMerkleTree(txids);
            txMerkleRoot = tree.get(tree.size()-1);
        }

        final byte[] generationSequence = Convert.generationSequence(previousBlock.getGenerationSequence(), publicKey);
        final byte[] previousBlockHash = previousBlock.getHash();

        BlockImpl block = new BlockImpl(getPosBlockVersion(previousBlock.getHeight()), blockTimestamp, previousBlock.getId(), null, 0, payloadLength,
                txMerkleRoot, publicKey, generationSequence, previousBlockHash, null, blockTransactions, secretPhrase);

        try {
            pushBlock(block);
            blockListeners.notify(block, Event.BLOCK_GENERATED);
            Logger.logDebugMessage("Account " + block.getGeneratorFullId().toString() + " generated block " + block.getStringId()
                    + " at height " + block.getHeight() + " timestamp " + block.getTimestamp() + " fee " + ((float)block.getRewardMQT())/Constants.ONE_MTR);
        } catch (TransactionNotAcceptedException e) {
            Logger.logDebugMessage("Generate block failed: " + e.getMessage());
            TransactionProcessorImpl.getInstance().processWaitingTransactions();
            TransactionImpl transaction = e.getTransaction();
            Logger.logDebugMessage("Removing invalid transaction: " + transaction.getStringId());
            blockchain.writeLock();
            try {
                TransactionProcessorImpl.getInstance().removeUnconfirmedTransaction(transaction);
            } finally {
                blockchain.writeUnlock();
            }
            throw e;
        } catch (BlockNotAcceptedException e) {
            Logger.logWarningMessage("Generate block failed: " + e.getMessage());
            throw e;
        }
    }

    boolean hasAllReferencedTransactions(TransactionImpl transaction, long timestamp, int count) {
        if (transaction.referencedTransactionFullHash() == null) {
            return timestamp - transaction.getTimestamp() < Constants.MAX_REFERENCED_TRANSACTION_TIMESPAN && count < 10;
        }
        TransactionImpl referencedTransaction = TransactionDb.findTransactionByFullHash(transaction.referencedTransactionFullHash());
        return referencedTransaction != null
                && referencedTransaction.getHeight() < transaction.getHeight()
                && hasAllReferencedTransactions(referencedTransaction, timestamp, count + 1);
    }

    void scheduleScan(int height, boolean validate) {
        try (Connection con = Db.db.getConnection();
             PreparedStatement pstmt = con.prepareStatement("UPDATE scan SET rescan = TRUE, height = ?, validate = ?")) {
            pstmt.setInt(1, height);
            pstmt.setBoolean(2, validate);
            pstmt.executeUpdate();
            Logger.logDebugMessage("Scheduled scan starting from height " + height + (validate ? ", with validation" : ""));
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public void scan(int height, boolean validate) {
        scan(height, validate, false);
    }

    @Override
    public void fullScanWithShutdown() {
        scan(0, true, true);
    }

    private void scan(int height, boolean validate, boolean shutdown) {
        blockchain.writeLock();
        try {
            if (!Db.db.isInTransaction()) {
                try {
                    Db.db.beginTransaction();
                    if (validate) {
                        blockListeners.addListener(checksumListener, Event.BLOCK_SCANNED);
                    }
                    scan(height, validate, shutdown);
                    Db.db.commitTransaction();
                } catch (Exception e) {
                    Db.db.rollbackTransaction();
                    throw e;
                } finally {
                    Db.db.endTransaction();
                    blockListeners.removeListener(checksumListener, Event.BLOCK_SCANNED);
                }
                return;
            }
            scheduleScan(height, validate);
            if (height > 0 && height < getLowestPossibleHeightForRollback()) {
                Logger.logMessage("Rollback to height less than " + getLowestPossibleHeightForRollback() + " not supported, will do a full scan");
                height = 0;
            }
            if (height < 0) {
                height = 0;
            }
            Logger.logMessage("Scanning blockchain starting from height " + height + "...");
            boolean doValidate = validate;
            int scanAfter2ndClusterFromHeight = 0;
            if (validate) {
                Logger.logDebugMessage("Also performing block/tx validation...");
                Block lastKeyBlock = BlockDb.findLastKeyBlock(height);
                if (lastKeyBlock == null) {
                    // we are scanning in the 1st cluster, so initial value of forgersMerkle would be 64 zeroes
                    resetForgersMerkle();
                } else {
                    // bug #265 solution: start with validate=false on key block preceding the "scanned area" (outside of it)
                    // accept on that block will update the correct Merkle root in memory
                    doValidate = false;
                    // switch to validate=true once we are in the scanned area - if passed height is at key block, we have to start with previous one, to be ready
                    scanAfter2ndClusterFromHeight = height;
                    if (lastKeyBlock.getHeight() == height) {
                        Block secondLastKeyBlock = BlockDb.findLastKeyBlock(height - 1);
                        if (secondLastKeyBlock != null) {
                            height = secondLastKeyBlock.getHeight();
                        } else {
                            resetForgersMerkle();
                        }
                    } else {
                        height = lastKeyBlock.getHeight();
                    }
                }
            }
            try (Connection con = Db.db.getConnection();
                 PreparedStatement pstmtSelect = con.prepareStatement("SELECT * FROM block WHERE " + (height > 0 ? "height >= ? AND " : "")
                         + " db_id >= ? ORDER BY db_id ASC LIMIT 50000");
                 PreparedStatement pstmtDone = con.prepareStatement("UPDATE scan SET rescan = FALSE, height = 0, validate = FALSE")) {
                isScanning = true;
                initialScanHeight = blockchain.getHeight();
                if (height > blockchain.getHeight() + 1) {
                    Logger.logMessage("Rollback height " + (height - 1) + " exceeds current blockchain height of " + blockchain.getHeight() + ", no scan needed");
                    pstmtDone.executeUpdate();
                    Db.db.commitTransaction();
                    return;
                }
                if (height == 0) {
                    Logger.logDebugMessage("Dropping all full text search indexes");
                    FullTextTrigger.dropAll(con);
                }
                for (DerivedDbTable table : derivedTables) {
                    if (height == 0) {
                        table.truncate();
                    } else {
                        table.rollback(height - 1);
                    }
                }
                Db.db.clearCache();
                Db.db.commitTransaction();
                Logger.logDebugMessage("Rolled back derived tables");
                BlockImpl currentBlock = BlockDb.findBlockAtHeight(height);
                blockListeners.notify(currentBlock, Event.RESCAN_BEGIN);
                long currentBlockId = currentBlock.getId();
                blockchain.forgetLastKeyBlock();
                if (height == 0) {
                    blockchain.setLastBlock(currentBlock); // special case to avoid no last block
                    Genesis.apply();
                } else {
                    blockchain.setLastBlock(BlockDb.findBlockAtHeight(height - 1));
                }

                if (shutdown) {
                    Logger.logMessage("Scan will be performed at next start");
                    new Thread(() -> System.exit(0)).start();
                    return;
                }
                int pstmtSelectIndex = 1;
                if (height > 0) {
                    pstmtSelect.setInt(pstmtSelectIndex++, height);
                }
                long dbId = Long.MIN_VALUE;
                boolean hasMore = true;
                outer:
                while (hasMore) {
                    hasMore = false;
                    pstmtSelect.setLong(pstmtSelectIndex, dbId);
                    try (ResultSet rs = pstmtSelect.executeQuery()) {
                        while (rs.next()) {
                            try {
                                dbId = rs.getLong("db_id");
                                currentBlock = BlockDb.loadBlock(con, rs, true);
                                int curHeight = currentBlock.getHeight();
                                if (scanAfter2ndClusterFromHeight > 0 && curHeight >= scanAfter2ndClusterFromHeight) {
                                    doValidate = true;
                                }
                                if (curHeight > 0) {
                                    currentBlock.loadTransactions();
                                    if (currentBlock.getId() != currentBlockId || curHeight > blockchain.getHeight() + 1) {
                                        throw new MetroException.NotValidException("Database blocks in the wrong order!");
                                    }
                                    Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
                                    List<TransactionImpl> validPhasedTransactions = new ArrayList<>();
                                    List<TransactionImpl> invalidPhasedTransactions = new ArrayList<>();
                                    validatePhasedTransactions(blockchain.getHeight(), validPhasedTransactions, invalidPhasedTransactions, duplicates);
                                    if (doValidate && curHeight > 0) {
                                        long curTime = Metro.getEpochTime();
                                        validate(currentBlock, blockchain.getLastBlock(), blockchain.getLastKeyBlock(), curTime);
                                        byte[] blockBytes = currentBlock.bytes();
                                        JSONObject blockJSON = (JSONObject) JSONValue.parse(currentBlock.getJSONObject().toJSONString());
                                        if (!Arrays.equals(blockBytes, BlockImpl.parseBlock(blockJSON, false).bytes())) {
                                            throw new MetroException.NotValidException("Block JSON cannot be parsed back to the same block");
                                        }
                                        validateTransactions(currentBlock, blockchain.getLastBlock(), curTime, duplicates, true);
                                        for (TransactionImpl transaction : currentBlock.getTransactions()) {
                                            byte[] transactionBytes = transaction.bytes();
                                            if (!Arrays.equals(transactionBytes, TransactionImpl.newTransactionBuilder(transactionBytes).build().bytes())) {
                                                throw new MetroException.NotValidException("Transaction bytes cannot be parsed back to the same transaction: "
                                                        + transaction.getJSONObject().toJSONString());
                                            }
                                            JSONObject transactionJSON = (JSONObject) JSONValue.parse(transaction.getJSONObject().toJSONString());
                                            if (!Arrays.equals(transactionBytes, TransactionImpl.newTransactionBuilder(transactionJSON).build().bytes())) {
                                                throw new MetroException.NotValidException("Transaction JSON cannot be parsed back to the same transaction: "
                                                        + transaction.getJSONObject().toJSONString());
                                            }
                                        }
                                    }
                                    blockListeners.notify(currentBlock, Event.BEFORE_BLOCK_ACCEPT);
                                    blockchain.setLastBlock(currentBlock);
                                    accept(currentBlock, validPhasedTransactions, invalidPhasedTransactions, duplicates);
                                    Db.db.clearCache();
                                    Db.db.commitTransaction();
                                    blockListeners.notify(currentBlock, Event.AFTER_BLOCK_ACCEPT);
                                }
                                currentBlockId = currentBlock.getNextBlockId();
                            } catch (MetroException | RuntimeException e) {
                                Db.db.rollbackTransaction();
                                Logger.logDebugMessage(e.toString(), e);
                                Logger.logDebugMessage("Applying block " + Long.toUnsignedString(currentBlockId) + " at height "
                                        + (currentBlock == null ? 0 : currentBlock.getHeight()) + " failed, deleting from database");
                                BlockImpl lastBlock = BlockDb.deleteBlocksFrom(currentBlockId);
                                blockchain.setLastBlock(lastBlock);
                                popOffTo(lastBlock);
                                break outer;
                            }
                            blockListeners.notify(currentBlock, Event.BLOCK_SCANNED);
                            hasMore = true;
                        }
                        dbId = dbId + 1;
                    }
                }
                if (height == 0) {
                    for (DerivedDbTable table : derivedTables) {
                        table.createSearchIndex(con);
                    }
                }
                pstmtDone.executeUpdate();
                Db.db.commitTransaction();
                blockListeners.notify(currentBlock, Event.RESCAN_END);
                Logger.logMessage("...done at height " + blockchain.getHeight());
                if (height == 0 && validate) {
                    Logger.logMessage("SUCCESSFULLY PERFORMED FULL RESCAN WITH VALIDATION");
                }
                lastRestoreTime = 0;
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            } finally {
                isScanning = false;
            }
        } finally {
            blockchain.writeUnlock();
        }
    }

    public List<TransactionImpl> prepareKeyBlockTransactions(BlockImpl previousBlock) {
        List<TransactionImpl> blockTransactions = new ArrayList<>();
        SortedSet<UnconfirmedTransaction> unconfirmedTransactions = getTransactionsForKeyBlockGeneration(previousBlock);
        if (unconfirmedTransactions.size() > 0) {
            for (UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
                TransactionImpl transaction = unconfirmedTransaction.getTransaction();
                blockTransactions.add(transaction);
            }
        }
        return blockTransactions;
    }

    public BlockImpl prepareKeyBlockTemplate(List<TransactionImpl> transactions) {
        blockchain.readLock();
        try {
            BlockImpl previousBlock = blockchain.getLastBlock();
            BlockImpl previousKeyBlock = blockchain.getLastKeyBlock();
            byte[] previousBlockHash = previousBlock.getHash();
            byte[] generatorPublicKey = Convert.parseHexString(Miner.getPublicKey());
            Long previousKeyBlockId = previousKeyBlock == null ? null : previousKeyBlock.getId();
            long baseTarget = BitcoinJUtils.encodeCompactBits(Metro.getBlockchain().getNextTarget());
            long blockTimestamp = Metro.getEpochTime();
            byte[] forgersMerkle = lastKeyBlockForgersMerkleBranches;
            List<TransactionImpl> blockTransactions = new ArrayList<>();

            int keyHeight = previousKeyBlock != null ? previousKeyBlock.getLocalHeight() + 1 : 0;
            blockTransactions.add(null);
            if (transactions != null) {
                //not include coinbase cause we make it later
                blockTransactions.addAll(transactions.subList(1, transactions.size()));
            } else {
                blockTransactions.addAll(prepareKeyBlockTransactions(previousBlock));
            }
            TransactionImpl coinbase = buildCoinbase(generatorPublicKey, blockTimestamp, blockTransactions, true, keyHeight, null);
            blockTransactions.set(0, coinbase);

            List<byte[]> txids = new ArrayList<>();
            for (TransactionImpl transaction : blockTransactions) {
                txids.add(transaction.fullHash());
            }
            List<byte[]> tree = BitcoinJUtils.buildMerkleTree(txids);
            byte[] txMerkleRoot = tree.get(tree.size() - 1);

            return new BlockImpl(getPreferableKeyBlockVersion(keyHeight), blockTimestamp, baseTarget, previousBlock.getId(), previousKeyBlockId, previousBlock.getHeight() + 1, 0,
                    txMerkleRoot, generatorPublicKey, null, null, previousBlockHash, forgersMerkle, blockTransactions);
        } finally {
            blockchain.readUnlock();
        }
    }

    @Override
    public Block composeKeyBlock(byte[] headerData, List<TransactionImpl> transactions) {
        blockchain.readLock();
        try {
            ByteBuffer header = ByteBuffer.wrap(headerData);
            header.order(ByteOrder.LITTLE_ENDIAN);
            short version = header.getShort();
            if (!BlockImpl.isKeyBlockVersion(version)) {
                throw new IllegalArgumentException("Wrong block version: 0x" + Integer.toUnsignedString(Short.toUnsignedInt(version), 16));
            }
            final int hashSize = Convert.HASH_SIZE;
            long timestamp = 0;
            byte[] txMerkleRoot = new byte[hashSize];
            if (version < Consensus.STRATUM_COMPATIBILITY_BLOCK) {
                timestamp = header.getLong();
                header.get(txMerkleRoot);
            }

            long previousBlockId = header.getLong();
            BlockImpl previousBlock = BlockDb.findBlock(previousBlockId);
            if (previousBlock == null) {
                List<BlockImpl> tip = TipCache.instance.get(previousBlockId);
                BlockImpl commonBlock = null;
                if (tip != null) {
                    long commonId = tip.get(0).getPreviousBlockId();
                    commonBlock = BlockDb.findBlock(commonId);
                }
                if (commonBlock == null) {
                    throw new IllegalArgumentException("Wrong prev block id: " + previousBlockId);
                }
                previousBlock = tip.get(tip.size()-1);
            }
            if (previousBlock.getGenerationSequence() == null) {
                throw new IllegalStateException("Generation sequence is not yet set in block " + previousBlockId + " given as previous");
            }
            byte[] previousBlockHash = HASH_FUNCTION.hash(previousBlock.getBytes());

            byte[] generationSequence = BlockImpl.advanceGenerationSequenceInKeyBlock(previousBlock);

            Long previousKeyBlockId = header.getLong();
            if (previousKeyBlockId != 0) {
                BlockImpl previousKeyBlock = BlockDb.findBlock(previousKeyBlockId);
                if (previousKeyBlock == null) {
                    throw new IllegalArgumentException("Wrong prev key block id: " + previousKeyBlockId);
                }
            } else {
                previousKeyBlockId = null;
            }
            if (version >= Consensus.STRATUM_COMPATIBILITY_BLOCK) {
                header.get(txMerkleRoot);
            }
            byte[] forgersMerkleRoot = new byte[hashSize];
            header.get(forgersMerkleRoot);
            byte[] forgersMerkleBranches = getLastKeyBlockForgersMerkleBranches();
            if (!Arrays.equals(forgersMerkleRoot, HASH_FUNCTION.hash(ArrayUtils.addAll(previousBlockHash, forgersMerkleBranches)))) {
                throw new IllegalArgumentException("Forgers root: " + Convert.toHexString(forgersMerkleRoot) + ", not matching branches: " + Convert.toHexString(forgersMerkleBranches));
            }
            if (version >= Consensus.STRATUM_COMPATIBILITY_BLOCK) {
                timestamp = header.getLong();
            }
            long baseTarget = header.getInt();
            int nonce = header.getInt();

            // constructor will restore generator id from transactions.get(0) - coinbase
            return new BlockImpl(version, timestamp, baseTarget, previousBlockId, previousKeyBlockId, nonce,
                    0, txMerkleRoot, null,
                    generationSequence, null, previousBlockHash, forgersMerkleBranches, transactions);
        } finally {
            blockchain.readUnlock();
        }
    }

    private SortedSet<UnconfirmedTransaction> getTransactionsForKeyBlockGeneration(Block previousBlock) {
        List<UnconfirmedTransaction> orderedUnconfirmedTransactions = new ArrayList<>();
        try (FilteringIterator<UnconfirmedTransaction> unconfirmedTransactions = new FilteringIterator<>(
                TransactionProcessorImpl.getInstance().getAllKeyBlockUnconfirmedTransactions(),
                transaction -> hasAllReferencedTransactions(transaction.getTransaction(), transaction.getTimestamp(), 0))) {
            for (UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
                orderedUnconfirmedTransactions.add(unconfirmedTransaction);
            }
        }
        SortedSet<UnconfirmedTransaction> sortedTransactions = new TreeSet<>(transactionArrivalComparator);
        int payloadLength = 0;
        while (payloadLength <= Consensus.KEYBLOCK_MAX_PAYLOAD_LENGTH) {
            int prevNumberOfNewTransactions = sortedTransactions.size();
            for (UnconfirmedTransaction unconfirmedTransaction : orderedUnconfirmedTransactions) {
                int transactionLength = unconfirmedTransaction.getTransaction().getFullSize();
                if (sortedTransactions.contains(unconfirmedTransaction) || payloadLength + transactionLength > Consensus.KEYBLOCK_MAX_PAYLOAD_LENGTH) {
                    continue;
                }
                if (unconfirmedTransaction.getVersion() != getTransactionVersion(previousBlock.getHeight())) {
                    continue;
                }
                //TODO investigate same logic with timestamp in generateBlock and implement if needed here
//                if (blockTimestamp > 0 && (unconfirmedTransaction.getTimestamp() > blockTimestamp + Constants.MAX_TIMEDRIFT
//                        || unconfirmedTransaction.getExpiration() < blockTimestamp)) {
//                    continue;
//                }
                try {
                    unconfirmedTransaction.getTransaction().validate();
                } catch (MetroException.ValidationException e) {
                    continue;
                }
                sortedTransactions.add(unconfirmedTransaction);
                payloadLength += transactionLength;
            }
            if (sortedTransactions.size() == prevNumberOfNewTransactions) {
                break;
            }
        }
        return sortedTransactions;
    }

    private byte[] getCurrentForgersMerkleBranches() {
        Metro.getBlockchain().readLock();
        try {
            MessageDigest mdg = HASH_FUNCTION.messageDigest();
            byte[] forgersMerkle = new byte[HASH_SIZE * 2];
            byte[] forgersMerkleVotersBranch = new byte[0];
            // TODO #211
            List<byte[]> outfeeders = new ArrayList<>();
            try (Connection con = Db.db.getConnection();
                 PreparedStatement pstmt = con.prepareStatement(SELECT_FORGERS_SQL)) {
                Block lastBlock = Metro.getBlockchain().getLastBlock();
                if (!lastBlock.isKeyBlock()) {
                    throw new IllegalStateException("On fast blocks forgersMerkle is not defined, call me when you are at key block!");
                }
                int height = lastBlock.getHeight();
                pstmt.setInt(1, Metro.getBlockchain().getGuaranteedBalanceHeight(height));
                pstmt.setInt(2, height);
                pstmt.setInt(3, Math.max(0, height - Consensus.FORGER_ACTIVITY_SNAPSHOT_INTERVAL));
                pstmt.setInt(4, height);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int amountMTR = (int)(rs.getLong("effective")/Constants.ONE_MTR);
                        if (amountMTR >= Consensus.MIN_FORKVOTING_AMOUNT_MTR) {
                            byte[] publicKey = rs.getBytes("public_key");
                            mdg.update(forgersMerkleVotersBranch);
                            mdg.update(publicKey);
                            forgersMerkleVotersBranch = mdg.digest(new byte[] {
                                    (byte)(amountMTR >> 24),
                                    (byte)(amountMTR >> 16),
                                    (byte)(amountMTR >> 8),
                                    (byte)amountMTR,
                            });
                            // TODO #211
                            outfeeders.add(publicKey);
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }

            if (forgersMerkleVotersBranch.length > 0) {
                // TODO #211 these should be not just available in effective balance, they need to be frozen!
                List<byte[]> tree = BitcoinJUtils.buildMerkleTree(outfeeders);
                byte[] forgersMerkleOutfeedersBranch = tree.get(tree.size() - 1);
                System.arraycopy(forgersMerkleVotersBranch, 0, forgersMerkle, 0, HASH_SIZE);
                System.arraycopy(forgersMerkleOutfeedersBranch, 0, forgersMerkle, HASH_SIZE, HASH_SIZE);
            }
            return forgersMerkle;
        } finally {
            Metro.getBlockchain().readUnlock();
        }
    }

    /**
     * Temporary, for testing in this git branch only!
     * @return
     */
    public List<Pair<String, Integer>> getCurrentForgers() {
        Metro.getBlockchain().readLock();
        try {
            List<Pair<String, Integer>> generators = new ArrayList<>();
            try (Connection con = Db.db.getConnection();
                 PreparedStatement pstmt = con.prepareStatement(SELECT_FORGERS_SQL)) {
                Block lastBlock = Metro.getBlockchain().getLastBlock();
                if (!lastBlock.isKeyBlock()) {
                    throw new IllegalStateException("On fast blocks forgersMerkle is not defined, call me when you are at key block!");
                }
                int height = lastBlock.getHeight();
                pstmt.setInt(1, Metro.getBlockchain().getGuaranteedBalanceHeight(height));
                pstmt.setInt(2, height);
                pstmt.setInt(3, Math.max(0, height - Consensus.FORGER_ACTIVITY_SNAPSHOT_INTERVAL));
                pstmt.setInt(4, height);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        int amountMTR = (int)(rs.getLong("effective")/Constants.ONE_MTR);
                        if (amountMTR >= Consensus.MIN_FORKVOTING_AMOUNT_MTR) {
                            generators.add(new ImmutablePair<>(Convert.toHexString(rs.getBytes("public_key")), amountMTR));
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
            return generators;
        } finally {
            Metro.getBlockchain().readUnlock();
        }
    }
}
