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

package dev.frostlake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.opentest4j.TestAbortedException;

/**
 * The live harness holds back the statements that would change the account's roles, users and grants: read off each
 * text's tokens where a statement begins, inside a block and in the text an EXECUTE IMMEDIATE runs or a routine's body
 * holds, and never off a string a query merely carries. A test that sends one is skipped before anything reaches the
 * account, and a teardown's one is passed over. The reading needs no account, and nothing here is sent to one.
 */
public class LiveAccountStatementsTest {

    @AfterEach
    public void forget() {
        LiveAccountStatements.beginTest();
    }

    @Test
    public void theRoleUserAndGrantStatementsAreHeldBack() {
        assertEquals("GRANT", LiveAccountStatements.heldBack("GRANT USAGE ON DATABASE d TO ROLE r"));
        assertEquals("GRANT", LiveAccountStatements.heldBack("grant role r to user u"));
        assertEquals("REVOKE", LiveAccountStatements.heldBack("REVOKE SELECT ON TABLE t FROM ROLE r"));
        assertEquals("USE ROLE", LiveAccountStatements.heldBack("use role accountadmin"));
        assertEquals("USE SECONDARY ROLES", LiveAccountStatements.heldBack("USE SECONDARY ROLES NONE"));
        assertEquals("CREATE ROLE", LiveAccountStatements.heldBack("CREATE ROLE IF NOT EXISTS r COMMENT = 'x'"));
        assertEquals("CREATE USER", LiveAccountStatements.heldBack("create or replace user u PASSWORD = 'p'"));
        assertEquals("CREATE DATABASE ROLE", LiveAccountStatements.heldBack("CREATE DATABASE ROLE test_db.dr"));
        assertEquals("DROP ROLE", LiveAccountStatements.heldBack("DROP ROLE IF EXISTS r"));
        assertEquals("ALTER USER", LiveAccountStatements.heldBack("ALTER USER u SET COMMENT = 'x'"));
        assertEquals("DROP DATABASE ROLE", LiveAccountStatements.heldBack("DROP DATABASE ROLE IF EXISTS test_db.dr"));
    }

    @Test
    public void aStatementIsFoundWhereverOneBegins() {
        assertEquals("GRANT", LiveAccountStatements.heldBack("SELECT 1; GRANT ROLE r TO ROLE s"));
        assertEquals("REVOKE", LiveAccountStatements.heldBack("BEGIN REVOKE ROLE r FROM ROLE s; RETURN 1; END"));
        assertEquals("USE ROLE", LiveAccountStatements.heldBack("BEGIN IF (TRUE) THEN USE ROLE r; END IF; END"));
        assertEquals("USE ROLE", LiveAccountStatements.heldBack("EXECUTE IMMEDIATE 'USE ROLE r'"));
        assertEquals("CREATE ROLE", LiveAccountStatements.heldBack("EXECUTE IMMEDIATE $$ BEGIN CREATE ROLE r; END $$"));
        assertEquals("GRANT", LiveAccountStatements.heldBack("CREATE PROCEDURE p() RETURNS INT LANGUAGE SQL AS "
            + "'BEGIN GRANT ROLE r TO ROLE s; RETURN 1; END'"));
    }

    @Test
    public void everyOtherStatementIsSent() {
        assertNull(LiveAccountStatements.heldBack("SHOW GRANTS ON DATABASE d"));
        assertNull(LiveAccountStatements.heldBack("SHOW ROLES"));
        assertNull(LiveAccountStatements.heldBack("DESCRIBE USER u"));
        assertNull(LiveAccountStatements.heldBack("SELECT CURRENT_ROLE(), 'GRANT ROLE r TO ROLE s'"));
        assertNull(LiveAccountStatements.heldBack("INSERT INTO t VALUES ('use role r')"));
        assertNull(LiveAccountStatements.heldBack("CREATE TABLE t (role VARCHAR, \"USER\" INT)"));
        assertNull(LiveAccountStatements.heldBack("CREATE OR REPLACE TABLE t2 COPY GRANTS AS SELECT 1 a"));
        assertNull(LiveAccountStatements.heldBack("CREATE DATABASE d"));
        assertNull(LiveAccountStatements.heldBack("DROP DATABASE IF EXISTS d"));
        assertNull(LiveAccountStatements.heldBack("USE WAREHOUSE w"));
        assertNull(LiveAccountStatements.heldBack("CREATE FUNCTION f() RETURNS VARCHAR AS 'SELECT ''GRANT'''"));
    }

    @Test
    public void aTestIsSkippedBeforeTheStatementIsSentAndATeardownPassesItOver() {
        assumeFalse(LiveAccountStatements.sent(), "the run sends these statements");
        final LiveSnowflakeEngine live = new LiveSnowflakeEngine();
        try {
            final TestAbortedException skipped = assertThrows(TestAbortedException.class, new Executable() {
                @Override
                public void execute() {
                    live.execute("GRANT ROLE r TO ROLE s");
                }
            });
            assertEquals(LiveAccountStatements.reason("GRANT"), skipped.getMessage());
            assertEquals("GRANT", LiveAccountStatements.heldBackInTest());
            live.beginTeardown();
            final ExecutionResult passedOver = live.execute("DROP ROLE IF EXISTS r");
            assertTrue(passedOver.isSuccess());
            assertTrue(passedOver.getResultSets().isEmpty());
        } finally {
            live.shutdown();
        }
    }
}
