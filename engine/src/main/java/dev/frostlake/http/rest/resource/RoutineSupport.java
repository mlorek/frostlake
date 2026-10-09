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

import dev.frostlake.http.ResultSetData;
import dev.frostlake.http.rest.RestCall;
import dev.frostlake.http.rest.RestCreateMode;
import dev.frostlake.http.rest.RestException;
import dev.frostlake.http.rest.RestIdentifier;
import dev.frostlake.http.rest.RestJson;
import dev.frostlake.http.rest.RestRow;
import dev.frostlake.http.rest.RestSql;
import dev.frostlake.storage.ResultSet;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the user-defined function and the procedure endpoints share: a routine's JSON from its SHOW row and its
 * DESCRIBE rows, the CREATE statement a request body describes, and the arguments and answers of a call.
 */
public final class RoutineSupport {

    /** The account's code for a call of a function or procedure that does not exist. */
    public static final String CODE_UNKNOWN_FUNCTION = "002141";

    /** The description SHOW reports for a function created without a comment. */
    private static final String DEFAULT_FUNCTION_DESCRIPTION = "user-defined function";
    /** The description SHOW reports for a procedure created without a comment. */
    private static final String DEFAULT_PROCEDURE_DESCRIPTION = "user-defined procedure";

    /** How a listing's {@code arguments} cell marks an argument that has a default. */
    private static final String DEFAULTED = "DEFAULT ";

    /** Static helpers only. */
    private RoutineSupport() {
    }

    // ---- reading ------------------------------------------------------------------------------------------

    /**
     * The DESCRIBE rows of one overload.
     *
     * @param kind {@code FUNCTION} or {@code PROCEDURE}
     * @throws RestException {@code 404} when the overload does not exist
     */
    public static List<RestRow> describe(final RestCall call, final String kind, final RestIdentifier name,
                                         final List<String> types) {
        return call.sql().show("DESCRIBE " + kind + " " + call.qualifiedSql(name) + "(" + String.join(", ", types)
            + ")");
    }

    /**
     * The default each argument of one overload declares, in order, as its DDL spells it: null for an argument
     * that declares none, and nothing at all when the DDL cannot be read.
     *
     * @param kind {@code FUNCTION} or {@code PROCEDURE}
     */
    public static List<String> defaults(final RestCall call, final String kind, final RestIdentifier name,
                                        final List<String> types) {
        try {
            final ResultSet ddl = call.sql().query("SELECT GET_DDL(" + RestSql.literal(kind) + ", "
                + RestSql.literal(call.qualifiedSql(name) + "(" + String.join(", ", types) + ")") + ")");
            if (ddl.getRows().isEmpty() || ddl.getRows().get(0).getValue(0) == null) {
                return new ArrayList<>();
            }
            return RoutineArgumentDefaults.of(ddl.getRows().get(0).getValue(0).toString());
        } catch (final RuntimeException unreadable) {
            return new ArrayList<>();
        }
    }

    /** A DESCRIBE property's value, or null when the answer has no such row. */
    public static String property(final List<RestRow> describe, final String property) {
        for (final RestRow row : describe) {
            if (property.equalsIgnoreCase(row.string("property"))) {
                return row.string("value");
            }
        }
        return null;
    }

    /** The argument types a SHOW row's {@code arguments} column lists, as {@code NAME(T1, T2) RETURN T}. */
    public static List<String> showArgumentTypes(final RestRow show) {
        final List<String> types = new ArrayList<>();
        final String arguments = show.string("arguments");
        if (arguments == null) {
            return types;
        }
        final String name = show.string("name");
        final int open = arguments.indexOf('(', name == null ? 0 : Math.min(name.length(), arguments.length()));
        if (open < 0) {
            return types;
        }
        final int close = closing(arguments, open);
        for (final String item : RoutineSignature.splitList(arguments.substring(open + 1, close))) {
            String plain = item.replace("[", "").replace("]", "").trim();
            if (plain.startsWith(DEFAULTED)) {
                // An argument that has a default is listed as DEFAULT <type>.
                plain = plain.substring(DEFAULTED.length()).trim();
            }
            if (!plain.isEmpty()) {
                types.add(RoutineSignature.typeOf(plain).toUpperCase(Locale.ROOT));
            }
        }
        return types;
    }

    /** The argument types a DESCRIBE {@code signature} row lists, as {@code (A T1, B T2)}. */
    public static List<String> signatureTypes(final List<RestRow> describe) {
        final List<String> types = new ArrayList<>();
        for (final String item : signatureItems(describe)) {
            types.add(RoutineSignature.typeOf(item).toUpperCase(Locale.ROOT));
        }
        return types;
    }

    private static List<String> signatureItems(final List<RestRow> describe) {
        final String signature = property(describe, "signature");
        if (signature == null) {
            return new ArrayList<>();
        }
        final int open = signature.indexOf('(');
        if (open < 0) {
            return new ArrayList<>();
        }
        return RoutineSignature.splitList(signature.substring(open + 1, closing(signature, open)));
    }

    /** Where the parenthesis opened at {@code open} closes; the text's end when it never does. */
    private static int closing(final String text, final int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            if (text.charAt(i) == '(') {
                depth++;
            } else if (text.charAt(i) == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return text.length();
    }

    /** Among a name's SHOW rows, the one whose argument types are the described overload's. */
    public static RestRow pick(final List<RestRow> shows, final List<String> types) {
        for (final RestRow show : shows) {
            if (sameTypes(showArgumentTypes(show), types)) {
                return show;
            }
        }
        for (final RestRow show : shows) {
            if (showArgumentTypes(show).size() == types.size()) {
                return show;
            }
        }
        return shows.isEmpty() ? null : shows.get(0);
    }

    private static boolean sameTypes(final List<String> left, final List<String> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!baseType(left.get(i)).equals(baseType(right.get(i)))) {
                return false;
            }
        }
        return true;
    }

    private static String baseType(final String type) {
        final int paren = type.indexOf('(');
        return (paren < 0 ? type : type.substring(0, paren)).trim().toUpperCase(Locale.ROOT);
    }

    /**
     * A routine as the {@code UserDefinedFunction} or {@code Procedure} schema describes it.
     *
     * @param show the routine's SHOW row
     * @param describe its DESCRIBE rows
     * @param procedure whether it is a procedure
     */
    public static ObjectNode toJson(final RestRow show, final List<RestRow> describe, final boolean procedure) {
        return toJson(show, describe, procedure, false);
    }

    /**
     * A routine as its schema describes it; a listing leaves the body out, as a null for a function and an empty
     * text for a procedure.
     */
    public static ObjectNode toJson(final RestRow show, final List<RestRow> describe, final boolean procedure,
                                    final boolean listing) {
        return toJson(show, describe, procedure, listing, new ArrayList<String>());
    }

    /**
     * A routine as its schema describes it, each argument with the default it declares.
     *
     * @param defaults each argument's default, in order (see {@link #defaults})
     */
    public static ObjectNode toJson(final RestRow show, final List<RestRow> describe, final boolean procedure,
                                    final boolean listing, final List<String> defaults) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", show, "name");
        if (!procedure) {
            RestJson.bool(node, "is_aggregate", show, "is_aggregate");
            RestJson.bool(node, "is_memoizable", show, "is_memoizable");
            RestJson.bool(node, "is_table_function", show, "is_table_function");
            node.putNull("valid_for_clustering");
            node.put("is_temporary", false);
        }
        RestJson.bool(node, "is_secure", show, "is_secure");
        if (procedure) {
            final String executeAs = property(describe, "execute as");
            if (executeAs != null && !executeAs.isEmpty()) {
                node.put("execute_as", executeAs.toUpperCase(Locale.ROOT));
            }
        }
        final ArrayNode arguments = RestJson.array();
        for (final String item : signatureItems(describe)) {
            final ObjectNode argument = RestJson.object();
            final String name = RoutineSignature.nameOf(item);
            RestJson.put(argument, "name", name);
            argument.put("datatype", RoutineSignature.typeOf(item));
            RestJson.put(argument, "default_value",
                arguments.size() < defaults.size() ? defaults.get(arguments.size()) : null);
            arguments.add(argument);
        }
        node.set("arguments", arguments);
        node.set("return_type", returnType(show, property(describe, "returns"), procedure));
        node.set("language_config", languageConfig(show, describe, procedure));
        final String description = show.nonEmpty("description");
        if (description != null && !DEFAULT_FUNCTION_DESCRIPTION.equals(description)
                && !DEFAULT_PROCEDURE_DESCRIPTION.equals(description)) {
            node.put("comment", description);
        }
        if (listing) {
            RestJson.put(node, "body", procedure ? "" : null);
        } else {
            RestJson.put(node, "body", property(describe, "body"));
        }
        RestJson.timestamp(node, "created_on", show, "created_on");
        RestJson.name(node, "schema_name", show, "schema_name");
        RestJson.name(node, "database_name", show, "catalog_name");
        RestJson.integer(node, "min_num_arguments", show, "min_num_arguments");
        RestJson.integer(node, "max_num_arguments", show, "max_num_arguments");
        RestJson.string(node, "owner", show, "owner");
        RestJson.string(node, "owner_role_type", show, "owner_role_type");
        if (procedure) {
            node.put("is_builtin", true);
        } else {
            RestJson.bool(node, "is_builtin", show, "is_builtin");
        }
        node.put("log_level", "OFF");
        node.put("trace_level", "OFF");
        if (procedure) {
            RestJson.nulls(node, "execute_as", "comment", "log_level", "trace_level");
        } else {
            RestJson.nulls(node, "is_temporary", "comment", "log_level", "trace_level");
        }
        return node;
    }

    private static ObjectNode returnType(final RestRow show, final String returns, final boolean procedure) {
        final ObjectNode type = RestJson.object();
        final String text = returns == null ? "" : returns.trim();
        final boolean table = text.toUpperCase(Locale.ROOT).startsWith("TABLE")
            || !procedure && Boolean.TRUE.equals(show.bool("is_table_function"));
        if (!table) {
            type.put("type", "DATATYPE");
            final String upper = text.toUpperCase(Locale.ROOT);
            if (upper.endsWith(" NOT NULL")) {
                type.put("datatype", text.substring(0, text.length() - " NOT NULL".length()).trim());
                type.put("nullable", false);
            } else {
                type.put("datatype", text);
                type.put("nullable", true);
            }
            return type;
        }
        type.put("type", "TABLE");
        final int open = text.indexOf('(');
        if (open >= 0) {
            final ArrayNode columns = RestJson.array();
            for (final String item : RoutineSignature.splitList(text.substring(open + 1, closing(text, open)))) {
                final ObjectNode column = RestJson.object();
                final String name = RoutineSignature.nameOf(item);
                RestJson.put(column, "name", name);
                column.put("datatype", RoutineSignature.typeOf(item));
                columns.add(column);
            }
            type.set("column_list", columns);
        }
        RestJson.nulls(type, "column_list");
        return type;
    }

    private static ObjectNode languageConfig(final RestRow show, final List<RestRow> describe,
                                             final boolean procedure) {
        final ObjectNode config = RestJson.object();
        String language = property(describe, "language");
        if (language == null) {
            language = show.nonEmpty("language");
        }
        language = language == null ? "SQL" : language.toUpperCase(Locale.ROOT);
        config.put("language", language);
        final String nullHandling = property(describe, "null handling");
        config.put("called_on_null_input", nullHandling == null
            || "CALLED ON NULL INPUT".equalsIgnoreCase(nullHandling.trim()));
        final String volatility = property(describe, "volatility");
        if (!procedure) {
            config.put("is_volatile", volatility == null || "VOLATILE".equalsIgnoreCase(volatility.trim()));
        }
        if ("JAVA".equals(language) || "PYTHON".equals(language) || "SCALA".equals(language)) {
            RestJson.put(config, "runtime_version", property(describe, "runtime_version"));
            RestJson.put(config, "handler", property(describe, "handler"));
            config.set("packages", textList(property(describe, "packages")));
            // A Python routine tells an empty list of imports, a Java or Scala one none at all.
            final ArrayNode imports = textList(property(describe, "imports"));
            if ("PYTHON".equals(language) || !imports.isEmpty()) {
                config.set("imports", imports);
            }
            RestJson.put(config, "target_path", property(describe, "target_path"));
            config.set("external_access_integrations", jsonList(show.nonEmpty("external_access_integrations")));
            config.set("secrets", secrets(show));
            if ("PYTHON".equals(language)) {
                RestJson.put(config, "artifact_repository", property(describe, "artifact_repository"));
                if (property(describe, "artifact_repository_packages") != null) {
                    config.set("artifact_repository_packages",
                        textList(property(describe, "artifact_repository_packages")));
                }
                RestJson.nulls(config, "artifact_repository", "artifact_repository_packages");
            } else {
                RestJson.nulls(config, "imports", "runtime_version", "target_path");
            }
        }
        RestJson.nulls(config, "called_on_null_input");
        if (!procedure) {
            RestJson.nulls(config, "is_volatile");
        }
        return config;
    }

    /** A listing cell that holds a JSON array, as that array; an empty array for an empty cell. */
    private static ArrayNode jsonList(final String cell) {
        if (cell != null) {
            try {
                final JsonNode parsed = RestJson.mapper().readTree(cell);
                if (parsed instanceof ArrayNode) {
                    return (ArrayNode) parsed;
                }
            } catch (final RuntimeException notJson) {
                return RestJson.array();
            }
        }
        return RestJson.array();
    }

    /**
     * The secrets a routine names, each by its full name: a listing names a secret from the routine's own
     * schema, bare within it and with its schema within the routine's database.
     */
    private static ObjectNode secrets(final RestRow show) {
        final ObjectNode out = RestJson.object();
        final String cell = show.nonEmpty("secrets");
        if (cell == null) {
            return out;
        }
        final JsonNode parsed;
        try {
            parsed = RestJson.mapper().readTree(cell);
        } catch (final RuntimeException notJson) {
            return out;
        }
        if (!(parsed instanceof ObjectNode)) {
            return out;
        }
        final String database = show.string("catalog_name");
        final String schema = show.string("schema_name");
        for (final Map.Entry<String, JsonNode> secret : parsed.properties()) {
            final String variable = secret.getKey();
            final String named = secret.getValue().asString();
            final int dots = named.length() - named.replace(".", "").length();
            out.put(variable, dots >= 2 ? named : dots == 1 ? database + "." + named
                : database + "." + schema + "." + named);
        }
        return out;
    }

    /** A list DESCRIBE writes as {@code ['a', 'b']} or {@code [a, b]}, as a JSON array of its items. */
    private static ArrayNode textList(final String text) {
        final ArrayNode out = RestJson.array();
        if (text == null) {
            return out;
        }
        String inner = text.trim();
        if (inner.startsWith("[") && inner.endsWith("]")) {
            inner = inner.substring(1, inner.length() - 1);
        }
        for (final String item : RoutineSignature.splitList(inner)) {
            String value = item.trim();
            if (value.length() >= 2 && value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') {
                value = value.substring(1, value.length() - 1);
            }
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    // ---- writing ------------------------------------------------------------------------------------------

    /**
     * The CREATE FUNCTION or CREATE PROCEDURE statement a request body describes. Body properties no statement
     * clause carries (external access integrations, secrets, a target path, an artifact repository) are left out.
     *
     * @throws RestException {@code 400} for a body missing a required property, {@code 501} for a routine kind the
     *     engine does not provide
     */
    public static String createSql(final RestCall call, final boolean procedure) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final StringBuilder sql = new StringBuilder("CREATE").append(mode.orReplace());
        if (Boolean.TRUE.equals(RestJson.bool(body, "is_aggregate"))) {
            throw RestException.notImplemented("Aggregate user-defined functions are not supported.");
        }
        if (procedure) {
            if (Boolean.TRUE.equals(RestJson.bool(body, "is_secure"))) {
                throw RestException.notImplemented("Secure procedures are not supported.");
            }
            sql.append(" PROCEDURE");
        } else {
            if (Boolean.TRUE.equals(RestJson.bool(body, "is_temporary"))) {
                sql.append(" TEMPORARY");
            }
            if (Boolean.TRUE.equals(RestJson.bool(body, "is_secure"))) {
                sql.append(" SECURE");
            }
            sql.append(" FUNCTION");
        }
        sql.append(mode.ifNotExists()).append(' ').append(call.qualifiedSql(name)).append('(');
        final JsonNode arguments = body.get("arguments");
        if (arguments == null || !arguments.isArray()) {
            throw RestException.badRequest("Missing required property 'arguments'.");
        }
        int count = 0;
        for (final JsonNode argument : arguments.values()) {
            if (count++ > 0) {
                sql.append(", ");
            }
            sql.append(RestJson.identifier(argument, "name", true).sql()).append(' ')
                .append(RoutineSignature.checkType(requiredText(argument, "datatype"), "arguments"));
            final String defaultValue = RestJson.text(argument, "default_value");
            if (defaultValue != null) {
                sql.append(" DEFAULT ").append(defaultValue);
            }
        }
        sql.append(") RETURNS ").append(returnsSql(body.get("return_type")));
        final JsonNode config = body.get("language_config");
        if (config == null || !config.isObject()) {
            throw RestException.badRequest("Missing required property 'language_config'.");
        }
        final String language = requiredText(config, "language").toUpperCase(Locale.ROOT);
        if (!"SQL".equals(language) && !"JAVA".equals(language) && !"PYTHON".equals(language)
                && !"SCALA".equals(language) && !"JAVASCRIPT".equals(language)) {
            throw RestException.badRequest("Invalid language '" + language + "'.");
        }
        sql.append(" LANGUAGE ").append(language);
        if (!procedure) {
            final Boolean calledOnNull = RestJson.bool(config, "called_on_null_input");
            if (calledOnNull != null) {
                sql.append(calledOnNull.booleanValue() ? " CALLED ON NULL INPUT" : " RETURNS NULL ON NULL INPUT");
            }
            final Boolean volatileFunction = RestJson.bool(config, "is_volatile");
            if (volatileFunction != null) {
                sql.append(volatileFunction.booleanValue() ? " VOLATILE" : " IMMUTABLE");
            }
            if (Boolean.TRUE.equals(RestJson.bool(body, "is_memoizable"))) {
                sql.append(" MEMOIZABLE");
            }
        }
        final String runtime = RestJson.text(config, "runtime_version");
        if (runtime != null) {
            sql.append(" RUNTIME_VERSION = ").append(RestSql.literal(runtime));
        }
        final String repository = RestJson.text(config, "artifact_repository");
        if (repository != null) {
            sql.append(" ARTIFACT_REPOSITORY = ").append(namePath(repository, "artifact_repository"));
        }
        // A Python routine's packages are written in one list, whichever of the two the body names them in.
        final List<String> packages = new ArrayList<>();
        addAll(packages, RestJson.strings(config, "packages"));
        addAll(packages, RestJson.strings(config, "artifact_repository_packages"));
        appendList(sql, packages, "PACKAGES");
        appendList(sql, RestJson.strings(config, "imports"), "IMPORTS");
        final String handler = RestJson.text(config, "handler");
        if (handler != null) {
            sql.append(" HANDLER = ").append(RestSql.literal(handler));
        }
        final String targetPath = RestJson.text(config, "target_path");
        if (targetPath != null) {
            sql.append(" TARGET_PATH = ").append(RestSql.literal(targetPath));
        }
        final List<String> integrations = RestJson.strings(config, "external_access_integrations");
        if (integrations != null && !integrations.isEmpty()) {
            sql.append(" EXTERNAL_ACCESS_INTEGRATIONS = (");
            for (int i = 0; i < integrations.size(); i++) {
                sql.append(i > 0 ? ", " : "").append(namePath(integrations.get(i), "external_access_integrations"));
            }
            sql.append(')');
        }
        final JsonNode secrets = config.get("secrets");
        if (secrets != null && secrets.isObject() && !secrets.isEmpty()) {
            sql.append(" SECRETS = (");
            int written = 0;
            for (final Map.Entry<String, JsonNode> secret : secrets.properties()) {
                sql.append(written++ > 0 ? ", " : "").append(RestSql.literal(secret.getKey())).append(" = ")
                    .append(namePath(secret.getValue().asString(), "secrets"));
            }
            sql.append(')');
        }
        final String comment = RestJson.text(body, "comment");
        if (comment != null) {
            sql.append(" COMMENT = ").append(RestSql.literal(comment));
        }
        if (procedure) {
            final String executeAs = RestJson.text(body, "execute_as");
            if (executeAs != null) {
                final String owner = executeAs.trim().toUpperCase(Locale.ROOT);
                if (!"OWNER".equals(owner) && !"CALLER".equals(owner) && !"RESTRICTED CALLER".equals(owner)) {
                    throw RestException.notImplemented("EXECUTE AS " + owner + " is not supported.");
                }
                sql.append(" EXECUTE AS ").append(owner);
            }
        }
        final String definition = RestJson.text(body, "body");
        if (definition != null) {
            sql.append(" AS ").append(RestSql.literal(definition));
        }
        return sql.toString();
    }

    private static String requiredText(final JsonNode node, final String property) {
        final String value = RestJson.text(node, property);
        if (value == null) {
            throw RestException.badRequest("Missing required property '" + property + "'.");
        }
        return value;
    }

    private static void addAll(final List<String> into, final List<String> values) {
        if (values != null) {
            into.addAll(values);
        }
    }

    /**
     * A dotted name a body gives - an artifact repository, an integration, a secret - as the statement writes it:
     * each part a plain identifier, so nothing but a name reaches the statement.
     *
     * @throws RestException {@code 400} for a value that is no such name
     */
    private static String namePath(final String value, final String property) {
        final String name = value == null ? "" : value.trim();
        boolean plain = !name.isEmpty();
        for (final String part : name.split("\\.", -1)) {
            plain = plain && !part.isEmpty();
            for (int i = 0; i < part.length(); i++) {
                final char c = part.charAt(i);
                plain = plain && (c == '_' || c == '$' && i > 0 || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'
                    || c >= '0' && c <= '9' && i > 0);
            }
        }
        if (!plain) {
            throw RestException.badRequest("Invalid value '" + name + "' for property '" + property + "'.");
        }
        return name;
    }

    private static void appendList(final StringBuilder sql, final List<String> values, final String keyword) {
        if (values == null || values.isEmpty()) {
            return;
        }
        sql.append(' ').append(keyword).append(" = (");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(RestSql.literal(values.get(i)));
        }
        sql.append(')');
    }

    private static String returnsSql(final JsonNode returnType) {
        if (returnType == null || !returnType.isObject()) {
            throw RestException.badRequest("Missing required property 'return_type'.");
        }
        final String type = RestJson.text(returnType, "type");
        if (type != null && "TABLE".equalsIgnoreCase(type.trim())) {
            final StringBuilder table = new StringBuilder("TABLE (");
            final JsonNode columns = returnType.get("column_list");
            int count = 0;
            if (columns != null && columns.isArray()) {
                for (final JsonNode column : columns.values()) {
                    if (count++ > 0) {
                        table.append(", ");
                    }
                    table.append(RestJson.identifier(column, "name", true).sql()).append(' ')
                        .append(RoutineSignature.checkType(requiredText(column, "datatype"), "column_list"));
                }
            }
            return table.append(')').toString();
        }
        if (type != null && !"DATATYPE".equalsIgnoreCase(type.trim())) {
            throw RestException.badRequest("Invalid return type '" + type + "': expected DATATYPE or TABLE.");
        }
        final String datatype = RoutineSignature.checkType(requiredText(returnType, "datatype"), "return_type");
        return Boolean.FALSE.equals(RestJson.bool(returnType, "nullable")) ? datatype + " NOT NULL" : datatype;
    }

    // ---- calling ------------------------------------------------------------------------------------------

    /**
     * A call's argument list: each argument's value cast to its declared type, passed by name when every argument
     * names itself and by position otherwise.
     */
    public static String arguments(final JsonNode arguments) {
        if (arguments == null || arguments.isNull()) {
            return "()";
        }
        if (!arguments.isArray()) {
            throw RestException.badRequest("The call arguments must be a JSON array.");
        }
        boolean named = true;
        for (final JsonNode argument : arguments.values()) {
            if (!RestJson.present(argument, "name")) {
                named = false;
            }
        }
        final StringBuilder sql = new StringBuilder("(");
        int count = 0;
        for (final JsonNode argument : arguments.values()) {
            if (!argument.isObject()) {
                throw RestException.badRequest("A call argument must be a JSON object.");
            }
            if (count++ > 0) {
                sql.append(", ");
            }
            if (named) {
                sql.append(RestIdentifier.display(RestJson.identifier(argument, "name", true).name()))
                    .append(" => ");
            }
            sql.append(value(argument));
        }
        return sql.append(')').toString();
    }

    private static String value(final JsonNode argument) {
        final JsonNode value = argument.get("value");
        final String literal;
        if (value == null || value.isNull() || value.isMissingNode()) {
            literal = "NULL";
        } else if (value.isString()) {
            literal = RestSql.literal(value.stringValue());
        } else if (value.isBoolean()) {
            literal = value.booleanValue() ? "TRUE" : "FALSE";
        } else if (value.isNumber()) {
            literal = value.toString();
        } else {
            literal = "PARSE_JSON(" + RestSql.literal(value.toString()) + ")";
        }
        final String datatype = RestJson.text(argument, "datatype");
        if (datatype == null) {
            return literal;
        }
        return "CAST(" + literal + " AS " + RoutineSignature.checkType(datatype, "datatype") + ")";
    }

    /**
     * Runs a call, reporting a routine the statement cannot find as the account does: {@code 400} with the
     * unknown-function code.
     */
    public static ResultSet call(final RestCall call, final String sql) {
        try {
            return call.sql().query(sql);
        } catch (final RestException refusal) {
            if (refusal.getMessage() != null
                    && refusal.getMessage().toLowerCase(Locale.ROOT).contains("unknown user-defined function")) {
                throw new RestException(400, refusal.getMessage(), CODE_UNKNOWN_FUNCTION);
            }
            throw refusal;
        }
    }

    /** A result's rows as JSON objects keyed by column name in lower case, each value in its text form. */
    public static ArrayNode rows(final ResultSet set) {
        final ResultSetData data = ResultSetData.from(set);
        final ArrayNode out = RestJson.array();
        for (final List<Object> row : data.getRows()) {
            final ObjectNode item = RestJson.object();
            for (int i = 0; i < row.size() && i < set.getColumns().size(); i++) {
                final Object cell = row.get(i);
                final String column = set.getColumns().get(i).getName().toLowerCase(Locale.ROOT);
                if (cell == null) {
                    item.putNull(column);
                } else {
                    item.put(column, String.valueOf(cell));
                }
            }
            out.add(item);
        }
        return out;
    }
}
