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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Correctness of {@code value IN (uncorrelated subquery)} after the membership test was changed from a
 * per-outer-row linear scan to a hash index ({@link dev.frostlake.executor.expressions.PreparedInSet}).
 * The index must reproduce the engine's IN equality exactly: scale-insensitive numeric equality across
 * types (so an integer {@code 4} matches a double {@code 4.0}), {@code toString} equality for strings,
 * and NULLs that never match a non-NULL value.
 */
public class InSubqueryMembershipTest {

    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE dim (i INTEGER, d DOUBLE, s VARCHAR)");
        engine.execute("INSERT INTO dim VALUES (10, 4.0, 'banana')");
        engine.execute("INSERT INTO dim VALUES (20, 8.0, 'cherry')");
        engine.execute("INSERT INTO dim VALUES (NULL, 16.0, 'date')");
        engine.execute("CREATE TABLE facts (id INTEGER, n INTEGER)");
        engine.execute("INSERT INTO facts VALUES (1, 10)");
        engine.execute("INSERT INTO facts VALUES (2, 4)");
        engine.execute("INSERT INTO facts VALUES (3, 99)");
    }

    @AfterAll
    public static void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private static long count(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRowCount();
    }

    @Test
    public void testIntegerMembership() {
        // dim.i = {10, 20, NULL}; facts.n = {10, 4, 99} -> only n=10 is a member.
        assertEquals(1, count("SELECT id FROM facts WHERE n IN (SELECT i FROM dim)"));
    }

    @Test
    public void testNumericScaleInsensitiveAcrossTypes() {
        // dim.d = {4.0, 8.0, 16.0} (DOUBLE); facts.n is INTEGER. n=4 must match d=4.0.
        assertEquals(1, count("SELECT id FROM facts WHERE n IN (SELECT d FROM dim)"));
    }

    @Test
    public void testNotIn() {
        // n NOT IN dim.i: n=4 and n=99 are not members -> 2 rows. (n=10 is a member.)
        assertEquals(2, count("SELECT id FROM facts WHERE n NOT IN (SELECT i FROM dim)"));
    }

    @Test
    public void testStringMembership() {
        // Every dim.s is a member of the set of all dim.s -> all 3 rows.
        assertEquals(3, count("SELECT i FROM dim WHERE s IN (SELECT s FROM dim)"));
    }

    @Test
    public void testNullInSubqueryResultDoesNotMatchNonNull() {
        // dim.i contains a NULL; it must not match any non-null facts.n (only n=10 matches 10).
        assertEquals(1, count("SELECT id FROM facts WHERE n IN (SELECT i FROM dim)"));
    }
}
