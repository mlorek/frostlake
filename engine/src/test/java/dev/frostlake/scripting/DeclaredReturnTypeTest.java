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

package dev.frostlake.scripting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A procedure's declared RETURNS type governs the value a DIRECT RETURN of a simple value hands back
 * — measured cell by cell on a real account and pinned on both engines.
 *
 * <p>★ WHAT CONVERTS: a RETURN written directly in the procedure's own block (first, last or in
 * between) whose value is a literal, a typed name — a DECLAREd or LET variable, a parameter, a bind —
 * or a cast. {@code RETURN 1.7777} under NUMBER(5,2) is 1.78 and the CALL's column is NUMBER(5,2);
 * {@code RETURN 'abcdef'} under VARCHAR(2) is refused as too long; {@code RETURN x} under DATE reads
 * the text; a decimal literal converts as a FLOAT (10.50 under a VARCHAR is '10.5'); a whole number
 * under a TIMESTAMP is a seconds epoch; a text under VARIANT is a JSON string.
 *
 * <p>★ WHAT KEEPS ITS OWN TYPE: arithmetic, a function call, a concatenation, a subquery, a
 * conditional, an untyped name (a FOR counter is text) — and ANY return inside an IF, a CASE, a loop,
 * a handler or a nested block, literal or not.
 *
 * <p>★ A REFUSED CONVERSION is an EXPRESSION_ERROR at the RETURN value's own position, the number's
 * range sentence or the string's truncation sentence: NUMBER(3,0) refuses 12345 as "Number out of
 * representable range: type FIXED[SB2](3,0){not null}, value 12345".
 *
 * <p>★ A NUMERIC ARGUMENT is read at its parameter's declared scale (1.7777 into NUMBER(5,2) is 1.78)
 * while a VARCHAR parameter's width is not enforced.
 */
public class DeclaredReturnTypeTest extends BaseDatabaseTest {

    private void proc(final String name, final String returns, final String body) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS " + returns
            + " LANGUAGE SQL AS $$ " + body + " $$");
    }

    /** The CALL's answer as TO_VARCHAR sees it, read back through RESULT_SCAN. */
    private String called(final String name) {
        engine.executeQuery("CALL " + name + "()");
        engine.execute("CREATE OR REPLACE TABLE drt_r AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        final Object value = engine.executeQuery("SELECT TO_VARCHAR(" + name + ") FROM drt_r")
            .getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    /** The CALL's result column type, read off RESULT_SCAN. */
    private String calledType(final String name) {
        engine.executeQuery("CALL " + name + "()");
        engine.execute("CREATE OR REPLACE TABLE drt_t AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        final ResultSet described = engine.executeQuery("DESC TABLE drt_t");
        return String.valueOf(described.getRows().get(0).getValue(1));
    }

    private String json(final String name) {
        engine.executeQuery("CALL " + name + "()");
        engine.execute("CREATE OR REPLACE TABLE drt_j AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        return String.valueOf(engine.executeQuery("SELECT TO_JSON(" + name + ") FROM drt_j")
            .getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return e.getMessage();
    }

    @Test
    public void aDirectReturnOfASimpleValueConvertsToTheDeclaredType() {
        proc("r_lit", "NUMBER(5,2)", "BEGIN RETURN 1.7777; END;");
        assertEquals("1.78", called("r_lit"));
        assertEquals("NUMBER(5,2)", calledType("r_lit"));
        proc("r_neg", "NUMBER(5,2)", "BEGIN RETURN -1.7777; END;");
        assertEquals("-1.78", called("r_neg"));
        proc("r_paren", "NUMBER(5,2)", "BEGIN RETURN (1.7777); END;");
        assertEquals("1.78", called("r_paren"));
        proc("r_var", "NUMBER(5,2)", "DECLARE x NUMBER(10,4) := 1.7777; BEGIN RETURN x; END;");
        assertEquals("1.78", called("r_var"));
        assertEquals("NUMBER(5,2)", calledType("r_var"));
        proc("r_bind", "NUMBER(5,2)", "DECLARE x NUMBER(10,4) := 1.7777; BEGIN RETURN :x; END;");
        assertEquals("1.78", called("r_bind"));
        proc("r_let", "NUMBER(5,2)", "BEGIN LET x := 1.7777; RETURN x; END;");
        assertEquals("1.78", called("r_let"));
        proc("r_let_int", "NUMBER(5,2)", "BEGIN LET n := 3; RETURN n; END;");
        assertEquals("3.00", called("r_let_int"));
        assertEquals("NUMBER(5,2)", calledType("r_let_int"));
        proc("r_text", "NUMBER(5,2)", "BEGIN RETURN '1.7777'; END;");
        assertEquals("1.78", called("r_text"));
        proc("r_textvar", "NUMBER(5,2)", "DECLARE x VARCHAR := '1.7777'; BEGIN RETURN x; END;");
        assertEquals("1.78", called("r_textvar"));
        proc("r_cast", "NUMBER(5,2)", "BEGIN RETURN 1.7777::NUMBER(10,4); END;");
        assertEquals("1.78", called("r_cast"));
        proc("r_cast_narrow", "NUMBER(5,2)", "BEGIN RETURN 1.7777::NUMBER(3,1); END;");
        assertEquals("1.80", called("r_cast_narrow"));
        proc("r_cast_expr", "NUMBER(5,2)", "BEGIN RETURN (1.7777 + 1)::NUMBER(10,4); END;");
        assertEquals("2.78", called("r_cast_expr"));
        proc("r_cast_fn", "NUMBER(5,2)", "BEGIN RETURN ABS(1.7777)::NUMBER(10,4); END;");
        assertEquals("1.78", called("r_cast_fn"));
        proc("r_noscale", "NUMBER(5,2)", "DECLARE x NUMBER := 1.7777; BEGIN RETURN x; END;");
        assertEquals("2.00", called("r_noscale"));
        proc("r_float_lit", "NUMBER(5,2)", "BEGIN RETURN 1.7777e0; END;");
        assertEquals("1.78", called("r_float_lit"));
        proc("r_int", "INTEGER", "BEGIN RETURN 1.7777; END;");
        assertEquals("2", called("r_int"));
        assertEquals("NUMBER(38,0)", calledType("r_int"));
        proc("r_float", "FLOAT", "DECLARE x NUMBER(5,2) := 1.5; BEGIN RETURN x; END;");
        assertEquals("1.5", called("r_float"));
        assertEquals("FLOAT", calledType("r_float"));
        proc("r_float_text", "FLOAT", "BEGIN RETURN '1.5'; END;");
        assertEquals("1.5", called("r_float_text"));
        proc("r_null", "NUMBER(5,2)", "BEGIN RETURN NULL; END;");
        assertNull(called("r_null"));
        proc("r_null_cast", "NUMBER(5,2)", "BEGIN RETURN NULL::VARCHAR; END;");
        assertNull(called("r_null_cast"));
        // A procedure whose body never RETURNs answers NULL.
        proc("r_none", "NUMBER(5,2)", "BEGIN LET x := 1; END;");
        assertNull(called("r_none"));
    }

    @Test
    public void theTextTemporalBooleanAndSemiStructuredTargets() {
        proc("t_num", "VARCHAR", "BEGIN RETURN 123; END;");
        assertEquals("123", called("t_num"));
        // A decimal literal converts as a FLOAT, a typed number keeps its scale.
        proc("t_dec", "VARCHAR", "BEGIN RETURN 10.50; END;");
        assertEquals("10.5", called("t_dec"));
        proc("t_dec_cast", "VARCHAR", "BEGIN RETURN 1.50::NUMBER(3,2); END;");
        assertEquals("1.50", called("t_dec_cast"));
        proc("t_let_dec", "VARCHAR", "BEGIN LET n := 1.50; RETURN n; END;");
        assertEquals("1.5", called("t_let_dec"));
        proc("t_int_var", "VARCHAR", "DECLARE x INTEGER := 42; BEGIN RETURN x; END;");
        assertEquals("42", called("t_int_var"));
        proc("t_date_var", "VARCHAR", "DECLARE x DATE := '2020-01-15'; BEGIN RETURN x; END;");
        assertEquals("2020-01-15", called("t_date_var"));
        // The declared WIDTH is not the column's: a VARCHAR(10) result is the full-width text column.
        proc("t_width", "VARCHAR(10)", "BEGIN RETURN 'abc'; END;");
        assertEquals("abc", called("t_width"));
        assertEquals("VARCHAR(16777216)", calledType("t_width"));
        proc("d_text", "DATE", "BEGIN RETURN '2020-01-15'; END;");
        assertEquals("2020-01-15", called("d_text"));
        assertEquals("DATE", calledType("d_text"));
        proc("d_var", "DATE", "DECLARE x VARCHAR := '2020-01-15'; BEGIN RETURN x; END;");
        assertEquals("2020-01-15", called("d_var"));
        proc("d_ts", "DATE", "DECLARE x TIMESTAMP_NTZ := '2020-01-15 10:00:00'; BEGIN RETURN x; END;");
        assertEquals("2020-01-15", called("d_ts"));
        proc("ts_text", "TIMESTAMP_NTZ", "BEGIN RETURN '2020-01-15 10:00:00'; END;");
        assertEquals("2020-01-15 10:00:00.000", called("ts_text"));
        assertEquals("TIMESTAMP_NTZ(9)", calledType("ts_text"));
        proc("ts_var", "TIMESTAMP_NTZ", "DECLARE x VARCHAR := '2020-01-15 10:00:00'; BEGIN RETURN x; END;");
        assertEquals("2020-01-15 10:00:00.000", called("ts_var"));
        proc("ts_epoch", "TIMESTAMP_NTZ", "BEGIN RETURN 1631711999; END;");
        assertEquals("2021-09-15 13:19:59.000", called("ts_epoch"));
        proc("tm_text", "TIME", "BEGIN RETURN '10:00:00'; END;");
        assertEquals("10:00:00", called("tm_text"));
        proc("b_num", "BOOLEAN", "BEGIN RETURN 1; END;");
        assertEquals("true", called("b_num"));
        proc("b_text", "BOOLEAN", "BEGIN RETURN 'yes'; END;");
        assertEquals("true", called("b_text"));
        proc("b_var", "BOOLEAN", "DECLARE x NUMBER := 1; BEGIN RETURN x; END;");
        assertEquals("true", called("b_var"));
        proc("v_num", "VARIANT", "BEGIN RETURN 1.7777; END;");
        assertEquals("1.7777", json("v_num"));
        proc("v_text", "VARIANT", "BEGIN RETURN 'abc'; END;");
        assertEquals("\"abc\"", json("v_text"));
        proc("a_num", "ARRAY", "BEGIN RETURN 5; END;");
        assertEquals("[5]", json("a_num"));
    }

    @Test
    public void aRefusedConversionIsAnExpressionFaultAtTheReturnValue() {
        proc("f_range", "NUMBER(3,0)", "BEGIN RETURN 12345; END;");
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : "
            + "Number out of representable range: type FIXED[SB2](3,0){not null}, value 12345",
            refusal("CALL f_range()"));
        proc("f_range_var", "NUMBER(3,0)", "DECLARE x NUMBER := 12345; BEGIN RETURN x; END;");
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : "
            + "Number out of representable range: type FIXED[SB2](3,0){not null}, value 12345",
            refusal("CALL f_range_var()"));
        proc("f_round", "NUMBER(3,0)", "BEGIN RETURN 12.5; END;");
        assertEquals("13", called("f_round"));
        proc("f_width", "VARCHAR(2)", "BEGIN RETURN 'abcdef'; END;");
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : "
            + "String 'abcdef' is too long and would be truncated", refusal("CALL f_width()"));
        proc("f_width_var", "VARCHAR(2)", "DECLARE x VARCHAR := 'abcdef'; BEGIN RETURN x; END;");
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : "
            + "String 'abcdef' is too long and would be truncated", refusal("CALL f_width_var()"));
        proc("f_width_num", "VARCHAR(3)", "DECLARE x NUMBER(5,2) := 1.50; BEGIN RETURN x; END;");
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : "
            + "String '1.50' is too long and would be truncated", refusal("CALL f_width_num()"));
        proc("f_date", "DATE", "BEGIN RETURN 'abc'; END;");
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : "
            + "Date 'abc' is not recognized", refusal("CALL f_date()"));
        proc("f_num", "NUMBER", "BEGIN RETURN 'abc'; END;");
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : "
            + "Numeric value 'abc' is not recognized", refusal("CALL f_num()"));
        proc("f_bool", "BOOLEAN", "BEGIN RETURN 'abc'; END;");
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : "
            + "Boolean value 'abc' is not recognized", refusal("CALL f_bool()"));
    }

    @Test
    public void anExpressionOrANestedReturnKeepsItsOwnType() {
        proc("k_abs", "NUMBER(5,2)", "BEGIN RETURN ABS(1.7777); END;");
        assertEquals("1.7777", called("k_abs"));
        proc("k_iff", "NUMBER(5,2)", "BEGIN RETURN IFF(TRUE, 1.7777, 0); END;");
        assertEquals("1.7777", called("k_iff"));
        proc("k_subq", "NUMBER(5,2)", "BEGIN RETURN (SELECT 1.7777); END;");
        assertEquals("1.7777", called("k_subq"));
        proc("k_tonum", "NUMBER(5,2)", "BEGIN RETURN TO_NUMBER('1.7777', 10, 4); END;");
        assertEquals("1.7777", called("k_tonum"));
        proc("k_negvar", "NUMBER(5,2)", "DECLARE x NUMBER(10,4) := 1.7777; BEGIN RETURN -x; END;");
        assertEquals("-1.7777", called("k_negvar"));
        proc("k_concat", "VARCHAR(2)", "BEGIN RETURN 'ab' || 'cdef'; END;");
        assertEquals("abcdef", called("k_concat"));
        proc("k_concat_fn", "VARCHAR(2)", "BEGIN RETURN CONCAT('ab', 'cdef'); END;");
        assertEquals("abcdef", called("k_concat_fn"));
        proc("k_subq_text", "VARCHAR(2)", "BEGIN RETURN (SELECT 'abcdef'); END;");
        assertEquals("abcdef", called("k_subq_text"));
        // Nested anywhere — an IF, an ELSE, a WHILE, a CASE, a handler, an inner block — the value
        // keeps its own type, literal or typed name alike.
        proc("k_if", "NUMBER(5,2)", "BEGIN IF (TRUE) THEN RETURN 1.7777; END IF; RETURN 0; END;");
        assertEquals("1.7777", called("k_if"));
        assertEquals("NUMBER(5,4)", calledType("k_if"));
        proc("k_else", "NUMBER(5,2)", "BEGIN IF (FALSE) THEN RETURN 0; ELSE RETURN 1.7777; END IF; END;");
        assertEquals("1.7777", called("k_else"));
        proc("k_while", "NUMBER(5,2)", "BEGIN WHILE (TRUE) DO RETURN 1.7777; END WHILE; END;");
        assertEquals("1.7777", called("k_while"));
        proc("k_case", "NUMBER(5,2)", "BEGIN CASE WHEN TRUE THEN RETURN 1.7777; END CASE; END;");
        assertEquals("1.7777", called("k_case"));
        proc("k_handler", "NUMBER(5,2)",
            "BEGIN SELECT 1/0; RETURN 0; EXCEPTION WHEN OTHER THEN RETURN 1.7777; END;");
        assertEquals("1.7777", called("k_handler"));
        proc("k_nested", "NUMBER(5,2)", "BEGIN BEGIN RETURN 1.7777; END; END;");
        assertEquals("1.7777", called("k_nested"));
        proc("k_nested_var", "NUMBER(5,2)", "DECLARE x NUMBER(10,4) := 1.7777; BEGIN BEGIN RETURN x; END; END;");
        assertEquals("1.7777", called("k_nested_var"));
        proc("k_nested_text", "VARCHAR(2)", "BEGIN BEGIN RETURN 'abcdef'; END; END;");
        assertEquals("abcdef", called("k_nested_text"));
        proc("k_if_date", "DATE", "BEGIN IF (TRUE) THEN RETURN '2020-01-15'; END IF; RETURN NULL; END;");
        assertEquals("2020-01-15", called("k_if_date"));
        // Direct means direct: first of two, after a LET, after an IF that did not return.
        proc("k_two", "NUMBER(5,2)", "BEGIN RETURN 1.7777; RETURN 0; END;");
        assertEquals("1.78", called("k_two"));
        proc("k_after_let", "NUMBER(5,2)", "BEGIN LET x := 1; RETURN 1.7777; END;");
        assertEquals("1.78", called("k_after_let"));
        proc("k_after_if", "NUMBER(5,2)", "BEGIN IF (FALSE) THEN RETURN 0; END IF; RETURN 1.7777; END;");
        assertEquals("1.78", called("k_after_if"));
        // An untyped name — the FOR counter — stays text, whatever the declaration says.
        proc("k_counter", "NUMBER(5,2)", "BEGIN FOR i IN 1 TO 1 DO RETURN i; END FOR; END;");
        assertEquals("1", called("k_counter"));
    }

    @Test
    public void aNumericArgumentIsReadAtItsParametersScale() {
        engine.execute("CREATE OR REPLACE PROCEDURE p_arg(a NUMBER(5,2)) RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " BEGIN RETURN a::VARCHAR; END; $$");
        assertEquals("1.78", String.valueOf(engine.executeQuery("CALL p_arg(1.7777)").getRows().get(0).getValue(0)));
        engine.execute("CREATE OR REPLACE PROCEDURE p_arg_type(a NUMBER(5,2)) RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " BEGIN RETURN (SELECT SYSTEM$TYPEOF(:a)); END; $$");
        assertEquals("NUMBER(5,2)[SB2]",
            String.valueOf(engine.executeQuery("CALL p_arg_type(1.7777)").getRows().get(0).getValue(0)));
        engine.execute("CREATE OR REPLACE PROCEDURE p_arg_ret(a NUMBER(10,4)) RETURNS NUMBER(5,2) LANGUAGE SQL AS $$"
            + " BEGIN RETURN a; END; $$");
        assertEquals("1.78", String.valueOf(engine.executeQuery("CALL p_arg_ret(1.7777)").getRows().get(0).getValue(0)));
        // A VARCHAR parameter's width is not enforced; a DATE parameter reads its text.
        engine.execute("CREATE OR REPLACE PROCEDURE p_arg_text(a VARCHAR(2)) RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " BEGIN RETURN a; END; $$");
        assertEquals("abcdef", String.valueOf(engine.executeQuery("CALL p_arg_text('abcdef')").getRows().get(0).getValue(0)));
        engine.execute("CREATE OR REPLACE PROCEDURE p_arg_date(a DATE) RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " BEGIN RETURN (SELECT SYSTEM$TYPEOF(:a)); END; $$");
        assertEquals("DATE[SB4]",
            String.valueOf(engine.executeQuery("CALL p_arg_date('2020-01-15')").getRows().get(0).getValue(0)));
    }
}
