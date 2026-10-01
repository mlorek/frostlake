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
import dev.frostlake.http.rest.RestSql;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A table's columns and constraints both ways: rendered from a {@code Table} body into a CREATE's column list,
 * and read back from {@code DESCRIBE TABLE} and {@code SHOW PRIMARY | UNIQUE | IMPORTED KEYS} into the
 * {@code columns} and {@code constraints} arrays; with the clustering key and the point in time a clone names.
 */
final class TableColumns {

    private static final String COLLATE = " COLLATE '";
    private static final String IDENTITY = "IDENTITY ";

    /** Static helpers only. */
    private TableColumns() {
    }

    // ---- request -----------------------------------------------------------------------------------------

    /**
     * The parenthesized column list a body's {@code columns} and {@code constraints} describe, each column's
     * own constraints written out of line over that column; null when the body names no column.
     */
    static String definitions(final RestCall call, final JsonNode body) {
        final JsonNode columns = body.get("columns");
        if (columns == null || columns.isNull()) {
            return null;
        }
        if (!columns.isArray()) {
            throw RestException.badRequest("Property 'columns' must be an array.");
        }
        final List<String> items = new ArrayList<>();
        final List<String> constraints = new ArrayList<>();
        for (final JsonNode column : columns.values()) {
            final RestIdentifier name = RestJson.identifier(column, "name", true);
            items.add(column(column, name));
            final JsonNode own = column.get("constraints");
            if (own != null && own.isArray()) {
                for (final JsonNode constraint : own.values()) {
                    constraints.add(constraint(call, constraint, name));
                }
            }
        }
        final JsonNode tableConstraints = body.get("constraints");
        if (tableConstraints != null && tableConstraints.isArray()) {
            for (final JsonNode constraint : tableConstraints.values()) {
                constraints.add(constraint(call, constraint, null));
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        items.addAll(constraints);
        return "(" + String.join(", ", items) + ")";
    }

    /** One column definition: name, type, then NOT NULL, COLLATE, DEFAULT or AUTOINCREMENT, and COMMENT. */
    private static String column(final JsonNode column, final RestIdentifier name) {
        final String datatype = RestJson.text(column, "datatype");
        if (datatype == null || datatype.isBlank()) {
            throw RestException.badRequest("Missing required property 'datatype' of column " + name + ".");
        }
        final StringBuilder sql = new StringBuilder(name.sql()).append(' ').append(datatype);
        final Boolean nullable = RestJson.bool(column, "nullable");
        if (nullable != null && !nullable.booleanValue()) {
            sql.append(" NOT NULL");
        }
        final String collate = RestJson.text(column, "collate");
        if (collate != null) {
            sql.append(" COLLATE ").append(RestSql.literal(collate));
        }
        final String defaultValue = RestJson.text(column, "default");
        final Boolean autoincrement = RestJson.bool(column, "autoincrement");
        if (autoincrement != null && autoincrement.booleanValue()) {
            sql.append(" AUTOINCREMENT");
            final Long start = RestJson.integer(column, "autoincrement_start");
            final Long increment = RestJson.integer(column, "autoincrement_increment");
            if (start != null || increment != null) {
                sql.append(" START ").append(start != null ? start.longValue() : 1L)
                    .append(" INCREMENT ").append(increment != null ? increment.longValue() : 1L);
            }
            final String order = RestJson.text(column, "autoincrement_order");
            if (order != null) {
                final String word = order.trim().toUpperCase(Locale.ROOT);
                if (!"ORDER".equals(word) && !"NOORDER".equals(word)) {
                    throw RestException.badRequest("Invalid autoincrement_order '" + order
                        + "': expected ORDER or NOORDER.");
                }
                sql.append(' ').append(word);
            }
        } else if (defaultValue != null) {
            sql.append(" DEFAULT ").append(defaultValue);
        }
        final String comment = RestJson.text(column, "comment");
        if (comment != null) {
            sql.append(" COMMENT ").append(RestSql.literal(comment));
        }
        return sql.toString();
    }

    /** An out-of-line constraint; a column's own constraint names no columns and covers that column. */
    private static String constraint(final RestCall call, final JsonNode constraint, final RestIdentifier column) {
        final String type = RestJson.text(constraint, "constraint_type");
        if (type == null) {
            throw RestException.badRequest("Missing required property 'constraint_type'.");
        }
        final StringBuilder sql = new StringBuilder();
        final RestIdentifier name = RestJson.identifier(constraint, "name", false);
        if (name != null) {
            sql.append("CONSTRAINT ").append(name.sql()).append(' ');
        }
        final String kind = type.trim().toUpperCase(Locale.ROOT);
        if ("PRIMARY KEY".equals(kind) || "UNIQUE".equals(kind)) {
            sql.append(kind).append(' ').append(columnList(constraint, "column_names", column));
        } else if ("FOREIGN KEY".equals(kind)) {
            sql.append("FOREIGN KEY ").append(columnList(constraint, "column_names", column))
                .append(" REFERENCES ")
                .append(RelationNames.qualified(call, RestJson.text(constraint, "referenced_table_name"),
                    "referenced_table_name"))
                .append(' ').append(columnList(constraint, "referenced_column_names", null));
        } else if ("CHECK".equals(kind)) {
            final String check = RestJson.text(constraint, "check_expression");
            if (check == null) {
                throw RestException.badRequest("Missing required property 'check_expression'.");
            }
            sql.append("CHECK (").append(check).append(')');
        } else {
            throw RestException.badRequest("Invalid constraint_type '" + type
                + "': expected PRIMARY KEY, UNIQUE, FOREIGN KEY or CHECK.");
        }
        final Boolean rely = RestJson.bool(constraint, "rely");
        if (rely != null && rely.booleanValue()) {
            sql.append(" RELY");
        }
        return sql.toString();
    }

    private static String columnList(final JsonNode constraint, final String property, final RestIdentifier column) {
        final List<String> names = RestJson.strings(constraint, property);
        final List<String> sql = new ArrayList<>();
        if (names == null || names.isEmpty()) {
            if (column == null) {
                throw RestException.badRequest("Missing required property '" + property + "'.");
            }
            sql.add(column.sql());
        } else {
            for (final String name : names) {
                sql.add(RestIdentifier.parse(name, property).sql());
            }
        }
        return "(" + String.join(", ", sql) + ")";
    }

    /** {@code " CLUSTER BY (a, b)"} for a body's {@code cluster_by} expressions, or nothing. */
    static String clusterBy(final JsonNode body) {
        final List<String> keys = RestJson.strings(body, "cluster_by");
        if (keys == null || keys.isEmpty()) {
            return "";
        }
        return " CLUSTER BY (" + String.join(", ", keys) + ")";
    }

    /**
     * The {@code AT | BEFORE} clause a {@code point_of_time} names, or nothing: a timestamp read as
     * TIMESTAMP_LTZ, an offset in seconds, or a statement's query id.
     */
    static String pointOfTime(final JsonNode body) {
        final JsonNode point = body.get("point_of_time");
        if (point == null || point.isNull()) {
            return "";
        }
        final String type = RestJson.text(point, "point_of_time_type");
        final String reference = RestJson.text(point, "reference");
        final String word = reference == null || "at".equalsIgnoreCase(reference) ? "AT"
            : "before".equalsIgnoreCase(reference) ? "BEFORE" : null;
        if (word == null) {
            throw RestException.badRequest("Invalid point_of_time reference '" + reference
                + "': expected at or before.");
        }
        if ("timestamp".equalsIgnoreCase(type)) {
            return " " + word + " (TIMESTAMP => " + RestSql.literal(required(point, "timestamp"))
                + "::TIMESTAMP_LTZ)";
        }
        if ("offset".equalsIgnoreCase(type)) {
            final String offset = required(point, "offset").trim();
            try {
                return " " + word + " (OFFSET => " + Long.parseLong(offset) + ")";
            } catch (final NumberFormatException notNumeric) {
                throw RestException.badRequest("Invalid point_of_time offset '" + offset
                    + "': expected a number of seconds.");
            }
        }
        if ("statement".equalsIgnoreCase(type)) {
            return " " + word + " (STATEMENT => " + RestSql.literal(required(point, "statement")) + ")";
        }
        throw RestException.badRequest("Invalid point_of_time_type '" + type
            + "': expected timestamp, offset or statement.");
    }

    private static String required(final JsonNode node, final String property) {
        final String value = RestJson.text(node, property);
        if (value == null) {
            throw RestException.badRequest("Missing required property '" + property + "'.");
        }
        return value;
    }

    // ---- response ----------------------------------------------------------------------------------------

    /** The {@code cluster_by} keys and the raw {@code cluster_by_raw} of a SHOW row's {@code cluster_by} cell. */
    static void clusterBy(final ObjectNode node, final RestRow row) {
        final String raw = row.nonEmpty("cluster_by");
        if (raw == null) {
            return;
        }
        node.put("cluster_by_raw", raw);
        final int open = raw.indexOf('(');
        final String inner = open >= 0 && raw.endsWith(")") ? raw.substring(open + 1, raw.length() - 1) : raw;
        final ArrayNode keys = RestJson.array();
        final StringBuilder key = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < inner.length(); i++) {
            final char c = inner.charAt(i);
            if (c == '\'' || c == '"') {
                quoted = !quoted;
            } else if (!quoted && c == '(') {
                depth++;
            } else if (!quoted && c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0 && !quoted) {
                keys.add(key.toString().trim());
                key.setLength(0);
            } else {
                key.append(c);
            }
        }
        if (key.length() > 0) {
            keys.add(key.toString().trim());
        }
        node.set("cluster_by", keys);
    }

    /** The {@code columns} array of a relation, read from {@code DESCRIBE <kind> <name>}. */
    static ArrayNode columns(final RestSql sql, final String describeSql, final boolean full) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : sql.show(describeSql)) {
            final String kind = row.nonEmpty("kind");
            if (kind != null && !"COLUMN".equalsIgnoreCase(kind)) {
                continue;
            }
            final ObjectNode column = RestJson.object();
            RestJson.name(column, "name", row, "name");
            String type = row.nonEmpty("type");
            if (type != null) {
                // The collation is reported apart from the type: as collate for a table, not at all for a view.
                final int collate = type.indexOf(COLLATE);
                if (collate > 0 && type.endsWith("'")) {
                    if (full) {
                        column.put("collate", type.substring(collate + COLLATE.length(), type.length() - 1));
                    }
                    type = type.substring(0, collate);
                }
            }
            RestJson.put(column, "datatype", type);
            if (full) {
                RestJson.bool(column, "nullable", row, "null?");
                final String defaultValue = row.nonEmpty("default");
                if (defaultValue != null && defaultValue.startsWith(IDENTITY)) {
                    identity(column, defaultValue);
                } else {
                    RestJson.put(column, "default", defaultValue);
                }
            }
            RestJson.string(column, "comment", row, "comment");
            if (full) {
                RestJson.nulls(column, "nullable", "collate", "default", "autoincrement", "autoincrement_start",
                    "autoincrement_increment", "autoincrement_order", "constraints", "comment");
            } else {
                RestJson.nulls(column, "datatype", "comment");
            }
            out.add(column);
        }
        return out;
    }

    /** An identity column's {@code autoincrement} properties, read from DESCRIBE's IDENTITY START s INCREMENT i ORDER|NOORDER. */
    private static void identity(final ObjectNode column, final String described) {
        column.put("autoincrement", true);
        final String[] words = described.trim().split("\\s+");
        for (int i = 0; i + 1 < words.length; i++) {
            if ("START".equals(words[i])) {
                column.put("autoincrement_start", Long.parseLong(words[i + 1]));
            } else if ("INCREMENT".equals(words[i])) {
                column.put("autoincrement_increment", Long.parseLong(words[i + 1]));
            }
        }
        final String last = words[words.length - 1];
        if ("ORDER".equals(last) || "NOORDER".equals(last)) {
            column.put("autoincrement_order", last);
        }
    }

    /** The {@code constraints} array of a table: its primary key, unique keys and foreign keys. */
    static ArrayNode constraints(final RestSql sql, final String tableSql) {
        final ArrayNode out = RestJson.array();
        keys(out, sql.show("SHOW PRIMARY KEYS IN TABLE " + tableSql), "PRIMARY KEY");
        keys(out, sql.show("SHOW UNIQUE KEYS IN TABLE " + tableSql), "UNIQUE");
        final Map<String, ObjectNode> foreign = new LinkedHashMap<>();
        for (final RestRow row : sql.show("SHOW IMPORTED KEYS IN TABLE " + tableSql)) {
            final String name = row.string("fk_name");
            ObjectNode constraint = foreign.get(name);
            if (constraint == null) {
                constraint = RestJson.object();
                RestJson.name(constraint, "name", row, "fk_name");
                constraint.set("column_names", RestJson.array());
                constraint.put("constraint_type", "FOREIGN KEY");
                constraint.put("referenced_table_name", RestIdentifier.display(row.string("pk_database_name")) + "."
                    + RestIdentifier.display(row.string("pk_schema_name")) + "."
                    + RestIdentifier.display(row.string("pk_table_name")));
                constraint.set("referenced_column_names", RestJson.array());
                RestJson.bool(constraint, "rely", row, "rely");
                RestJson.string(constraint, "comment", row, "comment");
                RestJson.string(constraint, "on_update", row, "update_rule");
                RestJson.string(constraint, "on_delete", row, "delete_rule");
                RestJson.string(constraint, "deferrability", row, "deferrability");
                RestJson.nulls(constraint, "enforced", "enable", "validate", "match");
                foreign.put(name, constraint);
            }
            ((ArrayNode) constraint.get("column_names")).add(RestIdentifier.display(row.string("fk_column_name")));
            ((ArrayNode) constraint.get("referenced_column_names"))
                .add(RestIdentifier.display(row.string("pk_column_name")));
        }
        for (final ObjectNode constraint : foreign.values()) {
            out.add(constraint);
        }
        return out;
    }

    /** One constraint per name of a SHOW PRIMARY KEYS or SHOW UNIQUE KEYS answer, its columns in key order. */
    private static void keys(final ArrayNode out, final List<RestRow> rows, final String type) {
        final Map<String, ObjectNode> byName = new LinkedHashMap<>();
        for (final RestRow row : rows) {
            final String name = row.string("constraint_name");
            ObjectNode constraint = byName.get(name);
            if (constraint == null) {
                constraint = RestJson.object();
                RestJson.name(constraint, "name", row, "constraint_name");
                constraint.set("column_names", RestJson.array());
                constraint.put("constraint_type", type);
                RestJson.bool(constraint, "rely", row, "rely");
                RestJson.string(constraint, "comment", row, "comment");
                constraint.put("deferrability", "NOT DEFERRABLE");
                constraint.put("enforced", false);
                constraint.put("validate", false);
                RestJson.nulls(constraint, "enable");
                byName.put(name, constraint);
            }
            ((ArrayNode) constraint.get("column_names")).add(RestIdentifier.display(row.string("column_name")));
        }
        for (final ObjectNode constraint : byName.values()) {
            out.add(constraint);
        }
    }
}
