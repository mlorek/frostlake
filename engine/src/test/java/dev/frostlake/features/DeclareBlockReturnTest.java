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

public class DeclareBlockReturnTest {

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
    public void testBooleanBindVarIfReturn() {
        ResultSet rs = engine.executeQuery(
            "DECLARE\n" +
            "    s STRING DEFAULT '';\n" +
            "    b BOOLEAN DEFAULT TRUE;\n" +
            "BEGIN\n" +
            "    IF (:b) THEN\n" +
            "        RETURN 0;\n" +
            "    END IF;\n" +
            "    RETURN 1;\n" +
            "END"
        );
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        Object val = rs.getRows().get(0).getValue(0);
        assertNotNull(val);
        assertEquals(0L, Long.parseLong(val.toString()), "Expected block to return 0 when b=true");
    }

    @Test
    public void testBooleanFalseSkipsIf() {
        ResultSet rs = engine.executeQuery(
            "DECLARE\n" +
            "    b BOOLEAN DEFAULT FALSE;\n" +
            "BEGIN\n" +
            "    IF (:b) THEN\n" +
            "        RETURN 0;\n" +
            "    END IF;\n" +
            "    RETURN 1;\n" +
            "END"
        );
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        Object val = rs.getRows().get(0).getValue(0);
        assertNotNull(val);
        assertEquals(1L, Long.parseLong(val.toString()), "Expected block to return 1 when b=false");
    }
}
