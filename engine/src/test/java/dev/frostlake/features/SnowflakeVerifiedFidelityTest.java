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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviors verified against LIVE Snowflake (the 10.2k-statement replay campaign): session-context
 * activation on CREATE DATABASE/SCHEMA and USE DATABASE, bare-INSERT arity, FOREIGN KEY local-column
 * validation, SELECT ALL, FORMAT as a function name, DROP SCHEMA PUBLIC / RESTRICT semantics,
 * snappy COMPRESS, and right-side parenthesized joins.
 */
public class SnowflakeVerifiedFidelityTest extends BaseDatabaseTest {

    private static final String EMBEDDED_SESSION_CONTEXT =
        "reads the active database/schema off engine.getCatalog(), which under SF_LIVE is still "
        + "the EMBEDDED catalog — the CREATE DATABASE/SCHEMA moved the context of the SNOWFLAKE "
        + "session, not of the embedded engine, so the accessor reports the embedded default";

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void createSchemaActivatesTheNewSchema() {
        Assumptions.assumeFalse(isLiveSnowflake(), EMBEDDED_SESSION_CONTEXT);
        engine.execute("CREATE TABLE ctx_t (id INTEGER)");
        engine.execute("CREATE SCHEMA ctx_other");
        assertEquals("CTX_OTHER", engine.getCatalog().getCurrentSchema());
        // Unqualified names now land in the new schema — no collision with test_schema's table.
        engine.execute("CREATE TABLE ctx_t (id INTEGER)");
        engine.execute("USE SCHEMA test_schema");
    }

    @Test
    public void createDatabaseActivatesItWithPublicSchema() {
        Assumptions.assumeFalse(isLiveSnowflake(), EMBEDDED_SESSION_CONTEXT);
        engine.execute("CREATE DATABASE ctx_db");
        assertEquals("CTX_DB", engine.getCatalog().getCurrentDatabase());
        assertEquals("PUBLIC", engine.getCatalog().getCurrentSchema());
        engine.execute("USE DATABASE test_db");
        assertEquals("PUBLIC", engine.getCatalog().getCurrentSchema(),
            "USE DATABASE resets the current schema to PUBLIC (live-verified)");
        engine.execute("USE SCHEMA test_schema");
    }

    @Test
    public void bareInsertRequiresTheExactColumnCount() {
        engine.execute("CREATE TABLE arity_t (a INTEGER, b INTEGER, c INTEGER)");
        final RuntimeException few = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO arity_t VALUES (1, 2)");
            }
        });
        assertTrue(few.getMessage().contains("expecting 3 but got 2"), "unexpected: " + few.getMessage());
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO arity_t VALUES (1, 2, 3, 4)");
            }
        });
        // An explicit column list still allows omitting defaulted columns.
        engine.execute("INSERT INTO arity_t (a, b) VALUES (1, 2)");
        assertEquals(1L, ((Number) scalar("SELECT COUNT(*) FROM arity_t")).longValue());
    }

    @Test
    public void addForeignKeyValidatesTheLocalColumn() {
        engine.execute("CREATE TABLE fk_dept (dept_id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE fk_emp (emp_id INTEGER)");
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE fk_emp ADD FOREIGN KEY (dept_id) REFERENCES fk_dept (dept_id)");
            }
        });
        assertTrue(e.getMessage().contains("invalid identifier 'DEPT_ID'"), "unexpected: " + e.getMessage());
    }

    @Test
    public void selectAllIsTheDistinctCounterpart() {
        engine.execute("CREATE TABLE all_t (n INTEGER)");
        engine.execute("INSERT INTO all_t VALUES (1), (1), (2)");
        assertEquals(3, engine.executeQuery("SELECT ALL n FROM all_t").getRowCount());
        assertEquals(2, engine.executeQuery("SELECT DISTINCT n FROM all_t").getRowCount());
    }

    @Test
    public void formatIsUsableAsAFunctionName() {
        engine.execute("CREATE FUNCTION format(v VARCHAR) RETURNS VARCHAR AS $$ v $$");
        assertEquals("x", scalar("SELECT format('x')"));
    }

    @Test
    public void dropSchemaMatchesLiveSemantics() {
        engine.execute("CREATE DATABASE drop_sem_db");   // activates it; current schema = PUBLIC
        engine.execute("USE SCHEMA information_schema");
        engine.execute("DROP SCHEMA drop_sem_db.PUBLIC");
        // RESTRICT drops a NON-EMPTY schema too (live-verified against Snowflake).
        engine.execute("CREATE SCHEMA drop_sem_db.rs");
        engine.execute("CREATE TABLE drop_sem_db.rs.t (id INTEGER)");
        engine.execute("DROP SCHEMA drop_sem_db.rs RESTRICT");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA test_schema");
    }

    @Test
    public void snappyCompressRoundTrips() {
        assertEquals("hello snappy",
            scalar("SELECT DECOMPRESS_STRING(COMPRESS('hello snappy', 'snappy'), 'snappy')"));
    }

    @Test
    public void parenthesizedJoinOnTheRightSideOfAJoin() {
        engine.execute("CREATE TABLE pj_orders (order_id INTEGER)");
        engine.execute("CREATE TABLE pj_ship (order_id INTEGER, s VARCHAR)");
        engine.execute("INSERT INTO pj_orders VALUES (1), (2)");
        engine.execute("INSERT INTO pj_ship VALUES (1, 'a')");
        assertEquals(1L, ((Number) scalar(
            "SELECT COUNT(*) FROM pj_orders o JOIN ( pj_ship s JOIN pj_orders o2 ON s.order_id = o2.order_id ) "
            + "ON o.order_id = s.order_id")).longValue());
    }
}
