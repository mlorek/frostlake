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
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PivotUnpivotTest extends BaseDatabaseTest {

    /** {@code empid → (Q1, Q2)} = {@code 1 → (10, 20)}, {@code 2 → (50, 5)}. */
    private void seedQuarterlySales() {
        engine.execute("CREATE TABLE qs (empid INTEGER, amount INTEGER, quarter VARCHAR)");
        engine.execute("INSERT INTO qs VALUES (1, 10, 'Q1'), (1, 20, 'Q2'), (2, 50, 'Q1'), (2, 5, 'Q2')");
    }

    private static final String PIVOTED =
        "qs PIVOT(SUM(amount) FOR quarter IN ('Q1','Q2')) AS p (eid, q1, q2)";

    // ── the pivot output is the query's source relation ──────────────────────
    // Every stage below used to be silently DISCARDED: the pivoted ResultSet was returned straight from the
    // pivot, so the SELECT list, GROUP BY, ORDER BY, DISTINCT and LIMIT never ran, and `SELECT <anything>`
    // handed back the whole pivoted relation.

    @Test
    public void dynamicPivotWithAnyUsesDistinctValues() {
        seedQuarterlySales();
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM qs PIVOT(SUM(amount) FOR quarter IN (ANY)) ORDER BY empid");
        assertEquals(2, rs.getRowCount());
        assertEquals(3, rs.getColumns().size(), "empid + one column per distinct quarter");
        assertEquals("'Q1'", rs.getColumns().get(1).getName());
        assertEquals("'Q2'", rs.getColumns().get(2).getName());
        assertEquals(10L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        engine.executeQuery("SELECT * FROM qs PIVOT(SUM(amount) FOR quarter IN (ANY ORDER BY quarter)) ORDER BY empid");
    }

    @Test
    public void subqueryDrivenPivotColumns() {
        seedQuarterlySales();
        engine.execute("CREATE TABLE wanted (q VARCHAR)");
        engine.execute("INSERT INTO wanted VALUES ('Q2')");
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM qs PIVOT(SUM(amount) FOR quarter IN (SELECT DISTINCT q FROM wanted)) ORDER BY empid");
        assertEquals(2, rs.getColumns().size(), "empid + only the subquery-selected quarter");
        assertEquals("'Q2'", rs.getColumns().get(1).getName());
        assertEquals(20L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void defaultOnNullFillsEmptyCells() {
        seedQuarterlySales();
        engine.execute("INSERT INTO qs VALUES (3, 7, 'Q1')");   // empid 3 has no Q2 rows
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM qs PIVOT(SUM(amount) FOR quarter IN ('Q1', 'Q2') DEFAULT ON NULL (0)) ORDER BY empid");
        assertEquals(3, rs.getRowCount());
        assertEquals(0L, ((Number) rs.getRows().get(2).getValue(2)).longValue(),
            "the empty Q2 cell must take the DEFAULT ON NULL value");
    }

    @Test
    public void theSelectListAppliesToThePivotOutput() {
        seedQuarterlySales();
        final ResultSet rs = engine.executeQuery("SELECT eid, q2 FROM " + PIVOTED + " ORDER BY eid");
        assertEquals(2, rs.getColumns().size());
        assertEquals("EID", rs.getColumns().get(0).getName());
        assertEquals("Q2", rs.getColumns().get(1).getName());
        assertEquals(20L, rs.getRows().get(0).getValue(1));
    }

    @Test
    public void anExpressionOverAPivotedColumnIsProjected() {
        seedQuarterlySales();
        final ResultSet rs = engine.executeQuery("SELECT q1 + q2 AS total FROM " + PIVOTED + " ORDER BY total");
        assertEquals(1, rs.getColumns().size());
        assertEquals(30L, rs.getRows().get(0).getValue(0));
        assertEquals(55L, rs.getRows().get(1).getValue(0));
    }

    @Test
    public void orderByAppliesToThePivotOutput() {
        seedQuarterlySales();
        final ResultSet rs = engine.executeQuery("SELECT eid, q1 FROM " + PIVOTED + " ORDER BY q1 DESC");
        assertEquals(2L, rs.getRows().get(0).getValue(0));
        assertEquals(1L, rs.getRows().get(1).getValue(0));
    }

    @Test
    public void anAggregateOverAPivotedColumnIsComputed() {
        seedQuarterlySales();
        assertEquals(60L, engine.executeQuery("SELECT SUM(q1) AS tot FROM " + PIVOTED)
            .getRows().get(0).getValue(0));
    }

    @Test
    public void limitAppliesToThePivotOutput() {
        seedQuarterlySales();
        assertEquals(1, engine.executeQuery("SELECT eid FROM " + PIVOTED + " ORDER BY eid LIMIT 1").getRowCount());
    }

    @Test
    public void aPivotedColumnResolvesThroughThePivotAlias() {
        seedQuarterlySales();
        assertEquals(2, engine.executeQuery("SELECT p.eid FROM " + PIVOTED + " ORDER BY 1").getRowCount());
    }

    @Test
    public void anInlineWhereFiltersThePivotOutput() {
        // Snowflake applies WHERE to the pivoted relation, so it may reference the pivoted columns. Frostlake
        // filtered the pivot's INPUT, which made this shape impossible — only the subquery form worked.
        seedQuarterlySales();
        final ResultSet rs = engine.executeQuery("SELECT eid, q1, q2 FROM " + PIVOTED + " WHERE q2 > q1");
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, rs.getRows().get(0).getValue(0));
        assertEquals(20L, rs.getRows().get(0).getValue(2));
    }

    // ── pivot output column naming ───────────────────────────────────────────

    @Test
    public void aPivotedColumnIsNamedAfterTheLiteralAsWritten() {
        // Snowflake keeps the quotes inside the identifier, so 'Q1' becomes a column called 'Q1' that is
        // referenced as "'Q1'" — which is how real queries write it, e.g. MAX("'Q1'").
        seedQuarterlySales();
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM qs PIVOT(SUM(amount) FOR quarter IN ('Q1','Q2'))");
        assertEquals("'Q1'", rs.getColumns().get(1).getName());
        assertEquals("'Q2'", rs.getColumns().get(2).getName());
    }

    @Test
    public void aPivotedColumnIsReferencedWithItsQuotesDoubleQuoted() {
        seedQuarterlySales();
        final ResultSet rs = engine.executeQuery("SELECT \"'Q2'\" FROM ("
            + "SELECT * FROM qs PIVOT(SUM(amount) FOR quarter IN ('Q1','Q2'))) ORDER BY 1");
        assertEquals(5L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(20L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }

    @Test
    public void anAggregateOverAQuotedPivotedColumnReadsTheColumn() {
        // The name-resolution helper treated any text wrapped in single quotes as a string literal, so
        // MAX("'Q1'") silently returned the STRING Q1 instead of reading the column. Every other path
        // (projection, WHERE, ORDER BY, scalar functions) already read it correctly.
        seedQuarterlySales();
        assertEquals(50L, ((Number) engine.executeQuery("SELECT MAX(\"'Q1'\") AS m FROM ("
            + "SELECT * FROM qs PIVOT(SUM(amount) FOR quarter IN ('Q1','Q2')))")
            .getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void anExplicitPivotValueAliasStillWins() {
        seedQuarterlySales();
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM qs PIVOT(SUM(amount) FOR quarter IN ('Q1','Q2')) AS p (eid, first_q, second_q)");
        assertEquals("EID", rs.getColumns().get(0).getName());
        assertEquals("FIRST_Q", rs.getColumns().get(1).getName());
        assertEquals("SECOND_Q", rs.getColumns().get(2).getName());
    }

    @Test
    public void theSelectListAppliesToAnUnpivotOutput() {
        final ResultSet rs = engine.executeQuery("SELECT quarter FROM (SELECT 1 AS empid, 10 AS q1, 20 AS q2) "
            + "UNPIVOT(amount FOR quarter IN (q1, q2)) ORDER BY quarter");
        assertEquals(1, rs.getColumns().size());
        assertEquals("Q1", rs.getRows().get(0).getValue(0).toString().toUpperCase());
        assertEquals("Q2", rs.getRows().get(1).getValue(0).toString().toUpperCase());
    }

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

        // Should have 4 columns: product plus one per pivot value. Snowflake names a pivoted column after
        // the literal AS WRITTEN, so a string pivot value keeps its single quotes INSIDE the identifier and
        // is referenced as "'Q1'". This assertion previously expected the stripped name, which is not what
        // Snowflake produces — and the stripped form made "'Q1'" unresolvable.
        assertEquals(4, result.getColumns().size());
        assertEquals("product", result.getColumns().get(0).getName().toLowerCase());
        assertEquals("'Q1'", result.getColumns().get(1).getName());
        assertEquals("'Q2'", result.getColumns().get(2).getName());
        assertEquals("'Q3'", result.getColumns().get(3).getName());

        // Check first row (product A). amount is INTEGER, so SUM is a whole number (100, not 100.0).
        assertEquals("A", result.getRows().get(0).getValue(0));
        assertEquals(100L, result.getRows().get(0).getValue(1));
        assertEquals(150L, result.getRows().get(0).getValue(2));
        assertEquals(200L, result.getRows().get(0).getValue(3));

        // Check second row (product B)
        assertEquals("B", result.getRows().get(1).getValue(0));
        assertEquals(200L, result.getRows().get(1).getValue(1));
        assertEquals(250L, result.getRows().get(1).getValue(2));
        assertEquals(300L, result.getRows().get(1).getValue(3));
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
        assertEquals("JANUARY", result.getColumns().get(1).getName());
        assertEquals("FEBRUARY", result.getColumns().get(2).getName());
    }

    @Test
    public void testPivotWithAverage() {
        engine.execute("CREATE TABLE scores (student VARCHAR, subject VARCHAR, score INTEGER)");
        engine.execute("INSERT INTO scores VALUES ('Alice', 'Math', 90), ('Alice', 'Math', 95)");
        engine.execute("INSERT INTO scores VALUES ('Bob', 'Math', 80), ('Bob', 'Math', 85)");

        // PIVOT does not order its groups; the ORDER BY makes the row order the assertions rely on
        // deterministic on any engine (Snowflake's grouping order is unspecified).
        ResultSet result = engine.executeQuery(
            "SELECT * FROM scores PIVOT (AVG(score) FOR subject IN ('Math')) ORDER BY student"
        );

        assertEquals(2, result.getRowCount());

        // AVG over an integer column is a BigDecimal with Snowflake's scale 6.
        // Alice average: (90 + 95) / 2 = 92.500000
        assertEquals("Alice", result.getRows().get(0).getValue(0));
        assertEquals(new BigDecimal("92.500000"), result.getRows().get(0).getValue(1));

        // Bob average: (80 + 85) / 2 = 82.500000
        assertEquals("Bob", result.getRows().get(1).getValue(0));
        assertEquals(new BigDecimal("82.500000"), result.getRows().get(1).getValue(1));
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
        assertEquals("QUARTER", result.getColumns().get(1).getName());
        assertEquals("AMOUNT", result.getColumns().get(2).getName());

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
        assertEquals("MONTH", result.getColumns().get(2).getName());
        assertEquals("SALES", result.getColumns().get(3).getName());

        // Check first 2 rows
        assertEquals("East", result.getRows().get(0).getValue(0));
        assertEquals("Widget", result.getRows().get(0).getValue(1));
        assertEquals("JAN", result.getRows().get(0).getValue(2));
        assertEquals(100L, ((Number) result.getRows().get(0).getValue(3)).longValue());

        assertEquals("East", result.getRows().get(1).getValue(0));
        assertEquals("Widget", result.getRows().get(1).getValue(1));
        assertEquals("FEB", result.getRows().get(1).getValue(2));
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
            + " ORDER BY city DESC"
        );

        assertEquals(2, maxResult.getRowCount());
        // MIN/MAX keep the input column's type — INTEGER in, Long out.
        assertEquals("NYC", maxResult.getRows().get(0).getValue(0));
        assertEquals(85L, maxResult.getRows().get(0).getValue(1));
        assertEquals(32L, maxResult.getRows().get(0).getValue(2));

        // Test MIN
        engine.execute("INSERT INTO temperatures VALUES ('NYC', 'Summer', 80)");
        ResultSet minResult = engine.executeQuery(
            "SELECT * FROM temperatures PIVOT (MIN(temp) FOR season IN ('Summer')) ORDER BY city DESC"
        );

        assertEquals(2, minResult.getRowCount());
        // NYC should have min summer temp of 80 (from the two summer temps: 85 and 80)
        assertEquals("NYC", minResult.getRows().get(0).getValue(0));
        assertEquals(80L, minResult.getRows().get(0).getValue(1));
    }

    @Test
    public void pivotAfterSourceAliasWithPivotAlias() {
        // FROM (subquery) src PIVOT(...) p — the source carries an alias and the PIVOT carries its own
        // trailing alias. Both aliases sit between/after the source and the PIVOT, not glued to the source.
        engine.execute("CREATE TABLE sales (region VARCHAR, q VARCHAR, amt INTEGER)");
        engine.execute("INSERT INTO sales VALUES ('E','Q1',10), ('E','Q2',20), ('W','Q1',30), ('W','Q2',40)");

        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM (SELECT region, q, amt FROM sales) m PIVOT(SUM(amt) FOR q IN ('Q1','Q2')) p");

        assertEquals(2, rs.getRowCount());
        assertEquals(3, rs.getColumns().size());   // region, Q1, Q2
        assertEquals("E", rs.getRows().get(0).getValue(0));
        assertEquals(10L, rs.getRows().get(0).getValue(1));
        assertEquals(20L, rs.getRows().get(0).getValue(2));
        assertEquals("W", rs.getRows().get(1).getValue(0));
        assertEquals(30L, rs.getRows().get(1).getValue(1));
        assertEquals(40L, rs.getRows().get(1).getValue(2));
    }

    @Test
    public void numericPivotValueColumnsAreReadableByQuotedReference() {
        // PIVOT ... FOR sev IN (1, 2, 3, 4) names its output columns "1".."4"; reading them back
        // (ZEROIFNULL("1") in the vendor recommended-actions loader) must resolve the COLUMN — the
        // numeric-literal fallback in column resolution silently returned 1/2/3/4 for every row.
        engine.execute("CREATE TABLE wk (aid VARCHAR, sev INTEGER, wc INTEGER)");
        engine.execute("INSERT INTO wk VALUES ('a', 1, 2), ('b', 1, 7), ('b', 2, 4), ('b', 3, 1)");

        final ResultSet rs = engine.executeQuery(
            "SELECT aid, ZEROIFNULL(\"1\") AS low, ZEROIFNULL(\"2\") AS med, ZEROIFNULL(\"3\") AS high"
            + " FROM (SELECT aid, sev, SUM(wc) w FROM wk GROUP BY aid, sev)"
            + " PIVOT(SUM(w) FOR sev IN (1, 2, 3, 4)) ORDER BY aid");

        assertEquals(2, rs.getRowCount());
        assertEquals(2L, rs.getRows().get(0).getValue(1));
        assertEquals(0L, rs.getRows().get(0).getValue(2));
        assertEquals(0L, rs.getRows().get(0).getValue(3));
        assertEquals(7L, rs.getRows().get(1).getValue(1));
        assertEquals(4L, rs.getRows().get(1).getValue(2));
        assertEquals(1L, rs.getRows().get(1).getValue(3));
    }

    @Test
    public void quotedNumericNamedColumnResolvesAsColumn() {
        // The general shape: a derived column whose (quoted) name is numeric text must be read as
        // the column everywhere — projection, expressions, WHERE — never as a number literal.
        final ResultSet rs = engine.executeQuery("SELECT \"1\" + 0 FROM (SELECT 42 AS \"1\")");
        assertEquals(42L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        final ResultSet where = engine.executeQuery(
            "SELECT COUNT(*) FROM (SELECT 42 AS \"1\") WHERE \"1\" = 42");
        assertEquals(1L, where.getRows().get(0).getValue(0));
    }

    @Test
    public void pivotAfterBaseTableAlias() {
        // FROM table alias PIVOT(...) — a base table with an alias, then PIVOT.
        engine.execute("CREATE TABLE t2 (region VARCHAR, q VARCHAR, amt INTEGER)");
        engine.execute("INSERT INTO t2 VALUES ('E','Q1',5), ('E','Q2',7)");

        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM t2 s PIVOT(SUM(amt) FOR q IN ('Q1','Q2'))");

        assertEquals(1, rs.getRowCount());
        assertEquals(5L, rs.getRows().get(0).getValue(1));
        assertEquals(7L, rs.getRows().get(0).getValue(2));
    }
}
