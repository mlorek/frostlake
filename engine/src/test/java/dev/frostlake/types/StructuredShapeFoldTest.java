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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structured types fold by shape, as Snowflake documents their coercion: two shapes meet only where one coerces to
 * the other. Comparing class and plain name made ARRAY(INT) and ARRAY(VARCHAR), or OBJECT(a INT) and OBJECT(b
 * VARCHAR), one type, so a UNION or a conditional over them declared the first shape, and its column held values that
 * did not fit it.
 */
public class StructuredShapeFoldTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE sf_db");
        engine.execute("USE SCHEMA sf_db.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String typeOf(final String table) {
        final Row column = engine.executeQuery("DESCRIBE TABLE " + table).getRows().get(0);
        return String.valueOf(column.getValue(1));
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void shapesThatDoNotCoerceAreRefusedRatherThanFoldedToTheFirst() {
        final String setOperator = "inconsistent data type for result columns for set operator input branches";
        assertTrue(refusal("CREATE TABLE u AS SELECT [1]::ARRAY(INT) c UNION ALL SELECT ['x']::ARRAY(VARCHAR)")
            .contains(setOperator));
        assertTrue(refusal("SELECT {'a':1}::OBJECT(a INT) c UNION ALL SELECT {'b':'x'}::OBJECT(b VARCHAR)")
            .contains(setOperator));
        assertTrue(refusal("SELECT {'a':'k'}::MAP(VARCHAR, INT) c UNION ALL SELECT {'a':'x'}::MAP(VARCHAR, VARCHAR)")
            .contains(setOperator));
        assertTrue(refusal("SELECT IFF(TRUE, {'a':1}::OBJECT(a INT), {'b':'x'}::OBJECT(b VARCHAR))")
            .contains("Can not convert parameter"));
        assertTrue(refusal("SELECT COALESCE([1]::ARRAY(INT), ['x']::ARRAY(VARCHAR))")
            .contains("Can not convert parameter"));
    }

    @Test
    public void shapesThatCoerceFoldToOneThatHoldsBoth() {
        engine.execute("CREATE TABLE u1 AS SELECT [1.5]::ARRAY(NUMBER(2,1)) c UNION ALL SELECT [2]::ARRAY(INT)");
        assertEquals("ARRAY(NUMBER(38,1))", typeOf("u1"));
        engine.execute("CREATE TABLE u2 AS SELECT {'a':1}::OBJECT(a INT) c UNION ALL SELECT {'a':2.5}::OBJECT(a FLOAT)");
        assertEquals("OBJECT(a FLOAT)", typeOf("u2"));
        engine.execute("CREATE TABLE u3 AS SELECT IFF(TRUE, [1.5]::ARRAY(NUMBER(2,1)), [2]::ARRAY(INT)) c");
        assertEquals("ARRAY(NUMBER(38,1))", typeOf("u3"));
        engine.execute("CREATE TABLE u4 AS SELECT [1]::ARRAY(INT) c UNION ALL SELECT [2]::ARRAY(INT)");
        assertEquals("ARRAY(NUMBER(38,0))", typeOf("u4"), "one shape is still its own fold");
    }
}
