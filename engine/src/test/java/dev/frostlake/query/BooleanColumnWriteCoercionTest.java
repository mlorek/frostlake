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
 * Writing into a BOOLEAN column converts implicitly the way Snowflake does: a number by zero/non-zero
 * (the {@code IFF(cond, 1, 0)} loader idiom stores real TRUE/FALSE) and a string by its TO_BOOLEAN
 * literal. The stored value then compares equal to boolean literals under EXCEPT / joins.
 */
public class BooleanColumnWriteCoercionTest extends BaseDatabaseTest {

    @Test
    public void testNumberZeroOneStoredAsBoolean() {
        engine.execute("CREATE TABLE flags (id NUMBER, ok BOOLEAN)");
        engine.execute("INSERT INTO flags SELECT 1, IFF(2 > 1, 1, 0)");
        engine.execute("INSERT INTO flags SELECT 2, IFF(1 > 2, 1, 0)");
        final ResultSet rs = engine.executeQuery("SELECT id, ok FROM flags ORDER BY id");
        assertEquals(Boolean.TRUE, rs.getRows().get(0).getValue(1));
        assertEquals(Boolean.FALSE, rs.getRows().get(1).getValue(1));
    }

    @Test
    public void testStoredBooleanMatchesLiteralUnderExcept() {
        engine.execute("CREATE TABLE flags2 (id NUMBER, ok BOOLEAN)");
        engine.execute("INSERT INTO flags2 SELECT 1, 1");
        final ResultSet rs = engine.executeQuery("""
            SELECT 1 AS id, true AS ok
            EXCEPT
            SELECT id, ok FROM flags2
            """);
        assertEquals(0, rs.getRows().size());
    }

    @Test
    public void testStringLiteralsStoredAsBoolean() {
        engine.execute("CREATE TABLE flags3 (id NUMBER, ok BOOLEAN)");
        engine.execute("INSERT INTO flags3 VALUES (1, 'true'), (2, 'f'), (3, 'yes'), (4, '0')");
        final ResultSet rs = engine.executeQuery("SELECT id, ok FROM flags3 ORDER BY id");
        assertEquals(Boolean.TRUE, rs.getRows().get(0).getValue(1));
        assertEquals(Boolean.FALSE, rs.getRows().get(1).getValue(1));
        assertEquals(Boolean.TRUE, rs.getRows().get(2).getValue(1));
        assertEquals(Boolean.FALSE, rs.getRows().get(3).getValue(1));
    }

    @Test
    public void testNonZeroNumberIsTrue() {
        engine.execute("CREATE TABLE flags4 (ok BOOLEAN)");
        engine.execute("INSERT INTO flags4 SELECT -3");
        assertEquals(Boolean.TRUE, engine.executeQuery("SELECT ok FROM flags4").getRows().get(0).getValue(0));
    }
}
