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

import static org.junit.jupiter.api.Assertions.*;

public class SelectIntoTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    @Test
    public void testSelectIntoReturnsOne() {
        ResultSet rs = engine.executeQuery(
            "DECLARE\n" +
            "    s STRING DEFAULT '';\n" +
            "    b BOOLEAN DEFAULT TRUE;\n" +
            "    i INTEGER;\n" +
            "BEGIN\n" +
            "    CREATE OR REPLACE TABLE t(i INTEGER);\n" +
            "    INSERT INTO t VALUES (1);\n" +
            "\n" +
            "    SELECT i\n" +
            "    INTO i\n" +
            "    FROM t;\n" +
            "\n" +
            "    RETURN i;\n" +
            "END"
        );
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        Object val = rs.getRows().get(0).getValue(0);
        assertNotNull(val, "Block should return a value");
        assertEquals(1L, Long.parseLong(val.toString()), "Expected block to return 1");
    }

    @Test
    public void testSelectIntoMultipleVars() {
        ResultSet rs = engine.executeQuery(
            "DECLARE\n" +
            "    a INTEGER;\n" +
            "    b VARCHAR;\n" +
            "BEGIN\n" +
            "    CREATE OR REPLACE TABLE t2(n INTEGER, s VARCHAR);\n" +
            "    INSERT INTO t2 VALUES (42, 'hello');\n" +
            "    SELECT n, s INTO a, b FROM t2;\n" +
            "    RETURN a;\n" +
            "END"
        );
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(42L, Long.parseLong(rs.getRows().get(0).getValue(0).toString()));
    }

    @Test
    public void testSelectIntoWithLimit() {
        // LIMIT reduces a multi-row result to the single row that SELECT INTO requires.
        ResultSet rs = engine.executeQuery("""
            DECLARE
                v INTEGER;
            BEGIN
                CREATE OR REPLACE TABLE nums(n INTEGER);
                INSERT INTO nums VALUES (10), (20), (30);
                SELECT n INTO v FROM nums ORDER BY n DESC LIMIT 1;
                RETURN v;
            END
            """);
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(30L, Long.parseLong(rs.getRows().get(0).getValue(0).toString()));
    }

    @Test
    public void testSelectIntoWithLimitOffset() {
        ResultSet rs = engine.executeQuery("""
            DECLARE
                v INTEGER;
            BEGIN
                CREATE OR REPLACE TABLE nums(n INTEGER);
                INSERT INTO nums VALUES (10), (20), (30);
                SELECT n INTO v FROM nums ORDER BY n ASC LIMIT 1 OFFSET 1;
                RETURN v;
            END
            """);
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(20L, Long.parseLong(rs.getRows().get(0).getValue(0).toString()));
    }

    @Test
    public void testSelectIntoWithFetchFirst() {
        ResultSet rs = engine.executeQuery("""
            DECLARE
                v INTEGER;
            BEGIN
                CREATE OR REPLACE TABLE nums(n INTEGER);
                INSERT INTO nums VALUES (10), (20), (30);
                SELECT n INTO v FROM nums ORDER BY n DESC FETCH FIRST 1 ROW ONLY;
                RETURN v;
            END
            """);
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(30L, Long.parseLong(rs.getRows().get(0).getValue(0).toString()));
    }

    @Test
    public void testSelectIntoWithQualify() {
        // QUALIFY over a window function also narrows the result to one row before INTO assigns it.
        ResultSet rs = engine.executeQuery("""
            DECLARE
                v INTEGER;
            BEGIN
                CREATE OR REPLACE TABLE nums(n INTEGER);
                INSERT INTO nums VALUES (10), (20), (30);
                SELECT n INTO v FROM nums QUALIFY ROW_NUMBER() OVER (ORDER BY n DESC) = 1;
                RETURN v;
            END
            """);
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(30L, Long.parseLong(rs.getRows().get(0).getValue(0).toString()));
    }

    @Test
    public void testSelectIntoNoRowsAssignsNull() {
        // A SELECT INTO whose query matches nothing is not an error — the target becomes NULL and the
        // block continues, so the lookup simply returns NULL.
        ResultSet rs = engine.executeQuery("""
            DECLARE
                col_type STRING;
            BEGIN
                CREATE OR REPLACE TABLE cols(name STRING, data_type STRING);
                SELECT data_type INTO :col_type FROM cols WHERE name = 'missing' LIMIT 1;
                RETURN :col_type;
            END
            """);
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertNull(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testSelectIntoFromInformationSchemaNoMatchReturnsNull() {
        // The reported repro: a metadata lookup that matches no column yields NULL, not an error.
        ResultSet rs = engine.executeQuery("""
            DECLARE
                col_type STRING;
            BEGIN
                SELECT data_type
                INTO :col_type
                FROM information_schema.columns
                WHERE table_name = 'dummy'
                  AND table_schema = 'dummy'
                  AND column_name = 'dummy'
                LIMIT 1;
                RETURN :col_type;
            END
            """);
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertNull(rs.getRows().get(0).getValue(0));
    }
}
