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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example demonstrating SELECT without FROM clause
 */
public class SelectWithoutFromExample {
    private static final Logger logger = LoggerFactory.getLogger(SelectWithoutFromExample.class);

    public static void main(final String[] args) {
        try {
            logger.info("=== SELECT Without FROM Clause Examples ===\n");

            DatabaseEngine engine = new DatabaseEngine();

            // 1. Simple literals
            logger.info("1. Simple Literals:");
            logger.info("   " + "-".repeat(60));

            ResultSet rs1 = engine.executeQuery("SELECT 42 as answer, 'Hello' as greeting");
            printResult(rs1);

            // 2. Expressions and calculations
            logger.info("\n2. Expressions and Calculations:");
            logger.info("   " + "-".repeat(60));

            ResultSet rs2 = engine.executeQuery("SELECT 10 + 5 as sum, 10 * 5 as product, 2 > 1 as comparison");
            printResult(rs2);

            // 3. String functions
            logger.info("\n3. String Functions:");
            logger.info("   " + "-".repeat(60));

            ResultSet rs3 = engine.executeQuery("SELECT UPPER('hello') as upper_text, LOWER('WORLD') as lower_text");
            printResult(rs3);

            // 4. JSON object literal
            logger.info("\n4. JSON Object Literal:");
            logger.info("   " + "-".repeat(60));

            ResultSet rs4 = engine.executeQuery("SELECT {'name': 'Alice', 'age': 30} as user_json");
            printResult(rs4);

            // 5. JSON array literal
            logger.info("\n5. JSON Array Literal:");
            logger.info("   " + "-".repeat(60));

            ResultSet rs5 = engine.executeQuery("SELECT [1, 2, 3, 4, 5] as numbers");
            printResult(rs5);

            // 6. Complex nested JSON
            logger.info("\n6. Complex Nested JSON:");
            logger.info("   " + "-".repeat(60));

            String complexQuery = """
                SELECT {
                    'status': 'success',
                    'code': 200,
                    'data': {
                        'message': 'Hello World'
                    },
                    'items': [1, 2, 3]
                } as response
                """;

            ResultSet rs6 = engine.executeQuery(complexQuery);
            printResult(rs6);

            // 7. Multiple values in one SELECT
            logger.info("\n7. Multiple Values:");
            logger.info("   " + "-".repeat(60));

            ResultSet rs7 = engine.executeQuery("SELECT 'OK' as status, 1 as ready, 100 + 200 as total");
            printResult(rs7);

            // 8. Use cases
            logger.info("\n8. Common Use Cases:");
            logger.info("   " + "-".repeat(60));
            logger.info("   \u2713 Testing expressions without data");
            logger.info("   \u2713 Generating constant values");
            logger.info("   \u2713 Building JSON responses");
            logger.info("   \u2713 Checking function behavior");
            logger.info("   \u2713 Computing derived values");
            logger.info("   \u2713 Health check queries");

            engine.shutdown();
            logger.info("\nExample completed successfully!");

        } catch (final Exception e) {
            logger.error("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printResult(final ResultSet rs) {
        if (rs.getRows().isEmpty()) {
            logger.info("   (No results)");
            return;
        }

        // Print each row
        for (final Row row : rs.getRows()) {
            StringBuilder line = new StringBuilder("   ");
            for (int i = 0; i < rs.getColumnCount(); i++) {
                String colName = rs.getColumns().get(i).getName();
                Object value = row.getValue(i);
                String strValue = value != null ? value.toString() : "NULL";

                // Format output
                if (i > 0) line.append(", ");
                line.append(colName).append(" = ").append(strValue);
            }
            logger.info(line.toString());
        }
    }
}
