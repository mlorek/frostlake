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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A PYTHON FUNCTION's body is compiled by CREATE, not by the first call — live-verified, and it needs
 * the runtime installed, which is why this lives in the rt-py module rather than beside the engine's
 * {@code RoutineBodyCompilationTest}.
 *
 * <p>Three things are refused there and here: a body that is not Python, a body whose module-level code
 * fails (importing something absent is the usual one), and a HANDLER the body does not define — or
 * defines with a different number of arguments than the function declares.
 *
 * <p>And one thing is NOT: the same nonsense body under CREATE PROCEDURE is accepted. That asymmetry is
 * measured, not inferred, and the engine-side test covers the procedure half.
 */
public class PythonRoutineCompilationTest extends BaseDatabaseTest {

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void aBodyThatIsNotPythonIsRefused() {
        assertTrue(refusalOf("CREATE FUNCTION f_py(x INTEGER) RETURNS INTEGER LANGUAGE PYTHON"
            + " RUNTIME_VERSION = '3.11' HANDLER = 'go' AS 'not sql at all'")
            .contains("SyntaxError"));
    }

    @Test
    public void aBodyWhoseImportsFailIsRefused() {
        assertTrue(refusalOf("CREATE FUNCTION f_imp(x INTEGER) RETURNS INTEGER LANGUAGE PYTHON"
            + " RUNTIME_VERSION = '3.11' HANDLER = 'go' AS $$\nimport nosuchmodule_xyz\n"
            + "def go(x):\n    return x\n$$")
            .contains("nosuchmodule_xyz"));
    }

    @Test
    public void aHandlerTheBodyDoesNotDefineIsRefused() {
        assertEquals("Could not find handler in function F_MISSING with handler go",
            refusalOf("CREATE FUNCTION f_missing(x INTEGER) RETURNS INTEGER LANGUAGE PYTHON"
                + " RUNTIME_VERSION = '3.11' HANDLER = 'go' AS $$\ndef other(x):\n    return x\n$$"));
    }

    @Test
    public void aHandlerWithTheWrongNumberOfArgumentsIsRefused() {
        assertEquals("Python function is defined with 0 arguments,"
            + " but UDF definition contains 1 arguments in function F_ARITY with handler go",
            refusalOf("CREATE FUNCTION f_arity(x INTEGER) RETURNS INTEGER LANGUAGE PYTHON"
                + " RUNTIME_VERSION = '3.11' HANDLER = 'go' AS $$\ndef go():\n    return 1\n$$"));
    }

    /** A body that compiles and whose handler matches is created, and still runs. */
    @Test
    public void aGoodBodyIsCreatedAndRuns() {
        engine.execute("CREATE FUNCTION f_ok(x INTEGER) RETURNS INTEGER LANGUAGE PYTHON"
            + " RUNTIME_VERSION = '3.11' HANDLER = 'go' AS $$\ndef go(x):\n    return x + 1\n$$");
        assertEquals(42L, ((Number) engine.executeQuery("SELECT f_ok(41) AS v")
            .getRows().get(0).getValue(0)).longValue());
    }
}
