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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The options a CSV file is written and read by, at the account's defaults until a format says otherwise:
 *
 * <pre>
 *   FIELD_DELIMITER               ,        NONE: a record is one field
 *   RECORD_DELIMITER              newline  NONE: a file is one record
 *   FIELD_OPTIONALLY_ENCLOSED_BY  NONE
 *   ESCAPE                        NONE     inside an enclosure
 *   ESCAPE_UNENCLOSED_FIELD       \        outside one
 *   NULL_IF                       ('\N')   the first value is what an unload writes for NULL
 *   EMPTY_FIELD_AS_NULL           TRUE
 * </pre>
 *
 * <p>The options come from a named format, from a stage's own format and from the statement's inline list, each
 * later one overriding the earlier; every value arrives decoded, a bare NONE as the word.
 */
public final class CsvFormatOptions {

    private static final String NONE = "NONE";

    private String fieldDelimiter = ",";
    private String recordDelimiter = "\n";
    private Character enclosure;
    private Character escape;
    private Character escapeUnenclosed = Character.valueOf('\\');
    private List<String> nullIf = Collections.singletonList("\\N");
    private boolean emptyFieldAsNull = true;
    private String dateFormat;
    private String timeFormat;
    private String timestampFormat;
    private String binaryFormat;
    private String compression;
    private String fileExtension;
    private String type;

    /**
     * Take every option of a named format or of a stage's own.
     *
     * @param options the options by upper-cased name, NULL_IF left out
     */
    public void takeAll(final Map<String, String> options) {
        for (final Map.Entry<String, String> option : options.entrySet()) {
            take(option.getKey(), option.getValue());
        }
    }

    /**
     * Take one option.
     *
     * @param name  the option's name
     * @param value its decoded value
     */
    public void take(final String name, final String value) {
        if (name == null || value == null) {
            return;
        }
        final boolean none = NONE.equalsIgnoreCase(value);
        switch (name.toUpperCase(Locale.ROOT)) {
            case "TYPE":
                type = value.toUpperCase(Locale.ROOT);
                break;
            case "FIELD_DELIMITER":
                fieldDelimiter = none ? null : value;
                break;
            case "RECORD_DELIMITER":
                recordDelimiter = none ? null : value;
                break;
            case "FIELD_OPTIONALLY_ENCLOSED_BY":
                enclosure = none || value.isEmpty() ? null : Character.valueOf(value.charAt(0));
                break;
            case "ESCAPE":
                escape = none || value.isEmpty() ? null : Character.valueOf(value.charAt(0));
                break;
            case "ESCAPE_UNENCLOSED_FIELD":
                escapeUnenclosed = none || value.isEmpty() ? null : Character.valueOf(value.charAt(0));
                break;
            case "EMPTY_FIELD_AS_NULL":
                emptyFieldAsNull = !"FALSE".equalsIgnoreCase(value);
                break;
            case "DATE_FORMAT":
                dateFormat = automatic(value);
                break;
            case "TIME_FORMAT":
                timeFormat = automatic(value);
                break;
            case "TIMESTAMP_FORMAT":
                timestampFormat = automatic(value);
                break;
            case "BINARY_FORMAT":
                binaryFormat = value.toUpperCase(Locale.ROOT);
                break;
            case "COMPRESSION":
                compression = value.toUpperCase(Locale.ROOT);
                break;
            case "FILE_EXTENSION":
                fileExtension = none ? null : value;
                break;
            default:
                break;
        }
    }

    /** AUTO leaves a value to the session's own output format. */
    private static String automatic(final String format) {
        return "AUTO".equalsIgnoreCase(format) ? null : format;
    }

    /**
     * Take NULL_IF's values.
     *
     * @param values the values in the order written; an empty list for {@code NULL_IF = ()}
     */
    public void takeNullIf(final List<String> values) {
        if (values != null) {
            nullIf = new ArrayList<String>(values);
        }
    }

    /**
     * Take NULL_IF as a stage lists it: {@code [\\N, NULL]}, a backslash doubled.
     *
     * @param listed the listed text, or null when the stage's format sets none
     */
    public void takeListedNullIf(final String listed) {
        if (listed == null || listed.length() < 2 || listed.charAt(0) != '[') {
            return;
        }
        final List<String> values = new ArrayList<String>();
        final String inner = listed.substring(1, listed.length() - 1);
        if (!inner.isEmpty()) {
            int start = 0;
            for (int i = 0; i <= inner.length(); i++) {
                if (i == inner.length() || inner.startsWith(", ", i)) {
                    values.add(inner.substring(start, i).replace("\\\\", "\\"));
                    start = i + 2;
                    i++;
                }
            }
        }
        nullIf = values;
    }

    public String getType() {
        return type;
    }

    /** The field delimiter, or null for NONE. */
    public String getFieldDelimiter() {
        return fieldDelimiter;
    }

    /** The record delimiter, or null for NONE. */
    public String getRecordDelimiter() {
        return recordDelimiter;
    }

    /** FIELD_OPTIONALLY_ENCLOSED_BY, or null for NONE. */
    public Character getEnclosure() {
        return enclosure;
    }

    /** ESCAPE, or null for NONE. */
    public Character getEscape() {
        return escape;
    }

    /** ESCAPE_UNENCLOSED_FIELD, or null for NONE. */
    public Character getEscapeUnenclosed() {
        return escapeUnenclosed;
    }

    /** NULL_IF's values in the order written. */
    public List<String> getNullIf() {
        return nullIf;
    }

    public boolean isEmptyFieldAsNull() {
        return emptyFieldAsNull;
    }

    /** DATE_FORMAT, or null for the session's own. */
    public String getDateFormat() {
        return dateFormat;
    }

    /** TIME_FORMAT, or null for the session's own. */
    public String getTimeFormat() {
        return timeFormat;
    }

    /** TIMESTAMP_FORMAT, or null for the session's own. */
    public String getTimestampFormat() {
        return timestampFormat;
    }

    /** BINARY_FORMAT upper-cased, or null for the session's own. */
    public String getBinaryFormat() {
        return binaryFormat;
    }

    /** COMPRESSION upper-cased, or null when no format names one. */
    public String getCompression() {
        return compression;
    }

    /** FILE_EXTENSION, or null when no format names one. */
    public String getFileExtension() {
        return fileExtension;
    }
}
