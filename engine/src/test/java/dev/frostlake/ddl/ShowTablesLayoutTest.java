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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SHOW TABLES emits live's full column layout — measured column for column on a real account —
 * including the feature-flag tail ({@code search_optimization*}, {@code is_hybrid},
 * {@code is_iceberg}, {@code is_dynamic}, …). The tail matters beyond display: real migration
 * scripts read it back through {@code TABLE(RESULT_SCAN(LAST_QUERY_ID()))}, e.g.
 * {@code MAX("is_hybrid")}, so the names must exist even where the feature is a constant here.
 */
public class ShowTablesLayoutTest extends BaseDatabaseTest {

    @Test
    public void showTablesCarriesLiveColumnLayout() {
        engine.execute("CREATE TABLE layout_probe (k INTEGER)");
        final ResultSet rs = engine.executeQuery("SHOW TABLES LIKE 'LAYOUT_PROBE'");
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn column : rs.getColumns()) {
            names.add(column.getName());
        }
        final List<String> expected = List.of(
            "created_on", "name", "database_name", "schema_name", "kind", "comment", "cluster_by",
            "rows", "bytes", "owner", "retention_time", "automatic_clustering", "change_tracking",
            "search_optimization", "search_optimization_progress", "search_optimization_bytes",
            "is_external", "enable_schema_evolution", "owner_role_type", "is_event", "is_hybrid",
            "is_iceberg", "is_dynamic", "is_immutable", "is_interactive", "row_timestamp",
            "error_logging");
        assertEquals(expected, names);
        assertEquals(1, rs.getRows().size());
    }

    /** The migration-script read pattern: SHOW TABLES → RESULT_SCAN → aggregate over a flag. */
    @Test
    public void isHybridReadsBackThroughResultScan() {
        engine.execute("CREATE TABLE rs_probe (k INTEGER)");
        engine.execute("SHOW TABLES LIKE 'RS_PROBE'");
        final ResultSet rs = engine.executeQuery(
            "SELECT COUNT(*), MAX(\"is_hybrid\") FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"
            + " WHERE \"name\" = 'RS_PROBE'");
        assertEquals(1, rs.getRows().size());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("N", String.valueOf(rs.getRows().get(0).getValue(1)));
    }

    @Test
    public void plainTableFlagValuesMatchLiveDefaults() {
        engine.execute("CREATE TABLE flags_probe (k INTEGER)");
        final ResultSet rs = engine.executeQuery("SHOW TABLES LIKE 'FLAGS_PROBE'");
        int isHybridIdx = -1;
        int ownerRoleTypeIdx = -1;
        int searchOptIdx = -1;
        final List<ResultSetColumn> columns = rs.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            if ("is_hybrid".equals(columns.get(i).getName())) {
                isHybridIdx = i;
            } else if ("owner_role_type".equals(columns.get(i).getName())) {
                ownerRoleTypeIdx = i;
            } else if ("search_optimization".equals(columns.get(i).getName())) {
                searchOptIdx = i;
            }
        }
        assertTrue(isHybridIdx >= 0 && ownerRoleTypeIdx >= 0 && searchOptIdx >= 0);
        assertEquals("N", rs.getRows().get(0).getValue(isHybridIdx));
        assertEquals("ROLE", rs.getRows().get(0).getValue(ownerRoleTypeIdx));
        assertEquals("OFF", rs.getRows().get(0).getValue(searchOptIdx));
    }
}
