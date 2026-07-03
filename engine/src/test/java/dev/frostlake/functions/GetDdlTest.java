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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GetDdlTest extends BaseDatabaseTest {

    private String getDdl(final String type, final String name) {
        final ResultSet rs = engine.executeQuery("SELECT GET_DDL('" + type + "', '" + name + "')");
        return (String) rs.getRows().get(0).getValue(0);
    }

    @Test
    public void tableDdlCarriesColumnsTypesAndPrimaryKey() {
        engine.execute("""
            CREATE TABLE employees (
              id INTEGER NOT NULL,
              name VARCHAR(100),
              salary NUMBER(10,2) DEFAULT 0,
              PRIMARY KEY (id)
            )""");
        final String ddl = getDdl("TABLE", "employees");
        assertTrue(ddl.startsWith("create or replace TABLE "), ddl);
        assertTrue(ddl.contains("VARCHAR(100)"), ddl);
        assertTrue(ddl.contains("NUMBER(10,2)"), ddl);
        assertTrue(ddl.contains("NOT NULL"), ddl);
        assertTrue(ddl.contains("DEFAULT 0"), ddl);
        assertTrue(ddl.toLowerCase().contains("primary key (id)"), ddl);
    }

    @Test
    public void tableDdlRoundTrips() {
        engine.execute("""
            CREATE TABLE widgets (
              id INTEGER NOT NULL,
              label VARCHAR(50),
              price NUMBER(12,4) DEFAULT 1,
              PRIMARY KEY (id)
            )""");
        final String ddl = getDdl("TABLE", "widgets");
        // The reconstructed DDL must be valid (re-executable) and stable (idempotent).
        engine.execute(ddl);
        assertEquals(ddl, getDdl("TABLE", "widgets"));
    }

    @Test
    public void viewDdlRoundTrips() {
        engine.execute("CREATE TABLE base (x INTEGER, y INTEGER)");
        engine.execute("CREATE VIEW v AS SELECT x FROM base WHERE y > 0");
        final String ddl = getDdl("VIEW", "v");
        assertTrue(ddl.toLowerCase().startsWith("create or replace view v as "), ddl);
        assertTrue(ddl.toUpperCase().contains("SELECT"), ddl);
        engine.execute(ddl);
        assertEquals(ddl, getDdl("VIEW", "v"));
    }

    @Test
    public void sequenceDdlRoundTrips() {
        engine.execute("CREATE SEQUENCE seq1 START 5 INCREMENT 2");
        final String ddl = getDdl("SEQUENCE", "seq1");
        assertTrue(ddl.startsWith("create or replace sequence "), ddl);
        assertTrue(ddl.contains("start 5"), ddl);
        assertTrue(ddl.contains("increment 2"), ddl);
        engine.execute(ddl);
        assertEquals(ddl, getDdl("SEQUENCE", "seq1"));
    }

    @Test
    public void identityColumnRendersAutoincrement() {
        engine.execute("CREATE TABLE idt (id INTEGER IDENTITY(1,1), v VARCHAR(10))");
        final String ddl = getDdl("TABLE", "idt");
        assertTrue(ddl.contains("autoincrement start 1 increment 1"), ddl);
    }

    @Test
    public void unsupportedObjectTypeThrows() {
        engine.execute("CREATE TABLE t (a INTEGER)");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                getDdl("PIPE", "t");
            }
        });
    }
}
