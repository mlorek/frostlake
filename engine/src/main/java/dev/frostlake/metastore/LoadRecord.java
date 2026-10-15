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

package dev.frostlake.metastore;

import java.time.Instant;

/**
 * One file a COPY loaded into a table, as INFORMATION_SCHEMA.LOAD_HISTORY and COPY_HISTORY report it: where the file
 * came from, when its load finished, what the per-file result said of it.
 */
public final class LoadRecord {

    private final String storageKey;
    private final boolean temporary;
    private final String database;
    private final String schema;
    private final String table;
    private final String fileName;
    private final String stageLocation;
    private final Instant loadTime;
    private final String status;
    private final long rowsParsed;
    private final long rowsLoaded;
    private final long fileSize;
    private final long errorCount;
    private final long errorLimit;
    private final String firstErrorMessage;
    private final Integer firstErrorLine;
    private final Integer firstErrorCharacter;
    private final String firstErrorColumnName;

    /**
     * @param storageKey           the table's storage key, by which its history is dropped with it
     * @param temporary            whether the table is the temporary one of that name
     * @param database             the table's database, as the catalog holds it
     * @param schema               the table's schema, as the catalog holds it
     * @param table                the table's name, as the catalog holds it
     * @param fileName             the file, as the per-file result names it
     * @param stageLocation        the location the COPY read from, as the statement wrote it
     * @param loadTime             when the load finished
     * @param status               LOADED, PARTIALLY_LOADED or LOAD_FAILED, as the per-file result says
     * @param rowsParsed           the records parsed
     * @param rowsLoaded           the rows loaded
     * @param fileSize             the file's size in bytes
     * @param errorCount           the records rejected
     * @param errorLimit           the rejections ON_ERROR allowed the file
     * @param firstErrorMessage    the first rejection's message, or null
     * @param firstErrorLine       its line, or null
     * @param firstErrorCharacter  its character, or null
     * @param firstErrorColumnName its column, or null
     */
    public LoadRecord(final String storageKey, final boolean temporary, final String database, final String schema,
                      final String table, final String fileName, final String stageLocation, final Instant loadTime,
                      final String status, final long rowsParsed, final long rowsLoaded, final long fileSize,
                      final long errorCount, final long errorLimit, final String firstErrorMessage,
                      final Integer firstErrorLine, final Integer firstErrorCharacter,
                      final String firstErrorColumnName) {
        this.storageKey = storageKey;
        this.temporary = temporary;
        this.database = database;
        this.schema = schema;
        this.table = table;
        this.fileName = fileName;
        this.stageLocation = stageLocation;
        this.loadTime = loadTime;
        this.status = status;
        this.rowsParsed = rowsParsed;
        this.rowsLoaded = rowsLoaded;
        this.fileSize = fileSize;
        this.errorCount = errorCount;
        this.errorLimit = errorLimit;
        this.firstErrorMessage = firstErrorMessage;
        this.firstErrorLine = firstErrorLine;
        this.firstErrorCharacter = firstErrorCharacter;
        this.firstErrorColumnName = firstErrorColumnName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public boolean isTemporary() {
        return temporary;
    }

    public String getDatabase() {
        return database;
    }

    public String getSchema() {
        return schema;
    }

    public String getTable() {
        return table;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStageLocation() {
        return stageLocation;
    }

    public Instant getLoadTime() {
        return loadTime;
    }

    public String getStatus() {
        return status;
    }

    public long getRowsParsed() {
        return rowsParsed;
    }

    public long getRowsLoaded() {
        return rowsLoaded;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public long getErrorLimit() {
        return errorLimit;
    }

    public String getFirstErrorMessage() {
        return firstErrorMessage;
    }

    public Integer getFirstErrorLine() {
        return firstErrorLine;
    }

    public Integer getFirstErrorCharacter() {
        return firstErrorCharacter;
    }

    public String getFirstErrorColumnName() {
        return firstErrorColumnName;
    }
}
