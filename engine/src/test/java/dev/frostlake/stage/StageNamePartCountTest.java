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

package dev.frostlake.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

/**
 * A stage name counts its parts, empty ones included, whatever reads it — a query, COPY, LIST, PUT and GET: five or
 * more are no identifier at all, refused with the name as written; four are one too many; and an empty part among at
 * most three names no container, resolved as an object name's containers are. A table's stage may be qualified.
 * Every cell is live-verified.
 */
public class StageNamePartCountTest extends BaseDatabaseTest {

    private static final String TOO_MANY = "SQL compilation error:|Object does not exist, or operation cannot be performed.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (x INT)");
        engine.execute("CREATE STAGE st");
    }

    /** Every row's first cell, a bar between rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "").append(row.getValue(0));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String invalid(final String written) {
        return "SQL compilation error:|Invalid identifier " + written;
    }

    @Test
    public void fivePartsOrMoreAreNoIdentifier() {
        assertEquals(invalid("test_db....st"), answer("SELECT $1 FROM @test_db....st"));
        assertEquals(invalid("test_db....st"), answer("SELECT $1 FROM @test_db....st/x"));
        assertEquals(invalid("test_db.....st"), answer("SELECT $1 FROM @test_db.....st"));
        assertEquals(invalid("....st"), answer("SELECT $1 FROM @....st"));
        assertEquals(invalid("a....st"), answer("SELECT $1 FROM @a....st"));
        assertEquals(invalid("\"a\"....st"), answer("SELECT $1 FROM @\"a\"....st"));
        assertEquals(invalid("test_db.public...st"), answer("SELECT $1 FROM @test_db.public...st"));
        assertEquals(invalid("a.b.c.d.e"), answer("SELECT $1 FROM @a.b.c.d.e"));
        assertEquals(invalid("....%T"), answer("SELECT $1 FROM @....%T"));
        assertEquals(invalid("test_db....st"), answer("LIST @test_db....st"));
        assertEquals(invalid("a.b.c.d.e"), answer("LIST @a.b.c.d.e"));
        assertEquals(invalid("test_db....st"), answer("COPY INTO t FROM @test_db....st"));
        assertEquals(invalid("....st"), answer("PUT file:///tmp/fl_nonexistent_part_count.csv @....st"));
    }

    @Test
    public void fourPartsAreOneTooMany() {
        assertEquals(TOO_MANY, answer("SELECT $1 FROM @a.b.c.d"));
        assertEquals(TOO_MANY, answer("SELECT $1 FROM @...st"));
        assertEquals(TOO_MANY, answer("SELECT $1 FROM @test_db.public..st"));
        assertEquals(TOO_MANY, answer("SELECT $1 FROM @test_db.test_schema.st.x"));
        assertEquals(TOO_MANY, answer("COPY INTO t FROM @test_db.public..st"));
        assertEquals(TOO_MANY, answer("LIST @...st"));
        assertEquals(TOO_MANY, answer("LIST @a.b.c.d"));
        assertEquals(TOO_MANY, answer("LIST @..st.x"));
        assertEquals(TOO_MANY, answer("LIST @test_db..st.x"));
        assertEquals(TOO_MANY, answer("LIST @test_db...st"));
        assertEquals(TOO_MANY, answer("LIST @test_db...%T"));
        assertEquals(TOO_MANY, answer("LIST @test_db.public..%T"));
        assertEquals("SQL compilation error:|Object 'A.B.C.T' does not exist or not authorized.",
            answer("SELECT $1 FROM @a.b.c.%T"));
    }

    @Test
    public void anEmptyPartNamesNoContainer() {
        final String noSchema = hinted("SQL compilation error:|Schema 'TEST_DB.\"\"' does not exist or not authorized.");
        final String noDatabase = hinted("SQL compilation error:|Database '\"\"' does not exist or not authorized.");
        assertEquals(noSchema, answer("SELECT $1 FROM @.st"));
        assertEquals(noSchema, answer("LIST @.st"));
        assertEquals(noSchema, answer("COPY INTO t FROM @.st"));
        assertEquals(noSchema, answer("LIST @.%T"));
        assertEquals(noSchema, answer("LIST @test_db..%T"));
        assertEquals(noSchema, answer("LIST @test_db..st"));
        assertEquals("SQL compilation error:|Object '\"\".T' does not exist or not authorized.",
            answer("SELECT $1 FROM @.%T"));
        assertEquals(noDatabase, answer("LIST @..st"));
        assertEquals(noDatabase, answer("LS @..st"));
        assertEquals(noDatabase, answer("LIST @..%t"));
        assertEquals(noDatabase, answer("LIST @.public.st"));
        assertEquals(hinted("SQL compilation error:|Database 'A' does not exist or not authorized."), answer("LIST @a.b.%T"));
    }

    @Test
    public void aTableStageMayBeQualified() {
        assertEquals("", answer("SELECT $1 FROM @test_db.test_schema.%t"));
        assertEquals("", answer("LIST @test_db.test_schema.%t"));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.NOSCH' does not exist or not authorized."),
            answer("LIST @test_db.nosch.%t"));
        assertEquals(hinted("SQL compilation error:|Database 'NODB' does not exist or not authorized."),
            answer("LIST @nodb.public.%t"));
    }
}
