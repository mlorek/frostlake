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
import dev.frostlake.executor.udf.JavaScriptExecutor;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Schema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Practical real-world examples of JavaScript UDF usage
 */
public class JavaScriptUDFPracticalTest {

    private static final Logger logger = LoggerFactory.getLogger(JavaScriptUDFPracticalTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testEmailValidation() {
        logger.info("Testing email validation JavaScript UDF");

        engine.execute("""
            CREATE FUNCTION validate_email(email VARCHAR)
            RETURNS VARCHAR
            LANGUAGE JAVASCRIPT
            AS $$
                var pattern = /^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/;
                return pattern.test(email) ? 'VALID' : 'INVALID';
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("validate_email");

        assertEquals("VALID",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList("user@example.com")));
        assertEquals("INVALID",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList("invalid.email")));
        assertEquals("INVALID",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList("@example.com")));
    }

    @Test
    public void testPhoneNumberFormatting() {
        logger.info("Testing phone number formatting JavaScript UDF");

        engine.execute("""
            CREATE FUNCTION format_phone(phone VARCHAR)
            RETURNS VARCHAR
            LANGUAGE JAVASCRIPT
            AS $$
                var digits = phone.replace(/\\D/g, '');
                if (digits.length === 10) {
                    return '(' + digits.substr(0,3) + ') ' +
                           digits.substr(3,3) + '-' + digits.substr(6);
                }
                return phone;
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("format_phone");

        assertEquals("(555) 123-4567",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList("5551234567")));
        assertEquals("(555) 123-4567",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList("555-123-4567")));
    }

    @Test
    public void testJSONExtraction() {
        logger.info("Testing JSON extraction JavaScript UDF");

        engine.execute("""
            CREATE FUNCTION extract_json_field(json VARCHAR, field VARCHAR)
            RETURNS VARCHAR
            LANGUAGE JAVASCRIPT
            AS $$
                try {
                    var obj = JSON.parse(json);
                    return obj[field] ? String(obj[field]) : null;
                } catch (e) {
                    return null;
                }
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("extract_json_field");

        String json = "{\"name\":\"John\",\"age\":30,\"city\":\"New York\"}";
        assertEquals("John",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList(json, "name")));
        assertEquals("30",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList(json, "age")));
        assertEquals("New York",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList(json, "city")));
    }

    @Test
    public void testTextSlugGeneration() {
        logger.info("Testing text slug generation JavaScript UDF");

        engine.execute("""
            CREATE FUNCTION generate_slug(text VARCHAR)
            RETURNS VARCHAR
            LANGUAGE JAVASCRIPT
            AS $$
                return text.toLowerCase()
                    .replace(/[^a-z0-9]+/g, '-')
                    .replace(/^-+|-+$/g, '');
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("generate_slug");

        assertEquals("hello-world",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList("Hello World")));
        assertEquals("javascript-udf-test",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList("JavaScript UDF Test!")));
    }

    @Test
    public void testPasswordStrength() {
        logger.info("Testing password strength checker JavaScript UDF");

        engine.execute("""
            CREATE FUNCTION check_password_strength(pw VARCHAR)
            RETURNS VARCHAR
            LANGUAGE JAVASCRIPT
            AS $$
                var len = pw.length;
                if (len >= 12) return 'STRONG';
                if (len >= 8) return 'MEDIUM';
                return 'WEAK';
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("check_password_strength");

        assertEquals("WEAK",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList("pass")));
        assertEquals("MEDIUM",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList("Password123")));
        assertEquals("STRONG",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList("P@ssw0rd!2023")));
    }

    @Test
    public void testDateDifferenceCalculation() {
        logger.info("Testing date difference calculation JavaScript UDF");

        engine.execute("""
            CREATE FUNCTION days_until_date(target_year INTEGER, target_month INTEGER, target_day INTEGER)
            RETURNS INTEGER
            LANGUAGE JAVASCRIPT
            AS $$
                var now = new Date();
                var target = new Date(target_year, target_month - 1, target_day);
                var diff = target - now;
                return Math.ceil(diff / (1000 * 60 * 60 * 24));
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("days_until_date");

        Object result = JavaScriptExecutor.executeJavaScriptFunction(
            func,
            Arrays.asList(2026, 12, 31)
        );
        assertNotNull(result);
        logger.info("Days until 2026-12-31: {}", result);
    }

    @Test
    public void testCurrencyFormatting() {
        logger.info("Testing currency formatting JavaScript UDF");

        engine.execute("""
            CREATE FUNCTION format_currency(amount FLOAT)
            RETURNS VARCHAR
            LANGUAGE JAVASCRIPT
            AS $$
                return '$' + amount.toFixed(2).replace(/\\d(?=(\\d{3})+\\.)/g, '$&,');
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("format_currency");

        assertEquals("$1,234.56",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList(1234.56)));
        assertEquals("$1,000,000.00",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList(1000000.0)));
    }

    @Test
    public void testInitialsExtraction() {
        logger.info("Testing initials extraction JavaScript UDF");

        engine.execute("""
            CREATE FUNCTION get_initials(full_name VARCHAR)
            RETURNS VARCHAR
            LANGUAGE JAVASCRIPT
            AS 'return full_name.split(" ").map(function(w) { return w.charAt(0).toUpperCase(); }).join("");'
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("get_initials");

        assertEquals("JD",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList("John Doe")));
        assertEquals("ABC",
            JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList("Alice Bob Charlie")));
    }

    @Test
    public void testHexEncoding() {
        logger.info("Testing hex encoding JavaScript UDF");

        engine.execute("""
            CREATE FUNCTION encode_hex(text VARCHAR)
            RETURNS VARCHAR
            LANGUAGE JAVASCRIPT
            AS $$
                var hex = '';
                for (var i = 0; i < text.length; i++) {
                    var charCode = text.charCodeAt(i);
                    hex += charCode.toString(16).padStart(2, '0');
                }
                return hex.toUpperCase();
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("encode_hex");

        Object result = JavaScriptExecutor.executeJavaScriptFunction(func, Arrays.asList("Hello"));
        assertNotNull(result);
        assertEquals("48656C6C6F", result);
        logger.info("Hex encoded 'Hello': {}", result);
    }

    @Test
    public void testComplexBusinessLogic() {
        logger.info("Testing complex business logic JavaScript UDF");

        engine.execute("""
            CREATE FUNCTION calculate_shipping_cost(weight FLOAT, distance INTEGER, is_express INTEGER)
            RETURNS FLOAT
            LANGUAGE JAVASCRIPT
            AS $$
                var base_cost = 5.0;
                var weight_cost = weight * 0.5;
                var distance_cost = distance * 0.1;
                var total = base_cost + weight_cost + distance_cost;

                if (is_express === 1) {
                    total = total * 1.5;
                }

                if (weight > 50) {
                    total = total * 0.9; // 10% discount for heavy items
                }

                return Math.round(total * 100) / 100;
            $$
            """);

        Schema schema = engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC");
        Function func = schema.getFunction("calculate_shipping_cost");

        // Standard shipping: 10 lbs, 100 miles, not express
        Object result1 = JavaScriptExecutor.executeJavaScriptFunction(
            func,
            Arrays.asList(10.0, 100, 0)
        );
        logger.info("Standard shipping cost: ${}", result1);
        assertTrue(((Number) result1).doubleValue() > 0);

        // Express shipping: 10 lbs, 100 miles, express
        Object result2 = JavaScriptExecutor.executeJavaScriptFunction(
            func,
            Arrays.asList(10.0, 100, 1)
        );
        logger.info("Express shipping cost: ${}", result2);
        assertTrue(((Number) result2).doubleValue() > ((Number) result1).doubleValue());

        // Heavy item discount: 60 lbs, 100 miles, not express
        Object result3 = JavaScriptExecutor.executeJavaScriptFunction(
            func,
            Arrays.asList(60.0, 100, 0)
        );
        logger.info("Heavy item shipping cost (with discount): ${}", result3);
        assertTrue(((Number) result3).doubleValue() > 0);
    }
}
