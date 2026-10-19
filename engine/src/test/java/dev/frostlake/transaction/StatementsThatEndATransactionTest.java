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

package dev.frostlake.transaction;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Which statements end the transaction they are issued in, each asked twice: at the scope that began the
 * transaction, where a statement that ends it commits it and the transaction is gone afterwards, and in a block
 * inside it, where such a statement is refused as another scope's and the transaction stays. Creating a table or
 * a view ends it, of whatever kind and from whatever source, and so do ALTER, DROP and COMMENT — a DROP IF EXISTS
 * that finds nothing too. Creating a sequence, a function, a procedure, a schema, a stage, a file format, a
 * stream, a tag, a masking or a row access policy, a task or a pipe does not, nor do ALTER STAGE, ALTER FILE
 * FORMAT, ALTER SESSION, TRUNCATE, USE, SET, a SHOW, a DESCRIBE, an EXPLAIN, a CALL, DML and a query; a CREATE
 * TABLE IF NOT EXISTS over a table that exists creates nothing and ends nothing. Every answer is live-verified.
 */
public class StatementsThatEndATransactionTest extends MeasuredCellsTestSupport {

    private static final String OPEN = "SELECT CURRENT_TRANSACTION() IS NOT NULL";

    private static final String KEPT = "SELECT COUNT(*) FROM tx";

    private static final String OTHER_SCOPE = "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at "
        + "position 6 : Modifying a transaction that has started at a different scope is not allowed.";

    private static final String[] SEED = {
        "CREATE OR REPLACE TABLE tx (a INT)",
        "CREATE OR REPLACE TABLE other_tx (a INT)",
        "CREATE OR REPLACE SEQUENCE k_seq2",
        "CREATE OR REPLACE STAGE k_stage2",
        "CREATE OR REPLACE TASK k_task2 SCHEDULE = '60 MINUTE' AS SELECT 1",
        "CREATE OR REPLACE FUNCTION k_fn2() RETURNS INT AS '1'",
        "CREATE OR REPLACE VIEW k_view2 AS SELECT a FROM other_tx",
        "CREATE OR REPLACE TAG k_tag2",
        "CREATE OR REPLACE STREAM k_stream2 ON TABLE other_tx",
        "CREATE OR REPLACE FILE FORMAT k_ff2 TYPE = CSV",
        "CREATE OR REPLACE PROCEDURE k_plain() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END;'",
    };

    private static final String[] ENDS = {
        "CREATE OR REPLACE TABLE k_t (a INT)",
        "CREATE OR REPLACE TEMPORARY TABLE k_tmp (a INT)",
        "CREATE OR REPLACE TRANSIENT TABLE k_tr (a INT)",
        "CREATE OR REPLACE TABLE k_ctas AS SELECT 1 AS a",
        "CREATE OR REPLACE TABLE k_like LIKE other_tx",
        "CREATE OR REPLACE TABLE k_clone CLONE other_tx",
        "CREATE OR REPLACE EVENT TABLE k_evt",
        "CREATE OR REPLACE VIEW k_v AS SELECT 1 AS x",
        "CREATE OR REPLACE SECURE VIEW k_sv AS SELECT 1 AS x",
        "ALTER TABLE other_tx ADD COLUMN IF NOT EXISTS c INT",
        "ALTER TABLE other_tx SET COMMENT = 'x'",
        "ALTER TABLE other_tx SET TAG k_tag2 = 'v'",
        "ALTER SEQUENCE k_seq2 SET INCREMENT = 2",
        "ALTER SCHEMA test_db.test_schema SET COMMENT = 'x'",
        "ALTER TASK k_task2 SUSPEND",
        "ALTER PROCEDURE k_plain() SET COMMENT = 'x'",
        "ALTER FUNCTION k_fn2() SET COMMENT = 'x'",
        "ALTER VIEW k_view2 SET COMMENT = 'x'",
        "ALTER TAG k_tag2 SET COMMENT = 'x'",
        "ALTER STREAM k_stream2 SET COMMENT = 'x'",
        "DROP TABLE IF EXISTS k_nosuch",
        "DROP SEQUENCE IF EXISTS k_nosuch_seq",
        "DROP PROCEDURE IF EXISTS k_nosuch_p()",
        "DROP VIEW IF EXISTS k_nosuch_v",
        "DROP SCHEMA IF EXISTS test_db.k_nosuch_schema",
        "DROP STAGE IF EXISTS k_nosuch_stage",
        "DROP FUNCTION IF EXISTS k_nosuch_f()",
        "DROP TASK IF EXISTS k_nosuch_task",
        "DROP STREAM IF EXISTS k_nosuch_stream",
        "DROP TAG IF EXISTS k_nosuch_tag",
        "DROP FILE FORMAT IF EXISTS k_nosuch_ff",
        "DROP MASKING POLICY IF EXISTS k_nosuch_mp",
        "DROP DATABASE IF EXISTS fl_nosuch_scope_db",
        "COMMENT ON TABLE other_tx IS 'x'",
        "COMMENT ON SCHEMA test_db.test_schema IS 'x'",
        "COMMENT ON COLUMN other_tx.a IS 'x'",
    };

    private static final String[] LEAVES_OPEN = {
        "CREATE TABLE IF NOT EXISTS other_tx (a INT)",
        "CREATE SEQUENCE IF NOT EXISTS k_seq2",
        "CREATE OR REPLACE SEQUENCE k_seq",
        "CREATE OR REPLACE PROCEDURE k_p() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END;'",
        "CREATE OR REPLACE FUNCTION k_f() RETURNS INT AS '1'",
        "CREATE OR REPLACE SECURE FUNCTION k_sf() RETURNS INT AS '1'",
        "CREATE OR REPLACE FUNCTION k_tf() RETURNS TABLE (a INT) AS 'SELECT 1'",
        "CREATE OR REPLACE SCHEMA test_db.k_schema",
        "CREATE OR REPLACE STAGE k_stage",
        "CREATE OR REPLACE TEMPORARY STAGE k_tstage",
        "CREATE OR REPLACE FILE FORMAT k_ff TYPE = CSV",
        "CREATE OR REPLACE STREAM k_stream ON TABLE other_tx",
        "CREATE OR REPLACE STREAM k_vstream ON VIEW k_view2",
        "CREATE OR REPLACE TAG k_tag",
        "CREATE OR REPLACE MASKING POLICY k_mp AS (v VARCHAR) RETURNS VARCHAR -> v",
        "CREATE OR REPLACE ROW ACCESS POLICY k_rap AS (v VARCHAR) RETURNS BOOLEAN -> TRUE",
        "CREATE OR REPLACE TASK k_task SCHEDULE = '60 MINUTE' AS SELECT 1",
        "CREATE OR REPLACE PIPE k_pipe AS COPY INTO other_tx FROM @k_stage2",
        "ALTER STAGE k_stage2 SET COMMENT = 'x'",
        "ALTER FILE FORMAT k_ff2 SET COMMENT = 'x'",
        "ALTER SESSION SET QUERY_TAG = 'k'",
        "TRUNCATE TABLE other_tx",
        "USE SCHEMA test_db.test_schema",
        "SET k_sv = 1",
        "UNSET k_nosuch_sv",
        "SHOW TABLES LIKE 'OTHER_TX'",
        "DESCRIBE TABLE other_tx",
        "EXPLAIN SELECT 1",
        "CALL k_plain()",
        "INSERT INTO other_tx (a) VALUES (1)",
        "DELETE FROM other_tx",
        "SELECT k_seq2.NEXTVAL",
    };

    @Override
    protected void setupTest() {
        for (final String statement : SEED) {
            engine.execute(statement);
        }
    }

    /** Puts back what the statements asked here may have left in the session. */
    private void restoreSession() {
        answer("ROLLBACK");
        answer("ALTER SESSION UNSET QUERY_TAG");
        answer("UNSET k_sv");
        answer("USE SCHEMA test_db.test_schema");
    }

    /** Opens a transaction holding one row of its own. */
    private void openTransaction() {
        answer("ROLLBACK");
        engine.execute("DELETE FROM tx");
        engine.execute("BEGIN TRANSACTION");
        engine.execute("INSERT INTO tx VALUES (1)");
    }

    /** The statement runs at the scope that began the transaction; what is left of the transaction afterwards. */
    private void assertAtItsOwnScope(final String statement, final boolean ends) {
        openTransaction();
        final String answer = answer(statement);
        assertFalse(answer.startsWith("REFUSED"), statement + " ==> " + answer);
        // A new schema is where the session is afterwards; the table stands in the one before it.
        answer("USE SCHEMA test_db.test_schema");
        assertEquals(ends ? "false" : "true", answer(OPEN), statement);
        answer("ROLLBACK");
        // A statement that ended the transaction committed its row; any other left it to the ROLLBACK.
        assertEquals(ends ? "1" : "0", answer(KEPT), statement);
    }

    /** The statement runs in a block inside the transaction: refused as another scope's, or run. */
    private void assertInABlock(final String statement, final boolean ends) {
        openTransaction();
        assertEquals(ends ? OTHER_SCOPE : "ran", answer("BEGIN " + statement + "; RETURN 'ran'; END;"), statement);
        answer("USE SCHEMA test_db.test_schema");
        assertEquals("true", answer(OPEN), statement);
        answer("ROLLBACK");
        assertEquals("0", answer(KEPT), statement);
    }

    @Test
    public void aStatementThatEndsItsTransactionCommitsIt() {
        try {
            for (final String statement : ENDS) {
                assertAtItsOwnScope(statement, true);
            }
        } finally {
            restoreSession();
        }
    }

    @Test
    public void aStatementThatDoesNotLeavesItsTransactionOpen() {
        try {
            for (final String statement : LEAVES_OPEN) {
                assertAtItsOwnScope(statement, false);
            }
        } finally {
            restoreSession();
        }
    }

    @Test
    public void aStatementThatEndsATransactionIsRefusedInABlockInsideIt() {
        try {
            for (final String statement : ENDS) {
                assertInABlock(statement, true);
            }
        } finally {
            restoreSession();
        }
    }

    @Test
    public void aStatementThatDoesNotRunsInABlockInsideIt() {
        try {
            for (final String statement : LEAVES_OPEN) {
                assertInABlock(statement, false);
            }
        } finally {
            restoreSession();
        }
    }
}
