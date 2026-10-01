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

import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the HTTP transport reads from a statement it sent: whether it set up context a fresh session would
 * not have, whether it opened or ended a transaction, and whether it is a plain USE the connection follows
 * as its scope — all from the lexer's tokens, so text inside strings, quoted names, $$ bodies and comments
 * never counts.
 */
public class JdbcSessionEffectsTest {

    private static List<Token> only(final String sql) {
        final List<List<Token>> statements = JdbcSessionEffects.statements(sql);
        assertEquals(1, statements.size(), sql);
        return statements.get(0);
    }

    @Test
    public void aRequestSplitsOnlyOnItsOwnSemicolons() {
        assertEquals(3, JdbcSessionEffects.statements("USE DATABASE a; SELECT ';'; SELECT $$ ; $$").size());
        assertEquals(1, JdbcSessionEffects.statements("-- a ; in a comment\nSELECT \"x;y\" FROM t;").size());
        assertEquals(1, JdbcSessionEffects.statements("/* ; */ SELECT 1;;").size());
        assertEquals(0, JdbcSessionEffects.statements(" ; ;").size());
        assertEquals(0, JdbcSessionEffects.statements(null).size());
    }

    @Test
    public void whatAFreshSessionWouldNotHaveTouchesTheSession() {
        final String[] touching = {
            "USE DATABASE d", "use schema s", "USE ROLE r", "USE WAREHOUSE w", "USE SECONDARY ROLES ALL",
            "SET x = 1", "SET (a, b) = (1, 2)", "UNSET x", "ALTER SESSION SET TIMEZONE = 'UTC'",
            "ALTER SESSION UNSET QUERY_TAG", "CREATE TEMPORARY TABLE t (i INT)",
            "CREATE OR REPLACE TEMP VIEW v AS SELECT 1", "CREATE LOCAL TEMPORARY TABLE t2 (i INT)",
            "create volatile table t3 (i int)", "CREATE DATABASE d", "CREATE OR REPLACE SCHEMA s",
            "CREATE SCHEMA IF NOT EXISTS s", "DROP DATABASE IF EXISTS d", "DROP SCHEMA s",
            "CREATE OR REPLACE TRANSIENT DATABASE d"
        };
        for (final String sql : touching) {
            assertTrue(JdbcSessionEffects.touchesSession(only(sql)), sql);
        }
    }

    @Test
    public void whatAnySessionWouldSeeTheSameLeavesItAlone() {
        final String[] plain = {
            "CREATE TABLE t (i INT)", "CREATE OR REPLACE TRANSIENT TABLE t (i INT)", "INSERT INTO t VALUES (1)",
            "SELECT 'USE DATABASE x'", "ALTER TABLE t ADD COLUMN c INT", "DROP TABLE t",
            "-- USE DATABASE x\nSELECT 1", "/* SET x = 1 */ SELECT 1", "SELECT $$ALTER SESSION SET x = 1$$",
            "ALTER USER u SET DEFAULT_ROLE = r", "CREATE VIEW v AS SELECT 1", "SHOW TABLES"
        };
        for (final String sql : plain) {
            assertFalse(JdbcSessionEffects.touchesSession(only(sql)), sql);
        }
    }

    @Test
    public void beginOnItsOwnOpensATransactionAndABlockDoesNot() {
        final String[] begins = {"BEGIN", "begin transaction", "BEGIN WORK", "BEGIN NAME t1", "START TRANSACTION"};
        for (final String sql : begins) {
            assertEquals(JdbcTransactionEffect.BEGINS, JdbcSessionEffects.transactionEffect(only(sql)), sql);
        }
        final String[] ends = {"COMMIT", "commit work", "ROLLBACK", "ROLLBACK WORK"};
        for (final String sql : ends) {
            assertEquals(JdbcTransactionEffect.ENDS, JdbcSessionEffects.transactionEffect(only(sql)), sql);
        }
        final String[] neither = {"SELECT 1", "BEGIN RETURN 1", "START WITH x", "INSERT INTO t VALUES (1)"};
        for (final String sql : neither) {
            assertEquals(JdbcTransactionEffect.NONE, JdbcSessionEffects.transactionEffect(only(sql)), sql);
        }
        // A block splits on its own semicolons; none of its pieces opens a transaction.
        for (final List<Token> piece : JdbcSessionEffects.statements("BEGIN INSERT INTO t VALUES (1); END;")) {
            assertEquals(JdbcTransactionEffect.NONE, JdbcSessionEffects.transactionEffect(piece));
        }
    }

    @Test
    public void onlyThePlainUseShapesAreTheConnectionsScope() {
        assertEquals(JdbcScopeUse.DATABASE, JdbcSessionEffects.scopeUse(only("USE DATABASE d")));
        assertEquals(JdbcScopeUse.DATABASE, JdbcSessionEffects.scopeUse(only("use database \"My Db\";")));
        assertEquals(JdbcScopeUse.SCHEMA, JdbcSessionEffects.scopeUse(only("USE SCHEMA s")));
        assertEquals(JdbcScopeUse.SCHEMA, JdbcSessionEffects.scopeUse(only("USE SCHEMA d.s")));
        final String[] other = {"USE d", "USE ROLE r", "USE WAREHOUSE w", "USE SECONDARY ROLES ALL",
            "USE SCHEMA d.s.x", "SELECT 1"};
        for (final String sql : other) {
            assertEquals(JdbcScopeUse.NONE, JdbcSessionEffects.scopeUse(only(sql)), sql);
        }
    }
}
