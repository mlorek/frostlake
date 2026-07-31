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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code FOR x IN [a, b] DO … END FOR} iterates the ARRAY literal's elements, binding the loop
 * variable to each element's value. An ARRAY value in this engine is canonical JSON text, which
 * matched no iteration branch and silently ran ZERO times — a loader's dual-pass
 * {@code FOR flag IN [False, True]} then executed neither pass. Boolean loop values must also bind
 * as real BOOLEAN literals: quoting them handed IFF a non-empty (truthy) string, so a FALSE flag
 * took the TRUE branch.
 */
public class ForOverArrayLiteralTest extends BaseDatabaseTest {

    private static final String FOR_OVER_ARRAY_BINDS =
        "binds the loop variable of a `FOR x IN [array literal]` loop into the body's SQL, which is a "
        + "Frostlake extension: a real account's FOR takes a range or a cursor / RESULTSET, so the body's "
        + "binds are never set there (\"Bind variable :x not set\")";

    @Test
    public void iteratesBooleanArrayWithCorrectBinding() {
        Assumptions.assumeFalse(isLiveSnowflake(), FOR_OVER_ARRAY_BINDS);
        engine.execute("CREATE TABLE loop_out (val VARCHAR, branch VARCHAR)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE dual_pass() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE flag BOOLEAN;
            BEGIN
              FOR x IN [False, True] DO
                IF (:x = False) THEN flag := False; ELSE flag := True; END IF;
                INSERT INTO loop_out SELECT :x::VARCHAR, IFF(:flag, 'cluster', 'leaf');
              END FOR;
              RETURN 'done';
            END $$""");
        engine.execute("CALL dual_pass()");
        final ResultSet rs = engine.executeQuery("SELECT val, branch FROM loop_out ORDER BY val");
        assertEquals(2, rs.getRowCount());
        assertEquals("false", String.valueOf(rs.getRows().get(0).getValue(0)));
        assertEquals("leaf", String.valueOf(rs.getRows().get(0).getValue(1)));
        assertEquals("true", String.valueOf(rs.getRows().get(1).getValue(0)));
        assertEquals("cluster", String.valueOf(rs.getRows().get(1).getValue(1)));
    }

    @Test
    public void iteratesStringAndNumberArrays() {
        Assumptions.assumeFalse(isLiveSnowflake(), FOR_OVER_ARRAY_BINDS);
        engine.execute("CREATE TABLE loop_out2 (val VARCHAR)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE walk_lists() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              FOR s IN ['alpha', 'beta'] DO
                INSERT INTO loop_out2 SELECT :s;
              END FOR;
              FOR n IN [10, 20, 30] DO
                INSERT INTO loop_out2 SELECT :n::VARCHAR;
              END FOR;
              RETURN 'done';
            END $$""");
        engine.execute("CALL walk_lists()");
        assertEquals(5, engine.executeQuery("SELECT val FROM loop_out2").getRowCount());
        assertEquals(1, engine.executeQuery("SELECT val FROM loop_out2 WHERE val = 'beta'").getRowCount());
        assertEquals(1, engine.executeQuery("SELECT val FROM loop_out2 WHERE val = '20'").getRowCount());
    }

    @Test
    public void emptyArrayRunsZeroIterations() {
        engine.execute("CREATE TABLE loop_out3 (val VARCHAR)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE walk_empty() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              FOR s IN [] DO
                INSERT INTO loop_out3 SELECT 'never';
              END FOR;
              RETURN 'done';
            END $$""");
        engine.execute("CALL walk_empty()");
        assertEquals(0, engine.executeQuery("SELECT val FROM loop_out3").getRowCount());
    }

    @Test
    public void iffRejectsNonBooleanConditionsAtCompileTime() {
        // Live-verified: Snowflake REJECTS VARCHAR and NUMBER conditions in IFF at compile time
        // ("Invalid argument types for function 'IFF'"); only BOOLEAN/VARIANT conditions compile.
        final RuntimeException text = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT IFF('false', 'a', 'b')");
            }
        });
        assertTrue(text.getMessage().contains("Invalid argument types for function 'IFF'"),
            "unexpected: " + text.getMessage());
        assertEquals("b", String.valueOf(
            engine.executeQuery("SELECT IFF(1 = 2, 'a', 'b')").getRows().get(0).getValue(0)));
        assertEquals("b", String.valueOf(
            engine.executeQuery("SELECT IFF(NULL, 'a', 'b')").getRows().get(0).getValue(0)));
    }
}
