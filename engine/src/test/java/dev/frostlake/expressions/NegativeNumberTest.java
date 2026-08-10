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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NegativeNumberTest extends BaseDatabaseTest {

    @Test
    public void testSelectNegativeInteger() {
        final ResultSet rs = engine.executeQuery("SELECT -1");
        assertEquals(-1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testSelectNegativeFloat() {
        final ResultSet rs = engine.executeQuery("SELECT -2.5");
        assertEquals(-2.5, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 0.001);
    }

    @Test
    public void testSelectNegativeInTable() {
        engine.execute("CREATE TABLE test (id INTEGER, value INTEGER)");
        engine.execute("INSERT INTO test VALUES (-1, -100)");

        final ResultSet rs = engine.executeQuery("SELECT * FROM test");
        assertEquals(-1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(-100L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void testSelectPositiveWithUnaryPlus() {
        final ResultSet rs = engine.executeQuery("SELECT +5");
        assertEquals(5L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testSelectNegativeExpression() {
        engine.execute("CREATE TABLE test (value INTEGER)");
        engine.execute("INSERT INTO test VALUES (10)");

        final ResultSet rs = engine.executeQuery("SELECT -value FROM test");
        assertEquals(-10L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }
}
