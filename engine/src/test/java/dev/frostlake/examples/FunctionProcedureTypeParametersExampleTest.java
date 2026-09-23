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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Routine parameters declared with type parameters — VARCHAR(50), DECIMAL(10, 2), TIMESTAMP(9), CHAR(2),
 * NUMBER(18, 2) — and each routine dropped by its signature's types. A SQL body must produce the declared
 * return type, and a CHAR(n) argument is dropped as CHAR(n). Every cell is live-verified.
 */
public class FunctionProcedureTypeParametersExampleTest extends BaseDatabaseTest {

    /** The first row's first cell, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void demonstrateTypeParameters() {
        engine.execute("CREATE FUNCTION format_name(first_name VARCHAR(50), last_name VARCHAR(50)) RETURNS VARCHAR"
            + " LANGUAGE SQL AS 'first_name'");
        assertEquals("Ann", answer("SELECT format_name('Ann', 'Lee')"));
        engine.execute("DROP FUNCTION format_name(VARCHAR, VARCHAR)");

        engine.execute("CREATE FUNCTION calculate_total(price DECIMAL(10, 2), quantity DECIMAL(8, 2)) RETURNS DECIMAL"
            + " LANGUAGE SQL AS 'price'");
        engine.execute("DROP FUNCTION calculate_total(DECIMAL, DECIMAL)");

        // The body must produce what RETURNS declares.
        assertEquals("Declared return type 'VARCHAR(134217728)' is incompatible with actual return type 'TIMESTAMP_NTZ(9)'",
            answer("CREATE FUNCTION format_timestamp(event_time TIMESTAMP(9)) RETURNS VARCHAR LANGUAGE SQL AS 'event_time'"));
        engine.execute("CREATE FUNCTION format_timestamp(event_time TIMESTAMP(9)) RETURNS VARCHAR"
            + " LANGUAGE SQL AS 'TO_VARCHAR(event_time)'");
        assertEquals("2024-01-01 12:00:00.123",
            answer("SELECT format_timestamp('2024-01-01 12:00:00.123456789'::TIMESTAMP(9))"));
        engine.execute("DROP FUNCTION format_timestamp(TIMESTAMP)");

        engine.execute("CREATE FUNCTION process_order(order_id INTEGER, customer_name VARCHAR(100),"
            + " total_amount DECIMAL(12, 2), order_date TIMESTAMP(6)) RETURNS VARCHAR LANGUAGE SQL AS 'customer_name'");
        engine.execute("DROP FUNCTION process_order(INTEGER, VARCHAR, DECIMAL, TIMESTAMP)");

        engine.execute("CREATE FUNCTION validate_country_code(code CHAR(2)) RETURNS INTEGER LANGUAGE SQL AS '1'");
        engine.execute("DROP FUNCTION validate_country_code(CHAR(2))");

        engine.execute("CREATE FUNCTION calculate_tax(amount NUMBER(15)) RETURNS NUMBER LANGUAGE SQL AS 'amount'");
        engine.execute("DROP FUNCTION calculate_tax(NUMBER)");

        engine.execute("CREATE FUNCTION compound_interest(principal NUMBER(18, 2), rate NUMBER(5, 4), years NUMBER(3))"
            + " RETURNS NUMBER LANGUAGE SQL AS 'principal'");
        engine.execute("DROP FUNCTION compound_interest(NUMBER, NUMBER, NUMBER)");

        engine.execute("CREATE PROCEDURE update_customer(customer_id INTEGER, email VARCHAR(200), phone VARCHAR(20))"
            + " RETURNS INTEGER LANGUAGE SQL AS 'BEGIN RETURN customer_id; END'");
        engine.execute("DROP PROCEDURE update_customer(INTEGER, VARCHAR, VARCHAR)");

        engine.execute("CREATE FUNCTION calculate_discount(price DECIMAL(10, 2), discount_pct DECIMAL(5, 2))"
            + " RETURNS DECIMAL LANGUAGE SQL AS 'price'");
        engine.execute("CREATE OR REPLACE FUNCTION calculate_discount(price DECIMAL(10, 2), discount_pct DECIMAL(5, 2))"
            + " RETURNS DECIMAL LANGUAGE SQL AS 'price'");
        engine.execute("DROP FUNCTION calculate_discount(DECIMAL, DECIMAL)");

        engine.execute("CREATE SCHEMA financial");
        engine.execute("CREATE FUNCTION financial.calculate_payment(loan_amount DECIMAL(15, 2),"
            + " interest_rate DECIMAL(6, 4), term_months INTEGER) RETURNS DECIMAL LANGUAGE SQL AS 'loan_amount'");
        engine.execute("DROP FUNCTION financial.calculate_payment(DECIMAL, DECIMAL, INTEGER)");

        engine.execute("CREATE FUNCTION generate_invoice(invoice_id INTEGER, customer_code CHAR(10),"
            + " customer_name VARCHAR(100), billing_address VARCHAR(500), subtotal DECIMAL(12, 2),"
            + " tax_amount DECIMAL(10, 2), total_amount DECIMAL(12, 2), invoice_date TIMESTAMP(6))"
            + " RETURNS VARCHAR LANGUAGE SQL AS 'customer_name'");
        engine.execute("DROP FUNCTION generate_invoice(INTEGER, CHAR(10), VARCHAR, VARCHAR, DECIMAL, DECIMAL, DECIMAL,"
            + " TIMESTAMP)");
    }
}
