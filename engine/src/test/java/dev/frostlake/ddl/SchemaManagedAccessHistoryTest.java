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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Managed access schemas, and SHOW SCHEMAS | DATABASES HISTORY listing what UNDROP can still restore. */
public class SchemaManagedAccessHistoryTest extends BaseDatabaseTest {

    private String options(final String database, final String schema) {
        final ResultSet rs = engine.executeQuery("SHOW SCHEMAS LIKE '" + schema + "' IN DATABASE " + database);
        return cell(rs, soleRowWhere(rs, "name", schema), "options");
    }

    @Test
    public void managedAccessIsDeclaredEnabledAndDisabled() {
        engine.execute("CREATE DATABASE ma_db");
        engine.execute("CREATE SCHEMA ma_db.m WITH MANAGED ACCESS COMMENT = 'm'");
        assertEquals("MANAGED ACCESS", options("ma_db", "M"));
        engine.execute("ALTER SCHEMA ma_db.m DISABLE MANAGED ACCESS");
        assertEquals("", options("ma_db", "M"));
        engine.execute("ALTER DATABASE ma_db SET DATA_RETENTION_TIME_IN_DAYS = 3");
        engine.execute("CREATE TRANSIENT SCHEMA ma_db.t");
        engine.execute("ALTER SCHEMA ma_db.t ENABLE MANAGED ACCESS");
        assertEquals("TRANSIENT, MANAGED ACCESS", options("ma_db", "T"));
        final ResultSet rs = engine.executeQuery("SHOW SCHEMAS LIKE 'T' IN DATABASE ma_db");
        assertEquals("1", cell(rs, soleRowWhere(rs, "name", "T"), "retention_time"),
            "a transient schema keeps at most one day of its database's retention");
    }

    @Test
    public void schemasHistoryListsADroppedSchemaUntilItIsUndropped() {
        engine.execute("CREATE DATABASE hist_db");
        engine.execute("CREATE SCHEMA hist_db.gone");
        engine.execute("DROP SCHEMA hist_db.gone");
        final ResultSet plain = engine.executeQuery("SHOW SCHEMAS IN DATABASE hist_db");
        assertTrue(rowsWhere(plain, "name", "GONE").isEmpty());
        assertFalse(plain.getColumns().get(plain.getColumns().size() - 1).getName().equals("dropped_on"));
        final ResultSet history = engine.executeQuery("SHOW SCHEMAS HISTORY IN DATABASE hist_db");
        assertNotNull(cell(history, soleRowWhere(history, "name", "GONE"), "dropped_on"));
        assertNull(cell(history, soleRowWhere(history, "name", "PUBLIC"), "dropped_on"));
        final ResultSet restored = engine.executeQuery("UNDROP SCHEMA hist_db.gone");
        assertEquals("Schema GONE successfully restored.", restored.getRows().get(0).getValue(0));
        final RuntimeException never = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("UNDROP SCHEMA hist_db.never_dropped");
            }
        });
        assertEquals("Schema NEVER_DROPPED did not exist or was purged.", never.getMessage());
        final ResultSet after = engine.executeQuery("SHOW SCHEMAS HISTORY LIKE 'GONE' IN DATABASE hist_db");
        assertNull(cell(after, soleRowWhere(after, "name", "GONE"), "dropped_on"));
    }

    @Test
    public void databasesHistoryListsADroppedDatabase() {
        engine.execute("CREATE DATABASE hist_gone");
        engine.execute("DROP DATABASE hist_gone");
        assertEquals(0, engine.executeQuery("SHOW DATABASES LIKE 'HIST_GONE'").getRows().size());
        final ResultSet history = engine.executeQuery("SHOW DATABASES HISTORY LIKE 'HIST_GONE'");
        assertNotNull(cell(history, soleRowWhere(history, "name", "HIST_GONE"), "dropped_on"));
    }
}
