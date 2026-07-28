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
 * An aggregate nested inside a larger scalar expression — a cast (COUNT(*)::VARCHAR), a concatenation
 * ('n=' || COUNT(*)), arithmetic (SUM(x) + 1) — must still be computed as an aggregate. Previously only a
 * bare aggregate call was recognized in the grouped-projection path, so a wrapped aggregate fell through
 * to a column-name lookup and resolved to NULL.
 */
public class AggregateInExpressionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (grp VARCHAR, id INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO t VALUES ('a', 1, 'x'), ('a', 2, 'y'), ('b', 10, 'z')");
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void countStarCastToVarchar() {
        assertEquals("3", scalar("SELECT COUNT(*)::VARCHAR FROM t"));
        assertEquals("3", scalar("SELECT CAST(COUNT(*) AS VARCHAR) FROM t"));
    }

    @Test
    public void countStarCastToInt() {
        assertEquals(3L, ((Number) scalar("SELECT COUNT(*)::INT FROM t")).longValue());
    }

    @Test
    public void aggregatesOfColumnsCast() {
        assertEquals("3", scalar("SELECT COUNT(id)::VARCHAR FROM t"));
        assertEquals("z", scalar("SELECT MAX(v)::VARCHAR FROM t"));
    }

    @Test
    public void aggregateInConcatenation() {
        assertEquals("n=3", scalar("SELECT 'n=' || COUNT(*) FROM t"));
        assertEquals("x3", scalar("SELECT 'x' || COUNT(*)::VARCHAR FROM t"));
    }

    @Test
    public void aggregateInArithmetic() {
        assertEquals(14.0, ((Number) scalar("SELECT SUM(id) + 1 FROM t")).doubleValue(), 1e-9);
        assertEquals(4L, ((Number) scalar("SELECT COUNT(*) + 1 FROM t")).longValue());
    }

    @Test
    public void nestedAggregateInScalarSubquery() {
        // A diagnostic shape from external-suite triage: an aggregate cast inside a scalar subquery.
        assertEquals("3", scalar("SELECT (SELECT COUNT(*)::VARCHAR FROM t)"));
        assertEquals("count=3", scalar("SELECT 'count=' || (SELECT COUNT(*)::VARCHAR FROM t)"));
    }

    @Test
    public void nestedAggregateWithGroupBy() {
        final ResultSet rs = engine.executeQuery(
            "SELECT grp, COUNT(*)::VARCHAR AS c FROM t GROUP BY grp ORDER BY grp");
        assertEquals(2, rs.getRowCount());
        assertEquals("a", rs.getRows().get(0).getValue(0));
        assertEquals("2", rs.getRows().get(0).getValue(1));
        assertEquals("b", rs.getRows().get(1).getValue(0));
        assertEquals("1", rs.getRows().get(1).getValue(1));
    }

    @Test
    public void nestedAggregateOverEmptyGroupIsZero() {
        assertEquals("0", scalar("SELECT COUNT(*)::VARCHAR FROM t WHERE id > 999"));
    }

    // ---- an aggregate nested inside a scalar FUNCTION argument (NVL/COALESCE/...) ----
    // The aggregate lives under functionArgList/functionArg/booleanExpr, not as a direct expression child,
    // so it was not detected — the query ran row-by-row and MIN/MAX were looked up as scalar functions
    // ("Unknown function: MIN").

    @Test
    public void aggregateInsideNvl() {
        assertEquals(1L, ((Number) scalar("SELECT NVL(MIN(id), 0) FROM t")).longValue());
    }

    @Test
    public void aggregateInsideCoalesce() {
        assertEquals(1L, ((Number) scalar("SELECT COALESCE(MIN(id), 0) FROM t")).longValue());
    }

    @Test
    public void maxInsideNvl() {
        assertEquals(10L, ((Number) scalar("SELECT NVL(MAX(id), 0) FROM t")).longValue());
    }

    @Test
    public void aggregateInScalarFnOverEmptyInputUsesDefault() {
        // The real loader form: NVL(MIN(...), default) over an empty input must return the default (an
        // implicit-aggregate one-row result of NULL), not crash with an index-out-of-bounds.
        assertEquals(-1L, ((Number) scalar("SELECT NVL(MIN(id), -1) FROM t WHERE id > 999")).longValue());
    }

    @Test
    public void aggregateInScalarFnInsideScalarSubquery() {
        assertEquals(1L, ((Number) scalar("SELECT (SELECT NVL(MIN(id), -1) FROM t)")).longValue());
    }

    @Test
    public void plainScalarFunctionIsNotTreatedAsAggregate() {
        // Regression: NVL over ordinary columns (no aggregate) must stay a per-row projection.
        final ResultSet rs = engine.executeQuery("SELECT NVL(v, 'none') FROM t ORDER BY id");
        assertEquals(3, rs.getRowCount());
        assertEquals("x", rs.getRows().get(0).getValue(0));
    }
}
