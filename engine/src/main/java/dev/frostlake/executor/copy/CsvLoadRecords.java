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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How a COPY load splits a delimited file, as a staged-file query splits it (see {@link CsvRecordReader}).
 *
 * <ul>
 *   <li>A record ends at the RECORD_DELIMITER: a line break by default, any other text as written, or the end of the
 *       file under NONE. A delimiter inside an enclosed field does not end the record, a line break included.</li>
 *   <li>A field is enclosed only when it starts with the FIELD_OPTIONALLY_ENCLOSED_BY character, after spaces under
 *       TRIM_SPACE: a quote inside an unenclosed field is a character of the value. Inside an enclosure a doubled
 *       enclosure is one character and the ESCAPE character takes the next one, so neither closes it. Outside one,
 *       the ESCAPE_UNENCLOSED_FIELD character takes the next one, so an escaped delimiter ends nothing; an escaped
 *       line break ends the line as before, and the load carries the record on into the next one.</li>
 *   <li>SKIP_HEADER skips line-break-delimited lines, whatever the record delimiter (documented).</li>
 *   <li>A FIELD_DELIMITER of several characters divides fields as one character would. The load's splitter reads
 *       one character, so each such delimiter outside an enclosure stands in as U+FDD0, a noncharacter that no
 *       text holds, and an escaped one is put back in the value. Under NONE a record is one field.</li>
 * </ul>
 */
final class CsvLoadRecords {

    /** Stands in for a field delimiter of several characters, or for none at all: no text holds it. */
    static final char STANDS_IN = '﷐';

    private final BufferedReader reader;
    /** The text between two records, null for a line break, NONE for none. */
    private final String recordDelimiter;
    private final Character enclosure;
    /** The text between two fields, null for NONE. */
    private final String fieldDelimiter;
    private final Character escapeEnclosed;
    private final Character escapeUnenclosed;
    private final boolean trimSpace;
    private boolean ended;

    /**
     * @param reader           the file's characters
     * @param recordDelimiter  the format's RECORD_DELIMITER, null for the default line break
     * @param enclosure        FIELD_OPTIONALLY_ENCLOSED_BY, or null
     * @param fieldDelimiter   the format's FIELD_DELIMITER, null for the default comma
     * @param escapeEnclosed   ESCAPE, which takes the next character inside an enclosure, or null
     * @param escapeUnenclosed ESCAPE_UNENCLOSED_FIELD, which takes the next character outside one, or null
     * @param trimSpace        TRIM_SPACE, under which spaces may come before a field's enclosure
     */
    CsvLoadRecords(final BufferedReader reader, final String recordDelimiter, final Character enclosure,
                   final String fieldDelimiter, final Character escapeEnclosed, final Character escapeUnenclosed,
                   final boolean trimSpace) {
        this.reader = reader;
        this.recordDelimiter = byLines(recordDelimiter) ? null : recordDelimiter;
        this.enclosure = enclosure;
        this.fieldDelimiter = fieldDelimiter == null || fieldDelimiter.isEmpty() ? ","
            : "NONE".equals(fieldDelimiter.toUpperCase(Locale.ROOT)) ? null : fieldDelimiter;
        this.escapeEnclosed = escapeEnclosed;
        this.escapeUnenclosed = escapeUnenclosed;
        this.trimSpace = trimSpace;
    }

    /** Whether a RECORD_DELIMITER is the line break a file is read by line with. */
    static boolean byLines(final String recordDelimiter) {
        return recordDelimiter == null || recordDelimiter.isEmpty() || "\n".equals(recordDelimiter)
            || "\r\n".equals(recordDelimiter);
    }

    /**
     * The next record, without its delimiter; null at the end of the file.
     *
     * @return the record
     * @throws IOException when the file cannot be read
     */
    String next() throws IOException {
        if (ended) {
            return null;
        }
        final boolean whole = recordDelimiter != null && "NONE".equals(recordDelimiter.toUpperCase(Locale.ROOT));
        final StringBuilder record = new StringBuilder();
        boolean enclosed = false;
        boolean fieldStart = true;
        int read = reader.read();
        if (read < 0) {
            ended = true;
            return null;
        }
        while (read >= 0) {
            final char c = (char) read;
            if (enclosed) {
                record.append(c);
                if (escapeEnclosed != null && c == escapeEnclosed.charValue()) {
                    final int escaped = reader.read();
                    if (escaped >= 0) {
                        record.append((char) escaped);
                    }
                } else if (c == enclosure.charValue()) {
                    if (peek() == enclosure.charValue()) {
                        record.append((char) reader.read());
                    } else {
                        enclosed = false;
                    }
                }
                read = reader.read();
                continue;
            }
            if (recordDelimiter == null && (c == '\n' || c == '\r')) {
                // A line ends as BufferedReader.readLine ends one: at a newline, a carriage return, or both.
                if (c == '\r' && peek() == '\n') {
                    reader.read();
                }
                return record.toString();
            }
            if (fieldStart && enclosure != null && c == enclosure.charValue()) {
                enclosed = true;
                fieldStart = false;
                record.append(c);
                read = reader.read();
                continue;
            }
            record.append(c);
            if (escapeUnenclosed != null && c == escapeUnenclosed.charValue()) {
                // The escaped character is the value's own; an escaped line break still ends the line.
                final int escaped = peek();
                if (escaped >= 0 && !(recordDelimiter == null && (escaped == '\n' || escaped == '\r'))) {
                    record.append((char) reader.read());
                }
                fieldStart = false;
                read = reader.read();
                continue;
            }
            if (!whole && recordDelimiter != null && endsWith(record, recordDelimiter)) {
                record.setLength(record.length() - recordDelimiter.length());
                return record.toString();
            }
            if (fieldDelimiter != null && endsWith(record, fieldDelimiter)) {
                fieldStart = true;
            } else if (!(fieldStart && trimSpace && c == ' ')) {
                fieldStart = false;
            }
            read = reader.read();
        }
        ended = true;
        return record.toString();
    }

    /** The next character without reading it, or -1 at the end of the file. */
    private int peek() throws IOException {
        reader.mark(1);
        final int next = reader.read();
        reader.reset();
        return next;
    }

    private static boolean endsWith(final StringBuilder record, final String delimiter) {
        final int start = record.length() - delimiter.length();
        if (start < 0) {
            return false;
        }
        for (int i = 0; i < delimiter.length(); i++) {
            if (record.charAt(start + i) != delimiter.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * How many line breaks a record holds inside its enclosed fields, which carry the file's line count on past it.
     *
     * @param record the record
     * @return the number of newlines in it
     */
    static int lineBreaks(final String record) {
        int breaks = 0;
        for (int i = 0; i < record.length(); i++) {
            if (record.charAt(i) == '\n') {
                breaks++;
            }
        }
        return breaks;
    }

    /**
     * Skips one line, through its line break, as SKIP_HEADER skips one.
     *
     * @return the line skipped, without its line break; null at the end of the file
     * @throws IOException when the file cannot be read
     */
    String skipLine() throws IOException {
        if (recordDelimiter == null) {
            return reader.readLine();
        }
        final StringBuilder line = new StringBuilder();
        int read = reader.read();
        if (read < 0) {
            return null;
        }
        while (read >= 0 && read != '\n') {
            line.append((char) read);
            read = reader.read();
        }
        return line.toString();
    }

    /** How many bytes the delimiter after a record takes in the file. */
    long terminatorBytes() {
        return recordDelimiter == null ? 1L : recordDelimiter.getBytes(StandardCharsets.UTF_8).length;
    }

    /** Whether a record may carry on past an escaped line break, which only a file read by line has. */
    boolean byLines() {
        return recordDelimiter == null;
    }

    /**
     * The character the load's splitter divides fields on: the FIELD_DELIMITER itself when it is one character, a
     * comma when none is given, and {@link #STANDS_IN} for NONE or a delimiter of several characters.
     *
     * @param fieldDelimiter the format's FIELD_DELIMITER
     * @return the character
     */
    static char splitCharacter(final String fieldDelimiter) {
        if (fieldDelimiter == null || fieldDelimiter.isEmpty()) {
            return ',';
        }
        return fieldDelimiter.length() == 1 ? fieldDelimiter.charAt(0) : STANDS_IN;
    }

    /** Whether a FIELD_DELIMITER stands in as {@link #STANDS_IN}. */
    private static boolean standsIn(final String fieldDelimiter) {
        return fieldDelimiter != null && fieldDelimiter.length() > 1
            && !"NONE".equals(fieldDelimiter.toUpperCase(Locale.ROOT));
    }

    /**
     * A record with each field delimiter of several characters outside an enclosure written as {@link #STANDS_IN}.
     *
     * @param record         the record
     * @param fieldDelimiter the format's FIELD_DELIMITER
     * @param enclosure      FIELD_OPTIONALLY_ENCLOSED_BY, or null
     * @param escapeEnclosed ESCAPE, which takes the next character inside an enclosure, or null
     * @return the record the splitter reads
     */
    static String translated(final String record, final String fieldDelimiter, final Character enclosure,
                             final Character escapeEnclosed) {
        if (!standsIn(fieldDelimiter) || record.indexOf(fieldDelimiter) < 0) {
            return record;
        }
        final StringBuilder out = new StringBuilder(record.length());
        boolean enclosed = false;
        for (int i = 0; i < record.length(); i++) {
            final char c = record.charAt(i);
            if (enclosed && escapeEnclosed != null && c == escapeEnclosed.charValue() && i + 1 < record.length()) {
                out.append(c).append(record.charAt(i + 1));
                i++;
            } else if (enclosure != null && c == enclosure.charValue()) {
                enclosed = !enclosed;
                out.append(c);
            } else if (!enclosed && record.startsWith(fieldDelimiter, i)) {
                out.append(STANDS_IN);
                i += fieldDelimiter.length() - 1;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Fields with an escaped field delimiter of several characters written back as itself.
     *
     * @param fields         the fields the splitter read
     * @param fieldDelimiter the format's FIELD_DELIMITER
     * @return the fields
     */
    static List<String> restored(final List<String> fields, final String fieldDelimiter) {
        if (!standsIn(fieldDelimiter)) {
            return fields;
        }
        final List<String> out = new ArrayList<String>(fields.size());
        for (final String field : fields) {
            out.add(field == null || field.indexOf(STANDS_IN) < 0 ? field
                : field.replace(String.valueOf(STANDS_IN), fieldDelimiter));
        }
        return out;
    }
}
