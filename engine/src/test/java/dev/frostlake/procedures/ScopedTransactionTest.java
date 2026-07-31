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

package dev.frostlake.procedures;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake's scoped-transaction rule, live-verified: a transaction STARTED inside a stored
 * procedure must be completed inside it — returning with it open rolls the transaction back and the
 * CALL fails with "Scoped transaction started in stored procedure is incomplete and it was rolled
 * back." A transaction opened by the CALLER passes through untouched, and a committed-inside
 * transaction works normally.
 */
public class ScopedTransactionTest extends BaseDatabaseTest {

    private long count(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void openScopedTransactionRollsBackAndErrors() {
        engine.execute("CREATE TABLE sct_t (i INTEGER)");
        engine.execute("""
            CREATE PROCEDURE p_open_txn() RETURNS VARCHAR LANGUAGE SQL AS
            BEGIN
                BEGIN TRANSACTION;
                INSERT INTO sct_t VALUES (1);
                RETURN 'left open';
            END
            """);
        final RuntimeException scoped = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CALL p_open_txn()");
            }
        });
        assertTrue(scoped.getMessage().contains(
            "Scoped transaction started in stored procedure is incomplete and it was rolled back."),
            scoped.getMessage());
        assertEquals(0L, count("SELECT COUNT(*) FROM sct_t"), "the open transaction's writes roll back");
    }

    @Test
    public void committedInsideTransactionWorks() {
        engine.execute("CREATE TABLE sct_c (i INTEGER)");
        engine.execute("""
            CREATE PROCEDURE p_closed_txn() RETURNS VARCHAR LANGUAGE SQL AS
            BEGIN
                BEGIN TRANSACTION;
                INSERT INTO sct_c VALUES (2);
                COMMIT;
                RETURN 'committed';
            END
            """);
        engine.execute("CALL p_closed_txn()");
        assertEquals(1L, count("SELECT COUNT(*) FROM sct_c"));
    }

    @Test
    public void callerOpenedTransactionPassesThrough() {
        engine.execute("CREATE TABLE sct_o (i INTEGER)");
        engine.execute("""
            CREATE PROCEDURE p_no_txn() RETURNS VARCHAR LANGUAGE SQL AS
            BEGIN
                INSERT INTO sct_o VALUES (3);
                RETURN 'ok';
            END
            """);
        engine.execute("BEGIN TRANSACTION");
        engine.execute("CALL p_no_txn()");
        engine.execute("COMMIT");
        assertEquals(1L, count("SELECT COUNT(*) FROM sct_o"),
            "a transaction the CALLER opened is not scoped to the procedure");
    }
}
