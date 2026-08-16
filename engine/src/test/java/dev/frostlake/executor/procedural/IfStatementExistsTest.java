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

package dev.frostlake.executor.procedural;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for IF statement with EXISTS operator in procedural code
 */
public class IfStatementExistsTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(IfStatementExistsTest.class);




    @Test
    public void testIfExistsWithResultReturnsOne() {
        logger.info("Testing IF EXISTS with result that returns rows");

        final String script = """
            BEGIN
                IF (EXISTS(SELECT 1 AS c)) THEN
                    RETURN 1;
                END IF;
                RETURN 0;
            END;
            """;

        final ResultSet result = engine.executeQuery(script);
        assertEquals(1, result.getRowCount());
        assertEquals(1L, result.getRows().get(0).getValue(0));

        logger.info("IF EXISTS with result returns 1 correctly");
    }

    @Test
    public void testIfExistsWithEmptyResultReturnsZero() {
        logger.info("Testing IF EXISTS with empty result");

        engine.execute("CREATE TABLE test_table (id INTEGER)");

        final String script = """
            BEGIN
                IF (EXISTS(SELECT * FROM test_table)) THEN
                    RETURN 1;
                END IF;
                RETURN 0;
            END;
            """;

        final ResultSet result = engine.executeQuery(script);
        assertEquals(1, result.getRowCount());
        assertEquals(0L, result.getRows().get(0).getValue(0));

        logger.info("IF EXISTS with empty result returns 0 correctly");
    }

    @Test
    public void testIfExistsWithNonEmptyTable() {
        logger.info("Testing IF EXISTS with non-empty table");

        engine.execute("CREATE TABLE test_table (id INTEGER)");
        engine.execute("INSERT INTO test_table VALUES (1), (2), (3)");

        final String script = """
            BEGIN
                IF (EXISTS(SELECT * FROM test_table)) THEN
                    RETURN 999;
                END IF;
                RETURN 0;
            END;
            """;

        final ResultSet result = engine.executeQuery(script);
        assertEquals(1, result.getRowCount());
        assertEquals(999L, result.getRows().get(0).getValue(0));

        logger.info("IF EXISTS with non-empty table returns 999 correctly");
    }

    @Test
    public void testIfExistsWithWhereClause() {
        logger.info("Testing IF EXISTS with WHERE clause");

        engine.execute("CREATE TABLE test_table (id INTEGER, status VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'active'), (2, 'inactive')");

        final String script = """
            BEGIN
                IF (EXISTS(SELECT * FROM test_table WHERE status = 'active')) THEN
                    RETURN 1;
                END IF;
                RETURN 0;
            END;
            """;

        final ResultSet result = engine.executeQuery(script);
        assertEquals(1, result.getRowCount());
        assertEquals(1L, result.getRows().get(0).getValue(0));

        logger.info("IF EXISTS with WHERE clause works correctly");
    }

    @Test
    public void testIfExistsWithWhereNoMatch() {
        logger.info("Testing IF EXISTS with WHERE clause that matches no rows");

        engine.execute("CREATE TABLE test_table (id INTEGER, status VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'inactive')");

        final String script = """
            BEGIN
                IF (EXISTS(SELECT * FROM test_table WHERE status = 'active')) THEN
                    RETURN 1;
                END IF;
                RETURN 0;
            END;
            """;

        final ResultSet result = engine.executeQuery(script);
        assertEquals(1, result.getRowCount());
        assertEquals(0L, result.getRows().get(0).getValue(0));

        logger.info("IF EXISTS with WHERE no match returns 0 correctly");
    }

    @Test
    public void testIfNotExists() {
        logger.info("Testing IF NOT EXISTS");

        engine.execute("CREATE TABLE test_table (id INTEGER)");

        final String script = """
            BEGIN
                IF (NOT EXISTS(SELECT * FROM test_table)) THEN
                    RETURN 100;
                END IF;
                RETURN 200;
            END;
            """;

        final ResultSet result = engine.executeQuery(script);
        assertEquals(1, result.getRowCount());
        assertEquals(100L, result.getRows().get(0).getValue(0));

        logger.info("IF NOT EXISTS works correctly");
    }

    @Test
    public void testIfNotExistsWithData() {
        logger.info("Testing IF NOT EXISTS with data");

        engine.execute("CREATE TABLE test_table (id INTEGER)");
        engine.execute("INSERT INTO test_table VALUES (1)");

        final String script = """
            BEGIN
                IF (NOT EXISTS(SELECT * FROM test_table)) THEN
                    RETURN 100;
                END IF;
                RETURN 200;
            END;
            """;

        final ResultSet result = engine.executeQuery(script);
        assertEquals(1, result.getRowCount());
        assertEquals(200L, result.getRows().get(0).getValue(0));

        logger.info("IF NOT EXISTS with data returns 200 correctly");
    }

    @Test
    public void testIfExistsInElseBranch() {
        logger.info("Testing IF with ELSE and EXISTS");

        engine.execute("CREATE TABLE test_table (id INTEGER)");

        final String script = """
            BEGIN
                IF (EXISTS(SELECT * FROM test_table)) THEN
                    RETURN 1;
                ELSE
                    RETURN 999;
                END IF;
            END;
            """;

        final ResultSet result = engine.executeQuery(script);
        assertEquals(1, result.getRowCount());
        assertEquals(999L, result.getRows().get(0).getValue(0));

        logger.info("IF with ELSE and EXISTS works correctly");
    }

    @Test
    public void testIfExistsWithMultipleConditions() {
        logger.info("Testing IF EXISTS with multiple conditions");

        engine.execute("CREATE TABLE test_table (id INTEGER)");
        engine.execute("INSERT INTO test_table VALUES (1)");

        final String script = """
            BEGIN
                IF (EXISTS(SELECT * FROM test_table) AND 1 = 1) THEN
                    RETURN 42;
                END IF;
                RETURN 0;
            END;
            """;

        final ResultSet result = engine.executeQuery(script);
        assertEquals(1, result.getRowCount());
        assertEquals(42L, result.getRows().get(0).getValue(0));

        logger.info("IF EXISTS with multiple conditions works correctly");
    }

    @Test
    public void testIfExistsInNestedIf() {
        logger.info("Testing EXISTS in nested IF statements");

        engine.execute("CREATE TABLE test_table (id INTEGER)");
        engine.execute("INSERT INTO test_table VALUES (1)");

        final String script = """
            BEGIN
                IF (EXISTS(SELECT * FROM test_table)) THEN
                    IF (EXISTS(SELECT 1)) THEN
                        RETURN 777;
                    END IF;
                    RETURN 555;
                END IF;
                RETURN 0;
            END;
            """;

        final ResultSet result = engine.executeQuery(script);
        assertEquals(1, result.getRowCount());
        assertEquals(777L, result.getRows().get(0).getValue(0));

        logger.info("EXISTS in nested IF works correctly");
    }

    @Test
    public void testIfExistsWithSubquery() {
        logger.info("Testing IF EXISTS with complex subquery");

        engine.execute("CREATE TABLE orders (id INTEGER, status VARCHAR, amount INTEGER)");
        engine.execute("INSERT INTO orders VALUES (1, 'completed', 100)");
        engine.execute("INSERT INTO orders VALUES (2, 'pending', 200)");
        engine.execute("INSERT INTO orders VALUES (3, 'completed', 300)");

        final String script = """
            BEGIN
                IF (EXISTS(SELECT * FROM orders WHERE status = 'completed' AND amount > 150)) THEN
                    RETURN 1;
                END IF;
                RETURN 0;
            END;
            """;

        final ResultSet result = engine.executeQuery(script);
        assertEquals(1, result.getRowCount());
        assertEquals(1L, result.getRows().get(0).getValue(0));

        logger.info("IF EXISTS with complex subquery works correctly");
    }

    @Test
    public void testIfExistsWithValuesClause() {
        logger.info("Testing IF EXISTS with VALUES clause");

        final String script = """
            BEGIN
                IF (EXISTS(SELECT * FROM VALUES(1, 2, 3))) THEN
                    RETURN 123;
                END IF;
                RETURN 0;
            END;
            """;

        final ResultSet result = engine.executeQuery(script);
        assertEquals(1, result.getRowCount());
        assertEquals(123L, result.getRows().get(0).getValue(0));

        logger.info("IF EXISTS with VALUES clause works correctly");
    }
}
