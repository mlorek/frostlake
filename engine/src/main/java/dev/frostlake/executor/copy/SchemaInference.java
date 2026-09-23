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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * INFER_SCHEMA over a set of staged files: each file's columns read on their own, then merged by name.
 *
 * <p>A column's ORDER_ID is its place in the first file, in path order, that has it — several columns can share
 * one when the files lay them out differently (the account reads the files in parallel, so which file's place it
 * reports then is not fixed). FILENAMES lists the files it appears in. NULLABLE is TRUE for every CSV and JSON
 * column; a self-describing file declares it, and a merged column is NOT NULL only when every file read declares
 * it so — a file that lacks the column, including one that is not of the format at all, lets it be NULL.
 *
 * <p>EXPRESSION reads the column back: {@code $3::DATE} by position for CSV, {@code $1:name::TEXT} by name for
 * the other formats, and {@code GET_IGNORE_CASE($1, 'NAME')::TEXT} under IGNORE_CASE, the name never quoted.
 */
public final class SchemaInference {

    /** The formats whose files declare their own columns, read through a {@link StageFileReader}. */
    private static final Set<String> SELF_DESCRIBING = new HashSet<String>(Arrays.asList("PARQUET", "AVRO", "ORC"));

    private SchemaInference() {
    }

    /**
     * The columns a set of staged files gives, in ORDER_ID order.
     *
     * @param files the files, in path order
     * @param options how to read them
     * @return the columns
     */
    public static List<InferredColumn> infer(final List<InferenceFile> files, final SchemaScanOptions options) {
        final boolean selfDescribing = SELF_DESCRIBING.contains(options.formatType());
        final StageFileReader reader = selfDescribing ? requireReader(options.formatType()) : null;
        final Map<String, MergedColumn> merged = new LinkedHashMap<String, MergedColumn>();
        final Map<String, Integer> filesWithColumn = new HashMap<String, Integer>();
        for (final InferenceFile file : files) {
            for (final FileColumn column : read(file, options, reader)) {
                MergedColumn together = merged.get(column.name());
                if (together == null) {
                    together = new MergedColumn(column.name(), column.position(), null, false);
                    merged.put(column.name(), together);
                }
                together.add(column, file.listedName(), selfDescribing, options.iceberg());
                final Integer count = filesWithColumn.get(column.name());
                filesWithColumn.put(column.name(), Integer.valueOf(count == null ? 1 : count.intValue() + 1));
            }
        }
        final List<MergedColumn> ordered = new ArrayList<MergedColumn>(merged.values());
        // A stable sort: columns sharing an ORDER_ID keep the order they were first met in.
        Collections.sort(ordered, new Comparator<MergedColumn>() {
            @Override
            public int compare(final MergedColumn a, final MergedColumn b) {
                return Integer.compare(a.orderId(), b.orderId());
            }
        });
        final boolean positional = "CSV".equals(options.formatType());
        final List<InferredColumn> columns = new ArrayList<InferredColumn>();
        for (final MergedColumn column : ordered) {
            final String type = column.type() == null ? InferredKind.TEXT.name() : column.type().typeText();
            final boolean nullable = !selfDescribing || column.nullable()
                || filesWithColumn.get(column.name()).intValue() < files.size();
            final String expression;
            if (positional) {
                expression = "$" + (column.orderId() + 1) + "::" + type;
            } else if (options.ignoreCase()) {
                expression = "GET_IGNORE_CASE($1, '" + column.name() + "')::" + type;
            } else {
                expression = "$1:" + column.name() + "::" + type;
            }
            columns.add(new InferredColumn(column.name(), type, nullable, expression,
                String.join(", ", column.fileNames()), column.orderId()));
        }
        return columns;
    }

    /** One file's columns, read the way its format is. */
    private static List<FileColumn> read(final InferenceFile file, final SchemaScanOptions options,
                                         final StageFileReader reader) {
        try {
            if ("CSV".equals(options.formatType())) {
                return CsvSchemaScanner.scan(file, options);
            }
            if ("JSON".equals(options.formatType())) {
                return JsonSchemaScanner.scan(file, options);
            }
            final List<StagedFileColumn> declared =
                reader.readColumns(file.path(), options.formatOptions(), options.iceberg());
            if (declared == null) {
                throw unsupported(options.formatType());
            }
            return declaredColumns(declared, options);
        } catch (final IOException unreadable) {
            throw new RuntimeException("INFER_SCHEMA: cannot read staged file " + file.relativeName() + ": "
                + unreadable.getMessage(), unreadable);
        }
    }

    /**
     * A self-describing file's columns under their reported names: under IGNORE_CASE two that differ only in case
     * are one column, at the first one's place and nullability, its type VARIANT when the two disagree.
     */
    private static List<FileColumn> declaredColumns(final List<StagedFileColumn> declared,
                                                    final SchemaScanOptions options) {
        final Map<String, FileColumn> byName = new LinkedHashMap<String, FileColumn>();
        for (int i = 0; i < declared.size(); i++) {
            final StagedFileColumn column = declared.get(i);
            final String name = options.reportedName(column.name());
            final InferredType type = InferredType.declared(column.typeText());
            final FileColumn existing = byName.get(name);
            if (existing == null) {
                byName.put(name, new FileColumn(name, i, type, column.nullable()));
            } else {
                existing.setType(InferredType.merge(existing.type(), type, true, options.iceberg()));
            }
        }
        return new ArrayList<FileColumn>(byName.values());
    }

    /** The reader of a self-describing format, which the optional formats module contributes. */
    private static StageFileReader requireReader(final String formatType) {
        final StageFileReader reader = StageReaderFactory.forType(formatType);
        if (reader == null) {
            throw unsupported(formatType);
        }
        return reader;
    }

    private static RuntimeException unsupported(final String formatType) {
        return new RuntimeException("INFER_SCHEMA: file format type '" + formatType + "' is not supported"
            + " (supported: " + StageReaderFactory.supportedTypesDisplay() + ")"
            + " — add the frostlake-formats module to the classpath to enable it");
    }
}
