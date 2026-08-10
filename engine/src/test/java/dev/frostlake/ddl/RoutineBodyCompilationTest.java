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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake compiles a routine's SQL body at CREATE time; Frostlake mirrors that for the shapes it can
 * judge. The check deliberately FAILS OPEN: Frostlake's grammar is a subset of Snowflake's, so a body the
 * engine cannot fully parse means "unrecognised construct", not "invalid SQL" — rejecting those would
 * break working deployment scripts the account itself accepts. What IS rejected is a shape the engine can
 * positively read and knows to be wrong: a LANGUAGE SQL procedure body that is one plain statement (a
 * block is required), and a TABLE function body that is a scripting block (a query is required).
 *
 * <p>A SCALAR SQL UDF, by contrast, accepts all three body shapes — expression, query and scripting block
 * — and {@link ScriptingUdfBodyTest} covers the block form and the restricted language it may use.
 */
public class RoutineBodyCompilationTest extends BaseDatabaseTest {

    /** These two build the IMPORTS stage on the local filesystem; live refuses that URL
     *  ("invalid URL prefix found in: 'file:///tmp/...'"), so the stage cannot exist there. */
    private static final String LOCAL_STAGE_ONLY =
        "IMPORTS validation needs a local-filesystem stage, which live does not accept";

    @Test
    public void procedureBodyThatIsNotABlockIsRejected() {
        final RuntimeException failure = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(
                    "CREATE PROCEDURE p_bad() RETURNS VARCHAR LANGUAGE SQL AS 'SELECT 1 + 1'");
            }
        });
        // The compiler raises exactly "SQL compilation error:\nsyntax error …"; the engine's uniform error
        // wrapping ("Failed to execute SQL: Failed to execute CREATE statement: …") prefixes every message.
        assertTrue(failure.getMessage().contains("SQL compilation error:\nsyntax error"), failure.getMessage());
    }

    @Test
    public void procedureBodyThatIsABareStatementIsRejected() {
        // A bare SELECT is a perfectly good SQL statement but NOT a scripting block — Snowflake rejects it.
        final RuntimeException failure = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(
                    "CREATE PROCEDURE p_bad2(a INTEGER) RETURNS INTEGER LANGUAGE SQL AS 'SELECT a + 1'");
            }
        });
        // Only the compilation-error HEAD is asserted: the hint that follows it ("… a BEGIN ... END
        // block is required") is Frostlake's wording, while a real account reports the same rejection as
        // a plain syntax error.
        assertTrue(failure.getMessage().contains("SQL compilation error"), failure.getMessage());
    }

    @Test
    public void procedureCompilationErrorIsNotSwallowedByIfNotExists() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE PROCEDURE IF NOT EXISTS p_bad3() RETURNS VARCHAR LANGUAGE SQL "
                    + "AS 'SELECT 1 + 1'");
            }
        });
    }

    @Test
    public void procedureBodyThatIsABlockSucceeds() {
        engine.execute("CREATE PROCEDURE p_ok() RETURNS VARCHAR LANGUAGE SQL AS BEGIN RETURN 'x'; END");
        final ResultSet result = engine.executeQuery("CALL p_ok()");
        assertEquals(1, result.getRowCount());
        assertEquals("x", result.getRows().get(0).getValue(0));
    }

    @Test
    public void procedureBodyWithADeclareSectionSucceeds() {
        engine.execute("""
            CREATE PROCEDURE p_declared(a INTEGER) RETURNS INTEGER LANGUAGE SQL AS
            $$
            DECLARE
              total INTEGER;
            BEGIN
              total := a + 1;
              RETURN total;
            END
            $$
            """);
        final ResultSet result = engine.executeQuery("CALL p_declared(41)");
        assertEquals(42, ((Number) result.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void sqlUdfBodyThatIsAScriptingBlockIsAccepted() {
        // Live-verified: a SCALAR SQL UDF may have a scripting block for a body, and it runs.
        // This test previously asserted the opposite — a rejection inferred as the symmetric mirror of the
        // (correct) procedure rule, never probed. ScriptingUdfBodyTest covers the shape in full.
        engine.execute("CREATE FUNCTION f_block(x INTEGER) RETURNS INTEGER AS 'BEGIN RETURN x; END'");
        final ResultSet result = engine.executeQuery("SELECT f_block(4) AS v");
        assertEquals(4, ((Number) result.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void tableFunctionBodyThatIsAScriptingBlockIsRejected() {
        // The one UDF shape a block is still wrong for: live, a RETURNS TABLE(...) function with a
        // BEGIN...END body is an ordinary SQL-UDF syntax error.
        final RuntimeException failure = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE FUNCTION f_bad_table() RETURNS TABLE(x INTEGER) "
                    + "AS 'BEGIN RETURN 1; END'");
            }
        });
        assertTrue(failure.getMessage().contains("SQL compilation error"), failure.getMessage());
    }

    @Test
    public void sqlUdfExpressionBodySucceeds() {
        engine.execute("CREATE FUNCTION f_expr(x INTEGER) RETURNS INTEGER AS 'x + 10'");
        final ResultSet result = engine.executeQuery("SELECT f_expr(5) AS v");
        assertEquals(15, ((Number) result.getRows().get(0).getValue(0)).intValue());
    }

    @Test
    public void sqlUdfQueryBodySucceeds() {
        engine.execute("CREATE TABLE udf_src (n INTEGER)");
        engine.execute("INSERT INTO udf_src VALUES (7), (8)");
        engine.execute("CREATE FUNCTION f_query() RETURNS INTEGER AS 'SELECT MAX(n) FROM udf_src'");
        final ResultSet result = engine.executeQuery("SELECT f_query() AS v");
        assertEquals(8, ((Number) result.getRows().get(0).getValue(0)).intValue());
    }

    /**
     * A JAVASCRIPT body is never compiled at CREATE, function or procedure — live-verified with a body
     * that is not JavaScript at all and with one that is broken JavaScript. Both are created; only a
     * call fails.
     */
    @Test
    public void javaScriptBodiesAreNotCompiled() {
        engine.execute("CREATE PROCEDURE p_js() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'not sql at all'");
        engine.execute("CREATE FUNCTION f_js(x FLOAT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS '}{'");

        assertTrue(showListsName(engine.executeQuery("SHOW PROCEDURES"), "P_JS"));
        assertTrue(showListsName(engine.executeQuery("SHOW USER FUNCTIONS"), "F_JS"));
    }

    /**
     * A PYTHON PROCEDURE's body is not compiled at CREATE either — the asymmetry that makes the language
     * table worth transcribing, since the identical body under LANGUAGE PYTHON is refused for a FUNCTION
     * (see {@code PythonRoutineCompilationTest} in the rt-py module, which needs the runtime installed).
     */
    @Test
    public void aPythonProcedureBodyIsNotCompiled() {
        engine.execute("CREATE PROCEDURE p_py() RETURNS VARCHAR LANGUAGE PYTHON"
            + " RUNTIME_VERSION = '3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'go'"
            + " AS 'not sql at all'");
        assertTrue(showListsName(engine.executeQuery("SHOW PROCEDURES"), "P_PY"));
    }

    /** A JAVA body IS compiled at CREATE, and the failure is the compiler's own diagnostic. */
    @Test
    public void aJavaFunctionBodyIsCompiledAtCreate() {
        final RuntimeException failure = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE FUNCTION f_java(x INTEGER) RETURNS INTEGER LANGUAGE JAVA"
                    + " RUNTIME_VERSION = '11' HANDLER = 'C.go' AS 'not java at all'");
            }
        });
        // The complaint after the prefix is the HOST compiler's own wording, as it is on a real
        // account, so only the shape both share is asserted.
        assertTrue(failure.getMessage().startsWith("Error while compiling source: "),
            failure.getMessage());
        assertFalse(showListsName(engine.executeQuery("SHOW USER FUNCTIONS"), "F_JAVA"));
    }

    /** …and the HANDLER must actually be in it, with the right number of arguments. */
    @Test
    public void aJavaHandlerMustBeInTheBodyWithTheRightArity() {
        assertEquals("Failed to find a public method named \"go\" with 1 arguments"
            + " in function F_JAVA2 with handler C.go",
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute("CREATE FUNCTION f_java2(x INTEGER) RETURNS INTEGER LANGUAGE JAVA"
                        + " RUNTIME_VERSION = '11' HANDLER = 'C.go'"
                        + " AS 'public class C { public static int other(int x) { return x; } }'");
                }
            }).getMessage());
        // The same body with the handler present is created.
        engine.execute("CREATE FUNCTION f_java3(x INTEGER) RETURNS INTEGER LANGUAGE JAVA"
            + " RUNTIME_VERSION = '11' HANDLER = 'C.go'"
            + " AS 'public class C { public static int go(int x) { return x; } }'");
        assertTrue(showListsName(engine.executeQuery("SHOW USER FUNCTIONS"), "F_JAVA3"));
    }

    /** A handler that lives in an IMPORTS jar is not in the body, so the body is not judged. */
    @Test
    public void anImportsHandlerLeavesTheBodyAlone() throws IOException {
        Assumptions.assumeFalse(isLiveSnowflake(), LOCAL_STAGE_ONLY);
        // The stage and the jar have to exist: live validates BOTH at CREATE time, so a routine naming a
        // stage that was never made is refused before it can demonstrate anything about its body.
        final Path stageDir = Files.createTempDirectory("fl_imports_");
        Files.writeString(stageDir.resolve("handlers.jar"), "not really a jar");
        engine.execute("CREATE STAGE stg URL='file://" + stageDir + "'");

        engine.execute("CREATE FUNCTION f_jar(x INTEGER) RETURNS INTEGER LANGUAGE JAVA"
            + " RUNTIME_VERSION = '11' IMPORTS = ('@stg/handlers.jar') HANDLER = 'C.go'"
            + " AS 'not java at all'");
        assertTrue(showListsName(engine.executeQuery("SHOW USER FUNCTIONS"), "F_JAR"));
    }

    /** And the validation itself: live refuses the stage leg and the file leg, each with its own words. */
    @Test
    public void importsMustNameAStageAndAFileThatExist() throws IOException {
        Assumptions.assumeFalse(isLiveSnowflake(), LOCAL_STAGE_ONLY);
        final RuntimeException noStage = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE FUNCTION f_nostage(x INTEGER) RETURNS INTEGER LANGUAGE JAVA"
                    + " RUNTIME_VERSION = '11' IMPORTS = ('@nosuchstage/handlers.jar')"
                    + " HANDLER = 'C.go' AS 'not java at all'");
            }
        });
        assertTrue(String.valueOf(noStage.getMessage()).contains("NOSUCHSTAGE' does not exist or not authorized."),
            noStage.getMessage());

        final Path emptyStage = Files.createTempDirectory("fl_imports_empty_");
        engine.execute("CREATE STAGE stg_empty URL='file://" + emptyStage + "'");
        final RuntimeException noFile = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE FUNCTION f_nofile(x INTEGER) RETURNS INTEGER LANGUAGE JAVA"
                    + " RUNTIME_VERSION = '11' IMPORTS = ('@stg_empty/handlers.jar')"
                    + " HANDLER = 'C.go' AS 'not java at all'");
            }
        });
        assertTrue(String.valueOf(noFile.getMessage())
                .contains("Remote file 'handlers.jar' was not found."), noFile.getMessage());
    }

    /**
     * PYTHON and SCALA name a versioned runtime, so RUNTIME_VERSION is required for functions and
     * procedures alike. JAVA asks for no version, and SQL has no runtime to version.
     */
    @Test
    public void versionedRuntimesRequireARuntimeVersion() {
        for (final String sql : List.of(
                "CREATE FUNCTION f_v(x INTEGER) RETURNS INTEGER LANGUAGE PYTHON AS 'body'",
                "CREATE FUNCTION f_w(x INTEGER) RETURNS INTEGER LANGUAGE SCALA AS 'body'",
                "CREATE PROCEDURE p_v() RETURNS VARCHAR LANGUAGE PYTHON AS 'body'")) {
            final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute(sql);
                }
            });
            assertEquals("Property 'runtime_version' must be specified", e.getMessage(),
                "for: " + sql);
        }
        // The languages that need none.
        engine.execute("CREATE FUNCTION f_sql(x INTEGER) RETURNS INTEGER AS 'x + 1'");
        engine.execute("CREATE PROCEDURE p_js2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'return 1'");
    }

    /**
     * A body-loading language names an entry point into that body, so HANDLER is required for JAVA,
     * SCALA and PYTHON, functions and procedures alike. RUNTIME_VERSION is checked first where both
     * are missing.
     */
    @Test
    public void bodyLoadingLanguagesRequireAHandler() {
        for (final String sql : List.of(
                "CREATE FUNCTION f_h(x INTEGER) RETURNS INTEGER LANGUAGE JAVA AS 'body'",
                "CREATE FUNCTION f_i(x INTEGER) RETURNS INTEGER LANGUAGE PYTHON"
                    + " RUNTIME_VERSION = '3.11' AS 'body'",
                "CREATE FUNCTION f_j(x INTEGER) RETURNS INTEGER LANGUAGE SCALA"
                    + " RUNTIME_VERSION = '2.12' AS 'body'",
                "CREATE PROCEDURE p_h() RETURNS VARCHAR LANGUAGE PYTHON"
                    + " RUNTIME_VERSION = '3.11' AS 'body'")) {
            assertEquals("Property 'handler' must be specified", rejectionOf(sql), "for: " + sql);
        }
        // Both missing: the runtime version is the one reported.
        assertEquals("Property 'runtime_version' must be specified",
            rejectionOf("CREATE FUNCTION f_k(x INTEGER) RETURNS INTEGER LANGUAGE PYTHON AS 'body'"));
    }

    /**
     * SQL and JAVASCRIPT run an inline body, so naming RUNTIME_VERSION or HANDLER for one is an
     * invalid property. The two rejections do not share a shape: RUNTIME_VERSION is reported upper
     * case against 'FUNCTION', handler lower case against the language's own "&lt;LANG&gt; function"
     * — the latter even when the routine is a procedure.
     */
    @Test
    public void inlineBodyLanguagesRejectRuntimeVersionAndHandler() {
        assertEquals("SQL compilation error:\ninvalid property 'RUNTIME_VERSION' for 'FUNCTION'",
            rejectionOf("CREATE FUNCTION f_a(x INTEGER) RETURNS INTEGER LANGUAGE SQL"
                + " RUNTIME_VERSION = '1' AS 'x + 1'"));
        assertEquals("SQL compilation error:\ninvalid property 'RUNTIME_VERSION' for 'FUNCTION'",
            rejectionOf("CREATE FUNCTION f_b(x INTEGER) RETURNS INTEGER LANGUAGE JAVASCRIPT"
                + " RUNTIME_VERSION = '1' AS 'return 1'"));

        assertEquals("SQL compilation error:\ninvalid property 'handler' for 'SQL function'",
            rejectionOf("CREATE FUNCTION f_c(x INTEGER) RETURNS INTEGER LANGUAGE SQL"
                + " HANDLER = 'h' AS 'x + 1'"));
        assertEquals("SQL compilation error:\ninvalid property 'handler' for 'JAVASCRIPT function'",
            rejectionOf("CREATE FUNCTION f_d(x INTEGER) RETURNS INTEGER LANGUAGE JAVASCRIPT"
                + " HANDLER = 'h' AS 'return 1'"));
        // A procedure is still reported as a "function" here.
        assertEquals("SQL compilation error:\ninvalid property 'handler' for 'JAVASCRIPT function'",
            rejectionOf("CREATE PROCEDURE p_d() RETURNS VARCHAR LANGUAGE JAVASCRIPT"
                + " HANDLER = 'h' AS 'return 1'"));
    }

    private String rejectionOf(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return e.getMessage();
    }

    private boolean showListsName(final ResultSet result, final String expected) {
        for (int row = 0; row < result.getRowCount(); row++) {
            for (int col = 0; col < result.getColumns().size(); col++) {
                final Object value = result.getRows().get(row).getValue(col);
                if (expected.equalsIgnoreCase(String.valueOf(value))) {
                    return true;
                }
            }
        }
        return false;
    }
}
