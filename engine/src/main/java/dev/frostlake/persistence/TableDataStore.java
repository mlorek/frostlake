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

import java.io.IOException;

/**
 * Where per-table row data goes during a state snapshot and comes from during a restore. The disk
 * implementation ({@link DiskTableDataStore}) keeps the on-disk checkpoint format; the in-memory one
 * ({@link MemoryTableDataStore}) backs {@code DatabaseEngine.cloneInstance()}, which never touches
 * the filesystem.
 */
public interface TableDataStore {

    void save(String database, String schema, String table, TableDataSnapshot data) throws IOException;

    /** The saved data for a table, or null when none was recorded (an empty table). */
    TableDataSnapshot load(String database, String schema, String table) throws IOException, ClassNotFoundException;

    /** Save the rows of a permanent table a temporary table of the same name hides, apart from that table's. */
    void saveShadowed(String database, String schema, String table, TableDataSnapshot data) throws IOException;

    /** The saved rows of a hidden permanent table, or null when none were recorded. */
    TableDataSnapshot loadShadowed(String database, String schema, String table)
        throws IOException, ClassNotFoundException;
}
