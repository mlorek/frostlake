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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for STRING_LITERAL with double quotes and newlines
 */
public class StringLiteralTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(StringLiteralTest.class);

    @Override
    protected void setupTest() {
        logger.info("DatabaseEngine initialized for STRING_LITERAL tests");
    }

    @Test
    public void testSingleQuotedStringWithNewline() {
        logger.info("Testing single-quoted string with newline");

        engine.execute("CREATE TABLE test_strings (id INTEGER, text VARCHAR)");
        engine.execute("INSERT INTO test_strings VALUES (1, 'Hello\nWorld')");

        final ResultSet rs = engine.executeQuery("SELECT text FROM test_strings WHERE id = 1");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());

        final String text = (String) rs.getRows().get(0).getValue(0);
        assertTrue(text.contains("\n") || text.contains("\\n"));

        logger.info("Single-quoted string with newline: {}", text);
    }

    @Test
    public void testSingleQuotedMultilineString() {
        logger.info("Testing single-quoted multi-line string");

        final String multiline = """
            First line
            Second line
            Third line""";

        engine.execute("CREATE TABLE test_multiline (id INTEGER, content VARCHAR)");
        engine.execute("INSERT INTO test_multiline VALUES (1, '" + multiline + "')");

        final ResultSet rs = engine.executeQuery("SELECT content FROM test_multiline WHERE id = 1");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());

        final String content = (String) rs.getRows().get(0).getValue(0);
        assertNotNull(content);

        logger.info("Multi-line string stored: length={}", content.length());
    }

    @Test
    public void testSingleQuotedStringWithoutNewline() {
        logger.info("Testing single-quoted string without newline still works");

        engine.execute("CREATE TABLE test_simple (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO test_simple VALUES (1, 'Alice')");

        final ResultSet rs = engine.executeQuery("SELECT name FROM test_simple WHERE id = 1");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        assertEquals("Alice", rs.getRows().get(0).getValue(0));

        logger.info("Simple single-quoted string works correctly");
    }

    @Test
    public void testSingleQuotedStringWithEscapedQuotes() {
        logger.info("Testing single-quoted string with escaped quotes");

        engine.execute("CREATE TABLE test_escaped (id INTEGER, text VARCHAR)");
        engine.execute("INSERT INTO test_escaped VALUES (1, 'She said ''Hello''')");

        final ResultSet rs = engine.executeQuery("SELECT text FROM test_escaped WHERE id = 1");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());

        final String text = (String) rs.getRows().get(0).getValue(0);
        assertNotNull(text);

        logger.info("Single-quoted string with escaped quotes: {}", text);
    }

    @Test
    public void testCommentWithNewline() {
        logger.info("Testing COMMENT with single-quoted string containing newline");

        engine.execute("CREATE TABLE test_comment (id INTEGER) COMMENT = 'This is a comment\nwith a newline'");

        final ResultSet rs = engine.executeQuery("SHOW TABLES");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() > 0);

        logger.info("COMMENT with single-quoted string containing newline works");
    }
}
