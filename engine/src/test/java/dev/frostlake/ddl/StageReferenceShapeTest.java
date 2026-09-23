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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stage reference's NAME runs to the first slash, and a DOT always separates name parts — never a
 * path. Frostlake read a dot as either, which made {@code @st.csv} a path on a stage the account says
 * is a SCHEMA, and made three shapes the account reads into syntax errors:
 *
 * <pre>
 *   @st/sub/../other   '..' is an ordinary segment; the account does not normalise it away
 *   @st.               a trailing dot leaves the stage nameless in the schema ST
 *   @st..              two of them push ST up to a database
 *   @%T.x              %T is the schema, spelled "%T", and X the stage
 * </pre>
 */
public class StageReferenceShapeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE STAGE st");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
    }

    /** "OK <n> rows", or the refusal's message. */
    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            int rows = 0;
            while (rs.next()) {
                rows++;
            }
            return "OK " + rows + " rows";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** A '..' is a path segment like any other, wherever it stands. */
    @Test
    public void doubleDotIsAnOrdinarySegment() {
        assertEquals("OK 0 rows", outcome("LIST @st/sub/../other"));
        assertEquals("OK 0 rows", outcome("LIST @st/sub/.."));
        assertEquals("OK 0 rows", outcome("LIST @st/.."));
        assertEquals("OK 0 rows", outcome("SELECT $1 FROM @st/sub/../other"));
    }

    /** A trailing dot is part of the NAME, and pushes what precedes it up a container. */
    @Test
    public void aTrailingDotNamesAContainer() {
        assertEquals(hinted("SQL compilation error: Schema 'TEST_DB.ST' does not exist or not authorized."),
            outcome("LIST @st."));
        assertEquals(hinted("SQL compilation error: Database 'ST' does not exist or not authorized."),
            outcome("LIST @st.."));
        assertEquals(hinted("SQL compilation error: Database 'A' does not exist or not authorized."),
            outcome("SELECT $1 FROM @a.."));
    }

    /** A dot after the stage name opens a name part, never a path — even one that looks like a file. */
    @Test
    public void aDotAfterTheNameIsNeverAPath() {
        final String schemaMiss =
            hinted("SQL compilation error: Schema 'TEST_DB.ST' does not exist or not authorized.");
        assertEquals(schemaMiss, outcome("LIST @st.x"));
        assertEquals(schemaMiss, outcome("LIST @st.csv"), "a file-looking suffix is still a name");
        assertEquals("OK 0 rows", outcome("LIST @st/f.csv"), "after a slash it IS a file");
    }

    /** A table stage's '%' belongs to its name once anything follows it. */
    @Test
    public void aTableStagePercentBelongsToTheName() {
        assertEquals(hinted("SQL compilation error: Schema 'TEST_DB.\"%T\"' does not exist or not"
            + " authorized."), outcome("LIST @%T.x"));
        assertEquals("OK 0 rows", outcome("LIST @%T"), "and the bare table stage still reads");
    }

    /** The ordinary qualified forms are untouched. */
    @Test
    public void theQualifiedFormsStillRead() {
        assertEquals("OK 0 rows", outcome("LIST @test_schema.st"));
        assertEquals("OK 0 rows", outcome("LIST @test_db.test_schema.st"));
        assertEquals("OK 0 rows", outcome("LIST @test_schema.st/sub"));
    }
}
