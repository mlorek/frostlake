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

package dev.frostlake.system;

import dev.frostlake.metastore.*;
import dev.frostlake.metastore.model.*;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystemViews {

    private static final Logger logger = LoggerFactory.getLogger(SystemViews.class);

    private final Catalog catalog;

    public SystemViews(final Catalog catalog) {
        this.catalog = catalog;
    }

    // ── DATABASES ─────────────────────────────────────────────────────────────

    public ResultSet queryDatabases() {
        ResultSet result = new ResultSet(Arrays.asList(
            col("DATABASE_NAME"), col("DATABASE_OWNER"), col("IS_TRANSIENT"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : catalog.getAllDatabases()) {
            result.addRow(new Row(
                db.getName(), db.getOwner(), "N",
                db.getCreatedTime(), db.getCreatedTime(), db.getComment()
            ));
        }
        return result;
    }

    // ── SCHEMATA ──────────────────────────────────────────────────────────────

    public ResultSet querySchemata(final String databaseName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("CATALOG_NAME"), col("SCHEMA_NAME"), col("SCHEMA_OWNER"),
            col("IS_TRANSIENT"), col("IS_MANAGED_ACCESS"),
            col("RETENTION_TIME"), colInt("RETENTION_TIME_DAYS"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : db.getAllSchemas()) {
                result.addRow(new Row(
                    db.getName(), schema.getName(), schema.getOwner(),
                    "N", "N", "1", 1L,
                    schema.getCreatedTime(), schema.getCreatedTime(), schema.getComment()
                ));
            }
        }
        return result;
    }

    // ── TABLES ────────────────────────────────────────────────────────────────

    public ResultSet queryTables(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"),
            col("TABLE_OWNER"), col("TABLE_TYPE"),
            col("IS_TRANSIENT"), col("CLUSTERING_KEY"),
            colLong("ROW_COUNT"), colLong("BYTES"),
            col("RETENTION_TIME"), col("CREATED"), col("LAST_ALTERED"),
            col("LAST_DDL"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Table table : schema.getTables()) {
                    String type = table.isTemporary() ? "TEMPORARY TABLE" :
                                  table.isTransient() ? "TRANSIENT TABLE" : "BASE TABLE";
                    result.addRow(new Row(
                        db.getName(), schema.getName(), table.getName(), table.getOwner(),
                        type, table.isTransient() ? "Y" : "N",
                        table.getClusterKeys().isEmpty() ? null : String.join(", ", table.getClusterKeys()),
                        (long) table.getRowCount(), 0L, "1",
                        table.getCreatedTime(), table.getCreatedTime(), table.getCreatedTime(),
                        table.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── COLUMNS ───────────────────────────────────────────────────────────────

    public ResultSet queryColumns(final String databaseName, final String schemaName, final String tableName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"),
            col("COLUMN_NAME"), colInt("ORDINAL_POSITION"),
            col("COLUMN_DEFAULT"), col("IS_NULLABLE"),
            col("DATA_TYPE"), colInt("CHARACTER_MAXIMUM_LENGTH"),
            colInt("NUMERIC_PRECISION"), colInt("NUMERIC_SCALE"),
            col("COLLATION_NAME"), col("IS_IDENTITY"),
            col("IDENTITY_START"), col("IDENTITY_INCREMENT"),
            col("COMMENT"), col("IS_PRIMARY_KEY")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                List<Table> tables = tableName != null
                    ? Collections.singletonList(schema.getTable(tableName))
                    : schema.getTables();
                for (final Table table : tables) {
                    int pos = 1;
                    for (final TableColumn col : table.getColumns()) {
                        String typeName = col.getDataType().getName().toUpperCase();
                        Integer charLen = typeName.startsWith("VARCHAR") || typeName.startsWith("CHAR") || typeName.startsWith("TEXT")
                            ? 16777216 : null;
                        Integer numPrec = typeName.contains("INT") || typeName.contains("NUM") ||
                                          typeName.contains("FLOAT") || typeName.contains("DOUBLE") ||
                                          typeName.contains("DECIMAL") ? 38 : null;
                        Integer numScale = typeName.contains("FLOAT") || typeName.contains("DOUBLE") ? 6 : null;
                        result.addRow(new Row(
                            db.getName(), schema.getName(), table.getName(),
                            col.getName(), pos++,
                            col.getDefaultValue() != null ? col.getDefaultValue().toString() : null,
                            col.isNullable() ? "YES" : "NO",
                            typeName, charLen, numPrec, numScale,
                            col.getCollation(),
                            col.isAutoIncrement() ? "YES" : "NO",
                            col.isAutoIncrement() ? "1" : null,
                            col.isAutoIncrement() ? "1" : null,
                            col.getComment(),
                            col.isPrimaryKey() ? "YES" : "NO"
                        ));
                    }
                }
            }
        }
        return result;
    }

    // ── VIEWS ─────────────────────────────────────────────────────────────────

    public ResultSet queryViews(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"),
            col("TABLE_OWNER"), col("VIEW_DEFINITION"), col("CHECK_OPTION"),
            col("IS_UPDATABLE"), col("IS_INSERTABLE_INTO"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final View view : schema.getViews()) {
                    // Snowflake's VIEW_DEFINITION is the view's full executable DDL, not just its
                    // query: deployment tooling recreates views via EXECUTE IMMEDIATE of this text.
                    result.addRow(new Row(
                        db.getName(), schema.getName(), view.getName(), view.getOwner(),
                        view.ddl(schema.getName() + "." + view.getName()), "NONE", "NO", "NO",
                        view.getCreatedTime(), view.getCreatedTime(), view.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── TABLE_CONSTRAINTS ─────────────────────────────────────────────────────

    public ResultSet queryTableConstraints(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("CONSTRAINT_CATALOG"), col("CONSTRAINT_SCHEMA"), col("CONSTRAINT_NAME"),
            col("TABLE_CATALOG"), col("TABLE_SCHEMA"), col("TABLE_NAME"),
            col("CONSTRAINT_TYPE"), col("IS_DEFERRABLE"), col("INITIALLY_DEFERRED"),
            col("ENFORCED"), col("RELY"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Table table : schema.getTables()) {
                    for (final TableColumn col : table.getColumns()) {
                        if (col.isPrimaryKey()) {
                            result.addRow(constraint(db, schema, table, col, "PRIMARY KEY"));
                        }
                        if (col.isUnique()) {
                            result.addRow(constraint(db, schema, table, col, "UNIQUE"));
                        }
                        if (col.hasForeignKey()) {
                            result.addRow(constraint(db, schema, table, col, "FOREIGN KEY"));
                        }
                    }
                    // Table-level FOREIGN KEY constraints (FOREIGN KEY (cols) REFERENCES …) live in the
                    // constraint list rather than as a column reference like an inline REFERENCES, so emit
                    // them too. Snowflake does not distinguish the two declaration forms in TABLE_CONSTRAINTS.
                    for (final ForeignKeyConstraint fk : table.getForeignKeys()) {
                        for (final String fkColumn : fk.getColumnNames()) {
                            result.addRow(foreignKeyConstraintRow(db, schema, table, fk, fkColumn));
                        }
                    }
                }
            }
        }
        return result;
    }

    private Row constraint(final Database db, final Schema schema, final Table table,
                            final TableColumn col, final String type) {
        String suffix = type.equals("PRIMARY KEY") ? "_PK" : type.equals("UNIQUE") ? "_UNIQUE" : "_FK";
        String name = table.getName() + "_" + col.getName() + suffix;
        String rely = col.getRely() != null && col.getRely() ? "YES" : "NO";
        return new Row(db.getName(), schema.getName(), name,
                       db.getName(), schema.getName(), table.getName(),
                       type, "NO", "NO", rely, rely, null);
    }

    // Constraint name for a table-level FK: the explicit CONSTRAINT name if given, else the same
    // generated {table}_{column}_FK shape used for inline foreign keys so the two views line up.
    private String foreignKeyName(final Table table, final ForeignKeyConstraint fk, final String columnName) {
        return fk.getConstraintName() != null && !fk.getConstraintName().isEmpty()
            ? fk.getConstraintName()
            : table.getName() + "_" + columnName + "_FK";
    }

    private Row foreignKeyConstraintRow(final Database db, final Schema schema, final Table table,
                                        final ForeignKeyConstraint fk, final String columnName) {
        final String rely = fk.getRely() != null && fk.getRely() ? "YES" : "NO";
        return new Row(db.getName(), schema.getName(), foreignKeyName(table, fk, columnName),
                       db.getName(), schema.getName(), table.getName(),
                       "FOREIGN KEY", "NO", "NO", rely, rely, null);
    }

    // ── REFERENTIAL_CONSTRAINTS ───────────────────────────────────────────────

    public ResultSet queryReferentialConstraints(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("CONSTRAINT_CATALOG"), col("CONSTRAINT_SCHEMA"), col("CONSTRAINT_NAME"),
            col("UNIQUE_CONSTRAINT_CATALOG"), col("UNIQUE_CONSTRAINT_SCHEMA"), col("UNIQUE_CONSTRAINT_NAME"),
            col("MATCH_OPTION"), col("UPDATE_RULE"), col("DELETE_RULE"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Table table : schema.getTables()) {
                    for (final TableColumn col : table.getColumns()) {
                        if (col.hasForeignKey()) {
                            result.addRow(new Row(
                                db.getName(), schema.getName(),
                                table.getName() + "_" + col.getName() + "_FK",
                                db.getName(), schema.getName(),
                                col.getReferencedTable() + "_PK",
                                "NONE", "NO ACTION", "NO ACTION", null
                            ));
                        }
                    }
                    // Table-level FOREIGN KEY constraints (one referential row per constraint).
                    for (final ForeignKeyConstraint fk : table.getForeignKeys()) {
                        result.addRow(new Row(
                            db.getName(), schema.getName(),
                            foreignKeyName(table, fk, fk.getColumnNames().get(0)),
                            db.getName(), schema.getName(),
                            fk.getReferencedTable() + "_PK",
                            "NONE",
                            fk.getOnUpdate() != null ? fk.getOnUpdate() : "NO ACTION",
                            fk.getOnDelete() != null ? fk.getOnDelete() : "NO ACTION",
                            null
                        ));
                    }
                }
            }
        }
        return result;
    }

    // ── PROCEDURES ────────────────────────────────────────────────────────────

    public ResultSet queryProcedures(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("PROCEDURE_CATALOG"), col("PROCEDURE_SCHEMA"), col("PROCEDURE_NAME"),
            col("PROCEDURE_OWNER"), col("PROCEDURE_LANGUAGE"),
            col("ARGUMENT_SIGNATURE"), col("DATA_TYPE"),
            col("PROCEDURE_DEFINITION"), col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Procedure proc : schema.getProcedures()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), proc.getName(), proc.getOwner(),
                        proc.getLanguage(), buildArgSignature(proc.getParameters()),
                        proc.getReturnType().getName(),
                        proc.getBody(), proc.getCreatedTime(), proc.getCreatedTime(), proc.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── FUNCTIONS ─────────────────────────────────────────────────────────────

    public ResultSet queryFunctions(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("FUNCTION_CATALOG"), col("FUNCTION_SCHEMA"), col("FUNCTION_NAME"),
            col("FUNCTION_OWNER"), col("FUNCTION_LANGUAGE"), col("IS_TABLE_FUNCTION"),
            col("ARGUMENT_SIGNATURE"), col("DATA_TYPE"),
            col("FUNCTION_DEFINITION"), col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Function func : schema.getFunctions()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), func.getName(), func.getOwner(),
                        func.getLanguage(), func.isTableFunction() ? "Y" : "N",
                        buildArgSignature(func.getParameters()),
                        func.getReturnType().getName(),
                        func.getBody(), func.getCreatedTime(), func.getCreatedTime(), func.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── SEQUENCES ─────────────────────────────────────────────────────────────

    public ResultSet querySequences(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("SEQUENCE_CATALOG"), col("SEQUENCE_SCHEMA"), col("SEQUENCE_NAME"),
            col("SEQUENCE_OWNER"), col("DATA_TYPE"),
            colLong("NUMERIC_PRECISION"), colLong("NUMERIC_PRECISION_RADIX"), colLong("NUMERIC_SCALE"),
            colLong("START_VALUE"), colLong("MINIMUM_VALUE"), colLong("MAXIMUM_VALUE"),
            colLong("INCREMENT"), col("CYCLE_OPTION"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Sequence seq : schema.getSequences()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), seq.getName().toUpperCase(), seq.getOwner(),
                        "NUMBER", 38L, 10L, 0L,
                        seq.getStartValue(), Long.MIN_VALUE, Long.MAX_VALUE,
                        seq.getIncrement(), "N",
                        null, null, seq.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── STAGES ────────────────────────────────────────────────────────────────

    public ResultSet queryStages(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("STAGE_CATALOG"), col("STAGE_SCHEMA"), col("STAGE_NAME"),
            col("STAGE_OWNER"), col("STAGE_TYPE"), col("STAGE_URL"),
            col("STAGE_REGION"), col("STAGE_FILE_FORMAT"), col("STAGE_COPY_OPTIONS"),
            col("CREATED"), col("LAST_ALTERED"), col("HAS_CREDENTIALS"),
            col("HAS_ENCRYPTION_KEY"), col("COMMENT")
        ));
        try {
            String dbN = databaseName != null ? databaseName : catalog.getCurrentDatabase();
            String scN = schemaName != null ? schemaName : catalog.getCurrentSchema();
            if (dbN != null && scN != null) {
                for (final Stage stage : catalog.getDatabase(dbN).getSchema(scN).getStages()) {
                    String url = stage.getUrl() != null ? stage.getUrl() : "";
                    String type = (url.startsWith("s3://") || url.startsWith("azure://") || url.startsWith("gcs://"))
                        ? "External Stage" : "Internal Named Stage";
                    result.addRow(new Row(
                        dbN, scN, stage.getName().toUpperCase(), stage.getOwner(),
                        type, url, null, stage.getFileFormat(), null,
                        stage.getCreatedAt(), stage.getCreatedAt(), "N", "N", stage.getComment()
                    ));
                }
            }
        } catch (final Exception e) {
            // Skip a row that failed to build rather than failing the whole system view, but log at
            // debug so the underlying error stays diagnosable.
            logger.debug("Skipping a system-view row that could not be built: {}", e.getMessage(), e);
        }
        return result;
    }

    // ── PIPES ─────────────────────────────────────────────────────────────────

    public ResultSet queryPipes(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("PIPE_CATALOG"), col("PIPE_SCHEMA"), col("PIPE_NAME"),
            col("PIPE_OWNER"), col("DEFINITION"), col("IS_AUTOINGEST_ENABLED"),
            col("NOTIFICATION_CHANNEL_NAME"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Pipe pipe : schema.getPipes()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), pipe.getName().toUpperCase(), pipe.getOwner(),
                        pipe.getCopyStatement(), pipe.isAutoIngest() ? "Y" : "N",
                        null, pipe.getCreatedTime(), pipe.getCreatedTime(), pipe.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── STREAMS ───────────────────────────────────────────────────────────────

    public ResultSet queryStreams(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("STREAM_CATALOG"), col("STREAM_SCHEMA"), col("STREAM_NAME"),
            col("STREAM_OWNER"), col("TABLE_NAME"), col("SOURCE_TYPE"),
            col("BASE_TABLES"), col("TYPE"), col("STALE"), col("MODE"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Stream stream : schema.getStreams()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), stream.getName(), stream.getOwner(),
                        stream.getSourceTableName(),
                        stream.getSourceType() != null ? stream.getSourceType().name() : null,
                        stream.getSourceTableName(), "Delta", "N", "DEFAULT",
                        stream.getCreatedAt(), stream.getCreatedAt(), stream.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── TASKS ─────────────────────────────────────────────────────────────────

    public ResultSet queryTasks(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("TASK_CATALOG"), col("TASK_SCHEMA"), col("TASK_NAME"),
            col("TASK_OWNER"), col("WAREHOUSE"), col("SCHEDULE"),
            col("PREDECESSORS"), col("STATE"), col("DEFINITION"),
            col("CONDITION"), col("ALLOW_OVERLAPPING_EXECUTION"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        for (final Database db : databases(databaseName)) {
            for (final Schema schema : schemas(db, schemaName)) {
                for (final Task task : schema.getTasks()) {
                    result.addRow(new Row(
                        db.getName(), schema.getName(), task.getName().toUpperCase(), task.getOwner(),
                        task.getWarehouse(), task.getSchedule(),
                        null,
                        task.getState() != null ? task.getState().name() : null,
                        task.getSqlStatement(), null, "FALSE",
                        task.getCreatedAt(), task.getCreatedAt(), task.getComment()
                    ));
                }
            }
        }
        return result;
    }

    // ── ENABLED_ROLES ─────────────────────────────────────────────────────────

    public ResultSet queryEnabledRoles() {
        ResultSet result = new ResultSet(Arrays.asList(col("ROLE_NAME"), col("ROLE_OWNER"), col("COMMENT")));
        for (final Role role : catalog.getAllRoles()) {
            result.addRow(new Row(role.getName(), role.getOwner(), role.getComment()));
        }
        return result;
    }

    // ── APPLICABLE_ROLES ──────────────────────────────────────────────────────

    public ResultSet queryApplicableRoles() {
        ResultSet result = new ResultSet(Arrays.asList(
            col("GRANTEE"), col("ROLE_NAME"), col("ROLE_OWNER"), col("IS_GRANTABLE")
        ));
        for (final User user : catalog.getAllUsers()) {
            for (final String role : user.getGrantedRoles()) {
                result.addRow(new Row(user.getName(), role, catalog.getRole(role).getOwner(), "NO"));
            }
        }
        return result;
    }

    // ── TABLE_PRIVILEGES ──────────────────────────────────────────────────────

    public ResultSet queryTablePrivileges(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("GRANTOR"), col("GRANTEE"), col("TABLE_CATALOG"),
            col("TABLE_SCHEMA"), col("TABLE_NAME"),
            col("PRIVILEGE_TYPE"), col("IS_GRANTABLE"), col("WITH_HIERARCHY")
        ));
        // Privileges are not fully tracked yet; return empty result set
        return result;
    }

    // ── OBJECT_PRIVILEGES ─────────────────────────────────────────────────────

    public ResultSet queryObjectPrivileges() {
        ResultSet result = new ResultSet(Arrays.asList(
            col("GRANTOR"), col("GRANTEE"), col("OBJECT_CATALOG"),
            col("OBJECT_SCHEMA"), col("OBJECT_NAME"), col("OBJECT_TYPE"),
            col("PRIVILEGE_TYPE"), col("IS_GRANTABLE")
        ));
        return result;
    }

    // ── USAGE_PRIVILEGES ──────────────────────────────────────────────────────

    public ResultSet queryUsagePrivileges() {
        ResultSet result = new ResultSet(Arrays.asList(
            col("GRANTOR"), col("GRANTEE"), col("OBJECT_CATALOG"),
            col("OBJECT_SCHEMA"), col("OBJECT_NAME"), col("OBJECT_TYPE"),
            col("PRIVILEGE_TYPE"), col("IS_GRANTABLE")
        ));
        return result;
    }

    // ── TAGS ──────────────────────────────────────────────────────────────────

    public ResultSet queryTags(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("TAG_DATABASE"), col("TAG_SCHEMA"), col("TAG_NAME"),
            col("TAG_OWNER"), col("DATA_TYPE"), col("ALLOWED_VALUES"),
            col("CREATED"), col("LAST_ALTERED"), col("COMMENT")
        ));
        try {
            String dbN = databaseName != null ? databaseName : catalog.getCurrentDatabase();
            String scN = schemaName != null ? schemaName : catalog.getCurrentSchema();
            if (dbN != null && scN != null) {
                for (final Tag tag : catalog.getDatabase(dbN).getSchema(scN).getTags()) {
                    result.addRow(new Row(
                        dbN, scN, tag.getName().toUpperCase(), tag.getOwner(),
                        "VARCHAR", null, tag.getCreatedTime(), tag.getCreatedTime(), tag.getComment()
                    ));
                }
            }
        } catch (final Exception e) {
            // Skip a row that failed to build rather than failing the whole system view, but log at
            // debug so the underlying error stays diagnosable.
            logger.debug("Skipping a system-view row that could not be built: {}", e.getMessage(), e);
        }
        return result;
    }

    public ResultSet queryTagReferences(final String databaseName, final String schemaName) {
        ResultSet result = new ResultSet(Arrays.asList(
            col("TAG_DATABASE"), col("TAG_SCHEMA"), col("TAG_NAME"), col("TAG_VALUE"),
            col("OBJECT_DATABASE"), col("OBJECT_SCHEMA"), col("OBJECT_NAME"),
            col("COLUMN_NAME"), col("DOMAIN")
        ));
        try {
            String dbN = databaseName != null ? databaseName : catalog.getCurrentDatabase();
            if (dbN == null) {
                return result;
            }
            Database db = catalog.getDatabase(dbN);
            addTagRows(result, dbN, null, db.getName(), null, "DATABASE", db.getTagValues());
            for (final Schema sc : db.getAllSchemas()) {
                if (schemaName != null && !sc.getName().equalsIgnoreCase(schemaName)) {
                    continue;
                }
                addTagRows(result, dbN, sc.getName(), sc.getName(), null, "SCHEMA", sc.getTagValues());
                for (final Table t : sc.getTables()) {
                    addTagRows(result, dbN, sc.getName(), t.getName(), null, "TABLE", t.getTagValues());
                    for (final TableColumn c : t.getColumns()) {
                        addTagRows(result, dbN, sc.getName(), t.getName(), c.getName(), "COLUMN", c.getTagValues());
                    }
                }
                for (final View v : sc.getViews()) {
                    addTagRows(result, dbN, sc.getName(), v.getName(), null, "VIEW", v.getTagValues());
                }
            }
        } catch (final Exception e) {
            logger.debug("Skipping tag-reference rows that could not be built: {}", e.getMessage(), e);
        }
        return result;
    }

    private void addTagRows(final ResultSet result, final String tagDb, final String objSchema,
                            final String objName, final String columnName, final String domain,
                            final Map<String, String> tagValues) {
        for (final Map.Entry<String, String> entry : tagValues.entrySet()) {
            result.addRow(new Row(
                tagDb, objSchema, entry.getKey(), entry.getValue(),
                tagDb, objSchema, objName, columnName, domain
            ));
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<Database> databases(final String databaseName) {
        return databaseName != null
            ? Collections.singletonList(catalog.getDatabase(databaseName))
            : catalog.getAllDatabases();
    }

    private List<Schema> schemas(final Database db, final String schemaName) {
        return schemaName != null
            ? Collections.singletonList(db.getSchema(schemaName))
            : db.getAllSchemas();
    }

    private ResultSetColumn col(final String name) {
        return new ResultSetColumn(name, StringType.VARCHAR);
    }

    private ResultSetColumn colInt(final String name) {
        return new ResultSetColumn(name, NumericType.INTEGER);
    }

    private ResultSetColumn colLong(final String name) {
        return new ResultSetColumn(name, NumericType.BIGINT);
    }

    private String buildArgSignature(final List<Parameter> params) {
        if (params == null || params.isEmpty()) return "()";
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(params.get(i).getName()).append(" ").append(params.get(i).getDataType().getName());
        }
        sb.append(")");
        return sb.toString();
    }
}
