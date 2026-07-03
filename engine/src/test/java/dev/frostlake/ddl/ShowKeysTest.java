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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests SHOW PRIMARY KEYS / SHOW UNIQUE KEYS — one row per key column, for a named table or every table in
 * the current schema.
 */
public class ShowKeysTest extends BaseDatabaseTest {

    @Test
    public void showPrimaryKeysForTable() {
        engine.execute("CREATE TABLE pk_t (id INTEGER PRIMARY KEY, name VARCHAR)");
        final ResultSet rs = engine.executeQuery("SHOW PRIMARY KEYS IN TABLE pk_t");
        assertEquals(1, rs.getRows().size());
        assertEquals("ID", String.valueOf(rs.getRows().get(0).getValue(2)).toUpperCase());
    }

    @Test
    public void showUniqueKeysForTable() {
        engine.execute("CREATE TABLE uk_t (id INTEGER, email VARCHAR UNIQUE)");
        final ResultSet rs = engine.executeQuery("SHOW UNIQUE KEYS IN TABLE uk_t");
        assertEquals(1, rs.getRows().size());
        assertEquals("EMAIL", String.valueOf(rs.getRows().get(0).getValue(2)).toUpperCase());
    }

    @Test
    public void showPrimaryKeysAllTablesInSchema() {
        engine.execute("CREATE TABLE ka (x INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE kb (y INTEGER PRIMARY KEY)");
        final ResultSet rs = engine.executeQuery("SHOW PRIMARY KEYS");
        int seen = 0;
        for (final Row row : rs.getRows()) {
            final String table = String.valueOf(row.getValue(1)).toUpperCase();
            if ("KA".equals(table) || "KB".equals(table)) {
                seen++;
            }
        }
        assertTrue(seen >= 2, "SHOW PRIMARY KEYS should list PK columns of all tables in the schema");
    }
}
