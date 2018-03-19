package nxt;

import nxt.crypto.Crypto;
import nxt.util.Convert;
import org.junit.Assert;
import org.junit.Test;

import java.security.MessageDigest;

import static nxt.Consensus.KEY_BLOCK_VERSION;

public class BlockTest extends BlockchainTest {
    @Test
    public void testProcessHeader() throws Exception {
        generateBlock();
        Block posBlock = Nxt.getBlockchain().getLastBlock();
        byte[] zero32bytes = new byte[Convert.EMPTY_HASH.length];
        byte[] generatorPublicKey = Convert.parseHexString("1259ec21d31a30898d7cd1609f80d9668b4778e3d97e941044b39f0c44d2e51b");
        byte[] prevBlockHash = Crypto.sha256().digest(posBlock.getBytes());
        byte[] prevKeyBlockHash = Convert.parseHexString("2e46f6ffdf11ae63645938a59d000e08fdcb307c69727a57efa4b90b556a16ed");
        MessageDigest digest = Crypto.sha256();
        digest.update(zero32bytes);
        byte[] generationSignature = digest.digest(generatorPublicKey);

        BlockImpl block0 = new BlockImpl(KEY_BLOCK_VERSION, Nxt.getEpochTime(), 0x9299FF3, 0, 0, 1,
                0, 0, 0, Convert.EMPTY_PAYLOAD_HASH, generatorPublicKey,
                generationSignature, null, prevBlockHash, prevKeyBlockHash, zero32bytes, zero32bytes, null);
        byte[] header = block0.bytes();
        Block block1 = Nxt.getBlockchain().processBlockHeader(header);
        Assert.assertArrayEquals(header, block1.getBytes());
        Assert.assertEquals(block0, block1);
    }
}
