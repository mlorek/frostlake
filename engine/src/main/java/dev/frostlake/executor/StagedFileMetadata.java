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

package dev.frostlake.executor;

import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

/**
 * The metadata columns every record of a staged file carries, in a staged-file query and in a COPY transformation
 * alike (documented, "Querying metadata for staged files"): METADATA$FILENAME, the file's name beneath the stage;
 * METADATA$FILE_ROW_NUMBER, the record's number in it; METADATA$FILE_CONTENT_KEY, a checksum of the file, here the
 * MD5 of its bytes that LIST shows; METADATA$FILE_LAST_MODIFIED, when the file was last modified, a TIMESTAMP_NTZ in
 * UTC; and METADATA$START_SCAN_TIME, when the statement began reading, a TIMESTAMP_LTZ. Each answers by name only:
 * a star expands to none of them.
 */
public final class StagedFileMetadata {

    /** The columns' names, in the order the documentation lists them. */
    private static final List<String> NAMES = Arrays.asList("METADATA$FILENAME", "METADATA$FILE_ROW_NUMBER",
        "METADATA$FILE_CONTENT_KEY", "METADATA$FILE_LAST_MODIFIED", "METADATA$START_SCAN_TIME");

    private final String name;
    private final String contentKey;
    private final LocalDateTime lastModified;

    private StagedFileMetadata(final String name, final String contentKey, final LocalDateTime lastModified) {
        this.name = name;
        this.contentKey = contentKey;
        this.lastModified = lastModified;
    }

    /**
     * A staged file's metadata, read once for all of its records. A file that cannot be read answers NULL for its
     * checksum and its time; its records are refused where they are read.
     *
     * @param file the staged file
     * @param name its name as METADATA$FILENAME reads it
     * @return the file's metadata
     */
    public static StagedFileMetadata of(final Path file, final String name) {
        try {
            return new StagedFileMetadata(name, StageQueryExecutor.md5Of(file), LocalDateTime.ofInstant(
                Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis()), ZoneOffset.UTC));
        } catch (final IOException unreadable) {
            return new StagedFileMetadata(name, null, null);
        }
    }

    /**
     * Whether a column is one of the metadata columns.
     *
     * @param upperName the column's name, upper-cased
     * @return true for a metadata column
     */
    public static boolean isMetadataColumn(final String upperName) {
        return NAMES.contains(upperName);
    }

    /**
     * Appends the metadata columns, in their order, after a staged file's fields.
     *
     * @param columns the columns of the relation the records are read as
     */
    public static void addColumns(final List<TableColumn> columns) {
        final List<DataType> types = Arrays.asList(StringType.VARCHAR, NumericType.BIGINT, StringType.VARCHAR,
            DateTimeType.TIMESTAMP_NTZ, DateTimeType.TIMESTAMP_LTZ);
        for (int i = 0; i < NAMES.size(); i++) {
            columns.add(new TableColumn(NAMES.get(i), types.get(i), true, null, false, false, false));
        }
    }

    /**
     * Appends a record's metadata values, in the columns' order.
     *
     * @param values    the record's values, its fields already in place
     * @param rowNumber the record's number in its file, from 1
     */
    public void addValues(final List<Object> values, final long rowNumber) {
        values.add(name);
        values.add(Long.valueOf(rowNumber));
        values.add(contentKey);
        values.add(lastModified);
        values.add(StatementClock.now());
    }
}
