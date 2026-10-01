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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A unit-suffixed interval literal inside a Snowflake Scripting block or a stored procedure is typed exactly as it
 * is in a query: arithmetic on it keeps its fields, drops what is finer than its trailing field, prints by its
 * fields and precisions and is refused past its leading precision; a condition compares the settled values; a
 * variable a block reads scales it as the number it holds; and a literal that names no type or does not read
 * refuses the whole block while it compiles, untaken branch or not. Every cell is the account's answer.
 */
public class IntervalScriptingTest extends BaseDatabaseTest {

    /** The block's one returned value, or the refusal. */
    private String returned(final String block) {
        try {
            for (final Row row : engine.executeQuery(block).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    /** {@code RETURN TO_VARCHAR(<expression>)} in an anonymous block. */
    private String text(final String expression) {
        return returned("EXECUTE IMMEDIATE $$ BEGIN RETURN TO_VARCHAR(" + expression + "); END; $$");
    }

    /** Arithmetic on a literal keeps its type in a block, as it does in a query. */
    @Test
    public void arithmeticKeepsTheLiteralsType() {
        assertEquals("+1 01", text("INTERVAL '1' DAY + INTERVAL '1' HOUR"));
        assertEquals("+0", text("INTERVAL '1' DAY / 2"));
        assertEquals("+1", text("INTERVAL '1' DAY * 1.5"));
        assertEquals("-1", text("-INTERVAL '1' DAY"));
        assertEquals("+1-01", text("INTERVAL '1' YEAR + INTERVAL '1' MONTH"));
        assertEquals("+2 04", text("INTERVAL '1 02' DAY TO HOUR * 2"));
        assertEquals("+2", text("INTERVAL '1' DAY * ABS(-2)"));
        assertEquals("+200", text("INTERVAL '1' DAY(2) * 200"));
        assertEquals("2024-03-31", text("DATE '2024-01-31' + INTERVAL '1' MONTH * 2"));
        assertEquals("+1|+1", returned("EXECUTE IMMEDIATE $$ BEGIN RETURN TO_VARCHAR(INTERVAL '1' YEAR * 1.5) || '|'"
            + " || TO_VARCHAR(INTERVAL '3' MONTH / 2); END; $$"));
        assertEquals("INTERVAL DAY(5)[SB8]", returned("EXECUTE IMMEDIATE $$ BEGIN RETURN"
            + " SYSTEM$TYPEOF(INTERVAL '1' DAY(2) * 200); END; $$"));
    }

    /** A literal's fractional precision prints, with or without arithmetic around it. */
    @Test
    public void aLiteralPrintsByItsPrecisions() {
        assertEquals("+1.250", text("INTERVAL '1.25' SECOND(2,3)"));
        assertEquals("+1 02:03:04", text("INTERVAL '1 02:03:04' DAY TO SECOND(0)"));
        assertEquals("+3:04.5", returned("EXECUTE IMMEDIATE $$ DECLARE v VARCHAR; BEGIN"
            + " v := TO_VARCHAR(INTERVAL '03:04.5' MINUTE(2) TO SECOND(1)); RETURN v; END; $$"));
        assertEquals("+1.250", returned("EXECUTE IMMEDIATE $$ DECLARE x VARCHAR; BEGIN"
            + " x := INTERVAL '1.25' SECOND(2,3); RETURN x; END; $$"));
        assertEquals("+0", returned("EXECUTE IMMEDIATE $$ DECLARE x VARCHAR; BEGIN x := INTERVAL '1' DAY / 2;"
            + " RETURN x; END; $$"));
        assertEquals("+1 02:03:04", returned("EXECUTE IMMEDIATE $$ DECLARE x VARCHAR; BEGIN"
            + " x := INTERVAL '1 02:03:04' DAY TO SECOND(0); RETURN x; END; $$"));
        assertEquals("+1 01", returned("EXECUTE IMMEDIATE $$ BEGIN LET x VARCHAR := INTERVAL '1' DAY"
            + " + INTERVAL '1' HOUR; RETURN x; END; $$"));
        assertEquals("x+0", returned("EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'x'"
            + " || TO_VARCHAR(INTERVAL '1' DAY / 2); BEGIN RETURN x; END; $$"));
    }

    /** A condition compares the settled values, in IF, CASE and WHILE alike. */
    @Test
    public void aConditionComparesTheSettledValues() {
        assertEquals("truncated", returned("EXECUTE IMMEDIATE $$ BEGIN IF (INTERVAL '1' DAY / 2 = INTERVAL '0' DAY)"
            + " THEN RETURN 'truncated'; END IF; RETURN 'kept'; END; $$"));
        assertEquals("truncated", returned("EXECUTE IMMEDIATE $$ BEGIN CASE WHEN INTERVAL '1' DAY / 2"
            + " = INTERVAL '0' DAY THEN RETURN 'truncated'; ELSE RETURN 'kept'; END CASE; END; $$"));
        assertEquals("more", returned("EXECUTE IMMEDIATE $$ BEGIN IF (INTERVAL '1' DAY * 2 > INTERVAL '36' HOUR)"
            + " THEN RETURN 'more'; END IF; RETURN 'less'; END; $$"));
        assertEquals("3", returned("EXECUTE IMMEDIATE $$ DECLARE i INTEGER DEFAULT 0; BEGIN"
            + " WHILE (INTERVAL '1' HOUR * i < INTERVAL '3' HOUR) DO i := i + 1; END WHILE; RETURN i; END; $$"));
    }

    /** A variable the block reads scales the literal as the number it holds; a FLOAT is refused. */
    @Test
    public void aVariableScalesTheLiteral() {
        assertEquals("+0", returned("EXECUTE IMMEDIATE $$ DECLARE n NUMBER := 2; BEGIN"
            + " RETURN TO_VARCHAR(INTERVAL '1' DAY / n); END; $$"));
        assertEquals("+1|INTERVAL DAY(5)[SB8]", returned("EXECUTE IMMEDIATE $$ DECLARE n NUMBER(3,1) := 1.5; BEGIN"
            + " RETURN TO_VARCHAR(INTERVAL '1' DAY(2) * n) || '|' || SYSTEM$TYPEOF(INTERVAL '1' DAY(2) * n); END; $$"));
        assertEquals("2024-03-31", returned("EXECUTE IMMEDIATE $$ DECLARE d DATE := '2024-01-31'; BEGIN"
            + " RETURN TO_VARCHAR(d + INTERVAL '1' MONTH * 2); END; $$"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : Interval division"
            + " by zero", returned("EXECUTE IMMEDIATE $$ DECLARE n NUMBER := 0; BEGIN"
            + " RETURN TO_VARCHAR(INTERVAL '1' DAY / n); END; $$"));
        final String floatFactor = returned("EXECUTE IMMEDIATE $$ DECLARE f FLOAT := 1.5; BEGIN"
            + " RETURN TO_VARCHAR(INTERVAL '1' DAY * f); END; $$");
        assertTrue(floatFactor.startsWith("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 :"
            + " SQL compilation error:"), floatFactor);
        assertTrue(floatFactor.endsWith("Invalid argument types for function '*': (INTERVAL DAY(9), FLOAT)"),
            floatFactor);
    }

    /** A result past the leading precision is refused at the expression the block evaluates. */
    @Test
    public void aResultPastTheLeadingPrecisionIsRefused() {
        final String range = "Interval out of representable range after multiply, type: "
            + "INTERVAL_DAY_TIME[SB16](9,6){not null}";
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : " + range,
            text("INTERVAL '999999999' DAY * 10"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 19 : " + range,
            returned("EXECUTE IMMEDIATE $$ BEGIN\n  LET a VARCHAR := TO_VARCHAR(\n     INTERVAL '999999999' DAY * 10);\n"
                + "  RETURN a;\nEND; $$"));
    }

    /** A stored procedure's body is a block like any other. */
    @Test
    public void aProcedureBodyKeepsTheLiteralsType() {
        engine.execute("CREATE OR REPLACE PROCEDURE interval_script_p() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN"
            + " RETURN TO_VARCHAR(INTERVAL '1' DAY + INTERVAL '1' HOUR) || '|' || TO_VARCHAR(INTERVAL '1' DAY / 2);"
            + " END; $$");
        assertEquals("+1 01|+0", returned("CALL interval_script_p()"));
    }

    /** A literal that names no type or does not read refuses the block while it compiles, untaken branch or not. */
    @Test
    public void anUnreadableLiteralRefusesTheWholeBlock() {
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY", returned("EXECUTE IMMEDIATE $$ BEGIN"
            + " IF (FALSE) THEN RETURN TO_VARCHAR(INTERVAL '1' DAY(0)); END IF; RETURN 'ok'; END; $$"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 6 unexpected '2'.",
            returned("EXECUTE IMMEDIATE $$ BEGIN IF (FALSE) THEN RETURN TO_VARCHAR(CURRENT_DATE"
                + " + INTERVAL '1 day 2 hours'); END IF; RETURN 'ok'; END; $$"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY", returned("EXECUTE IMMEDIATE $$"
            + " DECLARE x INT; BEGIN x := nosuch; RETURN TO_VARCHAR(INTERVAL '1' DAY(0)); END; $$"));
        assertEquals("Invalid specification for type INTERVAL: INTERVAL DAY",
            returned("BEGIN RETURN TO_VARCHAR(INTERVAL '1' DAY(0)); END"));
    }
}
