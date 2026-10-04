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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CREATE DATABASE/SCHEMA … CLONE copies every object of the container and every table in full. The schema's clone
 * rebuilt its tables from a few column attributes and copied several table properties only when a row access policy
 * was attached. It skipped tags, dynamic tables, materialized views and secrets. Policy comments and function
 * MEMOIZABLE were dropped, and tasks lost AFTER, WHEN and their settings while staying started. Pipes kept running,
 * even those on an internal stage, which a clone does not take (documented).
 */
public class NamespaceCloneKeepsEverythingTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE d1");
        engine.execute("USE SCHEMA d1.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** A query's rows, each one's values joined by '|', and the rows joined by " ; ". */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " ; " : "");
            for (int i = 0; i < row.size(); i++) {
                out.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** One column of a listing's rows, joined by " ; ". */
    private String listed(final String sql, final String column) {
        final ResultSet listing = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (listing.next()) {
            out.append(out.length() > 0 ? " ; " : "").append(listing.getValue(column));
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void tablesKeepTheirWholeDefinition() {
        engine.execute("CREATE PROJECTION POLICY pjp AS () RETURNS PROJECTION_CONSTRAINT"
            + " -> PROJECTION_CONSTRAINT(ALLOW => false)");
        engine.execute("CREATE AGGREGATION POLICY agp AS () RETURNS AGGREGATION_CONSTRAINT"
            + " -> AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 2) COMMENT = 'agg c'");
        engine.execute("CREATE TABLE parent (id INT NOT NULL, CONSTRAINT pk_parent PRIMARY KEY (id))");
        engine.execute("CREATE TABLE t (id INT IDENTITY(100, 10), drop_me INT, p_id INT,"
            + " b VARCHAR PROJECTION POLICY pjp, CONSTRAINT fk_p FOREIGN KEY (p_id) REFERENCES parent (id) RELY,"
            + " CONSTRAINT ck CHECK (p_id > 0)) DATA_RETENTION_TIME_IN_DAYS = 5 CHANGE_TRACKING = TRUE");
        engine.execute("ALTER TABLE t DROP COLUMN drop_me");
        engine.execute("ALTER TABLE t ADD SEARCH OPTIMIZATION ON EQUALITY(b)");
        engine.execute("CREATE TABLE agg (a INT) WITH AGGREGATION POLICY agp");
        engine.execute("CREATE FUNCTION f_memo() RETURNS INT MEMOIZABLE AS 'SELECT 1'");
        engine.execute("INSERT INTO t (p_id, b) VALUES (1, 'x')");
        engine.execute("CREATE DATABASE d2 CLONE d1");
        engine.execute("INSERT INTO d2.PUBLIC.t (p_id, b) VALUES (2, 'y')");

        assertEquals("100 ; 110", rows("SELECT id FROM d2.PUBLIC.t ORDER BY id"), "IDENTITY continues");
        assertEquals("FK_P ; PK_PARENT", rows("SELECT CONSTRAINT_NAME FROM d2.INFORMATION_SCHEMA.TABLE_CONSTRAINTS"
            + " ORDER BY 1"), "constraint names kept");
        assertEquals("ID|1 ; P_ID|3 ; B|4", rows("SELECT COLUMN_NAME, ORDINAL_POSITION FROM"
            + " d2.INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'T' ORDER BY 2"), "positions keep their gaps");
        assertTrue(refusal("SELECT b FROM d2.PUBLIC.t").contains("restricted by a Projection Policy"));
        assertTrue(refusal("INSERT INTO d2.PUBLIC.t (p_id, b) VALUES (-1, 'z')").contains("CHECK constraint CK"));
        assertEquals("SQL compilation error: Aggregation policy violation: aggregation required.",
            refusal("SELECT * FROM d2.PUBLIC.agg"), "kept without a row access policy too");
        assertTrue(rows("DESCRIBE SEARCH OPTIMIZATION ON d2.PUBLIC.t").startsWith("1|EQUALITY|B"));
        assertEquals("5", listed("SHOW TABLES LIKE 'T' IN DATABASE d2", "retention_time"));
        assertEquals("ON", listed("SHOW TABLES LIKE 'T' IN DATABASE d2", "change_tracking"));
        assertEquals("agg c", listed("SHOW AGGREGATION POLICIES IN DATABASE d2", "comment"), "policy comment kept");
        assertEquals("YES", rows("SELECT IS_MEMOIZABLE FROM d2.INFORMATION_SCHEMA.FUNCTIONS"
            + " WHERE FUNCTION_NAME = 'F_MEMO'"));
    }

    @Test
    public void tagsDynamicTablesMaterializedViewsAndSecretsAreCloned() {
        engine.execute("CREATE TAG pii ALLOWED_VALUES 'a', 'b'");
        engine.execute("CREATE TABLE t (a INT, b VARCHAR)");
        engine.execute("ALTER TABLE t SET TAG pii = 'a'");
        engine.execute("ALTER TABLE t MODIFY COLUMN b SET TAG pii = 'b'");
        engine.execute("INSERT INTO t VALUES (1, 'x'), (2, 'y')");
        engine.execute("CREATE DYNAMIC TABLE dt TARGET_LAG = '1 minute' WAREHOUSE = COMPUTE_WH AS SELECT a FROM t");
        engine.execute("CREATE MATERIALIZED VIEW mv AS SELECT a FROM t");
        engine.execute("CREATE SECRET sec TYPE = GENERIC_STRING SECRET_STRING = 'abc'");
        engine.execute("CREATE DATABASE d2 CLONE d1");

        assertEquals("PII", listed("SHOW TAGS IN DATABASE d2", "name"));
        assertEquals("a", rows("SELECT SYSTEM$GET_TAG('pii', 'd2.PUBLIC.t', 'TABLE')"));
        assertEquals("b", rows("SELECT SYSTEM$GET_TAG('pii', 'd2.PUBLIC.t.b', 'COLUMN')"));
        assertEquals("2", rows("SELECT COUNT(*) FROM d2.PUBLIC.dt"));
        assertEquals("2", rows("SELECT COUNT(*) FROM d2.PUBLIC.mv"));
        assertEquals("SEC", listed("SHOW SECRETS IN DATABASE d2", "name"));
    }

    @Test
    public void tasksKeepTheirGraphSuspendedAndPipesStayStopped() {
        engine.execute("CREATE TABLE src (id INT)");
        engine.execute("CREATE STREAM str ON TABLE src");
        engine.execute("CREATE TASK root_t WAREHOUSE = COMPUTE_WH SCHEDULE = '5 MINUTE' CONFIG = $${\"env\": \"qa\"}$$"
            + " SUSPEND_TASK_AFTER_NUM_FAILURES = 3 AS SELECT 1");
        engine.execute("CREATE TASK child_t WAREHOUSE = COMPUTE_WH AFTER root_t"
            + " WHEN SYSTEM$STREAM_HAS_DATA('STR') AS SELECT 2");
        engine.execute("ALTER TASK child_t RESUME");
        engine.execute("CREATE STAGE ext_stage URL = 's3://bucket/path'");
        engine.execute("CREATE STAGE int_stage");
        engine.execute("CREATE PIPE p_ext AS COPY INTO src FROM @ext_stage");
        engine.execute("CREATE PIPE p_int AS COPY INTO src FROM @int_stage");
        engine.execute("CREATE DATABASE d2 CLONE d1");

        final String child = rows("SELECT GET_DDL('TASK', 'd2.PUBLIC.child_t')");
        assertTrue(child.contains("after D2.PUBLIC.ROOT_T"), child);
        assertTrue(child.contains("when SYSTEM$STREAM_HAS_DATA('STR')"), child);
        final String root = rows("SELECT GET_DDL('TASK', 'd2.PUBLIC.root_t')");
        assertTrue(root.contains("config='{\"env\": \"qa\"}'"), root);
        assertTrue(root.contains("SUSPEND_TASK_AFTER_NUM_FAILURES=3"), root);
        assertEquals("suspended ; suspended", listed("SHOW TASKS IN DATABASE d2", "state"));
        assertEquals("started", listed("SHOW TASKS LIKE 'CHILD_T' IN DATABASE d1", "state"), "the source stays started");
        assertEquals("P_EXT", listed("SHOW PIPES IN DATABASE d2", "name"), "a pipe on an internal stage is not cloned");
        assertTrue(rows("SELECT SYSTEM$PIPE_STATUS('d2.PUBLIC.p_ext')").contains("\"executionState\": \"PAUSED\""));
        assertFalse(rows("SELECT SYSTEM$PIPE_STATUS('d1.PUBLIC.p_ext')").contains("PAUSED"));
    }
}
