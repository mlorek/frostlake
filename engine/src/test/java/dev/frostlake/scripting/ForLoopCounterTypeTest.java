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
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The types a scripting block gives names it never saw declared, measured on a real account and
 * pinned on both engines.
 *
 * <p>★ A FOR COUNTER IS A NUMBER(9,0): a column derived from {@code :i} declares NUMBER(9,0),
 * {@code :i + 1} NUMBER(10,0), SYSTEM$TYPEOF(:i) reads NUMBER(9,0) with the tag of the value. The
 * FIRST value the counter takes — the low bound, or the high one under REVERSE — must fit it and is
 * refused at that bound's own position with the number's range sentence; the values the loop counts
 * through are never re-checked. A RETURN of the counter is still text.
 *
 * <p>★ AN UNTYPED DECLARATION takes its initialiser's type: an integer literal is NUMBER(38,0), a
 * decimal literal FLOAT, a whole-number name (the counter) its own type and a scaled one FLOAT; the
 * type then governs assignments, as a declared one does.
 *
 * <p>★ A BOUND VARIABLE'S TAG FOLLOWS ITS VALUE: a NUMBER(10,4) holding 1.7777 reads [SB2], holding
 * 12345.6789 [SB4]. And a declared NUMBER holds its value to the declared width — DECLARE, LET and
 * := all refuse 12345 into a NUMBER(3,0) with "Number out of representable range: type
 * FIXED[SB2](3,0){not null}, value 12345" at the value's own position.
 */
public class ForLoopCounterTypeTest extends BaseDatabaseTest {

    private String block(final String body) {
        final Object value = engine.executeQuery("EXECUTE IMMEDIATE $$ " + body + " $$").getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    private String blockType(final String body) {
        engine.executeQuery("EXECUTE IMMEDIATE $$ " + body + " $$");
        engine.execute("CREATE OR REPLACE TABLE flc_b AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        return String.valueOf(engine.executeQuery("DESC TABLE flc_b").getRows().get(0).getValue(1));
    }

    private String columnType(final String table) {
        final ResultSet described = engine.executeQuery("DESC TABLE " + table);
        return String.valueOf(described.getRows().get(0).getValue(1));
    }

    private String refusal(final String body) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("EXECUTE IMMEDIATE $$ " + body + " $$");
            }
        });
        return e.getMessage();
    }

    private static String outOfRange(final int position, final String value) {
        return "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position " + position
            + " : Number out of representable range: type FIXED[SB4](9,0){not null}, value " + value;
    }

    @Test
    public void theCounterBindsAsANineDigitNumber() {
        block("BEGIN FOR i IN 1 TO 3 DO CREATE OR REPLACE TABLE flc_1 AS SELECT :i AS c; END FOR; RETURN 'ok'; END;");
        assertEquals("NUMBER(9,0)", columnType("flc_1"));
        block("BEGIN FOR i IN REVERSE 1 TO 3 DO CREATE OR REPLACE TABLE flc_2 AS SELECT :i AS c; END FOR; RETURN 'ok'; END;");
        assertEquals("NUMBER(9,0)", columnType("flc_2"));
        block("DECLARE n NUMBER(3,0) := 2; BEGIN FOR i IN 1 TO n DO CREATE OR REPLACE TABLE flc_3 AS SELECT :i AS c;"
            + " END FOR; RETURN 'ok'; END;");
        assertEquals("NUMBER(9,0)", columnType("flc_3"));
        block("BEGIN FOR i IN -2 TO -1 DO CREATE OR REPLACE TABLE flc_4 AS SELECT :i AS c; END FOR; RETURN 'ok'; END;");
        assertEquals("NUMBER(9,0)", columnType("flc_4"));
        block("BEGIN FOR i IN 999999999 TO 999999999 DO CREATE OR REPLACE TABLE flc_5 AS SELECT :i AS c;"
            + " END FOR; RETURN 'ok'; END;");
        assertEquals("NUMBER(9,0)", columnType("flc_5"));
        block("BEGIN FOR i IN 1 TO 2 DO CREATE OR REPLACE TABLE flc_6 AS SELECT :i + 1 AS c, :i * 2 AS d;"
            + " END FOR; RETURN 'ok'; END;");
        assertEquals("NUMBER(10,0)", columnType("flc_6"));
        assertEquals("NUMBER(9,0)[SB1]",
            block("DECLARE t VARCHAR; BEGIN FOR i IN 1 TO 1 DO t := (SELECT SYSTEM$TYPEOF(:i)); END FOR; RETURN t; END;"));
        assertEquals("NUMBER(9,0)[SB4]", block("DECLARE t VARCHAR; BEGIN FOR i IN 100000 TO 100000 DO"
            + " t := (SELECT SYSTEM$TYPEOF(:i)); END FOR; RETURN t; END;"));
        assertEquals("NUMBER(10,0)[SB1]", block("DECLARE t VARCHAR; BEGIN FOR i IN 1 TO 1 DO"
            + " t := (SELECT SYSTEM$TYPEOF(:i + 1)); END FOR; RETURN t; END;"));
    }

    @Test
    public void theFirstValueMustFitTheCounter() {
        assertEquals(outOfRange(16, "1000000000"),
            refusal("BEGIN FOR i IN 1000000000 TO 1 DO RETURN 'ran'; END FOR; RETURN 'empty'; END;"));
        assertEquals(outOfRange(16, "-1000000000"),
            refusal("BEGIN FOR i IN -1000000000 TO -1000000000 DO RETURN 'ran'; END FOR; RETURN 'done'; END;"));
        assertEquals(outOfRange(16, "1000000000"),
            refusal("BEGIN FOR i IN 1000000000 TO 1000000001 DO RETURN 'ran'; END FOR; RETURN 'done'; END;"));
        assertEquals(outOfRange(48, "1000000000"),
            refusal("DECLARE n NUMBER := 1000000000; BEGIN FOR i IN n TO n DO RETURN 'ran'; END FOR; RETURN 'done'; END;"));
        // Under REVERSE the first value is the HIGH bound.
        assertEquals(outOfRange(60, "1000000000"), refusal("DECLARE x NUMBER := 0; BEGIN FOR i IN REVERSE 999999999"
            + " TO 1000000000 DO x := x + 1; END FOR; RETURN x; END;"));
        // The values counted through are never re-checked.
        assertEquals("2", block("DECLARE x NUMBER := 0; BEGIN FOR i IN 999999999 TO 1000000000 DO x := x + 1;"
            + " END FOR; RETURN x; END;"));
        assertEquals("ran", block("BEGIN FOR i IN 1.5 TO 2.5 DO RETURN 'ran'; END FOR; RETURN 'done'; END;"));
        assertEquals("ran", block("BEGIN FOR i IN '1' TO '2' DO RETURN 'ran'; END FOR; RETURN 'done'; END;"));
    }

    @Test
    public void theCounterFlowsIntoTypedAndUntypedNames() {
        assertEquals("1.00", block("DECLARE x NUMBER(5,2); BEGIN FOR i IN 1 TO 1 DO x := i; END FOR; RETURN x; END;"));
        assertEquals("NUMBER(5,2)",
            blockType("DECLARE x NUMBER(5,2); BEGIN FOR i IN 1 TO 1 DO x := i; END FOR; RETURN x; END;"));
        assertEquals("1", block("BEGIN FOR i IN 1 TO 1 DO LET y := i; RETURN y; END FOR; END;"));
        assertEquals("NUMBER(9,0)", blockType("BEGIN FOR i IN 1 TO 1 DO LET y := i; RETURN y; END FOR; END;"));
        assertEquals("1", block("BEGIN FOR i IN 1 TO 1 DO RETURN i; END FOR; END;"));
        assertEquals("VARCHAR(16777216)", blockType("BEGIN FOR i IN 1 TO 1 DO RETURN i; END FOR; END;"));
        assertEquals("2", block("BEGIN FOR i IN 1 TO 1 DO RETURN i + 1; END FOR; END;"));
    }

    @Test
    public void anUntypedDeclarationTakesItsInitialisersType() {
        assertEquals("FLOAT[DOUBLE]",
            block("DECLARE t VARCHAR; BEGIN LET n := 1.7777; t := (SELECT SYSTEM$TYPEOF(:n)); RETURN t; END;"));
        assertEquals("NUMBER(38,0)[SB1]",
            block("DECLARE t VARCHAR; BEGIN LET n := 0; t := (SELECT SYSTEM$TYPEOF(:n)); RETURN t; END;"));
        assertEquals("FLOAT[DOUBLE]",
            block("DECLARE n := 1.7777; t VARCHAR; BEGIN t := (SELECT SYSTEM$TYPEOF(:n)); RETURN t; END;"));
        assertEquals("FLOAT[DOUBLE]", block("DECLARE x NUMBER(10,4) := 1.7777; t VARCHAR; BEGIN LET y := x;"
            + " t := (SELECT SYSTEM$TYPEOF(:y)); RETURN t; END;"));
        assertEquals("FLOAT[DOUBLE]",
            block("DECLARE t VARCHAR; BEGIN LET n := -1.7777; t := (SELECT SYSTEM$TYPEOF(:n)); RETURN t; END;"));
        // The inferred type governs what is assigned later.
        assertEquals("2", block("BEGIN LET n := 0; n := n + 1.5; RETURN n; END;"));
        assertEquals("12345", block("BEGIN LET n := 0; n := 12345; RETURN n; END;"));
        assertEquals("123.456789", block("BEGIN LET n := 1.7777; n := 123.456789; RETURN n; END;"));
        assertEquals("FLOAT", blockType("BEGIN LET n := 1.7777; n := 123.456789; RETURN n; END;"));
        assertEquals("abcdef", block("BEGIN LET s := 'abc'; s := 'abcdef'; RETURN s; END;"));
    }

    @Test
    public void aBoundVariablesTagFollowsItsValue() {
        assertEquals("NUMBER(10,4)[SB2]",
            block("DECLARE x NUMBER(10,4) := 1.7777; t VARCHAR; BEGIN t := (SELECT SYSTEM$TYPEOF(:x)); RETURN t; END;"));
        assertEquals("NUMBER(10,4)[SB4]", block("DECLARE x NUMBER(10,4) := 12345.6789; t VARCHAR;"
            + " BEGIN t := (SELECT SYSTEM$TYPEOF(:x)); RETURN t; END;"));
        assertEquals("NUMBER(5,2)[SB2]",
            block("DECLARE x NUMBER(5,2) := 1.7777; t VARCHAR; BEGIN t := (SELECT SYSTEM$TYPEOF(:x)); RETURN t; END;"));
    }

    @Test
    public void aDeclaredNumberHoldsItsValueToTheDeclaredWidth() {
        final String sentence = "Number out of representable range: type FIXED[SB2](3,0){not null}, value 12345";
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : " + sentence,
            refusal("DECLARE x NUMBER(3,0) := 12345; BEGIN RETURN x; END;"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 35 : " + sentence,
            refusal("DECLARE x NUMBER(3,0); BEGIN x := 12345; RETURN x; END;"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 28 : " + sentence,
            refusal("BEGIN LET x NUMBER(3,0) := 12345; RETURN x; END;"));
        // Rounded first, then held to the width: 999.5 does not fit three digits once rounded.
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : "
            + "Number out of representable range: type FIXED[SB2](3,0){not null}, value 999.5",
            refusal("DECLARE x NUMBER(3,0) := 999.5; BEGIN RETURN x; END;"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : "
            + "Number out of representable range: type FIXED[SB4](5,2){not null}, value 1234.567",
            refusal("DECLARE x NUMBER(5,2) := 1234.567; BEGIN RETURN x; END;"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : "
            + "Number out of representable range: type FIXED[SB4](5,2){not null}, value 999.999",
            refusal("DECLARE x NUMBER(5,2) := 999.999; BEGIN RETURN x; END;"));
        assertEquals("13", block("DECLARE x NUMBER(3,0) := 12.5; BEGIN RETURN x; END;"));
    }
}
