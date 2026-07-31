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

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests DESCRIBE for FUNCTION, PROCEDURE, USER, MASKING POLICY, and ROW ACCESS POLICY — each returns a
 * (property, value) result describing the object.
 */
public class DescribeObjectTest extends BaseDatabaseTest {

    /**
     * The (property, value) pairs of a DESCRIBE, keyed case-INSENSITIVELY: Snowflake is not
     * consistent about the case of the property column across DESCRIBE variants (DESC FUNCTION
     * answers {@code signature} / {@code language}, DESC USER answers {@code NAME}), and the property
     * a test asks for is the same property either way.
     */
    private Map<String, String> describe(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final Map<String, String> props = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
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
        // The user under test is created HERE rather than inherited from whatever an earlier test (or
        // an earlier run) left on the account: live, DESCRIBE USER alice answered nothing usable
        // ("expected ALICE but was null") because the test depended on leftover state. IF NOT EXISTS
        // plus the DROP below make it rerunnable on a stateful account.
        engine.execute("CREATE USER IF NOT EXISTS alice");
        final Map<String, String> p = describe("DESCRIBE USER alice");
        assertEquals("ALICE", p.get("name"));
        engine.execute("DROP USER IF EXISTS alice");
    }

    @Test
    public void describeMaskingPolicy() {
        engine.execute("CREATE MASKING POLICY mask_ssn AS (val STRING) RETURNS STRING -> '***'");
        // Live Snowflake returns ONE row with columns name | signature | return_type | body;
        // STRING normalizes to VARCHAR in both the signature and the return type.
        final ResultSet rs = engine.executeQuery("DESCRIBE MASKING POLICY mask_ssn");
        assertEquals(1, rs.getRowCount());
        final Row row = rs.getRows().get(0);
        assertEquals("MASK_SSN", row.getValue(rs.getColumnIndex("name")));
        assertEquals("(VAL VARCHAR)", row.getValue(rs.getColumnIndex("signature")));
        assertEquals("VARCHAR", row.getValue(rs.getColumnIndex("return_type")));
        assertEquals("'***'", row.getValue(rs.getColumnIndex("body")));
    }

    @Test
    public void describeRowAccessPolicy() {
        engine.execute("CREATE ROW ACCESS POLICY rap AS (v STRING) RETURNS BOOLEAN -> TRUE");
        final ResultSet rs = engine.executeQuery("DESCRIBE ROW ACCESS POLICY rap");
        assertEquals(1, rs.getRowCount());
        final Row row = rs.getRows().get(0);
        assertEquals("RAP", row.getValue(rs.getColumnIndex("name")));
        assertEquals("(V VARCHAR)", row.getValue(rs.getColumnIndex("signature")));
        assertEquals("BOOLEAN", row.getValue(rs.getColumnIndex("return_type")));
        assertEquals("TRUE", row.getValue(rs.getColumnIndex("body")));
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
