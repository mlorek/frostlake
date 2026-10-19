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

package dev.frostlake.types;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A MAP's key and value types take their parameters wherever a type is written — a cast, a column, a routine's
 * signature, a nested shape — as an ARRAY element and an OBJECT field always did. {@code MAP(VARCHAR, NUMBER(10,2))}
 * and {@code MAP(VARCHAR(5), NUMBER)} were syntax errors, while {@code MAP(NUMBER, ARRAY(VARCHAR(3)))} parsed.
 */
public class MapTypeParametersTest extends BaseDatabaseTest {

    private String scalarText(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aCastTakesTheKeysAndTheValuesParameters() {
        // Live-verified: SYSTEM$TYPEOF reads MAP(VARCHAR(5), NUMBER(38,0)) and the value is rounded to the scale.
        assertEquals("MAP(VARCHAR(5), NUMBER(38,0))[LOB]",
            scalarText("SELECT SYSTEM$TYPEOF({'a': 1}::MAP(VARCHAR(5), NUMBER))"));
        assertTrue(scalarText("SELECT SYSTEM$TYPEOF({'a': 1}::MAP(VARCHAR, NUMBER(10,2)))")
            .endsWith(", NUMBER(10,2))[LOB]"));
        assertEquals("{\"a\":1.24}", scalarText("SELECT {'a': 1.239}::MAP(VARCHAR, NUMBER(10,2))"));
        assertEquals("MAP(VARCHAR(5), NUMBER(10,2))[LOB]",
            scalarText("SELECT SYSTEM$TYPEOF(TRY_CAST(NULL AS MAP(VARCHAR(5), NUMBER(10,2))))"));
        assertEquals("MAP(NUMBER(38,0), VARCHAR(10))[LOB]",
            scalarText("SELECT SYSTEM$TYPEOF(CAST(NULL AS MAP(NUMBER(38,0), VARCHAR(10))))"));
    }

    @Test
    public void aColumnKeepsTheParametersItDeclared() {
        engine.execute("CREATE TABLE m (c MAP(VARCHAR, NUMBER(38,0)), d MAP(VARCHAR(5), TIMESTAMP_NTZ(3)),"
            + " e MAP(NUMBER(5,0), ARRAY(VARCHAR(3))))");
        assertEquals("MAP(VARCHAR(16777216), NUMBER(38,0))", describeCell("m", "C", "type"));
        assertEquals("MAP(VARCHAR(5), TIMESTAMP_NTZ(3))", describeCell("m", "D", "type"));
        assertEquals("MAP(NUMBER(5,0), ARRAY(VARCHAR(3)))", describeCell("m", "E", "type"));
        engine.execute("INSERT INTO m (c) SELECT {'a': 1}::MAP(VARCHAR, NUMBER(38,0))");
        assertEquals("{\"a\":1}", scalarText("SELECT c FROM m"));
    }

    @Test
    public void aRoutineSignatureTakesTheParameters() {
        engine.execute("CREATE FUNCTION mf(x MAP(VARCHAR(5), NUMBER(10,2))) RETURNS MAP(VARCHAR, NUMBER(10,2))"
            + " AS $$ x $$");
        assertEquals("MAP(VARCHAR(5), NUMBER(10,2))[LOB]",
            scalarText("SELECT SYSTEM$TYPEOF(mf({'a': 1.5}::MAP(VARCHAR(5), NUMBER(10,2))))"));
        assertEquals("{\"a\":1.5}", scalarText("SELECT mf({'a': 1.5}::MAP(VARCHAR(5), NUMBER(10,2)))"));
    }

    @Test
    public void theParametersSurviveAnEngineClone() throws Exception {
        engine.execute("CREATE TABLE mc (m MAP(VARCHAR(5), NUMBER(10,2)))");
        engine.execute("CREATE FUNCTION mcf(x MAP(VARCHAR(5), NUMBER(10,2))) RETURNS MAP(VARCHAR(5), NUMBER(10,2))"
            + " AS $$ x $$");
        final DatabaseEngine clone = engine.cloneInstance();
        try {
            final ResultSet described = clone.executeQuery("DESCRIBE TABLE mc");
            assertEquals("MAP(VARCHAR(5), NUMBER(10,2))", cell(described, soleRowWhere(described, "name", "M"), "type"));
            assertEquals("MAP(VARCHAR(5), NUMBER(10,2))[LOB]", String.valueOf(clone.executeQuery(
                "SELECT SYSTEM$TYPEOF(mcf({'a': 1.5}::MAP(VARCHAR(5), NUMBER(10,2))))").getRows().get(0).getValue(0)));
        } finally {
            clone.shutdown();
        }
    }

    @Test
    public void aNestedMapTakesTheParametersToo() {
        assertEquals("ARRAY(MAP(VARCHAR(2), NUMBER(3,0)))[LOB]",
            scalarText("SELECT SYSTEM$TYPEOF([{'a': 1}]::ARRAY(MAP(VARCHAR(2), NUMBER(3,0))))"));
        assertEquals("OBJECT(o MAP(VARCHAR(2), NUMBER(3,0)))[LOB]",
            scalarText("SELECT SYSTEM$TYPEOF({'o': {'a': 1}}::OBJECT(o MAP(VARCHAR(2), NUMBER(3,0))))"));
        assertEquals("MAP(VARCHAR(3), OBJECT(x NUMBER(4,1)))[LOB]",
            scalarText("SELECT SYSTEM$TYPEOF(CAST({'a': {'x': 1}} AS MAP(VARCHAR(3), OBJECT(x NUMBER(4,1)))))"));
    }

    @Test
    public void aMapStillRefusesWhatItRefusedBefore() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT NULL::MAP(VARCHAR, FILE)");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT CAST(OBJECT_CONSTRUCT() AS MAP())");
            }
        });
    }
}
