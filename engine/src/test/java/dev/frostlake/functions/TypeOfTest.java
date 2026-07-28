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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * TYPEOF reports Snowflake's variant type names: strings are VARCHAR (not "TEXT"), floating-point is
 * DOUBLE and fixed-point is DECIMAL (never "REAL"), and a SQL NULL is "NULL" (a JSON null value would
 * be "NULL_VALUE"). Previously it returned "TEXT" / "REAL" / "NULL_VALUE".
 */
public class TypeOfTest extends BaseDatabaseTest {

    private Object typeOf(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void integer() {
        assertEquals("INTEGER", typeOf("SELECT TYPEOF(42)"));
    }

    @Test
    public void stringIsVarcharNotText() {
        assertEquals("VARCHAR", typeOf("SELECT TYPEOF('hello')"));
    }

    @Test
    public void fixedPointIsDecimal() {
        // A bare decimal literal is fixed-point NUMBER in Snowflake, hence DECIMAL.
        assertEquals("DECIMAL", typeOf("SELECT TYPEOF(3.14)"));
        assertEquals("DECIMAL", typeOf("SELECT TYPEOF(3.14::NUMBER)"));
    }

    @Test
    public void floatingPointIsDouble() {
        assertEquals("DOUBLE", typeOf("SELECT TYPEOF(3.14::DOUBLE)"));
    }

    @Test
    public void booleanType() {
        assertEquals("BOOLEAN", typeOf("SELECT TYPEOF(TRUE)"));
    }

    @Test
    public void sqlNullIsNull() {
        // Snowflake: a SQL NULL input yields NULL (NULL in, NULL out); "NULL_VALUE" is reserved
        // for a JSON null VALUE inside a variant.
        assertNull(engine.executeQuery("SELECT TYPEOF(NULL)").getRows().get(0).getValue(0));
        assertEquals("NULL_VALUE", typeOf("SELECT TYPEOF(PARSE_JSON('null'))"));
    }

    @Test
    public void jsonArrayAndObject() {
        assertEquals("ARRAY", typeOf("SELECT TYPEOF(PARSE_JSON('[1,2]'))"));
        assertEquals("OBJECT", typeOf("SELECT TYPEOF(PARSE_JSON('{\"a\":1}'))"));
    }

    @Test
    public void jsonFloatingPointIsDouble() {
        assertEquals("DOUBLE", typeOf("SELECT TYPEOF(PARSE_JSON('3.14'))"));
    }
}
