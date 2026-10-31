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

package dev.frostlake.stream;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.http.SessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A transaction reads a stream as of its BEGIN in every statement: a change another session commits after the BEGIN
 * is not among its reads, nor is one of its own, and its commit leaves the stream at the BEGIN, so those changes stay
 * for the next read; another session's reads meanwhile list everything committed (live-verified).
 *
 * <p>Live, the two sessions are two connections; embedded, two sessions of one concurrent engine.
 */
public class StreamTransactionStartAcrossSessionsTest extends BaseDatabaseTest {

    private ConcurrentDatabaseEngine concurrent;
    private SessionContext sessionA;
    private SessionContext sessionB;
    private Connection connA;
    private Connection connB;

    @BeforeEach
    public void openTwoSessions() {
        if (isLiveSnowflake()) {
            connA = LiveSnowflake.open();
            connB = LiveSnowflake.open();
        } else {
            concurrent = new ConcurrentDatabaseEngine();
            sessionA = concurrent.createSession();
            sessionB = concurrent.createSession();
            say("A", "CREATE DATABASE IF NOT EXISTS test_db");
            say("A", "CREATE SCHEMA IF NOT EXISTS test_db.test_schema");
        }
        say("A", "USE SCHEMA test_db.test_schema");
        say("B", "USE SCHEMA test_db.test_schema");
    }

    @AfterEach
    public void closeTwoSessions() throws SQLException {
        say("A", "ROLLBACK");
        say("B", "ROLLBACK");
        for (final String name : new String[] {"tst3", "tst3_sink", "tst6"}) {
            say("A", "DROP TABLE IF EXISTS " + name);
        }
        if (isLiveSnowflake()) {
            connA.close();
            connB.close();
        } else {
            concurrent.shutdown();
        }
    }

    /**
     * Runs a statement in one session.
     *
     * @return its rows as {@code a|b ;; c|d}, the count of a statement that answers none, or {@code REFUSED} and the
     *         refusal
     */
    private String say(final String side, final String sql) {
        try {
            if (isLiveSnowflake()) {
                try (Statement statement = ("A".equals(side) ? connA : connB).createStatement()) {
                    if (!statement.execute(sql)) {
                        return "count " + statement.getUpdateCount();
                    }
                    final java.sql.ResultSet rows = statement.getResultSet();
                    final ResultSetMetaData columns = rows.getMetaData();
                    final StringBuilder out = new StringBuilder();
                    while (rows.next()) {
                        out.append(out.length() == 0 ? "" : " ;; ");
                        for (int i = 1; i <= columns.getColumnCount(); i++) {
                            out.append(i == 1 ? "" : "|").append(rows.getString(i));
                        }
                    }
                    return out.toString();
                }
            }
            final dev.frostlake.storage.ResultSet rows = concurrent.executeQuery(sql,
                "A".equals(side) ? sessionA : sessionB);
            final StringBuilder out = new StringBuilder();
            while (rows.next()) {
                out.append(out.length() == 0 ? "" : " ;; ");
                for (int i = 0; i < rows.getColumnCount(); i++) {
                    out.append(i == 0 ? "" : "|").append(rows.getValue(i));
                }
            }
            return out.toString();
        } catch (final SQLException | RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    @Test
    public void aTransactionReadsTheStreamAsItsBeginLeftIt() {
        say("A", "CREATE OR REPLACE TABLE tst3 (id INT)");
        say("A", "INSERT INTO tst3 VALUES (1)");
        say("A", "CREATE OR REPLACE STREAM tst3_s ON TABLE tst3");
        say("A", "INSERT INTO tst3 VALUES (2)");
        say("A", "CREATE OR REPLACE TABLE tst3_sink (id INT)");
        say("A", "BEGIN");
        say("B", "INSERT INTO tst3 VALUES (3)");
        assertEquals("2", say("A", "SELECT id FROM tst3_s ORDER BY 1"));
        say("B", "INSERT INTO tst3 VALUES (4)");
        assertEquals("2", say("A", "SELECT id FROM tst3_s ORDER BY 1"));
        say("A", "INSERT INTO tst3_sink SELECT id FROM tst3_s");
        assertEquals("2 ;; 3 ;; 4", say("B", "SELECT id FROM tst3_s ORDER BY 1"));
        assertEquals("2", say("A", "SELECT id FROM tst3_s ORDER BY 1"));
        say("A", "COMMIT");
        assertEquals("3 ;; 4", say("A", "SELECT id FROM tst3_s ORDER BY 1"));
        assertEquals("2", say("A", "SELECT id FROM tst3_sink ORDER BY 1"));
        assertEquals("3 ;; 4", say("B", "SELECT id FROM tst3_s ORDER BY 1"));
    }

    @Test
    public void changesAfterTheBeginStayForTheNextRead() {
        say("A", "CREATE OR REPLACE TABLE tst6 (id INT)");
        say("A", "INSERT INTO tst6 VALUES (1)");
        say("A", "CREATE OR REPLACE STREAM tst6_s ON TABLE tst6");
        say("A", "BEGIN");
        say("A", "INSERT INTO tst6 VALUES (2)");
        say("B", "INSERT INTO tst6 VALUES (3)");
        assertEquals("", say("A", "SELECT id FROM tst6_s ORDER BY 1"));
        say("A", "COMMIT");
        assertEquals("2 ;; 3", say("A", "SELECT id FROM tst6_s ORDER BY 1"));
        say("A", "BEGIN");
        say("B", "INSERT INTO tst6 VALUES (4)");
        assertEquals("TRUE", say("A", "SELECT SYSTEM$STREAM_HAS_DATA('tst6_s')").toUpperCase(Locale.ROOT));
        assertEquals("2", say("A", "SELECT COUNT(*) FROM tst6_s"));
        say("A", "COMMIT");
    }
}
