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

import dev.frostlake.types.DataType;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/** Serializable snapshot of a DYNAMIC TABLE definition. */
public class DynamicTableSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String query;
    public String targetLag;
    public String warehouse;
    public String refreshMode;
    public String initialize;
    public String state;
    public String comment;
    /** The user whose DDL created the relation (LAST_DDL_BY); null in a snapshot taken before it was kept. */
    public String lastDdlBy;
    public String owner;
    /** The CLUSTER BY keys as written; null in a snapshot taken before they were kept. */
    public List<String> clusterKeys;
    /**
     * The table's own column list — names, declared types (null where the query's is kept) and comments, in
     * step; null in a snapshot taken before it was kept, and for a table that declares none.
     */
    public List<String> columnNames;
    public List<DataType> columnTypes;
    public List<String> columnComments;
    /** The table's columns as they resolved when it was created; null in a snapshot taken before they were kept. */
    public List<ColumnSnapshot> resolvedColumns;
    /** The rows the table's last refresh held; null in a snapshot taken before they were kept. */
    public Long refreshedRows;
    public boolean transientTable;
    // The CREATE statement and its query as written, which SHOW DYNAMIC TABLES and GET_DDL show; the scheduling state
    // and when the table was suspended; its retention settings; whether it is a clone and its reclustering is
    // suspended. Null, zero or false on snapshots written before they were saved.
    public String createText;
    public String bodyText;
    /** Where the query reads its bare names once the table has moved; null where it lives and on older snapshots. */
    public String definitionDatabase;
    public String definitionSchema;
    public String schedulingState;
    public Instant lastSuspendedOn;
    public Integer dataRetentionDays;
    public Integer maxDataExtensionDays;
    public boolean reclusterSuspended;
    public boolean clone;
    // When the object was created. Null on snapshots written before it was saved, which restore it as new.
    public Instant createdAt;
    /** INFORMATION_SCHEMA's LAST_DDL. Null on snapshots written before it was saved, which report the CREATE. */
    public Instant lastDdlAt;
}
