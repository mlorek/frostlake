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

/**
 * The SNOWFLAKE database cannot be cloned, renamed, altered, commented on, dropped or replaced, and its schemas are
 * its own. The read-only flag it was given never stopped any of that: the one check reading it passes the database by
 * on purpose, because its PUBLIC schema is the engine's default working schema, which stays writable.
 */
public class SnowflakeDatabaseIsReadOnlyTest {

    private static final String READ_ONLY = "Database 'SNOWFLAKE' is read-only";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE p");
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

    private String scalar(final String sql) {
        final List<Row> rows = engine.executeQuery(sql).getRows();
        return rows.isEmpty() ? "no row" : String.valueOf(rows.get(0).getValue(0));
    }

    @Test
    public void theDatabaseItselfIsNotChanged() {
        assertEquals(READ_ONLY, refusal("CREATE DATABASE snow_copy CLONE SNOWFLAKE"));
        assertEquals(READ_ONLY, refusal("ALTER DATABASE SNOWFLAKE RENAME TO snow2"));
        assertEquals(READ_ONLY, refusal("ALTER DATABASE SNOWFLAKE SET COMMENT = 'x'"));
        assertEquals(READ_ONLY, refusal("ALTER DATABASE SNOWFLAKE SET DATA_RETENTION_TIME_IN_DAYS = 5"));
        assertEquals(READ_ONLY, refusal("COMMENT ON DATABASE SNOWFLAKE IS 'y'"));
        assertEquals(READ_ONLY, refusal("DROP DATABASE SNOWFLAKE"));
        assertEquals(READ_ONLY, refusal("DROP DATABASE IF EXISTS SNOWFLAKE"));
        assertEquals(READ_ONLY, refusal("CREATE OR REPLACE DATABASE SNOWFLAKE"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM p.INFORMATION_SCHEMA.DATABASES WHERE DATABASE_NAME = 'SNOWFLAKE'"
            + " AND COMMENT IS NULL"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM p.INFORMATION_SCHEMA.DATABASES"
            + " WHERE DATABASE_NAME IN ('SNOW_COPY', 'SNOW2')"));
        // Over the database it already is, IF NOT EXISTS changes nothing and says so.
        engine.execute("CREATE DATABASE IF NOT EXISTS SNOWFLAKE");
    }

    @Test
    public void itsSchemasAreItsOwn() {
        assertEquals(READ_ONLY, refusal("CREATE SCHEMA SNOWFLAKE.mine"));
        assertEquals(READ_ONLY, refusal("CREATE OR REPLACE SCHEMA SNOWFLAKE.PUBLIC"));
        assertEquals(READ_ONLY, refusal("CREATE SCHEMA SNOWFLAKE.c2 CLONE p.PUBLIC"));
        assertEquals(READ_ONLY, refusal("ALTER SCHEMA SNOWFLAKE.PUBLIC RENAME TO SNOWFLAKE.pub2"));
        assertEquals(READ_ONLY, refusal("ALTER SCHEMA SNOWFLAKE.PUBLIC SET COMMENT = 'c'"));
        assertEquals(READ_ONLY, refusal("COMMENT ON SCHEMA SNOWFLAKE.PUBLIC IS 'c'"));
        assertEquals(READ_ONLY, refusal("DROP SCHEMA SNOWFLAKE.PUBLIC"));
        engine.execute("CREATE SCHEMA p.s");
        assertEquals(READ_ONLY, refusal("ALTER SCHEMA p.s RENAME TO SNOWFLAKE.s"));
        // None was added: the schemas are the database's own and its working schema.
        assertEquals("ACCOUNT_USAGE,ALERT,BCR_ROLLOUT,BILLING,CORE,CORTEX,DATA_PRIVACY,DATA_SHARING_USAGE,IMAGES,"
            + "MARKETPLACE_NOTIFICATION,ML,MONITORING,NOTIFICATION,ORGANIZATION_USAGE,PUBLIC,READER_ACCOUNT_USAGE,"
            + "SNOWPARK,SNOWPARK_CONNECT,WORKLOAD_INSIGHTS,WORKLOAD_OPTIMIZATION",
            scalar("SELECT LISTAGG(SCHEMA_NAME, ',') WITHIN GROUP (ORDER BY SCHEMA_NAME)"
                + " FROM SNOWFLAKE.INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME <> 'INFORMATION_SCHEMA'"));
        // Neither IF form changes anything, and neither refuses.
        engine.execute("CREATE SCHEMA IF NOT EXISTS SNOWFLAKE.PUBLIC");
        engine.execute("DROP SCHEMA IF EXISTS SNOWFLAKE.nope");
    }

    @Test
    public void itsPublicSchemaStaysTheDefaultWorkingSchema() {
        engine.execute("USE SCHEMA SNOWFLAKE.PUBLIC");
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("ALTER TABLE t ADD COLUMN b INT");
        engine.execute("UPDATE t SET b = 2");
        engine.execute("CREATE VIEW v AS SELECT a + b s FROM t");
        assertEquals("3", scalar("SELECT s FROM SNOWFLAKE.PUBLIC.v"));
        // And what is there can be cloned out of it.
        engine.execute("CREATE SCHEMA p.from_snowflake CLONE SNOWFLAKE.PUBLIC");
        assertEquals("1", scalar("SELECT COUNT(*) FROM p.from_snowflake.t"));
    }
}
