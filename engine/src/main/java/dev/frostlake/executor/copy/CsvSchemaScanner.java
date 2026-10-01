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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The columns one staged CSV file gives INFER_SCHEMA, its records read as {@link CsvRecordReader} splits them.
 *
 * <p>Under PARSE_HEADER the first record names the columns, and every record must carry exactly as many fields:
 * a name that is empty (after TRIM_SPACE) or given twice, checked name by name, or a record short of or past
 * the header, refuses the whole call. Without it the columns are {@code c1}, {@code c2}, … as wide as the widest
 * record, and SKIP_HEADER only skips lines. A blank line is no record, and a file without a single record gives
 * no columns at all — a header alone names nothing.
 */
final class CsvSchemaScanner {

    /** The tail every CSV header refusal ends with. */
    private static final String ROW_TAIL = "\n  Row 0 starts at line 0, column ";

    private CsvSchemaScanner() {
    }

    /**
     * Read one file's columns.
     *
     * @param file the file
     * @param options the call's options
     * @return the columns in file order, empty when the file holds no record
     * @throws IOException when the file cannot be read
     */
    static List<FileColumn> scan(final InferenceFile file, final SchemaScanOptions options) throws IOException {
        List<String> header = null;
        final List<InferredType> types = new ArrayList<InferredType>();
        long records = 0;
        try (BufferedReader source = StagedFileIo.reader(file.path())) {
            final CsvRecordReader reader = new CsvRecordReader(source, file.relativeName(), options);
            final List<Boolean> enclosed = new ArrayList<Boolean>();
            if (options.parseHeader()) {
                final List<String> names = reader.header(enclosed);
                if (names == null) {
                    return new ArrayList<FileColumn>();
                }
                header = headerNames(names, enclosed, options.trimSpace(), file);
            } else {
                reader.skipLines(options.skipHeader());
            }
            final int width = header == null ? -1 : header.size();
            while (options.readsAllRecords() || records < options.maxRecordsPerFile()) {
                final List<String> fields = reader.next(width, records + 1, enclosed);
                if (fields == null) {
                    break;
                }
                records++;
                if (header != null && fields.size() < width) {
                    throw new RuntimeException("Error with CSV header: header defined " + width
                        + " columns while data contains " + fields.size() + " columns. \n\n  File '"
                        + file.relativeName() + "'" + ROW_TAIL);
                }
                settle(fields, enclosed, options);
                for (int i = 0; i < fields.size(); i++) {
                    while (types.size() <= i) {
                        types.add(null);
                    }
                    final String field = fields.get(i);
                    types.set(i, InferredType.absorb(types.get(i), StagedValueKinds.ofCsvField(field), field,
                        options.iceberg()));
                }
            }
        }
        final List<FileColumn> columns = new ArrayList<FileColumn>();
        if (records == 0) {
            return columns;
        }
        final int width = header != null ? header.size() : types.size();
        for (int i = 0; i < width; i++) {
            final String name = header != null ? header.get(i) : "c" + (i + 1);
            columns.add(new FileColumn(options.reportedName(name), i, i < types.size() ? types.get(i) : null, true));
        }
        return columns;
    }

    /** The header's names, TRIM_SPACE applied outside enclosures, refusing an empty one or one given twice. */
    private static List<String> headerNames(final List<String> written, final List<Boolean> enclosed,
                                            final boolean trimSpace, final InferenceFile file) {
        final List<String> names = new ArrayList<String>();
        final Set<String> seen = new HashSet<String>();
        for (int i = 0; i < written.size(); i++) {
            final boolean quoted = i < enclosed.size() && enclosed.get(i).booleanValue();
            final String name = trimSpace && !quoted ? written.get(i).trim() : written.get(i);
            if (name.isEmpty()) {
                throw new RuntimeException("Error with CSV header: empty string in the header is not allowed\n  File '"
                    + file.relativeName() + "'" + ROW_TAIL);
            }
            if (!seen.add(name)) {
                throw new RuntimeException("Error with CSV header: duplicated column names \"" + name
                    + "\" is not allowed in the header\n  File '" + file.relativeName() + "'" + ROW_TAIL);
            }
            names.add(name);
        }
        return names;
    }

    /** A record's fields once TRIM_SPACE, NULL_IF and EMPTY_FIELD_AS_NULL have settled them; null is a NULL. */
    private static void settle(final List<String> fields, final List<Boolean> enclosed,
                               final SchemaScanOptions options) {
        final List<String> nullIf = options.nullIf();
        for (int i = 0; i < fields.size(); i++) {
            final boolean fieldEnclosed = i < enclosed.size() && enclosed.get(i).booleanValue();
            String value = fields.get(i);
            if (options.trimSpace() && !fieldEnclosed) {
                value = value.trim();
            }
            if (nullIf.contains(value)) {
                value = null;
            } else if (options.emptyFieldAsNull() && value.isEmpty() && !fieldEnclosed) {
                value = null;
            }
            fields.set(i, value);
        }
    }
}
