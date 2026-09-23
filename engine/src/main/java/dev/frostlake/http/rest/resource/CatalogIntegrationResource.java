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
import dev.frostlake.http.rest.RestResource;
import dev.frostlake.http.rest.RestResponse;
import dev.frostlake.http.rest.RestRouter;
import dev.frostlake.http.rest.RestRow;
import dev.frostlake.http.rest.RestSql;
import dev.frostlake.http.rest.RestStatement;
import dev.frostlake.http.rest.RestTags;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catalog integrations ({@code catalog-integration.yaml}, {@code /api/v2/catalog-integrations}): list, create,
 * fetch, delete and the tag endpoints.
 *
 * <p>An integration is read from {@code SHOW CATALOG INTEGRATIONS} and {@code DESCRIBE CATALOG INTEGRATION}. Its
 * {@code catalog} is the catalog source (GLUE, OBJECT_STORE or POLARIS) with the source's own properties; a
 * Polaris catalog's REST configuration and OAuth authentication come from the REST_CONFIG and
 * REST_AUTHENTICATION objects DESCRIBE shows, the {@code warehouse} being the catalog name. The OAuth client
 * secret is write-only: DESCRIBE masks it and so does the response.
 */
public final class CatalogIntegrationResource implements RestResource {

    private static final String COLLECTION = "/api/v2/catalog-integrations";
    private static final String ITEM = COLLECTION + "/{name}";
    private static final String KIND = "CATALOG";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listCatalogIntegrations", this);
        router.add("POST", COLLECTION, "createCatalogIntegration", this);
        router.add("GET", ITEM, "fetchCatalogIntegration", this);
        router.add("DELETE", ITEM, "deleteCatalogIntegration", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            return RestIntegrations.tags(call, KIND);
        }
        switch (call.operation()) {
            case "listCatalogIntegrations":
                final ArrayNode out = RestJson.array();
                for (final RestRow row : RestIntegrations.rows(call, KIND)) {
                    out.add(toJson(call, row));
                }
                return RestResponse.json(200, out);
            case "createCatalogIntegration":
                return create(call);
            case "fetchCatalogIntegration":
                return RestResponse.json(200, toJson(call, RestIntegrations.row(call, KIND,
                    call.identifier("name"))));
            case "deleteCatalogIntegration":
                return call.sql().action("DROP CATALOG INTEGRATION" + call.ifExists() + " "
                    + call.identifier("name").sql());
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static ObjectNode toJson(final RestCall call, final RestRow row) {
        final ObjectNode node = RestIntegrations.base(row);
        final Map<String, String> properties = RestIntegrations.describe(call, KIND, row.string("name"));
        final ObjectNode catalog = RestJson.object();
        final String source = properties.get("CATALOG_SOURCE");
        catalog.put("catalog_source", source);
        if ("GLUE".equals(source)) {
            RestIntegrations.text(catalog, "glue_aws_role_arn", properties.get("GLUE_AWS_ROLE_ARN"));
            RestIntegrations.text(catalog, "glue_catalog_id", properties.get("GLUE_CATALOG_ID"));
            RestIntegrations.text(catalog, "glue_region", properties.get("GLUE_REGION"));
        }
        if (!"OBJECT_STORE".equals(source)) {
            RestIntegrations.text(catalog, "catalog_namespace", properties.get("CATALOG_NAMESPACE"));
        }
        if (properties.containsKey("REST_CONFIG")) {
            final JsonNode config = RestIntegrations.object(properties.get("REST_CONFIG"));
            final ObjectNode restConfig = RestJson.object();
            RestIntegrations.text(restConfig, "catalog_uri", RestIntegrations.member(config, "CATALOG_URI"));
            RestIntegrations.text(restConfig, "warehouse", RestIntegrations.member(config, "CATALOG_NAME"));
            catalog.set("rest_config", restConfig);
        }
        if (properties.containsKey("REST_AUTHENTICATION")) {
            final JsonNode auth = RestIntegrations.object(properties.get("REST_AUTHENTICATION"));
            final ObjectNode restAuth = RestJson.object();
            RestIntegrations.text(restAuth, "type", RestIntegrations.member(auth, "TYPE"));
            RestIntegrations.text(restAuth, "oauth_client_id", RestIntegrations.member(auth, "OAUTH_CLIENT_ID"));
            RestIntegrations.text(restAuth, "oauth_client_secret",
                RestIntegrations.member(auth, "OAUTH_CLIENT_SECRET"));
            final JsonNode scopes = auth.get("OAUTH_ALLOWED_SCOPES");
            final ArrayNode scopeArray = RestJson.array();
            if (scopes != null && scopes.isArray()) {
                for (final JsonNode scope : scopes.values()) {
                    scopeArray.add(scope.isString() ? scope.stringValue() : scope.toString());
                }
            } else if (scopes != null && !scopes.isNull()) {
                scopeArray.add(scopes.isString() ? scopes.stringValue() : scopes.toString());
            }
            restAuth.set("oauth_allowed_scopes", scopeArray);
            catalog.set("rest_authentication", restAuth);
        }
        node.set("catalog", catalog);
        RestIntegrations.text(node, "table_format", properties.get("TABLE_FORMAT"));
        final String refresh = properties.get("REFRESH_INTERVAL_SECONDS");
        RestJson.put(node, "refresh_interval_seconds", refresh == null || refresh.isEmpty() ? null
            : Long.valueOf(refresh));
        RestJson.nulls(node, "default_storage_config", "event_config");
        RestJson.string(node, "type", row, "type");
        RestJson.string(node, "category", row, "category");
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestIntegrations.bodyName(body, null);
        final JsonNode catalog = body.get("catalog");
        if (catalog == null || !catalog.isObject()) {
            throw RestException.badRequest("Missing required property 'catalog'.");
        }
        final String source = word(catalog, "catalog_source");
        final RestStatement sql = new RestStatement(RestIntegrations.createHead(call, KIND, name));
        sql.property("CATALOG_SOURCE", source);
        final String format = RestJson.text(body, "table_format");
        if (format != null) {
            sql.property("TABLE_FORMAT", word(body, "table_format"));
        }
        sql.string(catalog, "glue_aws_role_arn", "GLUE_AWS_ROLE_ARN");
        sql.string(catalog, "glue_catalog_id", "GLUE_CATALOG_ID");
        sql.string(catalog, "glue_region", "GLUE_REGION");
        sql.string(catalog, "catalog_namespace", "CATALOG_NAMESPACE");
        final JsonNode config = catalog.get("rest_config");
        if (config != null && config.isObject()) {
            final RestStatement nested = new RestStatement("(");
            nested.string(config, "catalog_uri", "CATALOG_URI");
            nested.string(config, "warehouse", "CATALOG_NAME");
            sql.property("REST_CONFIG", nested.append(" )").toString());
        }
        final JsonNode auth = catalog.get("rest_authentication");
        if (auth != null && auth.isObject()) {
            final RestStatement nested = new RestStatement("(");
            if (RestJson.text(auth, "type") != null) {
                nested.property("TYPE", word(auth, "type"));
            }
            nested.string(auth, "oauth_client_id", "OAUTH_CLIENT_ID");
            nested.string(auth, "oauth_client_secret", "OAUTH_CLIENT_SECRET");
            final List<String> scopes = RestJson.strings(auth, "oauth_allowed_scopes");
            if (scopes != null) {
                nested.property("OAUTH_ALLOWED_SCOPES", RestIntegrations.literalList(scopes));
            }
            sql.property("REST_AUTHENTICATION", nested.append(" )").toString());
        }
        sql.bool(body, "enabled", "ENABLED");
        sql.string(body, "comment", "COMMENT");
        return call.sql().action(sql.toString());
    }

    /** A property holding one of a fixed set of words, written bare after checking it is a word. */
    private static String word(final JsonNode node, final String property) {
        final String value = RestJson.text(node, property);
        if (value == null) {
            throw RestException.badRequest("Missing required property '" + property + "'.");
        }
        final String upper = value.toUpperCase(Locale.ROOT);
        if (!upper.matches("[A-Z_]+")) {
            throw RestException.badRequest("Invalid value " + RestSql.literal(value) + " for property '" + property
                + "'.");
        }
        return upper;
    }
}
