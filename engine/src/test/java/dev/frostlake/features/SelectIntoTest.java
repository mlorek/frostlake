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
}
