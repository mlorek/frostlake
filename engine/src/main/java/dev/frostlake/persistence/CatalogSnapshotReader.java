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

import dev.frostlake.config.S3PathResolver;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.AggregationPolicy;
import dev.frostlake.metastore.model.ChangeType;
import dev.frostlake.metastore.model.CheckConstraint;
import dev.frostlake.metastore.model.Contact;
import dev.frostlake.metastore.model.CortexSearchService;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DefaultValueExpression;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.JoinPolicy;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.ProjectionPolicy;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.ScalingPolicy;
import dev.frostlake.metastore.model.ScheduleType;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SearchOptimizationExpression;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.StageType;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamRecord;
import dev.frostlake.metastore.model.StreamSourceType;
import dev.frostlake.metastore.model.StreamType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskExecution;
import dev.frostlake.metastore.model.TaskExecutionState;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.metastore.model.User;
import dev.frostlake.metastore.model.View;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.metastore.model.WarehouseSize;
import dev.frostlake.metastore.model.WarehouseState;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.task.TaskTrigger;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.IntervalTypes;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.UuidType;
import dev.frostlake.types.VariantType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class CatalogSnapshotReader {

    private static final Logger logger = LoggerFactory.getLogger(CatalogSnapshotReader.class);

    private CatalogSnapshotReader() {}

    /**
     * Read a full catalog + table-data snapshot from the given locations into the live catalog + storage,
     * independent of the {@code persistence.enabled} flag. Shared by {@link #loadCatalog} and by WAL
     * recovery ({@link #restoreFrom}). A missing snapshot file is treated as "nothing to load".
     */
    static void readCatalogFrom(final Path catalogPath, final Path tablesDir, final S3PathResolver s3Resolver,
                                 final Catalog catalog, final StorageEngine storageEngine)
            throws IOException, ClassNotFoundException {
        if (!Files.exists(catalogPath)) {
            logger.info("No existing catalog file found, starting fresh");
            return;
        }

        logger.info("Loading catalog from: {}", catalogPath);

        final CatalogSnapshot snapshot;
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(catalogPath)))) {
            snapshot = (CatalogSnapshot) ois.readObject();
        }
        applySnapshot(snapshot, new DiskTableDataStore(tablesDir), s3Resolver, catalog, storageEngine);
    }

    /**
     * Rebuild engine state from a snapshot, pulling each table's rows from {@code tableData}. With a
     * {@link MemoryTableDataStore} this is the apply half of an in-memory engine clone; with a
     * {@link DiskTableDataStore} it is the apply half of an on-disk checkpoint restore.
     */
    static void applySnapshot(final CatalogSnapshot snapshot, final TableDataStore tableData,
                              final S3PathResolver s3Resolver,
                              final Catalog catalog, final StorageEngine storageEngine)
            throws IOException, ClassNotFoundException {
        // Load databases and schemas
        for (final DatabaseSnapshot dbSnapshot : snapshot.databases) {
            // Skip system database (will be created automatically)
            if ("SNOWFLAKE".equals(dbSnapshot.name)) {
                final Database db = catalog.getDatabase("SNOWFLAKE");
                loadDatabaseContent(tableData, catalog, db, dbSnapshot, s3Resolver, storageEngine);
                continue;
            }

            catalog.createDatabase(dbSnapshot.name);
            final Database db = catalog.getDatabase(dbSnapshot.name);
            db.setComment(dbSnapshot.comment);
            db.setReadOnly(dbSnapshot.readOnly);
            SnapshotTags.restore(db, dbSnapshot.tags);
            // Note: createdAt cannot be set (final field in SqlObject)

            loadDatabaseContent(tableData, catalog, db, dbSnapshot, s3Resolver, storageEngine);
        }

        SecurityObjectSnapshots.restore(snapshot.securityObjects, catalog.getSecurityObjects());
        SecurityObjectSnapshots.restoreAttachments(snapshot.securityAttachments, catalog.getSecurityObjects());
        RestCatalogSnapshots.restoreCatalog(snapshot, catalog);

        // Load warehouses
        for (final WarehouseSnapshot whSnapshot : snapshot.warehouses) {
            // The default warehouse already exists in a fresh catalog, so it is not created a second
            // time — but every setting an ALTER put on it is its own and has to come back, which
            // skipping the whole entry used to throw away.
            if (!catalog.hasWarehouse(whSnapshot.name)) {
                catalog.createWarehouse(whSnapshot.name, WarehouseSize.valueOf(whSnapshot.size));
            }
            final Warehouse wh = catalog.getWarehouse(whSnapshot.name);
            wh.setSize(WarehouseSize.valueOf(whSnapshot.size));
            wh.setState(WarehouseState.valueOf(whSnapshot.state));
            wh.setAutoSuspendSeconds(whSnapshot.autoSuspend);
            wh.setAutoResume(whSnapshot.autoResume);
            wh.setComment(whSnapshot.comment);
            if (whSnapshot.scalingPolicy != null) {
                wh.setScalingPolicy(ScalingPolicy.valueOf(whSnapshot.scalingPolicy));
            }
            // Cluster counts default to 0 on old snapshots; leave the warehouse's own defaults then.
            if (whSnapshot.minClusterCount > 0) {
                wh.setMinClusterCount(whSnapshot.minClusterCount);
            }
            if (whSnapshot.maxClusterCount > 0) {
                wh.setMaxClusterCount(whSnapshot.maxClusterCount);
            }
            if (whSnapshot.owner != null) {
                wh.setOwner(whSnapshot.owner);
            }
            if (whSnapshot.warehouseType != null) {
                wh.setWarehouseType(whSnapshot.warehouseType);
            }
            wh.setResourceMonitor(whSnapshot.resourceMonitor);
            RestCatalogSnapshots.restoreWarehouse(whSnapshot, wh);
            // Note: createdAt cannot be set (final field)
        }

        // Load users
        for (final UserSnapshot userSnapshot : snapshot.users) {
            try {
                catalog.createUser(userSnapshot.name, userSnapshot.password, userSnapshot.defaultRole);
            } catch (final RuntimeException e) {
                // User already exists (e.g. default user created during init) — skip creation
            }
            final User user = catalog.getUser(userSnapshot.name);
            user.setComment(userSnapshot.comment);
            // enabled is a nullable Boolean: null (a snapshot predating the field) means the user was never
            // disabled, so leave it enabled; only an explicit value flips it.
            if (userSnapshot.enabled != null) {
                user.setEnabled(userSnapshot.enabled);
            }
            if (userSnapshot.owner != null) {
                user.setOwner(userSnapshot.owner);
            }
            // The CREATE USER property set. Applied verbatim — nulls included — but only when the
            // snapshot actually carries the group; an older one leaves the new user's defaults be.
            if (Boolean.TRUE.equals(userSnapshot.propertiesWritten)) {
                user.setLoginName(userSnapshot.loginName);
                user.setDisplayName(userSnapshot.displayName);
                user.setFirstName(userSnapshot.firstName);
                user.setMiddleName(userSnapshot.middleName);
                user.setLastName(userSnapshot.lastName);
                user.setEmail(userSnapshot.email);
                user.setDefaultWarehouse(userSnapshot.defaultWarehouse);
                user.setDefaultNamespace(userSnapshot.defaultNamespace);
                user.setDefaultSecondaryRoles(userSnapshot.defaultSecondaryRoles);
                user.setMustChangePassword(userSnapshot.mustChangePassword);
                user.setUserType(userSnapshot.userType);
            }
            user.setExpiresAt(userSnapshot.expiresAt);
            user.setLockedUntil(userSnapshot.lockedUntil);
            user.setMfaBypassUntil(userSnapshot.mfaBypassUntil);
            user.setRsaPublicKey(userSnapshot.rsaPublicKey, userSnapshot.rsaPublicKeyFp,
                userSnapshot.rsaPublicKeyLastSetTime);
            user.setRsaPublicKey2(userSnapshot.rsaPublicKey2, userSnapshot.rsaPublicKey2Fp,
                userSnapshot.rsaPublicKey2LastSetTime);
            // Privileges granted directly to the user (object- and column-level). The referenced objects were
            // loaded above; grant* only records the entry, so this is order-independent for the grant itself.
            if (userSnapshot.privileges != null) {
                for (final PrivilegeSnapshot privSnapshot : userSnapshot.privileges) {
                    try {
                        RestCatalogSnapshots.grant(user, privSnapshot);
                    } catch (final Exception e) {
                        logger.warn("Could not grant privilege to user {}: {}", userSnapshot.name, e.getMessage());
                    }
                }
            }
            RestCatalogSnapshots.restoreUserGrants(userSnapshot, user);
            // Note: createdAt cannot be set (final field)
            // Roles will be granted after all roles are loaded
        }

        // Load custom roles (system roles already exist)
        for (final RoleSnapshot roleSnapshot : snapshot.roles) {
            if (!catalog.isSystemRole(roleSnapshot.name)) {
                catalog.createRole(roleSnapshot.name);
                final Role role = catalog.getRole(roleSnapshot.name);
                role.setComment(roleSnapshot.comment);
                if (roleSnapshot.owner != null) {
                    role.setOwner(roleSnapshot.owner);
                }
                // Note: createdAt cannot be set (final field)
            }
        }

        // Grant role hierarchies
        for (final RoleSnapshot roleSnapshot : snapshot.roles) {
            final Role role = catalog.getRole(roleSnapshot.name);
            for (final String grantedRoleName : roleSnapshot.grantedRoles) {
                try {
                    RestCatalogSnapshots.grantRole(role, grantedRoleName, roleSnapshot.roleGrantors);
                } catch (final Exception e) {
                    logger.warn("Could not grant role {} to {}: {}", grantedRoleName, roleSnapshot.name, e.getMessage());
                }
            }
        }

        // Grant privileges to roles
        for (final RoleSnapshot roleSnapshot : snapshot.roles) {
            final Role role = catalog.getRole(roleSnapshot.name);
            for (final PrivilegeSnapshot privSnapshot : roleSnapshot.privileges) {
                try {
                    RestCatalogSnapshots.grant(role, privSnapshot);
                } catch (final Exception e) {
                    logger.warn("Could not grant privilege: {}", e.getMessage());
                }
            }
            RestCatalogSnapshots.restoreRoleGrants(roleSnapshot, role);
        }

        // Grant roles to users
        for (final UserSnapshot userSnapshot : snapshot.users) {
            final User user = catalog.getUser(userSnapshot.name);
            for (final String roleName : userSnapshot.grantedRoles) {
                try {
                    RestCatalogSnapshots.grantRole(user, roleName, userSnapshot.roleGrantors);
                } catch (final Exception e) {
                    logger.warn("Could not grant role {} to user {}: {}", roleName, userSnapshot.name, e.getMessage());
                }
            }
        }

        // Restore current context
        if (snapshot.currentDatabase != null) {
            catalog.useDatabase(snapshot.currentDatabase);
        }
        if (snapshot.currentSchema != null) {
            catalog.useSchema(snapshot.currentSchema);
        }
        if (snapshot.currentWarehouse != null) {
            catalog.useWarehouse(snapshot.currentWarehouse);
        }

        logger.info("Catalog loaded successfully");
    }

    /** The trigger a recorded run names in TASK_HISTORY's scheduled_from; a schedule when none matches. */
    private static TaskTrigger taskTrigger(final String scheduledFrom) {
        for (final TaskTrigger trigger : TaskTrigger.values()) {
            if (trigger.reported().equals(scheduledFrom)) {
                return trigger;
            }
        }
        return TaskTrigger.SCHEDULE;
    }

    static void loadDatabaseContent(final TableDataStore tableData, final Catalog catalog, final Database db,
                                     final DatabaseSnapshot dbSnapshot, final S3PathResolver s3Resolver,
                                     final StorageEngine storageEngine) throws IOException, ClassNotFoundException {
        RestCatalogSnapshots.restoreDatabase(dbSnapshot, db);
        for (final SchemaSnapshot schemaSnapshot : dbSnapshot.schemas) {
            final Schema schema;

            // Get existing schema or create new one (skip default schemas)
            if ("PUBLIC".equals(schemaSnapshot.name) || "INFORMATION_SCHEMA".equals(schemaSnapshot.name)) {
                schema = db.getSchema(schemaSnapshot.name);
            } else {
                final Schema newSchema = new Schema(schemaSnapshot.name);
                newSchema.setComment(schemaSnapshot.comment);
                SnapshotTags.restore(newSchema, schemaSnapshot.tagValues);
                db.addSchema(newSchema);
                schema = newSchema;
                // Note: createdAt cannot be set (final field in SqlObject)
            }

            // Load tables
            for (final TableSnapshot tableSnapshot : schemaSnapshot.tables) {
                final List<TableColumn> columns = new ArrayList<>();
                for (final ColumnSnapshot colSnapshot : tableSnapshot.columns) {
                    // Identity start/increment only apply when autoIncrement; for other columns pass the
                    // model default (1/1). Old snapshots have autoIncrement=false, so identity is irrelevant.
                    final long identityStart = colSnapshot.autoIncrement ? colSnapshot.identityStart : 1L;
                    final long identityIncrement = colSnapshot.autoIncrement ? colSnapshot.identityIncrement : 1L;
                    // Re-wrap an expression default so INSERT evaluates it again; a literal stays a plain value.
                    final Object columnDefault = colSnapshot.defaultIsExpression && colSnapshot.defaultValue != null
                        ? new DefaultValueExpression(colSnapshot.defaultValue) : colSnapshot.defaultValue;
                    final TableColumn col = new TableColumn(
                        colSnapshot.name,
                        parseDataType(colSnapshot.dataType, colSnapshot.precision, colSnapshot.scale,
                            colSnapshot.maxLength, colSnapshot.binaryFixed),
                        colSnapshot.nullable,
                        columnDefault,
                        colSnapshot.primaryKey,
                        colSnapshot.unique,
                        colSnapshot.autoIncrement,
                        identityStart,
                        identityIncrement
                    );
                    col.setComment(colSnapshot.comment);
                    col.setMaskingPolicyName(colSnapshot.maskingPolicyName);
                    col.setProjectionPolicyName(colSnapshot.projectionPolicyName);
                    col.setCollation(colSnapshot.collation);
                    col.setOrdinalPosition(colSnapshot.ordinalPosition);
                    SnapshotTags.restore(col, colSnapshot.tags);
                    // Column-level FOREIGN KEY (REFERENCES) and its RELY flag, when present.
                    if (colSnapshot.referencedTable != null) {
                        col.setReferencedTable(colSnapshot.referencedTable);
                        col.setReferencedColumn(colSnapshot.referencedColumn);
                        col.setOnDelete(colSnapshot.onDelete);
                        col.setOnUpdate(colSnapshot.onUpdate);
                    }
                    col.setRely(colSnapshot.rely);
                    columns.add(col);
                }

                final Table table = new Table(tableSnapshot.name, columns,
                    tableSnapshot.temporary, tableSnapshot.isTransient);
                table.setHighestOrdinal(tableSnapshot.highestOrdinal);
                table.setHybrid(tableSnapshot.hybrid);
                table.setComment(tableSnapshot.comment);
                table.setLastDdlBy(tableSnapshot.lastDdlBy);
                if (tableSnapshot.clusterKeys != null) {
                    table.setClusterKeys(tableSnapshot.clusterKeys);
                }
                if (tableSnapshot.foreignKeys != null) {
                    for (final ForeignKeyConstraintSnapshot fkSnapshot : tableSnapshot.foreignKeys) {
                        table.addForeignKey(new ForeignKeyConstraint(fkSnapshot.constraintName,
                            fkSnapshot.columnNames, fkSnapshot.referencedTable, fkSnapshot.referencedColumns,
                            fkSnapshot.onDelete, fkSnapshot.onUpdate, fkSnapshot.rely));
                    }
                }
                if (tableSnapshot.rowAccessPolicyName != null) {
                    table.setRowAccessPolicyName(tableSnapshot.rowAccessPolicyName);
                    table.setAggregationPolicyName(tableSnapshot.aggregationPolicyName);
                    table.setJoinPolicyName(tableSnapshot.joinPolicyName);
                    if (tableSnapshot.searchOptimization != null) {
                        for (final SearchOptimizationSnapshot soSnapshot : tableSnapshot.searchOptimization) {
                            table.restoreSearchOptimization(new SearchOptimizationExpression(
                                soSnapshot.expressionId, soSnapshot.method, soSnapshot.target,
                                soSnapshot.targetDataType));
                        }
                    }
                    if (tableSnapshot.aggregationEntityKey != null) {
                        table.setAggregationEntityKey(tableSnapshot.aggregationEntityKey);
                    }
                    if (tableSnapshot.checkConstraints != null) {
                        for (final CheckConstraintSnapshot checkSnapshot : tableSnapshot.checkConstraints) {
                            table.addCheckConstraint(new CheckConstraint(checkSnapshot.name,
                                checkSnapshot.expression, checkSnapshot.autoNamed,
                                checkSnapshot.referencedColumns));
                        }
                    }
                    if (tableSnapshot.contacts != null) {
                        for (final Map.Entry<String, String> attached : tableSnapshot.contacts.entrySet()) {
                            table.setContact(attached.getKey(), attached.getValue());
                        }
                    }
                    if (tableSnapshot.rowAccessPolicyColumns != null) {
                        table.setRowAccessPolicyColumns(tableSnapshot.rowAccessPolicyColumns);
                    }
                }
                // Constraint names, so a reloaded table keeps reporting the ones it was created with. All
                // of these are null on snapshots written before they were persisted, in which case the
                // constraint auto-names itself again on first use.
                table.setPrimaryKeyConstraintName(tableSnapshot.primaryKeyConstraintName);
                if (tableSnapshot.uniqueConstraints != null) {
                    for (final UniqueConstraintSnapshot uniqueSnapshot : tableSnapshot.uniqueConstraints) {
                        table.addUniqueConstraint(new UniqueConstraint(uniqueSnapshot.constraintName,
                            uniqueSnapshot.columnNames));
                    }
                }
                for (final ColumnSnapshot colSnapshot : tableSnapshot.columns) {
                    table.setUniqueConstraintName(colSnapshot.name, colSnapshot.uniqueConstraintName);
                    table.setColumnForeignKeyConstraintName(colSnapshot.name, colSnapshot.foreignKeyConstraintName);
                }
                final String qualifiedName = QualifiedName.key(db.getName(), schema.getName(), table.getName());
                if (tableSnapshot.shadowed) {
                    // A permanent table a temporary one hid is restored hidden beneath it.
                    schema.addShadowedTable(table);
                    storageEngine.createShadowedTable(qualifiedName, table);
                } else {
                    SnapshotTags.restore(table, tableSnapshot.tags);
                    schema.addTable(table);
                    storageEngine.createTable(qualifiedName, table);
                }

                RestCatalogSnapshots.restoreTable(tableSnapshot, table);

                // Load table data
                loadTableData(tableData, db.getName(), schema.getName(), table, tableSnapshot.shadowed, storageEngine);
            }

            // Load views (skip INFORMATION_SCHEMA views as they're system-generated)
            if (!"INFORMATION_SCHEMA".equals(schemaSnapshot.name)) {
                for (final ViewSnapshot viewSnapshot : schemaSnapshot.views) {
                    final View view = viewSnapshot.columnNames != null
                        ? new View(viewSnapshot.name, viewSnapshot.columnNames, viewSnapshot.query)
                        : new View(viewSnapshot.name, viewSnapshot.query);
                    view.setComment(viewSnapshot.comment);
                    view.setLastDdlBy(viewSnapshot.lastDdlBy);
                    view.setSecure(viewSnapshot.secure);
                    view.setRecursive(viewSnapshot.recursive);
                    view.setWrittenBody(viewSnapshot.writtenBody);
                    if (viewSnapshot.rowAccessPolicyName != null) {
                        view.setRowAccessPolicyName(viewSnapshot.rowAccessPolicyName);
                        view.setRowAccessPolicyColumns(viewSnapshot.rowAccessPolicyColumns != null
                            ? viewSnapshot.rowAccessPolicyColumns : new ArrayList<>());
                    }
                    view.setResolvedColumns(derivedColumns(viewSnapshot.columns));
                    SnapshotTags.restore(view, viewSnapshot.tags);
                    schema.addView(view);
                }
            }

            // Load sequences. Null guards throughout: snapshots written before these fields existed
            // deserialize them as null (field initializers do not run during Java deserialization).
            if (schemaSnapshot.sequences != null) {
                for (final SequenceSnapshot seqSnapshot : schemaSnapshot.sequences) {
                    final Sequence seq = new Sequence(seqSnapshot.name, seqSnapshot.startValue,
                        seqSnapshot.increment, seqSnapshot.order, seqSnapshot.comment);
                    seq.setCurrentValue(seqSnapshot.currentValue);
                    if (seqSnapshot.owner != null) {
                        seq.setOwner(seqSnapshot.owner);
                    }
                    schema.addSequence(seq);
                }
            }

            // Load stages: recreate from the definition, recomputing the local backing directory
            // through the engine's resolver exactly as CREATE STAGE does. Null on older snapshots.
            if (schemaSnapshot.stages != null) {
                for (final StageSnapshot stageSnapshot : schemaSnapshot.stages) {
                    final Stage stage = new Stage(stageSnapshot.name, StageType.valueOf(stageSnapshot.type),
                        stageSnapshot.url, stageSnapshot.fileFormat, stageSnapshot.encryption,
                        stageSnapshot.comment, s3Resolver);
                    RestCatalogSnapshots.restoreStage(stageSnapshot, stage);
                    schema.addStage(stage);
                }
            }

            // Load Cortex search services. Null on older snapshots.
            if (schemaSnapshot.cortexSearchServices != null) {
                for (final CortexSearchServiceSnapshot serviceSnapshot
                        : schemaSnapshot.cortexSearchServices) {
                    final CortexSearchService service = new CortexSearchService(
                        serviceSnapshot.name, serviceSnapshot.searchColumn,
                        serviceSnapshot.attributeColumns, serviceSnapshot.columns,
                        serviceSnapshot.warehouse, serviceSnapshot.targetLag,
                        serviceSnapshot.embeddingModel, serviceSnapshot.definition,
                        serviceSnapshot.comment);
                    service.setOwner(serviceSnapshot.owner);
                    service.setIndexingSuspended(serviceSnapshot.indexingSuspended);
                    service.setServingSuspended(serviceSnapshot.servingSuspended);
                    schema.addCortexSearchService(service);
                }
            }

            // Load streams (pending change records restored at offset 0 = all unconsumed)
            if (schemaSnapshot.streams != null) {
                for (final StreamSnapshot streamSnapshot : schemaSnapshot.streams) {
                    final Stream stream = new Stream(streamSnapshot.name, streamSnapshot.sourceTableName,
                        StreamSourceType.valueOf(streamSnapshot.sourceType),
                        StreamType.valueOf(streamSnapshot.streamType), streamSnapshot.showInitialRows);
                    if (streamSnapshot.baseTableNames != null && !streamSnapshot.baseTableNames.isEmpty()) {
                        stream.setBaseTableNames(streamSnapshot.baseTableNames);
                    } else {
                        stream.setBaseTableName(streamSnapshot.baseTableName);
                    }
                    stream.setStale(streamSnapshot.stale);
                    stream.setComment(streamSnapshot.comment);
                    if (streamSnapshot.owner != null) {
                        stream.setOwner(streamSnapshot.owner);
                    }
                    if (streamSnapshot.records != null) {
                        for (final StreamRecordSnapshot recSnapshot : streamSnapshot.records) {
                            stream.addRecord(new StreamRecord(recSnapshot.values,
                                ChangeType.valueOf(recSnapshot.changeType), recSnapshot.update, recSnapshot.rowId,
                                recSnapshot.sourceTable));
                        }
                    }
                    if (streamSnapshot.initialRecords != null) {
                        final List<StreamRecord> initial = new ArrayList<>();
                        for (final StreamRecordSnapshot recSnapshot : streamSnapshot.initialRecords) {
                            initial.add(new StreamRecord(recSnapshot.values, ChangeType.valueOf(recSnapshot.changeType),
                                recSnapshot.update, recSnapshot.rowId, recSnapshot.sourceTable));
                        }
                        stream.setInitialRecords(initial);
                    }
                    if (streamSnapshot.refreshImage != null) {
                        stream.setRefreshImage(new ArrayList<>(streamSnapshot.refreshImage));
                    }
                    SnapshotTags.restore(stream, streamSnapshot.tags);
                    schema.addStream(stream);
                }
            }

            // Load tasks. State is restored as-is; persistence does not re-arm the scheduler, so a restored
            // STARTED task is not actively scheduled until it is RESUMEd again (RESUME arms the timer).
            if (schemaSnapshot.tasks != null) {
                for (final TaskSnapshot taskSnapshot : schemaSnapshot.tasks) {
                    final Task task = new Task(taskSnapshot.name, taskSnapshot.schedule,
                        taskSnapshot.scheduleType != null ? ScheduleType.valueOf(taskSnapshot.scheduleType) : null,
                        taskSnapshot.sqlStatement, taskSnapshot.warehouse);
                    task.setId(taskSnapshot.id);
                    task.setCreatedByUser(taskSnapshot.createdByUser);
                    SnapshotTags.restore(task, taskSnapshot.tags);
                    if (taskSnapshot.explicitParameters != null) {
                        for (final String parameterName : taskSnapshot.explicitParameters) {
                            task.markParameterSet(parameterName);
                        }
                    }
                    if (taskSnapshot.predecessors != null) {
                        for (final String pred : taskSnapshot.predecessors) {
                            task.addPredecessor(pred);
                        }
                    }
                    if (taskSnapshot.state != null) {
                        task.setState(TaskState.valueOf(taskSnapshot.state));
                    }
                    task.setComment(taskSnapshot.comment);
                    task.setCondition(taskSnapshot.condition);
                    if (taskSnapshot.owner != null) {
                        task.setOwner(taskSnapshot.owner);
                    }
                    task.setSuspendTaskAfterNumFailures(taskSnapshot.suspendTaskAfterNumFailures);
                    task.setTaskAutoRetryAttempts(taskSnapshot.taskAutoRetryAttempts);
                    task.setAllowOverlappingExecution(taskSnapshot.allowOverlappingExecution);
                    task.setUserTaskTimeoutMs(taskSnapshot.userTaskTimeoutMs);
                    task.setUserTaskManagedInitialWarehouseSize(taskSnapshot.userTaskManagedInitialWarehouseSize);
                    task.setServerlessTaskMaxStatementSize(taskSnapshot.serverlessTaskMaxStatementSize);
                    task.setTargetCompletionInterval(taskSnapshot.targetCompletionInterval);
                    task.setErrorIntegration(taskSnapshot.errorIntegration);
                    // 0 on old snapshots (field absent) → keep the task's own default trigger interval.
                    if (taskSnapshot.userTaskMinimumTriggerIntervalInSeconds > 0) {
                        task.setUserTaskMinimumTriggerIntervalInSeconds(taskSnapshot.userTaskMinimumTriggerIntervalInSeconds);
                    }
                    task.setConfig(taskSnapshot.config);
                    if (taskSnapshot.overlapPolicy != null) {
                        task.setOverlapPolicy(taskSnapshot.overlapPolicy);
                    }
                    if (taskSnapshot.sessionParameters != null) {
                        task.getSessionParameters().putAll(taskSnapshot.sessionParameters);
                    }
                    task.setSuccessIntegration(taskSnapshot.successIntegration);
                    task.setFinalizedRootTask(taskSnapshot.finalizedRootTask);
                    task.setExecuteAsUser(taskSnapshot.executeAsUser);
                    task.setServerlessTaskMinStatementSize(taskSnapshot.serverlessTaskMinStatementSize);
                    // The recorded runs, replayed through the task so its last run and failure count follow.
                    if (taskSnapshot.history != null) {
                        for (final TaskExecutionSnapshot run : taskSnapshot.history) {
                            task.recordExecution(new TaskExecution(run.scheduledTime, run.startTime, run.endTime,
                                TaskExecutionState.valueOf(run.state), run.errorMessage, run.rowsAffected,
                                taskTrigger(run.scheduledFrom)));
                        }
                    }
                    schema.addTask(task);
                }
            }

            // Load join policies (the table's attachment is restored with the table above)
            if (schemaSnapshot.joinPolicies != null) {
                for (final ProjectionPolicySnapshot policySnapshot : schemaSnapshot.joinPolicies) {
                    final JoinPolicy policy = new JoinPolicy(policySnapshot.name, policySnapshot.body);
                    policy.setComment(policySnapshot.comment);
                    if (policySnapshot.owner != null) {
                        policy.setOwner(policySnapshot.owner);
                    }
                    schema.addJoinPolicy(policy);
                }
            }

            // Load aggregation policies (the table's attachment is restored with the table above)
            if (schemaSnapshot.aggregationPolicies != null) {
                for (final ProjectionPolicySnapshot policySnapshot : schemaSnapshot.aggregationPolicies) {
                    final AggregationPolicy policy = new AggregationPolicy(policySnapshot.name, policySnapshot.body);
                    policy.setComment(policySnapshot.comment);
                    if (policySnapshot.owner != null) {
                        policy.setOwner(policySnapshot.owner);
                    }
                    schema.addAggregationPolicy(policy);
                }
            }

            // Load projection policies (a column's attachment is restored with the column above)
            if (schemaSnapshot.projectionPolicies != null) {
                for (final ProjectionPolicySnapshot policySnapshot : schemaSnapshot.projectionPolicies) {
                    final ProjectionPolicy policy = new ProjectionPolicy(policySnapshot.name, policySnapshot.body);
                    policy.setComment(policySnapshot.comment);
                    if (policySnapshot.owner != null) {
                        policy.setOwner(policySnapshot.owner);
                    }
                    schema.addProjectionPolicy(policy);
                }
            }

            SecurityObjectSnapshots.restore(schemaSnapshot.securityObjects, schema.getSecurityObjects());

            // Load contacts (a table's attachments are restored with the table above)
            if (schemaSnapshot.contacts != null) {
                for (final ContactSnapshot contactSnapshot : schemaSnapshot.contacts) {
                    final Contact contact = new Contact(contactSnapshot.name);
                    contact.setComment(contactSnapshot.comment);
                    contact.setUrl(contactSnapshot.url);
                    contact.setEmailDistributionList(contactSnapshot.emailDistributionList);
                    if (contactSnapshot.owner != null) {
                        contact.setOwner(contactSnapshot.owner);
                    }
                    schema.addContact(contact);
                }
            }

            // Load masking policies (the policy-to-column binding is restored on the column above)
            if (schemaSnapshot.maskingPolicies != null) {
                for (final MaskingPolicySnapshot policySnapshot : schemaSnapshot.maskingPolicies) {
                    final List<Parameter> params = new ArrayList<>();
                    if (policySnapshot.parameters != null) {
                        for (final MaskingPolicyParameterSnapshot paramSnapshot : policySnapshot.parameters) {
                            params.add(new Parameter(paramSnapshot.name,
                                parseDataType(paramSnapshot.dataType), paramSnapshot.defaultValue));
                        }
                    }
                    final MaskingPolicy policy = new MaskingPolicy(policySnapshot.name, params,
                        policySnapshot.returnType, policySnapshot.body);
                    policy.setComment(policySnapshot.comment);
                    if (policySnapshot.owner != null) {
                        policy.setOwner(policySnapshot.owner);
                    }
                    schema.addMaskingPolicy(policy);
                }
            }

            // Load file formats
            if (schemaSnapshot.fileFormats != null) {
                for (final FileFormatSnapshot ffSnapshot : schemaSnapshot.fileFormats) {
                    final FileFormat ff = new FileFormat(ffSnapshot.name, ffSnapshot.type);
                    ff.setComment(ffSnapshot.comment);
                    if (ffSnapshot.options != null) {
                        for (final Map.Entry<String, String> opt : ffSnapshot.options.entrySet()) {
                            ff.setOption(opt.getKey(), opt.getValue());
                        }
                    }
                    schema.addFileFormat(ff);
                }
            }

            // Load user-defined functions
            if (schemaSnapshot.functions != null) {
                for (final FunctionSnapshot fnSnapshot : schemaSnapshot.functions) {
                    final Function fn = new Function(fnSnapshot.name,
                        restoreParameters(fnSnapshot.parameters),
                        fnSnapshot.returnType != null ? parseDataType(fnSnapshot.returnType) : null,
                        restoreParameters(fnSnapshot.returnColumns),
                        fnSnapshot.body, fnSnapshot.tableFunction, fnSnapshot.language,
                        fnSnapshot.handler, fnSnapshot.runtimeVersion);
                    if (fnSnapshot.nullHandling != null) {
                        fn.setNullHandling(fnSnapshot.nullHandling);
                    }
                    if (fnSnapshot.volatility != null) {
                        fn.setVolatility(fnSnapshot.volatility);
                    }
                    fn.setSecure(fnSnapshot.secure);
                    fn.setMemoizable(fnSnapshot.memoizable);
                    fn.setImports(fnSnapshot.imports);
                    fn.setComment(fnSnapshot.comment);
                    if (fnSnapshot.owner != null) {
                        fn.setOwner(fnSnapshot.owner);
                    }
                    if (fnSnapshot.serviceName != null) {
                        fn.setService(fnSnapshot.serviceName, fnSnapshot.serviceEndpoint, fnSnapshot.maxBatchRows);
                    }
                    SnapshotTags.restore(fn, fnSnapshot.tags);
                    schema.addFunction(fn);
                }
            }

            // Load stored procedures
            if (schemaSnapshot.procedures != null) {
                for (final ProcedureSnapshot procSnapshot : schemaSnapshot.procedures) {
                    final Procedure proc = new Procedure(procSnapshot.name,
                        restoreParameters(procSnapshot.parameters),
                        procSnapshot.returnType != null ? parseDataType(procSnapshot.returnType) : null,
                        procSnapshot.body, procSnapshot.language,
                        procSnapshot.handler, procSnapshot.runtimeVersion, procSnapshot.packages);
                    if (procSnapshot.executeAs != null) {
                        proc.setExecuteAs(procSnapshot.executeAs);
                    }
                    proc.setImports(procSnapshot.imports);
                    proc.setComment(procSnapshot.comment);
                    proc.setReturnsTable(procSnapshot.returnsTable);
                    if (procSnapshot.returnColumns != null) {
                        proc.setReturnColumns(restoreParameters(procSnapshot.returnColumns));
                    }
                    if (procSnapshot.nullHandling != null) {
                        proc.setNullHandling(procSnapshot.nullHandling);
                    }
                    if (procSnapshot.volatility != null) {
                        proc.setVolatility(procSnapshot.volatility);
                    }
                    if (procSnapshot.owner != null) {
                        proc.setOwner(procSnapshot.owner);
                    }
                    SnapshotTags.restore(proc, procSnapshot.tags);
                    schema.addProcedure(proc);
                }
            }

            // Load pipes
            if (schemaSnapshot.pipes != null) {
                for (final PipeSnapshot pipeSnapshot : schemaSnapshot.pipes) {
                    final Pipe pipe = new Pipe(pipeSnapshot.name, pipeSnapshot.copyStatement,
                        pipeSnapshot.autoIngest, pipeSnapshot.notificationChannel);
                    pipe.setPaused(pipeSnapshot.paused);
                    SnapshotTags.restore(pipe, pipeSnapshot.tags);
                    pipe.setErrorIntegration(pipeSnapshot.errorIntegration);
                    pipe.setAwsSnsTopicArn(pipeSnapshot.awsSnsTopicArn);
                    pipe.setIntegration(pipeSnapshot.integration);
                    pipe.setLastLoadedFileCount(pipeSnapshot.lastLoadedFileCount);
                    pipe.setLastLoadedTime(pipeSnapshot.lastLoadedTime);
                    pipe.setComment(pipeSnapshot.comment);
                    if (pipeSnapshot.owner != null) {
                        pipe.setOwner(pipeSnapshot.owner);
                    }
                    schema.addPipe(pipe);
                }
            }

            // Load dynamic tables (definition only; contents rebuild on the next refresh)
            if (schemaSnapshot.dynamicTables != null) {
                for (final DynamicTableSnapshot dtSnapshot : schemaSnapshot.dynamicTables) {
                    final DynamicTable dt = new DynamicTable(dtSnapshot.name, dtSnapshot.query,
                        dtSnapshot.targetLag, dtSnapshot.warehouse);
                    dt.setComment(dtSnapshot.comment);
                    dt.setLastDdlBy(dtSnapshot.lastDdlBy);
                    if (dtSnapshot.owner != null) {
                        dt.setOwner(dtSnapshot.owner);
                    }
                    if (dtSnapshot.clusterKeys != null) {
                        dt.setClusterKeys(dtSnapshot.clusterKeys);
                    }
                    dt.setTransient(dtSnapshot.transientTable);
                    schema.addDynamicTable(dt);
                }
            }

            // Load row access policies (the policy-to-table binding is restored on the table above)
            if (schemaSnapshot.rowAccessPolicies != null) {
                for (final RowAccessPolicySnapshot policySnapshot : schemaSnapshot.rowAccessPolicies) {
                    final RowAccessPolicy policy = new RowAccessPolicy(policySnapshot.name,
                        restoreParameters(policySnapshot.parameters), policySnapshot.body);
                    policy.setComment(policySnapshot.comment);
                    if (policySnapshot.owner != null) {
                        policy.setOwner(policySnapshot.owner);
                    }
                    schema.addRowAccessPolicy(policy);
                }
            }

            // Load tags
            if (schemaSnapshot.tags != null) {
                for (final TagSnapshot tagSnapshot : schemaSnapshot.tags) {
                    final Tag tag = new Tag(tagSnapshot.name,
                        tagSnapshot.allowedValues, tagSnapshot.comment);
                    if (tagSnapshot.owner != null) {
                        tag.setOwner(tagSnapshot.owner);
                    }
                    schema.addTag(tag);
                }
            }

            // The schema's settings, and the objects added beside the long-standing ones
            RestCatalogSnapshots.restoreSchema(schemaSnapshot, schema);
        }
    }

    /** Rebuild a parameter list from its snapshot (data types re-parsed by name). */
    static List<Parameter> restoreParameters(final List<ParameterSnapshot> snapshots) {
        final List<Parameter> parameters = new ArrayList<>();
        if (snapshots != null) {
            for (final ParameterSnapshot paramSnapshot : snapshots) {
                parameters.add(new Parameter(paramSnapshot.name,
                    parseDataType(paramSnapshot.dataType), paramSnapshot.defaultValue));
            }
        }
        return parameters;
    }

    /**
     * Load table data from disk
     */
    static void loadTableData(final TableDataStore tableData, final String database, final String schema,
                               final Table table, final boolean shadowed, final StorageEngine storageEngine)
            throws IOException, ClassNotFoundException {
        final TableDataSnapshot dataSnapshot = shadowed
            ? tableData.loadShadowed(database, schema, table.getName()) : tableData.load(database, schema, table.getName());
        if (dataSnapshot == null) {
            logger.debug("No data recorded for table: {}.{}.{}", database, schema, table.getName());
            return;
        }

        final String qualifiedName = QualifiedName.key(database, schema, table.getName());
        final TableStorage storage = shadowed
            ? storageEngine.getShadowedTableStorage(qualifiedName) : storageEngine.getTableStorage(qualifiedName);

        // Insert all rows — each row's values defensively copied, so a snapshot applied from memory
        // never shares mutable lists with the engine it builds.
        for (final List<Object> rowValues : dataSnapshot.rows) {
            storage.insert(new Row(new ArrayList<>(rowValues)));
        }
        logger.debug("Loaded table data: {}.{}.{} ({} rows)",
                    database, schema, table.getName(), dataSnapshot.rows.size());
    }

    /**
     * Rebuild a DERIVED relation's frozen column list — a view's. Null in, null out, so a snapshot
     * written before view columns were captured leaves the view reporting none, as it did then.
     */
    static List<TableColumn> derivedColumns(final List<ColumnSnapshot> snapshots) {
        if (snapshots == null) {
            return null;
        }
        final List<TableColumn> columns = new ArrayList<>();
        for (final ColumnSnapshot colSnapshot : snapshots) {
            columns.add(new TableColumn(colSnapshot.name,
                parseDataType(colSnapshot.dataType, colSnapshot.precision, colSnapshot.scale,
                    colSnapshot.maxLength),
                colSnapshot.nullable, null, false, false, false));
        }
        return columns;
    }

    /**
     * Convert string type name to DataType instance
     */
    static DataType parseDataType(final String typeName) {
        return parseDataType(typeName, null, null, null);
    }

    /**
     * Rebuild a column type from its snapshot name plus the persisted parameters. The parameters are
     * null on pre-parameter snapshots — those fall back to the name's defaults, matching the old
     * behavior — but when present they restore the exact NUMBER(p,s) / VARCHAR(n), so a checkpointed
     * engine computes with the same scales as the one that wrote the checkpoint.
     */
    static DataType parseDataType(final String typeName, final Integer precision, final Integer scale,
                                  final Integer maxLength) {
        return parseDataType(typeName, precision, scale, maxLength, null);
    }

    /**
     * The same rebuild with a BINARY column's fixedness stated outright, which the type NAME cannot
     * carry: both spellings report BINARY. Null restores the older behaviour, where the persisted name
     * was still VARBINARY for the non-fixed spelling.
     *
     * @param typeName   the persisted type name
     * @param precision  a number's precision, or null
     * @param scale      a number's scale, or null
     * @param maxLength  a string or binary width, or null
     * @param fixedBinary whether a binary is the fixed spelling, or null on an older snapshot
     * @return the rebuilt type
     */
    static DataType parseDataType(final String typeName, final Integer precision, final Integer scale,
                                  final Integer maxLength, final Boolean fixedBinary) {
        if (typeName == null) {
            return StringType.VARCHAR; // default
        }

        final String upper = typeName.toUpperCase();
        final DataType interval = IntervalTypes.forName(upper);
        if (interval != null) {
            return interval;
        }
        switch (upper) {
            case "INTEGER":
            case "INT":
                return NumericType.INTEGER;
            case "BIGINT":
                return NumericType.BIGINT;
            case "SMALLINT":
                return NumericType.SMALLINT;
            case "TINYINT":
                return NumericType.TINYINT;
            case "UUID":
                return UuidType.UUID;
            case "NUMBER":
            case "DECIMAL":
            case "NUMERIC":
                return precision != null && scale != null
                    ? new NumericType(upper, precision, scale)
                    : NumericType.NUMBER;
            case "FLOAT":
                return NumericType.FLOAT;
            case "REAL":
            case "DOUBLE":
                return NumericType.DOUBLE;
            case "VARCHAR":
            case "STRING":
            case "TEXT":
                return maxLength != null && maxLength > 0
                    ? new StringType(upper, maxLength)
                    : StringType.VARCHAR;
            case "CHAR":
                return maxLength != null && maxLength > 0
                    ? new StringType(upper, maxLength)
                    : StringType.CHAR;
            case "BOOLEAN":
                return BooleanType.BOOLEAN;
            case "DATE":
                return DateTimeType.DATE;
            case "TIME":
                return DateTimeType.TIME;
            case "DATETIME":
                return DateTimeType.DATETIME;
            case "TIMESTAMP":
            case "TIMESTAMP_NTZ":
                return DateTimeType.TIMESTAMP_NTZ;
            case "TIMESTAMP_TZ":
                return DateTimeType.TIMESTAMP_TZ;
            case "TIMESTAMP_LTZ":
                return DateTimeType.TIMESTAMP_LTZ;
            case "VARIANT":
                return VariantType.VARIANT;
            case "ARRAY":
                return ArrayType.ARRAY;
            case "OBJECT":
                return ObjectType.OBJECT;
            case "BINARY":
            case "VARBINARY":
                // Both the WIDTH and the FIXEDNESS have to survive a restart: the width is the column's
                // declared one, and fixedness is the only thing SHOW COLUMNS' fixed cell reads. An
                // older snapshot carried fixedness in the NAME, which is the fallback here.
                final boolean fixed = fixedBinary != null ? fixedBinary.booleanValue()
                    : !"VARBINARY".equals(upper);
                return new BinaryType(maxLength != null && maxLength > 0
                    ? maxLength.intValue() : BinaryType.BINARY.getMaxLength(), fixed);
            default:
                return StringType.VARCHAR; // default
        }
    }
}
