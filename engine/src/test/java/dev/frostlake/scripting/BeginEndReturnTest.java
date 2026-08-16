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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BeginEndReturnTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(BeginEndReturnTest.class);

    @Test
    public void testBeginEndReturnInteger() {
        logger.info("Testing BEGIN...END block with RETURN integer value");

        final ResultSet rs = engine.executeQuery("BEGIN RETURN 1; END;");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return one row");

        final Object value = rs.getRows().get(0).getValue(0);
        logger.info("Returned value: {}", value);

        // The value should be 1
        assertEquals(1L, ((Number) value).longValue(), "Should return 1");
    }

    @Test
    public void testBeginEndReturnString() {
        logger.info("Testing BEGIN...END block with RETURN string value");

        final ResultSet rs = engine.executeQuery("BEGIN RETURN 'hello'; END;");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return one row");

        final Object value = rs.getRows().get(0).getValue(0);
        logger.info("Returned value: {}", value);

        assertEquals("hello", value, "Should return 'hello'");
    }

    @Test
    public void testBeginEndReturnExpression() {
        logger.info("Testing BEGIN...END block with RETURN expression");

        final ResultSet rs = engine.executeQuery("BEGIN RETURN 10 + 5; END;");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return one row");

        final Object value = rs.getRows().get(0).getValue(0);
        logger.info("Returned value: {}", value);

        // The value should be 15
        assertEquals(15.0, ((Number) value).doubleValue(), 0.001, "Should return 15");
    }

    @Test
    public void testBeginEndReturnWithVariable() {
        logger.info("Testing BEGIN...END block with RETURN using variable");

        final ResultSet rs = engine.executeQuery("""
            DECLARE x INTEGER DEFAULT 42;
            BEGIN
                RETURN x;
            END;
            """);

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return one row");

        final Object value = rs.getRows().get(0).getValue(0);
        logger.info("Returned value: {}", value);

        assertEquals(42L, ((Number) value).longValue(), "Should return 42");
    }

    @Test
    public void testBeginEndReturnNull() {
        logger.info("Testing BEGIN...END block with RETURN (no expression) - should return NULL");

        final ResultSet rs = engine.executeQuery("BEGIN RETURN; END;");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return one row");

        final Object value = rs.getRows().get(0).getValue(0);
        logger.info("Returned value: {}", value);

        // The value should be null
        assertEquals(null, value, "Should return NULL");
    }

    @Test
    public void testBeginEndNoReturn() {
        logger.info("Testing BEGIN...END block without RETURN");

        // This should not return a result set
        // The declaration belongs in the DECLARE section that precedes BEGIN — Snowflake has no
        // DECLARE-as-a-statement inside a block body.
        engine.execute("DECLARE x INTEGER DEFAULT 1; BEGIN x := 2; END;");

        // Should complete without error
        logger.info("Block executed successfully without return value");
    }

    @Test
    public void testBeginEndReturnArrayWithCursor() {
        logger.info("Testing BEGIN...END block with RETURN array built from cursor iteration");

        final ResultSet rs = engine.executeQuery("""
            DECLARE c1 CURSOR FOR SELECT 1 as c UNION SELECT 2 as c;
            BEGIN
                LET a ARRAY := [];

                FOR rec IN c1 DO
                    a := array_append(a, rec.c);
                END FOR;

                RETURN a;
            END;
            """);

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return one row");

        final Object value = rs.getRows().get(0).getValue(0);
        logger.info("Returned value: {}", value);

        // The value should be an array with [1, 2]
        assertNotNull(value, "Returned array should not be null");
        logger.info("Array returned successfully with value: {}", value);
    }

    @Test
    public void testBeginEndReturnObjectWithCursor() {
        logger.info("Testing BEGIN...END block with RETURN object built from cursor iteration");

        final ResultSet rs = engine.executeQuery("""
            DECLARE c1 CURSOR FOR SELECT 1 as c;
            BEGIN
                LET o OBJECT := {};

                FOR rec IN c1 DO
                    o := object_insert(o, 'k', rec.c);
                END FOR;

                RETURN o;
            END;
            """);

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return one row");

        final Object value = rs.getRows().get(0).getValue(0);
        logger.info("Returned value: {}", value);

        // The value should be an object with key 'k' and value 1
        assertNotNull(value, "Returned object should not be null");
        logger.info("Object returned successfully with value: {}", value);
    }

    @Test
    public void testBeginEndReturnTableDirect() {
        logger.info("Testing BEGIN...END block with RETURN TABLE (direct query)");

        engine.execute("CREATE TABLE simple_test (id INTEGER)");
        engine.execute("INSERT INTO simple_test VALUES (1), (2)");

        final ResultSet rs = engine.executeQuery("SELECT * FROM simple_test");
        logger.info("Direct query returned {} rows", rs.getRowCount());
        assertEquals(2, rs.getRowCount(), "Direct query should return 2 rows");
    }

    @Test
    public void testBeginEndReturnTableFromResultSet() {
        logger.info("Testing BEGIN...END block with RETURN TABLE(resultset)");

        engine.execute("CREATE TABLE test_values (id INTEGER)");
        engine.execute("INSERT INTO test_values VALUES (1), (2)");

        // EXECUTE IMMEDIATE must be wrapped in parentheses when assigning to a variable
        final ResultSet rs = engine.executeQuery("""
            DECLARE
              res RESULTSET;
              stmt VARCHAR;
            BEGIN
              stmt := 'select * from test_values';
              res := (EXECUTE IMMEDIATE stmt);
              RETURN TABLE(res);
            END;
            """);

        assertNotNull(rs, "Result set should not be null");
        logger.info("Row count: {}", rs.getRowCount());
        logger.info("Column count: {}", rs.getColumns().size());
        if (rs.getRowCount() > 0) {
            logger.info("First row: {}", rs.getRows().get(0));
            logger.info("First row value: {}", rs.getRows().get(0).getValue(0));
        }
        assertEquals(2, rs.getRowCount(), "Should return two rows");

        final Object value1 = rs.getRows().get(0).getValue(0);
        final Object value2 = rs.getRows().get(1).getValue(0);
        logger.info("Returned values: {}, {}", value1, value2);

        assertEquals(1L, ((Number) value1).longValue(), "First row should be 1");
        assertEquals(2L, ((Number) value2).longValue(), "Second row should be 2");
    }

    @Test
    public void testBeginEndDeleteWithRollback() {
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE customers (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO customers VALUES (1, 'Alice'), (2, 'Bob')");

        engine.execute("""
            BEGIN
                BEGIN TRANSACTION;
                DELETE FROM customers WHERE id = 1;
                ROLLBACK;
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM customers");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue(),
            "Both rows should remain after rollback");
    }
}
