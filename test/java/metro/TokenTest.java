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

package metro;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TokenTest extends BlockchainTest {
    @Test
    public void testParseValidToken() throws Exception {
        String token =  "6s7hchl9q0e5jgrrtgscoip2lcb2o3oi7ndso1bnjr475suv001ug93u00000000f2dmu0jbr8b5knrftpd7l22r97tahjj8lq9vs24m8rep7pjlnmgm60ehtts9ck3fo1ne7e4al1shidk65rhh6q9dbhfv6uo9017rn3v8";
        Token actual = Token.parseToken(token, "http://nxt.org");

        assertEquals(1000, actual.getTimestamp());
        assertTrue(actual.isValid());
    }

    @Test
    public void testParseInValidToken() throws Exception {
        String token =  "6s7hchl9q0e5jgrrtgscoip2lcb2o3oi7ndso1bnjr475suv001ug93u00000000f2dmu0jbr8b5knrftpd7l22r97tahjj8lq9vs24m8rep7pjlnmgm60ehtts9ck3fo1ne7e4al1shidk65rhh6q9dbhfv6uo9017rn3v8";
        Token actual = Token.parseToken(token, "http://next.org");

        assertEquals(1000, actual.getTimestamp());
        assertFalse(actual.isValid());
    }

    @Test
    public void testGenerateToken() throws Exception {
        long start = Metro.getEpochTime();
        String tokenString = Token.generateToken("secret", "http://nxt.org");
        long end = Metro.getEpochTime();
        Token token = Token.parseToken(tokenString, "http://nxt.org");

        assertTrue(token.isValid());
        assertTrue(token.getTimestamp() >= start);
        assertTrue(token.getTimestamp() <= end);
    }

    @Test
    public void emptySecret() throws Exception {
        String tokenString = Token.generateToken("", "http://nxt.org");
        Token token = Token.parseToken(tokenString, "http://nxt.org");
        assertTrue(token.isValid());
    }

    @Test
    public void emptySite() throws Exception {
        String tokenString = Token.generateToken("secret", "");
        Token token = Token.parseToken(tokenString, "");
        assertTrue(token.isValid());
    }

    @Test
    public void veryLongSite() throws Exception {
        StringBuilder site = new StringBuilder(6 * 100000);
        for (int i = 0; i < 100000; i++) {
            site.append("abcd10");
        }
        String tokenString = Token.generateToken("secret", site.toString());

        Token token = Token.parseToken(tokenString, site.toString());
        assertTrue(token.isValid());
    }
}
