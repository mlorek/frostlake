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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How INFER_SCHEMA reads the staged files of one call: the file format's type and options, and the call's own
 * IGNORE_CASE, MAX_RECORDS_PER_FILE and KIND.
 *
 * <p>A CSV field is read the way the file format says — its field and record delimiters, its optional enclosure
 * and the two escape characters, TRIM_SPACE, NULL_IF (by default {@code \N}) and EMPTY_FIELD_AS_NULL (by default
 * TRUE) — and PARSE_HEADER takes the column names from the first record, where SKIP_HEADER only skips lines.
 */
public final class SchemaScanOptions {

    /** The NULL_IF a CSV format applies when it names none. */
    private static final List<String> DEFAULT_NULL_IF = Collections.unmodifiableList(Arrays.asList("\\N"));

    private final String formatType;
    private final Map<String, String> formatOptions;
    private final boolean iceberg;
    private final boolean ignoreCase;
    private final long maxRecordsPerFile;

    /**
     * The options of one call.
     *
     * @param formatType the file format's TYPE, upper-cased
     * @param formatOptions the file format's options by upper-cased name, values as stored
     * @param iceberg whether the call asked for {@code KIND => 'ICEBERG'}
     * @param ignoreCase whether the call asked for {@code IGNORE_CASE => TRUE}
     * @param maxRecordsPerFile the records read of each CSV or JSON file, 0 for all of them
     */
    public SchemaScanOptions(final String formatType, final Map<String, String> formatOptions, final boolean iceberg,
                             final boolean ignoreCase, final long maxRecordsPerFile) {
        this.formatType = formatType.toUpperCase(Locale.ROOT);
        this.formatOptions = new LinkedHashMap<String, String>(formatOptions);
        this.iceberg = iceberg;
        this.ignoreCase = ignoreCase;
        this.maxRecordsPerFile = maxRecordsPerFile;
    }

    /** The file format's TYPE. */
    public String formatType() {
        return formatType;
    }

    /** The file format's options by upper-cased name. */
    public Map<String, String> formatOptions() {
        return Collections.unmodifiableMap(formatOptions);
    }

    /** Whether types that cannot be merged are refused rather than widened. */
    public boolean iceberg() {
        return iceberg;
    }

    /** Whether column names are folded to upper case and matched regardless of case. */
    public boolean ignoreCase() {
        return ignoreCase;
    }

    /** Whether the call reads every record of a file. */
    boolean readsAllRecords() {
        return maxRecordsPerFile <= 0;
    }

    /** The records read of each CSV or JSON file when not all of them. */
    long maxRecordsPerFile() {
        return maxRecordsPerFile;
    }

    /** The name a column is reported under. */
    String reportedName(final String name) {
        return ignoreCase ? name.toUpperCase(Locale.ROOT) : name;
    }

    /** The text between two fields, of one or more characters; null for {@code NONE}. */
    String fieldDelimiter() {
        return delimiter("FIELD_DELIMITER", ",");
    }

    /** The text between two records, of one or more characters; null for {@code NONE}. */
    String recordDelimiter() {
        return delimiter("RECORD_DELIMITER", "\n");
    }

    Character enclosure() {
        return character("FIELD_OPTIONALLY_ENCLOSED_BY", null);
    }

    /** The character that takes the next one literally inside an enclosed field: ESCAPE, NONE by default. */
    Character escape() {
        return character("ESCAPE", null);
    }

    /** The character that takes a delimiter, or itself, literally in an unenclosed field: a backslash by default. */
    Character escapeUnenclosed() {
        return character("ESCAPE_UNENCLOSED_FIELD", Character.valueOf('\\'));
    }

    private String delimiter(final String name, final String absent) {
        final String delimiter = option(name);
        if (delimiter == null || delimiter.isEmpty()) {
            return absent;
        }
        return "NONE".equalsIgnoreCase(delimiter) ? null : delimiter;
    }

    private Character character(final String name, final Character absent) {
        final String value = option(name);
        if (value == null) {
            return absent;
        }
        return value.isEmpty() || "NONE".equalsIgnoreCase(value) ? null : Character.valueOf(value.charAt(0));
    }

    int skipHeader() {
        final String lines = option("SKIP_HEADER");
        return lines == null ? 0 : Integer.parseInt(lines.trim());
    }

    boolean parseHeader() {
        return "TRUE".equalsIgnoreCase(option("PARSE_HEADER"));
    }

    boolean trimSpace() {
        return "TRUE".equalsIgnoreCase(option("TRIM_SPACE"));
    }

    boolean emptyFieldAsNull() {
        return !"FALSE".equalsIgnoreCase(option("EMPTY_FIELD_AS_NULL"));
    }

    boolean stripOuterArray() {
        return "TRUE".equalsIgnoreCase(option("STRIP_OUTER_ARRAY"));
    }

    /** Whether a JSON object may name a key twice, its last value kept. */
    boolean allowDuplicate() {
        return "TRUE".equalsIgnoreCase(option("ALLOW_DUPLICATE"));
    }

    List<String> nullIf() {
        final String stored = option("NULL_IF");
        if (stored == null) {
            return DEFAULT_NULL_IF;
        }
        // CREATE FILE FORMAT keeps a NULL_IF list comma-joined.
        final List<String> tokens = new ArrayList<String>();
        if (!stored.isEmpty()) {
            tokens.addAll(Arrays.asList(stored.split(",", -1)));
        }
        return tokens;
    }

    private String option(final String name) {
        return formatOptions.get(name);
    }
}
