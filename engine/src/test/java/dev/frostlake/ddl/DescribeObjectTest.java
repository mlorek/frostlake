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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests DESCRIBE for FUNCTION, PROCEDURE, USER, MASKING POLICY, and ROW ACCESS POLICY — each returns a
 * (property, value) result describing the object.
 */
public class DescribeObjectTest extends BaseDatabaseTest {

    private Map<String, String> describe(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final Map<String, String> props = new HashMap<>();
        for (final Row row : rs.getRows()) {
            props.put(String.valueOf(row.getValue(0)), String.valueOf(row.getValue(1)));
        }
        return props;
    }

    @Test
    public void describeFunction() {
        engine.execute("CREATE FUNCTION add_one(x INTEGER) RETURNS INTEGER AS $$ x + 1 $$");
        final Map<String, String> p = describe("DESCRIBE FUNCTION add_one(INTEGER)");
        assertTrue(p.containsKey("signature"), "should list signature");
        assertTrue(p.containsKey("returns"), "should list returns");
        assertEquals("SQL", p.get("language"));
    }

    @Test
    public void describeUser() {
        engine.execute("CREATE USER alice");
        final Map<String, String> p = describe("DESCRIBE USER alice");
        assertEquals("ALICE", p.get("name"));
    }

    @Test
    public void describeMaskingPolicy() {
        engine.execute("CREATE MASKING POLICY mask_ssn AS (val STRING) RETURNS STRING -> '***'");
        final Map<String, String> p = describe("DESCRIBE MASKING POLICY mask_ssn");
        assertTrue(p.containsKey("signature"));
        assertEquals("STRING", p.get("return_type"));
    }

    @Test
    public void describeRowAccessPolicy() {
        engine.execute("CREATE ROW ACCESS POLICY rap AS (v STRING) RETURNS BOOLEAN -> TRUE");
        final Map<String, String> p = describe("DESCRIBE ROW ACCESS POLICY rap");
        assertEquals("BOOLEAN", p.get("return_type"));
    }

    @Test
    public void describeNonexistentFunctionFails() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("DESCRIBE FUNCTION no_such_fn()");
            }
        });
    }
}
