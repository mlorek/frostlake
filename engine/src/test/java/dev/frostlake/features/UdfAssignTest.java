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

public class UdfAssignTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("CREATE OR REPLACE FUNCTION f() RETURNS OBJECT AS '{}'");
    }

    @Test
    public void testAssignFunctionCall() {
        final ResultSet rs = engine.executeQuery(
            "DECLARE o OBJECT DEFAULT NULL; BEGIN o := f(); RETURN o; END");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertNotNull(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testAssignParenFunctionCall() {
        final ResultSet rs = engine.executeQuery(
            "DECLARE o OBJECT DEFAULT NULL; BEGIN o := (f()); RETURN o; END");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertNotNull(rs.getRows().get(0).getValue(0));
    }
}
