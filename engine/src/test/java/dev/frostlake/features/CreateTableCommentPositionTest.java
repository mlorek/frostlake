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
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The COMMENT clause's positions in CREATE TABLE — immediately after the table name or at the
 * statement's end — asserted through the SQL surface: the {@code comment} and {@code cluster_by}
 * cells of {@code SHOW TABLES} (an unset comment reads as the empty string) and
 * {@code DESCRIBE TABLE} row counts, so every check runs against whichever engine executed the
 * DDL, embedded or live.
 */
public class CreateTableCommentPositionTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(CreateTableCommentPositionTest.class);

    /** One SHOW TABLES cell for the given table in the current schema. */
    private String tableCell(final String table, final String column) {
        final ResultSet rs = engine.executeQuery("SHOW TABLES LIKE '" + table + "'");
        return cell(rs, soleRowWhere(rs, "name", table.toUpperCase()), column);
    }

    private String tableComment(final String table) {
        return tableCell(table, "comment");
    }

    private int columnCount(final String table) {
        return engine.executeQuery("DESCRIBE TABLE " + table).getRowCount();
    }

    @Test
    public void testCommentAfterTableName() {
        logger.info("Testing COMMENT immediately after table name");

        engine.execute("CREATE TABLE test1 COMMENT = 'Table comment' (id INTEGER, name VARCHAR)");

        assertEquals("Table comment", tableComment("test1"));

        logger.info("COMMENT after table name works correctly");
    }

    @Test
    public void testCommentAtEnd() {
        logger.info("Testing COMMENT at end of CREATE TABLE (traditional position)");

        engine.execute("CREATE TABLE test2 (id INTEGER, name VARCHAR) COMMENT = 'End comment'");

        assertEquals("End comment", tableComment("test2"));

        logger.info("COMMENT at end works correctly");
    }

    @Test
    public void testCommentAfterTableNameWithClusterBy() {
        logger.info("Testing COMMENT after table name with CLUSTER BY");

        engine.execute("CREATE TABLE test3 COMMENT = 'Clustered table' (id INTEGER, date DATE) CLUSTER BY (date)");

        assertEquals("Clustered table", tableComment("test3"));
        assertEquals("LINEAR(date)", tableCell("test3", "cluster_by"));

        logger.info("COMMENT after table name with CLUSTER BY works correctly");
    }

    @Test
    public void testCommentAtEndWithClusterBy() {
        logger.info("Testing COMMENT at end with CLUSTER BY");

        engine.execute("CREATE TABLE test4 (id INTEGER, date DATE) CLUSTER BY (date) COMMENT = 'Clustered at end'");

        assertEquals("Clustered at end", tableComment("test4"));
        assertEquals("LINEAR(date)", tableCell("test4", "cluster_by"));

        logger.info("COMMENT at end with CLUSTER BY works correctly");
    }

    @Test
    public void testCommentAfterTableNameBeforeClusterBy() {
        logger.info("Testing COMMENT after table name, before CLUSTER BY");

        engine.execute("CREATE TABLE test5 COMMENT = 'Comment first' (id INTEGER, date DATE) CLUSTER BY (date)");

        assertEquals("Comment first", tableComment("test5"));

        logger.info("COMMENT before CLUSTER BY works correctly");
    }

    @Test
    public void testTwoCommentClausesAreRefused() {
        logger.info("Testing that a second COMMENT clause is refused");

        // A table carries at most one COMMENT, wherever it sits (live-verified). The refusal is
        // specific to COMMENT: a repeated CLUSTER BY is accepted.
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE test6 COMMENT = 'First position' (id INTEGER) COMMENT = 'Second position'");
            }
        });
        assertTrue(e.getMessage().contains("duplicate property 'COMMENT'"), e.getMessage());

        assertEquals(0, engine.executeQuery("SHOW TABLES LIKE 'test6'").getRowCount());

        logger.info("A second COMMENT clause is refused");
    }

    @Test
    public void testCommentAfterTableNameWithIfNotExists() {
        logger.info("Testing COMMENT after table name with IF NOT EXISTS");

        engine.execute("CREATE TABLE IF NOT EXISTS test7 COMMENT = 'With IF NOT EXISTS' (id INTEGER, name VARCHAR)");

        assertEquals("With IF NOT EXISTS", tableComment("test7"));

        logger.info("COMMENT with IF NOT EXISTS works correctly");
    }

    @Test
    public void testCommentAfterTableNameWithQualifiedName() {
        logger.info("Testing COMMENT after qualified table name");

        engine.execute("CREATE SCHEMA comment_pos_schema");
        engine.execute("CREATE TABLE comment_pos_schema.test8 COMMENT = 'Qualified name' (id INTEGER)");

        final ResultSet rs = engine.executeQuery("SHOW TABLES LIKE 'test8' IN SCHEMA comment_pos_schema");
        assertEquals("Qualified name", cell(rs, soleRowWhere(rs, "name", "TEST8"), "comment"));

        logger.info("COMMENT with qualified name works correctly");
    }

    @Test
    public void testCommentAfterTableNameWithMultipleColumns() {
        logger.info("Testing COMMENT after table name with multiple columns");

        engine.execute("""
            CREATE TABLE test9 COMMENT = 'Multi column table'
            (
                id INTEGER,
                name VARCHAR,
                email VARCHAR,
                created_at TIMESTAMP
            )
            """);

        assertEquals("Multi column table", tableComment("test9"));
        assertEquals(4, columnCount("test9"));

        logger.info("COMMENT with multiple columns works correctly");
    }

    @Test
    public void testNoCommentSpecified() {
        logger.info("Testing CREATE TABLE without COMMENT");

        engine.execute("CREATE TABLE test10 (id INTEGER, name VARCHAR)");

        assertEquals("", tableComment("test10"));

        logger.info("CREATE TABLE without COMMENT works correctly");
    }

    @Test
    public void testCommentAfterTableNameWithClone() {
        logger.info("Testing COMMENT after table name with CLONE");

        engine.execute("CREATE TABLE source (id INTEGER, name VARCHAR) COMMENT = 'Source table'");
        engine.execute("CREATE TABLE test11 COMMENT = 'Cloned with comment' CLONE source");

        assertEquals("Source table", tableComment("source"));
        assertEquals("Cloned with comment", tableComment("test11"));

        logger.info("COMMENT with CLONE overrides source comment");
    }

    @Test
    public void testCloneWithoutCommentInheritsSourceComment() {
        logger.info("Testing CLONE without COMMENT inherits source comment");

        engine.execute("CREATE TABLE source2 (id INTEGER) COMMENT = 'Original comment'");
        engine.execute("CREATE TABLE test12 CLONE source2");

        assertEquals("Original comment", tableComment("test12"));

        logger.info("CLONE without COMMENT inherits correctly");
    }

    @Test
    public void testCommentAtEndWithClone() {
        logger.info("Testing COMMENT at end with CLONE");

        engine.execute("CREATE TABLE source3 (id INTEGER) COMMENT = 'Source'");
        engine.execute("CREATE TABLE test13 CLONE source3 COMMENT = 'New comment at end'");

        assertEquals("New comment at end", tableComment("test13"));

        logger.info("COMMENT at end with CLONE works correctly");
    }

    @Test
    public void testCommentWithSpecialCharacters() {
        logger.info("Testing COMMENT with special characters");

        engine.execute("CREATE TABLE test14 COMMENT = 'Comment with \"quotes\" and \\'apostrophes\\'' (id INTEGER)");

        assertEquals("Comment with \"quotes\" and 'apostrophes'", tableComment("test14"));

        logger.info("COMMENT with special characters works correctly");
    }

    @Test
    public void testCommentAfterTableNameWithColumnConstraints() {
        logger.info("Testing COMMENT after table name with column constraints");

        engine.execute("""
            CREATE TABLE test15 COMMENT = 'Table with constraints'
            (
                id INTEGER PRIMARY KEY,
                email VARCHAR UNIQUE,
                name VARCHAR NOT NULL
            )
            """);

        assertEquals("Table with constraints", tableComment("test15"));

        logger.info("COMMENT with column constraints works correctly");
    }

    @Test
    public void testCommentAfterTableNameWithDefaultValues() {
        logger.info("Testing COMMENT after table name with DEFAULT values");

        engine.execute("""
            CREATE TABLE test16 COMMENT = 'Table with defaults'
            (
                id INTEGER DEFAULT 0,
                status VARCHAR DEFAULT 'active',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);

        assertEquals("Table with defaults", tableComment("test16"));

        logger.info("COMMENT with DEFAULT values works correctly");
    }

    @Test
    public void testClusterByThenCommentBeforeColumns() {
        logger.info("Testing CLUSTER BY then COMMENT, both before the column list");

        // Snowflake accepts the table-level property clauses in either order before the column list;
        // this CLUSTER-BY-then-COMMENT order is emitted by some production DDL.
        engine.execute("CREATE TABLE test17 CLUSTER BY (date) COMMENT = 'Cluster then comment' (id INTEGER, date DATE)");

        assertEquals("Cluster then comment", tableComment("test17"));
        assertEquals("LINEAR(date)", tableCell("test17", "cluster_by"));

        logger.info("CLUSTER BY then COMMENT before columns works correctly");
    }

    @Test
    public void testClusterByThenJsonCommentBeforeColumnsMultiKey() {
        logger.info("Testing multi-key CLUSTER BY then JSON COMMENT before columns");

        engine.execute("""
            CREATE TABLE IF NOT EXISTS test18
                CLUSTER BY (EFFECTIVE_DATE, GROUP_TYPE, RECORD_ID)
                COMMENT = '{"ver": "1.00.00", "doc": "derived KPI metric benchmarks."}'
            (
                METRIC_NAME    VARCHAR NOT NULL,
                EFFECTIVE_DATE DATE    NOT NULL,
                GROUP_TYPE     VARCHAR NOT NULL,
                RECORD_ID      VARCHAR NOT NULL
            )
            """);

        assertEquals("{\"ver\": \"1.00.00\", \"doc\": \"derived KPI metric benchmarks.\"}", tableComment("test18"));
        assertEquals("LINEAR(EFFECTIVE_DATE, GROUP_TYPE, RECORD_ID)", tableCell("test18", "cluster_by"));
        assertEquals(4, columnCount("test18"));

        logger.info("Multi-key CLUSTER BY then JSON COMMENT before columns works correctly");
    }
}
