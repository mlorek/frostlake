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

package dev.frostlake.functions.vector;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Shared helpers for the VECTOR function tests: the vector literals every case is built from, and the
 * single-value / text accessors. Every expectation in this package is a value measured on a real
 * Snowflake account on and is quoted in the test that asserts it.
 */
public abstract class BaseVectorFunctionTest extends BaseDatabaseTest {

    /** {@code [1,2,3]} as a FLOAT vector — the spec's reference operand. */
    protected static final String V123 = "[1,2,3]::VECTOR(FLOAT,3)";

    /** {@code [4,5,6]} as a FLOAT vector. */
    protected static final String V456 = "[4,5,6]::VECTOR(FLOAT,3)";

    /** {@code [1,2,3]} as an INT vector — the same numbers under the other element type. */
    protected static final String I123 = "[1,2,3]::VECTOR(INT,3)";

    /** {@code [4,5,6]} as an INT vector. */
    protected static final String I456 = "[4,5,6]::VECTOR(INT,3)";

    /** The first value of the first row, or null when the query returned no rows. */
    protected Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().isEmpty() ? null : rs.getRows().get(0).getValue(0);
    }

    /** The first value rendered as Snowflake renders it — a vector's own display text. */
    protected String text(final String sql) {
        final Object value = scalar(sql);
        return value == null ? null : value.toString();
    }

    /** The first value as a double, for the FLOAT-returning distance functions. */
    protected double number(final String sql) {
        return ((Number) scalar(sql)).doubleValue();
    }

    /**
     * Assert a FLOAT (float64) result to the FULL bit pattern — which is the point of these tests, since
     * the vector functions' distinguishing property is that they compute in float64 over float32
     * elements.
     *
     * <p>Under {@code SF_LIVE} the comparison is made at 10 significant digits instead. That is a limit
     * of the live HARNESS, not a difference in the answer: its session runs with
     * {@code JDBC_QUERY_RESULT_FORMAT = 'JSON'} (the Arrow default needs extra JVM flags), and the JSON
     * format truncates EVERY double on the wire — live-verified, plain
     * {@code SELECT SQRT(27)::DOUBLE} arrives as {@code 5.196152423} under JSON and as the full
     * {@code 5.196152422706632} under Arrow, and so does {@code VECTOR_L2_DISTANCE([1,2,3],[4,5,6])}.
     * The account's value is the full one; the embedded engine is still held to it exactly.
     */
    protected void assertFloat64(final double expected, final String sql) {
        final double actual = number(sql);
        if (!isLiveSnowflake()) {
            assertEquals(expected, actual, sql);
            return;
        }
        assertEquals(toTenSignificantDigits(expected), toTenSignificantDigits(actual),
            sql + " (compared at the 10 significant digits the JSON result format transports)");
    }

    private static String toTenSignificantDigits(final double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }
        return new BigDecimal(value).round(new MathContext(10)).stripTrailingZeros().toPlainString();
    }

    /**
     * Assert a VECTOR result by its DISPLAY TEXT — {@code [0.26726124,0.5345225,0.80178374]} — which is
     * how the 32-bit element width shows up.
     *
     * <p>Under {@code SF_LIVE} the elements are compared as numbers rounded to 6 decimals, the same
     * harness limitation as {@link #assertFloat64}: the session's {@code JDBC_QUERY_RESULT_FORMAT =
     * 'JSON'} emits a vector in a FIXED six-decimal form ({@code [1.000000,2.000000,3.000000]},
     * {@code [0.267261,0.534522,0.801784]}), while the account's own rendering — measured directly under
     * the Arrow format — is {@code [1.0,2.0,3.0]} and {@code [0.26726124,0.5345225,0.80178374]}. The
     * exact text is still asserted against the embedded engine.
     */
    protected void assertVector(final String expected, final String sql) {
        assertVectorValue(expected, scalar(sql), sql);
    }

    /** Same comparison as {@link #assertVector}, for a vector read out of a multi-row result set. */
    protected void assertVectorValue(final String expected, final Object actual, final String context) {
        final String rendered = actual == null ? null : actual.toString();
        if (!isLiveSnowflake()) {
            assertEquals(expected, rendered, context);
            return;
        }
        assertEquals(toSixDecimalElements(expected), toSixDecimalElements(rendered),
            context + " (compared at the 6 decimals the JSON result format transports)");
    }

    /**
     * {@code [1.0,2.0]} → {@code [1.000000,2.000000]} — the fixed form the JSON result format emits.
     *
     * <p>Each element is read as a FLOAT and only then widened, because vector elements ARE float32:
     * the text {@code 0.5345225} denotes the float32 whose exact value is {@code 0.5345224738…}, which
     * the wire's six decimals render as {@code 0.534522} — reading it as a float64 {@code 0.5345225}
     * would instead round to {@code 0.534523} and report a difference that does not exist.
     */
    private static String toSixDecimalElements(final String vectorText) {
        if (vectorText == null || !vectorText.startsWith("[") || !vectorText.endsWith("]")) {
            return String.valueOf(vectorText);
        }
        final String inner = vectorText.substring(1, vectorText.length() - 1).trim();
        if (inner.isEmpty()) {
            return "[]";
        }
        final StringBuilder normalized = new StringBuilder("[");
        for (final String element : inner.split(",")) {
            if (normalized.length() > 1) {
                normalized.append(',');
            }
            normalized.append(String.format(Locale.ROOT, "%.6f", (double) Float.parseFloat(element.trim())));
        }
        return normalized.append(']').toString();
    }

    /**
     * Assert that {@code sql} is REJECTED. Snowflake decides the VECTOR functions' argument legality at
     * COMPILE time, so these are errors on the account rather than NULLs or wrong answers.
     */
    protected void assertRejected(final String sql) {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql + " is a compile error on Snowflake");
    }
}
