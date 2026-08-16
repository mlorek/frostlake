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
 * A percentile's {@code WITHIN GROUP (ORDER BY …)} key when it is not a bare column name.
 *
 * <p>★ IT RESOLVED THE KEY BY COLUMN NAME, so anything that was not one fed the accumulator nulls and
 * the answer was NULL — an arithmetic key, a cast, a function call, a CASE, and, less obviously, a
 * QUALIFIED name: {@code pe.n102} missed as surely as {@code n102 * 2} did. The key is an expression
 * like every other aggregate argument and is now evaluated per row through the same reader.
 *
 * <p>★ THE TYPE ALREADY COMPOSED, which is what made this worth doing rather than rewriting: the
 * inferencer widened {@code n102 * 2}'s NUMBER(11,2) to NUMBER(14,5) and matched live exactly while
 * the value was null. Only the value was missing.
 *
 * <p>NULLs in the key are SKIPPED, on both engines: over 1.00, NULL and 8.00 the median is 4.50000,
 * which is the median of the two values that exist rather than of three.
 *
 * <p>Left for their own tasks: a key naming a SELECT ALIAS, which needs the alias channel the grouped
 * keys use; and a non-numeric key, where live refuses at row time ("Numeric value 'a' is not
 * recognized") and Frostlake answers 0.
 */
public class PercentileExpressionKeyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE pe (a INT, g INT, n102 NUMBER(10,2),"
            + " n380 NUMBER(38,0))");
        engine.execute("INSERT INTO pe VALUES (1, 1, 1.00, 1), (2, 1, 2.00, 2), (3, 1, 8.00, 4)");
        engine.execute("CREATE OR REPLACE TABLE pn (a INT, n102 NUMBER(10,2))");
        engine.execute("INSERT INTO pn VALUES (1, 1.00), (2, NULL), (3, 8.00)");
        engine.execute("CREATE OR REPLACE TABLE pj (a INT, m NUMBER(10,2))");
        engine.execute("INSERT INTO pj VALUES (1, 10.00), (2, 20.00), (3, 30.00)");
    }

    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder all = new StringBuilder();
        while (rs.next()) {
            if (all.length() > 0) {
                all.append(",");
            }
            for (int c = 0; c < rs.getColumns().size(); c++) {
                if (c > 0) {
                    all.append("/");
                }
                all.append(String.valueOf(rs.getValue(c)));
            }
        }
        return all.toString();
    }

    /** ★ An ARITHMETIC key, where the whole family used to answer NULL. */
    @Test
    public void anArithmeticKeyIsEvaluated() {
        assertEquals("4.00000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102 * 2) FROM pe"));
        assertEquals("4.00000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102 + n380) FROM pe"));
        assertEquals("4.00",
            answer("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n102 * 2) FROM pe"),
            "the picking form too");
    }

    /** A CAST key, whose declared type the widening rule then composes with. */
    @Test
    public void acastKeyIsEvaluated() {
        assertEquals("2.0000000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n380::NUMBER(12,4)) FROM pe"));
    }

    /** A FUNCTION call and a CASE, which is as far from a column name as a key gets. */
    @Test
    public void acallAndAcaseAreEvaluated() {
        assertEquals("2.00000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY ABS(n102)) FROM pe"));
        assertEquals("8.00000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP"
                + " (ORDER BY CASE WHEN a = 1 THEN 100.00 ELSE n102 END) FROM pe"),
            "the CASE turns 1.00 into 100.00, so the middle of 2.00, 8.00 and 100.00 is 8.00");
    }

    /** ★ A QUALIFIED name missed too — the resolution was by bare name, not by reference. */
    @Test
    public void aqualifiedNameIsEvaluated() {
        assertEquals("2.00000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY pe.n102) FROM pe"));
    }

    /** Over a JOIN, by the other relation's column and by an expression across both. */
    @Test
    public void ajoinedKeyIsEvaluated() {
        assertEquals("20.00000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY pj.m)"
                + " FROM pe JOIN pj ON pe.a = pj.a"));
        assertEquals("22.00000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY pj.m + pe.n102)"
                + " FROM pe JOIN pj ON pe.a = pj.a"));
    }

    /** ★ NULLs in the key are SKIPPED, not counted — measured, not assumed. */
    @Test
    public void nullsInTheKeyAreSkipped() {
        assertEquals("4.50000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102) FROM pn"),
            "the median of 1.00 and 8.00, the two values that exist");
        assertEquals("9.00000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102 * 2) FROM pn"));
        assertEquals("1.00",
            answer("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n102) FROM pn"));
    }

    /** A CONSTANT key is a value like any other. */
    @Test
    public void aconstantKeyIsEvaluated() {
        assertEquals("1.000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY 1) FROM pe"));
    }

    /** The GROUPED and WINDOWED spellings take the same key. */
    @Test
    public void thegroupedAndWindowedFormsAgree() {
        assertEquals("1/4.00000",
            answer("SELECT g, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102 * 2)"
                + " FROM pe GROUP BY g ORDER BY g"));
        assertEquals("1/4.00000,2/4.00000,3/4.00000",
            answer("SELECT a, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102 * 2)"
                + " OVER (PARTITION BY g) FROM pe ORDER BY a"));
    }

    /** The bare-column spelling, which always worked, must not move. */
    @Test
    public void thebareColumnSpellingIsUntouched() {
        assertEquals("2.00000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102) FROM pe"));
    }
}
