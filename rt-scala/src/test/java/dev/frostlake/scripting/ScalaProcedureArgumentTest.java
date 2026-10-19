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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a Scala procedure's handler receives for the arguments of its CALL: each converted to its parameter's SQL
 * type and handed over as the class the handler declares, as a Java handler's are — a NUMBER an {@code Int} or a
 * {@code Long}, a text read as the number it spells, a number given for a text parameter its text, a BOOLEAN read
 * from a text. A NULL is refused for a class that holds none, and a number past the class is refused. The handlers
 * here compile against this engine's own session class, so the class runs on the embedded engine alone.
 */
public class ScalaProcedureArgumentTest {

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

    private void scala(final String name, final String type, final String scalaType, final String body) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "(a " + type + ") RETURNS VARCHAR LANGUAGE SCALA "
            + "RUNTIME_VERSION = '2.12' HANDLER = 'H.run' AS $$ import com.snowflake.snowpark_java.Session; "
            + "object H { def run(s: Session, a: " + scalaType + "): String = " + body + " } $$");
    }

    private String first(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        Throwable root = refused;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return refused.getMessage() + " / " + root.getMessage();
    }

    @Test
    public void aNumberIsTheClassTheHandlerDeclares() {
        scala("si", "NUMBER", "Int", "\"v=\" + (a + 1)");
        assertEquals("v=42", first("CALL si(41)"));
        assertEquals("v=42", first("CALL si('41')"));
        assertEquals("v=4", first("CALL si(2.5)"));
        scala("sl", "NUMBER", "Long", "\"v=\" + (a + 1L)");
        assertEquals("v=3000000001", first("CALL sl(3000000000)"));
    }

    @Test
    public void aTextAndABooleanConvertBeforeTheHandlerRuns() {
        scala("ss", "VARCHAR", "String", "a + \"!\"");
        assertEquals("42!", first("CALL ss(42)"));
        assertEquals("true!", first("CALL ss(TRUE)"));
        scala("sb", "BOOLEAN", "Boolean", "if (a) \"T\" else \"F\"");
        assertEquals("F", first("CALL sb('off')"));
        assertEquals("T", first("CALL sb(1)"));
        final String notABoolean = refusal("CALL sb('maybe')");
        assertTrue(notABoolean.contains("Boolean value 'maybe' is not recognized"), notABoolean);
    }

    @Test
    public void aValueTheClassCannotHoldIsRefused() {
        scala("si", "NUMBER", "Int", "\"v=\" + a");
        final String tooWide = refusal("CALL si(3000000000)");
        assertTrue(tooWide.contains("Number out of representable range: type int32, value 3000000000"
            + " in function SI with handler H.run"), tooWide);
        final String none = refusal("CALL si(NULL)");
        assertTrue(none.contains("Cannot pass a NULL value as input to a Java type of int in function SI with handler"
            + " H.run"), none);
    }
}
