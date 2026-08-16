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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ScalarSubqueryInSelectTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price NUMBER, category_id INTEGER)");
        engine.execute("INSERT INTO products VALUES (1, 'Laptop', 1000, 1)");
        engine.execute("INSERT INTO products VALUES (2, 'Mouse', 25, 1)");
        engine.execute("INSERT INTO products VALUES (3, 'Desk', 500, 2)");
        engine.execute("INSERT INTO products VALUES (4, 'Chair', 300, 2)");

        engine.execute("CREATE TABLE categories (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO categories VALUES (1, 'Electronics')");
        engine.execute("INSERT INTO categories VALUES (2, 'Furniture')");
    }

    @Test
    public void testSimpleScalarSubquery() {
        final ResultSet result = engine.executeQuery(
            "SELECT name, price, (SELECT AVG(price) FROM products) as avg_price FROM products WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("Laptop", result.getRows().get(0).getValue(0));
        assertEquals(1000.0, ((Number) result.getRows().get(0).getValue(1)).doubleValue(), 0.01);
        assertEquals(456.25, ((Number) result.getRows().get(0).getValue(2)).doubleValue(), 0.01);
    }

    @Test
    public void testCorrelatedScalarSubquery() {
        final ResultSet result = engine.executeQuery("""
            SELECT
                name,
                (SELECT AVG(price) FROM products WHERE products.category_id = categories.id) as avg_price
            FROM categories
            ORDER BY id
            """
        );
        assertEquals(2, result.getRowCount());
        assertEquals("Electronics", result.getRows().get(0).getValue(0));
        assertEquals(512.5, ((Number) result.getRows().get(0).getValue(1)).doubleValue(), 0.01);
        assertEquals("Furniture", result.getRows().get(1).getValue(0));
        assertEquals(400.0, ((Number) result.getRows().get(1).getValue(1)).doubleValue(), 0.01);
    }

    @Test
    public void correlatedRefWithSameNamedColumnInInnerTableBindsToOuter() {
        // The loader shape: outer alias IDENT and the inner table BOTH have grp_id/item_key.
        // IDENT.item_key must bind to the OUTER row — mis-binding it to the inner table's own row
        // made the correlation compare the inner row with itself, so every EXISTS/COUNT came up 0.
        engine.execute("CREATE TABLE idents (grp_id VARCHAR, item_key VARCHAR, label VARCHAR)");
        engine.execute("INSERT INTO idents VALUES ('c1', 'ik-1', 'first'), ('c1', 'ik-2', 'second')");
        engine.execute("CREATE TABLE profiles (grp_id VARCHAR, item_key VARCHAR, ident_ref VARCHAR, status VARCHAR)");
        engine.execute("INSERT INTO profiles VALUES ('c1', 'ak-9', 'ik-1', 'ACTIVE')");
        final ResultSet rs = engine.executeQuery("""
            SELECT ident.label,
                   (SELECT COUNT(*) FROM profiles acc
                     WHERE acc.grp_id = ident.grp_id
                       AND acc.ident_ref = ident.item_key
                       AND acc.status = 'ACTIVE') AS n,
                   CASE WHEN EXISTS (SELECT 1 FROM profiles acc
                                      WHERE acc.grp_id = ident.grp_id
                                        AND acc.ident_ref = ident.item_key
                                        AND acc.status = 'ACTIVE')
                        THEN 'ACTIVE' END AS st
            FROM idents ident ORDER BY ident.label""");
        assertEquals(2, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(1)).longValue(), "ik-1 has one profile");
        assertEquals("ACTIVE", rs.getRows().get(0).getValue(2));
        assertEquals(0L, ((Number) rs.getRows().get(1).getValue(1)).longValue(), "ik-2 has none");
        assertNull(rs.getRows().get(1).getValue(2));
    }

    @Test
    public void testMultipleCorrelatedScalarSubqueries() {
        final ResultSet result = engine.executeQuery("""
            SELECT
                name,
                (SELECT COUNT(*) FROM products WHERE products.category_id = categories.id) as product_count,
                (SELECT MAX(price) FROM products WHERE products.category_id = categories.id) as max_price,
                (SELECT MIN(price) FROM products WHERE products.category_id = categories.id) as min_price
            FROM categories
            ORDER BY id
            """
        );
        assertEquals(2, result.getRowCount());

        assertEquals("Electronics", result.getRows().get(0).getValue(0));
        assertEquals(2, ((Number) result.getRows().get(0).getValue(1)).intValue());
        assertEquals(1000.0, ((Number) result.getRows().get(0).getValue(2)).doubleValue(), 0.01);
        assertEquals(25.0, ((Number) result.getRows().get(0).getValue(3)).doubleValue(), 0.01);

        assertEquals("Furniture", result.getRows().get(1).getValue(0));
        assertEquals(2, ((Number) result.getRows().get(1).getValue(1)).intValue());
        assertEquals(500.0, ((Number) result.getRows().get(1).getValue(2)).doubleValue(), 0.01);
        assertEquals(300.0, ((Number) result.getRows().get(1).getValue(3)).doubleValue(), 0.01);
    }

    @Test
    public void testScalarSubqueryReturnsNull() {
        engine.execute("CREATE TABLE empty (id INTEGER)");

        final ResultSet result = engine.executeQuery(
            "SELECT name, (SELECT MAX(id) FROM empty) as max_empty FROM products WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("Laptop", result.getRows().get(0).getValue(0));
        assertNull(result.getRows().get(0).getValue(1));
    }

    @Test
    public void testScalarSubqueryInArithmetic() {
        final ResultSet result = engine.executeQuery(
            "SELECT name, price - (SELECT AVG(price) FROM products) as diff_from_avg FROM products WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("Laptop", result.getRows().get(0).getValue(0));
        assertEquals(543.75, ((Number) result.getRows().get(0).getValue(1)).doubleValue(), 0.01);
    }
}
