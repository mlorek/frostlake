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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for MERGE statement with subquery source and aliases
 */
public class MergeSubqueryTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(MergeSubqueryTest.class);




    @Test
    public void testMergeWithSubqueryFromValues() {
        logger.info("Testing MERGE with subquery from VALUES");

        engine.execute("CREATE TABLE dst (a INTEGER, b INTEGER, c INTEGER)");

        final String mergeQuery = """
            MERGE INTO dst d USING (
                SELECT $1 AS a, $2 AS b, $3 AS c
                FROM VALUES(1,2,3), (4,5,6)
            ) s ON s.a = d.a
            WHEN MATCHED THEN
            UPDATE SET
                b = s.b,
                c = s.c
            WHEN NOT MATCHED THEN
            INSERT(a,b,c) VALUES (s.a, s.b, s.c)
            """;

        engine.execute(mergeQuery);

        final ResultSet result = engine.executeQuery("SELECT * FROM dst ORDER BY a");
        assertEquals(2, result.getRowCount());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(2L, result.getRows().get(0).getValue(1));
        assertEquals(3L, result.getRows().get(0).getValue(2));
        assertEquals(4L, result.getRows().get(1).getValue(0));
        assertEquals(5L, result.getRows().get(1).getValue(1));
        assertEquals(6L, result.getRows().get(1).getValue(2));

        logger.info("MERGE with subquery from VALUES works correctly");
    }

    @Test
    public void testMergeWithSubqueryUpdateExisting() {
        logger.info("Testing MERGE with subquery updating existing rows");

        engine.execute("CREATE TABLE dst (a INTEGER, b INTEGER, c INTEGER)");
        engine.execute("INSERT INTO dst VALUES (1, 10, 20)");
        engine.execute("INSERT INTO dst VALUES (2, 30, 40)");

        final String mergeQuery = """
            MERGE INTO dst d USING (
                SELECT $1 AS a, $2 AS b, $3 AS c
                FROM VALUES(1,100,200), (3,50,60)
            ) s ON s.a = d.a
            WHEN MATCHED THEN
            UPDATE SET
                b = s.b,
                c = s.c
            WHEN NOT MATCHED THEN
            INSERT(a,b,c) VALUES (s.a, s.b, s.c)
            """;

        engine.execute(mergeQuery);

        final ResultSet result = engine.executeQuery("SELECT * FROM dst ORDER BY a");
        assertEquals(3, result.getRowCount());

        // Updated row
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(100L, result.getRows().get(0).getValue(1));
        assertEquals(200L, result.getRows().get(0).getValue(2));

        // Unchanged row
        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals(30L, result.getRows().get(1).getValue(1));
        assertEquals(40L, result.getRows().get(1).getValue(2));

        // Inserted row
        assertEquals(3L, result.getRows().get(2).getValue(0));
        assertEquals(50L, result.getRows().get(2).getValue(1));
        assertEquals(60L, result.getRows().get(2).getValue(2));

        logger.info("MERGE with subquery updating existing rows works correctly");
    }

    @Test
    public void testMergeWithSubqueryFromTable() {
        logger.info("Testing MERGE with subquery from table");

        engine.execute("CREATE TABLE source (id INTEGER, value INTEGER)");
        engine.execute("INSERT INTO source VALUES (1, 100), (2, 200), (3, 300)");

        engine.execute("CREATE TABLE dst (a INTEGER, b INTEGER, c INTEGER)");
        engine.execute("INSERT INTO dst VALUES (1, 10, 20)");

        final String mergeQuery = """
            MERGE INTO dst d USING (
                SELECT id AS a, value AS b, value * 2 AS c
                FROM source
            ) s ON s.a = d.a
            WHEN MATCHED THEN
            UPDATE SET
                b = s.b,
                c = s.c
            WHEN NOT MATCHED THEN
            INSERT(a,b,c) VALUES (s.a, s.b, s.c)
            """;

        engine.execute(mergeQuery);

        final ResultSet result = engine.executeQuery("SELECT * FROM dst ORDER BY a");
        assertEquals(3, result.getRowCount());

        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(100L, result.getRows().get(0).getValue(1));
        assertEquals(200L, result.getRows().get(0).getValue(2));

        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals(200L, result.getRows().get(1).getValue(1));
        assertEquals(400L, result.getRows().get(1).getValue(2));

        assertEquals(3L, result.getRows().get(2).getValue(0));
        assertEquals(300L, result.getRows().get(2).getValue(1));
        assertEquals(600L, result.getRows().get(2).getValue(2));

        logger.info("MERGE with subquery from table works correctly");
    }

    @Test
    public void testMergeWithSubqueryAndWhere() {
        logger.info("Testing MERGE with subquery and WHERE clause");

        engine.execute("CREATE TABLE source (id INTEGER, value INTEGER, status VARCHAR)");
        engine.execute("INSERT INTO source VALUES (1, 100, 'active'), (2, 200, 'inactive'), (3, 300, 'active')");

        engine.execute("CREATE TABLE dst (a INTEGER, b INTEGER, c VARCHAR)");

        final String mergeQuery = """
            MERGE INTO dst d USING (
                SELECT id AS a, value AS b, status AS c
                FROM source
                WHERE status = 'active'
            ) s ON s.a = d.a
            WHEN NOT MATCHED THEN
            INSERT(a,b,c) VALUES (s.a, s.b, s.c)
            """;

        engine.execute(mergeQuery);

        final ResultSet result = engine.executeQuery("SELECT * FROM dst ORDER BY a");
        assertEquals(2, result.getRowCount());

        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(100L, result.getRows().get(0).getValue(1));
        assertEquals("active", result.getRows().get(0).getValue(2));

        assertEquals(3L, result.getRows().get(1).getValue(0));
        assertEquals(300L, result.getRows().get(1).getValue(1));
        assertEquals("active", result.getRows().get(1).getValue(2));

        logger.info("MERGE with subquery and WHERE clause works correctly");
    }

    @Test
    public void testMergeWithNestedSubquery() {
        logger.info("Testing MERGE with nested subquery");

        engine.execute("CREATE TABLE source1 (id INTEGER, value INTEGER)");
        engine.execute("INSERT INTO source1 VALUES (1, 10), (2, 20), (3, 30), (4, 40)");

        engine.execute("CREATE TABLE dst (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO dst VALUES (2, 200)");

        final String mergeQuery = """
            MERGE INTO dst d USING (
                SELECT id AS a, value * 10 AS b
                FROM (
                    SELECT id, value
                    FROM source1
                    WHERE value > 15
                )
            ) s ON s.a = d.a
            WHEN MATCHED THEN
            UPDATE SET b = s.b
            WHEN NOT MATCHED THEN
            INSERT(a,b) VALUES (s.a, s.b)
            """;

        engine.execute(mergeQuery);

        final ResultSet result = engine.executeQuery("SELECT * FROM dst ORDER BY a");
        assertEquals(3, result.getRowCount());

        assertEquals(2L, result.getRows().get(0).getValue(0));
        assertEquals(200L, result.getRows().get(0).getValue(1));

        assertEquals(3L, result.getRows().get(1).getValue(0));
        assertEquals(300L, result.getRows().get(1).getValue(1));

        assertEquals(4L, result.getRows().get(2).getValue(0));
        assertEquals(400L, result.getRows().get(2).getValue(1));

        logger.info("MERGE with nested subquery works correctly");
    }

    @Test
    public void testMergeWithSubqueryJoin() {
        logger.info("Testing MERGE with subquery containing JOIN");

        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO products VALUES (1, 'Product A'), (2, 'Product B')");

        engine.execute("CREATE TABLE prices (product_id INTEGER, price INTEGER)");
        engine.execute("INSERT INTO prices VALUES (1, 100), (2, 200)");

        engine.execute("CREATE TABLE dst (id INTEGER, name VARCHAR, price INTEGER)");
        engine.execute("INSERT INTO dst VALUES (1, 'Old Name', 50)");

        final String mergeQuery = """
            MERGE INTO dst d USING (
                SELECT p.id, p.name, pr.price
                FROM products p
                JOIN prices pr ON p.id = pr.product_id
            ) s ON s.id = d.id
            WHEN MATCHED THEN
            UPDATE SET
                name = s.name,
                price = s.price
            WHEN NOT MATCHED THEN
            INSERT(id, name, price) VALUES (s.id, s.name, s.price)
            """;

        engine.execute(mergeQuery);

        final ResultSet result = engine.executeQuery("SELECT * FROM dst ORDER BY id");
        assertEquals(2, result.getRowCount());

        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals("Product A", result.getRows().get(0).getValue(1));
        assertEquals(100L, result.getRows().get(0).getValue(2));

        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals("Product B", result.getRows().get(1).getValue(1));
        assertEquals(200L, result.getRows().get(1).getValue(2));

        logger.info("MERGE with subquery containing JOIN works correctly");
    }

    @Test
    public void testMergeWithSubqueryAggregate() {
        logger.info("Testing MERGE with subquery containing aggregation");

        engine.execute("CREATE TABLE transactions (customer_id INTEGER, amount INTEGER)");
        engine.execute("INSERT INTO transactions VALUES (1, 100), (1, 200), (2, 300), (3, 150)");

        engine.execute("CREATE TABLE customer_totals (id INTEGER, total INTEGER)");
        engine.execute("INSERT INTO customer_totals VALUES (1, 0)");

        final String mergeQuery = """
            MERGE INTO customer_totals ct USING (
                SELECT customer_id AS id, SUM(amount) AS total
                FROM transactions
                GROUP BY customer_id
            ) s ON s.id = ct.id
            WHEN MATCHED THEN
            UPDATE SET total = s.total
            WHEN NOT MATCHED THEN
            INSERT(id, total) VALUES (s.id, s.total)
            """;

        engine.execute(mergeQuery);

        final ResultSet result = engine.executeQuery("SELECT * FROM customer_totals ORDER BY id");
        assertEquals(3, result.getRowCount());

        assertEquals(1L, result.getRows().get(0).getValue(0));
        // SUM returns Double
        final Object total1 = result.getRows().get(0).getValue(1);
        assertEquals(300.0, ((Number) total1).doubleValue(), 0.01);

        assertEquals(2L, result.getRows().get(1).getValue(0));
        final Object total2 = result.getRows().get(1).getValue(1);
        assertEquals(300.0, ((Number) total2).doubleValue(), 0.01);

        assertEquals(3L, result.getRows().get(2).getValue(0));
        final Object total3 = result.getRows().get(2).getValue(1);
        assertEquals(150.0, ((Number) total3).doubleValue(), 0.01);

        logger.info("MERGE with subquery containing aggregation works correctly");
    }

    @Test
    public void testMergeWithAliasInConditions() {
        logger.info("Testing MERGE with aliases used in ON condition");

        engine.execute("CREATE TABLE dst (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO dst VALUES (1, 10), (2, 20)");

        final String mergeQuery = """
            MERGE INTO dst d USING (
                SELECT $1 AS a, $2 AS b
                FROM VALUES(1, 100), (3, 300)
            ) s ON d.a = s.a
            WHEN MATCHED THEN
            UPDATE SET b = s.b
            WHEN NOT MATCHED THEN
            INSERT(a, b) VALUES (s.a, s.b)
            """;

        engine.execute(mergeQuery);

        final ResultSet result = engine.executeQuery("SELECT * FROM dst ORDER BY a");
        assertEquals(3, result.getRowCount());

        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(100L, result.getRows().get(0).getValue(1));

        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals(20L, result.getRows().get(1).getValue(1));

        assertEquals(3L, result.getRows().get(2).getValue(0));
        assertEquals(300L, result.getRows().get(2).getValue(1));

        logger.info("MERGE with aliases in ON condition works correctly");
    }

    @Test
    public void testMergeWithoutDestinationAlias() {
        logger.info("Testing MERGE without destination alias");

        engine.execute("CREATE TABLE dst (a INTEGER, b INTEGER)");

        final String mergeQuery = """
            MERGE INTO dst USING (
                SELECT $1 AS a, $2 AS b
                FROM VALUES(1, 10), (2, 20)
            ) s ON s.a = dst.a
            WHEN NOT MATCHED THEN
            INSERT(a, b) VALUES (s.a, s.b)
            """;

        engine.execute(mergeQuery);

        final ResultSet result = engine.executeQuery("SELECT * FROM dst ORDER BY a");
        assertEquals(2, result.getRowCount());

        logger.info("MERGE without destination alias works correctly");
    }

    @Test
    public void testMergeWithCTE() {
        logger.info("Testing MERGE with CTE in subquery");

        engine.execute("CREATE TABLE source (id INTEGER, value INTEGER)");
        engine.execute("INSERT INTO source VALUES (1, 100), (2, 200), (3, 300)");

        engine.execute("CREATE TABLE dst (a INTEGER, b INTEGER)");

        final String mergeQuery = """
            MERGE INTO dst d USING (
                WITH filtered AS (
                    SELECT id, value
                    FROM source
                    WHERE value >= 200
                )
                SELECT id AS a, value AS b
                FROM filtered
            ) s ON s.a = d.a
            WHEN NOT MATCHED THEN
            INSERT(a, b) VALUES (s.a, s.b)
            """;

        engine.execute(mergeQuery);

        final ResultSet result = engine.executeQuery("SELECT * FROM dst ORDER BY a");
        assertEquals(2, result.getRowCount());

        assertEquals(2L, result.getRows().get(0).getValue(0));
        assertEquals(200L, result.getRows().get(0).getValue(1));

        assertEquals(3L, result.getRows().get(1).getValue(0));
        assertEquals(300L, result.getRows().get(1).getValue(1));

        logger.info("MERGE with CTE in subquery works correctly");
    }
}
