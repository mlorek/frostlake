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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SYSTEM$ functions that read a table or a stream by name answer only about one the session sees, and refuse
 * any other as one that is not there: SYSTEM$STREAM_HAS_DATA, SYSTEM$STREAM_GET_TABLE_TIMESTAMP,
 * SYSTEM$LAST_CHANGE_COMMIT_TIME, SYSTEM$CLUSTERING_DEPTH and SYSTEM$CLUSTERING_INFORMATION. A role that held nothing
 * on the objects learnt whether the table had changed, when, and its clustering statistics. Engine only: the live
 * account allows no CREATE ROLE, CREATE USER or GRANT.
 */
public class SystemObjectFunctionSightTest {

    private static final String NO_STREAM = "must be a valid stream name";
    private static final String NO_OBJECT = "does not exist or not authorized";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.priv");
        engine.execute("CREATE TABLE d1.priv.t (a INT)");
        engine.execute("INSERT INTO d1.priv.t VALUES (1), (2)");
        engine.execute("CREATE STREAM d1.priv.s ON TABLE d1.priv.t");
        engine.execute("INSERT INTO d1.priv.t VALUES (3)");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE r1 TO USER u1");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void asR1() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("U1");
        engine.setCurrentRole("R1");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql).getMessage();
    }

    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aRoleThatSeesNeitherObjectIsRefusedByEach() {
        asR1();
        assertTrue(refusal("SELECT SYSTEM$STREAM_HAS_DATA('d1.priv.s')").contains(NO_STREAM));
        assertTrue(refusal("SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP('d1.priv.s')").contains(NO_STREAM));
        assertTrue(refusal("SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('d1.priv.t')").contains(NO_OBJECT));
        assertTrue(refusal("SELECT SYSTEM$CLUSTERING_DEPTH('d1.priv.t', '(a)')").contains(NO_OBJECT));
        assertTrue(refusal("SELECT SYSTEM$CLUSTERING_INFORMATION('d1.priv.t', '(a)')").contains(NO_OBJECT));
    }

    @Test
    public void privilegesOnTheObjectsLetEachAnswer() {
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE r1");
        engine.execute("GRANT USAGE ON SCHEMA d1.priv TO ROLE r1");
        engine.execute("GRANT SELECT ON TABLE d1.priv.t TO ROLE r1");
        engine.execute("GRANT SELECT ON STREAM d1.priv.s TO ROLE r1");
        asR1();
        assertEquals("true", cell("SELECT SYSTEM$STREAM_HAS_DATA('d1.priv.s')"));
        assertEquals("true", cell("SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('d1.priv.t') > 0"));
        assertEquals("1.0", cell("SELECT SYSTEM$CLUSTERING_DEPTH('d1.priv.t', '(a)')"));
        assertTrue(cell("SELECT SYSTEM$CLUSTERING_INFORMATION('d1.priv.t', '(a)')").contains("LINEAR(a)"));
        assertTrue(Long.parseLong(cell("SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP('d1.priv.s')")) > 0);
    }
}
