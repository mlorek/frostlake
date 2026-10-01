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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code CREATE OR ALTER} creates the object when it is not there and brings it to the written shape when
 * it is — a TABLE column by column, keeping its rows, and a VIEW by taking the new body.
 *
 * <p>★ WHAT A TABLE MAY BECOME: a column APPENDED at the end, a column DROPPED from anywhere, NOT NULL set
 * or dropped, a column COMMENT, a VARCHAR widened, a NUMBER's precision changed either way, and the table's
 * own COMMENT and CLUSTER BY. The rows survive every one of them.
 *
 * <p>★ WHAT IT MAY NOT: a column added before the end, the columns re-ordered, another type, a VARCHAR
 * narrowed, a NUMBER's scale changed, a DEFAULT set on a column that had none or dropped from one that has
 * it, and a NOT NULL column added to a table that already holds rows. Each has its own sentence.
 *
 * <p>★ THE TYPE SPEAKS FIRST: a statement that changes one column's type and another's default reports the
 * type. Every cell is live-verified.
 *
 * <p>Deliberately not asserted: the account applies what it can before it meets a refusal and says so
 * ("CREATE OR ALTER execution failed. Partial updates may have been applied."), where this judges the whole
 * statement first and leaves the table untouched — so a refused statement's TABLE differs, not its answer.
 */
public class CreateOrAlterTest extends BaseDatabaseTest {

    /** "created", or the refusal on one line. */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "created";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The columns as INFORMATION_SCHEMA lists them, in order. */
    private String columns(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT LISTAGG(column_name, ',') WITHIN GROUP"
            + " (ORDER BY ordinal_position) FROM information_schema.columns WHERE table_name = '"
            + table.toUpperCase() + "'");
        return rs.next() ? String.valueOf(rs.getValue(0)) : "<none>";
    }

    /** The first cell of a query, as text. */
    private String cell(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.next() ? String.valueOf(rs.getValue(0)) : "<no row>";
    }

    /** ★ It creates what is not there, and keeps the rows of what is. */
    @Test
    public void itCreatesThenAltersInPlace() {
        assertEquals("created", outcome("CREATE OR ALTER TABLE t1 (a INT, b VARCHAR)"));
        engine.execute("INSERT INTO t1 VALUES (1, 'x')");
        assertEquals("created", outcome("CREATE OR ALTER TABLE t1 (a INT, b VARCHAR, c INT)"));
        assertEquals("A,B,C", columns("t1"), "a column joins at the end");
        assertEquals("1", cell("SELECT COUNT(*) FROM t1"), "and the rows stay");
        assertEquals("created", outcome("CREATE OR ALTER TABLE t1 (a INT, c INT)"));
        assertEquals("A,C", columns("t1"), "a column missing from the list is dropped");
        assertEquals("1", cell("SELECT COUNT(*) FROM t1"));
        assertEquals("1", cell("SELECT a FROM t1"), "and the values still read under their own columns");
        assertEquals("created", outcome("CREATE OR ALTER TABLE t1 (a INT, c INT)"),
            "a statement that changes nothing is accepted");
    }

    /** ★ A column's nullability, comment and width may change; the type and the scale may not. */
    @Test
    public void aColumnMayWidenButNotChangeType() {
        engine.execute("CREATE OR ALTER TABLE t2 (a INT, b VARCHAR(10))");
        engine.execute("INSERT INTO t2 VALUES (1, 'x')");
        assertEquals("created", outcome("CREATE OR ALTER TABLE t2 (a INT, b VARCHAR(20))"));
        assertEquals("20", cell("SELECT character_maximum_length FROM information_schema.columns"
            + " WHERE table_name = 'T2' AND column_name = 'B'"));
        assertEquals("SQL compilation error: cannot change column B from type VARCHAR(20) to VARCHAR(5)"
            + " because reducing the byte-length of a varchar is not supported.|",
            outcome("CREATE OR ALTER TABLE t2 (a INT, b VARCHAR(5))"));
        assertEquals("SQL compilation error: cannot change column A from type NUMBER(38,0) to"
            + " NUMBER(38,2) because changing the scale of a number is not supported.|",
            outcome("CREATE OR ALTER TABLE t2 (a NUMBER(38,2), b VARCHAR(20))"));
        assertEquals("created", outcome("CREATE OR ALTER TABLE t2 (a NUMBER(20,0), b VARCHAR(20))"),
            "a NUMBER's precision may change");
        assertEquals("SQL compilation error: cannot change column B from type VARCHAR(20) to"
            + " NUMBER(38,0)|", outcome("CREATE OR ALTER TABLE t2 (a NUMBER(20,0), b INT)"));
        assertEquals("created", outcome("CREATE OR ALTER TABLE t2 (a NUMBER(20,0) NOT NULL, b VARCHAR(20))"));
        assertEquals("NO", cell("SELECT is_nullable FROM information_schema.columns"
            + " WHERE table_name = 'T2' AND column_name = 'A'"));
        assertEquals("created", outcome("CREATE OR ALTER TABLE t2 (a NUMBER(20,0) COMMENT 'ac',"
            + " b VARCHAR(20))"), "and NOT NULL comes off again");
        assertEquals("ac", cell("SELECT comment FROM information_schema.columns"
            + " WHERE table_name = 'T2' AND column_name = 'A'"));
    }

    /** ★ The column list is a shape, not an order: no re-ordering, and nothing added before the end. */
    @Test
    public void theColumnsKeepTheirOrder() {
        engine.execute("CREATE OR ALTER TABLE t3 (a INT, b INT)");
        assertEquals("Unsupported feature 'CREATE OR ALTER TABLE column add before end of column list'.",
            outcome("CREATE OR ALTER TABLE t3 (a INT, x DATE, b INT)"));
        assertEquals("A,B", columns("t3"), "and nothing was changed");
        assertEquals("SQL compilation error:|Invalid operation column re-ordering is not possible in"
            + " CREATE OR ALTER TABLE", outcome("CREATE OR ALTER TABLE t3 (b INT, a INT)"));
        assertEquals("created", outcome("CREATE OR ALTER TABLE t3 (a INT, b INT, c INT)"));
        assertEquals("A,B,C", columns("t3"));
    }

    /** ★ A DEFAULT may come with a NEW column and nowhere else. */
    @Test
    public void aDefaultBelongsToANewColumnOnly() {
        engine.execute("CREATE OR ALTER TABLE t4 (a INT)");
        engine.execute("INSERT INTO t4 VALUES (1)");
        assertEquals("created", outcome("CREATE OR ALTER TABLE t4 (a INT, b INT DEFAULT 5)"));
        assertEquals("Unsupported feature 'Alter Column Set Default'.",
            outcome("CREATE OR ALTER TABLE t4 (a INT DEFAULT 7, b INT DEFAULT 5)"));
        assertEquals("Dropping default value is not allowed for column 'B' because the column was added"
            + " after table was created.", outcome("CREATE OR ALTER TABLE t4 (a INT, b INT)"));
        assertEquals("SQL compilation error: Non-nullable column 'C' cannot be added to non-empty table"
            + " 'T4' unless it has a non-null default value.",
            outcome("CREATE OR ALTER TABLE t4 (a INT, b INT DEFAULT 5, c INT NOT NULL)"));
    }

    /** ★ The TYPE speaks before a default. */
    @Test
    public void theTypeSpeaksFirst() {
        engine.execute("CREATE OR ALTER TABLE t5 (a INT, c INT)");
        assertEquals("SQL compilation error: cannot change column C from type NUMBER(38,0) to"
            + " VARCHAR(16777216)|",
            outcome("CREATE OR ALTER TABLE t5 (a INT NOT NULL DEFAULT 7, c VARCHAR)"));
    }

    /** ★ A VIEW takes the new body, and the name's kind still rules. */
    @Test
    public void aViewTakesTheNewBody() {
        assertEquals("created", outcome("CREATE OR ALTER VIEW v1 AS SELECT 1 AS x"));
        assertEquals("1", cell("SELECT * FROM v1"));
        assertEquals("created", outcome("CREATE OR ALTER VIEW v1 AS SELECT 2 AS y"));
        assertEquals("2", cell("SELECT * FROM v1"));
        assertEquals("created", outcome("CREATE OR ALTER VIEW v1 (z) AS SELECT 3"));
        assertEquals("3", cell("SELECT z FROM v1"));
        engine.execute("CREATE OR ALTER TABLE t6 (a INT)");
        assertEquals("SQL compilation error:|Object 'T6' already exists as TABLE",
            outcome("CREATE OR ALTER VIEW t6 AS SELECT 1 AS a"));
    }

    /** The table's own clauses apply too, and the temporary spellings are accepted. */
    @Test
    public void theTablesOwnClausesApply() {
        assertEquals("created", outcome("CREATE OR ALTER TABLE t7 (a INT, b INT) COMMENT = 'tc'"));
        assertEquals("tc", cell("SELECT comment FROM information_schema.tables WHERE table_name = 'T7'"));
        assertEquals("created", outcome("CREATE OR ALTER TABLE t7 (a INT, b INT) CLUSTER BY (a)"));
        assertEquals("created", outcome("CREATE OR ALTER TEMPORARY TABLE t8 (a INT)"));
        assertEquals("created", outcome("CREATE OR ALTER TRANSIENT TABLE t9 (a INT)"));
    }
}
