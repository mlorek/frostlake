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
 * An aggregate over a FLOAT COLUMN, which the values alone cannot recognise as approximate.
 *
 * <p>★ WHY THE DECLARED TYPE HAS TO BE PASSED IN. A FLOAT-declared value can reach an accumulator in
 * an exact carrier — a FLOAT column holds a double, but an expression computed exactly, or a value
 * restored from an older snapshot, does not — and then a 2 from a FLOAT and a 2 from a NUMBER(1,0) are
 * the same object. Reading approximateness off the VALUE therefore said "exact, scale 0", the NUMBER
 * scale rule was applied to what should stay a double, and live's 2.333333333 came back 2.333333.
 *
 * <p>★ THE SAME SWITCH DRIVES SIX FUNCTIONS AND FOUR PATHS: the variance/stddev family, AVG in the
 * scalar, grouped and windowed spellings, and the window's two shapes — a cumulative window averages
 * at the aggregate's own width, every other shape three decimals narrower.
 *
 * <p>★ AN EXPRESSION WAS ALREADY RIGHT, which is what narrowed the fix to the column read:
 * {@code VARIANCE(fl * 1.0)} and {@code VARIANCE(n::FLOAT)} both answered 2.333333333 all along,
 * because those values arrive as real doubles. Only a bare column needed its type carried alongside.
 *
 * <p>★ SUM IS DELIBERATELY UNTOUCHED. It keeps no scale of its own, so a FLOAT sum already agreed with
 * live (7, not 7.0) and forcing the double path there would have changed a correct answer.
 */
public class FloatAggregateInputTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fai (fl FLOAT, n NUMBER(10,2), g NUMBER(2,0))");
        engine.execute("INSERT INTO fai VALUES (1, 1.00, 1), (2, 2.00, 1), (4, 4.00, 2)");
        engine.execute("CREATE OR REPLACE TABLE faf (fl FLOAT)");
        engine.execute("INSERT INTO faf VALUES (0.1), (0.2), (0.4)");
    }

    private String text(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    @Test
    void theVarianceFamilyKeepsTheDoubleOverAFloatColumn() {
        assertEquals("2.333333333", text("SELECT TO_VARCHAR(VARIANCE(fl)) FROM fai"));
        assertEquals("2.333333333", text("SELECT TO_VARCHAR(VAR_SAMP(fl)) FROM fai"));
        assertEquals("1.555555556", text("SELECT TO_VARCHAR(VAR_POP(fl)) FROM fai"));
        assertEquals("1.527525232", text("SELECT TO_VARCHAR(STDDEV(fl)) FROM fai"));
        assertEquals("1.527525232", text("SELECT TO_VARCHAR(STDDEV_SAMP(fl)) FROM fai"));
        assertEquals("1.247219129", text("SELECT TO_VARCHAR(STDDEV_POP(fl)) FROM fai"));
    }

    @Test
    void aFractionalFloatIsTheSameStory() {
        assertEquals("0.02333333333", text("SELECT TO_VARCHAR(VARIANCE(fl)) FROM faf"));
        assertEquals("0.01555555556", text("SELECT TO_VARCHAR(VAR_POP(fl)) FROM faf"));
        assertEquals("0.1527525232", text("SELECT TO_VARCHAR(STDDEV(fl)) FROM faf"));
        assertEquals("0.1247219129", text("SELECT TO_VARCHAR(STDDEV_POP(fl)) FROM faf"));
        assertEquals("0.2333333333", text("SELECT TO_VARCHAR(AVG(fl)) FROM faf"));
    }

    @Test
    void avgKeepsItInEverySpelling() {
        assertEquals("2.333333333", text("SELECT TO_VARCHAR(AVG(fl)) FROM fai"));
        assertEquals("1.5",
            text("SELECT TO_VARCHAR(AVG(fl)) FROM fai GROUP BY g ORDER BY g LIMIT 1"));
        assertEquals("2.333333333", text("SELECT TO_VARCHAR(AVG(fl) OVER ()) FROM fai LIMIT 1"));
        assertEquals("1",
            text("SELECT TO_VARCHAR(AVG(fl) OVER (ORDER BY fl)) FROM fai ORDER BY fl LIMIT 1"));
        assertEquals("2.333333333",
            text("SELECT TO_VARCHAR(VARIANCE(fl) OVER ()) FROM fai LIMIT 1"));
    }

    @Test
    void anExpressionArgumentWasAlreadyRight() {
        assertEquals("2.333333333", text("SELECT TO_VARCHAR(VARIANCE(fl * 1.0)) FROM fai"));
        assertEquals("2.333333333", text("SELECT TO_VARCHAR(VARIANCE(n::FLOAT)) FROM fai"));
    }

    @Test
    void anExactColumnIsUntouched() {
        assertEquals("2.3333333333", text("SELECT TO_VARCHAR(VARIANCE(n)) FROM fai"));
        assertEquals("1.5555555556", text("SELECT TO_VARCHAR(VAR_POP(n)) FROM fai"));
        assertEquals("1.527525232", text("SELECT TO_VARCHAR(STDDEV(n)) FROM fai"));
        assertEquals("1.247219129", text("SELECT TO_VARCHAR(STDDEV_POP(n)) FROM fai"));
        assertEquals("2.33333333", text("SELECT TO_VARCHAR(AVG(n)) FROM fai"));
        assertEquals("7.00", text("SELECT TO_VARCHAR(SUM(n)) FROM fai"));
    }

    @Test
    void sumOverAFloatIsUnchanged() {
        assertEquals("7", text("SELECT TO_VARCHAR(SUM(fl)) FROM fai"));
        assertEquals("0.7", text("SELECT TO_VARCHAR(SUM(fl)) FROM faf"));
        assertEquals("3", text("SELECT TO_VARCHAR(SUM(fl)) FROM fai GROUP BY g ORDER BY g LIMIT 1"));
    }
}
