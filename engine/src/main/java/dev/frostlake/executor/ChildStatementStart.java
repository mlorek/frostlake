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

import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.transaction.Transaction;

import java.util.Set;

/**
 * What a child statement's QUERY_HISTORY row is completed from, taken as the statement begins: the statement, the
 * session's last transaction then, the tables the statement running it had written so far, set aside so that the
 * child counts its own and put back with the child's once it is counted, and the query ID it runs under.
 */
final class ChildStatementStart {

    private final FrostlakeParser.StatementContext statement;
    private final String lastTransactionBefore;
    private final Set<TableStorage> storedTables;
    private final Transaction transaction;
    private final Set<String> pendingTables;
    private final String reservedId;
    private final String runningBefore;

    /**
     * @param statement             the child statement
     * @param lastTransactionBefore the session's last transaction as it begins, or null
     * @param storedTables          the tables written through to storage so far
     * @param transaction           the transaction open as it begins, or null
     * @param pendingTables         the tables that transaction's statements had written so far, empty without one
     * @param reservedId            the query ID it runs under and ends with
     * @param runningBefore         the query ID the statement running it ran under before it began, or null
     */
    ChildStatementStart(final FrostlakeParser.StatementContext statement, final String lastTransactionBefore,
                        final Set<TableStorage> storedTables, final Transaction transaction,
                        final Set<String> pendingTables, final String reservedId, final String runningBefore) {
        this.statement = statement;
        this.lastTransactionBefore = lastTransactionBefore;
        this.storedTables = storedTables;
        this.transaction = transaction;
        this.pendingTables = pendingTables;
        this.reservedId = reservedId;
        this.runningBefore = runningBefore;
    }

    FrostlakeParser.StatementContext statement() {
        return statement;
    }

    String lastTransactionBefore() {
        return lastTransactionBefore;
    }

    Set<TableStorage> storedTables() {
        return storedTables;
    }

    Transaction transaction() {
        return transaction;
    }

    Set<String> pendingTables() {
        return pendingTables;
    }

    String reservedId() {
        return reservedId;
    }

    String runningBefore() {
        return runningBefore;
    }
}
