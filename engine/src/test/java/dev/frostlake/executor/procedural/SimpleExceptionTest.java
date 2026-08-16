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

package dev.frostlake.executor.procedural;

import dev.frostlake.DatabaseEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Deliberately NOT on the live surface: pins the TYPED shape of an uncaught user-defined
 * exception — the escaping object stays a {@link ProceduralException} carrying the declared
 * error code, and its message is exactly the live-verified uncaught wording (here for a
 * single-line script, where the RAISE position counts columns on line 1). Over JDBC only the
 * message text survives; {@code UserDefinedExceptionsTest} covers that two-sided surface.
 */
public class SimpleExceptionTest {
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testSimpleException() {
        final String script =
            "DECLARE my_exception EXCEPTION (-20002, 'my exception'); BEGIN RAISE my_exception; END;";

        final ProceduralException exception = assertThrows(ProceduralException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(script);
            }
        });

        assertEquals(-20002, exception.getErrorCode());
        assertEquals(
            "Uncaught exception of type 'MY_EXCEPTION' on line 1 at position 63 : my exception",
            exception.getMessage());
    }
}
