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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * EXECUTE IMMEDIATE '<sql with ? placeholders>' USING (v1, v2, ...) — Snowflake-style positional bind
 * variables. Covers the three execution paths (top-level statement, procedural statement, and the
 * parenthesized expression form) plus quote-escaping of a string bind.
 */
public class ExecuteImmediateUsingTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void usingBindsLiteralsInTopLevelStatement() {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.execute("EXECUTE IMMEDIATE 'INSERT INTO t VALUES (?, ?)' USING (1, 'Alice')");
        final ResultSet rs = engine.executeQuery("SELECT name FROM t WHERE id = 1");
        assertEquals("Alice", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void usingBindsVariablesInProcedureStatement() {
        engine.execute("CREATE TABLE u (id INTEGER, name VARCHAR)");
        engine.executeQuery(
            "DECLARE i INTEGER DEFAULT 7; nm STRING DEFAULT 'Bob'; "
            + "BEGIN "
            + "  EXECUTE IMMEDIATE 'INSERT INTO u VALUES (?, ?)' USING (i, nm); "
            + "  RETURN 'ok'; "
            + "END");
        final ResultSet rs = engine.executeQuery("SELECT name FROM u WHERE id = 7");
        assertEquals("Bob", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void usingBindsInExpressionForm() {
        engine.execute("CREATE TABLE p (id INTEGER, price INTEGER)");
        engine.execute("INSERT INTO p VALUES (1, 50), (2, 150), (3, 250)");
        // The Snowflake docs shape: rs := (EXECUTE IMMEDIATE :query USING (minimum_price)).
        final ResultSet rs = engine.executeQuery(
            "DECLARE minp INTEGER DEFAULT 100; "
            + "        q STRING DEFAULT 'SELECT COUNT(*) FROM p WHERE price > ?'; "
            + "BEGIN "
            + "  RETURN (EXECUTE IMMEDIATE :q USING (minp)); "
            + "END");
        // The expression form yields the bound query's ResultSet (RETURN wraps it as the cell value).
        final ResultSet inner = (ResultSet) rs.getRows().get(0).getValue(0);
        assertEquals("2", inner.getRows().get(0).getValue(0).toString());   // prices 150, 250
    }

    @Test
    public void usingEscapesStringBind() {
        engine.execute("CREATE TABLE q (name VARCHAR)");
        // The bind value contains a single quote — must be escaped, not break the inner INSERT.
        engine.execute("EXECUTE IMMEDIATE 'INSERT INTO q VALUES (?)' USING ('O''Brien')");
        final ResultSet rs = engine.executeQuery("SELECT name FROM q");
        assertEquals("O'Brien", rs.getRows().get(0).getValue(0).toString());
    }
}
