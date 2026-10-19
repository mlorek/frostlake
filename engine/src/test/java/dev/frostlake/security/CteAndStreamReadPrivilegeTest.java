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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What the first relation of a query asks of a role that is no administrator: a common table expression is the
 * statement's own relation and asks for nothing, a recursive one included; a stream is read with SELECT on the stream
 * and SELECT on the table it tracks, as Snowflake's access-control privileges list them. The live account allows no
 * CREATE ROLE, CREATE USER or GRANT, so the rule is the documented one, held by the embedded engine.
 */
public class CteAndStreamReadPrivilegeTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE ROLE reader_role");
        engine.execute("CREATE USER reader_user");
        engine.execute("GRANT ROLE reader_role TO USER reader_user");
        engine.execute("GRANT USAGE ON DATABASE db TO ROLE reader_role");
        engine.execute("GRANT USAGE ON SCHEMA db.public TO ROLE reader_role");
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE STREAM st ON TABLE t");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("GRANT SELECT ON TABLE t TO ROLE reader_role");
        engine.execute("GRANT SELECT ON STREAM st TO ROLE reader_role");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    /** The session as reader_user with only reader_role active. */
    private void asReader() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("reader_user");
        engine.setCurrentRole("reader_role");
    }

    /** The session as the engine's own user again, which may grant and revoke. */
    private void asSystem() {
        engine.setCurrentUser("SYSTEM");
    }

    private void refused(final String sql) {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
    }

    @Test
    public void aCommonTableExpressionAsksForNothing() {
        asReader();
        assertEquals(1, engine.executeQuery("WITH c AS (SELECT 1 AS x) SELECT * FROM c").getRowCount());
        assertEquals(3, engine.executeQuery(
            "WITH RECURSIVE n (k) AS (SELECT 1 UNION ALL SELECT k + 1 FROM n WHERE k < 3) SELECT * FROM n")
            .getRowCount());
        assertEquals(1, engine.executeQuery("WITH c AS (SELECT id FROM t) SELECT * FROM c").getRowCount());
    }

    @Test
    public void aStreamIsReadWithItsOwnGrantAndItsTables() {
        asReader();
        assertEquals(1, engine.executeQuery("SELECT * FROM st").getRowCount());
        asSystem();
        engine.execute("REVOKE SELECT ON STREAM st FROM ROLE reader_role");
        asReader();
        refused("SELECT * FROM st");
        asSystem();
        engine.execute("GRANT SELECT ON STREAM st TO ROLE reader_role");
        engine.execute("REVOKE SELECT ON TABLE t FROM ROLE reader_role");
        asReader();
        refused("SELECT * FROM st");
    }
}
