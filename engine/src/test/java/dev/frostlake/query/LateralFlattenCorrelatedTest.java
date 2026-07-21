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
 * LATERAL FLATTEN whose {@code input} is a FUNCTION-WRAPPED correlated column (e.g.
 * {@code FLATTEN(input => parse_json(tags))}). The correlated column lives on the outer row, so the whole
 * input expression must be evaluated with the lateral context in scope — a bare {@code input => tags}
 * always worked, but the wrapped form previously failed with "column not found".
 */
public class LateralFlattenCorrelatedTest extends BaseDatabaseTest {

    @Test
    public void flattenOverParseJsonOfCorrelatedColumn() {
        engine.execute("CREATE TABLE devices (name VARCHAR, tags VARCHAR)");
        engine.execute("INSERT INTO devices VALUES ('d1', '[\"a\",\"b\"]'), ('d2', '[\"c\"]')");

        final ResultSet rs = engine.executeQuery("""
            SELECT name, f.value::VARCHAR AS tag
            FROM devices, LATERAL FLATTEN(input => parse_json(tags)) f
            ORDER BY name, tag
            """);

        assertEquals(3, rs.getRowCount());
        assertEquals("d1", rs.getRows().get(0).getValue(0));
        assertEquals("a", rs.getRows().get(0).getValue(1));
        assertEquals("d1", rs.getRows().get(1).getValue(0));
        assertEquals("b", rs.getRows().get(1).getValue(1));
        assertEquals("d2", rs.getRows().get(2).getValue(0));
        assertEquals("c", rs.getRows().get(2).getValue(1));
    }

    @Test
    public void cteValuesFlattenDenseRankPipeline() {
        // My own version of a real-world pipeline: a CTE over VALUES (auto-named COLUMN1..n), a LATERAL
        // FLATTEN of a parse_json'd correlated column that FANS OUT one outer row into several, a
        // value::cast, a DENSE_RANK() OVER (ORDER BY <VALUES col>, <cast of the FLATTEN column>) — the
        // multi-key window ORDER BY that must tie-break on the flatten output — a ::NUMBER cast, and
        // string building with ||/UPPER/REPLACE/LPAD/TO_VARCHAR.
        final ResultSet rs = engine.executeQuery("""
            WITH orders AS (
              SELECT * FROM VALUES
                ('ORD1', 'alpha_team', '["red_hat","blue_sky"]', '50'),
                ('ORD2', 'beta_team',  '["green_field"]',        '75')
            )
            SELECT
              UPPER(REPLACE(column2,'_','-')) || '#' || UPPER(REPLACE(t.value::VARCHAR,'_','-')) || '#' ||
                LPAD(TO_VARCHAR(DENSE_RANK() OVER (ORDER BY column2, t.value::VARCHAR)), 3, '0') AS tag_id,
              column1 AS order_id,
              t.value::VARCHAR AS tag,
              column4::NUMBER AS score
            FROM orders, LATERAL FLATTEN(input => parse_json(column3)) t
            ORDER BY tag_id
            """);

        assertEquals(3, rs.getRowCount());

        // DENSE_RANK over (column2, t.value): (alpha,blue_sky)=1, (alpha,red_hat)=2, (beta,green_field)=3
        // — the second key (the cast FLATTEN column) is exactly what breaks the alpha_team tie.
        assertRow(rs, 0, "ALPHA-TEAM#BLUE-SKY#001", "ORD1", "blue_sky", 50L);
        assertRow(rs, 1, "ALPHA-TEAM#RED-HAT#002", "ORD1", "red_hat", 50L);
        assertRow(rs, 2, "BETA-TEAM#GREEN-FIELD#003", "ORD2", "green_field", 75L);
    }

    private void assertRow(final ResultSet rs, final int r, final String tagId, final String orderId,
                           final String tag, final long score) {
        assertEquals(tagId, rs.getRows().get(r).getValue(0), "tag_id row " + r);
        assertEquals(orderId, rs.getRows().get(r).getValue(1), "order_id row " + r);
        assertEquals(tag, rs.getRows().get(r).getValue(2), "tag row " + r);
        assertEquals(score, ((Number) rs.getRows().get(r).getValue(3)).longValue(), "score row " + r);
    }
}
