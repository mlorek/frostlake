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

package dev.frostlake.constraints;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Write-path type coercion (Phase 4), gated by {@code constraints.enforce.types} (default off; ON here).
 * Snowflake coerces values to the column type on write: VARCHAR(n) length is enforced, numeric strings are
 * parsed, and non-numeric strings into numeric columns are rejected. See docs/acid-snowflake-plan.md.
 */
public class TypeCoercionTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_CONSTRAINTS_ENFORCE_TYPES, "true");
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("CREATE SCHEMA s");
        engine.execute("USE SCHEMA s");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void expectError(final String sql) {
        try {
            engine.execute(sql);
            fail("expected a type/length error for: " + sql);
        } catch (final RuntimeException expected) {
            // expected
        }
    }

    private long rowCount() {
        return engine.executeQuery("SELECT * FROM t").getRowCount();
    }

    @Test
    public void varcharLengthIsEnforced() {
        engine.execute("CREATE TABLE t (v VARCHAR(3))");
        engine.execute("INSERT INTO t VALUES ('abc')");   // exactly 3 → ok
        expectError("INSERT INTO t VALUES ('abcd')");     // 4 > 3 → error
        assertEquals(1, rowCount());
    }

    @Test
    public void nonNumericStringIntoNumericColumnIsRejected() {
        engine.execute("CREATE TABLE t (n NUMBER(10,0))");
        expectError("INSERT INTO t VALUES ('not a number')");
        assertEquals(0, rowCount());
    }

    @Test
    public void numericStringIntoNumericColumnIsParsed() {
        engine.execute("CREATE TABLE t (n NUMBER(10,0))");
        engine.execute("INSERT INTO t VALUES ('123')");   // numeric string → parsed, accepted
        assertEquals(1, rowCount());
    }

    @Test
    public void validValuesPassThrough() {
        engine.execute("CREATE TABLE t (id INTEGER, v VARCHAR(10))");
        engine.execute("INSERT INTO t VALUES (1, 'hello')");
        engine.execute("INSERT INTO t VALUES (2, 'world')");
        assertEquals(2, rowCount());
    }
}
