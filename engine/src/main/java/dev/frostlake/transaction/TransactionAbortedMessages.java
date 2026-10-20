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
 * What a session is told while it holds a transaction SYSTEM$ABORT_TRANSACTION ended (live-verified): every
 * statement but a COMMIT and a ROLLBACK is refused with one sentence, and the COMMIT fails naming the transaction
 * — by the name BEGIN gave it, or the generated one — and its id. Both read SQLCODE 624 and 670 with SQLSTATE
 * 57014 in a block's handler.
 */
public final class TransactionAbortedMessages {

    /** The refusal of a statement that is neither a COMMIT nor a ROLLBACK. */
    public static final String IGNORED = "Your transaction was aborted. All statements will be ignored until you"
        + " issue a COMMIT or a ROLLBACK.";

    private TransactionAbortedMessages() {
    }

    /**
     * The refusal of a statement that was waiting for another session's lock when its own transaction was aborted:
     * it ends at once, naming the transaction and the user who aborted it, with the account's three spaces after
     * "SQL" (live-verified).
     *
     * @param aborted the transaction
     * @return the sentence
     */
    public static String canceledWhileWaiting(final Transaction aborted) {
        return "Transaction '" + aborted.getName() + "', id '" + aborted.getPublicId() + "', was aborted by user '"
            + aborted.getAbortedBy() + "'. SQL   execution canceled.";
    }

    /**
     * The failure of the COMMIT that acknowledges an aborted transaction.
     *
     * @param aborted the transaction
     * @return the sentence, which ends in a blank and a line end as the account's does
     */
    public static String commitFailed(final Transaction aborted) {
        return "COMMIT failed. Your transaction '" + aborted.getName() + "', id '" + aborted.getPublicId()
            + "', was already aborted. \n";
    }
}
