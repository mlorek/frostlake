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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dropping (or replacing) a database or schema must RELEASE the storage of the tables it contained, exactly
 * as DROP TABLE does. When it did not, the storage entries outlived the drop and the name could not be used
 * again: re-creating the same table failed with "Table storage already exists", and a CLONE into a re-created
 * database appended to the stale rows instead of replacing them. The rows are still snapshotted at drop time,
 * so UNDROP restores both metadata and data.
 */
public class DropReleasesStorageTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE seed");
        engine.execute("CREATE SCHEMA seed.s");
        engine.execute("CREATE TABLE seed.s.t (id INTEGER)");
        engine.execute("INSERT INTO seed.s.t VALUES (1), (2)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private long count(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM " + table);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void recreatingTableAfterDropDatabaseStartsEmpty() {
        engine.execute("CREATE DATABASE d");
        engine.execute("CREATE SCHEMA d.s");
        engine.execute("CREATE TABLE d.s.t (id INTEGER)");
        engine.execute("INSERT INTO d.s.t VALUES (7), (8)");
        engine.execute("DROP DATABASE d");
        // Re-creating the same names used to fail with "Table storage already exists".
        engine.execute("CREATE DATABASE d");
        engine.execute("CREATE SCHEMA d.s");
        engine.execute("CREATE TABLE d.s.t (id INTEGER)");
        assertEquals(0, count("d.s.t"));
    }

    @Test
    public void recreatingTableAfterDropSchemaStartsEmpty() {
        engine.execute("CREATE SCHEMA seed.s3");
        engine.execute("CREATE TABLE seed.s3.t (id INTEGER)");
        engine.execute("INSERT INTO seed.s3.t VALUES (4)");
        engine.execute("DROP SCHEMA seed.s3 CASCADE");
        engine.execute("CREATE SCHEMA seed.s3");
        engine.execute("CREATE TABLE seed.s3.t (id INTEGER)");
        assertEquals(0, count("seed.s3.t"));
    }

    @Test
    public void cloneIntoDroppedDatabaseNameReplacesRatherThanAppends() {
        engine.execute("CREATE DATABASE shared CLONE seed");
        engine.execute("INSERT INTO shared.s.t VALUES (99)");   // the clone is modified
        engine.execute("DROP DATABASE shared");
        engine.execute("CREATE DATABASE shared CLONE seed");    // restore it from the pristine source
        assertEquals(2, count("shared.s.t"));                   // 1,2 — not the old rows plus a second copy
    }

    @Test
    public void createOrReplaceDatabaseCloneReplacesRatherThanAppends() {
        engine.execute("CREATE DATABASE d2 CLONE seed");
        engine.execute("CREATE OR REPLACE DATABASE d2 CLONE seed");
        assertEquals(2, count("d2.s.t"));
    }

    @Test
    public void createOrReplaceSchemaDiscardsOldRows() {
        engine.execute("CREATE SCHEMA seed.s4");
        engine.execute("CREATE TABLE seed.s4.t (id INTEGER)");
        engine.execute("INSERT INTO seed.s4.t VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE SCHEMA seed.s4");
        engine.execute("CREATE TABLE seed.s4.t (id INTEGER)");
        assertEquals(0, count("seed.s4.t"));
    }

    // ---- UNDROP still restores the data the drop released ----

    @Test
    public void undropDatabaseRestoresRows() {
        engine.execute("CREATE DATABASE d3");
        engine.execute("CREATE SCHEMA d3.s");
        engine.execute("CREATE TABLE d3.s.t (id INTEGER)");
        engine.execute("INSERT INTO d3.s.t VALUES (1), (2), (3)");
        engine.execute("DROP DATABASE d3");
        engine.execute("UNDROP DATABASE d3");
        assertEquals(3, count("d3.s.t"));
    }

    @Test
    public void undropSchemaRestoresRows() {
        engine.execute("CREATE SCHEMA seed.s2");
        engine.execute("CREATE TABLE seed.s2.t (id INTEGER)");
        engine.execute("INSERT INTO seed.s2.t VALUES (7), (8)");
        engine.execute("DROP SCHEMA seed.s2 CASCADE");
        engine.execute("UNDROP SCHEMA seed.s2");
        assertEquals(2, count("seed.s2.t"));
    }

    @Test
    public void undropTableStillRestoresRows() {
        engine.execute("DROP TABLE seed.s.t");
        engine.execute("UNDROP TABLE seed.s.t");
        assertEquals(2, count("seed.s.t"));
    }

    // ---- DROP … CASCADE / RESTRICT now parse (the engine's own error tells you to use CASCADE) ----

    @Test
    public void dropSchemaCascadeDropsANonEmptySchema() {
        engine.execute("CREATE SCHEMA seed.s5");
        engine.execute("CREATE TABLE seed.s5.t (id INTEGER)");
        // Without CASCADE this is refused ("Schema is not empty") — the keyword the error names must parse.
        engine.execute("DROP SCHEMA seed.s5 CASCADE");
        assertFalse(schemaExists("S5"));
    }

    @Test
    public void dropSchemaRestrictRefusesANonEmptySchema() {
        engine.execute("CREATE SCHEMA seed.s6");
        engine.execute("CREATE TABLE seed.s6.t (id INTEGER)");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP SCHEMA seed.s6 RESTRICT");
            }
        });
        assertTrue(schemaExists("S6"));
    }

    /** True when database {@code seed} still has a schema of this name, per SHOW SCHEMAS. */
    private boolean schemaExists(final String schemaName) {
        final ResultSet rs = engine.executeQuery("SHOW SCHEMAS IN DATABASE seed");
        for (final Row row : rs.getRows()) {
            for (int i = 0; i < row.getValues().size(); i++) {
                if (schemaName.equalsIgnoreCase(String.valueOf(row.getValue(i)))) {
                    return true;
                }
            }
        }
        return false;
    }
}
