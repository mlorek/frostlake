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
 * Where a counter stood once a statement drew from it: the value a sequence or a table's IDENTITY serves next, which
 * a {@link WalRecordType#COUNTERS} record keeps. A value a statement drew is handed out whatever becomes of the
 * statement and of its transaction (documented: "Sequence values are not rolled back if a transaction is rolled
 * back"), but the transaction's own record reaches the log only when it commits; a transaction open at a shutdown or
 * a crash never does. The mark is logged when the statement ends, before its client sees what it drew, and the replay
 * moves the counter at least this far, so a restart never hands a value out twice.
 */
public final class CounterMark {

    /** The kind of a sequence's mark. */
    public static final String SEQUENCE = "SEQUENCE";
    /** The kind of a table's IDENTITY's mark. */
    public static final String IDENTITY = "IDENTITY";

    private final String kind;
    private final String database;
    private final String schema;
    private final String name;
    private final long next;

    /**
     * @param kind     {@link #SEQUENCE} or {@link #IDENTITY}
     * @param database the database holding the counter's object, by its canonical name
     * @param schema   the schema holding it, by its canonical name
     * @param name     the sequence's or the table's canonical name
     * @param next     the value the counter serves next
     */
    public CounterMark(final String kind, final String database, final String schema, final String name,
                       final long next) {
        this.kind = kind;
        this.database = database;
        this.schema = schema;
        this.name = name;
        this.next = next;
    }

    /** {@link #SEQUENCE} or {@link #IDENTITY}. */
    public String getKind() {
        return kind;
    }

    /** The database holding the counter's object. */
    public String getDatabase() {
        return database;
    }

    /** The schema holding the counter's object. */
    public String getSchema() {
        return schema;
    }

    /** The sequence's or the table's name. */
    public String getName() {
        return name;
    }

    /** The value the counter serves next. */
    public long getNext() {
        return next;
    }
}
