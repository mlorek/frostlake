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
 * The FIRST record one staged file rejected, as {@code COPY INTO <table>} reports it in the trailing four
 * columns of its per-file result — {@code first_error}, {@code first_error_line},
 * {@code first_error_character} and {@code first_error_column_name}.
 *
 * <p>"First" is by position in the file, not by error class. Live-verified on a real account
 * with the discriminating pair: a file whose line 2 holds a column-count error and whose line 3 holds a
 * numeric-cast error reported the column-count one, and the same two errors with their lines swapped
 * reported the numeric one — so whichever comes first in the file wins, and neither kind outranks the other.
 * A file with two bad columns on ONE line reports the leftmost ({@code "H1"["A":1]}).
 *
 * <p>{@code line} is 1-based over the PHYSICAL file and counts header lines even when {@code SKIP_HEADER}
 * discards them: a file with three header lines loaded under {@code SKIP_HEADER = 3} reported line 4 for a
 * bad first data record, while a headerless file reported line 1 for its own first record.
 *
 * <p>{@code character} is the 1-based offset of the offending field's first character within that RAW line —
 * quote characters included. Live-verified: a bad third field at raw offset 7 reported 7, the same field
 * pushed to offset 10 by a quoted second field reported 10, and a bad first field reported 1.
 *
 * <p>{@code columnName} is rendered {@code "TABLE"["COLUMN":ordinal]} with a 1-based column ordinal — the
 * BARE table name, never qualified: a {@code COPY INTO db.schema.tbl} reported {@code "F1"["AGE":3]}, not a
 * database- or schema-qualified name.
 *
 * <p>{@code character} and {@code columnName} are null when the format has no line-and-column geometry to
 * point at: a JSON load reported its record ordinal as the line and left both of those null.
 */
public final class CopyFileError {

    private final String message;
    private final int line;
    private final Integer character;
    private final String columnName;

    public CopyFileError(final String message, final int line, final Integer character, final String columnName) {
        this.message = message;
        this.line = line;
        this.character = character;
        this.columnName = columnName;
    }

    public String getMessage() {
        return message;
    }

    public int getLine() {
        return line;
    }

    /** The 1-based character offset within the raw line, or null for a format without one (JSON, XML, …). */
    public Integer getCharacter() {
        return character;
    }

    /** {@code "TABLE"["COLUMN":ordinal]}, or null when the offending column could not be pinned down. */
    public String getColumnName() {
        return columnName;
    }
}
