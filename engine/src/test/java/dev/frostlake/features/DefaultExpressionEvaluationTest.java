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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A column DEFAULT that is a non-literal EXPRESSION (arithmetic, concatenation, function call, CASE) is now
 * evaluated per row at INSERT time, instead of the raw expression text being stored as the value — e.g.
 * {@code DEFAULT 10 + 5} yields 15, not the string "10 + 5". Literal defaults are unchanged, and an
 * invalid expression default surfaces as an error at INSERT rather than silently inserting bogus text.
 */
public class DefaultExpressionEvaluationTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private Object firstValue(final String query) {
        final ResultSet rs = engine.executeQuery(query);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void arithmeticDefaultIsEvaluated() {
        engine.execute("CREATE TABLE t (id INTEGER, qty INTEGER DEFAULT 10 + 5)");
        engine.execute("INSERT INTO t (id) VALUES (1)");
        assertEquals(15L, ((Number) firstValue("SELECT qty FROM t WHERE id = 1")).longValue());
    }

    @Test
    public void concatenationDefaultIsEvaluated() {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR DEFAULT 'Mr. ' || 'Unknown')");
        engine.execute("INSERT INTO t (id) VALUES (1)");
        assertEquals("Mr. Unknown", firstValue("SELECT name FROM t WHERE id = 1").toString());
    }

    @Test
    public void functionCallDefaultIsEvaluated() {
        engine.execute("CREATE TABLE t (id INTEGER, upper_name VARCHAR DEFAULT UPPER('test'))");
        engine.execute("INSERT INTO t (id) VALUES (1)");
        assertEquals("TEST", firstValue("SELECT upper_name FROM t WHERE id = 1").toString());
    }

    @Test
    public void caseExpressionDefaultIsEvaluated() {
        engine.execute(
            "CREATE TABLE t (id INTEGER, priority VARCHAR DEFAULT CASE WHEN 1 = 1 THEN 'high' ELSE 'low' END)");
        engine.execute("INSERT INTO t (id) VALUES (1)");
        assertEquals("high", firstValue("SELECT priority FROM t WHERE id = 1").toString());
    }

    @Test
    public void literalStringDefaultIsNotReEvaluated() {
        // Regression guard: a literal default must stay its plain value, not be treated as an expression.
        engine.execute("CREATE TABLE t (id INTEGER, status VARCHAR DEFAULT 'active')");
        engine.execute("INSERT INTO t (id) VALUES (1)");
        assertEquals("active", firstValue("SELECT status FROM t WHERE id = 1").toString());
    }

    @Test
    public void literalNumberDefaultIsUnchanged() {
        engine.execute("CREATE TABLE t (id INTEGER, n INTEGER DEFAULT 42)");
        engine.execute("INSERT INTO t (id) VALUES (1)");
        assertEquals(42L, ((Number) firstValue("SELECT n FROM t WHERE id = 1")).longValue());
    }

    @Test
    public void currentTimestampDefaultStillEvaluates() {
        // The recognized-keyword path is unchanged: CURRENT_TIMESTAMP still resolves to a real value.
        engine.execute("CREATE TABLE t (id INTEGER, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        engine.execute("INSERT INTO t (id) VALUES (1)");
        assertNotNull(firstValue("SELECT created_at FROM t WHERE id = 1"));
    }

    @Test
    public void invalidExpressionDefaultErrorsAtInsert() {
        // A default referencing a non-existent column used to be stored as the text "badcol" and inserted
        // verbatim; it must now fail when a row relies on it.
        engine.execute("CREATE TABLE t (id INTEGER, x VARCHAR DEFAULT (badcol))");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO t (id) VALUES (1)");
            }
        });
    }

    @Test
    public void explicitValueOverridesExpressionDefault() {
        engine.execute("CREATE TABLE t (id INTEGER, qty INTEGER DEFAULT 10 + 5)");
        engine.execute("INSERT INTO t (id, qty) VALUES (1, 99)");
        assertEquals(99L, ((Number) firstValue("SELECT qty FROM t WHERE id = 1")).longValue());
    }
}
