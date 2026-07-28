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

package dev.frostlake.procedural;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An EXECUTE IMMEDIATE of a procedural script clears the session's transient procedural state (cursors,
 * user-defined exceptions, declared variable types) so a dynamic script may re-declare names left over from
 * a PREVIOUS top-level statement. That clear must not happen when the EXECUTE IMMEDIATE is itself running
 * inside a BEGIN…END block: it then wiped the ENCLOSING block's live state, so a procedure that declared an
 * exception, ran a dynamic procedural body, and then raised that exception failed with
 * "Undefined exception: &lt;name&gt;" — the exception it had declared itself.
 */
public class NestedExecuteImmediateStateTest extends BaseDatabaseTest {

    private String call(final String proc) {
        return engine.executeQuery("CALL " + proc).getRows().get(0).getValue(0).toString();
    }

    @Test
    public void declaredExceptionSurvivesANestedProceduralExecuteImmediate() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE
              my_exc EXCEPTION (-20002, 'TEST FAILED');
            BEGIN
              EXECUTE IMMEDIATE 'DECLARE d INTEGER; BEGIN d := 1; END;';
              RAISE my_exc;
            EXCEPTION
              WHEN my_exc THEN RETURN 'caught';
              WHEN other THEN RETURN 'OTHER: ' || SQLERRM;
            END
            $$""");
        assertEquals("caught", call("p()"));
    }

    @Test
    public void declaredExceptionSurvivesADynamicScriptThatDeclaresItsOwn() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE
              my_exc EXCEPTION (-20002, 'TEST FAILED');
            BEGIN
              EXECUTE IMMEDIATE 'DECLARE x INTEGER; BEGIN x := 1; END;';
              RAISE my_exc;
            EXCEPTION
              WHEN my_exc THEN RETURN 'caught';
              WHEN other THEN RETURN 'OTHER: ' || SQLERRM;
            END
            $$""");
        assertEquals("caught", call("p()"));
    }

    @Test
    public void declaredVariableTypeSurvivesANestedProceduralExecuteImmediate() {
        // The same clear drops declared variable types, which drive assignment coercion: a NUMBER(10,2)
        // variable must still round a later assignment after the nested dynamic block ran.
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE
              n NUMBER(10,2);
            BEGIN
              EXECUTE IMMEDIATE 'DECLARE d INTEGER; BEGIN d := 1; END;';
              n := 3.14159;
              RETURN :n::VARCHAR;
            END
            $$""");
        assertEquals("3.14", call("p()"));
    }

    @Test
    public void declaredCursorSurvivesANestedProceduralExecuteImmediate() {
        engine.execute("CREATE TABLE nums (id INTEGER)");
        engine.execute("INSERT INTO nums VALUES (7)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE
              c CURSOR FOR SELECT id FROM nums;
              v INTEGER;
            BEGIN
              OPEN c;
              EXECUTE IMMEDIATE 'DECLARE d INTEGER; BEGIN d := 1; END;';
              FETCH c INTO v;
              CLOSE c;
              RETURN :v::VARCHAR;
            END
            $$""");
        assertEquals("7", call("p()"));
    }

    @Test
    public void topLevelExecuteImmediateStillClearsBetweenStatements() {
        // Regression: outside any block the clear must still happen, so a dynamic script can re-declare the
        // same names a previous top-level statement declared (running it twice must not say "already declared").
        engine.execute("EXECUTE IMMEDIATE 'DECLARE x INTEGER; BEGIN x := 1; END;'");
        engine.execute("EXECUTE IMMEDIATE 'DECLARE x INTEGER; BEGIN x := 2; END;'");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE after_top_level() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE
              my_exc EXCEPTION (-20002, 'TEST FAILED');
            BEGIN
              RAISE my_exc;
            EXCEPTION
              WHEN my_exc THEN RETURN 'caught';
            END
            $$""");
        assertEquals("caught", call("after_top_level()"));
    }
}
