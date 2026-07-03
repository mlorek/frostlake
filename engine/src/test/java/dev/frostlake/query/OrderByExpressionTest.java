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
 * A non-aggregate SELECT must accept a SELECT alias, an arbitrary expression, or a function as an ORDER BY
 * key (Snowflake allows any in-scope expression), not only a bare column name.
 */
public class OrderByExpressionTest extends BaseDatabaseTest {

    private long a(final ResultSet rs, final int row) {
        return ((Number) rs.getRows().get(row).getValue(0)).longValue();
    }

    @Test
    public void orderByASelectAlias() {
        engine.execute("CREATE TABLE ob (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO ob VALUES (3, 1), (1, 10), (2, 3)");
        final ResultSet rs = engine.executeQuery("SELECT a AS z FROM ob ORDER BY z");
        assertEquals(1L, a(rs, 0));
        assertEquals(2L, a(rs, 1));
        assertEquals(3L, a(rs, 2));
    }

    @Test
    public void orderByAnArbitraryExpression() {
        engine.execute("CREATE TABLE ob (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO ob VALUES (3, 1), (1, 10), (2, 3)");
        // a+b = 4, 11, 5  ->  ascending 4,5,11  ->  first-column a = 3, 2, 1
        final ResultSet rs = engine.executeQuery("SELECT a, b FROM ob ORDER BY a+b");
        assertEquals(3L, a(rs, 0));
        assertEquals(2L, a(rs, 1));
        assertEquals(1L, a(rs, 2));
    }

    @Test
    public void orderByAParenthesizedExpressionDesc() {
        engine.execute("CREATE TABLE ob (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO ob VALUES (3, 1), (1, 10), (2, 3)");
        // a*2 = 6, 2, 4  ->  DESC 6,4,2  ->  a = 3, 2, 1
        final ResultSet rs = engine.executeQuery("SELECT a FROM ob ORDER BY (a*2) DESC");
        assertEquals(3L, a(rs, 0));
        assertEquals(2L, a(rs, 1));
        assertEquals(1L, a(rs, 2));
    }

    @Test
    public void orderByAFunction() {
        engine.execute("CREATE TABLE ob2 (a INTEGER)");
        engine.execute("INSERT INTO ob2 VALUES (-3), (1), (-2)");
        // ABS(a) = 3, 1, 2  ->  ascending 1,2,3  ->  a = 1, -2, -3
        final ResultSet rs = engine.executeQuery("SELECT a FROM ob2 ORDER BY ABS(a)");
        assertEquals(1L, a(rs, 0));
        assertEquals(-2L, a(rs, 1));
        assertEquals(-3L, a(rs, 2));
    }
}
