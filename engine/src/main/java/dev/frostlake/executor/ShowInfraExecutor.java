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
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * SHOW / DESCRIBE handlers for compute and storage infrastructure: warehouses and stages.
 * Extracted from {@link ShowCommandExecutor}, which delegates here.
 */
final class ShowInfraExecutor {

    private final Catalog catalog;

    ShowInfraExecutor(final Catalog catalog) {
        this.catalog = catalog;
    }

    public ResultSet showWarehouses() {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("state", StringType.VARCHAR),
            new ResultSetColumn("type", StringType.VARCHAR),
            new ResultSetColumn("size", StringType.VARCHAR),
            new ResultSetColumn("max_cluster_count", NumericType.INTEGER),
            new ResultSetColumn("min_cluster_count", NumericType.INTEGER),
            new ResultSetColumn("scaling_policy", StringType.VARCHAR),
            new ResultSetColumn("auto_suspend", NumericType.INTEGER),
            new ResultSetColumn("auto_resume", StringType.VARCHAR),
            new ResultSetColumn("available", StringType.VARCHAR),
            new ResultSetColumn("provisioning", StringType.VARCHAR),
            new ResultSetColumn("quiescing", StringType.VARCHAR),
            new ResultSetColumn("other", StringType.VARCHAR),
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("resumed_on", StringType.VARCHAR),
            new ResultSetColumn("updated_on", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("resource_monitor", StringType.VARCHAR),
            new ResultSetColumn("actives", NumericType.INTEGER),
            new ResultSetColumn("pendings", NumericType.INTEGER),
            new ResultSetColumn("failed", NumericType.INTEGER),
            new ResultSetColumn("suspended", NumericType.INTEGER),
            new ResultSetColumn("uuid", StringType.VARCHAR),
            new ResultSetColumn("enable_query_acceleration", StringType.VARCHAR),
            new ResultSetColumn("query_acceleration_max_scale_factor", NumericType.INTEGER),
            new ResultSetColumn("max_concurrency_level", NumericType.INTEGER),
            new ResultSetColumn("statement_queued_timeout_in_seconds", NumericType.INTEGER),
            new ResultSetColumn("statement_timeout_in_seconds", NumericType.INTEGER)
        );
        List<Row> rows = new ArrayList<>();
        for (final Warehouse wh : catalog.getAllWarehouses()) {
            String ts = wh.getCreatedAt() != null ? wh.getCreatedAt().toString() : null;
            String lastChange = wh.getLastStateChange() != null ? wh.getLastStateChange().toString() : ts;
            rows.add(new Row(Arrays.asList(
                wh.getName(),
                wh.getState().toString(),
                wh.getWarehouseType(),
                wh.getSize().toString(),
                (long) wh.getMaxClusterCount(),
                (long) wh.getMinClusterCount(),
                wh.getScalingPolicy().toString(),
                (long) wh.getAutoSuspendSeconds(),
                String.valueOf(wh.isAutoResume()),
                "0", "0", "0", "0",
                ts, lastChange, lastChange,
                wh.getOwner(),
                wh.getComment(),
                wh.getResourceMonitor() != null ? wh.getResourceMonitor() : "null",
                0L, 0L, 0L, 0L,
                UUID.nameUUIDFromBytes(wh.getName().getBytes()).toString(),
                String.valueOf(wh.isEnableQueryAcceleration()),
                (long) wh.getQueryAccelerationMaxScaleFactor(),
                (long) wh.getMaxConcurrencyLevel(),
                (long) wh.getStatementQueuedTimeoutSeconds(),
                (long) wh.getStatementTimeoutSeconds()
            )));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showStages(final String schemaName) {
        final String dbName = catalog.getCurrentDatabase();
        final String scName = schemaName != null ? schemaName : catalog.getCurrentSchema();
        if (dbName == null || scName == null) return new ResultSet(stageColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        appendStageRows(dbName, catalog.getDatabase(dbName).getSchema(scName), rows);
        return new ResultSet(stageColumns(), rows);
    }

    /** SHOW STAGES IN DATABASE &lt;db&gt;: stages across all schemas of the database. */
    public ResultSet showStagesInDatabase(final String databaseName) {
        final String dbName = databaseName != null ? databaseName : catalog.getCurrentDatabase();
        if (dbName == null) return new ResultSet(stageColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : catalog.getDatabase(dbName).getAllSchemas()) {
            appendStageRows(dbName, schema, rows);
        }
        return new ResultSet(stageColumns(), rows);
    }

    private void appendStageRows(final String dbName, final Schema schema, final List<Row> rows) {
        final String scName = schema.getName();
        for (final Stage stage : schema.getStages()) {
            final String url = stage.getUrl() != null ? stage.getUrl() : "";
            final String type = url.startsWith("s3://") || url.startsWith("azure://") || url.startsWith("gcs://")
                ? "External Stage" : "Internal Named Stage";
            final String cloud = url.startsWith("s3://") ? "aws" : url.startsWith("azure://") ? "azure"
                : url.startsWith("gcs://") ? "gcp" : null;
            rows.add(new Row(Arrays.asList(
                stage.getCreatedAt().toString(),
                stage.getName(),
                dbName, scName,
                url,
                "N", "N",
                stage.getOwner(),
                stage.getComment(),
                null, type, cloud, null, null, null, "N"
            )));
        }
    }

    private List<ResultSetColumn> stageColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("url", StringType.VARCHAR),
            new ResultSetColumn("has_credentials", StringType.VARCHAR),
            new ResultSetColumn("has_encryption_key", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("region", StringType.VARCHAR),
            new ResultSetColumn("type", StringType.VARCHAR),
            new ResultSetColumn("cloud", StringType.VARCHAR),
            new ResultSetColumn("notification_channel", StringType.VARCHAR),
            new ResultSetColumn("storage_integration", StringType.VARCHAR),
            new ResultSetColumn("endpoint", StringType.VARCHAR),
            new ResultSetColumn("directory_enabled", StringType.VARCHAR)
        );
    }

    public ResultSet describeWarehouse(final String warehouseName) {
        List<Row> rows = new ArrayList<>();
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("property", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR)
        );

        Warehouse wh = catalog.getWarehouse(warehouseName);
        rows.add(new Row(Arrays.asList("name", wh.getName())));
        rows.add(new Row(Arrays.asList("size", wh.getSize().toString())));
        rows.add(new Row(Arrays.asList("state", wh.getState().toString())));
        rows.add(new Row(Arrays.asList("auto_suspend", String.valueOf(wh.getAutoSuspendSeconds()))));
        rows.add(new Row(Arrays.asList("auto_resume", String.valueOf(wh.isAutoResume()))));

        return new ResultSet(columns, rows);
    }

    public ResultSet describeStage(final String stageName) {
        List<Row> rows = new ArrayList<>();
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("property", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR)
        );

        Stage stage = catalog.getStage(stageName);
        rows.add(new Row(Arrays.asList("name", stage.getName())));
        rows.add(new Row(Arrays.asList("type", stage.getType().toString())));
        rows.add(new Row(Arrays.asList("url", stage.getUrl())));
        rows.add(new Row(Arrays.asList("file_format", stage.getFileFormat())));
        rows.add(new Row(Arrays.asList("encryption", String.valueOf(stage.isEncryption()))));

        return new ResultSet(columns, rows);
    }
}
