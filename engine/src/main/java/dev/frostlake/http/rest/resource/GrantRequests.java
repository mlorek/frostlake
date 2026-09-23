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
import dev.frostlake.http.rest.RestResponse;
import dev.frostlake.http.rest.RestRow;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The {@code Grant} bodies the user, role and database-role resources share, translated into GRANT and REVOKE
 * statements, and the SHOW GRANTS rows they answer with. A grant names a securable ({@code securable_type} and
 * {@code securable}), or all of a kind in a scope ({@code containing_scope} without a securable); privileges and
 * kinds are SQL words, checked before they are written, and names are quoted identifiers.
 */
public final class GrantRequests {

    private static final Pattern WORDS = Pattern.compile("[A-Za-z][A-Za-z_ ]*");

    /** Static helpers only. */
    private GrantRequests() {
    }

    /**
     * A kind or privilege as SQL words: upper case, underscores and runs of spaces read as single spaces.
     *
     * @throws RestException {@code 400} when the text is not made of words
     */
    public static String words(final String text, final String what) {
        if (text == null || !WORDS.matcher(text.trim()).matches()) {
            throw RestException.badRequest("Invalid " + what + ": '" + text + "'.");
        }
        return text.trim().replace('_', ' ').replaceAll(" +", " ").toUpperCase(Locale.ROOT);
    }

    /** The plural a bulk grant names a kind by: TABLE is TABLES, POLICY is POLICIES. */
    public static String plural(final String kind) {
        if (kind.endsWith("Y")) {
            return kind.substring(0, kind.length() - 1) + "IES";
        }
        return kind.endsWith("S") ? kind : kind + "S";
    }

    /** The privileges a body lists, as SQL, e.g. {@code SELECT, INSERT}. */
    public static String privileges(final JsonNode body) {
        final List<String> listed = RestJson.strings(body, "privileges");
        if (listed == null || listed.isEmpty()) {
            throw RestException.badRequest("Missing required property 'privileges'.");
        }
        final StringBuilder out = new StringBuilder();
        for (final String privilege : listed) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(words(privilege, "privilege"));
        }
        return out.toString();
    }

    /** The body's securable kind as SQL words. */
    public static String kind(final JsonNode body) {
        final String type = RestJson.text(body, "securable_type");
        if (type == null) {
            throw RestException.badRequest("Missing required property 'securable_type'.");
        }
        return words(type, "securable_type");
    }

    /**
     * A dotted name as SQL: each part an identifier as the API writes it, quoted. {@code db.schema.table} is three
     * identifiers; a quoted part may hold dots.
     */
    public static String dotted(final String text, final String what) {
        final List<String> parts = new ArrayList<>();
        final StringBuilder part = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < (text == null ? 0 : text.length()); i++) {
            final char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            }
            if (c == '.' && !quoted) {
                parts.add(part.toString());
                part.setLength(0);
            } else {
                part.append(c);
            }
        }
        parts.add(part.toString());
        final StringBuilder out = new StringBuilder();
        for (final String name : parts) {
            if (out.length() > 0) {
                out.append('.');
            }
            out.append(RestIdentifier.parse(name, what).sql());
        }
        return out.toString();
    }

    /** The securable a body names, as a qualified SQL name: its database, schema and name as far as given. */
    public static String securable(final JsonNode body, final String kind) {
        final JsonNode securable = body.get("securable");
        if (securable == null || !securable.isObject()) {
            throw RestException.badRequest("Missing required property 'securable'.");
        }
        final RestIdentifier database = RestJson.identifier(securable, "database", false);
        final RestIdentifier schema = RestJson.identifier(securable, "schema", false);
        final RestIdentifier name = RestJson.identifier(securable, "name", false);
        final StringBuilder out = new StringBuilder();
        if (database != null && !("DATABASE".equals(kind) && name == null)) {
            out.append(database.sql());
        }
        if (schema != null && !("SCHEMA".equals(kind) && name == null)) {
            out.append(out.length() > 0 ? "." : "").append(schema.sql());
        }
        if (name != null) {
            out.append(out.length() > 0 ? "." : "").append(name.sql());
        } else if ("DATABASE".equals(kind) && database != null) {
            out.append(database.sql());
        } else if ("SCHEMA".equals(kind) && schema != null) {
            out.append(out.length() > 0 ? "." : "").append(schema.sql());
        } else {
            throw RestException.badRequest("Missing required property 'securable.name'.");
        }
        return out.toString();
    }

    /** {@code IN DATABASE d} or {@code IN SCHEMA d.s} for the body's containing scope. */
    public static String scope(final JsonNode body) {
        final JsonNode scope = body.get("containing_scope");
        if (scope == null || !scope.isObject()) {
            throw RestException.badRequest("Missing required property 'containing_scope'.");
        }
        final RestIdentifier database = RestJson.identifier(scope, "database", true);
        final RestIdentifier schema = RestJson.identifier(scope, "schema", false);
        return schema != null ? "IN SCHEMA " + database.sql() + "." + schema.sql() : "IN DATABASE " + database.sql();
    }

    /** The {@code mode} or {@code deleteMode} parameter as the REVOKE tail: RESTRICT, CASCADE or nothing. */
    public static String mode(final RestCall call, final String parameter) {
        final String mode = call.query(parameter);
        if (mode == null || mode.isEmpty()) {
            return "";
        }
        if ("restrict".equalsIgnoreCase(mode)) {
            return " RESTRICT";
        }
        if ("cascade".equalsIgnoreCase(mode)) {
            return " CASCADE";
        }
        // The account takes any other value as no mode at all.
        return "";
    }

    /**
     * The GRANT a {@code Grant} body asks for, to a grantee.
     *
     * @param grantee the grantee as SQL, e.g. {@code ROLE "R"}
     * @param future whether the endpoint grants on future objects
     */
    public static String grant(final JsonNode body, final String grantee, final boolean future) {
        final String kind = kind(body);
        final boolean option = Boolean.TRUE.equals(RestJson.bool(body, "grant_option"));
        if (!future && ("ROLE".equals(kind) || "DATABASE ROLE".equals(kind))) {
            return "GRANT " + kind + " " + securable(body, kind) + " TO " + grantee;
        }
        final StringBuilder sql = new StringBuilder("GRANT ").append(privileges(body)).append(" ON ");
        target(sql, body, kind, future);
        sql.append(" TO ").append(grantee);
        if (option) {
            sql.append(" WITH GRANT OPTION");
        }
        return sql.toString();
    }

    /**
     * A role's grant of another role or a database role names privileges: without them the account refuses the
     * request with the syntax error its translation hits, whatever the kind is spelled as.
     *
     * @throws RestException {@code 400} code {@code 001003} when the body names a role kind and no privileges
     */
    public static void requirePrivilegesForRoleGrant(final JsonNode body) {
        final String kind = kind(body);
        final List<String> listed = RestJson.strings(body, "privileges");
        if (("ROLE".equals(kind) || "DATABASE ROLE".equals(kind)) && (listed == null || listed.isEmpty())) {
            throw new RestException(400, "\nsyntax error line 1 at position 12 unexpected 'on'.", "001003");
        }
    }

    /**
     * The REVOKE a {@code Grant} body asks for, from a grantee: of the grant option alone when the body sets
     * {@code grant_option}.
     *
     * @param grantee the grantee as SQL, e.g. {@code ROLE "R"}
     * @param future whether the endpoint revokes future grants
     * @param mode the RESTRICT or CASCADE tail, or nothing
     */
    public static String revoke(final JsonNode body, final String grantee, final boolean future, final String mode) {
        final String kind = kind(body);
        if (!future && ("ROLE".equals(kind) || "DATABASE ROLE".equals(kind))) {
            return "REVOKE " + kind + " " + securable(body, kind) + " FROM " + grantee;
        }
        final boolean option = Boolean.TRUE.equals(RestJson.bool(body, "grant_option"));
        final StringBuilder sql = new StringBuilder("REVOKE ").append(option ? "GRANT OPTION FOR " : "")
            .append(privileges(body)).append(" ON ");
        target(sql, body, kind, future);
        return sql.append(" FROM ").append(grantee).append(mode).toString();
    }

    private static void target(final StringBuilder sql, final JsonNode body, final String kind, final boolean future) {
        if (future) {
            sql.append("FUTURE ").append(plural(kind)).append(' ').append(scope(body));
        } else if ("ACCOUNT".equals(kind)) {
            sql.append("ACCOUNT");
        } else if (!RestJson.present(body, "securable") && RestJson.present(body, "containing_scope")) {
            sql.append("ALL ").append(plural(kind)).append(' ').append(scope(body));
        } else {
            sql.append(kind).append(' ').append(securable(body, kind));
        }
    }

    // ---- responses ------------------------------------------------------------------------------------------

    /** A name from a listing split into its parts, each spelled as the API spells names. */
    public static List<String> nameParts(final String name) {
        final List<String> parts = new ArrayList<>();
        final StringBuilder part = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (c == '.' && !quoted) {
                parts.add(part.toString());
                part.setLength(0);
            } else {
                part.append(c);
            }
        }
        parts.add(part.toString());
        return parts;
    }

    /**
     * The {@code securable} object of a listed grant: its database, schema and name, and {@code service}, every
     * property present and null where the name has no such part, as the account answers. A future grant's name
     * ends in the kind's placeholder, {@code <TABLE>}, which the account spells quoted.
     */
    public static ObjectNode securableJson(final String kind, final String name) {
        final ObjectNode securable = RestJson.object();
        final List<String> parts = nameParts(name);
        final int last = parts.size() - 1;
        RestJson.put(securable, "database", last >= 1 ? RestIdentifier.display(parts.get(0)) : null);
        RestJson.put(securable, "name",
            "ACCOUNT".equals(kind) ? parts.get(last) : RestIdentifier.display(parts.get(last)));
        RestJson.put(securable, "schema", last >= 2 ? RestIdentifier.display(parts.get(1)) : null);
        securable.putNull("service");
        return securable;
    }

    /**
     * A SHOW GRANTS TO row as a {@code Grant}: the securable and its kind, the privilege, the grant option, when
     * and by whom. A grant names a securable, so its {@code containing_scope} is null.
     */
    public static ObjectNode grantJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        final String kind = row.nonEmpty("granted_on") == null ? "" : row.string("granted_on").replace('_', ' ');
        node.put("securable_type", kind);
        final String name = row.nonEmpty("name");
        if (name != null) {
            node.set("securable", securableJson(kind, name));
        } else {
            node.putNull("securable");
        }
        node.putNull("containing_scope");
        final ArrayNode privileges = RestJson.array();
        privileges.add(row.string("privilege"));
        node.set("privileges", privileges);
        RestJson.bool(node, "grant_option", row, "grant_option");
        RestJson.timestamp(node, "created_on", row, "created_on");
        // A grant made by no one, a database role's USAGE on its own database, answers an empty grantor.
        RestJson.put(node, "granted_by", row.string("granted_by"));
        return node;
    }

    /**
     * A SHOW FUTURE GRANTS row as a {@code Grant}, as the account answers it: the securable is the listing's
     * name, {@code <DB>.[<SCHEMA>.]<KIND>}, its last part the quoted placeholder; there is no containing scope and
     * no grantor.
     */
    public static ObjectNode futureGrantJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        final String kind = row.string("grant_on").replace('_', ' ');
        node.put("securable_type", kind);
        node.set("securable", securableJson(kind, row.string("name")));
        node.putNull("containing_scope");
        final ArrayNode privileges = RestJson.array();
        privileges.add(row.string("privilege"));
        node.set("privileges", privileges);
        RestJson.bool(node, "grant_option", row, "grant_option");
        RestJson.timestamp(node, "created_on", row, "created_on");
        node.putNull("granted_by");
        return node;
    }

    /**
     * A listing of grants as a JSON array, cut to {@code showLimit} rows when the call gives one.
     *
     * @param rows the listing's rows
     * @param future whether the rows are future grants
     */
    public static RestResponse list(final RestCall call, final List<RestRow> rows, final boolean future) {
        final Long limit = call.integer("showLimit");
        final ArrayNode out = RestJson.array();
        for (final RestRow row : rows) {
            if (limit != null && out.size() >= limit.longValue()) {
                break;
            }
            out.add(future ? futureGrantJson(row) : grantJson(row));
        }
        return RestResponse.json(200, out);
    }
}
