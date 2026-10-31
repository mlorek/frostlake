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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** A materialized view, as a snapshot holds it. */
public class MaterializedViewSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The canonical name. */
    public String name;

    /** The defining query. */
    public String definition;

    /** The explicit column names, or null when none were written. */
    public ArrayList<String> columnNames;

    /** The resolved column types, or null when not yet resolved. */
    public List<ColumnSnapshot> resolvedColumns;

    /** The owning role. */
    public String owner;

    /** The comment, or null. */
    public String comment;

    /** The warehouse, or null. */
    public String warehouse;

    /** Whether the view is suspended. */
    public boolean suspended;

    /** Why the view is invalid, or null while it is valid. */
    public String invalidReason;

    /** Whether a RESUME was accepted while the view was invalid. */
    public boolean resumedWhileInvalid;

    /** Whether the base table lost or renamed a column under the view. */
    public boolean baseReshaped;

    /** Whether the view is secure. */
    public boolean secure;

    /** The database of the source table, or null. */
    public String sourceDatabase;

    /** The schema of the source table, or null. */
    public String sourceSchema;

    /** The source table, or null. */
    public String sourceTable;

    /** The text SHOW lists, or null. */
    public String listedText;

    /** The CREATE statement as written, or null. */
    public String originalDdl;

    /** The source table's last data change the view has materialized, or null. */
    public Instant materializedAsOf;
    /** The rows the view materialized; null in a snapshot taken before they were kept. */
    public Long materializedRows;

    /** The clustering key's expressions, or null in a snapshot written before a view could have one. */
    public ArrayList<String> clusterKeys;
    /** The text written after CLUSTER BY; null in a snapshot taken before it was kept, and for no key. */
    public String clusterByText;

    /** Whether automatic reclustering is paused. */
    public boolean reclusterSuspended;

    /** The tags set on the view. */
    public HashMap<String, String> tags = new HashMap<>();

    /** When it was created. Null in a snapshot written before it was kept, which restores as the moment of the restore. */
    public Instant createdOn;

    /** INFORMATION_SCHEMA's LAST_DDL. Null on snapshots written before it was saved, which report the CREATE. */
    public Instant lastDdlAt;

    /** The user whose DDL last changed it, LAST_DDL_BY; null on snapshots written before it was saved. */
    public String lastDdlBy;
}
