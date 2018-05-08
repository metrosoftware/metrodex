package metro;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.security.MessageDigest;

import static metro.Consensus.HASH_FUNCTION;

public class FilledKeyBlockTest extends BlockchainTest {
    @Test
    public void testPrepareAndValidateSolvedKeyBlock() throws MetroException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Transaction.Builder builder = Metro.newTransactionBuilder(ALICE.getPublicKey(), 0, 100, (short) 10, Attachment.ARBITRARY_ENVELOPE);
        Metro.getTransactionProcessor().broadcast(builder.build(ALICE.getSecretPhrase()));
        Block minedBlock = mineBlock();
        Assert.assertEquals(2, minedBlock.getTransactions().size());
        Assert.assertTrue(minedBlock.getTransactions().get(0).getType().isCoinbase());
        Assert.assertEquals(TransactionType.Envelope.ARBITRARY_ENVELOPE, minedBlock.getTransactions().get(1).getType());
        // TODO #188 check forgersMerkle
        MessageDigest mdg = HASH_FUNCTION.messageDigest();
        mdg.update(txHashPrivateAccess(minedBlock.getTransactions().get(0)));
        Assert.assertArrayEquals(mdg.digest(txHashPrivateAccess(minedBlock.getTransactions().get(1))), minedBlock.getTxMerkleRoot());
    }

    @Test
    public void testLargeKeyblockSize() throws MetroException {
        Transaction.Builder builder;
        for (int i = 1; i <= 5554; i++) {
            builder = Metro.newTransactionBuilder(ALICE.getPublicKey(), 0, i, (short) 10, Attachment.ARBITRARY_ENVELOPE);
            Metro.getTransactionProcessor().broadcast(builder.build(ALICE.getSecretPhrase()));
        }
        Block minedBlock = mineBlock();
        Assert.assertEquals(5555, minedBlock.getTransactions().size());
        System.out.println("payloadLength=" + minedBlock.getPayloadLength());
        System.out.println("jsonLength=" + minedBlock.getJSONObject().toString().length());
    }

    @Test
    public void testTooLargeKeyblock() throws MetroException {
        Transaction.Builder builder;
        for (int i = 1; i <= 5600; i++) {
            builder = Metro.newTransactionBuilder(ALICE.getPublicKey(), 0, i, (short) 10, Attachment.ARBITRARY_ENVELOPE);
            Metro.getTransactionProcessor().broadcast(builder.build(ALICE.getSecretPhrase()));
        }
        Block minedBlock = mineBlock();
        Assert.assertNull(minedBlock);
    }
}
