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
 * The TRANSIENT table type, asserted through the SQL surface — the {@code SHOW TABLES} kind cell
 * (TRANSIENT vs TABLE, including through CLONE), plus the comment, cluster_by and DESCRIBE cells
 * of the co-declared options — so every check runs against whichever engine executed the DDL,
 * embedded or live.
 */
public class TransientTableTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(TransientTableTest.class);

    /** One SHOW TABLES cell for the given table, optionally schema-qualified. */
    private String tableCell(final String table, final String column) {
        final String bareName = table.contains(".")
            ? table.substring(table.lastIndexOf('.') + 1) : table;
        final String scope = table.contains(".")
            ? " IN SCHEMA " + table.substring(0, table.lastIndexOf('.')) : "";
        final ResultSet rs = engine.executeQuery("SHOW TABLES LIKE '" + bareName + "'" + scope);
        return cell(rs, soleRowWhere(rs, "name", bareName.toUpperCase()), column);
    }

    private String kind(final String table) {
        return tableCell(table, "kind");
    }

    @Test
    public void testCreateTransientTable() {
        logger.info("Testing CREATE TRANSIENT TABLE");

        engine.execute("CREATE TRANSIENT TABLE test1 (id INTEGER, name VARCHAR)");

        assertEquals("TRANSIENT", kind("test1"));

        logger.info("CREATE TRANSIENT TABLE works correctly");
    }

    @Test
    public void testCreateRegularTable() {
        logger.info("Testing CREATE TABLE without TRANSIENT");

        engine.execute("CREATE TABLE test2 (id INTEGER, name VARCHAR)");

        assertEquals("TABLE", kind("test2"));

        logger.info("Regular table is not transient");
    }

    @Test
    public void testTransientTableWithIfNotExists() {
        logger.info("Testing CREATE TRANSIENT TABLE IF NOT EXISTS");

        engine.execute("CREATE TRANSIENT TABLE IF NOT EXISTS test3 (id INTEGER, name VARCHAR)");

        assertEquals("TRANSIENT", kind("test3"));

        // Executing again should not throw error
        engine.execute("CREATE TRANSIENT TABLE IF NOT EXISTS test3 (id INTEGER, name VARCHAR)");

        logger.info("CREATE TRANSIENT TABLE IF NOT EXISTS works correctly");
    }

    @Test
    public void testTransientTableWithQualifiedName() {
        logger.info("Testing CREATE TRANSIENT TABLE with qualified name");

        engine.execute("CREATE SCHEMA transient_schema");
        engine.execute("CREATE TRANSIENT TABLE transient_schema.test4 (id INTEGER, name VARCHAR)");

        assertEquals("TRANSIENT", kind("transient_schema.test4"));

        logger.info("TRANSIENT table with qualified name works correctly");
    }

    @Test
    public void testTransientTableWithComment() {
        logger.info("Testing CREATE TRANSIENT TABLE with COMMENT");

        engine.execute("CREATE TRANSIENT TABLE test5 (id INTEGER, name VARCHAR) COMMENT = 'Test transient table'");

        assertEquals("TRANSIENT", kind("test5"));
        assertEquals("Test transient table", tableCell("test5", "comment"));

        logger.info("TRANSIENT table with COMMENT works correctly");
    }

    @Test
    public void testTransientTableWithCommentAfterTableName() {
        logger.info("Testing CREATE TRANSIENT TABLE with COMMENT after table name");

        engine.execute("CREATE TRANSIENT TABLE test6 COMMENT = 'Comment position test' (id INTEGER)");

        assertEquals("TRANSIENT", kind("test6"));
        assertEquals("Comment position test", tableCell("test6", "comment"));

        logger.info("TRANSIENT table with COMMENT after table name works correctly");
    }

    @Test
    public void testTransientTableWithClusterBy() {
        logger.info("Testing CREATE TRANSIENT TABLE with CLUSTER BY");

        engine.execute("""
            CREATE TRANSIENT TABLE test7 (
                id INTEGER,
                date DATE,
                value NUMBER
            ) CLUSTER BY (date)
            """);

        assertEquals("TRANSIENT", kind("test7"));
        assertEquals("LINEAR(date)", tableCell("test7", "cluster_by"));

        logger.info("TRANSIENT table with CLUSTER BY works correctly");
    }

    @Test
    public void testTransientTableWithConstraints() {
        logger.info("Testing CREATE TRANSIENT TABLE with constraints");

        engine.execute("""
            CREATE TRANSIENT TABLE test8 (
                id INTEGER PRIMARY KEY,
                email VARCHAR UNIQUE,
                name VARCHAR NOT NULL,
                status VARCHAR DEFAULT 'active'
            )
            """);

        assertEquals("TRANSIENT", kind("test8"));
        assertEquals("Y", describeCell("test8", "ID", "primary key"));

        logger.info("TRANSIENT table with constraints works correctly");
    }

    @Test
    public void testCloneTransientTable() {
        logger.info("Testing CLONE preserves TRANSIENT flag");

        engine.execute("CREATE TRANSIENT TABLE source (id INTEGER, name VARCHAR)");
        engine.execute("CREATE TRANSIENT TABLE test9 CLONE source");

        assertEquals("TRANSIENT", kind("source"));
        assertEquals("TRANSIENT", kind("test9"));

        logger.info("CLONE preserves TRANSIENT flag correctly");
    }

    @Test
    public void testCloneRegularTableAsTransient() {
        logger.info("Testing CLONE regular table as TRANSIENT");

        engine.execute("CREATE TABLE source2 (id INTEGER, name VARCHAR)");
        engine.execute("CREATE TRANSIENT TABLE test10 CLONE source2");

        assertEquals("TABLE", kind("source2"));
        assertEquals("TRANSIENT", kind("test10"));

        logger.info("CLONE regular table as TRANSIENT works correctly");
    }

    @Test
    public void testCloneTransientTableAsRegularIsRefused() {
        logger.info("Testing CLONE of a transient table into a permanent one is refused");

        engine.execute("CREATE TRANSIENT TABLE source3 (id INTEGER, name VARCHAR)");

        // A transient table cannot become permanent by cloning (live-verified). The reverse —
        // cloning a permanent table INTO a transient one — is allowed, as testCloneRegularTableAsTransient shows.
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE test11 CLONE source3");
            }
        });
        assertTrue(e.getMessage().contains("Transient object cannot be cloned to a permanent object."),
            e.getMessage());

        assertEquals(0, engine.executeQuery("SHOW TABLES LIKE 'test11'").getRowCount());

        logger.info("Transient-to-permanent clone is refused");
    }

    @Test
    public void testTransientTableWithMultipleColumns() {
        logger.info("Testing CREATE TRANSIENT TABLE with multiple columns");

        engine.execute("""
            CREATE TRANSIENT TABLE test12 (
                id INTEGER,
                first_name VARCHAR,
                last_name VARCHAR,
                email VARCHAR,
                created_at TIMESTAMP,
                updated_at TIMESTAMP
            )
            """);

        assertEquals("TRANSIENT", kind("test12"));
        assertEquals(6, engine.executeQuery("DESCRIBE TABLE test12").getRowCount());

        logger.info("TRANSIENT table with multiple columns works correctly");
    }

    @Test
    public void testTransientTableWithCollation() {
        logger.info("Testing CREATE TRANSIENT TABLE with COLLATE");

        engine.execute("""
            CREATE TRANSIENT TABLE test13 (
                id INTEGER,
                name VARCHAR COLLATE 'utf8'
            )
            """);

        assertEquals("TRANSIENT", kind("test13"));
        final String nameType = describeCell("test13", "NAME", "type");
        assertTrue(nameType.contains("COLLATE 'utf8'"), nameType);

        logger.info("TRANSIENT table with COLLATE works correctly");
    }

    @Test
    public void testTransientTableAllFeaturesCombined() {
        logger.info("Testing CREATE TRANSIENT TABLE with all features combined");

        engine.execute("CREATE SCHEMA transient_schema");
        engine.execute("""
            CREATE TRANSIENT TABLE IF NOT EXISTS transient_schema.test14
            COMMENT = 'Full featured transient table'
            (
                id INTEGER PRIMARY KEY,
                name VARCHAR(200) NOT NULL UNIQUE COLLATE 'utf8',
                status VARCHAR DEFAULT 'active',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                date DATE
            )
            CLUSTER BY (date)
            """);

        assertEquals("TRANSIENT", kind("transient_schema.test14"));
        assertEquals("Full featured transient table", tableCell("transient_schema.test14", "comment"));
        assertEquals("LINEAR(date)", tableCell("transient_schema.test14", "cluster_by"));

        logger.info("TRANSIENT table with all features works correctly");
    }

    @Test
    public void testMultipleTransientTables() {
        logger.info("Testing multiple TRANSIENT tables");

        engine.execute("CREATE TRANSIENT TABLE transient1 (id INTEGER)");
        engine.execute("CREATE TABLE regular1 (id INTEGER)");
        engine.execute("CREATE TRANSIENT TABLE transient2 (id INTEGER)");
        engine.execute("CREATE TABLE regular2 (id INTEGER)");

        assertEquals("TRANSIENT", kind("transient1"));
        assertEquals("TABLE", kind("regular1"));
        assertEquals("TRANSIENT", kind("transient2"));
        assertEquals("TABLE", kind("regular2"));

        logger.info("Multiple TRANSIENT tables work correctly");
    }

    @Test
    public void testTransientTableWithForeignKey() {
        logger.info("Testing CREATE TRANSIENT TABLE with FOREIGN KEY");

        engine.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TRANSIENT TABLE test15 (
                id INTEGER PRIMARY KEY,
                parent_id INTEGER REFERENCES parent(id)
            )
            """);

        assertEquals("TRANSIENT", kind("test15"));
        final ResultSet keys = engine.executeQuery("SHOW IMPORTED KEYS IN TABLE test15");
        soleRowWhere(keys, "fk_column_name", "PARENT_ID");

        logger.info("TRANSIENT table with FOREIGN KEY works correctly");
    }
}
