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
import java.util.Map;

/**
 * Serializable snapshot of table metadata
 */
public class TableSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String comment;
    /** The user whose DDL created the relation (LAST_DDL_BY); null in a snapshot taken before it was kept. */
    public String lastDdlBy;
    public Instant createdAt;
    public List<ColumnSnapshot> columns = new ArrayList<>();

    // The highest position ever given to a column of this table, so a restored table does not hand a
    // dropped LAST column's number to the next one added.
    public int highestOrdinal;
    // TEMPORARY / TRANSIENT table flags. Primitives default to false on old snapshots (a permanent table).
    public boolean temporary;
    public boolean isTransient;
    public boolean hybrid;
    // A permanent table a temporary table of the same name hides; false on old snapshots.
    public boolean shadowed;
    // CLUSTER BY keys and table-level FOREIGN KEY constraints. Null on old snapshots — restore null-guards.
    public List<String> clusterKeys;
    public List<ForeignKeyConstraintSnapshot> foreignKeys;
    // ROW ACCESS POLICY binding (the policy definition itself is in SchemaSnapshot.rowAccessPolicies);
    // null on old snapshot files — restore null-guards.
    public List<CheckConstraintSnapshot> checkConstraints;
    /** Attached contacts, purpose to the contact's fully qualified name. */
    public Map<String, String> contacts;
    public String aggregationPolicyName;
    public String joinPolicyName;
    public List<SearchOptimizationSnapshot> searchOptimization;
    public List<String> aggregationEntityKey;
    public String rowAccessPolicyName;
    public List<String> rowAccessPolicyColumns;
    // Constraint NAMES — the name an explicit CONSTRAINT <name> clause gave, or the generated
    // SYS_CONSTRAINT_<uuid>. Null on old snapshots, where a missing name simply regenerates on first
    // use, exactly as it did before these fields existed.
    public String primaryKeyConstraintName;
    // Only the UNIQUE constraints declared at TABLE level; a column-level UNIQUE is named per column
    // (ColumnSnapshot.uniqueConstraintName) and must not become a table-level constraint on reload.
    public List<UniqueConstraintSnapshot> uniqueConstraints;

    // Null on snapshots that predate the field (deserialization bypasses field initializers): restore null-checks.
    public Integer dataRetentionTimeInDays;
    public boolean eventTable;
    public IcebergMetadataSnapshot icebergMetadata;
    public boolean changeTracking;
    public boolean schemaEvolution;
    public boolean reclusterSuspended;
    public List<DataMetricSnapshot> dataMetrics;
    public String dataMetricSchedule;
    public HashMap<String, String> stageFileFormat;
    public HashMap<String, String> stageCopyOptions;

    // The object's tag associations, tag name -> value. Null in a snapshot written before tags were
    // recorded, which reads back as an object carrying none.
    public Map<String, String> tags;
}
