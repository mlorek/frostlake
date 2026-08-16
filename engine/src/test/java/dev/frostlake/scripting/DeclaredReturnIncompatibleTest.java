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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A procedure whose direct RETURN cannot become its declared RETURNS type at all is refused when it is
 * CALLed, before its body runs, anchored on that RETURN:
 *
 * <pre>
 *   SQL compilation error: error line 1 at position 7
 *    Declared return type 'DATE' is incompatible with actual return type 'NUMBER(10,0)'
 * </pre>
 *
 * The RETURNs judged are the ones the declared type governs: a literal, a cast or a typed name written
 * directly in the procedure's own block. The pairs are the plain cast's; an OBJECT takes nothing but a
 * VARIANT or an OBJECT. The first incompatible RETURN in source order is the one reported, reachable or
 * not, unless another compile fault comes before it. Every cell was measured on a real account.
 */
public class DeclaredReturnIncompatibleTest extends BaseDatabaseTest {

    private void proc(final String name, final String returns, final String body) {
        procWith(name, "", returns, body);
    }

    private void procWith(final String name, final String parameters, final String returns, final String body) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "(" + parameters + ") RETURNS " + returns
            + " LANGUAGE SQL AS $$ " + body + " $$");
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return String.valueOf(refused.getMessage()).replace('\n', '|');
    }

    private static String incompatible(final int position, final String declared, final String actual) {
        return "SQL compilation error: error line 1 at position " + position + "| Declared return type '"
            + declared + "' is incompatible with actual return type '" + actual + "'";
    }

    /** The CALL's answer as TO_VARCHAR sees it, read back through RESULT_SCAN. */
    private String called(final String name) {
        engine.executeQuery("CALL " + name + "()");
        engine.execute("CREATE OR REPLACE TABLE dri_r AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        final Object value = engine.executeQuery("SELECT TO_VARCHAR(" + name + ") FROM dri_r")
            .getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    /** A literal is typed as written, an exponent by the value it spells, parenthesised or negated. */
    @Test
    public void aLiteralIsTypedAsWritten() {
        proc("dri1", "DATE", "BEGIN RETURN 1631711999; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(10,0)"), refusal("CALL dri1()"));
        proc("dri2", "DATE", "BEGIN RETURN TRUE; END;");
        assertEquals(incompatible(7, "DATE", "BOOLEAN"), refusal("CALL dri2()"));
        proc("dri3", "DATE", "BEGIN RETURN (5); END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(1,0)"), refusal("CALL dri3()"));
        proc("dri4", "DATE", "BEGIN RETURN -5; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(1,0)"), refusal("CALL dri4()"));
        proc("dri5", "DATE", "BEGIN RETURN 5.5; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(2,1)"), refusal("CALL dri5()"));
        proc("dri6", "DATE", "BEGIN RETURN 1e3; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(4,0)"), refusal("CALL dri6()"));
        proc("dri7", "DATE", "BEGIN RETURN 1.5e3; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(4,0)"), refusal("CALL dri7()"));
        proc("dri8", "DATE", "BEGIN RETURN 1e-3; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(4,3)"), refusal("CALL dri8()"));
        proc("dri9", "DATE", "BEGIN RETURN 2.5e0; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(2,1)"), refusal("CALL dri9()"));
        proc("dri10", "DATE", "BEGIN   RETURN 5; END;");
        assertEquals(incompatible(9, "DATE", "NUMBER(1,0)"), refusal("CALL dri10()"), "anchored on the RETURN");
    }

    /** The declared type is spelled with its default parameters, a bare BINARY at 67108864. */
    @Test
    public void theDeclaredTypeIsSpelledWithItsDefaults() {
        proc("dri11", "TIME", "BEGIN RETURN 5; END;");
        assertEquals(incompatible(7, "TIME(9)", "NUMBER(1,0)"), refusal("CALL dri11()"));
        proc("dri12", "BINARY", "BEGIN RETURN 5; END;");
        assertEquals(incompatible(7, "BINARY(67108864)", "NUMBER(1,0)"), refusal("CALL dri12()"));
        proc("dri13", "FLOAT", "BEGIN RETURN TRUE; END;");
        assertEquals(incompatible(7, "FLOAT", "BOOLEAN"), refusal("CALL dri13()"));
        proc("dri14", "TIMESTAMP_LTZ", "BEGIN RETURN TRUE; END;");
        assertEquals(incompatible(7, "TIMESTAMP_LTZ(9)", "BOOLEAN"), refusal("CALL dri14()"));
        proc("dri15", "NUMBER", "DECLARE x DATE := '2020-01-15'; BEGIN RETURN x; END;");
        assertEquals(incompatible(39, "NUMBER(38,0)", "DATE"), refusal("CALL dri15()"));
    }

    /** A typed name carries its declared type: a DECLARE, a LET, a parameter, a bind. */
    @Test
    public void aTypedNameCarriesItsDeclaredType() {
        proc("dri16", "DATE", "DECLARE x NUMBER := 5; BEGIN RETURN x; END;");
        assertEquals(incompatible(30, "DATE", "NUMBER(38,0)"), refusal("CALL dri16()"));
        proc("dri17", "DATE", "DECLARE x NUMBER(10,2) := 5; BEGIN RETURN x; END;");
        assertEquals(incompatible(36, "DATE", "NUMBER(10,2)"), refusal("CALL dri17()"));
        proc("dri18", "DATE", "DECLARE x NUMBER; BEGIN x := 5; RETURN x; END;");
        assertEquals(incompatible(33, "DATE", "NUMBER(38,0)"), refusal("CALL dri18()"));
        proc("dri19", "DATE", "BEGIN LET x NUMBER := 5; RETURN x; END;");
        assertEquals(incompatible(26, "DATE", "NUMBER(38,0)"), refusal("CALL dri19()"));
        procWith("dri20", "a NUMBER", "DATE", "BEGIN RETURN a; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(38,0)"), refusal("CALL dri20(5)"));
        procWith("dri21", "a NUMBER(10,2)", "DATE", "BEGIN RETURN :a; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(10,2)"), refusal("CALL dri21(5)"));
        proc("dri22", "NUMBER", "DECLARE x TIMESTAMP_NTZ := '2020-01-15'; BEGIN RETURN x; END;");
        assertEquals(incompatible(48, "NUMBER(38,0)", "TIMESTAMP_NTZ(9)"), refusal("CALL dri22()"));
        proc("dri23", "DATE", "DECLARE x TIME := '10:00:00'; BEGIN RETURN x; END;");
        assertEquals(incompatible(37, "DATE", "TIME(9)"), refusal("CALL dri23()"));
        proc("dri24", "FLOAT", "DECLARE x DATE := '2020-01-15'; BEGIN RETURN x; END;");
        assertEquals(incompatible(39, "FLOAT", "DATE"), refusal("CALL dri24()"));
        proc("dri25", "BOOLEAN", "DECLARE x FLOAT := 1.5; BEGIN RETURN x; END;");
        assertEquals(incompatible(31, "BOOLEAN", "FLOAT"), refusal("CALL dri25()"));
    }

    /** An untyped declaration takes a type from its initialiser. */
    @Test
    public void anUntypedDeclarationIsTypedByItsInitialiser() {
        proc("dri26", "DATE", "BEGIN LET x := 5; RETURN x; END;");
        assertEquals(incompatible(19, "DATE", "NUMBER(38,0)"), refusal("CALL dri26()"),
            "an integer makes a NUMBER(38,0)");
        proc("dri27", "DATE", "DECLARE x := 5; BEGIN RETURN x; END;");
        assertEquals(incompatible(23, "DATE", "NUMBER(38,0)"), refusal("CALL dri27()"));
        proc("dri28", "DATE", "BEGIN LET y := 1.5; RETURN y; END;");
        assertEquals(incompatible(21, "DATE", "FLOAT"), refusal("CALL dri28()"), "a decimal a FLOAT");
        proc("dri29", "DATE", "BEGIN LET y := TRUE; RETURN y; END;");
        assertEquals(incompatible(22, "DATE", "BOOLEAN"), refusal("CALL dri29()"));
        proc("dri30", "DATE", "BEGIN LET y NUMBER := 1; LET y2 := y; RETURN y2; END;");
        assertEquals(incompatible(39, "DATE", "NUMBER(38,0)"), refusal("CALL dri30()"), "and a name its own");
    }

    /** A cast is typed by its target, a NULL cast included. */
    @Test
    public void aCastIsTypedByItsTarget() {
        proc("dri31", "DATE", "BEGIN RETURN '5'::NUMBER; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(38,0)"), refusal("CALL dri31()"));
        proc("dri32", "DATE", "BEGIN RETURN CAST(5 AS NUMBER(3,1)); END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(3,1)"), refusal("CALL dri32()"));
        proc("dri33", "DATE", "BEGIN RETURN NULL::NUMBER; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(38,0)"), refusal("CALL dri33()"));
    }

    /** An OBJECT takes nothing but a VARIANT or an OBJECT, and a scalar refuses a container. */
    @Test
    public void containersFollowTheirOwnRule() {
        proc("dri34", "OBJECT", "BEGIN RETURN 5; END;");
        assertEquals(incompatible(7, "OBJECT", "NUMBER(1,0)"), refusal("CALL dri34()"));
        proc("dri35", "OBJECT", "BEGIN RETURN 'abc'; END;");
        assertEquals(incompatible(7, "OBJECT", "VARCHAR(3)"), refusal("CALL dri35()"), "a text included");
        proc("dri36", "OBJECT", "DECLARE x DATE := '2020-01-15'; BEGIN RETURN x; END;");
        assertEquals(incompatible(39, "OBJECT", "DATE"), refusal("CALL dri36()"));
        proc("dri37", "OBJECT", "DECLARE x ARRAY := ARRAY_CONSTRUCT(1); BEGIN RETURN x; END;");
        assertEquals(incompatible(46, "OBJECT", "ARRAY"), refusal("CALL dri37()"));
        proc("dri38", "NUMBER", "DECLARE x OBJECT := OBJECT_CONSTRUCT('a', 1); BEGIN RETURN x; END;");
        assertEquals(incompatible(53, "NUMBER(38,0)", "OBJECT"), refusal("CALL dri38()"));
        proc("dri39", "DATE", "DECLARE x ARRAY := ARRAY_CONSTRUCT(1); BEGIN RETURN x; END;");
        assertEquals(incompatible(46, "DATE", "ARRAY"), refusal("CALL dri39()"));
        proc("dri40", "NUMBER", "DECLARE x BINARY := TO_BINARY('AB'); BEGIN RETURN x; END;");
        assertEquals(incompatible(44, "NUMBER(38,0)", "BINARY(67108864)"), refusal("CALL dri40()"));
    }

    /** Only a DIRECT RETURN is judged, the first in source order, reachable or not. */
    @Test
    public void theFirstFaultInSourceOrderIsReported() {
        proc("dri41", "DATE", "BEGIN RETURN '2020-01-15'; RETURN 5; END;");
        assertEquals(incompatible(28, "DATE", "NUMBER(1,0)"), refusal("CALL dri41()"), "an unreachable RETURN too");
        proc("dri42", "DATE", "BEGIN RETURN 5; RETURN TRUE; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(1,0)"), refusal("CALL dri42()"));
        proc("dri43", "DATE", "BEGIN RETURN 5; EXCEPTION WHEN OTHER THEN RETURN 6; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(1,0)"), refusal("CALL dri43()"));
        proc("dri44", "DATE", "BEGIN RETURN 5; RETURN missing; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(1,0)"), refusal("CALL dri44()"), "ahead of a later unknown name");
        proc("dri45", "DATE", "BEGIN RETURN 5; LET a := missing; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(1,0)"), refusal("CALL dri45()"));
        proc("dri46", "DATE", "BEGIN FOR i IN 1 TO 1 DO i := 5; END FOR; RETURN 5; END;");
        assertEquals("SQL compilation error: error line 1 at position 28| Assignment to variable 'I' is not permitted.",
            refusal("CALL dri46()"), "behind an earlier counter assignment");
        proc("dri47", "DATE", "BEGIN RETURN 5; FOR i IN 1 TO 1 DO i := 5; END FOR; END;");
        assertEquals(incompatible(7, "DATE", "NUMBER(1,0)"), refusal("CALL dri47()"));
    }

    /** What the declared type does not govern runs, and a compatible pair converts. */
    @Test
    public void whatTheDeclaredTypeDoesNotGovernRuns() {
        proc("dri48", "DATE", "BEGIN RETURN 1 + 1; END;");
        assertEquals("2", called("dri48"), "arithmetic keeps its own type");
        proc("dri49", "DATE", "BEGIN RETURN ABS(5); END;");
        assertEquals("5", called("dri49"), "so does a function call");
        proc("dri50", "DATE", "BEGIN IF (TRUE) THEN RETURN 5; END IF; RETURN '2020-01-15'; END;");
        assertEquals("5", called("dri50"), "and a RETURN inside an IF");
        proc("dri51", "DATE", "BEGIN BEGIN RETURN 5; END; END;");
        assertEquals("5", called("dri51"), "or a nested block");
        proc("dri52", "DATE", "BEGIN RETURN NULL; END;");
        assertNull(called("dri52"));
        proc("dri53", "VARCHAR", "DECLARE x DATE := '2020-01-15'; BEGIN RETURN x; END;");
        assertEquals("2020-01-15", called("dri53"));
        proc("dri54", "NUMBER", "DECLARE x VARCHAR := '5'; BEGIN RETURN x; END;");
        assertEquals("5", called("dri54"), "a text converts to any scalar at run time");
        proc("dri55", "VARCHAR", "BEGIN RETURN TRUE; END;");
        assertEquals("true", called("dri55"));
        proc("dri56", "NUMBER", "BEGIN RETURN TO_DATE('2020-01-15'); END;");
        assertEquals("2020-01-15", called("dri56"));
    }
}
