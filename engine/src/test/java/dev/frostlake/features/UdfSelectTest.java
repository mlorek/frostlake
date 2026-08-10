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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class UdfSelectTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
    }

    @Test
    public void testUdfReturnsObject() {
        engine.execute("CREATE OR REPLACE FUNCTION f() RETURNS OBJECT AS '{}'");
        final ResultSet rs = engine.executeQuery("SELECT f()");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertNotNull(rs.getRows().get(0).getValue(0));
        assertEquals("{}", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void testUdfWithArgs() {
        engine.execute("CREATE OR REPLACE FUNCTION add_one(n INTEGER) RETURNS INTEGER AS 'n + 1'");
        final ResultSet rs = engine.executeQuery("SELECT add_one(41)");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals(42L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testUdfInFromlessSelect() {
        engine.execute("CREATE OR REPLACE FUNCTION greet(name VARCHAR) RETURNS VARCHAR AS 'concat(''hello '', name)'");
        final ResultSet rs = engine.executeQuery("SELECT greet('world')");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("hello world", rs.getRows().get(0).getValue(0).toString());
    }
}
