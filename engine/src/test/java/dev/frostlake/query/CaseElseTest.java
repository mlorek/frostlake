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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Simple test for CASE ELSE clause
 */
public class CaseElseTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE test (id INTEGER, val INTEGER)");
        engine.execute("INSERT INTO test VALUES (1, 10)");
        engine.execute("INSERT INTO test VALUES (2, NULL)");
    }

    @Test
    public void testSimpleElse() {
        final ResultSet rs = engine.executeQuery(
            "SELECT CASE WHEN 1 = 2 THEN 'no' ELSE 'yes' END as result"
        );
        assertEquals("yes", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testElseWithNull() {
        final ResultSet rs = engine.executeQuery("""
            SELECT id,
            CASE WHEN val > 5 THEN 'big' ELSE 'small' END as size
            FROM test ORDER BY id
            """);
        assertEquals(2, rs.getRowCount());
        assertEquals("big", rs.getRows().get(0).getValue(1));
        assertEquals("small", rs.getRows().get(1).getValue(1)); // NULL > 5 is false, so ELSE
    }
}
