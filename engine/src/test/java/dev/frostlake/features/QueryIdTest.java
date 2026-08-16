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
import dev.frostlake.ExecutionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueryIdTest extends BaseDatabaseTest {

    @Test
    public void testQueryIdIsReturnedForSelect() {
        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'Alice')");

        final ExecutionResult result = engine.execute("SELECT * FROM test_table");

        assertNotNull(result.getQueryId(), "Query ID should not be null");
        assertTrue(result.getQueryId().length() > 0, "Query ID should not be empty");
    }

    @Test
    public void testQueryIdIsReturnedForInsert() {
        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");

        final ExecutionResult result = engine.execute("INSERT INTO test_table VALUES (1, 'Bob')");

        assertNotNull(result.getQueryId(), "Query ID should not be null for INSERT");
        assertTrue(result.getQueryId().length() > 0, "Query ID should not be empty");
    }

    @Test
    public void testDifferentQueriesHaveDifferentIds() {
        engine.execute("CREATE TABLE test_table (id INTEGER)");

        final ExecutionResult result1 = engine.execute("INSERT INTO test_table VALUES (1)");
        final ExecutionResult result2 = engine.execute("INSERT INTO test_table VALUES (2)");

        assertNotNull(result1.getQueryId());
        assertNotNull(result2.getQueryId());
        assertTrue(!result1.getQueryId().equals(result2.getQueryId()),
                "Different queries should have different IDs");
    }
}
