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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ALTER … RENAME accepts the canonical Snowflake {@code RENAME TO <name>} syntax (the {@code TO} keyword),
 * and RENAME COLUMN accepts {@code RENAME COLUMN a TO b}, while the no-TO form still parses.
 */
public class AlterRenameToTest extends BaseDatabaseTest {

    private long count(final String table) {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM " + table).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void renameTableWithTo() {
        engine.execute("CREATE TABLE r1 (id INTEGER)");
        engine.execute("INSERT INTO r1 VALUES (7)");
        engine.execute("ALTER TABLE r1 RENAME TO r2");
        assertEquals(1L, count("r2"));
    }

    @Test
    public void renameColumnWithTo() {
        engine.execute("CREATE TABLE rc (a INTEGER)");
        engine.execute("INSERT INTO rc VALUES (5)");
        engine.execute("ALTER TABLE rc RENAME COLUMN a TO b");
        final ResultSet rs = engine.executeQuery("SELECT b FROM rc");
        assertEquals(5L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void renameWithoutToStillParses() {
        engine.execute("CREATE TABLE r3 (id INTEGER)");
        engine.execute("ALTER TABLE r3 RENAME r4");
        assertEquals(0L, count("r4"));
    }

    @Test
    public void renameToQualifiedSameSchema() {
        // A qualified target in the same schema is just a rename in place.
        engine.execute("CREATE TABLE q1 (id INTEGER)");
        engine.execute("INSERT INTO q1 VALUES (7)");
        engine.execute("ALTER TABLE test_schema.q1 RENAME TO test_schema.q2");
        assertEquals(1L, count("q2"));
    }

    @Test
    public void renameToOtherSchemaMovesTableWithData() {
        engine.execute("CREATE SCHEMA s2");
        engine.execute("CREATE TABLE m1 (id INTEGER)");
        engine.execute("INSERT INTO m1 VALUES (1), (2)");
        engine.execute("ALTER TABLE test_schema.m1 RENAME TO s2.m2");

        assertEquals(2L, count("s2.m2"));                        // data moved with the table
        assertThrows(RuntimeException.class, new Executable() {  // gone from the source schema
            @Override
            public void execute() {
                engine.executeQuery("SELECT COUNT(*) FROM test_schema.m1");
            }
        });
    }

    @Test
    public void ifExistsQualifiedRenameMovesTable() {
        // The reported form: ALTER TABLE IF EXISTS s1.t1 RENAME TO s2.t2.
        engine.execute("CREATE SCHEMA s2");
        engine.execute("CREATE TABLE t1 (id INTEGER)");
        engine.execute("INSERT INTO t1 VALUES (9)");
        engine.execute("ALTER TABLE IF EXISTS test_schema.t1 RENAME TO s2.t2");
        assertEquals(1L, count("s2.t2"));
    }

    @Test
    public void ifExistsMissingSourceIsNoOp() {
        engine.execute("CREATE SCHEMA s2");
        // IF EXISTS on a missing source table must not raise.
        engine.execute("ALTER TABLE IF EXISTS test_schema.nope RENAME TO s2.whatever");
    }

    @Test
    public void moveToMissingSchemaErrors() {
        engine.execute("CREATE TABLE mm (id INTEGER)");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE test_schema.mm RENAME TO no_such_schema.x");
            }
        });
        assertTrue(ex.getMessage().toLowerCase().contains("schema"),
            "expected a missing-schema error: " + ex.getMessage());
    }

    @Test
    public void moveOntoExistingNameErrors() {
        engine.execute("CREATE SCHEMA s2");
        engine.execute("CREATE TABLE dup (id INTEGER)");
        engine.execute("CREATE TABLE s2.dup (id INTEGER)");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE test_schema.dup RENAME TO s2.dup");
            }
        });
        assertTrue(ex.getMessage().toLowerCase().contains("already exists"),
            "expected a name-collision error: " + ex.getMessage());
    }
}
