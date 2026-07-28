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
    public void testSelectIntoZeroRowsAssignsNull() {
        // Snowflake Scripting (verified against live Snowflake): SELECT INTO over ZERO rows assigns
        // NULL to the targets and continues — it does NOT raise. Only more than one row errors.
        ResultSet rs = engine.executeQuery("""
            DECLARE
                col_type STRING;
            BEGIN
                CREATE OR REPLACE TABLE cols(name STRING, data_type STRING);
                SELECT data_type INTO :col_type FROM cols WHERE name = 'missing' LIMIT 1;
                RETURN COALESCE(:col_type, 'was-null');
            END
            """);
        assertNotNull(rs);
        assertEquals("was-null", String.valueOf(rs.getRows().get(0).getValue(0)));
    }

    @Test
    public void testSelectIntoZeroRowsLetsAProbeBlockComplete() {
        // The vendor stream-reset idiom: probe SHOW output via RESULT_SCAN; on a fresh database the
        // probe finds nothing, the flag stays NULL, the IF is skipped and the block returns normally.
        ResultSet rs = engine.executeQuery("""
            DECLARE
                stale_flag BOOLEAN DEFAULT FALSE;
            BEGIN
                SHOW STREAMS LIKE 'NO_SUCH_STREAM';
                SELECT "stale" INTO :stale_flag FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-1)));
                IF (:stale_flag) THEN
                    RETURN 'dropped stale';
                END IF;
                RETURN 'not stale';
            END
            """);
        assertNotNull(rs);
        assertEquals("not stale", String.valueOf(rs.getRows().get(0).getValue(0)));
    }

    @Test
    public void testSelectIntoMoreThanOneRowStillErrors() {
        assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                engine.executeQuery("""
                    DECLARE
                        v STRING;
                    BEGIN
                        CREATE OR REPLACE TABLE two_rows(x STRING);
                        INSERT INTO two_rows VALUES ('a'), ('b');
                        SELECT x INTO :v FROM two_rows;
                        RETURN :v;
                    END
                    """);
            }
        });
    }
}
