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
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FunctionProcedureTypeParametersExample {
    private static final Logger logger = LoggerFactory.getLogger(FunctionProcedureTypeParametersExample.class);

    @Test
    public void demonstrateTypeParameters() {
        DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== CREATE FUNCTION/PROCEDURE with Type Parameters ===");

            // Example 1: VARCHAR with size
            logger.info("\n1. Function with VARCHAR(size) parameter:");
            engine.execute("CREATE FUNCTION format_name(first_name VARCHAR(50), last_name VARCHAR(50)) RETURNS VARCHAR LANGUAGE SQL AS 'first_name'");
            logger.info("Created: format_name(first_name VARCHAR(50), last_name VARCHAR(50))");
            engine.execute("DROP FUNCTION format_name(VARCHAR, VARCHAR)");

            // Example 2: DECIMAL with precision and scale
            logger.info("\n2. Function with DECIMAL(precision, scale) parameters:");
            engine.execute("CREATE FUNCTION calculate_total(price DECIMAL(10, 2), quantity DECIMAL(8, 2)) RETURNS DECIMAL LANGUAGE SQL AS 'price'");
            logger.info("Created: calculate_total(price DECIMAL(10, 2), quantity DECIMAL(8, 2))");
            engine.execute("DROP FUNCTION calculate_total(DECIMAL, DECIMAL)");

            // Example 3: TIMESTAMP with precision
            logger.info("\n3. Function with TIMESTAMP(precision) parameter:");
            engine.execute("CREATE FUNCTION format_timestamp(event_time TIMESTAMP(9)) RETURNS VARCHAR LANGUAGE SQL AS 'event_time'");
            logger.info("Created: format_timestamp(event_time TIMESTAMP(9))");
            engine.execute("DROP FUNCTION format_timestamp(TIMESTAMP)");

            // Example 4: Mixed parameter types
            logger.info("\n4. Function with mixed parameter types:");
            engine.execute("CREATE FUNCTION process_order(order_id INTEGER, customer_name VARCHAR(100), total_amount DECIMAL(12, 2), order_date TIMESTAMP(6)) RETURNS VARCHAR LANGUAGE SQL AS 'customer_name'");
            logger.info("Created: process_order(");
            logger.info("  order_id INTEGER,");
            logger.info("  customer_name VARCHAR(100),");
            logger.info("  total_amount DECIMAL(12, 2),");
            logger.info("  order_date TIMESTAMP(6)");
            logger.info(")");
            engine.execute("DROP FUNCTION process_order(INTEGER, VARCHAR, DECIMAL, TIMESTAMP)");

            // Example 5: CHAR with size
            logger.info("\n5. Function with CHAR(size) parameter:");
            engine.execute("CREATE FUNCTION validate_country_code(code CHAR(2)) RETURNS INTEGER LANGUAGE SQL AS '1'");
            logger.info("Created: validate_country_code(code CHAR(2))");
            engine.execute("DROP FUNCTION validate_country_code(CHAR)");

            // Example 6: NUMBER with precision
            logger.info("\n6. Function with NUMBER(precision) parameter:");
            engine.execute("CREATE FUNCTION calculate_tax(amount NUMBER(15)) RETURNS NUMBER LANGUAGE SQL AS 'amount'");
            logger.info("Created: calculate_tax(amount NUMBER(15))");
            engine.execute("DROP FUNCTION calculate_tax(NUMBER)");

            // Example 7: NUMBER with precision and scale
            logger.info("\n7. Function with NUMBER(precision, scale) parameters:");
            engine.execute("CREATE FUNCTION compound_interest(principal NUMBER(18, 2), rate NUMBER(5, 4), years NUMBER(3)) RETURNS NUMBER LANGUAGE SQL AS 'principal'");
            logger.info("Created: compound_interest(");
            logger.info("  principal NUMBER(18, 2),");
            logger.info("  rate NUMBER(5, 4),");
            logger.info("  years NUMBER(3)");
            logger.info(")");
            engine.execute("DROP FUNCTION compound_interest(NUMBER, NUMBER, NUMBER)");

            // Example 8: Procedure with typed parameters
            logger.info("\n8. Procedure with typed parameters:");
            engine.execute("CREATE PROCEDURE update_customer(customer_id INTEGER, email VARCHAR(200), phone VARCHAR(20)) RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN customer_id; END'");
            logger.info("Created procedure: update_customer(");
            logger.info("  customer_id INTEGER,");
            logger.info("  email VARCHAR(200),");
            logger.info("  phone VARCHAR(20)");
            logger.info(")");
            engine.execute("DROP PROCEDURE update_customer(INTEGER, VARCHAR, VARCHAR)");

            // Example 9: CREATE OR REPLACE with typed parameters
            logger.info("\n9. CREATE OR REPLACE with typed parameters:");
            engine.execute("CREATE FUNCTION calculate_discount(price DECIMAL(10, 2), discount_pct DECIMAL(5, 2)) RETURNS DECIMAL LANGUAGE SQL AS 'price'");
            logger.info("Created: calculate_discount(price DECIMAL(10, 2), discount_pct DECIMAL(5, 2))");

            engine.execute("CREATE OR REPLACE FUNCTION calculate_discount(price DECIMAL(10, 2), discount_pct DECIMAL(5, 2)) RETURNS DECIMAL LANGUAGE SQL AS 'price'");
            logger.info("Replaced with same signature");
            engine.execute("DROP FUNCTION calculate_discount(DECIMAL, DECIMAL)");

            // Example 10: Schema-qualified with typed parameters
            logger.info("\n10. Schema-qualified function with typed parameters:");
            engine.execute("CREATE SCHEMA financial");
            engine.execute("CREATE FUNCTION financial.calculate_payment(loan_amount DECIMAL(15, 2), interest_rate DECIMAL(6, 4), term_months INTEGER) RETURNS DECIMAL LANGUAGE SQL AS 'loan_amount'");
            logger.info("Created: financial.calculate_payment(");
            logger.info("  loan_amount DECIMAL(15, 2),");
            logger.info("  interest_rate DECIMAL(6, 4),");
            logger.info("  term_months INTEGER");
            logger.info(")");
            engine.execute("DROP FUNCTION financial.calculate_payment(DECIMAL, DECIMAL, INTEGER)");

            // Example 11: Complex signature
            logger.info("\n11. Complex function signature:");
            engine.execute("CREATE FUNCTION generate_invoice(invoice_id INTEGER, customer_code CHAR(10), customer_name VARCHAR(100), billing_address VARCHAR(500), subtotal DECIMAL(12, 2), tax_amount DECIMAL(10, 2), total_amount DECIMAL(12, 2), invoice_date TIMESTAMP(6)) RETURNS VARCHAR LANGUAGE SQL AS 'customer_name'");
            logger.info("Created complex function with 8 typed parameters:");
            logger.info("  invoice_id INTEGER");
            logger.info("  customer_code CHAR(10)");
            logger.info("  customer_name VARCHAR(100)");
            logger.info("  billing_address VARCHAR(500)");
            logger.info("  subtotal DECIMAL(12, 2)");
            logger.info("  tax_amount DECIMAL(10, 2)");
            logger.info("  total_amount DECIMAL(12, 2)");
            logger.info("  invoice_date TIMESTAMP(6)");
            engine.execute("DROP FUNCTION generate_invoice(INTEGER, CHAR, VARCHAR, VARCHAR, DECIMAL, DECIMAL, DECIMAL, TIMESTAMP)");

            logger.info("\n=== Summary ===");
            logger.info("Type parameters in function/procedure signatures:");
            logger.info("  - VARCHAR(size)              - String with maximum length");
            logger.info("  - CHAR(size)                 - Fixed-length string");
            logger.info("  - DECIMAL(precision, scale)  - Decimal with precision and scale");
            logger.info("  - NUMBER(precision)          - Number with precision");
            logger.info("  - NUMBER(precision, scale)   - Number with precision and scale");
            logger.info("  - TIMESTAMP(precision)       - Timestamp with fractional seconds precision");
            logger.info("\nBenefits:");
            logger.info("  - More precise type definitions");
            logger.info("  - Better documentation of expected data ranges");
            logger.info("  - Matches Snowflake SQL syntax");
            logger.info("  - Works with CREATE OR REPLACE");
            logger.info("  - Compatible with DROP FUNCTION/PROCEDURE signature matching");

        } finally {
            engine.shutdown();
        }
    }
}
