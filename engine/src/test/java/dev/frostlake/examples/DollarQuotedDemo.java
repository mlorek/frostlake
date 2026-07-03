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
import dev.frostlake.jdbc.DirectConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Demo showing dollar-quoted string literals for procedures and functions
 */
public class DollarQuotedDemo {
    private static final Logger logger = LoggerFactory.getLogger(DollarQuotedDemo.class);

    public static void main(final String[] args) throws Exception {
        logger.info("=== Dollar-Quoted String Literals Demo ===\n");

        DatabaseEngine engine = new DatabaseEngine();
        Connection conn = new DirectConnection(engine);
        Statement stmt = conn.createStatement();

        // Setup
        stmt.execute("DROP DATABASE IF EXISTS demo_db");
        stmt.execute("CREATE DATABASE demo_db");
        stmt.execute("USE DATABASE demo_db");
        stmt.execute("USE SCHEMA PUBLIC");

        logger.info("1. Creating procedure with dollar-quoted body...");
        logger.info("   (No need to escape single quotes!)");
        stmt.execute("""
                CREATE PROCEDURE calculate_bonus(salary INTEGER) RETURNS VARCHAR AS $$
                DECLARE
                  bonus INTEGER;
                  message VARCHAR;
                BEGIN
                  -- Single quotes work without escaping
                  IF salary > 100000 THEN
                    SET bonus = salary * 0.15;
                    SET message = 'High earner bonus: ' || bonus;
                  ELSIF salary > 50000 THEN
                    SET bonus = salary * 0.10;
                    SET message = 'Standard bonus: ' || bonus;
                  ELSE
                    SET bonus = salary * 0.05;
                    SET message = 'Entry level bonus: ' || bonus;
                  END IF;

                  RETURN message;
                END;
                $$
                """);
        logger.info("   ✓ Procedure created\n");

        logger.info("2. Creating function with simple dollar-quoted body...");
        stmt.execute("CREATE FUNCTION triple(n INTEGER) RETURNS INTEGER AS $$n * 3$$");
        logger.info("   ✓ Function created\n");

        logger.info("3. Creating procedure with OBJECT_CONSTRUCT...");
        logger.info("   (Dollar quotes make JSON-like syntax easy!)");
        stmt.execute("""
                CREATE PROCEDURE create_employee(name VARCHAR, age INTEGER) RETURNS OBJECT AS $$
                DECLARE
                  emp OBJECT;
                BEGIN
                  SET emp = OBJECT_CONSTRUCT('name', name, 'age', age, 'status', 'active');
                  RETURN emp;
                END;
                $$
                """);
        logger.info("   ✓ Procedure created\n");

        logger.info("4. Traditional single-quoted syntax still works...");
        stmt.execute("CREATE FUNCTION double_it(x INTEGER) RETURNS INTEGER AS 'x * 2'");
        logger.info("   ✓ Function created with traditional syntax\n");

        logger.info("5. Listing created procedures and functions...");
        logger.info("\n   Procedures:");
        ResultSet rs = stmt.executeQuery("SHOW PROCEDURES");
        while (rs.next()) {
            logger.info("   - {}", rs.getString("name"));
        }
        rs.close();

        logger.info("\n   Functions:");
        rs = stmt.executeQuery("SHOW FUNCTIONS");
        while (rs.next()) {
            logger.info("   - {}", rs.getString("name"));
        }
        rs.close();

        logger.info("\n=== Demo Complete ===");
        logger.info("\nKey Benefits of Dollar-Quoted Strings:");
        logger.info("✓ No need to escape single quotes");
        logger.info("✓ Cleaner multi-line code");
        logger.info("✓ Better for complex procedural logic");
        logger.info("✓ Compatible with Snowflake SQL syntax");

        conn.close();
        engine.shutdown();
    }
}
