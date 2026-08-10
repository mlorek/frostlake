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
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.rt.js.JavaScriptProcedureExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Example demonstrating JavaScript stored procedures with snowflake.execute() API
 *
 * This matches Snowflake DB's JavaScript procedure capabilities including:
 * - snowflake.execute() for running SQL
 * - ResultSet processing with next() and getColumnValue()
 * - Returning complex objects
 * - Try-catch-finally error handling
 */
public final class JavaScriptProcedureExample {

    /** Static helpers only — never instantiated. */
    private JavaScriptProcedureExample() {
    }
    private static final Logger logger = LoggerFactory.getLogger(JavaScriptProcedureExample.class);

    public static void main(final String[] args) {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== JavaScript Stored Procedure Examples ===\n");

            // Example 1: User's test case from the issue
            logger.info("1. User's Test Case: Procedure p1 with error handling");
            engine.execute("""
                CREATE PROCEDURE public.p1 (s STRING)
                RETURNS OBJECT
                LANGUAGE JAVASCRIPT
                AS
                $$
                  const obj = {
                    p: null
                  };
                  try{
                    var cmd = "SELECT 'a' as c";
                    var rs = snowflake.execute({sqlText: cmd});
                    if(rs.next()){
                      obj.rc = rs.getColumnValue(1);
                    }
                    obj.p = 1;
                  }
                  catch(err){
                    obj.msg = err.message;
                    obj.f = true;
                  }
                  finally{
                    obj.p2 = 2;
                    return obj;
                  }
                $$
                """);

            final Schema schema = engine.getCatalog().getDatabase("DEMO_DB").getSchema("PUBLIC");
            final Procedure p1 = schema.getProcedure("p1");
            final Object result1 = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
                p1,
                Arrays.asList("test"),
                engine
            );
            logger.info("   Result: " + result1);
            logger.info("");

            // Example 2: Simple query execution
            logger.info("2. Simple Query Execution");
            engine.execute("""
                CREATE PROCEDURE get_number()
                RETURNS INTEGER
                LANGUAGE JAVASCRIPT
                AS
                $$
                    var cmd = "SELECT 42 as answer";
                    var rs = snowflake.execute({sqlText: cmd});
                    if (rs.next()) {
                        return rs.getColumnValue(1);
                    }
                    return null;
                $$
                """);

            final Procedure getNumber = schema.getProcedure("get_number");
            final Object result2 = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
                getNumber,
                Arrays.asList(),
                engine
            );
            logger.info("   Answer to everything: " + result2);
            logger.info("");

            // Example 3: Processing multiple rows
            logger.info("3. Processing Multiple Rows");
            engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price FLOAT)");
            engine.execute("INSERT INTO products VALUES (1, 'Laptop', 999.99), (2, 'Mouse', 29.99), (3, 'Keyboard', 79.99)");

            engine.execute("""
                CREATE PROCEDURE list_products()
                RETURNS OBJECT
                LANGUAGE JAVASCRIPT
                AS
                $$
                    var cmd = "SELECT id, name, price FROM products ORDER BY price DESC";
                    var rs = snowflake.execute({sqlText: cmd});

                    const items = [];
                    while (rs.next()) {
                        items.push({
                            id: rs.getColumnValue(1),
                            name: rs.getColumnValue(2),
                            price: rs.getColumnValue(3)
                        });
                    }

                    return {
                        count: items.length,
                        items: items,
                        totalValue: items.reduce(function(sum, item) { return sum + item.price; }, 0)
                    };
                $$
                """);

            final Procedure listProducts = schema.getProcedure("list_products");
            final Object result3 = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
                listProducts,
                Arrays.asList(),
                engine
            );
            logger.info("   Products: " + result3);
            logger.info("");

            // Example 4: Dynamic SQL with parameters
            logger.info("4. Dynamic SQL with Parameters");
            engine.execute("CREATE TABLE sales (amount FLOAT, region VARCHAR)");
            engine.execute("INSERT INTO sales VALUES (100, 'North'), (200, 'South'), (150, 'East'), (300, 'West')");

            engine.execute("""
                CREATE PROCEDURE sales_above(min_amount VARCHAR)
                RETURNS OBJECT
                LANGUAGE JAVASCRIPT
                AS
                $$
                    var cmd = "SELECT COUNT(*) as cnt, SUM(amount) as total FROM sales WHERE amount > " + min_amount;
                    var rs = snowflake.execute({sqlText: cmd});

                    if (rs.next()) {
                        return {
                            count: rs.getColumnValue(1),
                            total: rs.getColumnValue(2),
                            query: cmd
                        };
                    }
                    return null;
                $$
                """);

            final Procedure salesAbove = schema.getProcedure("sales_above");
            final Object result4 = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
                salesAbove,
                Arrays.asList("150"),
                engine
            );
            logger.info("   Sales above 150: " + result4);
            logger.info("");

            // Example 5: Accessing columns by name
            logger.info("5. Accessing Columns by Name");
            engine.execute("CREATE TABLE employees (first_name VARCHAR, last_name VARCHAR, salary FLOAT)");
            engine.execute("INSERT INTO employees VALUES ('John', 'Doe', 75000), ('Jane', 'Smith', 85000)");

            engine.execute("""
                CREATE PROCEDURE get_employee()
                RETURNS OBJECT
                LANGUAGE JAVASCRIPT
                AS
                $$
                    var cmd = "SELECT first_name, last_name, salary FROM employees ORDER BY salary DESC LIMIT 1";
                    var rs = snowflake.execute({sqlText: cmd});

                    if (rs.next()) {
                        return {
                            firstName: rs.getColumnValue('first_name'),
                            lastName: rs.getColumnValue('last_name'),
                            salary: rs.getColumnValue('salary'),
                            fullName: rs.getColumnValue('first_name') + ' ' + rs.getColumnValue('last_name')
                        };
                    }
                    return null;
                $$
                """);

            final Procedure getEmployee = schema.getProcedure("get_employee");
            final Object result5 = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
                getEmployee,
                Arrays.asList(),
                engine
            );
            logger.info("   Top paid employee: " + result5);
            logger.info("");

            // Example 6: Error handling
            logger.info("6. Error Handling");
            engine.execute("""
                CREATE PROCEDURE safe_query(table_name VARCHAR)
                RETURNS OBJECT
                LANGUAGE JAVASCRIPT
                AS
                $$
                    const result = {
                        success: false,
                        data: null,
                        error: null,
                        table: table_name
                    };

                    try {
                        var cmd = "SELECT COUNT(*) as cnt FROM " + table_name;
                        var rs = snowflake.execute({sqlText: cmd});

                        if (rs.next()) {
                            result.data = {count: rs.getColumnValue(1)};
                            result.success = true;
                        }
                    } catch (err) {
                        result.error = err.message;
                    }

                    return result;
                $$
                """);

            final Procedure safeQuery = schema.getProcedure("safe_query");

            logger.info("   Query existing table (employees):");
            final Object result6a = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
                safeQuery,
                Arrays.asList("employees"),
                engine
            );
            logger.info("   " + result6a);

            logger.info("   Query non-existent table:");
            final Object result6b = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
                safeQuery,
                Arrays.asList("nonexistent"),
                engine
            );
            logger.info("   " + result6b);
            logger.info("");

            // Example 7: Aggregation and statistics
            logger.info("7. Data Aggregation");
            engine.execute("CREATE TABLE metrics (name VARCHAR, value INTEGER)");
            engine.execute("INSERT INTO metrics VALUES ('cpu', 75), ('memory', 60), ('disk', 85), ('network', 45)");

            engine.execute("""
                CREATE PROCEDURE compute_stats()
                RETURNS OBJECT
                LANGUAGE JAVASCRIPT
                AS
                $$
                    var cmd = "SELECT name, value FROM metrics";
                    var rs = snowflake.execute({sqlText: cmd});

                    const values = [];
                    while (rs.next()) {
                        values.push({
                            name: rs.getColumnValue('name'),
                            value: rs.getColumnValue('value')
                        });
                    }

                    var sum = 0;
                    var max = 0;
                    var min = 999999;
                    for (var i = 0; i < values.length; i++) {
                        sum += values[i].value;
                        if (values[i].value > max) max = values[i].value;
                        if (values[i].value < min) min = values[i].value;
                    }

                    return {
                        count: values.length,
                        sum: sum,
                        avg: sum / values.length,
                        max: max,
                        min: min,
                        data: values
                    };
                $$
                """);

            final Procedure computeStats = schema.getProcedure("compute_stats");
            final Object result7 = JavaScriptProcedureExecutor.executeJavaScriptProcedure(
                computeStats,
                Arrays.asList(),
                engine
            );
            logger.info("   Statistics: " + result7);

            logger.info("\n=== JavaScript Stored Procedures Demo Complete ===");
            logger.info("\nKey Features Demonstrated:");
            logger.info("✅ snowflake.execute() API");
            logger.info("✅ ResultSet processing (next(), getColumnValue())");
            logger.info("✅ Column access by index and name");
            logger.info("✅ Multiple row iteration");
            logger.info("✅ Try-catch-finally error handling");
            logger.info("✅ Returning complex objects");
            logger.info("✅ Dynamic SQL generation");
            logger.info("✅ Data aggregation and statistics");

        } finally {
            engine.shutdown();
        }
    }
}
