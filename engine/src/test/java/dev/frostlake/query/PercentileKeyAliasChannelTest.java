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
 * The channel a percentile's WITHIN GROUP key resolves through, and the type rule MEDIAN shares with it.
 *
 * <p>★ THE KEY REACHES THE SELECT LIST. A key naming a sibling SELECT ALIAS means that alias's defining
 * EXPRESSION evaluated per row — the same channel an ordinary aggregate argument travels, which MEDIAN,
 * SUM, AVG, LISTAGG and MAX all already reached. The percentiles did not: the key was read straight off
 * the parse tree, so the name resolved against the relation alone and the call was refused as an invalid
 * identifier.
 *
 * <p>★ A REAL COLUMN OUTRANKS AN ALIAS OF THE SAME NAME, so an alias that shadows a column changes
 * nothing — the whole point of resolving the column first rather than substituting text blindly.
 *
 * <p>★ MEDIAN IS THE 0.5 PERCENTILE, AND SHARES THE KEY'S TYPE RULE. A temporal key is refused at
 * compile time with one sentence for the whole family:
 *
 * <pre>
 *   PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY &lt;DATE&gt;)   incompatible types: [DATE] and [NUMBER(9,0)]
 *   PERCENTILE_DISC …                                       same
 *   MEDIAN(&lt;DATE&gt;)                                          same
 * </pre>
 *
 * <p>★ THE RULE DOES NOT CARE ABOUT THE OVER CLAUSE. A windowed median or percentile over a date is
 * refused exactly as the plain one is — measured, not assumed, because a rule about an argument's type
 * has no obvious reason to reach a window and every reason to be checked.
 *
 * <p>★ THE TWO RULES MEET. A key naming an alias that is itself a DATE has to be resolved through the
 * alias BEFORE it can be typed, so the alias channel is what makes the type rule reach it at all.
 */
public class PercentileKeyAliasChannelTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE pk (n102 NUMBER(10,2), d DATE, z NUMBER(10,2))");
        engine.execute("INSERT INTO pk VALUES (1.00, '2026-01-01', 100.00),"
            + " (2.00, '2026-01-02', 200.00), (8.00, '2026-01-03', 800.00)");
    }

    /** Every row's LAST column, or the refusal — the surface both engines share. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            while (rs.next()) {
                all.append(" ").append(String.valueOf(rs.getValue(rs.getColumns().size() - 1)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** ★ A key naming a sibling SELECT alias resolves through it. */
    @Test
    public void thekeyResolvesASiblingSelectAlias() {
        assertEquals("ACCEPTED: 1.00000 2.00000 8.00000",
            answer("SELECT n102 AS zz, TO_VARCHAR(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY zz))"
                + " FROM pk GROUP BY n102 ORDER BY zz"));
        assertEquals("ACCEPTED: 1.00 2.00 8.00",
            answer("SELECT n102 AS zz, TO_VARCHAR(PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY zz))"
                + " FROM pk GROUP BY n102 ORDER BY zz"));
    }

    /** The aggregates that already reached the alias channel must keep reaching it. */
    @Test
    public void theotherAggregatesReachTheSameChannel() {
        assertEquals("ACCEPTED: 1.00000 2.00000 8.00000",
            answer("SELECT n102 AS zz, TO_VARCHAR(MEDIAN(zz)) FROM pk GROUP BY n102 ORDER BY zz"));
        assertEquals("ACCEPTED: 1.00 2.00 8.00",
            answer("SELECT n102 AS zz, TO_VARCHAR(SUM(zz)) FROM pk GROUP BY n102 ORDER BY zz"));
        assertEquals("ACCEPTED: 1.00000000 2.00000000 8.00000000",
            answer("SELECT n102 AS zz, TO_VARCHAR(AVG(zz)) FROM pk GROUP BY n102 ORDER BY zz"));
    }

    /** ★ A REAL COLUMN OUTRANKS AN ALIAS of the same name — z is the column's 100/200/800, not n102. */
    @Test
    public void acolumnOutranksAnAliasOfTheSameName() {
        assertEquals("ACCEPTED: 100.00000 200.00000 800.00000",
            answer("SELECT n102 AS z, TO_VARCHAR(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY z))"
                + " FROM pk GROUP BY n102 ORDER BY 1"));
        assertEquals("ACCEPTED: 100.00000 200.00000 800.00000",
            answer("SELECT n102 AS z, TO_VARCHAR(MEDIAN(z)) FROM pk GROUP BY n102 ORDER BY 1"));
    }

    /** ★ MEDIAN over a DATE is the percentiles' own refusal, reached through its single argument. */
    @Test
    public void medianOverATemporalArgumentIsIncompatible() {
        assertEquals("SQL compilation error:|incompatible types: [DATE] and [NUMBER(9,0)]",
            answer("SELECT MEDIAN(d) FROM pk"));
        assertEquals("SQL compilation error:|incompatible types: [DATE] and [NUMBER(9,0)]",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY d) FROM pk"));
    }

    /** ★ THE OVER CLAUSE DOES NOT EXEMPT IT — the rule is about the argument's type. */
    @Test
    public void awindowedFormIsRefusedTheSameWay() {
        assertEquals("SQL compilation error:|incompatible types: [DATE] and [NUMBER(9,0)]",
            answer("SELECT MEDIAN(d) OVER (PARTITION BY n102) FROM pk ORDER BY n102"));
        assertEquals("SQL compilation error:|incompatible types: [DATE] and [NUMBER(9,0)]",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY d) OVER (PARTITION BY n102)"
                + " FROM pk ORDER BY n102"));
    }

    /** ★ THE TWO RULES MEET: a key naming an alias that is a DATE is typed through the alias. */
    @Test
    public void analiasNamingATemporalColumnIsTypedThroughIt() {
        assertEquals("SQL compilation error:|incompatible types: [DATE] and [NUMBER(9,0)]",
            answer("SELECT d AS dd, MEDIAN(dd) FROM pk GROUP BY d ORDER BY dd"));
        assertEquals("SQL compilation error:|incompatible types: [DATE] and [NUMBER(9,0)]",
            answer("SELECT d AS dd, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY dd) FROM pk"
                + " GROUP BY d ORDER BY dd"));
    }

    /**
     * ★ THE ALIAS IS REACHED INSIDE A LARGER KEY, not only when it IS the key. Matching the whole key
     * text against a whole alias left {@code DATEADD(day, 1, dd)} holding a name that resolved to
     * nothing, and a temporal function over an argument it cannot type falls back to TIMESTAMP_NTZ —
     * so the refusal named the wrong type. Written as arithmetic instead, the key was ANSWERED.
     */
    @Test
    public void analiasIsResolvedWhereverItAppearsInTheKey() {
        assertEquals("SQL compilation error:|incompatible types: [DATE] and [NUMBER(9,0)]",
            answer("SELECT d AS dd, MEDIAN(DATEADD(day, 1, dd)) FROM pk GROUP BY d ORDER BY dd"));
        assertEquals("SQL compilation error:|incompatible types: [DATE] and [NUMBER(9,0)]",
            answer("SELECT d AS dd, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY DATEADD(day, 1, dd))"
                + " FROM pk GROUP BY d ORDER BY dd"));
        assertEquals("SQL compilation error:|incompatible types: [DATE] and [NUMBER(9,0)]",
            answer("SELECT d AS dd, PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY DATEADD(day, 1, dd))"
                + " FROM pk GROUP BY d ORDER BY dd"));
        // Arithmetic rather than a call, which Frostlake answered before the alias was resolved.
        assertEquals("SQL compilation error:|incompatible types: [DATE] and [NUMBER(9,0)]",
            answer("SELECT d AS dd, MEDIAN(dd + 1) FROM pk GROUP BY d ORDER BY dd"));
        // An alias defined by another alias, which needs the substitution to repeat.
        assertEquals("SQL compilation error:|incompatible types: [DATE] and [NUMBER(9,0)]",
            answer("SELECT d AS dd, dd AS ee, MEDIAN(DATEADD(day, 1, ee)) FROM pk GROUP BY d"
                + " ORDER BY dd"));
    }

    /**
     * A NUMERIC alias inside a larger key is answered, so resolving the alias must not make the rule
     * fire on everything it can now see.
     */
    @Test
    public void anumericAliasInsideAKeyIsStillAnswered() {
        assertEquals("ACCEPTED: 2.00000",
            answer("SELECT n102 AS nn, TO_VARCHAR(MEDIAN(nn + 1)) FROM pk GROUP BY n102"
                + " ORDER BY nn LIMIT 1"));
    }

    /** The plain and computed keys, which already agreed, must not move. */
    @Test
    public void theordinaryKeysAreUntouched() {
        assertEquals("ACCEPTED: 2.00000",
            answer("SELECT TO_VARCHAR(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102)) FROM pk"));
        assertEquals("ACCEPTED: 4.00000",
            answer("SELECT TO_VARCHAR(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102 * 2)) FROM pk"));
        assertEquals("SQL compilation error: error line 1 at position 51|invalid identifier 'NOSUCHCOL'",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY nosuchcol) FROM pk"),
            "an alias exemption must not blind the identifier check");
    }
}
