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

package dev.frostlake.console;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.ExecutionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that query_id is properly returned for console display
 */
public class QueryIdDisplayTest extends BaseDatabaseTest {

    @Test
    public void testQueryIdDisplayedForSelect() {
        engine.execute("CREATE TABLE test (id INTEGER)");
        engine.execute("INSERT INTO test VALUES (1)");

        ExecutionResult result = engine.execute("SELECT * FROM test");

        assertNotNull(result.getQueryId(), "Query ID should be present for SELECT");
        assertTrue(result.getQueryId().matches("[0-9a-f-]+"), "Query ID should be a UUID format");
    }

    @Test
    public void testQueryIdDisplayedForInsert() {
        engine.execute("CREATE TABLE test (id INTEGER)");

        ExecutionResult result = engine.execute("INSERT INTO test VALUES (1)");

        assertNotNull(result.getQueryId(), "Query ID should be present for INSERT");
        assertTrue(result.getQueryId().matches("[0-9a-f-]+"), "Query ID should be a UUID format");
    }

    @Test
    public void testQueryIdDisplayedForUpdate() {
        engine.execute("CREATE TABLE test (id INTEGER)");
        engine.execute("INSERT INTO test VALUES (1)");

        ExecutionResult result = engine.execute("UPDATE test SET id = 2");

        assertNotNull(result.getQueryId(), "Query ID should be present for UPDATE");
        assertTrue(result.getQueryId().matches("[0-9a-f-]+"), "Query ID should be a UUID format");
    }

    @Test
    public void testQueryIdDisplayedForDelete() {
        engine.execute("CREATE TABLE test (id INTEGER)");
        engine.execute("INSERT INTO test VALUES (1)");

        ExecutionResult result = engine.execute("DELETE FROM test");

        assertNotNull(result.getQueryId(), "Query ID should be present for DELETE");
        assertTrue(result.getQueryId().matches("[0-9a-f-]+"), "Query ID should be a UUID format");
    }

    @Test
    public void testQueryIdDisplayedForDDL() {
        ExecutionResult result = engine.execute("CREATE TABLE test (id INTEGER)");

        assertNotNull(result.getQueryId(), "Query ID should be present for DDL");
        assertTrue(result.getQueryId().matches("[0-9a-f-]+"), "Query ID should be a UUID format");
    }
}
