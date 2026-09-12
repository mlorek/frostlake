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
 * What tier a numeric aggregate lands on when its input is TEXT. A VARCHAR carries no declared scale
 * for an exact result to be derived from, so live puts the whole computing family on FLOAT — SUM, AVG,
 * STDDEV, VARIANCE and the two POP spellings alike — and the answer is presented at the FLOAT width
 * rather than at a scale.
 *
 * <p>★ WHAT THE TEXT HOLDS DOES NOT MATTER. Integers, decimals and exponents all give FLOAT; the tier
 * is a property of the ARGUMENT'S TYPE, not of the values it happens to carry.
 *
 * <p>★ A VARIANT ARGUMENT IS THE SAME TIER, for the same reason.
 *
 * <p>★ MIN AND MAX ARE NOT IN THIS. They hand back an input, so they keep the VARCHAR they were given
 * — and compare it as text, which is why the largest of '1e3' and '2e3' is '2e3'.
 *
 * <p>NOT COVERED HERE: MEDIAN and the percentiles over a VARCHAR, which convert each value to a whole
 * number first and stay exact on a width of their own; and text that will not convert at all, which is
 * a row-time refusal with its own tests.
 */
public class NumericTextAggregateTierTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE ints (t VARCHAR(10))");
        engine.execute("INSERT INTO ints VALUES ('1'), ('2'), ('8')");
        engine.execute("CREATE OR REPLACE TABLE decs (t VARCHAR(10))");
        engine.execute("INSERT INTO decs VALUES ('1.5'), ('2.25')");
        engine.execute("CREATE OR REPLACE TABLE expo (t VARCHAR(10))");
        engine.execute("INSERT INTO expo VALUES ('1e3'), ('2e3')");
    }

    /** The first column of the first row, as text. */
    private String value(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** One expression's DECLARED type, read back through CTAS and DESCRIBE. */
    private String declared(final String expr, final String from) {
        engine.execute("CREATE OR REPLACE TABLE tier_ct AS SELECT " + expr + " AS c FROM " + from);
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE tier_ct");
        rs.next();
        return String.valueOf(rs.getValue(1));
    }

    @Test
    void theComputingAggregatesDeclareFloatOverAVarchar() {
        assertEquals("FLOAT", declared("SUM(t)", "ints"));
        assertEquals("FLOAT", declared("AVG(t)", "ints"));
        assertEquals("FLOAT", declared("STDDEV(t)", "ints"));
        assertEquals("FLOAT", declared("VARIANCE(t)", "ints"));
        // Decimals in the text change nothing — the tier is the argument's, not the values'.
        assertEquals("FLOAT", declared("SUM(t)", "decs"));
    }

    @Test
    void aVariantArgumentIsTheSameTier() {
        engine.execute("CREATE OR REPLACE TABLE vtxt (v VARIANT)");
        engine.execute("INSERT INTO vtxt SELECT TO_VARIANT('1') UNION ALL SELECT TO_VARIANT('2')"
            + " UNION ALL SELECT TO_VARIANT('8')");
        assertEquals("FLOAT", declared("SUM(v)", "vtxt"));
        assertEquals("11.0", value("SELECT SUM(v) FROM vtxt"));
        assertEquals("3.666666667", value("SELECT TO_VARCHAR(AVG(v)) FROM vtxt"));
    }

    @Test
    void aStringLiteralArgumentIsTheSameTier() {
        engine.execute("CREATE OR REPLACE TABLE onerow (n NUMBER(3,0))");
        engine.execute("INSERT INTO onerow VALUES (1)");
        assertEquals("FLOAT", declared("SUM('2')", "onerow"));
        assertEquals("2.0", value("SELECT SUM('2') FROM onerow"));
    }

    @Test
    void theAnswerIsPresentedAtTheFloatWidth() {
        assertEquals("11.0", value("SELECT SUM(t) FROM ints"));
        assertEquals("3.666666667", value("SELECT TO_VARCHAR(AVG(t)) FROM ints"));
        assertEquals("3.785938897", value("SELECT TO_VARCHAR(STDDEV(t)) FROM ints"));
        assertEquals("14.333333333", value("SELECT TO_VARCHAR(VARIANCE(t)) FROM ints"));
        assertEquals("3.091206165", value("SELECT TO_VARCHAR(STDDEV_POP(t)) FROM ints"));
        assertEquals("9.555555556", value("SELECT TO_VARCHAR(VAR_POP(t)) FROM ints"));
    }

    @Test
    void theWidthFollowsTheMagnitudeNotTheInput() {
        // Ten significant digits below ten, one more per decade — the ordinary FLOAT width rule.
        assertEquals("0.5303300859", value("SELECT TO_VARCHAR(STDDEV(t)) FROM decs"));
        assertEquals("707.106781187", value("SELECT TO_VARCHAR(STDDEV(t)) FROM expo"));
        assertEquals("3.75", value("SELECT TO_VARCHAR(SUM(t)) FROM decs"));
        assertEquals("3000", value("SELECT TO_VARCHAR(SUM(t)) FROM expo"));
    }

    @Test
    void minAndMaxKeepTheVarcharTheyWereGiven() {
        assertEquals("VARCHAR(10)", declared("MIN(t)", "ints"));
        assertEquals("VARCHAR(10)", declared("MAX(t)", "ints"));
        assertEquals("1", value("SELECT MIN(t) FROM ints"));
        assertEquals("8", value("SELECT MAX(t) FROM ints"));
        // Compared as text, so the exponent spellings sort by their characters.
        assertEquals("1e3", value("SELECT MIN(t) FROM expo"));
        assertEquals("2e3", value("SELECT MAX(t) FROM expo"));
    }
}
