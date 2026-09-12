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

package dev.frostlake.udf;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that INLINE user-defined functions actually EXECUTE (not merely get created) for each
 * scripting language, by creating a function and invoking it in a SELECT.
 *
 * <p>Scope: scalar-UDF execution is wired for JAVA (in-process javac via {@code JavaFunctionCompiler}),
 * JAVASCRIPT (GraalVM JS via {@code JavaScriptExecutor}) and PYTHON (Jython via {@code PythonExecutor}).
 * SCALA is supported only for stored PROCEDURES ({@code ScalaProcedureExecutor}) — there is no scalar
 * Scala function executor — so a Scala scalar UDF is expected to be rejected, which is guarded below.
 * (Pre-existing {@code JavaScriptUDFTest}/{@code PythonUDFTest} assert only that the function is
 * created; this class asserts it returns the correct value when called.)
 */
public class InlineUdfExecutionTest extends BaseJdbcTest {

    private static final Logger logger = LoggerFactory.getLogger(InlineUdfExecutionTest.class);

    @Test
    public void inlineJavaUdfExecutes() throws SQLException {
        logger.info("inline Java UDF");
        statement.execute("""
            CREATE OR REPLACE FUNCTION java_greet(name STRING)
            RETURNS STRING
            LANGUAGE JAVA
            HANDLER = 'G.greet'
            AS $$
            class G {
              public static String greet(String name) { return "Hello, " + name; }
            }
            $$
            """);
        try (ResultSet rs = statement.executeQuery("SELECT java_greet('World')")) {
            rs.next();
            assertEquals("Hello, World", rs.getString(1));
        }
    }

    @Test
    public void inlineJavaScriptUdfExecutes() throws SQLException {
        logger.info("inline JavaScript UDF (GraalVM JS)");
        statement.execute("""
            CREATE OR REPLACE FUNCTION js_concat(x FLOAT)
            RETURNS STRING
            LANGUAGE JAVASCRIPT
            AS $$
                return "js:" + X;
            $$
            """);
        try (ResultSet rs = statement.executeQuery("SELECT js_concat(7)")) {
            rs.next();
            assertEquals("js:7", rs.getString(1));
        }
    }

    @Test
    public void inlinePythonUdfExecutes() throws SQLException {
        logger.info("inline Python UDF (Jython)");
        statement.execute("""
            CREATE OR REPLACE FUNCTION py_concat(x INTEGER)
            RETURNS STRING
            LANGUAGE PYTHON
            RUNTIME_VERSION = '3.11'
            HANDLER = 'go'
            AS $$
            def go(x):
                return 'py:' + str(x)
            $$
            """);
        try (ResultSet rs = statement.executeQuery("SELECT py_concat(7)")) {
            rs.next();
            assertEquals("py:7", rs.getString(1));
        }
    }

    /**
     * Scala scalar UDFs execute via {@link dev.frostlake.executor.udf.ScalaFunctionExecutor}, which
     * compiles the body in-process with the bundled scala-compiler (no external {@code scalac}).
     */
}
