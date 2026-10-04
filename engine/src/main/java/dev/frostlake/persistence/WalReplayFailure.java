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

package dev.frostlake.persistence;

/**
 * A transaction the write-ahead log holds that recovery could not re-apply. Recovery rolls such a transaction back
 * and goes on with the records after it, so the engine starts without that transaction's work. These are what
 * {@code DatabaseEngine.getWalReplayFailures()} reports: the way an embedder learns that the state it was handed is
 * not everything that was committed.
 */
public final class WalReplayFailure {

    private final int recordIndex;
    private final String statement;
    private final String message;

    /**
     * @param recordIndex the record's position in the log, counting from its first record, or -1 when recovery as
     *                    a whole failed (an unreadable log or checkpoint)
     * @param statement   the statement that failed, or null when the failure came before any statement ran
     * @param message     the message of the error the failure raised
     */
    public WalReplayFailure(final int recordIndex, final String statement, final String message) {
        this.recordIndex = recordIndex;
        this.statement = statement;
        this.message = message;
    }

    /** The record's position in the log, counting from its first record; -1 when recovery as a whole failed. */
    public int getRecordIndex() {
        return recordIndex;
    }

    /** The statement that failed, or null when the failure came before any statement ran. */
    public String getStatement() {
        return statement;
    }

    /** The message of the error the failure raised. */
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "record " + recordIndex + (statement != null ? " (" + statement + ")" : "") + ": " + message;
    }
}
