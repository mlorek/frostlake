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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a snapshot keeps besides an object's definition: when DDL and DML last changed it, a tag's PROPAGATE and
 * ON_CONFLICT, and the width of a masking policy's RETURNS type. A clone or a restore reported its own moment as
 * every table's LAST_ALTERED and every object's LAST_DDL, a tag that propagated as one that did not, and
 * {@code RETURNS VARCHAR(5)} as {@code RETURNS VARCHAR}. UNDROP is a DDL of its own, and moves LAST_DDL.
 */
public class SnapshotKeepsChangeTimesTest {

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
        final Path dir = Files.createTempDirectory("change_times_");
        dirs.add(dir);
        original.checkpointStateTo(dir);
        final DatabaseEngine restored = track(new DatabaseEngine());
        restored.restoreStateFrom(dir);
        out.add(restored);
        return out;
    }

    private static final String TIMES = "SELECT table_name || ' ' || created || ' ' || last_altered || ' ' || last_ddl"
        + " || ' ' || COALESCE(last_ddl_by, '-') AS r FROM ct.INFORMATION_SCHEMA.TABLES WHERE table_schema = 'S'"
        + " ORDER BY table_name";

    @Test
    public void lastAlteredAndLastDdlSurviveACloneAndARestore() throws Exception {
        final DatabaseEngine original = track(new DatabaseEngine());
        original.execute("CREATE DATABASE ct");
        original.execute("CREATE SCHEMA ct.s");
        original.execute("USE SCHEMA ct.s");
        original.execute("CREATE TABLE t (a INT)");
        original.execute("CREATE TABLE w (a INT)");
        original.execute("CREATE VIEW v AS SELECT a FROM t");
        original.execute("ALTER TABLE t ADD COLUMN b INT");
        original.execute("INSERT INTO w VALUES (1)");
        original.execute("COMMENT ON VIEW v IS 'vee'");
        original.execute("ALTER SCHEMA ct.s SET COMMENT = 'ess'");
        original.execute("ALTER DATABASE ct SET COMMENT = 'see'");
        final List<String> times = column(original, TIMES, "R");
        final List<String> schema = column(original,
            "SELECT last_altered::VARCHAR AS r FROM ct.INFORMATION_SCHEMA.SCHEMATA WHERE schema_name = 'S'", "R");
        final List<String> database = column(original,
            "SELECT last_altered::VARCHAR AS r FROM ct.INFORMATION_SCHEMA.DATABASES WHERE database_name = 'CT'", "R");
        final List<String> token = column(original, "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('ct.s.w')::VARCHAR AS r",
            "R");

        for (final DatabaseEngine copy : copies(original)) {
            assertEquals(times, column(copy, TIMES, "R"));
            assertEquals(schema, column(copy,
                "SELECT last_altered::VARCHAR AS r FROM ct.INFORMATION_SCHEMA.SCHEMATA WHERE schema_name = 'S'", "R"));
            assertEquals(database, column(copy,
                "SELECT last_altered::VARCHAR AS r FROM ct.INFORMATION_SCHEMA.DATABASES WHERE database_name = 'CT'",
                "R"));
            assertEquals(token, column(copy, "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('ct.s.w')::VARCHAR AS r", "R"));
        }
    }

    private static final String STAMPS = "SELECT 'seq ' || sequence_name || ' ' || created || ' ' || last_altered AS r"
        + " FROM cs.INFORMATION_SCHEMA.SEQUENCES"
        + " UNION ALL SELECT 'ff ' || file_format_name || ' ' || created || ' ' || last_altered"
        + " FROM cs.INFORMATION_SCHEMA.FILE_FORMATS"
        + " UNION ALL SELECT 'table ' || table_name || ' ' || last_altered FROM cs.INFORMATION_SCHEMA.TABLES"
        + " WHERE table_schema = 'PUBLIC'"
        + " UNION ALL SELECT 'constraint ' || constraint_name || ' ' || created || ' ' || last_altered"
        + " FROM cs.INFORMATION_SCHEMA.TABLE_CONSTRAINTS"
        + " UNION ALL SELECT 'fk ' || constraint_name || ' ' || created || ' ' || last_altered"
        + " FROM cs.INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS"
        + " ORDER BY r";

    @Test
    public void sequenceFileFormatDmlAndConstraintDatesSurviveACloneAndARestore() throws Exception {
        final DatabaseEngine original = track(new DatabaseEngine());
        original.execute("CREATE DATABASE cs");
        original.execute("USE SCHEMA cs.PUBLIC");
        original.execute("CREATE SEQUENCE sq");
        original.execute("CREATE FILE FORMAT ff TYPE = CSV");
        original.execute("CREATE TABLE p (id INT PRIMARY KEY)");
        original.execute("CREATE TABLE c (id INT, pid INT)");
        original.execute("CREATE TABLE u (a INT)");
        original.execute("ALTER SEQUENCE sq SET INCREMENT = 5");
        original.execute("ALTER FILE FORMAT ff SET SKIP_HEADER = 1");
        original.execute("ALTER TABLE c ADD CONSTRAINT fk1 FOREIGN KEY (pid) REFERENCES p (id)");
        original.execute("ALTER TABLE u ADD CONSTRAINT uq1 UNIQUE (a)");
        original.execute("ALTER TABLE u RENAME CONSTRAINT uq1 TO uq2");
        original.execute("ALTER TABLE c ALTER CONSTRAINT fk1 RELY");
        // A DML that changes no row moves the table's LAST_ALTERED all the same.
        original.execute("DELETE FROM p WHERE id = 99");
        final List<String> stamps = column(original, STAMPS, "R");
        assertEquals(9, stamps.size(), String.valueOf(stamps));

        for (final DatabaseEngine copy : copies(original)) {
            assertEquals(stamps, column(copy, STAMPS, "R"));
        }
    }

    @Test
    public void undropMovesLastDdlAndLeavesTheRowsLastWrite() {
        final DatabaseEngine engine = track(new DatabaseEngine());
        engine.execute("CREATE DATABASE ud");
        engine.execute("USE SCHEMA ud.PUBLIC");
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE SCHEMA s");
        engine.execute("CREATE TABLE s.inner_t (a INT)");
        engine.execute("INSERT INTO s.inner_t VALUES (1)");
        engine.execute("USE SCHEMA ud.PUBLIC");
        final String innerAltered = single(engine,
            "SELECT last_altered::VARCHAR AS r FROM ud.INFORMATION_SCHEMA.TABLES WHERE table_name = 'INNER_T'");
        engine.execute("DROP TABLE t");
        engine.execute("DROP SCHEMA s");
        engine.execute("SET before_undrop = CURRENT_TIMESTAMP()");
        engine.execute("UNDROP TABLE t");
        engine.execute("UNDROP SCHEMA s");
        assertEquals("true true", single(engine, "SELECT (last_ddl > $before_undrop)::VARCHAR || ' '"
            + " || (last_altered = last_ddl)::VARCHAR AS r FROM ud.INFORMATION_SCHEMA.TABLES WHERE table_name = 'T'"));
        assertEquals("true", single(engine, "SELECT (last_altered > $before_undrop)::VARCHAR AS r"
            + " FROM ud.INFORMATION_SCHEMA.SCHEMATA WHERE schema_name = 'S'"));
        assertEquals(innerAltered, single(engine,
            "SELECT last_altered::VARCHAR AS r FROM ud.INFORMATION_SCHEMA.TABLES WHERE table_name = 'INNER_T'"),
            "a table the UNDROP SCHEMA brought back was not written by it");
        engine.execute("CREATE DATABASE gone");
        engine.execute("DROP DATABASE gone");
        engine.execute("SET before_database = CURRENT_TIMESTAMP()");
        engine.execute("UNDROP DATABASE gone");
        assertEquals("true", single(engine, "SELECT (last_altered > $before_database)::VARCHAR AS r"
            + " FROM gone.INFORMATION_SCHEMA.DATABASES WHERE database_name = 'GONE'"));
    }

    @Test
    public void aTagKeepsItsPropagationAndConflictRule() throws Exception {
        final DatabaseEngine original = track(new DatabaseEngine());
        original.execute("CREATE DATABASE tg");
        original.execute("USE SCHEMA tg.PUBLIC");
        original.execute("CREATE TAG tp PROPAGATE = ON_DEPENDENCY_AND_DATA_MOVEMENT ON_CONFLICT = 'HIGHEST'"
            + " COMMENT = 'tp c'");
        final List<String> shown = tagOptions(original);
        assertEquals(List.of("ON_DEPENDENCY_AND_DATA_MOVEMENT HIGHEST"), shown);
        for (final DatabaseEngine copy : copies(original)) {
            copy.execute("USE SCHEMA tg.PUBLIC");
            assertEquals(shown, tagOptions(copy));
        }
    }

    @Test
    public void aMaskingPolicyKeepsItsReturnWidth() throws Exception {
        final DatabaseEngine original = track(new DatabaseEngine());
        original.execute("CREATE DATABASE mp");
        original.execute("USE SCHEMA mp.PUBLIC");
        original.execute("CREATE MASKING POLICY m AS (v VARCHAR) RETURNS VARCHAR(5) -> LEFT(v, 5)");
        assertTrue(policyDdl(original, "mp").contains("returns VARCHAR(5)"), policyDdl(original, "mp"));
        original.execute("CREATE DATABASE mp2 CLONE mp");
        assertTrue(policyDdl(original, "mp2").contains("returns VARCHAR(5)"), policyDdl(original, "mp2"));
        for (final DatabaseEngine copy : copies(original)) {
            assertTrue(policyDdl(copy, "mp").contains("returns VARCHAR(5)"), policyDdl(copy, "mp"));
            assertTrue(policyDdl(copy, "mp2").contains("returns VARCHAR(5)"), policyDdl(copy, "mp2"));
        }
    }

    private static List<String> tagOptions(final DatabaseEngine engine) {
        final ResultSet rs = engine.executeQuery("SHOW TAGS LIKE 'TP'");
        final List<String> out = new ArrayList<>();
        while (rs.next()) {
            out.add(rs.getValue("propagate") + " " + rs.getValue("on_conflict"));
        }
        return out;
    }

    private static String policyDdl(final DatabaseEngine engine, final String database) {
        return single(engine, "SELECT GET_DDL('POLICY', '" + database + ".PUBLIC.m') AS r");
    }

    private static String single(final DatabaseEngine engine, final String sql) {
        final List<String> values = column(engine, sql, "R");
        assertEquals(1, values.size(), sql);
        return values.get(0);
    }

    private static List<String> column(final DatabaseEngine engine, final String sql, final String name) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        while (rs.next()) {
            out.add(String.valueOf(rs.getValue(name)));
        }
        return out;
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
