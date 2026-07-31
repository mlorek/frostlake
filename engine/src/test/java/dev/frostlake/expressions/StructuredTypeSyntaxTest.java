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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structured semi-structured type SYNTAX — {@code ARRAY(INT)}, {@code OBJECT(field type [NOT NULL],
 * ...)} — in casts (incl. the {@code RENAME FIELDS} / {@code ADD FIELDS} modifiers) and UDF
 * parameter lists. The engine's semi-structured model is untyped JSON, so the field structure is
 * accepted and the value behaves as a plain OBJECT/ARRAY.
 */
public class StructuredTypeSyntaxTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void structuredArrayCast() {
        assertEquals("[1,2,3]", String.valueOf(scalar("SELECT CAST([1, 2, 3] AS ARRAY(INT))")));
    }

    @Test
    public void structuredObjectCastWithFieldModifiers() {
        assertTrue(String.valueOf(scalar("SELECT CAST(OBJECT_CONSTRUCT('a', 1) AS OBJECT(a CHAR NOT NULL))"))
            .contains("\"a\""));
        assertTrue(String.valueOf(scalar("SELECT CAST(OBJECT_CONSTRUCT('x', 1) AS OBJECT(x CHAR) RENAME FIELDS)"))
            .contains("1"));
        assertTrue(String.valueOf(scalar(
            "SELECT CAST(OBJECT_CONSTRUCT('x', 1) AS OBJECT(x CHAR, y VARCHAR) ADD FIELDS)"))
            .contains("1"));
    }

    @Test
    public void structuredTypesInUdfSignatures() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION my_udf(location OBJECT(city VARCHAR, zipcode DECIMAL(38, 0), val ARRAY(BOOLEAN)))
            RETURNS VARCHAR AS $$ 'foo' $$""");
        assertEquals("foo", String.valueOf(scalar("SELECT my_udf(OBJECT_CONSTRUCT('city', 'x'))")));
        engine.execute("DROP FUNCTION my_udf (OBJECT(city VARCHAR, zipcode DECIMAL(38, 0), val ARRAY(BOOLEAN)))");
    }
}
