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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine embeds GraalPy (Python 3.12), so ordinary Snowflake handler syntax — parameter and return
 * annotations, f-strings, assignment expressions, the Python 3 standard library — simply runs. Only a
 * body using syntax added AFTER 3.12 can fail on the version, and such a failure is annotated with the
 * declared RUNTIME_VERSION and the embedded version rather than surfacing a bare parser error.
 */
public class PythonRuntimeVersionErrorTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void testAnnotationsRun() {
        engine.execute("""
            CREATE FUNCTION py_annot(s VARCHAR)
            RETURNS VARCHAR
            LANGUAGE PYTHON
            RUNTIME_VERSION = '3.11'
            HANDLER = 'go'
            AS
            $$
            def go(s: str) -> str:
                return s.upper()
            $$
            """);
        assertEquals("ABC", scalar("SELECT py_annot('abc')"));
    }

    @Test
    public void testFStringsAndWalrusRun() {
        engine.execute("""
            CREATE FUNCTION py_modern(n NUMBER)
            RETURNS VARCHAR
            LANGUAGE PYTHON
            RUNTIME_VERSION = '3.10'
            HANDLER = 'go'
            AS
            $$
            def go(n):
                doubled = [y for x in [n] if (y := x * 2) > 0]
                return f"value={doubled[0]}"
            $$
            """);
        assertEquals("value=10", scalar("SELECT py_modern(5)"));
    }

    @Test
    public void testPython3StandardLibraryIsAvailable() {
        engine.execute("""
            CREATE FUNCTION py_stdlib(s VARCHAR)
            RETURNS VARCHAR
            LANGUAGE PYTHON
            RUNTIME_VERSION = '3.11'
            HANDLER = 'go'
            AS
            $$
            from urllib.parse import unquote
            def go(s):
                return unquote(s)
            $$
            """);
        assertEquals("a/b", scalar("SELECT py_stdlib('a%2Fb')"));
    }

    @Test
    public void testNewerThanEmbeddedSyntaxIsExplained() {
        // PEP 696 type-parameter DEFAULTS (`def f[T = int]`) are 3.13 syntax; on the embedded 3.12 that
        // is a parse error, and the message must name the version gap rather than just echo the parser.
        engine.execute("""
            CREATE FUNCTION py_future(s VARCHAR)
            RETURNS VARCHAR
            LANGUAGE PYTHON
            RUNTIME_VERSION = '3.13'
            HANDLER = 'go'
            AS
            $$
            def go[T = str](s: T) -> T:
                return s
            $$
            """);
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT py_future('x')");
            }
        });
        final String message = error.getMessage();
        assertTrue(message.contains("RUNTIME_VERSION '3.13'"), message);
        assertTrue(message.contains("3.12 (GraalPy)"), message);
    }

    @Test
    public void testRuntimeErrorKeepsPlainMessage() {
        // A genuine runtime failure (not a parse failure) must not be blamed on the Python version.
        engine.execute("""
            CREATE FUNCTION py_boom(s VARCHAR)
            RETURNS VARCHAR
            LANGUAGE PYTHON
            RUNTIME_VERSION = '3.13'
            HANDLER = 'go'
            AS
            $$
            def go(s):
                raise ValueError('boom')
            $$
            """);
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT py_boom('x')");
            }
        });
        assertFalse(error.getMessage().contains("embeds Python"), error.getMessage());
    }

    @Test
    public void testProcedureRunsPython3Syntax() {
        engine.execute("""
            CREATE PROCEDURE py_proc()
            RETURNS VARCHAR
            LANGUAGE PYTHON
            RUNTIME_VERSION = '3.11'
            HANDLER = 'run'
            AS
            $$
            def run(session, limit: int = 3) -> str:
                return f"ok:{limit}"
            $$
            """);
        assertEquals("ok:3", scalar("CALL py_proc()"));
    }
}
