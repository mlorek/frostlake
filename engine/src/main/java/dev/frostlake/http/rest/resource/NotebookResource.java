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
 * Notebooks ({@code notebook.yaml}, {@code /api/v2/databases/{database}/schemas/{schema}/notebooks}): list,
 * create, fetch, delete, {@code :rename}, {@code :execute} ({@code EXECUTE NOTEBOOK}, which runs no notebook code
 * and answers as a run that succeeded), and the version actions {@code :add-live-version}
 * ({@code ALTER NOTEBOOK … ADD LIVE VERSION FROM LAST}) and {@code :commit}, each with its {@code comment}. A
 * notebook is read from {@code SHOW NOTEBOOKS} and {@code DESCRIBE NOTEBOOK}.
 *
 * <p>Not provided, answering {@code 501}: {@code :add-live-version} with {@code fromLast=false}, {@code :commit}
 * naming a {@code version} (the SQL's COMMIT takes no version name), and the tag endpoints (notebooks take no tags).
 */
public final class NotebookResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/notebooks";
    private static final String ITEM = COLLECTION + "/{name}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listNotebooks", this);
        router.add("POST", COLLECTION, "createNotebook", this);
        router.add("GET", ITEM, "fetchNotebook", this);
        router.add("DELETE", ITEM, "deleteNotebook", this);
        router.add("POST", ITEM + ":execute", "executeNotebook", this);
        router.add("POST", ITEM + ":rename", "renameNotebook", this);
        router.add("POST", ITEM + ":add-live-version", "addLiveVersionNotebook", this);
        router.add("POST", ITEM + ":commit", "commitNotebook", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if ("getTags".equals(call.operation())) {
            return AppObjectRest.noTags(call, "NOTEBOOKS", "Notebook");
        }
        if (RestTags.handles(call)) {
            throw RestException.notImplemented("Setting tags on notebooks is not supported.");
        }
        switch (call.operation()) {
            case "listNotebooks":
                return list(call);
            case "createNotebook":
                return create(call);
            case "fetchNotebook":
                return fetch(call);
            case "deleteNotebook":
                return delete(call);
            case "renameNotebook":
                return AppObjectRest.rename(call, "NOTEBOOK");
            case "executeNotebook":
                return call.sql().action("EXECUTE NOTEBOOK " + call.qualifiedSql(call.identifier("name")) + "()");
            case "addLiveVersionNotebook":
                if (!call.flag("fromLast", true)) {
                    throw RestException.notImplemented("A live version can only be added from the last version "
                        + "(fromLast=true).");
                }
                return AppObjectRest.versionAction(call, "NOTEBOOK", "ADD LIVE VERSION FROM LAST"
                    + AppObjectRest.comment(call.query("comment")));
            case "commitNotebook":
                if (call.query("version") != null) {
                    throw RestException.notImplemented("Naming the committed version is not supported: the SQL's "
                        + "COMMIT takes no version name.");
                }
                return AppObjectRest.versionAction(call, "NOTEBOOK", "COMMIT"
                    + AppObjectRest.comment(call.query("comment")));
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW NOTEBOOKS" + RestShow.like(call) + " IN SCHEMA "
                + call.schemaSql() + RestShow.tail(call))) {
            out.add(toJson(row, AppObjectRest.describeRow(call, "NOTEBOOK",
                RestIdentifier.ofResolved(row.string("name")))));
        }
        return RestResponse.json(200, out);
    }

    private static RestResponse fetch(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        final RestRow row = AppObjectRest.showRow(call, "NOTEBOOKS", "Notebook", name);
        return RestResponse.json(200, toJson(row, AppObjectRest.describeRow(call, "NOTEBOOK", name)));
    }

    /** The notebook as the {@code Notebook} schema describes it: every property, null when unknown. */
    private static ObjectNode toJson(final RestRow row, final RestRow described) {
        final ObjectNode node = RestJson.object();
        AppObjectRest.showProperties(node, row);
        RestJson.string(node, "main_file", described, "main_file");
        RestJson.string(node, "title", described, "title");
        RestJson.string(node, "default_packages", described, "default_packages");
        RestJson.put(node, "user_packages", described.string("user_packages"));
        RestJson.string(node, "runtime_name", described, "runtime_name");
        RestJson.string(node, "compute_pool", described, "compute_pool");
        AppObjectRest.list(node, "import_urls", described, "import_urls");
        AppObjectRest.list(node, "external_access_integrations", described, "external_access_integrations");
        RestJson.integer(node, "idle_auto_shutdown_time_seconds", described, "idle_auto_shutdown_time_seconds");
        AppObjectRest.versions(node, described);
        RestJson.nulls(node, "version", "fromLocation", "import_urls", "external_access_integrations", "budget",
            "external_access_secrets",
            "default_version_details", "last_version_details");
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " NOTEBOOK" + mode.ifNotExists()
            + " " + call.qualifiedSql(name));
        final String from = RestJson.text(body, "fromLocation");
        if (from != null) {
            sql.append(" FROM " + RestSql.literal(from));
        }
        sql.string(body, "main_file", "MAIN_FILE");
        sql.string(body, "comment", "COMMENT");
        sql.identifier(body, "query_warehouse", "QUERY_WAREHOUSE");
        sql.integer(body, "idle_auto_shutdown_time_seconds", "IDLE_AUTO_SHUTDOWN_TIME_SECONDS");
        sql.string(body, "runtime_name", "RUNTIME_NAME");
        sql.string(body, "compute_pool", "COMPUTE_POOL");
        return call.sql().action(sql.toString());
    }

    /** DROP NOTEBOOK [IF EXISTS]. */
    private static RestResponse delete(final RestCall call) {
        return call.sql().action("DROP NOTEBOOK" + call.ifExists() + " " + call.qualifiedSql(call.identifier("name")));
    }
}
