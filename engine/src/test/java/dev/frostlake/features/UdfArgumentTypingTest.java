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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SQL UDF's body is typed as if the call were inlined: a parameter reads at the ARGUMENT's own static
 * type wherever the argument reaches it without conversion, and at the declared type only where it does
 * not. The values follow the same rule — an exact number passes through unrounded, however small the
 * parameter's declared scale, while a text argument converts and rounds. Live-verified.
 */
public class UdfArgumentTypingTest extends BaseDatabaseTest {

    /** The type name SYSTEM$TYPEOF reports, without its storage tag. */
    private String typeOf(final String expression) {
        final ResultSet rs = engine.executeQuery("SELECT SYSTEM$TYPEOF(" + expression + ")"
            + (expression.contains("(t.") ? " FROM udf_typing_source t" : ""));
        assertEquals(1, rs.getRowCount(), expression);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString().replaceAll("\\[[^\\]]*\\]$", "");
    }

    /** The single cell of a query, as text. */
    private String valueOf(final String expression) {
        final ResultSet rs = engine.executeQuery("SELECT TO_JSON(TO_VARIANT(" + expression + "))");
        assertEquals(1, rs.getRowCount(), expression);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString();
    }

    private void createRoutines() {
        engine.execute("CREATE OR REPLACE FUNCTION num_p(a NUMBER(10,2)) RETURNS NUMBER(10,2) AS 'a'");
        engine.execute("CREATE OR REPLACE FUNCTION str_p(s VARCHAR(10)) RETURNS VARCHAR AS 's || ''x'''");
        engine.execute("CREATE OR REPLACE FUNCTION inc_p(a NUMBER) RETURNS NUMBER AS 'a + 1'");
        engine.execute("CREATE OR REPLACE FUNCTION bin_p(b BINARY(6)) RETURNS BINARY AS 'b'");
        engine.execute("CREATE OR REPLACE FUNCTION flt_p(a FLOAT) RETURNS FLOAT AS 'a'");
        engine.execute("CREATE OR REPLACE TABLE udf_typing_source (n NUMBER(5,2), n12 NUMBER(12,2),"
            + " n50 NUMBER(5,0), s VARCHAR(4), s20 VARCHAR(20), f FLOAT, b2 BINARY(2), b20 BINARY(20))");
        engine.execute("INSERT INTO udf_typing_source VALUES"
            + " (1.5, 2.5, 3, 'ab', 'abcd', 1.5, X'01', X'02')");
    }

    /** An exact NUMBER parameter keeps an argument's own precision and scale — if the scale suffices. */
    @Test
    public void anExactNumberKeepsTheArgumentsScale() {
        createRoutines();
        assertEquals("NUMBER(3,2)", typeOf("num_p(1.25)"));
        assertEquals("NUMBER(4,3)", typeOf("num_p(1.257)"));
        assertEquals("NUMBER(14,2)", typeOf("num_p(123456789012.25)"));
        assertEquals("NUMBER(5,2)", typeOf("num_p(t.n)"));
        assertEquals("NUMBER(12,2)", typeOf("num_p(t.n12)"));
        // A scale below the parameter's takes the declared type, and so does another family.
        assertEquals("NUMBER(10,2)", typeOf("num_p(1)"));
        assertEquals("NUMBER(10,2)", typeOf("num_p(t.n50)"));
        assertEquals("NUMBER(10,2)", typeOf("num_p(t.f)"));
        assertEquals("NUMBER(10,2)", typeOf("num_p('5')"));
        assertEquals("NUMBER(10,2)", typeOf("num_p(NULL)"));
    }

    /** A VARCHAR parameter takes a text argument's width, and the unbounded one for anything else. */
    @Test
    public void aVarcharTakesTheArgumentsWidthOrTheUnboundedOne() {
        createRoutines();
        assertEquals("VARCHAR(3)", typeOf("str_p('ab')"));
        assertEquals("VARCHAR(5)", typeOf("str_p(t.s)"));
        assertEquals("VARCHAR(21)", typeOf("str_p(t.s20)"));
        assertEquals("VARCHAR(134217728)", typeOf("str_p(12)"));
        assertEquals("VARCHAR(134217728)", typeOf("str_p(NULL)"));
        assertEquals("VARCHAR(134217728)", typeOf("str_p(TO_DATE('2020-01-01'))"));
    }

    /** A BINARY parameter takes a binary argument's width, narrower or wider than its own. */
    @Test
    public void aBinaryTakesTheArgumentsWidth() {
        createRoutines();
        assertEquals("BINARY(1)", typeOf("bin_p(X'00')"));
        assertEquals("BINARY(2)", typeOf("bin_p(t.b2)"));
        assertEquals("BINARY(20)", typeOf("bin_p(t.b20)"));
        assertEquals("BINARY(6)", typeOf("bin_p(X'00'::BINARY(6))"));
    }

    /** The body is then typed over that: the arithmetic in it widens the argument's own type. */
    @Test
    public void theBodyIsTypedOverTheArgument() {
        createRoutines();
        assertEquals("NUMBER(2,0)", typeOf("inc_p(1)"));
        assertEquals("NUMBER(4,1)", typeOf("inc_p(1.5)"));
        assertEquals("NUMBER(6,2)", typeOf("inc_p(t.n)"));
        assertEquals("NUMBER(11,2)", typeOf("inc_p(num_p(1))"));
        assertEquals("NUMBER(38,0)", typeOf("inc_p('5')"));
        assertEquals("NUMBER(38,0)", typeOf("inc_p(t.f)"));
        assertEquals("FLOAT", typeOf("flt_p(1)"));
        assertEquals("FLOAT", typeOf("flt_p(t.n)"));
    }

    /** The values follow: a number is not rounded to the parameter's scale, but a text is. */
    @Test
    public void anExactNumberIsNotRoundedButATextIs() {
        createRoutines();
        assertEquals("1", valueOf("num_p(1)"));
        assertEquals("1.5", valueOf("num_p(1.5)"));
        assertEquals("1.257", valueOf("num_p(1.257)"));
        assertEquals("5", valueOf("num_p('5')"));
        assertEquals("5.68", valueOf("num_p('5.678')"));
        assertEquals("\"abcdefghijklx\"", valueOf("str_p('abcdefghijkl')"));
        assertEquals("\"12x\"", valueOf("str_p(12)"));
        assertEquals("\"00\"", valueOf("bin_p(X'00')"));
    }
}
