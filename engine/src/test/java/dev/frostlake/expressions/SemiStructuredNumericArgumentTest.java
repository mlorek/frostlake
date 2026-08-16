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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A statically OBJECT- or ARRAY-typed value is not a number, and Snowflake refuses it wherever one is
 * expected. Frostlake used to accept every one of these and FABRICATE an answer: {@code SUM(o)} came
 * back as {@code 0.0} — worse than an error, because it is a plausible number a caller may go on to
 * sum, average or compare — and the whole moment family came back as NULL.
 *
 * <p>Every expectation below was measured against a live Snowflake account on over a
 * populated OBJECT column, ARRAY column and structured OBJECT / ARRAY / MAP columns. The family does
 * not speak with one voice, which is why the three shapes are asserted separately rather than
 * parameterised: {@code SUM} names itself in an argument-type list (SQLSTATE 42P13), {@code MEDIAN}
 * uses a completely different sentence and SQLSTATE (42846), and the moment aggregates name the
 * internal multiplication {@code '*'} and list the offending argument TWICE.
 *
 * <p>The ACCEPTED forms matter at least as much as the rejections: arithmetic and the numeric
 * functions are shared machinery, so a rule even slightly too broad breaks ordinary queries. The
 * bound that matters most is pinned below — a VARIANT is never rejected, even when it HOLDS an
 * object, and casting an OBJECT to VARIANT moves the failure from compile time to run time on live
 * rather than removing it.
 */
public class SemiStructuredNumericArgumentTest extends BaseDatabaseTest {

    /** Live: "Invalid argument types for function 'SUM': (OBJECT)" (SQLSTATE 42P13, code 1044). */
    private static final String SUM_OBJECT =
        "Invalid argument types for function 'SUM': (OBJECT)";

    /** Live: "incompatible types: [OBJECT] and [NUMBER(9,0)]" (SQLSTATE 42846, code 1010). */
    private static final String MEDIAN_OBJECT =
        "incompatible types: [OBJECT] and [NUMBER(9,0)]";

    /** Live: "Invalid argument types for function '*': (OBJECT, OBJECT)" — the aggregate is unnamed. */
    private static final String MOMENT_OBJECT =
        "Invalid argument types for function '*': (OBJECT, OBJECT)";

    @BeforeEach
    public void createSemiStructuredTable() {
        engine.execute("CREATE TABLE snt (id INTEGER, o OBJECT, a ARRAY, v VARIANT, vo VARIANT,"
            + " s VARCHAR, n NUMBER, f DOUBLE, d DATE)");
        engine.execute("INSERT INTO snt SELECT 1, OBJECT_CONSTRUCT('k', 'v1'), ARRAY_CONSTRUCT(1, 2),"
            + " TO_VARIANT(1), TO_VARIANT(OBJECT_CONSTRUCT('x', 1)), 'xy', 10, 1.5, '2020-01-01'");
        engine.execute("INSERT INTO snt SELECT 2, OBJECT_CONSTRUCT('k', 'v2'), ARRAY_CONSTRUCT(3, 4),"
            + " TO_VARIANT(2), TO_VARIANT(OBJECT_CONSTRUCT('x', 2)), 'zw', 20, 2.5, '2020-02-01'");
        engine.execute("CREATE TABLE snst (id INTEGER, so OBJECT(x VARCHAR), sa ARRAY(INT),"
            + " sm MAP(VARCHAR, INT))");
        engine.execute("INSERT INTO snst SELECT 1, OBJECT_CONSTRUCT('x', 'a')::OBJECT(x VARCHAR),"
            + " ARRAY_CONSTRUCT(1, 2)::ARRAY(INT), OBJECT_CONSTRUCT('k', 1)::MAP(VARCHAR, INT)");
    }

    /** The exact message live Snowflake produces, asserted in full — not merely that it threw. */
    private void assertFails(final String sql, final String expectedMessage) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(error.getMessage().contains(expectedMessage),
            "expected [" + expectedMessage + "] for [" + sql + "] but got: " + error.getMessage());
    }

    private void assertAccepted(final String sql, final int expectedRows) {
        assertEquals(expectedRows, engine.executeQuery(sql).getRows().size(),
            "expected " + expectedRows + " row(s) from [" + sql + "]");
    }

    private void assertFirstValue(final String sql, final String expected) {
        assertEquals(expected, String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0)),
            "unexpected value from [" + sql + "]");
    }

    // ── Shape 1: the argument-type list ──────────────────────────────────────

    /**
     * The filed defect. {@code SUM(o)} answered {@code 0.0} — not a wrong number so much as an
     * invented one, indistinguishable from a real empty-ish sum.
     */
    @Test
    public void sumAndAvgRejectSemiStructured() {
        assertFails("SELECT SUM(o) FROM snt ORDER BY 1", SUM_OBJECT);
        assertFails("SELECT SUM(a) FROM snt ORDER BY 1",
            "Invalid argument types for function 'SUM': (ARRAY)");
        assertFails("SELECT AVG(o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'SUM': (OBJECT)");
        assertFails("SELECT AVG(a) FROM snt ORDER BY 1",
            "Invalid argument types for function 'SUM': (ARRAY)");
    }

    /** The DISTINCT and windowed forms reject identically on live. */
    @Test
    public void theDistinctAndWindowedFormsRejectToo() {
        assertFails("SELECT SUM(DISTINCT o) FROM snt ORDER BY 1", SUM_OBJECT);
        assertFails("SELECT AVG(DISTINCT o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'AVG': (OBJECT)");
        assertFails("SELECT SUM(o) OVER (PARTITION BY id) FROM snt ORDER BY 1", SUM_OBJECT);
        assertFails("SELECT AVG(o) OVER (PARTITION BY id) FROM snt ORDER BY 1",
            "Invalid argument types for function 'AVG': (OBJECT)");
        assertFails("SELECT STDDEV(o) OVER (PARTITION BY id) FROM snt ORDER BY 1", MOMENT_OBJECT);
    }

    /**
     * Live reports the BARE {@code AVG(o)} as 'SUM' because Snowflake desugars an average into a sum
     * over a count before it type-checks — while {@code AVG(DISTINCT o)} and {@code AVG(o) OVER (…)}
     * report 'AVG'. Both halves are copied exactly: the bare form
     * here, the DISTINCT and windowed forms in the test above.
     */
    @Test
    public void avgReportsTheSumItDesugarsInto() {
        assertFails("SELECT AVG(o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'SUM': (OBJECT)");
    }

    /** Live rejects on an EMPTY input too — the rule is a compile-time one. */
    @Test
    public void theAggregatesRejectOverAnEmptyInput() {
        assertFails("SELECT SUM(o) FROM snt WHERE 1 = 0", SUM_OBJECT);
        assertFails("SELECT MEDIAN(o) FROM snt WHERE 1 = 0", MEDIAN_OBJECT);
        assertFails("SELECT STDDEV(o) FROM snt WHERE 1 = 0", MOMENT_OBJECT);
        assertFails("SELECT o + 1 FROM snt WHERE 1 = 0",
            "Invalid argument types for function '+': (OBJECT, NUMBER(1,0))");
    }

    /** The bitwise aggregates read their input as an integer, and refuse the same way. */
    @Test
    public void theBitwiseAggregatesRejectSemiStructured() {
        assertFails("SELECT BITAND_AGG(o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'BITAND_AGG': (OBJECT)");
        assertFails("SELECT BITOR_AGG(o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'BITOR_AGG': (OBJECT)");
        assertFails("SELECT BITXOR_AGG(o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'BITXOR_AGG': (OBJECT)");
        assertFails("SELECT BITOR_AGG(a) FROM snt ORDER BY 1",
            "Invalid argument types for function 'BITOR_AGG': (ARRAY)");
    }

    // ── Shape 2: the incompatible-types pair ─────────────────────────────────

    /**
     * A completely different sentence, and a different SQLSTATE (42846 rather than 42P13). The
     * {@code NUMBER(9,0)} is a CONSTANT — {@code PERCENTILE_CONT(0.9)} and
     * {@code PERCENTILE_DISC(0.9999)} name exactly the same type live, so it is not derived from the
     * fraction.
     */
    @Test
    public void medianUsesTheIncompatibleTypesShape() {
        assertFails("SELECT MEDIAN(o) FROM snt ORDER BY 1", MEDIAN_OBJECT);
        assertFails("SELECT MEDIAN(a) FROM snt ORDER BY 1", "incompatible types: [ARRAY] and [NUMBER(9,0)]");
        assertFails("SELECT MEDIAN(o) FROM snt GROUP BY id", MEDIAN_OBJECT);
        assertFails("SELECT MEDIAN(o) OVER (PARTITION BY id) FROM snt ORDER BY 1", MEDIAN_OBJECT);
    }

    /** The percentiles take their value through WITHIN GROUP, and refuse it with the same sentence. */
    @Test
    public void thePercentileWithinGroupValueRejects() {
        assertFails("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY o) FROM snt", MEDIAN_OBJECT);
        assertFails("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY o) FROM snt", MEDIAN_OBJECT);
        assertFails("SELECT PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY o) FROM snt", MEDIAN_OBJECT);
        assertFails("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY a) FROM snt",
            "incompatible types: [ARRAY] and [NUMBER(9,0)]");
    }

    /**
     * One call, TWO message shapes: the FRACTION is an ordinary argument and refuses with the
     * argument-type list, while the value being ordered refuses with the incompatible-types pair.
     */
    @Test
    public void thePercentileFractionArgumentUsesTheOtherShape() {
        assertFails("SELECT PERCENTILE_CONT(o) WITHIN GROUP (ORDER BY n) FROM snt",
            "Invalid argument types for function 'PERCENTILE_CONT': (OBJECT)");
    }

    // ── Shape 3: the internal multiplication ─────────────────────────────────

    /**
     * The moment aggregates never name themselves live: they reach their sum of SQUARES before any
     * type check, so the message is about {@code '*'} and lists the one offending argument twice.
     */
    @Test
    public void theMomentAggregatesReportTheirInternalMultiplication() {
        assertFails("SELECT STDDEV(o) FROM snt ORDER BY 1", MOMENT_OBJECT);
        assertFails("SELECT STDDEV_POP(o) FROM snt ORDER BY 1", MOMENT_OBJECT);
        assertFails("SELECT STDDEV_SAMP(o) FROM snt ORDER BY 1", MOMENT_OBJECT);
        assertFails("SELECT VARIANCE(o) FROM snt ORDER BY 1", MOMENT_OBJECT);
        assertFails("SELECT VARIANCE_POP(o) FROM snt ORDER BY 1", MOMENT_OBJECT);
        assertFails("SELECT VARIANCE_SAMP(o) FROM snt ORDER BY 1", MOMENT_OBJECT);
        assertFails("SELECT VAR_POP(o) FROM snt ORDER BY 1", MOMENT_OBJECT);
        assertFails("SELECT VAR_SAMP(o) FROM snt ORDER BY 1", MOMENT_OBJECT);
        assertFails("SELECT SKEW(o) FROM snt ORDER BY 1", MOMENT_OBJECT);
        assertFails("SELECT KURTOSIS(o) FROM snt ORDER BY 1", MOMENT_OBJECT);
        assertFails("SELECT REGR_R2(o, n) FROM snt ORDER BY 1", MOMENT_OBJECT);
    }

    /** Over an ARRAY column both operands name ARRAY — the offending type, not the position's. */
    @Test
    public void theMomentAggregatesNameTheOffendingTypeOnBothSides() {
        assertFails("SELECT STDDEV(a) FROM snt ORDER BY 1",
            "Invalid argument types for function '*': (ARRAY, ARRAY)");
        assertFails("SELECT VARIANCE(a) FROM snt ORDER BY 1",
            "Invalid argument types for function '*': (ARRAY, ARRAY)");
        assertFails("SELECT REGR_R2(n, o) FROM snt ORDER BY 1", MOMENT_OBJECT);
    }

    // ── The numeric scalar family ────────────────────────────────────────────

    /** Each one live-verified over an OBJECT column; a semi-structured value coerces to no number. */
    @Test
    public void theNumericScalarsRejectSemiStructured() {
        assertFails("SELECT ABS(o) FROM snt ORDER BY 1", "Invalid argument types for function 'ABS': (OBJECT)");
        assertFails("SELECT CEIL(o) FROM snt ORDER BY 1", "Invalid argument types for function 'CEIL': (OBJECT)");
        assertFails("SELECT FLOOR(o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'FLOOR': (OBJECT)");
        assertFails("SELECT ROUND(o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'ROUND': (OBJECT)");
        assertFails("SELECT SQRT(o) FROM snt ORDER BY 1", "Invalid argument types for function 'SQRT': (OBJECT)");
        assertFails("SELECT EXP(o) FROM snt ORDER BY 1", "Invalid argument types for function 'EXP': (OBJECT)");
        assertFails("SELECT LN(o) FROM snt ORDER BY 1", "Invalid argument types for function 'LN': (OBJECT)");
        assertFails("SELECT SIGN(o) FROM snt ORDER BY 1", "Invalid argument types for function 'SIGN': (OBJECT)");
        assertFails("SELECT SQUARE(o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'SQUARE': (OBJECT)");
        assertFails("SELECT FACTORIAL(o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'FACTORIAL': (OBJECT)");
        assertFails("SELECT DEGREES(o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'DEGREES': (OBJECT)");
        assertFails("SELECT ZEROIFNULL(o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'ZEROIFNULL': (OBJECT)");
        assertFails("SELECT ABS(a) FROM snt ORDER BY 1", "Invalid argument types for function 'ABS': (ARRAY)");
    }

    /** The trigonometric and bitwise scalars behave the same way. */
    @Test
    public void theTrigonometricAndBitwiseScalarsRejectSemiStructured() {
        assertFails("SELECT ACOS(o) FROM snt ORDER BY 1", "Invalid argument types for function 'ACOS': (OBJECT)");
        assertFails("SELECT ASIN(a) FROM snt ORDER BY 1", "Invalid argument types for function 'ASIN': (ARRAY)");
        assertFails("SELECT COS(o) FROM snt ORDER BY 1", "Invalid argument types for function 'COS': (OBJECT)");
        assertFails("SELECT TANH(o) FROM snt ORDER BY 1", "Invalid argument types for function 'TANH': (OBJECT)");
        assertFails("SELECT BITNOT(o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'BITNOT': (OBJECT)");
        assertFails("SELECT BITAND(o, 1) FROM snt ORDER BY 1",
            "Invalid argument types for function 'BITAND': (OBJECT, NUMBER(1,0))");
        assertFails("SELECT BITSHIFTLEFT(o, 1) FROM snt ORDER BY 1",
            "Invalid argument types for function 'BITSHIFTLEFT': (OBJECT, NUMBER(1,0))");
        assertFails("SELECT GETBIT(o, 1) FROM snt ORDER BY 1",
            "Invalid argument types for function 'GETBIT': (OBJECT, NUMBER(1,0))");
    }

    /**
     * Every argument position refuses one, not merely the first — measured live, since a
     * semi-structured value is no more a NUMBER in one position than another.
     */
    @Test
    public void everyArgumentPositionRefusesSemiStructured() {
        assertFails("SELECT ROUND(n, o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'ROUND': (NUMBER(38,0), OBJECT)");
        assertFails("SELECT ATAN2(1, o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'ATAN2': (NUMBER(1,0), OBJECT)");
        assertFails("SELECT LOG(2, o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'LOG': (NUMBER(1,0), OBJECT)");
        assertFails("SELECT POWER(2, o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'POWER': (NUMBER(1,0), OBJECT)");
        assertFails("SELECT MOD(2, o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'MOD': (NUMBER(1,0), OBJECT)");
        assertFails("SELECT DIV0(2, o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'DIV0': (NUMBER(1,0), OBJECT)");
        assertFails("SELECT GETBIT(n, o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'GETBIT': (NUMBER(38,0), OBJECT)");
        assertFails("SELECT WIDTH_BUCKET(1, o, 10, 3) FROM snt ORDER BY 1",
            "Invalid argument types for function 'WIDTH_BUCKET':"
                + " (NUMBER(1,0), OBJECT, NUMBER(2,0), NUMBER(1,0))");
        assertFails("SELECT HAVERSINE(1, 2, 3, o) FROM snt ORDER BY 1",
            "Invalid argument types for function 'HAVERSINE':"
                + " (NUMBER(1,0), NUMBER(1,0), NUMBER(1,0), OBJECT)");
    }

    // ── The arithmetic operators ─────────────────────────────────────────────

    /**
     * The family's obvious neighbours, and in: live names the operator itself, so an arithmetic
     * rejection reads exactly like a function one. Frostlake used to reach the evaluator and fail
     * there with "Cannot add: {"k":"v1"} + 1", a message no Snowflake caller can match.
     */
    @Test
    public void theArithmeticOperatorsRejectSemiStructured() {
        assertFails("SELECT o + 1 FROM snt ORDER BY 1",
            "Invalid argument types for function '+': (OBJECT, NUMBER(1,0))");
        assertFails("SELECT 1 + o FROM snt ORDER BY 1",
            "Invalid argument types for function '+': (NUMBER(1,0), OBJECT)");
        assertFails("SELECT o - 1 FROM snt ORDER BY 1",
            "Invalid argument types for function '-': (OBJECT, NUMBER(1,0))");
        assertFails("SELECT o * 2 FROM snt ORDER BY 1",
            "Invalid argument types for function '*': (OBJECT, NUMBER(1,0))");
        assertFails("SELECT o / 2 FROM snt ORDER BY 1",
            "Invalid argument types for function '/': (OBJECT, NUMBER(1,0))");
        assertFails("SELECT o % 2 FROM snt ORDER BY 1",
            "Invalid argument types for function '%': (OBJECT, NUMBER(1,0))");
        assertFails("SELECT o + o FROM snt ORDER BY 1",
            "Invalid argument types for function '+': (OBJECT, OBJECT)");
        assertFails("SELECT a + 1 FROM snt ORDER BY 1",
            "Invalid argument types for function '+': (ARRAY, NUMBER(1,0))");
        assertFails("SELECT o + n FROM snt ORDER BY 1",
            "Invalid argument types for function '+': (OBJECT, NUMBER(38,0))");
        assertFails("SELECT d + o FROM snt ORDER BY 1",
            "Invalid argument types for function '+': (DATE, OBJECT)");
        assertFails("SELECT s + o FROM snt ORDER BY 1",
            "Invalid argument types for function '+': (VARCHAR(16777216), OBJECT)");
    }

    /** Unary minus reports itself as 'NEGATE' live, not as '-'. */
    @Test
    public void unaryMinusReportsItselfAsNegate() {
        assertFails("SELECT -o FROM snt ORDER BY 1", "Invalid argument types for function 'NEGATE': (OBJECT)");
        assertFails("SELECT -a FROM snt ORDER BY 1", "Invalid argument types for function 'NEGATE': (ARRAY)");
    }

    /** Nested inside a larger expression, live still names the operator. */
    @Test
    public void theOperatorRuleFiresInsideALargerExpression() {
        assertFails("SELECT SUM(o + 1) FROM snt ORDER BY 1",
            "Invalid argument types for function '+': (OBJECT, NUMBER(1,0))");
        assertFails("SELECT ABS(o * 2) FROM snt ORDER BY 1",
            "Invalid argument types for function '*': (OBJECT, NUMBER(1,0))");
    }

    // ── Structured types name their whole type ───────────────────────────────

    /**
     * A structured column names its whole parameterised type in all three shapes, MAP included —
     * "OBJECT(x VARCHAR(16777216))", "ARRAY(NUMBER(38,0))", "MAP(VARCHAR(16777216), NUMBER(38,0))".
     */
    @Test
    public void structuredTypesNameTheirWholeTypeInEveryShape() {
        assertFails("SELECT SUM(so) FROM snst",
            "Invalid argument types for function 'SUM': (OBJECT(x VARCHAR(16777216)))");
        assertFails("SELECT SUM(sa) FROM snst",
            "Invalid argument types for function 'SUM': (ARRAY(NUMBER(38,0)))");
        assertFails("SELECT SUM(sm) FROM snst",
            "Invalid argument types for function 'SUM': (MAP(VARCHAR(16777216), NUMBER(38,0)))");
        assertFails("SELECT MEDIAN(so) FROM snst",
            "incompatible types: [OBJECT(x VARCHAR(16777216))] and [NUMBER(9,0)]");
        assertFails("SELECT MEDIAN(sm) FROM snst",
            "incompatible types: [MAP(VARCHAR(16777216), NUMBER(38,0))] and [NUMBER(9,0)]");
        assertFails("SELECT STDDEV(so) FROM snst",
            "Invalid argument types for function '*': (OBJECT(x VARCHAR(16777216)),"
                + " OBJECT(x VARCHAR(16777216)))");
        assertFails("SELECT VARIANCE(sm) FROM snst",
            "Invalid argument types for function '*': (MAP(VARCHAR(16777216), NUMBER(38,0)),"
                + " MAP(VARCHAR(16777216), NUMBER(38,0)))");
        assertFails("SELECT so + 1 FROM snst",
            "Invalid argument types for function '+': (OBJECT(x VARCHAR(16777216)), NUMBER(1,0))");
        assertFails("SELECT -sm FROM snst",
            "Invalid argument types for function 'NEGATE': (MAP(VARCHAR(16777216), NUMBER(38,0)))");
        assertFails("SELECT ABS(so) FROM snst",
            "Invalid argument types for function 'ABS': (OBJECT(x VARCHAR(16777216)))");
    }

    // ── The bound: a VARIANT is never rejected ───────────────────────────────

    /**
     * The rule is keyed on the DECLARED type, so a VARIANT passes even when it HOLDS an object. Live
     * accepts the compile of every one of these; {@code SUM(v)} returned 3 and {@code v + 1} returned
     * 2 and 3.
     *
     * <p>The VARIANT-holding-an-object cases are the ones this test exists to protect: live fails
     * them at RUN time ("Failed to cast variant value {"x":1} to REAL"), which is a DIFFERENT outcome
     * from a compile rejection, and turning them into one would be over-broad. Asserting on the
     * message directly is what pins that: none of them may produce an argument-type error.
     */
    @Test
    public void aVariantIsNeverRejectedAtCompileTimeEvenHoldingAnObject() {
        assertAccepted("SELECT SUM(v) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT AVG(v) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT MEDIAN(v) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT STDDEV(v) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT BITOR_AGG(v) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT v + 1 FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT ABS(v) FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT ROUND(v, 1) FROM snt ORDER BY 1", 2);
        assertNotAnArgumentTypeError("SELECT SUM(vo) FROM snt ORDER BY 1");
        assertNotAnArgumentTypeError("SELECT AVG(vo) FROM snt ORDER BY 1");
        assertNotAnArgumentTypeError("SELECT MEDIAN(vo) FROM snt ORDER BY 1");
        assertNotAnArgumentTypeError("SELECT STDDEV(vo) FROM snt ORDER BY 1");
        assertNotAnArgumentTypeError("SELECT vo + 1 FROM snt ORDER BY 1");
        assertNotAnArgumentTypeError("SELECT ABS(vo) FROM snt ORDER BY 1");
    }

    /**
     * Casting an OBJECT to VARIANT moves the failure to run time on live rather than removing it, so
     * the only thing Frostlake must guarantee is that the COMPILE-time rule does not fire.
     */
    @Test
    public void castingToVariantLiftsTheCompileTimeRejection() {
        assertNotAnArgumentTypeError("SELECT SUM(o::VARIANT) FROM snt ORDER BY 1");
        assertNotAnArgumentTypeError("SELECT SUM(TO_VARIANT(o)) FROM snt ORDER BY 1");
        assertNotAnArgumentTypeError("SELECT ABS(o::VARIANT) FROM snt ORDER BY 1");
    }

    /** And the same value cast the other way — to OBJECT — IS refused, in all three shapes. */
    @Test
    public void castingAVariantToObjectBringsTheRejectionBack() {
        assertFails("SELECT SUM(v::OBJECT) FROM snt ORDER BY 1", SUM_OBJECT);
        assertFails("SELECT MEDIAN(v::OBJECT) FROM snt ORDER BY 1", MEDIAN_OBJECT);
        assertFails("SELECT STDDEV(v::OBJECT) FROM snt ORDER BY 1", MOMENT_OBJECT);
        assertFails("SELECT v::OBJECT + 1 FROM snt ORDER BY 1",
            "Invalid argument types for function '+': (OBJECT, NUMBER(1,0))");
    }

    /** A path or index read yields VARIANT, so it sums and rounds fine — live-verified. */
    @Test
    public void readingIntoTheValueYieldsAVariantThatIsAccepted() {
        assertAccepted("SELECT SUM(a[0]) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT ABS(a[0]) FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT a[0] + 1 FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT MEDIAN(a[0]) FROM snt ORDER BY 1", 1);
    }

    // ── The bound: the rest of the surface is untouched ──────────────────────

    /** Live accepts every one of these over an OBJECT column — they are not numeric positions. */
    @Test
    public void theAcceptedAggregatesAreUntouched() {
        assertAccepted("SELECT ANY_VALUE(o) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT OBJECT_AGG(s, o) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT HASH_AGG(o) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT MAX_BY(o, n) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT MIN_BY(o, n) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT APPROX_COUNT_DISTINCT(o) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT ARRAY_AGG(o) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT ARRAY_UNIQUE_AGG(o) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT COUNT(o) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT COUNT(DISTINCT o) FROM snt ORDER BY 1", 1);
    }

    /** Grouping, sorting, partitioning, DISTINCT, comparison and set operations all still take one. */
    @Test
    public void keyPositionsStillAcceptSemiStructured() {
        assertAccepted("SELECT COUNT(*) FROM snt GROUP BY o", 2);
        assertAccepted("SELECT SUM(n) FROM snt GROUP BY o", 2);
        assertAccepted("SELECT id FROM snt ORDER BY o", 2);
        assertAccepted("SELECT SUM(n) OVER (PARTITION BY o) FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT DISTINCT o FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT id FROM snt WHERE o = o", 2);
        assertAccepted("SELECT o FROM snt UNION SELECT o FROM snt ORDER BY 1", 2);
    }

    /** Ordinary arithmetic is untouched — NUMBER, DOUBLE, DATE and an untyped NULL all still add. */
    @Test
    public void ordinaryArithmeticStillWorks() {
        assertAccepted("SELECT n + 1, n - 1, n * 2, n / 2, n % 3, -n FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT f + 1 FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT d + 1 FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT NULL + 1 FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT v + v FROM snt ORDER BY 1", 2);
    }

    /** And the ordinary numeric functions and aggregates, over every type live coerces. */
    @Test
    public void theOrdinaryNumericSurfaceStillWorks() {
        assertAccepted("SELECT ABS(n), CEIL(f), SQRT(n), POWER(n, 2), MOD(n, 3) FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT ROUND(f, 1) FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT ABS(NULL) FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT SUM(n), AVG(n), MEDIAN(n), STDDEV(n), VARIANCE(n) FROM snt ORDER BY 1", 1);
        assertAccepted("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n) FROM snt", 1);
        assertAccepted("SELECT BITOR_AGG(n) FROM snt ORDER BY 1", 1);
    }

    /**
     * The trap the rule must not fall into: an EXPLICIT conversion is legal where implicit coercion is
     * not. Live accepts all of these over a plain OBJECT, and the converted value flows on.
     */
    @Test
    public void explicitConversionsStayAccepted() {
        assertFirstValue("SELECT CAST(o AS VARCHAR) FROM snt ORDER BY 1", "{\"k\":\"v1\"}");
        assertFirstValue("SELECT TO_VARCHAR(o) FROM snt ORDER BY 1", "{\"k\":\"v1\"}");
        assertFirstValue("SELECT TO_CHAR(o) FROM snt ORDER BY 1", "{\"k\":\"v1\"}");
        assertAccepted("SELECT LENGTH(TO_VARCHAR(o)) + 1 FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT SUM(ARRAY_SIZE(a)) FROM snt ORDER BY 1", 1);
    }

    /** The semi-structured surface proper is untouched. */
    @Test
    public void theSemiStructuredAwareFunctionsAreUntouched() {
        assertAccepted("SELECT ARRAY_SIZE(a) FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT OBJECT_KEYS(o) FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT TYPEOF(o) FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT COALESCE(o, o) FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT GREATEST(o, o) FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT HASH(o) FROM snt ORDER BY 1", 2);
        assertAccepted("SELECT OBJECT_INSERT(o, 'j', 2) FROM snt ORDER BY 1", 2);
    }

    // ── The bound: the declared type is read through wrappers ────────────────

    /**
     * A conditional over OBJECT branches IS an OBJECT, and so is a derived or CTE column — live
     * rejects {@code SUM(IFF(TRUE, o, o))} and {@code SUM(x)} over {@code (SELECT o AS x FROM t)}
     * with the same message as the bare column.
     */
    @Test
    public void theDeclaredTypeIsReadThroughConditionalsAndDerivedColumns() {
        assertFails("SELECT SUM(IFF(TRUE, o, o)) FROM snt ORDER BY 1", SUM_OBJECT);
        assertFails("SELECT SUM(COALESCE(o, o)) FROM snt ORDER BY 1", SUM_OBJECT);
        assertFails("SELECT SUM(CASE WHEN TRUE THEN o ELSE o END) FROM snt ORDER BY 1", SUM_OBJECT);
        assertFails("SELECT SUM(x) FROM (SELECT o AS x FROM snt)", SUM_OBJECT);
        assertFails("WITH c AS (SELECT o AS x FROM snt) SELECT SUM(x) FROM c", SUM_OBJECT);
        assertFails("SELECT STDDEV(IFF(TRUE, o, o)) FROM snt ORDER BY 1", MOMENT_OBJECT);
        assertFails("SELECT MEDIAN(IFF(TRUE, o, o)) FROM snt ORDER BY 1", MEDIAN_OBJECT);
    }

    /** The producing functions declare their own type, so live rejects those results too. */
    @Test
    public void theSemiStructuredProducersAreRejectedAsArguments() {
        assertFails("SELECT SUM(OBJECT_CONSTRUCT('k', 1)) FROM snt ORDER BY 1", SUM_OBJECT);
        assertFails("SELECT SUM(ARRAY_CONSTRUCT(1)) FROM snt ORDER BY 1",
            "Invalid argument types for function 'SUM': (ARRAY)");
        assertFails("SELECT SUM(ARRAY_AGG(n)) FROM snt ORDER BY 1",
            "Invalid argument types for function 'SUM': (ARRAY)");
        assertFails("SELECT ABS(OBJECT_KEYS(o)) FROM snt ORDER BY 1",
            "Invalid argument types for function 'ABS': (ARRAY)");
    }

    /**
     * Whatever a statement does with a VARIANT, it must not be the COMPILE-time argument-type
     * rejection — live keeps those failures at run time, and the two are not interchangeable.
     */
    private void assertNotAnArgumentTypeError(final String sql) {
        String message = null;
        try {
            engine.executeQuery(sql);
        } catch (final RuntimeException e) {
            message = String.valueOf(e.getMessage());
        }
        if (message == null) {
            return;
        }
        assertFalse(message.contains("Invalid argument types") || message.contains("incompatible types:"),
            "[" + sql + "] must not be rejected at compile time, but got: " + message);
    }
}
