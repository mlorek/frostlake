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
import dev.frostlake.metastore.InstanceFamilies;
import dev.frostlake.metastore.model.ComputePool;
import dev.frostlake.metastore.model.ComputePoolState;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.metastore.model.WarehouseState;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.StringType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * SHOW / DESCRIBE handlers for compute and storage infrastructure: warehouses and stages.
 * Extracted from {@link ShowCommandExecutor}, which delegates here.
 */
final class ShowInfraExecutor {

    /** The compute family a standard warehouse runs on, as live reports it. */
    private static final String RESOURCE_CONSTRAINT = "STANDARD_GEN_2";

    private final Catalog catalog;

    ShowInfraExecutor(final Catalog catalog) {
        this.catalog = catalog;
    }

    /**
     * The warehouse listing live emits, column for column. The warehouse-level parameters
     * (MAX_CONCURRENCY_LEVEL, the two statement timeouts) are deliberately absent: live reports
     * those through SHOW PARAMETERS IN WAREHOUSE, not here.
     */
    public ResultSet showWarehouses() {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("state", StringType.VARCHAR),
            new ResultSetColumn("type", StringType.VARCHAR),
            new ResultSetColumn("size", StringType.VARCHAR),
            new ResultSetColumn("min_cluster_count", NumericType.INTEGER),
            new ResultSetColumn("max_cluster_count", NumericType.INTEGER),
            new ResultSetColumn("started_clusters", NumericType.INTEGER),
            new ResultSetColumn("running", NumericType.INTEGER),
            new ResultSetColumn("queued", NumericType.INTEGER),
            new ResultSetColumn("is_default", StringType.VARCHAR),
            new ResultSetColumn("is_current", StringType.VARCHAR),
            new ResultSetColumn("auto_suspend", NumericType.INTEGER),
            new ResultSetColumn("auto_resume", StringType.VARCHAR),
            new ResultSetColumn("available", StringType.VARCHAR),
            new ResultSetColumn("provisioning", StringType.VARCHAR),
            new ResultSetColumn("quiescing", StringType.VARCHAR),
            new ResultSetColumn("other", StringType.VARCHAR),
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("resumed_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("updated_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("enable_query_acceleration", StringType.VARCHAR),
            new ResultSetColumn("query_acceleration_max_scale_factor", NumericType.INTEGER),
            new ResultSetColumn("resource_monitor", StringType.VARCHAR),
            new ResultSetColumn("actives", NumericType.INTEGER),
            new ResultSetColumn("pendings", NumericType.INTEGER),
            new ResultSetColumn("failed", NumericType.INTEGER),
            new ResultSetColumn("suspended", NumericType.INTEGER),
            new ResultSetColumn("uuid", StringType.VARCHAR),
            new ResultSetColumn("scaling_policy", StringType.VARCHAR),
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("resource_constraint", StringType.VARCHAR),
            new ResultSetColumn("generation", StringType.VARCHAR),
            new ResultSetColumn("query_throughput_multiplier", StringType.VARCHAR),
            new ResultSetColumn("max_query_performance_level", StringType.VARCHAR),
            new ResultSetColumn("disabled_reasons", StringType.VARCHAR),
            new ResultSetColumn("tables", StringType.VARCHAR)
        );
        final String currentWarehouse = catalog.getCurrentWarehouse();
        final List<Row> rows = new ArrayList<>();
        for (final Warehouse wh : catalog.getAllWarehouses()) {
            final LocalDateTime created = ShowResultHelpers.createdOn(wh.getCreatedAt());
            final LocalDateTime lastChange = wh.getLastStateChange() != null
                ? ShowResultHelpers.createdOn(wh.getLastStateChange()) : created;
            final boolean running = wh.getState() == WarehouseState.STARTED;
            rows.add(new Row(Arrays.asList(
                wh.getName(),
                wh.getState().toString(),
                wh.getWarehouseType(),
                // Live spells the size the way CREATE WAREHOUSE displays it: X-Small, Small, X-Large.
                wh.getSize().getDisplayName(),
                (long) wh.getMinClusterCount(),
                (long) wh.getMaxClusterCount(),
                running ? (long) wh.getMinClusterCount() : 0L,
                0L, 0L,
                "N",
                wh.getName().equalsIgnoreCase(currentWarehouse) ? "Y" : "N",
                (long) wh.getAutoSuspendSeconds(),
                String.valueOf(wh.isAutoResume()),
                // The four cluster-health cells are empty strings on an idle warehouse.
                "", "", "", "",
                created, lastChange, lastChange,
                wh.getOwner(),
                ShowResultHelpers.text(wh.getComment()),
                String.valueOf(wh.isEnableQueryAcceleration()),
                (long) wh.getQueryAccelerationMaxScaleFactor(),
                wh.getResourceMonitor() != null ? wh.getResourceMonitor() : "null",
                running ? 1L : 0L,
                0L, 0L,
                running ? 0L : 1L,
                UUID.nameUUIDFromBytes(wh.getName().getBytes(StandardCharsets.UTF_8)).toString(),
                wh.getScalingPolicy().toString(),
                ShowResultHelpers.OWNER_ROLE_TYPE,
                RESOURCE_CONSTRAINT,
                wh.getGeneration(),
                null, null, null, null
            )));
        }
        return new ResultSet(columns, rows);
    }

    public ResultSet showStages(final String schemaName) {
        final String dbName = ShowResultHelpers.scopeDatabase(catalog, schemaName);
        final String scName = ShowResultHelpers.scopeSchemaName(catalog, schemaName);
        if (dbName == null || scName == null) return new ResultSet(stageColumns(), new ArrayList<>());
        final List<Row> rows = new ArrayList<>();
        appendStageRows(dbName, ShowResultHelpers.scopeSchemaReportedGenerically(catalog, dbName, scName), rows);
        return new ResultSet(stageColumns(), rows);
    }

    /** SHOW STAGES IN DATABASE &lt;db&gt;: stages across all schemas of the database. */
    /** SHOW STAGES IN ACCOUNT: every database's stages, in database order. */
    public ResultSet showStagesInAccount() {
        final ResultSet across = ShowResultHelpers.acrossAllDatabases(catalog, new DatabaseScopedListing() {
            @Override
            public ResultSet listIn(final String databaseName) {
                return showStagesInDatabase(databaseName);
            }
        });
        return across != null ? across : showStages(null);
    }

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
                ? "EXTERNAL" : "INTERNAL";
            final String cloud = url.startsWith("s3://") ? "aws" : url.startsWith("azure://") ? "azure"
                : url.startsWith("gcs://") ? "gcp" : null;
            rows.add(new Row(Arrays.asList(
                ShowResultHelpers.createdOn(stage.getCreatedAt()),
                stage.getName(),
                dbName, scName,
                url,
                "N", "N",
                stage.getOwner(),
                ShowResultHelpers.text(stage.getComment()),
                null, type, cloud, null, null, null,
                ShowResultHelpers.OWNER_ROLE_TYPE, "N"
            )));
        }
    }

    private List<ResultSetColumn> stageColumns() {
        return Arrays.asList(
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
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
            new ResultSetColumn("owner_role_type", StringType.VARCHAR),
            new ResultSetColumn("directory_enabled", StringType.VARCHAR)
        );
    }

    /**
     * DESCRIBE WAREHOUSE returns three columns on a real account — the warehouse's identity, not its
     * settings. Those are read through SHOW WAREHOUSES and SHOW PARAMETERS IN WAREHOUSE.
     */
    /**
     * SHOW COMPUTE POOLS — a real account's 21 columns in its order. Number columns render as their
     * numerals, auto_resume and is_exclusive as lower-case true/false, and comment stays NULL when
     * unset (unlike most SHOW text, which spells absence as an empty string). A pool that has never
     * resumed reports the epoch in resumed_on, exactly as a real account does; target_nodes is the
     * node count being asked for — MIN_NODES while STARTING, zero suspended.
     */
    public ResultSet showComputePools() {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("state", StringType.VARCHAR),
            new ResultSetColumn("min_nodes", NumericType.NUMBER),
            new ResultSetColumn("max_nodes", NumericType.NUMBER),
            new ResultSetColumn("instance_family", StringType.VARCHAR),
            new ResultSetColumn("num_services", NumericType.NUMBER),
            new ResultSetColumn("num_jobs", NumericType.NUMBER),
            new ResultSetColumn("auto_suspend_secs", NumericType.NUMBER),
            new ResultSetColumn("auto_resume", StringType.VARCHAR),
            new ResultSetColumn("active_nodes", NumericType.NUMBER),
            new ResultSetColumn("idle_nodes", NumericType.NUMBER),
            new ResultSetColumn("target_nodes", NumericType.NUMBER),
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("resumed_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("updated_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("is_exclusive", StringType.VARCHAR),
            new ResultSetColumn("application", StringType.VARCHAR),
            new ResultSetColumn("placement_group", StringType.VARCHAR),
            new ResultSetColumn("backup_instance_families", StringType.VARCHAR)
        );
        final List<Row> rows = new ArrayList<>();
        for (final ComputePool pool : catalog.getComputePools()) {
            rows.add(new Row(computePoolRow(pool, false)));
        }
        return new ResultSet(columns, rows);
    }

    /**
     * DESCRIBE COMPUTE POOL — the SHOW row widened by error_code and status_message (both empty),
     * inserted after application, a real account's 23-column shape.
     */
    public ResultSet describeComputePool(final String poolName) {
        final ComputePool pool = catalog.getComputePool(poolName);
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("state", StringType.VARCHAR),
            new ResultSetColumn("min_nodes", NumericType.NUMBER),
            new ResultSetColumn("max_nodes", NumericType.NUMBER),
            new ResultSetColumn("instance_family", StringType.VARCHAR),
            new ResultSetColumn("num_services", NumericType.NUMBER),
            new ResultSetColumn("num_jobs", NumericType.NUMBER),
            new ResultSetColumn("auto_suspend_secs", NumericType.NUMBER),
            new ResultSetColumn("auto_resume", StringType.VARCHAR),
            new ResultSetColumn("active_nodes", NumericType.NUMBER),
            new ResultSetColumn("idle_nodes", NumericType.NUMBER),
            new ResultSetColumn("target_nodes", NumericType.NUMBER),
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("resumed_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("updated_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("is_exclusive", StringType.VARCHAR),
            new ResultSetColumn("application", StringType.VARCHAR),
            new ResultSetColumn("error_code", StringType.VARCHAR),
            new ResultSetColumn("status_message", StringType.VARCHAR),
            new ResultSetColumn("placement_group", StringType.VARCHAR),
            new ResultSetColumn("backup_instance_families", StringType.VARCHAR)
        );
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(computePoolRow(pool, true)));
        return new ResultSet(columns, rows);
    }

    /** SHOW COMPUTE POOL INSTANCE FAMILIES — the account catalog, in its own (unsorted) order. */
    public ResultSet showComputePoolInstanceFamilies() {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("description", StringType.VARCHAR),
            new ResultSetColumn("vcpu", NumericType.NUMBER),
            new ResultSetColumn("memory_gib", NumericType.NUMBER),
            // storage_gib is VARCHAR even though every row's value is 93.13 — the exception that
            // makes the numeric columns a declared list rather than "whatever parses as a number".
            new ResultSetColumn("storage_gib", StringType.VARCHAR),
            new ResultSetColumn("gpu", StringType.VARCHAR),
            new ResultSetColumn("gpu_count", NumericType.NUMBER),
            new ResultSetColumn("gpu_memory_gib", NumericType.NUMBER),
            new ResultSetColumn("current_node_usage", NumericType.NUMBER),
            new ResultSetColumn("message", StringType.VARCHAR)
        );
        final List<Row> rows = new ArrayList<>();
        for (final String[] family : InstanceFamilies.rows()) {
            final List<Object> values = new ArrayList<>();
            // The catalog is transcribed as text, one cell per printed column; the DECLARED type
            // decides what each cell becomes, so the two can never drift apart.
            for (int i = 0; i < family.length; i++) {
                if (columns.get(i).getDataType() instanceof NumericType) {
                    values.add(Long.valueOf(family[i]));
                } else {
                    values.add(family[i]);
                }
            }
            rows.add(new Row(values));
        }
        return new ResultSet(columns, rows);
    }

    /** One pool's SHOW/DESCRIBE values; DESCRIBE inserts error_code and status_message. */
    private List<Object> computePoolRow(final ComputePool pool, final boolean describeShape) {
        final List<Object> values = new ArrayList<>();
        values.add(pool.getName());
        values.add(pool.getState().name());
        // The node and job counters are NUMBER, not text. auto_resume and is_exclusive beside them
        // ARE text ("true"/"false"), which is why the two groups are not treated alike.
        values.add(Long.valueOf(pool.getMinNodes()));
        values.add(Long.valueOf(pool.getMaxNodes()));
        values.add(pool.getInstanceFamily());
        values.add(Long.valueOf(0L));
        values.add(Long.valueOf(0L));
        values.add(Long.valueOf(pool.getAutoSuspendSecs()));
        values.add(pool.isAutoResume() ? "true" : "false");
        values.add(Long.valueOf(0L));
        values.add(Long.valueOf(0L));
        values.add(Long.valueOf(pool.getState() == ComputePoolState.STARTING
            ? pool.getMinNodes() : 0));
        values.add(ShowResultHelpers.createdOn(pool.getCreatedTime()));
        values.add(ShowResultHelpers.createdOn(
            pool.getResumedOn() == null ? Instant.EPOCH : pool.getResumedOn()));
        values.add(ShowResultHelpers.createdOn(pool.getUpdatedOn()));
        values.add(pool.getOwner());
        values.add(pool.getComment());
        values.add(pool.getApplication() != null ? "true" : "false");
        values.add(pool.getApplication());
        if (describeShape) {
            values.add("");
            values.add("");
        }
        values.add(pool.getPlacementGroup());
        values.add(joinedBackupFamilies(pool));
        return values;
    }

    private String joinedBackupFamilies(final ComputePool pool) {
        final StringBuilder joined = new StringBuilder();
        for (final String family : pool.getBackupInstanceFamilies()) {
            if (joined.length() > 0) {
                joined.append(',');
            }
            joined.append(family);
        }
        return joined.toString();
    }

    public ResultSet describeWarehouse(final String warehouseName) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("created_on", DateTimeType.TIMESTAMP_LTZ),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("kind", StringType.VARCHAR)
        );
        final Warehouse wh = catalog.getWarehouse(warehouseName);
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.asList(
            ShowResultHelpers.createdOn(wh.getCreatedAt()), wh.getName(), "WAREHOUSE")));
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
