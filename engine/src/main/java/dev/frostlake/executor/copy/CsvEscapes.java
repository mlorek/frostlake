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

import java.util.ArrayList;
import java.util.List;

/**
 * ESCAPE_UNENCLOSED_FIELD for the readers that split a CSV record with {@link CopyCommandExecutor#parseCsvLine}.
 * Outside an enclosure the escape character takes the field delimiter, the record delimiter or itself literally;
 * before any other character it is an ordinary character and stays (live-verified):
 *
 * <pre>
 *   a\\b    a\b        the escape itself
 *   e\|f    e|f        the delimiter, when the delimiter is |: one field
 *   c\,d    c\,d       not the delimiter here: both characters stay
 *   x\ty    x\ty       no escape sequence is read: a backslash and a t
 * </pre>
 *
 * <p>The splitter knows no escape, so a record is SHIELDED before it is split — the escaped character replaced by
 * a stand-in the splitter passes over, the escape character left where it was so every position in the record
 * still counts as written — and each field is EXPOSED afterwards, the escape dropped and the character put back.
 */
public final class CsvEscapes {

    /** Stands in for an escaped field delimiter. */
    private static final char DELIMITER = '';

    /** Stands in for an escaped escape character. */
    private static final char ESCAPE = '';

    /** Stands in for an escaped record delimiter: the line break two lines were joined over. */
    private static final char RECORD = '';

    private CsvEscapes() {
    }

    /**
     * Shield one record's escaped characters from the splitter.
     *
     * @param record    the record as written
     * @param delimiter the field delimiter
     * @param enclosure FIELD_OPTIONALLY_ENCLOSED_BY, or null
     * @param escape    ESCAPE_UNENCLOSED_FIELD, or null for NONE
     * @return the record, as long as it was
     */
    public static String shielded(final String record, final char delimiter, final Character enclosure,
                                  final Character escape) {
        return shielded(record, delimiter, enclosure, escape, null);
    }

    /**
     * {@link #shielded(String, char, Character, Character)} for a format with an ESCAPE too: inside an enclosure
     * that character takes the next one literally, the enclosure character included, which then closes nothing.
     *
     * @param record         the record as written
     * @param delimiter      the field delimiter
     * @param enclosure      FIELD_OPTIONALLY_ENCLOSED_BY, or null
     * @param escape         ESCAPE_UNENCLOSED_FIELD, or null for NONE
     * @param escapeEnclosed ESCAPE, or null for NONE
     * @return the record, as long as it was
     */
    public static String shielded(final String record, final char delimiter, final Character enclosure,
                                  final Character escape, final Character escapeEnclosed) {
        if (escape == null || record.indexOf(escape.charValue()) < 0) {
            return record;
        }
        final char mark = escape.charValue();
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
            } else if (!enclosed && c == mark && i + 1 < record.length()
                    && (record.charAt(i + 1) == delimiter || record.charAt(i + 1) == mark
                        || record.charAt(i + 1) == '\n')) {
                final char next = record.charAt(i + 1);
                out.append(c).append(next == delimiter ? DELIMITER : next == mark ? ESCAPE : RECORD);
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Join the lines an escaped line break was split over: a line ending in an escape character that nothing
     * escapes carries on in the next.
     *
     * @param lines  a file's lines
     * @param escape ESCAPE_UNENCLOSED_FIELD, or null for NONE
     * @return the records, each of one line or of several joined by their line break
     */
    public static List<String> joined(final List<String> lines, final Character escape) {
        if (escape == null) {
            return lines;
        }
        final List<String> records = new ArrayList<String>(lines.size());
        StringBuilder open = null;
        for (final String line : lines) {
            final String whole = open == null ? line : open.append('\n').append(line).toString();
            if (endsInEscape(line, escape.charValue())) {
                open = new StringBuilder(whole);
            } else {
                open = null;
                records.add(whole);
            }
        }
        if (open != null) {
            records.add(open.toString());
        }
        return records;
    }

    /**
     * Whether a line's record carries on into the next: it ends in an escape character that nothing escapes.
     *
     * @param line   the record read so far
     * @param escape ESCAPE_UNENCLOSED_FIELD, or null for NONE
     * @return true when the line break is escaped
     */
    public static boolean carriesOn(final String line, final Character escape) {
        return escape != null && endsInEscape(line, escape.charValue());
    }

    private static boolean endsInEscape(final String line, final char escape) {
        int trailing = 0;
        while (trailing < line.length() && line.charAt(line.length() - 1 - trailing) == escape) {
            trailing++;
        }
        return trailing % 2 == 1;
    }

    /**
     * Expose the characters {@link #shielded} hid, in every field of a split record.
     *
     * @param fields    the fields, changed in place
     * @param delimiter the field delimiter
     * @param escape    ESCAPE_UNENCLOSED_FIELD, or null for NONE
     * @return the same list
     */
    public static List<String> exposed(final List<String> fields, final char delimiter, final Character escape) {
        if (escape == null) {
            return fields;
        }
        for (int f = 0; f < fields.size(); f++) {
            final String field = fields.get(f);
            if (field == null || !hidesOne(field)) {
                continue;
            }
            final StringBuilder out = new StringBuilder(field.length());
            for (int i = 0; i < field.length(); i++) {
                final char c = field.charAt(i);
                if (c == DELIMITER || c == ESCAPE || c == RECORD) {
                    // The escape character stood right before it.
                    if (out.length() > 0) {
                        out.setLength(out.length() - 1);
                    }
                    out.append(c == DELIMITER ? delimiter : c == ESCAPE ? escape.charValue() : '\n');
                } else {
                    out.append(c);
                }
            }
            fields.set(f, out.toString());
        }
        return fields;
    }

    private static boolean hidesOne(final String field) {
        return field.indexOf(DELIMITER) >= 0 || field.indexOf(ESCAPE) >= 0 || field.indexOf(RECORD) >= 0;
    }
}
