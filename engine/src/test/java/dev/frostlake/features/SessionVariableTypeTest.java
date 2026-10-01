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

package dev.frostlake.features;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A session variable carries the type of the VALUE it holds — a variable remembers a value, not an
 * expression, so {@code SET n = 2 + 3} is NUMBER(1,0) for the 5 it holds and {@code SET s = 'a' || 'bc'}
 * is VARCHAR(3) — and a decimal stays exact, trailing zeros dropped. {@code SYSTEM$STREAM_HAS_DATA} is
 * BOOLEAN. Both used to have no static type at all, which declared them VARCHAR on every result and let
 * a numeric variable stand as a predicate (live-verified).
 */
public class SessionVariableTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO t VALUES (1, TRUE)");
        engine.execute("CREATE OR REPLACE STREAM s ON TABLE t");
    }

    /** Each row's cells joined by commas, rows by bars. */
    private String rows(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int r = 0; r < result.getRowCount(); r++) {
            text.append(r > 0 ? " | " : "");
            for (int c = 0; c < result.getColumnCount(); c++) {
                text.append(c > 0 ? ", " : "").append(result.getRows().get(r).getValue(c));
            }
        }
        return text.toString();
    }

    /** The first column's declared type, as the result reports it. */
    private String declared(final String sql) {
        return engine.executeQuery(sql).getColumns().get(0).getDataType().getName();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void aVariableCarriesTheTypeOfTheValueItHolds() {
        engine.execute("SET flag = TRUE");
        engine.execute("SET num = 5");
        engine.execute("SET big = 12345678901234567890123");
        engine.execute("SET dec = 1.5");
        engine.execute("SET txt = 'abc'");
        engine.execute("SET dt = '2026-01-02'::DATE");
        engine.execute("SET ts = '2026-01-02 03:04:05'::TIMESTAMP_NTZ");
        engine.execute("SET bin = TO_BINARY('AB', 'HEX')");
        engine.execute("SET fl = 1.5::FLOAT");
        assertEquals("BOOLEAN[SB1]", rows("SELECT SYSTEM$TYPEOF($flag)"));
        assertEquals("NUMBER(1,0)[SB1]", rows("SELECT SYSTEM$TYPEOF($num)"));
        assertEquals("NUMBER(23,0)[SB16]", rows("SELECT SYSTEM$TYPEOF($big)"));
        assertEquals("NUMBER(2,1)[SB1]", rows("SELECT SYSTEM$TYPEOF($dec)"));
        assertEquals("VARCHAR(3)[LOB]", rows("SELECT SYSTEM$TYPEOF($txt)"));
        assertEquals("DATE[SB4]", rows("SELECT SYSTEM$TYPEOF($dt)"));
        assertEquals("TIMESTAMP_NTZ(9)[SB16]", rows("SELECT SYSTEM$TYPEOF($ts)"));
        assertEquals("BINARY[LOB]", rows("SELECT SYSTEM$TYPEOF($bin)"));
        assertEquals("FLOAT[DOUBLE]", rows("SELECT SYSTEM$TYPEOF($fl)"));
        // The value the variable remembers, not the expression that made it.
        engine.execute("SET sum = 2 + 3");
        engine.execute("SET cat = 'a' || 'bc'");
        engine.execute("SET zeros = 1.500000");
        assertEquals("NUMBER(1,0)[SB1]", rows("SELECT SYSTEM$TYPEOF($sum)"));
        assertEquals("VARCHAR(3)[LOB]", rows("SELECT SYSTEM$TYPEOF($cat)"));
        assertEquals("1.5", rows("SELECT $zeros"));
        assertEquals("NUMBER(2,1)[SB1]", rows("SELECT SYSTEM$TYPEOF($zeros)"));
        // A variable set from NULL carries no type, as it always did.
        engine.execute("SET nothing = NULL");
        assertEquals("NULL[LOB]", rows("SELECT SYSTEM$TYPEOF($nothing)"));
    }

    @Test
    public void theTypeReachesTheResultColumnAndThePredicateRule() {
        engine.execute("SET flag = TRUE");
        engine.execute("SET num = 5");
        assertEquals("BOOLEAN", declared("SELECT $flag"));
        assertEquals("NUMBER", declared("SELECT $num"));
        assertEquals("BOOLEAN", declared("SELECT SYSTEM$STREAM_HAS_DATA('s')"));
        assertEquals("BOOLEAN[SB1]", rows("SELECT SYSTEM$TYPEOF(SYSTEM$STREAM_HAS_DATA('s'))"));
        // A boolean variable stands as a predicate; a numeric one is refused, named canonically.
        assertEquals("1", rows("SELECT 1 FROM t WHERE $flag"));
        assertEquals("SQL compilation error:\nInvalid data type [NUMBER(1,0)] for predicate [$NUM]",
            refusal("SELECT 1 FROM t WHERE $num"));
        // A stream's own predicate is a boolean, so it is not refused.
        assertEquals("", rows("SELECT 1 FROM t WHERE SYSTEM$STREAM_HAS_DATA('s')"));
    }
}
