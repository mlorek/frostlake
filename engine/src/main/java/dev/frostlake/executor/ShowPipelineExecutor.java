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

package dev.frostlake.executor;

import dev.frostlake.executor.operators.ResultSetProvider;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.CortexSearchService;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.RefreshMode;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamType;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.values.VariantValue;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SHOW / DESCRIBE handlers for the data-pipeline object family: streams, tasks, pipes, sequences and
 * dynamic tables. Extracted from {@link ShowCommandExecutor}, which delegates here.
 */
final class ShowPipelineExecutor {

    /**
     * What live prints in a task column that does not apply to the row: the four-character text
     * "null", not a SQL NULL (which live does use for the columns that are merely unset).
     */
    private static final String NOT_APPLICABLE = "null";

    /** Snowflake's default MAX_DATA_EXTENSION_TIME_IN_DAYS, which bounds how long a stream stays fresh. */
    private static final int MAX_DATA_EXTENSION_DAYS = 14;

    private final Catalog catalog;

    ShowPipelineExecutor(final Catalog catalog) {
        this.catalog = catalog;
    }

    /** SHOW STREAMS IN ACCOUNT: every database's streams, in database order. */
    public ResultSet showStreamsInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showStreamsInDatabase(databaseName);
            }
        });
        return across != null ? across : showStreams(null);
    }

    public ResultSet showStreamsInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) throw new RuntimeException("No database specified");
        final List<Row> allRows = new ArrayList<>();
        List<ResultSetColumn> columns = null;
        try {
            final Database db = catalog.getDatabase(dbName);
            for (final Schema schema : db.getAllSchemas()) {
                if (schema.getName().equalsIgnoreCase("INFORMATION_SCHEMA")) continue;
                final ResultSet rs = showStreams(dbName, schema.getName());
                if (columns == null) columns = rs.getColumns();
                allRows.addAll(rs.getRows());
            }
        } catch (final Exception e) {
            throw new RuntimeException("Failed to show streams in database " + dbName + ": " + e.getMessage(), e);
        }
        if (columns == null) {
            columns = showStreams(null, null).getColumns();
        }
        return new ResultSet(columns, allRows);
    }

    public ResultSet showStreams(final String schemaName) {
        return showStreams(null, schemaName);
    }

    public ResultSet showStreams(final String databaseNameOverride, final String schemaName) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("table_name", StringType.VARCHAR),
            new ResultSetColumn("source_type", StringType.VARCHAR),
            new ResultSetColumn("base_tables", StringType.VARCHAR),
            new ResultSetColumn("type", StringType.VARCHAR),
            new ResultSetColumn("stale", StringType.VARCHAR),
            new ResultSetColumn("mode", StringType.VARCHAR),
            new ResultSetColumn("stale_after", StringType.VARCHAR),
            new ResultSetColumn("invalid_reason", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR)
        );
        final String dbName = databaseNameOverride != null
            ? databaseNameOverride
            : ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName == null || scName == null) throw new RuntimeException("No database or schema selected");
        final List<Row> rows = new ArrayList<>();
        for (final Stream stream : catalog.getDatabase(dbName).getSchema(scName).getStreams()) {
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(stream.getCreatedAt()),
                stream.getName(),
                dbName, scName,
                stream.getOwner(),
                ShowResultHelpers.text(stream.getComment()),
                qualifiedSourceName(dbName, scName, stream.getSourceTableName()),
                sourceTypeText(stream),
                qualifiedSourceName(dbName, scName, stream.getSourceTableName()),
                "DELTA",
                stream.isStale() ? "true" : "false",
                stream.getStreamType() == StreamType.APPEND_ONLY ? "APPEND_ONLY" : "DEFAULT",
                ShowResultHelpers.createdOn(staleAfter(stream)),
                "N/A",
                ShowResultHelpers.OWNER_ROLE_TYPE
            )));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showTasks(final String schemaName) {
        return showTasks(null, schemaName);
    }

    public ResultSet showTasks(final String databaseNameOverride, final String schemaName) {
        final String dbName = databaseNameOverride != null
            ? databaseNameOverride
            : ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName == null || scName == null) throw new RuntimeException("No database or schema selected");
        final List<Row> rows = new ArrayList<>();
        for (final Task task : catalog.getDatabase(dbName).getSchema(scName).getTasks()) {
            rows.add(taskRow(task, dbName, scName));
        }
        return new ResultSet(taskColumns(), rows);
    }

    /**
     * The task listing live emits, column for column. The task-level parameters
     * (USER_TASK_TIMEOUT_MS and friends) are deliberately absent: live reports those through
     * SHOW PARAMETERS IN TASK, not here.
     */
    private List<ResultSetColumn> taskColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("id", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("warehouse", StringType.VARCHAR),
            new ResultSetColumn("schedule", StringType.VARCHAR),
            new ResultSetColumn("predecessors", new ArrayType(StringType.VARCHAR)),
            new ResultSetColumn("state", StringType.VARCHAR),
            new ResultSetColumn("definition", StringType.VARCHAR),
            new ResultSetColumn("condition", StringType.VARCHAR),
            new ResultSetColumn("allow_overlapping_execution", StringType.VARCHAR),
            new ResultSetColumn("error_integration", StringType.VARCHAR),
            new ResultSetColumn("last_committed_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("last_suspended_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("config", StringType.VARCHAR),
            new ResultSetColumn("task_relations", StringType.VARCHAR),
            new ResultSetColumn("last_suspended_reason", StringType.VARCHAR),
            new ResultSetColumn("success_integration", StringType.VARCHAR),
            new ResultSetColumn("scheduling_mode", StringType.VARCHAR),
            new ResultSetColumn("target_completion_interval", StringType.VARCHAR),
            new ResultSetColumn("execute_as_user", StringType.VARCHAR),
            new ResultSetColumn("overlap_policy", StringType.VARCHAR),
            new ResultSetColumn("created_by_user", StringType.VARCHAR)
        );
    }

    /** One task row in live's column order. DESCRIBE TASK returns exactly this shape too. */
    private Row taskRow(final Task task, final String dbName, final String scName) {
        final boolean isChild = !task.getPredecessors().isEmpty();
        return new Row(Arrays.asList(
            ShowResultHelpers.createdOn(task.getCreatedAt()),
            task.getName(),
            task.getId(),
            dbName, scName,
            task.getOwner(),
            ShowResultHelpers.text(task.getComment()),
            task.getWarehouse(),
            task.getSchedule(),
            VariantValue.of(predecessorsJson(task, dbName, scName)),
            // Live spells the task state in lower case: started / suspended.
            task.getState() != null ? task.getState().toString().toLowerCase() : null,
            task.getSqlStatement(),
            task.getCondition(),
            // A child task carries no overlap setting of its own; live prints the text "null" there.
            isChild ? NOT_APPLICABLE : String.valueOf(task.isAllowOverlappingExecution()),
            task.getErrorIntegration() != null ? task.getErrorIntegration() : NOT_APPLICABLE,
            null,
            null,
            ShowResultHelpers.OWNER_ROLE_TYPE,
            null,
            "{\"Predecessors\":" + predecessorsJson(task, dbName, scName) + "}",
            null,
            NOT_APPLICABLE,
            null,
            task.getTargetCompletionInterval(),
            null,
            isChild ? null : task.isAllowOverlappingExecution() ? "ALLOW_CHILD_OVERLAP" : "NO_OVERLAP",
            task.getCreatedByUser()
        ));
    }

    /** The task's predecessors as a JSON array of fully qualified names, the form live reports. */
    private String predecessorsJson(final Task task, final String dbName, final String scName) {
        final StringBuilder json = new StringBuilder("[");
        for (final String predecessor : task.getPredecessors()) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append('"').append(qualifiedSourceName(dbName, scName, predecessor)).append('"');
        }
        return json.append(']').toString();
    }

    /** SHOW TASKS IN ACCOUNT: every database's tasks, in database order. */
    public ResultSet showTasksInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showTasksInDatabase(databaseName);
            }
        });
        return across != null ? across : showTasks(null);
    }

    public ResultSet showTasksInDatabase(final String databaseName) {
        // Aggregate tasks from all schemas in the specified database
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) throw new RuntimeException("No database specified");
        final List<Row> allRows = new ArrayList<>();
        List<ResultSetColumn> columns = null;
        try {
            final Database db = catalog.getDatabase(dbName);
            for (final Schema schema : db.getAllSchemas()) {
                if (schema.getName().equalsIgnoreCase("INFORMATION_SCHEMA")) continue;
                final ResultSet rs = showTasks(dbName, schema.getName());
                if (columns == null) columns = rs.getColumns();
                allRows.addAll(rs.getRows());
            }
        } catch (final Exception e) {
            throw new RuntimeException("Failed to show tasks in database " + dbName + ": " + e.getMessage(), e);
        }
        if (columns == null) {
            final ResultSet empty = showTasks(null);
            columns = empty.getColumns();
        }
        return new ResultSet(columns, allRows);
    }

    public ResultSet showPipes(final String schemaName, final String like) {
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName == null || scName == null) throw new RuntimeException("No database or schema selected");
        final List<Row> rows = new ArrayList<>();
        appendPipeRows(dbName, catalog.getDatabase(dbName).getSchema(scName), like, rows);
        return new ResultSet(pipeColumns(), rows);
    }

    /** SHOW PIPES IN DATABASE &lt;db&gt;: pipes across all schemas of the database. */
    /** SHOW PIPES IN ACCOUNT: every database's pipes, in database order. */
    public ResultSet showPipesInAccount(final String like) {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showPipesInDatabase(databaseName, like);
            }
        });
        return across != null ? across : showPipes(null, like);
    }

    public ResultSet showPipesInDatabase(final String databaseName, final String like) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) throw new RuntimeException("No database specified");
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(dbName).getAllSchemas()) {
            appendPipeRows(dbName, schema, like, rows);
        }
        return new ResultSet(pipeColumns(), rows);
    }

    private void appendPipeRows(final String dbName, final Schema schema, final String like, final List<Row> rows) {
        final String scName = schema.getName();
        for (final Pipe pipe : schema.getPipes()) {
            if (like != null && !matchesLike(pipe.getName(), like)) {
                continue;
            }
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(pipe.getCreatedTime()),
                pipe.getName(),
                dbName, scName,
                pipe.getCopyStatement(),
                pipe.getOwner(),
                pipe.getNotificationChannel(),
                ShowResultHelpers.text(pipe.getComment()),
                pipe.getIntegration(),
                null,
                pipe.getErrorIntegration(),
                ShowResultHelpers.OWNER_ROLE_TYPE,
                null,
                "STAGE",
                "false"
            )));
        }
    }

    private List<ResultSetColumn> pipeColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("definition", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("notification_channel", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("integration", StringType.VARCHAR),
            new ResultSetColumn("pattern", StringType.VARCHAR),
            new ResultSetColumn("error_integration", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("invalid_reason", StringType.VARCHAR),
            new ResultSetColumn("kind", StringType.VARCHAR),
            new ResultSetColumn("is_snowflake_managed", StringType.VARCHAR)
        );
    }

    /** Live reports a stream's source with its database and schema, even for a same-schema table. */
    private String qualifiedSourceName(final String dbName, final String scName, final String source) {
        if (source == null || source.indexOf('.') >= 0) {
            return source;
        }
        return dbName + "." + scName + "." + source;
    }

    /** Live capitalises the source kind as a word (Table, View), not as the enum constant. */
    private String sourceTypeText(final Stream stream) {
        if (stream.getSourceType() == null) {
            return null;
        }
        final String name = stream.getSourceType().name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    /**
     * When the stream's change data expires. Snowflake extends a stream's retention up to
     * MAX_DATA_EXTENSION_TIME_IN_DAYS, which defaults to 14 days past the last offset advance.
     */
    private LocalDateTime staleAfter(final Stream stream) {
        if (stream.getCreatedAt() == null) {
            return null;
        }
        return stream.getCreatedAt().plusDays(MAX_DATA_EXTENSION_DAYS);
    }

    /** SQL LIKE match for SHOW … LIKE filters — shared via {@link ShowResultHelpers#matchesLike}. */
    private static boolean matchesLike(final String value, final String pattern) {
        return ShowResultHelpers.matchesLike(value, pattern);
    }

    public ResultSet showSequences(final String schemaName) {
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName == null || scName == null) throw new RuntimeException("No database or schema selected");
        final List<Row> rows = new ArrayList<>();
        appendSequenceRows(dbName, catalog.getDatabase(dbName).getSchema(scName), rows);
        return new ResultSet(sequenceColumns(), rows);
    }

    /** SHOW SEQUENCES IN DATABASE &lt;db&gt;: sequences across all schemas of the database. */
    /** SHOW SEQUENCES IN ACCOUNT: the sequences of every database, in database order. */
    public ResultSet showSequencesInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showSequencesInDatabase(databaseName);
            }
        });
        return across != null ? across : showSequences(null);
    }

    public ResultSet showSequencesInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) throw new RuntimeException("No database specified");
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(dbName).getAllSchemas()) {
            appendSequenceRows(dbName, schema, rows);
        }
        return new ResultSet(sequenceColumns(), rows);
    }

    private void appendSequenceRows(final String databaseName, final Schema schema, final List<Row> rows) {
        for (final Sequence seq : schema.getSequences()) {
            rows.add(new Row(Arrays.asList(
                seq.getName(),
                databaseName,
                schema.getName(),
                seq.getCurrentValueRaw() + seq.getIncrement(),
                seq.getIncrement(),
                ShowResultHelpers.createdOn(seq.getCreatedTime()),
                seq.getOwner(),
                ShowResultHelpers.text(seq.getComment()),
                ShowResultHelpers.OWNER_ROLE_TYPE,
                seq.isOrder() ? "Y" : "N"
            )));
        }
    }

    /**
     * SHOW SEQUENCES' output shape, taken verbatim from a live account: unlike most SHOW
     * listings it leads with {@code name}, carries {@code database_name}, and calls the two numbers
     * {@code next_value} (the value the next NEXTVAL hands out — the START value for a fresh sequence)
     * and {@code interval} rather than start_value / increment.
     */
    private List<ResultSetColumn> sequenceColumns() {
        return Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("next_value", NumericType.BIGINT),
            new ResultSetColumn("interval", NumericType.BIGINT),
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("ordered", StringType.VARCHAR)
        );
    }

    public ResultSet describeStream(final String streamName) {
        final List<Row> rows = new ArrayList<>();
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("property", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR)
        );

        final String dbName = catalog.getCurrentDatabase();
        final String scName = catalog.getCurrentSchema();
        final Schema schema = catalog.getDatabase(dbName).getSchema(scName);
        final Stream stream = schema.getStream(streamName);

        rows.add(new Row(Arrays.asList("name", stream.getName())));
        rows.add(new Row(Arrays.asList("table_name", stream.getSourceTableName())));
        rows.add(new Row(Arrays.asList("type", stream.getStreamType().toString())));

        return new ResultSet(columns, rows);
    }

    /** DESCRIBE TASK returns the task's SHOW TASKS row — live gives the two commands one shape. */
    public ResultSet describeTask(final String taskName) {
        final String dbName = catalog.getCurrentDatabase();
        final String scName = catalog.getCurrentSchema();
        final Schema schema = catalog.getDatabase(dbName).getSchema(scName);
        final Task task = schema.getTask(taskName);
        if (task == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Task", taskName));
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(taskRow(task, dbName, scName));
        return new ResultSet(taskColumns(), rows);
    }

    /** DESCRIBE PIPE returns the pipe's SHOW PIPES row — live gives the two commands one shape. */
    public ResultSet describePipe(final String pipeName) {
        final String dbName = catalog.getCurrentDatabase();
        final String scName = catalog.getCurrentSchema();
        final Schema schema = catalog.getDatabase(dbName).getSchema(scName);
        final List<Row> rows = new ArrayList<>();
        appendPipeRows(dbName, schema, null, rows);
        final List<Row> matching = new ArrayList<>();
        for (final Row row : rows) {
            if (pipeName.equalsIgnoreCase(String.valueOf(row.getValue(1)))) {
                matching.add(row);
            }
        }
        if (matching.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Pipe", pipeName));
        }
        return new ResultSet(pipeColumns(), matching);
    }

    public ResultSet describeSequence(final String sequenceName) {
        // Snowflake DESC SEQUENCE returns a single columnar row, not property/value rows (live-verified).
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("next_value", NumericType.BIGINT),
            new ResultSetColumn("interval", NumericType.BIGINT),
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("ordered", StringType.VARCHAR)
        );

        final String dbName = catalog.getCurrentDatabase();
        final String scName = catalog.getCurrentSchema();
        final Schema schema = catalog.getDatabase(dbName).getSchema(scName);
        final Sequence sequence = schema.getSequence(sequenceName);

        final List<Row> rows = new ArrayList<>();
        // The same ten cells SHOW SEQUENCES prints for the one sequence — the creation instant and
        // the empty-string comment included; live fills both here exactly as it does there.
        rows.add(new Row(Arrays.asList(
            sequence.getName(),
            dbName, scName,
            sequence.getCurrentValueRaw() + sequence.getIncrement(), // the next value NEXTVAL would serve
            sequence.getIncrement(),
            ShowResultHelpers.createdOn(sequence.getCreatedTime()),
            sequence.getOwner(),
            ShowResultHelpers.text(sequence.getComment()),
            ShowResultHelpers.OWNER_ROLE_TYPE,
            sequence.isOrder() ? "Y" : "N"
        )));

        return new ResultSet(columns, rows);
    }

    /**
     * SHOW CORTEX SEARCH SERVICES — a real account's 20 columns, in its order. The three counters
     * (source_data_num_rows, scoring_profile_count, auto_suspend) and the two lifecycle states come
     * from the service as the engine keeps it rather than from a running indexer, so a service here
     * reports itself ACTIVE and served the moment it is created.
     */
    public ResultSet showCortexSearchServices(final String schemaName, final String like) {
        final List<Row> rows = new ArrayList<>();
        // IN SCHEMA takes a name that may already carry its database, so the schema is RESOLVED rather
        // than looked up under the current one — `IN SCHEMA db.schema` names a schema, not a schema
        // called "db.schema".
        appendCortexSearchServiceRows(resolveShowSchema(schemaName), like, rows);
        return new ResultSet(cortexSearchServiceColumns(), rows);
    }

    private Schema resolveShowSchema(final String schemaName) {
        if (schemaName != null) {
            return catalog.resolveSchema(schemaName);
        }
        final String dbName = catalog.getCurrentDatabase();
        final String scName = catalog.getCurrentSchema();
        if (dbName == null || scName == null) {
            throw new RuntimeException("No database or schema selected");
        }
        return catalog.getDatabase(dbName).getSchema(scName);
    }

    /** SHOW CORTEX SEARCH SERVICES IN ACCOUNT: every database's services, in database order. */
    public ResultSet showCortexSearchServicesInAccount(final String like) {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showCortexSearchServicesInDatabase(databaseName, like);
            }
        });
        return across != null ? across : showCortexSearchServices(null, like);
    }

    public ResultSet showCortexSearchServicesInDatabase(final String databaseName, final String like) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) {
            throw new RuntimeException("No database specified");
        }
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(dbName).getAllSchemas()) {
            appendCortexSearchServiceRows(schema, like, rows);
        }
        return new ResultSet(cortexSearchServiceColumns(), rows);
    }

    /** DESCRIBE CORTEX SEARCH SERVICE — the one service, in the SHOW shape. */
    public ResultSet describeCortexSearchService(final String serviceName) {
        final CortexSearchService service = catalog.resolveCortexSearchService(serviceName);
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(cortexSearchServiceRow(service)));
        return new ResultSet(cortexSearchServiceColumns(), rows);
    }

    private void appendCortexSearchServiceRows(final Schema schema, final String like,
                                               final List<Row> rows) {
        for (final CortexSearchService service : schema.getCortexSearchServices()) {
            if (like == null || matchesLike(service.getName(), like)) {
                rows.add(new Row(cortexSearchServiceRow(service)));
            }
        }
    }

    private List<Object> cortexSearchServiceRow(final CortexSearchService service) {
        return Arrays.asList(
            ShowResultHelpers.createdOn(service.getCreatedOn()),
            service.getName(),
            service.getDatabaseName(),
            service.getSchemaName(),
            service.getTargetLag(),
            service.getWarehouse(),
            service.getSearchColumn(),
            nameArrayText(service.getAttributeColumns()),
            nameArrayText(service.getColumns()),
            service.getDefinition(),
            service.getComment(),
            service.getEmbeddingModel(),
            "ACTIVE",
            "RUNNING",
            Long.valueOf(0L),
            nameArrayText(new ArrayList<String>()),
            Long.valueOf(0L),
            null,
            "AUTO",
            nameArrayText(new ArrayList<String>())
        );
    }

    /**
     * A list of column names as SHOW prints one: a JSON array of quoted names, or {@code []} when the
     * service names none.
     */
    private String nameArrayText(final List<String> names) {
        final StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append('"').append(names.get(i)).append('"');
        }
        return text.append(']').toString();
    }

    private List<ResultSetColumn> cortexSearchServiceColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("target_lag", StringType.VARCHAR),
            new ResultSetColumn("warehouse", StringType.VARCHAR),
            new ResultSetColumn("search_column", StringType.VARCHAR),
            new ResultSetColumn("attribute_columns", StringType.VARCHAR),
            new ResultSetColumn("columns", StringType.VARCHAR),
            new ResultSetColumn("definition", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("embedding_model", StringType.VARCHAR),
            new ResultSetColumn("indexing_state", StringType.VARCHAR),
            new ResultSetColumn("serving_state", StringType.VARCHAR),
            new ResultSetColumn("source_data_num_rows", NumericType.BIGINT),
            new ResultSetColumn("primary_key_columns", StringType.VARCHAR),
            new ResultSetColumn("scoring_profile_count", NumericType.BIGINT),
            new ResultSetColumn("auto_suspend", StringType.VARCHAR),
            new ResultSetColumn("refresh_mode", StringType.VARCHAR),
            new ResultSetColumn("vector_indexes", StringType.VARCHAR)
        );
    }

    public ResultSet showDynamicTables(final String schemaName) {
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName == null || scName == null) throw new RuntimeException("No database or schema selected");
        final List<Row> rows = new ArrayList<>();
        appendDynamicTableRows(dbName, ShowResultHelpers.scopeSchemaReportedGenerically(catalog, dbName, scName), rows);
        return new ResultSet(dynamicTableColumns(), rows);
    }

    /** SHOW DYNAMIC TABLES IN DATABASE &lt;db&gt;: dynamic tables across all schemas of the database. */
    /** SHOW DYNAMIC TABLES IN ACCOUNT: every database's dynamic tables, in database order. */
    public ResultSet showDynamicTablesInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showDynamicTablesInDatabase(databaseName);
            }
        });
        return across != null ? across : showDynamicTables(null);
    }

    public ResultSet showDynamicTablesInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) throw new RuntimeException("No database specified");
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(dbName).getAllSchemas()) {
            appendDynamicTableRows(dbName, schema, rows);
        }
        return new ResultSet(dynamicTableColumns(), rows);
    }

    private void appendDynamicTableRows(final String dbName, final Schema schema, final List<Row> rows) {
        final String scName = schema.getName();
        for (final DynamicTable dt : schema.getDynamicTables()) {
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(dt.getCreatedTime()),
                dt.getName(), dbName, scName,
                null, 0L, 0L,
                dt.getOwner(),
                dt.getTargetLag(),
                resolvedRefreshMode(dt),
                null,
                dt.getWarehouse(),
                ShowResultHelpers.text(dt.getComment()),
                dt.getQuery(),
                "OFF",
                dt.getSchedulingState(),
                null, "N", "N", "N",
                dt.getLastRefreshedTime() != null ? dt.getLastRefreshedTime().toString() : null,
                ShowResultHelpers.OWNER_ROLE_TYPE,
                null, null, null, null, null, null, null, null,
                dt.getRefreshMode().name()
            )));
        }
    }

    /**
     * The refresh_mode CELL reports the RESOLVED mode: a dynamic table configured AUTO runs (and
     * reads back) INCREMENTAL for the plain projections this engine evaluates, live-verified,
     * while configured_refresh_mode keeps the declared word.
     */
    private static String resolvedRefreshMode(final DynamicTable dt) {
        return dt.getRefreshMode() == RefreshMode.AUTO
            ? RefreshMode.INCREMENTAL.name()
            : dt.getRefreshMode().name();
    }

    /** SHOW DYNAMIC TABLES in live's column order — the definition column is named {@code text}. */
    private List<ResultSetColumn> dynamicTableColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("cluster_by", StringType.VARCHAR),
            new ResultSetColumn("rows", NumericType.BIGINT),
            new ResultSetColumn("bytes", NumericType.BIGINT),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("target_lag", StringType.VARCHAR),
            new ResultSetColumn("refresh_mode", StringType.VARCHAR),
            new ResultSetColumn("refresh_mode_reason", StringType.VARCHAR),
            new ResultSetColumn("warehouse", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("text", StringType.VARCHAR),
            new ResultSetColumn("automatic_clustering", StringType.VARCHAR),
            new ResultSetColumn("scheduling_state", StringType.VARCHAR),
            new ResultSetColumn("last_suspended_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("is_clone", StringType.VARCHAR),
            new ResultSetColumn("is_replica", StringType.VARCHAR),
            new ResultSetColumn("is_iceberg", StringType.VARCHAR),
            new ResultSetColumn("data_timestamp", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("execute_as_user", StringType.VARCHAR),
            new ResultSetColumn("secondary_role_names", StringType.VARCHAR),
            new ResultSetColumn("insert_only_inputs", StringType.VARCHAR),
            new ResultSetColumn("immutable_where", StringType.VARCHAR),
            new ResultSetColumn("initialization_warehouse", StringType.VARCHAR),
            new ResultSetColumn("backfill_from", StringType.VARCHAR),
            new ResultSetColumn("scheduler", StringType.VARCHAR),
            new ResultSetColumn("frozen_where", StringType.VARCHAR),
            new ResultSetColumn("configured_refresh_mode", StringType.VARCHAR)
        );
    }

    /**
     * DESCRIBE DYNAMIC TABLE answers the COLUMN list, exactly as DESCRIBE TABLE does
     * (live-verified) — never property/value rows. The dynamic table itself is resolved first, so
     * a missing one refuses before the projection runs; the projection then supplies the columns.
     */
    public ResultSet describeDynamicTable(final String tableName, final ResultSetProvider projection) {
        final String dbName = catalog.getCurrentDatabase();
        final String scName = catalog.getCurrentSchema();
        catalog.getDatabase(dbName).getSchema(scName).getDynamicTable(tableName);

        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("type", StringType.VARCHAR),
            new ResultSetColumn("kind", StringType.VARCHAR),
            new ResultSetColumn("null?", StringType.VARCHAR),
            new ResultSetColumn("default", StringType.VARCHAR),
            new ResultSetColumn("primary key", StringType.VARCHAR),
            new ResultSetColumn("unique key", StringType.VARCHAR),
            new ResultSetColumn("check", StringType.VARCHAR),
            new ResultSetColumn("expression", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("policy name", StringType.VARCHAR)
        );
        final List<Row> rows = new ArrayList<>();
        for (final ResultSetColumn column : projection.getResultSet().getColumns()) {
            rows.add(new Row(Arrays.asList(
                column.getName().toUpperCase(),
                column.getDataType().getName(),
                "COLUMN",
                "Y",
                null, "N", "N",
                null, null, null, null
            )));
        }
        return new ResultSet(columns, rows);
    }
}
