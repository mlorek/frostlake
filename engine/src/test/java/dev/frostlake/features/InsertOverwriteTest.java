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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for INSERT OVERWRITE statement
 */
public class InsertOverwriteTest {
    private static final Logger logger = LoggerFactory.getLogger(InsertOverwriteTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for INSERT OVERWRITE tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void insideATransactionOverwriteReplacesRowsInsertedByThatTransaction() {
        // The stage/rebuild pattern: fill a scratch table, then rebuild it in place from itself. The truncate
        // used to clear only the BASE store, while an explicit transaction's inserts are buffered in its write
        // set — so those rows survived and the OVERWRITE appended to them instead of replacing them.
        engine.execute("CREATE TABLE staged (id INTEGER, tag VARCHAR)");
        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT INTO staged VALUES (1, 'a'), (2, 'b'), (3, 'c')");
        engine.execute("INSERT OVERWRITE INTO staged (id, tag) SELECT id, tag FROM staged WHERE id = 3");

        final ResultSet inTxn = engine.executeQuery("SELECT id, tag FROM staged");
        assertEquals(1, inTxn.getRowCount());
        assertEquals("c", inTxn.getRows().get(0).getValue(1));

        engine.execute("COMMIT");
        final ResultSet committed = engine.executeQuery("SELECT id, tag FROM staged");
        assertEquals(1, committed.getRowCount());
        assertEquals("c", committed.getRows().get(0).getValue(1));
        logger.info("INSERT OVERWRITE replaces same-transaction rows");
    }

    @Test
    public void insideATransactionAnEmptySourceStillEmptiesTheTable() {
        // The staging-loader shape: when the rebuild SELECT filters everything out, the table must end up
        // EMPTY. It used to keep the transaction's staged rows, which then flowed on to the next statement.
        engine.execute("CREATE TABLE staged (id INTEGER, tag VARCHAR)");
        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT INTO staged VALUES (1, 'a'), (2, 'b')");
        engine.execute("INSERT OVERWRITE INTO staged (id, tag) SELECT id, tag FROM staged WHERE 1 = 0");

        assertEquals(0, engine.executeQuery("SELECT id, tag FROM staged").getRowCount());
        engine.execute("COMMIT");
        assertEquals(0, engine.executeQuery("SELECT id, tag FROM staged").getRowCount());
        logger.info("INSERT OVERWRITE with an empty source empties the table inside a transaction");
    }

    @Test
    public void insideATransactionOverwriteIsUndoneByRollback() {
        engine.execute("CREATE TABLE staged (id INTEGER, tag VARCHAR)");
        engine.execute("INSERT INTO staged VALUES (1, 'committed')");
        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT OVERWRITE INTO staged (id, tag) SELECT 9, 'replaced'");
        assertEquals("replaced", engine.executeQuery("SELECT tag FROM staged").getRows().get(0).getValue(0));
        engine.execute("ROLLBACK");

        final ResultSet afterRollback = engine.executeQuery("SELECT id, tag FROM staged");
        assertEquals(1, afterRollback.getRowCount());
        assertEquals("committed", afterRollback.getRows().get(0).getValue(1));
        logger.info("INSERT OVERWRITE is rolled back with its transaction");
    }

    @Test
    public void testInsertOverwriteWithValues() {
        logger.info("Testing INSERT OVERWRITE with VALUES");

        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'Alice'), (2, 'Bob')");

        ResultSet rs1 = engine.executeQuery("SELECT * FROM test_table");
        assertEquals(2, rs1.getRowCount());

        engine.execute("INSERT OVERWRITE INTO test_table VALUES (3, 'Charlie'), (4, 'Diana')");

        ResultSet rs2 = engine.executeQuery("SELECT * FROM test_table");
        assertEquals(2, rs2.getRowCount());
        assertEquals(3L, rs2.getRows().get(0).getValue(0));
        assertEquals("Charlie", rs2.getRows().get(0).getValue(1));
        assertEquals(4L, rs2.getRows().get(1).getValue(0));
        assertEquals("Diana", rs2.getRows().get(1).getValue(1));

        logger.info("INSERT OVERWRITE with VALUES works correctly");
    }

    @Test
    public void testInsertOverwriteWithSelect() {
        logger.info("Testing INSERT OVERWRITE with SELECT");

        engine.execute("CREATE TABLE source (id INTEGER, value VARCHAR)");
        engine.execute("INSERT INTO source VALUES (1, 'A'), (2, 'B'), (3, 'C')");

        engine.execute("CREATE TABLE target (id INTEGER, value VARCHAR)");
        engine.execute("INSERT INTO target VALUES (10, 'X'), (20, 'Y')");

        ResultSet rs1 = engine.executeQuery("SELECT * FROM target");
        assertEquals(2, rs1.getRowCount());

        engine.execute("INSERT OVERWRITE INTO target SELECT * FROM source");

        ResultSet rs2 = engine.executeQuery("SELECT * FROM target");
        assertEquals(3, rs2.getRowCount());
        assertEquals(1L, rs2.getRows().get(0).getValue(0));
        assertEquals("A", rs2.getRows().get(0).getValue(1));
        assertEquals(2L, rs2.getRows().get(1).getValue(0));
        assertEquals("B", rs2.getRows().get(1).getValue(1));
        assertEquals(3L, rs2.getRows().get(2).getValue(0));
        assertEquals("C", rs2.getRows().get(2).getValue(1));

        logger.info("INSERT OVERWRITE with SELECT works correctly");
    }

    @Test
    public void testInsertOverwriteEmptyTable() {
        logger.info("Testing INSERT OVERWRITE on empty table");

        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");

        ResultSet rs1 = engine.executeQuery("SELECT * FROM test_table");
        assertEquals(0, rs1.getRowCount());

        engine.execute("INSERT OVERWRITE INTO test_table VALUES (1, 'Alice')");

        ResultSet rs2 = engine.executeQuery("SELECT * FROM test_table");
        assertEquals(1, rs2.getRowCount());
        assertEquals(1L, rs2.getRows().get(0).getValue(0));
        assertEquals("Alice", rs2.getRows().get(0).getValue(1));

        logger.info("INSERT OVERWRITE on empty table works correctly");
    }

    @Test
    public void testInsertOverwriteWithColumnList() {
        logger.info("Testing INSERT OVERWRITE with column list");

        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR, value INTEGER)");
        engine.execute("INSERT INTO test_table VALUES (1, 'Alice', 100), (2, 'Bob', 200)");

        ResultSet rs1 = engine.executeQuery("SELECT * FROM test_table");
        assertEquals(2, rs1.getRowCount());

        engine.execute("INSERT OVERWRITE INTO test_table (id, name) VALUES (3, 'Charlie'), (4, 'Diana')");

        ResultSet rs2 = engine.executeQuery("SELECT * FROM test_table");
        assertEquals(2, rs2.getRowCount());
        assertEquals(3L, rs2.getRows().get(0).getValue(0));
        assertEquals("Charlie", rs2.getRows().get(0).getValue(1));

        logger.info("INSERT OVERWRITE with column list works correctly");
    }

    @Test
    public void testInsertOverwriteWithSelectAndWhere() {
        logger.info("Testing INSERT OVERWRITE with SELECT and WHERE");

        engine.execute("CREATE TABLE source (id INTEGER, category VARCHAR, value INTEGER)");
        engine.execute("INSERT INTO source VALUES (1, 'A', 100), (2, 'B', 200), (3, 'A', 300)");

        engine.execute("CREATE TABLE target (id INTEGER, category VARCHAR, value INTEGER)");
        engine.execute("INSERT INTO target VALUES (10, 'X', 1000)");

        engine.execute("INSERT OVERWRITE INTO target SELECT * FROM source WHERE category = 'A'");

        ResultSet rs = engine.executeQuery("SELECT * FROM target");
        assertEquals(2, rs.getRowCount());
        assertEquals(1L, rs.getRows().get(0).getValue(0));
        assertEquals("A", rs.getRows().get(0).getValue(1));
        assertEquals(3L, rs.getRows().get(1).getValue(0));
        assertEquals("A", rs.getRows().get(1).getValue(1));

        logger.info("INSERT OVERWRITE with SELECT and WHERE works correctly");
    }

    @Test
    public void testInsertOverwriteMultipleTimes() {
        logger.info("Testing multiple INSERT OVERWRITE operations");

        engine.execute("CREATE TABLE test_table (id INTEGER, value VARCHAR)");

        engine.execute("INSERT OVERWRITE INTO test_table VALUES (1, 'First')");
        ResultSet rs1 = engine.executeQuery("SELECT * FROM test_table");
        assertEquals(1, rs1.getRowCount());
        assertEquals("First", rs1.getRows().get(0).getValue(1));

        engine.execute("INSERT OVERWRITE INTO test_table VALUES (2, 'Second')");
        ResultSet rs2 = engine.executeQuery("SELECT * FROM test_table");
        assertEquals(1, rs2.getRowCount());
        assertEquals("Second", rs2.getRows().get(0).getValue(1));

        engine.execute("INSERT OVERWRITE INTO test_table VALUES (3, 'Third')");
        ResultSet rs3 = engine.executeQuery("SELECT * FROM test_table");
        assertEquals(1, rs3.getRowCount());
        assertEquals("Third", rs3.getRows().get(0).getValue(1));

        logger.info("Multiple INSERT OVERWRITE operations work correctly");
    }

    @Test
    public void testInsertOverwriteVsRegularInsert() {
        logger.info("Testing INSERT OVERWRITE vs regular INSERT");

        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'Alice')");

        ResultSet rs1 = engine.executeQuery("SELECT * FROM test_table");
        assertEquals(1, rs1.getRowCount());

        engine.execute("INSERT INTO test_table VALUES (2, 'Bob')");

        ResultSet rs2 = engine.executeQuery("SELECT * FROM test_table");
        assertEquals(2, rs2.getRowCount());

        engine.execute("INSERT OVERWRITE INTO test_table VALUES (3, 'Charlie')");

        ResultSet rs3 = engine.executeQuery("SELECT * FROM test_table");
        assertEquals(1, rs3.getRowCount());
        assertEquals(3L, rs3.getRows().get(0).getValue(0));
        assertEquals("Charlie", rs3.getRows().get(0).getValue(1));

        logger.info("INSERT OVERWRITE vs regular INSERT works correctly");
    }

    @Test
    public void testInsertOverwriteWithSelectOrderBy() {
        logger.info("Testing INSERT OVERWRITE with SELECT ORDER BY");

        engine.execute("CREATE TABLE source (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO source VALUES (3, 'C'), (1, 'A'), (2, 'B')");

        engine.execute("CREATE TABLE target (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO target VALUES (99, 'Z')");

        engine.execute("INSERT OVERWRITE INTO target SELECT * FROM source ORDER BY id");

        ResultSet rs = engine.executeQuery("SELECT * FROM target");
        assertEquals(3, rs.getRowCount());
        assertEquals(1L, rs.getRows().get(0).getValue(0));
        assertEquals("A", rs.getRows().get(0).getValue(1));

        logger.info("INSERT OVERWRITE with SELECT ORDER BY works correctly");
    }

    @Test
    public void testInsertOverwriteWithCTE() {
        logger.info("Testing INSERT OVERWRITE with CTE");

        engine.execute("CREATE TABLE target (id INTEGER, value INTEGER)");
        engine.execute("INSERT INTO target VALUES (1, 10), (2, 20)");

        ResultSet rs1 = engine.executeQuery("SELECT * FROM target");
        assertEquals(2, rs1.getRowCount());

        engine.execute("""
            WITH cte AS (
                SELECT 100 AS id, 200 AS value
            )
            INSERT OVERWRITE INTO target SELECT * FROM cte
            """);

        ResultSet rs2 = engine.executeQuery("SELECT * FROM target");
        assertEquals(1, rs2.getRowCount());
        assertEquals(100L, rs2.getRows().get(0).getValue(0));
        assertEquals(200L, rs2.getRows().get(0).getValue(1));

        logger.info("INSERT OVERWRITE with CTE works correctly");
    }

    @Test
    public void testInsertOverwriteWithQualifiedTableName() {
        logger.info("Testing INSERT OVERWRITE with qualified table name");

        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("CREATE TABLE test_schema.test_table (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO test_schema.test_table VALUES (1, 'Old')");

        ResultSet rs1 = engine.executeQuery("SELECT * FROM test_schema.test_table");
        assertEquals(1, rs1.getRowCount());

        engine.execute("INSERT OVERWRITE INTO test_schema.test_table VALUES (2, 'New')");

        ResultSet rs2 = engine.executeQuery("SELECT * FROM test_schema.test_table");
        assertEquals(1, rs2.getRowCount());
        assertEquals(2L, rs2.getRows().get(0).getValue(0));
        assertEquals("New", rs2.getRows().get(0).getValue(1));

        logger.info("INSERT OVERWRITE with qualified table name works correctly");
    }

    @Test
    public void testInsertOverwriteWithMultipleValues() {
        logger.info("Testing INSERT OVERWRITE with multiple value tuples");

        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'A')");

        engine.execute("INSERT OVERWRITE INTO test_table VALUES (2, 'B'), (3, 'C'), (4, 'D'), (5, 'E')");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_table");
        assertEquals(4, rs.getRowCount());

        logger.info("INSERT OVERWRITE with multiple value tuples works correctly");
    }

    @Test
    public void testInsertOverwriteWithSelectFromValues() {
        logger.info("Testing INSERT OVERWRITE with SELECT FROM VALUES");

        engine.execute("CREATE TABLE target (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO target VALUES (1, 'Old1'), (2, 'Old2')");

        ResultSet rs1 = engine.executeQuery("SELECT * FROM target");
        assertEquals(2, rs1.getRowCount());

        engine.execute("""
            INSERT OVERWRITE INTO target
            SELECT $1, $2 FROM VALUES(10, 'New1'), (20, 'New2')
            """);

        ResultSet rs2 = engine.executeQuery("SELECT * FROM target");
        assertEquals(2, rs2.getRowCount());
        assertEquals(10L, rs2.getRows().get(0).getValue(0));
        assertEquals("New1", rs2.getRows().get(0).getValue(1));

        logger.info("INSERT OVERWRITE with SELECT FROM VALUES works correctly");
    }
}
