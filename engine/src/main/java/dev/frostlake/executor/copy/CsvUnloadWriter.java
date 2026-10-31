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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.Decfloats;
import dev.frostlake.values.VariantValue;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * Writes rows as the CSV text an unload stages, by the format's options (live-verified, each line):
 *
 * <pre>
 *   NULL                       \\N         NULL_IF's first value; "\N" in an enclosed column; nothing for NULL_IF = ()
 *   a,b   a\b                  a\,b  a\\b  the delimiters and the escape itself, behind ESCAPE_UNENCLOSED_FIELD
 *   a,b   a"b   (enclosed)     "a,b" "a""b"   the enclosure doubled, or behind ESCAPE; nothing else escaped
 *   ''                         refused     unless enclosed (""), or EMPTY_FIELD_AS_NULL = FALSE (nothing)
 *   1.50  1.5::FLOAT  TRUE     1.50 1.5 true   never enclosed
 *   a date, a time, a timestamp, a binary, a VARIANT   enclosed like a text when an enclosure is set
 * </pre>
 *
 * <p>A delimiter of several characters is not escaped in the data, and a header's names are written as they are,
 * enclosed when an enclosure is set.
 */
final class CsvUnloadWriter {

    private static final String EMPTY_STRING_REFUSAL = "Cannot unload empty string without file format option"
        + " field_optionally_enclosed_by being specified.";

    private final FileFormatOptions options;
    private final BuiltInFunction toText;

    /**
     * A writer for one unload.
     *
     * @param options the unload's options
     * @param toText  the function that casts a value to text, TO_VARCHAR
     */
    CsvUnloadWriter(final FileFormatOptions options, final BuiltInFunction toText) {
        this.options = options;
        this.toText = toText;
    }

    /**
     * The file's text.
     *
     * @param data   the rows
     * @param header whether a record of the column names leads
     * @return the text, every record ended by the record delimiter
     */
    String text(final ResultSet data, final boolean header) {
        final String fieldEnd = options.getFieldDelimiter() == null ? "" : options.getFieldDelimiter();
        final String recordEnd = options.getRecordDelimiter() == null ? "" : options.getRecordDelimiter();
        final List<ResultSetColumn> columns = data.getColumns();
        final StringBuilder text = new StringBuilder();
        if (header) {
            for (int c = 0; c < columns.size(); c++) {
                text.append(c == 0 ? "" : fieldEnd);
                text.append(options.getEnclosure() == null ? columns.get(c).getName() : enclosed(columns.get(c).getName()));
            }
            text.append(recordEnd);
        }
        for (final Row row : data.getRows()) {
            for (int c = 0; c < row.getValues().size(); c++) {
                text.append(c == 0 ? "" : fieldEnd);
                text.append(field(row.getValue(c), c < columns.size() ? columns.get(c).getDataType() : null));
            }
            text.append(recordEnd);
        }
        return text.toString();
    }

    private String field(final Object value, final DataType type) {
        if (value == null) {
            if (options.getNullIf().isEmpty()) {
                return "";
            }
            final String marker = options.getNullIf().get(0);
            if (marker.isEmpty()) {
                return emptyText();
            }
            // A NULL is written as a value of its column would be: enclosed where the column's values are.
            return options.getEnclosure() != null && isEnclosedKind(type) ? enclosed(marker) : unenclosed(marker);
        }
        if (value instanceof Number && NumericType.isDecfloat(type)) {
            // A DECFLOAT's text is its own, as a cast to VARCHAR writes it: 1e16384, not its 16385 digits.
            return unenclosed(Decfloats.text(Decfloats.of((Number) value, "TO_DECFLOAT")));
        }
        if (value instanceof Number || value instanceof Boolean) {
            return unenclosed(String.valueOf(toText.evaluate(Arrays.asList(value))));
        }
        final String written = written(value, type);
        if (options.getEnclosure() != null) {
            return enclosed(written);
        }
        return written.isEmpty() ? emptyText() : unenclosed(written);
    }

    /**
     * Whether a column's values are enclosed when an enclosure is set: a text, a date, a time, a timestamp, a
     * binary and a semi-structured value are, a number and a boolean are not — nor is a bare NULL's column, which
     * has no type of its own.
     */
    private static boolean isEnclosedKind(final DataType type) {
        if (type instanceof StringType) {
            return ((StringType) type).getMaxLength() != 0;
        }
        return type instanceof DateTimeType || type instanceof BinaryType || type instanceof VariantType
            || type instanceof ArrayType || type instanceof ObjectType;
    }

    /** A value's text: a date, a time, a timestamp and a binary by the format the options name, if any. */
    private String written(final Object value, final DataType type) {
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof VariantValue) {
            return ((VariantValue) value).text();
        }
        String format = null;
        if (type instanceof BinaryType || value instanceof BinaryValue) {
            format = options.getBinaryFormat();
        } else if (value instanceof LocalDate) {
            format = options.getDateFormat();
        } else if (value instanceof LocalTime) {
            format = options.getTimeFormat();
        } else if (type instanceof DateTimeType) {
            format = options.getTimestampFormat();
        } else if (type instanceof VariantType || type instanceof ArrayType || type instanceof ObjectType) {
            format = null;
        }
        final Object text = format == null ? toText.evaluate(Arrays.asList(value))
            : toText.evaluate(Arrays.asList(value, format));
        return String.valueOf(text);
    }

    /** An empty text where no enclosure can tell it from a NULL: written only when the format reads it as one. */
    private String emptyText() {
        if (options.getEnclosure() != null) {
            return enclosed("");
        }
        if (options.isEmptyFieldAsNull()) {
            throw new RuntimeException(EMPTY_STRING_REFUSAL);
        }
        return "";
    }

    private String enclosed(final String value) {
        final char quote = options.getEnclosure().charValue();
        final StringBuilder text = new StringBuilder().append(quote);
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == quote) {
                text.append(options.getEscape() != null ? options.getEscape().charValue() : quote);
            }
            text.append(c);
        }
        return text.append(quote).toString();
    }

    private String unenclosed(final String value) {
        if (options.getEscapeUnenclosed() == null) {
            return value;
        }
        final char escape = options.getEscapeUnenclosed().charValue();
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == escape || isThe(options.getFieldDelimiter(), c) || isThe(options.getRecordDelimiter(), c)) {
                text.append(escape);
            }
            text.append(c);
        }
        return text.toString();
    }

    /** Whether a delimiter is this one character: a delimiter of several is never escaped. */
    private static boolean isThe(final String delimiter, final char c) {
        return delimiter != null && delimiter.length() == 1 && delimiter.charAt(0) == c;
    }
}
