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

import java.nio.charset.Charset;

/**
 * How a COPY load reads a delimited file's records and what it does to a row before the table's constraints judge it
 * (documented): the file is read in the character set its ENCODING names and split at its FIELD_DELIMITER and
 * RECORD_DELIMITER (see {@link CsvLoadRecords}), a byte sequence the set cannot read is
 * replaced by U+FFFD under REPLACE_INVALID_CHARACTERS = TRUE and refuses its record otherwise; a JSON record loses its
 * null-valued fields and null array elements under STRIP_NULL_VALUES = TRUE; each column the
 * statement's column list leaves out takes its DEFAULT, or the next IDENTITY value, as an INSERT that names the same
 * columns gives it; and where the load truncates (TRUNCATECOLUMNS = TRUE, ENFORCE_LENGTH = FALSE) a text longer than
 * its column is cut to the column's length.
 */
final class LoadedRowRules {

    private final Charset charset;
    private final boolean replaceInvalidCharacters;
    private final boolean stripNullValues;
    private final boolean stripOuterArray;
    private final boolean truncateToColumns;
    private final boolean[] omittedColumns;
    private final String storageName;
    private final String fieldDelimiter;
    private final String recordDelimiter;
    private final String matchByColumnName;

    /**
     * @param charset                  the character set a delimited file is read in
     * @param replaceInvalidCharacters whether a byte sequence the set cannot read becomes U+FFFD rather than refusing
     *                                 its record
     * @param stripNullValues          whether a JSON record loses its null-valued fields and null array elements
     * @param stripOuterArray          whether a JSON file's top-level array is one record per element
     * @param truncateToColumns        whether a text longer than its column is cut to the column's length
     * @param omittedColumns           by table position, whether the column list leaves the column out; null for a
     *                                 COPY that writes no column list
     * @param storageName              the table's storage key, for its IDENTITY counter
     * @param fieldDelimiter           the format's FIELD_DELIMITER as written, or null
     * @param recordDelimiter          the format's RECORD_DELIMITER as written, null for a line break
     * @param matchByColumnName        MATCH_BY_COLUMN_NAME as written, or null
     */
    LoadedRowRules(final Charset charset, final boolean replaceInvalidCharacters, final boolean stripNullValues,
                   final boolean stripOuterArray, final boolean truncateToColumns, final boolean[] omittedColumns,
                   final String storageName, final String fieldDelimiter, final String recordDelimiter,
                   final String matchByColumnName) {
        this.charset = charset;
        this.replaceInvalidCharacters = replaceInvalidCharacters;
        this.stripNullValues = stripNullValues;
        this.stripOuterArray = stripOuterArray;
        this.truncateToColumns = truncateToColumns;
        this.omittedColumns = omittedColumns == null ? null : omittedColumns.clone();
        this.storageName = storageName;
        this.fieldDelimiter = fieldDelimiter;
        this.recordDelimiter = recordDelimiter;
        this.matchByColumnName = matchByColumnName;
    }

    Charset charset() {
        return charset;
    }

    boolean replacesInvalidCharacters() {
        return replaceInvalidCharacters;
    }

    boolean stripsNullValues() {
        return stripNullValues;
    }

    boolean stripsOuterArray() {
        return stripOuterArray;
    }

    boolean truncatesToColumns() {
        return truncateToColumns;
    }

    /** Whether the column list leaves the column at this table position out. */
    boolean isOmitted(final int column) {
        return omittedColumns != null && column < omittedColumns.length && omittedColumns[column];
    }

    String storageName() {
        return storageName;
    }

    /** The format's FIELD_DELIMITER as written, or null. */
    String fieldDelimiter() {
        return fieldDelimiter;
    }

    /** The format's RECORD_DELIMITER as written, null for a line break. */
    String recordDelimiter() {
        return recordDelimiter;
    }

    /** MATCH_BY_COLUMN_NAME as written: CASE_SENSITIVE, CASE_INSENSITIVE, NONE, or null when not given. */
    String matchByColumnName() {
        return matchByColumnName;
    }
}
