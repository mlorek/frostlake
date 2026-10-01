/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.functions.scalar.file;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GET_PRESIGNED_URL: a signed, expiring download URL of a staged file, served by the engine's HTTP server. */
public class GetPresignedUrlTest extends BaseDatabaseTest {

    /** A presigned URL is a CLOUD stage's, where the engine stages files in a local directory. */
    private static final String NO_CLOUD_STAGE = "a presigned URL belongs to a cloud stage; the"
        + " engine stages files in a local directory";

    private String url(final String args) {
        return String.valueOf(engine.executeQuery("SELECT GET_PRESIGNED_URL(" + args + ")").getRows().get(0)
            .getValue(0));
    }

    /** The token part of a presigned URL. */
    private static String token(final String url) {
        final String tail = url.substring(url.indexOf(PresignedUrls.CONTEXT) + PresignedUrls.CONTEXT.length());
        return tail.substring(0, tail.indexOf('/'));
    }

    @Test
    public void theUrlNamesTheStagedFileUntilItExpires() {
        Assumptions.assumeFalse(isLiveSnowflake(), NO_CLOUD_STAGE);
        engine.execute("CREATE STAGE purl");
        stageLocalFile("purl", "data.csv", "1,a\n");
        final long before = Instant.now().getEpochSecond();
        final String url = url("'@purl', 'data.csv'");
        assertTrue(url.startsWith("http://"), url);
        assertTrue(url.contains(PresignedUrls.CONTEXT), url);
        assertTrue(url.endsWith("/data.csv"), url);
        final long[] expiry = new long[1];
        final Path file = PresignedUrls.verify(token(url), expiry);
        assertNotNull(file);
        assertTrue(file.toString().endsWith("data.csv"), file.toString());
        assertTrue(expiry[0] >= before + GetPresignedUrl.DEFAULT_EXPIRATION, "an hour by default");

        final long[] shortExpiry = new long[1];
        PresignedUrls.verify(token(url("'@test_db.test_schema.purl', '/data.csv', 60")), shortExpiry);
        assertTrue(shortExpiry[0] <= Instant.now().getEpochSecond() + 60);
        assertTrue(url("'@purl', 'not/staged.csv'").contains(PresignedUrls.CONTEXT), "made even for a missing file");
    }

    @Test
    public void aTamperedTokenIsNotVerified() {
        engine.execute("CREATE STAGE purl2");
        final String token = token(url("'@purl2', 'x.csv'"));
        assertNull(PresignedUrls.verify(token.substring(1), new long[1]));
        assertNull(PresignedUrls.verify("no-dot", new long[1]));
    }

    @Test
    public void theStageIsWrittenBareOrAsAString() {
        engine.execute("CREATE STAGE purl_bare");
        for (final String args : new String[] {"@purl_bare, 'f.csv'", "'@purl_bare', 'f.csv'",
                "@test_schema.purl_bare, 'f.csv', 60", "@purl_bare, '../x.csv'", "@purl_bare, 'f.csv', 60.0",
                "@purl_bare, 'f.csv', 1e2", "@purl_bare, 'f.csv', 604800", "@purl_bare, 'f.csv', -(-60)"}) {
            assertNotNull(engine.executeQuery("SELECT GET_PRESIGNED_URL(" + args + ")").getRows().get(0).getValue(0),
                args);
        }
        assertEquals("null", url("@purl_bare, p) FROM (SELECT NULL::VARCHAR p"));
    }

    @Test
    public void refusals() {
        engine.execute("CREATE STAGE purl3");
        engine.execute("CREATE TABLE purl_t (a INT)");
        final String prefix = "SQL compilation error: Argument 1 to function 'GET_PRESIGNED_URL' ";
        assertEquals(prefix + "cannot be null or empty.", refusal("NULL, 'x.csv'"));
        assertEquals(prefix + "does not start with '@'. Please specify stage name as '@<stage_name>'.",
            refusal("'purl3', 'x.csv'"));
        assertEquals(prefix + "should only contain the stage name and not a path.", refusal("'@purl3/d', 'x.csv'"));
        final String kinds = prefix + "provides a user or table stage. These stage kinds are not supported by this"
            + " function.";
        assertEquals(kinds, refusal("@~, 'x.csv'"));
        assertEquals(kinds, refusal("@%purl_t, 'x.csv'"));
        assertEquals("SQL compilation error: Stage '@no_such_stage' provided to the function 'GET_PRESIGNED_URL'"
            + " does not exist or is not authorized.", refusal("@no_such_stage, 'x.csv'"));
        assertEquals("SQL compilation error: Argument 2 to function 'GET_PRESIGNED_URL' cannot be null or empty.",
            refusal("@purl3, NULL"));
        assertEquals("SQL compilation error: Argument 2 to function 'GET_PRESIGNED_URL' cannot be null or empty.",
            refusal("@purl3, ''"));
        assertEquals("SQL compilation error:\nArgument number 2 for function 'GET_PRESIGNED_URL' needs to be a string"
            + " literal.", refusal("@purl3, 1"));
        final String range = "SQL compilation error: GET_PRESIGNED_URL expiry in seconds is invalid. Must be between 0"
            + " and 604,800 (1 week).";
        assertEquals(range, refusal("@purl3, 'x.csv', 604801"));
        assertEquals(range, refusal("@purl3, 'x.csv', -1"));
        assertEquals(range, refusal("@purl3, 'x.csv', 0.5"));
        assertEquals(range, refusal("@purl3, 'x.csv', '60'"));
        assertEquals(range, refusal("@purl3, 'x.csv', NULL"));
        assertEquals("SQL compilation error: Presigned URL expiry in seconds is invalid based on the stage credential"
            + " type. Must be between 1 and 604,800.", refusal("@purl3, 'x.csv', 0"));
        assertEquals("SQL compilation error:\nargument 3 to function GET_PRESIGNED_URL needs to be constant, found"
            + " 'CAST(60 AS NUMBER(38,0))'", refusal("@purl3, 'x.csv', 60::INT"));
        assertEquals("SQL compilation error:\nargument 3 to function GET_PRESIGNED_URL needs to be constant, found"
            + " '30 + 30'", refusal("@purl3, 'x.csv', 30 + 30"));
        // A plus is no sign of a constant: +60 is the expression UNARY PLUS(60).
        assertEquals("SQL compilation error:\nargument 3 to function GET_PRESIGNED_URL needs to be constant, found"
            + " 'UNARY PLUS(60)'", refusal("@purl3, 'x.csv', +60"));
        assertEquals("SQL compilation error:\nargument 3 to function GET_PRESIGNED_URL needs to be constant, found"
            + " 'UNARY PLUS(0)'", refusal("@purl3, 'x.csv', +0"));
        assertEquals("SQL compilation error:\nargument 3 to function GET_PRESIGNED_URL needs to be constant, found"
            + " 'CAST(null AS NUMBER(38,0))'", refusal("@purl3, 'x.csv', NULL::INT"));
        assertEquals("SQL compilation error:\nargument 3 to function GET_PRESIGNED_URL needs to be constant, found"
            + " 'CAST(null AS FLOAT)'", refusal("@purl3, 'x.csv', NULL::FLOAT"));
        assertEquals("SQL compilation error:\nargument 3 to function GET_PRESIGNED_URL needs to be constant, found"
            + " 'CAST('60' AS NUMBER(38,0))'", refusal("@purl3, 'x.csv', '60'::INT"));
        // Four arguments are refused in a sentence of their own, five or more by the count, "expected 4".
        assertEquals("Invalid number of arguments", refusal("@purl3, 'x.csv', 1, 2"));
        assertEquals("SQL compilation error: error line 1 at position 7\ntoo many arguments for function"
            + " [GET_PRESIGNED_URL('@purl3', 'x.csv', 60, 1, 2)] expected 4, got 5",
            refusal("@purl3, 'x.csv', 60, 1, 2"));
        assertEquals("SQL compilation error: error line 1 at position 7\nnot enough arguments for function"
            + " [GET_PRESIGNED_URL('@purl3')], expected 2, got 1", refusal("@purl3"));
    }

    private String refusal(final String args) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                url(args);
            }
        }).getMessage();
    }
}
