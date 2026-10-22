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

package dev.frostlake.functions.table;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.DataMetricFunctions;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.DataMetricAttachment;
import dev.frostlake.metastore.model.DataMetricAttachments;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@code INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES} — the DATA METRIC FUNCTIONS attached to
 * an object, or the objects one metric is attached to.
 *
 * <p>TWO CELLS ARE NOT REPRODUCIBLE and read null here rather than being invented: {@code REF_ID} is
 * a per-attachment UUID, and live's {@code REF_ARGUMENTS} embeds the account's internal COLUMN ID
 * ({@code {"domain":"COLUMN","id":"150561","name":"ID"}}). Frostlake writes the same array WITHOUT
 * the id — a field omitted, never a value made up. Everything else is measured and exact, including
 * the mixed-case {@code Table} domain, the cron rendering of DATA_METRIC_SCHEDULE, and the PROPERTIES
 * blob.
 */
public class DataMetricReferencesFunction extends TableFunction {

    /** Live's column order. */
    private static final String[] COLUMN_NAMES = {
        "METRIC_DATABASE_NAME", "METRIC_SCHEMA_NAME", "METRIC_NAME", "METRIC_SIGNATURE",
        "METRIC_DATA_TYPE", "REF_ENTITY_DATABASE_NAME", "REF_ENTITY_SCHEMA_NAME", "REF_ENTITY_NAME",
        "REF_ENTITY_DOMAIN", "REF_ARGUMENTS", "REF_ID", "SCHEDULE", "SCHEDULE_STATUS",
        "DATA_QUALITY_NOTIFICATION_STATUS", "ANOMALY_DETECTION_STATUS",
        "ANOMALY_DETECTION_SENSITIVITY_LEVEL", "USE_ROLE", "LEVEL", "EXCLUDE_TABLE_TYPES", "PROPERTIES"
    };

    /** Every system metric answers a NUMBER, and the domain is spelled in mixed case. */
    private static final String METRIC_DATA_TYPE = "NUMBER(38,0)";
    private static final String DOMAIN = "Table";
    private static final String VIEW_DOMAIN = "View";

    /** How a schedule given as a cron expression is written: the expression and its time zone follow. */
    private static final String CRON_PREFIX = "USING CRON ";

    /** The blob live builds from the same defaults, keys in alphabetical order. */
    private static final String PROPERTIES = "{\n"
        + "  \"anomaly_detection_sensitivity_level\": \"MEDIUM\",\n"
        + "  \"anomaly_detection_status\": \"DISABLED\",\n"
        + "  \"data_quality_notification_status\": \"DISABLED\",\n"
        + "  \"exclude_table_types\": null,\n"
        + "  \"filter\": null,\n"
        + "  \"group_limit\": null,\n"
        + "  \"level\": \"Table\",\n"
        + "  \"use_role\": null,\n"
        + "  \"within_group\": null\n"
        + "}";

    /** The table-like kinds REF_ENTITY_DOMAIN takes, each with the noun a miss is reported in. */
    private static final Map<String, String> RELATION_NOUNS = relationNouns();

    /** The table-like kinds whose miss echoes a bare name bare; the others spell the path in full. */
    private static final Set<String> BARE_NAMED_DOMAINS = new HashSet<String>(Arrays.asList("TABLE", "VIEW",
        "MATERIALIZED VIEW"));

    /**
     * The kinds of object the account knows and this argument takes no value of, each refused by its name with an
     * underscore between its words; any other word is an unknown domain, as written (live-verified word by word).
     */
    private static final Set<String> REFUSED_DOMAINS = new HashSet<String>(Arrays.asList("ACCOUNT",
        "AGGREGATION POLICY", "ALERT", "APPLICATION", "APPLICATION PACKAGE", "APPLICATION ROLE", "APPLICATION SERVICE",
        "APPLICATION SPECIFICATION", "ARTIFACT REPOSITORY", "AUTHENTICATION POLICY", "BUDGET", "CATALOG", "CHANNEL",
        "CLASS", "COLUMN", "COMPUTE POOL", "CONNECTION", "CONSTRAINT", "CONTACT", "CORTEX AGENT",
        "CORTEX SEARCH SERVICE", "DATA EXCHANGE", "DATA METRIC FUNCTION", "DATABASE", "DATABASE ROLE", "DATASET",
        "DBT PROJECT", "DCM PROJECT", "DELEGATED AUTHORIZATION", "DEPLOYMENT", "EVENT ROUTING TABLE", "EXPERIMENT",
        "EXTERNAL VOLUME", "FAILOVER GROUP", "FEATURE POLICY", "FILE", "FILE FORMAT", "FUNCTION", "GIT BRANCH",
        "GIT REPOSITORY", "GIT TAG", "GLOBAL ACCOUNT", "IMAGE REPOSITORY", "INSTANCE", "INTEGRATION",
        "INTERACTIVE TABLE", "JOB", "JOIN POLICY", "LOCK", "MAINTENANCE POLICY", "MANAGED ACCOUNT", "MASKING POLICY",
        "MFA METHOD", "MODEL", "MULTI PARTY APPROVAL POLICY", "NETWORK POLICY", "NETWORK RULE", "NOTEBOOK",
        "NOTEBOOK PROJECT", "NOTIFICATION", "OBJECT", "ONLINE FEATURE TABLE", "ORGANIZATION", "ORGANIZATION PROFILE",
        "ORGANIZATION USER", "ORGANIZATION USER GROUP", "PACKAGES POLICY", "PARAMETER", "PASSWORD POLICY", "PIPE",
        "POLICY", "POSTGRES", "PRIVACY POLICY", "PROCEDURE", "PROJECTION POLICY", "PROVISIONED THROUGHPUT",
        "REPLICATION GROUP", "RESOURCE MONITOR", "RESULT", "ROLE", "ROW ACCESS POLICY", "SECRET", "SEMANTIC VIEW",
        "SEQUENCE", "SERVICE", "SERVICE CONTAINER", "SERVICE VOLUME", "SESSION", "SESSION POLICY",
        "SNOWFLAKE INTELLIGENCE", "STAGE", "STORAGE LIFECYCLE POLICY", "STREAM", "STREAMLIT", "TABLE FUNCTION", "TAG",
        "TASK", "TRANSACTION", "USER", "VARIABLE", "VOLUME", "WAREHOUSE", "WORKSPACE"));

    private final Catalog catalog;

    private static Map<String, String> relationNouns() {
        final Map<String, String> nouns = new HashMap<>();
        nouns.put("TABLE", "Table");
        nouns.put("VIEW", "View");
        nouns.put("MATERIALIZED VIEW", "Materialized view");
        nouns.put("EXTERNAL TABLE", "External table");
        nouns.put("DYNAMIC TABLE", "Dynamic table");
        nouns.put("ICEBERG TABLE", "Iceberg table");
        nouns.put("EVENT TABLE", "Event table");
        return nouns;
    }

    public DataMetricReferencesFunction(final Catalog catalog) {
        super("DATA_METRIC_FUNCTION_REFERENCES");
        this.catalog = catalog;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Judged in execute, where the argument combination is seen as a whole.
    }

    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        // One argument written without its name is the metric's (live-verified).
        if (positionalArgs == null || positionalArgs.size() != 1 || positionalArgs.get(0) == null) {
            throw new RuntimeException(argumentRequirement());
        }
        return metricReferences(positionalArgs.get(0));
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        final Object metricName = value(namedArgs, "METRIC_NAME");
        final String entityName = argument(namedArgs, "REF_ENTITY_NAME");
        final String entityDomain = argument(namedArgs, "REF_ENTITY_DOMAIN");
        // The metric, or the entity and its domain: a metric beside either of the other two is neither.
        if (metricName != null ? entityName != null || entityDomain != null
                : entityName == null || entityDomain == null) {
            throw new RuntimeException(argumentRequirement());
        }
        if (metricName != null) {
            return metricReferences(metricName);
        }
        final List<Row> rows = new ArrayList<>();
        appendEntityRows(entityName, entityDomain, rows);
        return new ResultSet(columns(), rows);
    }

    /**
     * The attachments of one metric across the account. The metric must be named in full, by the path of a system
     * metric as an identifier path spells it; a name that is none is refused as the argument wrote it, without a
     * privilege hint (live-verified).
     */
    private ResultSet metricReferences(final Object metric) {
        final String metricName = metric.toString();
        // A part written in quotes names the metric only in the case the account holds it in.
        final String[] parts = SqlIdentifiers.canonicalTextParts(metricName);
        final String path = QualifiedName.join(parts);
        if (!path.equals(path.toUpperCase(Locale.ROOT)) || !DataMetricFunctions.exists(path)) {
            throw new RuntimeException(SqlCompilationError.of("Data Metric Function '" + metricName
                + "' does not exist or not authorized."));
        }
        final List<Row> rows = new ArrayList<>();
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Table table : schema.getTables()) {
                    appendRows(schema, table.getName(), DOMAIN, table.dataMetricAttachments(), table,
                        parts[parts.length - 1], rows);
                }
                for (final View view : schema.getViews()) {
                    appendRows(schema, view.getName(), VIEW_DOMAIN, view.dataMetricAttachments(), columnsOf(view),
                        parts[parts.length - 1], rows);
                }
            }
        }
        return new ResultSet(columns(), rows);
    }

    /**
     * Adds the attachments of the entity a name and a domain name; a schema carries none here. The domain is judged
     * first, as written: a table-like kind looks the name up as any table or view, a view's rows naming the View
     * domain whichever kind was asked; a schema is looked up as one; another kind of object is no value for the
     * argument; and a word that names no kind is an unknown domain. A name that resolves to nothing is refused in the
     * domain's own noun — by the bare name where a table, a view or a materialized view was named bare, by the full
     * path otherwise (live-verified).
     */
    private void appendEntityRows(final String entityName, final String entityDomain, final List<Row> rows) {
        // A kind of two words may be written with an underscore between them.
        final String domain = entityDomain.toUpperCase(Locale.ROOT).replace('_', ' ');
        if (REFUSED_DOMAINS.contains(domain)) {
            throw new RuntimeException(SqlCompilationError.inline("Invalid value '" + domain.replace(' ', '_')
                + "' for argument 'ref_entity_domain' for function "
                + " information_schema.data_metric_function_references."));
        }
        final String[] parts = SqlIdentifiers.canonicalTextParts(entityName);
        if ("SCHEMA".equals(domain)) {
            if (parts.length > 2) {
                throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
            }
            catalog.resolveSchema(QualifiedName.of(parts));
            return;
        }
        if (!RELATION_NOUNS.containsKey(domain)) {
            throw new RuntimeException("Unknown domain: " + entityDomain + ".");
        }
        if (parts.length > 3) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        final Table table = schema.tableExact(name);
        if (table != null) {
            appendRows(schema, table.getName(), DOMAIN, table.dataMetricAttachments(), table, null, rows);
            return;
        }
        if (schema.hasView(name) && schema.getView(name).getName().equals(name)) {
            final View view = schema.getView(name);
            appendRows(schema, view.getName(), VIEW_DOMAIN, view.dataMetricAttachments(), columnsOf(view), null,
                rows);
            return;
        }
        final boolean bare = parts.length == 1 && BARE_NAMED_DOMAINS.contains(domain);
        throw new RuntimeException(SqlCompilationError.doesNotExist(RELATION_NOUNS.get(domain),
            bare ? name : QualifiedName.join(schema.getDatabaseName(), schema.getName(), name)));
    }

    private String argumentRequirement() {
        return SqlCompilationError.PREFIX + " function"
            + " 'information_schema.data_metric_function_references' expects argument"
            + " (metric_name=>'metricName') or (ref_entity_name=>'name', ref_entity_domain=>'domain').";
    }

    /**
     * One row per attachment of a table or a view, ordered by metric name and then by signature, as live orders
     * them. A view's rows differ from a table's in their domain alone: their level is the table's (live-verified).
     */
    private void appendRows(final Schema schema, final String entityName, final String domain,
                            final DataMetricAttachments attachments, final Table columns, final String onlyMetric,
                            final List<Row> rows) {
        final Map<String, Row> ordered = new TreeMap<>();
        for (final DataMetricAttachment attachment : attachments.list()) {
            final String metric = DataMetricFunctions.bareName(attachment.getMetricName());
            if (onlyMetric != null && !onlyMetric.equalsIgnoreCase(metric)) {
                continue;
            }
            final String signature = signatureOf(columns, attachment);
            ordered.put(metric + "\0" + signature, new Row(Arrays.asList(
                "SNOWFLAKE", "CORE", metric, signature, METRIC_DATA_TYPE,
                schema.getDatabaseName(), schema.getName(), entityName, domain,
                renderArguments(attachment.getColumns()),
                // The account's per-attachment UUID: nothing here can stand in for it.
                null,
                schedule(attachments.getSchedule()), attachment.isSuspended() ? "SUSPENDED_BY_USER_ACTION" : "STARTED",
                "DISABLED", "DISABLED", "MEDIUM", "", "Table", "", PROPERTIES)));
        }
        rows.addAll(ordered.values());
    }

    /** A view's columns as it resolved them, held as a table's are so a metric's overload can be read off them. */
    private static Table columnsOf(final View view) {
        return new Table(view.getName(), view.hasResolvedColumns() ? view.getResolvedColumns()
            : new ArrayList<TableColumn>(), false);
    }

    /**
     * The overload the attachment resolved to: {@code TABLE(NUMBER)}, {@code TABLE(VARCHAR(n))},
     * {@code TABLE(DATE)} … and EMPTY for a table-level metric, which takes no column.
     */
    private String signatureOf(final Table table, final DataMetricAttachment attachment) {
        if (attachment.getColumns().isEmpty()) {
            return "";
        }
        final DataType type = table.getColumn(attachment.getColumns().get(0)).getDataType();
        final String canonical = SqlTypeNames.canonical(type);
        if (canonical.startsWith("VARCHAR")) {
            // The string overload is declared with its length; every other one is bare.
            return "TABLE(" + canonical + ")";
        }
        final int paren = canonical.indexOf('(');
        return "TABLE(" + (paren < 0 ? canonical : canonical.substring(0, paren)) + ")";
    }

    /** Live renders the attachment's columns as a JSON array — minus the internal id, here. */
    private String renderArguments(final List<String> columns) {
        if (columns.isEmpty()) {
            return "[]";
        }
        final StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < columns.size(); i++) {
            out.append(i == 0 ? "" : ",").append("{\"domain\":\"COLUMN\",\"name\":\"")
               .append(columns.get(i).toUpperCase(Locale.ROOT)).append("\"}");
        }
        return out.append("]").toString();
    }

    /**
     * DATA_METRIC_SCHEDULE as a cron string: minutes below an hour, whole hours above, and a cron expression given as
     * {@code USING CRON 0 0 1 1 * UTC} as the expression and its time zone (live-verified).
     */
    private String schedule(final String schedule) {
        if (schedule == null || schedule.isEmpty()) {
            return null;
        }
        if (schedule.regionMatches(true, 0, CRON_PREFIX, 0, CRON_PREFIX.length())) {
            return schedule.substring(CRON_PREFIX.length()).trim();
        }
        final String[] parts = schedule.trim().split("\\s+");
        if (parts.length < 2 || !parts[0].matches("[0-9]+")) {
            return schedule + " UTC";
        }
        final int minutes = Integer.parseInt(parts[0]);
        return minutes % 60 == 0 && minutes >= 60
            ? "0 */" + minutes / 60 + " * * * UTC"
            : "*/" + minutes + " * * * * UTC";
    }

    private String argument(final Map<String, Object> namedArgs, final String name) {
        final Object value = value(namedArgs, name);
        return value == null ? null : value.toString();
    }

    /** A named argument's value; null when it is absent or NULL. The last of a name written twice is read. */
    private Object value(final Map<String, Object> namedArgs, final String name) {
        if (namedArgs == null) {
            return null;
        }
        Object value = null;
        for (final Map.Entry<String, Object> entry : namedArgs.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                value = entry.getValue();
            }
        }
        return value;
    }

    private List<ResultSetColumn> columns() {
        final List<ResultSetColumn> columns = new ArrayList<>();
        for (final String name : COLUMN_NAMES) {
            columns.add(new ResultSetColumn(name, StringType.VARCHAR));
        }
        return columns;
    }
}
