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
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A complex (non-aggregate) SELECT item over a grouped JOIN must resolve its qualified references
 * (s.col, o.col) against the joined-row layout. The representative-row evaluator used for such items
 * previously lacked the multi-table context, so on a LEFT-JOIN group whose right side is a miss
 * (null-padded) the whole item silently evaluated to NULL — while the same reference inside an
 * aggregate worked. Group keys referencing the item's alias were unaffected, which is what made the
 * output rows exist with a NULL where the computed value belonged.
 */
public class GroupByJoinItemResolutionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE readings (grp VARCHAR, node VARCHAR, label VARCHAR)");
        engine.execute("CREATE TABLE catalog_names (grp VARCHAR, node VARCHAR, label VARCHAR, family VARCHAR)");
        engine.execute(
            "INSERT INTO readings VALUES ('g','n1','alpha:beta:'), ('g','n2','SOLO_LABEL'), ('g','n3','OTHER')");
        engine.execute("INSERT INTO catalog_names VALUES ('g','n1','alpha:beta:','alpha')");
    }

    private Map<String, Object> byNode(final String sql, final int keyCol, final int valCol) {
        final ResultSet result = engine.executeQuery(sql);
        final Map<String, Object> out = new HashMap<>();
        for (final Row row : result.getRows()) {
            out.put((String) row.getValue(keyCol), row.getValue(valCol));
        }
        return out;
    }

    @Test
    public void complexItemResolvesLeftSideColumnsOnJoinMissGroups() {
        final Map<String, Object> got = byNode(
            """
            SELECT s.node, UPPER(s.label) AS u
            FROM readings s LEFT JOIN catalog_names o ON o.grp = s.grp AND o.node = s.node
            GROUP BY s.node, u
            """, 0, 1);
        assertEquals("ALPHA:BETA:", got.get("n1"));
        assertEquals("SOLO_LABEL", got.get("n2"));
        assertEquals("OTHER", got.get("n3"));
    }

    @Test
    public void caseOverBothSidesUsesElseBranchOnMissGroups() {
        final Map<String, Object> got = byNode(
            """
            SELECT s.node,
                   UPPER(CASE WHEN o.node IS NOT NULL AND ARRAY_SIZE(SPLIT(s.label, ':')) = 3
                              THEN o.family || ':' || o.label
                              ELSE s.label END) AS sid
            FROM readings s LEFT JOIN catalog_names o ON o.grp = s.grp AND o.node = s.node
            GROUP BY s.node, sid
            """, 0, 1);
        assertEquals("ALPHA:ALPHA:BETA:", got.get("n1"));
        assertEquals("SOLO_LABEL", got.get("n2"));
        assertEquals("OTHER", got.get("n3"));
    }

    @Test
    public void aliasInWhereFiltersBeforeGrouping() {
        final ResultSet result = engine.executeQuery(
            """
            SELECT s.node, UPPER(s.label) AS u
            FROM readings s LEFT JOIN catalog_names o ON o.grp = s.grp AND o.node = s.node
            WHERE u <> 'OTHER'
            GROUP BY s.node, u
            ORDER BY s.node
            """);
        assertEquals(2, result.getRows().size());
    }

    @Test
    public void rightSideColumnStaysNullOnMissGroups() {
        final Map<String, Object> got = byNode(
            """
            SELECT s.node, o.family AS fam, COUNT(*) AS c
            FROM readings s LEFT JOIN catalog_names o ON o.grp = s.grp AND o.node = s.node
            GROUP BY s.node, fam
            """, 0, 1);
        assertEquals("alpha", got.get("n1"));
        assertEquals(null, got.get("n2"));
    }
}
