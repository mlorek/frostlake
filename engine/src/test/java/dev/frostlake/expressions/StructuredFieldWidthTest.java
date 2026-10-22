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

package dev.frostlake.expressions;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.executor.expressions.StructuredCast;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A structured type's text leaves are held to their declared width, as a scalar VARCHAR(n) cast holds its value.
 * The width was never applied: {@code {'a': 'abcdef'}::OBJECT(a VARCHAR(3))} kept the whole text.
 */
public class StructuredFieldWidthTest {

    private static final String TOO_LONG = "String 'abcdef' is too long and would be truncated";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE sw_db");
        engine.execute("USE SCHEMA sw_db.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String first(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
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
    public void aFieldTooLongForItsWidthIsRefused() {
        // Live-verified on qa-develop: the sentence, the type of a value that fits, and the decimal field's rounding.
        assertEquals(TOO_LONG, refusal("SELECT {'a': 'abcdef'}::OBJECT(a VARCHAR(3))"));
        assertEquals("OBJECT(a VARCHAR(3))[LOB]", first("SELECT SYSTEM$TYPEOF({'a': 'ab'}::OBJECT(a VARCHAR(3)))"));
        assertEquals("{\"a\":1.24}", first("SELECT {'a': 1.239}::OBJECT(a NUMBER(10,2))"));
    }

    @Test
    public void elementsNestedFieldsAndMapValuesAreHeldToo() {
        assertEquals(TOO_LONG, refusal("SELECT ['abcdef']::ARRAY(VARCHAR(3))"));
        assertEquals(TOO_LONG, refusal("SELECT {'a': {'b': 'abcdef'}}::OBJECT(a OBJECT(b VARCHAR(3)))"));
        assertEquals(TOO_LONG, refusal("SELECT {'k': ['abcdef']}::MAP(VARCHAR, ARRAY(VARCHAR(3)))"));
        assertEquals("String '123456' is too long and would be truncated",
            refusal("SELECT {'a': 123456}::OBJECT(a VARCHAR(3))"), "a number is held on the text it converts to");
        assertEquals("[\"abc\"]", first("SELECT ['abc']::ARRAY(VARCHAR(3))"));
    }

    @Test
    public void aMapKeyIsHeldToItsDeclaredWidth() {
        // MAP(VARCHAR(3), ...) does not parse yet, so the type is built directly.
        final MapType map = new MapType(new StringType("VARCHAR", 3), new NumericType("NUMBER", 38, 0));
        String refused = "not refused";
        try {
            StructuredCast.forColumn("{\"abcdef\": 1}", map);
        } catch (final RuntimeException expected) {
            refused = expected.getMessage();
        }
        assertEquals(TOO_LONG, refused);
        assertEquals("{\"abc\":1}", String.valueOf(StructuredCast.forColumn("{\"abc\": 1}", map)));
    }

    @Test
    public void aWriteToAStructuredColumnIsHeldToTheColumnsWidths() {
        engine.execute("CREATE TABLE so (o OBJECT(a VARCHAR(3)))");
        final String refused = refusal("INSERT INTO so (o) SELECT {'a': 'abcdef'}::OBJECT(a VARCHAR(10))");
        assertTrue(refused.contains(TOO_LONG), refused);
        engine.execute("INSERT INTO so (o) SELECT {'a': 'abc'}::OBJECT(a VARCHAR(10))");
        assertEquals("{\"a\":\"abc\"}", first("SELECT o FROM so"));
    }
}
