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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.StreamSources;
import dev.frostlake.metastore.model.AggregationPolicy;
import dev.frostlake.metastore.model.CheckConstraint;
import dev.frostlake.metastore.model.Contact;
import dev.frostlake.metastore.model.CortexSearchService;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DefaultValueExpression;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.DynamicTableColumn;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.JoinPolicy;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.ProjectionPolicy;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SearchOptimizationExpression;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamRecord;
import dev.frostlake.metastore.model.StreamSourceType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskExecution;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.metastore.model.User;
import dev.frostlake.metastore.model.View;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
        final CatalogSnapshot snapshot = new CatalogSnapshot();
        snapshot.currentDatabase = catalog.getCurrentDatabase();
        snapshot.currentSchema = catalog.getCurrentSchema();
        snapshot.currentWarehouse = catalog.getCurrentWarehouse();

        // Save databases
        for (final Database db : catalog.getAllDatabases()) {
            snapshot.databases.add(saveDatabase(catalog, db, storageEngine, tableData));
        }
        // And the dropped ones UNDROP can still restore
        snapshot.droppedObjects = DroppedObjectSnapshots.save(catalog);

        snapshot.securityObjects = SecurityObjectSnapshots.save(catalog.getSecurityObjects());
        snapshot.securityAttachments = new HashMap<>(catalog.getSecurityObjects().attachments());
        RestCatalogSnapshots.saveCatalog(catalog, snapshot);

        // Save warehouses
        for (final Warehouse wh : catalog.getAllWarehouses()) {
            final WarehouseSnapshot whSnapshot = new WarehouseSnapshot();
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
            RestCatalogSnapshots.saveWarehouse(wh, whSnapshot);
            snapshot.warehouses.add(whSnapshot);
        }

        // Save users
        for (final User user : catalog.getAllUsers()) {
            final UserSnapshot userSnapshot = new UserSnapshot();
            userSnapshot.name = user.getName();
            userSnapshot.password = user.getPassword();
            userSnapshot.defaultRole = user.getDefaultRole();
            userSnapshot.grantedRoles = new ArrayList<>(user.getGrantedRoles());
            userSnapshot.comment = user.getComment();
            userSnapshot.createdAt = user.getCreatedTime();
            userSnapshot.enabled = user.isEnabled();
            userSnapshot.owner = user.getOwner();
            userSnapshot.privileges = snapshotPrivileges(user.getAllPrivileges(), user.getAllColumnPrivileges());
            // The CREATE USER property set, written as-is so an unset property stays unset.
            userSnapshot.propertiesWritten = Boolean.TRUE;
            userSnapshot.loginName = user.getLoginName();
            userSnapshot.displayName = user.getDisplayName();
            userSnapshot.firstName = user.getFirstName();
            userSnapshot.middleName = user.getMiddleName();
            userSnapshot.lastName = user.getLastName();
            userSnapshot.email = user.getEmail();
            userSnapshot.defaultWarehouse = user.getDefaultWarehouse();
            userSnapshot.defaultNamespace = user.getDefaultNamespace();
            userSnapshot.defaultSecondaryRoles = user.getDefaultSecondaryRoles();
            userSnapshot.mustChangePassword = user.isMustChangePassword();
            userSnapshot.userType = user.getUserType();
            userSnapshot.expiresAt = user.getExpiresAt();
            userSnapshot.lockedUntil = user.getLockedUntil();
            userSnapshot.mfaBypassUntil = user.getMfaBypassUntil();
            userSnapshot.rsaPublicKey = user.getRsaPublicKey();
            userSnapshot.rsaPublicKeyFp = user.getRsaPublicKeyFp();
            userSnapshot.rsaPublicKeyLastSetTime = user.getRsaPublicKeyLastSetTime();
            userSnapshot.rsaPublicKey2 = user.getRsaPublicKey2();
            userSnapshot.rsaPublicKey2Fp = user.getRsaPublicKey2Fp();
            userSnapshot.rsaPublicKey2LastSetTime = user.getRsaPublicKey2LastSetTime();
            RestCatalogSnapshots.saveUserGrants(user, userSnapshot);
            snapshot.users.add(userSnapshot);
        }

        // Save roles
        for (final Role role : catalog.getAllRoles()) {
            // Object- and column-level privileges, with their grantors, grant options and future grants.
            snapshot.roles.add(RestCatalogSnapshots.saveRole(role));
        }

        return snapshot;
    }

    /**
     * A database as a snapshot holds it, each of its schemas saved by {@link #saveSchema}: the write half of a live
     * database's restore, and of a dropped one's ({@link DroppedObjectSnapshots}).
     */
    static DatabaseSnapshot saveDatabase(final Catalog catalog, final Database db, final StorageEngine storageEngine,
                                         final TableDataStore tableData) throws IOException {
        final DatabaseSnapshot dbSnapshot = new DatabaseSnapshot();
        dbSnapshot.name = db.getName();
        dbSnapshot.tags = SnapshotTags.of(db);
        dbSnapshot.comment = db.getComment();
        dbSnapshot.createdAt = db.getCreatedTime();
        dbSnapshot.lastDdlAt = db.getLastDdlTime();
        dbSnapshot.owner = db.getOwner();
        dbSnapshot.readOnly = db.isReadOnly();
        RestCatalogSnapshots.saveDatabase(db, dbSnapshot);

        // Save schemas
        for (final Schema schema : db.getAllSchemas()) {
            dbSnapshot.schemas.add(saveSchema(catalog, db.getName(), schema, storageEngine, tableData));
        }
        return dbSnapshot;
    }

    /**
     * A schema as a snapshot holds it, every object in it included. Its tables' rows go to {@code tableData}, or
     * nowhere when that is null: a dropped version keeps its rows beside it ({@link DroppedObjectSnapshot}), never
     * under a name a live table may hold. With a null {@code catalog} a stream is saved stale only when it is: a
     * dropped schema's tables are not in the catalog to look up, and they come back with the schema.
     */
    static SchemaSnapshot saveSchema(final Catalog catalog, final String databaseName, final Schema schema,
                                     final StorageEngine storageEngine, final TableDataStore tableData)
            throws IOException {
        final SchemaSnapshot schemaSnapshot = new SchemaSnapshot();
        schemaSnapshot.name = schema.getName();
        schemaSnapshot.tagValues = SnapshotTags.of(schema);
        schemaSnapshot.comment = schema.getComment();
        schemaSnapshot.createdAt = schema.getCreatedTime();
        schemaSnapshot.lastDdlAt = schema.getLastDdlTime();
        schemaSnapshot.owner = schema.getOwner();
        RestCatalogSnapshots.saveSchema(schema, schemaSnapshot);

        // Save table metadata, a permanent table a temporary one hides included
        final List<Table> tables = schema.getTables();
        tables.addAll(schema.getShadowedTables());
        for (final Table table : tables) {
            final boolean shadowed = schema.shadowedTable(table.getName()) == table;
            final TableSnapshot tableSnapshot = new TableSnapshot();
            tableSnapshot.name = table.getName();
            tableSnapshot.tags = SnapshotTags.of(table);
            tableSnapshot.shadowed = shadowed;
            tableSnapshot.comment = table.getComment();
            tableSnapshot.lastDdlBy = table.getLastDdlBy();
            tableSnapshot.createdAt = table.getCreatedTime();
            tableSnapshot.lastDdlAt = table.getLastDdlTime();
            tableSnapshot.lastDataChange = table.getLastDataChange();
            tableSnapshot.dataChanges = table.getDataChangeCount();
            tableSnapshot.lastDml = table.getLastDml();
            tableSnapshot.constraintCreated = stampsOrNull(table.getConstraintCreatedTimes());
            tableSnapshot.constraintAltered = stampsOrNull(table.getConstraintAlteredTimes());
            tableSnapshot.owner = table.getOwner();
            tableSnapshot.temporary = table.isTemporary();
            tableSnapshot.isTransient = table.isTransient();
            tableSnapshot.hybrid = table.isHybrid();
            tableSnapshot.clusterKeys = table.getClusterKeys() != null && !table.getClusterKeys().isEmpty()
                ? new ArrayList<>(table.getClusterKeys()) : null;
            tableSnapshot.clusterByText = table.getClusterByText();

            for (final TableColumn col : table.getColumns()) {
                final ColumnSnapshot colSnapshot = new ColumnSnapshot();
                colSnapshot.name = col.getName();
                colSnapshot.dataType = col.getDataType().getName();
                colSnapshot.type = col.getDataType();
                if (col.getDataType() instanceof NumericType) {
                    final NumericType numericType = (NumericType) col.getDataType();
                    colSnapshot.precision = numericType.getPrecision();
                    colSnapshot.scale = numericType.getScale();
                } else if (col.getDataType() instanceof StringType) {
                    colSnapshot.maxLength = ((StringType) col.getDataType()).getMaxLength();
                } else if (col.getDataType() instanceof BinaryType) {
                    // The width AND the fixedness: the name says BINARY for both spellings, so
                    // neither survives on its own.
                    colSnapshot.maxLength = ((BinaryType) col.getDataType()).getMaxLength();
                    colSnapshot.binaryFixed =
                        Boolean.valueOf(((BinaryType) col.getDataType()).isFixed());
                }
                colSnapshot.nullable = col.isNullable();
                colSnapshot.primaryKey = col.isPrimaryKey();
                colSnapshot.defaultValue = col.getDefaultValue() != null ? col.getDefaultValue().toString() : null;
                colSnapshot.defaultIsExpression = col.getDefaultValue() instanceof DefaultValueExpression;
                colSnapshot.comment = col.getComment();
                colSnapshot.maskingPolicyName = col.getMaskingPolicyName();
                colSnapshot.maskingPolicyArguments = col.getMaskingPolicyArguments();
                colSnapshot.projectionPolicyName = col.getProjectionPolicyName();
                colSnapshot.unique = col.isUnique();
                colSnapshot.autoIncrement = col.isAutoIncrement();
                colSnapshot.identityStart = col.getIdentityStart();
                colSnapshot.identityIncrement = col.getIdentityIncrement();
                colSnapshot.identityOrdered = col.isIdentityOrdered();
                colSnapshot.collation = col.getCollation();
                colSnapshot.ordinalPosition = col.getOrdinalPosition();
                colSnapshot.tags = SnapshotTags.of(col);
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
            tableSnapshot.highestOrdinal = table.getHighestOrdinal();
            tableSnapshot.nextIdentityValue = table.getNextIdentityValue();
            tableSnapshot.primaryKeyConstraintName = table.primaryKeyConstraintName();
            if (!table.getDeclaredUniqueConstraints().isEmpty()) {
                tableSnapshot.uniqueConstraints = new ArrayList<>();
                for (final UniqueConstraint unique : table.getDeclaredUniqueConstraints()) {
                    final UniqueConstraintSnapshot uniqueSnapshot = new UniqueConstraintSnapshot();
                    uniqueSnapshot.constraintName = unique.getConstraintName();
                    uniqueSnapshot.columnNames = new ArrayList<>(unique.getColumnNames());
                    tableSnapshot.uniqueConstraints.add(uniqueSnapshot);
                }
            }

            // Table-level FOREIGN KEY constraints (column-level REFERENCES are on the column above).
            if (!table.getForeignKeys().isEmpty()) {
                tableSnapshot.foreignKeys = new ArrayList<>();
                for (final ForeignKeyConstraint fk : table.getForeignKeys()) {
                    final ForeignKeyConstraintSnapshot fkSnapshot = new ForeignKeyConstraintSnapshot();
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
            tableSnapshot.checkConstraints = new ArrayList<>();
            for (final CheckConstraint check : table.getCheckConstraints()) {
                final CheckConstraintSnapshot checkSnapshot = new CheckConstraintSnapshot();
                checkSnapshot.name = check.getName();
                checkSnapshot.expression = check.getExpression();
                checkSnapshot.autoNamed = check.isAutoNamed();
                checkSnapshot.referencedColumns = check.getReferencedColumns();
                tableSnapshot.checkConstraints.add(checkSnapshot);
            }
            tableSnapshot.aggregationPolicyName = table.getAggregationPolicyName();
            tableSnapshot.joinPolicyName = table.getJoinPolicyName();
            tableSnapshot.searchOptimization = new ArrayList<>();
            for (final SearchOptimizationExpression expression : table.getSearchOptimization()) {
                final SearchOptimizationSnapshot soSnapshot = new SearchOptimizationSnapshot();
                soSnapshot.expressionId = expression.getExpressionId();
                soSnapshot.method = expression.getMethod();
                soSnapshot.target = expression.getTarget();
                soSnapshot.targetDataType = expression.getTargetDataType();
                tableSnapshot.searchOptimization.add(soSnapshot);
            }
            tableSnapshot.aggregationEntityKey = table.getAggregationEntityKey();
            tableSnapshot.contacts = table.getContacts();
            tableSnapshot.rowAccessPolicyName = table.getRowAccessPolicyName();
            tableSnapshot.rowAccessPolicyColumns = table.getRowAccessPolicyName() != null
                ? new ArrayList<>(table.getRowAccessPolicyColumns()) : null;

            RestCatalogSnapshots.saveTable(table, tableSnapshot);
            schemaSnapshot.tables.add(tableSnapshot);

            // Save table data (a dropped version's rows are kept apart; see the method's comment)
            if (tableData != null) {
                final TableDataSnapshot rows =
                    buildTableData(databaseName, schema.getName(), table, shadowed, storageEngine);
                if (shadowed) {
                    tableData.saveShadowed(databaseName, schema.getName(), table.getName(), rows);
                } else {
                    tableData.save(databaseName, schema.getName(), table.getName(), rows);
                }
            }
        }

        // Save view metadata
        for (final View view : schema.getViews()) {
            final ViewSnapshot viewSnapshot = new ViewSnapshot();
            viewSnapshot.name = view.getName();
            viewSnapshot.tags = SnapshotTags.of(view);
            viewSnapshot.query = view.getDefinition();
            viewSnapshot.comment = view.getComment();
            viewSnapshot.lastDdlBy = view.getLastDdlBy();
            viewSnapshot.createdAt = view.getCreatedTime();
            viewSnapshot.lastDdlAt = view.getLastDdlTime();
            viewSnapshot.owner = view.getOwner();
            viewSnapshot.secure = view.isSecure();
            viewSnapshot.recursive = view.isRecursive();
            viewSnapshot.writtenBody = view.getWrittenBody();
            viewSnapshot.originalDdl = view.getOriginalDdl();
            viewSnapshot.definitionText = view.getDefinitionText();
            viewSnapshot.listedText = view.getListedText();
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
            final SequenceSnapshot seqSnapshot = new SequenceSnapshot();
            seqSnapshot.name = seq.getName();
            seqSnapshot.startValue = seq.getStartValue();
            seqSnapshot.increment = seq.getIncrement();
            seqSnapshot.order = seq.isOrder();
            seqSnapshot.currentValue = seq.getCurrentValueRaw();
            seqSnapshot.comment = seq.getComment();
            seqSnapshot.owner = seq.getOwner();
            seqSnapshot.createdAt = seq.getCreatedTime();
            seqSnapshot.lastDdlAt = seq.getLastDdlTime();
            schemaSnapshot.sequences.add(seqSnapshot);
        }

        // Save stages (the DEFINITION — the local backing dir is recomputed on restore, like
        // CREATE STAGE does — so @stage references in COPY / UDF IMPORTS keep resolving)
        for (final Stage stage : schema.getStages()) {
            final StageSnapshot stageSnapshot = new StageSnapshot();
            stageSnapshot.name = stage.getName();
            stageSnapshot.type = stage.getType().name();
            stageSnapshot.url = stage.getUrl();
            stageSnapshot.fileFormat = stage.getFileFormat();
            stageSnapshot.encryption = stage.isEncryption();
            stageSnapshot.comment = stage.getComment();
            stageSnapshot.owner = stage.getOwner();
            stageSnapshot.createdAt = stage.getCreatedTime();
            stageSnapshot.lastDdlAt = stage.getLastDdlTime();
            RestCatalogSnapshots.saveStage(stage, stageSnapshot);
            schemaSnapshot.stages.add(stageSnapshot);
        }

        // Save Cortex search services (the whole definition; nothing is indexed to save)
        for (final CortexSearchService service : schema.getCortexSearchServices()) {
            final CortexSearchServiceSnapshot serviceSnapshot = new CortexSearchServiceSnapshot();
            serviceSnapshot.name = service.getName();
            serviceSnapshot.searchColumn = service.getSearchColumn();
            serviceSnapshot.attributeColumns = new ArrayList<>(service.getAttributeColumns());
            serviceSnapshot.columns = new ArrayList<>(service.getColumns());
            serviceSnapshot.warehouse = service.getWarehouse();
            serviceSnapshot.targetLag = service.getTargetLag();
            serviceSnapshot.embeddingModel = service.getEmbeddingModel();
            serviceSnapshot.definition = service.getDefinition();
            serviceSnapshot.comment = service.getComment();
            serviceSnapshot.owner = service.getOwner();
            serviceSnapshot.createdAt = service.getCreatedOn();
            serviceSnapshot.indexingSuspended = service.isIndexingSuspended();
            serviceSnapshot.servingSuspended = service.isServingSuspended();
            serviceSnapshot.primaryKeyColumns = new ArrayList<>(service.getPrimaryKeyColumns());
            serviceSnapshot.fullIndexBuildIntervalDays = service.getFullIndexBuildIntervalDays();
            serviceSnapshot.requestLogging = service.isRequestLogging();
            serviceSnapshot.autoSuspend = service.getAutoSuspend();
            serviceSnapshot.tags = new LinkedHashMap<>(service.getTagValues());
            schemaSnapshot.cortexSearchServices.add(serviceSnapshot);
        }

        // Save streams (definition + pending unconsumed change records)
        for (final Stream stream : schema.getStreams()) {
            final StreamSnapshot streamSnapshot = new StreamSnapshot();
            streamSnapshot.name = stream.getName();
            streamSnapshot.tags = SnapshotTags.of(stream);
            streamSnapshot.sourceTableName = stream.getSourceTableName();
            streamSnapshot.baseTableName = stream.getBaseTableName();
            streamSnapshot.baseTableNames = new ArrayList<>(stream.getBaseTableNames());
            streamSnapshot.sourceType = stream.getSourceType().name();
            streamSnapshot.streamType = stream.getStreamType().name();
            streamSnapshot.showInitialRows = stream.isShowInitialRows();
            // A stream whose table is gone, or any of whose view's tables is, is saved stale, so a restore
            // never binds it to a later table of the same name.
            streamSnapshot.stale = stream.isStale() || catalog != null
                && (stream.getSourceType() == StreamSourceType.TABLE && StreamSources.sourceOf(catalog, schema, stream) == null
                || StreamSources.missingBaseOf(catalog, stream) != null);
            streamSnapshot.comment = stream.getComment();
            streamSnapshot.owner = stream.getOwner();
            streamSnapshot.createdAt = stream.getCreatedAt();
            for (final StreamRecord rec : stream.getUnconsumedRecords()) {
                streamSnapshot.records.add(recordSnapshot(rec));
            }
            if (stream.getInitialRecords() != null) {
                streamSnapshot.initialRecords = new ArrayList<>();
                for (final StreamRecord rec : stream.getInitialRecords()) {
                    streamSnapshot.initialRecords.add(recordSnapshot(rec));
                }
            }
            if (stream.getRefreshImage() != null) {
                streamSnapshot.refreshImage = new ArrayList<>();
                for (final List<Object> row : stream.getRefreshImage()) {
                    streamSnapshot.refreshImage.add(new ArrayList<>(row));
                }
            }
            schemaSnapshot.streams.add(streamSnapshot);
        }

        // Save tasks (definition + STARTED/SUSPENDED state)
        for (final Task task : schema.getTasks()) {
            final TaskSnapshot taskSnapshot = new TaskSnapshot();
            taskSnapshot.name = task.getName();
            taskSnapshot.tags = SnapshotTags.of(task);
            taskSnapshot.id = task.getId();
            taskSnapshot.createdByUser = task.getCreatedByUser();
            taskSnapshot.explicitParameters = new ArrayList<>(task.getExplicitParameters());
            taskSnapshot.schedule = task.getSchedule();
            taskSnapshot.scheduleType = task.getScheduleType() != null ? task.getScheduleType().name() : null;
            taskSnapshot.sqlStatement = task.getSqlStatement();
            taskSnapshot.warehouse = task.getWarehouse();
            taskSnapshot.predecessors = new ArrayList<>(task.getPredecessors());
            taskSnapshot.state = task.getState() != null ? task.getState().name() : null;
            taskSnapshot.comment = task.getComment();
            taskSnapshot.condition = task.getCondition();
            taskSnapshot.owner = task.getOwner();
            taskSnapshot.createdAt = task.getCreatedAt();
            taskSnapshot.suspendTaskAfterNumFailures = task.getSuspendTaskAfterNumFailures();
            taskSnapshot.taskAutoRetryAttempts = task.getTaskAutoRetryAttempts();
            taskSnapshot.allowOverlappingExecution = task.isAllowOverlappingExecution();
            taskSnapshot.userTaskTimeoutMs = task.getUserTaskTimeoutMs();
            taskSnapshot.userTaskManagedInitialWarehouseSize = task.getUserTaskManagedInitialWarehouseSize();
            taskSnapshot.serverlessTaskMaxStatementSize = task.getServerlessTaskMaxStatementSize();
            taskSnapshot.targetCompletionInterval = task.getTargetCompletionInterval();
            taskSnapshot.errorIntegration = task.getErrorIntegration();
            taskSnapshot.userTaskMinimumTriggerIntervalInSeconds = task.getUserTaskMinimumTriggerIntervalInSeconds();
            taskSnapshot.config = task.getConfig();
            taskSnapshot.overlapPolicy = task.getOverlapPolicy();
            taskSnapshot.sessionParameters = new LinkedHashMap<>(task.getSessionParameters());
            taskSnapshot.successIntegration = task.getSuccessIntegration();
            taskSnapshot.finalizedRootTask = task.getFinalizedRootTask();
            taskSnapshot.executeAsUser = task.getExecuteAsUser();
            taskSnapshot.contacts = new LinkedHashMap<>(task.getContacts());
            taskSnapshot.serverlessTaskMinStatementSize = task.getServerlessTaskMinStatementSize();
            taskSnapshot.history = new ArrayList<>();
            for (final TaskExecution execution : task.getExecutionHistory()) {
                final TaskExecutionSnapshot run = new TaskExecutionSnapshot();
                run.scheduledTime = execution.getScheduledTime();
                run.startTime = execution.getStartTime();
                run.endTime = execution.getEndTime();
                run.state = execution.getState();
                run.errorMessage = execution.getErrorMessage();
                run.rowsAffected = execution.getRowsAffected();
                run.scheduledFrom = execution.getScheduledFrom();
                taskSnapshot.history.add(run);
            }
            schemaSnapshot.tasks.add(taskSnapshot);
        }

        // Save join policies (same shape again: a name and a body)
        for (final JoinPolicy policy : schema.getJoinPolicies()) {
            final ProjectionPolicySnapshot policySnapshot = new ProjectionPolicySnapshot();
            policySnapshot.name = policy.getName();
            policySnapshot.body = policy.getBody();
            policySnapshot.comment = policy.getComment();
            policySnapshot.owner = policy.getOwner();
            policySnapshot.createdAt = policy.getCreatedTime();
            schemaSnapshot.joinPolicies.add(policySnapshot);
        }

        // Save aggregation policies (same shape as a projection policy: a name and a body)
        for (final AggregationPolicy policy : schema.getAggregationPolicies()) {
            final ProjectionPolicySnapshot policySnapshot = new ProjectionPolicySnapshot();
            policySnapshot.name = policy.getName();
            policySnapshot.body = policy.getBody();
            policySnapshot.comment = policy.getComment();
            policySnapshot.owner = policy.getOwner();
            policySnapshot.createdAt = policy.getCreatedTime();
            schemaSnapshot.aggregationPolicies.add(policySnapshot);
        }

        // Save projection policies (the object; a column's attachment is saved on the column)
        for (final ProjectionPolicy policy : schema.getProjectionPolicies()) {
            final ProjectionPolicySnapshot policySnapshot = new ProjectionPolicySnapshot();
            policySnapshot.name = policy.getName();
            policySnapshot.body = policy.getBody();
            policySnapshot.comment = policy.getComment();
            policySnapshot.owner = policy.getOwner();
            policySnapshot.createdAt = policy.getCreatedTime();
            schemaSnapshot.projectionPolicies.add(policySnapshot);
        }

        schemaSnapshot.securityObjects = SecurityObjectSnapshots.save(schema.getSecurityObjects());

        // Save contacts (the object; a table's attachments are saved on the table)
        for (final Contact contact : schema.getContacts()) {
            final ContactSnapshot contactSnapshot = new ContactSnapshot();
            contactSnapshot.name = contact.getName();
            contactSnapshot.comment = contact.getComment();
            contactSnapshot.url = contact.getUrl();
            contactSnapshot.emailDistributionList = contact.getEmailDistributionList();
            contactSnapshot.owner = contact.getOwner();
            contactSnapshot.createdAt = contact.getCreatedTime();
            schemaSnapshot.contacts.add(contactSnapshot);
        }

        // Save masking policies (definition; the policy-to-column binding is saved on the column)
        for (final MaskingPolicy policy : schema.getMaskingPolicies()) {
            final MaskingPolicySnapshot policySnapshot = new MaskingPolicySnapshot();
            policySnapshot.name = policy.getName();
            policySnapshot.returnType = policy.getReturnType();
            policySnapshot.returnDataType = policy.getReturnDataType();
            policySnapshot.returnSpelling = policy.getReturnSpelling();
            policySnapshot.body = policy.getBody();
            policySnapshot.comment = policy.getComment();
            policySnapshot.owner = policy.getOwner();
            policySnapshot.createdAt = policy.getCreatedTime();
            for (final Parameter param : policy.getParameters()) {
                final MaskingPolicyParameterSnapshot paramSnapshot = new MaskingPolicyParameterSnapshot();
                paramSnapshot.name = param.getName();
                paramSnapshot.dataType = param.getDataType() != null ? param.getDataType().getName() : null;
                paramSnapshot.type = param.getDataType();
                paramSnapshot.defaultValue = param.getDefaultValue();
                policySnapshot.parameters.add(paramSnapshot);
            }
            schemaSnapshot.maskingPolicies.add(policySnapshot);
        }

        // Save file formats (TYPE + options map)
        for (final FileFormat ff : schema.getFileFormats()) {
            final FileFormatSnapshot ffSnapshot = new FileFormatSnapshot();
            ffSnapshot.name = ff.getName();
            ffSnapshot.type = ff.getType();
            ffSnapshot.options = new LinkedHashMap<>(ff.getOptions());
            ffSnapshot.comment = ff.getComment();
            ffSnapshot.owner = ff.getOwner();
            ffSnapshot.createdAt = ff.getCreatedTime();
            ffSnapshot.lastDdlAt = ff.getLastDdlTime();
            schemaSnapshot.fileFormats.add(ffSnapshot);
        }

        // Save user-defined functions (all languages, scalar and table)
        for (final Function fn : schema.getFunctions()) {
            final FunctionSnapshot fnSnapshot = new FunctionSnapshot();
            fnSnapshot.name = fn.getName();
            fnSnapshot.tags = SnapshotTags.of(fn);
            fnSnapshot.parameters = snapshotParameters(fn.getParameters());
            fnSnapshot.returnType = fn.getReturnType() != null ? fn.getReturnType().getName() : null;
            fnSnapshot.returnDataType = fn.getReturnType();
            fnSnapshot.returnColumns = snapshotParameters(fn.getReturnColumns());
            fnSnapshot.body = fn.getBody();
            fnSnapshot.tableFunction = fn.isTableFunction();
            fnSnapshot.language = fn.getLanguage();
            fnSnapshot.handler = fn.getHandler();
            fnSnapshot.runtimeVersion = fn.getRuntimeVersion();
            fnSnapshot.nullHandling = fn.getNullHandling();
            fnSnapshot.volatility = fn.getVolatility();
            fnSnapshot.secure = fn.isSecure();
            fnSnapshot.memoizable = fn.isMemoizable();
            fnSnapshot.imports = new ArrayList<>(fn.getImports());
            fnSnapshot.environment = fn.getEnvironment().copy();
            fnSnapshot.comment = fn.getComment();
            fnSnapshot.owner = fn.getOwner();
            fnSnapshot.createdAt = fn.getCreatedTime();
            fnSnapshot.lastDdlAt = fn.getLastDdlTime();
            fnSnapshot.serviceName = fn.getServiceName();
            fnSnapshot.serviceEndpoint = fn.getServiceEndpoint();
            fnSnapshot.maxBatchRows = fn.getMaxBatchRows();
            schemaSnapshot.functions.add(fnSnapshot);
        }

        // Save stored procedures (all languages)
        for (final Procedure proc : schema.getProcedures()) {
            final ProcedureSnapshot procSnapshot = new ProcedureSnapshot();
            procSnapshot.name = proc.getName();
            procSnapshot.tags = SnapshotTags.of(proc);
            procSnapshot.parameters = snapshotParameters(proc.getParameters());
            procSnapshot.returnType = proc.getReturnType() != null ? proc.getReturnType().getName() : null;
            procSnapshot.returnDataType = proc.getReturnType();
            procSnapshot.body = proc.getBody();
            procSnapshot.language = proc.getLanguage();
            procSnapshot.handler = proc.getHandler();
            procSnapshot.runtimeVersion = proc.getRuntimeVersion();
            procSnapshot.packages = new ArrayList<>(proc.getPackages());
            procSnapshot.executeAs = proc.getExecuteAs();
            procSnapshot.imports = new ArrayList<>(proc.getImports());
            procSnapshot.environment = proc.getEnvironment().copy();
            procSnapshot.comment = proc.getComment();
            procSnapshot.owner = proc.getOwner();
            procSnapshot.createdAt = proc.getCreatedTime();
            procSnapshot.lastDdlAt = proc.getLastDdlTime();
            procSnapshot.returnsTable = proc.returnsTable();
            procSnapshot.returnColumns = snapshotParameters(proc.getReturnColumns());
            procSnapshot.nullHandling = proc.getNullHandling();
            procSnapshot.volatility = proc.getVolatility();
            procSnapshot.secure = proc.isSecure();
            schemaSnapshot.procedures.add(procSnapshot);
        }

        // Save pipes
        for (final Pipe pipe : schema.getPipes()) {
            final PipeSnapshot pipeSnapshot = new PipeSnapshot();
            pipeSnapshot.name = pipe.getName();
            pipeSnapshot.tags = SnapshotTags.of(pipe);
            pipeSnapshot.copyStatement = pipe.getCopyStatement();
            pipeSnapshot.autoIngest = pipe.isAutoIngest();
            pipeSnapshot.notificationChannel = pipe.getNotificationChannel();
            pipeSnapshot.paused = pipe.isPaused();
            pipeSnapshot.stoppedCloned = pipe.isStoppedCloned();
            pipeSnapshot.errorIntegration = pipe.getErrorIntegration();
            pipeSnapshot.awsSnsTopicArn = pipe.getAwsSnsTopicArn();
            pipeSnapshot.integration = pipe.getIntegration();
            pipeSnapshot.lastLoadedFileCount = pipe.getLastLoadedFileCount();
            pipeSnapshot.lastLoadedTime = pipe.getLastLoadedTime();
            pipeSnapshot.comment = pipe.getComment();
            pipeSnapshot.owner = pipe.getOwner();
            pipeSnapshot.createdAt = pipe.getCreatedTime();
            pipeSnapshot.lastDdlAt = pipe.getLastDdlTime();
            schemaSnapshot.pipes.add(pipeSnapshot);
        }

        // Save dynamic tables (definition; the materialized rows are rebuilt on refresh)
        for (final DynamicTable dt : schema.getDynamicTables()) {
            final DynamicTableSnapshot dtSnapshot = new DynamicTableSnapshot();
            dtSnapshot.name = dt.getName();
            dtSnapshot.query = dt.getQuery();
            dtSnapshot.targetLag = dt.getTargetLag();
            dtSnapshot.warehouse = dt.getWarehouse();
            dtSnapshot.refreshMode = dt.getRefreshMode() != null ? dt.getRefreshMode().name() : null;
            dtSnapshot.initialize = dt.getInitialize() != null ? dt.getInitialize().name() : null;
            dtSnapshot.state = dt.getState() != null ? dt.getState().name() : null;
            dtSnapshot.comment = dt.getComment();
            dtSnapshot.owner = dt.getOwner();
            dtSnapshot.createdAt = dt.getCreatedTime();
            dtSnapshot.lastDdlAt = dt.getLastDdlTime();
            dtSnapshot.lastDdlBy = dt.getLastDdlBy();
            dtSnapshot.clusterKeys = dt.getClusterKeys();
            dtSnapshot.refreshedRows = Long.valueOf(dt.getRefreshedRows());
            if (dt.getResolvedColumns() != null) {
                dtSnapshot.resolvedColumns = derivedColumnSnapshots(dt.getResolvedColumns());
            }
            if (!dt.getDeclaredColumns().isEmpty()) {
                dtSnapshot.columnNames = new ArrayList<>();
                dtSnapshot.columnTypes = new ArrayList<>();
                dtSnapshot.columnComments = new ArrayList<>();
                for (final DynamicTableColumn column : dt.getDeclaredColumns()) {
                    dtSnapshot.columnNames.add(column.getName());
                    dtSnapshot.columnTypes.add(column.getType());
                    dtSnapshot.columnComments.add(column.getComment());
                }
            }
            dtSnapshot.transientTable = dt.isTransient();
            dtSnapshot.createText = dt.getCreateText();
            dtSnapshot.bodyText = dt.getBodyText();
            dtSnapshot.definitionDatabase = dt.getDefinitionDatabase();
            dtSnapshot.definitionSchema = dt.getDefinitionSchema();
            dtSnapshot.schedulingState = dt.getSchedulingState();
            dtSnapshot.lastSuspendedOn = dt.getLastSuspendedOn();
            dtSnapshot.dataRetentionDays = dt.getDataRetentionDays();
            dtSnapshot.maxDataExtensionDays = dt.getMaxDataExtensionDays();
            dtSnapshot.reclusterSuspended = dt.isReclusterSuspended();
            dtSnapshot.clone = dt.isClone();
            schemaSnapshot.dynamicTables.add(dtSnapshot);
        }

        // Save row access policies (the policy-to-table binding is saved on the table)
        for (final RowAccessPolicy policy : schema.getRowAccessPolicies()) {
            final RowAccessPolicySnapshot policySnapshot = new RowAccessPolicySnapshot();
            policySnapshot.name = policy.getName();
            policySnapshot.parameters = snapshotParameters(policy.getParameters());
            policySnapshot.body = policy.getBody();
            policySnapshot.comment = policy.getComment();
            policySnapshot.owner = policy.getOwner();
            policySnapshot.createdAt = policy.getCreatedTime();
            schemaSnapshot.rowAccessPolicies.add(policySnapshot);
        }

        // Save tags
        for (final Tag tag : schema.getTags()) {
            final TagSnapshot tagSnapshot = new TagSnapshot();
            tagSnapshot.name = tag.getName();
            tagSnapshot.id = tag.getId();
            tagSnapshot.allowedValues = new ArrayList<>(tag.getAllowedValues());
            tagSnapshot.allowedValuesListed = tag.hasAllowedValues();
            tagSnapshot.comment = tag.getComment();
            tagSnapshot.owner = tag.getOwner();
            tagSnapshot.createdAt = tag.getCreatedTime();
            tagSnapshot.propagate = tag.getPropagate();
            tagSnapshot.onConflict = tag.getOnConflict();
            schemaSnapshot.tags.add(tagSnapshot);
        }
        return schemaSnapshot;
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
            colSnapshot.type = col.getDataType();
            if (col.getDataType() instanceof NumericType) {
                final NumericType numericType = (NumericType) col.getDataType();
                colSnapshot.precision = numericType.getPrecision();
                colSnapshot.scale = numericType.getScale();
            } else if (col.getDataType() instanceof StringType) {
                colSnapshot.maxLength = ((StringType) col.getDataType()).getMaxLength();
            }
            colSnapshot.nullable = col.isNullable();
            // A view column declared with a COMMENT (CREATE VIEW v (c COMMENT '...') AS ...) keeps it.
            colSnapshot.comment = col.getComment();
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
                paramSnapshot.type = param.getDataType();
                paramSnapshot.defaultValue = param.getDefaultValue();
                snapshots.add(paramSnapshot);
            }
        }
        return snapshots;
    }

    /** One stream change record as a snapshot value. */
    private static StreamRecordSnapshot recordSnapshot(final StreamRecord rec) {
        final StreamRecordSnapshot recSnapshot = new StreamRecordSnapshot();
        recSnapshot.values = new ArrayList<>(rec.getValues());
        recSnapshot.changeType = rec.getChangeType().name();
        recSnapshot.update = rec.isUpdate();
        recSnapshot.rowId = rec.getRowId();
        recSnapshot.sourceTable = rec.getSourceTable();
        return recSnapshot;
    }

    /** A table's constraint dates as a snapshot keeps them, or null when it has none to keep. */
    private static HashMap<String, Instant> stampsOrNull(final Map<String, Instant> stamps) {
        return stamps.isEmpty() ? null : new HashMap<>(stamps);
    }

    /**
     * Save table data to disk
     */
    /** A table's rows as a snapshot value (each row's values defensively copied). */
    static TableDataSnapshot buildTableData(final String database, final String schema, final Table table,
                                            final boolean shadowed, final StorageEngine storageEngine) {
        final String qualifiedName = QualifiedName.key(database, schema, table.getName());
        final TableStorage storage = shadowed
            ? storageEngine.getShadowedTableStorage(qualifiedName) : storageEngine.getTableStorage(qualifiedName);
        final TableDataSnapshot dataSnapshot = new TableDataSnapshot();
        dataSnapshot.database = database;
        dataSnapshot.schema = schema;
        dataSnapshot.table = table.getName();
        for (final Row row : storage.scan()) {
            dataSnapshot.rows.add(new ArrayList<>(row.getValues()));
        }
        return dataSnapshot;
    }
}
