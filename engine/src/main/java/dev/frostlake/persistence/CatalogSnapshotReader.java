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
import dev.frostlake.metastore.GrantNames;
import dev.frostlake.metastore.PolicyReferences;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.AggregationPolicy;
import dev.frostlake.metastore.model.ChangeType;
import dev.frostlake.metastore.model.CheckConstraint;
import dev.frostlake.metastore.model.Contact;
import dev.frostlake.metastore.model.CortexSearchService;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DefaultValueExpression;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.DynamicTableColumn;
import dev.frostlake.metastore.model.DynamicTableRefresh;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Initialize;
import dev.frostlake.metastore.model.JoinPolicy;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.ProjectionPolicy;
import dev.frostlake.metastore.model.RefreshMode;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.ScalingPolicy;
import dev.frostlake.metastore.model.ScheduleType;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SearchOptimizationExpression;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.SequenceDefault;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.StageType;
import dev.frostlake.metastore.model.State;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamRecord;
import dev.frostlake.metastore.model.StreamSourceType;
import dev.frostlake.metastore.model.StreamType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.TableStructure;
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
import dev.frostlake.storage.SnapshotSequence;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
        // Whatever the restore draws — a table's creation, a stream's offset — follows every history it restores.
        SnapshotSequence.advancePast(snapshot.snapshotSequence);
        // And a row stored after it takes an identity no row before it had, a deleted one a stream still names too.
        if (snapshot.nextRowId > 0) {
            storageEngine.advanceRowIdsPast(snapshot.nextRowId - 1);
        }
        // The files each table had loaded, which a COPY of it skips again.
        catalog.getCopyLoadedFiles().clear();
        if (snapshot.copyLoadedFiles != null) {
            for (final Map.Entry<String, List<String>> loaded : snapshot.copyLoadedFiles.entrySet()) {
                catalog.getCopyLoadedFiles().put(loaded.getKey(), new HashSet<>(loaded.getValue()));
            }
        }
        // Load databases and schemas
        for (final DatabaseSnapshot dbSnapshot : snapshot.databases) {
            // Skip system database (will be created automatically)
            if ("SNOWFLAKE".equals(dbSnapshot.name)) {
                final Database db = catalog.getDatabase("SNOWFLAKE");
                db.restoreCreatedTime(dbSnapshot.createdAt);
                loadDatabaseContent(tableData, catalog, db, dbSnapshot, s3Resolver, storageEngine);
                continue;
            }

            catalog.createDatabase(dbSnapshot.name);
            final Database db = catalog.getDatabase(dbSnapshot.name);
            restoreDatabaseAttributes(db, dbSnapshot);

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
            wh.restoreCreatedAt(whSnapshot.createdAt);
            wh.restoreLastStateChange(whSnapshot.lastStateChange);
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
            user.restoreCreatedTime(userSnapshot.createdAt);
            // Roles will be granted after all roles are loaded
        }

        // Load custom roles (system roles already exist, and keep the creation time they had)
        for (final RoleSnapshot roleSnapshot : snapshot.roles) {
            if (catalog.isSystemRole(roleSnapshot.name)) {
                catalog.getRole(roleSnapshot.name).restoreCreatedTime(roleSnapshot.createdAt);
            } else {
                catalog.createRole(roleSnapshot.name);
                final Role role = catalog.getRole(roleSnapshot.name);
                role.setComment(roleSnapshot.comment);
                if (roleSnapshot.owner != null) {
                    role.setOwner(roleSnapshot.owner);
                }
                role.restoreCreatedTime(roleSnapshot.createdAt);
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
            RestCatalogSnapshots.restoreRoleGrantTimes(roleSnapshot, role);
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
            RestCatalogSnapshots.restoreRoleGrantTimes(userSnapshot, user);
        }

        rebindSequenceDefaults(catalog);

        // Privileges an older snapshot recorded under a partial name are recorded in full when it names one object.
        GrantNames.qualifyLegacy(catalog);

        // The dropped versions UNDROP can still restore, which a snapshot written before they were kept has none of.
        DroppedObjectSnapshots.restore(snapshot.droppedObjects, catalog, s3Resolver, storageEngine);
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

    /** A database's own settings, as a snapshot recorded them: everything but its schemas. */
    static void restoreDatabaseAttributes(final Database db, final DatabaseSnapshot dbSnapshot) {
        db.setComment(dbSnapshot.comment);
        if (dbSnapshot.owner != null) {
            db.setOwner(dbSnapshot.owner);
        }
        db.restoreCreatedTime(dbSnapshot.createdAt);
        db.restoreLastDdlTime(dbSnapshot.lastDdlAt);
        db.setReadOnly(dbSnapshot.readOnly);
        SnapshotTags.restore(db, dbSnapshot.tags);
    }

    /** A key's RELY as a snapshot that kept it on the columns reads: true when one of its columns relies. */
    private static Boolean relyOfColumns(final Table table, final List<String> columnNames) {
        if (columnNames == null) {
            return null;
        }
        for (final String columnName : columnNames) {
            if (table.hasColumn(columnName) && Boolean.TRUE.equals(table.getColumn(columnName).getRely())) {
                return Boolean.TRUE;
            }
        }
        return null;
    }

    /** The trigger a recorded run names in TASK_HISTORY's scheduled_from; a schedule when none matches. */
    /**
     * Binds each restored column default that names a sequence's NEXTVAL in full — the text a bound default is
     * snapshotted as — to the sequence it names, once every database is back (see {@link SequenceDefault}). A
     * default naming no sequence stays the expression it was.
     */
    private static void rebindSequenceDefaults(final Catalog catalog) {
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Table table : schema.getTables()) {
                    for (final TableColumn column : table.columnsView()) {
                        final Object value = column.getDefaultValue();
                        if (!(value instanceof DefaultValueExpression) || value instanceof SequenceDefault) {
                            continue;
                        }
                        try {
                            final String[] parts =
                                QualifiedName.parse(((DefaultValueExpression) value).getExpressionText()).parts();
                            if (parts.length == 4 && "NEXTVAL".equals(parts[3])) {
                                column.setDefaultValue(new SequenceDefault(
                                    catalog.getDatabase(parts[0]).getSchema(parts[1]).getSequence(parts[2])));
                            }
                        } catch (final RuntimeException notASequence) {
                            continue;
                        }
                    }
                }
            }
        }
    }

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

            // INFORMATION_SCHEMA is the database's own and is seeded, never restored. PUBLIC is restored like any
            // other schema, into the PUBLIC every database starts with: its comment and tags come back too. So is a
            // schema the SNOWFLAKE database starts with.
            if ("INFORMATION_SCHEMA".equals(schemaSnapshot.name)) {
                schema = db.getSchema(schemaSnapshot.name);
            } else if (db.hasSchemaExact(schemaSnapshot.name)) {
                schema = db.schemaExact(schemaSnapshot.name);
                schema.setComment(schemaSnapshot.comment);
                SnapshotTags.restore(schema, schemaSnapshot.tagValues);
            } else {
                final Schema newSchema = new Schema(schemaSnapshot.name);
                newSchema.setComment(schemaSnapshot.comment);
                SnapshotTags.restore(newSchema, schemaSnapshot.tagValues);
                db.addSchema(newSchema);
                schema = newSchema;
                // Note: createdAt cannot be set (final field in SqlObject)
            }

            if (!"INFORMATION_SCHEMA".equals(schemaSnapshot.name)) {
                if (schemaSnapshot.owner != null) {
                    schema.setOwner(schemaSnapshot.owner);
                }
                schema.restoreLastDdlTime(schemaSnapshot.lastDdlAt);
            }
            // The seeded INFORMATION_SCHEMA keeps the creation time it had too.
            schema.restoreCreatedTime(schemaSnapshot.createdAt);
            
            // Load tables
            for (final TableSnapshot tableSnapshot : schemaSnapshot.tables) {
                final List<TableColumn> columns = new ArrayList<>();
                for (final ColumnSnapshot colSnapshot : tableSnapshot.columns) {
                    columns.add(restoredColumn(colSnapshot));
                }

                final Table table = new Table(tableSnapshot.name, columns,
                    tableSnapshot.temporary, tableSnapshot.isTransient);
                table.setHighestOrdinal(tableSnapshot.highestOrdinal);
                table.setNextIdentityValue(tableSnapshot.nextIdentityValue);
                table.setHybrid(tableSnapshot.hybrid);
                table.setComment(tableSnapshot.comment);
                table.setLastDdlBy(tableSnapshot.lastDdlBy);
                if (tableSnapshot.owner != null) {
                    table.setOwner(tableSnapshot.owner);
                }
                table.restoreCreatedTime(tableSnapshot.createdAt);
                table.restoreLastDdlTime(tableSnapshot.lastDdlAt);
                if (tableSnapshot.clusterKeys != null) {
                    table.setClusterKeys(tableSnapshot.clusterKeys, tableSnapshot.clusterByText);
                }
                if (tableSnapshot.foreignKeys != null) {
                    for (final ForeignKeyConstraintSnapshot fkSnapshot : tableSnapshot.foreignKeys) {
                        table.addForeignKey(new ForeignKeyConstraint(fkSnapshot.constraintName,
                            fkSnapshot.columnNames, fkSnapshot.referencedTable, fkSnapshot.referencedColumns,
                            fkSnapshot.onDelete, fkSnapshot.onUpdate, fkSnapshot.rely));
                    }
                }
                // Only the row access policy and its columns depend on a row access policy being attached;
                // the rest is restored whatever the table carries.
                if (tableSnapshot.rowAccessPolicyName != null) {
                    table.setRowAccessPolicyName(tableSnapshot.rowAccessPolicyName);
                    if (tableSnapshot.rowAccessPolicyColumns != null) {
                        table.setRowAccessPolicyColumns(tableSnapshot.rowAccessPolicyColumns);
                    }
                }
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
                // Constraint names, so a reloaded table keeps reporting the ones it was created with. All
                // of these are null on snapshots written before they were persisted, in which case the
                // constraint auto-names itself again on first use.
                table.setPrimaryKeyConstraintName(tableSnapshot.primaryKeyConstraintName);
                // A snapshot without ownUniqueColumns kept a key's RELY on its columns: each key relies when one
                // of its columns does, as it read then.
                final boolean relyOnColumns = tableSnapshot.ownUniqueColumns == null;
                if (tableSnapshot.uniqueConstraints != null) {
                    for (final UniqueConstraintSnapshot uniqueSnapshot : tableSnapshot.uniqueConstraints) {
                        table.addUniqueConstraint(new UniqueConstraint(uniqueSnapshot.constraintName,
                            uniqueSnapshot.columnNames, relyOnColumns
                                ? relyOfColumns(table, uniqueSnapshot.columnNames) : uniqueSnapshot.rely));
                    }
                }
                table.orderPrimaryKey(tableSnapshot.primaryKeyColumns);
                table.setPrimaryKeyRely(relyOnColumns ? relyOfColumns(table, table.getPrimaryKeys())
                    : tableSnapshot.primaryKeyRely);
                if (tableSnapshot.ownUniqueColumns != null) {
                    for (final String column : tableSnapshot.ownUniqueColumns) {
                        table.markOwnUnique(column);
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
                if (tableSnapshot.nextIdentityValue == null && table.getNextIdentityValue() != null) {
                    // Written before the IDENTITY sequence was recorded: move it past the rows just restored, or
                    // the table would hand out values it already holds.
                    table.continueIdentityPast((tableSnapshot.shadowed ? storageEngine.getShadowedTableStorage(
                        qualifiedName) : storageEngine.getTableStorage(qualifiedName)).scan());
                }
                if (tableSnapshot.dataChanges != null) {
                    // Loading the rows wrote them; the table's last write is the one the snapshot recorded.
                    table.restoreDataChange(tableSnapshot.lastDataChange, tableSnapshot.dataChanges);
                }
                table.restoreLastDml(tableSnapshot.lastDml);
                table.restoreConstraintTimes(tableSnapshot.constraintCreated, tableSnapshot.constraintAltered);
            }

            // Load views (skip INFORMATION_SCHEMA views as they're system-generated)
            if (!"INFORMATION_SCHEMA".equals(schemaSnapshot.name)) {
                for (final ViewSnapshot viewSnapshot : schemaSnapshot.views) {
                    final View view = viewSnapshot.columnNames != null
                        ? new View(viewSnapshot.name, viewSnapshot.columnNames, viewSnapshot.query)
                        : new View(viewSnapshot.name, viewSnapshot.query);
                    view.setComment(viewSnapshot.comment);
                    view.setLastDdlBy(viewSnapshot.lastDdlBy);
                    if (viewSnapshot.owner != null) {
                        view.setOwner(viewSnapshot.owner);
                    }
                    view.restoreCreatedTime(viewSnapshot.createdAt);
                    view.restoreLastDdlTime(viewSnapshot.lastDdlAt);
                    view.setSecure(viewSnapshot.secure);
                    view.setChangeTracking(viewSnapshot.changeTracking);
                    view.setRecursive(viewSnapshot.recursive);
                    view.setWrittenBody(viewSnapshot.writtenBody);
                    view.setOriginalDdl(viewSnapshot.originalDdl);
                    view.setDefinitionText(viewSnapshot.definitionText);
                    view.setListedText(viewSnapshot.listedText);
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
                    seq.restoreCreatedTime(seqSnapshot.createdAt);
                    seq.restoreLastDdlTime(seqSnapshot.lastDdlAt);
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
                    if (stageSnapshot.owner != null) {
                        stage.setOwner(stageSnapshot.owner);
                    }
                    stage.restoreCreatedTime(stageSnapshot.createdAt);
                    stage.restoreLastDdlTime(stageSnapshot.lastDdlAt);
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
                    service.restoreCreatedTime(serviceSnapshot.createdAt);
                    service.setIndexingSuspended(serviceSnapshot.indexingSuspended);
                    service.setServingSuspended(serviceSnapshot.servingSuspended);
                    if (serviceSnapshot.primaryKeyColumns != null) {
                        service.setPrimaryKeyColumns(serviceSnapshot.primaryKeyColumns);
                    }
                    service.setFullIndexBuildIntervalDays(serviceSnapshot.fullIndexBuildIntervalDays);
                    service.setRequestLogging(serviceSnapshot.requestLogging);
                    service.setAutoSuspend(serviceSnapshot.autoSuspend);
                    if (serviceSnapshot.tags != null) {
                        for (final Map.Entry<String, String> tag : serviceSnapshot.tags.entrySet()) {
                            service.setTag(tag.getKey(), tag.getValue());
                        }
                    }
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
                    if (streamSnapshot.lostInClone) {
                        stream.markLostInClone();
                    }
                    stream.setComment(streamSnapshot.comment);
                    if (streamSnapshot.owner != null) {
                        stream.setOwner(streamSnapshot.owner);
                    }
                    stream.restoreCreatedTime(streamSnapshot.createdAt);
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
                    task.restoreCreatedTime(taskSnapshot.createdAt);
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
                    if (taskSnapshot.contacts != null) {
                        for (final Map.Entry<String, String> attached : taskSnapshot.contacts.entrySet()) {
                            task.setContact(attached.getKey(), attached.getValue());
                        }
                    }
                    task.setServerlessTaskMinStatementSize(taskSnapshot.serverlessTaskMinStatementSize);
                    // The recorded runs, replayed through the task so its last run and failure count follow.
                    if (taskSnapshot.history != null) {
                        for (final TaskExecutionSnapshot run : taskSnapshot.history) {
                            task.recordExecution(new TaskExecution(run.scheduledTime, run.startTime, run.endTime,
                                TaskExecutionState.valueOf(run.state), run.errorMessage, run.rowsAffected,
                                taskTrigger(run.scheduledFrom), run.attempt == null ? 1 : run.attempt.intValue()));
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
                    policy.restoreCreatedTime(policySnapshot.createdAt);
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
                    policy.restoreCreatedTime(policySnapshot.createdAt);
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
                    policy.restoreCreatedTime(policySnapshot.createdAt);
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
                    contact.restoreCreatedTime(contactSnapshot.createdAt);
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
                                restoredType(paramSnapshot.type, paramSnapshot.dataType), paramSnapshot.defaultValue));
                        }
                    }
                    final MaskingPolicy policy = new MaskingPolicy(policySnapshot.name, params,
                        policySnapshot.returnType, policySnapshot.body);
                    policy.setReturnDataType(policySnapshot.returnDataType);
                    policy.setReturnSpelling(policySnapshot.returnSpelling);
                    policy.setComment(policySnapshot.comment);
                    policy.setExemptOtherPolicies(policySnapshot.exemptOtherPolicies);
                    if (policySnapshot.owner != null) {
                        policy.setOwner(policySnapshot.owner);
                    }
                    policy.restoreCreatedTime(policySnapshot.createdAt);
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
                    if (ffSnapshot.owner != null) {
                        ff.setOwner(ffSnapshot.owner);
                    }
                    ff.restoreCreatedTime(ffSnapshot.createdAt);
                    ff.restoreLastDdlTime(ffSnapshot.lastDdlAt);
                    schema.addFileFormat(ff);
                }
            }

            // Load user-defined functions
            if (schemaSnapshot.functions != null) {
                for (final FunctionSnapshot fnSnapshot : schemaSnapshot.functions) {
                    final Function fn = new Function(fnSnapshot.name,
                        restoreParameters(fnSnapshot.parameters),
                        fnSnapshot.returnType != null
                            ? restoredType(fnSnapshot.returnDataType, fnSnapshot.returnType) : null,
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
                    fn.setReturnSized(fnSnapshot.returnSized);
                    fn.setReturnNotNull(Boolean.TRUE.equals(fnSnapshot.returnNotNull));
                    fn.setMemoizable(fnSnapshot.memoizable);
                    fn.setImports(fnSnapshot.imports);
                    fn.setEnvironment(fnSnapshot.environment == null ? null : fnSnapshot.environment.copy());
                    fn.setComment(fnSnapshot.comment);
                    if (fnSnapshot.owner != null) {
                        fn.setOwner(fnSnapshot.owner);
                    }
                    fn.restoreCreatedTime(fnSnapshot.createdAt);
                    fn.restoreLastDdlTime(fnSnapshot.lastDdlAt);
                    if (fnSnapshot.serviceName != null) {
                        fn.setService(fnSnapshot.serviceName, fnSnapshot.serviceEndpoint, fnSnapshot.maxBatchRows);
                    }
                    if (fnSnapshot.apiIntegration != null) {
                        fn.setExternal(fnSnapshot.apiIntegration, fnSnapshot.externalUrl);
                    }
                    fn.setAggregate(fnSnapshot.aggregate);
                    if (fnSnapshot.aggregate) {
                        catalog.noteAggregateFunction(fn.getName());
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
                        procSnapshot.returnType != null
                            ? restoredType(procSnapshot.returnDataType, procSnapshot.returnType) : null,
                        procSnapshot.body, procSnapshot.language,
                        procSnapshot.handler, procSnapshot.runtimeVersion, procSnapshot.packages);
                    if (procSnapshot.executeAs != null) {
                        proc.setExecuteAs(procSnapshot.executeAs);
                    }
                    proc.setImports(procSnapshot.imports);
                    proc.setReturnSized(procSnapshot.returnSized);
                    proc.setReturnNotNull(Boolean.TRUE.equals(procSnapshot.returnNotNull));
                    proc.setEnvironment(procSnapshot.environment == null ? null : procSnapshot.environment.copy());
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
                    proc.setSecure(procSnapshot.secure);
                    if (procSnapshot.owner != null) {
                        proc.setOwner(procSnapshot.owner);
                    }
                    proc.restoreCreatedTime(procSnapshot.createdAt);
                    proc.restoreLastDdlTime(procSnapshot.lastDdlAt);
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
                    // After the pause, which a resumed pipe clears it with.
                    pipe.setStoppedCloned(pipeSnapshot.stoppedCloned);
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
                    pipe.restoreCreatedTime(pipeSnapshot.createdAt);
                    pipe.restoreLastDdlTime(pipeSnapshot.lastDdlAt);
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
                    dt.restoreCreatedTime(dtSnapshot.createdAt);
                    dt.restoreLastDdlTime(dtSnapshot.lastDdlAt);
                    if (dtSnapshot.clusterKeys != null) {
                        dt.setClusterKeys(dtSnapshot.clusterKeys);
                    }
                    if (dtSnapshot.refreshedRows != null) {
                        dt.setRefreshedRows(dtSnapshot.refreshedRows.longValue());
                    }
                    if (dtSnapshot.lastRefreshedTime != null) {
                        dt.restoreRefreshState(dtSnapshot.lastRefreshedTime,
                            dtSnapshot.firstRefreshedSequence != null ? dtSnapshot.firstRefreshedSequence : 0L,
                            dtSnapshot.firstRefreshedMillis != null ? dtSnapshot.firstRefreshedMillis : 0L,
                            dtSnapshot.refreshImage);
                    }
                    if (dtSnapshot.refreshSequences != null) {
                        final List<DynamicTableRefresh> kept = new ArrayList<>();
                        for (int r = 0; r < dtSnapshot.refreshSequences.size(); r++) {
                            kept.add(new DynamicTableRefresh(dtSnapshot.refreshSequences.get(r).longValue(),
                                dtSnapshot.refreshMillis.get(r).longValue(), dtSnapshot.refreshRows.get(r)));
                        }
                        dt.restoreRefreshes(kept);
                    }
                    if (dtSnapshot.resolvedColumns != null) {
                        dt.setResolvedColumns(derivedColumns(dtSnapshot.resolvedColumns));
                    }
                    if (dtSnapshot.columnNames != null) {
                        final List<DynamicTableColumn> declared = new ArrayList<>();
                        for (int c = 0; c < dtSnapshot.columnNames.size(); c++) {
                            declared.add(new DynamicTableColumn(dtSnapshot.columnNames.get(c),
                                dtSnapshot.columnTypes.get(c), dtSnapshot.columnComments.get(c)));
                        }
                        dt.setDeclaredColumns(declared);
                    }
                    dt.setTransient(dtSnapshot.transientTable);
                    // The settings the CREATE and ALTERs gave it, which it otherwise restored with their defaults:
                    // REFRESH_MODE AUTO, INITIALIZE ON_CREATE, running.
                    if (dtSnapshot.refreshMode != null) {
                        dt.setRefreshMode(RefreshMode.valueOf(dtSnapshot.refreshMode));
                    }
                    if (dtSnapshot.initialize != null) {
                        dt.setInitialize(Initialize.valueOf(dtSnapshot.initialize));
                    }
                    if (dtSnapshot.state != null) {
                        dt.setState(State.valueOf(dtSnapshot.state));
                    }
                    if (dtSnapshot.definitionDatabase != null) {
                        dt.keepDefinitionHome(dtSnapshot.definitionDatabase, dtSnapshot.definitionSchema);
                    }
                    if (dtSnapshot.createText != null) {
                        dt.setCreateText(dtSnapshot.createText);
                        dt.setBodyText(dtSnapshot.bodyText);
                    }
                    if (dtSnapshot.schedulingState != null) {
                        dt.setSchedulingState(dtSnapshot.schedulingState);
                    }
                    dt.setLastSuspendedOn(dtSnapshot.lastSuspendedOn);
                    if (dtSnapshot.dataRetentionDays != null) {
                        dt.setDataRetentionDays(dtSnapshot.dataRetentionDays);
                    }
                    if (dtSnapshot.parameters != null) {
                        for (final Map.Entry<String, String> parameter : dtSnapshot.parameters.entrySet()) {
                            dt.setParameter(parameter.getKey(), parameter.getValue());
                        }
                    }
                    if (dtSnapshot.maxDataExtensionDays != null) {
                        dt.setMaxDataExtensionDays(dtSnapshot.maxDataExtensionDays);
                    }
                    dt.setReclusterSuspended(dtSnapshot.reclusterSuspended);
                    dt.setClone(dtSnapshot.clone);
                    schema.addDynamicTable(dt);
                }
            }

            // Load row access policies (the policy-to-table binding is restored on the table above)
            if (schemaSnapshot.rowAccessPolicies != null) {
                for (final RowAccessPolicySnapshot policySnapshot : schemaSnapshot.rowAccessPolicies) {
                    final RowAccessPolicy policy = new RowAccessPolicy(policySnapshot.name,
                        restoreParameters(policySnapshot.parameters), policySnapshot.body);
                    policy.setComment(policySnapshot.comment);
                    policy.setExemptOtherPolicies(policySnapshot.exemptOtherPolicies);
                    if (policySnapshot.owner != null) {
                        policy.setOwner(policySnapshot.owner);
                    }
                    policy.restoreCreatedTime(policySnapshot.createdAt);
                    schema.addRowAccessPolicy(policy);
                }
            }

            // Load tags
            if (schemaSnapshot.tags != null) {
                for (final TagSnapshot tagSnapshot : schemaSnapshot.tags) {
                    final Tag tag = new Tag(tagSnapshot.name,
                        tagSnapshot.allowedValues, tagSnapshot.comment);
                    tag.markAllowedValuesListed(tagSnapshot.allowedValuesListed);
                    if (tagSnapshot.owner != null) {
                        tag.setOwner(tagSnapshot.owner);
                    }
                    tag.restoreCreatedTime(tagSnapshot.createdAt);
                    tag.restoreId(tagSnapshot.id);
                    tag.setPropagate(tagSnapshot.propagate);
                    tag.setOnConflict(tagSnapshot.onConflict);
                    schema.addTag(tag);
                }
            }

            // The schema's settings, and the objects added beside the long-standing ones
            RestCatalogSnapshots.restoreSchema(schemaSnapshot, schema);
        }
        removeDroppedPublic(db, dbSnapshot);
        // Attachments recorded before they were recorded in full are completed where their relation lives.
        PolicyReferences.qualify(db.getAllSchemas());
    }

    /**
     * A database whose PUBLIC was dropped is saved without it, and is restored without it: the PUBLIC a new database
     * starts with goes. Only a snapshot that lists INFORMATION_SCHEMA is read this way, since it lists every schema
     * the database had, so a snapshot that saved no default schemas never loses PUBLIC.
     */
    private static void removeDroppedPublic(final Database db, final DatabaseSnapshot dbSnapshot) {
        boolean listsAll = false;
        for (final SchemaSnapshot schemaSnapshot : dbSnapshot.schemas) {
            if ("PUBLIC".equals(schemaSnapshot.name)) {
                return;
            }
            listsAll = listsAll || "INFORMATION_SCHEMA".equals(schemaSnapshot.name);
        }
        if (listsAll && db.hasSchemaExact("PUBLIC")) {
            db.detachSchema("PUBLIC");
        }
    }

    /** Rebuild a parameter list from its snapshot (data types re-parsed by name). */
    static List<Parameter> restoreParameters(final List<ParameterSnapshot> snapshots) {
        final List<Parameter> parameters = new ArrayList<>();
        if (snapshots != null) {
            for (final ParameterSnapshot paramSnapshot : snapshots) {
                final DataType type = restoredType(paramSnapshot.type, paramSnapshot.dataType);
                parameters.add(new Parameter(paramSnapshot.name, type, paramSnapshot.defaultValue,
                    paramSnapshot.unsized == null ? Parameter.holdsDefaultLength(type)
                        : paramSnapshot.unsized.booleanValue(), paramSnapshot.defaultTold));
            }
        }
        return parameters;
    }

    /**
     * A table column as a snapshot kept it: a live table's, or one of a past shape's.
     *
     * @param colSnapshot the saved column
     * @return the column
     */
    static TableColumn restoredColumn(final ColumnSnapshot colSnapshot) {
        // Identity start/increment only apply when autoIncrement; for other columns pass the
        // model default (1/1). Old snapshots have autoIncrement=false, so identity is irrelevant.
        final long identityStart = colSnapshot.autoIncrement ? colSnapshot.identityStart : 1L;
        final long identityIncrement = colSnapshot.autoIncrement ? colSnapshot.identityIncrement : 1L;
        // Re-wrap an expression default so INSERT evaluates it again; a literal stays a plain value.
        final Object columnDefault = colSnapshot.defaultIsExpression && colSnapshot.defaultValue != null
            ? new DefaultValueExpression(colSnapshot.defaultValue)
            : colSnapshot.defaultLiteral != null ? colSnapshot.defaultLiteral : colSnapshot.defaultValue;
        final TableColumn col = new TableColumn(
            colSnapshot.name,
            restoredColumnType(colSnapshot),
            colSnapshot.nullable,
            columnDefault,
            colSnapshot.primaryKey,
            colSnapshot.unique,
            colSnapshot.autoIncrement,
            identityStart,
            identityIncrement
        );
        col.setIdentityOrdered(colSnapshot.autoIncrement && colSnapshot.identityOrdered);
        col.setComment(colSnapshot.comment);
        col.setMaskingPolicyName(colSnapshot.maskingPolicyName);
        col.setMaskingPolicyArguments(colSnapshot.maskingPolicyArguments);
        col.setProjectionPolicyName(colSnapshot.projectionPolicyName);
        col.setCollation(colSnapshot.collation);
        col.setFormatType(colSnapshot.formatType);
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
        return col;
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
        fillTableData(storage, table, dataSnapshot);
        logger.debug("Loaded table data: {}.{}.{} ({} rows)",
                    database, schema, table.getName(), dataSnapshot.rows.size());
    }

    /**
     * Fills a table's empty storage with what a snapshot saved of it: its rows, under the identities they had, and its
     * time-travel history — a live table's, or a dropped table's that waits for its UNDROP.
     *
     * @param storage      the storage
     * @param table        the table
     * @param dataSnapshot the saved rows and history
     */
    static void fillTableData(final TableStorage storage, final Table table, final TableDataSnapshot dataSnapshot) {
        // Insert all rows — each row's values defensively copied, so a snapshot applied from memory
        // never shares mutable lists with the engine it builds — under the identities they had.
        final boolean keptIds = dataSnapshot.rowIds != null && dataSnapshot.rowIds.size() == dataSnapshot.rows.size();
        for (int i = 0; i < dataSnapshot.rows.size(); i++) {
            final Row row = new Row(new ArrayList<>(dataSnapshot.rows.get(i)));
            if (keptIds) {
                storage.insertRestored(row, dataSnapshot.rowIds.get(i));
            } else {
                storage.insert(row);
            }
        }
        if (dataSnapshot.history != null) {
            restoreHistory(storage, table, dataSnapshot.history, keptIds);
        }
    }

    /**
     * Puts a table's time-travel history back on its storage, just filled with the saved rows. A saved identity is
     * kept where the rows kept theirs, and is otherwise renumbered: a snapshot's row that is stored now is that stored
     * row, and a past row an UPDATE rewrote shares its identity with the row stored now. The snapshots hold their rows
     * in the table's current columns, as the saved ones did. The table's shapes come back first, and each snapshot
     * takes the one it was taken in, with its rows as it took them, so a clone as of a point before a column change
     * takes the shape and the rows of the point; a snapshot written before shapes were kept takes the shape the table
     * has now.
     *
     * @param storage the table's storage, holding the saved rows in their saved order
     * @param table   the table
     * @param history the saved history
     * @param keptIds whether the stored rows took their saved identities
     */
    static void restoreHistory(final TableStorage storage, final Table table, final TableHistorySnapshot history,
                               final boolean keptIds) {
        final List<Long> ids = new ArrayList<>();
        final List<Row> current = storage.scanWithIds(ids);
        if (current.size() != history.currentRowIds.size()) {
            logger.warn("History of table {} does not match its rows; it is not restored", table.getName());
            return;
        }
        final Map<Long, Long> renumbered = new HashMap<>();
        for (int i = 0; i < current.size(); i++) {
            renumbered.put(history.currentRowIds.get(i), ids.get(i));
        }
        final List<Row> past = new ArrayList<>(history.pastRows.size());
        final List<Long> pastIds = new ArrayList<>(history.pastRows.size());
        for (int p = 0; p < history.pastRows.size(); p++) {
            final Long saved = history.pastRowIds.get(p);
            Long id = renumbered.get(saved);
            if (id == null) {
                id = keptIds ? storage.keptRowId(saved) : storage.freshRowId();
                renumbered.put(saved, id);
            }
            past.add(new Row(new ArrayList<>(history.pastRows.get(p))));
            pastIds.add(id);
        }
        final List<TableStructure> shapes = restoredStructures(history);
        table.restoreStructureHistory(shapes);
        final TableStructure layout = table.currentStructure();
        final List<Row> taken = new ArrayList<>();
        if (history.takenRows != null) {
            for (final List<Object> values : history.takenRows) {
                taken.add(new Row(new ArrayList<>(values)));
            }
        }
        final List<dev.frostlake.storage.TableSnapshot> restored = new ArrayList<>(history.points.size());
        for (final TableHistoryPoint point : history.points) {
            final List<Row> rows = new ArrayList<>(point.rows.size());
            final List<Long> rowIds = new ArrayList<>(point.rows.size());
            for (final Integer place : point.rows) {
                if (place < 0) {
                    rows.add(current.get(-place - 1));
                    rowIds.add(ids.get(-place - 1));
                } else {
                    rows.add(past.get(place));
                    rowIds.add(pastIds.get(place));
                }
            }
            final boolean ownShape = point.layout != null && point.layout >= 0 && point.layout < shapes.size();
            List<Row> asTaken = null;
            if (ownShape && point.takenRows != null) {
                asTaken = new ArrayList<>(point.takenRows.size());
                for (final Integer index : point.takenRows) {
                    asTaken.add(taken.get(index));
                }
            }
            restored.add(new dev.frostlake.storage.TableSnapshot(point.epochMillis, point.sequence, rows, rowIds,
                ownShape ? shapes.get(point.layout) : layout, asTaken));
        }
        storage.restoreHistory(history.startMillis, history.startSequence, history.evicted, restored);
    }

    /** The table's shapes a history saved, oldest first; none for a history written before shapes were kept. */
    private static List<TableStructure> restoredStructures(final TableHistorySnapshot history) {
        final List<TableStructure> shapes = new ArrayList<>();
        if (history.structures == null) {
            return shapes;
        }
        for (final TableStructureSnapshot saved : history.structures) {
            final List<TableColumn> columns = new ArrayList<>(saved.columns.size());
            for (final ColumnSnapshot column : saved.columns) {
                columns.add(restoredColumn(column));
            }
            shapes.add(TableStructure.restored(saved.fromMillis, saved.fromSequence, columns, saved.highestOrdinal));
            SnapshotSequence.advancePast(saved.fromSequence);
        }
        return shapes;
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
            final TableColumn column = new TableColumn(colSnapshot.name, restoredColumnType(colSnapshot),
                colSnapshot.nullable, null, false, false, false);
            column.setComment(colSnapshot.comment);
            column.setCollation(colSnapshot.collation);
            columns.add(column);
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
     * A routine's or a policy's persisted type: the type itself where the snapshot carries it, else
     * rebuilt from its name, as a snapshot written before {@link ParameterSnapshot#type} restores it.
     *
     * @param saved    the persisted type, or null on an older snapshot
     * @param typeName the persisted type name
     * @return the restored type
     */
    static DataType restoredType(final DataType saved, final String typeName) {
        return saved != null ? saved : parseDataType(typeName);
    }

    /**
     * A column's persisted type: the type itself where the snapshot carries it, else rebuilt from its
     * name and parameters, as a snapshot written before {@link ColumnSnapshot#type} restores it.
     *
     * @param colSnapshot the persisted column
     * @return the restored type
     */
    static DataType restoredColumnType(final ColumnSnapshot colSnapshot) {
        return colSnapshot.type != null ? colSnapshot.type
            : parseDataType(colSnapshot.dataType, colSnapshot.precision, colSnapshot.scale,
                colSnapshot.maxLength, colSnapshot.binaryFixed);
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

        final String upper = typeName.toUpperCase(Locale.ROOT);
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
