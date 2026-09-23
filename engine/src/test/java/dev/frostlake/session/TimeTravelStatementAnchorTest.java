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

package dev.frostlake.session;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code AT(STATEMENT => id)} reads the table WITH that statement's changes, {@code BEFORE} without them; a
 * statement inside an explicit transaction counts from its COMMIT. Only a statement that changed data can
 * anchor time travel.
 */
public class TimeTravelStatementAnchorTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t9 (a INT)");
        engine.execute("INSERT INTO t9 VALUES (1)");
        engine.execute("SET qid = LAST_QUERY_ID()");
        engine.execute("INSERT INTO t9 VALUES (4)");
    }

    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        while (rs.next()) {
            if (text.length() > 0) {
                text.append(';');
            }
            text.append(rs.getValue(0));
        }
        return text.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                rows(sql);
            }
        }).getMessage().replace("\n", " | ");
    }

    /** AT includes the statement's own write, and BEFORE leaves it out. */
    @Test
    public void atIncludesTheStatementsOwnWrite() {
        assertEquals("1", rows("SELECT a FROM t9 AT(STATEMENT => $qid) ORDER BY a"));
        assertEquals("", rows("SELECT a FROM t9 BEFORE(STATEMENT => $qid) ORDER BY a"));
    }

    /** A statement inside an explicit transaction counts from the transaction's COMMIT. */
    @Test
    public void aStatementInATransactionCountsFromItsCommit() {
        engine.execute("BEGIN");
        engine.execute("INSERT INTO t9 VALUES (7)");
        engine.execute("SET open_qid = LAST_QUERY_ID()");
        engine.execute("COMMIT");
        assertEquals("1;4;7", rows("SELECT a FROM t9 AT(STATEMENT => $open_qid) ORDER BY a"));
    }

    /** A DDL statement's id cannot anchor time travel, and the refusal has no compilation prefix. */
    @Test
    public void aDdlIdIsRefused() {
        engine.execute("ALTER TABLE t9 ADD COLUMN d INT");
        engine.execute("SET ddl = LAST_QUERY_ID()");
        final String message = refusal("SELECT a FROM t9 AT(STATEMENT => $ddl)");
        assertTrue(message.endsWith("cannot be used to specify time for time travel query."), message);
        assertTrue(message.startsWith("Statement "), message);
    }

    /** An id no statement had is not found. */
    @Test
    public void anUnknownIdIsNotFound() {
        assertEquals("Statement nope not found", refusal("SELECT a FROM t9 AT(STATEMENT => 'nope')"));
    }
}
