package metro;

import metro.http.APICall;
import metro.util.Convert;
import metro.util.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;

import static metro.Consensus.GUARANTEED_BALANCE_KEYBLOCK_CONFIRMATIONS;
import static metro.Consensus.HASH_FUNCTION;

public class BlockTest extends BlockchainTest {

    private BlockImpl prepareBlock(byte[] prevKeyBlockHash, Tester issuer) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        generateBlock();
        Block posBlock = Metro.getBlockchain().getLastBlock();
        byte[] zero32bytes = new byte[Convert.HASH_SIZE];
        byte[] forgersBranches = Convert.parseHexString("1584abcb68b7beb00d30c4a93270d8607e596da72697bd92f0d7edac79e49fa80000000000000000000000000000000000000000000000000000000000000000");
        byte[] prevBlockHash = HASH_FUNCTION.hash(posBlock.getBytes());
        long prevBlockId = posBlock.getId();
        byte[] pubKey = issuer.getPublicKey();
        byte[] generationSignature = Convert.generationSequence(posBlock.getGenerationSequence(), pubKey);
        if (prevKeyBlockHash != null) {
            Method coinbaseBuilder = blockchainProcessor.getClass().getDeclaredMethod("buildCoinbase", byte[].class, long.class, List.class, boolean.class, int.class);
            coinbaseBuilder.setAccessible(true);

            TransactionImpl coinbase = (TransactionImpl) coinbaseBuilder.invoke(blockchainProcessor, ALICE.getPublicKey(), posBlock.getTimestamp() + 1, Collections.EMPTY_LIST, true, 0);
            return new BlockImpl(Consensus.getKeyBlockVersion(posBlock.getHeight()), Metro.getEpochTime(), 0x9299FF3, prevBlockId, 0, 1,
                    0, 0, 0, Convert.EMPTY_HASH, pubKey,
                    generationSignature, null, prevBlockHash, prevKeyBlockHash, forgersBranches, Collections.singletonList(coinbase));
        } else {
            return new BlockImpl(Consensus.getPosBlockVersion(posBlock.getHeight()), Metro.getEpochTime(), prevBlockId, 0, 0,
                    0, 0, 0, Convert.EMPTY_HASH, pubKey,
                    generationSignature, prevBlockHash, zero32bytes, forgersBranches, Collections.emptyList(), issuer.getSecretPhrase());
        }
    }

    @Test
    public void testProcessHeader() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        byte[] prevKeyBlockHash = Convert.parseHexString("2e46f6ffdf11ae63645938a59d000e08fdcb307c69727a57efa4b90b556a16ed");

        BlockImpl block0 = prepareBlock(prevKeyBlockHash, ALICE);
        byte[] header = block0.bytes();
        Block block1 = Metro.getBlockchain().composeKeyBlock(header, ALICE.getPublicKey(), block0.getTransactions());
        Assert.assertArrayEquals(header, block1.getBytes());
        Assert.assertEquals(block0, block1);
        Assert.assertEquals(0x9299FF3, block1.getBaseTarget());
    }

    @Test
    public void testConvertPosBlockToJSON_andBack() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        BlockImpl block0 = prepareBlock(null, ALICE);
        JSONObject json = block0.getJSONObject();

        Method parser = block0.getClass().getDeclaredMethod("parseBlock", json.getClass());
        parser.setAccessible(true);
        BlockImpl block1 = (BlockImpl) parser.invoke(block0, (JSONObject)JSONValue.parse(json.toJSONString()));
        Assert.assertEquals(block0, block1);
        Assert.assertEquals(0, block1.getBaseTarget());
    }

    @Test
    public void testConvertKeyBlockToJSON_andBack() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        BlockImpl block0 = prepareBlock(Convert.EMPTY_HASH, ALICE);

        Method parser = block0.getClass().getDeclaredMethod("parseBlock", JSONObject.class);
        parser.setAccessible(true);
        block0.sign(ALICE.getSecretPhrase());
        BlockImpl block1 = (BlockImpl) parser.invoke(block0, (JSONObject)JSONValue.parse(block0.getJSONObject().toJSONString()));
        Assert.assertEquals(block0, block1);
        Assert.assertEquals(block0.getBaseTarget(), block1.getBaseTarget());
        Assert.assertEquals(0x9299FF3, block1.getBaseTarget());
    }

    @Test
    public void testPrepareAndValidateKeyBlockWithoutSolving() throws MetroException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        BlockImpl preparedBlock = Metro.getBlockchainProcessor().prepareKeyBlock(null);
        Assert.assertEquals(0x8000, Short.toUnsignedInt(preparedBlock.getVersion()) & 0x8000);

        // prev block hash must point to Genesis
        BlockImpl genesis = BlockDb.findBlockAtHeight(0);
        Assert.assertEquals(genesis.getId(), preparedBlock.getPreviousBlockId());
        Assert.assertArrayEquals(Consensus.HASH_FUNCTION.hash(genesis.bytes()), preparedBlock.getPreviousBlockHash());
        Assert.assertArrayEquals(Convert.EMPTY_HASH, preparedBlock.getPreviousKeyBlockHash());
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
            Metro.getBlockchain().composeKeyBlock(buffer.array(), preparedBlock.getGeneratorPublicKey(), preparedBlock.getTransactions());
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
            Metro.getBlockchain().composeKeyBlock(buffer.array(), preparedBlock.getGeneratorPublicKey(), preparedBlock.getTransactions());
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Wrong prev block hash: d2b2f2000000008ad54465acf12b9fee64f4e574443fdf531cfd2c8275721f2c", e.getMessage());
        }
    }

    @Test
    public void testKeyblockGenerationSequence() {
        generateBlocks(2);
        Block posBlock = Metro.getBlockchain().getLastBlock();
        BlockImpl preparedBlock = Metro.getBlockchainProcessor().prepareKeyBlock(null);
        Block blockTemplate = Metro.getBlockchain().composeKeyBlock(preparedBlock.bytes(), preparedBlock.getGeneratorPublicKey(), preparedBlock.getTransactions());
        Assert.assertArrayEquals("generation sequence does not match", Convert.generationSequence(posBlock.getGenerationSequence(), blockTemplate.getGeneratorPublicKey()), blockTemplate.getGenerationSequence());
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
        Assert.assertEquals(1531805231244546040l, buffer.getLong());
        byte[] encodedCapsule = new byte[64 - 8];
        buffer.get(encodedCapsule);
        Assert.assertEquals(timeCapsule.length(), encodedCapsule[0]);
        Assert.assertTrue(timeCapsule.equalsIgnoreCase(Convert.decodeTimeCapsule(encodedCapsule)));
    }
}
