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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A conditional's chosen branch CONVERTED to the type its branches fold to — the difference between
 * declaring a type and holding one. Frostlake folded the type correctly and then handed the VARIANT
 * back uncast, so a DATE-declared column answered a number and nothing marked it.
 *
 * <pre>
 *   COALESCE(&lt;VARIANT 1&gt;, &lt;DATE&gt;)              Failed to cast variant value 1 to DATE
 *   COALESCE(&lt;VARIANT "2020-01-01"&gt;, &lt;DATE&gt;)   2020-01-01, the quotes gone
 * </pre>
 *
 * <p>★ THE TWO CELLS ABOVE ARE THE WHOLE TEST. A fix that only refuses passes the first and fails the
 * second; one that only unwraps passes the second and fails the first. Both come from the CAST itself
 * rather than from a rule restated here, which is why every spelling agrees at once.
 *
 * <p>★ SIX SPELLINGS, ONE RULE: IFF, COALESCE, NVL, IFNULL, CASE and DECODE. The first four share one
 * short-circuit exit, CASE is a clause with its own, and DECODE does not short-circuit at all — three
 * paths, but the rule is applied where the conditional's value meets its folded type in each.
 *
 * <p>★ IT IS A ROW-TIME REFUSAL, NOT A COMPILE-TIME ONE. Over an EMPTY table there is no value to
 * convert, so the query answers nothing rather than refusing — measured on both engines, and the reason
 * this cannot be lifted into the type checker.
 *
 * <p>★ A FOLD THAT STAYS VARIANT CONVERTS NOTHING. {@code COALESCE(<VARIANT>, <OBJECT>)} folds to
 * VARIANT and answers the variant unchanged; the conversion is scoped to a VARIANT source meeting a
 * CONCRETE folded type, which is the case where the value's type and the column's genuinely differ.
 */
public class ConditionalBranchCastTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE vf (va VARIANT, vd VARIANT, vs VARIANT,"
            + " d DATE, n NUMBER(10,2), s VARCHAR, obj OBJECT)");
        engine.execute("INSERT INTO vf SELECT PARSE_JSON('1'), PARSE_JSON('\"2020-01-01\"'),"
            + " PARSE_JSON('\"abc\"'), '2020-01-01', 1.00, 'x', PARSE_JSON('{\"k\":1}')");
        engine.execute("CREATE OR REPLACE TABLE vempty (va VARIANT, d DATE)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            while (rs.next()) {
                all.append(" ").append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** ★ A VARIANT holding a NUMBER cannot become the folded DATE, in every spelling. */
    @Test
    public void avariantThatCannotBecomeTheFoldedTypeIsRefused() {
        assertEquals("Failed to cast variant value 1 to DATE",
            answer("SELECT COALESCE(va, d) FROM vf"));
        assertEquals("Failed to cast variant value 1 to DATE",
            answer("SELECT IFF(n = 1, va, d) FROM vf"));
        assertEquals("Failed to cast variant value 1 to DATE",
            answer("SELECT NVL(va, d) FROM vf"));
        assertEquals("Failed to cast variant value 1 to DATE",
            answer("SELECT IFNULL(va, d) FROM vf"));
    }

    /** CASE is a conditional written as a clause, and DECODE does not short-circuit — both agree. */
    @Test
    public void thecaseClauseAndDecodeAgree() {
        assertEquals("Failed to cast variant value 1 to DATE",
            answer("SELECT CASE WHEN n = 1 THEN va ELSE d END FROM vf"));
        assertEquals("Failed to cast variant value 1 to DATE",
            answer("SELECT DECODE(n, 1, va, d) FROM vf"));
    }

    /** ★ THE OTHER HALF: a VARIANT holding a date STRING becomes the date, quotes gone. */
    @Test
    public void avariantHoldingTheTextOfTheFoldedTypeConvertsToIt() {
        assertEquals("ACCEPTED: 2020-01-01", answer("SELECT COALESCE(vd, d) FROM vf"));
        assertEquals("ACCEPTED: 2020-01-01", answer("SELECT IFF(n = 1, vd, d) FROM vf"));
        assertEquals("ACCEPTED: 2020-01-01",
            answer("SELECT CASE WHEN n = 1 THEN vd ELSE d END FROM vf"));
    }

    /** A VARIANT string beside a VARCHAR folds to the string, so the conversion unwraps it. */
    @Test
    public void avariantStringBesideAVarcharUnwraps() {
        assertEquals("ACCEPTED: abc", answer("SELECT COALESCE(vs, s) FROM vf"));
    }

    /** ★ A FOLD THAT STAYS VARIANT CONVERTS NOTHING. */
    @Test
    public void avariantFoldIsLeftAlone() {
        assertEquals("ACCEPTED: 1", answer("SELECT COALESCE(va, obj) FROM vf"));
    }

    /** ★ ROW-TIME, NOT COMPILE-TIME: an empty table has nothing to convert and answers nothing. */
    @Test
    public void anemptyTableIsStillAccepted() {
        assertEquals("ACCEPTED:", answer("SELECT COALESCE(va, d) FROM vempty"));
        assertEquals("ACCEPTED:", answer("SELECT CASE WHEN 1 = 1 THEN va ELSE d END FROM vempty"));
    }

    /** The bare cast this reuses, and the conditionals over matching types, must not move. */
    @Test
    public void thecastAndTheOrdinaryConditionalsAreUntouched() {
        assertEquals("Failed to cast variant value 1 to DATE",
            answer("SELECT PARSE_JSON('1')::DATE FROM vf"));
        assertEquals("ACCEPTED: 2020-01-01", answer("SELECT PARSE_JSON('\"2020-01-01\"')::DATE FROM vf"));
        assertEquals("ACCEPTED: 2020-01-01", answer("SELECT COALESCE(d, d) FROM vf"));
        assertEquals("ACCEPTED: 1.00", answer("SELECT COALESCE(n, n) FROM vf"));
    }

    /**
     * ★ COALESCE AND NVL CONVERT A BRANCH THEY NEVER RETURN. Live plans the two as
     * {@code COALESCE(CAST(a AS T), CAST(b AS T))} and applies every cast, so a value it does not hand
     * back can still fail the row — {@code obj} is object-shaped and IS what would be returned, and the
     * statement is refused anyway for the {@code 1} sitting in the branch behind it.
     */
    @Test
    public void coalesceAndNvlConvertABranchTheyNeverReturn() {
        assertEquals("Failed to cast variant value 1 to OBJECT",
            answer("SELECT COALESCE(obj, va) FROM vf"));
        assertEquals("Failed to cast variant value 1 to OBJECT",
            answer("SELECT NVL(obj, va) FROM vf"));
        assertEquals("Failed to cast variant value 1 to OBJECT",
            answer("SELECT COALESCE(obj, obj, va) FROM vf"));
        // A computed branch is converted just the same — it is the fold that decides, not the shape.
        assertEquals("Failed to cast variant value 1 to OBJECT",
            answer("SELECT COALESCE(obj, PARSE_JSON('1')) FROM vf"));
        assertEquals("Failed to cast variant value 1 to OBJECT",
            answer("SELECT COALESCE(obj, TO_VARIANT(1)) FROM vf"));
    }

    /** ★ The CONDITIONAL family does NOT do this — the same pairing is answered by all three. */
    @Test
    public void theconditionalsLeaveTheirUnselectedBranchAlone() {
        assertEquals("ACCEPTED: {\"k\":1}",
            answer("SELECT IFF(TRUE, obj, va) FROM vf"));
        assertEquals("ACCEPTED: {\"k\":1}",
            answer("SELECT CASE WHEN TRUE THEN obj ELSE va END FROM vf"));
        assertEquals("ACCEPTED: {\"k\":1}",
            answer("SELECT DECODE(1, 1, obj, va) FROM vf"));
    }

    /**
     * ★ THE GUARD IDIOM SURVIVES, because a branch needing no conversion is never touched. Both of
     * these answer on live too: the fold is numeric, the second branch is numeric, so there is no cast
     * to apply and the division is never computed.
     */
    @Test
    public void abranchNeedingNoConversionIsNeverEvaluated() {
        // The unselected division is never computed, but its NUMBER(7,6) still widens the fold, and
        // the chosen value is PRESENTED at that fold — 1.000000, live-verified — see
        // ConditionalValueAtFoldTest for the whole family.
        assertEquals("ACCEPTED: 1.000000", answer("SELECT COALESCE(n, 1/0) FROM vf"));
        assertEquals("ACCEPTED: 1.000000", answer("SELECT NVL(n, 1/0) FROM vf"));
        assertEquals("ACCEPTED: 1.000000", answer("SELECT IFF(TRUE, 1, 1/0) FROM vf"));
        assertEquals("ACCEPTED: 1.000000", answer("SELECT CASE WHEN TRUE THEN 1 ELSE 1/0 END FROM vf"));
        // A branch that would fail its own CONVERSION is not evaluated either, for the same reason.
        assertTrue(answer("SELECT IFF(TRUE, 1, TO_NUMBER('a')) FROM vf").startsWith("ACCEPTED:"));
    }

    /** A NULL in the unreturned branch converts to nothing, and an ARRAY fold accepts a scalar. */
    @Test
    public void aconvertibleUnreturnedBranchChangesNothing() {
        engine.execute("CREATE OR REPLACE TABLE vnull (va VARIANT, obj OBJECT)");
        engine.execute("INSERT INTO vnull SELECT NULL, PARSE_JSON('{\"k\":1}')");
        assertEquals("ACCEPTED: {\"k\":1}", answer("SELECT COALESCE(obj, va) FROM vnull"));
        engine.execute("CREATE OR REPLACE TABLE varr (va VARIANT, arr ARRAY)");
        engine.execute("INSERT INTO varr SELECT TO_VARIANT(1), ARRAY_CONSTRUCT(1, 2)");
        assertEquals("ACCEPTED: [1,2]", answer("SELECT COALESCE(arr, va) FROM varr"));
    }
}
