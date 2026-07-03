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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests UNDROP TABLE / SCHEMA / DATABASE — restoring a recently dropped object. Tables restore both metadata
 * and row data (storage is purged on DROP TABLE and re-created from a snapshot); schemas and databases restore
 * the metadata object (their tables' storage survives the drop).
 */
public class UndropTest extends BaseDatabaseTest {

    private long count(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM " + table);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void undropTableRestoresData() {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'Alice'), (2, 'Bob')");
        engine.execute("DROP TABLE t");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM t");
            }
        });

        engine.execute("UNDROP TABLE t");
        assertEquals(2, count("t"));
        final ResultSet rs = engine.executeQuery("SELECT name FROM t WHERE id = 2");
        assertEquals("Bob", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void undropSchemaRestoresIt() {
        // DROP SCHEMA requires an empty schema (no CASCADE), so undrop is a metadata restore here.
        engine.execute("CREATE SCHEMA sc");
        engine.execute("DROP SCHEMA sc");

        engine.execute("UNDROP SCHEMA sc");
        // Restored and usable again.
        engine.execute("CREATE TABLE sc.tbl (id INTEGER)");
        engine.execute("INSERT INTO sc.tbl VALUES (1), (2)");
        assertEquals(2, count("sc.tbl"));
    }

    @Test
    public void undropDatabaseRestores() {
        engine.execute("CREATE DATABASE dbx");
        engine.execute("CREATE SCHEMA dbx.s1");
        engine.execute("CREATE TABLE dbx.s1.t1 (id INTEGER)");
        engine.execute("INSERT INTO dbx.s1.t1 VALUES (1)");
        engine.execute("DROP DATABASE dbx");

        engine.execute("UNDROP DATABASE dbx");
        assertEquals(1, count("dbx.s1.t1"));
    }

    @Test
    public void undropNonexistentFails() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("UNDROP TABLE never_existed");
            }
        });
    }
}
