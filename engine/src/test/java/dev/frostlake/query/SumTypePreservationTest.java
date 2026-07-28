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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SUM preserves its argument's integer-ness, matching Snowflake: summing an INTEGER / NUMBER(p,0) column
 * yields a whole number (SUM(i)::VARCHAR is "13", not "13.0"), while a DECIMAL or FLOAT argument keeps a
 * floating result. An integer sum that overflows a long is returned exactly (Snowflake's NUMBER(38,0)).
 */
public class SumTypePreservationTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (grp VARCHAR, i INTEGER, d DECIMAL(10,2), f FLOAT, big NUMBER(38,0))");
        engine.execute("INSERT INTO t VALUES ('a', 1, 1.50, 1.5, 9000000000000000000)");
        engine.execute("INSERT INTO t VALUES ('a', 2, 2.50, 2.5, 9000000000000000000)");
        engine.execute("INSERT INTO t VALUES ('b', 10, 10.00, 10.0, 9000000000000000000)");
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private String str(final String sql) {
        return String.valueOf(scalar(sql));
    }

    @Test
    public void sumOfIntegerIsWholeNumber() {
        assertEquals("13", str("SELECT SUM(i)::VARCHAR FROM t"));
        assertTrue(scalar("SELECT SUM(i) FROM t") instanceof Long, "SUM of INTEGER should be integer-typed");
        assertEquals(13L, ((Number) scalar("SELECT SUM(i) FROM t")).longValue());
    }

    @Test
    public void sumOfDecimalStaysFractional() {
        assertEquals(14.0, ((Number) scalar("SELECT SUM(d) FROM t")).doubleValue(), 1e-9);
    }

    @Test
    public void sumOfFloatStaysFractional() {
        assertEquals(14.0, ((Number) scalar("SELECT SUM(f) FROM t")).doubleValue(), 1e-9);
    }

    @Test
    public void sumInGroupByIsWholeNumber() {
        final ResultSet rs = engine.executeQuery(
            "SELECT grp, SUM(i)::VARCHAR AS s FROM t GROUP BY grp ORDER BY grp");
        assertEquals("3", rs.getRows().get(0).getValue(1));   // group a: 1 + 2
        assertEquals("10", rs.getRows().get(1).getValue(1));  // group b: 10
    }

    @Test
    public void sumDistinctAndExpressionArgumentsAreWhole() {
        assertEquals("13", str("SELECT SUM(DISTINCT i)::VARCHAR FROM t"));
        assertEquals("13", str("SELECT SUM(i + 0)::VARCHAR FROM t"));
    }

    @Test
    public void sumOverWindowIsWholeNumber() {
        final Object v = scalar("SELECT SUM(i) OVER () FROM t LIMIT 1");
        assertTrue(v instanceof Long, "window SUM of INTEGER should be integer-typed");
        assertEquals(13L, ((Number) v).longValue());
    }

    @Test
    public void sumArithmeticStaysWhole() {
        assertEquals("26", str("SELECT SUM(i) * 2 FROM t"));
    }

    @Test
    public void integerSumBeyondLongRangeIsExact() {
        // 3 * 9e18 = 2.7e19 overflows a signed long, so it comes back as an exact big integer.
        assertEquals("27000000000000000000", str("SELECT SUM(big)::VARCHAR FROM t"));
    }

    @Test
    public void sumOverNoRowsIsNull() {
        assertNull(scalar("SELECT SUM(i) FROM t WHERE i > 999"));
    }
}
