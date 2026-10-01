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
 * Two stages, or two tables, whose names differ only in case own two stages: a file unloaded to one is listed and
 * read through that one alone, an unquoted reference reaches the upper-case stage, re-creating one leaves the
 * other's files, and each table loads from its own stage. A path that holds no staged file reads as no rows.
 * Every cell is live-verified.
 */
public class StageNameCaseTest extends BaseDatabaseTest {

    private static final String DB = "FL_STAGE_NAME_CASE";
    private static final String CSV = " FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE) SINGLE = TRUE";

    @BeforeEach
    public void createDatabase() {
        engine.execute("CREATE OR REPLACE DATABASE " + DB);
        engine.execute("USE SCHEMA " + DB + ".PUBLIC");
    }

    @AfterEach
    public void dropDatabase() {
        engine.execute("DROP DATABASE IF EXISTS " + DB);
        engine.execute("USE SCHEMA test_db.test_schema");
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
    public void twoStagesDifferingOnlyInCaseKeepTheirOwnFiles() {
        engine.execute("CREATE STAGE ST");
        engine.execute("CREATE STAGE \"st\"");
        engine.execute("COPY INTO @ST/upper/f FROM (SELECT 1 AS a)" + CSV);
        engine.execute("COPY INTO @\"st\"/lower/f FROM (SELECT 2 AS a)" + CSV);
        assertEquals("st/upper/f", answer("LIST @ST"));
        assertEquals("st/lower/f", answer("LIST @\"st\""));
        assertEquals("st/upper/f", answer("LIST @st"));
        assertEquals("1", answer("SELECT $1 FROM @ST/upper/f"));
        assertEquals("2", answer("SELECT $1 FROM @\"st\"/lower/f"));
        assertEquals("0", answer("SELECT COUNT(*) FROM @\"st\"/upper/f"));
        assertEquals("", answer("SELECT $1 FROM @\"st\"/upper/f"));

        // Re-creating one stage empties it alone.
        engine.execute("DROP STAGE \"st\"");
        engine.execute("CREATE STAGE \"st\"");
        assertEquals("", answer("LIST @\"st\""));
        assertEquals("st/upper/f", answer("LIST @ST"));
    }

    @Test
    public void twoTablesDifferingOnlyInCaseKeepTheirOwnStages() {
        engine.execute("CREATE TABLE T (a INT)");
        engine.execute("CREATE TABLE \"t\" (a INT)");
        engine.execute("COPY INTO @%T/tf FROM (SELECT 3 AS a)" + CSV);
        engine.execute("COPY INTO @%\"t\"/tg FROM (SELECT 4 AS a)" + CSV);
        assertEquals("3", answer("SELECT $1 FROM @%T/tf"));
        assertEquals("0", answer("SELECT COUNT(*) FROM @%\"t\"/tf"));
        assertEquals("4", answer("SELECT $1 FROM @%\"t\"/tg"));
        engine.execute("COPY INTO \"t\" FROM @%\"t\" FILE_FORMAT = (TYPE = CSV)");
        engine.execute("COPY INTO T FROM @%T FILE_FORMAT = (TYPE = CSV)");
        assertEquals("4", answer("SELECT a FROM \"t\""));
        assertEquals("3", answer("SELECT a FROM T"));
    }
}
