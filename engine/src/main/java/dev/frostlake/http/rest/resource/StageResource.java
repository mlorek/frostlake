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
import dev.frostlake.http.rest.RestCreateMode;
import dev.frostlake.http.rest.RestException;
import dev.frostlake.http.rest.RestIdentifier;
import dev.frostlake.http.rest.RestJson;
import dev.frostlake.http.rest.RestResource;
import dev.frostlake.http.rest.RestResponse;
import dev.frostlake.http.rest.RestRouter;
import dev.frostlake.http.rest.RestRow;
import dev.frostlake.http.rest.RestShow;
import dev.frostlake.http.rest.RestSql;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stages ({@code stage.yaml}, {@code /api/v2/databases/{database}/schemas/{schema}/stages}): list, create,
 * fetch, delete, the staged files ({@code GET /{name}/files}, {@code LIST @stage}) and
 * {@code :presigned-url} ({@code GET_PRESIGNED_URL}).
 *
 * <p>A stage is read from {@code SHOW STAGES}; a fetch adds what only {@code DESCRIBE STAGE} carries — the file
 * format, the copy options and the directory table. A create writes every body property as the matching
 * {@code CREATE STAGE} parameter: the nested objects become the parenthesised option lists
 * ({@code CREDENTIALS = (…)}, {@code ENCRYPTION = (…)}, {@code DIRECTORY = (…)}, {@code FILE_FORMAT = (…)},
 * {@code COPY_OPTIONS = (…)}), each option key checked to be a plain word and each value written as the literal
 * its JSON type calls for.
 */
public final class StageResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/stages";
    private static final String ITEM = COLLECTION + "/{name}";
    private static final Pattern OPTION_KEY = Pattern.compile("[a-z][a-z0-9_]*");
    /** The file format types a stage's inline format may name. */
    private static final Set<String> FORMAT_TYPES = new HashSet<>(Arrays.asList(
        "CSV", "JSON", "AVRO", "ORC", "PARQUET", "XML", "CUSTOM"));
    /** The option keys each inline format type reports, as the specification lists them. */
    private static final Map<String, List<String>> FORMAT_OPTIONS = Map.of(
        "CSV", Arrays.asList("compression", "record_delimiter", "field_delimiter", "file_extension",
            "parse_header", "skip_header", "skip_blank_lines", "date_format", "time_format", "timestamp_format",
            "binary_format", "escape", "escape_unenclosed_field", "trim_space", "field_optionally_enclosed_by",
            "null_if", "error_on_column_count_mismatch", "replace_invalid_characters", "empty_field_as_null",
            "skip_byte_order_mark", "encoding", "multi_line"),
        "JSON", Arrays.asList("compression", "date_format", "time_format", "timestamp_format", "binary_format",
            "trim_space", "null_if", "file_extension", "enable_octal", "allow_duplicate", "strip_outer_array",
            "strip_null_values", "replace_invalid_characters", "ignore_utf8_errors", "skip_byte_order_mark",
            "multi_line"),
        "AVRO", Arrays.asList("compression", "trim_space", "replace_invalid_characters", "null_if",
            "use_logical_type"),
        "ORC", Arrays.asList("trim_space", "replace_invalid_characters", "null_if"),
        "PARQUET", Arrays.asList("compression", "binary_as_text", "use_logical_type", "use_vectorized_scanner",
            "trim_space", "replace_invalid_characters", "null_if"),
        "XML", Arrays.asList("compression", "ignore_utf8_errors", "preserve_space", "strip_outer_element",
            "disable_auto_convert", "replace_invalid_characters", "skip_byte_order_mark"));

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listStages", this);
        router.add("POST", COLLECTION, "createStage", this);
        router.add("GET", ITEM, "fetchStage", this);
        router.add("DELETE", ITEM, "deleteStage", this);
        router.add("GET", ITEM + "/files", "listFiles", this);
        router.add("POST", ITEM + "/files/{filePath}:presigned-url", "getPresignedUrl", this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        switch (call.operation()) {
            case "listStages":
                return list(call);
            case "createStage":
                return create(call);
            case "fetchStage":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "deleteStage":
                return call.sql().action("DROP STAGE" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")));
            case "listFiles":
                return listFiles(call);
            case "getPresignedUrl":
                return presignedUrl(call);
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW STAGES" + RestShow.like(call) + " IN SCHEMA "
                + call.schemaSql())) {
            out.add(toJson(row, null));
        }
        return RestResponse.json(200, out);
    }

    /** The stage as the {@code Stage} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW STAGES" + RestShow.likeName(name) + " IN SCHEMA "
            + call.schemaSql(), name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Stage '" + name + "' does not exist or not authorized.");
        }
        return toJson(rows.get(0), call.sql().show("DESCRIBE STAGE " + call.qualifiedSql(name)));
    }

    private static ObjectNode toJson(final RestRow row, final List<RestRow> described) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        final String type = row.nonEmpty("type");
        node.put("kind", type != null && type.toUpperCase(Locale.ROOT).contains("TEMPORARY")
            ? "TEMPORARY" : "PERMANENT");
        // An internal stage lists an empty URL, which the account answers as it is; an empty comment is null.
        RestJson.put(node, "url", row.string("url"));
        RestJson.string(node, "endpoint", row, "endpoint");
        RestJson.name(node, "storage_integration", row, "storage_integration");
        RestJson.string(node, "comment", row, "comment");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.bool(node, "has_credentials", row, "has_credentials");
        RestJson.bool(node, "has_encryption_key", row, "has_encryption_key");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.string(node, "region", row, "region");
        RestJson.string(node, "cloud", row, "cloud");
        final ObjectNode directory = RestJson.object();
        RestJson.bool(directory, "enable", row, "directory_enabled");
        if (described == null) {
            if (directory.size() > 0) {
                node.set("directory_table", directory);
            }
            RestJson.nulls(node, "name", "kind", "url", "endpoint", "storage_integration", "comment", "credentials",
                "encryption", "directory_table", "file_format", "copy_options", "created_on", "has_credentials",
                "has_encryption_key", "owner", "owner_role_type", "region", "cloud");
            return node;
        }
        final ObjectNode format = RestJson.object();
        final ObjectNode copyOptions = RestJson.object();
        for (final RestRow property : described) {
            final String parent = property.nonEmpty("parent_property");
            final String key = property.nonEmpty("property");
            if (parent == null || key == null) {
                continue;
            }
            if ("DIRECTORY".equals(parent)) {
                describedValue(directory, key.toLowerCase(Locale.ROOT), property);
            } else if ("STAGE_COPY_OPTIONS".equals(parent)) {
                describedValue(copyOptions, key.toLowerCase(Locale.ROOT), property);
            } else if ("STAGE_FILE_FORMAT".equals(parent)) {
                describedValue(format, key.toLowerCase(Locale.ROOT), property);
            }
        }
        // The account answers every directory table property, the ones the listing does not carry as their
        // defaults: no notification channel, and a refresh when the directory is created.
        if (!directory.has("aws_sns_topic")) {
            directory.putNull("aws_sns_topic");
        }
        if (!directory.has("notification_integration")) {
            directory.putNull("notification_integration");
        }
        if (!directory.has("refresh_on_create")) {
            directory.put("refresh_on_create", true);
        }
        node.set("directory_table", directory);
        if (copyOptions.size() > 0) {
            node.set("copy_options", copyOptions);
        }
        final ObjectNode fileFormat = fileFormat(format);
        if (fileFormat != null) {
            node.set("file_format", fileFormat);
        }
        RestJson.nulls(node, "name", "kind", "url", "endpoint", "storage_integration", "comment", "credentials",
            "encryption", "directory_table", "file_format", "copy_options", "created_on", "has_credentials",
            "has_encryption_key", "owner", "owner_role_type", "region", "cloud");
        return node;
    }

    /**
     * A DESCRIBE STAGE property as a JSON value typed by its {@code property_type}; an empty value is null. Text is
     * decoded from the listing's escaped display, as the account answers it: {@code \\} is a backslash and
     * {@code \n} a line break.
     */
    private static void describedValue(final ObjectNode node, final String key, final RestRow property) {
        final String value = property.nonEmpty("property_value");
        if (value == null) {
            node.putNull(key);
            return;
        }
        final String type = property.nonEmpty("property_type");
        if ("Boolean".equalsIgnoreCase(type)) {
            RestJson.bool(node, key, property, "property_value");
        } else if ("Integer".equalsIgnoreCase(type) || "Long".equalsIgnoreCase(type)) {
            RestJson.integer(node, key, property, "property_value");
        } else if ("List".equalsIgnoreCase(type)) {
            final ArrayNode items = RestJson.array();
            String inner = value.trim();
            if (inner.startsWith("[") && inner.endsWith("]")) {
                inner = inner.substring(1, inner.length() - 1);
            }
            if (!inner.isEmpty()) {
                for (final String item : inner.split(", ")) {
                    items.add(unescape(item));
                }
            }
            node.set(key, items);
        } else {
            node.put(key, unescape(value));
        }
    }

    /** A listed text with its escapes read: {@code \\}, {@code \n}, {@code \r} and {@code \t}. */
    private static String unescape(final String text) {
        final StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            final char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (c == '\\' && (next == '\\' || next == 'n' || next == 'r' || next == 't')) {
                out.append(next == 'n' ? '\n' : next == 'r' ? '\r' : next == 't' ? '\t' : '\\');
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * The described format as the {@code StageFileFormat} its type selects: an inline format keeps the options
     * the specification lists for that type; any other type names a file format object.
     */
    private static ObjectNode fileFormat(final ObjectNode described) {
        final JsonNode typeNode = described.get("type");
        if (typeNode == null || typeNode.isNull()) {
            return null;
        }
        final String type = typeNode.asString().toUpperCase(Locale.ROOT);
        final ObjectNode out = RestJson.object();
        final List<String> keys = FORMAT_OPTIONS.get(type);
        if (keys == null) {
            out.put("type", "FORMAT_REF");
            out.put("format_name", typeNode.asString());
            return out;
        }
        out.put("type", type);
        for (final String key : keys) {
            if (described.has(key)) {
                out.set(key, described.get(key));
            }
        }
        return out;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final String kind = RestJson.text(body, "kind");
        final boolean temporary;
        if (kind == null || "PERMANENT".equalsIgnoreCase(kind)) {
            temporary = false;
        } else if ("TEMPORARY".equalsIgnoreCase(kind)) {
            temporary = true;
        } else {
            throw RestException.badRequest("Invalid value '" + kind + "' for property 'kind': expected PERMANENT "
                + "or TEMPORARY.");
        }
        final StringBuilder sql = new StringBuilder("CREATE").append(mode.orReplace())
            .append(temporary ? " TEMPORARY" : "").append(" STAGE").append(mode.ifNotExists()).append(' ')
            .append(call.qualifiedSql(name));
        final String url = RestJson.text(body, "url");
        if (url != null) {
            sql.append(" URL = ").append(RestSql.literal(url));
        }
        final RestIdentifier integration = RestJson.identifier(body, "storage_integration", false);
        if (integration != null) {
            sql.append(" STORAGE_INTEGRATION = ").append(integration.sql());
        }
        final String endpoint = RestJson.text(body, "endpoint");
        if (endpoint != null) {
            sql.append(" ENDPOINT = ").append(RestSql.literal(endpoint));
        }
        credentials(sql, body.get("credentials"));
        optionList(sql, "ENCRYPTION", body.get("encryption"), "encryption");
        optionList(sql, "DIRECTORY", body.get("directory_table"), "directory_table");
        fileFormat(sql, body.get("file_format"));
        optionList(sql, "COPY_OPTIONS", body.get("copy_options"), "copy_options");
        final String comment = RestJson.text(body, "comment");
        if (comment != null) {
            sql.append(" COMMENT = ").append(RestSql.literal(comment));
        }
        return call.sql().action(sql.toString());
    }

    /** {@code CREDENTIALS = (…)} from an AWS or Azure credentials object. */
    private static void credentials(final StringBuilder sql, final JsonNode credentials) {
        if (credentials == null || credentials.isNull()) {
            return;
        }
        if (!credentials.isObject()) {
            throw RestException.badRequest("Property 'credentials' must be an object.");
        }
        final StringBuilder list = new StringBuilder();
        for (final Map.Entry<String, JsonNode> entry : credentials.properties()) {
            if (!"credential_type".equals(entry.getKey())) {
                appendOption(list, entry.getKey(), entry.getValue(), "credentials");
            }
        }
        sql.append(" CREDENTIALS = (").append(list).append(')');
    }

    /** {@code FILE_FORMAT = (FORMAT_NAME = '…')} for a reference, {@code FILE_FORMAT = (TYPE = … …)} inline. */
    private static void fileFormat(final StringBuilder sql, final JsonNode format) {
        if (format == null || format.isNull()) {
            return;
        }
        if (!format.isObject()) {
            throw RestException.badRequest("Property 'file_format' must be an object.");
        }
        final String type = RestJson.text(format, "type");
        if (type == null) {
            throw RestException.badRequest("Missing required property 'type' of 'file_format'.");
        }
        final String upper = type.toUpperCase(Locale.ROOT);
        if ("FORMAT_REF".equals(upper)) {
            final String formatName = RestJson.text(format, "format_name");
            if (formatName == null) {
                throw RestException.badRequest("Missing required property 'format_name' of 'file_format'.");
            }
            sql.append(" FILE_FORMAT = (FORMAT_NAME = ").append(RestSql.literal(formatName)).append(')');
            return;
        }
        if (!FORMAT_TYPES.contains(upper)) {
            throw RestException.badRequest("Invalid value '" + type + "' for property 'type' of 'file_format'.");
        }
        final StringBuilder list = new StringBuilder("TYPE = ").append(upper);
        for (final Map.Entry<String, JsonNode> entry : format.properties()) {
            if (!"type".equals(entry.getKey())) {
                appendOption(list, entry.getKey(), entry.getValue(), "file_format");
            }
        }
        sql.append(" FILE_FORMAT = (").append(list).append(')');
    }

    /** {@code KEYWORD = (key = value …)} from a flat object, when the body sets it. */
    private static void optionList(final StringBuilder sql, final String keyword, final JsonNode options,
                                   final String property) {
        if (options == null || options.isNull()) {
            return;
        }
        if (!options.isObject()) {
            throw RestException.badRequest("Property '" + property + "' must be an object.");
        }
        final StringBuilder list = new StringBuilder();
        for (final Map.Entry<String, JsonNode> entry : options.properties()) {
            appendOption(list, entry.getKey(), entry.getValue(), property);
        }
        sql.append(' ').append(keyword).append(" = (").append(list).append(')');
    }

    /** One {@code KEY = value} of an option list; nulls are skipped. */
    private static void appendOption(final StringBuilder list, final String key, final JsonNode value,
                                     final String property) {
        if (value == null || value.isNull()) {
            return;
        }
        if (!OPTION_KEY.matcher(key).matches()) {
            throw RestException.badRequest("Invalid option '" + key + "' in property '" + property + "'.");
        }
        final String rendered;
        if (value.isBoolean()) {
            rendered = value.booleanValue() ? "TRUE" : "FALSE";
        } else if (value.isIntegralNumber()) {
            rendered = Long.toString(value.longValue());
        } else if (value.isString()) {
            rendered = RestSql.literal(value.stringValue());
        } else if (value.isArray()) {
            final StringBuilder items = new StringBuilder("(");
            for (final JsonNode item : value.values()) {
                if (item.isObject() || item.isArray() || item.isNull()) {
                    throw RestException.badRequest("Option '" + key + "' in property '" + property
                        + "' must be an array of strings.");
                }
                if (items.length() > 1) {
                    items.append(", ");
                }
                items.append(RestSql.literal(item.isString() ? item.stringValue() : item.toString()));
            }
            rendered = items.append(')').toString();
        } else {
            throw RestException.badRequest("Option '" + key + "' in property '" + property
                + "' must be a string, a number, a boolean or an array of strings.");
        }
        if (list.length() > 0) {
            list.append(' ');
        }
        list.append(key.toUpperCase(Locale.ROOT)).append(" = ").append(rendered);
    }

    private static RestResponse listFiles(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        final StringBuilder sql = new StringBuilder("LIST @").append(call.qualifiedSql(name));
        final String pattern = call.query("pattern");
        if (pattern != null) {
            sql.append(" PATTERN = ").append(RestSql.literal(pattern));
        }
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show(sql.toString())) {
            final ObjectNode file = RestJson.object();
            RestJson.string(file, "name", row, "name");
            final Long size = row.integer("size");
            if (size != null) {
                file.put("size", size.toString());
            }
            RestJson.string(file, "md5", row, "md5");
            RestJson.string(file, "last_modified", row, "last_modified");
            out.add(file);
        }
        return RestResponse.json(200, out);
    }

    /**
     * {@code GET_PRESIGNED_URL('@db.schema.stage', '<filePath>'[, <expiration_time>])} as a
     * {@code FileTransferMaterial}.
     */
    private static RestResponse presignedUrl(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        final String filePath = call.pathParameter("filePath");
        if (filePath == null || filePath.isEmpty()) {
            throw RestException.badRequest("Missing file path.");
        }
        final Long expiration = RestJson.integer(call.bodyOrEmpty(), "expiration_time");
        final StringBuilder sql = new StringBuilder("SELECT GET_PRESIGNED_URL(")
            .append(RestSql.literal("@" + SchemaObjectNames.dotted(call, name))).append(", ")
            .append(RestSql.literal(filePath));
        if (expiration != null) {
            sql.append(", ").append(expiration.longValue());
        }
        if (call.sql().showNamed("SHOW STAGES" + RestShow.likeName(name) + " IN SCHEMA " + call.schemaSql(), name)
                .isEmpty()) {
            throw RestException.notFound("Stage '" + name + "' does not exist or not authorized.");
        }
        final ObjectNode out = RestJson.object();
        RestJson.put(out, "presigned_url", call.sql().status(sql.append(')').toString()));
        return RestResponse.json(200, out);
    }
}
