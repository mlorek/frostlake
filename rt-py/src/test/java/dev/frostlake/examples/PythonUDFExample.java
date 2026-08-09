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

package dev.frostlake.examples;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.types.NumericType;
import dev.frostlake.rt.py.PythonExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Example demonstrating Python UDF support in Frostlake Engine
 *
 * This shows Python functions alongside SQL and JavaScript functions,
 * demonstrating the engine's multi-language UDF capabilities.
 */
public class PythonUDFExample {
    private static final Logger logger = LoggerFactory.getLogger(PythonUDFExample.class);

    public static void main(final String[] args) {
        DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== Python UDF Examples ===\n");

            // Example 1: Simple Python function
            logger.info("1. Simple Python Function");
            engine.execute("""
                CREATE FUNCTION py_square(x INTEGER)
                RETURNS INTEGER
                LANGUAGE PYTHON
                RUNTIME_VERSION = '3.11'
                AS $$
                    return x * x
                $$
                """);

            Schema schema = engine.getCatalog().getDatabase("DEMO_DB").getSchema("PUBLIC");
            Function square = schema.getFunction("py_square");
            Object result1 = PythonExecutor.executePythonFunction(square, Arrays.asList(7));
            logger.info("   py_square(7) = " + result1);
            logger.info("");

            // Example 2: String manipulation
            logger.info("2. String Manipulation with Python");
            engine.execute("""
                CREATE FUNCTION py_title_case(text VARCHAR)
                RETURNS VARCHAR
                LANGUAGE PYTHON
                RUNTIME_VERSION = '3.11'
                AS $$
                    return text.title()
                $$
                """);

            Function titleCase = schema.getFunction("py_title_case");
            Object result2 = PythonExecutor.executePythonFunction(titleCase, Arrays.asList("hello world"));
            logger.info("   py_title_case('hello world') = " + result2);
            logger.info("");

            // Example 3: Conditional logic
            logger.info("3. Conditional Logic");
            engine.execute("""
                CREATE FUNCTION py_grade(score INTEGER)
                RETURNS VARCHAR
                LANGUAGE PYTHON
                RUNTIME_VERSION = '3.11'
                AS $$
                    if score >= 90:
                        return 'A'
                    elif score >= 80:
                        return 'B'
                    elif score >= 70:
                        return 'C'
                    elif score >= 60:
                        return 'D'
                    else:
                        return 'F'
                $$
                """);

            Function grade = schema.getFunction("py_grade");
            logger.info("   py_grade(95) = " + PythonExecutor.executePythonFunction(grade, Arrays.asList(95)));
            logger.info("   py_grade(75) = " + PythonExecutor.executePythonFunction(grade, Arrays.asList(75)));
            logger.info("   py_grade(55) = " + PythonExecutor.executePythonFunction(grade, Arrays.asList(55)));
            logger.info("");

            // Example 4: Loop and accumulation
            logger.info("4. Loops and Accumulation");
            engine.execute("""
                CREATE FUNCTION py_factorial(n INTEGER)
                RETURNS INTEGER
                LANGUAGE PYTHON
                RUNTIME_VERSION = '3.11'
                AS $$
                    result = 1
                    for i in range(1, n + 1):
                        result = result * i
                    return result
                $$
                """);

            Function factorial = schema.getFunction("py_factorial");
            logger.info("   Factorial values:");
            for (int i = 1; i <= 6; i++) {
                Object fact = PythonExecutor.executePythonFunction(factorial, Arrays.asList(i));
                logger.info("   " + i + "! = " + fact);
            }
            logger.info("");

            // Example 5: List comprehensions
            logger.info("5. List Comprehensions");
            engine.execute("""
                CREATE FUNCTION py_sum_squares(n INTEGER)
                RETURNS INTEGER
                LANGUAGE PYTHON
                RUNTIME_VERSION = '3.11'
                AS $$
                    squares = [i * i for i in range(1, n + 1)]
                    return sum(squares)
                $$
                """);

            Function sumSquares = schema.getFunction("py_sum_squares");
            Object result5 = PythonExecutor.executePythonFunction(sumSquares, Arrays.asList(5));
            logger.info("   Sum of squares from 1 to 5: " + result5);
            logger.info("   (1² + 2² + 3² + 4² + 5² = " + result5 + ")");
            logger.info("");

            // Example 6: String operations
            logger.info("6. Advanced String Operations");
            engine.execute("""
                CREATE FUNCTION py_reverse_words(text VARCHAR)
                RETURNS VARCHAR
                LANGUAGE PYTHON
                RUNTIME_VERSION = '3.11'
                AS $$
                    words = text.split()
                    return ' '.join(reversed(words))
                $$
                """);

            Function reverseWords = schema.getFunction("py_reverse_words");
            Object result6 = PythonExecutor.executePythonFunction(reverseWords, Arrays.asList("Python is awesome"));
            logger.info("   Original: 'Python is awesome'");
            logger.info("   Reversed: '" + result6 + "'");
            logger.info("");

            // Example 7: Complex function with nested definition
            logger.info("7. Fibonacci Sequence");
            List<Parameter> fibParams = new ArrayList<>();
            fibParams.add(new Parameter("n", NumericType.INTEGER));

            String fibBody = """
                def fibonacci(n):
                    if n <= 1:
                        return n
                    a, b = 0, 1
                    for _ in range(2, n + 1):
                        a, b = b, a + b
                    return b
                """;

            Function fibonacci = new Function("py_fib", fibParams, NumericType.INTEGER,
                                             fibBody, false, "PYTHON");

            logger.info("   Fibonacci sequence:");
            for (int i = 0; i <= 10; i++) {
                Object fib = PythonExecutor.executePythonFunction(fibonacci, Arrays.asList(i));
                logger.info("   F(" + i + ") = " + fib);
            }
            logger.info("");

            // Example 8: Multi-language comparison
            logger.info("8. Multi-Language Function Comparison");

            engine.execute("""
                CREATE FUNCTION sql_double(x INTEGER)
                RETURNS INTEGER
                LANGUAGE SQL
                AS 'x * 2'
                """);

            engine.execute("""
                CREATE FUNCTION js_double(x INTEGER)
                RETURNS INTEGER
                LANGUAGE JAVASCRIPT
                AS 'return x * 2;'
                """);

            engine.execute("""
                CREATE FUNCTION py_double(x INTEGER)
                RETURNS INTEGER
                LANGUAGE PYTHON
                RUNTIME_VERSION = '3.11'
                AS 'return x * 2'
                """);

            logger.info("   Three functions, same result:");
            Function sqlDouble = schema.getFunction("sql_double");
            Function jsDouble = schema.getFunction("js_double");
            Function pyDouble = schema.getFunction("py_double");

            logger.info("   SQL:        " + sqlDouble.getLanguage() + " - Body: " + sqlDouble.getBody());
            logger.info("   JavaScript: " + jsDouble.getLanguage() + " - Body: " + jsDouble.getBody());
            logger.info("   Python:     " + pyDouble.getLanguage() + " - Body: " + pyDouble.getBody());
            logger.info("");

            // Example 9: Practical use case - Temperature conversion
            logger.info("9. Practical Example: Temperature Conversion");
            List<Parameter> tempParams = new ArrayList<>();
            tempParams.add(new Parameter("fahrenheit", NumericType.INTEGER));

            String tempBody = """
                celsius = (fahrenheit - 32) * 5.0 / 9.0
                return int(round(celsius))
                """;

            Function fahrenheitToCelsius = new Function("f_to_c", tempParams, NumericType.INTEGER,
                                                       tempBody, false, "PYTHON");

            logger.info("   Fahrenheit to Celsius conversions:");
            int[] temps = {32, 50, 68, 86, 100, 212};
            for (final int temp : temps) {
                Object celsius = PythonExecutor.executePythonFunction(fahrenheitToCelsius, Arrays.asList(temp));
                logger.info("   " + temp + "°F = " + celsius + "°C");
            }
            logger.info("");

            // Show all functions
            logger.info("10. All Created Functions");
            var functions = engine.showFunctions();
            logger.info("   Total functions: " + functions.getRowCount());
            int sqlCount = 0, jsCount = 0, pyCount = 0;
            for (final var row : functions.getRows()) {
                String funcName = row.getValue(0).toString();
                try {
                    Function func = schema.getFunction(funcName.split("\\.")[2]);
                    if ("SQL".equals(func.getLanguage())) sqlCount++;
                    else if ("JAVASCRIPT".equals(func.getLanguage())) jsCount++;
                    else if ("PYTHON".equals(func.getLanguage())) pyCount++;
                } catch (final Exception e) {
                    // Skip if function name parsing fails
                }
            }
            logger.info("   - SQL functions: " + sqlCount);
            logger.info("   - JavaScript functions: " + jsCount);
            logger.info("   - Python functions: " + pyCount);

            logger.info("\n=== Python UDF Demo Complete ===");
            logger.info("\nKey Features Demonstrated:");
            logger.info("✅ Simple expressions (arithmetic, string operations)");
            logger.info("✅ Conditional logic (if/elif/else)");
            logger.info("✅ Loops and iteration (for, while)");
            logger.info("✅ List comprehensions");
            logger.info("✅ String manipulation (split, join, reversed)");
            logger.info("✅ Nested function definitions");
            logger.info("✅ Multi-language support (SQL, JavaScript, Python)");
            logger.info("✅ Practical use cases");

        } finally {
            engine.shutdown();
        }
    }
}
