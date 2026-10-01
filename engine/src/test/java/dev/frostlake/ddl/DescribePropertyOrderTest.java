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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A DESCRIBE's properties are judged one by one in the order written, each name before its value: {@code x = $nosuch}
 * is the invalid parameter, not the missing variable. A TYPE given a session variable reads the variable's value —
 * text naming STAGE or COLUMNS, in any case — and any other value is refused as the variable written. Every cell is
 * live-verified.
 */
public class DescribePropertyOrderTest extends BaseDatabaseTest {

    private static final String INVALID_X = "SQL compilation error:|invalid parameter 'x'";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t1 (x INT)");
        engine.execute("CREATE VIEW v1 AS SELECT 1 AS x");
        engine.execute("CREATE STAGE st");
    }

    /** The first row's first cell, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final List<Row> rows = engine.executeQuery(sql).getRows();
            return rows.isEmpty() ? "" : String.valueOf(rows.get(0).getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void aPropertysNameIsJudgedBeforeItsValue() {
        assertEquals(INVALID_X, answer("DESCRIBE TABLE t1 x = $nosuch"));
        assertEquals(INVALID_X, answer("DESCRIBE VIEW v1 x = $nosuch"));
        assertEquals(INVALID_X, answer("DESCRIBE STAGE st x = $nosuch"));
        assertEquals(INVALID_X, answer("DESCRIBE SCHEMA test_schema x = $nosuch"));
        assertEquals(INVALID_X, answer("DESCRIBE DATABASE test_db x = $nosuch"));
        assertEquals(INVALID_X, answer("DESCRIBE TABLE t1 type = columns x = $nosuch"));
        assertEquals(INVALID_X, answer("DESCRIBE TABLE t1 x = $nosuch type = columns"));
        assertEquals(INVALID_X, answer("DESCRIBE TABLE t1 x = 1 type = $nosuch"));
        assertEquals("SQL compilation error: error line 1 at position 25|Session variable '$NOSUCH' does not exist",
            answer("DESCRIBE TABLE t1 type = $nosuch x = 1"));
        assertEquals("SQL compilation error: error line 1 at position 25|Session variable '$NOSUCH' does not exist",
            answer("DESCRIBE TABLE t1 type = $nosuch"));
        assertEquals("SQL compilation error:|syntax error line 1 at position 27 unexpected '('.",
            answer("DESCRIBE TABLE t1 x = UPPER('a')"));
    }

    @Test
    public void aTypeReadsItsVariable() {
        engine.execute("SET abc = 'columns'");
        assertEquals("X", answer("DESCRIBE TABLE t1 type = $abc"));
        assertEquals("X", answer("DESCRIBE VIEW v1 type = $abc"));
        assertEquals(INVALID_X, answer("DESCRIBE TABLE t1 TYPE = $abc x = 1"));
        engine.execute("SET abc = 'Columns'");
        assertEquals("X", answer("DESCRIBE TABLE t1 type = $abc"));
        engine.execute("SET abc = 'STAGE'");
        assertEquals("STAGE_FILE_FORMAT", answer("DESCRIBE TABLE t1 type = $abc"));
        assertEquals(INVALID_X, answer("DESCRIBE TABLE t1 x = $abc"));
    }

    @Test
    public void anyOtherValueIsRefusedAsWritten() {
        engine.execute("SET abc = 'bogus'");
        assertEquals("SQL compilation error:|invalid value [$abc] for parameter 'TYPE'",
            answer("DESCRIBE TABLE t1 type = $abc"));
        engine.execute("SET abc = TRUE");
        assertEquals("SQL compilation error:|invalid value [$abc] for parameter 'TYPE'",
            answer("DESCRIBE TABLE t1 type = $abc"));
        engine.execute("SET abc = NULL");
        assertEquals("SQL compilation error:|invalid value [$abc] for parameter 'TYPE'",
            answer("DESCRIBE TABLE t1 type = $abc"));
        engine.execute("SET abc = 1");
        assertEquals("SQL compilation error:|invalid type [$abc] for parameter 'TYPE'",
            answer("DESCRIBE TABLE t1 type = $abc"));
        engine.execute("SET abc = 1.5");
        assertEquals("SQL compilation error:|invalid type [$abc] for parameter 'TYPE'",
            answer("DESCRIBE TABLE t1 type = $abc"));
        assertEquals("SQL compilation error:|invalid value [-1] for parameter 'TYPE'",
            answer("DESCRIBE TABLE t1 type = -1"));
        assertEquals("Invalid value specified for property 'TYPE'", answer("DESCRIBE TABLE t1 type = ($abc)"));
    }
}
