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

package dev.frostlake.executor;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A compilation error's detail begins on a SECOND line, the way Snowflake writes it. Frostlake used to
 * join the prefix and the detail with a space, which made every message differ from live by one
 * character — invisible in normal use, but enough that the whole rejection suite disagreed with the real
 * account when replayed against it under SF_LIVE.
 *
 * <p>Every expectation here was measured on a real account; see
 * {@link SqlCompilationError} for the captured messages.
 */
public class SqlCompilationErrorFormatTest extends BaseDatabaseTest {

    /** The message a statement actually fails with. */
    private String messageOf(final String sql) {
        return assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void theDetailStartsOnTheSecondLine() {
        assertEquals("SQL compilation error:\ndetail here", SqlCompilationError.of("detail here"));
    }

    /** A source position stays on the FIRST line — live: "…error line 1 at position 10\n'T.B' in…". */
    @Test
    public void aPositionStaysOnTheFirstLine() {
        assertEquals("SQL compilation error: error line 1 at position 10\ndetail here",
            SqlCompilationError.at(1, 10, "detail here"));
    }

    /**
     * The prefix is never joined to its detail with a space in a message the engine actually raises. The
     * only text allowed between the prefix and the newline is the position clause.
     */
    @Test
    public void noRaisedMessageJoinsThePrefixWithASpace() {
        engine.execute("CREATE TABLE err_src (a INTEGER, b INTEGER)");
        final String[] failing = {
            "SELECT a, b FROM err_src GROUP BY a",
            "SELECT MAX(OBJECT_CONSTRUCT('k', 1)) FROM err_src",
            "SELECT 1::FILE",
        };
        for (final String sql : failing) {
            final String message = messageOf(sql);
            assertTrue(message.contains(SqlCompilationError.PREFIX), sql + " -> " + message);
            assertTrue(message.contains(SqlCompilationError.PREFIX + "\n")
                    || message.contains(SqlCompilationError.PREFIX + " error line "),
                "prefix must be followed by a newline or a position clause: " + message);
        }
    }

    /** Live: "SQL compilation error:\ninvalid type [CAST(1 AS FILE)] for parameter 'TO_FILE'". */
    @Test
    public void anInvalidTypeMatchesLiveExactly() {
        assertTrue(messageOf("SELECT 1::FILE").endsWith(
            "SQL compilation error:\ninvalid type [CAST(1 AS FILE)] for parameter 'TO_FILE'"),
            messageOf("SELECT 1::FILE"));
    }

    /**
     * GROUP BY keeps the position on line one and the detail on line two — live:
     * "SQL compilation error: error line 1 at position 10\n'T.B' in select clause is neither …".
     */
    @Test
    public void aGroupByRejectionPutsThePositionOnLineOne() {
        engine.execute("CREATE TABLE gb_src (a INTEGER, b INTEGER)");
        final String message = messageOf("SELECT a, b FROM gb_src GROUP BY a");
        assertTrue(message.contains(SqlCompilationError.PREFIX + " error line 1 at position 10\n"), message);
        assertTrue(message.endsWith(
            "'GB_SRC.B' in select clause is neither an aggregate nor in the group by clause."), message);
        assertEquals(2, message.split("\n", -1).length, "detail belongs on its own line: " + message);
    }

    /**
     * A name that resolves to nothing reports the live shape, and NOTHING wraps it. Frostlake used to
     * answer {@code Failed to execute SQL: Failed to execute DROP statement: Failed to execute DROP
     * statement: View does not exist: V} — three preambles, one of them twice, none of them Snowflake's.
     * Every expectation below was measured on a real account.
     */
    @Test
    public void aMissingObjectMatchesLiveAndCarriesNoPreamble() {
        assertEquals("SQL compilation error:\nView 'TEST_DB.TEST_SCHEMA.NOSUCHVIEW_X'"
            + " does not exist or not authorized.", messageOf("DROP VIEW nosuchview_x"));
        assertEquals("SQL compilation error:\nTable 'TEST_DB.TEST_SCHEMA.NOSUCHTABLE_X'"
            + " does not exist or not authorized.", messageOf("DROP TABLE nosuchtable_x"));
        assertEquals("SQL compilation error:\nSchema 'TEST_DB.NOSUCHSCHEMA_X'"
            + " does not exist or not authorized.", messageOf("DROP SCHEMA nosuchschema_x"));
        assertEquals("SQL compilation error:\nStream 'TEST_DB.TEST_SCHEMA.NOSUCHSTREAM_X'"
            + " does not exist or not authorized.", messageOf("DROP STREAM nosuchstream_x"));
    }

    /**
     * A name missing from a FROM clause is an OBJECT; the same name missing from DESCRIBE or INSERT is a
     * TABLE. Live draws that distinction and Frostlake now does too.
     */
    @Test
    public void aMissingFromClauseNameIsAnObjectNotATable() {
        assertEquals("SQL compilation error:\nObject 'NOSUCHTABLE_Y' does not exist or not authorized.",
            messageOf("SELECT 1 FROM nosuchtable_y"));
        assertEquals("SQL compilation error:\nTable 'NOSUCHTABLE_Y' does not exist or not authorized.",
            messageOf("DESCRIBE TABLE nosuchtable_y"));
        assertEquals("SQL compilation error:\nTable 'NOSUCHTABLE_Y' does not exist or not authorized.",
            messageOf("INSERT INTO nosuchtable_y VALUES (1)"));
    }

    /** No statement handler re-describes a failure it is only passing on. */
    @Test
    public void noHandlerPrefixesTheStatementKind() {
        final String[] failing = {
            "DROP TABLE nosuchtable_z",
            "SELECT 1 FROM nosuchtable_z",
            "INSERT INTO nosuchtable_z VALUES (1)",
            "TRUNCATE TABLE nosuchtable_z",
            "ALTER TABLE nosuchtable_z ADD COLUMN c INTEGER",
        };
        for (final String sql : failing) {
            assertTrue(messageOf(sql).startsWith(SqlCompilationError.PREFIX),
                sql + " -> " + messageOf(sql));
        }
    }

    /**
     * How much of the name is spelled depends on the statement, and Frostlake now draws the line where
     * Snowflake does. Measured on a real account: a DDL statement always spells the name in
     * full, while a query or DML statement echoes what the writer wrote — bare if they wrote it bare.
     */
    @Test
    public void ddlSpellsTheNameInFullAndDmlEchoesWhatWasWritten() {
        assertEquals("SQL compilation error:\nTable 'TEST_DB.TEST_SCHEMA.NOSUCH_Q'"
            + " does not exist or not authorized.", messageOf("DROP TABLE nosuch_q"));
        assertEquals("SQL compilation error:\nTable 'TEST_DB.TEST_SCHEMA.NOSUCH_Q'"
            + " does not exist or not authorized.", messageOf("TRUNCATE TABLE nosuch_q"));
        assertEquals("SQL compilation error:\nTable 'TEST_DB.TEST_SCHEMA.NOSUCH_Q'"
            + " does not exist or not authorized.",
            messageOf("ALTER TABLE nosuch_q ADD COLUMN c INTEGER"));

        assertEquals("SQL compilation error:\nTable 'NOSUCH_Q' does not exist or not authorized.",
            messageOf("INSERT INTO nosuch_q VALUES (1)"));
        assertEquals("SQL compilation error:\nTable 'NOSUCH_Q' does not exist or not authorized.",
            messageOf("DESCRIBE TABLE nosuch_q"));
    }

    /** SELECT, UPDATE and DELETE report a missing name as an Object; INSERT and DESCRIBE as a Table. */
    @Test
    public void theQueryFamilyReportsAnObject() {
        assertEquals("SQL compilation error:\nObject 'NOSUCH_R' does not exist or not authorized.",
            messageOf("SELECT 1 FROM nosuch_r"));
        assertEquals("SQL compilation error:\nObject 'NOSUCH_R' does not exist or not authorized.",
            messageOf("UPDATE nosuch_r SET c = 1"));
        assertEquals("SQL compilation error:\nObject 'NOSUCH_R' does not exist or not authorized.",
            messageOf("DELETE FROM nosuch_r"));
    }

    /** Any qualifier at all and even a query expands the name in full. */
    @Test
    public void aWrittenQualifierIsExpandedInFull() {
        assertEquals("SQL compilation error:\nObject 'TEST_DB.TEST_SCHEMA.NOSUCH_S'"
            + " does not exist or not authorized.", messageOf("SELECT 1 FROM test_schema.nosuch_s"));
        assertEquals("SQL compilation error:\nTable 'TEST_DB.TEST_SCHEMA.NOSUCH_S'"
            + " does not exist or not authorized.",
            messageOf("INSERT INTO test_schema.nosuch_s VALUES (1)"));
    }

    /** Resolution reports the OUTERMOST level that is missing, and qualifies it as far as it is known. */
    @Test
    public void theOutermostMissingLevelIsReported() {
        assertEquals("SQL compilation error:\nSchema 'TEST_DB.NOSUCHSCHEMA'"
            + " does not exist or not authorized.", messageOf("DROP TABLE nosuchschema.nosuch_t"));
        assertEquals("SQL compilation error:\nDatabase 'NOSUCHDB' does not exist or not authorized.",
            messageOf("DROP TABLE nosuchdb.s.nosuch_t"));
    }

    /**
     * A name that resolves to no COLUMN is an {@code invalid identifier} — not a missing object. Measured
     * on a real account across the select list, WHERE, UPDATE SET and every ALTER COLUMN form,
     * and the name keeps the qualifier the writer used.
     *
     * <p>The position is the IDENTIFIER's own 0-based offset in every one of these, live-verified —
     * never the clause's and never the select item's.
     */
    @Test
    public void anUnresolvableColumnIsAnInvalidIdentifier() {
        engine.execute("CREATE TABLE col_src (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO col_src VALUES (1, 2)");
        // The position is the IDENTIFIER's own 0-based offset, live-verified — not the clause's:
        // `SELECT nosuchcol` reports 7, and `WHERE nosuchcol = 1` reports 22 rather than the WHERE.
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'NOSUCHCOL'",
            messageOf("SELECT nosuchcol FROM col_src"));
        assertEquals("SQL compilation error: error line 1 at position 28\ninvalid identifier 'NOSUCHCOL'",
            messageOf("SELECT a FROM col_src WHERE nosuchcol = 1"));
        // The DDL/DML paths resolve columns outside the expression walk, so their position is supplied
        // by the HANDLER, which still holds the statement — measured at 19 and 37 respectively.
        assertEquals("SQL compilation error: error line 1 at position 19\ninvalid identifier 'NOSUCHCOL'",
            messageOf("UPDATE col_src SET nosuchcol = 1"));
        assertEquals("SQL compilation error: error line 1 at position 37\ninvalid identifier 'NOSUCHCOL'",
            messageOf("ALTER TABLE col_src ADD PRIMARY KEY (nosuchcol)"));
        // …and the value side of a SET, which goes back through the expression walk.
        assertEquals("SQL compilation error: error line 1 at position 23\ninvalid identifier 'NOSUCHCOL'",
            messageOf("UPDATE col_src SET a = nosuchcol"));
        assertEquals("SQL compilation error: error line 1 at position 46\ninvalid identifier 'NOSUCHCOL'",
            messageOf("ALTER TABLE col_src ADD CONSTRAINT ck UNIQUE (nosuchcol)"));
        assertEquals("SQL compilation error: error line 1 at position 31\ninvalid identifier 'NOSUCHCOL'",
            messageOf("UPDATE col_src SET a = 1 WHERE nosuchcol = 2"));
        assertEquals("SQL compilation error: error line 1 at position 26\ninvalid identifier 'NOSUCHCOL'",
            messageOf("DELETE FROM col_src WHERE nosuchcol = 1"));
        // An INSERT naming a column the table has not got is refused OUTRIGHT — this used to be
        // accepted, which is a fidelity bug of its own rather than a missing position.
        assertEquals("SQL compilation error: error line 1 at position 21\ninvalid identifier 'NOSUCHCOL'",
            messageOf("INSERT INTO col_src (nosuchcol) VALUES (1)"));
    }

    /** The qualifier is kept: live answers {@code invalid identifier 'T.NOSUCHCOL'} for {@code t.nosuchcol}. */
    @Test
    public void aQualifiedColumnKeepsItsQualifier() {
        engine.execute("CREATE TABLE qual_src (a INTEGER)");
        engine.execute("INSERT INTO qual_src VALUES (1)");
        // The position is the WHOLE reference's start, qualifier included — 7, not the offset of the
        // column part after the dot.
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "invalid identifier 'QUAL_SRC.NOSUCHCOL'",
            messageOf("SELECT qual_src.nosuchcol FROM qual_src"));
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'X.NOSUCHCOL'",
            messageOf("SELECT x.nosuchcol FROM qual_src x"));
        // The one shape still without its position: a qualifier that RESOLVES (qual_src really is in
        // the FROM) passes the plan-time walk, so the refusal happens at row time inside an operator
        // that was handed the predicate as extracted TEXT and no longer knows its offset. Live answers
        // position 28 here. Threading operator offsets is the remaining half of task #143.
        assertEquals("SQL compilation error:\ninvalid identifier 'QUAL_SRC.NOSUCHCOL'",
            messageOf("SELECT a FROM qual_src WHERE qual_src.nosuchcol = 1"));
    }

    /**
     * The two ALTER forms Snowflake phrases differently from everything else: DROP COLUMN uses a
     * lower-case {@code column} with no "or not authorized" tail, and RENAME COLUMN reports an
     * {@code Object}.
     */
    @Test
    public void dropColumnAndRenameColumnHaveTheirOwnWording() {
        engine.execute("CREATE TABLE alt_src (a INTEGER)");
        assertEquals("SQL compilation error:\ncolumn 'NOSUCHCOL' does not exist",
            messageOf("ALTER TABLE alt_src DROP COLUMN nosuchcol"));
        assertEquals("SQL compilation error:\nObject 'NOSUCHCOL' does not exist or not authorized.",
            messageOf("ALTER TABLE alt_src RENAME COLUMN nosuchcol TO z"));
        // COMMENT ON COLUMN treats the column as an object the same way — live.
        assertEquals("SQL compilation error:\nObject 'NOSUCHCOL' does not exist or not authorized.",
            messageOf("COMMENT ON COLUMN alt_src.nosuchcol IS 'c'"));
    }
}
