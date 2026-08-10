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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The COLLATE clause's position among column constraints, asserted through the SQL surface: the
 * {@code DESCRIBE TABLE} type cell carries {@code COLLATE '<spec>'} and the constraint cells carry
 * their marks, so every accepted position runs against whichever engine executed the DDL, embedded
 * or live.
 *
 * <p>COLLATE moves freely around NOT NULL, UNIQUE, PRIMARY KEY and COMMENT, with ONE exception
 * (live-verified): it may not FOLLOW a DEFAULT — the default was already typed without the
 * collation, so Snowflake answers "Default value data type does not match data type for column X".
 * COLLATE before the DEFAULT is fine.
 */
public class CollateConstraintOrderTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(CollateConstraintOrderTest.class);

    /** Asserts the column's DESCRIBE type cell carries exactly this collation spec. */
    private void assertCollation(final String table, final String column, final String spec) {
        final String type = describeCell(table, column, "type");
        assertTrue(type.toUpperCase().contains("COLLATE '" + spec.toUpperCase() + "'"),
            column + " should be collated '" + spec + "' but its type reads: " + type);
    }

    @Test
    public void testCollateBeforeNotNull() {
        logger.info("Testing COLLATE before NOT NULL");

        engine.execute("CREATE TABLE test1 (id INTEGER, name VARCHAR COLLATE 'utf8' NOT NULL)");

        assertCollation("test1", "NAME", "utf8");
        assertEquals("N", describeCell("test1", "NAME", "null?"));

        logger.info("COLLATE before NOT NULL works correctly");
    }

    @Test
    public void testCollateAfterNotNull() {
        logger.info("Testing COLLATE after NOT NULL");

        engine.execute("CREATE TABLE test2 (id INTEGER, name VARCHAR NOT NULL COLLATE 'utf8')");

        assertCollation("test2", "NAME", "utf8");
        assertEquals("N", describeCell("test2", "NAME", "null?"));

        logger.info("COLLATE after NOT NULL works correctly");
    }

    @Test
    public void testCollateBeforeUnique() {
        logger.info("Testing COLLATE before UNIQUE");

        engine.execute("CREATE TABLE test3 (id INTEGER, email VARCHAR COLLATE 'en-cs' UNIQUE)");

        assertCollation("test3", "EMAIL", "en-cs");
        assertEquals("Y", describeCell("test3", "EMAIL", "unique key"));

        logger.info("COLLATE before UNIQUE works correctly");
    }

    @Test
    public void testCollateAfterUnique() {
        logger.info("Testing COLLATE after UNIQUE");

        engine.execute("CREATE TABLE test4 (id INTEGER, email VARCHAR UNIQUE COLLATE 'en-cs')");

        assertCollation("test4", "EMAIL", "en-cs");
        assertEquals("Y", describeCell("test4", "EMAIL", "unique key"));

        logger.info("COLLATE after UNIQUE works correctly");
    }

    @Test
    public void testCollateBeforeDefault() {
        logger.info("Testing COLLATE before DEFAULT");

        engine.execute("CREATE TABLE test5 (id INTEGER, status VARCHAR COLLATE 'utf8' DEFAULT 'active')");

        assertCollation("test5", "STATUS", "utf8");
        assertEquals("'active'", describeCell("test5", "STATUS", "default"));

        logger.info("COLLATE before DEFAULT works correctly");
    }

    @Test
    public void testCollateAfterDefaultIsRefused() {
        logger.info("Testing COLLATE after DEFAULT is refused");

        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE test6 (id INTEGER, status VARCHAR DEFAULT 'active' COLLATE 'utf8')");
            }
        });
        assertTrue(e.getMessage().contains(
            "Default value data type does not match data type for column STATUS"), e.getMessage());

        logger.info("COLLATE after DEFAULT is refused");
    }

    @Test
    public void testCollateBetweenConstraints() {
        logger.info("Testing COLLATE between NOT NULL and UNIQUE");

        engine.execute("CREATE TABLE test7 (id INTEGER, name VARCHAR NOT NULL COLLATE 'utf8' UNIQUE)");

        assertCollation("test7", "NAME", "utf8");
        assertEquals("N", describeCell("test7", "NAME", "null?"));
        assertEquals("Y", describeCell("test7", "NAME", "unique key"));

        logger.info("COLLATE between constraints works correctly");
    }

    @Test
    public void testCollateFirstAmongMultiple() {
        logger.info("Testing COLLATE first among multiple constraints");

        engine.execute("CREATE TABLE test8 (id INTEGER, name VARCHAR COLLATE 'utf8' NOT NULL UNIQUE DEFAULT 'unknown')");

        assertCollation("test8", "NAME", "utf8");
        assertEquals("N", describeCell("test8", "NAME", "null?"));
        assertEquals("Y", describeCell("test8", "NAME", "unique key"));
        assertEquals("'unknown'", describeCell("test8", "NAME", "default"));

        logger.info("COLLATE first among multiple constraints works correctly");
    }

    @Test
    public void testCollateLastAmongMultiple() {
        logger.info("Testing COLLATE last among constraints that carry no DEFAULT");

        engine.execute("CREATE TABLE test9 (id INTEGER, name VARCHAR NOT NULL UNIQUE COLLATE 'utf8')");

        assertCollation("test9", "NAME", "utf8");
        assertEquals("N", describeCell("test9", "NAME", "null?"));
        assertEquals("Y", describeCell("test9", "NAME", "unique key"));

        logger.info("COLLATE last among multiple constraints works correctly");
    }

    @Test
    public void testCollateMiddleAmongMultiple() {
        logger.info("Testing COLLATE in middle among multiple constraints");

        engine.execute("CREATE TABLE test10 (id INTEGER, name VARCHAR NOT NULL COLLATE 'utf8' DEFAULT 'test' UNIQUE)");

        assertCollation("test10", "NAME", "utf8");
        assertEquals("N", describeCell("test10", "NAME", "null?"));
        assertEquals("Y", describeCell("test10", "NAME", "unique key"));
        assertEquals("'test'", describeCell("test10", "NAME", "default"));

        logger.info("COLLATE in middle among multiple constraints works correctly");
    }

    @Test
    public void testCollateWithPrimaryKey() {
        logger.info("Testing COLLATE with PRIMARY KEY");

        engine.execute("CREATE TABLE test11 (id INTEGER, name VARCHAR PRIMARY KEY COLLATE 'utf8')");

        assertCollation("test11", "NAME", "utf8");
        assertEquals("Y", describeCell("test11", "NAME", "primary key"));

        logger.info("COLLATE with PRIMARY KEY works correctly");
    }

    @Test
    public void testCollateBeforePrimaryKey() {
        logger.info("Testing COLLATE before PRIMARY KEY");

        engine.execute("CREATE TABLE test12 (id INTEGER, name VARCHAR COLLATE 'utf8' PRIMARY KEY)");

        assertCollation("test12", "NAME", "utf8");
        assertEquals("Y", describeCell("test12", "NAME", "primary key"));

        logger.info("COLLATE before PRIMARY KEY works correctly");
    }

    @Test
    public void testMultipleColumnsWithCollateInDifferentPositions() {
        logger.info("Testing multiple columns with COLLATE in different positions");

        engine.execute("""
            CREATE TABLE test13 (
                id INTEGER,
                col1 VARCHAR COLLATE 'utf8' NOT NULL,
                col2 VARCHAR NOT NULL COLLATE 'en-cs',
                col3 VARCHAR UNIQUE COLLATE 'en-ci',
                col4 VARCHAR COLLATE 'utf8' DEFAULT 'test'
            )
            """);

        assertCollation("test13", "COL1", "utf8");
        assertCollation("test13", "COL2", "en-cs");
        assertCollation("test13", "COL3", "en-ci");
        assertCollation("test13", "COL4", "utf8");

        logger.info("Multiple columns with COLLATE in different positions work correctly");
    }

    @Test
    public void testCollateWithComment() {
        logger.info("Testing COLLATE before COMMENT");

        engine.execute("CREATE TABLE test14 (id INTEGER, name VARCHAR COLLATE 'utf8' COMMENT 'User name')");

        assertCollation("test14", "NAME", "utf8");
        assertEquals("User name", describeCell("test14", "NAME", "comment"));

        logger.info("COLLATE with COMMENT works correctly");
    }

    @Test
    public void testCollateAfterAllConstraintsBeforeComment() {
        logger.info("Testing COLLATE after the constraints and before COMMENT");

        engine.execute("""
            CREATE TABLE test15 (
                id INTEGER,
                name VARCHAR NOT NULL UNIQUE COLLATE 'utf8' DEFAULT 'test' COMMENT 'Test column'
            )
            """);

        assertCollation("test15", "NAME", "utf8");
        assertEquals("Test column", describeCell("test15", "NAME", "comment"));
        assertEquals("N", describeCell("test15", "NAME", "null?"));
        assertEquals("Y", describeCell("test15", "NAME", "unique key"));

        logger.info("COLLATE after all constraints before COMMENT works correctly");
    }

    @Test
    public void testCollateFlexibilityAllPositions() {
        logger.info("Testing COLLATE flexibility with all constraint positions");

        engine.execute("""
            CREATE TABLE test16 (
                col1 VARCHAR COLLATE 'utf8',
                col2 VARCHAR NOT NULL COLLATE 'utf8',
                col3 VARCHAR COLLATE 'utf8' NOT NULL,
                col4 VARCHAR UNIQUE COLLATE 'utf8',
                col5 VARCHAR COLLATE 'utf8' UNIQUE,
                col6 VARCHAR COLLATE 'utf8' DEFAULT 'x',
                col7 VARCHAR COLLATE 'utf8' COMMENT 'c'
            )
            """);

        for (int i = 1; i <= 7; i++) {
            final String colName = "COL" + i;
            assertNotNull(describeCell("test16", colName, "type"));
            assertCollation("test16", colName, "utf8");
        }

        logger.info("COLLATE flexibility with all constraint positions works correctly");
    }
}
