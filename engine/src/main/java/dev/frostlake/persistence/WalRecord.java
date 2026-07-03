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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One record in the write-ahead log. A {@link WalRecordType#TRANSACTION} record carries the mutating SQL
 * statements of a single committed transaction (replayed together, as a unit); a
 * {@link WalRecordType#CHECKPOINT} record carries the reference of a durable state snapshot that supersedes
 * every record written before it. See {@link WriteAheadLog}.
 */
public class WalRecord {

    private final WalRecordType type;
    private final List<String> statements;
    private final String checkpointRef;

    private WalRecord(final WalRecordType type, final List<String> statements, final String checkpointRef) {
        this.type = type;
        this.statements = statements;
        this.checkpointRef = checkpointRef;
    }

    /** A committed-transaction record holding the given statements (in execution order). */
    public static WalRecord transaction(final List<String> statements) {
        return new WalRecord(WalRecordType.TRANSACTION, new ArrayList<>(statements), null);
    }

    /** A checkpoint marker referencing a durable state snapshot. */
    public static WalRecord checkpoint(final String checkpointRef) {
        return new WalRecord(WalRecordType.CHECKPOINT, Collections.<String>emptyList(), checkpointRef);
    }

    public WalRecordType getType() {
        return type;
    }

    /** The transaction's statements (empty for a checkpoint record). */
    public List<String> getStatements() {
        return statements;
    }

    /** The snapshot reference (null for a transaction record). */
    public String getCheckpointRef() {
        return checkpointRef;
    }
}
