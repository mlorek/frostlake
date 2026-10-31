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

package dev.frostlake.scripting;

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A schema-qualified call resolves in the current database whatever the current schema is (Snowflake's name
 * resolution documentation), so an untyped LET reads {@code PUBLIC.f1(1)} as every other place does: while PUBLIC is
 * the current schema, after another schema becomes current, typed, from DECLARE and inside a procedure.
 */
public class QualifiedCallInUntypedLetTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE DATABASE p");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private Object one(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void anUntypedLetCallsThePublicFunction() {
        assertEquals("2", String.valueOf(one("EXECUTE IMMEDIATE $$ BEGIN LET x := PUBLIC.f1(1); RETURN x; END; $$")));
        assertEquals("2", String.valueOf(one("SELECT PUBLIC.f1(1)")));
        assertEquals("2", String.valueOf(one(
            "EXECUTE IMMEDIATE $$ BEGIN LET x INT := PUBLIC.f1(1); RETURN x; END; $$")));
        assertEquals("2", String.valueOf(one(
            "EXECUTE IMMEDIATE $$ DECLARE x DEFAULT PUBLIC.f1(1); BEGIN RETURN x; END; $$")));
        assertEquals("2", String.valueOf(one("EXECUTE IMMEDIATE $$ BEGIN LET x := p.PUBLIC.f1(1); RETURN x; END; $$")));
        engine.execute("CREATE PROCEDURE pr() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := PUBLIC.f1(1); RETURN x; "
            + "END; $$");
        assertEquals("2", String.valueOf(one("CALL pr()")));
        engine.execute("CREATE SCHEMA s3");
        engine.execute("USE SCHEMA s3");
        assertEquals("2", String.valueOf(one("EXECUTE IMMEDIATE $$ BEGIN LET x := PUBLIC.f1(1); RETURN x; END; $$")));
    }
}
