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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three super-group constructs, run as Snowflake's own documentation writes them — the fixtures and
 * queries come from the CUBE, ROLLUP, GROUPING SETS, GROUPING and GROUPING_ID pages, and the expected
 * rows are the ones those pages print. Every case here was also run against a live account and agrees
 * cell for cell.
 *
 * <p>Two facts are worth stating because they are easy to get wrong:
 *
 * <ul>
 *   <li>A super-aggregate row puts NULL in the columns it is not grouped by, and that NULL is
 *       INDISTINGUISHABLE from a real NULL in the data — the nurses fixture has both at once, and
 *       {@code GROUPING()} is the only thing that tells them apart.</li>
 *   <li>Ordering: where a documented query's ORDER BY leaves rows tied, the tie order is not defined
 *       by either engine, so those cases are compared as SETS. The rest assert exact order.</li>
 * </ul>
 */
public class SuperGroupDocExamplesTest extends BaseDatabaseTest {

    private static final String PROFIT = "SELECT state, city,"
        + " SUM((s.retail_price - p.wholesale_price) * s.quantity) AS profit"
        + " FROM products AS p, sales AS s WHERE s.product_ID = p.product_ID GROUP BY ";
    private static final String PROFIT_STATE = "SELECT state,"
        + " SUM((s.retail_price - p.wholesale_price) * s.quantity) AS profit"
        + " FROM products AS p, sales AS s WHERE s.product_ID = p.product_ID GROUP BY ";
    private static final String NURSE_COUNT =
        "SELECT COUNT(*), medical_license, radio_license FROM nurses GROUP BY ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE products (product_ID INTEGER, wholesale_price REAL)");
        engine.execute("INSERT INTO products (product_ID, wholesale_price) VALUES (1, 1.00), (2, 2.00)");
        engine.execute("CREATE OR REPLACE TABLE sales (product_ID INTEGER, retail_price REAL,"
            + " quantity INTEGER, city VARCHAR, state VARCHAR)");
        engine.execute("INSERT INTO sales (product_id, retail_price, quantity, city, state) VALUES"
            + " (1, 2.00, 1, 'SF', 'CA'), (1, 2.00, 2, 'SJ', 'CA'), (2, 5.00, 4, 'SF', 'CA'),"
            + " (2, 5.00, 8, 'SJ', 'CA'), (2, 5.00, 16, 'Miami', 'FL'), (2, 5.00, 32, 'Orlando', 'FL'),"
            + " (2, 5.00, 64, 'SJ', 'PR')");
    }

    private void createNurses() {
        engine.execute("CREATE OR REPLACE TABLE nurses (ID INTEGER, full_name VARCHAR,"
            + " medical_license VARCHAR, radio_license VARCHAR)");
        engine.execute("INSERT INTO nurses (ID, full_name, medical_license, radio_license) VALUES"
            + " (201, 'Thomas Leonard Vicente', 'LVN', 'Technician'),"
            + " (202, 'Tamara Lolita VanZant', 'LVN', 'Technician'),"
            + " (341, 'Georgeann Linda Vente', 'LVN', 'General'),"
            + " (471, 'Andrea Renee Nouveau', 'RN', 'Amateur Extra')");
    }

    private void createAggr2() {
        engine.execute("CREATE OR REPLACE TABLE aggr2 (col_x int, col_y int, col_z int)");
        engine.execute("INSERT INTO aggr2 VALUES (1, 2, 1), (1, 2, 3)");
        engine.execute("INSERT INTO aggr2 VALUES (2, 1, 10), (2, 2, 11), (2, 2, 3)");
    }

    private String cell(final Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number) {
            final double number = ((Number) value).doubleValue();
            if (number == Math.rint(number) && !Double.isInfinite(number)) {
                return String.valueOf((long) number);
            }
        }
        return String.valueOf(value);
    }

    private List<String> rowList(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        while (rs.next()) {
            final StringBuilder row = new StringBuilder();
            for (int i = 0; i < rs.getColumns().size(); i++) {
                row.append(i > 0 ? "|" : "").append(cell(rs.getValue(i)));
            }
            out.add(row.toString());
        }
        return out;
    }

    /** Rows in the order the query returned them. */
    private String rows(final String sql) {
        return String.join(" ;; ", rowList(sql));
    }

    /** Rows as a SET — for a documented query whose ORDER BY leaves rows tied. */
    private String rowSet(final String sql) {
        final List<String> out = rowList(sql);
        Collections.sort(out);
        return String.join(" ;; ", out);
    }

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    /** ROLLUP totals each prefix of its key list, then the grand total — the doc page's own table. */
    @Test
    public void rollupMatchesTheDocumentedTable() {
        assertEquals("CA|SF|13 ;; CA|SJ|26 ;; CA|NULL|39 ;; FL|Miami|48 ;; FL|Orlando|96 ;;"
            + " FL|NULL|144 ;; PR|SJ|192 ;; PR|NULL|192 ;; NULL|NULL|375",
            rows(PROFIT + "ROLLUP (state, city) ORDER BY state, city NULLS LAST"));
    }

    /** CUBE adds every other combination, including city totals across states. */
    @Test
    public void cubeMatchesTheDocumentedTable() {
        assertEquals("CA|SF|13 ;; CA|SJ|26 ;; CA|NULL|39 ;; FL|Miami|48 ;; FL|Orlando|96 ;;"
            + " FL|NULL|144 ;; PR|SJ|192 ;; PR|NULL|192 ;; NULL|Miami|48 ;; NULL|Orlando|96 ;;"
            + " NULL|SF|13 ;; NULL|SJ|218 ;; NULL|NULL|375",
            rows(PROFIT + "CUBE (state, city) ORDER BY state, city NULLS LAST"));
    }

    /** A key may be written as a column, a SELECT alias, an ordinal, or an expression. */
    @Test
    public void aKeyMayBeAColumnAliasOrdinalOrExpression() {
        final String expected = "CA|39 ;; FL|144 ;; PR|192 ;; NULL|375";
        assertEquals(expected, rows(PROFIT_STATE + "ROLLUP (state) ORDER BY state NULLS LAST"));
        assertEquals(expected, rows(PROFIT_STATE + "ROLLUP (1) ORDER BY 1 NULLS LAST"));
        assertEquals(expected, rows("SELECT state AS st,"
            + " SUM((s.retail_price - p.wholesale_price) * s.quantity) AS profit"
            + " FROM products AS p, sales AS s WHERE s.product_ID = p.product_ID"
            + " GROUP BY ROLLUP (st) ORDER BY st NULLS LAST"));
        assertEquals("C|39 ;; F|144 ;; P|192 ;; NULL|375", rows("SELECT SUBSTR(state, 1, 1) AS s1,"
            + " SUM((s.retail_price - p.wholesale_price) * s.quantity) AS profit"
            + " FROM products AS p, sales AS s WHERE s.product_ID = p.product_ID"
            + " GROUP BY ROLLUP (SUBSTR(state, 1, 1)) ORDER BY s1 NULLS LAST"));
    }

    /** A key REPEATED in a CUBE repeats its rows — the duplicate sets are not folded together. */
    @Test
    public void aRepeatedCubeKeyRepeatsTheRows() {
        assertEquals("CA|39 ;; CA|39 ;; CA|39 ;; FL|144 ;; FL|144 ;; FL|144 ;;"
            + " PR|192 ;; PR|192 ;; PR|192 ;; NULL|375",
            rows(PROFIT_STATE + "CUBE (state, state) ORDER BY state NULLS LAST"));
    }

    /** Two bare grouping sets group by each key separately; one parenthesised set groups by the pair. */
    @Test
    public void groupingSetsGroupSeparatelyOrTogether() {
        createNurses();
        assertEquals("1|NULL|Amateur Extra ;; 1|NULL|General ;; 1|RN|NULL ;; 2|NULL|Technician ;;"
            + " 3|LVN|NULL",
            rowSet(NURSE_COUNT + "GROUPING SETS (medical_license, radio_license)"
                + " ORDER BY 3 DESC NULLS FIRST"));
        assertEquals("2|LVN|Technician ;; 1|LVN|General ;; 1|RN|Amateur Extra",
            rows(NURSE_COUNT + "GROUPING SETS ((medical_license, radio_license))"
                + " ORDER BY 3 DESC NULLS FIRST"));
    }

    /**
     * The NULL ambiguity the GROUPING function exists for: once the data itself carries NULL radio
     * licences, the row {@code 3 | NULL | NULL} is a REAL group of three nurses, sitting beside
     * super-aggregate rows whose NULLs mean "not grouped by this".
     */
    @Test
    public void aRealNullSitsBesideASuperGroupNull() {
        createNurses();
        engine.execute("INSERT INTO nurses (ID, full_name, medical_license, radio_license) VALUES"
            + " (101, 'Lily Vine', 'LVN', NULL), (102, 'Larry Vancouver', 'LVN', NULL),"
            + " (172, 'Rhonda Nova', 'RN', NULL)");
        assertEquals("1|NULL|Amateur Extra ;; 1|NULL|General ;; 2|NULL|Technician ;; 2|RN|NULL ;;"
            + " 3|NULL|NULL ;; 5|LVN|NULL",
            rowSet(NURSE_COUNT + "GROUPING SETS (medical_license, radio_license)"
                + " ORDER BY 3 DESC NULLS FIRST"));

        // GROUPING() is what separates them: 1 means "this column is not part of THIS row's set".
        assertEquals("5|LVN|NULL|0|1 ;; 2|RN|NULL|0|1 ;; 3|NULL|NULL|1|0 ;; 1|NULL|Amateur Extra|1|0 ;;"
            + " 1|NULL|General|1|0 ;; 2|NULL|Technician|1|0",
            rows("SELECT COUNT(*), medical_license, radio_license,"
                + " GROUPING(medical_license) AS grp_medical, GROUPING(radio_license) AS grp_radio"
                + " FROM nurses GROUP BY GROUPING SETS (medical_license, radio_license)"
                + " ORDER BY 4, 5, 2 NULLS FIRST, 3 NULLS FIRST"));
    }

    /** A grouping set of () beside a plain key adds that key's own totals to the result. */
    @Test
    public void anEmptyGroupingSetAddsTheKeysOwnTotals() {
        createNurses();
        engine.execute("INSERT INTO nurses (ID, full_name, medical_license, radio_license) VALUES"
            + " (101, 'Lily Vine', 'LVN', NULL), (102, 'Larry Vancouver', 'LVN', NULL),"
            + " (172, 'Rhonda Nova', 'RN', NULL)");
        assertEquals("1|LVN|General ;; 1|RN|Amateur Extra ;; 1|RN|NULL ;; 2|LVN|NULL ;;"
            + " 2|LVN|Technician ;; 2|RN|NULL ;; 5|LVN|NULL",
            rowSet(NURSE_COUNT + "medical_license, GROUPING SETS (radio_license, ())"
                + " ORDER BY 3 DESC NULLS FIRST"));
        assertEquals("1|LVN|General ;; 1|RN|Amateur Extra ;; 1|RN|NULL ;; 2|LVN|NULL ;;"
            + " 2|LVN|Technician",
            rowSet(NURSE_COUNT + "medical_license, radio_license ORDER BY 3 DESC NULLS FIRST"));
    }

    /** GROUPING over several columns is a BITMASK, most significant bit first — the doc's own table. */
    @Test
    public void groupingReturnsABitmaskOverSeveralColumns() {
        createAggr2();
        assertEquals("1|NULL|4|0|1|1 ;; 2|NULL|24|0|1|1 ;; NULL|1|10|1|0|2 ;; NULL|2|18|1|0|2 ;;"
            + " NULL|NULL|28|1|1|3",
            rows("SELECT col_x, col_y, sum(col_z), grouping(col_x), grouping(col_y),"
                + " grouping(col_x, col_y) FROM aggr2"
                + " GROUP BY GROUPING SETS ((col_x), (col_y), ()) ORDER BY 1, 2"));
    }

    /** GROUPING_ID answers the same numbers, and is 0 for every row of a plain GROUP BY. */
    @Test
    public void groupingIdMatchesGroupingAndIsZeroWithoutSuperGroups() {
        createAggr2();
        assertEquals("1|4|0 ;; 2|24|0",
            rows("SELECT col_x, sum(col_z), GROUPING_ID(col_x) FROM aggr2 GROUP BY col_x ORDER BY col_x"));
        assertEquals("1|NULL|4|0|1|1 ;; 2|NULL|24|0|1|1 ;; NULL|NULL|28|1|1|3 ;; NULL|2|18|1|0|2 ;;"
            + " NULL|1|10|1|0|2",
            rows("SELECT col_x, col_y, sum(col_z), GROUPING_ID(col_x), GROUPING_ID(col_y),"
                + " GROUPING_ID(col_x, col_y) FROM aggr2"
                + " GROUP BY GROUPING SETS ((col_x), (col_y), ()) ORDER BY col_x ASC, col_y DESC"));
    }

    /** HAVING can filter ON the grouping level, keeping only the super-aggregate rows. */
    @Test
    public void havingCanFilterOnTheGroupingLevel() {
        createAggr2();
        assertEquals("NULL|1|10 ;; NULL|2|18 ;; NULL|NULL|28",
            rows("SELECT col_x, col_y, sum(col_z) FROM aggr2"
                + " GROUP BY GROUPING SETS ((col_x), (col_y), ()) HAVING GROUPING(col_x) = 1"
                + " ORDER BY 1, 2"));
    }

    /** CUBE and ROLLUP do not NEST — inside another super-group they are read as unknown functions. */
    @Test
    public void nestedSuperGroupsAreUnknownFunctions() {
        final String quantity = "SELECT SUM(s.quantity) AS q FROM products AS p, sales AS s"
            + " WHERE s.product_ID = p.product_ID GROUP BY ";
        assertTrue(refusal(quantity + "GROUPING SETS (CUBE (state, city))")
            .contains("Unknown function CUBE."),
            refusal(quantity + "GROUPING SETS (CUBE (state, city))"));
        assertTrue(refusal(quantity + "GROUPING SETS ((state), ROLLUP (city))")
            .contains("Unknown function ROLLUP."),
            refusal(quantity + "GROUPING SETS ((state), ROLLUP (city))"));
    }
}
