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

import java.sql.SQLNonTransientConnectionException;

/**
 * The server no longer holds the session a statement was sent to — it idled out, was released, or went
 * with a restart — and that session held something a fresh one cannot reproduce: an open transaction, or
 * context set up on it with a USE, a SET, an ALTER SESSION or a temporary object. The statement did not
 * run.
 *
 * <p>The SQLState is 08003, a connection that no longer exists, so a connection pool treats the
 * connection as broken. The connection itself stays usable: its next statement starts a new session on
 * the connection's own scope — the database and schema it was opened with or switched to, and its
 * autocommit mode.
 */
public class FrostlakeSessionLostException extends SQLNonTransientConnectionException {

    private static final long serialVersionUID = 1L;

    /** The SQLState of a connection that no longer exists. */
    public static final String SQL_STATE = "08003";

    private final boolean transactionLost;

    /**
     * @param reason what was lost, for the message
     * @param transactionLost whether the lost session held an open transaction
     */
    public FrostlakeSessionLostException(final String reason, final boolean transactionLost) {
        super(reason, SQL_STATE);
        this.transactionLost = transactionLost;
    }

    /**
     * Whether the lost session held an open transaction — rolled back with the session, so nothing in it
     * was committed.
     *
     * @return true when a transaction went with the session
     */
    public boolean isTransactionLost() {
        return transactionLost;
    }
}
