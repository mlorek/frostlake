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

import dev.frostlake.transaction.Transaction;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.transaction.WriteSetSavepoint;

/**
 * The savepoint of one DML statement inside an explicit transaction: when the statement fails, its own changes
 * are undone and the transaction stays open with everything else in it. A statement of a block, of a procedure's
 * body or one a handler issues is such a statement of its own, so what ran before it keeps its changes whatever
 * becomes of the block or the CALL (live-verified). Outside an explicit transaction a statement commits or rolls
 * back by itself, and there is nothing to mark.
 */
final class StatementSavepoint {

    private static final StatementSavepoint NONE = new StatementSavepoint(null, null, null);

    private final TransactionManager transactions;
    private final Transaction transaction;
    private final WriteSetSavepoint savepoint;

    private StatementSavepoint(final TransactionManager transactions, final Transaction transaction,
                               final WriteSetSavepoint savepoint) {
        this.transactions = transactions;
        this.transaction = transaction;
        this.savepoint = savepoint;
    }

    /**
     * Marks where the statement about to run starts.
     *
     * @param queryExecutor the executor the statement runs on
     * @return the mark, to be ended with {@link #end}
     */
    static StatementSavepoint begin(final QueryExecutor queryExecutor) {
        final TransactionManager transactions = queryExecutor.getTransactionManager();
        if (!queryExecutor.isDeferredApply() || !transactions.isExplicitTransaction()) {
            return NONE;
        }
        final Transaction transaction = transactions.getCurrentTransaction();
        return new StatementSavepoint(transactions, transaction, transaction.getWriteSet().beginStatementSavepoint());
    }

    /**
     * Ends the mark: a statement that completed keeps its changes, one that failed loses them.
     *
     * @param completed whether the statement ran to its end
     */
    void end(final boolean completed) {
        if (savepoint == null || transactions.getCurrentTransaction() != transaction) {
            // No mark, or the transaction it was taken in is over.
            return;
        }
        if (completed) {
            transaction.getWriteSet().endStatementSavepoint(savepoint);
        } else {
            transaction.getWriteSet().rollbackToSavepoint(savepoint);
        }
    }
}
