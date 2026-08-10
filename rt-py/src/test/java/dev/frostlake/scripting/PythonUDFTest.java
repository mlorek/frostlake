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
import dev.frostlake.rt.py.PythonExecutor;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PythonUDFTest {

    private static final Logger logger = LoggerFactory.getLogger(PythonUDFTest.class);
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
    public void testCreatePythonFunction() {
        logger.info("Testing CREATE FUNCTION with LANGUAGE PYTHON");

        engine.execute("""
            CREATE FUNCTION py_double(x INTEGER)
            RETURNS INTEGER
            LANGUAGE PYTHON
            RUNTIME_VERSION = '3.11'
            HANDLER = 'go'
            AS $$
            def go(x):
                return x * 2
            $$
            """);

        final Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        final Function func = schema.getFunction("py_double");

        assertNotNull(func, "Function should be created");
        assertEquals("PY_DOUBLE", func.getName());
        assertEquals("PYTHON", func.getLanguage());
        assertEquals(1, func.getParameters().size());
    }

    @Test
    public void testExecutePythonFunction() {
        logger.info("Testing Python function execution");

        final List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("x", NumericType.INTEGER));

        final Function func = new Function("test_square", parameters, NumericType.INTEGER,
                                    "return x * x", false, "PYTHON");

        final List<Object> args = Arrays.asList(7);
        final Object result = PythonExecutor.executePythonFunction(func, args);

        assertNotNull(result, "Result should not be null");
        logger.info("Result: {}", result);
        assertEquals(49, ((Number) result).intValue(), "Result should be 49");
    }

    @Test
    public void testPythonFunctionWithMultipleParameters() {
        logger.info("Testing Python function with multiple parameters");

        final List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("a", NumericType.INTEGER));
        parameters.add(new Parameter("b", NumericType.INTEGER));

        final Function func = new Function("test_add", parameters, NumericType.INTEGER,
                                    "return a + b", false, "PYTHON");

        final List<Object> args = Arrays.asList(15, 27);
        final Object result = PythonExecutor.executePythonFunction(func, args);

        assertNotNull(result, "Result should not be null");
        logger.info("Result: {}", result);
        assertEquals(42, ((Number) result).intValue(), "Result should be 42");
    }

    @Test
    public void testPythonFunctionWithStringParameter() {
        logger.info("Testing Python function with string parameter");

        final List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("s", StringType.VARCHAR));

        final Function func = new Function("test_upper", parameters, StringType.VARCHAR,
                                    "return s.upper()", false, "PYTHON");

        final List<Object> args = Arrays.asList("hello");
        final Object result = PythonExecutor.executePythonFunction(func, args);

        assertNotNull(result, "Result should not be null");
        logger.info("Result: {}", result);
        assertEquals("HELLO", result.toString(), "Result should be 'HELLO'");
    }

    @Test
    public void testPythonFunctionWithConditional() {
        logger.info("Testing Python function with conditional logic");

        final List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("n", NumericType.INTEGER));

        final String body = """
            if n < 0:
                return "negative"
            elif n == 0:
                return "zero"
            else:
                return "positive"
            """;

        final Function func = new Function("test_sign", parameters, StringType.VARCHAR,
                                    body, false, "PYTHON");

        final Object result1 = PythonExecutor.executePythonFunction(func, Arrays.asList(-5));
        assertEquals("negative", result1.toString());

        final Object result2 = PythonExecutor.executePythonFunction(func, Arrays.asList(0));
        assertEquals("zero", result2.toString());

        final Object result3 = PythonExecutor.executePythonFunction(func, Arrays.asList(5));
        assertEquals("positive", result3.toString());
    }

    @Test
    public void testPythonFunctionWithLoop() {
        logger.info("Testing Python function with loop");

        final List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("n", NumericType.INTEGER));

        final String body = """
            total = 0
            for i in range(1, n + 1):
                total += i
            return total
            """;

        final Function func = new Function("test_sum", parameters, NumericType.INTEGER,
                                    body, false, "PYTHON");

        final Object result = PythonExecutor.executePythonFunction(func, Arrays.asList(10));
        assertNotNull(result, "Result should not be null");
        logger.info("Sum of 1 to 10: {}", result);
        assertEquals(55, ((Number) result).intValue(), "Sum should be 55");
    }

    @Test
    public void testPythonFunctionWithListComprehension() {
        logger.info("Testing Python function with list comprehension");

        final List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("n", NumericType.INTEGER));

        final String body = """
            squares = [i * i for i in range(1, n + 1)]
            return sum(squares)
            """;

        final Function func = new Function("test_squares_sum", parameters, NumericType.INTEGER,
                                    body, false, "PYTHON");

        final Object result = PythonExecutor.executePythonFunction(func, Arrays.asList(5));
        assertNotNull(result, "Result should not be null");
        logger.info("Sum of squares 1-5: {}", result);
        // 1 + 4 + 9 + 16 + 25 = 55
        assertEquals(55, ((Number) result).intValue(), "Sum should be 55");
    }

    @Test
    public void testPythonFunctionStringManipulation() {
        logger.info("Testing Python function with string manipulation");

        final List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("text", StringType.VARCHAR));

        final String body = """
            words = text.split()
            return ' '.join(reversed(words))
            """;

        final Function func = new Function("test_reverse_words", parameters, StringType.VARCHAR,
                                    body, false, "PYTHON");

        final Object result = PythonExecutor.executePythonFunction(func, Arrays.asList("Hello World Python"));
        assertNotNull(result, "Result should not be null");
        logger.info("Reversed: {}", result);
        assertEquals("Python World Hello", result.toString());
    }

    @Test
    public void testCompareLanguages() {
        logger.info("Testing comparison of SQL, JavaScript, and Python functions");

        engine.execute("""
            CREATE FUNCTION sql_triple(x INTEGER)
            RETURNS INTEGER
            LANGUAGE SQL
            AS 'x * 3'
            """);

        engine.execute("""
            CREATE FUNCTION js_triple(x INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVASCRIPT
            AS 'return x * 3;'
            """);

        engine.execute("""
            CREATE FUNCTION py_triple(x INTEGER)
            RETURNS INTEGER
            LANGUAGE PYTHON
            RUNTIME_VERSION = '3.11'
            HANDLER = 'go'
            AS $$
            def go(x):
                return x * 3
            $$
            """);

        final Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");

        final Function sqlFunc = schema.getFunction("sql_triple");
        final Function jsFunc = schema.getFunction("js_triple");
        final Function pyFunc = schema.getFunction("py_triple");

        assertEquals("SQL", sqlFunc.getLanguage());
        assertEquals("JAVASCRIPT", jsFunc.getLanguage());
        assertEquals("PYTHON", pyFunc.getLanguage());

        logger.info("All three language functions created successfully");
    }

    @Test
    public void testPythonFunctionComplexLogic() {
        logger.info("Testing Python function with complex logic");

        final List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("n", NumericType.INTEGER));

        final String body = """
            def fibonacci(n):
                if n <= 1:
                    return n
                a, b = 0, 1
                for _ in range(2, n + 1):
                    a, b = b, a + b
                return b
            """;

        final Function func = new Function("test_fib", parameters, NumericType.INTEGER,
                                    body, false, "PYTHON");

        final Object result = PythonExecutor.executePythonFunction(func, Arrays.asList(10));
        assertNotNull(result, "Result should not be null");
        logger.info("Fibonacci(10): {}", result);
        assertEquals(55, ((Number) result).intValue(), "Fibonacci(10) should be 55");
    }
}
