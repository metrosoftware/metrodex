/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 * Copyright © 2018 metro.software
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package metro.crypto;

import metro.crypto.ReedSolomon.DecodeException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ReedSolomonTest {

    //????-R???-KF23-W6N3-GC23-????
    private Object[][] testAccounts = {
            {-1L, -1, "ZZZZ-ZZZZ-ZZZZ-ZZZZ-ZZZ3-NP2G"},
            {8264278205416377583L, -1782054, "BMQU-XZZY-YNMY-E6QF-JPW2-FQH9"},
            {8301188658053077183L, -2302994, "TQZG-RZZX-FWR7-SCJK-MTW2-2GTH"},
            {1798923958688893959L, 88893959, "SUJ9-2W4N-9Z3L-EU7Z-R982-RTZJ"},
            {6899983965971136120L, 39659711, "UC7Z-H237-FDPZ-YF8Z-3YR2-S3HP"},
            {1629938923029941274L, 923029941, "ANFP-5AVJ-AMQB-E4L3-YN72-H9TZ"},
            {6474206656034063375L, 66560340, "HACN-3W3Z-M42F-6CSY-TGQ2-RX76"},
            {1691406066100673814L, 66100673, "39G3-4S3Z-FM4J-XSRT-TV72-FYRH"},
            {2992669254877342352L, 992669254, "PVL8-L2XL-C55F-CMEQ-AEC2-W4X6"},
            {-43918951749449909L, 17494499, "PWH5-BE2J-TZY5-9A66-5VZ3-4HUJ"},
            {-9129355674909631300L, -909631300, "JA7W-RM6W-KF23-W6N3-GC23-EERJ"},
            {-9129355674909631300L, 90963130, "RZ7U-RJ4Q-KF23-W6N3-GC23-HPH7"},
            {-9129355674909631300L, -9096313, "CEW9-RMZR-KF23-W6N3-GC23-TQ9C"},
            {-9129355674909631300L, 909631, "VSBZ-RJ22-KF23-W6N3-GC23-Q934"},
            {-9129355674909631300L, -90963, "X97F-RMZZ-KF23-W6N3-GC23-MMVX"},
            {-9129355674909631300L, 963, "22Y5-RJ22-KF23-W6N3-GC23-GVWC"},
            {-9129355674909631300L, -63, "ZZY3-RMZZ-KF23-W6N3-GC23-E4WY"},
            {-9129355674909631300L, 0, "2222-RJ22-KF23-W6N3-GC23-Q3JR"},
            {0L, 0, "2222-2222-2222-2222-2222-2222"},
            {1L, -1, "ZZZZ-29ZZ-2222-2222-2222-C6VR"},
            {10L, 100000000, "DSA2-3A4Z-2222-2222-2222-6P7U"},
            {100L, 10000000, "K7N2-EJ2B-2222-2222-2222-5LS5"},
            {1000L, 1000000, "YJL2-X222-2222-5222-2222-AX8Q"},
            {10000L, 100000, "53P2-4222-2222-9322-2222-RBEP"},
            {100000L, 10000, "2BSJ-N222-2222-8E22-2222-9YM7"},
            {1000000L, 1000, "22ZA-A222-2222-4U52-2222-WBHC"},
            {10000000L, 100, "2256-J222-2222-Q683-2222-N27Z"},
            {100000000L, 10, "222C-2222-2222-3HXD-2222-DNVJ"},
            {1000000000L, 1, "2223-2222-2225-CQ8R-2222-87KT"},
            {10000000000L, 100000000, "DSA2-224Z-3227-6Z4A-2222-F3ME"},
            {100000000000L, 10000000, "K7N2-222B-D22N-ARXJ-2222-DB9W"},
            {1000000000000L, 1000000, "YJL2-2222-N52F-JABB-2222-8JBU"},
            {10000000000000L, 100000, "53P2-2222-E636-2PWW-2222-UT2M"},
            {100000000000000L, 10000, "2BSJ-2222-TDDK-2LY2-2222-7CVK"},
            {1000000000000000L, 1000, "22ZA-2222-ZPKC-2NKB-5222-BUBK"},
            {10000000000000000L, 100, "2256-2222-TVJ8-2AJZ-5322-CNWG"},
            {100000000000000000L, 10, "222C-2222-WPA7-2J4V-5D22-JUEP"},
            {1000000000000000000L, 1, "2223-2222-TUQU-22TG-2H52-Z8YW"}
    };

    @Test
    public void testSamples() {
        for (Object[] testAccount : testAccounts) {
            assertEquals(testAccount[2], ReedSolomon.encode((Long) testAccount[0], (Integer) testAccount[1]));
            try {
                Pair<Long, Integer> id = ReedSolomon.decode((String) testAccount[2]);
                assertEquals(testAccount[0], id.getLeft());
                assertEquals(testAccount[1], id.getRight());
            } catch (DecodeException e) {
                fail(e.toString());
            }
        }
    }

}
