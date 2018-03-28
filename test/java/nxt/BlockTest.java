package nxt;

import nxt.http.SubmitBlockSolution;
import nxt.util.Convert;
import org.junit.Assert;
import org.junit.Test;

import static nxt.Consensus.HASH_FUNCTION;

public class BlockTest extends BlockchainTest {

    private static final byte[] generatorPublicKey = Convert.parseHexString("1259ec21d31a30898d7cd1609f80d9668b4778e3d97e941044b39f0c44d2e51b");

    @Test
    public void testProcessHeader() throws Exception {
        generateBlock();
        Block posBlock = Nxt.getBlockchain().getLastBlock();
        byte[] zero32bytes = new byte[Convert.HASH_SIZE];

        byte[] prevBlockHash = HASH_FUNCTION.hash(posBlock.getBytes());
        long prevBlockId = posBlock.getId();
        byte[] prevKeyBlockHash = Convert.parseHexString("2e46f6ffdf11ae63645938a59d000e08fdcb307c69727a57efa4b90b556a16ed");

        byte[] generationSignature = Convert.generationSignature(posBlock.getGenerationSignature(), generatorPublicKey);

        BlockImpl block0 = new BlockImpl(Consensus.getKeyBlockVersion(posBlock.getHeight()), Nxt.getEpochTime(), 0x9299FF3, prevBlockId, 0, 1,
                0, 0, 0, Convert.EMPTY_PAYLOAD_HASH, generatorPublicKey,
                generationSignature, null, prevBlockHash, prevKeyBlockHash, zero32bytes, zero32bytes, null);
        byte[] header = block0.bytes();
        Block block1 = Nxt.getBlockchain().processBlockHeader(header, generatorPublicKey);
        Assert.assertArrayEquals(header, block1.getBytes());
        Assert.assertEquals(block0, block1);
    }

    @Test
    public void testPseudoMining() throws Exception {
        generateBlock();
        generateBlock();
        generateBlock();
        Block posBlock = Nxt.getBlockchain().getLastBlock();
        String keyBlockHeader = SubmitBlockSolution.generateHeaderFromTemplate(BlockImpl.getHeaderSize(true, false));
        Block block1 = Nxt.getBlockchain().processBlockHeader(Convert.parseHexString(keyBlockHeader), generatorPublicKey);
        Assert.assertEquals("previousBlockId should match posBlock.getId()", posBlock.getId(), block1.getPreviousBlockId());
        boolean blockAccepted = Nxt.getBlockchainProcessor().processMinerBlock(block1, null);
        // TODO #126 add validateKeyBlock() call to BlockchainProcessor.validate()
        Assert.assertTrue(blockAccepted);
    }
}
