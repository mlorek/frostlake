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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CTAS with a typed column list WRITES the selected values into the declared types, as an INSERT does: a
 * number is rounded to the column's scale, text is parsed into the column's type, and a value that does not
 * fit is refused inside the DML envelope that names the table one level up (DB.SCHEMA.T) and the column.
 * A NULL in a NOT NULL column is the bare "NULL result in a non-nullable column". Every cell is live-verified.
 */
public class CtasColumnListWriteTest extends BaseDatabaseTest {

    private static final String ENVELOPE = "DML operation to table TEST_DB.TEST_SCHEMA.";

    private String first(final String sql) {
        final StringBuilder out = new StringBuilder();
        final java.util.List<Object> values = engine.executeQuery(sql).getRows().get(0).getValues();
        for (int c = 0; c < values.size(); c++) {
            if (c > 0) {
                out.append(", ");
            }
            out.append(values.get(c));
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), sql + " -> " + refused.getMessage());
    }

    @Test
    public void aValueIsWrittenIntoItsDeclaredType() {
        engine.execute("CREATE TABLE t1 (x NUMBER(5,1)) AS SELECT 1.25");
        assertEquals("1.3, NUMBER(5,1)[SB1]", first("SELECT x, SYSTEM$TYPEOF(x) FROM t1"));
        engine.execute("CREATE TABLE t5 (x DATE) AS SELECT '2024-01-02'");
        assertEquals("2024-01-02, DATE", first("SELECT x, TYPEOF(x::VARIANT) FROM t5"));
        engine.execute("CREATE TABLE t6 (x NUMBER(10,2)) AS SELECT '3.456'");
        assertEquals("3.46", first("SELECT x FROM t6"));
        engine.execute("CREATE TABLE t12 (x NUMBER(5,2)) AS SELECT 1.005");
        assertEquals("1.01", first("SELECT x FROM t12"));
        engine.execute("CREATE TABLE t13 (x VARCHAR) AS SELECT 12");
        assertEquals("12, 2", first("SELECT x, LENGTH(x) FROM t13"));
        engine.execute("CREATE TABLE t16 (x NUMBER(5,1), y VARCHAR(3)) AS SELECT 2.46, 'abc'");
        assertEquals("2.5, abc", first("SELECT x, y FROM t16"));
        engine.execute("CREATE TABLE t7 (x BOOLEAN) AS SELECT 'yes'");
        assertEquals("true", first("SELECT x FROM t7").toLowerCase());
        engine.execute("CREATE TABLE t15 (x FLOAT) AS SELECT '1e3'");
        assertEquals(1000.0, Double.parseDouble(first("SELECT x FROM t15")));
        engine.execute("CREATE TABLE t17 (x TIMESTAMP_NTZ) AS SELECT '2024-01-02 03:04:05'");
        assertEquals("2024-01-02 03:04:05", first("SELECT TO_VARCHAR(x, 'YYYY-MM-DD HH24:MI:SS') FROM t17"));
    }

    @Test
    public void aValueThatDoesNotFitIsRefusedInTheDmlEnvelope() {
        assertRefused("CREATE TABLE t2 (x NUMBER(2,0)) AS SELECT 1000", ENVELOPE
            + "T2 failed on column X with error: Number out of representable range: type FIXED[SB2](2,0){nullable}, value 1000");
        assertRefused("CREATE TABLE t3 (x VARCHAR(2)) AS SELECT 'abcd'", ENVELOPE
            + "T3 failed on column X with error: String 'abcd' is too long and would be truncated");
        assertRefused("CREATE TABLE t4 (x DATE) AS SELECT 'nope'", ENVELOPE
            + "T4 failed on column X with error: Date 'nope' is not recognized");
        assertRefused("CREATE TABLE t9 (x NUMBER(2,0)) AS SELECT 99 UNION ALL SELECT 100", ENVELOPE
            + "T9 failed on column X with error: Number out of representable range: type FIXED[SB1](2,0){nullable}, value 100");
        assertRefused("CREATE TABLE t14 (x NUMBER) AS SELECT 'abc'", ENVELOPE
            + "T14 failed on column X with error: Numeric value 'abc' is not recognized");
        assertRefused("CREATE TABLE t8 (x INT NOT NULL) AS SELECT NULL", "NULL result in a non-nullable column");
    }
}
