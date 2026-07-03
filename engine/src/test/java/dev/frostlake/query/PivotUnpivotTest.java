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

public class PivotUnpivotTest extends BaseDatabaseTest {

    @Test
    public void testBasicPivot() {
        // Create sales table
        engine.execute("CREATE TABLE sales (product VARCHAR, quarter VARCHAR, amount INTEGER)");
        engine.execute("INSERT INTO sales VALUES ('A', 'Q1', 100), ('A', 'Q2', 150), ('A', 'Q3', 200)");
        engine.execute("INSERT INTO sales VALUES ('B', 'Q1', 200), ('B', 'Q2', 250), ('B', 'Q3', 300)");

        // Pivot: transform quarters into columns
        ResultSet result = engine.executeQuery(
            "SELECT * FROM sales PIVOT (SUM(amount) FOR quarter IN ('Q1', 'Q2', 'Q3'))"
        );

        // Should have 2 rows (products A and B)
        assertEquals(2, result.getRowCount());

        // Should have 4 columns: product, Q1, Q2, Q3
        assertEquals(4, result.getColumns().size());
        assertEquals("product", result.getColumns().get(0).getName().toLowerCase());
        assertEquals("Q1", result.getColumns().get(1).getName());
        assertEquals("Q2", result.getColumns().get(2).getName());
        assertEquals("Q3", result.getColumns().get(3).getName());

        // Check first row (product A)
        assertEquals("A", result.getRows().get(0).getValue(0));
        assertEquals(100.0, result.getRows().get(0).getValue(1));
        assertEquals(150.0, result.getRows().get(0).getValue(2));
        assertEquals(200.0, result.getRows().get(0).getValue(3));

        // Check second row (product B)
        assertEquals("B", result.getRows().get(1).getValue(0));
        assertEquals(200.0, result.getRows().get(1).getValue(1));
        assertEquals(250.0, result.getRows().get(1).getValue(2));
        assertEquals(300.0, result.getRows().get(1).getValue(3));
    }

    @Test
    public void testPivotWithCount() {
        engine.execute("CREATE TABLE orders (customer VARCHAR, status VARCHAR, order_id INTEGER)");
        engine.execute("""
            INSERT INTO orders VALUES ('Alice', 'pending', 1), ('Alice', 'complete', 2), ('Alice', 'complete', 3)
            """);
        engine.execute("""
            INSERT INTO orders VALUES ('Bob', 'pending', 4), ('Bob', 'pending', 5), ('Bob', 'complete', 6)
            """);

        ResultSet result = engine.executeQuery(
            "SELECT * FROM orders PIVOT (COUNT(order_id) FOR status IN ('pending', 'complete'))"
        );

        assertEquals(2, result.getRowCount());
        assertEquals(3, result.getColumns().size());

        // Alice: 1 pending, 2 complete
        assertEquals("Alice", result.getRows().get(0).getValue(0));
        assertEquals(1L, result.getRows().get(0).getValue(1));
        assertEquals(2L, result.getRows().get(0).getValue(2));

        // Bob: 2 pending, 1 complete
        assertEquals("Bob", result.getRows().get(1).getValue(0));
        assertEquals(2L, result.getRows().get(1).getValue(1));
        assertEquals(1L, result.getRows().get(1).getValue(2));
    }

    @Test
    public void testPivotWithAliases() {
        engine.execute("CREATE TABLE revenue (region VARCHAR, month VARCHAR, sales INTEGER)");
        engine.execute("INSERT INTO revenue VALUES ('East', 'Jan', 1000), ('East', 'Feb', 1200)");
        engine.execute("INSERT INTO revenue VALUES ('West', 'Jan', 900), ('West', 'Feb', 1100)");

        ResultSet result = engine.executeQuery(
            "SELECT * FROM revenue PIVOT (SUM(sales) FOR month IN ('Jan' AS January, 'Feb' AS February))"
        );

        assertEquals(2, result.getRowCount());
        assertEquals(3, result.getColumns().size());

        // Check column names use aliases
        assertEquals("region", result.getColumns().get(0).getName().toLowerCase());
        assertEquals("January", result.getColumns().get(1).getName());
        assertEquals("February", result.getColumns().get(2).getName());
    }

    @Test
    public void testPivotWithAverage() {
        engine.execute("CREATE TABLE scores (student VARCHAR, subject VARCHAR, score INTEGER)");
        engine.execute("INSERT INTO scores VALUES ('Alice', 'Math', 90), ('Alice', 'Math', 95)");
        engine.execute("INSERT INTO scores VALUES ('Bob', 'Math', 80), ('Bob', 'Math', 85)");

        ResultSet result = engine.executeQuery(
            "SELECT * FROM scores PIVOT (AVG(score) FOR subject IN ('Math'))"
        );

        assertEquals(2, result.getRowCount());

        // Alice average: (90 + 95) / 2 = 92.5
        assertEquals("Alice", result.getRows().get(0).getValue(0));
        assertEquals(92.5, result.getRows().get(0).getValue(1));

        // Bob average: (80 + 85) / 2 = 82.5
        assertEquals("Bob", result.getRows().get(1).getValue(0));
        assertEquals(82.5, result.getRows().get(1).getValue(1));
    }

    @Test
    public void testBasicUnpivot() {
        // Create table with quarters as columns
        engine.execute("CREATE TABLE quarterly_sales (product VARCHAR, Q1 INTEGER, Q2 INTEGER, Q3 INTEGER)");
        engine.execute("INSERT INTO quarterly_sales VALUES ('A', 100, 150, 200)");
        engine.execute("INSERT INTO quarterly_sales VALUES ('B', 200, 250, 300)");

        // Unpivot: transform quarter columns into rows
        ResultSet result = engine.executeQuery(
            "SELECT * FROM quarterly_sales UNPIVOT (amount FOR quarter IN (Q1, Q2, Q3))"
        );

        // Should have 6 rows (2 products × 3 quarters)
        assertEquals(6, result.getRowCount());

        // Should have 3 columns: product, quarter, amount
        assertEquals(3, result.getColumns().size());
        assertEquals("product", result.getColumns().get(0).getName().toLowerCase());
        assertEquals("quarter", result.getColumns().get(1).getName());
        assertEquals("amount", result.getColumns().get(2).getName());

        // Check first 3 rows (product A)
        assertEquals("A", result.getRows().get(0).getValue(0));
        assertEquals("Q1", result.getRows().get(0).getValue(1));
        assertEquals(100L, ((Number) result.getRows().get(0).getValue(2)).longValue());

        assertEquals("A", result.getRows().get(1).getValue(0));
        assertEquals("Q2", result.getRows().get(1).getValue(1));
        assertEquals(150L, ((Number) result.getRows().get(1).getValue(2)).longValue());

        assertEquals("A", result.getRows().get(2).getValue(0));
        assertEquals("Q3", result.getRows().get(2).getValue(1));
        assertEquals(200L, ((Number) result.getRows().get(2).getValue(2)).longValue());

        // Check next 3 rows (product B)
        assertEquals("B", result.getRows().get(3).getValue(0));
        assertEquals("Q1", result.getRows().get(3).getValue(1));
        assertEquals(200L, ((Number) result.getRows().get(3).getValue(2)).longValue());

        assertEquals("B", result.getRows().get(4).getValue(0));
        assertEquals("Q2", result.getRows().get(4).getValue(1));
        assertEquals(250L, ((Number) result.getRows().get(4).getValue(2)).longValue());

        assertEquals("B", result.getRows().get(5).getValue(0));
        assertEquals("Q3", result.getRows().get(5).getValue(1));
        assertEquals(300L, ((Number) result.getRows().get(5).getValue(2)).longValue());
    }

    @Test
    public void testUnpivotWithMultiplePreservedColumns() {
        engine.execute("CREATE TABLE data (region VARCHAR, product VARCHAR, jan INTEGER, feb INTEGER)");
        engine.execute("INSERT INTO data VALUES ('East', 'Widget', 100, 120)");
        engine.execute("INSERT INTO data VALUES ('West', 'Gadget', 200, 220)");

        ResultSet result = engine.executeQuery(
            "SELECT * FROM data UNPIVOT (sales FOR month IN (jan, feb))"
        );

        // Should have 4 rows (2 regions × 2 months)
        assertEquals(4, result.getRowCount());

        // Should have 4 columns: region, product, month, sales
        assertEquals(4, result.getColumns().size());
        assertEquals("region", result.getColumns().get(0).getName().toLowerCase());
        assertEquals("product", result.getColumns().get(1).getName().toLowerCase());
        assertEquals("month", result.getColumns().get(2).getName());
        assertEquals("sales", result.getColumns().get(3).getName());

        // Check first 2 rows
        assertEquals("East", result.getRows().get(0).getValue(0));
        assertEquals("Widget", result.getRows().get(0).getValue(1));
        assertEquals("jan", result.getRows().get(0).getValue(2));
        assertEquals(100L, ((Number) result.getRows().get(0).getValue(3)).longValue());

        assertEquals("East", result.getRows().get(1).getValue(0));
        assertEquals("Widget", result.getRows().get(1).getValue(1));
        assertEquals("feb", result.getRows().get(1).getValue(2));
        assertEquals(120L, ((Number) result.getRows().get(1).getValue(3)).longValue());
    }

    @Test
    public void testPivotUnpivotRoundTrip() {
        // Original data
        engine.execute("CREATE TABLE original (product VARCHAR, quarter VARCHAR, amount INTEGER)");
        engine.execute("INSERT INTO original VALUES ('A', 'Q1', 100), ('A', 'Q2', 150)");
        engine.execute("INSERT INTO original VALUES ('B', 'Q1', 200), ('B', 'Q2', 250)");

        // Pivot then unpivot should give us back similar structure
        engine.execute("CREATE TABLE pivoted (product VARCHAR, Q1 INTEGER, Q2 INTEGER)");
        ResultSet pivotResult = engine.executeQuery(
            "SELECT * FROM original PIVOT (SUM(amount) FOR quarter IN ('Q1', 'Q2'))"
        );

        // Insert pivoted data
        for (int i = 0; i < pivotResult.getRowCount(); i++) {
            String product = pivotResult.getRows().get(i).getValue(0).toString();
            Object q1 = pivotResult.getRows().get(i).getValue(1);
            Object q2 = pivotResult.getRows().get(i).getValue(2);
            engine.execute(String.format("INSERT INTO pivoted VALUES ('%s', %s, %s)", product, q1, q2));
        }

        // Now unpivot
        ResultSet unpivotResult = engine.executeQuery(
            "SELECT * FROM pivoted UNPIVOT (amount FOR quarter IN (Q1, Q2))"
        );

        // Should have 4 rows again
        assertEquals(4, unpivotResult.getRowCount());
        assertEquals(3, unpivotResult.getColumns().size());
    }

    @Test
    public void testPivotWithMinMax() {
        engine.execute("CREATE TABLE temperatures (city VARCHAR, season VARCHAR, temp INTEGER)");
        engine.execute("INSERT INTO temperatures VALUES ('NYC', 'Summer', 85), ('NYC', 'Winter', 32)");
        engine.execute("INSERT INTO temperatures VALUES ('LA', 'Summer', 95), ('LA', 'Winter', 55)");

        // Test MAX
        ResultSet maxResult = engine.executeQuery(
            "SELECT * FROM temperatures PIVOT (MAX(temp) FOR season IN ('Summer', 'Winter'))"
        );

        assertEquals(2, maxResult.getRowCount());
        assertEquals("NYC", maxResult.getRows().get(0).getValue(0));
        assertEquals(85.0, maxResult.getRows().get(0).getValue(1));
        assertEquals(32.0, maxResult.getRows().get(0).getValue(2));

        // Test MIN
        engine.execute("INSERT INTO temperatures VALUES ('NYC', 'Summer', 80)");
        ResultSet minResult = engine.executeQuery(
            "SELECT * FROM temperatures PIVOT (MIN(temp) FOR season IN ('Summer'))"
        );

        assertEquals(2, minResult.getRowCount());
        // NYC should have min summer temp of 80 (from the two summer temps: 85 and 80)
        assertEquals("NYC", minResult.getRows().get(0).getValue(0));
        assertEquals(80.0, minResult.getRows().get(0).getValue(1));
    }
}
