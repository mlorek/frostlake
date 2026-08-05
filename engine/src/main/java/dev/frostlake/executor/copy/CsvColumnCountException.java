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

package dev.frostlake.executor.copy;

/**
 * A staged CSV record whose field count does not match the target table's column count, rejected by
 * {@code ERROR_ON_COLUMN_COUNT_MISMATCH} (see
 * {@code CopyCommandExecutor.requireCsvColumnCount} for the rule and the live evidence behind it).
 *
 * <p>It carries its own {@code first_error} geometry rather than letting the generic CSV error describer
 * work it out: the record is rejected BEFORE a row is ever built, so there is no built row to re-check
 * column by column, and the position the account reports is not the offending field's start — for the two
 * "record ran out" shapes it is the position just PAST the record, which is off the end of the field list
 * entirely.
 */
public final class CsvColumnCountException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int line;
    private final Integer character;
    private final String columnName;

    public CsvColumnCountException(final String message, final int line, final Integer character,
            final String columnName) {
        super(message);
        this.line = line;
        this.character = character;
        this.columnName = columnName;
    }

    /**
     * The 1-based PHYSICAL line the account reports — NOT always the offending record's own line. A mismatch
     * on the file's first data record is reported at the position just past that record, which the account
     * renders as the start of the FOLLOWING line when there is one.
     */
    public int getLine() {
        return line;
    }

    /** The 1-based character offset within the reported line. */
    public Integer getCharacter() {
        return character;
    }

    /** {@code "TABLE"["COLUMN":ordinal]}, or {@code "TABLE"[ordinal]} when no such column exists. */
    public String getColumnName() {
        return columnName;
    }
}
