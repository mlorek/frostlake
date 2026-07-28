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
 * FROM items apply in textual order. A comma LATERAL / TABLE(FLATTEN(...)) written BEFORE an explicit
 * join must be in scope for that join's ON condition (the flatten output is the join's left side); a
 * lateral written AFTER the joins still sees the joined tables. Before the fix, any explicit join
 * deferred every comma-lateral, so an ON referencing the flatten output matched nothing.
 */
public class LateralBeforeJoinOrderTest extends BaseDatabaseTest {

    private void seed() {
        engine.execute("CREATE TABLE cards (card_id VARCHAR, labels VARIANT)");
        engine.execute("INSERT INTO cards SELECT 'c1', PARSE_JSON('[\"l1\",\"l2\"]')");
        engine.execute("CREATE TABLE labels (label_key VARCHAR, label_name VARCHAR)");
        engine.execute("INSERT INTO labels VALUES ('l1', 'first'), ('l2', 'second'), ('l3', 'third')");
    }

    @Test
    public void testJoinOnSeesPrecedingFlattenOutput() {
        seed();
        final ResultSet rs = engine.executeQuery("""
            SELECT c.card_id, t.value::VARCHAR AS label_id, lb.label_name
            FROM cards AS c, TABLE(FLATTEN(labels)) AS t
            INNER JOIN labels AS lb ON lb.label_key = t.value::VARCHAR
            ORDER BY label_id
            """);
        assertEquals(2, rs.getRows().size());
        assertEquals("first", rs.getRows().get(0).getValue(2));
        assertEquals("second", rs.getRows().get(1).getValue(2));
    }

    @Test
    public void testJoinOnSeesPrecedingFlattenViaLateralAlias() {
        seed();
        // The ON references the SELECT-list alias (Snowflake lateral column alias), the vendor idiom.
        final ResultSet rs = engine.executeQuery("""
            SELECT c.card_id, t.value::VARCHAR AS label_id, lb.label_name
            FROM cards AS c, TABLE(FLATTEN(labels)) AS t
            INNER JOIN labels AS lb ON lb.label_key = label_id
            ORDER BY label_id
            """);
        assertEquals(2, rs.getRows().size());
    }

    @Test
    public void testLateralAfterJoinStillSeesJoinedTable() {
        engine.execute("CREATE TABLE a_side (k VARCHAR)");
        engine.execute("INSERT INTO a_side VALUES ('x')");
        engine.execute("CREATE TABLE b_side (k VARCHAR, arr VARIANT)");
        engine.execute("INSERT INTO b_side SELECT 'x', PARSE_JSON('[10,20]')");
        // The lateral is written AFTER the join and correlates to the JOINED table b — the deferred path.
        final ResultSet rs = engine.executeQuery("""
            SELECT a.k, f.value::NUMBER AS v
            FROM a_side AS a
            INNER JOIN b_side AS b ON b.k = a.k, TABLE(FLATTEN(b.arr)) AS f
            ORDER BY v
            """);
        assertEquals(2, rs.getRows().size());
        assertEquals(10L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }
}
