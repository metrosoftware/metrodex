package metro;

import metro.util.Convert;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;

import static metro.Consensus.HASH_FUNCTION;

public class BlockTest extends BlockchainTest {

    @Test
    public void testProcessHeader() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        generateBlock();
        Block posBlock = Metro.getBlockchain().getLastBlock();
        byte[] zero32bytes = new byte[Convert.HASH_SIZE];

        byte[] prevBlockHash = HASH_FUNCTION.hash(posBlock.getBytes());
        long prevBlockId = posBlock.getId();
        byte[] prevKeyBlockHash = Convert.parseHexString("2e46f6ffdf11ae63645938a59d000e08fdcb307c69727a57efa4b90b556a16ed");
        byte[] publicKey = ALICE.getPublicKey();
        byte[] generationSignature = Convert.generationSequence(posBlock.getGenerationSequence(), publicKey);

        Method coinbaseBuilder = blockchainProcessor.getClass().getDeclaredMethod("buildCoinbase", long.class, String.class, List.class, boolean.class, int.class);
        coinbaseBuilder.setAccessible(true);

        TransactionImpl coinbase = (TransactionImpl) coinbaseBuilder.invoke(blockchainProcessor, posBlock.getTimestamp() + 1, ALICE.getSecretPhrase(), Collections.EMPTY_LIST, true, 0);

        BlockImpl block0 = new BlockImpl(Consensus.getKeyBlockVersion(posBlock.getHeight()), Metro.getEpochTime(), 0x9299FF3, prevBlockId, 0, 1,
                0, 0, 0, Convert.EMPTY_HASH, publicKey,
                generationSignature, null, prevBlockHash, prevKeyBlockHash, zero32bytes, Collections.singletonList(coinbase));
        byte[] header = block0.bytes();
        Block block1 = Metro.getBlockchain().composeKeyBlock(header, publicKey, Collections.singletonList(coinbase));
        Assert.assertArrayEquals(header, block1.getBytes());
        Assert.assertEquals(block0, block1);
    }

    @Test
    public void testPrepareAndValidateKeyBlockWithoutSolving() throws MetroException {
        BlockImpl preparedBlock = Metro.getBlockchainProcessor().prepareKeyBlock();
        Assert.assertEquals(0x8000, Short.toUnsignedInt(preparedBlock.getVersion()) & 0x8000);

        // prev block hash must point to Genesis
        BlockImpl genesis = BlockDb.findBlockAtHeight(0);
        Assert.assertEquals(genesis.getId(), preparedBlock.getPreviousBlockId());
        Assert.assertArrayEquals(Consensus.HASH_FUNCTION.hash(genesis.bytes()), preparedBlock.getPreviousBlockHash());
        Assert.assertArrayEquals(Convert.EMPTY_HASH, preparedBlock.getPreviousKeyBlockHash());
        Assert.assertNotEquals("non-zero txMerkleRoot expected", Convert.toHexString(Convert.EMPTY_HASH), Convert.toHexString(preparedBlock.getTxMerkleRoot()));
        Assert.assertEquals(1, preparedBlock.getTransactions().size());
        Assert.assertTrue(preparedBlock.getTransactions().get(0).getType().isCoinbase());
        // TODO #188 check forgersMerkle
        try {
            Metro.getBlockchainProcessor().processMinerBlock(preparedBlock);
        } catch (BlockchainProcessor.BlockNotAcceptedException e) {
            Assert.assertTrue(e.getMessage().startsWith("Special keyBlock validation failed: INSUFFICIENT_WORK"));
        }
    }

    @Test
    public void testPrepareAndValidateSolvedKeyBlock() throws MetroException {
        Block minedBlock = mineBlock();
        Assert.assertEquals(1, minedBlock.getTransactions().size());
        Assert.assertTrue(minedBlock.getTransactions().get(0).getType().isCoinbase());
        // TODO #188 check forgersMerkle

    }

    @Test
    public void testProcessHeaderWrongVersion() {
        BlockImpl preparedBlock = Metro.getBlockchainProcessor().prepareKeyBlock();
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
        BlockImpl preparedBlock = Metro.getBlockchainProcessor().prepareKeyBlock();
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
        BlockImpl preparedBlock = Metro.getBlockchainProcessor().prepareKeyBlock();
        Block blockTemplate = Metro.getBlockchain().composeKeyBlock(preparedBlock.bytes(), preparedBlock.getGeneratorPublicKey(), preparedBlock.getTransactions());
        Assert.assertArrayEquals("generation sequence does not match", Convert.generationSequence(posBlock.getGenerationSequence(), blockTemplate.getGeneratorPublicKey()), blockTemplate.getGenerationSequence());
    }
}
