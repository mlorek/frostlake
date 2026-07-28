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
 * A real FROM-source column takes precedence over a same-named sibling (lateral) SELECT-list alias, as in
 * Snowflake. This is what makes a swap projection — {@code SELECT b AS a, a AS b FROM t} — read BOTH values
 * from the input row; a resolver that prefers the earlier alias collapses the swap to b, b. A sibling alias
 * that is NOT backed by a column still resolves laterally.
 */
public class LateralAliasColumnPrecedenceTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE hops (start_key VARCHAR, end_key VARCHAR, start_kind VARCHAR, end_kind VARCHAR)");
        engine.execute("INSERT INTO hops VALUES ('K1', 'K2', 'ALPHA', 'BETA')");
    }

    @Test
    public void swapProjectionReadsBothValuesFromTheInputRow() {
        final ResultSet result = engine.executeQuery(
            "SELECT end_key AS start_key, start_key AS end_key FROM hops");
        assertEquals(1, result.getRows().size());
        assertEquals("K2", result.getRows().get(0).getValue(0));
        assertEquals("K1", result.getRows().get(0).getValue(1));
    }

    @Test
    public void fourColumnSwapKeepsEveryPairIndependent() {
        final ResultSet result = engine.executeQuery(
            """
            SELECT end_key   AS start_key,
                   start_key  AS end_key,
                   end_kind   AS start_kind,
                   start_kind AS end_kind
            FROM hops
            """);
        assertEquals("K2", result.getRows().get(0).getValue(0));
        assertEquals("K1", result.getRows().get(0).getValue(1));
        assertEquals("BETA", result.getRows().get(0).getValue(2));
        assertEquals("ALPHA", result.getRows().get(0).getValue(3));
    }

    @Test
    public void swapProjectionThroughAViewMirrorBranch() {
        engine.execute(
            """
            CREATE VIEW mirrored AS
            SELECT start_key, end_key FROM hops
            UNION ALL
            SELECT end_key AS start_key, start_key AS end_key FROM hops
            """);
        final ResultSet result = engine.executeQuery(
            "SELECT start_key, end_key FROM mirrored ORDER BY start_key");
        assertEquals(2, result.getRows().size());
        assertEquals("K1", result.getRows().get(0).getValue(0));
        assertEquals("K2", result.getRows().get(0).getValue(1));
        assertEquals("K2", result.getRows().get(1).getValue(0));
        assertEquals("K1", result.getRows().get(1).getValue(1));
    }

    @Test
    public void aliasNotBackedByAColumnStillResolvesLaterally() {
        final ResultSet result = engine.executeQuery(
            "SELECT UPPER(start_key) || '!' AS shouted, shouted AS again FROM hops");
        assertEquals("K1!", result.getRows().get(0).getValue(0));
        assertEquals("K1!", result.getRows().get(0).getValue(1));
    }

    @Test
    public void derivedItemOverAnUnbackedAliasResolvesLaterally() {
        final ResultSet result = engine.executeQuery(
            "SELECT LENGTH(start_key) + 1 AS n, n * 10 AS n10 FROM hops");
        assertEquals(3L, ((Number) result.getRows().get(0).getValue(0)).longValue());
        assertEquals(30L, ((Number) result.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void groupByKeyColumnWinsOverSameNamedAggregateAlias() {
        engine.execute("CREATE TABLE readings (grp VARCHAR, amount INTEGER)");
        engine.execute("INSERT INTO readings VALUES ('g1', 5)");
        engine.execute("INSERT INTO readings VALUES ('g1', 7)");
        final ResultSet result = engine.executeQuery(
            "SELECT MAX(amount) AS grp, grp AS grp_again FROM readings GROUP BY grp");
        assertEquals(1, result.getRows().size());
        assertEquals(7L, ((Number) result.getRows().get(0).getValue(0)).longValue());
        assertEquals("g1", result.getRows().get(0).getValue(1));
    }
}
