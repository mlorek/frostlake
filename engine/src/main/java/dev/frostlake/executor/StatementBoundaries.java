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

import dev.frostlake.transaction.WriteSetSavepoint;

/**
 * What happens between the statements of a request that holds several. The account runs each statement of such
 * a request as a statement of its own: under autocommit it commits as it completes, and a later statement's
 * failure undoes only that statement, inside an explicit transaction too. The query executor finds the
 * boundaries while it walks the request; whoever owns the transaction and the write-ahead log acts on them.
 */
public interface StatementBoundaries {

    /** A top-level statement of the request is about to run. */
    void begin();

    /**
     * That statement completed.
     *
     * @param statementText the statement's own source text, as the write-ahead log records it
     */
    void complete(String statementText);

    /**
     * That statement failed: its own writes are undone before the failure propagates.
     *
     * @param cause what it failed with
     */
    void fail(RuntimeException cause);

    /**
     * A stage of a top-level {@code ->>} chain is about to run. Each stage is a statement of its own, which keeps its
     * work when a later stage fails — {@code INSERT INTO t VALUES (2) ->> SELECT 1/0} leaves the row inserted, under
     * autocommit, in an explicit transaction and with AUTOCOMMIT off alike (live-verified).
     *
     * @return the stage's savepoint in a transaction that outlives the statement, or null
     */
    WriteSetSavepoint beginStage();

    /**
     * That stage completed. Under autocommit its work commits as a statement's does, with no record of its own: the
     * chain's record, written when the chain completes, carries every stage, since a stage that reads an earlier
     * one's result cannot run again by itself.
     *
     * @param savepoint what {@link #beginStage} answered
     * @param commit    whether the stage commits now: the last one commits with the chain's record
     */
    void completeStage(WriteSetSavepoint savepoint, boolean commit);

    /**
     * That stage failed: its writes alone are undone, and the stages before it stand, so the statement's failure and
     * the request's undo nothing more. The chain is not logged, so it owes a checkpoint for the work they left, as a
     * failed block does.
     *
     * @param savepoint what {@link #beginStage} answered
     * @param cause     what it failed with
     */
    void failStage(WriteSetSavepoint savepoint, RuntimeException cause);
}
