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

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The records of one staged CSV file as INFER_SCHEMA splits them.
 *
 * <p>A record ends at the RECORD_DELIMITER — a newline by default, which a carriage return before it joins — and
 * a field at the FIELD_DELIMITER; either may be several characters or NONE. An empty record is no record.
 *
 * <ul>
 *   <li>A field that starts with the FIELD_OPTIONALLY_ENCLOSED_BY character (after spaces, under TRIM_SPACE) is
 *       enclosed: it runs to the closing one across delimiters and newlines, a doubled enclosure is one
 *       character, and the ESCAPE character takes the next one literally. Spaces may follow the closing
 *       enclosure; any other character before the next delimiter is refused, and so is an enclosure still open
 *       at the end of the file.</li>
 *   <li>Any other field — a quote inside it included — runs to the next delimiter, and the
 *       ESCAPE_UNENCLOSED_FIELD character (a backslash by default) takes a delimiter or itself literally; before
 *       any other character it stays in the value ({@code \N} is still NULL_IF's default marker).</li>
 *   <li>The header record is split without that escape: an escaped delimiter keeps the escape character in the
 *       name and still divides it from the next one, and an enclosure still open at the end of the file closes
 *       there.</li>
 * </ul>
 *
 * <p>A data record wider than the header is refused as the field past it starts. The refusals name the file,
 * the line and character, and the row — {@code Row 1 starts at line 2} when the fault is on a later line than
 * the record's first — and the column, {@code "TRANSIENT_STAGE_TABLE"["$1":1]} for the first and
 * {@code "TRANSIENT_STAGE_TABLE"[2]} for any other.
 *
 * <p>A staged-file query reads its CSV files with the same splitting (live-verified): a delimiter of several
 * characters, a record delimiter other than a newline, a closing enclosure followed by anything but a delimiter
 * refused. Its refusals end at the row and name every column {@code "TRANSIENT_STAGE_TABLE"["$2":2]}.
 */
public final class CsvRecordReader {

    /** A field that ended at a field delimiter: the record goes on. */
    private static final int NEXT_FIELD = 0;

    /** A field that ended at a record delimiter. */
    private static final int RECORD_END = 1;

    /** A field that ended with the file. */
    private static final int FILE_END = 2;

    /** The tail of a refusal of a record's width. */
    private static final String HEADER_TAIL = "\n  Row 0 starts at line 0, column ";

    private final Reader source;
    private final String relativeName;
    private final String fieldDelimiter;
    private final String recordDelimiter;
    private final Character enclosure;
    private final Character escape;
    private final Character escapeUnenclosed;
    private final boolean trimSpace;
    /** Whether a staged-file query reads the file, whose refusals are worded as its own. */
    private final boolean stagedQuery;

    /** Whether a field holding bytes its file's encoding could not read is settled here (see settlingUnreadable). */
    private boolean settlesUnreadable;

    /** Whether such a field reads each unreadable sequence as U+FFFD rather than being refused. */
    private boolean replacesUnreadable;

    /** The refusals the last record's fields hold, by field number from 1 (see {@link #unreadableFaults}). */
    private final Map<Integer, RuntimeException> unreadable = new HashMap<Integer, RuntimeException>();

    /** The characters read from the source and not yet taken. */
    private final StringBuilder ahead = new StringBuilder();
    private boolean exhausted;

    /** The line of the next character, from 1. */
    private int line = 1;

    /** The character of the next one within its line, from 1. */
    private int column = 1;

    /** How many fields the first data record held, which stands in for a header's width without one. */
    private int firstWidth = -1;

    /** The line the last record started on. */
    private int recordStartLine = 1;

    /** Where each field of the last record started, as {line, character}. */
    private final List<int[]> fieldStarts = new ArrayList<int[]>();

    /** The place just past the last record's last field: its record delimiter's, or the end of the file. */
    private int recordEndLine = 1;
    private int recordEndColumn = 1;

    /**
     * A reader over one file.
     *
     * @param source the file's characters
     * @param relativeName the file's stage-relative name, as the refusals name it
     * @param options the call's options
     */
    CsvRecordReader(final Reader source, final String relativeName, final SchemaScanOptions options) {
        this.source = source;
        this.relativeName = relativeName;
        this.fieldDelimiter = options.fieldDelimiter();
        this.recordDelimiter = options.recordDelimiter();
        this.enclosure = options.enclosure();
        this.escape = options.escape();
        this.escapeUnenclosed = options.escapeUnenclosed();
        this.trimSpace = options.trimSpace();
        this.stagedQuery = false;
    }

    /**
     * A reader over one file as a staged-file query reads it.
     *
     * @param source the file's characters
     * @param relativeName the file's stage-relative name, as the refusals name it
     * @param fieldDelimiter the text between two fields, null for NONE
     * @param recordDelimiter the text between two records, null for NONE
     * @param enclosure FIELD_OPTIONALLY_ENCLOSED_BY, or null
     * @param escape ESCAPE, or null
     * @param escapeUnenclosed ESCAPE_UNENCLOSED_FIELD, or null
     */
    public CsvRecordReader(final Reader source, final String relativeName, final String fieldDelimiter,
                           final String recordDelimiter, final Character enclosure, final Character escape,
                           final Character escapeUnenclosed) {
        this.source = source;
        this.relativeName = relativeName;
        this.fieldDelimiter = fieldDelimiter;
        this.recordDelimiter = recordDelimiter;
        this.enclosure = enclosure;
        this.escape = escape;
        this.escapeUnenclosed = escapeUnenclosed;
        this.trimSpace = false;
        this.stagedQuery = true;
    }

    /**
     * Skip whole lines, as SKIP_HEADER does.
     *
     * @param lines how many
     * @throws IOException when the file cannot be read
     */
    public void skipLines(final int lines) throws IOException {
        for (int skipped = 0; skipped < lines; skipped++) {
            int c = take();
            while (c >= 0 && c != '\n') {
                c = take();
            }
        }
    }

    /**
     * The header record's names, as written; null when the file is empty.
     *
     * @param enclosedOut filled with whether each name was enclosed
     * @return the names
     * @throws IOException when the file cannot be read
     */
    List<String> header(final List<Boolean> enclosedOut) throws IOException {
        return peek(0) < 0 ? null : fields(true, -1, 0, enclosedOut);
    }

    /**
     * The next data record's fields, as written; null at the end of the file.
     *
     * @param width the header's width, or -1 without one
     * @param row the record's number, from 1, as the refusals name it
     * @param enclosedOut filled with whether each field was enclosed
     * @return the fields
     * @throws IOException when the file cannot be read
     */
    public List<String> next(final int width, final long row, final List<Boolean> enclosedOut) throws IOException {
        int blank = recordDelimiterLength();
        while (blank > 0) {
            consume(blank);
            blank = recordDelimiterLength();
        }
        if (peek(0) < 0) {
            return null;
        }
        final List<String> fields = fields(false, width, row, enclosedOut);
        if (firstWidth < 0) {
            firstWidth = fields.size();
        }
        return fields;
    }

    private List<String> fields(final boolean header, final int width, final long row, final List<Boolean> enclosedOut)
            throws IOException {
        enclosedOut.clear();
        unreadable.clear();
        fieldStarts.clear();
        final int startLine = line;
        recordStartLine = startLine;
        final List<String> fields = new ArrayList<String>();
        int ending = NEXT_FIELD;
        while (ending == NEXT_FIELD) {
            final int index = fields.size() + 1;
            if (width > 0 && index > width) {
                throw widthRefusal("header defined (" + width + ") columns while data contains more columns", line,
                    column, row, startLine, width);
            }
            final StringBuilder value = new StringBuilder();
            final int fieldLine = line;
            final int fieldColumn = column;
            fieldStarts.add(new int[] {fieldLine, fieldColumn});
            final boolean enclosed = enclosureAhead();
            ending = enclosed ? enclosedField(value, header, width, row, startLine, index)
                : unenclosedField(value, header);
            fields.add(header ? value.toString()
                : settled(value.toString(), fieldLine, fieldColumn, row, startLine, index));
            enclosedOut.add(Boolean.valueOf(enclosed));
        }
        return fields;
    }

    /**
     * Settles the bytes a field's file could not read in its encoding, as a staged-file query does: the field holds
     * its refusal (see {@link #unreadableFaults}), or each unreadable sequence is read as U+FFFD under
     * REPLACE_INVALID_CHARACTERS = TRUE.
     *
     * @param replace whether the format's REPLACE_INVALID_CHARACTERS is TRUE
     * @return this reader
     */
    public CsvRecordReader settlingUnreadable(final boolean replace) {
        this.settlesUnreadable = true;
        this.replacesUnreadable = replace;
        return this;
    }

    /**
     * The refusals of the last record's fields that hold bytes their file's encoding could not read, in the account's
     * words — the bytes spelled as {@code 0xE9}, the field's start named — by field number from 1. A query refuses
     * only where it reads such a field: a count of the file's rows reads none (live-verified).
     *
     * @return the refusals, empty when every field read
     */
    public Map<Integer, RuntimeException> unreadableFaults() {
        return new HashMap<Integer, RuntimeException>(unreadable);
    }

    /** A field as it is read, each unreadable sequence as U+FFFD under REPLACE_INVALID_CHARACTERS = TRUE. */
    private String settled(final String value, final int fieldLine, final int fieldColumn, final long row,
                           final int startLine, final int index) {
        if (!settlesUnreadable || StagedFileIo.firstUnreadable(value) < 0) {
            return value;
        }
        if (replacesUnreadable) {
            return StagedFileIo.unreadableReplaced(value);
        }
        unreadable.put(Integer.valueOf(index), parseRefusal("Invalid UTF8 detected in string '"
            + StagedFileIo.unreadableShown(value) + "'", fieldLine, fieldColumn, row, startLine, index));
        return value;
    }

    /** Whether the next field is enclosed, the spaces before its enclosure taken under TRIM_SPACE. */
    private boolean enclosureAhead() throws IOException {
        if (enclosure == null) {
            return false;
        }
        int spaces = 0;
        if (trimSpace) {
            while (peek(spaces) == ' ') {
                spaces++;
            }
        }
        if (peek(spaces) != enclosure.charValue()) {
            return false;
        }
        consume(spaces);
        return true;
    }

    private int enclosedField(final StringBuilder value, final boolean header, final int width, final long row,
                              final int startLine, final int index) throws IOException {
        final int openLine = line;
        final int openColumn = column;
        consume(1);
        while (true) {
            int c = take();
            if (c >= 0 && c == enclosure.charValue()) {
                if (peek(0) != enclosure.charValue()) {
                    break;
                }
                consume(1);
            } else if (c >= 0 && escape != null && c == escape.charValue()) {
                c = take();
            }
            if (c < 0) {
                if (header) {
                    return FILE_END;
                }
                throw parseRefusal("matching enclosing character '" + enclosure + "' not found before end of file",
                    openLine, openColumn, row, startLine, index);
            }
            value.append((char) c);
        }
        while (peek(0) == ' ') {
            consume(1);
        }
        final int ending = delimiterEnding();
        if (ending >= 0) {
            return ending;
        }
        if (header) {
            return unenclosedField(value, true);
        }
        throw foundCharacter(width, row, startLine, index);
    }

    private int unenclosedField(final StringBuilder value, final boolean header) throws IOException {
        while (true) {
            final int ending = delimiterEnding();
            if (ending >= 0) {
                return ending;
            }
            final char c = (char) take();
            if (escapeUnenclosed == null || c != escapeUnenclosed.charValue()) {
                value.append(c);
                continue;
            }
            final int escapedField = fieldDelimiterLength();
            final int escapedRecord = escapedField > 0 ? 0 : recordDelimiterLength();
            if (header) {
                value.append(c);
                if (escapedField + escapedRecord > 0) {
                    consume(escapedField + escapedRecord);
                    return NEXT_FIELD;
                }
            } else if (escapedField + escapedRecord > 0) {
                value.append(taken(escapedField + escapedRecord));
            } else if (peek(0) == c) {
                value.append((char) take());
            } else {
                value.append(c);
            }
        }
    }

    /** The delimiter at the cursor, taken, as the ending it makes; -1 when there is none and the file goes on. */
    private int delimiterEnding() throws IOException {
        if (peek(0) < 0) {
            recordEndLine = line;
            recordEndColumn = column;
            return FILE_END;
        }
        final int field = fieldDelimiterLength();
        if (field > 0) {
            consume(field);
            return NEXT_FIELD;
        }
        final int record = recordDelimiterLength();
        if (record > 0) {
            recordEndLine = line;
            recordEndColumn = column;
            consume(record);
            return RECORD_END;
        }
        return -1;
    }

    /**
     * The refusal of the last record's width against a table's, in a load's words, or null when the widths agree.
     * The rule is COPY's (see {@code CopyCommandExecutor.requireCsvColumnCount}), and a table stage's query follows it
     * (live-verified): the file's first record of another width is {@code Number of columns in file (F) does not match
     * that of the corresponding table (T), …}, placed just past the record — at the next line's start when the file
     * goes on — against column F, named when the table has one; a later record holding more fields is refused at the
     * delimiter that should have ended it, against the table's last column, and one holding fewer just past its end,
     * against the first column it never reached.
     *
     * @param fieldCount  the last record's fields
     * @param table       the table's name, as the refusal spells it
     * @param columns     the table's column names, in order
     * @param firstRecord whether the record is its file's first
     * @param row         the record's number
     * @return the refusal, or null
     * @throws IOException when the file cannot be read
     */
    public RuntimeException tableWidthRefusal(final int fieldCount, final String table, final List<String> columns,
                                              final boolean firstRecord, final long row) throws IOException {
        final int width = columns.size();
        if (fieldCount == width) {
            return null;
        }
        if (firstRecord) {
            final boolean goesOn = peek(0) >= 0;
            final String against = fieldCount > width ? "\"" + table + "\"[" + fieldCount + "]"
                : tableColumn(table, columns, fieldCount);
            return tableRefusal("Number of columns in file (" + fieldCount
                + ") does not match that of the corresponding table (" + width
                + "), use file format option error_on_column_count_mismatch=false to ignore this error",
                goesOn ? line : recordEndLine, goesOn ? column : recordEndColumn, row, against);
        }
        if (fieldCount > width) {
            final int[] past = fieldStarts.get(width);
            return tableRefusal("Field delimiter '" + shown(fieldDelimiter)
                + "' found while expecting record delimiter '" + shown(recordDelimiter) + "'", past[0],
                past[1] - (fieldDelimiter == null ? 0 : fieldDelimiter.length()), row,
                tableColumn(table, columns, width));
        }
        final String missing = tableColumn(table, columns, fieldCount + 1);
        return tableRefusal("End of record reached while expected to parse column '" + missing + "'", recordEndLine,
            recordEndColumn, row, missing);
    }

    /** {@code "T"["C":n]}, a table's n-th column as a load's refusal names it. */
    private static String tableColumn(final String table, final List<String> columns, final int ordinal) {
        return "\"" + table + "\"[\"" + columns.get(ordinal - 1) + "\":" + ordinal + "]";
    }

    /** A refusal of the last record against a table: the sentence, the file and place, the row and the column. */
    private RuntimeException tableRefusal(final String detail, final int atLine, final int atColumn, final long row,
                                          final String against) {
        return new RuntimeException(detail + "\n  File '" + relativeName + "', line " + atLine + ", character "
            + atColumn + "\n  Row " + row + startingAt(atLine, recordStartLine) + ", column " + against);
    }

    /**
     * A character after a closing enclosure. The delimiter it stands in for is the record's for the header's last
     * field, the field one otherwise; and when the record, read on from there, holds more fields than the header,
     * the refusal is the header's.
     */
    private RuntimeException foundCharacter(final int width, final long row, final int startLine, final int index)
            throws IOException {
        final int atLine = line;
        final int atColumn = column;
        final char found = (char) peek(0);
        final int expected = width > 0 ? width : firstWidth;
        final String detail = "Found character '" + found + "' instead of " + (expected > 0 && index == expected
            ? "record delimiter '" + shown(recordDelimiter) : "field delimiter '" + shown(fieldDelimiter)) + "'";
        if (width > 0 && index + 1 + fieldDelimitersLeft() > width) {
            return widthRefusal("error caused more fields in data than fields in header.\n" + detail, atLine,
                atColumn, row, startLine, index);
        }
        return parseRefusal(detail, atLine, atColumn, row, startLine, index);
    }

    /**
     * How many field delimiters the rest of the record holds, taking it. The rest of the refused field is read as
     * unenclosed, and each field after it as enclosed only when it starts with the enclosure.
     */
    private int fieldDelimitersLeft() throws IOException {
        int count = 0;
        boolean inside = false;
        boolean fieldStart = false;
        while (peek(0) >= 0 && (inside || recordDelimiterLength() == 0)) {
            final int field = inside ? 0 : fieldDelimiterLength();
            if (field > 0) {
                count++;
                consume(field);
                fieldStart = true;
                continue;
            }
            final int c = take();
            if (enclosure != null && c == enclosure.charValue()) {
                if (fieldStart) {
                    inside = true;
                } else if (inside && peek(0) == enclosure.charValue()) {
                    consume(1);
                } else if (inside) {
                    inside = false;
                }
            }
            fieldStart = false;
        }
        return count;
    }

    /** A refusal of a record's width, in the header's words. */
    private RuntimeException widthRefusal(final String detail, final int atLine, final int atColumn, final long row,
                                          final int startLine, final int index) {
        return new RuntimeException("Error with CSV header: " + detail + "\n  File '" + relativeName + "', line "
            + atLine + ", character " + atColumn + "\n  Row " + row + startingAt(atLine, startLine) + ", column "
            + columnReference(index) + "\n  File '" + relativeName + "'" + HEADER_TAIL);
    }

    /** A refusal of a record that does not parse. */
    private RuntimeException parseRefusal(final String detail, final int atLine, final int atColumn, final long row,
                                          final int startLine, final int index) {
        final String where = "\n  File '" + relativeName + "', line " + atLine + ", character " + atColumn;
        final String at = detail + where + "\n  Row " + row + startingAt(atLine, startLine) + ", column "
            + columnReference(index);
        return new RuntimeException(stagedQuery ? at
            : at + where + "\n  Row 0 starts at line 0, column " + columnReference(index));
    }

    private static String startingAt(final int atLine, final int startLine) {
        return atLine == startLine ? "" : " starts at line " + startLine;
    }

    private String columnReference(final int index) {
        return "\"TRANSIENT_STAGE_TABLE\"" + (index == 1 || stagedQuery ? "[\"$" + index + "\":" + index + "]"
            : "[" + index + "]");
    }

    /** A delimiter as a refusal spells it, a newline, a carriage return and a tab escaped. */
    private static String shown(final String delimiter) {
        if (delimiter == null) {
            return "";
        }
        return delimiter.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private int fieldDelimiterLength() throws IOException {
        return fieldDelimiter != null && lookingAt(fieldDelimiter) ? fieldDelimiter.length() : 0;
    }

    private int recordDelimiterLength() throws IOException {
        if (recordDelimiter == null) {
            return 0;
        }
        if (lookingAt(recordDelimiter)) {
            return recordDelimiter.length();
        }
        return "\n".equals(recordDelimiter) && lookingAt("\r\n") ? 2 : 0;
    }

    private boolean lookingAt(final String text) throws IOException {
        for (int i = 0; i < text.length(); i++) {
            if (peek(i) != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** The character {@code offset} places ahead of the cursor, or -1 past the end of the file. */
    private int peek(final int offset) throws IOException {
        while (ahead.length() <= offset && !exhausted) {
            final int c = source.read();
            if (c < 0) {
                exhausted = true;
            } else {
                ahead.append((char) c);
            }
        }
        return offset < ahead.length() ? ahead.charAt(offset) : -1;
    }

    /** The character at the cursor, moving past it; -1 at the end of the file. */
    private int take() throws IOException {
        final int c = peek(0);
        if (c >= 0) {
            ahead.deleteCharAt(0);
            if (c == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return c;
    }

    private void consume(final int count) throws IOException {
        for (int i = 0; i < count; i++) {
            take();
        }
    }

    private String taken(final int count) throws IOException {
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append((char) take());
        }
        return text.toString();
    }
}
