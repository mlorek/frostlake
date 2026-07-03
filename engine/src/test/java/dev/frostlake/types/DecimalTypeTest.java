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

package dev.frostlake.types;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DecimalTypeTest {

    private static final Logger logger = LoggerFactory.getLogger(DecimalTypeTest.class);
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
    public void testDecimalWithPrecisionAndScale() {
        logger.info("Testing DECIMAL(10, 2)");
        engine.execute("CREATE TABLE prices (id INTEGER, amount DECIMAL(10, 2))");
        engine.execute("INSERT INTO prices VALUES (1, 123.45)");

        ResultSet rs = engine.executeQuery("SELECT * FROM prices");
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(123.45, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 0.001);
    }

    @Test
    public void testDecimalWithPrecisionOnly() {
        logger.info("Testing DECIMAL(5)");
        engine.execute("CREATE TABLE counts (id INTEGER, value DECIMAL(5))");
        engine.execute("INSERT INTO counts VALUES (1, 12345)");

        ResultSet rs = engine.executeQuery("SELECT * FROM counts");
        assertEquals(1, rs.getRowCount());
        assertEquals(12345.0, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 0.001);
    }

    @Test
    public void testNumberWithPrecisionAndScale() {
        logger.info("Testing NUMBER(15, 4)");
        engine.execute("CREATE TABLE measurements (id INTEGER, value NUMBER(15, 4))");
        engine.execute("INSERT INTO measurements VALUES (1, 1234.5678)");

        ResultSet rs = engine.executeQuery("SELECT * FROM measurements");
        assertEquals(1, rs.getRowCount());
        assertEquals(1234.5678, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 0.0001);
    }

    @Test
    public void testVarcharWithLength() {
        logger.info("Testing VARCHAR(50)");
        engine.execute("CREATE TABLE names (id INTEGER, name VARCHAR(50))");
        engine.execute("INSERT INTO names VALUES (1, 'Alice')");

        ResultSet rs = engine.executeQuery("SELECT * FROM names");
        assertEquals(1, rs.getRowCount());
        assertEquals("Alice", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void testCharWithLength() {
        logger.info("Testing CHAR(10)");
        engine.execute("CREATE TABLE codes (id INTEGER, code CHAR(10))");
        engine.execute("INSERT INTO codes VALUES (1, 'ABC123')");

        ResultSet rs = engine.executeQuery("SELECT * FROM codes");
        assertEquals(1, rs.getRowCount());
        assertEquals("ABC123", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void testMultipleColumnsWithTypes() {
        logger.info("Testing multiple columns with different types");
        engine.execute("""
            CREATE TABLE products (
                id INTEGER,
                name VARCHAR(100),
                price DECIMAL(10, 2),
                quantity NUMBER(8),
                discount DECIMAL(5, 2)
            )
            """);
        engine.execute("INSERT INTO products VALUES (1, 'Widget', 99.99, 100, 5.50)");

        ResultSet rs = engine.executeQuery("SELECT * FROM products");
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("Widget", rs.getRows().get(0).getValue(1));
        assertEquals(99.99, ((Number) rs.getRows().get(0).getValue(2)).doubleValue(), 0.001);
        assertEquals(100.0, ((Number) rs.getRows().get(0).getValue(3)).doubleValue(), 0.001);
        assertEquals(5.50, ((Number) rs.getRows().get(0).getValue(4)).doubleValue(), 0.001);
    }

    @Test
    public void testTableWithDefaultTypes() {
        logger.info("Testing types without parameters");
        engine.execute("CREATE TABLE mixed (id INTEGER, amount DECIMAL, name VARCHAR)");
        engine.execute("INSERT INTO mixed VALUES (1, 123.45, 'Test')");

        ResultSet rs = engine.executeQuery("SELECT * FROM mixed");
        assertEquals(1, rs.getRowCount());
        assertNotNull(rs.getRows().get(0).getValue(0));
        assertNotNull(rs.getRows().get(0).getValue(1));
        assertNotNull(rs.getRows().get(0).getValue(2));
    }
}
