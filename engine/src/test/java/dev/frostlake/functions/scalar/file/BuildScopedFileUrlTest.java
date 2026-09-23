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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUILD_SCOPED_FILE_URL: a scoped URL of a staged file, {@code …/api/files/<query id>/<number>/<token>}. The token is
 * the account's own encryption live and a signed path here, so the assertions read the URL's shape and behaviour.
 */
public class BuildScopedFileUrlTest extends BaseDatabaseTest {

    /** The shape both sides share: a query id, a number and a percent-encoded base64 token. */
    private static final String SHAPE =
        ".*/api/files/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9]+/[A-Za-z0-9%]+";

    private String value(final String sql) {
        final Object value = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String segment(final String url, final int fromEnd) {
        final String[] parts = url.split("/");
        return parts[parts.length - 1 - fromEnd];
    }

    @Test
    public void theUrlCarriesAQueryIdANumberAndAToken() {
        engine.execute("CREATE STAGE bsc");
        for (final String args : new String[] {"@bsc, 'f.csv'", "'@bsc', 'dir/sub dir/f 1.csv'",
                "@bsc, 'f.csv', FALSE", "@bsc, 'f.csv', TRUE", "@bsc, ''"}) {
            final String url = value("SELECT BUILD_SCOPED_FILE_URL(" + args + ")");
            assertTrue(url.matches(SHAPE), args + " -> " + url);
        }
        assertNull(value("SELECT BUILD_SCOPED_FILE_URL(@bsc, NULL)"));
    }

    /** Every call makes a different URL; the calls of one statement share its query id. */
    @Test
    public void everyCallMakesADifferentUrl() {
        engine.execute("CREATE STAGE bsc_twice");
        assertEquals("false", value("SELECT BUILD_SCOPED_FILE_URL(@bsc_twice, 'a') = BUILD_SCOPED_FILE_URL(@bsc_twice,"
            + " 'a')").toLowerCase());
        final Row row = engine.executeQuery("SELECT BUILD_SCOPED_FILE_URL(@bsc_twice, 'a') AS u1,"
            + " BUILD_SCOPED_FILE_URL(@bsc_twice, 'b') AS u2").getRows().get(0);
        final String first = row.getValue(0).toString();
        final String second = row.getValue(1).toString();
        assertEquals(segment(first, 2), segment(second, 2));
        assertEquals(segment(first, 1), segment(second, 1));
        assertNotEquals(segment(first, 0), segment(second, 0));
    }

    @Test
    public void theThirdArgumentIsABooleanLiteral() {
        engine.execute("CREATE STAGE bsc_pl");
        engine.execute("CREATE TABLE bsc_t (b BOOLEAN)");
        engine.execute("INSERT INTO bsc_t VALUES (TRUE)");
        final String refused = "SQL compilation error: BUILD_SCOPED_FILE_URL operation expects valid boolean for"
            + " privatelink argument.";
        assertEquals(refused, refusal("SELECT BUILD_SCOPED_FILE_URL(@bsc_pl, 'f.csv', NULL)"));
        assertEquals(refused, refusal("SELECT BUILD_SCOPED_FILE_URL(@bsc_pl, 'f.csv', 'x')"));
        assertEquals(refused, refusal("SELECT BUILD_SCOPED_FILE_URL(@bsc_pl, 'f.csv', 'TRUE')"));
        assertEquals(refused, refusal("SELECT BUILD_SCOPED_FILE_URL(@bsc_pl, 'f.csv', 1)"));
        assertEquals(refused, refusal("SELECT BUILD_SCOPED_FILE_URL(@bsc_pl, 'f.csv', NOT TRUE)"));
        assertEquals(refused, refusal("SELECT BUILD_SCOPED_FILE_URL(@bsc_pl, 'f.csv', b) FROM bsc_t"));
    }

    @Test
    public void theStageArgumentIsRefusedAsTheAccountRefusesIt() {
        engine.execute("CREATE TABLE bsc_rt (a INT)");
        final String prefix = "SQL compilation error: Argument 1 to function 'BUILD_SCOPED_FILE_URL' ";
        assertEquals(prefix + "cannot be null or empty.", refusal("SELECT BUILD_SCOPED_FILE_URL(NULL, 'f.csv')"));
        assertEquals(prefix + "does not start with '@'. Please specify stage name as '@<stage_name>'.",
            refusal("SELECT BUILD_SCOPED_FILE_URL('bsc', 'f.csv')"));
        assertEquals(prefix + "should only contain the stage name and not a path.",
            refusal("SELECT BUILD_SCOPED_FILE_URL('@bsc/d', 'f.csv')"));
        final String kinds = prefix + "provides a user or table stage. These stage kinds are not supported by this"
            + " function.";
        assertEquals(kinds, refusal("SELECT BUILD_SCOPED_FILE_URL(@~, 'f.csv')"));
        assertEquals(kinds, refusal("SELECT BUILD_SCOPED_FILE_URL(@%bsc_rt, 'f.csv')"));
        assertEquals("SQL compilation error: Stage '@bsc_nosuch' provided to the function 'BUILD_SCOPED_FILE_URL'"
            + " does not exist or is not authorized.", refusal("SELECT BUILD_SCOPED_FILE_URL(@bsc_nosuch, 'f.csv')"));
        assertEquals("SQL compilation error:\nargument 1 to function BUILD_SCOPED_FILE_URL needs to be constant,"
            + " found 'X'", refusal("SELECT BUILD_SCOPED_FILE_URL(x, 'f.csv') FROM (SELECT '@bsc' x)"));
        assertEquals("SQL compilation error: error line 1 at position 7\nnot enough arguments for function"
            + " [BUILD_SCOPED_FILE_URL('@bsc')], expected 2, got 1", refusal("SELECT BUILD_SCOPED_FILE_URL(@bsc)"));
        assertEquals("SQL compilation error: error line 1 at position 7\ntoo many arguments for function"
                + " [BUILD_SCOPED_FILE_URL('@bsc', 'f.csv', TRUE, 1)] expected 3, got 4",
            refusal("SELECT BUILD_SCOPED_FILE_URL(@bsc, 'f.csv', TRUE, 1)"));
    }

    @Test
    public void theResultIsABareVarchar() {
        engine.execute("CREATE STAGE bsc_type");
        assertEquals("VARCHAR[LOB]", value("SELECT SYSTEM$TYPEOF(BUILD_SCOPED_FILE_URL(@bsc_type, 'f.csv'))"));
    }

    /** The token signs the staged file's local path for a day. */
    @Test
    public void theTokenNamesTheStagedFileForADay() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the token is the engine's own signature");
        engine.execute("CREATE STAGE bsc_tok");
        stageLocalFile("bsc_tok", "data.csv", "1,a\n");
        final long before = Instant.now().getEpochSecond();
        final String url = value("SELECT BUILD_SCOPED_FILE_URL(@bsc_tok, 'data.csv')");
        final long[] expiry = new long[1];
        final Path file = PresignedUrls.verify(BuildScopedFileUrl.tokenOf(segment(url, 0)), expiry);
        assertNotNull(file);
        assertTrue(file.toString().endsWith("data.csv"), file.toString());
        assertTrue(expiry[0] >= before + BuildScopedFileUrl.VALIDITY_SECONDS);
        assertNull(BuildScopedFileUrl.tokenOf("not-base64!"));
    }
}
