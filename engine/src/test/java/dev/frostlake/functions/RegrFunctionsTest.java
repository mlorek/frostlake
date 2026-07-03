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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The REGR_* linear-regression aggregate family, argument order REGR_xxx(y, x) (y dependent, x independent).
 * The base table is the perfect line y = 2x + 1 over x = 1..4, so the fit is exact; degenerate cases
 * (constant x / constant y / empty) exercise the NULL and 1.0 edges.
 */
public class RegrFunctionsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (x DOUBLE, y DOUBLE)");
        engine.execute("INSERT INTO t VALUES (1, 3), (2, 5), (3, 7), (4, 9)");   // y = 2x + 1
    }

    private double scalar(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    private Object raw(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void slopeAndIntercept() {
        assertEquals(2.0, scalar("SELECT REGR_SLOPE(y, x) FROM t"), 1e-9);
        assertEquals(1.0, scalar("SELECT REGR_INTERCEPT(y, x) FROM t"), 1e-9);
    }

    @Test
    public void r2IsOneForAPerfectFit() {
        assertEquals(1.0, scalar("SELECT REGR_R2(y, x) FROM t"), 1e-9);
    }

    @Test
    public void countAndAverages() {
        assertEquals(4L, ((Number) raw("SELECT REGR_COUNT(y, x) FROM t")).longValue());
        assertEquals(2.5, scalar("SELECT REGR_AVGX(y, x) FROM t"), 1e-9);
        assertEquals(6.0, scalar("SELECT REGR_AVGY(y, x) FROM t"), 1e-9);
    }

    @Test
    public void sumsOfSquaresAndProducts() {
        assertEquals(5.0, scalar("SELECT REGR_SXX(y, x) FROM t"), 1e-9);
        assertEquals(20.0, scalar("SELECT REGR_SYY(y, x) FROM t"), 1e-9);
        assertEquals(10.0, scalar("SELECT REGR_SXY(y, x) FROM t"), 1e-9);
    }

    @Test
    public void nullPairsAreIgnored() {
        engine.execute("INSERT INTO t VALUES (5, NULL), (NULL, 11)");
        // The two partial rows are dropped, so the fit is still exactly y = 2x + 1.
        assertEquals(2.0, scalar("SELECT REGR_SLOPE(y, x) FROM t"), 1e-9);
        assertEquals(4L, ((Number) raw("SELECT REGR_COUNT(y, x) FROM t")).longValue());
    }

    @Test
    public void constantXMakesSlopeAndR2Null() {
        engine.execute("CREATE TABLE c (x DOUBLE, y DOUBLE)");
        engine.execute("INSERT INTO c VALUES (5, 1), (5, 2), (5, 3)");   // x has zero variance
        assertNull(raw("SELECT REGR_SLOPE(y, x) FROM c"));
        assertNull(raw("SELECT REGR_R2(y, x) FROM c"));
        assertEquals(0.0, scalar("SELECT REGR_SXX(y, x) FROM c"), 1e-9);
    }

    @Test
    public void constantYMakesR2One() {
        engine.execute("CREATE TABLE k (x DOUBLE, y DOUBLE)");
        engine.execute("INSERT INTO k VALUES (1, 7), (2, 7), (3, 7)");   // y has zero variance, x does not
        assertEquals(1.0, scalar("SELECT REGR_R2(y, x) FROM k"), 1e-9);
    }

    @Test
    public void emptyInputCountsZeroAndOthersNull() {
        engine.execute("CREATE TABLE e (x DOUBLE, y DOUBLE)");
        assertEquals(0L, ((Number) raw("SELECT REGR_COUNT(y, x) FROM e")).longValue());
        assertNull(raw("SELECT REGR_SLOPE(y, x) FROM e"));
        assertNull(raw("SELECT REGR_AVGX(y, x) FROM e"));
    }

    @Test
    public void groupedRegression() {
        engine.execute("CREATE TABLE g (grp INTEGER, x DOUBLE, y DOUBLE)");
        // grp 1: y = 2x + 1 ; grp 2: y = -x + 10
        engine.execute("INSERT INTO g VALUES (1,1,3),(1,2,5),(1,3,7),(2,1,9),(2,2,8),(2,3,7)");
        assertEquals(2.0, scalar("SELECT REGR_SLOPE(y, x) FROM g WHERE grp = 1"), 1e-9);
        assertEquals(-1.0, scalar("SELECT REGR_SLOPE(y, x) FROM g WHERE grp = 2"), 1e-9);
    }
}
