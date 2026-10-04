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

package dev.frostlake.persistence;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An engine clone, a checkpoint restore and a write-ahead-log replay hand back the objects as they were. A view lost
 * the statement as typed and its columns' comments. A dynamic table came back AUTO, ON_CREATE and running. Databases,
 * schemas, tables, views, stages and file formats came back owned by SYSADMIN, and every object came back created at
 * the restore. PUBLIC lost its comment and tags, and a dropped PUBLIC came back.
 */
public class SnapshotKeepsDefinitionsTest {

    private Path dir;

    @AfterEach
    public void cleanup() {
        if (dir != null) {
            deleteRecursively(dir.toFile());
        }
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    /** An engine clone and a checkpoint restore of the original. */
    private List<DatabaseEngine> copiesOf(final DatabaseEngine original) throws Exception {
        Thread.sleep(5);   // a copy rebuilt in another millisecond shows a creation time it did not restore
        final List<DatabaseEngine> copies = new ArrayList<>();
        copies.add(original.cloneInstance());
        dir = Files.createTempDirectory("snapshot_keeps_");
        original.checkpointStateTo(dir);
        final DatabaseEngine restored = new DatabaseEngine();
        restored.restoreStateFrom(dir);
        copies.add(restored);
        return copies;
    }

    /** The named columns of each row a statement answers, as "a|b|c" lines. */
    private static List<String> rows(final DatabaseEngine on, final String sql, final String... columns) {
        final ResultSet rs = on.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        while (rs.next()) {
            final StringBuilder line = new StringBuilder();
            for (final String column : columns) {
                line.append(line.length() > 0 ? "|" : "").append(rs.getValue(column));
            }
            out.add(line.toString());
        }
        return out;
    }

    @Test
    public void publicKeepsItsCommentAndTagsAndADroppedOneStaysDropped() throws Exception {
        final DatabaseEngine original = new DatabaseEngine();
        original.execute("CREATE DATABASE p1");
        original.execute("CREATE SCHEMA p1.tags");
        original.execute("CREATE TAG p1.tags.tg");
        original.execute("COMMENT ON SCHEMA p1.PUBLIC IS 'public comment'");
        original.execute("ALTER SCHEMA p1.PUBLIC SET TAG p1.tags.tg = 'pv'");
        original.execute("CREATE DATABASE p2");
        original.execute("DROP SCHEMA p2.PUBLIC");
        original.execute("CREATE SCHEMA p2.other");
        for (final DatabaseEngine copy : copiesOf(original)) {
            assertEquals(List.of("public comment"), rows(copy, "SHOW SCHEMAS LIKE 'PUBLIC' IN DATABASE p1", "comment"));
            assertEquals("pv", copy.getCatalog().getDatabase("P1").getSchema("PUBLIC").getTagValues().values()
                .iterator().next());
            assertEquals(List.of("INFORMATION_SCHEMA", "OTHER"), rows(copy, "SHOW SCHEMAS IN DATABASE p2", "name"));
            copy.shutdown();
        }
        original.shutdown();
    }

    @Test
    public void viewsKeepTheStatementAsTypedAndTheirColumnComments() throws Exception {
        final DatabaseEngine original = new DatabaseEngine();
        original.execute("CREATE DATABASE v1");
        original.execute("USE SCHEMA v1.PUBLIC");
        original.execute("CREATE TABLE base (id INT, name VARCHAR(20))");
        original.execute("CREATE VIEW v1 (vid COMMENT 'the id', vname) COMMENT = 'v1 c' AS SELECT id, name FROM base");
        final List<String> wanted = view(original);
        assertTrue(wanted.contains("VID|the id"), String.valueOf(wanted));
        assertTrue(wanted.toString().contains("VID COMMENT 'the id'"), String.valueOf(wanted));
        for (final DatabaseEngine copy : copiesOf(original)) {
            assertEquals(wanted, view(copy));
            copy.shutdown();
        }
        original.shutdown();
    }

    private static List<String> view(final DatabaseEngine on) {
        final List<String> out = new ArrayList<>();
        out.addAll(rows(on, "SELECT VIEW_DEFINITION FROM v1.INFORMATION_SCHEMA.VIEWS WHERE TABLE_NAME = 'V1'",
            "VIEW_DEFINITION"));
        out.addAll(rows(on, "SELECT COLUMN_NAME, COMMENT FROM v1.INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'V1'"
            + " ORDER BY ORDINAL_POSITION", "COLUMN_NAME", "COMMENT"));
        out.addAll(rows(on, "SELECT GET_DDL('VIEW', 'v1.PUBLIC.v1') AS d", "D"));
        out.addAll(rows(on, "DESCRIBE VIEW v1.PUBLIC.v1", "name", "comment"));
        out.addAll(rows(on, "SHOW VIEWS IN SCHEMA v1.PUBLIC", "name", "text", "comment"));
        return out;
    }

    @Test
    public void dynamicTablesKeepTheirSettings() throws Exception {
        final DatabaseEngine original = new DatabaseEngine();
        original.execute("CREATE DATABASE d1");
        original.execute("USE SCHEMA d1.PUBLIC");
        original.execute("CREATE TABLE b (g VARCHAR, amt INT)");
        original.execute("CREATE DYNAMIC TABLE dt1 TARGET_LAG = '1 minute' WAREHOUSE = COMPUTE_WH REFRESH_MODE = FULL"
            + " INITIALIZE = ON_SCHEDULE AS SELECT g, SUM(amt) s FROM b GROUP BY g");
        original.execute("ALTER DYNAMIC TABLE dt1 SUSPEND");
        final List<String> wanted = dynamicTable(original);
        assertTrue(wanted.get(0).startsWith("FULL|SUSPENDED|"), String.valueOf(wanted));
        for (final DatabaseEngine copy : copiesOf(original)) {
            assertEquals(wanted, dynamicTable(copy));
            copy.shutdown();
        }
        original.shutdown();
    }

    private static List<String> dynamicTable(final DatabaseEngine on) {
        final List<String> out = new ArrayList<>(rows(on, "SHOW DYNAMIC TABLES IN SCHEMA d1.PUBLIC",
            "refresh_mode", "scheduling_state", "last_suspended_on", "text"));
        out.addAll(rows(on, "SELECT GET_DDL('TABLE', 'd1.PUBLIC.dt1') AS d", "D"));
        return out;
    }

    /** Objects of most kinds, made by ACCOUNTADMIN so their owner is not the default SYSADMIN. */
    private static void makeObjects(final DatabaseEngine on) {
        on.execute("USE ROLE ACCOUNTADMIN");
        on.execute("CREATE DATABASE o1");
        on.execute("CREATE SCHEMA o1.s");
        on.execute("USE SCHEMA o1.s");
        on.execute("CREATE TABLE t (a INT)");
        on.execute("CREATE VIEW v AS SELECT a FROM t");
        on.execute("CREATE STAGE stg");
        on.execute("CREATE FILE FORMAT ff TYPE = CSV");
        on.execute("CREATE SEQUENCE sq");
        on.execute("CREATE STREAM st ON TABLE t");
        on.execute("CREATE FUNCTION f() RETURNS INT AS '1'");
        on.execute("CREATE PROCEDURE pr() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END $$");
        on.execute("CREATE TASK tk WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE' AS SELECT 1");
        on.execute("CREATE TAG tg");
        on.execute("CREATE MASKING POLICY mp AS (v STRING) RETURNS STRING -> '***'");
        on.execute("CREATE ROW ACCESS POLICY rap AS (k STRING) RETURNS BOOLEAN -> TRUE");
    }

    /** Each object's name, owner and creation time, as the listings report them. */
    private static List<String> stamps(final DatabaseEngine on) {
        final List<String> out = new ArrayList<>();
        final String[] owned = {"name", "owner", "created_on"};
        out.addAll(rows(on, "SHOW DATABASES LIKE 'O1'", owned));
        out.addAll(rows(on, "SHOW SCHEMAS LIKE 'S' IN DATABASE o1", owned));
        for (final String listing : Arrays.asList("TABLES", "VIEWS", "STAGES", "FILE FORMATS", "SEQUENCES", "TASKS",
                "TAGS", "MASKING POLICIES", "ROW ACCESS POLICIES")) {
            out.addAll(rows(on, "SHOW " + listing + " IN SCHEMA o1.s", owned));
        }
        out.addAll(rows(on, "SHOW STREAMS IN SCHEMA o1.s", "name", "owner", "created_on", "stale_after"));
        out.addAll(rows(on, "SHOW USER FUNCTIONS IN SCHEMA o1.s", "name", "created_on"));
        out.addAll(rows(on, "SHOW USER PROCEDURES IN SCHEMA o1.s", "name", "created_on"));
        out.addAll(rows(on, "SELECT TABLE_NAME, TABLE_OWNER, CREATED FROM o1.INFORMATION_SCHEMA.TABLES"
            + " WHERE TABLE_SCHEMA = 'S' ORDER BY 1", "TABLE_NAME", "TABLE_OWNER", "CREATED"));
        return out;
    }

    @Test
    public void ownersAndCreationTimesSurviveACloneAndARestore() throws Exception {
        final DatabaseEngine original = new DatabaseEngine();
        makeObjects(original);
        final List<String> wanted = stamps(original);
        assertTrue(wanted.toString().contains("T|ACCOUNTADMIN|"), String.valueOf(wanted));
        for (final DatabaseEngine copy : copiesOf(original)) {
            assertEquals(wanted, stamps(copy));
            copy.shutdown();
        }
        original.shutdown();
    }

    @Test
    public void aReplayedLogStampsWhatTheOriginalRunStamped() throws Exception {
        dir = Files.createTempDirectory("snapshot_keeps_wal_");
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        final DatabaseEngine first = new DatabaseEngine(cfg);
        makeObjects(first);
        final List<String> wanted = stamps(first);
        first.shutdown();
        Thread.sleep(5);
        final DatabaseEngine recovered = new DatabaseEngine(cfg);
        try {
            assertEquals(wanted, stamps(recovered));
        } finally {
            recovered.shutdown();
        }
    }
}
