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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamType;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SHOW / DESCRIBE handlers for the data-pipeline object family: streams, tasks, pipes, sequences and
 * dynamic tables. Extracted from {@link ShowCommandExecutor}, which delegates here.
 */
final class ShowPipelineExecutor {

    private final Catalog catalog;

    ShowPipelineExecutor(final Catalog catalog) {
        this.catalog = catalog;
    }

    public ResultSet showStreamsInDatabase(final String databaseName) {
        String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) throw new RuntimeException("No database specified");
        List<Row> allRows = new ArrayList<>();
        List<ResultSetColumn> columns = null;
        try {
            Database db = catalog.getDatabase(dbName);
            for (final Schema schema : db.getAllSchemas()) {
                if (schema.getName().equalsIgnoreCase("INFORMATION_SCHEMA")) continue;
                ResultSet rs = showStreams(dbName, schema.getName());
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
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
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
            new ResultSetColumn("mode", StringType.VARCHAR)
        );
        String dbName = databaseNameOverride != null ? databaseNameOverride : catalog.getCurrentDatabase();
        String scName = schemaName != null ? schemaName : catalog.getCurrentSchema();
        if (dbName == null || scName == null) throw new RuntimeException("No database or schema selected");
        List<Row> rows = new ArrayList<>();
        for (final Stream stream : catalog.getDatabase(dbName).getSchema(scName).getStreams()) {
            rows.add(new Row(Arrays.asList(
                stream.getCreatedAt() != null ? stream.getCreatedAt().toString() : null,
                stream.getName(),
                dbName, scName,
                stream.getOwner(),
                stream.getComment(),
                stream.getSourceTableName(),
                stream.getSourceType() != null ? stream.getSourceType().name() : null,
                stream.getSourceTableName(),
                "Delta",
                stream.isStale() ? "Y" : "N",
                stream.getStreamType() == StreamType.APPEND_ONLY ? "APPEND_ONLY" : "DEFAULT"
            )));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showTasks(final String schemaName) {
        return showTasks(null, schemaName);
    }

    public ResultSet showTasks(final String databaseNameOverride, final String schemaName) {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("warehouse", StringType.VARCHAR),
            new ResultSetColumn("schedule", StringType.VARCHAR),
            new ResultSetColumn("predecessors", StringType.VARCHAR),
            new ResultSetColumn("state", StringType.VARCHAR),
            new ResultSetColumn("definition", StringType.VARCHAR),
            new ResultSetColumn("condition", StringType.VARCHAR),
            new ResultSetColumn("allow_overlapping_execution", StringType.VARCHAR),
            new ResultSetColumn("user_task_timeout_ms", NumericType.BIGINT),
            new ResultSetColumn("suspend_task_after_num_failures", NumericType.INTEGER),
            new ResultSetColumn("task_auto_retry_attempts", NumericType.INTEGER),
            new ResultSetColumn("user_task_managed_initial_warehouse_size", StringType.VARCHAR),
            new ResultSetColumn("serverless_task_max_statement_size", StringType.VARCHAR),
            new ResultSetColumn("target_completion_interval", StringType.VARCHAR),
            new ResultSetColumn("error_integration", StringType.VARCHAR),
            new ResultSetColumn("user_task_minimum_trigger_interval_in_seconds", NumericType.INTEGER)
        );
        String dbName = databaseNameOverride != null ? databaseNameOverride : catalog.getCurrentDatabase();
        String scName = schemaName != null ? schemaName : catalog.getCurrentSchema();
        if (dbName == null || scName == null) throw new RuntimeException("No database or schema selected");
        List<Row> rows = new ArrayList<>();
        for (final Task task : catalog.getDatabase(dbName).getSchema(scName).getTasks()) {
            rows.add(new Row(Arrays.asList(
                task.getCreatedAt() != null ? task.getCreatedAt().toString() : null,
                task.getName(),
                dbName, scName,
                task.getOwner(),
                task.getComment(),
                task.getWarehouse(),
                task.getSchedule(),
                task.getPredecessors().isEmpty() ? "[]"
                    : "[" + task.getPredecessors().stream()
                        .map((final var p) -> "\"" + p + "\"")
                        .collect(Collectors.joining(",")) + "]",
                task.getState() != null ? task.getState().toString() : null,
                task.getSqlStatement(),
                task.getCondition(),
                String.valueOf(task.isAllowOverlappingExecution()),
                task.getUserTaskTimeoutMs(),
                (long) task.getSuspendTaskAfterNumFailures(),
                (long) task.getTaskAutoRetryAttempts(),
                task.getUserTaskManagedInitialWarehouseSize(),
                task.getServerlessTaskMaxStatementSize(),
                task.getTargetCompletionInterval(),
                task.getErrorIntegration(),
                (long) task.getUserTaskMinimumTriggerIntervalInSeconds()
            )));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showTasksInDatabase(final String databaseName) {
        // Aggregate tasks from all schemas in the specified database
        String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) throw new RuntimeException("No database specified");
        List<Row> allRows = new ArrayList<>();
        List<ResultSetColumn> columns = null;
        try {
            Database db = catalog.getDatabase(dbName);
            for (final Schema schema : db.getAllSchemas()) {
                if (schema.getName().equalsIgnoreCase("INFORMATION_SCHEMA")) continue;
                ResultSet rs = showTasks(dbName, schema.getName());
                if (columns == null) columns = rs.getColumns();
                allRows.addAll(rs.getRows());
            }
        } catch (final Exception e) {
            throw new RuntimeException("Failed to show tasks in database " + dbName + ": " + e.getMessage(), e);
        }
        if (columns == null) {
            ResultSet empty = showTasks(null);
            columns = empty.getColumns();
        }
        return new ResultSet(columns, allRows);
    }

    public ResultSet showPipes(final String schemaName, final String like) {
        final String dbName = catalog.getCurrentDatabase();
        final String scName = schemaName != null ? schemaName : catalog.getCurrentSchema();
        if (dbName == null || scName == null) throw new RuntimeException("No database or schema selected");
        final List<Row> rows = new ArrayList<>();
        appendPipeRows(dbName, catalog.getDatabase(dbName).getSchema(scName), like, rows);
        return new ResultSet(pipeColumns(), rows);
    }

    /** SHOW PIPES IN DATABASE &lt;db&gt;: pipes across all schemas of the database. */
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
                pipe.getCreatedTime().toString(),
                pipe.getName(),
                dbName, scName,
                pipe.getOwner(),
                pipe.getComment(),
                pipe.getNotificationChannel(),
                pipe.getCopyStatement(),
                pipe.isAutoIngest() ? "true" : "false",
                pipe.getIntegration(),
                pipe.getErrorIntegration(),
                pipe.getAwsSnsTopicArn(),
                pipe.getStatus()
            )));
        }
    }

    private List<ResultSetColumn> pipeColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("notification_channel", StringType.VARCHAR),
            new ResultSetColumn("definition", StringType.VARCHAR),
            new ResultSetColumn("auto_ingest", StringType.VARCHAR),
            new ResultSetColumn("integration", StringType.VARCHAR),
            new ResultSetColumn("error_integration", StringType.VARCHAR),
            new ResultSetColumn("aws_sns_topic_arn", StringType.VARCHAR),
            new ResultSetColumn("status", StringType.VARCHAR)
        );
    }

    /** SQL LIKE match (case-insensitive, {@code %} and {@code _} wildcards) for SHOW … LIKE filters. */
    private static boolean matchesLike(final String value, final String pattern) {
        if (value == null) {
            return false;
        }
        final StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            final char c = pattern.charAt(i);
            if (c == '%') {
                regex.append(".*");
            } else if (c == '_') {
                regex.append('.');
            } else if ("\\.[]{}()*+-?^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return value.matches("(?i)" + regex);
    }

    public ResultSet showSequences(final String schemaName) {
        final String dbName = catalog.getCurrentDatabase();
        final String scName = schemaName != null ? schemaName : catalog.getCurrentSchema();
        if (dbName == null || scName == null) throw new RuntimeException("No database or schema selected");
        final List<Row> rows = new ArrayList<>();
        appendSequenceRows(catalog.getDatabase(dbName).getSchema(scName), rows);
        return new ResultSet(sequenceColumns(), rows);
    }

    /** SHOW SEQUENCES IN DATABASE &lt;db&gt;: sequences across all schemas of the database. */
    /** SHOW SEQUENCES IN ACCOUNT: the sequences of every database, in database order. */
    public ResultSet showSequencesInAccount() {
        List<ResultSetColumn> cols = null;
        final List<Row> rows = new ArrayList<>();
        for (final Database db : catalog.getAllDatabases()) {
            final ResultSet part = showSequencesInDatabase(db.getName());
            cols = part.getColumns();
            rows.addAll(part.getRows());
        }
        return cols != null ? new ResultSet(cols, rows) : showSequences(null);
    }

    public ResultSet showSequencesInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) throw new RuntimeException("No database specified");
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(dbName).getAllSchemas()) {
            appendSequenceRows(schema, rows);
        }
        return new ResultSet(sequenceColumns(), rows);
    }

    private void appendSequenceRows(final Schema schema, final List<Row> rows) {
        final String scName = schema.getName();
        for (final Sequence seq : schema.getSequences()) {
            rows.add(new Row(Arrays.asList(
                null,
                seq.getName(),
                scName,
                "NUMBER",
                seq.getStartValue(),
                1L,
                Long.MAX_VALUE,
                seq.getIncrement(),
                "N",
                seq.getCurrentValueRaw(),
                seq.getOwner(),
                seq.getComment(),
                seq.isOrder() ? "Y" : "N"
            )));
        }
    }

    private List<ResultSetColumn> sequenceColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("data_type", StringType.VARCHAR),
            new ResultSetColumn("start_value", NumericType.BIGINT),
            new ResultSetColumn("minimum", NumericType.BIGINT),
            new ResultSetColumn("maximum", NumericType.BIGINT),
            new ResultSetColumn("increment", NumericType.BIGINT),
            new ResultSetColumn("cycle_option", StringType.VARCHAR),
            new ResultSetColumn("next_value", NumericType.BIGINT),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("ordered", StringType.VARCHAR)
        );
    }

    public ResultSet describeStream(final String streamName) {
        List<Row> rows = new ArrayList<>();
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("property", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR)
        );

        String dbName = catalog.getCurrentDatabase();
        String scName = catalog.getCurrentSchema();
        Schema schema = catalog.getDatabase(dbName).getSchema(scName);
        Stream stream = schema.getStream(streamName);

        rows.add(new Row(Arrays.asList("name", stream.getName())));
        rows.add(new Row(Arrays.asList("table_name", stream.getSourceTableName())));
        rows.add(new Row(Arrays.asList("type", stream.getStreamType().toString())));

        return new ResultSet(columns, rows);
    }

    public ResultSet describeTask(final String taskName) {
        List<Row> rows = new ArrayList<>();
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("property", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR)
        );

        String dbName = catalog.getCurrentDatabase();
        String scName = catalog.getCurrentSchema();
        Schema schema = catalog.getDatabase(dbName).getSchema(scName);
        Task task = schema.getTask(taskName);

        rows.add(new Row(Arrays.asList("name", task.getName())));
        rows.add(new Row(Arrays.asList("schedule", task.getSchedule())));
        rows.add(new Row(Arrays.asList("state", task.getState().toString())));
        rows.add(new Row(Arrays.asList("warehouse", task.getWarehouse())));
        rows.add(new Row(Arrays.asList("definition", task.getSqlStatement())));

        return new ResultSet(columns, rows);
    }

    public ResultSet describePipe(final String pipeName) {
        String dbName = catalog.getCurrentDatabase();
        String scName = catalog.getCurrentSchema();
        Schema schema = catalog.getDatabase(dbName).getSchema(scName);
        Pipe pipe = schema.getPipe(pipeName);

        // Snowflake DESC PIPE returns a single columnar row (the SHOW PIPES attributes), not property/value rows.
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("definition", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("notification_channel", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("integration", StringType.VARCHAR),
            new ResultSetColumn("pattern", StringType.VARCHAR),
            new ResultSetColumn("error_integration", StringType.VARCHAR)
        );
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList(
            pipe.getCreatedTime().toString(),
            pipe.getName(),
            dbName, scName,
            pipe.getCopyStatement(),
            pipe.getOwner(),
            pipe.getNotificationChannel(),
            pipe.getComment(),
            pipe.getIntegration(),
            null,                       // pattern — not modeled
            pipe.getErrorIntegration()
        )));
        return new ResultSet(columns, rows);
    }

    public ResultSet describeSequence(final String sequenceName) {
        List<Row> rows = new ArrayList<>();
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("property", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR)
        );

        String dbName = catalog.getCurrentDatabase();
        String scName = catalog.getCurrentSchema();
        Schema schema = catalog.getDatabase(dbName).getSchema(scName);
        Sequence sequence = schema.getSequence(sequenceName);

        rows.add(new Row(Arrays.asList("name", sequence.getName())));
        rows.add(new Row(Arrays.asList("start_value", String.valueOf(sequence.getStartValue()))));
        rows.add(new Row(Arrays.asList("increment", String.valueOf(sequence.getIncrement()))));
        if (sequence.getComment() != null) {
            rows.add(new Row(Arrays.asList("comment", sequence.getComment())));
        }

        return new ResultSet(columns, rows);
    }

    public ResultSet showDynamicTables(final String schemaName) {
        final String dbName = catalog.getCurrentDatabase();
        final String scName = schemaName != null ? schemaName : catalog.getCurrentSchema();
        if (dbName == null || scName == null) throw new RuntimeException("No database or schema selected");
        final List<Row> rows = new ArrayList<>();
        appendDynamicTableRows(dbName, catalog.getDatabase(dbName).getSchema(scName), rows);
        return new ResultSet(dynamicTableColumns(), rows);
    }

    /** SHOW DYNAMIC TABLES IN DATABASE &lt;db&gt;: dynamic tables across all schemas of the database. */
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
                dt.getCreatedTime().toString(),
                dt.getName(), dbName, scName,
                null, 0L, 0L,
                dt.getOwner(),
                dt.getTargetLag(),
                dt.getRefreshMode().name(),
                null, "OFF", "OFF",
                dt.getSchedulingState(),
                null, "N", "N",
                dt.getLastRefreshedTime() != null ? dt.getLastRefreshedTime().toString() : null,
                dt.getWarehouse(),
                dt.getQuery(),
                dt.getComment()
            )));
        }
    }

    private List<ResultSetColumn> dynamicTableColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
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
            new ResultSetColumn("compaction", StringType.VARCHAR),
            new ResultSetColumn("enable_schema_evolution", StringType.VARCHAR),
            new ResultSetColumn("scheduling_state", StringType.VARCHAR),
            new ResultSetColumn("last_suspended_on", StringType.VARCHAR),
            new ResultSetColumn("is_clone", StringType.VARCHAR),
            new ResultSetColumn("is_replica", StringType.VARCHAR),
            new ResultSetColumn("data_timestamp", StringType.VARCHAR),
            new ResultSetColumn("warehouse", StringType.VARCHAR),
            new ResultSetColumn("query", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR)
        );
    }

    public ResultSet describeDynamicTable(final String tableName) {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("property", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR)
        );
        String dbName = catalog.getCurrentDatabase();
        String scName = catalog.getCurrentSchema();
        DynamicTable dt =
            catalog.getDatabase(dbName).getSchema(scName).getDynamicTable(tableName.toUpperCase());
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList("name", dt.getName())));
        rows.add(new Row(Arrays.asList("target_lag", dt.getTargetLag())));
        rows.add(new Row(Arrays.asList("warehouse", dt.getWarehouse())));
        rows.add(new Row(Arrays.asList("refresh_mode", dt.getRefreshMode().name())));
        rows.add(new Row(Arrays.asList("initialize", dt.getInitialize().name())));
        rows.add(new Row(Arrays.asList("scheduling_state", dt.getSchedulingState())));
        rows.add(new Row(Arrays.asList("data_retention_days", String.valueOf(dt.getDataRetentionDays()))));
        rows.add(new Row(Arrays.asList("query", dt.getQuery())));
        if (dt.getComment() != null) rows.add(new Row(Arrays.asList("comment", dt.getComment())));
        return new ResultSet(columns, rows);
    }
}
