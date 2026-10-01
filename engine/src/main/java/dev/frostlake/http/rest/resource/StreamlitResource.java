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
import dev.frostlake.http.rest.RestStatement;
import dev.frostlake.http.rest.RestTags;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Streamlit apps ({@code streamlit.yaml}, {@code /api/v2/databases/{database}/schemas/{schema}/streamlits}):
 * list, create, fetch, delete, {@code :undrop}, {@code :rename}, the version actions {@code :add-live-version}
 * ({@code ALTER STREAMLIT … ADD LIVE VERSION [<alias>] FROM LAST}), {@code :commit}, {@code :abort} and
 * {@code :add-version} ({@code ADD VERSION [IF NOT EXISTS] <alias> FROM '<source_location>'}), and the Git actions
 * {@code :add-version-from-git} ({@code ADD VERSION <alias> FROM '<git_ref>'}), {@code :pull} and {@code :push},
 * which Frostlake answers as the account does for an app whose versions come from no Git repository. An app is read
 * from {@code SHOW STREAMLITS} and {@code DESCRIBE STREAMLIT}.
 *
 * <p>Not provided, answering {@code 501}: {@code :add-live-version} with {@code fromLast=false}, and the tag
 * endpoints (Streamlit apps take no tags).
 */
public final class StreamlitResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/streamlits";
    private static final String ITEM = COLLECTION + "/{name}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listStreamlits", this);
        router.add("POST", COLLECTION, "createStreamlit", this);
        router.add("GET", ITEM, "fetchStreamlit", this);
        router.add("DELETE", ITEM, "deleteStreamlit", this);
        router.add("POST", ITEM + ":undrop", "undropStreamlit", this);
        router.add("POST", ITEM + ":rename", "renameStreamlit", this);
        router.add("POST", ITEM + ":add-live-version", "addLiveVersionStreamlit", this);
        router.add("POST", ITEM + ":commit", "commitStreamlit", this);
        router.add("POST", ITEM + ":add-version", "addVersionStreamlit", this);
        router.add("POST", ITEM + ":add-version-from-git", "addVersionFromGitStreamlit", this);
        router.add("POST", ITEM + ":abort", "abortStreamlit", this);
        router.add("POST", ITEM + ":pull", "pullStreamlit", this);
        router.add("POST", ITEM + ":push", "pushStreamlit", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if ("getTags".equals(call.operation())) {
            return AppObjectRest.noTags(call, "STREAMLITS", "Streamlit");
        }
        if (RestTags.handles(call)) {
            throw RestException.notImplemented("Setting tags on Streamlit apps is not supported.");
        }
        switch (call.operation()) {
            case "listStreamlits":
                return list(call);
            case "createStreamlit":
                return create(call);
            case "fetchStreamlit":
                return fetch(call);
            case "deleteStreamlit":
                return call.sql().action("DROP STREAMLIT" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")));
            case "undropStreamlit":
                return call.sql().action("UNDROP STREAMLIT " + call.qualifiedSql(call.identifier("name")));
            case "renameStreamlit":
                return AppObjectRest.rename(call, "STREAMLIT");
            case "addLiveVersionStreamlit":
                if (!call.flag("fromLast", true)) {
                    throw RestException.notImplemented("A live version can only be added from the last version "
                        + "(fromLast=true).");
                }
                final JsonNode live = version(call.bodyOrEmpty());
                return alter(call, "ADD LIVE VERSION" + AppObjectRest.alias(live, "name", false) + " FROM LAST"
                    + AppObjectRest.comment(RestJson.text(live, "comment")));
            case "commitStreamlit":
                return alter(call, "COMMIT" + AppObjectRest.comment(RestJson.text(version(call.bodyOrEmpty()),
                    "comment")));
            case "abortStreamlit":
                return alter(call, "ABORT");
            case "addVersionStreamlit":
                return addVersion(call);
            case "addVersionFromGitStreamlit":
                return addVersionFromGit(call);
            case "pullStreamlit":
                return alter(call, "PULL");
            case "pushStreamlit":
                return alter(call, push(call.bodyOrEmpty()));
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    /** An ALTER STREAMLIT action; a missing live version carries the account's code for it. */
    private static RestResponse alter(final RestCall call, final String action) {
        return AppObjectRest.versionAction(call, "STREAMLIT", action);
    }

    /** A request body's {@code version} object, or an empty one. */
    private static JsonNode version(final JsonNode body) {
        final JsonNode version = body.get("version");
        return version != null && version.isObject() ? version : RestJson.object();
    }

    /** {@code :add-version}: a version copied from {@code source_location}, named by {@code version.name}. */
    private static RestResponse addVersion(final RestCall call) {
        final JsonNode body = call.body();
        final String source = RestJson.text(body, "source_location");
        if (source == null) {
            throw RestException.missingProperty("source_location");
        }
        final JsonNode version = version(body);
        final Boolean ifNotExists = RestJson.bool(version, "ifNotExists");
        return alter(call, "ADD VERSION" + (Boolean.TRUE.equals(ifNotExists) ? " IF NOT EXISTS" : "")
            + AppObjectRest.alias(version, "name", false) + " FROM " + RestSql.literal(source)
            + AppObjectRest.comment(RestJson.text(version, "comment")));
    }

    /** {@code :add-version-from-git}: a version copied from the Git reference {@code git_ref}. */
    private static RestResponse addVersionFromGit(final RestCall call) {
        final JsonNode body = call.body();
        final String reference = RestJson.text(body, "git_ref");
        if (reference == null) {
            throw RestException.missingProperty("git_ref");
        }
        final JsonNode version = version(body);
        return alter(call, "ADD VERSION" + AppObjectRest.alias(version, "name", true) + " FROM "
            + RestSql.literal(reference) + AppObjectRest.comment(RestJson.text(version, "comment")));
    }

    /**
     * {@code :push}'s statement from its options: the branch, the credentials — a secret for {@code CREDENTIALS}, a
     * user name and password for {@code USERNAME_PASSWORD} — the author, and the push comment. A body that sets none
     * pushes to the version's own branch.
     */
    private static String push(final JsonNode options) {
        final StringBuilder sql = new StringBuilder("PUSH");
        final String branch = RestJson.text(options, "to_git_branch_uri");
        if (branch != null) {
            sql.append(" TO ").append(RestSql.literal(branch));
        }
        final String secret = RestJson.text(options, "git_credentials");
        if (secret != null) {
            final StringBuilder name = new StringBuilder();
            for (final String part : secret.split("\\.", -1)) {
                name.append(name.length() > 0 ? "." : "").append(RestIdentifier.parse(part, "git_credentials").sql());
            }
            sql.append(" GIT_CREDENTIALS = ").append(name);
        }
        final String[][] texts = {
            {"git_username", "USERNAME"}, {"git_password", "PASSWORD"}, {"git_author_name", "NAME"},
            {"git_author_email", "EMAIL"}, {"git_push_comment", "COMMENT"},
        };
        for (final String[] text : texts) {
            final String value = RestJson.text(options, text[0]);
            if (value != null) {
                sql.append(' ').append(text[1]).append(" = ").append(RestSql.literal(value));
            }
        }
        return sql.toString();
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW STREAMLITS" + RestShow.like(call) + " IN SCHEMA "
                + call.schemaSql() + RestShow.tail(call))) {
            out.add(toJson(row, AppObjectRest.describeRow(call, "STREAMLIT",
                RestIdentifier.ofResolved(row.string("name")))));
        }
        return RestResponse.json(200, out);
    }

    private static RestResponse fetch(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        final RestRow row = AppObjectRest.showRow(call, "STREAMLITS", "Streamlit", name);
        return RestResponse.json(200, toJson(row, AppObjectRest.describeRow(call, "STREAMLIT", name)));
    }

    /** The app as the {@code Streamlit} schema describes it: every property, null when unknown. */
    private static ObjectNode toJson(final RestRow row, final RestRow described) {
        final ObjectNode node = RestJson.object();
        AppObjectRest.showProperties(node, row);
        RestJson.string(node, "title", row, "title");
        RestJson.string(node, "main_file", described, "main_file");
        RestJson.string(node, "runtime_name", described, "runtime_name");
        RestJson.name(node, "compute_pool", described, "compute_pool");
        AppObjectRest.list(node, "imports", described, "import_urls");
        AppObjectRest.list(node, "external_access_integrations", described, "external_access_integrations");
        AppObjectRest.commaList(node, "default_packages", described, "default_packages");
        AppObjectRest.commaList(node, "user_packages", described, "user_packages");
        AppObjectRest.list(node, "artifact_repositories", row, "artifact_repositories");
        node.set("scheduled_tasks", RestJson.array());
        RestJson.string(node, "source_location", described, "root_location");
        AppObjectRest.versions(node, described);
        RestJson.nulls(node, "imports", "external_access_integrations", "external_access_secrets", "code_warehouse",
            "runtime_environment_version", "execute_as", "execute_as_role", "idle_auto_shutdown_time_seconds",
            "scheduled_tasks", "min_instances", "max_instances", "artifact_repositories", "default_packages",
            "user_packages", "default_version_details", "last_version_details");
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " STREAMLIT"
            + mode.ifNotExists() + " " + call.qualifiedSql(name));
        final String from = RestJson.text(body, "source_location");
        if (from != null) {
            sql.append(" FROM " + RestSql.literal(from));
        }
        sql.string(body, "main_file", "MAIN_FILE");
        sql.identifier(body, "query_warehouse", "QUERY_WAREHOUSE");
        sql.string(body, "comment", "COMMENT");
        sql.string(body, "title", "TITLE");
        sql.stringList(body, "imports", "IMPORTS");
        sql.append(AppObjectRest.identifierList(body, "external_access_integrations",
            "EXTERNAL_ACCESS_INTEGRATIONS"));
        return call.sql().action(sql.toString());
    }
}
