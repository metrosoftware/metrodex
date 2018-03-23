package nxt;

import nxt.crypto.Crypto;
import nxt.util.Convert;
import org.junit.Assert;
import org.junit.Test;

public class BlockTest extends BlockchainTest {
    @Test
    public void testProcessHeader() throws Exception {
        generateBlock();
        Block posBlock = Nxt.getBlockchain().getLastBlock();
        byte[] zero32bytes = new byte[Convert.HASH_SIZE];
        byte[] generatorPublicKey = Convert.parseHexString("1259ec21d31a30898d7cd1609f80d9668b4778e3d97e941044b39f0c44d2e51b");
        byte[] prevBlockHash = Crypto.sha256().digest(posBlock.getBytes());
        byte[] prevKeyBlockHash = Convert.parseHexString("2e46f6ffdf11ae63645938a59d000e08fdcb307c69727a57efa4b90b556a16ed");

        byte[] generationSignature = Convert.generationSignature(zero32bytes, generatorPublicKey);

        BlockImpl block0 = new BlockImpl(Consensus.getKeyBlockVersion(posBlock.getHeight()), Nxt.getEpochTime(), 0x9299FF3, 0, 0, 1,
                0, 0, 0, Convert.EMPTY_PAYLOAD_HASH, generatorPublicKey,
                generationSignature, null, prevBlockHash, prevKeyBlockHash, zero32bytes, zero32bytes, null);
        byte[] header = block0.bytes();
        Block block1 = Nxt.getBlockchain().processBlockHeader(header);
        Assert.assertArrayEquals(header, block1.getBytes());
        Assert.assertEquals(block0, block1);
    }
}
