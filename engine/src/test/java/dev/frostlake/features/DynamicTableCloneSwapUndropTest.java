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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * CREATE DYNAMIC TABLE ... CLONE, UNDROP DYNAMIC TABLE and the ALTER DYNAMIC TABLE actions SWAP WITH,
 * SUSPEND | RESUME RECLUSTER and SET | UNSET TAG, and DESCRIBE DYNAMIC TABLE of a qualified name.
 */
public class DynamicTableCloneSwapUndropTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS wh_dtc WITH WAREHOUSE_SIZE = 'XSMALL' INITIALLY_SUSPENDED = TRUE");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE SCHEMA IF NOT EXISTS test_db.other_schema");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE TABLE src (x INT, y VARCHAR)");
        engine.execute("INSERT INTO src VALUES (1, 'a'), (2, 'b')");
        engine.execute("CREATE DYNAMIC TABLE dt_a TARGET_LAG = '1 hour' WAREHOUSE = wh_dtc CLUSTER BY (x) "
            + "COMMENT = 'first' AS SELECT x, y FROM test_db.test_schema.src");
        engine.execute("CREATE DYNAMIC TABLE dt_b TARGET_LAG = DOWNSTREAM WAREHOUSE = wh_dtc AS SELECT x FROM test_db.test_schema.src");
    }

    @Override
    protected void teardownTest() {
        engine.execute("DROP WAREHOUSE IF EXISTS wh_dtc");
    }

    /** One dynamic table's SHOW DYNAMIC TABLES cells, through a pipe. */
    private List<Object> cells(final String scope, final String name, final String columns) {
        final ResultSet rs = engine.executeQuery("SHOW DYNAMIC TABLES LIKE '" + name + "' IN SCHEMA " + scope
            + " ->> SELECT " + columns + " FROM $1");
        assertEquals(1, rs.getRows().size(), name + " is listed once");
        return rs.getRows().get(0).getValues();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void aCloneTakesTheDefinitionAndStartsSuspended() {
        engine.execute("CREATE DYNAMIC TABLE test_db.other_schema.dt_c CLONE dt_a AT (OFFSET => -1) COPY GRANTS "
            + "TARGET_LAG = '2 hours'");
        assertEquals(List.of("LINEAR(x)", "2 hours", "WH_DTC", "first", "SUSPENDED", "OFF", "true"),
            cells("test_db.other_schema", "DT_C", "\"cluster_by\", \"target_lag\", \"warehouse\", \"comment\", "
                + "\"scheduling_state\", \"automatic_clustering\", \"is_clone\""));
        assertEquals(2L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM test_db.other_schema.dt_c")
            .getRows().get(0).getValue(0)).longValue());
        assertTrue(refusal("CREATE DYNAMIC TABLE dt_b CLONE dt_a").contains("Object 'DT_B' already exists."));
        engine.execute("CREATE DYNAMIC TABLE IF NOT EXISTS dt_b CLONE dt_a");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dt_b CLONE dt_a WAREHOUSE = wh_dtc");
        assertEquals(List.of("LINEAR(x)"), cells("test_db.test_schema", "DT_B", "\"cluster_by\""));
        assertTrue(refusal("CREATE DYNAMIC TABLE dt_d CLONE nosuch")
            .contains("Object 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized."));
    }

    @Test
    public void aDroppedDynamicTableIsUndropped() {
        engine.execute("DROP DYNAMIC TABLE dt_a");
        assertEquals(0, engine.executeQuery("SHOW DYNAMIC TABLES LIKE 'DT_A'").getRows().size());
        engine.execute("UNDROP DYNAMIC TABLE test_db.test_schema.dt_a");
        assertEquals(List.of("LINEAR(x)", "first"), cells("test_db.test_schema", "DT_A", "\"cluster_by\", \"comment\""));
        assertTrue(refusal("UNDROP DYNAMIC TABLE dt_a").contains("Object 'DT_A' already exists."));
        assertEquals("Dynamic_table DT_NEVER did not exist or was purged.", refusal("UNDROP DYNAMIC TABLE dt_never"));
    }

    @Test
    public void swapWithExchangesTheNames() {
        engine.execute("ALTER DYNAMIC TABLE dt_a SWAP WITH dt_b");
        assertEquals(List.of("DOWNSTREAM", ""), cells("test_db.test_schema", "DT_A", "\"target_lag\", \"cluster_by\""));
        assertEquals(List.of("1 hour", "LINEAR(x)"), cells("test_db.test_schema", "DT_B",
            "\"target_lag\", \"cluster_by\""));
        assertTrue(refusal("ALTER DYNAMIC TABLE dt_a SWAP WITH nosuch").contains("does not exist or not authorized"));
    }

    @Test
    public void reclusteringIsSuspendedAndResumedOnAClusteredTable() {
        engine.execute("ALTER DYNAMIC TABLE dt_a SUSPEND RECLUSTER");
        assertEquals(List.of("OFF"), cells("test_db.test_schema", "DT_A", "\"automatic_clustering\""));
        engine.execute("ALTER DYNAMIC TABLE dt_a RESUME RECLUSTER");
        assertEquals(List.of("ON"), cells("test_db.test_schema", "DT_A", "\"automatic_clustering\""));
        assertEquals("Table 'DT_B' is not clustered\n", refusal("ALTER DYNAMIC TABLE dt_b SUSPEND RECLUSTER"));
    }

    @Test
    public void tagsAreSetAndUnset() {
        engine.execute("CREATE TAG dt_tag");
        engine.execute("ALTER DYNAMIC TABLE dt_a SET TAG dt_tag = 'gold'");
        final ResultSet tags = engine.executeQuery(
            "SELECT tag_name, tag_value FROM TABLE(test_db.INFORMATION_SCHEMA.TAG_REFERENCES('dt_a', 'TABLE'))");
        assertEquals(List.of(List.of("DT_TAG", "gold")), List.of(tags.getRows().get(0).getValues()));
        engine.execute("ALTER DYNAMIC TABLE dt_a UNSET TAG dt_tag");
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM TABLE(test_db.INFORMATION_SCHEMA.TAG_REFERENCES('dt_a', 'TABLE'))").getRows().size());
    }

    @Test
    public void describeReachesAQualifiedName() {
        engine.execute("USE SCHEMA test_db.other_schema");
        final ResultSet described = engine.executeQuery("DESCRIBE DYNAMIC TABLE test_db.test_schema.dt_a");
        assertEquals(2, described.getRows().size());
        assertEquals("X", described.getRows().get(0).getValue(0));
        assertTrue(refusal("DESCRIBE DYNAMIC TABLE test_db.test_schema.nosuch")
            .contains("Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized."));
    }
}
