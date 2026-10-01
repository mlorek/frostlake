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

import dev.frostlake.http.rest.RestCall;
import dev.frostlake.http.rest.RestException;
import dev.frostlake.http.rest.RestIdentifier;
import dev.frostlake.http.rest.RestJson;
import dev.frostlake.http.rest.RestRow;
import dev.frostlake.http.rest.RestShow;
import dev.frostlake.http.rest.RestStatement;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * What the event table and Iceberg table resources read about a table beyond their own listing: its SHOW TABLES
 * row (clustering, retention, change tracking, sizes), its columns from DESCRIBE TABLE, and the CLUSTER BY a
 * body's {@code cluster_by} names.
 */
final class RestTableDetails {

    private RestTableDetails() {
    }

    /** The table's SHOW TABLES row, or null when the schema holds no table of that exact name. */
    static RestRow tableRow(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW TABLES" + RestShow.likeName(name) + " IN SCHEMA "
            + call.schemaSql(), name);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** The SHOW TABLES properties a table schema shares: clustering, retention and change tracking. */
    static void tableProperties(final ObjectNode node, final RestRow table) {
        if (table == null) {
            return;
        }
        final String clusterBy = table.nonEmpty("cluster_by");
        final ArrayNode keys = RestJson.array();
        if (clusterBy != null) {
            final int open = clusterBy.indexOf('(');
            final String inner = open >= 0 && clusterBy.endsWith(")")
                ? clusterBy.substring(open + 1, clusterBy.length() - 1) : clusterBy;
            int depth = 0;
            int start = 0;
            for (int i = 0; i <= inner.length(); i++) {
                final char c = i < inner.length() ? inner.charAt(i) : ',';
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    final String key = inner.substring(start, i).trim();
                    if (!key.isEmpty()) {
                        keys.add(key);
                    }
                    start = i + 1;
                }
            }
        }
        if (keys.isEmpty()) {
            node.putNull("cluster_by");
        } else {
            node.set("cluster_by", keys);
        }
        RestJson.integer(node, "data_retention_time_in_days", table, "retention_time");
        RestJson.bool(node, "change_tracking", table, "change_tracking");
    }

    /** The columns DESCRIBE TABLE reports, each with the properties the event table schema names. */
    static ArrayNode columns(final RestCall call, final String tableSql, final boolean icebergShape) {
        final ArrayNode columns = RestJson.array();
        for (final RestRow row : call.sql().show("DESCRIBE TABLE " + tableSql)) {
            final ObjectNode column = RestJson.object();
            RestJson.string(column, "name", row, "name");
            RestJson.string(column, "datatype", row, "type");
            RestJson.bool(column, "nullable", row, "null?");
            if (icebergShape) {
                RestJson.string(column, "default_value", row, "default");
            } else {
                RestJson.string(column, "default", row, "default");
                RestJson.bool(column, "primary_key", row, "primary key");
                RestJson.bool(column, "unique_key", row, "unique key");
                RestJson.string(column, "check", row, "check");
                RestJson.string(column, "expression", row, "expression");
            }
            RestJson.string(column, "comment", row, "comment");
            columns.add(column);
        }
        return columns;
    }

    /** {@code CLUSTER BY ("A", "B")} for a body's {@code cluster_by} column names, or nothing. */
    static void clusterBy(final RestStatement sql, final JsonNode body) {
        final List<String> keys = RestJson.strings(body, "cluster_by");
        if (keys == null || keys.isEmpty()) {
            return;
        }
        final StringBuilder clause = new StringBuilder(" CLUSTER BY (");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                clause.append(", ");
            }
            final RestIdentifier key = RestIdentifier.tryParse(keys.get(i));
            if (key == null) {
                throw RestException.badRequest("Invalid cluster_by column '" + keys.get(i)
                    + "': expected a column name.");
            }
            clause.append(key.sql());
        }
        sql.append(clause.append(')').toString());
    }
}
