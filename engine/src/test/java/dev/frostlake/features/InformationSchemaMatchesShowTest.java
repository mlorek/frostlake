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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two INFORMATION_SCHEMA cells that disagreed with SHOW on the same fact: TABLES.RETENTION_TIME was 1 for every table
 * whatever SHOW TABLES reported, and SCHEMATA.IS_MANAGED_ACCESS was NO for a schema SHOW SCHEMAS lists with the
 * MANAGED ACCESS option (live-verified: YES, a VARCHAR(3)).
 */
public class InformationSchemaMatchesShowTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE ims DATA_RETENTION_TIME_IN_DAYS = 3");
        engine.execute("USE SCHEMA ims.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    /** SHOW TABLES' retention_time for one table. */
    private String shownRetention(final String table) {
        final ResultSet shown = engine.executeQuery("SHOW TABLES LIKE '" + table + "'");
        return String.valueOf(shown.getRows().get(0).getValue(shown.getColumnIndex("retention_time")));
    }

    @Test
    public void retentionTimeIsWhatShowTablesReports() {
        engine.execute("CREATE TABLE own (a INT) DATA_RETENTION_TIME_IN_DAYS = 5");
        engine.execute("CREATE TABLE inherits (a INT)");
        engine.execute("CREATE TRANSIENT TABLE tr (a INT) DATA_RETENTION_TIME_IN_DAYS = 0");
        engine.execute("CREATE MATERIALIZED VIEW mv AS SELECT a FROM own");
        engine.execute("CREATE VIEW v AS SELECT a FROM own");
        for (final String table : new String[] {"OWN", "INHERITS", "TR"}) {
            assertEquals(shownRetention(table), String.valueOf(scalar("SELECT retention_time"
                + " FROM information_schema.tables WHERE table_name = '" + table + "'")), table);
        }
        assertEquals("5", String.valueOf(scalar("SELECT retention_time FROM information_schema.tables"
            + " WHERE table_name = 'OWN'")), "the table's own");
        assertEquals("3", String.valueOf(scalar("SELECT retention_time FROM information_schema.tables"
            + " WHERE table_name = 'INHERITS'")), "its database's");
        assertEquals("3", String.valueOf(scalar("SELECT retention_time FROM information_schema.tables"
            + " WHERE table_name = 'MV'")), "a materialized view's, as SHOW OBJECTS reports it");
        assertEquals("null", String.valueOf(scalar("SELECT retention_time FROM information_schema.tables"
            + " WHERE table_name = 'V'")), "a view keeps none");

        engine.execute("ALTER TABLE inherits SET DATA_RETENTION_TIME_IN_DAYS = 7");
        assertEquals("7", String.valueOf(scalar("SELECT retention_time FROM information_schema.tables"
            + " WHERE table_name = 'INHERITS'")), "an ALTER moves it");
    }

    @Test
    public void isManagedAccessIsTheSchemasFlag() {
        engine.execute("CREATE SCHEMA ms WITH MANAGED ACCESS");
        engine.execute("CREATE SCHEMA plain");
        assertEquals("YES", scalar("SELECT is_managed_access FROM information_schema.schemata"
            + " WHERE schema_name = 'MS'"));
        assertEquals("NO", scalar("SELECT is_managed_access FROM information_schema.schemata"
            + " WHERE schema_name = 'PLAIN'"));
        assertEquals("VARCHAR(3)[LOB]", scalar("SELECT SYSTEM$TYPEOF(is_managed_access)"
            + " FROM information_schema.schemata WHERE schema_name = 'MS'"));

        engine.execute("ALTER SCHEMA ms DISABLE MANAGED ACCESS");
        engine.execute("ALTER SCHEMA plain ENABLE MANAGED ACCESS");
        assertEquals("NO", scalar("SELECT is_managed_access FROM information_schema.schemata"
            + " WHERE schema_name = 'MS'"));
        assertEquals("YES", scalar("SELECT is_managed_access FROM information_schema.schemata"
            + " WHERE schema_name = 'PLAIN'"));
    }
}
