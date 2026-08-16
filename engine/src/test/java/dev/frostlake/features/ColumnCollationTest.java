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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The COLLATE clause in column definitions, asserted through the SQL surface — a collated column
 * carries {@code COLLATE '<spec>'} inside its {@code DESCRIBE TABLE} type cell — so every check
 * runs against whichever engine executed the DDL, embedded or live. The specs used here are all
 * valid Snowflake collation specifications ({@code utf8}, locale specs like {@code en-ci}).
 */
public class ColumnCollationTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(ColumnCollationTest.class);

    /** The DESCRIBE TABLE type cell for one column. */
    private String columnType(final String table, final String column) {
        return describeCell(table, column, "type");
    }

    /** Asserts the column's type cell carries exactly this collation spec. */
    private void assertCollation(final String table, final String column, final String spec) {
        final String type = columnType(table, column);
        assertTrue(type.toUpperCase().contains("COLLATE '" + spec.toUpperCase() + "'"),
            column + " should be collated '" + spec + "' but its type reads: " + type);
    }

    private void assertNoCollation(final String table, final String column) {
        final String type = columnType(table, column);
        assertFalse(type.toUpperCase().contains("COLLATE"),
            column + " should carry no collation but its type reads: " + type);
    }

    @Test
    public void testSingleColumnWithCollation() {
        logger.info("Testing single column with COLLATE");

        engine.execute("CREATE TABLE test1 (id INTEGER, name VARCHAR COLLATE 'utf8')");

        assertCollation("test1", "NAME", "utf8");

        logger.info("Single column with COLLATE works correctly");
    }

    @Test
    public void testMultipleColumnsWithCollation() {
        logger.info("Testing multiple columns with COLLATE");

        engine.execute("""
            CREATE TABLE test2 (
                id INTEGER,
                name VARCHAR COLLATE 'utf8',
                description VARCHAR COLLATE 'en-ci',
                code VARCHAR
            )
            """);

        assertCollation("test2", "NAME", "utf8");
        assertCollation("test2", "DESCRIPTION", "en-ci");
        assertNoCollation("test2", "CODE");

        logger.info("Multiple columns with COLLATE work correctly");
    }

    @Test
    public void testCollationWithTypeParameters() {
        logger.info("Testing COLLATE with type parameters");

        engine.execute("CREATE TABLE test3 (id INTEGER, name VARCHAR(100) COLLATE 'en-cs')");

        assertCollation("test3", "NAME", "en-cs");

        logger.info("COLLATE with type parameters works correctly");
    }

    @Test
    public void testCollationWithConstraints() {
        logger.info("Testing COLLATE with column constraints");

        engine.execute("""
            CREATE TABLE test4 (
                id INTEGER PRIMARY KEY,
                name VARCHAR NOT NULL COLLATE 'utf8',
                email VARCHAR UNIQUE COLLATE 'fr-ci'
            )
            """);

        assertCollation("test4", "NAME", "utf8");
        assertEquals("N", describeCell("test4", "NAME", "null?"));

        assertCollation("test4", "EMAIL", "fr-ci");
        assertEquals("Y", describeCell("test4", "EMAIL", "unique key"));

        logger.info("COLLATE with constraints works correctly");
    }

    @Test
    public void testCollationWithDefault() {
        logger.info("Testing COLLATE with DEFAULT constraint");

        engine.execute("""
            CREATE TABLE test5 (
                id INTEGER,
                status VARCHAR COLLATE 'utf8' DEFAULT 'active'
            )
            """);

        assertCollation("test5", "STATUS", "utf8");
        assertEquals("'active'", describeCell("test5", "STATUS", "default"));

        logger.info("COLLATE with DEFAULT works correctly");
    }

    @Test
    public void testCollationWithComment() {
        logger.info("Testing COLLATE with COMMENT");

        engine.execute("""
            CREATE TABLE test6 (
                id INTEGER,
                name VARCHAR COLLATE 'utf8' COMMENT 'User name'
            )
            """);

        assertCollation("test6", "NAME", "utf8");
        assertEquals("User name", describeCell("test6", "NAME", "comment"));

        logger.info("COLLATE with COMMENT works correctly");
    }

    @Test
    public void testCollationCaseInsensitive() {
        logger.info("Testing COLLATE case-insensitive names");

        engine.execute("CREATE TABLE test7 (id INTEGER, name VARCHAR COLLATE 'EN-CI')");

        assertCollation("test7", "NAME", "en-ci");

        logger.info("COLLATE case-insensitive names work correctly");
    }

    @Test
    public void testCollationWithQualifiedTableName() {
        logger.info("Testing COLLATE with qualified table name");

        engine.execute("CREATE SCHEMA collation_schema");
        engine.execute("""
            CREATE TABLE collation_schema.test8 (
                id INTEGER,
                name VARCHAR COLLATE 'utf8'
            )
            """);

        assertCollation("collation_schema.test8", "NAME", "utf8");

        logger.info("COLLATE with qualified table name works correctly");
    }

    @Test
    public void testCollationCloneTable() {
        logger.info("Testing COLLATE preserved during CLONE");

        engine.execute("""
            CREATE TABLE source (
                id INTEGER,
                name VARCHAR COLLATE 'utf8',
                description VARCHAR COLLATE 'en-cs'
            )
            """);
        engine.execute("CREATE TABLE test9 CLONE source");

        assertCollation("test9", "NAME", "utf8");
        assertCollation("test9", "DESCRIPTION", "en-cs");

        logger.info("COLLATE preserved during CLONE correctly");
    }

    @Test
    public void testCollationWithIfNotExists() {
        logger.info("Testing COLLATE with IF NOT EXISTS");

        engine.execute("""
            CREATE TABLE IF NOT EXISTS test10 (
                id INTEGER,
                name VARCHAR COLLATE 'utf8'
            )
            """);

        assertCollation("test10", "NAME", "utf8");

        logger.info("COLLATE with IF NOT EXISTS works correctly");
    }

    @Test
    public void testCollationWithAllConstraints() {
        logger.info("Testing COLLATE with all constraint types");

        engine.execute("""
            CREATE TABLE test11 (
                id INTEGER PRIMARY KEY,
                name VARCHAR(200) NOT NULL UNIQUE COLLATE 'utf8' DEFAULT 'unknown' COMMENT 'User name'
            )
            """);

        assertCollation("test11", "NAME", "utf8");
        assertEquals("N", describeCell("test11", "NAME", "null?"));
        assertEquals("Y", describeCell("test11", "NAME", "unique key"));
        assertEquals("'unknown'", describeCell("test11", "NAME", "default"));
        assertEquals("User name", describeCell("test11", "NAME", "comment"));

        logger.info("COLLATE with all constraints works correctly");
    }

    @Test
    public void testNoCollationSpecified() {
        logger.info("Testing column without COLLATE");

        engine.execute("CREATE TABLE test12 (id INTEGER, name VARCHAR)");

        assertNoCollation("test12", "NAME");

        logger.info("Column without COLLATE has no collation");
    }

    @Test
    public void testCollationWithMultipleTypes() {
        logger.info("Testing COLLATE with different data types");

        engine.execute("""
            CREATE TABLE test13 (
                id INTEGER,
                varchar_col VARCHAR COLLATE 'utf8',
                text_col VARCHAR(1000) COLLATE 'en-cs',
                number_col NUMBER
            )
            """);

        assertCollation("test13", "VARCHAR_COL", "utf8");
        assertCollation("test13", "TEXT_COL", "en-cs");
        assertNoCollation("test13", "NUMBER_COL");

        logger.info("COLLATE with different data types works correctly");
    }

    @Test
    public void testCollationPreservedDuringAlterColumn() {
        logger.info("Testing COLLATE carried through ALTER COLUMN by restating it");

        engine.execute("""
            CREATE TABLE test14 (
                id INTEGER,
                name VARCHAR(100) COLLATE 'utf8'
            )
            """);

        assertCollation("test14", "NAME", "utf8");

        // A collated column's retype must RESTATE the collation (live-verified).
        engine.execute("ALTER TABLE test14 ALTER COLUMN name VARCHAR(200) COLLATE 'utf8'");

        assertCollation("test14", "NAME", "utf8");

        logger.info("COLLATE preserved during ALTER COLUMN correctly");
    }

    @Test
    public void testRetypeWithoutRestatingCollationIsRefused() {
        engine.execute("CREATE TABLE test17 (id INTEGER, name VARCHAR(100) COLLATE 'utf8')");

        // Dropping, changing or adding a collation in a retype is refused, and the message
        // QUOTES both types (live-verified).
        final RuntimeException dropped = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE test17 ALTER COLUMN name SET DATA TYPE VARCHAR(200)");
            }
        });
        assertTrue(dropped.getMessage().contains(
            "cannot change column NAME from type \"VARCHAR(100) COLLATE 'utf8'\" to \"VARCHAR(200)\""
            + " because they have incompatible collations."), dropped.getMessage());

        final RuntimeException changed = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE test17 ALTER COLUMN name SET DATA TYPE VARCHAR(300) COLLATE 'en-ci'");
            }
        });
        assertTrue(changed.getMessage().contains("because they have incompatible collations."),
            changed.getMessage());

        engine.execute("CREATE TABLE test18 (id INTEGER, name VARCHAR(100))");
        final RuntimeException added = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE test18 ALTER COLUMN name SET DATA TYPE VARCHAR(200) COLLATE 'utf8'");
            }
        });
        assertTrue(added.getMessage().contains(
            "cannot change column NAME from type \"VARCHAR(100)\" to \"VARCHAR(200) COLLATE 'utf8'\""
            + " because they have incompatible collations."), added.getMessage());
    }

    @Test
    public void testCollationWithClusterBy() {
        logger.info("Testing COLLATE with CLUSTER BY");

        engine.execute("""
            CREATE TABLE test15 (
                id INTEGER,
                name VARCHAR COLLATE 'utf8',
                date DATE
            ) CLUSTER BY (date)
            """);

        assertCollation("test15", "NAME", "utf8");
        final ResultSet tables = engine.executeQuery("SHOW TABLES LIKE 'test15'");
        assertEquals("LINEAR(date)",
            cell(tables, soleRowWhere(tables, "name", "TEST15"), "cluster_by"));

        logger.info("COLLATE with CLUSTER BY works correctly");
    }

    @Test
    public void testCollationWithTableComment() {
        logger.info("Testing COLLATE with table COMMENT");

        engine.execute("""
            CREATE TABLE test16 (
                id INTEGER,
                name VARCHAR COLLATE 'utf8'
            ) COMMENT = 'Test table'
            """);

        assertCollation("test16", "NAME", "utf8");
        final ResultSet tables = engine.executeQuery("SHOW TABLES LIKE 'test16'");
        assertEquals("Test table",
            cell(tables, soleRowWhere(tables, "name", "TEST16"), "comment"));

        logger.info("COLLATE with table COMMENT works correctly");
    }
}
