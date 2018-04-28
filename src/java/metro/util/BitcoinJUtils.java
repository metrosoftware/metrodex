/*
 * The following code gladly taken from org.bitcoinj.core.Utils
 *
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package metro.util;

import metro.Consensus;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.List;

public class BitcoinJUtils {

    public static long readUint32BE(byte[] bytes, int offset) {
        return ((long)bytes[offset] & 255L) << 24 | ((long)bytes[offset + 1] & 255L) << 16 | ((long)bytes[offset + 2] & 255L) << 8 | (long)bytes[offset + 3] & 255L;
    }

    public static void uint32ToByteArrayBE(long val, byte[] out, int offset) {
        out[offset] = (byte)((int)(255L & val >> 24));
        out[offset + 1] = (byte)((int)(255L & val >> 16));
        out[offset + 2] = (byte)((int)(255L & val >> 8));
        out[offset + 3] = (byte)((int)(255L & val));
    }

    public static BigInteger decodeCompactBits(int compact) {
        int size = compact >> 24 & 255;
        byte[] bytes = new byte[4 + size];
        bytes[3] = (byte)size;
        if (size >= 1) {
            bytes[4] = (byte)((int)(compact >> 16 & 255L));
        }

        if (size >= 2) {
            bytes[5] = (byte)((int)(compact >> 8 & 255L));
        }

        if (size >= 3) {
            bytes[6] = (byte)((int)(compact & 255L));
        }

        return decodeMPI(bytes, true);
    }

    public static int encodeCompactBits(BigInteger value) {
        int size = value.toByteArray().length;
        long result;
        if (size <= 3) {
            result = value.longValue() << 8 * (3 - size);
        } else {
            result = value.shiftRight(8 * (size - 3)).longValue();
        }

        if ((result & 8388608L) != 0L) {
            result >>= 8;
            ++size;
        }

        result |= (long)(size << 24);
        result |= value.signum() == -1 ? 8388608L : 0L;
        return (int)result;
    }

    public static BigInteger decodeMPI(byte[] mpi, boolean hasLength) {
        byte[] buf;
        if (hasLength) {
            int length = (int)readUint32BE(mpi, 0);
            buf = new byte[length];
            System.arraycopy(mpi, 4, buf, 0, length);
        } else {
            buf = mpi;
        }

        if (buf.length == 0) {
            return BigInteger.ZERO;
        } else {
            boolean isNegative = (buf[0] & 128) == 128;
            if (isNegative) {
                buf[0] = (byte)(buf[0] & 127);
            }

            BigInteger result = new BigInteger(buf);
            return isNegative ? result.negate() : result;
        }
    }

    public static byte[] encodeMPI(BigInteger value, boolean includeLength) {
        if (value.equals(BigInteger.ZERO)) {
            return !includeLength ? new byte[0] : new byte[]{0, 0, 0, 0};
        } else {
            boolean isNegative = value.signum() < 0;
            if (isNegative) {
                value = value.negate();
            }

            byte[] array = value.toByteArray();
            int length = array.length;
            if ((array[0] & 128) == 128) {
                ++length;
            }

            byte[] result;
            if (includeLength) {
                result = new byte[length + 4];
                System.arraycopy(array, 0, result, length - array.length + 3, array.length);
                uint32ToByteArrayBE((long)length, result, 0);
                if (isNegative) {
                    result[4] = (byte)(result[4] | 128);
                }

                return result;
            } else {
                if (length != array.length) {
                    result = new byte[length];
                    System.arraycopy(array, 0, result, 1, array.length);
                } else {
                    result = array;
                }

                if (isNegative) {
                    result[0] = (byte)(result[0] | 128);
                }

                return result;
            }
        }
    }

    public static List<byte[]> buildMerkleTree(List<byte[]> tree) {
        // The Merkle root is based on a tree of hashes calculated from the transactions:
        //
        //     root
        //      / \
        //   A      B
        //  / \    / \
        // t1 t2 t3 t4
        //
        // The tree is represented as a list: t1,t2,t3,t4,A,B,root where each
        // entry is a hash.
        //
        // The hashing algorithm is double SHA-256. The leaves are a hash of the serialized contents of the transaction.
        // The interior nodes are hashes of the concatenation of the two child hashes.
        //
        // This structure allows the creation of proof that a transaction was included into a block without having to
        // provide the full block contents. Instead, you can provide only a Merkle branch. For example to prove tx2 was
        // in a block you can just provide tx2, the hash(tx1) and B. Now the other party has everything they need to
        // derive the root, which can be checked against the block header. These proofs aren't used right now but
        // will be helpful later when we want to download partial block contents.
        //
        // Note that if the number of transactions is not even the last tx is repeated to make it so (see
        // tx3 above). A tree with 5 transactions would look like this:
        //
        //         root
        //        /     \
        //       1        5
        //     /   \     / \
        //    2     3    4  4
        //  / \   / \   / \
        // t1 t2 t3 t4 t5 t5

        MessageDigest sha3 = Consensus.HASH_FUNCTION.messageDigest();
        int levelOffset = 0; // Offset in the list where the currently processed level starts.
        // Step through each level, stopping when we reach the root (levelSize == 1).
        for (int levelSize = tree.size(); levelSize > 1; levelSize = (levelSize + 1) / 2) {
            // For each pair of nodes on that level:
            for (int left = 0; left < levelSize; left += 2) {
                // The right hand node can be the same as the left hand, in the case where we don't have enough
                // transactions.
                int right = Math.min(left + 1, levelSize - 1);
                byte[] leftBytes = tree.get(levelOffset + left);
                byte[] rightBytes = tree.get(levelOffset + right);
                sha3.update(leftBytes);
                sha3.update(rightBytes);
                tree.add(sha3.digest());
            }
            // Move to the next level.
            levelOffset += levelSize;
        }
        return tree;
    }

}
