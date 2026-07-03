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
import dev.frostlake.config.EngineConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UNIQUE enforcement is opt-in via {@code constraints.enforce.uniqueKey} and OFF by default (UNIQUE is
 * otherwise informational, matching Snowflake). When enabled, a non-null duplicate in a UNIQUE column
 * is rejected — across statements and within a single multi-row INSERT — while NULLs are unconstrained.
 */
public class UniqueKeyEnforcementTest {

    private DatabaseEngine engine;

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    /** Fresh engine with UNIQUE enforcement on/off and a table whose email column is UNIQUE. */
    private void newEngine(final boolean enforceUnique) {
        final EngineConfig cfg = new EngineConfig();
        if (enforceUnique) {
            cfg.setProperty(EngineConfig.PROP_CONSTRAINTS_ENFORCE_UNIQUE_KEY, "true");
        }
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE TABLE t (id INTEGER, email VARCHAR UNIQUE)");
    }

    private long rowCount() {
        return ((Number) engine.executeQuery("SELECT COUNT(*) AS c FROM t").getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void disabledByDefaultAllowsDuplicates() {
        newEngine(false);
        engine.execute("INSERT INTO t VALUES (1, 'a@x'), (2, 'a@x')");
        assertEquals(2L, rowCount());
    }

    @Test
    public void enabledRejectsDuplicateAcrossStatements() {
        newEngine(true);
        engine.execute("INSERT INTO t VALUES (1, 'a@x')");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO t VALUES (2, 'a@x')");
            }
        });
    }

    @Test
    public void enabledRejectsDuplicateWithinOneStatement() {
        newEngine(true);
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO t VALUES (1, 'a@x'), (2, 'a@x')");
            }
        });
    }

    @Test
    public void enabledAllowsDistinctValues() {
        newEngine(true);
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO t VALUES (1, 'a@x'), (2, 'b@x')");
            }
        });
        assertEquals(2L, rowCount());
    }

    @Test
    public void enabledAllowsMultipleNulls() {
        newEngine(true);
        engine.execute("INSERT INTO t VALUES (1, NULL), (2, NULL)");
        assertEquals(2L, rowCount());
    }
}
