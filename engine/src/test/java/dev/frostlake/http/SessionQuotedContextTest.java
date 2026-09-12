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

package dev.frostlake.http;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A session on the concurrent engine carries its current database and schema from one statement to the
 * next by their stored names, so a quoted lower-case name keeps its case: the per-statement scope used to
 * fold both names to upper case, which left a session that had selected {@code "php dsn db"} pointing at
 * PHP DSN DB, a database nothing is stored under, and every unqualified name after it failed.
 */
public class SessionQuotedContextTest {

    private ConcurrentDatabaseEngine engine;

    @BeforeEach
    public void setup() {
        engine = new ConcurrentDatabaseEngine();
    }

    @AfterEach
    public void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void aQuotedDatabaseAndSchemaSurviveBetweenStatements() {
        final SessionContext session = engine.createSession();
        engine.execute("CREATE DATABASE \"php dsn db\"", session);
        engine.execute("USE DATABASE \"php dsn db\"", session);
        engine.execute("CREATE SCHEMA \"low sch\"", session);
        engine.execute("CREATE TABLE t1 (i INT)", session);
        engine.execute("INSERT INTO t1 VALUES (7)", session);
        final ResultSet result = engine.executeQuery("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA(), i FROM t1", session);
        assertEquals("php dsn db", result.getRows().get(0).getValue(0));
        assertEquals("low sch", result.getRows().get(0).getValue(1));
        assertEquals("7", String.valueOf(result.getRows().get(0).getValue(2)));
        assertEquals("php dsn db", session.getCurrentDatabase());
        assertEquals("low sch", session.getCurrentSchema());
    }

    @Test
    public void anotherSessionSelectsTheQuotedNameExactly() {
        final SessionContext admin = engine.createSession();
        engine.execute("CREATE DATABASE \"php dsn db\"", admin);
        final SessionContext other = engine.createSession();
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("USE DATABASE \"PHP DSN DB\"", other);
            }
        });
        assertTrue(refused.getMessage().contains("Object does not exist, or operation cannot be performed."),
            refused.getMessage());
        engine.execute("USE DATABASE \"php dsn db\"", other);
        final ResultSet result = engine.executeQuery("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()", other);
        assertEquals("php dsn db", result.getRows().get(0).getValue(0));
        assertEquals("PUBLIC", result.getRows().get(0).getValue(1));
    }
}
