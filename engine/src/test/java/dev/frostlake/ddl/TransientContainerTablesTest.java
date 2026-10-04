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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every table in a transient schema or database is transient (documented). Only GET_DDL said so: a table created
 * there, or brought there by a TRANSIENT clone of a permanent database or schema, was a permanent TABLE to SHOW
 * TABLES and INFORMATION_SCHEMA and took any retention. A transient table now keeps a day at most, as its transient
 * containers do, and stays transient when it moves out.
 */
public class TransientContainerTablesTest {

    private static final String CAPPED = "invalid value [5] for parameter 'DATA_RETENTION_TIME_IN_DAYS'";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.PUBLIC");
        engine.execute("CREATE TABLE pt (a INT)");
        engine.execute("INSERT INTO pt VALUES (1)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    /** SHOW TABLES' kind of one table. */
    private String kind(final String schema, final String table) {
        for (final Row row : engine.executeQuery("SHOW TABLES LIKE '" + table + "' IN SCHEMA " + schema).getRows()) {
            return String.valueOf(row.getValue(4));
        }
        return "no table";
    }

    private String isTransient(final String database, final String schema, final String table) {
        final List<Row> rows = engine.executeQuery("SELECT IS_TRANSIENT FROM " + database
            + ".INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = '" + schema + "' AND TABLE_NAME = '" + table + "'")
            .getRows();
        return rows.isEmpty() ? "no table" : String.valueOf(rows.get(0).getValue(0));
    }

    @Test
    public void aTableCreatedInATransientSchemaIsTransient() {
        engine.execute("CREATE TRANSIENT SCHEMA p.ts");
        engine.execute("CREATE TABLE p.ts.t (a INT)");
        engine.execute("CREATE TABLE p.ts.c CLONE p.PUBLIC.pt");
        engine.execute("CREATE TABLE p.ts.l LIKE p.PUBLIC.pt");
        engine.execute("CREATE TABLE p.ts.ctas AS SELECT 1 x");
        for (final String table : new String[] {"T", "C", "L", "CTAS"}) {
            assertEquals("TRANSIENT", kind("p.ts", table), table);
            assertEquals("YES", isTransient("p", "TS", table), table);
        }
        engine.execute("CREATE TEMPORARY TABLE p.ts.tmp (a INT)");
        assertEquals("TEMPORARY", kind("p.ts", "TMP"));
        engine.execute("CREATE DYNAMIC TABLE p.ts.dt TARGET_LAG = '1 minute' WAREHOUSE = COMPUTE_WH"
            + " AS SELECT a FROM p.PUBLIC.pt");
        assertEquals("TRANSIENT", kind("p.ts", "DT"));
    }

    @Test
    public void aTableInATransientDatabaseIsTransient() {
        engine.execute("CREATE TRANSIENT DATABASE d4");
        engine.execute("CREATE TABLE d4.PUBLIC.x (a INT)");
        assertEquals("TRANSIENT", kind("d4.PUBLIC", "X"));
        assertEquals("YES", isTransient("d4", "PUBLIC", "X"));
    }

    @Test
    public void aTransientCloneOfAPermanentDatabaseHoldsTransientTables() {
        engine.execute("CREATE TRANSIENT DATABASE d2 CLONE p");
        assertEquals("TRANSIENT", kind("d2.PUBLIC", "PT"));
        assertEquals("YES", isTransient("d2", "PUBLIC", "PT"));
        assertEquals("1", String.valueOf(engine.executeQuery("SELECT COUNT(*) FROM d2.PUBLIC.pt").getRows().get(0)
            .getValue(0)));
        assertTrue(String.valueOf(engine.executeQuery("SELECT GET_DDL('TABLE', 'd2.PUBLIC.pt')").getRows().get(0)
            .getValue(0)).startsWith("create or replace TRANSIENT TABLE PT"));
        // The source keeps its permanent table.
        assertEquals("TABLE", kind("p.PUBLIC", "PT"));
        // A transient table moved out of its transient database stays transient.
        engine.execute("ALTER TABLE d2.PUBLIC.pt RENAME TO p.PUBLIC.moved");
        assertEquals("TRANSIENT", kind("p.PUBLIC", "MOVED"));
    }

    @Test
    public void aTransientCloneOfAPermanentSchemaHoldsTransientTables() {
        engine.execute("CREATE TRANSIENT SCHEMA p.s2 CLONE p.PUBLIC");
        assertEquals("TRANSIENT", kind("p.s2", "PT"));
        assertEquals("YES", isTransient("p", "S2", "PT"));
        assertEquals("TABLE", kind("p.PUBLIC", "PT"));
    }

    @Test
    public void aTransientTableKeepsADayAtMost() {
        engine.execute("CREATE TRANSIENT SCHEMA p.ts");
        engine.execute("CREATE TABLE p.ts.t (a INT)");
        assertTrue(refusal("ALTER TABLE p.ts.t SET DATA_RETENTION_TIME_IN_DAYS = 5").contains(CAPPED));
        assertTrue(refusal("CREATE TABLE p.ts.t5 (a INT) DATA_RETENTION_TIME_IN_DAYS = 5").contains(CAPPED));
        assertTrue(refusal("CREATE TRANSIENT TABLE p.PUBLIC.tt (a INT) DATA_RETENTION_TIME_IN_DAYS = 5").contains(CAPPED));
        engine.execute("ALTER TABLE p.ts.t SET DATA_RETENTION_TIME_IN_DAYS = 0");
        engine.execute("ALTER TABLE p.PUBLIC.pt SET DATA_RETENTION_TIME_IN_DAYS = 5");
    }
}
