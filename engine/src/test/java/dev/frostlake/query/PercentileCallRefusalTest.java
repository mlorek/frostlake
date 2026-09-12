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

/**
 * The shapes a percentile call is REFUSED for, every one of which Frostlake accepted — most answering
 * NULL, which is worse than a wrong answer because nothing marks it.
 *
 * <p>★ A MISSING WITHIN GROUP AND A TWO-ELEMENT ONE ARE ONE REFUSAL, not two: live's sentence says
 * "containing a single order by element" and covers both, which is why the rule is written as "exactly
 * one" rather than as two checks.
 *
 * <p>★ THE ARGUMENT NUMBER IS NOT THE WRITTEN POSITION. A column reference is "argument 1" and a
 * computed expression "argument 0" — for the same, only, first-written argument. Live's plan puts the
 * ordered value FIRST and the fraction second (its nested-aggregate echo prints
 * {@code PERCENTILE_CONT(CAST(… AS …), 0.5)}), so "argument 1" is the fraction's place in the PLAN
 * while "argument 0" is its place as written, and which one appears depends on the stage that refuses.
 * Reproduced as measured rather than tidied.
 *
 * <p>★ THE RANGE REFUSAL CARRIES NO COMPILATION PREFIX — it is a bad VALUE, not a bad statement — and
 * echoes the fraction exactly as written. The boundaries are INCLUSIVE: 0 and 1 both run.
 *
 * <p>★ THE BAD COLUMN WAS A SCOPE HOLE, not a wording difference: an unresolvable identifier inside
 * WITHIN GROUP was never walked at all, so the call answered NULL over a column that does not exist.
 *
 * <p>★ AN ARGUMENT-TYPE COMPLAINT OUTRANKS THE CONSTANT ONE, which the engine suite caught:
 * {@code PERCENTILE_CONT(<OBJECT column>)} is an invalid-argument-types refusal live, not a complaint
 * that an object is not constant.
 *
 * <p>Left for their own tasks: an AGGREGATE nested in the key, whose refusal echoes the call as a plan
 * with an inserted CAST and its arguments reordered; and a VARCHAR key, refused by live at ROW time.
 */
public class PercentileCallRefusalTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE pc (n102 NUMBER(10,2), n380 NUMBER(38,0),"
            + " n11 NUMBER(1,1), d DATE, ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO pc VALUES (1.00, 1, 0.1, '2020-01-01', '2020-01-01 00:00:00'),"
            + " (2.00, 2, 0.2, '2020-01-02', '2020-01-02 00:00:00'),"
            + " (8.00, 4, 0.4, '2020-01-03', '2020-01-03 00:00:00')");
        engine.execute("CREATE OR REPLACE TABLE pce (n102 NUMBER(10,2))");
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

    private String singleElement(final String function) {
        return "SQL compilation error:|Function " + function
            + " requires a WITHIN GROUP clause containing a single order by element.";
    }

    /** ★ One sentence for a MISSING clause and a two-element one alike. */
    @Test
    public void thewithinGroupClauseTakesExactlyOneElement() {
        assertEquals(singleElement("PERCENTILE_CONT"),
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102, n380) FROM pc"));
        assertEquals(singleElement("PERCENTILE_CONT"),
            answer("SELECT PERCENTILE_CONT(0.5) FROM pc"),
            "no clause at all is the SAME refusal");
        assertEquals(singleElement("PERCENTILE_DISC"),
            answer("SELECT PERCENTILE_DISC(0.5) FROM pc"));
        assertEquals(singleElement("PERCENTILE_DISC"),
            answer("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n102, n380) FROM pc"));
    }

    /** ★ A fraction outside [0,1], refused with NO compilation prefix and the literal as written. */
    @Test
    public void afractionOutsideTheUnitRangeIsRefused() {
        assertEquals("Invalid parameter value: 2. Reason: percentile must be between 0 and 1",
            answer("SELECT PERCENTILE_CONT(2) WITHIN GROUP (ORDER BY n102) FROM pc"));
        assertEquals("Invalid parameter value: -0.5. Reason: percentile must be between 0 and 1",
            answer("SELECT PERCENTILE_CONT(-0.5) WITHIN GROUP (ORDER BY n102) FROM pc"));
        assertEquals("Invalid parameter value: 1.5. Reason: percentile must be between 0 and 1",
            answer("SELECT PERCENTILE_CONT(1.5) WITHIN GROUP (ORDER BY n102) FROM pc"));
    }

    /** ★ The boundaries are INCLUSIVE — 0 and 1 both run. */
    @Test
    public void theboundariesAreInclusive() {
        assertEquals("ACCEPTED: 1.00000",
            answer("SELECT PERCENTILE_CONT(0) WITHIN GROUP (ORDER BY n102) FROM pc"));
        assertEquals("ACCEPTED: 8.00000",
            answer("SELECT PERCENTILE_CONT(1) WITHIN GROUP (ORDER BY n102) FROM pc"));
    }

    /** ★ A NON-CONSTANT fraction, with live's own two argument numbers. */
    @Test
    public void anonConstantFractionIsRefused() {
        assertEquals("SQL compilation error:|argument 1 to function PERCENTILE_CONT"
            + " needs to be constant, found 'PC.N11'",
            answer("SELECT PERCENTILE_CONT(n11) WITHIN GROUP (ORDER BY n102) FROM pc"),
            "a COLUMN is argument 1, echoed as the plan resolves it");
        assertEquals("SQL compilation error:|argument 0 to function PERCENTILE_CONT"
            + " needs to be constant, found '0.2 + 0.3'",
            answer("SELECT PERCENTILE_CONT(0.2 + 0.3) WITHIN GROUP (ORDER BY n102) FROM pc"),
            "a computed expression is argument 0, echoed as written");
    }

    /** A SECOND argument is an argument-type refusal, positioned at the call. */
    @Test
    public void asecondArgumentIsRefusedByType() {
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for"
            + " function 'PERCENTILE_CONT': (NUMBER(2,1), NUMBER(2,1))",
            answer("SELECT PERCENTILE_CONT(0.5, 0.5) WITHIN GROUP (ORDER BY n102) FROM pc"));
    }

    /** ★ A bad column inside WITHIN GROUP — the scope hole, named at its own offset. */
    @Test
    public void abadColumnInsideWithinGroupIsNamed() {
        assertEquals("SQL compilation error: error line 1 at position 51|invalid identifier 'NOSUCHCOL'",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY nosuchcol) FROM pc"));
        assertEquals("SQL compilation error: error line 1 at position 51|invalid identifier 'NOSUCHCOL'",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY nosuchcol) FROM pce"),
            "over an EMPTY relation too, because it is a compile-time refusal");
    }

    /** ★ A TEMPORAL key is incompatible with the numeric the percentile is defined over. */
    @Test
    public void atemporalKeyIsIncompatible() {
        assertEquals("SQL compilation error:|incompatible types: [DATE] and [NUMBER(9,0)]",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY d) FROM pc"));
        assertEquals("SQL compilation error:|incompatible types: [TIMESTAMP_NTZ(9)] and [NUMBER(9,0)]",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY ts) FROM pc"));
        assertEquals("SQL compilation error:|incompatible types: [DATE] and [NUMBER(9,0)]",
            answer("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY d) FROM pc"),
            "the PICKING form too, though it never does arithmetic on the value");
    }

    /** The arity refusal, which already agreed, must not move. */
    @Test
    public void thearityRefusalIsUntouched() {
        assertEquals("SQL compilation error: error line 1 at position 7|not enough arguments for"
            + " function [PERCENTILE_CONT()], expected 1, got 0",
            answer("SELECT PERCENTILE_CONT() WITHIN GROUP (ORDER BY n102) FROM pc"));
    }

    /** A well-formed call is untouched. */
    @Test
    public void awellFormedCallIsUntouched() {
        assertEquals("ACCEPTED: 2.00000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102) FROM pc"));
        assertEquals("ACCEPTED: 2.00",
            answer("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n102) FROM pc"));
    }
}
