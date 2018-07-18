package metro;

import metro.crypto.Crypto;
import metro.http.APICall;
import metro.util.Convert;
import metro.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static metro.Consensus.GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS;
import static metro.Consensus.HASH_FUNCTION;

public class BlockTest extends BlockchainTest {

    // bad compressed target example: 2147483647 (Integer.MAX_VALUE)
    private final JSONObject emptyKeyBlockJSON = (JSONObject) JSONValue.parse( "{\n"+
        "\"previousBlock\":\"2886809478417845031\",\n"+
        "\"previousBlockHash\":\"278b53f7ec01102802d483c4bfceb5052775c4eaed15c8b1b9729bea2b804807\",\n"+
        "\"forgersMerkleRoot\":\"00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000\",\n"+
        "\"baseTarget\":520159231\n"+
        "\"txMerkleRoot\":\"cf4427e6395500ac14668dc28c5c0fab3c097cb16c77949cdbb76c968a2de5e5\",\n"+
        "\"transactions\":[{\"senderPublicKey\":\"0b4e505972149e7ceb51309edc76729795cabe1f2cc42d87688138d0966db436\",\n" +
            "\"signature\":null,\"feeMQT\":0,\"type\":4,\"version\":1,\"ecBlockId\":\"2886809478417845031\",\"amountMQT\":0," +
            "\"attachment\":{" +
            "\"recipients\":{\"67526593929050481459625094635\":200000000000}," +
            "\"version.OrdinaryCoinbase\":1}," +
            "\"subtype\":0,\"recipient\":\"67526593929050481459625094635\",\"ecBlockHeight\":0,\"deadline\":1,\"timestamp\":2330920106" +
        "}],\n"+
        "\"version\":-32767,\n"+
        "\"nonce\":120145,\n"+
        "\"timestamp\":2330920108\n"+
    "}");
    private final JSONObject emptyPosBlockJSON = (JSONObject) JSONValue.parse( "{\n"+
        "\"previousBlockHash\":\"109b15b55c004b523c3107acb0eb60adcc0a55d7ecfd730550336fc337761e63\",\n"+
        "\"payloadLength\":0,\n"+
        "\"previousBlock\":\"5929833732538473232\",\n"+
        "\"generatorPublicKey\":\"112e0c5748b5ea610a44a09b1ad0d2bddc945a6ef5edc7551b80576249ba585b\",\n"+
        "\"txMerkleRoot\":\"0000000000000000000000000000000000000000000000000000000000000000\",\n"+
        "\"blockSignature\":\"a0342cf13be868bd5eafb3a68b7c3664e741c24cba26d98d21f45d957a07e30ab7e8511e799f7506c62390c264d0294d7f224a15dca9cbc8fcefff40695dae54\",\n"+
        "\"transactions\":[],\n"+
        "\"version\":3,\n"+
        "\"generationSequence\":\"e102248f2ecbae5f788658efec4a9faddb7b62d97d2cb05e3e2125dc9913055f\",\n"+
        "\"timestamp\":2329622736\n"+
    "}");

    private static Method blockParser;
    @BeforeClass
    public static void init2() throws NoSuchMethodException {
        blockParser = BlockImpl.class.getDeclaredMethod("parseBlock", JSONObject.class, boolean.class);
        blockParser.setAccessible(true);
    }

    private BlockImpl prepareBlock(byte[] prevKeyBlockHash, Tester issuer) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        generateBlock();
        Block posBlock = Metro.getBlockchain().getLastBlock();
        byte[] forgersBranches = Convert.parseHexString("1584abcb68b7beb00d30c4a93270d8607e596da72697bd92f0d7edac79e49fa80000000000000000000000000000000000000000000000000000000000000000");
        byte[] prevBlockHash = HASH_FUNCTION.hash(posBlock.getBytes());
        long prevBlockId = posBlock.getId();
        byte[] pubKey = issuer.getPublicKey();
        byte[] generationSequence = Convert.generationSequence(posBlock.getGenerationSequence(), pubKey);
        if (prevKeyBlockHash != null) {
            Method coinbaseBuilder = blockchainProcessor.getClass().getDeclaredMethod("buildCoinbase", byte[].class, long.class, List.class, boolean.class, int.class);
            coinbaseBuilder.setAccessible(true);

            TransactionImpl coinbase = (TransactionImpl) coinbaseBuilder.invoke(blockchainProcessor, ALICE.getPublicKey(), posBlock.getTimestamp() + 1, Collections.EMPTY_LIST, true, 0);
            return new BlockImpl(Consensus.getKeyBlockVersion(posBlock.getHeight()), Metro.getEpochTime(), 0x9299FF3, prevBlockId, null, 1,
                     0, Convert.EMPTY_HASH, pubKey,
                    generationSequence, null, prevBlockHash, forgersBranches, Collections.singletonList(coinbase));
        } else {
            return new BlockImpl(Consensus.getPosBlockVersion(posBlock.getHeight()), Metro.getEpochTime(), prevBlockId, null, 0,
                     0, Convert.EMPTY_HASH, pubKey,
                    generationSequence, prevBlockHash, forgersBranches, Collections.emptyList(), issuer.getSecretPhrase());
        }
    }

    @Test
    public void testProcessHeader() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        byte[] prevKeyBlockHash = Convert.parseHexString("2e46f6ffdf11ae63645938a59d000e08fdcb307c69727a57efa4b90b556a16ed");

        BlockImpl block0 = prepareBlock(prevKeyBlockHash, ALICE);
        byte[] header = block0.bytes();
        Block block1 = Metro.getBlockchain().composeKeyBlock(header, block0.getTransactions());
        Assert.assertArrayEquals(header, block1.getBytes());
        Assert.assertEquals(block0, block1);
        Assert.assertEquals(0x9299FF3, block1.getBaseTarget());
    }

    @Test
    public void testConvertPosBlockToJSON_andBack() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        BlockImpl block0 = prepareBlock(null, ALICE);
        JSONObject json = block0.getJSONObject();
        BlockImpl block1 = (BlockImpl) blockParser.invoke(block0, (JSONObject)JSONValue.parse(json.toJSONString()), false);
        Assert.assertEquals(block0, block1);
        Assert.assertEquals(0, block1.getBaseTarget());
    }

    @Test
    public void testConvertKeyBlockToJSON_andBack() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        BlockImpl block0 = prepareBlock(Convert.EMPTY_HASH, ALICE);
        block0.sign(ALICE.getSecretPhrase());
        BlockImpl block1 = (BlockImpl) blockParser.invoke(block0, (JSONObject)JSONValue.parse(block0.getJSONObject().toJSONString()), false);
        Assert.assertEquals(block0, block1);
        Assert.assertEquals(block0.getBaseTarget(), block1.getBaseTarget());
        Assert.assertEquals(0x9299FF3, block1.getBaseTarget());
    }

    @Test
    public void testPrepareAndValidateKeyBlockWithoutSolving() throws MetroException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        BlockImpl preparedBlock = Metro.getBlockchainProcessor().prepareKeyBlock(null);
        Assert.assertEquals((short)0x8001, preparedBlock.getVersion());

        // prev block hash must point to Genesis
        BlockImpl genesis = BlockDb.findBlockAtHeight(0);
        Assert.assertEquals(genesis.getId(), preparedBlock.getPreviousBlockId());
        Assert.assertArrayEquals(genesis.getHash(), preparedBlock.getPreviousBlockHash());
        Assert.assertEquals(1, preparedBlock.getTransactions().size());
        Assert.assertTrue(preparedBlock.getTransactions().get(0).getType().isCoinbase());
        Assert.assertArrayEquals(txHashPrivateAccess(preparedBlock.getTransactions().get(0)), preparedBlock.getTxMerkleRoot());
        try {
            Metro.getBlockchainProcessor().processKeyBlock(preparedBlock);
        } catch (BlockchainProcessor.BlockNotAcceptedException e) {
            Assert.assertTrue(e.getMessage().startsWith("Special keyBlock validation failed: INSUFFICIENT_WORK"));
        }
    }

    @Test
    public void testPrepareAndValidateSolvedKeyBlock() throws MetroException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Block minedBlock = mineBlock();
        Assert.assertNotNull("mined block not accepted", minedBlock);
        Assert.assertEquals(1, minedBlock.getTransactions().size());
        Assert.assertTrue(minedBlock.getTransactions().get(0).getType().isCoinbase());
        Assert.assertArrayEquals(new byte[Convert.HASH_SIZE * 2], minedBlock.getForgersMerkleRoot());
        Assert.assertArrayEquals(txHashPrivateAccess(minedBlock.getTransactions().get(0)), minedBlock.getTxMerkleRoot());
        generateBlock();
        for (int i = 0; i < Math.min(GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS, 1); i++) {
            minedBlock = mineBlock();
            Assert.assertNotNull(minedBlock);
        }

        Assert.assertEquals("1584abcb68b7beb00d30c4a93270d8607e596da72697bd92f0d7edac79e49fa80000000000000000000000000000000000000000000000000000000000000000", Convert.toHexString(minedBlock.getForgersMerkleRoot()));
    }

    @Test
    public void testProcessHeaderWrongVersion() {
        BlockImpl preparedBlock = Metro.getBlockchainProcessor().prepareKeyBlock(null);
        ByteBuffer buffer = ByteBuffer.wrap(preparedBlock.bytes());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(0, (short)0x7001);

        try {
            Metro.getBlockchain().composeKeyBlock(buffer.array(), preparedBlock.getTransactions());
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Wrong block version: 0x7001", e.getMessage());
        }
    }

    @Test
    public void testComposeKeyBlockNonExistentPrevBlock() {
        BlockImpl preparedBlock = Metro.getBlockchainProcessor().prepareKeyBlock(null);
        ByteBuffer buffer = ByteBuffer.wrap(preparedBlock.bytes());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(53, 0);

        try {
            Metro.getBlockchain().composeKeyBlock(buffer.array(), preparedBlock.getTransactions());
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Wrong prev block hash: d2b2f2000000008ad54465acf12b9fee64f4e574443fdf531cfd2c8275721f2c", e.getMessage());
        }
    }

    @Test
    public void testKeyblockGenerationSequence() {
        generateBlocks(2);
        Block posBlock = Metro.getBlockchain().getLastBlock();
        BlockImpl preparedBlock = Metro.getBlockchainProcessor().prepareKeyBlock(null);
        Block blockTemplate = Metro.getBlockchain().composeKeyBlock(preparedBlock.bytes(), preparedBlock.getTransactions());
        Assert.assertArrayEquals("generation sequence does not match", Convert.generationSequence(posBlock.getGenerationSequence(), posBlock.getGeneratorPublicKey()), blockTemplate.getGenerationSequence());
    }

    @Test
    public void testECBlocks() throws MetroException {
        JSONObject response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 1000 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlock();
        Block posBlock = Metro.getBlockchain().getLastBlock();
        Block genesis = BlockDb.findBlock(posBlock.getPreviousBlockId());
        Assert.assertEquals((short)0, genesis.getVersion());
        Assert.assertEquals(0, posBlock.getTransactions().get(0).getECBlockHeight());
        Assert.assertEquals(genesis.getId(), posBlock.getTransactions().get(0).getECBlockId());
        Assert.assertEquals(0, posBlock.getTransactions().get(1).getECBlockHeight());
        Assert.assertEquals(genesis.getId(), posBlock.getTransactions().get(1).getECBlockId());
        Block minedBlock = mineBlock(), minedBlock2 = null;
        Assert.assertNotNull("mined block not accepted", minedBlock);
        Assert.assertEquals(1, minedBlock.getTransactions().size());
        Assert.assertTrue(minedBlock.getTransactions().get(0).getType().isCoinbase());
        Assert.assertEquals(0, minedBlock.getTransactions().get(0).getECBlockHeight());
        Assert.assertEquals(genesis.getId(), minedBlock.getTransactions().get(0).getECBlockId());
        Assert.assertEquals(ALICE.getFullId(), Account.getAccount(minedBlock.getGeneratorId()).getFullId());
        response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 1000 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlock();
        posBlock = Metro.getBlockchain().getLastBlock();
        Assert.assertEquals(0, posBlock.getTransactions().get(0).getECBlockHeight());
        Assert.assertEquals(genesis.getId(), posBlock.getTransactions().get(0).getECBlockId());
        Assert.assertEquals(0, posBlock.getTransactions().get(1).getECBlockHeight());
        Assert.assertEquals(genesis.getId(), posBlock.getTransactions().get(1).getECBlockId());
        Block minedBlock1 = mineBlock();
        Assert.assertEquals(0, minedBlock1.getTransactions().get(0).getECBlockHeight());
        Assert.assertEquals(genesis.getId(), minedBlock1.getTransactions().get(0).getECBlockId());
        for (int i = 0; i < GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS - 2; i++) {
            minedBlock2 = mineBlock();
            Assert.assertNotNull(minedBlock);
        }
        Assert.assertEquals(0, minedBlock2.getTransactions().get(0).getECBlockHeight());
        Assert.assertEquals(genesis.getId(), minedBlock2.getTransactions().get(0).getECBlockId());
        response = new APICall.Builder("sendMoney").
                param("secretPhrase", ALICE.getSecretPhrase()).
                param("recipient", BOB.getStrId()).
                param("amountMQT", 1000 * Constants.ONE_MTR).
                param("feeMQT", Constants.ONE_MTR).
                build().invoke();
        Logger.logDebugMessage("sendMoney: " + response);
        generateBlock();
        posBlock = Metro.getBlockchain().getLastBlock();
        Assert.assertEquals(2, posBlock.getTransactions().get(0).getECBlockHeight());
        Assert.assertEquals(minedBlock.getId(), posBlock.getTransactions().get(0).getECBlockId());
        Assert.assertEquals(2, posBlock.getTransactions().get(1).getECBlockHeight());
        Assert.assertEquals(minedBlock.getId(), posBlock.getTransactions().get(1).getECBlockId());
        minedBlock2 = mineBlock();
        Assert.assertEquals(2, minedBlock2.getTransactions().get(0).getECBlockHeight());
        Assert.assertEquals(minedBlock.getId(), minedBlock2.getTransactions().get(0).getECBlockId());
    }

    @Test
    public void testTimeCapsuleEncoding() {
        String timeCapsule = Genesis.TIME_CAPSULE;
        BlockImpl genesis = BlockDb.findBlockAtHeight(0);
        Assert.assertEquals("f88f6af1bd1042152fb70446114813505400948fd441c133a00537c108308c4433a00f5094dc0200000000000000000000000000000000000000000000000000", Convert.toHexString(genesis.getBlockSignature()));
        ByteBuffer buffer = ByteBuffer.wrap(genesis.getBlockSignature());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        Assert.assertEquals(1531805231244546040L, buffer.getLong());
        byte[] encodedCapsule = new byte[64 - 8];
        buffer.get(encodedCapsule);
        Assert.assertEquals(timeCapsule.length(), encodedCapsule[0]);
        Assert.assertTrue(timeCapsule.equalsIgnoreCase(Convert.decodeTimeCapsule(encodedCapsule)));
    }

    /**
     * Execution passes through "Typical constructor called for a block not yet in DB"
     * Some key block fields (previousBlockHash, generationSequence) can only be restored after adding parsed block on top of Blockchain
     * (see BlockTest.testParseFromJSONandPush)
     * as they need previous block, or preceding POS block acc. to feat#239 to calculate them
     *
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    @Test
    public void testConstructionFromJSON() throws InvocationTargetException, IllegalAccessException {
        BlockImpl block1 = (BlockImpl) blockParser.invoke(null, emptyPosBlockJSON, false);
        Assert.assertEquals(3, block1.getVersion());
        Assert.assertEquals(2144570468632264358L, block1.getId());
        Assert.assertEquals(0, block1.getTransactions().size());
        Assert.assertEquals(0, block1.getPayloadLength());
        Assert.assertEquals(5929833732538473232L, block1.getPreviousBlockId());
        Assert.assertArrayEquals(Convert.parseHexString("109b15b55c004b523c3107acb0eb60adcc0a55d7ecfd730550336fc337761e63"), block1.getPreviousBlockHash());
        Assert.assertEquals(5873880488492319831L, block1.getGeneratorId());
        Assert.assertArrayEquals(Convert.parseHexString("112e0c5748b5ea610a44a09b1ad0d2bddc945a6ef5edc7551b80576249ba585b"), block1.getGeneratorPublicKey());
        Assert.assertEquals(0, block1.getBaseTarget());
        Assert.assertArrayEquals(Convert.parseHexString("e102248f2ecbae5f788658efec4a9faddb7b62d97d2cb05e3e2125dc9913055f"), block1.getGenerationSequence());
        Assert.assertArrayEquals(Convert.parseHexString("a0342cf13be868bd5eafb3a68b7c3664e741c24cba26d98d21f45d957a07e30ab7e8511e799f7506c62390c264d0294d7f224a15dca9cbc8fcefff40695dae54"), block1.getBlockSignature());
        Assert.assertEquals(2329622736L, block1.getTimestamp());

        BlockImpl block2 = (BlockImpl) blockParser.invoke(null, emptyKeyBlockJSON, false);
        Assert.assertNull(block2.getBlockSignature());
        // will be restored from Blockchain when peer receives JSON
        Assert.assertNull(block2.getGenerationSequence());

        Assert.assertEquals((short)0x8001, block2.getVersion());
        Assert.assertEquals(7794052885702480634L, block2.getId());
        Assert.assertEquals("2886809478417845031", Long.toUnsignedString(block2.getPreviousBlockId()));
        // This is Bob
        Assert.assertEquals(-1454519625466876437L, block2.getGeneratorId());
        Assert.assertArrayEquals(Convert.parseHexString("0b4e505972149e7ceb51309edc76729795cabe1f2cc42d87688138d0966db436"), block2.getGeneratorPublicKey());
        Assert.assertEquals(520159231, block2.getBaseTarget());
        Assert.assertEquals(120145, block2.getNonce());
        Assert.assertEquals(1, block2.getTransactions().size());
        Transaction coinbase = block2.getTransactions().get(0);
        Assert.assertEquals(-5050349899876126764L, coinbase.getId());
        Assert.assertEquals("d42f7422868de9b94afcbb167b527757dfdfa8a81398694266fd53c64e6cafb6", coinbase.getFullHash());
        Assert.assertArrayEquals(block2.getGeneratorPublicKey(), coinbase.getSenderPublicKey());
        Assert.assertEquals(2886809478417845031L, coinbase.getECBlockId());
        Assert.assertEquals(block2.getGeneratorId(), coinbase.getRecipientId());
        Attachment.CoinbaseRecipientsAttachment attachment = (Attachment.CoinbaseRecipientsAttachment) coinbase.getAttachment();
        Map<Account.FullId, Long> coinbaseRewards = attachment.getRecipients();
        Assert.assertEquals(Account.getAccount(block2.getGeneratorId()).getFullId(), coinbaseRewards.keySet().iterator().next());

        Assert.assertArrayEquals(Convert.parseHexString("cf4427e6395500ac14668dc28c5c0fab3c097cb16c77949cdbb76c968a2de5e5"), block2.getTxMerkleRoot());
        Assert.assertEquals(0, block2.getPayloadLength());
        Assert.assertEquals(2330920108L, block2.getTimestamp());
    }

    /**
     * Emulate receiving a couple of key and one fast block from peer and placing them on our tip.
     * Key block fields previousBlockHash and generationSequence are restored now, inside BlockchainProcessorImpl.processPeerBlock
     * (as they need previous block, or preceding POS block acc. to feat#239 to calculate them).
     *
     * @throws MetroException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    @Test
    public void testPeerPushTwoKeyBlocksAndOneFastOnTopOfGenesis() throws MetroException, InvocationTargetException, IllegalAccessException {
        // 1st key block
        System.out.println(Convert.toHexString(Metro.getBlockchain().getLastBlock().getHash()));
        Metro.getBlockchainProcessor().processPeerBlock(emptyKeyBlockJSON);

        Block keyBlock1 = Metro.getBlockchain().getLastBlock();
        emptyKeyBlockJSON.put("previousBlock", keyBlock1.getStringId());
        emptyKeyBlockJSON.put("previousBlockHash", Convert.toHexString(keyBlock1.getHash()));
        emptyKeyBlockJSON.put("previousKeyBlock", keyBlock1.getStringId());
        emptyKeyBlockJSON.put("nonce", 154192);
        emptyKeyBlockJSON.put("timestamp", 2330920110L);
        JSONArray transactions = (JSONArray)emptyKeyBlockJSON.get("transactions");
        ((JSONObject)transactions.get(0)).put("timestamp", 2330920107L);
        // adding 2nd key block with no fast in between: new prevBlock, nonce, timestamp and coinbase timestamp set
        Metro.getBlockchainProcessor().processPeerBlock(emptyKeyBlockJSON);

        Block keyBlock2 = Metro.getBlockchain().getLastBlock();
        emptyPosBlockJSON.put("previousBlock", keyBlock2.getStringId());
        emptyPosBlockJSON.put("previousBlockHash", Convert.toHexString(keyBlock2.getHash()));
        emptyPosBlockJSON.put("timestamp", 2330920111L);
        emptyPosBlockJSON.remove("blockSignature");
        BlockImpl block1 = (BlockImpl) blockParser.invoke(null, emptyPosBlockJSON, true);
        emptyPosBlockJSON.put("blockSignature", Convert.toHexString(Crypto.sign(block1.bytes(), ALICE.getSecretPhrase())));
        // re-signed fast block with new timestamp
        Metro.getBlockchainProcessor().processPeerBlock(emptyPosBlockJSON);
        Block block2 = Metro.getBlockchain().getLastBlock();
        Assert.assertEquals("872a9430b85839bd199eb8b1b7e17a5a0a2389cde66623adaac38ba01d0e4202bbea452aaf6a8f4001f71f50508c9e1bc70da359c687504051a95673d82a691b", Convert.toHexString(block2.getBlockSignature()));
/*
        BlockImpl preparedBlock = (BlockImpl) blockParser.invoke(null, emptyKeyBlockJSON, false);
        ByteBuffer buffer = ByteBuffer.wrap(preparedBlock.bytes());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int noncePos = buffer.limit() - 4;
        int currentNonce = 0, poolSize = 1, startingNonce = 0;
        while (!Thread.currentThread().isInterrupted()) {
            buffer.putInt(noncePos, currentNonce);
            byte[] hash = HASH_FUNCTION.hash(buffer.array());
            ArrayUtils.reverse(hash);
            if (new BigInteger(1, hash).compareTo(BitcoinJUtils.decodeCompactBits((int)preparedBlock.getBaseTarget())) < 0) {
                Logger.logDebugMessage("%s found solution Keccak nonce %d" +
                                " hash %s meets target",
                        Thread.currentThread().getName(), currentNonce,
                        Arrays.toString(hash));
                Assert.assertEquals(0, currentNonce);
            }
            currentNonce += poolSize;
            if (currentNonce > 256L * 256L * 256L * 256L) {
                Logger.logInfoMessage("%s solution not found for nonce within %d upto 2^32", Thread.currentThread().getName(), startingNonce);
            }
            if (((currentNonce - startingNonce) % (poolSize * 1000000)) == 0) {
                Logger.logInfoMessage("%s computed %d [MH]", Thread.currentThread().getName(), (currentNonce - startingNonce) / poolSize / 1000000);
            }
        }
*/
    }
    @Test
    public void testPeerPushThreeFastBlocksAndOneKeyOnTopOfGenesis() throws MetroException, InvocationTargetException, IllegalAccessException {
        Metro.getBlockchainProcessor().processPeerBlock(emptyPosBlockJSON);
        Block posBlock1 = Metro.getBlockchain().getLastBlock();
        emptyPosBlockJSON.put("previousBlock", posBlock1.getStringId());
        emptyPosBlockJSON.put("previousBlockHash", Convert.toHexString(HASH_FUNCTION.hash(posBlock1.getBytes())));
        emptyPosBlockJSON.put("timestamp", 2330920110L);
        emptyPosBlockJSON.remove("blockSignature");
        BlockImpl block1 = (BlockImpl) blockParser.invoke(null, emptyPosBlockJSON, true);
        emptyPosBlockJSON.put("blockSignature", Convert.toHexString(Crypto.sign(block1.bytes(), ALICE.getSecretPhrase())));
        // 2nd fast block
        Metro.getBlockchainProcessor().processPeerBlock(emptyPosBlockJSON);
        Block posBlock2 = Metro.getBlockchain().getLastBlock();
        emptyPosBlockJSON.put("previousBlock", posBlock2.getStringId());
        emptyPosBlockJSON.put("previousBlockHash", Convert.toHexString(HASH_FUNCTION.hash(posBlock2.getBytes())));
        emptyPosBlockJSON.put("timestamp", 2330920111L);
        emptyPosBlockJSON.remove("blockSignature");
        block1 = (BlockImpl) blockParser.invoke(null, emptyPosBlockJSON, true);
        emptyPosBlockJSON.put("blockSignature", Convert.toHexString(Crypto.sign(block1.bytes(), ALICE.getSecretPhrase())));
        // 2rd fast block
        Metro.getBlockchainProcessor().processPeerBlock(emptyPosBlockJSON);
        Block posBlock3 = Metro.getBlockchain().getLastBlock();
        Assert.assertEquals("e76df3c6daa584a41ef2d7d1fe3dbc42ae0fce1b2fdb07b8993f799e28befd095b1efe8361d4a1cbe80c3c6664c2bfd7d0fd5e2bde845a372bcaf1a3a0018fed", Convert.toHexString(posBlock3.getBlockSignature()));
        emptyKeyBlockJSON.put("previousBlock", posBlock3.getStringId());
        emptyKeyBlockJSON.put("previousBlockHash", Convert.toHexString(posBlock3.getHash()));
        emptyKeyBlockJSON.put("nonce", 212428);
        emptyKeyBlockJSON.put("timestamp", 2330920112L);
        emptyKeyBlockJSON.put("forgersMerkleRoot", Convert.toHexString(Metro.getBlockchainProcessor().getForgersMerkleAtLastKeyBlock()));
        // adding key block that finishes cluster consisting of 3 blocks
        Metro.getBlockchainProcessor().processPeerBlock(emptyKeyBlockJSON);
    }
}
