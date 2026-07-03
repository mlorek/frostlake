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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValuesColumnAliasExample {
    private static final Logger logger = LoggerFactory.getLogger(ValuesColumnAliasExample.class);

    public static void main(final String[] args) {
        DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== VALUES with Column Aliases Examples ===\n");

            // Example 1: Basic column aliases
            logger.info("1. Basic Column Aliases");
            logger.info("   Query: SELECT * FROM VALUES(1, 2), (3, 4) AS t(i, j)");
            ResultSet rs1 = engine.executeQuery("SELECT * FROM VALUES(1, 2), (3, 4) AS t(i, j)");
            logger.info("   Columns: " + rs1.getColumns().get(0).getName() + ", " +
                             rs1.getColumns().get(1).getName());
            for (final var row : rs1.getRows()) {
                logger.info("     " + row.getValue(0) + ", " + row.getValue(1));
            }
            logger.info("");

            // Example 2: Using aliases in SELECT and GROUP BY
            logger.info("2. Column Aliases with GROUP BY");
            logger.info("   Query: SELECT i, count(*) FROM VALUES(1,2),(1,2) AS t(i,j) GROUP BY i");
            ResultSet rs2 = engine.executeQuery(
                "SELECT i, count(*) FROM VALUES(1, 2), (1, 2) AS t(i, j) GROUP BY i");
            logger.info("   Result:");
            for (final var row : rs2.getRows()) {
                logger.info("     i=" + row.getValue(0) + ", count=" + row.getValue(1));
            }
            logger.info("");

            // Example 3: Partial column aliases
            logger.info("3. Partial Column Aliases");
            logger.info("   Query: SELECT x FROM VALUES(10, 20), (30, 40) AS t(x)");
            ResultSet rs3 = engine.executeQuery(
                "SELECT x FROM VALUES(10, 20), (30, 40) AS t(x)");
            logger.info("   Column: " + rs3.getColumns().get(0).getName());
            for (final var row : rs3.getRows()) {
                logger.info("     " + row.getValue(0));
            }
            logger.info("");

            // Example 4: Using aliases in WHERE clause
            logger.info("4. Column Aliases with WHERE");
            logger.info("   Query: SELECT name, age FROM VALUES('Alice', 25), ('Bob', 30) AS people(name, age) WHERE age > 26");
            ResultSet rs4 = engine.executeQuery(
                "SELECT name, age FROM VALUES('Alice', 25), ('Bob', 30) AS people(name, age) WHERE age > 26");
            logger.info("   Results:");
            for (final var row : rs4.getRows()) {
                logger.info("     " + row.getValue(0) + ", age " + row.getValue(1));
            }
            logger.info("");

            logger.info("=== Column Aliases Demo Complete ===");
            logger.info("\nKey Points:");
            logger.info("✅ Column aliases let you name columns in VALUES clauses");
            logger.info("✅ Syntax: VALUES(...) AS table_name(col1, col2, ...)");
            logger.info("✅ Aliases can be used in SELECT, WHERE, GROUP BY, etc.");
            logger.info("✅ Makes queries more readable and maintainable");

        } finally {
            engine.shutdown();
        }
    }
}
