package metro;

import metro.util.Convert;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static metro.Consensus.HASH_FUNCTION;

public class Uncle {
    private final long id;
    private final short version;
    private final long timestamp;
    private final long previousBlockId;
    private final long previousKeyBlockId;
    private final byte[] previousBlockHash;
    private final byte[] previousKeyBlockHash;
    private final long baseTarget;
    private final long nonce;
    private final byte[] forgersMerkleRoot;
    private final int height;
    private final int localHeight;
    private byte[] blockSignature;
    private final byte[] txMerkleRoot;
    private final long generatorId;
    private volatile byte[] generatorPublicKey;
    private volatile String stringId = null;
    private volatile byte[] bytes = null;
    private final short clusterSize;
    private long nephewId;

    public Uncle(Block block) {
        this.id = block.getId();
        this.version = block.getVersion();
        this.timestamp = block.getTimestamp();
        this.previousBlockId = block.getPreviousBlockId();
        this.previousBlockHash = block.getPreviousBlockHash();
        this.previousKeyBlockId = block.getPreviousKeyBlockId();
        this.previousKeyBlockHash = block.getPreviousKeyBlockHash();
        this.baseTarget = block.getBaseTarget();
        this.nonce = block.getNonce();
        this.forgersMerkleRoot = block.getForgersMerkleRoot();
        this.height = block.getHeight();
        this.localHeight = block.getLocalHeight();
        this.blockSignature = block.getBlockSignature();
        this.txMerkleRoot = block.getTxMerkleRoot();
        this.generatorId = block.getGeneratorId();
        //FIXME use to calculate bytes
        long uncleMerkleId = block.getUncleMerkleId();
        this.clusterSize = block.getClusterSize();
    }

    public Uncle (byte[] headerData) {
        this.id = Convert.fullHashToId(headerData);
        ByteBuffer header = ByteBuffer.wrap(headerData);
        header.order(ByteOrder.LITTLE_ENDIAN);
        this.version = header.getShort();
        if (!BlockImpl.isKeyBlockVersion(version)) {
            throw new IllegalArgumentException("Wrong uncle version: 0x" + Integer.toUnsignedString(Short.toUnsignedInt(version), 16));
        }
        this.timestamp = header.getLong();
        this.txMerkleRoot = new byte[Convert.HASH_SIZE];
        header.get(txMerkleRoot);

        this.previousBlockId = header.getLong();
        BlockImpl previousBlock = BlockDb.findBlock(previousBlockId);
        if (previousBlock == null) {
            throw new IllegalArgumentException("Wrong prev block id: " + previousBlockId);
        }
        this.previousBlockHash = HASH_FUNCTION.hash(previousBlock.getBytes());
        this.height = previousBlock.getHeight() + 1;

        this.previousKeyBlockId = header.getLong();
        if (previousKeyBlockId > 0) {
            BlockImpl previousKeyBlock = BlockDb.findBlock(previousKeyBlockId);
            if (previousKeyBlock == null) {
                throw new IllegalArgumentException("Wrong prev key block id: " + previousKeyBlockId);
            }
            this.previousKeyBlockHash = HASH_FUNCTION.hash(previousKeyBlock.getBytes());
            this.localHeight = previousKeyBlock.getHeight() + 1;
        } else {
            this.previousKeyBlockHash = Convert.EMPTY_HASH;
            this.localHeight = 0;
        }

        this.forgersMerkleRoot = new byte[Convert.HASH_SIZE];
        header.get(forgersMerkleRoot);
        this.baseTarget = header.getInt();
        long uncleMerkleId = header.getLong();
        this.clusterSize = header.getShort();
        this.generatorId = header.getLong();
        this.nonce = header.getLong();

    }
    public Uncle(short version, long timestamp, long previousBlockId, long previousKeyBlockId, long nonce, byte[] txMerkleRoot, long generatorId, byte[] blockSignature, byte[] previousBlockHash, byte[] previousKeyBlockHash, byte[] forgersMerkleRoot, long baseTarget, int height, int localHeight, long id, long uncleMerkleId, short clusterSize) {
        this.version = version;
        this.timestamp = timestamp;
        this.baseTarget = baseTarget;
        this.previousBlockId = previousBlockId;
        this.previousKeyBlockId = previousKeyBlockId;
        this.nonce = nonce;
        this.txMerkleRoot = txMerkleRoot;
        this.generatorPublicKey = generatorPublicKey;
        this.blockSignature = blockSignature;
        this.previousBlockHash = previousBlockHash;
        this.previousKeyBlockHash = previousKeyBlockHash;
        this.forgersMerkleRoot = forgersMerkleRoot;
        this.height = height;
        this.localHeight = localHeight;
        this.id = id;
        this.generatorId = generatorId;
        this.clusterSize = clusterSize;
        //FIXME uncleMerkleId to calculate bytes
    }

    public long getNephewId() {
        return nephewId;
    }

    public void setNephewId(long nephewId) {
        this.nephewId = nephewId;
    }

    public long getId() {
        return id;
    }

    public short getVersion() {
        return version;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getPreviousBlockId() {
        return previousBlockId;
    }

    public long getPreviousKeyBlockId() {
        return previousKeyBlockId;
    }

    public byte[] getPreviousBlockHash() {
        return previousBlockHash;
    }

    public byte[] getPreviousKeyBlockHash() {
        return previousKeyBlockHash;
    }

    public long getBaseTarget() {
        return baseTarget;
    }

    public long getNonce() {
        return nonce;
    }

    public byte[] getForgersMerkleRoot() {
        return forgersMerkleRoot;
    }

    public int getHeight() {
        return height;
    }

    public int getLocalHeight() {
        return localHeight;
    }

    public byte[] getBlockSignature() {
        return blockSignature;
    }

    public byte[] getTxMerkleRoot() {
        return txMerkleRoot;
    }

    public long getGeneratorId() {
        return generatorId;
    }

    public byte[] getGeneratorPublicKey() {
        return generatorPublicKey;
    }

    public String getStringId() {
        return stringId;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public short getClusterSize() {
        return clusterSize;
    }


}
