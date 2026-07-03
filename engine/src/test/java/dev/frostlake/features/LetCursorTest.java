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

package dev.frostlake.features;

import dev.frostlake.DatabaseEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests for LET with CURSOR declaration
 * LET cursor_name CURSOR FOR SELECT...
 */
public class LetCursorTest {
    private static final Logger logger = LoggerFactory.getLogger(LetCursorTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for LET CURSOR tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testLetCursorBasic() {
        logger.info("Testing basic LET CURSOR");

        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine.execute("INSERT INTO users VALUES (2, 'Bob')");

        assertDoesNotThrow(() -> {
            engine.execute("""
                EXECUTE IMMEDIATE $$
                BEGIN
                    LET cur CURSOR FOR SELECT id, name FROM users;
                    RETURN 'success';
                END;
                $$;
                """);
        });

        logger.info("Basic LET CURSOR works correctly");
    }

    @Test
    public void testLetCursorWithWhereClause() {
        logger.info("Testing LET CURSOR with WHERE clause");

        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
        engine.execute("INSERT INTO products VALUES (1, 'Widget', 100)");
        engine.execute("INSERT INTO products VALUES (2, 'Gadget', 200)");

        assertDoesNotThrow(() -> {
            engine.execute("""
                EXECUTE IMMEDIATE $$
                BEGIN
                    LET cur CURSOR FOR SELECT id, name FROM products WHERE price > 50;
                    RETURN 'success';
                END;
                $$;
                """);
        });

        logger.info("LET CURSOR with WHERE clause works correctly");
    }

    @Test
    public void testLetCursorWithForLoop() {
        logger.info("Testing LET CURSOR with FOR loop");

        engine.execute("CREATE TABLE items (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO items VALUES (1, 'Item1')");
        engine.execute("INSERT INTO items VALUES (2, 'Item2')");

        assertDoesNotThrow(() -> {
            engine.execute("""
                EXECUTE IMMEDIATE $$
                DECLARE result ARRAY DEFAULT [];
                BEGIN
                    LET cur CURSOR FOR SELECT id, name FROM items;
                    FOR rec IN cur DO
                        result := ARRAY_APPEND(result, rec.name);
                    END FOR;
                    RETURN result;
                END;
                $$;
                """);
        });

        logger.info("LET CURSOR with FOR loop works correctly");
    }

    @Test
    public void testLetCursorMultipleCursors() {
        logger.info("Testing multiple LET CURSOR declarations");

        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine.execute("INSERT INTO products VALUES (1, 'Widget')");

        assertDoesNotThrow(() -> {
            engine.execute("""
                EXECUTE IMMEDIATE $$
                BEGIN
                    LET cur1 CURSOR FOR SELECT id, name FROM users;
                    LET cur2 CURSOR FOR SELECT id, name FROM products;
                    RETURN 'success';
                END;
                $$;
                """);
        });

        logger.info("Multiple LET CURSOR declarations work correctly");
    }

    @Test
    public void testLetCursorWithJoin() {
        logger.info("Testing LET CURSOR with JOIN");

        engine.execute("CREATE TABLE orders (id INTEGER, user_id INTEGER)");
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine.execute("INSERT INTO orders VALUES (100, 1)");

        assertDoesNotThrow(() -> {
            engine.execute("""
                EXECUTE IMMEDIATE $$
                BEGIN
                    LET cur CURSOR FOR
                        SELECT o.id, u.name
                        FROM orders o
                        JOIN users u ON o.user_id = u.id;
                    RETURN 'success';
                END;
                $$;
                """);
        });

        logger.info("LET CURSOR with JOIN works correctly");
    }

    @Test
    public void testLetCursorWithOrderBy() {
        logger.info("Testing LET CURSOR with ORDER BY");

        engine.execute("CREATE TABLE scores (id INTEGER, score INTEGER)");
        engine.execute("INSERT INTO scores VALUES (1, 100)");
        engine.execute("INSERT INTO scores VALUES (2, 200)");

        assertDoesNotThrow(() -> {
            engine.execute("""
                EXECUTE IMMEDIATE $$
                BEGIN
                    LET cur CURSOR FOR SELECT id, score FROM scores ORDER BY score DESC;
                    RETURN 'success';
                END;
                $$;
                """);
        });

        logger.info("LET CURSOR with ORDER BY works correctly");
    }

    @Test
    public void testLetCursorWithGroupBy() {
        logger.info("Testing LET CURSOR with GROUP BY");

        engine.execute("CREATE TABLE sales (product VARCHAR, amount INTEGER)");
        engine.execute("INSERT INTO sales VALUES ('Widget', 100)");
        engine.execute("INSERT INTO sales VALUES ('Widget', 200)");

        assertDoesNotThrow(() -> {
            engine.execute("""
                EXECUTE IMMEDIATE $$
                BEGIN
                    LET cur CURSOR FOR
                        SELECT product, SUM(amount) as total
                        FROM sales
                        GROUP BY product;
                    RETURN 'success';
                END;
                $$;
                """);
        });

        logger.info("LET CURSOR with GROUP BY works correctly");
    }

    @Test
    public void testLetCursorWithLimit() {
        logger.info("Testing LET CURSOR with LIMIT");

        engine.execute("CREATE TABLE data (id INTEGER)");
        engine.execute("INSERT INTO data VALUES (1)");
        engine.execute("INSERT INTO data VALUES (2)");
        engine.execute("INSERT INTO data VALUES (3)");

        assertDoesNotThrow(() -> {
            engine.execute("""
                EXECUTE IMMEDIATE $$
                BEGIN
                    LET cur CURSOR FOR SELECT id FROM data LIMIT 2;
                    RETURN 'success';
                END;
                $$;
                """);
        });

        logger.info("LET CURSOR with LIMIT works correctly");
    }

    @Test
    public void testLetCursorInInformationSchema() {
        logger.info("Testing LET CURSOR with information_schema query");

        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");

        assertDoesNotThrow(() -> {
            engine.execute("""
                EXECUTE IMMEDIATE $$
                BEGIN
                    LET cur CURSOR FOR
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = 'PUBLIC';
                    RETURN 'success';
                END;
                $$;
                """);
        });

        logger.info("LET CURSOR with information_schema query works correctly");
    }

    @Test
    public void testLetCursorWithTableConstraints() {
        logger.info("Testing LET CURSOR with table_constraints query");

        engine.execute("CREATE SCHEMA BASE");
        engine.execute("CREATE TABLE base.test_table (id INTEGER PRIMARY KEY)");

        assertDoesNotThrow(() -> {
            engine.execute("""
                EXECUTE IMMEDIATE $$
                BEGIN
                    LET cur CURSOR FOR
                        SELECT constraint_name, table_schema, table_name
                        FROM information_schema.table_constraints
                        WHERE table_schema = 'BASE';
                    RETURN 'success';
                END;
                $$;
                """);
        });

        logger.info("LET CURSOR with table_constraints query works correctly");
    }

    @Test
    public void testLetCursorMixedWithLetVariable() {
        logger.info("Testing LET CURSOR mixed with LET variable declarations");

        engine.execute("CREATE TABLE items (id INTEGER)");
        engine.execute("INSERT INTO items VALUES (1)");

        assertDoesNotThrow(() -> {
            engine.execute("""
                EXECUTE IMMEDIATE $$
                DECLARE result VARCHAR DEFAULT 'start';
                BEGIN
                    LET count INTEGER := 0;
                    LET cur CURSOR FOR SELECT id FROM items;
                    LET total INTEGER := 10;
                    RETURN result;
                END;
                $$;
                """);
        });

        logger.info("LET CURSOR mixed with LET variable declarations works correctly");
    }

    @Test
    public void testLetCursorWithSubquery() {
        logger.info("Testing LET CURSOR with subquery");

        engine.execute("CREATE TABLE numbers (value INTEGER)");
        engine.execute("INSERT INTO numbers VALUES (1)");
        engine.execute("INSERT INTO numbers VALUES (2)");

        assertDoesNotThrow(() -> {
            engine.execute("""
                EXECUTE IMMEDIATE $$
                BEGIN
                    LET cur CURSOR FOR
                        SELECT value FROM (
                            SELECT value * 2 as value FROM numbers
                        );
                    RETURN 'success';
                END;
                $$;
                """);
        });

        logger.info("LET CURSOR with subquery works correctly");
    }

    @Test
    public void testLetCursorWithComplexQuery() {
        logger.info("Testing LET CURSOR with complex query");

        engine.execute("CREATE TABLE orders (id INTEGER, amount INTEGER, status VARCHAR)");
        engine.execute("INSERT INTO orders VALUES (1, 100, 'active')");
        engine.execute("INSERT INTO orders VALUES (2, 200, 'active')");

        assertDoesNotThrow(() -> {
            engine.execute("""
                EXECUTE IMMEDIATE $$
                BEGIN
                    LET cur CURSOR FOR
                        SELECT id, amount
                        FROM orders
                        WHERE status = 'active'
                            AND amount > 50
                        ORDER BY amount DESC
                        LIMIT 10;
                    RETURN 'success';
                END;
                $$;
                """);
        });

        logger.info("LET CURSOR with complex query works correctly");
    }
}
