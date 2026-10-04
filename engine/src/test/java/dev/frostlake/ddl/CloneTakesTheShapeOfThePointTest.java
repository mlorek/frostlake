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

package dev.frostlake.ddl;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CLONE ... AT | BEFORE takes the source's columns as they stood at the point (documented: a clone inherits "the name
 * and structure of the source object current at the time the statement runs, or at a specified time/point in the
 * past"), where a time-travel SELECT reads the same point through the table's CURRENT columns (live-verified). The
 * clone copied the rows of the point into the columns of now: a column dropped since was gone with its values, one
 * added since was there, and a rename, a retype or a comment showed as it is now.
 */
public class CloneTakesTheShapeOfThePointTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE shape_db");
        engine.execute("USE SCHEMA shape_db.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** A point between two statements: the clock is read after a pause, and the next statement waits a little. */
    private void markPoint(final String variable) throws InterruptedException {
        Thread.sleep(15);
        engine.execute("SET " + variable + " = CURRENT_TIMESTAMP()");
        Thread.sleep(15);
    }

    /** Each column as DESCRIBE TABLE spells it: name, type, null?, default and comment. */
    private String describe(final String table) {
        final ResultSet described = engine.executeQuery("DESCRIBE TABLE " + table);
        final StringBuilder out = new StringBuilder();
        while (described.next()) {
            out.append(out.length() > 0 ? "; " : "").append(described.getValue("name")).append(' ')
                .append(described.getValue("type")).append(' ').append(described.getValue("null?")).append(' ')
                .append(described.getValue("default")).append(' ').append(described.getValue("comment"));
        }
        return out.toString();
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? "; " : "").append(row.getValues());
        }
        return out.toString();
    }

    private String positions(final String table) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT column_name, ordinal_position FROM information_schema.columns"
                + " WHERE table_name = '" + table + "' ORDER BY ordinal_position").getRows()) {
            out.append(out.length() > 0 ? "; " : "").append(row.getValue(0)).append('=').append(row.getValue(1));
        }
        return out.toString();
    }

    private void createAndAlter() throws InterruptedException {
        engine.execute("CREATE TABLE t (a INT, b VARCHAR(5) NOT NULL DEFAULT 'x' COMMENT 'bee', c INT)");
        engine.execute("INSERT INTO t VALUES (1, 'one', 10), (2, 'two', 20)");
        markPoint("before_drop");
        engine.execute("ALTER TABLE t DROP COLUMN c");
        markPoint("before_add");
        engine.execute("ALTER TABLE t ADD COLUMN d INT DEFAULT 7");
        engine.execute("ALTER TABLE t RENAME COLUMN b TO bb");
        engine.execute("ALTER TABLE t ALTER COLUMN bb SET DATA TYPE VARCHAR(10)");
        engine.execute("ALTER TABLE t ALTER COLUMN bb DROP NOT NULL");
        engine.execute("COMMENT ON COLUMN t.bb IS 'renamed'");
        markPoint("after_all");
        engine.execute("INSERT INTO t (a, bb) VALUES (3, 'three')");
    }

    @Test
    public void aCloneBeforeADropKeepsTheDroppedColumnAndItsValues() throws InterruptedException {
        createAndAlter();
        engine.execute("CREATE TABLE before_drop CLONE t AT(TIMESTAMP => $before_drop)");

        assertEquals("A NUMBER(38,0) Y null null; B VARCHAR(5) N 'x' bee; C NUMBER(38,0) Y null null",
            describe("before_drop"));
        assertEquals("[1, one, 10]; [2, two, 20]", rows("SELECT * FROM before_drop ORDER BY a"));
    }

    @Test
    public void aCloneBeforeAnAddLeavesTheColumnOutAndTakesTheOldNamesTypesAndComments()
            throws InterruptedException {
        createAndAlter();
        engine.execute("CREATE TABLE before_add CLONE t AT(TIMESTAMP => $before_add)");
        engine.execute("CREATE TABLE after_all CLONE t AT(TIMESTAMP => $after_all)");

        assertEquals("A NUMBER(38,0) Y null null; B VARCHAR(5) N 'x' bee", describe("before_add"));
        assertEquals("[1, one]; [2, two]", rows("SELECT * FROM before_add ORDER BY a"));
        assertEquals("A NUMBER(38,0) Y null null; BB VARCHAR(10) Y 'x' renamed; D NUMBER(38,0) Y 7 null",
            describe("after_all"));
        assertEquals("[1, one, 7]; [2, two, 7]", rows("SELECT * FROM after_all ORDER BY a"));
    }

    @Test
    public void aSelectAtThePointStillReadsTheCurrentColumns() throws InterruptedException {
        createAndAlter();

        assertEquals("[1, one, 7]; [2, two, 7]", rows("SELECT * FROM t AT(TIMESTAMP => $before_drop) ORDER BY a"));
        assertEquals("[1, one, 7]; [2, two, 7]; [3, three, 7]", rows("SELECT * FROM t ORDER BY a"));
    }

    @Test
    public void statementPointsTakeTheShapeOfTheirStatement() {
        engine.execute("CREATE TABLE s (id INT, name VARCHAR(8), gone INT)");
        engine.execute("INSERT INTO s VALUES (1, 'a', 100), (2, 'b', 200)");
        engine.execute("SET q1 = LAST_QUERY_ID()");
        engine.execute("ALTER TABLE s DROP COLUMN gone");
        engine.execute("ALTER TABLE s RENAME COLUMN name TO label");
        engine.execute("ALTER TABLE s ADD COLUMN extra INT");
        engine.execute("INSERT INTO s VALUES (3, 'c', 9)");
        engine.execute("SET q2 = LAST_QUERY_ID()");

        engine.execute("CREATE TABLE at_q1 CLONE s AT(STATEMENT => $q1)");
        engine.execute("CREATE TABLE before_q2 CLONE s BEFORE(STATEMENT => $q2)");

        assertEquals("ID NUMBER(38,0) Y null null; NAME VARCHAR(8) Y null null; GONE NUMBER(38,0) Y null null",
            describe("at_q1"));
        assertEquals("[1, a, 100]; [2, b, 200]", rows("SELECT * FROM at_q1 ORDER BY id"));
        assertEquals("ID NUMBER(38,0) Y null null; LABEL VARCHAR(8) Y null null; EXTRA NUMBER(38,0) Y null null",
            describe("before_q2"));
        assertEquals("[1, a, null]; [2, b, null]", rows("SELECT * FROM before_q2 ORDER BY id"));
    }

    @Test
    public void aCloneOfAnEmptyTableTakesItsShapeBeforeAnAdd() throws InterruptedException {
        engine.execute("CREATE TABLE e (a INT)");
        markPoint("before_add");
        engine.execute("ALTER TABLE e ADD COLUMN b INT");
        engine.execute("INSERT INTO e VALUES (1, 2)");

        engine.execute("CREATE TABLE e_then CLONE e AT(TIMESTAMP => $before_add)");

        assertEquals("A NUMBER(38,0) Y null null", describe("e_then"));
        assertEquals("", rows("SELECT * FROM e_then"));
    }

    @Test
    public void constraintsAddedAfterThePointStayOutAndTheNextColumnFollowsThePositionsOfThen() {
        engine.execute("CREATE TABLE k (id INT, extra INT)");
        engine.execute("INSERT INTO k VALUES (1, 1)");
        engine.execute("SET q1 = LAST_QUERY_ID()");
        engine.execute("ALTER TABLE k ADD COLUMN later INT");
        engine.execute("ALTER TABLE k ADD CONSTRAINT uq_later UNIQUE (later)");
        engine.execute("ALTER TABLE k ADD PRIMARY KEY (id)");

        engine.execute("CREATE TABLE k_then CLONE k AT(STATEMENT => $q1)");

        assertEquals("ID NUMBER(38,0) Y null null; EXTRA NUMBER(38,0) Y null null", describe("k_then"));
        assertEquals(0, engine.executeQuery("SHOW PRIMARY KEYS IN TABLE k_then").getRows().size());
        assertEquals(0, engine.executeQuery("SHOW UNIQUE KEYS IN TABLE k_then").getRows().size());
        engine.execute("ALTER TABLE k_then ADD COLUMN next_one INT");
        assertEquals("ID=1; EXTRA=2; NEXT_ONE=3", positions("K_THEN"));
        engine.execute("INSERT INTO k_then VALUES (2, 2, 2)");
        assertEquals("[1, 1, null]; [2, 2, 2]", rows("SELECT * FROM k_then ORDER BY id"));
    }

    @Test
    public void aColumnThatStillStandsKeepsTheMaskingPolicyItHasNow() {
        engine.execute("CREATE MASKING POLICY hide AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("CREATE TABLE m (id INT, name VARCHAR(8))");
        engine.execute("INSERT INTO m VALUES (1, 'secret')");
        engine.execute("SET q1 = LAST_QUERY_ID()");
        engine.execute("ALTER TABLE m RENAME COLUMN name TO label");
        engine.execute("ALTER TABLE m MODIFY COLUMN label SET MASKING POLICY hide");

        engine.execute("CREATE TABLE m_then CLONE m AT(STATEMENT => $q1)");

        assertEquals("ID NUMBER(38,0) Y null null; NAME VARCHAR(8) Y null null", describe("m_then"));
        assertEquals("[1, ***]", rows("SELECT * FROM m_then"));
    }

    @Test
    public void schemaAndDatabaseClonesTakeEachTablesShapeAsOfThePoint() {
        engine.execute("CREATE SCHEMA src");
        engine.execute("CREATE TABLE src.t (id INT, gone INT)");
        engine.execute("INSERT INTO src.t VALUES (1, 100)");
        engine.execute("SET q1 = LAST_QUERY_ID()");
        engine.execute("ALTER TABLE src.t DROP COLUMN gone");
        engine.execute("ALTER TABLE src.t ADD COLUMN extra INT");

        engine.execute("CREATE SCHEMA shape_db.src_then CLONE shape_db.src AT(STATEMENT => $q1)");
        engine.execute("CREATE DATABASE shape_db_then CLONE shape_db AT(STATEMENT => $q1)");
        engine.execute("CREATE SCHEMA shape_db.src_now CLONE shape_db.src");

        assertEquals("ID NUMBER(38,0) Y null null; GONE NUMBER(38,0) Y null null", describe("shape_db.src_then.t"));
        assertEquals("[1, 100]", rows("SELECT * FROM shape_db.src_then.t"));
        assertEquals("ID NUMBER(38,0) Y null null; GONE NUMBER(38,0) Y null null", describe("shape_db_then.src.t"));
        assertEquals("[1, 100]", rows("SELECT * FROM shape_db_then.src.t"));
        assertEquals("ID NUMBER(38,0) Y null null; EXTRA NUMBER(38,0) Y null null", describe("shape_db.src_now.t"));
        assertEquals("[1, null]", rows("SELECT * FROM shape_db.src_now.t"));
    }

    @Test
    public void aCreateOrAlterChangesTheShapeAsOfItsStatement() throws InterruptedException {
        engine.execute("CREATE TABLE o (a INT, b VARCHAR(5))");
        engine.execute("INSERT INTO o VALUES (1, 'x')");
        markPoint("before_alter");
        engine.execute("CREATE OR ALTER TABLE o (a INT, b VARCHAR(10), c INT)");

        engine.execute("CREATE TABLE o_then CLONE o AT(TIMESTAMP => $before_alter)");

        assertEquals("A NUMBER(38,0) Y null null; B VARCHAR(5) Y null null", describe("o_then"));
        assertEquals("[1, x]", rows("SELECT * FROM o_then"));
    }
}
