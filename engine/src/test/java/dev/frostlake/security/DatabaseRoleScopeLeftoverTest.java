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
package dev.frostlake.security;

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A database role is scoped to its own database for future grants and for the database roles granted to it
 * (documented): a future grant on another database's schema or on the database itself is refused, as is another
 * database's role granted to it. The refusals' wording is the engine's own.
 */
public class DatabaseRoleScopeLeftoverTest {

    private static final String REFUSED = "SQL compilation error:\nPrivileges on objects of database HR cannot be "
        + "granted to database role SALES.ANALYST.";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE sales");
        engine.execute("CREATE DATABASE hr");
        engine.execute("CREATE DATABASE ROLE sales.analyst");
        engine.execute("CREATE DATABASE ROLE hr.x");
        engine.execute("CREATE DATABASE ROLE sales.y");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void aFutureGrantOnAnotherDatabaseIsRefused() {
        assertEquals(REFUSED,
            refusal("GRANT SELECT ON FUTURE TABLES IN SCHEMA hr.public TO DATABASE ROLE sales.analyst"));
        assertEquals(REFUSED, refusal("GRANT SELECT ON FUTURE TABLES IN DATABASE hr TO DATABASE ROLE sales.analyst"));
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("GRANT SELECT ON FUTURE TABLES IN SCHEMA sales.public TO DATABASE ROLE sales.analyst");
            }
        });
    }

    @Test
    public void anotherDatabasesRoleIsRefused() {
        assertEquals("SQL compilation error:\nDatabase role HR.X cannot be granted to database role SALES.ANALYST of "
            + "another database.", refusal("GRANT DATABASE ROLE hr.x TO DATABASE ROLE sales.analyst"));
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("GRANT DATABASE ROLE sales.y TO DATABASE ROLE sales.analyst");
            }
        });
    }
}
