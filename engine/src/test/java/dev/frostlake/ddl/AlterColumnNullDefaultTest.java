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
 * Tests ALTER TABLE ... ALTER COLUMN SET/DROP NOT NULL and SET/DROP DEFAULT, verified through their effect on
 * INSERT (NOT NULL rejects NULLs; a set default fills an omitted column).
 */
public class AlterColumnNullDefaultTest extends BaseDatabaseTest {

    @Test
    public void setThenDropNotNull() {
        engine.execute("CREATE TABLE nn_t (id INTEGER, name VARCHAR)");
        engine.execute("ALTER TABLE nn_t ALTER COLUMN name SET NOT NULL");

        // A NULL into the now-NOT-NULL column is rejected.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO nn_t VALUES (1, NULL)");
            }
        });

        // DROP NOT NULL makes it nullable again.
        engine.execute("ALTER TABLE nn_t ALTER COLUMN name DROP NOT NULL");
        engine.execute("INSERT INTO nn_t VALUES (2, NULL)");

        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM nn_t");
        assertEquals(1, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void setDefaultLiteralIsRejected() {
        // Live-Snowflake verified: ALTER COLUMN ... SET DEFAULT <literal> is unsupported (sequence
        // defaults only); a CREATE-time default still fills the omitted column.
        engine.execute("CREATE TABLE def_t (id INTEGER, qty INTEGER DEFAULT 5)");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE def_t ALTER COLUMN qty SET DEFAULT 7");
            }
        });

        engine.execute("INSERT INTO def_t (id) VALUES (1)");
        final ResultSet rs = engine.executeQuery("SELECT qty FROM def_t WHERE id = 1");
        assertEquals(5, ((Number) rs.getRows().get(0).getValue(0)).intValue());
    }

    private int columnCount(final String table) {
        return engine.executeQuery("DESCRIBE TABLE " + table).getRows().size();
    }

    /**
     * Regression: {@code ALTER COLUMN col DROP NOT NULL} and {@code DROP DEFAULT} must alter the column in place,
     * not delete it. Both statements carry DROP + COLUMN tokens, so they were wrongly dispatched to the
     * table-level DROP COLUMN handler and silently removed the column (dropping the table from 2 columns to 1).
     */
    @Test
    public void alterColumnDropKeepsTheColumn() {
        engine.execute("CREATE TABLE keep_t (id INTEGER, name VARCHAR)");
        assertEquals(2, columnCount("keep_t"));

        engine.execute("ALTER TABLE keep_t ALTER COLUMN name SET NOT NULL");
        engine.execute("ALTER TABLE keep_t ALTER COLUMN name DROP NOT NULL");
        assertEquals(2, columnCount("keep_t"));

        engine.execute("ALTER TABLE keep_t ALTER COLUMN name DROP DEFAULT");
        assertEquals(2, columnCount("keep_t"));

        // DROP NOT NULL genuinely made the column nullable again, and the column still accepts data.
        engine.execute("INSERT INTO keep_t (id, name) VALUES (1, NULL)");
        assertEquals(1, ((Number) engine.executeQuery("SELECT COUNT(*) FROM keep_t")
            .getRows().get(0).getValue(0)).intValue());

        // The genuine table-level DROP COLUMN still removes the column.
        engine.execute("ALTER TABLE keep_t DROP COLUMN name");
        assertEquals(1, columnCount("keep_t"));
    }
}
