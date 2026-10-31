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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable snapshot of catalog metadata
 */
public class CatalogSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String currentDatabase;
    public String currentSchema;
    public String currentWarehouse;
    public List<DatabaseSnapshot> databases = new ArrayList<>();
    public List<WarehouseSnapshot> warehouses = new ArrayList<>();
    public List<SecurityObjectSnapshot> securityObjects = new ArrayList<>();
    public HashMap<String, String> securityAttachments = new HashMap<>();
    public List<StageSnapshot> stages = new ArrayList<>();
    public List<UserSnapshot> users = new ArrayList<>();
    public List<RoleSnapshot> roles = new ArrayList<>();

    // Null on snapshots that predate the field (deserialization bypasses field initializers): restore null-checks.
    public List<IntegrationSnapshot> integrations = new ArrayList<>();
    public List<ExternalVolumeSnapshot> externalVolumes = new ArrayList<>();
    public List<AccountSnapshot> accounts = new ArrayList<>();
    /** The parameters set on the account, by name. */
    public HashMap<String, String> accountParameters = new HashMap<>();
    /** The tags set on the account itself, by tag; null on an older snapshot or where none is set. */
    public HashMap<String, String> accountTags;
    public List<ManagedAccountSnapshot> managedAccounts = new ArrayList<>();
    /** The last account locator number handed out, so a restored engine does not reuse one. */
    public int accountLocatorSequence;
    public List<ComputePoolSnapshot> computePools = new ArrayList<>();
    /** The dropped external volumes UNDROP can still restore, each name's drops the earliest first. */
    public List<ExternalVolumeSnapshot> droppedExternalVolumes = new ArrayList<>();
    /** The dropped tables, dynamic tables, tags, schemas and databases UNDROP can still restore, alike. */
    public List<DroppedObjectSnapshot> droppedObjects = new ArrayList<>();
    /**
     * The last number the order time-travel snapshots are taken in had drawn, so what a restore draws follows every
     * history it restores; zero on a snapshot written before histories were kept.
     */
    public long snapshotSequence;
    /** The identity the next row stored would have taken; zero on a snapshot written before identities were kept. */
    public long nextRowId;
    /** The files each permanent table has loaded, by the table's storage key; null on an older snapshot. */
    public Map<String, List<String>> copyLoadedFiles;
    /** The files each COPY loaded, as LOAD_HISTORY and COPY_HISTORY list them, oldest first; null on an older one. */
    public List<LoadRecordSnapshot> loadHistory;
    /** The statements a time-travel point may name by query ID, oldest first; null on an older snapshot. */
    public List<QueryPointSnapshot> queryPoints;
    /** The refreshes DYNAMIC_TABLE_REFRESH_HISTORY lists, oldest first; null on an older snapshot. */
    public List<DynamicTableRefreshRecordSnapshot> dynamicTableRefreshes;
}
