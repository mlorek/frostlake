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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A QUOTIENT presented at the scale its column DECLARES.
 *
 * <p>★ THE DECLARED TYPE WAS ALREADY RIGHT. {@code 1.0 / 3.0} is NUMBER(7,6) on both engines, and a CTAS
 * already stored six decimals. It was the bare SELECT that handed back the division's own working scale,
 * which grew with the DIVIDEND's decimals — one written {@code 1.0000000} reached twelve.
 *
 * <p>★ MOST DIVISIONS NEVER MOVED, and they are the ones to watch: wherever the working scale already
 * equalled the declared one — {@code 1/3}, {@code 7/2}, {@code 1/8}, {@code 10.5/2}, a column over a
 * literal — the text is unchanged. Only the cells where the two disagreed shift.
 */
public class DivisionDeclaredScaleTest extends BaseDatabaseTest {

    private String one(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    @Test
    void aDividendsExtraDecimalsNoLongerWiden() {
        assertEquals("0.333333", one("SELECT 1.0 / 3.0"));
        assertEquals("0.333333", one("SELECT 1.000 / 3.0"));
        assertEquals("0.333333", one("SELECT 1.0000000 / 3.0"));
    }

    @Test
    void theDivisionsThatAlreadyAgreedAreUnmoved() {
        assertEquals("0.333333", one("SELECT 1 / 3"));
        assertEquals("3.500000", one("SELECT 7 / 2"));
        assertEquals("0.125000", one("SELECT 1 / 8"));
        assertEquals("5.2500000", one("SELECT 10.5 / 2"));
    }

    @Test
    void aColumnDividedKeepsItsDeclaredScale() {
        engine.execute("CREATE OR REPLACE TABLE div_scale (a NUMBER(10,2))");
        engine.execute("INSERT INTO div_scale VALUES (1.00), (2.00)");
        assertEquals("0.33333333", one("SELECT a / 3 FROM div_scale ORDER BY a"));
    }

    @Test
    void theDeclaredTypeIsUnchanged() {
        engine.execute("CREATE OR REPLACE TABLE div_scale_t AS SELECT 1.0 / 3.0 AS c");
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE div_scale_t");
        rs.next();
        assertEquals("NUMBER(7,6)", String.valueOf(rs.getValue(1)));
    }

    @Test
    void aStoredQuotientAgreesWithTheSelected() {
        engine.execute("CREATE OR REPLACE TABLE div_scale_s AS SELECT 1.0 / 3.0 AS c");
        assertEquals("0.333333", one("SELECT c FROM div_scale_s"));
        assertEquals("0.333333", one("SELECT 1.0 / 3.0"));
    }

    @Test
    void divisionInsideAndOutsideAnAggregateIsUnmoved() {
        engine.execute("CREATE OR REPLACE TABLE div_agg (a NUMBER(10,2))");
        engine.execute("INSERT INTO div_agg VALUES (1.00), (2.00), (3.00), (5.00)");
        assertEquals("3.66666667", one("SELECT SUM(a / 3) FROM div_agg"));
        assertEquals("3.66666667", one("SELECT SUM(a) / 3 FROM div_agg"));
        assertEquals("3.66666667", one("SELECT SUM(a / 3) OVER () FROM div_agg"));
    }
}
