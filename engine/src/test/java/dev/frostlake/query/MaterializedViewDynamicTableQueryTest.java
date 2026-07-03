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
 * MATERIALIZED VIEW and DYNAMIC TABLE are queryable: a reference in FROM materializes on read by
 * executing the object's defining query. Previously they were registered with no backing storage, so
 * SELECT … FROM &lt;mv&gt; returned nothing. Because they materialize on read, they reflect base changes.
 */
public class MaterializedViewDynamicTableQueryTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE WAREHOUSE wh");
        engine.execute("CREATE TABLE base (id INTEGER, amount INTEGER)");
        engine.execute("INSERT INTO base VALUES (1, 100), (2, 200), (3, 300)");
        engine.execute("CREATE MATERIALIZED VIEW mv AS SELECT id, amount FROM base WHERE amount >= 200");
        engine.execute("CREATE DYNAMIC TABLE dt TARGET_LAG='1 MINUTE' WAREHOUSE=wh "
            + "AS SELECT id, amount * 2 AS dbl FROM base");
    }

    private long scalar(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void selectFromMaterializedViewAppliesItsPredicate() {
        final ResultSet rs = engine.executeQuery("SELECT id, amount FROM mv ORDER BY id");
        assertEquals(2, rs.getRowCount());
        assertEquals(200, ((Number) rs.getRows().get(0).getValue(1)).intValue());
        assertEquals(300, ((Number) rs.getRows().get(1).getValue(1)).intValue());
    }

    @Test
    public void aggregateOverMaterializedView() {
        assertEquals(2L, scalar("SELECT COUNT(*) FROM mv"));
    }

    @Test
    public void materializedViewReflectsBaseChanges() {
        assertEquals(2L, scalar("SELECT COUNT(*) FROM mv"));
        engine.execute("INSERT INTO base VALUES (4, 400)");
        assertEquals(3L, scalar("SELECT COUNT(*) FROM mv"));
    }

    @Test
    public void selectFromDynamicTableComputesColumns() {
        final ResultSet rs = engine.executeQuery("SELECT id, dbl FROM dt ORDER BY id");
        assertEquals(3, rs.getRowCount());
        assertEquals(200, ((Number) rs.getRows().get(0).getValue(1)).intValue()); // 100 * 2
        assertEquals(600, ((Number) rs.getRows().get(2).getValue(1)).intValue()); // 300 * 2
    }

    @Test
    public void filterOverDynamicTable() {
        assertEquals(2L, scalar("SELECT COUNT(*) FROM dt WHERE dbl > 300"));
    }
}
