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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ALTER TABLE ... ADD COLUMN on a table that holds rows: a NOT NULL column needs a non-null default to fill the rows
 * with, and a column whose value is drawn for each row — AUTOINCREMENT, IDENTITY, a sequence's NEXTVAL — is refused
 * outright; every column of the statement is judged before any is added. An empty table takes them all
 * (live-verified).
 */
public class AddColumnToRowsTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    private String first(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    private static String unfillable(final String column, final String table) {
        return "SQL compilation error: Non-nullable column '" + column + "' cannot be added to non-empty table '"
            + table + "' unless it has a non-null default value.";
    }

    private static String drawn(final String column, final String table) {
        return "SQL compilation error: Cannot add column '" + column + "' with non-constant default to non-empty"
            + " table '" + table + "'.";
    }

    @Test
    public void aNotNullColumnNeedsANonNullDefault() {
        engine.execute("CREATE TABLE t (a INT, b VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'x'), (2, 'y')");
        assertEquals(unfillable("D", "T"), refusal("ALTER TABLE t ADD COLUMN d VARCHAR NOT NULL"));
        assertEquals(unfillable("I", "T"), refusal("ALTER TABLE t ADD COLUMN IF NOT EXISTS i VARCHAR NOT NULL"));
        assertEquals(unfillable("J", "T"), refusal("ALTER TABLE t ADD COLUMN j VARCHAR DEFAULT NULL NOT NULL"));
        engine.execute("ALTER TABLE t ADD COLUMN e VARCHAR DEFAULT 'q' NOT NULL");
        assertEquals("q", first("SELECT e FROM t WHERE a = 2"));
        engine.execute("UPDATE t SET b = 'z' WHERE a = 1");
    }

    @Test
    public void everyColumnIsJudgedBeforeAnyIsAdded() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        assertEquals(unfillable("G2", "T"), refusal("ALTER TABLE t ADD COLUMN h2 INT, g2 VARCHAR NOT NULL"));
        assertEquals(drawn("K3", "T"), refusal("ALTER TABLE t ADD COLUMN ok1 INT, k3 INT AUTOINCREMENT"));
        assertEquals(1, engine.executeQuery("SELECT * FROM t").getColumns().size());
    }

    @Test
    public void aValueDrawnForEachRowIsRefused() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE SEQUENCE sq_add");
        assertEquals(drawn("K", "T"), refusal("ALTER TABLE t ADD COLUMN k INT AUTOINCREMENT"));
        assertEquals(drawn("K2", "T"), refusal("ALTER TABLE t ADD COLUMN k2 INT IDENTITY"));
        assertEquals(drawn("K4", "T"), refusal("ALTER TABLE t ADD COLUMN k4 INT AUTOINCREMENT NOT NULL"));
        assertEquals(drawn("SQ", "T"), refusal("ALTER TABLE t ADD COLUMN sq INT DEFAULT sq_add.NEXTVAL"));
    }

    @Test
    public void anEmptyTableTakesThemAll() {
        engine.execute("CREATE TABLE e (a INT)");
        engine.execute("CREATE SEQUENCE sq_empty");
        engine.execute("ALTER TABLE e ADD COLUMN d VARCHAR NOT NULL");
        engine.execute("ALTER TABLE e ADD COLUMN k INT AUTOINCREMENT");
        engine.execute("ALTER TABLE e ADD COLUMN sq INT DEFAULT sq_empty.NEXTVAL");
        assertEquals(4, engine.executeQuery("SELECT * FROM e").getColumns().size());
    }

    @Test
    public void aTransientTableIsHeldAlike() {
        engine.execute("CREATE TRANSIENT TABLE tt (a INT)");
        engine.execute("INSERT INTO tt VALUES (1)");
        assertEquals(unfillable("D", "TT"), refusal("ALTER TABLE tt ADD COLUMN d VARCHAR NOT NULL"));
    }
}
