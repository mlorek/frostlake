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
 * An aggregate written inside another aggregate, which Frostlake ACCEPTED — answering NULL, the quiet
 * shape that nothing marks.
 *
 * <p>★ THE OUTER CALL IS ECHOED AS THE PLAN HOLDS IT, not as it was written. For the ordered
 * aggregates that is a different shape entirely: the WITHIN GROUP clause is folded into the argument
 * list, the ordered value comes FIRST and the fraction second, and the value is converted to the type
 * the call returns.
 *
 * <pre>
 *   PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY SUM(n102))
 *       [SUM(AW.N102)] nested in [PERCENTILE_CONT(CAST(SUM(AW.N102) AS NUMBER(25,5)), 0.5)]
 *   PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY SUM(n102))
 *       [SUM(AW.N102)] nested in [PERCENTILE_DISC(SUM(AW.N102), 0.5)]
 * </pre>
 *
 * <p>★ THE CAST APPEARS ONLY WHERE THE TYPE CHANGES, which is why PERCENTILE_DISC has none: it hands
 * back a value from the input and keeps its type, while PERCENTILE_CONT interpolates and widens. The
 * rule is a type comparison rather than a per-function list, and the widths follow the ordered value —
 * NUMBER(25,5) over a scaled sum, NUMBER(38,3) where the widening would pass 38, NUMBER(21,3) over a
 * count.
 *
 * <p>★ MEDIAN IS ECHOED AS PERCENTILE_CONT(x, 0.5). Live does not print the name that was written,
 * because by the time a plan exists MEDIAN has already become the 0.5 percentile — the same identity
 * the type rules rest on.
 *
 * <p>★ WHEN BOTH A NESTED AGGREGATE AND A NESTED WINDOW ARE PRESENT, THE ONE WRITTEN FIRST IS
 * REPORTED. The two rules do not rank against each other; the source position decides. Any fixed order
 * between the two checks gets one of these two statements wrong, which is why they are one walk.
 *
 * <p>★ A WINDOWED OUTER CALL STAYS LEGAL — {@code SUM(SUM(x)) OVER (PARTITION BY k)} is the ordinary
 * running-total-of-a-total and live runs it, because the window is computed after the grouping.
 *
 * <p>★ THE NESTING PREFIX KEEPS ITS SEPARATOR WITH NO POSITION, leaving a trailing space after
 * "SQL compilation error:" — while the window sentence, in the same family, has none.
 */
public class NestedAggregateRefusalTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE aw (n102 NUMBER(10,2), n380 NUMBER(38,0),"
            + " k NUMBER(38,0), t VARCHAR, f FLOAT, b BOOLEAN, i NUMBER(5,0), n302 NUMBER(30,2),"
            + " n52 NUMBER(5,2), n2010 NUMBER(20,10), n3812 NUMBER(38,12))");
        engine.execute("INSERT INTO aw VALUES (1.00, 1, 1, 'a', 1.5, TRUE, 1, 1.5, 1.5, 1.5, 1.5),"
            + " (2.00, 2, 1, 'b', 2.5, FALSE, 2, 2.5, 2.5, 2.5, 2.5), (8.00, 4, 2, 'c', 4, TRUE, 3, 4, 4, 4, 4)");
    }

    /** Every row's first column, or the refusal with its newlines made visible. */
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

    private String nested(final String inner, final String outer) {
        return "SQL compilation error: |Aggregate functions cannot be nested: [" + inner
            + "] nested in [" + outer + "]";
    }

    /** ★ The percentile's echo: reordered, WITHIN GROUP folded away, and a CAST inserted. */
    @Test
    public void thepercentileEchoesItsPlanRatherThanItsSource() {
        assertEquals(nested("SUM(AW.N102)", "PERCENTILE_CONT(CAST(SUM(AW.N102) AS NUMBER(25,5)), 0.5)"),
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY SUM(n102)) FROM aw"));
        assertEquals(nested("SUM(AW.N380)", "PERCENTILE_CONT(CAST(SUM(AW.N380) AS NUMBER(38,3)), 0.5)"),
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY SUM(n380)) FROM aw"),
            "the widening is capped at 38 digits");
        assertEquals(nested("COUNT(*)", "PERCENTILE_CONT(CAST(COUNT(*) AS NUMBER(21,3)), 0.5)"),
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY COUNT(*)) FROM aw"));
    }

    /** ★ PERCENTILE_DISC keeps the input's type, so its echo carries NO cast. */
    @Test
    public void thediscreteFormHasNoCast() {
        assertEquals(nested("SUM(AW.N102)", "PERCENTILE_DISC(SUM(AW.N102), 0.5)"),
            answer("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY SUM(n102)) FROM aw"));
        assertEquals(nested("SUM(AW.N380)", "PERCENTILE_DISC(SUM(AW.N380), 0.5)"),
            answer("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY SUM(n380)) FROM aw"));
    }

    /** ★ MEDIAN is echoed as the 0.5 percentile, under that name. */
    @Test
    public void medianIsEchoedAsThePercentileItIs() {
        assertEquals(nested("SUM(AW.N102)", "PERCENTILE_CONT(CAST(SUM(AW.N102) AS NUMBER(25,5)), 0.5)"),
            answer("SELECT MEDIAN(SUM(n102)) FROM aw"));
    }

    /** An ordinary aggregate echoes as the plain qualified re-print. */
    @Test
    public void anordinaryAggregateEchoesAsWrittenButQualified() {
        assertEquals(nested("SUM(AW.N102)", "SUM(SUM(AW.N102))"),
            answer("SELECT SUM(SUM(n102)) FROM aw"));
        assertEquals(nested("COUNT(*)", "MAX(COUNT(*))"),
            answer("SELECT MAX(COUNT(*)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "ARRAY_AGG(SUM(AW.N102))"),
            answer("SELECT ARRAY_AGG(SUM(n102)) FROM aw"));
    }

    /** The nesting need not be immediate — a scalar call in between changes nothing. */
    @Test
    public void anestingUnderAScalarCallIsStillNesting() {
        assertEquals(nested("SUM(AW.N102)", "SUM(ABS(SUM(AW.N102)))"),
            answer("SELECT SUM(ABS(SUM(n102))) FROM aw"));
    }

    /** The refusal is a COMPILE-time one, so grouping the query does not excuse it. */
    @Test
    public void groupingTheQueryDoesNotExcuseIt() {
        assertEquals(nested("SUM(AW.N102)", "PERCENTILE_CONT(CAST(SUM(AW.N102) AS NUMBER(25,5)), 0.5)"),
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY SUM(n102)) FROM aw GROUP BY k"));
    }

    /**
     * ★ WHICHEVER FAULT IS WRITTEN FIRST IS REPORTED. Frostlake ran the window check ahead of the
     * nesting one and so answered the window sentence for both spellings; live answers each according
     * to what comes first in the text.
     */
    @Test
    public void thefaultWrittenFirstIsTheOneReported() {
        assertTrue(answer("SELECT SUM(SUM(n102) + ROW_NUMBER() OVER (ORDER BY n102)) FROM aw")
                .startsWith("SQL compilation error: |Aggregate functions cannot be nested:"
                    + " [SUM(AW.N102)] nested in ["),
            "the aggregate is written first, so the nesting sentence wins");
        assertEquals("SQL compilation error:|Window function [ROW_NUMBER() OVER (ORDER BY AW.N102"
            + " ASC NULLS LAST)] may not appear inside an aggregate function.",
            answer("SELECT SUM(ROW_NUMBER() OVER (ORDER BY n102) + SUM(n102)) FROM aw"),
            "the window is written first, so the window sentence wins");
    }

    /** A window call inside an aggregate keeps its own sentence, and its own prefix with no space. */
    @Test
    public void thewindowSentenceIsUnchanged() {
        assertEquals("SQL compilation error:|Window function [ROW_NUMBER() OVER (ORDER BY AW.N102"
            + " ASC NULLS LAST)] may not appear inside an aggregate function.",
            answer("SELECT SUM(ROW_NUMBER() OVER (ORDER BY n102)) FROM aw"));
        assertEquals("SQL compilation error:|Window function [ROW_NUMBER() OVER (ORDER BY AW.N102"
            + " ASC NULLS LAST)] may not appear inside an aggregate function.",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY ROW_NUMBER()"
                + " OVER (ORDER BY n102)) FROM aw"));
    }

    /** ★ A WINDOWED OUTER CALL IS LEGAL — the window runs after the grouping, so nothing competes. */
    @Test
    public void awindowedOuterCallStaysLegal() {
        assertEquals("ACCEPTED: 3.00 8.00",
            answer("SELECT SUM(SUM(n102)) OVER (PARTITION BY k) FROM aw GROUP BY k ORDER BY k"));
        assertEquals("ACCEPTED: 3.00000 8.00000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY SUM(n102))"
                + " OVER (PARTITION BY k) FROM aw GROUP BY k ORDER BY k"));
    }

    /**
     * ★ AN ARGUMENT-TYPE COMPLAINT OUTRANKS THE NESTING ONE. The types are settled before the
     * arrangement is judged, so a nested aggregate whose TYPE is also wrong reports the type — with
     * each function's own sentence, which is not one wording but two.
     */
    @Test
    public void anargumentTypeComplaintOutranksTheNesting() {
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for"
            + " function 'SUM': (ARRAY)",
            answer("SELECT SUM(ARRAY_AGG(n102)) FROM aw"));
        assertEquals("SQL compilation error:|Function MAX does not support ARRAY argument type",
            answer("SELECT MAX(ARRAY_AGG(n102)) FROM aw"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for"
            + " function 'SUM': (ARRAY)",
            answer("SELECT SUM(ARRAY_CONSTRUCT(1, 2)) FROM aw"),
            "the same sentence with no nesting at all, so the nesting rule did not shape it");
    }

    /** The un-nested calls, which must not move. */
    @Test
    public void theunnestedCallsAreUntouched() {
        assertEquals("ACCEPTED: 2.00000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102) FROM aw"));
        assertEquals("ACCEPTED: 11.00", answer("SELECT SUM(n102) FROM aw"));
    }

    /**
     * ★ A plain AVG is echoed as its DEFINITION — a SUM cast to AVG's own width over a COUNT — and
     * when the AVG is itself the nested call, its SUM half is what the bracket names. The width is the
     * argument's: NUMBER(11,2) for {@code n102 * 2}, NUMBER(1,0) for a literal. An explicit cast around
     * it prints as CAST(… AS …).
     */
    @Test
    public void anAverageEchoesAsItsDefinition() {
        assertEquals(nested("SUM(AW.N102)", "SUM((CAST(SUM(AW.N102) AS NUMBER(28,8))) / (COUNT(AW.N102)))"),
            answer("SELECT SUM(AVG(n102)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "COUNT((CAST(SUM(AW.N102) AS NUMBER(28,8))) / (COUNT(AW.N102)))"),
            answer("SELECT COUNT(AVG(n102)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "MAX((CAST(SUM(AW.N102) AS NUMBER(28,8))) / (COUNT(AW.N102)))"),
            answer("SELECT MAX(AVG(n102)) FROM aw"));
        assertEquals(nested("SUM(A.N102)", "SUM((CAST(SUM(A.N102) AS NUMBER(28,8))) / (COUNT(A.N102)))"),
            answer("SELECT SUM(AVG(a.n102)) FROM aw a"));
        assertEquals(nested("SUM(AW.N102)", "SUM((CAST(SUM(AW.N102) AS NUMBER(28,8))) / (COUNT(AW.N102)))"),
            answer("SELECT SUM(AVG(n102)) FROM aw GROUP BY k"));
        assertEquals(nested("SUM(AW.N102)", "SUM((CAST(SUM(AW.N102) AS NUMBER(28,8))) / (COUNT(AW.N102)))"),
            answer("SELECT SUM(AVG(n102)) FROM aw WHERE FALSE"));
        assertEquals(nested("SUM(AW.N102 * 2)",
                "SUM((CAST(SUM(AW.N102 * 2) AS NUMBER(29,8))) / (COUNT(AW.N102 * 2)))"),
            answer("SELECT SUM(AVG(n102 * 2)) FROM aw"));
        assertEquals(nested("SUM(1)", "SUM((CAST(SUM(1) AS NUMBER(19,6))) / (COUNT(1)))"),
            answer("SELECT SUM(AVG(1)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)",
                "SUM(CAST((CAST(SUM(AW.N102) AS NUMBER(28,8))) / (COUNT(AW.N102)) AS NUMBER(5,1)))"),
            answer("SELECT SUM(AVG(n102)::NUMBER(5,1)) FROM aw"));
    }

    /**
     * ★ The width decides the form: the CAST while AVG's uncapped width fits in 38 digits or the scale
     * does not grow, live's internal SCALED_ROUND_INT_DIVIDE once a growing scale would overflow it —
     * a NUMBER(38,0) column, and SUM's own NUMBER(22,2) under an AVG.
     */
    @Test
    public void theAverageWidthDecidesTheForm() {
        assertEquals(nested("SUM(AW.N380)", "SUM(SCALED_ROUND_INT_DIVIDE(SUM(AW.N380), COUNT(AW.N380)))"),
            answer("SELECT SUM(AVG(n380)) FROM aw"));
        assertEquals(nested("SUM(SUM(AW.N102))",
                "SUM(SCALED_ROUND_INT_DIVIDE(SUM(SUM(AW.N102)), COUNT(SUM(AW.N102))))"),
            answer("SELECT SUM(AVG(SUM(n102))) FROM aw"));
    }

    /** ★ A FLOAT argument divides by the count cast to FLOAT; a text argument is cast inside the SUM. */
    @Test
    public void anApproximateOrTextAverageDividesAsFloat() {
        assertEquals(nested("SUM(AW.F)", "SUM((SUM(AW.F)) / (CAST(COUNT(AW.F) AS FLOAT)))"),
            answer("SELECT SUM(AVG(f)) FROM aw"));
        assertEquals(nested("SUM(CAST(AW.T AS FLOAT))",
                "SUM((SUM(CAST(AW.T AS FLOAT))) / (CAST(COUNT(AW.T) AS FLOAT)))"),
            answer("SELECT SUM(AVG(t)) FROM aw"));
    }

    /**
     * ★ An OUTER AVG is named by its SUM half with the inner call printed as it is; an AVG under it
     * expands, and the outermost pair is the one reported however deep the nesting goes.
     */
    @Test
    public void anOuterAverageIsNamedByItsSumHalf() {
        assertEquals(nested("SUM(AW.N102)", "SUM(SUM(AW.N102))"), answer("SELECT AVG(SUM(n102)) FROM aw"));
        assertEquals(nested("MAX(AW.N102)", "SUM(MAX(AW.N102))"), answer("SELECT AVG(MAX(n102)) FROM aw"));
        assertEquals(nested("COUNT(*)", "SUM(COUNT(*))"), answer("SELECT AVG(COUNT(*)) FROM aw"));
        assertEquals(nested("COUNT(AW.N102)", "SUM(COUNT(AW.N102))"), answer("SELECT AVG(COUNT(n102)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((CAST(SUM(AW.N102) AS NUMBER(28,8))) / (COUNT(AW.N102)))"),
            answer("SELECT AVG(AVG(n102)) FROM aw"));
        assertEquals(nested("SUM((CAST(SUM(AW.N102) AS NUMBER(28,8))) / (COUNT(AW.N102)))",
                "SUM(SUM((CAST(SUM(AW.N102) AS NUMBER(28,8))) / (COUNT(AW.N102))))"),
            answer("SELECT SUM(SUM(AVG(n102))) FROM aw"));
    }

    /** ★ A NESTED MEDIAN is reshaped too, and an AVG under MEDIAN is expanded inside the CAST. */
    @Test
    public void aNestedMedianIsReshapedToo() {
        assertEquals(nested("PERCENTILE_CONT(CAST(AW.N102 AS NUMBER(13,5)), 0.5)",
                "SUM(PERCENTILE_CONT(CAST(AW.N102 AS NUMBER(13,5)), 0.5))"),
            answer("SELECT SUM(MEDIAN(n102)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)",
                "PERCENTILE_CONT(CAST((CAST(SUM(AW.N102) AS NUMBER(28,8))) / (COUNT(AW.N102)) AS NUMBER(31,11)), 0.5)"),
            answer("SELECT MEDIAN(AVG(n102)) FROM aw"));
    }

    /** ★ HAVING is judged by the same rule, where it used to pass unjudged. */
    @Test
    public void havingIsJudgedToo() {
        assertEquals(nested("SUM(AW.N102)", "SUM((CAST(SUM(AW.N102) AS NUMBER(28,8))) / (COUNT(AW.N102)))"),
            answer("SELECT 1 FROM aw HAVING SUM(AVG(n102)) > 0"));
    }

    /**
     * ★ THE CAST A CALL APPLIES TO ITS OWN ARGUMENT appears where the FAMILY changes, never for a
     * width: LISTAGG casts a number, a float, a count, a boolean to VARCHAR(134217728) and leaves a
     * VARCHAR(10) alone; a DISTINCT repeats the word inside the cast; the WITHIN GROUP clause is
     * folded away.
     */
    @Test
    public void listaggCastsWhatIsNotText() {
        final String toText = " AS VARCHAR(134217728))";
        assertEquals(nested("SUM(AW.N102)", "LISTAGG(CAST(SUM(AW.N102)" + toText + ", ',')"),
            answer("SELECT LISTAGG(SUM(n102), ',') FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "LISTAGG(CAST(SUM(AW.N102)" + toText + ")"),
            answer("SELECT LISTAGG(SUM(n102)) FROM aw"));
        assertEquals(nested("SUM(AW.F)", "LISTAGG(CAST(SUM(AW.F)" + toText + ", ',')"),
            answer("SELECT LISTAGG(SUM(f), ',') FROM aw"));
        assertEquals(nested("COUNT(*)", "LISTAGG(CAST(COUNT(*)" + toText + ", ',')"),
            answer("SELECT LISTAGG(COUNT(*), ',') FROM aw"));
        assertEquals(nested("MAX(AW.B)", "LISTAGG(CAST(MAX(AW.B)" + toText + ", ',')"),
            answer("SELECT LISTAGG(MAX(b), ',') FROM aw"));
        assertEquals(nested("SUM(CAST(AW.T AS FLOAT))",
                "LISTAGG(CAST(SUM(CAST(AW.T AS FLOAT))" + toText + ", ',')"),
            answer("SELECT LISTAGG(SUM(t), ',') FROM aw"));
        assertEquals(nested("MIN(AW.T)", "LISTAGG(MIN(AW.T), ',')"),
            answer("SELECT LISTAGG(MIN(t), ',') FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "LISTAGG(DISTINCT CAST(DISTINCT SUM(AW.N102)" + toText + ", ',')"),
            answer("SELECT LISTAGG(DISTINCT SUM(n102), ',') FROM aw"));
        assertEquals(nested("MAX(AW.T)", "LISTAGG(MAX(AW.T), ',')"),
            answer("SELECT LISTAGG(MAX(t), ',') WITHIN GROUP (ORDER BY MAX(t)) FROM aw"));
    }

    /** ★ Text under a SUM is cast to FLOAT, in the nested bracket and in the container alike. */
    @Test
    public void textUnderASumIsCastToFloat() {
        assertEquals(nested("SUM(CAST(AW.T AS FLOAT))", "SUM(SUM(CAST(AW.T AS FLOAT)))"),
            answer("SELECT SUM(SUM(t)) FROM aw"));
        assertEquals(nested("MAX(AW.T)", "SUM(CAST(MAX(AW.T) AS FLOAT))"),
            answer("SELECT SUM(MAX(t)) FROM aw"));
        assertEquals(nested("MAX(AW.T)", "SUM(DISTINCT CAST(DISTINCT MAX(AW.T) AS FLOAT))"),
            answer("SELECT SUM(DISTINCT MAX(t)) FROM aw"));
        assertEquals(nested("COUNT(AW.T)", "SUM(COUNT(AW.T))"),
            answer("SELECT SUM(COUNT(t)) FROM aw"));
        assertEquals(nested("SUM(CAST(AW.T AS FLOAT))", "MAX(SUM(CAST(AW.T AS FLOAT)))"),
            answer("SELECT MAX(SUM(t)) FROM aw"));
        assertEquals(nested("MAX(AW.T)", "SUM(CAST(MAX(AW.T) AS FLOAT))"),
            answer("SELECT AVG(MAX(t)) FROM aw"));
    }

    /** ★ A percentile over text converts it to the whole number it computes on, TO_NUMBER(x, 9, 0). */
    @Test
    public void aPercentileOverTextConvertsToAWholeNumber() {
        assertEquals(nested("MAX(AW.T)", "PERCENTILE_CONT(CAST(TO_NUMBER(MAX(AW.T), 9, 0) AS NUMBER(12,3)), 0.5)"),
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY MAX(t)) FROM aw"));
        assertEquals(nested("MAX(AW.T)", "PERCENTILE_CONT(CAST(TO_NUMBER(MAX(AW.T), 9, 0) AS NUMBER(12,3)), 0.25)"),
            answer("SELECT PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY MAX(t) DESC) FROM aw"));
        assertEquals(nested("MAX(AW.T)", "PERCENTILE_DISC(TO_NUMBER(MAX(AW.T), 9, 0), 0.5)"),
            answer("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY MAX(t)) FROM aw"));
        assertEquals(nested("MAX(AW.T)", "PERCENTILE_CONT(CAST(TO_NUMBER(MAX(AW.T), 9, 0) AS NUMBER(12,3)), 0.5)"),
            answer("SELECT MEDIAN(MAX(t)) FROM aw"));
        assertEquals(nested("SUM(CAST(AW.T AS FLOAT))", "PERCENTILE_CONT(SUM(CAST(AW.T AS FLOAT)), 0.5)"),
            answer("SELECT MEDIAN(SUM(t)) FROM aw"));
        assertEquals(nested("MAX(AW.F)", "PERCENTILE_CONT(MAX(AW.F), 0.5)"),
            answer("SELECT MEDIAN(MAX(f)) FROM aw"));
    }

    /**
     * ★ The other families: OBJECT_AGG's value goes to VARIANT, a bit aggregate takes the integer
     * digits of its argument (a FLOAT as NUMBER(18,0), text through TO_NUMBER(x, 18, 0), a whole
     * number left alone; BITXOR keeps a DISTINCT where BITOR drops it), the boolean aggregates are the plan's MAX / MIN over a
     * boolean — a number compared with zero, text cast — COUNT_IF is a SUM of an IFF, and a
     * one-argument APPROX_COUNT_DISTINCT is its accumulator (the multi-argument form, which live
     * prints as written, is not accepted here yet).
     */
    @Test
    public void theOtherFamiliesCastOrExpandAsThePlanDoes() {
        assertEquals(nested("SUM(AW.N102)", "OBJECT_AGG(AW.T, CAST(SUM(AW.N102) AS VARIANT))"),
            answer("SELECT OBJECT_AGG(t, SUM(n102)) FROM aw"));
        assertEquals(nested("MAX(AW.B)", "OBJECT_AGG(AW.T, CAST(MAX(AW.B) AS VARIANT))"),
            answer("SELECT OBJECT_AGG(t, MAX(b)) FROM aw"));
        assertEquals(nested("MAX(AW.N102)", "BITOR_AGG(CAST(MAX(AW.N102) AS NUMBER(8,0)))"),
            answer("SELECT BITOR_AGG(MAX(n102)) FROM aw"));
        assertEquals(nested("MAX(AW.N102)", "BITAND_AGG(CAST(MAX(AW.N102) AS NUMBER(8,0)))"),
            answer("SELECT BITAND_AGG(MAX(n102)) FROM aw"));
        assertEquals(nested("MAX(AW.N102)", "BITXOR_AGG(DISTINCT CAST(DISTINCT MAX(AW.N102) AS NUMBER(8,0)))"),
            answer("SELECT BITXOR_AGG(DISTINCT MAX(n102)) FROM aw"));
        assertEquals(nested("MAX(AW.N102)", "BITOR_AGG(CAST(MAX(AW.N102) AS NUMBER(8,0)))"),
            answer("SELECT BITOR_AGG(DISTINCT MAX(n102)) FROM aw"));
        assertEquals(nested("MAX(AW.N380)", "BITOR_AGG(MAX(AW.N380))"),
            answer("SELECT BITOR_AGG(MAX(n380)) FROM aw"));
        assertEquals(nested("MAX(AW.F)", "BITOR_AGG(CAST(MAX(AW.F) AS NUMBER(18,0)))"),
            answer("SELECT BITOR_AGG(MAX(f)) FROM aw"));
        assertEquals(nested("MAX(AW.T)", "BITOR_AGG(TO_NUMBER(MAX(AW.T), 18, 0))"),
            answer("SELECT BITOR_AGG(MAX(t)) FROM aw"));
        assertEquals(nested("MAX(AW.B)", "MAX(MAX(AW.B))"), answer("SELECT BOOLOR_AGG(MAX(b)) FROM aw"));
        assertEquals(nested("MAX(AW.B)", "MIN(MAX(AW.B))"), answer("SELECT BOOLAND_AGG(MAX(b)) FROM aw"));
        assertEquals(nested("MAX(AW.N102)", "MAX(CAST((MAX(AW.N102)) <> (CAST(0 AS NUMBER(10,2))) AS BOOLEAN))"),
            answer("SELECT BOOLOR_AGG(MAX(n102)) FROM aw"));
        assertEquals(nested("MAX(AW.N102)", "MIN(CAST((MAX(AW.N102)) <> (CAST(0 AS NUMBER(10,2))) AS BOOLEAN))"),
            answer("SELECT BOOLAND_AGG(MAX(n102)) FROM aw"));
        assertEquals(nested("MAX(AW.N380)", "MAX(CAST((MAX(AW.N380)) <> 0 AS BOOLEAN))"),
            answer("SELECT BOOLOR_AGG(MAX(n380)) FROM aw"));
        assertEquals(nested("MAX(AW.T)", "MAX(CAST(MAX(AW.T) AS BOOLEAN))"),
            answer("SELECT BOOLOR_AGG(MAX(t)) FROM aw"));
        assertEquals(nested("MAX(AW.B)", "SUM(IFF(CAST(MAX(AW.B) AS BOOLEAN), 1, 0))"),
            answer("SELECT COUNT_IF(MAX(b)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "HLL_ACCUMULATE(SUM(AW.N102))"),
            answer("SELECT APPROX_COUNT_DISTINCT(SUM(n102)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "HLL_ACCUMULATE(SUM(AW.N102))"),
            answer("SELECT APPROX_COUNT_DISTINCT(DISTINCT SUM(n102)) FROM aw"));
    }

    /** ★ What takes NO cast: the collecting and picking aggregates print their argument as it is. */
    @Test
    public void theCollectingAggregatesTakeNoCast() {
        assertEquals(nested("SUM(CAST(AW.T AS FLOAT))", "ARRAY_AGG(SUM(CAST(AW.T AS FLOAT)))"),
            answer("SELECT ARRAY_AGG(SUM(t)) FROM aw"));
        assertEquals(nested("MIN(AW.T)", "ARRAY_AGG(MIN(AW.T))"), answer("SELECT ARRAY_AGG(MIN(t)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "ARRAY_AGG(DISTINCT SUM(AW.N102))"),
            answer("SELECT ARRAY_AGG(DISTINCT SUM(n102)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "ARRAY_AGG(SUM(AW.N102))"),
            answer("SELECT ARRAY_AGG(SUM(n102)) WITHIN GROUP (ORDER BY SUM(n102)) FROM aw"));
        assertEquals(nested("MIN(AW.T)", "HASH_AGG(MIN(AW.T))"), answer("SELECT HASH_AGG(MIN(t)) FROM aw"));
        assertEquals(nested("MAX(AW.T)", "HASH_AGG(MAX(AW.T), MAX(AW.N102))"),
            answer("SELECT HASH_AGG(MAX(t), MAX(n102)) FROM aw"));
        assertEquals(nested("SUM(CAST(AW.T AS FLOAT))", "ANY_VALUE(SUM(CAST(AW.T AS FLOAT)))"),
            answer("SELECT ANY_VALUE(SUM(t)) FROM aw"));
        assertEquals(nested("SUM(CAST(AW.T AS FLOAT))", "MODE(SUM(CAST(AW.T AS FLOAT)))"),
            answer("SELECT MODE(SUM(t)) FROM aw"));
        assertEquals(nested("SUM(CAST(AW.T AS FLOAT))", "MAX_BY(SUM(CAST(AW.T AS FLOAT)), SUM(AW.N102))"),
            answer("SELECT MAX_BY(SUM(t), SUM(n102)) FROM aw"));
    }

    /**
     * ★ ARITHMETIC OPERANDS: a call is bracketed, a column and a literal are bare, and the lower-scale
     * side of +, -, % and a comparison is cast to the higher scale at the wider precision — never for a
     * multiplication, never for a precision difference alone. Live-verified across the widths.
     */
    @Test
    public void arithmeticOperandsAreBracketedAndRescaled() {
        assertEquals(nested("SUM(AW.N102)", "SUM((SUM(AW.N102)) + (CAST(1 AS NUMBER(22,2))))"),
            answer("SELECT SUM(SUM(n102) + 1) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((SUM(AW.N102)) + (CAST(COUNT(AW.N102) AS NUMBER(22,2))))"),
            answer("SELECT SUM(SUM(n102) + COUNT(n102)) FROM aw"));
        assertEquals(nested("SUM(AW.I)", "SUM((SUM(AW.I)) + (MIN(AW.I)))"),
            answer("SELECT SUM(SUM(i) + MIN(i)) FROM aw"));
        assertEquals(nested("SUM(AW.N302)", "SUM((SUM(AW.N302)) + (SUM(AW.N52)))"),
            answer("SELECT SUM(SUM(n302) + SUM(n52)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((SUM(AW.N102)) + (CAST(SUM(AW.I) AS NUMBER(22,2))))"),
            answer("SELECT SUM(SUM(n102) + SUM(i)) FROM aw"));
        assertEquals(nested("SUM(AW.I)", "SUM((SUM(AW.I)) + 1)"), answer("SELECT SUM(SUM(i) + 1) FROM aw"));
        assertEquals(nested("SUM(AW.I)", "SUM((CAST(SUM(AW.I) AS NUMBER(18,1))) + 1.5)"),
            answer("SELECT SUM(SUM(i) + 1.5) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((SUM(AW.N102)) + (CAST(0.5 AS NUMBER(22,2))))"),
            answer("SELECT SUM(SUM(n102) + 0.5) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((CAST(SUM(AW.N102) AS NUMBER(32,10))) + (SUM(AW.N2010)))"),
            answer("SELECT SUM(SUM(n102) + SUM(n2010)) FROM aw"));
        assertEquals(nested("SUM(AW.I)", "SUM((CAST(SUM(AW.I) AS NUMBER(19,2))) + AW.N52)"),
            answer("SELECT SUM(SUM(i) + n52) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((SUM(AW.N102)) + (CAST(AW.I AS NUMBER(22,2))))"),
            answer("SELECT SUM(SUM(n102) + i) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((SUM(AW.N102)) + AW.N102)"),
            answer("SELECT SUM(SUM(n102) + n102) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((SUM(AW.N102)) * 2)"), answer("SELECT SUM(SUM(n102) * 2) FROM aw"));
        assertEquals(nested("SUM(AW.I)", "SUM((SUM(AW.I)) * (SUM(AW.N102)))"),
            answer("SELECT SUM(SUM(i) * SUM(n102)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((SUM(AW.N102)) - (CAST(1 AS NUMBER(22,2))))"),
            answer("SELECT SUM(SUM(n102) - 1) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((SUM(AW.N102)) % (CAST(2 AS NUMBER(22,2))))"),
            answer("SELECT SUM(SUM(n102) % 2) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((CAST(1 AS NUMBER(22,2))) + (SUM(AW.N102)))"),
            answer("SELECT SUM(1 + SUM(n102)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)",
                "SUM(((SUM(AW.N102)) + (CAST(1 AS NUMBER(22,2)))) + (CAST(2 AS NUMBER(23,2))))"),
            answer("SELECT SUM(SUM(n102) + 1 + 2) FROM aw"));
        assertEquals(nested("SUM(AW.I)", "SUM(((SUM(AW.I)) + 1) + 2)"), answer("SELECT SUM(SUM(i) + 1 + 2) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM(((SUM(AW.N102)) + (CAST(1 AS NUMBER(22,2)))) * 2)"),
            answer("SELECT SUM((SUM(n102) + 1) * 2) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((SUM(AW.N102)) + (CAST(1 * 2 AS NUMBER(22,2))))"),
            answer("SELECT SUM(SUM(n102) + (1 * 2)) FROM aw"));
        assertEquals(nested("SUM(AW.I)", "SUM((SUM(AW.I)) + (1 * 2))"), answer("SELECT SUM(SUM(i) + (1 * 2)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((ABS(SUM(AW.N102))) + (CAST(1 AS NUMBER(22,2))))"),
            answer("SELECT SUM(ABS(SUM(n102)) + 1) FROM aw"));
    }

    /** ★ A FLOAT beside an exact number casts the exact side to FLOAT, whatever the operator. */
    @Test
    public void aFloatBesideAnExactNumberCastsTheExactSide() {
        assertEquals(nested("SUM(AW.N102)", "SUM((CAST(SUM(AW.N102) AS FLOAT)) + (SUM(AW.F)))"),
            answer("SELECT SUM(SUM(n102) + SUM(f)) FROM aw"));
        assertEquals(nested("SUM(AW.F)", "SUM((SUM(AW.F)) + (CAST(1 AS FLOAT)))"),
            answer("SELECT SUM(SUM(f) + 1) FROM aw"));
        assertEquals(nested("SUM(AW.F)", "SUM((SUM(AW.F)) + (CAST(1.5 AS FLOAT)))"),
            answer("SELECT SUM(SUM(f) + 1.5) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((CAST(SUM(AW.N102) AS FLOAT)) + AW.F)"),
            answer("SELECT SUM(SUM(n102) + f) FROM aw"));
        assertEquals(nested("SUM(AW.F)", "SUM((SUM(AW.F)) + (CAST(AW.N102 AS FLOAT)))"),
            answer("SELECT SUM(SUM(f) + n102) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((CAST(SUM(AW.N102) AS FLOAT)) * (SUM(AW.F)))"),
            answer("SELECT SUM(SUM(n102) * SUM(f)) FROM aw"));
        assertEquals(nested("SUM(CAST(AW.T AS FLOAT))", "SUM((SUM(CAST(AW.T AS FLOAT))) + (CAST(1 AS FLOAT)))"),
            answer("SELECT SUM(SUM(t) + 1) FROM aw"));
    }

    /**
     * ★ A DIVISION rescales its dividend to the quotient's width — six more decimals to a cap of
     * twelve, the divisor's scale added, never narrowing — and past 38 digits becomes live's internal;
     * a FLOAT division takes the float rule instead.
     */
    @Test
    public void aDivisionRescalesItsDividend() {
        assertEquals(nested("SUM(AW.N102)", "SUM((CAST(SUM(AW.N102) AS NUMBER(28,8))) / 2)"),
            answer("SELECT SUM(SUM(n102) / 2) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((CAST(SUM(AW.N102) AS NUMBER(28,8))) / (SUM(AW.I)))"),
            answer("SELECT SUM(SUM(n102) / SUM(i)) FROM aw"));
        assertEquals(nested("SUM(AW.I)", "SUM((CAST(SUM(AW.I) AS NUMBER(23,6))) / (SUM(AW.I)))"),
            answer("SELECT SUM(SUM(i) / SUM(i)) FROM aw"));
        assertEquals(nested("SUM(AW.N52)", "SUM((CAST(SUM(AW.N52) AS NUMBER(23,8))) / 3)"),
            answer("SELECT SUM(SUM(n52) / 3) FROM aw"));
        assertEquals(nested("SUM(AW.N2010)", "SUM((CAST(SUM(AW.N2010) AS NUMBER(34,12))) / 2)"),
            answer("SELECT SUM(SUM(n2010) / 2) FROM aw"));
        assertEquals(nested("SUM(AW.N3812)", "SUM((CAST(SUM(AW.N3812) AS NUMBER(38,12))) / 2)"),
            answer("SELECT SUM(SUM(n3812) / 2) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((CAST(2 AS NUMBER(11,8))) / (SUM(AW.N102)))"),
            answer("SELECT SUM(2 / SUM(n102)) FROM aw"));
        assertEquals(nested("SUM(AW.N380)", "SUM(SCALED_ROUND_INT_DIVIDE(SUM(AW.N380), 2))"),
            answer("SELECT SUM(SUM(n380) / 2) FROM aw"));
        assertEquals(nested("SUM(AW.N302)", "SUM(SCALED_ROUND_INT_DIVIDE(SUM(AW.N302), 2))"),
            answer("SELECT SUM(SUM(n302) / 2) FROM aw"));
        assertEquals(nested("SUM(AW.F)", "SUM((SUM(AW.F)) / (CAST(2 AS FLOAT)))"),
            answer("SELECT SUM(SUM(f) / 2) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "SUM((CAST(SUM(AW.N102) AS FLOAT)) / (SUM(AW.F)))"),
            answer("SELECT SUM(SUM(n102) / SUM(f)) FROM aw"));
    }

    /**
     * ★ The rest of the operator family: NEGATE and NOT are calls with a bare operand (a doubled NOT
     * folded away), a comparison rescales like an addition and an extreme over one casts it to BOOLEAN,
     * IN and LIKE bracket their subject, BETWEEN is its two comparisons, IS NULL is bare, a
     * concatenation casts a number to text, and a window call is spelled canonically inside the sum.
     */
    @Test
    public void theOtherOperatorsPrintAsThePlanHoldsThem() {
        assertEquals(nested("SUM(AW.N102)", "SUM(NEGATE(SUM(AW.N102)))"), answer("SELECT SUM(-SUM(n102)) FROM aw"));
        assertEquals(nested("MAX(AW.B)", "MAX(NOT(MAX(AW.B)))"), answer("SELECT BOOLOR_AGG(NOT MAX(b)) FROM aw"));
        assertEquals(nested("MAX(AW.B)", "MAX((NOT(MAX(AW.B))) AND (MIN(AW.B)))"),
            answer("SELECT BOOLOR_AGG(NOT MAX(b) AND MIN(b)) FROM aw"));
        assertEquals(nested("MAX(AW.B)", "MAX(MAX(AW.B))"), answer("SELECT BOOLOR_AGG(NOT NOT MAX(b)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "MAX(CAST((SUM(AW.N102)) > (CAST(1 AS NUMBER(22,2))) AS BOOLEAN))"),
            answer("SELECT MAX(SUM(n102) > 1) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "MAX(CAST((SUM(AW.N102)) > (CAST(1 AS NUMBER(22,2))) AS BOOLEAN))"),
            answer("SELECT BOOLOR_AGG(SUM(n102) > 1) FROM aw"));
        assertEquals(nested("SUM(AW.I)", "MAX(CAST((SUM(AW.I)) = (MIN(AW.I)) AS BOOLEAN))"),
            answer("SELECT BOOLOR_AGG(SUM(i) = MIN(i)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "MAX(CAST(NOT((SUM(AW.N102)) > (CAST(1 AS NUMBER(22,2)))) AS BOOLEAN))"),
            answer("SELECT BOOLOR_AGG(NOT (SUM(n102) > 1)) FROM aw"));
        assertEquals(nested("SUM(AW.F)", "MAX(CAST((SUM(AW.F)) > (CAST(1 AS FLOAT)) AS BOOLEAN))"),
            answer("SELECT BOOLOR_AGG(SUM(f) > 1) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "MAX(IFF((SUM(AW.N102)) > (CAST(1 AS NUMBER(22,2))), 1, 0))"),
            answer("SELECT MAX(IFF(SUM(n102) > 1, 1, 0)) FROM aw"));
        assertEquals(nested("SUM(AW.I)", "MAX(IFF((SUM(AW.I)) > 1, 1, 0))"),
            answer("SELECT MAX(IFF(SUM(i) > 1, 1, 0)) FROM aw"));
        assertEquals(nested("SUM(AW.I)", "MAX(IFF((SUM(AW.I)) IN (1, 2), 1, 0))"),
            answer("SELECT MAX(IFF(SUM(i) IN (1, 2), 1, 0)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)",
                "MAX(IFF(((SUM(AW.N102)) >= (CAST(1 AS NUMBER(22,2)))) AND ((SUM(AW.N102)) <= (CAST(2 AS NUMBER(22,2)))), 1, 0))"),
            answer("SELECT MAX(IFF(SUM(n102) BETWEEN 1 AND 2, 1, 0)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)", "MAX(IFF(SUM(AW.N102) IS NULL, 1, 0))"),
            answer("SELECT MAX(IFF(SUM(n102) IS NULL, 1, 0)) FROM aw"));
        assertEquals(nested("MAX(AW.T)", "MAX(IFF((MAX(AW.T)) LIKE 'a%', 1, 0))"),
            answer("SELECT MAX(IFF(MAX(t) LIKE 'a%', 1, 0)) FROM aw"));
        assertEquals(nested("MAX(AW.T)", "MAX((MAX(AW.T)) || 'x')"), answer("SELECT MAX(MAX(t) || 'x') FROM aw"));
        assertEquals(nested("MAX(AW.T)", "MAX((MAX(AW.T)) || AW.T)"), answer("SELECT MAX(MAX(t) || t) FROM aw"));
        assertEquals(nested("SUM(AW.I)", "MAX((CAST(SUM(AW.I) AS VARCHAR(134217728))) || 'x')"),
            answer("SELECT MAX(SUM(i) || 'x') FROM aw"));
        assertEquals(nested("SUM(AW.N102)",
                "SUM((SUM(AW.N102)) + (CAST(ROW_NUMBER() OVER (ORDER BY AW.N102 ASC NULLS LAST) AS NUMBER(22,2))))"),
            answer("SELECT SUM(SUM(n102) + ROW_NUMBER() OVER (ORDER BY n102)) FROM aw"));
        assertEquals(nested("SUM(AW.I)", "SUM((SUM(AW.I)) + ROW_NUMBER() OVER (ORDER BY AW.I ASC NULLS LAST))"),
            answer("SELECT SUM(SUM(i) + ROW_NUMBER() OVER (ORDER BY i)) FROM aw"));
        assertEquals(nested("SUM(AW.N102)",
                "SUM((SUM(AW.N102)) + (CAST(ROW_NUMBER() OVER (PARTITION BY AW.I ORDER BY AW.N102 DESC NULLS FIRST) AS NUMBER(22,2))))"),
            answer("SELECT SUM(SUM(n102) + ROW_NUMBER() OVER (PARTITION BY i ORDER BY n102 DESC)) FROM aw"));
    }
}
