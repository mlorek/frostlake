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

/**
 * RATIO_TO_REPORT typed from its ARGUMENT rather than fixed at NUMBER(38,6).
 *
 * <p>★ THE SCALE GROWS BY SIX AND STOPS AT TWELVE, but never narrows: an input already scaled past
 * twelve keeps what it has. The precision then adds that whole new scale to the input's own digits and
 * stops at thirty-eight. Every width here is live-verified.
 *
 * <p>★ IT READS ANY ARGUMENT'S TYPE, not only a column's — an expression, a literal and an aggregate
 * each get the rule applied to their own declared width — and an input that is not an exact number
 * divides as a double, so a FLOAT, a VARCHAR and a VARIANT all answer FLOAT.
 *
 * <p>★ THE VALUE FOLLOWS THE TYPE, which is the point of fixing it: the same call that declared six
 * decimals and rounded to six now declares eight and keeps eight.
 *
 * <p>NOT COVERED HERE: a DATE argument and a second argument are both refused live, in SUM's own
 * vocabulary rather than RATIO_TO_REPORT's — that surface is pinned in the refusal test beside this.
 */
public class RatioToReportDeclaredWidthTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("""
            CREATE OR REPLACE TABLE ratio_w (
                a NUMBER(10,2), b NUMBER(10,0), c NUMBER(20,10), e NUMBER(33,5),
                f FLOAT, t VARCHAR(20))""");
        engine.execute("""
            INSERT INTO ratio_w VALUES (1.00, 1, 1, 1, 1.0, '1'), (2.00, 2, 2, 2, 2.0, '2'),
                (3.00, 3, 3, 3, 3.0, '3'), (5.00, 5, 5, 5, 5.0, '5')""");
        // Its one integer digit cannot hold the four-row total, so the passthrough case gets a table
        // of its own whose sum still fits.
        engine.execute("CREATE OR REPLACE TABLE ratio_tiny (d NUMBER(38,37))");
        engine.execute("INSERT INTO ratio_tiny VALUES (1.5), (2.5)");
    }

    private String one(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** One call's declared type, without the storage-byte tag SYSTEM$TYPEOF appends. */
    private String declared(final String sql) {
        final String typed = one(sql);
        final int tag = typed.indexOf('[');
        return tag < 0 ? typed : typed.substring(0, tag);
    }

    private String ratioType(final String argument) {
        return declared("SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(" + argument
            + ") OVER ()) FROM ratio_w");
    }

    @Test
    void theScaleGrowsBySixOverAnExactInput() {
        assertEquals("NUMBER(16,6)", ratioType("b"));
        assertEquals("NUMBER(18,8)", ratioType("a"));
    }

    @Test
    void theScaleStopsAtTwelve() {
        assertEquals("NUMBER(32,12)", ratioType("c"));
    }

    @Test
    void anInputAlreadyPastTwelveKeepsItsOwnScale() {
        assertEquals("NUMBER(38,37)", declared(
            "SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(d) OVER ()) FROM ratio_tiny"));
    }

    @Test
    void thePrecisionStopsAtThirtyEight() {
        assertEquals("NUMBER(38,11)", ratioType("e"));
    }

    @Test
    void anInexactInputAnswersFloat() {
        assertEquals("FLOAT", ratioType("f"));
        assertEquals("FLOAT", ratioType("t"));
    }

    @Test
    void anUntypedNullCountsAsTheEighteenDigitInteger() {
        assertEquals("NUMBER(24,6)", ratioType("NULL"));
    }

    @Test
    void theRuleReadsWhateverTypeTheArgumentCarries() {
        assertEquals("NUMBER(7,6)", ratioType("2"));
        assertEquals("NUMBER(21,8)", ratioType("a * 100"));
        assertEquals("NUMBER(30,8)", declared(
            "SELECT SYSTEM$TYPEOF(RATIO_TO_REPORT(CAST(SUM(a) AS NUMBER(22,2))) OVER ())"
                + " FROM ratio_w GROUP BY a"));
    }

    @Test
    void theValueIsPresentedAtTheDeclaredScale() {
        assertEquals("0.09090909",
            one("SELECT RATIO_TO_REPORT(a) OVER () FROM ratio_w ORDER BY 1 LIMIT 1"));
        assertEquals("0.090909",
            one("SELECT RATIO_TO_REPORT(b) OVER () FROM ratio_w ORDER BY 1 LIMIT 1"));
        assertEquals("0.090909090909",
            one("SELECT RATIO_TO_REPORT(c) OVER () FROM ratio_w ORDER BY 1 LIMIT 1"));
        assertEquals("0.09090909091",
            one("SELECT RATIO_TO_REPORT(e) OVER () FROM ratio_w ORDER BY 1 LIMIT 1"));
        assertEquals("0.3750000000000000000000000000000000000",
            one("SELECT RATIO_TO_REPORT(d) OVER () FROM ratio_tiny ORDER BY 1 LIMIT 1"));
    }

    @Test
    void aStoredColumnDeclaresTheSameWidth() {
        engine.execute(
            "CREATE OR REPLACE TABLE ratio_s AS SELECT RATIO_TO_REPORT(a) OVER () AS r FROM ratio_w");
        assertEquals("NUMBER(18,8)", declared("SELECT SYSTEM$TYPEOF(r) FROM ratio_s"));
        assertEquals("0.09090909", one("SELECT r FROM ratio_s ORDER BY r LIMIT 1"));
    }
}
