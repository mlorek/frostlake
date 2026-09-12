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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROUND's optional third argument, the rounding MODE.
 *
 * <p>★ FROSTLAKE IGNORED IT OUTRIGHT — any third argument was accepted and dropped, so
 * {@code ROUND(n, 0, 'nosuch')} answered as if the word had been a mode. Live refuses it while the
 * statement COMPILES: the same call over an EMPTY table still refuses, on both engines now.
 *
 * <p>★ THE "at position 21" IS A CHARACTER OFFSET, not an argument ordinal. Moving the statement five
 * characters right moves the prefix's position 7 → 12 AND the trailing one 21 → 26, which is what
 * separates this template from BASE64_ENCODE's identical-looking one, where the number really is the
 * ordinal. Both offsets are asserted at two different places below, because one of them alone cannot
 * tell the two readings apart. Note the sentence ends with a COMMA.
 *
 * <p>★ THE THIRD ARGUMENT ONLY EXISTS FOR AN EXACT NUMERIC. Over a FLOAT live does not look at the
 * mode at all — it says the call has too many arguments, because that overload takes two. So the
 * ARITY depends on the first argument's family.
 *
 * <p>★ A DELIBERATE WORDING DIVERGENCE, and the reason for it: a non-constant or non-string mode is
 * refused by live as "argument 1 to function 22 needs to be constant" — an internal function id and an
 * internal argument index, neither of them reproducible (the id was 21 in one measurement and 22 in
 * another, for the same call). Frostlake refuses the same shapes with the template its base64 and
 * percentile families already share, naming an honest ordinal and the real function name. The two
 * cells below therefore assert that BOTH engines refuse, and only the part of the sentence they
 * share — the accept/refuse boundary is what matters and it now matches.
 *
 * <p>★ A MODE IS WHAT LIVE FOLDS. A join, a case mapping, CONCAT, CONCAT_WS and a session variable
 * fold to a word that is judged like a literal; TRIM, a CAST, IFF and a number inside the join do not
 * fold and are "not constant". A folded word that names no mode points at -1, not at itself — only a
 * literal or a variable keeps its place. Nothing is trimmed.
 *
 * <p>★ THE ORDER: argument families first — though a BOOLEAN or temporal MODE is "not constant" rather
 * than the wrong type — then a constant NULL anywhere folds the call to NULL, then the overload's arity
 * (a FLOAT or VARIANT takes two), then the mode.
 *
 * <p>What already agreed and must not move: a good mode answers in either case, the word is
 * case-insensitive, a NULL mode makes the whole call NULL, and TRUNC / CEIL / FLOOR have no third
 * argument at all.
 */
public class RoundModeArgumentTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rr (n21 NUMBER(21,0), n102 NUMBER(10,2),"
            + " s VARCHAR, f FLOAT)");
        engine.execute("INSERT INTO rr VALUES (3, 2.50, 'HALF_TO_EVEN', 2.5)");
        engine.execute("CREATE OR REPLACE TABLE rempty (n21 NUMBER(21,0))");
    }

    /** Every row's first column joined, or the refusal. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder();
            while (rs.next()) {
                if (all.length() > 0) {
                    all.append(",");
                }
                all.append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String badMode(final int callAt, final String written, final int writtenAt) {
        return "SQL compilation error: error line 1 at position " + callAt
            + "|invalid argument for function [ROUND] unexpected argument [" + written
            + "] at position " + writtenAt + ",";
    }

    /** ★ A word that is not a mode is refused, and the detail points AT the argument. */
    @Test
    public void awordThatIsNotAModeIsRefused() {
        assertEquals(badMode(7, "nosuch", 21),
            answer("SELECT ROUND(n21, 0, 'nosuch') FROM rr"));
        assertEquals(badMode(7, "zzz", 21), answer("SELECT ROUND(n21, 0, 'zzz') FROM rr"),
            "the echoed value is the argument's own text");
        assertEquals(badMode(7, "", 21), answer("SELECT ROUND(n21, 0, '') FROM rr"),
            "including an empty one");
    }

    /** ★ BOTH numbers are character offsets — the cell that proves it is the same call moved right. */
    @Test
    public void bothNumbersAreCharacterOffsets() {
        assertEquals(badMode(12, "nosuch", 26),
            answer("SELECT      ROUND(n21, 0, 'nosuch') FROM rr"),
            "five spaces move the call's position and the argument's by five each");
        assertEquals(badMode(7, "nosuch", 19), answer("SELECT ROUND(3, 0, 'nosuch')"),
            "and a shorter first argument moves only the second number");
    }

    /** ★ It is a COMPILE-time refusal — an empty table refuses just the same. */
    @Test
    public void anemptyTableIsRefusedToo() {
        assertEquals(badMode(7, "nosuch", 21),
            answer("SELECT ROUND(n21, 0, 'nosuch') FROM rempty"));
    }

    /** ★ Over a FLOAT there is no third argument at all, so the ARITY is what refuses. */
    @Test
    public void afloatHasNoThirdArgument() {
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|too many arguments for function [ROUND] expected 2, got 3",
            answer("SELECT ROUND(f, 0, 'nosuch') FROM rr"));
    }

    /** A non-constant or non-string mode is refused by both — see the class comment for the wording. */
    @Test
    public void anonConstantModeIsRefusedByBoth() {
        assertTrue(answer("SELECT ROUND(n102, 1, 1) FROM rr").contains("needs to be constant"),
            "a numeric mode");
        assertTrue(answer("SELECT ROUND(n102, 0, s) FROM rr").contains("needs to be constant"),
            "and a column one");
    }

    /** What already agreed: a good mode answers, in either case, and NULL makes the call NULL. */
    @Test
    public void agoodModeIsUntouched() {
        assertEquals("2", answer("SELECT ROUND(n102, 0, 'HALF_TO_EVEN') FROM rr"));
        assertEquals("2", answer("SELECT ROUND(n102, 0, 'half_to_even') FROM rr"),
            "the word is case-insensitive");
        assertEquals("3", answer("SELECT ROUND(n102, 0, 'HALF_AWAY_FROM_ZERO') FROM rr"));
        assertEquals("null", answer("SELECT ROUND(n102, 0, NULL) FROM rr"));
        assertEquals("3", answer("SELECT ROUND(n102, 0) FROM rr"), "and two arguments still work");
    }

    /**
     * ★ A mode need not be a literal — live FOLDS a narrow family first and judges what comes out:
     * joins, case mappings, parentheses and a dollar-quoted string all answer.
     */
    @Test
    public void afoldedConstantAnswersLikeALiteral() {
        assertEquals("2", answer("SELECT ROUND(n102, 0, 'HALF_TO_' || 'EVEN') FROM rr"));
        assertEquals("2", answer("SELECT ROUND(n102, 0, UPPER('half_to_even')) FROM rr"));
        assertEquals("2", answer("SELECT ROUND(n102, 0, LOWER('HALF_TO_EVEN')) FROM rr"));
        assertEquals("2", answer("SELECT ROUND(n102, 0, CONCAT('HALF_', 'TO_EVEN')) FROM rr"));
        assertEquals("2", answer("SELECT ROUND(n102, 0, CONCAT_WS('_', 'HALF', 'TO', 'EVEN')) FROM rr"));
        assertEquals("2", answer("SELECT ROUND(n102, 0, CONCAT_WS(NULL, 'HALF_TO_EVEN')) FROM rr"),
            "a lone value never reads the separator");
        assertEquals("2", answer("SELECT ROUND(n102, 0, UPPER(CONCAT('half_', LOWER('TO_EVEN')))) FROM rr"),
            "and the family nests");
        assertEquals("2", answer("SELECT ROUND(n102, 0, (('HALF_TO_EVEN'))) FROM rr"));
        assertEquals("2", answer("SELECT ROUND(n102, 0, $$HALF_TO_EVEN$$) FROM rr"));
        assertEquals("2", answer("SELECT ROUND(n102, 0, COLLATE('HALF_TO_EVEN', 'en-ci')) FROM rr"));
        assertEquals("-3", answer("SELECT ROUND(-n102, 0, 'half_' || 'away_from_zero') FROM rr"));
    }

    /** ★ A folded NULL is still NULL — the whole call answers NULL, as a written one does. */
    @Test
    public void afoldedNullMakesTheCallNull() {
        assertEquals("null", answer("SELECT ROUND(n102, 0, NULL || 'x') FROM rr"));
        assertEquals("null", answer("SELECT ROUND(n102, 0, UPPER(NULL)) FROM rr"));
        assertEquals("null", answer("SELECT ROUND(n102, 0, CONCAT('HALF_TO_EVEN', NULL)) FROM rr"));
        assertEquals("null", answer("SELECT ROUND(n102, 0, CONCAT_WS('_', 'HALF', NULL)) FROM rr"));
        assertEquals("null", answer("SELECT ROUND(n102, 0, (NULL)) FROM rr"));
    }

    /**
     * ★ A folded word that is not a mode is refused while the statement compiles, and since the value
     * was WRITTEN nowhere, the detail's position is -1. The prefix still points at the call.
     */
    @Test
    public void afoldedWordThatIsNotAModeIsRefusedAtNoPosition() {
        assertEquals(badMode(7, "X", -1), answer("SELECT ROUND(n21, 0, UPPER('x')) FROM rr"),
            "the echo is the FOLDED value");
        assertEquals(badMode(7, "x", -1), answer("SELECT ROUND(n21, 0, 'x' || '') FROM rr"));
        assertEquals(badMode(7, "xY", -1), answer("SELECT ROUND(n21, 0, 'x' || UPPER('y')) FROM rr"));
        assertEquals(badMode(7, "xy", -1), answer("SELECT ROUND(n21, 0, CONCAT('x', 'y')) FROM rr"));
        assertEquals(badMode(12, "x", -1), answer("SELECT n21, ROUND(n21, 0, 'x' || '') FROM rr"),
            "the prefix moves with the call; the -1 does not");
        assertEquals(badMode(7, "X", -1), answer("SELECT ROUND(n21, 0, UPPER('x')) FROM rempty"),
            "and an empty table refuses just the same");
    }

    /** ★ Nothing is trimmed: a padded word names no mode, and a parenthesised literal keeps its place. */
    @Test
    public void apaddedWordIsNotAMode() {
        assertEquals(badMode(7, " HALF_TO_EVEN", 21),
            answer("SELECT ROUND(n21, 0, ' HALF_TO_EVEN') FROM rr"));
        assertEquals(badMode(7, "HALF_TO_EVEN ", 21),
            answer("SELECT ROUND(n21, 0, 'HALF_TO_EVEN ') FROM rr"));
        assertEquals(badMode(7, " HALF_TO_EVEN", -1),
            answer("SELECT ROUND(n21, 0, UPPER(' half_to_even')) FROM rr"), "and a fold trims nothing either");
        assertEquals(badMode(7, "x", 22), answer("SELECT ROUND(n21, 0, ('x')) FROM rr"),
            "the literal inside the parentheses is still a literal, pointed at");
        assertEquals(badMode(7, "x", 21), answer("SELECT ROUND(n21, 0, $$x$$) FROM rr"));
    }

    /**
     * ★ What live does NOT fold is refused as non-constant however constant it looks — and so is a
     * number anywhere inside a join, whose implicit cast stops the fold. Worded per the class comment.
     */
    @Test
    public void whatLiveDoesNotFoldIsNotConstant() {
        final String[] modes = {
            "REPLACE('HALF-TO-EVEN', '-', '_')", "SUBSTR('xHALF_TO_EVEN', 2)", "LEFT('HALF_TO_EVENxx', 12)",
            "TRIM(' HALF_TO_EVEN')", "LTRIM(' HALF_TO_EVEN')", "COALESCE(NULL, 'HALF_TO_EVEN')",
            "NVL(NULL, 'HALF_TO_EVEN')", "IFF(TRUE, 'HALF_TO_EVEN', 'x')",
            "CASE WHEN TRUE THEN 'HALF_TO_EVEN' END", "'HALF_TO_EVEN'::VARCHAR",
            "CAST('HALF_TO_EVEN' AS VARCHAR(20))", "TO_VARCHAR('HALF_TO_EVEN')",
            "1 || ''", "CONCAT('x', 1)", "UPPER(1)", "1 + 2", "TRUE",
        };
        for (final String mode : modes) {
            assertTrue(answer("SELECT ROUND(n102, 0, " + mode + ") FROM rr").contains("needs to be constant"),
                mode);
        }
    }

    /**
     * ★ A session variable is a constant too: its value is judged like a literal's, and a refusal points
     * at the variable. Holding a number it is not constant; holding NULL the call is NULL.
     */
    @Test
    public void asessionVariableIsAConstant() {
        try {
            engine.execute("SET round_mode_word = 'HALF_TO_EVEN'");
            assertEquals("2", answer("SELECT ROUND(n102, 0, $round_mode_word) FROM rr"));
            assertEquals("2", answer("SELECT ROUND(n102, 0, $round_mode_word || '') FROM rr"));
            engine.execute("SET round_mode_word = 'x'");
            assertEquals(badMode(7, "x", 21), answer("SELECT ROUND(n21, 0, $round_mode_word) FROM rr"));
            engine.execute("SET round_mode_word = ' HALF_TO_EVEN'");
            assertEquals(badMode(7, " HALF_TO_EVEN", 21),
                answer("SELECT ROUND(n21, 0, $round_mode_word) FROM rr"));
            engine.execute("SET round_mode_word = 3");
            assertTrue(answer("SELECT ROUND(n102, 0, $round_mode_word) FROM rr").contains("needs to be constant"));
            engine.execute("SET round_mode_word = NULL");
            assertEquals("null", answer("SELECT ROUND(n102, 0, $round_mode_word) FROM rr"));
        } finally {
            engine.execute("UNSET round_mode_word");
        }
    }

    /**
     * ★ The mode slot is not part of the numeric signature: a BOOLEAN or a temporal mode is refused as
     * not constant, while an ARRAY or OBJECT one is the wrong type — and a BOOLEAN in a numeric slot is
     * the wrong type even when the mode is bad too.
     */
    @Test
    public void aboolOrTemporalModeIsNotConstantButAnArrayIsTheWrongType() {
        final String[] notConstant = {
            "TRUE", "FALSE", "CURRENT_DATE", "DATE '2020-01-01'", "TIME '10:00:00'", "CURRENT_TIMESTAMP",
            "TO_TIMESTAMP_TZ('2020-01-01')", "PARSE_JSON('\"HALF_TO_EVEN\"')", "1.5",
        };
        for (final String mode : notConstant) {
            assertTrue(answer("SELECT ROUND(2.5, 0, " + mode + ")").contains("needs to be constant"), mode);
        }
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|Invalid argument types for function 'ROUND': (NUMBER(2,1), NUMBER(1,0), ARRAY)",
            answer("SELECT ROUND(2.5, 0, ARRAY_CONSTRUCT())"));
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|Invalid argument types for function 'ROUND': (NUMBER(2,1), NUMBER(1,0), OBJECT)",
            answer("SELECT ROUND(2.5, 0, OBJECT_CONSTRUCT())"));
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|Invalid argument types for function 'ROUND': (BOOLEAN, NUMBER(1,0), VARCHAR(1))",
            answer("SELECT ROUND(TRUE, 0, 'x')"), "a BOOLEAN to round is still the wrong type");
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|Invalid argument types for function 'ROUND': (NUMBER(2,1), BOOLEAN, VARCHAR(1))",
            answer("SELECT ROUND(2.5, TRUE, 'x')"), "and so is a BOOLEAN scale");
    }

    /**
     * ★ A NULL argument folds the call to NULL before its overload is chosen — no mode is judged, and a
     * FLOAT's arity is not either — as long as the NULL is a CONSTANT. Only the argument families come
     * first, and a NULL-valued column is no constant. A fourth argument is plain arity.
     */
    @Test
    public void anullArgumentFoldsTheCallFirst() {
        final String[] nullCalls = {
            "ROUND(NULL, 0, 'x')", "ROUND(2.5, NULL, 'x')", "ROUND(NULL, 0, TRUE)", "ROUND(NULL, 0, 1)",
            "ROUND(2.5::FLOAT, 0, NULL)", "ROUND(2.5::FLOAT, NULL, 'x')", "ROUND(NULL::FLOAT, 0, 'x')",
            "ROUND(2.5::FLOAT, 0, UPPER(NULL))", "ROUND(2.5::FLOAT, 0, NULL || 'x')",
            "ROUND(2.5, 0, NULL::VARCHAR)", "ROUND(PARSE_JSON('2.5'), 0, UPPER(NULL))",
        };
        for (final String call : nullCalls) {
            assertEquals("null", answer("SELECT " + call), call);
        }
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|Invalid argument types for function 'ROUND': (BOOLEAN, NUMBER(1,0), NULL)",
            answer("SELECT ROUND(TRUE, 0, NULL)"), "the families still come first");
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|Invalid argument types for function 'ROUND': (NULL, NUMBER(1,0), ARRAY)",
            answer("SELECT ROUND(NULL, 0, ARRAY_CONSTRUCT())"));
        assertEquals(badMode(7, "x", 19), answer("SELECT ROUND(n, 0, 'x') FROM (SELECT NULL::NUMBER AS n)"),
            "a NULL-valued column is no constant");
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|too many arguments for function [ROUND(2.5, 0, null, 1)] expected 3, got 4",
            answer("SELECT ROUND(2.5, 0, NULL, 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|too many arguments for function [ROUND(2.5, 0, 'x', 1)] expected 3, got 4",
            answer("SELECT ROUND(2.5, 0, 'x', 1)"), "a fourth argument outranks the mode");
    }

    /** ★ A VARIANT takes the FLOAT overload, so it has no third argument either; a VARCHAR has one. */
    @Test
    public void avariantHasNoThirdArgument() {
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|too many arguments for function [ROUND] expected 2, got 3",
            answer("SELECT ROUND(PARSE_JSON('2.5'), 0, 'HALF_TO_EVEN')"));
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|too many arguments for function [ROUND] expected 2, got 3",
            answer("SELECT ROUND(PARSE_JSON('2.5'), 0, 'x')"));
        assertEquals("2", answer("SELECT ROUND('2.5', 0, 'HALF_TO_EVEN')"), "a VARCHAR reads as a NUMBER");
        assertEquals(badMode(7, "x", 23), answer("SELECT ROUND('2.5', 0, 'x')"));
    }

    /** The rest of the family never had a third argument — the control on the arity rule. */
    @Test
    public void therestOfTheFamilyTakesTwo() {
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "|too many arguments for function [TRUNC(RR.N102, 0, 'HALF_TO_EVEN')] expected 2, got 3",
            answer("SELECT TRUNC(n102, 0, 'HALF_TO_EVEN') FROM rr"));
    }
}
