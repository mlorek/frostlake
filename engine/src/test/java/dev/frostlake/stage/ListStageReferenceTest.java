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
 * LIST takes any stage reference — a named stage, the user's stage and a table's, qualified or not — with a path
 * that is a prefix of the files' stage-relative paths. A named stage is matched exactly and its files are named after
 * the stage's own name, lower-cased; a user's or a table's stage lists the bare relative path; a table stage of a
 * missing table is a missing stage named after it. PATTERN matches a file's path under the stage's own folder, the
 * way the account stores it. Every cell is live-verified; the sizes differ (the account stores its files padded), so
 * only the names are compared.
 */
public class ListStageReferenceTest extends BaseDatabaseTest {

    private static final String CSV = " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (x INT)");
        engine.execute("CREATE STAGE st");
        engine.execute("CREATE STAGE \"MySt\"");
        engine.execute("COPY INTO @st/sub/f.csv FROM (SELECT 1 AS x)" + CSV);
        engine.execute("COPY INTO @st/other/g.csv FROM (SELECT 2 AS x)" + CSV);
        engine.execute("COPY INTO @%t/tsub/h.csv FROM (SELECT 3 AS x)" + CSV);
        engine.execute("COPY INTO @\"MySt\"/q/i.csv FROM (SELECT 4 AS x)" + CSV);
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

    @Test
    public void aPathIsAPrefix() {
        assertEquals("st/other/g.csv | st/sub/f.csv", answer("LIST @st"));
        assertEquals("st/other/g.csv | st/sub/f.csv", answer("LIST @st/"));
        assertEquals("st/sub/f.csv", answer("LIST @st/sub"));
        assertEquals("st/sub/f.csv", answer("LIST @st/sub/"));
        assertEquals("st/sub/f.csv", answer("LIST @st/su"));
        assertEquals("st/sub/f.csv", answer("LIST @st/sub/f.csv"));
        assertEquals("", answer("LIST @st/nothing"));
        assertEquals("st/sub/f.csv", answer("LS @st/sub"));
    }

    @Test
    public void aNamedStagesFilesCarryItsOwnName() {
        assertEquals("st/other/g.csv | st/sub/f.csv", answer("LIST @ST"));
        assertEquals("st/other/g.csv | st/sub/f.csv", answer("LIST @test_schema.st"));
        assertEquals("st/other/g.csv | st/sub/f.csv", answer("LIST @TEST_DB.TEST_SCHEMA.ST"));
        assertEquals("st/sub/f.csv", answer("LIST @test_db.test_schema.st/sub"));
        assertEquals("myst/q/i.csv", answer("LIST @\"MySt\""));
    }

    @Test
    public void aNamedStageIsMatchedExactly() {
        assertEquals(hinted("SQL compilation error:|Stage 'TEST_DB.TEST_SCHEMA.\"st\"' does not exist or not authorized."),
            answer("LIST @\"st\""));
        assertEquals(hinted("SQL compilation error:|Stage 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized."),
            answer("LIST @nosuch/x"));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.NOSCH' does not exist or not authorized."),
            answer("LIST @nosch.st"));
        assertEquals(hinted("SQL compilation error:|Database 'NODB' does not exist or not authorized."),
            answer("LIST @nodb.public.st"));
    }

    @Test
    public void tableAndUserStagesListTheBarePath() {
        assertEquals("tsub/h.csv", answer("LIST @%t"));
        assertEquals("tsub/h.csv", answer("LIST @%T"));
        assertEquals("tsub/h.csv", answer("LIST @%t/tsub"));
        assertEquals("", answer("LIST @%t/nothing"));
        assertEquals("tsub/h.csv", answer("LIST @test_db.test_schema.%t"));
        assertEquals(hinted("SQL compilation error:|Stage 'TEST_DB.TEST_SCHEMA.\"%NOSUCH\"' does not exist or not authorized."),
            answer("LIST @%nosuch"));
        assertEquals(hinted("SQL compilation error:|Stage 'TEST_DB.TEST_SCHEMA.\"%t\"' does not exist or not authorized."),
            answer("LIST @%\"t\""));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.NOSCH' does not exist or not authorized."),
            answer("LIST @test_db.nosch.%t"));
        engine.execute("COPY INTO @~/fl_list_stage_reference/j.csv FROM (SELECT 5 AS x)" + CSV + " OVERWRITE = TRUE");
        assertEquals("fl_list_stage_reference/j.csv", answer("LIST @~/fl_list_stage_reference"));
    }

    @Test
    public void patternMatchesThePathUnderTheStagesFolder() {
        assertEquals("st/other/g.csv | st/sub/f.csv", answer("LIST @st PATTERN = '.*g.*'"));
        assertEquals("st/other/g.csv | st/sub/f.csv", answer("LIST @st PATTERN = 'stages/.*'"));
        assertEquals("st/sub/f.csv", answer("LIST @st PATTERN = '.*sub/f.csv'"));
        assertEquals("st/sub/f.csv", answer("LIST @st/sub PATTERN = '.*'"));
        assertEquals("", answer("LIST @st PATTERN = 'st/.*'"));
        assertEquals("", answer("LIST @st PATTERN = 'sub/.*'"));
        assertEquals("tsub/h.csv", answer("LIST @%t PATTERN = '.*'"));
    }
}
