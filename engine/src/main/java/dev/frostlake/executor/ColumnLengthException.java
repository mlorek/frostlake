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

/**
 * A value too long for its {@code VARCHAR(n)} column, rejected on the write path.
 *
 * <p>It exists because Snowflake words this ONE violation two different ways depending on which path hit it,
 * and the check itself is shared. Live-verified on a real account, the same over-long value
 * against the same {@code VARCHAR(3)} column:
 *
 * <ul>
 *   <li>DML — INSERT, {@code INSERT … SELECT}, UPDATE and MERGE all reported
 *       {@code String 'abcdefgh' is too long and would be truncated}, which is what {@link #getMessage()}
 *       returns;</li>
 *   <li>COPY — the per-file result's {@code first_error} reported
 *       {@code User character length limit (3) exceeded by string 'abcdefgh'} instead.</li>
 * </ul>
 *
 * So the parts are carried rather than the sentence: the COPY layer renders its own wording from
 * {@link #getLimit()} and {@link #getValue()} while every DML caller reads the message as-is. Two other
 * write violations were probed the same way and turned out NOT to differ — a NOT NULL breach is
 * {@code NULL result in a non-nullable column} and a bad numeric is
 * {@code Numeric value 'BADX' is not recognized} on both paths — so only this one needs splitting.
 *
 * <p>The DML wording is the one the plain message carries because Snowflake nests it in a per-statement
 * envelope ({@code DML operation to table E1 failed on column NAME with error: …}) that names the table and
 * column separately. Frostlake does not build that envelope, which is a distinct gap from this one.
 */
public final class ColumnLengthException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int limit;
    private final String value;

    public ColumnLengthException(final int limit, final String value) {
        super("String '" + value + "' is too long and would be truncated");
        this.limit = limit;
        this.value = value;
    }

    /** The column's declared maximum length. */
    public int getLimit() {
        return limit;
    }

    /** The offending value, in full — Snowflake quotes the whole string, not a truncation of it. */
    public String getValue() {
        return value;
    }
}
