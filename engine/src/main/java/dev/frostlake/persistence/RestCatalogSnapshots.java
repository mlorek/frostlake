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

import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.AccountDirectory;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.ExternalVolumeRegistry;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.model.Account;
import dev.frostlake.metastore.model.Alert;
import dev.frostlake.metastore.model.AlertExecution;
import dev.frostlake.metastore.model.AlertState;
import dev.frostlake.metastore.model.AppObject;
import dev.frostlake.metastore.model.AppObjectKind;
import dev.frostlake.metastore.model.AppObjectVersion;
import dev.frostlake.metastore.model.ArtifactRepository;
import dev.frostlake.metastore.model.ComputePool;
import dev.frostlake.metastore.model.ComputePoolState;
import dev.frostlake.metastore.model.ContainerObjects;
import dev.frostlake.metastore.model.ContainerService;
import dev.frostlake.metastore.model.DataMetricAttachment;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DatabaseRole;
import dev.frostlake.metastore.model.ExternalVolume;
import dev.frostlake.metastore.model.FutureGrant;
import dev.frostlake.metastore.model.IcebergTableMetadata;
import dev.frostlake.metastore.model.ImageRepository;
import dev.frostlake.metastore.model.Integration;
import dev.frostlake.metastore.model.IntegrationKind;
import dev.frostlake.metastore.model.ManagedAccount;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.ObjectParameters;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.PropertyValue;
import dev.frostlake.metastore.model.PropertyValueKind;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.User;
import dev.frostlake.metastore.model.Warehouse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saves and restores the catalog state the REST API work added beside the long-standing objects: the
 * organization's accounts, integrations, external volumes and compute pools; database roles, grant options,
 * grantors and future grants; database and schema parameters, retention, transience and managed access; notebooks,
 * Streamlit apps, image repositories, services, job services and artifact repositories; alerts and materialized
 * views; and a table's event-table, Iceberg, data metric and table-stage settings.
 *
 * <p>Every field it reads may be absent from a snapshot written before it existed — Java deserialization leaves such
 * a field null — so each restore treats null as "nothing recorded" and leaves the fresh object's defaults in place.
 * Each object keeps the moments it carries — its creation, change, resume and suspension times, a grant's time, a
 * notebook's URL id — and a dropped external volume, notebook or Streamlit app stays restorable by UNDROP; a
 * snapshot written before those were kept restores them as the moment of the restore.
 */
final class RestCatalogSnapshots {

    private RestCatalogSnapshots() {
    }

    // ------------------------------------------------------------------ the account level

    /** Records the account-level objects: accounts, integrations, external volumes and compute pools. */
    static void saveCatalog(final Catalog catalog, final CatalogSnapshot snapshot) {
        for (final Integration integration : catalog.getIntegrations().all()) {
            final IntegrationSnapshot saved = new IntegrationSnapshot();
            saved.name = integration.getName();
            saved.kind = integration.getKind().name();
            saved.enabled = integration.isEnabled();
            saved.owner = integration.getOwner();
            saved.comment = integration.getComment();
            saved.properties = saveProperties(integration.getProperties());
            saved.tags = new HashMap<>(integration.getTagValues());
            saved.createdOn = integration.getCreatedTime();
            snapshot.integrations.add(saved);
        }
        for (final ExternalVolume volume : catalog.getExternalVolumes().all()) {
            snapshot.externalVolumes.add(saveVolume(volume));
        }
        for (final ExternalVolume volume : catalog.getExternalVolumes().droppedVolumes()) {
            snapshot.droppedExternalVolumes.add(saveVolume(volume));
        }
        final AccountDirectory directory = catalog.getAccountDirectory();
        for (final Account account : directory.accounts()) {
            final AccountSnapshot saved = new AccountSnapshot();
            saved.name = account.getName();
            saved.edition = account.getEdition();
            saved.region = account.getRegion();
            saved.regionGroup = account.getRegionGroup();
            saved.comment = account.getComment();
            saved.adminName = account.getAdminName();
            saved.email = account.getEmail();
            saved.locator = account.getLocator();
            saved.createdOn = account.getCreatedOn();
            saved.droppedOn = account.getDroppedOn();
            saved.scheduledDeletionTime = account.getScheduledDeletionTime();
            saved.restoredOn = account.getRestoredOn();
            snapshot.accounts.add(saved);
        }
        for (final ManagedAccount account : directory.managedAccounts()) {
            final ManagedAccountSnapshot saved = new ManagedAccountSnapshot();
            saved.name = account.getName();
            saved.adminName = account.getAdminName();
            saved.comment = account.getComment();
            saved.locator = account.getLocator();
            saved.createdOn = account.getCreatedOn();
            snapshot.managedAccounts.add(saved);
        }
        snapshot.accountLocatorSequence = directory.getLocatorSequence();
        for (final ComputePool pool : catalog.getComputePools()) {
            final ComputePoolSnapshot saved = new ComputePoolSnapshot();
            saved.name = pool.getName();
            saved.owner = pool.getOwner();
            saved.comment = pool.getComment();
            saved.minNodes = pool.getMinNodes();
            saved.maxNodes = pool.getMaxNodes();
            saved.instanceFamily = pool.getInstanceFamily();
            saved.autoResume = pool.isAutoResume();
            saved.autoSuspendSecs = pool.getAutoSuspendSecs();
            saved.state = pool.getState() == null ? null : pool.getState().name();
            saved.application = pool.getApplication();
            saved.placementGroup = pool.getPlacementGroup();
            saved.backupInstanceFamilies = pool.getBackupInstanceFamilies() == null ? null
                : new ArrayList<>(pool.getBackupInstanceFamilies());
            saved.tags = new HashMap<>(pool.getTagValues());
            saved.createdOn = pool.getCreatedTime();
            saved.resumedOn = pool.getResumedOn();
            saved.updatedOn = pool.getUpdatedOn();
            snapshot.computePools.add(saved);
        }
    }

    /** Puts the account-level objects of a snapshot back. */
    static void restoreCatalog(final CatalogSnapshot snapshot, final Catalog catalog) {
        if (snapshot.integrations != null) {
            for (final IntegrationSnapshot saved : snapshot.integrations) {
                final Integration integration = new Integration(saved.name, IntegrationKind.valueOf(saved.kind),
                    orNow(saved.createdOn));
                integration.setEnabled(saved.enabled);
                restoreOwnership(integration, saved.owner, saved.comment, saved.tags);
                integration.getProperties().putAll(restoreProperties(saved.properties));
                catalog.getIntegrations().put(integration);
            }
        }
        final ExternalVolumeRegistry volumes = catalog.getExternalVolumes();
        if (snapshot.externalVolumes != null) {
            for (final ExternalVolumeSnapshot saved : snapshot.externalVolumes) {
                volumes.put(restoreVolume(saved));
            }
        }
        if (snapshot.droppedExternalVolumes != null) {
            for (final ExternalVolumeSnapshot saved : snapshot.droppedExternalVolumes) {
                final ExternalVolume volume = restoreVolume(saved);
                volume.setDroppedOn(orNow(saved.droppedOn));
                volumes.restoreDropped(volume);
            }
        }
        final AccountDirectory directory = catalog.getAccountDirectory();
        if (snapshot.accounts != null) {
            for (final AccountSnapshot saved : snapshot.accounts) {
                final Account account = new Account(saved.name, saved.edition, saved.region, saved.regionGroup,
                    saved.comment, saved.adminName, saved.email, saved.locator, saved.createdOn);
                account.restoreLifecycle(saved.droppedOn, saved.scheduledDeletionTime, saved.restoredOn);
                directory.putAccount(account);
            }
        }
        if (snapshot.managedAccounts != null) {
            for (final ManagedAccountSnapshot saved : snapshot.managedAccounts) {
                directory.putManagedAccount(new ManagedAccount(saved.name, saved.adminName, saved.comment,
                    saved.locator, saved.createdOn));
            }
        }
        if (snapshot.accountLocatorSequence > directory.getLocatorSequence()) {
            directory.setLocatorSequence(snapshot.accountLocatorSequence);
        }
        if (snapshot.computePools != null) {
            for (final ComputePoolSnapshot saved : snapshot.computePools) {
                if (catalog.hasComputePool(saved.name)) {
                    continue;
                }
                final ComputePool pool = new ComputePool(saved.name, orNow(saved.createdOn));
                pool.setMinNodes(saved.minNodes);
                pool.setMaxNodes(saved.maxNodes);
                pool.setInstanceFamily(saved.instanceFamily);
                pool.setAutoResume(saved.autoResume);
                pool.setAutoSuspendSecs(saved.autoSuspendSecs);
                if (saved.state != null) {
                    pool.setState(ComputePoolState.valueOf(saved.state));
                }
                pool.setApplication(saved.application);
                pool.setPlacementGroup(saved.placementGroup);
                if (saved.backupInstanceFamilies != null) {
                    pool.setBackupInstanceFamilies(new ArrayList<>(saved.backupInstanceFamilies));
                }
                if (saved.createdOn != null) {
                    pool.setResumedOn(saved.resumedOn);
                    pool.setUpdatedOn(saved.updatedOn);
                }
                // Created first: the catalog stamps a new pool with the current role, which the recorded
                // owner then replaces.
                catalog.createComputePool(pool);
                restoreOwnership(pool, saved.owner, saved.comment, saved.tags);
            }
        }
    }

    // ------------------------------------------------------------------ databases and schemas

    /** Records a database's parameters, retention, transience and database roles. */
    static void saveDatabase(final Database db, final DatabaseSnapshot snapshot) {
        snapshot.dataRetentionTimeInDays = db.getDataRetentionTimeInDays();
        snapshot.transientObject = db.isTransientObject();
        snapshot.parameters = new HashMap<>(db.getParameters().entries());
        for (final DatabaseRole role : db.getDatabaseRoles()) {
            snapshot.databaseRoles.add(saveRole(role));
        }
    }

    /** Puts a database's parameters, retention, transience and database roles back. */
    static void restoreDatabase(final DatabaseSnapshot snapshot, final Database db) {
        if (snapshot.dataRetentionTimeInDays != null) {
            db.setDataRetentionTimeInDays(snapshot.dataRetentionTimeInDays);
        }
        if (snapshot.transientObject) {
            db.setTransientObject(true);
        }
        restoreParameters(snapshot.parameters, db.getParameters());
        if (snapshot.databaseRoles != null) {
            for (final RoleSnapshot saved : snapshot.databaseRoles) {
                final DatabaseRole role = saved.createdAt != null
                    ? new DatabaseRole(db.getName(), saved.name, saved.createdAt)
                    : new DatabaseRole(db.getName(), saved.name);
                role.setComment(saved.comment);
                if (saved.owner != null) {
                    role.setOwner(saved.owner);
                }
                if (saved.grantedRoles != null) {
                    for (final String granted : saved.grantedRoles) {
                        grantRole(role, granted, saved.roleGrantors);
                    }
                }
                if (saved.privileges != null) {
                    for (final PrivilegeSnapshot privilege : saved.privileges) {
                        grant(role, privilege);
                    }
                }
                restoreRoleGrants(saved, role);
                db.putDatabaseRole(role);
            }
        }
    }

    /** Records a schema's settings and the schema-level objects the older snapshot left out. */
    static void saveSchema(final Schema schema, final SchemaSnapshot snapshot) {
        snapshot.dataRetentionTimeInDays = schema.getDataRetentionTimeInDays();
        snapshot.transientObject = schema.isTransientObject();
        snapshot.managedAccess = schema.isManagedAccess();
        snapshot.parameters = new HashMap<>(schema.getParameters().entries());
        for (final AppObjectKind kind : AppObjectKind.values()) {
            for (final AppObject object : schema.getAppObjects().all(kind)) {
                snapshot.appObjects.add(saveAppObject(object));
            }
        }
        for (final AppObject object : schema.getAppObjects().droppedObjects()) {
            snapshot.droppedAppObjects.add(saveAppObject(object));
        }
        final ContainerObjects containers = schema.getContainerObjects();
        for (final ImageRepository repository : containers.getImageRepositories()) {
            final ImageRepositorySnapshot saved = new ImageRepositorySnapshot();
            saved.name = repository.getName();
            saved.owner = repository.getOwner();
            saved.comment = repository.getComment();
            saved.encryption = repository.getEncryption();
            saved.createdOn = repository.getCreatedOn();
            snapshot.imageRepositories.add(saved);
        }
        for (final ContainerService service : containers.getServices()) {
            snapshot.services.add(saveService(service));
        }
        for (final ArtifactRepository repository : containers.getArtifactRepositories()) {
            final ArtifactRepositorySnapshot saved = new ArtifactRepositorySnapshot();
            saved.name = repository.getName();
            saved.type = repository.getType();
            saved.apiIntegration = repository.getApiIntegration();
            saved.owner = repository.getOwner();
            saved.comment = repository.getComment();
            saved.createdOn = repository.getCreatedOn();
            snapshot.artifactRepositories.add(saved);
        }
        for (final Alert alert : schema.getAlerts()) {
            final AlertSnapshot saved = new AlertSnapshot();
            saved.name = alert.getName();
            saved.owner = alert.getOwner();
            saved.comment = alert.getComment();
            saved.condition = alert.getCondition();
            saved.action = alert.getAction();
            saved.warehouse = alert.getWarehouse();
            saved.schedule = alert.getSchedule();
            saved.config = alert.getConfig();
            saved.runbook = alert.getRunbook();
            saved.suspendAfterNumFailures = alert.getSuspendAfterNumFailures();
            saved.state = alert.getState() == null ? null : alert.getState().name();
            saved.wasAutoSuspended = alert.getWasAutoSuspended();
            saved.tags = new HashMap<>(alert.getTagValues());
            saved.createdOn = alert.getCreatedTime();
            saved.history = new ArrayList<>();
            for (final AlertExecution execution : alert.getHistory()) {
                final AlertExecutionSnapshot run = new AlertExecutionSnapshot();
                run.scheduledTime = execution.getScheduledTime();
                run.completedTime = execution.getCompletedTime();
                run.state = execution.getState();
                run.errorMessage = execution.getErrorMessage();
                run.scheduledFrom = execution.getScheduledFrom();
                saved.history.add(run);
            }
            snapshot.alerts.add(saved);
        }
        for (final MaterializedView view : schema.getMaterializedViews()) {
            snapshot.materializedViews.add(saveMaterializedView(view));
        }
    }

    /** Puts a schema's settings and schema-level objects back. */
    static void restoreSchema(final SchemaSnapshot snapshot, final Schema schema) {
        if (snapshot.dataRetentionTimeInDays != null) {
            schema.setDataRetentionTimeInDays(snapshot.dataRetentionTimeInDays);
        }
        if (snapshot.transientObject) {
            schema.setTransientObject(true);
        }
        if (snapshot.managedAccess) {
            schema.setManagedAccess(true);
        }
        restoreParameters(snapshot.parameters, schema.getParameters());
        if (snapshot.appObjects != null) {
            for (final AppObjectSnapshot saved : snapshot.appObjects) {
                schema.getAppObjects().put(restoreAppObject(saved, schema));
            }
        }
        if (snapshot.droppedAppObjects != null) {
            for (final AppObjectSnapshot saved : snapshot.droppedAppObjects) {
                final AppObject object = restoreAppObject(saved, schema);
                object.setDroppedOn(saved.droppedOn != null ? saved.droppedOn : hostNow());
                schema.getAppObjects().restoreDropped(object);
            }
        }
        final ContainerObjects containers = schema.getContainerObjects();
        if (snapshot.imageRepositories != null) {
            for (final ImageRepositorySnapshot saved : snapshot.imageRepositories) {
                final ImageRepository repository = new ImageRepository(saved.name, orNow(saved.createdOn));
                repository.setOwner(saved.owner);
                repository.setComment(saved.comment);
                if (saved.encryption != null) {
                    repository.setEncryption(saved.encryption);
                }
                containers.putImageRepository(repository);
            }
        }
        if (snapshot.services != null) {
            for (final ContainerServiceSnapshot saved : snapshot.services) {
                containers.putService(restoreService(saved));
            }
        }
        if (snapshot.artifactRepositories != null) {
            for (final ArtifactRepositorySnapshot saved : snapshot.artifactRepositories) {
                final ArtifactRepository repository = new ArtifactRepository(saved.name, saved.type,
                    orNow(saved.createdOn));
                repository.setApiIntegration(saved.apiIntegration);
                repository.setOwner(saved.owner);
                repository.setComment(saved.comment);
                containers.putArtifactRepository(repository);
            }
        }
        if (snapshot.alerts != null) {
            for (final AlertSnapshot saved : snapshot.alerts) {
                final Alert alert = new Alert(saved.name, saved.condition, saved.action, orNow(saved.createdOn));
                alert.setWarehouse(saved.warehouse);
                alert.setSchedule(saved.schedule);
                alert.setConfig(saved.config);
                alert.setRunbook(saved.runbook);
                alert.setSuspendAfterNumFailures(saved.suspendAfterNumFailures);
                if (saved.state != null) {
                    alert.setState(AlertState.valueOf(saved.state));
                }
                alert.setWasAutoSuspended(saved.wasAutoSuspended);
                if (saved.history != null) {
                    for (final AlertExecutionSnapshot run : saved.history) {
                        alert.recordExecution(new AlertExecution(run.scheduledTime, run.completedTime, run.state,
                            run.errorMessage, run.scheduledFrom));
                    }
                }
                restoreOwnership(alert, saved.owner, saved.comment, saved.tags);
                schema.addAlert(alert);
            }
        }
        if (snapshot.materializedViews != null) {
            for (final MaterializedViewSnapshot saved : snapshot.materializedViews) {
                schema.addMaterializedView(restoreMaterializedView(saved));
            }
        }
    }

    // ------------------------------------------------------------------ tables

    /** Records a table's retention, event-table and Iceberg settings, data metrics and table-stage options. */
    static void saveTable(final Table table, final TableSnapshot snapshot) {
        snapshot.dataRetentionTimeInDays = table.getDataRetentionTimeInDays();
        snapshot.eventTable = table.isEventTable();
        snapshot.changeTracking = table.isChangeTracking();
        snapshot.schemaEvolution = table.isSchemaEvolution();
        snapshot.reclusterSuspended = table.isReclusterSuspended();
        final IcebergTableMetadata iceberg = table.getIcebergMetadata();
        if (iceberg != null) {
            final IcebergMetadataSnapshot saved = new IcebergMetadataSnapshot();
            saved.externalVolume = iceberg.getExternalVolume();
            saved.catalog = iceberg.getCatalog();
            saved.baseLocation = iceberg.getBaseLocation();
            saved.catalogSync = iceberg.getCatalogSync();
            saved.storageSerializationPolicy = iceberg.getStorageSerializationPolicy();
            saved.catalogTableName = iceberg.getCatalogTableName();
            saved.catalogNamespace = iceberg.getCatalogNamespace();
            saved.metadataFilePath = iceberg.getMetadataFilePath();
            snapshot.icebergMetadata = saved;
        }
        final List<DataMetricSnapshot> metrics = new ArrayList<>();
        for (final DataMetricAttachment attachment : table.getDataMetrics()) {
            final DataMetricSnapshot saved = new DataMetricSnapshot();
            saved.metricName = attachment.getMetricName();
            saved.columns = new ArrayList<>(attachment.getColumns());
            saved.suspended = attachment.isSuspended();
            metrics.add(saved);
        }
        snapshot.dataMetrics = metrics;
        snapshot.dataMetricSchedule = table.getDataMetricSchedule();
        snapshot.stageFileFormat = new HashMap<>(table.getStageFileFormat());
        snapshot.stageCopyOptions = new HashMap<>(table.getStageCopyOptions());
    }

    /** Puts a table's retention, event-table and Iceberg settings, data metrics and table-stage options back. */
    static void restoreTable(final TableSnapshot snapshot, final Table table) {
        if (snapshot.dataRetentionTimeInDays != null) {
            table.setDataRetentionTimeInDays(snapshot.dataRetentionTimeInDays);
        }
        if (snapshot.eventTable) {
            table.setEventTable(true);
        }
        if (snapshot.changeTracking) {
            table.setChangeTracking(true);
        }
        if (snapshot.schemaEvolution) {
            table.setSchemaEvolution(true);
        }
        if (snapshot.reclusterSuspended) {
            table.setReclusterSuspended(true);
        }
        final IcebergMetadataSnapshot saved = snapshot.icebergMetadata;
        if (saved != null) {
            final IcebergTableMetadata iceberg = new IcebergTableMetadata();
            iceberg.setExternalVolume(saved.externalVolume);
            iceberg.setCatalog(saved.catalog);
            iceberg.setBaseLocation(saved.baseLocation);
            iceberg.setCatalogSync(saved.catalogSync);
            iceberg.setStorageSerializationPolicy(saved.storageSerializationPolicy);
            iceberg.setCatalogTableName(saved.catalogTableName);
            iceberg.setCatalogNamespace(saved.catalogNamespace);
            iceberg.setMetadataFilePath(saved.metadataFilePath);
            table.setIcebergMetadata(iceberg);
        }
        if (snapshot.dataMetrics != null) {
            for (final DataMetricSnapshot metric : snapshot.dataMetrics) {
                final DataMetricAttachment attachment = new DataMetricAttachment(metric.metricName, metric.columns);
                attachment.setSuspended(metric.suspended);
                table.addDataMetric(attachment);
            }
        }
        if (snapshot.dataMetricSchedule != null) {
            table.setDataMetricSchedule(snapshot.dataMetricSchedule);
        }
        if (snapshot.stageFileFormat != null) {
            table.getStageFileFormat().putAll(snapshot.stageFileFormat);
        }
        if (snapshot.stageCopyOptions != null) {
            table.getStageCopyOptions().putAll(snapshot.stageCopyOptions);
        }
    }

    // ------------------------------------------------------------------ warehouses and stages

    /** Records a warehouse's settings the older snapshot left out. */
    static void saveWarehouse(final Warehouse wh, final WarehouseSnapshot snapshot) {
        snapshot.settingsWritten = Boolean.TRUE;
        snapshot.initiallySuspended = wh.isInitiallySuspended();
        snapshot.maxConcurrencyLevel = wh.getMaxConcurrencyLevel();
        snapshot.statementQueuedTimeoutSeconds = wh.getStatementQueuedTimeoutSeconds();
        snapshot.statementTimeoutSeconds = wh.getStatementTimeoutSeconds();
        snapshot.enableQueryAcceleration = wh.isEnableQueryAcceleration();
        snapshot.queryAccelerationMaxScaleFactor = wh.getQueryAccelerationMaxScaleFactor();
        snapshot.generation = wh.getGeneration();
        snapshot.resourceConstraint = wh.getResourceConstraintSetting();
        snapshot.enabled = wh.isEnabled();
        snapshot.parametersSet = new ArrayList<>(wh.getParametersSet());
        snapshot.tags = new HashMap<>(wh.getTagValues());
    }

    /** Puts a warehouse's settings back, when the snapshot recorded them. */
    static void restoreWarehouse(final WarehouseSnapshot snapshot, final Warehouse wh) {
        if (!Boolean.TRUE.equals(snapshot.settingsWritten)) {
            return;
        }
        wh.setInitiallySuspended(snapshot.initiallySuspended);
        wh.setMaxConcurrencyLevel(snapshot.maxConcurrencyLevel);
        wh.setStatementQueuedTimeoutSeconds(snapshot.statementQueuedTimeoutSeconds);
        wh.setStatementTimeoutSeconds(snapshot.statementTimeoutSeconds);
        wh.setEnableQueryAcceleration(snapshot.enableQueryAcceleration);
        wh.setQueryAccelerationMaxScaleFactor(snapshot.queryAccelerationMaxScaleFactor);
        wh.setGeneration(snapshot.generation);
        wh.setResourceConstraint(snapshot.resourceConstraint);
        wh.setEnabled(snapshot.enabled);
        if (snapshot.parametersSet != null) {
            for (final String parameter : snapshot.parametersSet) {
                wh.markParameterSet(parameter);
            }
        }
        if (snapshot.tags != null) {
            for (final Map.Entry<String, String> tag : snapshot.tags.entrySet()) {
                wh.setTag(tag.getKey(), tag.getValue());
            }
        }
    }

    /** Records a stage's encryption type, directory table and file format and copy options. */
    static void saveStage(final Stage stage, final StageSnapshot snapshot) {
        snapshot.encryptionType = stage.getEncryptionType();
        snapshot.directoryEnabled = stage.isDirectoryEnabled();
        snapshot.fileFormatOptions = new HashMap<>(stage.getFileFormatOptions());
        snapshot.copyOptions = new HashMap<>(stage.getCopyOptions());
        snapshot.directoryRegistry = new LinkedHashMap<>();
        for (final Map.Entry<String, List<Object>> file : stage.getDirectoryRegistry().entrySet()) {
            snapshot.directoryRegistry.put(file.getKey(), new ArrayList<>(file.getValue()));
        }
    }

    /** Puts a stage's encryption type, directory table and file format and copy options back. */
    static void restoreStage(final StageSnapshot snapshot, final Stage stage) {
        if (snapshot.encryptionType != null) {
            stage.setEncryptionType(snapshot.encryptionType);
        }
        if (snapshot.directoryEnabled) {
            stage.setDirectoryEnabled(true);
        }
        if (snapshot.fileFormatOptions != null) {
            stage.getFileFormatOptions().putAll(snapshot.fileFormatOptions);
        }
        if (snapshot.copyOptions != null) {
            stage.getCopyOptions().putAll(snapshot.copyOptions);
        }
        if (snapshot.directoryRegistry != null) {
            for (final Map.Entry<String, ArrayList<Object>> file : snapshot.directoryRegistry.entrySet()) {
                stage.getDirectoryRegistry().put(file.getKey(), new ArrayList<>(file.getValue()));
            }
        }
    }

    // ------------------------------------------------------------------ roles, users and their grants

    /** A role as a snapshot holds it, with its grant options, grantors, future grants and database-role grants. */
    static RoleSnapshot saveRole(final Role role) {
        final RoleSnapshot saved = new RoleSnapshot();
        saved.name = role.getName();
        saved.grantedRoles = new ArrayList<>(role.getGrantedRoles());
        saved.comment = role.getComment();
        saved.createdAt = role.getCreatedTime();
        saved.owner = role.getOwner();
        saved.privileges = CatalogSnapshotWriter.snapshotPrivileges(role.getAllPrivileges(),
            role.getAllColumnPrivileges());
        saveRoleGrants(role, saved);
        return saved;
    }

    /** Adds what the older role snapshot left out: grantors, grant options, future grants, database roles, tags. */
    static void saveRoleGrants(final Role role, final RoleSnapshot saved) {
        for (final PrivilegeSnapshot privilege : saved.privileges) {
            if (privilege.column != null) {
                continue;
            }
            final Privilege granted = Privilege.valueOf(privilege.privilege);
            privilege.grantor = role.getPrivilegeGrantor(privilege.objectType, privilege.objectName, granted);
            privilege.grantOption = role.hasGrantOption(privilege.objectType, privilege.objectName, granted);
            privilege.grantedOn = role.getPrivilegeGrantTime(privilege.objectType, privilege.objectName, granted);
        }
        saved.roleGrantors = new HashMap<>();
        for (final String granted : role.getGrantedRoles()) {
            final String grantor = role.getRoleGrantor(granted);
            if (grantor != null) {
                saved.roleGrantors.put(granted, grantor);
            }
        }
        saved.futureGrants = new ArrayList<>();
        for (final FutureGrant grant : role.getFutureGrants()) {
            final FutureGrantSnapshot future = new FutureGrantSnapshot();
            future.objectKind = grant.getObjectKind();
            future.scopeKind = grant.getScopeKind();
            future.scopeName = grant.getScopeName();
            future.privilege = grant.getPrivilege();
            future.grantOption = grant.hasGrantOption();
            future.createdOn = grant.getCreatedOn();
            saved.futureGrants.add(future);
        }
        saved.databaseRoleGrants = new HashMap<>(role.getDatabaseRoleGrants());
        saved.databaseRoleGrantTimes = new HashMap<>();
        for (final String held : role.getDatabaseRoleGrants().keySet()) {
            final Instant granted = role.getDatabaseRoleGrantTime(held);
            if (granted != null) {
                saved.databaseRoleGrantTimes.put(held, granted);
            }
        }
        saved.tags = new HashMap<>(role.getTagValues());
    }

    /** Adds what the older user snapshot left out: privilege and role grantors, database roles, tags. */
    static void saveUserGrants(final User user, final UserSnapshot saved) {
        for (final PrivilegeSnapshot privilege : saved.privileges) {
            if (privilege.column == null) {
                privilege.grantor = user.getPrivilegeGrantor(privilege.objectType, privilege.objectName,
                    Privilege.valueOf(privilege.privilege));
            }
        }
        saved.roleGrantors = new HashMap<>();
        for (final String granted : user.getGrantedRoles()) {
            final String grantor = user.getRoleGrantor(granted);
            if (grantor != null) {
                saved.roleGrantors.put(granted, grantor);
            }
        }
        saved.databaseRoleGrants = new HashMap<>(user.getDatabaseRoleGrants());
        saved.tags = new HashMap<>(user.getTagValues());
    }

    /** Grants one recorded privilege to a role, with its grantor and grant option when the snapshot has them. */
    static void grant(final Role role, final PrivilegeSnapshot privilege) {
        final Privilege granted = Privilege.valueOf(privilege.privilege);
        if (privilege.column != null) {
            role.grantColumnPrivilege(privilege.objectType, privilege.objectName, privilege.column, granted);
            return;
        }
        if (privilege.grantor != null) {
            role.grantPrivilege(privilege.objectType, privilege.objectName, granted, privilege.grantor);
        } else {
            role.grantPrivilege(privilege.objectType, privilege.objectName, granted);
        }
        if (Boolean.TRUE.equals(privilege.grantOption)) {
            role.setGrantOption(privilege.objectType, privilege.objectName, granted, true);
        }
        if (privilege.grantedOn != null) {
            role.setPrivilegeGrantTime(privilege.objectType, privilege.objectName, granted, privilege.grantedOn);
        }
    }

    /** Grants one recorded privilege to a user, with its grantor when the snapshot has one. */
    static void grant(final User user, final PrivilegeSnapshot privilege) {
        final Privilege granted = Privilege.valueOf(privilege.privilege);
        if (privilege.column != null) {
            user.grantColumnPrivilege(privilege.objectType, privilege.objectName, privilege.column, granted);
        } else if (privilege.grantor != null) {
            user.grantPrivilege(privilege.objectType, privilege.objectName, granted, privilege.grantor);
        } else {
            user.grantPrivilege(privilege.objectType, privilege.objectName, granted);
        }
    }

    /** Grants a role to a role, with the grantor the snapshot recorded for it, if any. */
    static void grantRole(final Role role, final String granted, final Map<String, String> grantors) {
        final String grantor = grantors == null ? null : grantors.get(granted);
        if (grantor != null) {
            role.grantRole(granted, grantor);
        } else {
            role.grantRole(granted);
        }
    }

    /** Grants a role to a user, with the grantor the snapshot recorded for it, if any. */
    static void grantRole(final User user, final String granted, final Map<String, String> grantors) {
        final String grantor = grantors == null ? null : grantors.get(granted);
        if (grantor != null) {
            user.grantRole(granted, grantor);
        } else {
            user.grantRole(granted);
        }
    }

    /** Puts a role's future grants, database-role grants and tags back. */
    static void restoreRoleGrants(final RoleSnapshot saved, final Role role) {
        if (saved.futureGrants != null) {
            for (final FutureGrantSnapshot future : saved.futureGrants) {
                role.addFutureGrant(future.objectKind, future.scopeKind, future.scopeName, future.privilege,
                    future.grantOption, orNow(future.createdOn));
            }
        }
        if (saved.databaseRoleGrants != null) {
            for (final Map.Entry<String, String> grant : saved.databaseRoleGrants.entrySet()) {
                role.grantDatabaseRole(grant.getKey(), grant.getValue());
                final Instant granted = saved.databaseRoleGrantTimes == null ? null
                    : saved.databaseRoleGrantTimes.get(grant.getKey());
                if (granted != null) {
                    role.setDatabaseRoleGrantTime(grant.getKey(), granted);
                }
            }
        }
        if (saved.tags != null) {
            for (final Map.Entry<String, String> tag : saved.tags.entrySet()) {
                role.setTag(tag.getKey(), tag.getValue());
            }
        }
    }

    /** Puts a user's database-role grants and tags back. */
    static void restoreUserGrants(final UserSnapshot saved, final User user) {
        if (saved.databaseRoleGrants != null) {
            for (final Map.Entry<String, String> grant : saved.databaseRoleGrants.entrySet()) {
                user.grantDatabaseRole(grant.getKey(), grant.getValue());
            }
        }
        if (saved.tags != null) {
            for (final Map.Entry<String, String> tag : saved.tags.entrySet()) {
                user.setTag(tag.getKey(), tag.getValue());
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static AppObjectSnapshot saveAppObject(final AppObject object) {
        final AppObjectSnapshot saved = new AppObjectSnapshot();
        saved.kind = object.getKind().name();
        saved.name = object.getName();
        saved.owner = object.getOwner();
        saved.comment = object.getComment();
        saved.fromLocation = object.getFromLocation();
        saved.rootLocation = object.getRootLocation();
        saved.mainFile = object.getMainFile();
        saved.queryWarehouse = object.getQueryWarehouse();
        saved.warehouse = object.getWarehouse();
        saved.runtimeName = object.getRuntimeName();
        saved.computePool = object.getComputePool();
        saved.title = object.getTitle();
        saved.idleAutoShutdownTimeSeconds = object.getIdleAutoShutdownTimeSeconds();
        saved.imports = new ArrayList<>(object.getImports());
        saved.externalAccessIntegrations = new ArrayList<>(object.getExternalAccessIntegrations());
        saved.versionCount = object.getVersionCount();
        saved.liveVersion = object.hasLiveVersion();
        saved.versions = new ArrayList<>();
        for (final AppObjectVersion version : object.getVersions()) {
            saved.versions.add(saveVersion(version));
        }
        saved.live = object.getLiveVersion() == null ? null : saveVersion(object.getLiveVersion());
        saved.createdOn = object.getCreatedOn();
        saved.urlId = object.getUrlId();
        saved.droppedOn = object.getDroppedOn();
        return saved;
    }

    private static AppObject restoreAppObject(final AppObjectSnapshot saved, final Schema schema) {
        final AppObjectKind kind = AppObjectKind.valueOf(saved.kind);
        final AppObject object = saved.createdOn != null && saved.urlId != null
            ? new AppObject(kind, saved.name, saved.createdOn, saved.urlId)
            : new AppObject(kind, saved.name);
        object.setDatabaseName(schema.getDatabaseName());
        object.setSchemaName(schema.getName());
        object.setOwner(saved.owner);
        object.setComment(saved.comment);
        object.setFromLocation(saved.fromLocation);
        object.setRootLocation(saved.rootLocation);
        object.setMainFile(saved.mainFile);
        object.setQueryWarehouse(saved.queryWarehouse);
        object.setWarehouse(saved.warehouse);
        object.setRuntimeName(saved.runtimeName);
        object.setComputePool(saved.computePool);
        object.setTitle(saved.title);
        object.setIdleAutoShutdownTimeSeconds(saved.idleAutoShutdownTimeSeconds);
        if (saved.imports != null) {
            object.setImports(new ArrayList<>(saved.imports));
        }
        if (saved.externalAccessIntegrations != null) {
            object.setExternalAccessIntegrations(new ArrayList<>(saved.externalAccessIntegrations));
        }
        if (saved.versions != null) {
            final List<AppObjectVersion> versions = new ArrayList<>();
            for (final AppObjectVersionSnapshot version : saved.versions) {
                versions.add(restoreVersion(version));
            }
            object.restoreVersions(versions, saved.live == null ? null : restoreVersion(saved.live));
            return object;
        }
        for (int i = 0; i < saved.versionCount; i++) {
            object.addVersion();
        }
        object.setLiveVersion(saved.liveVersion);
        return object;
    }

    private static AppObjectVersionSnapshot saveVersion(final AppObjectVersion version) {
        final AppObjectVersionSnapshot saved = new AppObjectVersionSnapshot();
        saved.number = version.getNumber();
        saved.alias = version.getAlias();
        saved.comment = version.getComment();
        saved.sourceLocation = version.getSourceLocation();
        saved.createdOn = version.getCreatedOn();
        return saved;
    }

    private static AppObjectVersion restoreVersion(final AppObjectVersionSnapshot saved) {
        return new AppObjectVersion(saved.number, saved.alias, saved.comment, saved.sourceLocation, saved.createdOn);
    }

    private static ContainerServiceSnapshot saveService(final ContainerService service) {
        final ContainerServiceSnapshot saved = new ContainerServiceSnapshot();
        saved.name = service.getName();
        saved.job = service.isJob();
        saved.computePool = service.getComputePool();
        saved.specification = service.getSpecification();
        saved.specificationStage = service.getSpecificationStage();
        saved.specificationFile = service.getSpecificationFile();
        saved.template = service.isTemplate();
        saved.status = service.getStatus();
        saved.owner = service.getOwner();
        saved.comment = service.getComment();
        saved.queryWarehouse = service.getQueryWarehouse();
        saved.logLevel = service.getLogLevel();
        saved.minInstances = service.getMinInstances();
        saved.maxInstances = service.getMaxInstances();
        saved.minReadyInstances = service.getMinReadyInstances();
        saved.autoSuspendSecs = service.getAutoSuspendSecs();
        saved.autoResume = service.getAutoResume();
        saved.asyncJob = service.isAsyncJob();
        saved.externalAccessIntegrations = new ArrayList<>(service.getExternalAccessIntegrations());
        saved.createdOn = service.getCreatedOn();
        saved.updatedOn = service.getUpdatedOn();
        saved.resumedOn = service.getResumedOn();
        saved.suspendedOn = service.getSuspendedOn();
        return saved;
    }

    private static ContainerService restoreService(final ContainerServiceSnapshot saved) {
        final ContainerService service = new ContainerService(saved.name, saved.job, orNow(saved.createdOn));
        service.setComputePool(saved.computePool);
        service.setSpecification(saved.specification, saved.specificationStage, saved.specificationFile,
            saved.template);
        if (saved.status != null) {
            service.setStatus(saved.status);
        }
        service.setOwner(saved.owner);
        service.setComment(saved.comment);
        service.setQueryWarehouse(saved.queryWarehouse);
        service.setLogLevel(saved.logLevel);
        service.setMinInstances(saved.minInstances);
        service.setMaxInstances(saved.maxInstances);
        service.setMinReadyInstances(saved.minReadyInstances);
        service.setAutoSuspendSecs(saved.autoSuspendSecs);
        service.setAutoResume(saved.autoResume);
        service.setAsyncJob(saved.asyncJob);
        if (saved.externalAccessIntegrations != null) {
            service.setExternalAccessIntegrations(new ArrayList<>(saved.externalAccessIntegrations));
        }
        if (saved.createdOn != null) {
            service.restoreTimes(saved.updatedOn, saved.resumedOn, saved.suspendedOn);
        }
        return service;
    }

    private static MaterializedViewSnapshot saveMaterializedView(final MaterializedView view) {
        final MaterializedViewSnapshot saved = new MaterializedViewSnapshot();
        saved.name = view.getName();
        saved.definition = view.getDefinition();
        saved.columnNames = view.hasExplicitColumnNames() ? new ArrayList<>(view.getColumnNames()) : null;
        saved.resolvedColumns = view.hasResolvedColumns()
            ? CatalogSnapshotWriter.derivedColumnSnapshots(view.getResolvedColumns()) : null;
        saved.owner = view.getOwner();
        saved.comment = view.getComment();
        saved.warehouse = view.getWarehouse();
        saved.suspended = view.isSuspended();
        saved.secure = view.isSecure();
        saved.sourceDatabase = view.getSourceDatabase();
        saved.sourceSchema = view.getSourceSchema();
        saved.sourceTable = view.getSourceTable();
        saved.listedText = view.getListedText();
        saved.originalDdl = view.getOriginalDdl();
        saved.materializedAsOf = view.getMaterializedAsOf();
        saved.tags = new HashMap<>(view.getTagValues());
        saved.createdOn = view.getCreatedTime();
        return saved;
    }

    private static MaterializedView restoreMaterializedView(final MaterializedViewSnapshot saved) {
        final MaterializedView view = new MaterializedView(saved.name,
            saved.columnNames != null ? new ArrayList<>(saved.columnNames) : null, saved.definition,
            orNow(saved.createdOn));
        if (saved.resolvedColumns != null) {
            view.setResolvedColumns(CatalogSnapshotReader.derivedColumns(saved.resolvedColumns));
        }
        view.setWarehouse(saved.warehouse);
        view.setSuspended(saved.suspended);
        view.setSecure(saved.secure);
        if (saved.sourceTable != null) {
            view.setSource(saved.sourceDatabase, saved.sourceSchema, saved.sourceTable);
        }
        view.setListedText(saved.listedText);
        view.setOriginalDdl(saved.originalDdl);
        view.setMaterializedAsOf(saved.materializedAsOf);
        restoreOwnership(view, saved.owner, saved.comment, saved.tags);
        return view;
    }

    private static ExternalVolumeSnapshot saveVolume(final ExternalVolume volume) {
        final ExternalVolumeSnapshot saved = new ExternalVolumeSnapshot();
        saved.name = volume.getName();
        saved.owner = volume.getOwner();
        saved.comment = volume.getComment();
        saved.allowWrites = volume.isAllowWrites();
        for (final Map<String, PropertyValue> location : volume.getStorageLocations()) {
            saved.storageLocations.add(saveProperties(location));
        }
        saved.tags = new HashMap<>(volume.getTagValues());
        saved.createdOn = volume.getCreatedTime();
        saved.droppedOn = volume.getDroppedOn();
        return saved;
    }

    private static ExternalVolume restoreVolume(final ExternalVolumeSnapshot saved) {
        final ExternalVolume volume = new ExternalVolume(saved.name, orNow(saved.createdOn));
        volume.setAllowWrites(saved.allowWrites);
        restoreOwnership(volume, saved.owner, saved.comment, saved.tags);
        if (saved.storageLocations != null) {
            for (final LinkedHashMap<String, PropertyValueSnapshot> location : saved.storageLocations) {
                volume.getStorageLocations().add(restoreProperties(location));
            }
        }
        return volume;
    }

    /** A recorded moment, or the moment of the restore for a snapshot written before it was kept. */
    private static Instant orNow(final Instant saved) {
        return saved != null ? saved : StatementClock.instant();
    }

    /** The moment of the restore on the host's wall clock, as the models stamping host-local times read it. */
    private static LocalDateTime hostNow() {
        return LocalDateTime.ofInstant(StatementClock.instant(), ZoneId.systemDefault());
    }

    private static void restoreOwnership(final SqlObject object, final String owner, final String comment,
                                         final Map<String, String> tags) {
        if (owner != null) {
            object.setOwner(owner);
        }
        object.setComment(comment);
        if (tags != null) {
            for (final Map.Entry<String, String> tag : tags.entrySet()) {
                object.setTag(tag.getKey(), tag.getValue());
            }
        }
    }

    private static void restoreParameters(final Map<String, String> saved, final ObjectParameters parameters) {
        if (saved == null) {
            return;
        }
        for (final Map.Entry<String, String> parameter : saved.entrySet()) {
            parameters.set(parameter.getKey(), parameter.getValue());
        }
    }

    private static LinkedHashMap<String, PropertyValueSnapshot> saveProperties(
            final Map<String, PropertyValue> properties) {
        final LinkedHashMap<String, PropertyValueSnapshot> saved = new LinkedHashMap<>();
        for (final Map.Entry<String, PropertyValue> property : properties.entrySet()) {
            saved.put(property.getKey(), saveValue(property.getValue()));
        }
        return saved;
    }

    private static Map<String, PropertyValue> restoreProperties(
            final Map<String, PropertyValueSnapshot> saved) {
        final Map<String, PropertyValue> properties = new LinkedHashMap<>();
        if (saved == null) {
            return properties;
        }
        for (final Map.Entry<String, PropertyValueSnapshot> property : saved.entrySet()) {
            final PropertyValue value = restoreValue(property.getValue());
            if (value != null) {
                properties.put(property.getKey(), value);
            }
        }
        return properties;
    }

    private static PropertyValueSnapshot saveValue(final PropertyValue value) {
        final PropertyValueSnapshot saved = new PropertyValueSnapshot();
        saved.kind = value.getKind().name();
        saved.written = value.getWritten();
        if (value.getKind() == PropertyValueKind.LIST) {
            saved.items = new ArrayList<>();
            for (final PropertyValue item : value.getItems()) {
                saved.items.add(saveValue(item));
            }
        } else if (value.getKind() == PropertyValueKind.PROPERTIES || value.getKind() == PropertyValueKind.PAIRS) {
            saved.entries = saveProperties(value.getEntries());
        } else {
            saved.text = value.getText();
        }
        return saved;
    }

    private static PropertyValue restoreValue(final PropertyValueSnapshot saved) {
        if (saved == null || saved.kind == null) {
            return null;
        }
        final PropertyValue value;
        switch (PropertyValueKind.valueOf(saved.kind)) {
            case NUMBER:
                value = PropertyValue.number(saved.text);
                break;
            case BOOLEAN:
                value = PropertyValue.bool("TRUE".equalsIgnoreCase(saved.text));
                break;
            case WORD:
                value = PropertyValue.word(saved.text);
                break;
            case LIST:
                value = PropertyValue.list(restoreItems(saved.items));
                break;
            case PROPERTIES:
                value = PropertyValue.properties(restoreProperties(saved.entries));
                break;
            case PAIRS:
                value = PropertyValue.pairs(restoreProperties(saved.entries));
                break;
            default:
                value = PropertyValue.text(saved.text);
                break;
        }
        if (saved.written != null) {
            value.writtenAs(saved.written);
        }
        return value;
    }

    private static List<PropertyValue> restoreItems(final List<PropertyValueSnapshot> saved) {
        final List<PropertyValue> items = new ArrayList<>();
        if (saved == null) {
            return items;
        }
        for (final PropertyValueSnapshot item : saved) {
            final PropertyValue restored = restoreValue(item);
            if (restored != null) {
                items.add(restored);
            }
        }
        return items;
    }
}
