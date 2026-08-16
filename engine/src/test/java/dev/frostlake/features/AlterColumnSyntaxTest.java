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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ALTER COLUMN retype spellings — {@code ALTER COLUMN col [ [ SET DATA ] TYPE ] datatype} —
 * asserted through the SQL surface: the {@code DESCRIBE TABLE} type cell, matched by its family
 * prefix, tolerant of the parameter suffixes the engines spell differently. Every retype here is
 * one Snowflake permits (a widening or a restatement), so the checks run against whichever engine
 * executed the DDL, embedded or live.
 */
public class AlterColumnSyntaxTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(AlterColumnSyntaxTest.class);

    /** Asserts one column's DESCRIBE type cell starts with the expected family spelling. */
    private void assertColumnTypeStartsWith(final String table, final String column, final String prefix) {
        final String type = describeCell(table, column, "type");
        assertTrue(type.startsWith(prefix),
            column + " should be a " + prefix + " but its type reads: " + type);
    }

    @Test
    public void testAlterColumnWithSetDataType() {
        logger.info("Testing ALTER COLUMN with SET DATA TYPE");

        engine.execute("CREATE TABLE test1 (id INTEGER, col1 VARCHAR(10))");
        engine.execute("ALTER TABLE test1 ALTER COLUMN col1 SET DATA TYPE VARCHAR");

        assertColumnTypeStartsWith("test1", "COL1", "VARCHAR");

        logger.info("ALTER COLUMN with SET DATA TYPE works correctly");
    }

    @Test
    public void testAlterColumnWithTypeOnly() {
        logger.info("Testing ALTER COLUMN with TYPE keyword");

        engine.execute("CREATE TABLE test2 (id INTEGER, col1 VARCHAR(10))");
        engine.execute("ALTER TABLE test2 ALTER COLUMN col1 TYPE VARCHAR");

        assertColumnTypeStartsWith("test2", "COL1", "VARCHAR");

        logger.info("ALTER COLUMN with TYPE works correctly");
    }

    @Test
    public void testAlterColumnWithoutTypeKeyword() {
        logger.info("Testing ALTER COLUMN without TYPE keyword (minimal form)");

        engine.execute("CREATE TABLE test3 (id INTEGER, col1 VARCHAR(10))");
        engine.execute("ALTER TABLE test3 ALTER COLUMN col1 VARCHAR");

        assertColumnTypeStartsWith("test3", "COL1", "VARCHAR");

        logger.info("ALTER COLUMN without TYPE keyword works correctly");
    }

    @Test
    public void testAlterColumnWithSetDataTypeAndParameters() {
        logger.info("Testing ALTER COLUMN with SET DATA TYPE and type parameters");

        engine.execute("CREATE TABLE test4 (id INTEGER, col1 VARCHAR(10))");
        engine.execute("ALTER TABLE test4 ALTER COLUMN col1 SET DATA TYPE VARCHAR(100)");

        assertColumnTypeStartsWith("test4", "COL1", "VARCHAR");

        logger.info("ALTER COLUMN with SET DATA TYPE and parameters works correctly");
    }

    @Test
    public void testAlterColumnWithTypeAndParameters() {
        logger.info("Testing ALTER COLUMN with TYPE and type parameters");

        engine.execute("CREATE TABLE test5 (id INTEGER, col1 DECIMAL(8, 2))");
        engine.execute("ALTER TABLE test5 ALTER COLUMN col1 TYPE DECIMAL(10, 2)");

        assertColumnTypeStartsWith("test5", "COL1", "NUMBER");

        logger.info("ALTER COLUMN with TYPE and parameters works correctly");
    }

    @Test
    public void testAlterColumnWithoutTypeKeywordAndParameters() {
        logger.info("Testing ALTER COLUMN without TYPE keyword and type parameters");

        engine.execute("CREATE TABLE test6 (id INTEGER, col1 VARCHAR(100))");
        engine.execute("ALTER TABLE test6 ALTER COLUMN col1 VARCHAR(500)");

        assertColumnTypeStartsWith("test6", "COL1", "VARCHAR");

        logger.info("ALTER COLUMN without TYPE keyword and parameters works correctly");
    }

    @Test
    public void testMultipleColumnsWithDifferentSyntax() {
        logger.info("Testing multiple columns altered with different syntax variations");

        engine.execute("CREATE TABLE test7 (id INTEGER, col1 VARCHAR(10), col2 DATE, col3 TIMESTAMP)");

        engine.execute("ALTER TABLE test7 ALTER COLUMN col1 SET DATA TYPE VARCHAR");
        engine.execute("ALTER TABLE test7 ALTER COLUMN col2 TYPE DATE");
        engine.execute("ALTER TABLE test7 ALTER COLUMN col3 TIMESTAMP");

        assertColumnTypeStartsWith("test7", "COL1", "VARCHAR");
        assertColumnTypeStartsWith("test7", "COL2", "DATE");
        assertColumnTypeStartsWith("test7", "COL3", "TIMESTAMP");

        logger.info("Multiple columns with different syntax variations work correctly");
    }

    @Test
    public void testAlterColumnWithIfExists() {
        logger.info("Testing ALTER COLUMN with IF EXISTS");

        engine.execute("CREATE TABLE test8 (id INTEGER, col1 VARCHAR(10))");
        engine.execute("ALTER TABLE IF EXISTS test8 ALTER COLUMN col1 TYPE VARCHAR");

        assertColumnTypeStartsWith("test8", "COL1", "VARCHAR");

        logger.info("ALTER COLUMN with IF EXISTS works correctly");
    }

    @Test
    public void testAlterColumnWithQualifiedTableName() {
        logger.info("Testing ALTER COLUMN with schema-qualified table name");

        engine.execute("CREATE SCHEMA alter_syntax_schema");
        engine.execute("CREATE TABLE alter_syntax_schema.test9 (id INTEGER, col1 VARCHAR(10))");

        engine.execute("ALTER TABLE alter_syntax_schema.test9 ALTER COLUMN col1 TYPE VARCHAR");

        assertColumnTypeStartsWith("alter_syntax_schema.test9", "COL1", "VARCHAR");

        logger.info("ALTER COLUMN with qualified name works correctly");
    }

    @Test
    public void testAlterColumnWithComplexDataType() {
        logger.info("Testing ALTER COLUMN restating a parameterized timestamp subtype");

        engine.execute("CREATE TABLE test10 (id INTEGER, col1 TIMESTAMP_LTZ)");
        engine.execute("ALTER TABLE test10 ALTER COLUMN col1 TYPE TIMESTAMP_LTZ(9)");

        assertColumnTypeStartsWith("test10", "COL1", "TIMESTAMP_LTZ");

        logger.info("ALTER COLUMN with complex data type works correctly");
    }

    @Test
    public void testAllSyntaxVariationsProduceSameResult() {
        logger.info("Testing that all syntax variations produce the same result");

        engine.execute("CREATE TABLE test11 (id INTEGER, col1 VARCHAR(10), col2 VARCHAR(10), col3 VARCHAR(10))");

        engine.execute("ALTER TABLE test11 ALTER COLUMN col1 SET DATA TYPE VARCHAR");
        engine.execute("ALTER TABLE test11 ALTER COLUMN col2 TYPE VARCHAR");
        engine.execute("ALTER TABLE test11 ALTER COLUMN col3 VARCHAR");

        assertColumnTypeStartsWith("test11", "COL1", "VARCHAR");
        assertColumnTypeStartsWith("test11", "COL2", "VARCHAR");
        assertColumnTypeStartsWith("test11", "COL3", "VARCHAR");

        logger.info("All syntax variations produce the same result");
    }

    @Test
    public void testAlterColumnWithoutTypeKeywordWithDecimal() {
        logger.info("Testing ALTER COLUMN without TYPE keyword with DECIMAL");

        engine.execute("CREATE TABLE test12 (id INTEGER, col1 DECIMAL(10, 3))");
        engine.execute("ALTER TABLE test12 ALTER COLUMN col1 DECIMAL(15, 3)");

        assertColumnTypeStartsWith("test12", "COL1", "NUMBER");

        logger.info("ALTER COLUMN without TYPE keyword with DECIMAL works correctly");
    }

    @Test
    public void testAlterColumnSetDataTypeWithDecimal() {
        logger.info("Testing ALTER COLUMN SET DATA TYPE with DECIMAL");

        engine.execute("CREATE TABLE test13 (id INTEGER, col1 DECIMAL(12, 5))");
        engine.execute("ALTER TABLE test13 ALTER COLUMN col1 SET DATA TYPE DECIMAL(20, 5)");

        assertColumnTypeStartsWith("test13", "COL1", "NUMBER");

        logger.info("ALTER COLUMN SET DATA TYPE with DECIMAL works correctly");
    }
}
