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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A materialized view is a VIEW to the listings: SHOW VIEWS lists it with {@code is_materialized} true
 * and SHOW OBJECTS with kind VIEW, while SHOW TABLES leaves it out. All three list their relations in
 * NAME order across the kinds, not one kind after another. Live-verified.
 */
public class MaterializedViewListingTest extends BaseDatabaseTest {

    /** Names ordered so that listing kind-by-kind and listing by name give different answers. */
    private void createMixedSchema() {
        engine.execute("CREATE OR REPLACE TABLE zt (x INT, y INT)");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW a_mv AS SELECT x FROM zt");
        engine.execute("CREATE OR REPLACE VIEW m_view AS SELECT x FROM zt");
        engine.execute("CREATE OR REPLACE VIEW b_view AS SELECT x FROM zt");
    }

    /** One column of a listing, joined by '|', in the order the listing returned it. */
    private String column(final String show, final String columnName) {
        final ResultSet rs = engine.executeQuery(show);
        final int index = rs.getColumnIndex(columnName);
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < rs.getRowCount(); i++) {
            if (i > 0) {
                text.append('|');
            }
            final Object value = rs.getRows().get(i).getValue(index);
            text.append(value == null ? "NULL" : value.toString());
        }
        return text.toString();
    }

    /** The scope this test's schema is named by. */
    private String scope() {
        return "IN SCHEMA " + currentDatabase() + ".test_schema";
    }

    /** The current database name, as the fixture created it. */
    private String currentDatabase() {
        final ResultSet rs = engine.executeQuery("SELECT CURRENT_DATABASE()");
        return rs.getRows().get(0).getValue(0).toString();
    }

    /** SHOW VIEWS lists a materialized view, flagged, in name order with the plain ones. */
    @Test
    public void showViewsListsMaterializedViews() {
        createMixedSchema();
        assertEquals("A_MV|B_VIEW|M_VIEW", column("SHOW VIEWS " + scope(), "name"));
        assertEquals("true|false|false", column("SHOW VIEWS " + scope(), "is_materialized"));
    }

    /** Its other cells read as a view's do. */
    @Test
    public void aMaterializedViewsCellsReadAsAViewsDo() {
        createMixedSchema();
        assertEquals("false|false|false", column("SHOW VIEWS " + scope(), "is_secure"));
        assertEquals("OFF|OFF|OFF", column("SHOW VIEWS " + scope(), "change_tracking"));
        final ResultSet rs = engine.executeQuery("SHOW VIEWS " + scope());
        assertEquals("CREATE OR REPLACE MATERIALIZED VIEW a_mv AS SELECT x FROM zt",
            rs.getRows().get(0).getValue(rs.getColumnIndex("text")).toString());
    }

    /** SHOW OBJECTS lists it with kind VIEW, in name order with everything else. */
    @Test
    public void showObjectsListsItAsAView() {
        createMixedSchema();
        assertEquals("A_MV|B_VIEW|M_VIEW|ZT", column("SHOW OBJECTS " + scope(), "name"));
        assertEquals("VIEW|VIEW|VIEW|TABLE", column("SHOW OBJECTS " + scope(), "kind"));
    }

    /** SHOW TABLES leaves it out; SHOW MATERIALIZED VIEWS lists it alone. */
    @Test
    public void theKindSpecificListingsStaySeparate() {
        createMixedSchema();
        assertEquals("ZT", column("SHOW TABLES " + scope(), "name"));
        assertEquals("A_MV", column("SHOW MATERIALIZED VIEWS " + scope(), "name"));
    }

    /** SHOW COLUMNS IN SCHEMA orders by relation name across the three kinds. */
    @Test
    public void showColumnsOrdersByRelationName() {
        createMixedSchema();
        assertEquals("A_MV|B_VIEW|M_VIEW|ZT|ZT",
            column("SHOW COLUMNS " + scope(), "table_name"));
        assertEquals("X|X|X|X|Y", column("SHOW COLUMNS " + scope(), "column_name"));
    }
}
