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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips the schema-level catalog objects that snapshot persistence previously dropped — sequences,
 * streams, tasks, masking policies (and their column bindings), and file formats. Each test writes the
 * object with one engine, lets {@code shutdown()} persist, then opens a second engine over the same data
 * directory and verifies the object (and its durable state) was restored.
 */
public class PersistenceExtrasTest {

    private EngineConfig config;
    private Path dataDir;

    @BeforeEach
    public void setUp() throws IOException {
        dataDir = Files.createTempDirectory("persist_extras_");
        config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, dataDir.toString());
        config.setProperty(EngineConfig.PROP_PERSISTENCE_AUTO_SAVE, "false");
    }

    @AfterEach
    public void tearDown() {
        if (dataDir != null) {
            deleteRecursively(dataDir.toFile());
        }
    }

    @Test
    public void sequenceCurrentValueSurvives() {
        final DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("CREATE SEQUENCE seq START WITH 1 INCREMENT BY 1");
        assertEquals(1L, nextval(engine1));
        assertEquals(2L, nextval(engine1));
        engine1.shutdown();

        // A fresh engine must continue from 2 (NEXTVAL -> 3), not reset to the start value.
        final DatabaseEngine engine2 = new DatabaseEngine(config);
        engine2.execute("USE DATABASE test_db");
        assertEquals(3L, nextval(engine2), "sequence current value must survive a reload");
        engine2.shutdown();
    }

    @Test
    public void streamSurvives() {
        final DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine1.execute("CREATE STREAM user_stream ON TABLE users");
        engine1.shutdown();

        final DatabaseEngine engine2 = new DatabaseEngine(config);
        engine2.execute("USE DATABASE test_db");
        final ResultSet streams = engine2.executeQuery("SHOW STREAMS");
        assertTrue(rowsContain(streams, "USER_STREAM"), "stream USER_STREAM must be persisted");
        engine2.shutdown();
    }

    @Test
    public void taskAndStartedStateSurvive() {
        final DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("""
            CREATE TASK test_task
            WAREHOUSE = 'COMPUTE_WH'
            SCHEDULE = '5 MINUTES'
            AS SELECT 1
            """);
        engine1.execute("ALTER TASK test_task RESUME");
        engine1.shutdown();

        final DatabaseEngine engine2 = new DatabaseEngine(config);
        engine2.execute("USE DATABASE test_db");
        final ResultSet tasks = engine2.executeQuery("SHOW TASKS");
        assertTrue(rowsContain(tasks, "TEST_TASK", "STARTED"),
            "task TEST_TASK must be persisted with its STARTED state");
        engine2.shutdown();
    }

    @Test
    public void maskingPolicyAndColumnBindingSurvive() {
        final DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("USE SCHEMA public");
        engine1.execute("CREATE TABLE employees (id INTEGER, salary DOUBLE)");
        engine1.execute("CREATE MASKING POLICY salary_mask AS (val DOUBLE) RETURNS DOUBLE -> IFF(CURRENT_ROLE() = 'HR_ADMIN', val, -1)");
        engine1.execute("ALTER TABLE employees ALTER COLUMN salary SET MASKING POLICY salary_mask");
        engine1.shutdown();

        final DatabaseEngine engine2 = new DatabaseEngine(config);
        final MaskingPolicy policy = engine2.getCatalog()
            .getDatabase("TEST_DB").getSchema("PUBLIC").getMaskingPolicy("SALARY_MASK");
        assertNotNull(policy, "masking policy must be persisted");
        assertEquals(1, policy.getParameters().size());

        final TableColumn col = engine2.getCatalog()
            .getDatabase("TEST_DB").getSchema("PUBLIC").getTable("EMPLOYEES").getColumn("salary");
        assertTrue(col.hasMaskingPolicy(), "the policy-to-column binding must be persisted");
        // Attachments record the policy in full, the way live reports one.
        assertEquals("TEST_DB.PUBLIC.SALARY_MASK", col.getMaskingPolicyName());
        engine2.shutdown();
    }

    @Test
    public void fileFormatSurvives() {
        final DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("CREATE FILE FORMAT my_csv TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 1");
        engine1.shutdown();

        final DatabaseEngine engine2 = new DatabaseEngine(config);
        engine2.execute("USE DATABASE test_db");
        final ResultSet formats = engine2.executeQuery("SHOW FILE FORMATS");
        assertTrue(rowsContain(formats, "MY_CSV"), "file format MY_CSV must be persisted");
        engine2.shutdown();
    }

    // ── objects previously DROPPED by the snapshot (lost after a WAL checkpoint + restart) ─────────

    @Test
    public void sqlFunctionSurvivesAndExecutes() {
        final DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("CREATE FUNCTION double_it(x INTEGER) RETURNS INTEGER LANGUAGE SQL"
            + " AS 'x * 2'");
        engine1.shutdown();

        final DatabaseEngine engine2 = new DatabaseEngine(config);
        engine2.execute("USE DATABASE test_db");
        final ResultSet rs = engine2.executeQuery("SELECT double_it(21)");
        assertEquals(42L, ((Number) rs.getRows().get(0).getValue(0)).longValue(),
            "restored UDF must execute");
        engine2.shutdown();
    }

    @Test
    public void procedureSurvivesAndExecutes() {
        final DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("CREATE PROCEDURE greet() RETURNS VARCHAR LANGUAGE SQL"
            + " AS $$ BEGIN RETURN 'alive'; END $$");
        engine1.shutdown();

        final DatabaseEngine engine2 = new DatabaseEngine(config);
        engine2.execute("USE DATABASE test_db");
        final ResultSet rs = engine2.executeQuery("CALL greet()");
        assertEquals("alive", rs.getRows().get(0).getValue(0), "restored procedure must execute");
        engine2.shutdown();
    }

    @Test
    public void pipeSurvives() {
        final DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("CREATE TABLE target_table (a VARCHAR)");
        engine1.execute("CREATE STAGE my_stage URL='s3://bucket/data'");
        engine1.execute("CREATE PIPE my_pipe AS COPY INTO target_table FROM @my_stage"
            + " FILE_FORMAT = (TYPE = 'CSV')");
        engine1.shutdown();

        final DatabaseEngine engine2 = new DatabaseEngine(config);
        engine2.execute("USE DATABASE test_db");
        assertTrue(rowsContain(engine2.executeQuery("SHOW PIPES"), "MY_PIPE"),
            "pipe MY_PIPE must be persisted");
        engine2.shutdown();
    }

    @Test
    public void dynamicTableSurvives() {
        final DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("CREATE TABLE source (id INTEGER, val INTEGER)");
        engine1.execute("CREATE WAREHOUSE wh1");
        engine1.execute("CREATE DYNAMIC TABLE sales_agg TARGET_LAG = '1 minutes' WAREHOUSE = wh1"
            + " AS SELECT id, val FROM source");
        engine1.shutdown();

        final DatabaseEngine engine2 = new DatabaseEngine(config);
        engine2.execute("USE DATABASE test_db");
        assertTrue(rowsContain(engine2.executeQuery("SHOW DYNAMIC TABLES"), "SALES_AGG"),
            "dynamic table SALES_AGG must be persisted");
        engine2.shutdown();
    }

    @Test
    public void rowAccessPolicyAndTableBindingSurvive() {
        final DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("CREATE TABLE employees (id INTEGER, department VARCHAR)");
        engine1.execute("CREATE ROW ACCESS POLICY dept_policy AS (dept VARCHAR) RETURNS BOOLEAN"
            + " -> CURRENT_ROLE() = 'ADMIN'");
        engine1.execute("ALTER TABLE employees ADD ROW ACCESS POLICY dept_policy ON (department)");
        engine1.shutdown();

        final DatabaseEngine engine2 = new DatabaseEngine(config);
        assertEquals("DEPT_POLICY", engine2.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC")
            .getRowAccessPolicy("DEPT_POLICY").getName(), "policy definition must be persisted");
        assertEquals("DEPT_POLICY", engine2.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC")
            .getTable("EMPLOYEES").getRowAccessPolicyName().toUpperCase(),
            "policy-to-table binding must be persisted");
        engine2.shutdown();
    }

    @Test
    public void tagWithAllowedValuesSurvives() {
        final DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("CREATE TAG classification ALLOWED_VALUES 'PII', 'PUBLIC'");
        engine1.shutdown();

        final DatabaseEngine engine2 = new DatabaseEngine(config);
        engine2.execute("USE DATABASE test_db");
        assertEquals(2, engine2.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC")
            .getTag("CLASSIFICATION").getAllowedValues().size(),
            "tag definition incl. allowed values must be persisted");
        engine2.shutdown();
    }

    @Test
    public void metastoreDefinitionAndRowDataSurviveTogether() {
        final DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("USE SCHEMA public");
        engine1.execute("CREATE TABLE kv (k INTEGER, v VARCHAR)");
        engine1.execute("INSERT INTO kv VALUES (1, 'one'), (2, 'two')");
        engine1.shutdown();

        final DatabaseEngine engine2 = new DatabaseEngine(config);

        // Metastore: the table definition (column names + types) is restored from catalog.dat.
        final List<TableColumn> cols = engine2.getCatalog()
            .getDatabase("TEST_DB").getSchema("PUBLIC").getTable("KV").getColumns();
        assertEquals(2, cols.size(), "table definition must be persisted");
        assertEquals("K", cols.get(0).getName().toUpperCase());
        assertEquals("V", cols.get(1).getName().toUpperCase());
        assertNotNull(cols.get(0).getDataType());
        assertNotNull(cols.get(1).getDataType());

        // Data: the rows are restored from tables/TEST_DB_PUBLIC_KV.dat.
        engine2.execute("USE DATABASE test_db");
        final ResultSet rs = engine2.executeQuery("SELECT k, v FROM kv ORDER BY k");
        assertEquals(2, rs.getRows().size(), "row data must be persisted");
        assertEquals("one", rs.getRows().get(0).getValue(1).toString());
        assertEquals("two", rs.getRows().get(1).getValue(1).toString());
        engine2.shutdown();
    }

    private long nextval(final DatabaseEngine engine) {
        final ResultSet rs = engine.executeQuery("SELECT seq.NEXTVAL");
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    /** True if some row in the result has, across its columns, a string value equal (ignoring case) to
     *  every one of the required tokens — robust to the exact SHOW column names/ordering. */
    private static boolean rowsContain(final ResultSet rs, final String... required) {
        for (final Row row : rs.getRows()) {
            boolean allPresent = true;
            for (final String token : required) {
                boolean found = false;
                for (final Object value : row.getValues()) {
                    if (value != null && token.equalsIgnoreCase(value.toString())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    allPresent = false;
                    break;
                }
            }
            if (allPresent) {
                return true;
            }
        }
        return false;
    }

    private static void deleteRecursively(final File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            final File[] children = f.listFiles();
            if (children != null) {
                for (final File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        f.delete();
    }
}
