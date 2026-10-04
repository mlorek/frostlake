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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INFORMATION_SCHEMA's LAST_ALTERED follows the last DDL or DML and LAST_DDL the last DDL, as documented. Both were
 * filled from the creation time, so neither moved for an INSERT, an ALTER TABLE, an ALTER VIEW or an ALTER SCHEMA.
 */
public class LastAlteredAndLastDdlTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE la");
        engine.execute("USE SCHEMA la.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private List<Object> row(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return row.getValues();
    }

    @Test
    public void aTablesTimesFollowItsWritesAndItsDdl() {
        engine.execute("CREATE TABLE x (a INT)");
        assertEquals(List.of(true, true), row("SELECT last_altered = created, last_ddl = created"
            + " FROM information_schema.tables WHERE table_name = 'X'"), "a new table: both are its creation");

        engine.execute("SET t = CURRENT_TIMESTAMP()");
        engine.execute("INSERT INTO x VALUES (1)");
        assertEquals(List.of(true, true), row("SELECT last_altered > $t, last_ddl = created"
            + " FROM information_schema.tables WHERE table_name = 'X'"), "a write moves LAST_ALTERED only");

        engine.execute("SET t = CURRENT_TIMESTAMP()");
        engine.execute("ALTER TABLE x ADD COLUMN b INT");
        assertEquals(List.of(true, true), row("SELECT last_altered > $t, last_ddl > $t"
            + " FROM information_schema.tables WHERE table_name = 'X'"), "an ALTER moves both");

        engine.execute("SET t = CURRENT_TIMESTAMP()");
        engine.execute("COMMENT ON COLUMN x.b IS 'b'");
        assertEquals(List.of(true), row("SELECT last_ddl > $t"
            + " FROM information_schema.tables WHERE table_name = 'X'"), "a comment is DDL too");
    }

    @Test
    public void viewsSchemasAndDatabasesMoveWithTheirDdl() {
        engine.execute("CREATE TABLE x (a INT)");
        engine.execute("CREATE VIEW v AS SELECT a FROM x");
        engine.execute("SET t = CURRENT_TIMESTAMP()");
        engine.execute("ALTER VIEW v SET COMMENT = 'v'");
        assertEquals(List.of(true, true), row("SELECT last_altered > $t, last_ddl > $t"
            + " FROM information_schema.views WHERE table_name = 'V'"), "VIEWS");
        assertEquals(List.of(true, true), row("SELECT last_altered > $t, last_ddl > $t"
            + " FROM information_schema.tables WHERE table_name = 'V'"), "TABLES' row for the view");

        engine.execute("SET t = CURRENT_TIMESTAMP()");
        engine.execute("ALTER SCHEMA la.PUBLIC SET COMMENT = 's'");
        assertEquals(List.of(true), row("SELECT last_altered > $t"
            + " FROM information_schema.schemata WHERE schema_name = 'PUBLIC'"), "SCHEMATA");

        engine.execute("SET t = CURRENT_TIMESTAMP()");
        engine.execute("ALTER DATABASE la SET COMMENT = 'd'");
        assertEquals(List.of(true), row("SELECT last_altered > $t"
            + " FROM information_schema.databases WHERE database_name = 'LA'"), "DATABASES");
    }
}
