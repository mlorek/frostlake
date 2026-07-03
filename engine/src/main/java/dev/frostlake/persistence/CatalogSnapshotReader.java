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
import dev.frostlake.metastore.model.StageType;
import dev.frostlake.metastore.model.WarehouseSize;
import dev.frostlake.metastore.model.WarehouseState;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
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
    static void readCatalogFrom(final Path catalogPath, final Path tablesDir,
                                 final Catalog catalog, final StorageEngine storageEngine)
            throws IOException, ClassNotFoundException {
        if (!Files.exists(catalogPath)) {
            logger.info("No existing catalog file found, starting fresh");
            return;
        }

        logger.info("Loading catalog from: {}", catalogPath);

        CatalogSnapshot snapshot;
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(catalogPath)))) {
            snapshot = (CatalogSnapshot) ois.readObject();
        }

        // Load databases and schemas
        for (final DatabaseSnapshot dbSnapshot : snapshot.databases) {
            // Skip system database (will be created automatically)
            if ("SNOWFLAKE".equals(dbSnapshot.name)) {
                Database db = catalog.getDatabase("SNOWFLAKE");
                loadDatabaseContent(tablesDir, catalog, db, dbSnapshot, storageEngine);
                continue;
            }

            catalog.createDatabase(dbSnapshot.name);
            Database db = catalog.getDatabase(dbSnapshot.name);
            db.setComment(dbSnapshot.comment);
            db.setReadOnly(dbSnapshot.readOnly);
            // Note: createdAt cannot be set (final field in SqlObject)

            loadDatabaseContent(tablesDir, catalog, db, dbSnapshot, storageEngine);
        }

        // Load warehouses
        for (final WarehouseSnapshot whSnapshot : snapshot.warehouses) {
            // Skip default warehouse
            if ("COMPUTE_WH".equals(whSnapshot.name)) {
                continue;
            }

            catalog.createWarehouse(whSnapshot.name, WarehouseSize.valueOf(whSnapshot.size));
            Warehouse wh = catalog.getWarehouse(whSnapshot.name);
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
            // Note: createdAt cannot be set (final field)
        }

        // Load stages
        for (final StageSnapshot stageSnapshot : snapshot.stages) {
            catalog.createStage(stageSnapshot.name, StageType.valueOf(stageSnapshot.type),
                              stageSnapshot.url, stageSnapshot.fileFormat,
                              stageSnapshot.encryption, stageSnapshot.comment);
            // Note: createdAt cannot be set (final field in SqlObject)
        }

        // Load users
        for (final UserSnapshot userSnapshot : snapshot.users) {
            try {
                catalog.createUser(userSnapshot.name, userSnapshot.password, userSnapshot.defaultRole);
            } catch (final RuntimeException e) {
                // User already exists (e.g. default user created during init) — skip creation
            }
            User user = catalog.getUser(userSnapshot.name);
            user.setComment(userSnapshot.comment);
            // enabled is a nullable Boolean: null (a snapshot predating the field) means the user was never
            // disabled, so leave it enabled; only an explicit value flips it.
            if (userSnapshot.enabled != null) {
                user.setEnabled(userSnapshot.enabled);
            }
            if (userSnapshot.owner != null) {
                user.setOwner(userSnapshot.owner);
            }
            // Privileges granted directly to the user (object- and column-level). The referenced objects were
            // loaded above; grant* only records the entry, so this is order-independent for the grant itself.
            if (userSnapshot.privileges != null) {
                for (final PrivilegeSnapshot privSnapshot : userSnapshot.privileges) {
                    try {
                        if (privSnapshot.column != null) {
                            user.grantColumnPrivilege(privSnapshot.objectType, privSnapshot.objectName,
                                privSnapshot.column, Privilege.valueOf(privSnapshot.privilege));
                        } else {
                            user.grantPrivilege(privSnapshot.objectType, privSnapshot.objectName,
                                Privilege.valueOf(privSnapshot.privilege));
                        }
                    } catch (final Exception e) {
                        logger.warn("Could not grant privilege to user {}: {}", userSnapshot.name, e.getMessage());
                    }
                }
            }
            // Note: createdAt cannot be set (final field)
            // Roles will be granted after all roles are loaded
        }

        // Load custom roles (system roles already exist)
        for (final RoleSnapshot roleSnapshot : snapshot.roles) {
            if (!catalog.isSystemRole(roleSnapshot.name)) {
                catalog.createRole(roleSnapshot.name);
                Role role = catalog.getRole(roleSnapshot.name);
                role.setComment(roleSnapshot.comment);
                if (roleSnapshot.owner != null) {
                    role.setOwner(roleSnapshot.owner);
                }
                // Note: createdAt cannot be set (final field)
            }
        }

        // Grant role hierarchies
        for (final RoleSnapshot roleSnapshot : snapshot.roles) {
            Role role = catalog.getRole(roleSnapshot.name);
            for (final String grantedRoleName : roleSnapshot.grantedRoles) {
                try {
                    role.grantRole(grantedRoleName);
                } catch (final Exception e) {
                    logger.warn("Could not grant role {} to {}: {}", grantedRoleName, roleSnapshot.name, e.getMessage());
                }
            }
        }

        // Grant privileges to roles
        for (final RoleSnapshot roleSnapshot : snapshot.roles) {
            Role role = catalog.getRole(roleSnapshot.name);
            for (final PrivilegeSnapshot privSnapshot : roleSnapshot.privileges) {
                try {
                    if (privSnapshot.column != null) {
                        role.grantColumnPrivilege(privSnapshot.objectType, privSnapshot.objectName,
                            privSnapshot.column, Privilege.valueOf(privSnapshot.privilege));
                    } else {
                        role.grantPrivilege(privSnapshot.objectType, privSnapshot.objectName,
                            Privilege.valueOf(privSnapshot.privilege));
                    }
                } catch (final Exception e) {
                    logger.warn("Could not grant privilege: {}", e.getMessage());
                }
            }
        }

        // Grant roles to users
        for (final UserSnapshot userSnapshot : snapshot.users) {
            User user = catalog.getUser(userSnapshot.name);
            for (final String roleName : userSnapshot.grantedRoles) {
                try {
                    user.grantRole(roleName);
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

    static void loadDatabaseContent(final Path tablesDir, final Catalog catalog, final Database db,
                                     final DatabaseSnapshot dbSnapshot,
                                     final StorageEngine storageEngine) throws IOException, ClassNotFoundException {
        for (final SchemaSnapshot schemaSnapshot : dbSnapshot.schemas) {
            Schema schema;

            // Get existing schema or create new one (skip default schemas)
            if ("PUBLIC".equals(schemaSnapshot.name) || "INFORMATION_SCHEMA".equals(schemaSnapshot.name)) {
                schema = db.getSchema(schemaSnapshot.name);
            } else {
                Schema newSchema = new Schema(schemaSnapshot.name);
                newSchema.setComment(schemaSnapshot.comment);
                db.addSchema(newSchema);
                schema = newSchema;
                // Note: createdAt cannot be set (final field in SqlObject)
            }

            // Load tables
            for (final TableSnapshot tableSnapshot : schemaSnapshot.tables) {
                List<TableColumn> columns = new ArrayList<>();
                for (final ColumnSnapshot colSnapshot : tableSnapshot.columns) {
                    // Identity start/increment only apply when autoIncrement; for other columns pass the
                    // model default (1/1). Old snapshots have autoIncrement=false, so identity is irrelevant.
                    final long identityStart = colSnapshot.autoIncrement ? colSnapshot.identityStart : 1L;
                    final long identityIncrement = colSnapshot.autoIncrement ? colSnapshot.identityIncrement : 1L;
                    // Re-wrap an expression default so INSERT evaluates it again; a literal stays a plain value.
                    final Object columnDefault = colSnapshot.defaultIsExpression && colSnapshot.defaultValue != null
                        ? new DefaultValueExpression(colSnapshot.defaultValue) : colSnapshot.defaultValue;
                    TableColumn col = new TableColumn(
                        colSnapshot.name,
                        parseDataType(colSnapshot.dataType),
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
                    col.setCollation(colSnapshot.collation);
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

                Table table = new Table(tableSnapshot.name, columns,
                    tableSnapshot.temporary, tableSnapshot.isTransient);
                table.setComment(tableSnapshot.comment);
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
                    if (tableSnapshot.rowAccessPolicyColumns != null) {
                        table.setRowAccessPolicyColumns(tableSnapshot.rowAccessPolicyColumns);
                    }
                }
                schema.addTable(table);

                // Create storage for table
                String qualifiedName = db.getName().toUpperCase() + "." + schema.getName().toUpperCase() + "." + table.getName().toUpperCase();
                storageEngine.createTable(qualifiedName, table);

                // Load table data
                loadTableData(tablesDir, db.getName(), schema.getName(), table, storageEngine);
            }

            // Load views (skip INFORMATION_SCHEMA views as they're system-generated)
            if (!"INFORMATION_SCHEMA".equals(schemaSnapshot.name)) {
                for (final ViewSnapshot viewSnapshot : schemaSnapshot.views) {
                    View view = viewSnapshot.columnNames != null
                        ? new View(viewSnapshot.name, viewSnapshot.columnNames, viewSnapshot.query)
                        : new View(viewSnapshot.name, viewSnapshot.query);
                    view.setComment(viewSnapshot.comment);
                    view.setSecure(viewSnapshot.secure);
                    schema.addView(view);
                }
            }

            // Load sequences. Null guards throughout: snapshots written before these fields existed
            // deserialize them as null (field initializers do not run during Java deserialization).
            if (schemaSnapshot.sequences != null) {
                for (final SequenceSnapshot seqSnapshot : schemaSnapshot.sequences) {
                    Sequence seq = new Sequence(seqSnapshot.name, seqSnapshot.startValue,
                        seqSnapshot.increment, seqSnapshot.order, seqSnapshot.comment);
                    seq.setCurrentValue(seqSnapshot.currentValue);
                    if (seqSnapshot.owner != null) {
                        seq.setOwner(seqSnapshot.owner);
                    }
                    schema.addSequence(seq);
                }
            }

            // Load streams (pending change records restored at offset 0 = all unconsumed)
            if (schemaSnapshot.streams != null) {
                for (final StreamSnapshot streamSnapshot : schemaSnapshot.streams) {
                    Stream stream = new Stream(streamSnapshot.name, streamSnapshot.sourceTableName,
                        StreamSourceType.valueOf(streamSnapshot.sourceType),
                        StreamType.valueOf(streamSnapshot.streamType), streamSnapshot.showInitialRows);
                    stream.setBaseTableName(streamSnapshot.baseTableName);
                    stream.setStale(streamSnapshot.stale);
                    stream.setComment(streamSnapshot.comment);
                    if (streamSnapshot.owner != null) {
                        stream.setOwner(streamSnapshot.owner);
                    }
                    if (streamSnapshot.records != null) {
                        for (final StreamRecordSnapshot recSnapshot : streamSnapshot.records) {
                            stream.addRecord(new StreamRecord(recSnapshot.values,
                                ChangeType.valueOf(recSnapshot.changeType), recSnapshot.update, recSnapshot.rowId));
                        }
                    }
                    schema.addStream(stream);
                }
            }

            // Load tasks. State is restored as-is; persistence does not re-arm the scheduler, so a restored
            // STARTED task is not actively scheduled until it is RESUMEd again (RESUME arms the timer).
            if (schemaSnapshot.tasks != null) {
                for (final TaskSnapshot taskSnapshot : schemaSnapshot.tasks) {
                    Task task = new Task(taskSnapshot.name, taskSnapshot.schedule,
                        taskSnapshot.scheduleType != null ? ScheduleType.valueOf(taskSnapshot.scheduleType) : null,
                        taskSnapshot.sqlStatement, taskSnapshot.warehouse);
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
                    schema.addTask(task);
                }
            }

            // Load masking policies (the policy-to-column binding is restored on the column above)
            if (schemaSnapshot.maskingPolicies != null) {
                for (final MaskingPolicySnapshot policySnapshot : schemaSnapshot.maskingPolicies) {
                    List<Parameter> params = new ArrayList<>();
                    if (policySnapshot.parameters != null) {
                        for (final MaskingPolicyParameterSnapshot paramSnapshot : policySnapshot.parameters) {
                            params.add(new Parameter(paramSnapshot.name,
                                parseDataType(paramSnapshot.dataType), paramSnapshot.defaultValue));
                        }
                    }
                    MaskingPolicy policy = new MaskingPolicy(policySnapshot.name, params,
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
                    FileFormat ff = new FileFormat(ffSnapshot.name, ffSnapshot.type);
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
                    Function fn = new Function(fnSnapshot.name,
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
                    fn.setImports(fnSnapshot.imports);
                    fn.setComment(fnSnapshot.comment);
                    if (fnSnapshot.owner != null) {
                        fn.setOwner(fnSnapshot.owner);
                    }
                    schema.addFunction(fn);
                }
            }

            // Load stored procedures
            if (schemaSnapshot.procedures != null) {
                for (final ProcedureSnapshot procSnapshot : schemaSnapshot.procedures) {
                    Procedure proc = new Procedure(procSnapshot.name,
                        restoreParameters(procSnapshot.parameters),
                        procSnapshot.returnType != null ? parseDataType(procSnapshot.returnType) : null,
                        procSnapshot.body, procSnapshot.language,
                        procSnapshot.handler, procSnapshot.runtimeVersion, procSnapshot.packages);
                    if (procSnapshot.executeAs != null) {
                        proc.setExecuteAs(procSnapshot.executeAs);
                    }
                    proc.setImports(procSnapshot.imports);
                    proc.setComment(procSnapshot.comment);
                    if (procSnapshot.owner != null) {
                        proc.setOwner(procSnapshot.owner);
                    }
                    schema.addProcedure(proc);
                }
            }

            // Load pipes
            if (schemaSnapshot.pipes != null) {
                for (final PipeSnapshot pipeSnapshot : schemaSnapshot.pipes) {
                    Pipe pipe = new Pipe(pipeSnapshot.name, pipeSnapshot.copyStatement,
                        pipeSnapshot.autoIngest, pipeSnapshot.notificationChannel);
                    pipe.setPaused(pipeSnapshot.paused);
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
                    DynamicTable dt = new DynamicTable(dtSnapshot.name, dtSnapshot.query,
                        dtSnapshot.targetLag, dtSnapshot.warehouse);
                    dt.setComment(dtSnapshot.comment);
                    if (dtSnapshot.owner != null) {
                        dt.setOwner(dtSnapshot.owner);
                    }
                    schema.addDynamicTable(dt);
                }
            }

            // Load row access policies (the policy-to-table binding is restored on the table above)
            if (schemaSnapshot.rowAccessPolicies != null) {
                for (final RowAccessPolicySnapshot policySnapshot : schemaSnapshot.rowAccessPolicies) {
                    RowAccessPolicy policy = new RowAccessPolicy(policySnapshot.name,
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
                    Tag tag = new Tag(tagSnapshot.name,
                        tagSnapshot.allowedValues, tagSnapshot.masking, tagSnapshot.comment);
                    if (tagSnapshot.owner != null) {
                        tag.setOwner(tagSnapshot.owner);
                    }
                    schema.addTag(tag);
                }
            }
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
    static void loadTableData(final Path tablesDir, final String database, final String schema, final Table table,
                               final StorageEngine storageEngine) throws IOException, ClassNotFoundException {
        Path tableFile = tablesDir.resolve(
            String.format("%s_%s_%s.dat", database, schema, table.getName())
        );

        if (!Files.exists(tableFile)) {
            logger.debug("No data file found for table: {}.{}.{}", database, schema, table.getName());
            return;
        }

        String qualifiedName = database.toUpperCase() + "." + schema.toUpperCase() + "." + table.getName().toUpperCase();
        StorageEngine.TableStorage storage = storageEngine.getTableStorage(qualifiedName);

        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(tableFile)))) {

            TableDataSnapshot dataSnapshot = (TableDataSnapshot) ois.readObject();

            // Insert all rows
            for (final List<Object> rowValues : dataSnapshot.rows) {
                Row row = new Row(rowValues);
                storage.insert(row);
            }

            logger.debug("Loaded table data: {}.{}.{} ({} rows)",
                        database, schema, table.getName(), dataSnapshot.rows.size());
        }
    }

    /**
     * Convert string type name to DataType instance
     */
    static DataType parseDataType(final String typeName) {
        if (typeName == null) {
            return StringType.VARCHAR; // default
        }

        switch (typeName.toUpperCase()) {
            case "INTEGER":
            case "INT":
                return NumericType.INTEGER;
            case "BIGINT":
                return NumericType.BIGINT;
            case "SMALLINT":
                return NumericType.SMALLINT;
            case "NUMBER":
            case "DECIMAL":
                return NumericType.NUMBER;
            case "FLOAT":
                return NumericType.FLOAT;
            case "DOUBLE":
                return NumericType.DOUBLE;
            case "VARCHAR":
            case "STRING":
            case "TEXT":
                return StringType.VARCHAR;
            case "CHAR":
                return StringType.CHAR;
            case "BOOLEAN":
                return BooleanType.BOOLEAN;
            case "DATE":
                return DateTimeType.DATE;
            case "TIMESTAMP":
            case "TIMESTAMP_NTZ":
                return DateTimeType.TIMESTAMP_NTZ;
            case "VARIANT":
                return VariantType.VARIANT;
            case "ARRAY":
                return ArrayType.ARRAY;
            case "OBJECT":
                return ObjectType.OBJECT;
            case "BINARY":
            case "VARBINARY":
                return BinaryType.BINARY;
            default:
                return StringType.VARCHAR; // default
        }
    }
}
