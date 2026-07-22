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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code CREATE HYBRID TABLE} (Snowflake's OLTP row-store table type) parses and is created as an ordinary
 * table — Frostlake does not model hybrid storage, so the {@code HYBRID} keyword is accepted and ignored.
 * Defaults, NOT NULL and the PRIMARY KEY constraint are kept, and the table is fully usable.
 */
public class HybridTableTest extends BaseDatabaseTest {

    @Test
    public void createHybridTableBehavesAsNormalTable() {
        engine.execute("""
            CREATE HYBRID TABLE IF NOT EXISTS h1 (
                k1      VARCHAR NOT NULL,
                k2      VARCHAR NOT NULL,
                status  VARCHAR DEFAULT 'active',
                CONSTRAINT pk_h1 PRIMARY KEY (k1, k2)
            )
            """);
        engine.execute("INSERT INTO h1 (k1, k2) VALUES ('t', 's')");

        final ResultSet rs = engine.executeQuery("SELECT status FROM h1");
        assertEquals("active", rs.getRows().get(0).getValue(0));

        // The PRIMARY KEY constraint survives (one row per key column in TABLE_CONSTRAINTS).
        final ResultSet pk = engine.executeQuery(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
            + "WHERE TABLE_NAME = 'H1' AND CONSTRAINT_TYPE = 'PRIMARY KEY'");
        assertEquals(2L, ((Number) pk.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void createHybridTableViaExecuteImmediate() {
        // HYBRID TABLE inside a dynamic-SQL string.
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute(
                    "EXECUTE IMMEDIATE 'CREATE HYBRID TABLE IF NOT EXISTS cfg "
                    + "(k1 VARCHAR NOT NULL, k2 VARCHAR NOT NULL, "
                    + "CONSTRAINT pk_cfg PRIMARY KEY (k1, k2))'");
            }
        });
    }

    @Test
    public void showHybridTablesListsOnlyHybridTables() {
        engine.execute("CREATE HYBRID TABLE hyb (a INT)");
        engine.execute("CREATE TABLE plain (a INT)");

        final ResultSet rs = engine.executeQuery("SHOW HYBRID TABLES");
        assertEquals(1, rs.getRowCount(), "only the hybrid table is listed");
        assertEquals("HYB", rs.getRows().get(0).getValue(1));
        assertEquals("HYBRID TABLE", rs.getRows().get(0).getValue(4));
    }

    @Test
    public void showTablesReportsHybridKind() {
        engine.execute("CREATE HYBRID TABLE hyb (a INT)");
        engine.execute("CREATE TABLE plain (a INT)");

        final ResultSet rs = engine.executeQuery("SHOW TABLES");
        String hybKind = null;
        String plainKind = null;
        for (int i = 0; i < rs.getRowCount(); i++) {
            final Object name = rs.getRows().get(i).getValue(1);
            if ("HYB".equals(name)) {
                hybKind = (String) rs.getRows().get(i).getValue(4);
            } else if ("PLAIN".equals(name)) {
                plainKind = (String) rs.getRows().get(i).getValue(4);
            }
        }
        assertEquals("HYBRID TABLE", hybKind);
        assertEquals("TABLE", plainKind);
    }

    @Test
    public void hybridStillUsableAsIdentifier() {
        // Adding the HYBRID keyword must not stop 'hybrid' being a plain column name.
        engine.execute("CREATE TABLE k (hybrid INT)");
        engine.execute("INSERT INTO k VALUES (7)");
        assertEquals(7, ((Number) engine.executeQuery("SELECT hybrid FROM k")
            .getRows().get(0).getValue(0)).intValue());
    }
}
