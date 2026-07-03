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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IDENTIFIER('<name>') supplies an object name dynamically in USE and DROP TABLE (in addition to the
 * already-supported table-source position), so a name held in a string/variable resolves to the object.
 */
public class IdentifierFunctionTest extends BaseDatabaseTest {

    @Test
    public void dropTableViaIdentifier() {
        engine.execute("CREATE TABLE dt (id INTEGER)");
        engine.execute("DROP TABLE IDENTIFIER('dt')");
        // Table is gone — querying it now fails.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT COUNT(*) FROM dt");
            }
        });
    }

    @Test
    public void useDatabaseViaIdentifier() {
        engine.execute("CREATE DATABASE db_ident");
        engine.execute("USE DATABASE IDENTIFIER('db_ident')");
        final Object cur = engine.executeQuery("SELECT CURRENT_DATABASE()").getRows().get(0).getValue(0);
        assertTrue("db_ident".equalsIgnoreCase(cur.toString()), cur.toString());
    }

    @Test
    public void createTableViaIdentifier() {
        engine.execute("CREATE TABLE IDENTIFIER('ct_ident') (id INTEGER)");
        engine.execute("INSERT INTO ct_ident VALUES (42)");
        assertEquals(42L, ((Number) engine.executeQuery("SELECT id FROM ct_ident")
            .getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void plainCreateTableStillWorks() {
        engine.execute("CREATE TABLE ct_plain (a INTEGER, b VARCHAR)");
        engine.execute("INSERT INTO ct_plain VALUES (1, 'x')");
        assertEquals(1L, ((Number) engine.executeQuery("SELECT a FROM ct_plain")
            .getRows().get(0).getValue(0)).longValue());
    }

    private long scalar(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void insertIntoViaIdentifier() {
        engine.execute("CREATE TABLE ins_ident (id INTEGER)");
        engine.execute("INSERT INTO IDENTIFIER('ins_ident') VALUES (7)");
        assertEquals(7L, scalar("SELECT id FROM ins_ident"));
    }

    @Test
    public void updateViaIdentifier() {
        engine.execute("CREATE TABLE upd_ident (id INTEGER, v INTEGER)");
        engine.execute("INSERT INTO upd_ident VALUES (1, 10)");
        engine.execute("UPDATE IDENTIFIER('upd_ident') SET v = 99 WHERE id = 1");
        assertEquals(99L, scalar("SELECT v FROM upd_ident WHERE id = 1"));
    }

    @Test
    public void deleteViaIdentifier() {
        engine.execute("CREATE TABLE del_ident (id INTEGER)");
        engine.execute("INSERT INTO del_ident VALUES (1), (2)");
        engine.execute("DELETE FROM IDENTIFIER('del_ident') WHERE id = 1");
        assertEquals(1L, scalar("SELECT COUNT(*) FROM del_ident"));
    }

    @Test
    public void singleColumnInsertListStillWorks() {
        // The case that broke the loose approach: a single-column list must NOT be read as IDENTIFIER(expr).
        engine.execute("CREATE TABLE sc (n INTEGER)");
        engine.execute("INSERT INTO sc (n) VALUES (5)");
        assertEquals(5L, scalar("SELECT n FROM sc"));
    }

    @Test
    public void identifierWordStillUsableAsAName() {
        // "identifier" now lexes as KW_IDENTIFIER but must still work as a plain column and table name.
        engine.execute("CREATE TABLE identifier (identifier INTEGER, other INTEGER)");
        engine.execute("INSERT INTO identifier (identifier, other) VALUES (3, 4)");
        assertEquals(3L, scalar("SELECT identifier FROM identifier"));
    }

    @Test
    public void plainDropAndUseStillWork() {
        engine.execute("CREATE TABLE dt2 (id INTEGER)");
        engine.execute("DROP TABLE dt2");
        engine.execute("USE DATABASE test_db");
    }
}
