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
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Metadata output uses plenty of names that are also keywords, and callers read those listings back
 * through {@code TABLE(RESULT_SCAN(LAST_QUERY_ID()))} by selecting columns by name. Each of these
 * names has to survive in that position, because a listing whose columns cannot be named is only
 * half readable.
 *
 * <p>Two names are deliberately NOT here: a real account reserves INCREMENT and ROWS, so
 * {@code INFORMATION_SCHEMA.SEQUENCES."increment"} and SHOW TABLES' {@code "rows"} have to be quoted
 * there too — accepting them bare would be a leniency live does not have.
 */
public class MetadataColumnNamesTest extends BaseDatabaseTest {

    /** Every keyword-shaped metadata column name a real account accepts unquoted. */
    private static final List<String> USABLE_NAMES = List.of(
        "ALLOWED_VALUES", "AUTOINCREMENT", "AUTO_RESUME", "AUTO_SUSPEND", "COMPRESSION",
        "DATE_FORMAT", "DEFAULT_ROLE", "ERROR_INTEGRATION", "ESCAPE", "FIELD_DELIMITER",
        "HANDLER", "INTEGRATION", "INTERVAL", "LANGUAGE", "MAX_CLUSTER_COUNT", "MIN_CLUSTER_COUNT",
        "RECORD_DELIMITER", "RELY", "RESOURCE_MONITOR", "RUNTIME_VERSION", "SCHEDULE",
        "SKIP_HEADER", "WAREHOUSE");

    @Test
    public void keywordShapedColumnNamesAreUsableAsIdentifiers() {
        for (final String name : USABLE_NAMES) {
            final ResultSet rs = engine.executeQuery(
                "SELECT " + name + " FROM (SELECT 1 AS " + name + ")");
            assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue(),
                name + " must be usable as a column name");
        }
    }

    /** INCREMENT and ROWS stay reserved, as they are on a real account. */
    @Test
    public void namesLiveReservesStayReserved() {
        for (final String reserved : List.of("INCREMENT", "ROWS")) {
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SELECT " + reserved + " FROM (SELECT 1 AS \"" + reserved + "\")");
                }
            }, reserved + " is reserved on a real account and must stay reserved here");
            // Quoting is the way to read them, in both engines.
            assertNotNull(engine.executeQuery(
                "SELECT \"" + reserved + "\" FROM (SELECT 1 AS \"" + reserved + "\")"));
        }
    }

    /** The read-back idiom: SHOW, then select the keyword-named columns off RESULT_SCAN. */
    @Test
    public void showColumnsAreSelectableByNameThroughResultScan() {
        engine.execute("CREATE WAREHOUSE name_probe_wh WAREHOUSE_SIZE = XSMALL");
        engine.execute("SHOW WAREHOUSES LIKE 'NAME_PROBE_WH'");
        // SHOW names its columns in lower case, so reading them back takes the quoted spelling —
        // an unquoted reference folds to upper case and does not find them.
        final ResultSet warehouses = engine.executeQuery(
            "SELECT \"auto_suspend\", \"auto_resume\", \"min_cluster_count\", \"max_cluster_count\","
            + " \"resource_monitor\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertEquals(1, warehouses.getRows().size());

        engine.execute("CREATE TABLE sched_sink (k INTEGER)");
        engine.execute("CREATE TASK name_probe_task WAREHOUSE = name_probe_wh"
            + " SCHEDULE = '60 MINUTE' AS INSERT INTO sched_sink VALUES (1)");
        engine.execute("SHOW TASKS LIKE 'NAME_PROBE_TASK'");
        final ResultSet tasks = engine.executeQuery(
            "SELECT \"schedule\", \"warehouse\", \"error_integration\""
            + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertEquals("60 MINUTE", tasks.getRows().get(0).getValue(0));
    }

    /** The same names have to work against INFORMATION_SCHEMA, whose columns are upper case. */
    @Test
    public void informationSchemaColumnsAreSelectableByName() {
        engine.execute("CREATE FUNCTION name_probe_fn(x INTEGER) RETURNS INTEGER AS $$ SELECT x $$");
        assertNotNull(engine.executeQuery(
            "SELECT handler, runtime_version FROM INFORMATION_SCHEMA.FUNCTIONS"));
        assertNotNull(engine.executeQuery("SELECT rely FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS"));
        // INCREMENT is a keyword the parser will not take bare, so this one needs its quotes — and
        // upper case ones, INFORMATION_SCHEMA naming its columns that way.
        assertNotNull(engine.executeQuery("SELECT \"INCREMENT\" FROM INFORMATION_SCHEMA.SEQUENCES"));
    }
}
