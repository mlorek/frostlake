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
package dev.frostlake.metastore;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.http.SessionContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A session holds its current schema by name (live-verified). While its database holds no schema of that name — another
 * session or the session itself renamed it away, or another session dropped it — CURRENT_SCHEMA() is NULL, a bare name
 * misses its object, a CREATE or a DROP of a bare name has no current schema, and SHOW TABLES lists the database; a
 * schema renamed back, restored or created under the name is current again. The session's own DROP moves it to
 * PUBLIC, or to no schema where PUBLIC is gone, and a restore does not bring it back.
 *
 * <p>Live, the two sessions are two connections; embedded, two sessions of one concurrent engine.
 */
public class CurrentSchemaByNameTest extends BaseDatabaseTest {

    private static final String NO_T215 =
        "REFUSED SQL compilation error:\nObject 'T215' does not exist or not authorized.";

    private static final String NO_SCHEMA = " This session does not have a current schema. Call 'USE SCHEMA', or use a"
        + " qualified name.";

    private static final String WHERE = "SELECT CURRENT_DATABASE() || '|' || NVL(CURRENT_SCHEMA(), 'null')";

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
        say("A", "UNDROP SCHEMA test_db.public");
        say("A", "USE SCHEMA test_db.test_schema");
        for (final String schema : new String[] {"s215", "s215b", "s215o", "s215p"}) {
            say("B", "DROP SCHEMA IF EXISTS test_db." + schema);
        }
        if (isLiveSnowflake()) {
            connA.close();
            connB.close();
        } else {
            concurrent.shutdown();
        }
    }

    @Test
    public void anotherSessionsRenameAndDropLeaveTheNameHeld() {
        say("B", "CREATE OR REPLACE SCHEMA test_db.s215");
        say("B", "CREATE OR REPLACE TABLE test_db.s215.t215 (v INT)");
        say("B", "INSERT INTO test_db.s215.t215 VALUES (1)");
        say("A", "USE SCHEMA test_db.s215");
        assertEquals("TEST_DB|S215", say("A", WHERE));
        assertEquals("1", say("A", "SELECT v FROM t215"));
        say("B", "ALTER SCHEMA test_db.s215 RENAME TO test_db.s215b");
        assertEquals("TEST_DB|null", say("A", WHERE));
        assertEquals(NO_T215, firstSentence(say("A", "SELECT v FROM t215")));
        assertEquals("REFUSED Cannot perform CREATE TABLE." + NO_SCHEMA, say("A", "CREATE TABLE t215c (v INT)"));
        assertEquals("REFUSED Cannot perform DROP." + NO_SCHEMA, say("A", "DROP TABLE t215"));
        assertEquals("T215|S215B", say("A",
            "SHOW TABLES ->> SELECT \"name\" || '|' || \"schema_name\" FROM $1 WHERE \"name\" LIKE 'T215%'"));
        assertEquals("[\"TEST_DB.PUBLIC\"]", say("A", "SELECT CURRENT_SCHEMAS()"));
        say("B", "ALTER SCHEMA test_db.s215b RENAME TO test_db.s215");
        assertEquals("TEST_DB|S215", say("A", WHERE));
        assertEquals("1", say("A", "SELECT v FROM t215"));
        say("B", "DROP SCHEMA test_db.s215");
        assertEquals("TEST_DB|null", say("A", WHERE));
        assertEquals(NO_T215, firstSentence(say("A", "SELECT v FROM t215")));
        assertEquals("REFUSED Cannot perform CREATE TABLE." + NO_SCHEMA, say("A", "CREATE TABLE t215d (v INT)"));
        say("B", "UNDROP SCHEMA test_db.s215");
        assertEquals("TEST_DB|S215", say("A", WHERE));
        assertEquals("1", say("A", "SELECT v FROM t215"));
        say("B", "CREATE OR REPLACE SCHEMA test_db.s215");
        assertEquals("TEST_DB|S215", say("A", WHERE));
        assertEquals(NO_T215, firstSentence(say("A", "SELECT v FROM t215")));
    }

    @Test
    public void theSessionsOwnRenameHoldsTheNameAndItsDropMovesIt() {
        say("A", "CREATE OR REPLACE SCHEMA test_db.s215o");
        say("A", "CREATE OR REPLACE TABLE t215o (v INT)");
        assertEquals("TEST_DB|S215O", say("A", WHERE));
        say("A", "ALTER SCHEMA test_db.s215o RENAME TO test_db.s215p");
        assertEquals("TEST_DB|null", say("A", WHERE));
        say("A", "ALTER SCHEMA test_db.s215p RENAME TO test_db.s215o");
        assertEquals("TEST_DB|S215O", say("A", WHERE));
        assertEquals("0", say("A", "SELECT COUNT(*) FROM t215o"));
        say("A", "DROP SCHEMA test_db.s215o");
        assertEquals("TEST_DB|PUBLIC", say("A", WHERE));
        say("A", "UNDROP SCHEMA test_db.s215o");
        assertEquals("TEST_DB|PUBLIC", say("A", WHERE));
        say("A", "DROP SCHEMA test_db.public");
        assertEquals("TEST_DB|null", say("A", WHERE));
        assertEquals("[]", say("A", "SELECT CURRENT_SCHEMAS()"));
        say("A", "UNDROP SCHEMA test_db.public");
        assertEquals("TEST_DB|null", say("A", WHERE));
        say("A", "USE SCHEMA test_db.s215o");
        say("A", "DROP SCHEMA test_db.public");
        say("A", "DROP SCHEMA test_db.s215o");
        assertEquals("TEST_DB|null", say("A", WHERE));
        say("A", "UNDROP SCHEMA test_db.s215o");
        assertEquals("TEST_DB|null", say("A", WHERE));
        say("A", "UNDROP SCHEMA test_db.public");
        say("A", "USE SCHEMA test_db.s215o");
        say("A", "DROP SCHEMA IF EXISTS test_db.nosuch215");
        assertEquals("TEST_DB|S215O", say("A", WHERE));
    }

    /** A refusal cut after its first sentence, ahead of the role it names. */
    private static String firstSentence(final String refused) {
        final int role = refused.indexOf(" Your primary role");
        return role < 0 ? refused : refused.substring(0, role);
    }

    /** Runs a statement in one session: its first cell, or {@code REFUSED} and the refusal. */
    private String say(final String side, final String sql) {
        try {
            if (isLiveSnowflake()) {
                try (Statement statement = ("A".equals(side) ? connA : connB).createStatement()) {
                    if (!statement.execute(sql)) {
                        return "count " + statement.getUpdateCount();
                    }
                    final StringBuilder out = new StringBuilder();
                    try (ResultSet rows = statement.getResultSet()) {
                        while (rows.next()) {
                            out.append(out.length() == 0 ? "" : " ;; ").append(rows.getString(1));
                        }
                    }
                    return out.toString();
                }
            }
            final dev.frostlake.storage.ResultSet rows = concurrent.executeQuery(sql,
                "A".equals(side) ? sessionA : sessionB);
            final StringBuilder out = new StringBuilder();
            while (rows.next()) {
                out.append(out.length() == 0 ? "" : " ;; ").append(rows.getValue(0));
            }
            return out.toString();
        } catch (final SQLException | RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }
}
