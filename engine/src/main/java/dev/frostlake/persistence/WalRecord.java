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

import dev.frostlake.transaction.WalStatementContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One record in the write-ahead log. A {@link WalRecordType#TRANSACTION} record carries the mutating SQL
 * statements of a single committed transaction (replayed together, as a unit); a
 * {@link WalRecordType#CHECKPOINT} record carries the reference of a durable state snapshot that supersedes
 * every record written before it; a {@link WalRecordType#COUNTERS} record carries where counters stood once a
 * statement drew from them (see {@link CounterMark}). See {@link WriteAheadLog}.
 */
public final class WalRecord {

    private final WalRecordType type;
    private final List<String> statements;
    private final String checkpointRef;
    private final List<Instant> statementInstants;
    private final List<WalStatementContext> statementContexts;
    private final List<CounterMark> counterMarks;

    private WalRecord(final WalRecordType type, final List<String> statements, final String checkpointRef,
                      final List<Instant> statementInstants, final List<WalStatementContext> statementContexts) {
        this(type, statements, checkpointRef, statementInstants, statementContexts,
            Collections.<CounterMark>emptyList());
    }

    private WalRecord(final WalRecordType type, final List<String> statements, final String checkpointRef,
                      final List<Instant> statementInstants, final List<WalStatementContext> statementContexts,
                      final List<CounterMark> counterMarks) {
        this.type = type;
        this.statements = statements;
        this.checkpointRef = checkpointRef;
        this.statementInstants = statementInstants;
        this.statementContexts = statementContexts;
        this.counterMarks = counterMarks;
    }

    /** A committed-transaction record holding the given statements (in execution order). */
    public static WalRecord transaction(final List<String> statements) {
        return transaction(statements, Collections.<Instant>emptyList());
    }

    /**
     * A committed-transaction record that also remembers what EACH of its statements called "now".
     * Replay puts those instants back, so a re-executed {@code CURRENT_TIMESTAMP} resolves to the value
     * the row was originally written with rather than to the recovery time.
     *
     * <p>One instant per statement, not one per record: live, two statements inside a single
     * BEGIN…COMMIT get different times, so a per-record instant would flatten them together.
     */
    public static WalRecord transaction(final List<String> statements,
                                        final List<Instant> statementInstants) {
        return transaction(statements, statementInstants, Collections.<WalStatementContext>emptyList());
    }

    /**
     * A committed-transaction record that also remembers what each statement ran under (see
     * {@link WalStatementContext}); a record from a log before v4 carries none and replays as it did.
     */
    public static WalRecord transaction(final List<String> statements, final List<Instant> statementInstants,
                                        final List<WalStatementContext> statementContexts) {
        return new WalRecord(WalRecordType.TRANSACTION, new ArrayList<>(statements), null,
            new ArrayList<>(statementInstants), new ArrayList<>(statementContexts));
    }

    /** A checkpoint marker referencing a durable state snapshot. */
    public static WalRecord checkpoint(final String checkpointRef) {
        return new WalRecord(WalRecordType.CHECKPOINT, Collections.<String>emptyList(), checkpointRef,
            Collections.<Instant>emptyList(), Collections.<WalStatementContext>emptyList());
    }

    /**
     * What the statement at {@code index} called "now", or null when the record predates v3 and carried
     * no instants — those replay at the wall clock, exactly as they did before.
     */
    /**
     * A record of where counters stood once a statement drew from them.
     *
     * @param marks the counters' marks
     * @return the record
     */
    public static WalRecord counters(final List<CounterMark> marks) {
        return new WalRecord(WalRecordType.COUNTERS, Collections.<String>emptyList(), null,
            Collections.<Instant>emptyList(), Collections.<WalStatementContext>emptyList(), new ArrayList<>(marks));
    }

    /** The marks of a {@link WalRecordType#COUNTERS} record; empty for any other record. */
    public List<CounterMark> getCounterMarks() {
        return counterMarks;
    }

    public Instant getStatementInstant(final int index) {
        return index < statementInstants.size() ? statementInstants.get(index) : null;
    }

    /**
     * What the statement at {@code index} ran under, or null when the record predates v4 and carried none — those
     * replay in the recovery's own session, as they did before.
     */
    public WalStatementContext getStatementContext(final int index) {
        return index < statementContexts.size() ? statementContexts.get(index) : null;
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
