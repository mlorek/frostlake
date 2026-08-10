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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code POSITION(<needle> IN <haystack>)} — the ANSI infix spelling, which Frostlake read as a syntax
 * error while the account runs it. The same call as the two-argument function form, in the same
 * argument order.
 *
 * <p>The spelling belongs to POSITION ALONE: {@code CHARINDEX('b' IN s)} and {@code UPPER('b' IN s)}
 * are syntax errors live, so the form is gated on the function's name rather than opened to every call
 * the way EXTRACT's {@code FROM} form is. And there is no ANSI {@code FROM} tail — live refuses
 * {@code POSITION('b' IN s FROM 3)}, so the three-argument search has to be written as a call.
 */
public class AnsiPositionFormTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ps (s VARCHAR(10), b BINARY(4), n NUMBER)");
        engine.execute("INSERT INTO ps SELECT 'abcabc', TO_BINARY('4142'), 1");
    }

    private int value(final String expression) {
        final ResultSet rs = engine.executeQuery("SELECT " + expression + " AS c FROM ps");
        rs.next();
        return ((Number) rs.getValue("c")).intValue();
    }

    private void refuses(final String expression) {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT " + expression + " AS c FROM ps");
            }
        }, expression + " should be refused");
    }

    /** The ANSI form finds the needle, over a literal, a column and an expression alike. */
    @Test
    public void theAnsiFormFindsTheNeedle() {
        assertEquals(2, value("POSITION('b' IN s)"));
        assertEquals(1, value("POSITION(s IN s)"));
        assertEquals(0, value("POSITION('b' IN UPPER(s))"));
    }

    /** It works over BINARY operands too, and in lower case. */
    @Test
    public void itWorksOverBinariesAndInLowerCase() {
        assertEquals(2, value("POSITION(TO_BINARY('42') IN b)"));
        assertEquals(2, value("position('b' in s)"));
    }

    /** The function spelling is unchanged, including its three-argument form. */
    @Test
    public void theFunctionSpellingStillWorks() {
        assertEquals(2, value("POSITION('b', s)"));
        assertEquals(5, value("POSITION('b', s, 3)"));
    }

    /** There is no ANSI FROM tail — the third argument must be written as a call. */
    @Test
    public void thereIsNoAnsiFromTail() {
        refuses("POSITION('b' IN s FROM 3)");
    }

    /** And the IN spelling is POSITION's alone: no other function accepts it. */
    @Test
    public void noOtherFunctionTakesTheInSpelling() {
        refuses("CHARINDEX('b' IN s)");
        refuses("UPPER('b' IN s)");
        refuses("SUBSTR('b' IN s)");
    }

    /**
     * That refusal is anchored on the OPERAND after IN, and names it. Live reads the argument as an
     * ordinary membership test, whose right side must be parenthesised, so the bare identifier is the
     * offending token — {@code CHARINDEX('b' IN s)} points at position 24, not at the function name.
     * Frostlake pointed at the name while the alternative was gated by a predicate: eliminating a
     * SYNTACTICALLY VIABLE alternative leaves the decision with nothing to pick, and the refusal
     * anchors where the decision began rather than where the parse actually failed.
     */
    @Test
    public void theRefusalIsAnchoredOnTheOperand() {
        assertAnchoredAt("SELECT CHARINDEX('b' IN s) AS c FROM ps", 24, "s");
        assertAnchoredAt("SELECT UPPER('b' IN s) AS c FROM ps", 20, "s");
        assertAnchoredAt("SELECT LENGTH('b' IN s) AS c FROM ps", 21, "s");
        assertAnchoredAt("SELECT s, CHARINDEX('b' IN s) AS c FROM ps", 27, "s");
        assertAnchoredAt("SELECT CONCAT('a', UPPER('b' IN s)) AS c FROM ps", 32, "s");
        // The IN in a LATER argument was always anchored right — it reaches no competing alternative.
        assertAnchoredAt("SELECT CHARINDEX('b', s IN s) AS c FROM ps", 27, "s");
    }

    /** An ordinary membership test inside an argument is untouched by the opened alternative. */
    @Test
    public void aRealMembershipTestStillParses() {
        engine.executeQuery("SELECT UPPER(IFF(s IN ('abcabc'), 'y', 'n')) AS c FROM ps");
        engine.executeQuery("SELECT COUNT(*) AS c FROM ps WHERE s IN ('abcabc')");
        engine.executeQuery("SELECT IFF(s IN (SELECT s FROM ps), 'y', 'n') AS c FROM ps");
    }

    private void assertAnchoredAt(final String sql, final int position, final String word) {
        String got = "accepted";
        try {
            engine.executeQuery(sql);
        } catch (final RuntimeException refused) {
            got = refused.getMessage().replace('\n', ' ');
        }
        assertTrue(got.contains("syntax error line 1 at position " + position
                + " unexpected '" + word + "'."),
            "[" + sql + "] expected position " + position + ", got: " + got);
    }

    /** The declared type is the position family's nine digits, as CHARINDEX declares. */
    @Test
    public void itDeclaresThePositionWidth() {
        engine.execute("CREATE OR REPLACE VIEW ps_v AS SELECT POSITION('b' IN s) AS c FROM ps");
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE test_db.test_schema.ps_v");
        rs.next();
        assertEquals("""
            {"type":"FIXED","precision":9,"scale":0,"nullable":true}""",
            String.valueOf(rs.getValue("data_type")));
    }
}
