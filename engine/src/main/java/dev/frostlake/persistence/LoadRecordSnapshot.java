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

package dev.frostlake.persistence;

import dev.frostlake.metastore.LoadRecord;

import java.io.Serializable;
import java.time.Instant;

/** One file a COPY loaded, as LOAD_HISTORY and COPY_HISTORY list it (see {@link LoadRecord}). */
public class LoadRecordSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String storageKey;
    public boolean temporary;
    public String database;
    public String schema;
    public String table;
    public String fileName;
    public String stageLocation;
    public Instant loadTime;
    public String status;
    public long rowsParsed;
    public long rowsLoaded;
    public long fileSize;
    public long errorCount;
    public long errorLimit;
    public String firstErrorMessage;
    public Integer firstErrorLine;
    public Integer firstErrorCharacter;
    public String firstErrorColumnName;

    /**
     * A record as a snapshot keeps it.
     *
     * @param load the record
     * @return its snapshot
     */
    public static LoadRecordSnapshot of(final LoadRecord load) {
        final LoadRecordSnapshot saved = new LoadRecordSnapshot();
        saved.storageKey = load.getStorageKey();
        saved.temporary = load.isTemporary();
        saved.database = load.getDatabase();
        saved.schema = load.getSchema();
        saved.table = load.getTable();
        saved.fileName = load.getFileName();
        saved.stageLocation = load.getStageLocation();
        saved.loadTime = load.getLoadTime();
        saved.status = load.getStatus();
        saved.rowsParsed = load.getRowsParsed();
        saved.rowsLoaded = load.getRowsLoaded();
        saved.fileSize = load.getFileSize();
        saved.errorCount = load.getErrorCount();
        saved.errorLimit = load.getErrorLimit();
        saved.firstErrorMessage = load.getFirstErrorMessage();
        saved.firstErrorLine = load.getFirstErrorLine();
        saved.firstErrorCharacter = load.getFirstErrorCharacter();
        saved.firstErrorColumnName = load.getFirstErrorColumnName();
        return saved;
    }

    /**
     * The record this snapshot keeps.
     *
     * @return the record
     */
    public LoadRecord toRecord() {
        return new LoadRecord(storageKey, temporary, database, schema, table, fileName, stageLocation, loadTime,
            status, rowsParsed, rowsLoaded, fileSize, errorCount, errorLimit, firstErrorMessage, firstErrorLine,
            firstErrorCharacter, firstErrorColumnName);
    }
}
