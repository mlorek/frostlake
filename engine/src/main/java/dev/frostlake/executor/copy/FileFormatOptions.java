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

import dev.frostlake.executor.FileFormatClause;
import dev.frostlake.executor.SessionInputFormats;
import dev.frostlake.metastore.model.FileFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The options a staged file is read and written by - a load's, a staged-file query's, INFER_SCHEMA's and an
 * unload's alike - at the account's defaults until a format says otherwise:
 *
 * <pre>
 *   FIELD_DELIMITER                 ,        NONE: a record is one field
 *   RECORD_DELIMITER                newline  NONE: a file is one record
 *   FIELD_OPTIONALLY_ENCLOSED_BY    NONE
 *   ESCAPE                          NONE     inside an enclosure
 *   ESCAPE_UNENCLOSED_FIELD         \        outside one
 *   NULL_IF                         ('\N')   the first value is what an unload writes for NULL
 *   EMPTY_FIELD_AS_NULL             TRUE
 *   SKIP_HEADER                     0        PARSE_HEADER FALSE
 *   TRIM_SPACE                      FALSE    SKIP_BLANK_LINES FALSE
 *   ERROR_ON_COLUMN_COUNT_MISMATCH  TRUE
 *   STRIP_OUTER_ARRAY               FALSE    STRIP_NULL_VALUES FALSE, ALLOW_DUPLICATE FALSE (JSON)
 *   STRIP_OUTER_ELEMENT             FALSE    DISABLE_AUTO_CONVERT FALSE, PRESERVE_SPACE FALSE (XML)
 *   REPLACE_INVALID_CHARACTERS      FALSE    IGNORE_UTF8_ERRORS FALSE, its other spelling
 * </pre>
 *
 * <p>The options come from a stage's own format, from a named format and from the statement's inline list, each
 * later one overriding the earlier; every value arrives decoded, a bare NONE as the word, and NULL_IF as a format
 * keeps it (see {@link FileFormat#nullIfValues}).
 */
public final class FileFormatOptions {

    private static final String NONE = "NONE";

    private String type = "CSV";
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
    private String encoding;
    private boolean replaceInvalidCharacters;
    private int skipHeader;
    private boolean parseHeader;
    private boolean trimSpace;
    private boolean skipBlankLines;
    private boolean errorOnColumnCountMismatch = true;
    private boolean stripOuterArray;
    private boolean stripNullValues;
    private boolean allowDuplicate;
    private boolean ignoreUtf8Errors;
    private boolean stripOuterElement;
    private boolean disableAutoConvert;
    private boolean preserveSpace;

    /** DATE_FORMAT, TIME_FORMAT, TIMESTAMP_FORMAT and BINARY_FORMAT as written, by the input format each stands for. */
    private final Map<String, String> inputFormats = new HashMap<>();

    /**
     * Take a format whole: its type and every option.
     *
     * @param format a named format, or the one a stage keeps
     */
    public void takeFormat(final FileFormat format) {
        take("TYPE", format.getType());
        takeAll(format.getOptions());
    }

    /**
     * Take what a statement's inline list wrote: its TYPE where it wrote one, and its options.
     *
     * @param clause the list as read
     */
    public void takeWritten(final FileFormatClause clause) {
        if (clause.writtenType() != null) {
            take("TYPE", clause.writtenType());
        }
        takeAll(clause.format().getOptions());
    }

    /**
     * Take every option of a named format or of a stage's own.
     *
     * @param options the options by upper-cased name
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
            case "NULL_IF":
                nullIf = FileFormat.nullIfValues(value);
                break;
            case "EMPTY_FIELD_AS_NULL":
                emptyFieldAsNull = !"FALSE".equalsIgnoreCase(value);
                break;
            case "DATE_FORMAT":
                dateFormat = automatic(value);
                inputFormats.put(SessionInputFormats.DATE, value);
                break;
            case "TIME_FORMAT":
                timeFormat = automatic(value);
                inputFormats.put(SessionInputFormats.TIME, value);
                break;
            case "TIMESTAMP_FORMAT":
                timestampFormat = automatic(value);
                inputFormats.put(SessionInputFormats.TIMESTAMP, value);
                break;
            case "BINARY_FORMAT":
                binaryFormat = value.toUpperCase(Locale.ROOT);
                inputFormats.put(SessionInputFormats.BINARY, value);
                break;
            case "COMPRESSION":
                compression = value.toUpperCase(Locale.ROOT);
                break;
            case "FILE_EXTENSION":
                fileExtension = none ? null : value;
                break;
            case "ENCODING":
                encoding = value;
                break;
            case "REPLACE_INVALID_CHARACTERS":
                replaceInvalidCharacters = "TRUE".equalsIgnoreCase(value);
                break;
            case "SKIP_HEADER":
                skipHeader = Integer.parseInt(value.trim());
                break;
            case "PARSE_HEADER":
                parseHeader = "TRUE".equalsIgnoreCase(value);
                break;
            case "TRIM_SPACE":
                trimSpace = "TRUE".equalsIgnoreCase(value);
                break;
            case "SKIP_BLANK_LINES":
                skipBlankLines = "TRUE".equalsIgnoreCase(value);
                break;
            case "ERROR_ON_COLUMN_COUNT_MISMATCH":
                errorOnColumnCountMismatch = !"FALSE".equalsIgnoreCase(value);
                break;
            case "STRIP_OUTER_ARRAY":
                stripOuterArray = "TRUE".equalsIgnoreCase(value);
                break;
            case "STRIP_NULL_VALUES":
                stripNullValues = "TRUE".equalsIgnoreCase(value);
                break;
            case "ALLOW_DUPLICATE":
                allowDuplicate = "TRUE".equalsIgnoreCase(value);
                break;
            case "IGNORE_UTF8_ERRORS":
                ignoreUtf8Errors = "TRUE".equalsIgnoreCase(value);
                break;
            case "STRIP_OUTER_ELEMENT":
                stripOuterElement = "TRUE".equalsIgnoreCase(value);
                break;
            case "DISABLE_AUTO_CONVERT":
                disableAutoConvert = "TRUE".equalsIgnoreCase(value);
                break;
            case "PRESERVE_SPACE":
                preserveSpace = "TRUE".equalsIgnoreCase(value);
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
     * Settle a record's fields as the account reads them, in this order: TRIM_SPACE trims an unenclosed field
     * whole and leaves an enclosed one as it is, a field that reads as one of NULL_IF's values is NULL, and so is an
     * unenclosed empty one under EMPTY_FIELD_AS_NULL (live-verified: an enclosed empty field is the empty string).
     *
     * @param fields   the record's fields, settled in place; a null is a NULL
     * @param enclosed whether each field was enclosed, shorter than the fields where the rest were not
     */
    public void settle(final List<String> fields, final List<Boolean> enclosed) {
        for (int i = 0; i < fields.size(); i++) {
            String value = fields.get(i);
            if (value == null) {
                continue;
            }
            final boolean fieldEnclosed = i < enclosed.size() && enclosed.get(i).booleanValue();
            if (trimSpace && !fieldEnclosed) {
                value = value.trim();
            }
            if (nullIf.contains(value)) {
                value = null;
            } else if (emptyFieldAsNull && value.isEmpty() && !fieldEnclosed) {
                value = null;
            }
            fields.set(i, value);
        }
    }

    /** The format's TYPE, upper-cased; CSV until a format says otherwise. */
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

    /** ENCODING as written, or null when no format names one: UTF-8 then. */
    public String getEncoding() {
        return encoding;
    }

    /**
     * Whether REPLACE_INVALID_CHARACTERS = TRUE, or IGNORE_UTF8_ERRORS = TRUE, its other spelling, reads an unreadable
     * byte sequence as U+FFFD instead of refusing (live-verified for XML).
     */
    public boolean replacesInvalidCharacters() {
        return replaceInvalidCharacters || ignoreUtf8Errors;
    }

    /** Whether an XML file's records are the elements its outer elements hold (STRIP_OUTER_ELEMENT). */
    public boolean stripsOuterElement() {
        return stripOuterElement;
    }

    /** Whether an XML file's text stays text rather than turning into numbers and booleans (DISABLE_AUTO_CONVERT). */
    public boolean disablesAutoConvert() {
        return disableAutoConvert;
    }

    /** Whether an XML file's text keeps its leading and trailing white space (PRESERVE_SPACE). */
    public boolean preservesSpace() {
        return preserveSpace;
    }

    /** SKIP_HEADER: how many lines a file's start is passed over. */
    public int getSkipHeader() {
        return skipHeader;
    }

    /** Whether PARSE_HEADER takes the column names from the first record. */
    public boolean parsesHeader() {
        return parseHeader;
    }

    /** Whether TRIM_SPACE trims an unenclosed field. */
    public boolean trimsSpace() {
        return trimSpace;
    }

    /** Whether SKIP_BLANK_LINES passes over a blank line rather than reading it as a record of one empty field. */
    public boolean skipsBlankLines() {
        return skipBlankLines;
    }

    /** Whether ERROR_ON_COLUMN_COUNT_MISMATCH holds a record to its table's width. */
    public boolean checksColumnCount() {
        return errorOnColumnCountMismatch;
    }

    /** Whether a JSON file's outer array opens into one record per element (STRIP_OUTER_ARRAY). */
    public boolean stripsOuterArray() {
        return stripOuterArray;
    }

    /** Whether a JSON record loses its null-valued fields and null array elements (STRIP_NULL_VALUES). */
    public boolean stripsNullValues() {
        return stripNullValues;
    }

    /** Whether a JSON object may name a key twice, its last value kept (ALLOW_DUPLICATE). */
    public boolean allowsDuplicate() {
        return allowDuplicate;
    }

    /**
     * The input formats a load reads its fields' text by - DATE_FORMAT, TIME_FORMAT, TIMESTAMP_FORMAT and
     * BINARY_FORMAT as written, each under the session parameter it stands in for; BINARY_FORMAT is HEX unless one
     * is written (documented).
     *
     * @return the formats by session parameter name
     */
    public Map<String, String> loadInputFormats() {
        final Map<String, String> formats = new HashMap<String, String>();
        formats.put(SessionInputFormats.BINARY, "HEX");
        formats.putAll(inputFormats);
        return formats;
    }
}
