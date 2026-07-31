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

import dev.frostlake.metastore.*;
import dev.frostlake.metastore.model.*;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CatalogSnapshotWriter {

    private static final Logger logger = LoggerFactory.getLogger(CatalogSnapshotWriter.class);

    private CatalogSnapshotWriter() {}

    /**
     * Write a full catalog + table-data snapshot to the given locations, independent of the
     * {@code persistence.enabled} flag. Shared by {@link #saveCatalog} and by WAL checkpointing
     * ({@link #checkpointTo}).
     */
    static void writeCatalogTo(final Path catalogPath, final Path tablesDir,
                                final Catalog catalog, final StorageEngine storageEngine) throws IOException {
        logger.debug("Saving catalog to: {}", catalogPath);
        final CatalogSnapshot snapshot = buildSnapshot(catalog, storageEngine, new DiskTableDataStore(tablesDir));
        // Write to a temp file then atomically rename in, so a crash can't leave a half-written snapshot.
        final Path tmp = catalogPath.resolveSibling(catalogPath.getFileName() + ".tmp");
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(tmp)))) {
            oos.writeObject(snapshot);
        }
        Files.move(tmp, catalogPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        logger.info("Catalog saved successfully");
    }

    /**
     * Build the full engine-state snapshot, pushing each table's rows into {@code tableData}. With a
     * {@link MemoryTableDataStore} this is the write half of an in-memory engine clone; with a
     * {@link DiskTableDataStore} it is the write half of an on-disk checkpoint.
     */
    static CatalogSnapshot buildSnapshot(final Catalog catalog, final StorageEngine storageEngine,
                                         final TableDataStore tableData) throws IOException {
        CatalogSnapshot snapshot = new CatalogSnapshot();
        snapshot.currentDatabase = catalog.getCurrentDatabase();
        snapshot.currentSchema = catalog.getCurrentSchema();
        snapshot.currentWarehouse = catalog.getCurrentWarehouse();

        // Save databases
        for (final Database db : catalog.getAllDatabases()) {
            DatabaseSnapshot dbSnapshot = new DatabaseSnapshot();
            dbSnapshot.name = db.getName();
            dbSnapshot.comment = db.getComment();
            dbSnapshot.createdAt = db.getCreatedTime();
            dbSnapshot.readOnly = db.isReadOnly();

            // Save schemas
            for (final Schema schema : db.getAllSchemas()) {
                SchemaSnapshot schemaSnapshot = new SchemaSnapshot();
                schemaSnapshot.name = schema.getName();
                schemaSnapshot.comment = schema.getComment();
                schemaSnapshot.createdAt = schema.getCreatedTime();

                // Save table metadata
                for (final Table table : schema.getTables()) {
                    TableSnapshot tableSnapshot = new TableSnapshot();
                    tableSnapshot.name = table.getName();
                    tableSnapshot.comment = table.getComment();
                    tableSnapshot.createdAt = table.getCreatedTime();
                    tableSnapshot.temporary = table.isTemporary();
                    tableSnapshot.isTransient = table.isTransient();
                    tableSnapshot.hybrid = table.isHybrid();
                    tableSnapshot.clusterKeys = table.getClusterKeys() != null && !table.getClusterKeys().isEmpty()
                        ? new ArrayList<>(table.getClusterKeys()) : null;

                    for (final TableColumn col : table.getColumns()) {
                        ColumnSnapshot colSnapshot = new ColumnSnapshot();
                        colSnapshot.name = col.getName();
                        colSnapshot.dataType = col.getDataType().getName();
                        if (col.getDataType() instanceof NumericType) {
                            final NumericType numericType = (NumericType) col.getDataType();
                            colSnapshot.precision = numericType.getPrecision();
                            colSnapshot.scale = numericType.getScale();
                        } else if (col.getDataType() instanceof StringType) {
                            colSnapshot.maxLength = ((StringType) col.getDataType()).getMaxLength();
                        }
                        colSnapshot.nullable = col.isNullable();
                        colSnapshot.primaryKey = col.isPrimaryKey();
                        colSnapshot.defaultValue = col.getDefaultValue() != null ? col.getDefaultValue().toString() : null;
                        colSnapshot.defaultIsExpression = col.getDefaultValue() instanceof DefaultValueExpression;
                        colSnapshot.comment = col.getComment();
                        colSnapshot.maskingPolicyName = col.getMaskingPolicyName();
                        colSnapshot.unique = col.isUnique();
                        colSnapshot.autoIncrement = col.isAutoIncrement();
                        colSnapshot.identityStart = col.getIdentityStart();
                        colSnapshot.identityIncrement = col.getIdentityIncrement();
                        colSnapshot.collation = col.getCollation();
                        colSnapshot.referencedTable = col.getReferencedTable();
                        colSnapshot.referencedColumn = col.getReferencedColumn();
                        colSnapshot.onDelete = col.getOnDelete();
                        colSnapshot.onUpdate = col.getOnUpdate();
                        colSnapshot.rely = col.getRely();
                        // Constraint names, so a restored table keeps reporting the same ones. Reading them
                        // is what generates an as-yet-unnamed constraint's SYS_CONSTRAINT_<uuid>, which is
                        // exactly what should be pinned into the snapshot.
                        colSnapshot.uniqueConstraintName = col.isUnique()
                            ? table.uniqueConstraintName(col.getName()) : null;
                        colSnapshot.foreignKeyConstraintName = col.hasForeignKey()
                            ? table.columnForeignKeyConstraintName(col.getName()) : null;
                        tableSnapshot.columns.add(colSnapshot);
                    }

                    // PRIMARY KEY name (null when the table has no primary key) and the table-level UNIQUE
                    // constraints, whose names and multi-column spans the column flags cannot express.
                    tableSnapshot.primaryKeyConstraintName = table.primaryKeyConstraintName();
                    if (!table.getDeclaredUniqueConstraints().isEmpty()) {
                        tableSnapshot.uniqueConstraints = new ArrayList<>();
                        for (final UniqueConstraint unique : table.getDeclaredUniqueConstraints()) {
                            UniqueConstraintSnapshot uniqueSnapshot = new UniqueConstraintSnapshot();
                            uniqueSnapshot.constraintName = unique.getConstraintName();
                            uniqueSnapshot.columnNames = new ArrayList<>(unique.getColumnNames());
                            tableSnapshot.uniqueConstraints.add(uniqueSnapshot);
                        }
                    }

                    // Table-level FOREIGN KEY constraints (column-level REFERENCES are on the column above).
                    if (!table.getForeignKeys().isEmpty()) {
                        tableSnapshot.foreignKeys = new ArrayList<>();
                        for (final ForeignKeyConstraint fk : table.getForeignKeys()) {
                            ForeignKeyConstraintSnapshot fkSnapshot = new ForeignKeyConstraintSnapshot();
                            fkSnapshot.constraintName = fk.getConstraintName();
                            fkSnapshot.columnNames = new ArrayList<>(fk.getColumnNames());
                            fkSnapshot.referencedTable = fk.getReferencedTable();
                            fkSnapshot.referencedColumns = new ArrayList<>(fk.getReferencedColumns());
                            fkSnapshot.onDelete = fk.getOnDelete();
                            fkSnapshot.onUpdate = fk.getOnUpdate();
                            fkSnapshot.rely = fk.getRely();
                            tableSnapshot.foreignKeys.add(fkSnapshot);
                        }
                    }

                    // ROW ACCESS POLICY binding (the definition is saved with the schema's policies)
                    tableSnapshot.rowAccessPolicyName = table.getRowAccessPolicyName();
                    tableSnapshot.rowAccessPolicyColumns = table.getRowAccessPolicyName() != null
                        ? new ArrayList<>(table.getRowAccessPolicyColumns()) : null;

                    schemaSnapshot.tables.add(tableSnapshot);

                    // Save table data
                    tableData.save(db.getName(), schema.getName(), table.getName(),
                        buildTableData(db.getName(), schema.getName(), table, storageEngine));
                }

                // Save view metadata
                for (final View view : schema.getViews()) {
                    ViewSnapshot viewSnapshot = new ViewSnapshot();
                    viewSnapshot.name = view.getName();
                    viewSnapshot.query = view.getDefinition();
                    viewSnapshot.comment = view.getComment();
                    viewSnapshot.createdAt = view.getCreatedTime();
                    viewSnapshot.secure = view.isSecure();
                    viewSnapshot.columnNames = view.getColumnNames() != null && !view.getColumnNames().isEmpty()
                        ? new ArrayList<>(view.getColumnNames()) : null;
                    viewSnapshot.rowAccessPolicyName = view.getRowAccessPolicyName();
                    viewSnapshot.rowAccessPolicyColumns = view.hasRowAccessPolicy()
                        ? new ArrayList<>(view.getRowAccessPolicyColumns()) : null;
                    viewSnapshot.columns = derivedColumnSnapshots(view.getResolvedColumns());
                    schemaSnapshot.views.add(viewSnapshot);
                }

                // Save sequences (definition + current value, so NEXTVAL continues after reload)
                for (final Sequence seq : schema.getSequences()) {
                    SequenceSnapshot seqSnapshot = new SequenceSnapshot();
                    seqSnapshot.name = seq.getName();
                    seqSnapshot.startValue = seq.getStartValue();
                    seqSnapshot.increment = seq.getIncrement();
                    seqSnapshot.order = seq.isOrder();
                    seqSnapshot.currentValue = seq.getCurrentValueRaw();
                    seqSnapshot.comment = seq.getComment();
                    seqSnapshot.owner = seq.getOwner();
                    schemaSnapshot.sequences.add(seqSnapshot);
                }

                // Save stages (the DEFINITION — the local backing dir is recomputed on restore, like
                // CREATE STAGE does — so @stage references in COPY / UDF IMPORTS keep resolving)
                for (final Stage stage : schema.getStages()) {
                    StageSnapshot stageSnapshot = new StageSnapshot();
                    stageSnapshot.name = stage.getName();
                    stageSnapshot.type = stage.getType().name();
                    stageSnapshot.url = stage.getUrl();
                    stageSnapshot.fileFormat = stage.getFileFormat();
                    stageSnapshot.encryption = stage.isEncryption();
                    stageSnapshot.comment = stage.getComment();
                    schemaSnapshot.stages.add(stageSnapshot);
                }

                // Save streams (definition + pending unconsumed change records)
                for (final Stream stream : schema.getStreams()) {
                    StreamSnapshot streamSnapshot = new StreamSnapshot();
                    streamSnapshot.name = stream.getName();
                    streamSnapshot.sourceTableName = stream.getSourceTableName();
                    streamSnapshot.baseTableName = stream.getBaseTableName();
                    streamSnapshot.baseTableNames = new ArrayList<>(stream.getBaseTableNames());
                    streamSnapshot.sourceType = stream.getSourceType().name();
                    streamSnapshot.streamType = stream.getStreamType().name();
                    streamSnapshot.showInitialRows = stream.isShowInitialRows();
                    streamSnapshot.stale = stream.isStale();
                    streamSnapshot.comment = stream.getComment();
                    streamSnapshot.owner = stream.getOwner();
                    for (final StreamRecord rec : stream.getUnconsumedRecords()) {
                        StreamRecordSnapshot recSnapshot = new StreamRecordSnapshot();
                        recSnapshot.values = new ArrayList<>(rec.getValues());
                        recSnapshot.changeType = rec.getChangeType().name();
                        recSnapshot.update = rec.isUpdate();
                        recSnapshot.rowId = rec.getRowId();
                        recSnapshot.sourceTable = rec.getSourceTable();
                        streamSnapshot.records.add(recSnapshot);
                    }
                    schemaSnapshot.streams.add(streamSnapshot);
                }

                // Save tasks (definition + STARTED/SUSPENDED state)
                for (final Task task : schema.getTasks()) {
                    TaskSnapshot taskSnapshot = new TaskSnapshot();
                    taskSnapshot.name = task.getName();
                    taskSnapshot.schedule = task.getSchedule();
                    taskSnapshot.scheduleType = task.getScheduleType() != null ? task.getScheduleType().name() : null;
                    taskSnapshot.sqlStatement = task.getSqlStatement();
                    taskSnapshot.warehouse = task.getWarehouse();
                    taskSnapshot.predecessors = new ArrayList<>(task.getPredecessors());
                    taskSnapshot.state = task.getState() != null ? task.getState().name() : null;
                    taskSnapshot.comment = task.getComment();
                    taskSnapshot.condition = task.getCondition();
                    taskSnapshot.owner = task.getOwner();
                    taskSnapshot.suspendTaskAfterNumFailures = task.getSuspendTaskAfterNumFailures();
                    taskSnapshot.taskAutoRetryAttempts = task.getTaskAutoRetryAttempts();
                    taskSnapshot.allowOverlappingExecution = task.isAllowOverlappingExecution();
                    taskSnapshot.userTaskTimeoutMs = task.getUserTaskTimeoutMs();
                    taskSnapshot.userTaskManagedInitialWarehouseSize = task.getUserTaskManagedInitialWarehouseSize();
                    taskSnapshot.serverlessTaskMaxStatementSize = task.getServerlessTaskMaxStatementSize();
                    taskSnapshot.targetCompletionInterval = task.getTargetCompletionInterval();
                    taskSnapshot.errorIntegration = task.getErrorIntegration();
                    taskSnapshot.userTaskMinimumTriggerIntervalInSeconds = task.getUserTaskMinimumTriggerIntervalInSeconds();
                    schemaSnapshot.tasks.add(taskSnapshot);
                }

                // Save masking policies (definition; the policy-to-column binding is saved on the column)
                for (final MaskingPolicy policy : schema.getMaskingPolicies()) {
                    MaskingPolicySnapshot policySnapshot = new MaskingPolicySnapshot();
                    policySnapshot.name = policy.getName();
                    policySnapshot.returnType = policy.getReturnType();
                    policySnapshot.body = policy.getBody();
                    policySnapshot.comment = policy.getComment();
                    policySnapshot.owner = policy.getOwner();
                    for (final Parameter param : policy.getParameters()) {
                        MaskingPolicyParameterSnapshot paramSnapshot = new MaskingPolicyParameterSnapshot();
                        paramSnapshot.name = param.getName();
                        paramSnapshot.dataType = param.getDataType() != null ? param.getDataType().getName() : null;
                        paramSnapshot.defaultValue = param.getDefaultValue();
                        policySnapshot.parameters.add(paramSnapshot);
                    }
                    schemaSnapshot.maskingPolicies.add(policySnapshot);
                }

                // Save file formats (TYPE + options map)
                for (final FileFormat ff : schema.getFileFormats()) {
                    FileFormatSnapshot ffSnapshot = new FileFormatSnapshot();
                    ffSnapshot.name = ff.getName();
                    ffSnapshot.type = ff.getType();
                    ffSnapshot.options = new LinkedHashMap<>(ff.getOptions());
                    ffSnapshot.comment = ff.getComment();
                    schemaSnapshot.fileFormats.add(ffSnapshot);
                }

                // Save user-defined functions (all languages, scalar and table)
                for (final Function fn : schema.getFunctions()) {
                    FunctionSnapshot fnSnapshot = new FunctionSnapshot();
                    fnSnapshot.name = fn.getName();
                    fnSnapshot.parameters = snapshotParameters(fn.getParameters());
                    fnSnapshot.returnType = fn.getReturnType() != null ? fn.getReturnType().getName() : null;
                    fnSnapshot.returnColumns = snapshotParameters(fn.getReturnColumns());
                    fnSnapshot.body = fn.getBody();
                    fnSnapshot.tableFunction = fn.isTableFunction();
                    fnSnapshot.language = fn.getLanguage();
                    fnSnapshot.handler = fn.getHandler();
                    fnSnapshot.runtimeVersion = fn.getRuntimeVersion();
                    fnSnapshot.nullHandling = fn.getNullHandling();
                    fnSnapshot.volatility = fn.getVolatility();
                    fnSnapshot.secure = fn.isSecure();
                    fnSnapshot.imports = new ArrayList<>(fn.getImports());
                    fnSnapshot.comment = fn.getComment();
                    fnSnapshot.owner = fn.getOwner();
                    schemaSnapshot.functions.add(fnSnapshot);
                }

                // Save stored procedures (all languages)
                for (final Procedure proc : schema.getProcedures()) {
                    ProcedureSnapshot procSnapshot = new ProcedureSnapshot();
                    procSnapshot.name = proc.getName();
                    procSnapshot.parameters = snapshotParameters(proc.getParameters());
                    procSnapshot.returnType = proc.getReturnType() != null ? proc.getReturnType().getName() : null;
                    procSnapshot.body = proc.getBody();
                    procSnapshot.language = proc.getLanguage();
                    procSnapshot.handler = proc.getHandler();
                    procSnapshot.runtimeVersion = proc.getRuntimeVersion();
                    procSnapshot.packages = new ArrayList<>(proc.getPackages());
                    procSnapshot.executeAs = proc.getExecuteAs();
                    procSnapshot.imports = new ArrayList<>(proc.getImports());
                    procSnapshot.comment = proc.getComment();
                    procSnapshot.owner = proc.getOwner();
                    schemaSnapshot.procedures.add(procSnapshot);
                }

                // Save pipes
                for (final Pipe pipe : schema.getPipes()) {
                    PipeSnapshot pipeSnapshot = new PipeSnapshot();
                    pipeSnapshot.name = pipe.getName();
                    pipeSnapshot.copyStatement = pipe.getCopyStatement();
                    pipeSnapshot.autoIngest = pipe.isAutoIngest();
                    pipeSnapshot.notificationChannel = pipe.getNotificationChannel();
                    pipeSnapshot.paused = pipe.isPaused();
                    pipeSnapshot.errorIntegration = pipe.getErrorIntegration();
                    pipeSnapshot.awsSnsTopicArn = pipe.getAwsSnsTopicArn();
                    pipeSnapshot.integration = pipe.getIntegration();
                    pipeSnapshot.lastLoadedFileCount = pipe.getLastLoadedFileCount();
                    pipeSnapshot.lastLoadedTime = pipe.getLastLoadedTime();
                    pipeSnapshot.comment = pipe.getComment();
                    pipeSnapshot.owner = pipe.getOwner();
                    schemaSnapshot.pipes.add(pipeSnapshot);
                }

                // Save dynamic tables (definition; the materialized rows are rebuilt on refresh)
                for (final DynamicTable dt : schema.getDynamicTables()) {
                    DynamicTableSnapshot dtSnapshot = new DynamicTableSnapshot();
                    dtSnapshot.name = dt.getName();
                    dtSnapshot.query = dt.getQuery();
                    dtSnapshot.targetLag = dt.getTargetLag();
                    dtSnapshot.warehouse = dt.getWarehouse();
                    dtSnapshot.refreshMode = dt.getRefreshMode() != null ? dt.getRefreshMode().name() : null;
                    dtSnapshot.initialize = dt.getInitialize() != null ? dt.getInitialize().name() : null;
                    dtSnapshot.state = dt.getState() != null ? dt.getState().name() : null;
                    dtSnapshot.comment = dt.getComment();
                    dtSnapshot.owner = dt.getOwner();
                    schemaSnapshot.dynamicTables.add(dtSnapshot);
                }

                // Save row access policies (the policy-to-table binding is saved on the table)
                for (final RowAccessPolicy policy : schema.getRowAccessPolicies()) {
                    RowAccessPolicySnapshot policySnapshot = new RowAccessPolicySnapshot();
                    policySnapshot.name = policy.getName();
                    policySnapshot.parameters = snapshotParameters(policy.getParameters());
                    policySnapshot.body = policy.getBody();
                    policySnapshot.comment = policy.getComment();
                    policySnapshot.owner = policy.getOwner();
                    schemaSnapshot.rowAccessPolicies.add(policySnapshot);
                }

                // Save tags
                for (final Tag tag : schema.getTags()) {
                    TagSnapshot tagSnapshot = new TagSnapshot();
                    tagSnapshot.name = tag.getName();
                    tagSnapshot.allowedValues = new ArrayList<>(tag.getAllowedValues());
                    tagSnapshot.masking = tag.isMasking();
                    tagSnapshot.comment = tag.getComment();
                    tagSnapshot.owner = tag.getOwner();
                    schemaSnapshot.tags.add(tagSnapshot);
                }

                dbSnapshot.schemas.add(schemaSnapshot);
            }

            snapshot.databases.add(dbSnapshot);
        }

        // Save warehouses
        for (final Warehouse wh : catalog.getAllWarehouses()) {
            WarehouseSnapshot whSnapshot = new WarehouseSnapshot();
            whSnapshot.name = wh.getName();
            whSnapshot.size = wh.getSize().name();
            whSnapshot.state = wh.getState().name();
            whSnapshot.autoSuspend = wh.getAutoSuspendSeconds();
            whSnapshot.autoResume = wh.isAutoResume();
            whSnapshot.comment = wh.getComment();
            whSnapshot.createdAt = wh.getCreatedAt();
            whSnapshot.scalingPolicy = wh.getScalingPolicy() != null ? wh.getScalingPolicy().name() : null;
            whSnapshot.minClusterCount = wh.getMinClusterCount();
            whSnapshot.maxClusterCount = wh.getMaxClusterCount();
            whSnapshot.owner = wh.getOwner();
            whSnapshot.warehouseType = wh.getWarehouseType();
            whSnapshot.resourceMonitor = wh.getResourceMonitor();
            snapshot.warehouses.add(whSnapshot);
        }

        // Save users
        for (final User user : catalog.getAllUsers()) {
            UserSnapshot userSnapshot = new UserSnapshot();
            userSnapshot.name = user.getName();
            userSnapshot.password = user.getPassword();
            userSnapshot.defaultRole = user.getDefaultRole();
            userSnapshot.grantedRoles = new ArrayList<>(user.getGrantedRoles());
            userSnapshot.comment = user.getComment();
            userSnapshot.createdAt = user.getCreatedTime();
            userSnapshot.enabled = user.isEnabled();
            userSnapshot.owner = user.getOwner();
            userSnapshot.privileges = snapshotPrivileges(user.getAllPrivileges(), user.getAllColumnPrivileges());
            snapshot.users.add(userSnapshot);
        }

        // Save roles
        for (final Role role : catalog.getAllRoles()) {
            RoleSnapshot roleSnapshot = new RoleSnapshot();
            roleSnapshot.name = role.getName();
            roleSnapshot.grantedRoles = new ArrayList<>(role.getGrantedRoles());
            roleSnapshot.comment = role.getComment();
            roleSnapshot.createdAt = role.getCreatedTime();
            roleSnapshot.owner = role.getOwner();

            // Save privileges — both object-level and column-level grants.
            roleSnapshot.privileges = snapshotPrivileges(role.getAllPrivileges(), role.getAllColumnPrivileges());

            snapshot.roles.add(roleSnapshot);
        }

        return snapshot;
    }

    /**
     * Snapshot the privileges granted to a role or user: object-level grants (keyed
     * {@code objectType:objectName}) and column-level grants (the same key, then columnName). A
     * column-level entry carries a non-null {@link PrivilegeSnapshot#column}; an object-level entry null.
     */
    static List<PrivilegeSnapshot> snapshotPrivileges(final Map<String, Set<Privilege>> objectPrivileges,
            final Map<String, Map<String, Set<Privilege>>> columnPrivileges) {
        final List<PrivilegeSnapshot> result = new ArrayList<>();
        for (final Map.Entry<String, Set<Privilege>> entry : objectPrivileges.entrySet()) {
            final String[] parts = entry.getKey().split(":");
            if (parts.length == 2) {
                for (final Privilege priv : entry.getValue()) {
                    final PrivilegeSnapshot privSnapshot = new PrivilegeSnapshot();
                    privSnapshot.objectType = parts[0];
                    privSnapshot.objectName = parts[1];
                    privSnapshot.privilege = priv.name();
                    result.add(privSnapshot);
                }
            }
        }
        for (final Map.Entry<String, Map<String, Set<Privilege>>> entry : columnPrivileges.entrySet()) {
            final String[] parts = entry.getKey().split(":");
            if (parts.length == 2) {
                for (final Map.Entry<String, Set<Privilege>> colEntry : entry.getValue().entrySet()) {
                    for (final Privilege priv : colEntry.getValue()) {
                        final PrivilegeSnapshot privSnapshot = new PrivilegeSnapshot();
                        privSnapshot.objectType = parts[0];
                        privSnapshot.objectName = parts[1];
                        privSnapshot.column = colEntry.getKey();
                        privSnapshot.privilege = priv.name();
                        result.add(privSnapshot);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Snapshot a DERIVED relation's column list — a view's, resolved once when it was created. Only the
     * identity and the declared type are written, including the parameters that make a NUMBER(p,s) or
     * VARCHAR(n) survive the round trip: a view column carries no DEFAULT, IDENTITY, key or constraint
     * for INFORMATION_SCHEMA to report. Null in, null out — an unresolved view stays unresolved.
     */
    static List<ColumnSnapshot> derivedColumnSnapshots(final List<TableColumn> columns) {
        if (columns == null) {
            return null;
        }
        final List<ColumnSnapshot> snapshots = new ArrayList<>();
        for (final TableColumn col : columns) {
            final ColumnSnapshot colSnapshot = new ColumnSnapshot();
            colSnapshot.name = col.getName();
            colSnapshot.dataType = col.getDataType() != null ? col.getDataType().getName() : null;
            if (col.getDataType() instanceof NumericType) {
                final NumericType numericType = (NumericType) col.getDataType();
                colSnapshot.precision = numericType.getPrecision();
                colSnapshot.scale = numericType.getScale();
            } else if (col.getDataType() instanceof StringType) {
                colSnapshot.maxLength = ((StringType) col.getDataType()).getMaxLength();
            }
            colSnapshot.nullable = col.isNullable();
            snapshots.add(colSnapshot);
        }
        return snapshots;
    }

    /** Snapshot a routine/policy parameter list (name + data-type name + optional default). */
    static List<ParameterSnapshot> snapshotParameters(final List<Parameter> parameters) {
        final List<ParameterSnapshot> snapshots = new ArrayList<>();
        if (parameters != null) {
            for (final Parameter param : parameters) {
                final ParameterSnapshot paramSnapshot = new ParameterSnapshot();
                paramSnapshot.name = param.getName();
                paramSnapshot.dataType = param.getDataType() != null ? param.getDataType().getName() : null;
                paramSnapshot.defaultValue = param.getDefaultValue();
                snapshots.add(paramSnapshot);
            }
        }
        return snapshots;
    }

    /**
     * Save table data to disk
     */
    /** A table's rows as a snapshot value (each row's values defensively copied). */
    static TableDataSnapshot buildTableData(final String database, final String schema, final Table table,
                                            final StorageEngine storageEngine) {
        String qualifiedName = database.toUpperCase() + "." + schema.toUpperCase() + "." + table.getName().toUpperCase();
        StorageEngine.TableStorage storage = storageEngine.getTableStorage(qualifiedName);
        TableDataSnapshot dataSnapshot = new TableDataSnapshot();
        dataSnapshot.database = database;
        dataSnapshot.schema = schema;
        dataSnapshot.table = table.getName();
        for (final Row row : storage.scan()) {
            dataSnapshot.rows.add(new ArrayList<>(row.getValues()));
        }
        return dataSnapshot;
    }
}
