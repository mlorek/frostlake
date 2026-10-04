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
import dev.frostlake.metastore.DroppedObject;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dropped versions UNDROP can restore survive an engine clone and a checkpoint, and each is kept for the
 * retention it had when it was dropped. A snapshot kept none, so UNDROP refused on the copy what it restored on the
 * original, and SHOW SCHEMAS | DATABASES HISTORY listed nothing dropped.
 */
public class DroppedVersionsSurviveSnapshotsTest {

    private final List<Path> dirs = new ArrayList<>();
    private final List<DatabaseEngine> engines = new ArrayList<>();

    @AfterEach
    public void cleanup() {
        for (final DatabaseEngine engine : engines) {
            engine.shutdown();
        }
        for (final Path dir : dirs) {
            deleteRecursively(dir.toFile());
        }
    }

    private DatabaseEngine track(final DatabaseEngine engine) {
        engines.add(engine);
        return engine;
    }

    /** An in-memory clone of {@code original}, and an engine restored from its checkpoint. */
    private List<DatabaseEngine> copies(final DatabaseEngine original) throws Exception {
        final List<DatabaseEngine> out = new ArrayList<>();
        out.add(track(original.cloneInstance()));
        final Path dir = Files.createTempDirectory("dropped_versions_");
        dirs.add(dir);
        original.checkpointStateTo(dir);
        final DatabaseEngine restored = track(new DatabaseEngine());
        restored.restoreStateFrom(dir);
        out.add(restored);
        return out;
    }

    @Test
    public void droppedTablesSchemasAndDatabasesCanBeUndroppedAfterACloneAndARestore() throws Exception {
        final DatabaseEngine original = track(new DatabaseEngine());
        original.execute("CREATE DATABASE d");
        original.execute("USE SCHEMA d.PUBLIC");
        original.execute("CREATE TABLE t (a INT, b VARCHAR(5) COMMENT 'bee')");
        original.execute("INSERT INTO t VALUES (1, 'one'), (2, 'two')");
        original.execute("DROP TABLE t");
        original.execute("CREATE SCHEMA s");
        original.execute("CREATE TABLE s.u (x INT)");
        original.execute("INSERT INTO s.u VALUES (7)");
        original.execute("CREATE VIEW s.v AS SELECT x FROM s.u");
        original.execute("DROP SCHEMA s");
        original.execute("CREATE DATABASE gone");
        original.execute("CREATE TABLE gone.PUBLIC.w (y INT)");
        original.execute("INSERT INTO gone.PUBLIC.w VALUES (9)");
        original.execute("DROP DATABASE gone");
        original.execute("USE SCHEMA d.PUBLIC");
        final List<String> schemaDroppedOn = column(original, "SHOW SCHEMAS HISTORY LIKE 'S' IN DATABASE d", "dropped_on");
        final List<String> databaseDroppedOn = column(original, "SHOW DATABASES HISTORY LIKE 'GONE'", "dropped_on");
        assertEquals(1, schemaDroppedOn.size());
        assertEquals(1, databaseDroppedOn.size());

        for (final DatabaseEngine copy : copies(original)) {
            copy.execute("USE SCHEMA d.PUBLIC");
            assertEquals(schemaDroppedOn, column(copy, "SHOW SCHEMAS HISTORY LIKE 'S' IN DATABASE d", "dropped_on"),
                "the dropped schema is listed, dropped when it was");
            assertEquals(databaseDroppedOn, column(copy, "SHOW DATABASES HISTORY LIKE 'GONE'", "dropped_on"),
                "the dropped database is listed, dropped when it was");
            copy.execute("UNDROP TABLE t");
            assertEquals(List.of("1 one", "2 two"), column(copy, "SELECT a || ' ' || b AS r FROM t ORDER BY a", "R"));
            assertEquals(List.of("bee"), column(copy, "SELECT comment FROM d.INFORMATION_SCHEMA.COLUMNS"
                + " WHERE table_name = 'T' AND column_name = 'B'", "COMMENT"));
            copy.execute("UNDROP SCHEMA s");
            assertEquals(List.of("7"), column(copy, "SELECT x FROM s.v", "X"));
            copy.execute("UNDROP DATABASE gone");
            assertEquals(List.of("9"), column(copy, "SELECT y FROM gone.PUBLIC.w", "Y"));
        }
    }

    @Test
    public void everyDroppedVersionComesBackNewestFirst() throws Exception {
        final DatabaseEngine original = track(new DatabaseEngine());
        original.execute("CREATE DATABASE d");
        original.execute("USE SCHEMA d.PUBLIC");
        for (int i = 1; i <= 3; i++) {
            original.execute("CREATE TABLE t (v INT)");
            original.execute("INSERT INTO t VALUES (" + i + ")");
            original.execute("DROP TABLE t");
        }
        for (final DatabaseEngine copy : copies(original)) {
            copy.execute("USE SCHEMA d.PUBLIC");
            for (int i = 3; i >= 1; i--) {
                copy.execute("UNDROP TABLE t");
                assertEquals(List.of(String.valueOf(i)), column(copy, "SELECT v FROM t", "V"));
                copy.execute("ALTER TABLE t RENAME TO t" + i);
            }
            assertEquals("Table T did not exist or was purged.", refusal(copy, "UNDROP TABLE t"));
        }
    }

    @Test
    public void droppedDynamicTablesAndTagsCanBeUndroppedAfterAClone() throws Exception {
        final DatabaseEngine original = track(new DatabaseEngine());
        original.execute("CREATE DATABASE d");
        original.execute("USE SCHEMA d.PUBLIC");
        original.execute("CREATE TABLE src (a INT)");
        original.execute("CREATE DYNAMIC TABLE dt TARGET_LAG = '1 minute' WAREHOUSE = COMPUTE_WH"
            + " AS SELECT a FROM src");
        original.execute("DROP DYNAMIC TABLE dt");
        original.execute("CREATE TAG tg ALLOWED_VALUES 'x', 'y' COMMENT = 'tag c'");
        original.execute("DROP TAG tg");
        for (final DatabaseEngine copy : copies(original)) {
            copy.execute("USE SCHEMA d.PUBLIC");
            copy.execute("UNDROP DYNAMIC TABLE dt");
            assertEquals(List.of("DT"), column(copy, "SHOW DYNAMIC TABLES LIKE 'DT'", "name"));
            copy.execute("UNDROP TAG tg");
            assertEquals(List.of("tag c"), column(copy, "SHOW TAGS LIKE 'TG'", "comment"));
        }
    }

    @Test
    public void anOlderSnapshotsUpperCasedKeysComeBackUnderExactNames() throws Exception {
        final DatabaseEngine original = track(new DatabaseEngine());
        original.execute("CREATE DATABASE d");
        original.execute("CREATE SCHEMA d.\"lower\"");
        original.execute("DROP SCHEMA d.\"lower\"");
        original.execute("CREATE DATABASE \"ld\"");
        original.execute("DROP DATABASE \"ld\"");
        original.execute("USE SCHEMA d.PUBLIC");
        original.execute("CREATE TABLE t (a INT)");
        original.execute("DROP TABLE t");
        final List<DroppedObjectSnapshot> saved = DroppedObjectSnapshots.save(original.getCatalog());
        assertEquals(3, saved.size());
        for (final DroppedObjectSnapshot version : saved) {
            // As a snapshot written while the catalog upper-cased its keys holds them.
            version.key = version.key.toUpperCase();
        }
        final DatabaseEngine restored = track(new DatabaseEngine());
        restored.execute("CREATE DATABASE d");
        DroppedObjectSnapshots.restore(saved, restored.getCatalog(), restored.getS3PathResolver());
        restored.execute("UNDROP SCHEMA d.\"lower\"");
        restored.execute("UNDROP DATABASE \"ld\"");
        restored.execute("UNDROP TABLE d.PUBLIC.t");
        assertEquals(List.of("T"), column(restored, "SHOW TABLES IN SCHEMA d.PUBLIC", "name"));
    }

    @Test
    public void aDropWithNoRetentionCannotBeUndropped() {
        final DatabaseEngine engine = track(new DatabaseEngine());
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        engine.execute("CREATE TABLE t (a INT) DATA_RETENTION_TIME_IN_DAYS = 0");
        engine.execute("DROP TABLE t");
        assertEquals("Table T did not exist or was purged.", refusal(engine, "UNDROP TABLE t"));
        engine.execute("CREATE SCHEMA s DATA_RETENTION_TIME_IN_DAYS = 0");
        engine.execute("DROP SCHEMA s");
        assertEquals(List.of(), column(engine, "SHOW SCHEMAS HISTORY LIKE 'S' IN DATABASE d", "name"));
        assertEquals("Schema S did not exist or was purged.", refusal(engine, "UNDROP SCHEMA s"));
        // A table that declares no retention of its own keeps its schema's.
        engine.execute("CREATE SCHEMA keep DATA_RETENTION_TIME_IN_DAYS = 0");
        engine.execute("CREATE TABLE keep.k (a INT)");
        engine.execute("DROP TABLE keep.k");
        assertEquals("Table K did not exist or was purged.", refusal(engine, "UNDROP TABLE keep.k"));
        engine.execute("CREATE SCHEMA s2 DATA_RETENTION_TIME_IN_DAYS = 3");
        engine.execute("DROP SCHEMA s2");
        assertEquals(List.of("S2"), column(engine, "SHOW SCHEMAS HISTORY LIKE 'S2' IN DATABASE d", "name"));
    }

    @Test
    public void aVersionIsKeptForTheRetentionItWasDroppedWith() {
        final Instant droppedOn = Instant.parse("2026-01-01T00:00:00Z");
        final DroppedObject version = DroppedObject.restored("t", null, null, null, 2, droppedOn);
        assertFalse(version.isPurgedAt(droppedOn.plus(Duration.ofDays(2)).minusMillis(1)));
        assertTrue(version.isPurgedAt(droppedOn.plus(Duration.ofDays(2))));
        assertTrue(DroppedObject.restored("t", null, null, null, 0, droppedOn).isPurgedAt(droppedOn));
    }

    @Test
    public void aVersionPastItsRetentionIsPurged() {
        final DatabaseEngine engine = track(new DatabaseEngine());
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        engine.execute("CREATE SCHEMA old");
        engine.execute("DROP SCHEMA old");
        // The same version as though it had been dropped three days ago with the default retention of one day, as
        // the restore of an old checkpoint finds it.
        final DroppedObject dropped = engine.getCatalog().takeDropped("SCHEMA:D.OLD");
        engine.getCatalog().recordDropped("SCHEMA:D.OLD", DroppedObject.restored(dropped.getObject(), null,
            dropped.getTableRows(), dropped.getHiddenTableRows(), 1, Instant.now().minus(Duration.ofDays(3))));
        assertEquals(List.of(), column(engine, "SHOW SCHEMAS HISTORY LIKE 'OLD' IN DATABASE d", "name"));
        assertEquals("Schema OLD did not exist or was purged.", refusal(engine, "UNDROP SCHEMA old"));
    }

    @Test
    public void aDropIsStampedWithItsStatementsInstant() throws Exception {
        final Path dir = Files.createTempDirectory("dropped_on_wal_");
        dirs.add(dir);
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        final DatabaseEngine first = new DatabaseEngine(cfg);
        first.execute("CREATE DATABASE d");
        first.execute("CREATE SCHEMA d.s");
        first.execute("DROP SCHEMA d.s");
        final Instant droppedOn = first.getCatalog().droppedOfKind("SCHEMA").get(0).getDroppedOn();
        assertEquals(0, droppedOn.getNano() % 1_000_000, "a millisecond, as the statement clock reads");
        final List<String> listed = column(first, "SHOW SCHEMAS HISTORY LIKE 'S' IN DATABASE d", "dropped_on");
        first.shutdown();
        // The replayed DROP runs at the instant the log recorded, so the version is dropped when it first was.
        final DatabaseEngine replayed = track(new DatabaseEngine(cfg));
        assertEquals(listed, column(replayed, "SHOW SCHEMAS HISTORY LIKE 'S' IN DATABASE d", "dropped_on"));
    }

    private static List<String> column(final DatabaseEngine engine, final String sql, final String name) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        while (rs.next()) {
            out.add(String.valueOf(rs.getValue(name)));
        }
        return out;
    }

    private static String refusal(final DatabaseEngine engine, final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
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
}
