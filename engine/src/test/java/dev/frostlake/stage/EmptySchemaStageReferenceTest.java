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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A named stage written with its schema part left empty, {@code @db..st}, names no schema: where an object
 * name reads the empty part as the PUBLIC schema, a stage reference is refused as
 * {@code Schema 'DB.""' does not exist or not authorized.} — in a query over the stage, with a path,
 * metadata columns, parameters or an alias, in a COPY from it or into it, and in LIST — once its database
 * resolves; a missing database is refused as that. Every cell is live-verified.
 */
public class EmptySchemaStageReferenceTest extends BaseDatabaseTest {

    private static final String DB = "FL_EMPTY_STAGE_SCHEMA";

    @BeforeEach
    public void createStage() {
        engine.execute("CREATE OR REPLACE DATABASE " + DB);
        engine.execute("USE SCHEMA " + DB + ".PUBLIC");
        engine.execute("CREATE OR REPLACE STAGE st");
        engine.execute("CREATE OR REPLACE TABLE t (a VARCHAR)");
    }

    @AfterEach
    public void dropDatabase() {
        engine.execute("DROP DATABASE IF EXISTS " + DB);
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
    public void anEmptySchemaPartNamesNoSchema() {
        final String noSchema = hinted("SQL compilation error:|Schema '" + DB + ".\"\"' does not exist or not authorized.");
        final String[] refused = {
            "SELECT $1 FROM @" + DB + "..st",
            "SELECT $1 FROM @" + DB + "..st/path/file.csv",
            "SELECT $1 FROM @\"" + DB + "\"..st",
            "SELECT METADATA$FILENAME FROM @" + DB + "..st",
            "SELECT $1 FROM @" + DB + "..st (FILE_FORMAT => 'ff')",
            "SELECT $1 FROM @" + DB + "..st t1",
            "SELECT $1 FROM @" + DB + "..nosuch",
            "COPY INTO t FROM @" + DB + "..st",
            "COPY INTO t FROM @" + DB + "..st FILES = ('a.csv')",
            "COPY INTO @" + DB + "..st FROM (SELECT 1)",
            "LIST @" + DB + "..st",
            "LIST @" + DB + "..st PATTERN = '.*'",
        };
        for (final String sql : refused) {
            assertEquals(noSchema, answer(sql), sql);
        }
        assertEquals(hinted("SQL compilation error:|Database 'NOSUCH_STAGE_DB' does not exist or not authorized."),
            answer("LIST @nosuch_stage_db..st"));
        assertEquals(hinted("SQL compilation error:|Database 'NOSUCH_STAGE_DB' does not exist or not authorized."),
            answer("SELECT $1 FROM @nosuch_stage_db..st"));
        assertEquals("", answer("SELECT $1 FROM @" + DB + ".public.st"));
        assertEquals("", answer("LIST @" + DB + ".public.st"));
        assertEquals("", answer("SELECT $1 FROM @st"));
    }
}
