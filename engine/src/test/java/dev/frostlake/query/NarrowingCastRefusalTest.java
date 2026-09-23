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
package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A narrowing string cast refuses a longer value wherever the value has to be converted: in a JOIN ON or a
 * WHERE that compares it with a column, under any other operator, and in HAVING. A filter answers without
 * converting where the value itself decides: an equality with a constant that fits, an IS NULL, a LIKE whose
 * fixed prefix the value does not start with, and a conjunct beside a constant FALSE. Every cell is
 * live-verified.
 */
public class NarrowingCastRefusalTest extends BaseDatabaseTest {

    private static final String TOO_LONG = "String 'abcdefgh' is too long and would be truncated";

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE DATABASE NARROWING_CAST_DB");
        engine.execute("CREATE OR REPLACE TABLE nc (s VARCHAR)");
        engine.execute("INSERT INTO nc VALUES ('abcdefgh')");
        engine.execute("CREATE OR REPLACE TABLE nc2 (t VARCHAR)");
        engine.execute("INSERT INTO nc2 VALUES ('x')");
    }

    @AfterEach
    public void dropTables() {
        engine.execute("DROP DATABASE IF EXISTS NARROWING_CAST_DB");
    }

    /** The first row's first cell, empty for no rows, or the refusal. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void aComparisonWithAColumnConvertsTheValue() {
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc JOIN nc2 ON CAST(nc.s AS VARCHAR(5)) = nc2.t"));
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc JOIN nc2 ON nc2.t = CAST(nc.s AS VARCHAR(5))"));
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc LEFT JOIN nc2 ON CAST(nc.s AS VARCHAR(5)) = nc2.t"));
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc JOIN nc2 ON nc.s::VARCHAR(5) = nc2.t"));
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc, nc2 WHERE CAST(nc.s AS VARCHAR(5)) = nc2.t"));
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc JOIN nc2 ON CAST(nc.s AS VARCHAR(5)) = CAST(nc2.t AS VARCHAR(5))"));
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc JOIN nc2 ON CAST(nc.s AS VARCHAR(5)) != nc2.t"));
        assertEquals("0", answer("SELECT COUNT(*) FROM nc JOIN nc2 ON CAST(nc.s AS VARCHAR(5)) = nc2.t AND 1 = 0"));
        assertEquals("0", answer("SELECT COUNT(*) FROM nc JOIN nc2 ON CAST(nc.s AS VARCHAR(5)) = 'x'"));
    }

    @Test
    public void havingConvertsTheValue() {
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc GROUP BY s HAVING CAST(s AS VARCHAR(5)) = 'x'"));
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc GROUP BY s HAVING CAST(MAX(s) AS VARCHAR(5)) = 'x'"));
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc GROUP BY s HAVING s::VARCHAR(5) = 'x'"));
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc GROUP BY s HAVING CAST(s AS VARCHAR(5)) = 'abcde'"));
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc GROUP BY s HAVING COUNT(*) > 0 AND CAST(s AS VARCHAR(5)) = 'x'"));
    }

    @Test
    public void aFilterAnswersWhereTheValueDecides() {
        assertEquals("0", answer("SELECT COUNT(*) FROM nc WHERE CAST(s AS VARCHAR(5)) = 'x'"));
        assertEquals("0", answer("SELECT COUNT(*) FROM nc WHERE 'x' = CAST(s AS VARCHAR(5))"));
        assertEquals("0", answer("SELECT COUNT(*) FROM nc WHERE CAST(s AS VARCHAR(5)) = 'x' AND LENGTH(s) > 0"));
        assertEquals("0", answer("SELECT COUNT(*) FROM nc WHERE CAST(s AS VARCHAR(5)) IS NULL"));
        assertEquals("0", answer("SELECT COUNT(*) FROM nc WHERE CAST(s AS VARCHAR(5)) LIKE 'x%'"));
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc WHERE CAST(s AS VARCHAR(5)) LIKE 'a%'"));
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc WHERE CAST(s AS VARCHAR(5)) > 'a'"));
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc WHERE LENGTH(CAST(s AS VARCHAR(5))) > 0"));
        assertEquals(TOO_LONG, answer("SELECT COUNT(*) FROM nc WHERE CAST(s AS VARCHAR(5)) <> 'x'"));
    }
}
