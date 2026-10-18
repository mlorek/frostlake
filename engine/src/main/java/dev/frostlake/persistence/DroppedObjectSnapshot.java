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

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

/**
 * One dropped version UNDROP can still restore, as a snapshot keeps it: the object saved as a live one is, its rows
 * and its tables' storage, when it was dropped and for how long it is kept. A table, dynamic table or tag is saved
 * inside a schema of its own that holds nothing else; a dropped schema is that schema, a dropped database the whole
 * database.
 */
public class DroppedObjectSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The catalog's key for the name, {@code KIND:qualified name} ({@code TABLE:D.S.T}), as UNDROP looks it up. */
    public String key;

    /** When it was dropped. */
    public Instant droppedOn;

    /** The retention it had when it was dropped, in days. */
    public int retentionDays;

    /**
     * Where the stage files of a dropped table, or of a dropped schema's or database's tables and stages, wait for its
     * UNDROP; null for every other kind and in older snapshots.
     */
    public String stageToken;

    /** The database the object was in, or the dropped database's own name. */
    public String database;

    /** A dropped database, whole; null for every other kind. */
    public DatabaseSnapshot droppedDatabase;

    /** A dropped schema, or the schema a dropped table, dynamic table or tag is saved in; null for a database. */
    public SchemaSnapshot schema;

    /** A dropped table's rows; null for every other kind. */
    public List<List<Object>> rows;

    /** A dropped schema's or database's table rows, by the fully-qualified name the drop recorded them under. */
    public HashMap<String, List<List<Object>>> tableRows;

    /** The rows of the permanent tables temporary ones hid in a dropped schema or database, keyed alike. */
    public HashMap<String, List<List<Object>>> hiddenTableRows;

    /**
     * The storage of each dropped table that waited with the version — its rows, their identities and its time-travel
     * history — by the table's fully-qualified name; null in a snapshot written before it was kept, whose UNDROP fills
     * the tables with their rows.
     */
    public HashMap<String, TableDataSnapshot> keptTables;
}
