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

package dev.frostlake.jdbc;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.FrostlakeJdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A transaction belongs to the CONNECTION, not to the thread that drives it.
 *
 * <p>The engine keeps its current transaction in a thread-local, so two direct connections used from
 * one thread shared it: the second saw the first's uncommitted rows, and a BEGIN on either reached
 * both. An account keeps them apart — the other connection reads what is committed and nothing more —
 * which is the same isolation the HTTP front-end already gave its sessions, by rebinding each
 * session's transaction around every statement.
 *
 * <p>These assertions are engine-side: they need two connections to ONE engine, which neither the live
 * harness nor the FL-over-JDBC one has a counterpart for — the latter already wraps the test's engine
 * in a connection of its own, so a connection built here would sit on top of it. The behaviour they
 * pin was measured on the account over two JDBC connections.
 */
public class DirectConnectionTransactionIsolationTest extends BaseDatabaseTest {

    /** The row count this connection can see. */
    private String visibleRows(final Connection connection) throws SQLException {
        final Statement statement = connection.createStatement();
        final ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM txi");
        rs.next();
        final String count = rs.getString(1);
        statement.close();
        return count;
    }

    /** One connection's open transaction is invisible to another until it commits. */
    @Test
    public void anotherConnectionSeesOnlyCommittedRows() throws SQLException {
        if (isLiveSnowflake() || FrostlakeJdbc.enabled()) {
            return;
        }
        engine.execute("CREATE OR REPLACE TABLE txi (a INT)");
        final Connection writer = new DirectConnection(engine);
        final Connection reader = new DirectConnection(engine);
        try {
            final Statement writing = writer.createStatement();

            writing.execute("BEGIN TRANSACTION");
            writing.execute("INSERT INTO txi VALUES (1)");
            assertEquals("1", visibleRows(writer), "the writer sees its own row");
            assertEquals("0", visibleRows(reader), "the reader does not");
            writing.execute("ROLLBACK");
            assertEquals("0", visibleRows(reader));

            writing.execute("BEGIN TRANSACTION");
            writing.execute("INSERT INTO txi VALUES (2)");
            assertEquals("0", visibleRows(reader), "still nothing before the commit");
            writing.execute("COMMIT");
            assertEquals("1", visibleRows(reader), "and the row after it");
            writing.close();
        } finally {
            writer.close();
            reader.close();
        }
    }

    /** Each connection's BEGIN and ROLLBACK reach its own transaction only. */
    @Test
    public void eachConnectionKeepsItsOwnTransaction() throws SQLException {
        if (isLiveSnowflake() || FrostlakeJdbc.enabled()) {
            return;
        }
        engine.execute("CREATE OR REPLACE TABLE txi (a INT)");
        final Connection first = new DirectConnection(engine);
        final Connection second = new DirectConnection(engine);
        try {
            final Statement one = first.createStatement();
            final Statement two = second.createStatement();

            one.execute("BEGIN TRANSACTION");
            one.execute("INSERT INTO txi VALUES (1)");

            two.execute("BEGIN TRANSACTION");
            two.execute("INSERT INTO txi VALUES (2)");
            two.execute("ROLLBACK");

            assertEquals("1", visibleRows(first),
                "the second connection's rollback left the first's transaction alone");
            one.execute("COMMIT");
            assertEquals("1", visibleRows(second));
            one.close();
            two.close();
        } finally {
            first.close();
            second.close();
        }
    }

    /** A connection that never opened one is unaffected by another's, in either order. */
    @Test
    public void aConnectionWithoutATransactionIsUnaffected() throws SQLException {
        if (isLiveSnowflake() || FrostlakeJdbc.enabled()) {
            return;
        }
        engine.execute("CREATE OR REPLACE TABLE txi (a INT)");
        final Connection writer = new DirectConnection(engine);
        final Connection plain = new DirectConnection(engine);
        try {
            final Statement writing = writer.createStatement();
            final Statement plainly = plain.createStatement();

            writing.execute("BEGIN TRANSACTION");
            writing.execute("INSERT INTO txi VALUES (1)");
            // The plain connection's own write autocommits, and does not join the open transaction.
            plainly.execute("INSERT INTO txi VALUES (2)");
            assertEquals("1", visibleRows(plain), "it sees its own committed row only");
            assertEquals("2", visibleRows(writer), "the writer sees that row and its own");

            writing.execute("ROLLBACK");
            assertEquals("1", visibleRows(plain), "the rollback took only the writer's row");
            assertEquals("1", visibleRows(writer));
            writing.close();
            plainly.close();
        } finally {
            writer.close();
            plain.close();
        }
    }
}
