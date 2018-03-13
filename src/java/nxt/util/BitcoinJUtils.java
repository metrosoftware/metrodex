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
package nxt.util;

import java.math.BigInteger;

public class BitcoinJUtils {

    public static long readInt64(byte[] bytes, int offset) {
        return (long)bytes[offset] & 255L | ((long)bytes[offset + 1] & 255L) << 8 | ((long)bytes[offset + 2] & 255L) << 16 | ((long)bytes[offset + 3] & 255L) << 24 | ((long)bytes[offset + 4] & 255L) << 32 | ((long)bytes[offset + 5] & 255L) << 40 | ((long)bytes[offset + 6] & 255L) << 48 | ((long)bytes[offset + 7] & 255L) << 56;
    }

    public static long readUint32(byte[] bytes, int offset) {
        return (long)bytes[offset] & 255L | ((long)bytes[offset + 1] & 255L) << 8 | ((long)bytes[offset + 2] & 255L) << 16 | ((long)bytes[offset + 3] & 255L) << 24;
    }

    public static int readUint16(byte[] bytes, int offset) {
        return bytes[offset] & 255 | (bytes[offset + 1] & 255) << 8;
    }

    public static long readUint32BE(byte[] bytes, int offset) {
        return ((long)bytes[offset] & 255L) << 24 | ((long)bytes[offset + 1] & 255L) << 16 | ((long)bytes[offset + 2] & 255L) << 8 | (long)bytes[offset + 3] & 255L;
    }

    public static byte[] read256bits(byte[] bytes, int offset) {
        byte[] bits = new byte[Convert.EMPTY_HASH.length];
        System.arraycopy(bytes, offset, bits, 0, bits.length);
        return bits;
    }

    public static void uint32ToByteArrayBE(long val, byte[] out, int offset) {
        out[offset] = (byte)((int)(255L & val >> 24));
        out[offset + 1] = (byte)((int)(255L & val >> 16));
        out[offset + 2] = (byte)((int)(255L & val >> 8));
        out[offset + 3] = (byte)((int)(255L & val));
    }

    public static BigInteger decodeCompactBits(long compact) {
        int size = (int)(compact >> 24) & 255;
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

    public static long encodeCompactBits(BigInteger value) {
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
        return result;
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

}
