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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ORDER BY keys whose SQL needs whitespace between tokens — {@code x IS NOT NULL},
 * {@code CAST(x AS NUMBER)}, {@code x BETWEEN a AND b}, CASE — must resolve. The key text used to be
 * taken with ANTLR's {@code getText()}, which concatenates tokens with NO separator, collapsing
 * {@code x IS NOT NULL} into the identifier {@code xISNOTNULL}; the key then matched nothing and the
 * query died with "Column not found in ORDER BY". Keys whose stripped form happens to stay valid
 * ({@code ABS(x)}, {@code a+b}) worked by luck, and a single-row result hid the bug because no sort
 * runs — so every case here needs at least two rows.
 */
public class OrderByKeySpacingTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE ob_src (x NUMBER)");
        engine.execute("INSERT INTO ob_src VALUES (5), (2), (50)");
    }

    private ResultSet q(final String sql) {
        return engine.executeQuery(sql);
    }

    @Test
    public void testOrdinalOverIsNotNullKey() {
        final ResultSet rs = q("SELECT x IS NOT NULL AS g, x FROM ob_src ORDER BY 2");
        assertEquals(3, rs.getRows().size());
        assertEquals(Boolean.TRUE, rs.getRows().get(0).getValue(0));
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void testOrdinalOverCastKeySortsByValue() {
        final ResultSet rs = q("SELECT CAST(x AS NUMBER) AS g FROM ob_src ORDER BY 1");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(5L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals(50L, ((Number) rs.getRows().get(2).getValue(0)).longValue());
    }

    @Test
    public void testOrdinalOverCastKeyDescending() {
        final ResultSet rs = q("SELECT CAST(x AS NUMBER) AS g FROM ob_src ORDER BY 1 DESC");
        assertEquals(50L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(2).getValue(0)).longValue());
    }

    @Test
    public void testOrdinalOverBetweenKey() {
        // BETWEEN yields false for 50, true for 2 and 5 — false sorts first.
        final ResultSet rs = q("SELECT x BETWEEN 1 AND 9 AS g, x FROM ob_src ORDER BY 1, 2");
        assertEquals(Boolean.FALSE, rs.getRows().get(0).getValue(0));
        assertEquals(50L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(Boolean.TRUE, rs.getRows().get(1).getValue(0));
    }

    @Test
    public void testOrdinalOverCaseKey() {
        final ResultSet rs = q("""
            SELECT CASE WHEN x > 10 THEN 1 ELSE 2 END AS g, x FROM ob_src ORDER BY 1, 2
            """);
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(50L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }

    @Test
    public void testExplicitExpressionKeyNeedingSpaces() {
        // Not an ordinal and not an alias — the key as written must survive intact.
        final ResultSet rs = q("SELECT x FROM ob_src ORDER BY x BETWEEN 1 AND 9, x");
        assertEquals(50L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }

    @Test
    public void testAliasKeyStillWorks() {
        final ResultSet rs = q("SELECT CAST(x AS NUMBER) AS g FROM ob_src ORDER BY g");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(50L, ((Number) rs.getRows().get(2).getValue(0)).longValue());
    }

    @Test
    public void testGuardedFunctionCallOrdinal() {
        final ResultSet rs = q("SELECT IFF(x IS NOT NULL, 100/x, -1) AS guarded FROM ob_src ORDER BY 1");
        assertEquals(3, rs.getRows().size());
        assertEquals(2.0, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 0.0001);
        assertEquals(50.0, ((Number) rs.getRows().get(2).getValue(0)).doubleValue(), 0.0001);
    }

    @Test
    public void testGroupedOrdinalOverSpacedKey() {
        final ResultSet rs = q("SELECT x BETWEEN 1 AND 9 AS g, COUNT(*) AS c FROM ob_src GROUP BY 1 ORDER BY 1");
        assertEquals(2, rs.getRows().size());
        assertEquals(Boolean.FALSE, rs.getRows().get(0).getValue(0));
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(Boolean.TRUE, rs.getRows().get(1).getValue(0));
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }

    @Test
    public void testPlainKeysUnaffected() {
        // The keys that always worked must keep working (ordinal, plain column, function, arithmetic).
        assertEquals(2L, ((Number) q("SELECT ABS(x) FROM ob_src ORDER BY 1").getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) q("SELECT x FROM ob_src ORDER BY x").getRows().get(0).getValue(0)).longValue());
        assertEquals(3L, ((Number) q("SELECT x + 1 FROM ob_src ORDER BY 1").getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testCteValuesOrdinalOverFunctionCall() {
        final ResultSet rs = q("""
            WITH r AS (SELECT * FROM VALUES (3, 4), (1, 2) AS t(a, b))
            SELECT ABS(a), a FROM r ORDER BY 1
            """);
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(3L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }
}
