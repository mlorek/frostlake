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

/**
 * What {@link TransactionManager#setAsideTransaction} set aside on a thread: the transaction its statements were
 * running in, the session's per-thread autocommit mode and the depth of any enclosing atomic section. Work that
 * runs as a session of its own, such as a task's run, starts without them; {@link TransactionManager#resumeTransaction}
 * puts them back.
 */
public final class SetAsideTransaction {

    private final Transaction transaction;
    private final Boolean sessionAutoCommit;
    private final int atomicSectionDepth;

    SetAsideTransaction(final Transaction transaction, final Boolean sessionAutoCommit, final int atomicSectionDepth) {
        this.transaction = transaction;
        this.sessionAutoCommit = sessionAutoCommit;
        this.atomicSectionDepth = atomicSectionDepth;
    }

    /** The transaction set aside, or null when there was none. */
    Transaction transaction() {
        return transaction;
    }

    /** The thread's autocommit override, or null when it had none and read the shared mode. */
    Boolean sessionAutoCommit() {
        return sessionAutoCommit;
    }

    /** How deep in atomic sections the thread was. */
    int atomicSectionDepth() {
        return atomicSectionDepth;
    }
}
