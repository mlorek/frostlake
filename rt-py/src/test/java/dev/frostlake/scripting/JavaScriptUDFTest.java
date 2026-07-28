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
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.rt.js.JavaScriptExecutor;
import dev.frostlake.rt.py.PythonExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JavaScriptUDFTest {

    private static final Logger logger = LoggerFactory.getLogger(JavaScriptUDFTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testCreateJavaScriptFunction() {
        logger.info("Testing CREATE FUNCTION with LANGUAGE JAVASCRIPT");

        engine.execute("""
            CREATE FUNCTION js_double(x INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVASCRIPT
            AS $$
                return x * 2;
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("js_double");

        assertNotNull(func, "Function should be created");
        assertEquals("JS_DOUBLE", func.getName());
        assertEquals("JAVASCRIPT", func.getLanguage());
        assertEquals(1, func.getParameters().size());
        assertTrue(func.getBody().contains("return x * 2"),
                   "Function body should contain JavaScript code");
    }

    @Test
    public void testCreateJavaScriptFunctionWithMultipleParameters() {
        logger.info("Testing JavaScript function with multiple parameters");

        engine.execute("""
            CREATE FUNCTION js_add(a INTEGER, b INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVASCRIPT
            AS $$
                return a + b;
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("js_add");

        assertNotNull(func, "Function should be created");
        assertEquals("JS_ADD", func.getName());
        assertEquals("JAVASCRIPT", func.getLanguage());
        assertEquals(2, func.getParameters().size());
    }

    @Test
    public void testCreateJavaScriptFunctionWithStringParameter() {
        logger.info("Testing JavaScript function with string parameter");

        engine.execute("""
            CREATE FUNCTION js_uppercase(s VARCHAR)
            RETURNS VARCHAR
            LANGUAGE JAVASCRIPT
            AS $$
                return s.toUpperCase();
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("js_uppercase");

        assertNotNull(func, "Function should be created");
        assertEquals("JS_UPPERCASE", func.getName());
        assertEquals("JAVASCRIPT", func.getLanguage());
    }

    @Test
    public void testExecuteJavaScriptFunction() {
        logger.info("Testing JavaScript function execution");

        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("x", NumericType.INTEGER));

        Function func = new Function("test_double", parameters, NumericType.INTEGER,
                                    "return x * 2;", false, "JAVASCRIPT");

        List<Object> args = Arrays.asList(21);
        Object result = JavaScriptExecutor.executeJavaScriptFunction(func, args);

        assertNotNull(result, "Result should not be null");
        logger.info("Result: {}", result);
        assertEquals(42, ((Number) result).intValue(), "Result should be 42");
    }

    @Test
    public void testExecuteJavaScriptFunctionWithMultipleArgs() {
        logger.info("Testing JavaScript function execution with multiple arguments");

        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("a", NumericType.INTEGER));
        parameters.add(new Parameter("b", NumericType.INTEGER));

        Function func = new Function("test_add", parameters, NumericType.INTEGER,
                                    "return a + b;", false, "JAVASCRIPT");

        List<Object> args = Arrays.asList(10, 32);
        Object result = JavaScriptExecutor.executeJavaScriptFunction(func, args);

        assertNotNull(result, "Result should not be null");
        logger.info("Result: {}", result);
        assertEquals(42, ((Number) result).intValue(), "Result should be 42");
    }

    @Test
    public void testExecuteJavaScriptFunctionWithStringArg() {
        logger.info("Testing JavaScript function execution with string argument");

        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("s", StringType.VARCHAR));

        Function func = new Function("test_upper", parameters, StringType.VARCHAR,
                                    "return s.toUpperCase();", false, "JAVASCRIPT");

        List<Object> args = Arrays.asList("hello");
        Object result = JavaScriptExecutor.executeJavaScriptFunction(func, args);

        assertNotNull(result, "Result should not be null");
        logger.info("Result: {}", result);
        assertEquals("HELLO", result.toString(), "Result should be 'HELLO'");
    }

    @Test
    public void testJavaScriptFunctionWithComplexLogic() {
        logger.info("Testing JavaScript function with complex logic");

        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("n", NumericType.INTEGER));

        String body = """
            if (n <= 1) {
                return 1;
            }
            return n * 2;
            """;

        Function func = new Function("test_conditional", parameters, NumericType.INTEGER,
                                    body, false, "JAVASCRIPT");

        List<Object> args1 = Arrays.asList(1);
        Object result1 = JavaScriptExecutor.executeJavaScriptFunction(func, args1);
        assertEquals(1, ((Number) result1).intValue(), "Result for n=1 should be 1");

        List<Object> args2 = Arrays.asList(5);
        Object result2 = JavaScriptExecutor.executeJavaScriptFunction(func, args2);
        assertEquals(10, ((Number) result2).intValue(), "Result for n=5 should be 10");
    }

    @Test
    public void testCreateSQLFunction() {
        logger.info("Testing CREATE FUNCTION with LANGUAGE SQL (default)");

        engine.execute("""
            CREATE FUNCTION sql_double(x INTEGER)
            RETURNS INTEGER
            LANGUAGE SQL
            AS 'x * 2'
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("sql_double");

        assertNotNull(func, "Function should be created");
        assertEquals("SQL_DOUBLE", func.getName());
        assertEquals("SQL", func.getLanguage());
        assertEquals("x * 2", func.getBody());
    }

    @Test
    public void testCreateFunctionDefaultsToSQL() {
        logger.info("Testing CREATE FUNCTION defaults to SQL when language not specified");

        engine.execute("""
            CREATE FUNCTION default_func(x INTEGER)
            RETURNS INTEGER
            AS 'x + 1'
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("default_func");

        assertNotNull(func, "Function should be created");
        assertEquals("DEFAULT_FUNC", func.getName());
        assertEquals("SQL", func.getLanguage());
    }

    @Test
    public void testDropJavaScriptFunction() {
        logger.info("Testing DROP JavaScript function");

        engine.execute("""
            CREATE FUNCTION js_test(x INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVASCRIPT
            AS 'return x;'
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        assertNotNull(schema.getFunction("js_test"), "Function should exist");

        engine.execute("DROP FUNCTION js_test(INTEGER)");

        RuntimeException exception = Assertions.assertThrows(
            RuntimeException.class,
            () -> schema.getFunction("js_test")
        );
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    public void testCreatePythonFunctionWithHandler() {
        logger.info("Testing CREATE FUNCTION with LANGUAGE PYTHON and HANDLER");

        engine.execute("""
            CREATE FUNCTION py_multiply(x INTEGER, y INTEGER)
            RETURNS INTEGER
            LANGUAGE PYTHON
            RUNTIME_VERSION = '3.8'
            HANDLER = 'multiply_handler'
            AS $$
def multiply_handler(x, y):
    return x * y
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("py_multiply");

        assertNotNull(func, "Function should be created");
        assertEquals("PY_MULTIPLY", func.getName());
        assertEquals("PYTHON", func.getLanguage());
        assertEquals("multiply_handler", func.getHandler());
        assertEquals("3.8", func.getRuntimeVersion());
        assertEquals(2, func.getParameters().size());
        assertTrue(func.getBody().contains("def multiply_handler"),
                   "Function body should contain handler function");
    }

    @Test
    public void testExecutePythonFunctionWithHandler() {
        logger.info("Testing Python function execution with HANDLER");

        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("x", NumericType.INTEGER));
        parameters.add(new Parameter("y", NumericType.INTEGER));

        String body = """
def multiply_handler(x, y):
    return x * y
""";

        Function func = new Function("test_multiply", parameters, NumericType.INTEGER,
                                    body, false, "PYTHON", "multiply_handler", "3.8");

        List<Object> args = Arrays.asList(6, 7);
        Object result = PythonExecutor.executePythonFunction(func, args);

        assertNotNull(result, "Result should not be null");
        logger.info("Result: {}", result);
        assertEquals(42, ((Number) result).intValue(), "Result should be 42");
    }

    @Test
    public void testCreatePythonFunctionWithHandlerOnly() {
        logger.info("Testing CREATE FUNCTION with HANDLER but no RUNTIME_VERSION");

        engine.execute("""
            CREATE FUNCTION py_square(x INTEGER)
            RETURNS INTEGER
            LANGUAGE PYTHON
            HANDLER = 'square_it'
            AS $$
def square_it(x):
    return x * x
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("py_square");

        assertNotNull(func, "Function should be created");
        assertEquals("PY_SQUARE", func.getName());
        assertEquals("PYTHON", func.getLanguage());
        assertEquals("square_it", func.getHandler());
        assertEquals(1, func.getParameters().size());
    }

    @Test
    public void testExecutePythonFunctionWithHandlerComplexLogic() {
        logger.info("Testing Python function with HANDLER and complex logic");

        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("n", NumericType.INTEGER));

        String body = """
def fibonacci(n):
    if n <= 1:
        return n
    a, b = 0, 1
    for _ in range(2, n + 1):
        a, b = b, a + b
    return b
""";

        Function func = new Function("test_fib", parameters, NumericType.INTEGER,
                                    body, false, "PYTHON", "fibonacci", "3.8");

        List<Object> args = Arrays.asList(10);
        Object result = PythonExecutor.executePythonFunction(func, args);

        assertNotNull(result, "Result should not be null");
        logger.info("Fibonacci(10) = {}", result);
        assertEquals(55, ((Number) result).intValue(), "Fibonacci(10) should be 55");
    }
}
