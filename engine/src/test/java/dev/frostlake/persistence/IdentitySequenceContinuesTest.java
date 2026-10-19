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
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An IDENTITY column never hands out a value its table already holds, whatever copy of the table it is. The
 * sequence lived only in the table's storage, which every copy builds afresh. An engine clone, a checkpoint
 * restore, UNDROP and CREATE TABLE/SCHEMA/DATABASE … CLONE all restarted it at START, and the next insert repeated
 * the first id. A table without a row access policy also lost its CHECK constraints, aggregation and join policies
 * and search optimization in every snapshot restore.
 */
public class IdentitySequenceContinuesTest {

    private DatabaseEngine engine;
    private Path dir;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE id_db");
        engine.execute("USE SCHEMA id_db.PUBLIC");
        engine.execute("CREATE TABLE t (id INT IDENTITY(1, 1), v VARCHAR)");
        engine.execute("INSERT INTO t (v) VALUES ('a'), ('b'), ('c')");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
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

    /** The ids of a table, in order, joined by ','. */
    private static String ids(final DatabaseEngine on, final String table) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : on.executeQuery("SELECT id FROM " + table + " ORDER BY id").getRows()) {
            out.append(out.length() > 0 ? "," : "").append(row.getValue(0));
        }
        return out.toString();
    }

    @Test
    public void anEngineCloneAndACheckpointRestoreContinueTheSequence() throws Exception {
        final DatabaseEngine clone = engine.cloneInstance();
        try {
            clone.execute("INSERT INTO id_db.PUBLIC.t (v) VALUES ('new')");
            assertEquals("1,2,3,4", ids(clone, "id_db.PUBLIC.t"));
        } finally {
            clone.shutdown();
        }
        dir = Files.createTempDirectory("identity_sequence_");
        engine.checkpointStateTo(dir);
        final DatabaseEngine restored = new DatabaseEngine();
        try {
            restored.restoreStateFrom(dir);
            restored.execute("INSERT INTO id_db.PUBLIC.t (v) VALUES ('new')");
            assertEquals("1,2,3,4", ids(restored, "id_db.PUBLIC.t"));
        } finally {
            restored.shutdown();
        }
    }

    @Test
    public void undropContinuesTheSequence() {
        engine.execute("DROP TABLE t");
        engine.execute("UNDROP TABLE t");
        engine.execute("INSERT INTO t (v) VALUES ('after')");
        assertEquals("1,2,3,4", ids(engine, "t"));
        engine.execute("CREATE SCHEMA s10");
        engine.execute("CREATE TABLE s10.u (id INT IDENTITY(10, 10), v VARCHAR)");
        engine.execute("INSERT INTO s10.u (v) VALUES ('x'), ('y')");
        engine.execute("DROP SCHEMA s10");
        engine.execute("UNDROP SCHEMA s10");
        engine.execute("INSERT INTO s10.u (v) VALUES ('after')");
        assertEquals("10,20,30", ids(engine, "s10.u"));
    }

    @Test
    public void everyCloneContinuesTheSequence() {
        engine.execute("CREATE TABLE tc CLONE t");
        engine.execute("CREATE SCHEMA sc CLONE PUBLIC");
        engine.execute("CREATE DATABASE dc CLONE id_db");
        engine.execute("INSERT INTO id_db.PUBLIC.tc (v) VALUES ('new')");
        engine.execute("INSERT INTO id_db.sc.t (v) VALUES ('new')");
        engine.execute("INSERT INTO dc.PUBLIC.t (v) VALUES ('new')");
        assertEquals("1,2,3,4", ids(engine, "id_db.PUBLIC.tc"));
        assertEquals("1,2,3,4", ids(engine, "id_db.sc.t"));
        assertEquals("1,2,3,4", ids(engine, "dc.PUBLIC.t"));
        engine.execute("INSERT INTO id_db.PUBLIC.t (v) VALUES ('source')");
        assertEquals("1,2,3,4", ids(engine, "id_db.PUBLIC.t"), "each copy draws on its own");
    }

    /** A snapshot written before the sequence was recorded restarts it past the rows it brings back. */
    @Test
    public void anOldSnapshotsTableMovesPastItsRows() {
        final Table table = new Table("OLD", Arrays.asList(
            new TableColumn("ID", NumericType.INTEGER, false, null, false, false, true, 5L, 5L),
            new TableColumn("V", NumericType.INTEGER, true, null, false, false, false)), false);
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(new ArrayList<Object>(Arrays.asList(5L, 1L))));
        rows.add(new Row(new ArrayList<Object>(Arrays.asList(15L, 2L))));
        table.continueIdentityPast(rows);
        assertEquals(20L, table.drawIdentityValue());
    }

    @Test
    public void aRestoreKeepsTablePropertiesWithoutARowAccessPolicy() throws Exception {
        engine.execute("CREATE AGGREGATION POLICY agp AS () RETURNS AGGREGATION_CONSTRAINT"
            + " -> AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 2)");
        engine.execute("CREATE JOIN POLICY jp AS () RETURNS JOIN_CONSTRAINT -> JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)");
        engine.execute("CREATE TABLE p (a INT, b VARCHAR, CONSTRAINT ck_a CHECK (a > 0)) WITH AGGREGATION POLICY agp");
        engine.execute("CREATE TABLE j (a INT) WITH JOIN POLICY jp");
        engine.execute("ALTER TABLE p ADD SEARCH OPTIMIZATION ON EQUALITY(b)");
        final DatabaseEngine clone = engine.cloneInstance();
        try {
            clone.execute("USE SCHEMA id_db.PUBLIC");
            assertTrue(refused(clone, "SELECT * FROM p").contains("aggregation required"));
            assertTrue(refused(clone, "INSERT INTO p VALUES (-5, 'neg')").contains("CHECK constraint CK_A"));
            assertTrue(refused(clone, "SELECT * FROM j").contains("Join Policy violation"));
            assertEquals(1, clone.executeQuery("DESCRIBE SEARCH OPTIMIZATION ON p").getRows().size());
        } finally {
            clone.shutdown();
        }
    }

    private static String refused(final DatabaseEngine on, final String sql) {
        try {
            on.execute(sql);
            return "not refused";
        } catch (final RuntimeException refusal) {
            return String.valueOf(refusal.getMessage());
        }
    }
}
