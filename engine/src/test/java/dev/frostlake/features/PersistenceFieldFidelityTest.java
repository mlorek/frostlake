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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.ConstraintNames;
import dev.frostlake.metastore.model.CortexSearchService;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.ScalingPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.metastore.model.User;
import dev.frostlake.metastore.model.Warehouse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips catalog model FIELDS that snapshot persistence previously dropped — silently flipping
 * security-relevant state (SECURE VIEW, disabled user, READ ONLY database) or losing constraint/scaling
 * metadata (UNIQUE, IDENTITY, collation, foreign keys, TRANSIENT, CLUSTER BY, warehouse scaling, ownership,
 * column-level grants) across a save/reload. Each test mutates state with one engine, lets {@code shutdown()}
 * persist, then opens a second engine over the same directory and asserts the field survived.
 */
public class PersistenceFieldFidelityTest {

    private EngineConfig config;
    private Path dataDir;

    @BeforeEach
    public void setUp() throws IOException {
        dataDir = Files.createTempDirectory("persist_fields_");
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

    /** A fresh engine over the shared data directory with an empty test_db.test_schema in context. */
    private DatabaseEngine freshEngine() {
        final DatabaseEngine engine = new DatabaseEngine(config);
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA IF NOT EXISTS test_schema");
        engine.execute("USE SCHEMA test_schema");
        return engine;
    }

    /** A second engine over the same directory — its constructor restores the persisted catalog. */
    private DatabaseEngine reopenEngine() {
        final DatabaseEngine engine = new DatabaseEngine(config);
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA test_schema");
        return engine;
    }

    private Schema schema(final DatabaseEngine engine) {
        return engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
    }

    /** A Cortex search service is definition-only, and every part of that definition survives a reload. */
    @Test
    public void cortexSearchServiceSurvivesAReload() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE TABLE docs(id INT, body VARCHAR, cat VARCHAR)");
        // The service NAMES this warehouse, and a named warehouse must exist (live-verified).
        engine1.execute("CREATE WAREHOUSE IF NOT EXISTS wh");
        engine1.execute("""
            CREATE CORTEX SEARCH SERVICE persist_svc
              ON body ATTRIBUTES cat
              WAREHOUSE = wh TARGET_LAG = '30 minutes'
              EMBEDDING_MODEL = 'snowflake-arctic-embed-l-v2.0'
              COMMENT = 'kept'
              AS (SELECT id, body, cat FROM docs)
            """);
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        final CortexSearchService service = schema(engine2).getCortexSearchService("PERSIST_SVC");
        assertEquals("BODY", service.getSearchColumn());
        assertEquals(List.of("CAT"), service.getAttributeColumns());
        assertEquals(List.of("ID", "BODY", "CAT"), service.getColumns());
        assertEquals("WH", service.getWarehouse());
        assertEquals("30 minutes", service.getTargetLag());
        assertEquals("snowflake-arctic-embed-l-v2.0", service.getEmbeddingModel());
        assertEquals("kept", service.getComment());
        assertTrue(service.getDefinition().contains("FROM docs"));
        engine2.shutdown();
    }

    /**
     * The CREATE USER property set survives a save/reload — it was dropped entirely before, so a
     * reopened engine reported a user with none of the details they were created with.
     */
    @Test
    public void userPropertiesSurviveAReload() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE USER persist_u LOGIN_NAME = 'ln' DISPLAY_NAME = 'dn'"
            + " FIRST_NAME = 'F' MIDDLE_NAME = 'M' LAST_NAME = 'L' EMAIL = 'e@x.com'"
            + " DEFAULT_WAREHOUSE = 'WH1' DEFAULT_NAMESPACE = 'test_db.test_schema'"
            + " MUST_CHANGE_PASSWORD = TRUE COMMENT = 'c'");
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        final User user = engine2.getCatalog().getUser("PERSIST_U");
        assertEquals("LN", user.getLoginName());
        assertEquals("dn", user.getDisplayName());
        assertEquals("F", user.getFirstName());
        assertEquals("M", user.getMiddleName());
        assertEquals("L", user.getLastName());
        assertEquals("e@x.com", user.getEmail());
        assertEquals("WH1", user.getDefaultWarehouse());
        assertEquals("TEST_DB.TEST_SCHEMA", user.getDefaultNamespace());
        assertTrue(user.isMustChangePassword());
        // PERSON, because the name properties above are person-only: a LEGACY_SERVICE user may
        // keep a password but not a first/middle/last name (live-verified).
        assertEquals("PERSON", user.getUserType());
        assertEquals("c", user.getComment());
        engine2.shutdown();
    }

    /**
     * An UNSET property stays unset across a reload. This is what the snapshot's written-marker is
     * for: the group is restored VERBATIM, so a null display name is not mistaken for a snapshot
     * that predates the field and quietly refilled with the user's own name.
     */
    @Test
    public void anUnsetUserPropertyStaysUnset() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE USER persist_u DISPLAY_NAME = 'dn' FIRST_NAME = 'F'");
        engine1.execute("ALTER USER persist_u UNSET DISPLAY_NAME, FIRST_NAME");
        assertNull(engine1.getCatalog().getUser("PERSIST_U").getDisplayName(),
            "precondition: display name is unset");
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        final User user = engine2.getCatalog().getUser("PERSIST_U");
        assertNull(user.getDisplayName(), "an unset display name must not come back as the name");
        assertNull(user.getFirstName());
        engine2.shutdown();
    }

    /** A user created without a display name still shows their own name after a reload. */
    @Test
    public void theDisplayNameDefaultSurvivesAReload() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE USER persist_u");
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        assertEquals("PERSIST_U", engine2.getCatalog().getUser("PERSIST_U").getDisplayName());
        engine2.shutdown();
    }

    @Test
    public void secureViewStaysSecure() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE SECURE VIEW sv AS SELECT 1 AS x");
        assertTrue(schema(engine1).getView("sv").isSecure(), "precondition: created view is secure");
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        assertTrue(schema(engine2).getView("sv").isSecure(), "SECURE VIEW must not degrade to a plain view");
        engine2.shutdown();
    }

    @Test
    public void readOnlyDatabaseStaysReadOnly() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.getCatalog().getDatabase("test_db").setReadOnly(true);
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        assertTrue(engine2.getCatalog().getDatabase("test_db").isReadOnly(),
            "READ ONLY database must not become writable");
        engine2.shutdown();
    }

    @Test
    public void columnUniqueAndIdentitySurvive() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE TABLE t (id INTEGER IDENTITY(100,5), email VARCHAR UNIQUE)");
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        final Table t = schema(engine2).getTable("t");
        assertTrue(t.getColumn("email").isUnique(), "UNIQUE flag must survive");
        assertTrue(t.getColumn("id").isAutoIncrement(), "IDENTITY/autoIncrement flag must survive");
        assertEquals(100L, t.getColumn("id").getIdentityStart(), "IDENTITY start must survive");
        assertEquals(5L, t.getColumn("id").getIdentityIncrement(), "IDENTITY increment must survive");
        engine2.shutdown();
    }

    @Test
    public void columnForeignKeyReferenceSurvives() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
        engine1.execute("CREATE TABLE child (pid INTEGER REFERENCES parent(id))");
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        assertEquals("parent",
            schema(engine2).getTable("child").getColumn("pid").getReferencedTable().toLowerCase(),
            "column-level REFERENCES target must survive");
        engine2.shutdown();
    }

    @Test
    public void tableForeignKeyConstraintSurvives() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE TABLE p2 (id INTEGER PRIMARY KEY)");
        engine1.execute("CREATE TABLE c2 (pid INTEGER, FOREIGN KEY (pid) REFERENCES p2(id))");
        assertFalse(schema(engine1).getTable("c2").getForeignKeys().isEmpty(), "precondition: FK captured");
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        assertFalse(schema(engine2).getTable("c2").getForeignKeys().isEmpty(),
            "table-level FOREIGN KEY must survive");
        engine2.shutdown();
    }

    @Test
    public void constraintNamesSurviveReload() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("""
            CREATE TABLE named_c (
                a INTEGER,
                b INTEGER,
                c VARCHAR,
                d VARCHAR UNIQUE,
                CONSTRAINT my_pk PRIMARY KEY (a, b),
                CONSTRAINT my_uq UNIQUE (c)
            )
            """);
        final Table before = schema(engine1).getTable("named_c");
        final String generatedUniqueName = before.uniqueConstraintName("d");
        assertEquals("MY_PK", before.primaryKeyConstraintName(), "precondition: explicit PK name is kept");
        assertTrue(generatedUniqueName.startsWith(ConstraintNames.PREFIX),
            "precondition: a column-level UNIQUE auto-names itself");
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        final Table after = schema(engine2).getTable("named_c");
        assertEquals("MY_PK", after.primaryKeyConstraintName(),
            "an explicit PRIMARY KEY name must survive a snapshot round-trip");
        assertEquals(generatedUniqueName, after.uniqueConstraintName("d"),
            "a generated UNIQUE name must be restored, not re-generated");
        assertEquals(2, after.getUniqueConstraints().size(),
            "the table-level UNIQUE and the column-level one are two constraints");
        assertEquals("MY_UQ", uniqueConstraintNameCovering(after, "c"),
            "an explicit UNIQUE name must survive a snapshot round-trip");
        engine2.shutdown();
    }

    @Test
    public void multiColumnUniqueStaysOneConstraintAcrossReload() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE TABLE mc_uq (a INTEGER, b INTEGER, CONSTRAINT uq_ab UNIQUE (a, b))");
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        final Table after = schema(engine2).getTable("mc_uq");
        assertEquals(1, after.getUniqueConstraints().size(),
            "UNIQUE (a, b) must not split into one constraint per column on reload");
        assertEquals("UQ_AB", after.getUniqueConstraints().get(0).getConstraintName());
        assertEquals(2, after.getUniqueConstraints().get(0).getColumnNames().size(),
            "both columns must still belong to the restored constraint");
        engine2.shutdown();
    }

    @Test
    public void generatedForeignKeyConstraintNameSurvivesReload() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE TABLE fk_p (id INTEGER PRIMARY KEY)");
        engine1.execute("CREATE TABLE fk_c (pid INTEGER REFERENCES fk_p(id))");
        final String generatedName = schema(engine1).getTable("fk_c").columnForeignKeyConstraintName("pid");
        final String parentKeyName = schema(engine1).getTable("fk_p").primaryKeyConstraintName();
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        assertEquals(generatedName, schema(engine2).getTable("fk_c").columnForeignKeyConstraintName("pid"),
            "an inline REFERENCES keeps the constraint name it was given across a reload");
        assertEquals(parentKeyName, schema(engine2).getTable("fk_p").primaryKeyConstraintName(),
            "a generated PRIMARY KEY name must be restored, not re-generated");
        engine2.shutdown();
    }

    /** The name of the restored UNIQUE constraint spanning one column — constraint names are opaque. */
    private String uniqueConstraintNameCovering(final Table table, final String columnName) {
        for (final UniqueConstraint unique : table.getUniqueConstraints()) {
            if (unique.covers(columnName)) {
                return unique.getConstraintName();
            }
        }
        return null;
    }

    @Test
    public void transientAndClusterKeysSurvive() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE TRANSIENT TABLE tt (id INTEGER, region VARCHAR) CLUSTER BY (region)");
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        final Table tt = schema(engine2).getTable("tt");
        assertTrue(tt.isTransient(), "TRANSIENT flag must survive");
        assertTrue(tt.getClusterKeys().contains("region"), "CLUSTER BY keys must survive");
        engine2.shutdown();
    }

    @Test
    public void warehouseScalingAndOwnerSurvive() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE WAREHOUSE test_wh WAREHOUSE_SIZE = 'SMALL'");
        final Warehouse wh1 = engine1.getCatalog().getWarehouse("test_wh");
        wh1.setMinClusterCount(2);
        wh1.setMaxClusterCount(4);
        wh1.setScalingPolicy(ScalingPolicy.ECONOMY);
        wh1.setOwner("SECURITYADMIN");
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        final Warehouse wh2 = engine2.getCatalog().getWarehouse("test_wh");
        assertEquals(2, wh2.getMinClusterCount(), "MIN_CLUSTER_COUNT must survive");
        assertEquals(4, wh2.getMaxClusterCount(), "MAX_CLUSTER_COUNT must survive");
        assertEquals(ScalingPolicy.ECONOMY, wh2.getScalingPolicy(), "SCALING_POLICY must survive");
        assertEquals("SECURITYADMIN", wh2.getOwner(), "warehouse owner must survive");
        engine2.shutdown();
    }

    @Test
    public void disabledUserStaysDisabled() {
        final DatabaseEngine engine1 = freshEngine();
        final Catalog catalog1 = engine1.getCatalog();
        catalog1.createUser("bob", "secret", "PUBLIC");
        final User bob1 = catalog1.getUser("bob");
        bob1.setEnabled(false);
        bob1.setOwner("SECURITYADMIN");
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        final User bob2 = engine2.getCatalog().getUser("bob");
        assertFalse(bob2.isEnabled(), "a disabled user must not be re-enabled on reload");
        assertEquals("SECURITYADMIN", bob2.getOwner(), "user owner must survive");
        engine2.shutdown();
    }

    @Test
    public void roleOwnerAndColumnGrantSurvive() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE TABLE grants_tbl (id INTEGER, secret VARCHAR)");
        final Catalog catalog1 = engine1.getCatalog();
        catalog1.createRole("analyst");
        final Role role1 = catalog1.getRole("analyst");
        role1.setOwner("SECURITYADMIN");
        role1.grantColumnPrivilege("TABLE", "TEST_DB.TEST_SCHEMA.GRANTS_TBL", "SECRET", Privilege.SELECT);
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        final Role role2 = engine2.getCatalog().getRole("analyst");
        assertEquals("SECURITYADMIN", role2.getOwner(), "role owner must survive");
        assertFalse(role2.getAllColumnPrivileges().isEmpty(), "column-level grant must survive");
        assertNotNull(role2.getAllColumnPrivileges().get("TABLE:TEST_DB.TEST_SCHEMA.GRANTS_TBL"),
            "column grant must be restored under its object key");
        engine2.shutdown();
    }

    @Test
    public void taskAlterParametersSurvive() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE TASK t_task SCHEDULE = '1 MINUTE' AS SELECT 1");
        engine1.getCatalog().getDatabase("test_db").getSchema("test_schema")
            .getTask("t_task").setErrorIntegration("my_error_int");
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        assertEquals("my_error_int",
            schema(engine2).getTask("t_task").getErrorIntegration(),
            "ALTER TASK error-integration parameter must survive");
        engine2.shutdown();
    }

    @Test
    public void expressionColumnDefaultStaysAnExpressionAcrossReload() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE TABLE t (id INTEGER, qty INTEGER DEFAULT 10 + 5)");
        engine1.shutdown();

        // After reload the default must still be EVALUATED (→ 15), not restored as the literal text "10 + 5".
        final DatabaseEngine engine2 = reopenEngine();
        engine2.execute("INSERT INTO t (id) VALUES (1)");
        final Object qty = engine2.executeQuery("SELECT qty FROM t WHERE id = 1").getRows().get(0).getValue(0);
        assertEquals(15L, ((Number) qty).longValue(), "expression default must survive reload as an expression");
        engine2.shutdown();
    }

    private void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    /**
     * A task's identity survives a reload. SHOW TASKS reports an id and a created_by_user, and live's
     * are stable across time, so a snapshot that dropped them would hand back a different task to
     * anything that recorded the id.
     */
    @Test
    public void taskIdentityAndParameterLevelsSurvive() {
        final DatabaseEngine engine1 = freshEngine();
        engine1.execute("CREATE TABLE task_sink (k INTEGER)");
        engine1.execute("CREATE TASK identity_task SCHEDULE = '60 MINUTE'"
            + " USER_TASK_TIMEOUT_MS = 120000 AS INSERT INTO task_sink VALUES (1)");
        final Task before = schema(engine1).getTask("identity_task");
        final String idBefore = before.getId();
        final String userBefore = before.getCreatedByUser();
        assertNotNull(idBefore, "a task must have an id to persist");
        engine1.shutdown();

        final DatabaseEngine engine2 = reopenEngine();
        final Task after = schema(engine2).getTask("identity_task");
        assertEquals(idBefore, after.getId(), "the task id must survive a reload");
        assertEquals(userBefore, after.getCreatedByUser(), "created_by_user must survive a reload");
        // The parameter's VALUE always survived; without its level, SHOW PARAMETERS would report a
        // task-set timeout as though it were inherited.
        assertEquals(120000L, after.getUserTaskTimeoutMs());
        assertTrue(after.isParameterSetOnTask("USER_TASK_TIMEOUT_MS"),
            "a parameter set by the DDL must still report the TASK level after a reload");
        assertFalse(after.isParameterSetOnTask("TASK_AUTO_RETRY_ATTEMPTS"),
            "a parameter left at its default must not claim the TASK level");
        engine2.shutdown();
    }
}
