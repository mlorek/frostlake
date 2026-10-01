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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code GETVARIABLE(<name>)} reads a session variable by name, as TEXT.
 *
 * <p>★ THE NAME IS MATCHED EXACTLY against the name SET stores, which is upper-cased: {@code 'SV'} reads
 * the variable {@code SET sv = 5} defined, while {@code 'sv'} and {@code '"SV"'} read nothing.
 *
 * <p>★ A MISSING NAME IS NULL, not a refusal — where {@code $sv} refuses an unset variable outright — and
 * so is a NULL name. UNSET makes the same call answer NULL again.
 *
 * <p>★ THE ANSWER IS TEXT of no width whatever the variable holds: a numeric variable reads {@code '5'},
 * SYSTEM$TYPEOF spells the result the bare word VARCHAR, and {@code GETVARIABLE('SV') + 1} is 6.00000.
 *
 * <p>★ THE NAME MUST BE CONSTANT TEXT, and the CALL is not a constant itself: a column or a number in the
 * name's place is refused while the statement compiles, and so is the call in an argument slot that folds
 * its argument. Every cell is live-verified; only the plan's SPELLING inside those refusals differs, which
 * is recorded on its own.
 */
public class GetVariableTest extends BaseDatabaseTest {

    @BeforeEach
    public void setVariables() {
        engine.execute("SET sv = 5");
        engine.execute("SET tv = 'abc'");
    }

    /** The first cell, or the refusal with its lines joined by '|'. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return rs.next() ? String.valueOf(rs.getValue(0)) : "<no row>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** ★ The name is matched exactly against the upper-cased name SET stores. */
    @Test
    public void theNameIsMatchedExactly() {
        assertEquals("5", answer("SELECT GETVARIABLE('SV')"));
        assertEquals("abc", answer("SELECT GETVARIABLE('TV')"));
        assertEquals("null", answer("SELECT GETVARIABLE('sv')"), "the lower-case name names nothing");
        assertEquals("null", answer("SELECT GETVARIABLE('\"SV\"')"), "nor does a quoted one");
    }

    /** ★ A name no SET defined, and a NULL name, answer NULL. */
    @Test
    public void anUnsetNameAnswersNull() {
        assertEquals("null", answer("SELECT GETVARIABLE('NOPE')"));
        assertEquals("null", answer("SELECT GETVARIABLE(NULL)"));
        engine.execute("UNSET sv");
        assertEquals("null", answer("SELECT GETVARIABLE('SV')"), "and UNSET puts it back to nothing");
    }

    /** ★ The answer is TEXT of no width, whatever the variable holds. */
    @Test
    public void theAnswerIsWidthlessText() {
        assertEquals("VARCHAR[LOB]", answer("SELECT SYSTEM$TYPEOF(GETVARIABLE('SV'))"));
        assertEquals("VARCHAR[LOB]", answer("SELECT SYSTEM$TYPEOF(GETVARIABLE('TV'))"));
        assertEquals("VARCHAR[LOB]", answer("SELECT SYSTEM$TYPEOF(GETVARIABLE('NOPE'))"),
            "an unset name is typed like any other");
        assertEquals("6.00000", answer("SELECT GETVARIABLE('SV') + 1"), "and the text converts as text does");
    }

    /** The variable reference and the function read the same value. */
    @Test
    public void itReadsWhatTheDollarReferenceReads() {
        assertEquals("5", answer("SELECT $sv"));
        assertEquals("5", answer("SELECT GETVARIABLE('SV')"));
    }

    /** ★ The name must be constant text. */
    @Test
    public void theNameMustBeConstantText() {
        assertTrue(answer("SELECT GETVARIABLE(c) FROM (SELECT 'SV' AS c)")
            .startsWith("SQL compilation error:|argument 0 to function GETVARIABLE needs to be constant"),
            answer("SELECT GETVARIABLE(c) FROM (SELECT 'SV' AS c)"));
        assertTrue(answer("SELECT GETVARIABLE(1)")
            .startsWith("SQL compilation error:|argument 0 to function GETVARIABLE needs to be constant"),
            "a number takes an implicit cast, which is no constant");
    }

    /** ★ The call is not a constant, so a folding argument slot refuses it. */
    @Test
    public void theCallIsNotAConstant() {
        assertTrue(answer("SELECT RANDOM(GETVARIABLE('SV')) IS NOT NULL")
            .startsWith("SQL compilation error:|argument 1 to function RANDOM needs to be constant"),
            answer("SELECT RANDOM(GETVARIABLE('SV')) IS NOT NULL"));
    }

    /** The call takes one argument, no more. */
    @Test
    public void itTakesOneArgument() {
        assertTrue(answer("SELECT GETVARIABLE('SV', 1)")
            .startsWith("SQL compilation error: error line 1 at position 7|too many arguments for function"),
            answer("SELECT GETVARIABLE('SV', 1)"));
        assertTrue(answer("SELECT GETVARIABLE()")
            .startsWith("SQL compilation error: error line 1 at position 7|not enough arguments for function"),
            answer("SELECT GETVARIABLE()"));
    }
}
