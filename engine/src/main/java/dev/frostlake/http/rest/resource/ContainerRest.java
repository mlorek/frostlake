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

package dev.frostlake.http.rest.resource;

import dev.frostlake.executor.ContainerParameterCatalog;
import dev.frostlake.executor.SessionParameterRow;
import dev.frostlake.http.rest.RestCall;
import dev.frostlake.http.rest.RestException;
import dev.frostlake.http.rest.RestJson;
import dev.frostlake.http.rest.RestResponse;
import dev.frostlake.http.rest.RestRow;
import dev.frostlake.http.rest.RestSql;
import dev.frostlake.http.rest.RestStatement;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What the database and schema resources share: the container properties a body sets (each written into a
 * CREATE, or into one ALTER … SET, as {@code KEYWORD = value}), their read-back from
 * {@code SHOW PARAMETERS IN DATABASE | SCHEMA}, the {@code kind}, the clone's point of time and the delete's
 * {@code restrict}.
 */
final class ContainerRest {

    /** A fourth cell marking a property the API reports but ignores in a request body, as the account does. */
    static final String IGNORED = "ignored";

    /** Type marks of the property tables: text, integer, boolean, object name. */
    static final String TEXT = "S";
    static final String INTEGER = "I";
    static final String BOOLEAN = "B";
    static final String NAME = "N";

    /** The properties databases and schemas share: body property, SQL keyword, type mark. */
    static final String[][] PROPERTIES = {
        {"data_retention_time_in_days", "DATA_RETENTION_TIME_IN_DAYS", INTEGER},
        {"max_data_extension_time_in_days", "MAX_DATA_EXTENSION_TIME_IN_DAYS", INTEGER},
        {"default_ddl_collation", "DEFAULT_DDL_COLLATION", TEXT},
        {"log_level", "LOG_LEVEL", TEXT, IGNORED},
        {"trace_level", "TRACE_LEVEL", TEXT, IGNORED},
        {"suspend_task_after_num_failures", "SUSPEND_TASK_AFTER_NUM_FAILURES", INTEGER},
        {"user_task_managed_initial_warehouse_size", "USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE", TEXT},
        {"user_task_timeout_ms", "USER_TASK_TIMEOUT_MS", INTEGER},
        {"serverless_task_min_statement_size", "SERVERLESS_TASK_MIN_STATEMENT_SIZE", TEXT},
        {"serverless_task_max_statement_size", "SERVERLESS_TASK_MAX_STATEMENT_SIZE", TEXT},
        {"external_volume", "EXTERNAL_VOLUME", NAME},
        {"catalog", "CATALOG", NAME},
        {"iceberg_version_default", "ICEBERG_VERSION_DEFAULT", INTEGER},
        {"iceberg_merge_on_read_behavior", "ICEBERG_MERGE_ON_READ_BEHAVIOR", TEXT},
        {"storage_serialization_policy", "STORAGE_SERIALIZATION_POLICY", TEXT},
        {"catalog_sync", "CATALOG_SYNC", TEXT},
        {"enable_data_compaction", "ENABLE_DATA_COMPACTION", BOOLEAN},
        {"replace_invalid_characters", "REPLACE_INVALID_CHARACTERS", BOOLEAN},
    };

    private static final Pattern OFFSET = Pattern.compile("-?[0-9]+(\\.[0-9]+)?");

    /** Static helpers only. */
    private ContainerRest() {
    }

    /** Appends {@code KEYWORD = value} for every property of the table the body sets. */
    static void append(final RestStatement sql, final JsonNode body, final String[][] properties) {
        for (final String[] property : properties) {
            appendOne(sql, body, property);
        }
    }

    /** Whether a body's value for the property is ignored: the account reports it but does not take it. */
    static boolean ignored(final String[] property) {
        return property.length > 3 && IGNORED.equals(property[3]);
    }

    /** Appends {@code KEYWORD = value} when the body sets the property and the property is not ignored. */
    static void appendOne(final RestStatement sql, final JsonNode body, final String[] property) {
        if (ignored(property)) {
            return;
        }
        if (INTEGER.equals(property[2])) {
            sql.integer(body, property[0], property[1]);
        } else if (BOOLEAN.equals(property[2])) {
            sql.bool(body, property[0], property[1]);
        } else if (NAME.equals(property[2])) {
            sql.identifier(body, property[0], property[1]);
        } else {
            sql.string(body, property[0], property[1]);
        }
    }

    /**
     * {@code " TRANSIENT"} when the {@code kind} query parameter or the body's {@code kind} asks for a transient
     * container, else nothing.
     *
     * @throws RestException {@code 400} for a kind that is neither PERMANENT nor TRANSIENT
     */
    static String transientWord(final RestCall call, final JsonNode body) {
        final String kind = kind(call, body);
        return "TRANSIENT".equals(kind) ? " TRANSIENT" : "";
    }

    /**
     * The kind asked for, upper-cased, or null: the {@code kind} query parameter when it is the lower-case word
     * {@code transient} (the account ignores any other spelling of it), else the body's {@code kind}.
     */
    static String kind(final RestCall call, final JsonNode body) {
        if ("transient".equals(call.query("kind"))) {
            return "TRANSIENT";
        }
        final String kind = RestJson.text(body, "kind");
        if (kind == null || kind.isEmpty()) {
            return null;
        }
        final String upper = kind.trim().toUpperCase(Locale.ROOT);
        if (!"PERMANENT".equals(upper) && !"TRANSIENT".equals(upper)) {
            throw RestException.badRequest("Invalid kind '" + kind + "': expected PERMANENT or TRANSIENT.");
        }
        return upper;
    }

    /** The kind a SHOW row's {@code options} cell spells: TRANSIENT when it says so, else PERMANENT. */
    static String kindOf(final RestRow row) {
        final String options = row.string("options");
        return options != null && options.contains("TRANSIENT") ? "TRANSIENT" : "PERMANENT";
    }

    /**
     * The time-travel clause of a clone's {@code point_of_time}: {@code AT | BEFORE (TIMESTAMP => …)},
     * {@code (OFFSET => n)} or {@code (STATEMENT => '…')}; nothing when the body has none.
     *
     * @throws RestException {@code 400} for a point of time the API does not define
     */
    static String pointOfTime(final JsonNode body) {
        final JsonNode point = body == null ? null : body.get("point_of_time");
        if (point == null || point.isNull() || point.isMissingNode()) {
            return "";
        }
        if (!point.isObject()) {
            throw RestException.badRequest("Property 'point_of_time' must be an object.");
        }
        final String reference = RestJson.text(point, "reference");
        final String word;
        if (reference == null || "at".equalsIgnoreCase(reference)) {
            word = " AT";
        } else if ("before".equalsIgnoreCase(reference)) {
            word = " BEFORE";
        } else {
            throw RestException.badRequest("Invalid point_of_time reference '" + reference
                + "': expected at or before.");
        }
        final String type = RestJson.text(point, "point_of_time_type");
        if ("timestamp".equalsIgnoreCase(type)) {
            return word + " (TIMESTAMP => " + RestSql.literal(required(point, "timestamp")) + "::TIMESTAMP_LTZ)";
        }
        if ("offset".equalsIgnoreCase(type)) {
            final String offset = required(point, "offset").trim();
            if (!OFFSET.matcher(offset).matches()) {
                throw RestException.badRequest("Invalid point_of_time offset '" + offset + "': expected seconds.");
            }
            return word + " (OFFSET => " + offset + ")";
        }
        if ("statement".equalsIgnoreCase(type)) {
            return word + " (STATEMENT => " + RestSql.literal(required(point, "statement")) + ")";
        }
        throw RestException.badRequest("Invalid point_of_time_type '" + type
            + "': expected timestamp, offset or statement.");
    }

    private static String required(final JsonNode point, final String property) {
        final String value = RestJson.text(point, property);
        if (value == null) {
            throw RestException.badRequest("Missing required property 'point_of_time." + property + "'.");
        }
        return value;
    }

    /** {@code " RESTRICT"} for {@code restrict=true}, {@code " CASCADE"} for {@code false}, else nothing. */
    static String dropBehavior(final RestCall call) {
        if (call.query("restrict") == null) {
            return "";
        }
        return call.flag("restrict", false) ? " RESTRICT" : " CASCADE";
    }

    /** Sets, from {@code SHOW PARAMETERS} rows, each property of the table: null where the rows have none. */
    static void parameters(final ObjectNode node, final List<RestRow> parameters, final String[][] properties) {
        for (final String[] property : properties) {
            final RestRow row = parameter(parameters, property[1]);
            if (row == null) {
                node.putNull(property[0]);
                continue;
            }
            if (INTEGER.equals(property[2])) {
                RestJson.integer(node, property[0], row, "value");
            } else if (BOOLEAN.equals(property[2])) {
                RestJson.bool(node, property[0], row, "value");
            } else if (property[0].endsWith("_size")) {
                // A warehouse size reads back upper-cased, as the account's body spells it.
                final String size = row.string("value");
                RestJson.put(node, property[0], size == null ? null : size.toUpperCase(Locale.ROOT));
            } else {
                // An empty parameter reads back as the empty string, not null.
                RestJson.put(node, property[0], row.string("value"));
            }
        }
    }

    /**
     * The parameter rows of a dropped database or schema, which SHOW PARAMETERS cannot list: every parameter at
     * its account default, except a database's retention, which its listing row reports.
     *
     * @param schema whether the rows are a schema's
     * @param retention the retention to report, or null for the default
     */
    static List<RestRow> defaults(final boolean schema, final Long retention) {
        final ResultSet set = new ResultSet(Arrays.asList(new ResultSetColumn("key", StringType.VARCHAR),
            new ResultSetColumn("value", StringType.VARCHAR), new ResultSetColumn("level", StringType.VARCHAR)),
            new ArrayList<Row>());
        final List<RestRow> rows = new ArrayList<>();
        for (final SessionParameterRow parameter : schema ? ContainerParameterCatalog.schemaRows()
                : ContainerParameterCatalog.databaseRows()) {
            final String value = retention != null && "DATA_RETENTION_TIME_IN_DAYS".equals(parameter.getName())
                ? String.valueOf(retention) : parameter.getDefaultValue();
            rows.add(new RestRow(set, new Row(Arrays.<Object>asList(parameter.getName(), value, ""))));
        }
        return rows;
    }

    /** The {@code SHOW PARAMETERS} row of a parameter, or null. */
    static RestRow parameter(final List<RestRow> parameters, final String key) {
        for (final RestRow row : parameters) {
            if (key.equalsIgnoreCase(row.string("key"))) {
                return row;
            }
        }
        return null;
    }

    /**
     * Sets an existing container to a body: each property the body names is SET, and each one it leaves out that
     * the container sets itself ({@code level} = its own) — or that the listing cannot tell — is UNSET, so the
     * body is the whole property set. The comment follows the same rule.
     *
     * @param alterHead {@code ALTER DATABASE <name>} or {@code ALTER SCHEMA <db>.<name>}
     * @param ownLevel the level {@code SHOW PARAMETERS} reports for the container's own settings
     * @param comment the container's current comment
     * @return the sentence of the last statement run, or the default status when none was needed
     */
    static String alter(final RestCall call, final String alterHead, final JsonNode body,
                        final String[][] properties, final List<RestRow> parameters, final String ownLevel,
                        final String comment) {
        String status = RestResponse.DEFAULT_STATUS;
        for (final String[] property : properties) {
            if (ignored(property)) {
                continue;
            }
            if (RestJson.present(body, property[0])) {
                final RestStatement set = new RestStatement(alterHead + " SET");
                appendOne(set, body, property);
                status = call.sql().status(set.toString());
                continue;
            }
            final RestRow row = parameter(parameters, property[1]);
            if (row == null || ownLevel.equalsIgnoreCase(row.string("level"))) {
                status = call.sql().status(alterHead + " UNSET " + property[1]);
            }
        }
        final String newComment = RestJson.text(body, "comment");
        if (newComment != null) {
            status = call.sql().status(alterHead + " SET COMMENT = " + RestSql.literal(newComment));
        } else if (comment != null && !comment.isEmpty()) {
            status = call.sql().status(alterHead + " UNSET COMMENT");
        }
        return status;
    }
}
