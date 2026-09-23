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
import dev.frostlake.http.rest.RestShow;
import dev.frostlake.http.rest.RestStatement;
import dev.frostlake.http.rest.RestTags;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * API integrations ({@code api-integration.yaml}, {@code /api/v2/api-integrations}): list, create, fetch,
 * create-or-alter, delete and the tag endpoints.
 *
 * <p>An integration is read from {@code SHOW API INTEGRATIONS} and {@code DESCRIBE API INTEGRATION}; its
 * {@code api_hook} is the provider and the provider's own properties, its type chosen by the provider (AWS, AZURE,
 * GC or GIT). The API key is write-only and never returned. {@code PUT} is a create-or-alter done here, as SQL
 * has no CREATE OR ALTER for integrations: the integration is created when absent, and otherwise the properties
 * the body names are SET and the unsettable ones it leaves out are UNSET; the provider cannot change.
 */
public final class ApiIntegrationResource implements RestResource {

    private static final String COLLECTION = "/api/v2/api-integrations";
    private static final String ITEM = COLLECTION + "/{name}";
    private static final String KIND = "API";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listAPIIntegrations", this);
        router.add("POST", COLLECTION, "createAPIIntegration", this);
        router.add("GET", ITEM, "fetchAPIIntegration", this);
        router.add("PUT", ITEM, "createOrAlterAPIIntegration", this);
        router.add("DELETE", ITEM, "deleteAPIIntegration", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            return RestIntegrations.tags(call, KIND);
        }
        switch (call.operation()) {
            case "listAPIIntegrations":
                final ArrayNode out = RestJson.array();
                for (final RestRow row : RestIntegrations.rows(call, KIND)) {
                    out.add(toJson(call, row));
                }
                return RestResponse.json(200, out);
            case "createAPIIntegration":
                return create(call, RestIntegrations.bodyName(call.body(), null), call.body(), true);
            case "fetchAPIIntegration":
                final RestIdentifier name = call.identifier("name");
                return RestResponse.json(200, toJson(call, RestIntegrations.row(call, KIND, name)));
            case "createOrAlterAPIIntegration":
                return createOrAlter(call);
            case "deleteAPIIntegration":
                return call.sql().action("DROP API INTEGRATION" + call.ifExists() + " " + call.identifier("name").sql());
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static ObjectNode toJson(final RestCall call, final RestRow row) {
        final ObjectNode node = RestIntegrations.base(row);
        final Map<String, String> properties = RestIntegrations.describe(call, KIND, row.string("name"));
        final String provider = properties.get("API_PROVIDER");
        final ObjectNode hook = RestJson.object();
        final String type = hookType(provider);
        hook.put("type", type);
        if ("GIT".equals(type)) {
            final String secrets = properties.get("ALLOWED_AUTHENTICATION_SECRETS");
            hook.put("allow_any_secret", secrets != null && "ALL".equalsIgnoreCase(secrets));
            RestIntegrations.list(hook, "allowed_authentication_secrets", secrets == null
                || "ALL".equalsIgnoreCase(secrets) || "NONE".equalsIgnoreCase(secrets) ? null : secrets);
            RestIntegrations.list(hook, "allowed_api_authentication_integrations",
                properties.get("ALLOWED_API_AUTHENTICATION_INTEGRATIONS"));
        } else {
            RestIntegrations.text(hook, "api_provider", provider);
            if ("AWS".equals(type)) {
                RestIntegrations.text(hook, "api_aws_role_arn", properties.get("API_AWS_ROLE_ARN"));
            } else if ("AZURE".equals(type)) {
                RestIntegrations.text(hook, "azure_tenant_id", properties.get("AZURE_TENANT_ID"));
                RestIntegrations.text(hook, "azure_ad_application_id", properties.get("AZURE_AD_APPLICATION_ID"));
            } else {
                RestIntegrations.text(hook, "google_audience", properties.get("GOOGLE_AUDIENCE"));
            }
            // The key comes back masked, one character per character of the key.
            RestIntegrations.text(hook, "api_key", properties.get("API_KEY"));
        }
        node.set("api_hook", hook);
        RestIntegrations.list(node, "api_allowed_prefixes", properties.get("API_ALLOWED_PREFIXES"));
        RestIntegrations.list(node, "api_blocked_prefixes", properties.get("API_BLOCKED_PREFIXES"));
        return node;
    }

    /** The hook type a provider belongs to. */
    private static String hookType(final String provider) {
        if (provider == null || provider.startsWith("AWS_")) {
            return "AWS";
        }
        if (provider.startsWith("AZURE_")) {
            return "AZURE";
        }
        return "GOOGLE_API_GATEWAY".equals(provider) ? "GC" : "GIT";
    }

    /** The provider a hook names: its {@code api_provider}, or Git's one provider. */
    private static String provider(final JsonNode hook) {
        final String type = RestJson.text(hook, "type");
        if (type == null) {
            throw RestException.badRequest("Missing required property 'api_hook.type'.");
        }
        final String upper = type.toUpperCase(Locale.ROOT);
        if ("GIT".equals(upper)) {
            return "GIT_HTTPS_API";
        }
        final String provider = RestJson.text(hook, "api_provider");
        if (provider == null) {
            throw RestException.badRequest("Missing required property 'api_hook.api_provider'.");
        }
        final String providerUpper = provider.toUpperCase(Locale.ROOT);
        if (!providerUpper.matches("[A-Z_]+") || !hookType(providerUpper).equals(upper)) {
            throw RestException.badRequest("Invalid api_provider '" + provider + "' for an api_hook of type '"
                + type + "'.");
        }
        return providerUpper;
    }

    private static JsonNode hook(final JsonNode body) {
        final JsonNode hook = body.get("api_hook");
        if (hook == null || !hook.isObject()) {
            throw RestException.badRequest("Missing required property 'api_hook'.");
        }
        return hook;
    }

    private static RestResponse create(final RestCall call, final RestIdentifier name, final JsonNode body,
                                       final boolean honourMode) {
        final JsonNode hook = hook(body);
        final RestStatement sql = new RestStatement(honourMode ? RestIntegrations.createHead(call, KIND, name)
            : "CREATE API INTEGRATION " + name.sql());
        sql.property("API_PROVIDER", provider(hook));
        hookProperties(sql, hook);
        return call.sql().action(finish(sql, body).toString());
    }

    /** The provider's own properties, as the hook sets them. */
    private static void hookProperties(final RestStatement sql, final JsonNode hook) {
        sql.string(hook, "api_aws_role_arn", "API_AWS_ROLE_ARN");
        sql.string(hook, "azure_tenant_id", "AZURE_TENANT_ID");
        sql.string(hook, "azure_ad_application_id", "AZURE_AD_APPLICATION_ID");
        sql.string(hook, "google_audience", "GOOGLE_AUDIENCE");
        sql.string(hook, "api_key", "API_KEY");
        final Boolean anySecret = RestJson.bool(hook, "allow_any_secret");
        final List<String> secrets = RestJson.strings(hook, "allowed_authentication_secrets");
        if (Boolean.TRUE.equals(anySecret)) {
            sql.property("ALLOWED_AUTHENTICATION_SECRETS", "ALL");
        } else if (secrets != null) {
            sql.property("ALLOWED_AUTHENTICATION_SECRETS", RestIntegrations.literalList(secrets));
        }
        final List<String> authIntegrations = RestJson.strings(hook, "allowed_api_authentication_integrations");
        if (authIntegrations != null) {
            sql.property("ALLOWED_API_AUTHENTICATION_INTEGRATIONS", RestIntegrations.literalList(authIntegrations));
        }
    }

    /** The prefixes, the flag and the comment every provider takes. */
    private static RestStatement finish(final RestStatement sql, final JsonNode body) {
        sql.stringList(body, "api_allowed_prefixes", "API_ALLOWED_PREFIXES");
        sql.stringList(body, "api_blocked_prefixes", "API_BLOCKED_PREFIXES");
        sql.bool(body, "enabled", "ENABLED");
        sql.string(body, "comment", "COMMENT");
        return sql;
    }

    private static RestResponse createOrAlter(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        final JsonNode body = call.body();
        call.requireBodyNames(name);
        final List<RestRow> existing = call.sql().showNamed("SHOW INTEGRATIONS" + RestShow.likeName(name), name);
        if (existing.isEmpty()) {
            return create(call, name, body, false);
        }
        if (!"API".equals(existing.get(0).string("category"))) {
            throw RestException.badRequest("Integration '" + name + "' is not an API integration.");
        }
        final JsonNode hook = hook(body);
        final String standing = RestIntegrations.describe(call, KIND, name.name()).get("API_PROVIDER");
        if (!provider(hook).equals(standing)) {
            throw RestException.badRequest("The api_provider of API integration '" + name
                + "' cannot be changed.");
        }
        final RestStatement set = new RestStatement("ALTER API INTEGRATION " + name.sql() + " SET");
        set.string(hook, "api_aws_role_arn", "API_AWS_ROLE_ARN");
        set.string(hook, "azure_ad_application_id", "AZURE_AD_APPLICATION_ID");
        set.string(hook, "api_key", "API_KEY");
        final Boolean anySecret = RestJson.bool(hook, "allow_any_secret");
        final List<String> secrets = RestJson.strings(hook, "allowed_authentication_secrets");
        if (Boolean.TRUE.equals(anySecret)) {
            set.property("ALLOWED_AUTHENTICATION_SECRETS", "ALL");
        } else if (secrets != null) {
            set.property("ALLOWED_AUTHENTICATION_SECRETS", RestIntegrations.literalList(secrets));
        }
        finish(set, body);
        String status = RestResponse.DEFAULT_STATUS;
        if (set.propertyCount() > 0) {
            status = call.sql().status(set.toString());
        }
        final List<String> unset = new ArrayList<>();
        if (!RestJson.present(hook, "api_key")) {
            unset.add("API_KEY");
        }
        if (!RestJson.present(body, "api_blocked_prefixes")) {
            unset.add("API_BLOCKED_PREFIXES");
        }
        if (!RestJson.present(body, "comment")) {
            unset.add("COMMENT");
        }
        status = call.sql().status("ALTER API INTEGRATION " + name.sql() + " UNSET " + String.join(", ", unset));
        return RestResponse.success(status);
    }
}
