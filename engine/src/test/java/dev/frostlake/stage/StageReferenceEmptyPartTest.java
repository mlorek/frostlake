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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stage reference written with a leading empty part, with two empty parts, or as a table stage whose schema
 * is left empty parses, and is refused as the name resolves: no database, no object, or no schema — and, read
 * in a query's FROM, a table stage as the table it names.
 */
public class StageReferenceEmptyPartTest extends BaseDatabaseTest {

    private static final String NO_DATABASE = "SQL compilation error:|Database '\"\"' does not exist or not authorized.";
    private static final String NO_OBJECT = "SQL compilation error:|Object does not exist, or operation cannot be performed.";
    private static final String NO_SCHEMA = "SQL compilation error:|Schema 'TEST_DB.\"\"' does not exist or not authorized.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE STAGE st");
        engine.execute("CREATE OR REPLACE TABLE t (a VARCHAR)");
    }

    /** Every row's first cell, or the refusal on one line. */
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

    @Test
    public void aLeadingEmptyPartNamesNoDatabase() {
        assertEquals(hinted(NO_DATABASE), answer("SELECT $1 FROM @..st"));
        assertEquals(hinted(NO_DATABASE), answer("SELECT $1 FROM @..st/x"));
        assertEquals(hinted(NO_DATABASE), answer("SELECT $1 FROM @..nosuch"));
        assertEquals(hinted(NO_DATABASE), answer("COPY INTO t FROM @..st"));
        assertEquals(hinted(NO_DATABASE), answer("COPY INTO @..st FROM t"));
    }

    @Test
    public void twoEmptyPartsOrAFourPartNameNameNoObject() {
        assertEquals(NO_OBJECT, answer("SELECT $1 FROM @test_db...st"));
        assertEquals(NO_OBJECT, answer("SELECT $1 FROM @nosuchdb...st"));
        assertEquals(NO_OBJECT, answer("SELECT $1 FROM @test_db...nosuch"));
        assertEquals(NO_OBJECT, answer("SELECT $1 FROM @test_db..PUBLIC.st"));
        assertEquals(NO_OBJECT, answer("COPY INTO t FROM @test_db...st"));
    }

    @Test
    public void aTableStageWithAnEmptySchemaNamesNoSchema() {
        assertEquals(hinted(NO_SCHEMA), answer("COPY INTO t FROM @test_db..%t"));
        assertEquals(hinted(NO_SCHEMA), answer("COPY INTO @test_db..%t FROM t"));
        assertEquals(hinted(NO_SCHEMA), answer("SELECT $1 FROM @test_db..st"));
    }

    @Test
    public void aQueryReadsAnEmptySchemaTableStageAsItsTable() {
        assertEquals("SQL compilation error:|Object 'TEST_DB.\"\".T' does not exist or not authorized.",
            answer("SELECT $1 FROM @test_db..%t"));
        assertEquals("SQL compilation error:|Object 'TEST_DB.\"\".T' does not exist or not authorized.",
            answer("SELECT $1 FROM @test_db..%t/x"));
        assertEquals("SQL compilation error:|Object 'NOSUCHDB.\"\".T' does not exist or not authorized.",
            answer("SELECT $1 FROM @nosuchdb..%t"));
        assertEquals("SQL compilation error:|Object '\"\".\"\".T' does not exist or not authorized.",
            answer("SELECT $1 FROM @..%t"));
    }
}
