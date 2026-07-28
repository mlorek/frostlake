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
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for SQL parsing edge cases to identify any "mismatched input" errors
 */
public class ParsingEdgeCasesTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(ParsingEdgeCasesTest.class);

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE test (id INTEGER, name VARCHAR, value DOUBLE)");
        engine.execute("INSERT INTO test VALUES (1, 'A', 10.5)");
        engine.execute("INSERT INTO test VALUES (2, 'B', 20.5)");
    }

    @Test
    public void testSelectStar() {
        logger.info("Testing SELECT *");
        final ResultSet result = engine.executeQuery("SELECT * FROM test");
        assertEquals(2, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }

    @Test
    public void testSelectWithParentheses() {
        logger.info("Testing SELECT with parentheses");
        final ResultSet parenColumn = engine.executeQuery("SELECT (id) FROM test");
        assertEquals(2, parenColumn.getRowCount());
        assertEquals(1, parenColumn.getColumnCount());
        assertEquals(1L, ((Number) parenColumn.getRows().get(0).getValue(0)).longValue());

        final ResultSet parenExpr = engine.executeQuery("SELECT (id + 1) FROM test");
        assertEquals(2, parenExpr.getRowCount());
        assertEquals(2L, ((Number) parenExpr.getRows().get(0).getValue(0)).longValue());
        assertEquals(3L, ((Number) parenExpr.getRows().get(1).getValue(0)).longValue());
    }

    @Test
    public void testSelectWithMultipleParentheses() {
        logger.info("Testing SELECT with nested parentheses");
        final ResultSet nested = engine.executeQuery("SELECT ((id)) FROM test");
        assertEquals(2, nested.getRowCount());
        assertEquals(1L, ((Number) nested.getRows().get(0).getValue(0)).longValue());

        final ResultSet nestedExpr = engine.executeQuery("SELECT ((id + 1) * 2) FROM test");
        assertEquals(2, nestedExpr.getRowCount());
        // (1 + 1) * 2 = 4, (2 + 1) * 2 = 6
        assertEquals(4L, ((Number) nestedExpr.getRows().get(0).getValue(0)).longValue());
        assertEquals(6L, ((Number) nestedExpr.getRows().get(1).getValue(0)).longValue());
    }

    @Test
    public void testSelectWithCaseExpression() {
        logger.info("Testing SELECT with CASE");
        final ResultSet result = engine.executeQuery("""
            SELECT CASE WHEN id = 1 THEN 'one' ELSE 'other' END FROM test
            """);
        assertEquals(2, result.getRowCount());
        assertEquals("one", result.getRows().get(0).getValue(0));
        assertEquals("other", result.getRows().get(1).getValue(0));
    }

    @Test
    public void testSelectWithSubquery() {
        logger.info("Testing SELECT with subquery");
        final ResultSet result = engine.executeQuery("SELECT * FROM (SELECT * FROM test) AS t");
        assertEquals(2, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }

    @Test
    public void testSelectWithMultipleJoins() {
        logger.info("Testing SELECT with multiple joins");
        engine.execute("CREATE TABLE t2 (id INTEGER, other VARCHAR)");
        engine.execute("INSERT INTO t2 VALUES (1, 'X')");

        final ResultSet result = engine.executeQuery("""
            SELECT t1.id, t2.other
            FROM test t1
            JOIN t2 ON t1.id = t2.id
            """);
        // Only id=1 matches between test and t2.
        assertEquals(1, result.getRowCount());
        assertEquals(2, result.getColumnCount());
        assertEquals(1L, ((Number) result.getRows().get(0).getValue(0)).longValue());
        assertEquals("X", result.getRows().get(0).getValue(1));
    }

    @Test
    public void testSelectWithUnion() {
        logger.info("Testing SELECT with UNION");
        final ResultSet result = engine.executeQuery("""
            SELECT id FROM test WHERE id = 1
            UNION
            SELECT id FROM test WHERE id = 2
            """);
        assertEquals(2, result.getRowCount());
        assertEquals(1, result.getColumnCount());
    }

    @Test
    public void testSelectWithGroupBy() {
        logger.info("Testing SELECT with GROUP BY");
        final ResultSet result = engine.executeQuery("SELECT name, COUNT(*) FROM test GROUP BY name");
        // Two distinct names ('A', 'B'), each with one row.
        assertEquals(2, result.getRowCount());
        assertEquals(2, result.getColumnCount());
    }

    @Test
    public void testSelectWithOrderBy() {
        logger.info("Testing SELECT with ORDER BY");
        final ResultSet result = engine.executeQuery("SELECT * FROM test ORDER BY id DESC");
        assertEquals(2, result.getRowCount());
        // DESC: id=2 first, id=1 second.
        assertEquals(2L, ((Number) result.getRows().get(0).getValue(0)).longValue());
        assertEquals(1L, ((Number) result.getRows().get(1).getValue(0)).longValue());
    }

    @Test
    public void testSelectWithLimit() {
        logger.info("Testing SELECT with LIMIT");
        final ResultSet result = engine.executeQuery("SELECT * FROM test LIMIT 1");
        assertEquals(1, result.getRowCount());
    }

    @Test
    public void testInsertSelect() {
        logger.info("Testing INSERT SELECT");
        engine.execute("CREATE TABLE target (id INTEGER, name VARCHAR, value DOUBLE)");
        engine.execute("INSERT INTO target SELECT * FROM test");

        final ResultSet result = engine.executeQuery("SELECT * FROM target ORDER BY id");
        assertEquals(2, result.getRowCount());
        assertEquals(3, result.getColumnCount());
        assertEquals(1L, ((Number) result.getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) result.getRows().get(1).getValue(0)).longValue());
    }

    @Test
    public void testUpdateWithSubquery() {
        logger.info("Testing UPDATE with subquery");
        engine.execute("""
            UPDATE test SET value = (SELECT MAX(value) FROM test) WHERE id = 1
            """);

        // MAX(value) over {10.5, 20.5} is 20.5; only id=1 is updated.
        final ResultSet result = engine.executeQuery("SELECT value FROM test WHERE id = 1");
        assertEquals(1, result.getRowCount());
        assertEquals(20.5, ((Number) result.getRows().get(0).getValue(0)).doubleValue(), 0.0001);
    }

    @Test
    public void testDeleteWithSubquery() {
        logger.info("Testing DELETE with subquery");
        engine.execute("""
            DELETE FROM test WHERE id IN (SELECT id FROM test WHERE value > 15)
            """);

        // Only id=2 has value > 15, so it is deleted, leaving id=1.
        final ResultSet result = engine.executeQuery("SELECT id FROM test ORDER BY id");
        assertEquals(1, result.getRowCount());
        assertEquals(1L, ((Number) result.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testSelectDistinct() {
        logger.info("Testing SELECT DISTINCT");
        final ResultSet result = engine.executeQuery("SELECT DISTINCT name FROM test");
        // Names 'A' and 'B' are already distinct.
        assertEquals(2, result.getRowCount());
        assertEquals(1, result.getColumnCount());
    }

    @Test
    public void testSelectWithAllKeyword() {
        logger.info("Testing SELECT with ALL keyword");
        // SELECT ALL is the (default) DISTINCT counterpart — live-Snowflake verified.
        assertEquals(2, engine.executeQuery("SELECT ALL name FROM test").getRowCount());
    }

    @Test
    public void testSelectWithTop() {
        logger.info("Testing SELECT with TOP");
        final ResultSet result = engine.executeQuery("SELECT TOP 1 * FROM test");
        assertEquals(1, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }

    @Test
    public void testSelectWithOffset() {
        logger.info("Testing SELECT with OFFSET");
        // OFFSET is only valid as part of a LIMIT clause, not standalone.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM test OFFSET 1");
            }
        });
    }

    @Test
    public void testSelectWithFetch() {
        logger.info("Testing SELECT with FETCH");
        final ResultSet result = engine.executeQuery("SELECT * FROM test FETCH FIRST 1 ROWS ONLY");
        assertEquals(1, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }

    @Test
    public void testSelectForUpdate() {
        logger.info("Testing SELECT FOR UPDATE");
        // FOR UPDATE locking syntax is not part of the grammar.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM test FOR UPDATE");
            }
        });
    }

    @Test
    public void testCommentInSQL() {
        logger.info("Testing comments in SQL");
        final ResultSet result = engine.executeQuery("""
            -- This is a comment
            SELECT * FROM test
            """);
        assertEquals(2, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }

    @Test
    public void testMultilineComment() {
        logger.info("Testing multiline comment");
        final ResultSet result = engine.executeQuery("""
            /* This is a
               multiline comment */
            SELECT * FROM test
            """);
        assertEquals(2, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }
}
