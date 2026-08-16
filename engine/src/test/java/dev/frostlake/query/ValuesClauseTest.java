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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ValuesClauseTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(ValuesClauseTest.class);

    @Test
    public void testSelectFromValuesTwoColumns() {
        logger.info("Testing SELECT * FROM VALUES with two columns ORDER BY 1");

        final ResultSet rs = engine.executeQuery("SELECT * FROM VALUES(1,2),(3,4) ORDER BY 1");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(2, rs.getRowCount(), "Should return 2 rows");
        assertEquals(2, rs.getColumns().size(), "Should have 2 columns");

        // Check column names
        assertEquals("COLUMN1", rs.getColumns().get(0).getName(), "First column should be COLUMN1");
        assertEquals("COLUMN2", rs.getColumns().get(1).getName(), "Second column should be COLUMN2");

        // Check row 1
        final Object row1col1 = rs.getRows().get(0).getValue(0);
        final Object row1col2 = rs.getRows().get(0).getValue(1);
        logger.info("Row 1: {}, {}", row1col1, row1col2);
        assertEquals(1L, ((Number) row1col1).longValue(), "Row 1 Column 1 should be 1");
        assertEquals(2L, ((Number) row1col2).longValue(), "Row 1 Column 2 should be 2");

        // Check row 2
        final Object row2col1 = rs.getRows().get(1).getValue(0);
        final Object row2col2 = rs.getRows().get(1).getValue(1);
        logger.info("Row 2: {}, {}", row2col1, row2col2);
        assertEquals(3L, ((Number) row2col1).longValue(), "Row 2 Column 1 should be 3");
        assertEquals(4L, ((Number) row2col2).longValue(), "Row 2 Column 2 should be 4");
    }

    @Test
    public void testSelectFromValuesSingleColumn() {
        logger.info("Testing SELECT * FROM VALUES with single column ORDER BY 1");

        final ResultSet rs = engine.executeQuery("SELECT * FROM VALUES(10),(20),(30) ORDER BY 1");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(3, rs.getRowCount(), "Should return 3 rows");
        assertEquals(1, rs.getColumns().size(), "Should have 1 column");

        assertEquals("COLUMN1", rs.getColumns().get(0).getName(), "Column should be COLUMN1");

        assertEquals(10L, ((Number) rs.getRows().get(0).getValue(0)).longValue(), "Row 1 should be 10");
        assertEquals(20L, ((Number) rs.getRows().get(1).getValue(0)).longValue(), "Row 2 should be 20");
        assertEquals(30L, ((Number) rs.getRows().get(2).getValue(0)).longValue(), "Row 3 should be 30");
    }

    @Test
    public void testSelectFromValuesMixedTypes() {
        logger.info("Testing SELECT * FROM VALUES with mixed types ORDER BY 1");

        final ResultSet rs = engine.executeQuery("SELECT * FROM VALUES(1, 'Alice'), (2, 'Bob') ORDER BY 1");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(2, rs.getRowCount(), "Should return 2 rows");
        assertEquals(2, rs.getColumns().size(), "Should have 2 columns");

        // Row 1
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("Alice", rs.getRows().get(0).getValue(1));

        // Row 2
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals("Bob", rs.getRows().get(1).getValue(1));
    }

    @Test
    public void testSelectFromValuesWithAlias() {
        logger.info("Testing SELECT * FROM VALUES with table alias ORDER BY 1");

        final ResultSet rs = engine.executeQuery("SELECT * FROM VALUES(1, 2), (3, 4) AS t ORDER BY 1");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(2, rs.getRowCount(), "Should return 2 rows");
        assertEquals(2, rs.getColumns().size(), "Should have 2 columns");
    }

    @Test
    public void testSelectSpecificColumnsFromValues() {
        logger.info("Testing SELECT specific columns FROM VALUES ORDER BY 1");

        final ResultSet rs = engine.executeQuery("SELECT COLUMN1 FROM VALUES(1, 2), (3, 4) ORDER BY 1");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(2, rs.getRowCount(), "Should return 2 rows");
        assertEquals(1, rs.getColumns().size(), "Should have 1 column");

        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(3L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }

    @Test
    public void testSelectFromValuesWithWhere() {
        logger.info("Testing SELECT FROM VALUES with WHERE clause ORDER BY 1");

        final ResultSet rs = engine.executeQuery("SELECT * FROM VALUES(1, 2), (3, 4) WHERE COLUMN1 > 1 ORDER BY 1");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return 1 row after filtering");
        assertEquals(2, rs.getColumns().size(), "Should have 2 columns");

        assertEquals(3L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(4L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void testSelectFromValuesWithColumnAliases() {
        logger.info("Testing SELECT FROM VALUES with column aliases ORDER BY 1");

        final ResultSet rs = engine.executeQuery("SELECT i, j FROM VALUES(1, 2), (3, 4) AS t(i, j) ORDER BY 1");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(2, rs.getRowCount(), "Should return 2 rows");
        assertEquals(2, rs.getColumns().size(), "Should have 2 columns");

        // Check column names
        assertEquals("I", rs.getColumns().get(0).getName(), "First column should be I");
        assertEquals("J", rs.getColumns().get(1).getName(), "Second column should be J");

        // Check values
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(3L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals(4L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }

    @Test
    public void testSelectFromValuesWithColumnAliasesAndGroupBy() {
        logger.info("Testing SELECT FROM VALUES with column aliases and GROUP BY ORDER BY 1");

        final ResultSet rs = engine.executeQuery("SELECT i, count(*) FROM VALUES(1, 2), (1, 2) AS t(i, j) GROUP BY i ORDER BY 1");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return 1 row after grouping");
        assertEquals(2, rs.getColumns().size(), "Should have 2 columns");

        // Check the grouped value
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue(), "Should group by i=1");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue(), "Count should be 2");
    }

    @Test
    public void testSelectFromValuesWithPartialColumnAliases() {
        logger.info("Testing SELECT FROM VALUES with partial column aliases ORDER BY 1");

        // Only provide alias for first column
        final ResultSet rs = engine.executeQuery("SELECT x FROM VALUES(10, 20), (30, 40) AS t(x) ORDER BY 1");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(2, rs.getRowCount(), "Should return 2 rows");
        assertEquals(1, rs.getColumns().size(), "Should have 1 column");

        assertEquals("X", rs.getColumns().get(0).getName(), "Column should be X");
        assertEquals(10L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(30L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }

    @Test
    public void testSelectStarFromValuesEmptyStringsLabelsColumns() {
        logger.info("Testing SELECT * FROM VALUES with empty-string tuples labels COLUMN1..n ORDER BY 1");

        // The reported query: empty-string values still auto-name the columns COLUMN1..COLUMNn.
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM VALUES ('', '', '', '', ''), ('', '', '', '', '') ORDER BY 1");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(2, rs.getRowCount(), "Should return 2 rows");
        assertEquals(5, rs.getColumns().size(), "Should have 5 columns");
        for (int i = 0; i < 5; i++) {
            assertEquals("COLUMN" + (i + 1), rs.getColumns().get(i).getName(),
                "Column " + (i + 1) + " should be COLUMN" + (i + 1));
            assertEquals("", rs.getRows().get(0).getValue(i), "Value should be the empty string");
        }
    }

    @Test
    public void testUserQueryExample() {
        logger.info("Testing user's exact query example");

        // User's exact query: select i, count(*) from values (1,2),(1,2) as t(i,j) group by i
        final ResultSet rs = engine.executeQuery("select i, count(*) from values (1,2),(1,2) as t(i,j) group by i");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return 1 grouped row");
        assertEquals(2, rs.getColumns().size(), "Should have 2 columns");

        // Verify column names
        assertEquals("I", rs.getColumns().get(0).getName(), "First column should be I");
        // An unaliased expression is named by its source text folded to upper case (live-verified)
        assertEquals("COUNT(*)", rs.getColumns().get(1).getName(), "Second column should be COUNT(*)");

        // Verify values
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue(), "Grouped value should be 1");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue(), "Count should be 2");
    }
}
