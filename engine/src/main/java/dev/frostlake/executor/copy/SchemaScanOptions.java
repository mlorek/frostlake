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

import dev.frostlake.metastore.model.FileFormat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How INFER_SCHEMA reads the staged files of one call: the file format's type and options, and the call's own
 * IGNORE_CASE, MAX_RECORDS_PER_FILE and KIND.
 *
 * <p>A CSV field is read the way the file format says (see {@link FileFormatOptions}) — its field and record
 * delimiters, its optional enclosure and the two escape characters, TRIM_SPACE, NULL_IF (by default {@code \N})
 * and EMPTY_FIELD_AS_NULL (by default TRUE) — and PARSE_HEADER takes the column names from the first record,
 * where SKIP_HEADER only skips lines.
 */
public final class SchemaScanOptions {

    private final FileFormatOptions format;
    private final Map<String, String> formatOptions;
    private final boolean iceberg;
    private final boolean ignoreCase;
    private final long maxRecordsPerFile;

    /**
     * The options of one call.
     *
     * @param fileFormat the file format the files are read by
     * @param iceberg whether the call asked for {@code KIND => 'ICEBERG'}
     * @param ignoreCase whether the call asked for {@code IGNORE_CASE => TRUE}
     * @param maxRecordsPerFile the records read of each CSV or JSON file, 0 for all of them
     */
    public SchemaScanOptions(final FileFormat fileFormat, final boolean iceberg, final boolean ignoreCase,
                             final long maxRecordsPerFile) {
        this.format = new FileFormatOptions();
        this.format.takeFormat(fileFormat);
        this.formatOptions = new LinkedHashMap<String, String>(fileFormat.getOptions());
        this.iceberg = iceberg;
        this.ignoreCase = ignoreCase;
        this.maxRecordsPerFile = maxRecordsPerFile;
    }

    /** The effective format the files are read by. */
    public FileFormatOptions format() {
        return format;
    }

    /** The file format's TYPE. */
    public String formatType() {
        return format.getType();
    }

    /** The file format's options by upper-cased name, as the format keeps them. */
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

    int skipHeader() {
        return format.getSkipHeader();
    }

    boolean parseHeader() {
        return format.parsesHeader();
    }

    boolean trimSpace() {
        return format.trimsSpace();
    }

    boolean stripOuterArray() {
        return format.stripsOuterArray();
    }

    /** Whether a JSON object may name a key twice, its last value kept. */
    boolean allowDuplicate() {
        return format.allowsDuplicate();
    }
}
