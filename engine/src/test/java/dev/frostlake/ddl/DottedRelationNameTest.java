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
 * A quoted relation or schema name may hold a dot, and it stays one name everywhere: created, read and
 * written bare or qualified, altered, renamed, listed, described, dropped and spelled back in a refusal
 * with its quotes. Every cell is live-verified.
 */
public class DottedRelationNameTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), sql + " -> " + refused.getMessage());
    }

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aDottedTableIsOneName() {
        engine.execute("CREATE OR REPLACE TABLE \"a.b\" (a INT)");
        engine.execute("INSERT INTO \"a.b\" VALUES (1)");
        assertEquals("1", scalar("SELECT * FROM \"a.b\""));
        engine.execute("UPDATE \"a.b\" SET a = 2");
        assertEquals("2", scalar("SELECT * FROM test_db.test_schema.\"a.b\""));
        assertEquals("2", scalar("SELECT * FROM test_schema.\"a.b\""));
        engine.execute("DELETE FROM \"a.b\"");
        assertEquals(0, engine.executeQuery("SELECT * FROM \"a.b\"").getRows().size());
        engine.execute("ALTER TABLE \"a.b\" ADD COLUMN c INT");
        engine.execute("INSERT INTO \"a.b\" (a, c) VALUES (5, 6)");
        engine.execute("MERGE INTO \"a.b\" t USING (SELECT 5 AS a) s ON t.a = s.a WHEN MATCHED THEN UPDATE SET c = 7");
        assertEquals("7", String.valueOf(engine.executeQuery("SELECT a, c FROM \"a.b\"").getRows().get(0).getValue(1)));
        assertEquals("5", scalar("SELECT \"a.b\".a FROM \"a.b\""));
        final ResultSet tables = engine.executeQuery("SHOW TABLES LIKE 'a.b'");
        assertEquals("a.b", cell(tables, tables.getRows().get(0), "name"));
        assertEquals("A", scalar("DESCRIBE TABLE \"a.b\""));
        engine.execute("CREATE OR REPLACE TABLE \"a.b.c\" (a INT)");
        engine.execute("INSERT INTO \"a.b.c\" VALUES (1)");
        assertEquals("1", scalar("SELECT COUNT(*) FROM \"a.b.c\""));
        engine.execute("ALTER TABLE \"a.b\" RENAME TO \"c.d\"");
        assertEquals("5", scalar("SELECT a FROM \"c.d\""));
        engine.execute("DROP TABLE \"c.d\"");
    }

    @Test
    public void aDottedCtasViewAndDdl() {
        engine.execute("CREATE OR REPLACE TABLE \"p.q\" AS SELECT 1 AS a");
        assertEquals("create or replace TABLE \"p.q\" (\n\tA NUMBER(1,0)\n);", scalar("SELECT GET_DDL('TABLE', '\"p.q\"')"));
        engine.execute("TRUNCATE TABLE \"p.q\"");
        assertEquals(0, engine.executeQuery("SELECT * FROM \"p.q\"").getRows().size());
        engine.execute("CREATE OR REPLACE VIEW \"v.w\" AS SELECT 1 AS a");
        assertEquals("1", scalar("SELECT * FROM \"v.w\""));
        engine.execute("DROP VIEW \"v.w\"");
    }

    @Test
    public void aDottedSchemaIsOneName() {
        engine.execute("CREATE SCHEMA \"s.x\"");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE TABLE \"s.x\".t (a INT)");
        engine.execute("INSERT INTO \"s.x\".t VALUES (1)");
        assertEquals("1", scalar("SELECT * FROM test_db.\"s.x\".t"));
        engine.execute("USE SCHEMA test_db.\"s.x\"");
        assertEquals("s.x", scalar("SELECT CURRENT_SCHEMA()"));
        assertEquals("1", scalar("SELECT * FROM t"));
        assertRefused("SELECT * FROM \"c.d\"", "Object '\"c.d\"' does not exist or not authorized.");
        assertRefused("DROP TABLE \"c.d\"", "Table 'TEST_DB.\"s.x\".\"c.d\"' does not exist or not authorized.");
        assertRefused("DROP VIEW \"v.w\"", "View 'TEST_DB.\"s.x\".\"v.w\"' does not exist or not authorized.");
        engine.execute("USE SCHEMA test_db.test_schema");
    }
}
