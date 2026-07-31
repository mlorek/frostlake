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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests EXPLAIN — returns the structural execution plan (operator steps + scanned tables) as a result set.
 */
public class ExplainTest extends BaseDatabaseTest {

    private static final String PLAN_SHAPE =
        "asserts Frostlake's operator names (TableScan / Filter / Aggregate / …); an EXPLAIN plan on a real "
        + "account is Snowflake's own internal plan shape, with different rows, columns and operator names";

    private Set<String> operations(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final Set<String> ops = new HashSet<>();
        for (final Row row : rs.getRows()) {
            ops.add(String.valueOf(row.getValue(1)));
        }
        return ops;
    }

    @Test
    public void explainSelectShowsOperators() {
        Assumptions.assumeFalse(isLiveSnowflake(), PLAN_SHAPE);
        engine.execute("CREATE TABLE t (id INTEGER, region VARCHAR, amount INTEGER)");
        final Set<String> ops = operations(
            "EXPLAIN SELECT region, SUM(amount) FROM t WHERE id > 0 GROUP BY region ORDER BY region LIMIT 5");
        assertTrue(ops.contains("Result"), ops.toString());
        assertTrue(ops.contains("TableScan"), ops.toString());
        assertTrue(ops.contains("Filter"), ops.toString());
        assertTrue(ops.contains("Aggregate"), ops.toString());
        assertTrue(ops.contains("Sort"), ops.toString());
        assertTrue(ops.contains("Limit"), ops.toString());
    }

    @Test
    public void explainJoinShowsJoin() {
        Assumptions.assumeFalse(isLiveSnowflake(), PLAN_SHAPE);
        engine.execute("CREATE TABLE a (id INTEGER)");
        engine.execute("CREATE TABLE b (id INTEGER)");
        final Set<String> ops = operations("EXPLAIN SELECT * FROM a JOIN b ON a.id = b.id");
        assertTrue(ops.contains("Join"), ops.toString());
        assertTrue(ops.contains("TableScan"), ops.toString());
    }

    @Test
    public void explainUsingTabular() {
        Assumptions.assumeFalse(isLiveSnowflake(), PLAN_SHAPE);
        engine.execute("CREATE TABLE t2 (id INTEGER)");
        final Set<String> ops = operations("EXPLAIN USING TABULAR SELECT * FROM t2");
        assertTrue(ops.contains("Result"), ops.toString());
        assertTrue(ops.contains("TableScan"), ops.toString());
    }
}
