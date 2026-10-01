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
import dev.frostlake.metastore.QualifiedName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Locale;
import java.util.Map;

/**
 * Notification integrations ({@code notification-integration.yaml}, {@code /api/v2/notification-integrations}):
 * list, create, fetch, delete and the tag endpoints.
 *
 * <p>An integration is read from {@code SHOW NOTIFICATION INTEGRATIONS} and {@code DESCRIBE NOTIFICATION
 * INTEGRATION}. Its {@code notification_hook} type names the SQL variant: EMAIL and WEBHOOK are the TYPE, and the
 * five queue types are TYPE = QUEUE with a provider and a direction — AWS SNS, Azure Event Grid and Google Pub/Sub
 * outbound, Azure storage queue ({@code QUEUE_AZURE_EVENT_GRID_INBOUND}) and Google Pub/Sub inbound. The
 * identities a cloud provider would hand out for a queue are not invented: they are left out.
 */
public final class NotificationIntegrationResource implements RestResource {

    private static final String COLLECTION = "/api/v2/notification-integrations";
    private static final String ITEM = COLLECTION + "/{name}";
    private static final String KIND = "NOTIFICATION";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listNotificationIntegrations", this);
        router.add("POST", COLLECTION, "createNotificationIntegration", this);
        router.add("GET", ITEM, "fetchNotificationIntegration", this);
        router.add("DELETE", ITEM, "deleteNotificationIntegration", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            return RestIntegrations.tags(call, KIND);
        }
        switch (call.operation()) {
            case "listNotificationIntegrations":
                final ArrayNode out = RestJson.array();
                for (final RestRow row : RestIntegrations.rows(call, KIND)) {
                    out.add(toJson(call, row));
                }
                return RestResponse.json(200, out);
            case "createNotificationIntegration":
                return create(call);
            case "fetchNotificationIntegration":
                return RestResponse.json(200, toJson(call, RestIntegrations.row(call, KIND,
                    call.identifier("name"))));
            case "deleteNotificationIntegration":
                return call.sql().action("DROP NOTIFICATION INTEGRATION" + call.ifExists() + " "
                    + call.identifier("name").sql());
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static ObjectNode toJson(final RestCall call, final RestRow row) {
        final ObjectNode node = RestIntegrations.base(row);
        final Map<String, String> properties = RestIntegrations.describe(call, KIND, row.string("name"));
        final ObjectNode hook = RestJson.object();
        final String type = hookType(row.string("type"), properties);
        hook.put("type", type);
        if ("EMAIL".equals(type)) {
            RestIntegrations.list(hook, "allowed_recipients", properties.get("ALLOWED_RECIPIENTS"));
            RestIntegrations.list(hook, "default_recipients", properties.get("DEFAULT_RECIPIENTS"));
            RestIntegrations.text(hook, "default_subject", properties.get("DEFAULT_SUBJECT"));
        } else if ("WEBHOOK".equals(type)) {
            webhook(hook, properties);
        } else {
            queue(hook, type, properties);
        }
        node.set("notification_hook", hook);
        return node;
    }

    /** A webhook's URL, secret, body template and headers. */
    private static void webhook(final ObjectNode hook, final Map<String, String> properties) {
        RestIntegrations.text(hook, "webhook_url", properties.get("WEBHOOK_URL"));
        hook.putNull("webhook_secret");
        final String secret = properties.get("WEBHOOK_SECRET");
        if (secret != null && !secret.isEmpty()) {
            final String[] parts = QualifiedName.parse(secret).parts();
            final ObjectNode secretNode = RestJson.object();
            secretNode.put("name", RestIdentifier.display(parts[parts.length - 1]));
            if (parts.length >= 2) {
                secretNode.put("schema_name", RestIdentifier.display(parts[parts.length - 2]));
            }
            if (parts.length >= 3) {
                secretNode.put("database_name", RestIdentifier.display(parts[parts.length - 3]));
            }
            hook.set("webhook_secret", secretNode);
        }
        RestIntegrations.text(hook, "webhook_body_template", properties.get("WEBHOOK_BODY_TEMPLATE"));
        final String headers = properties.get("WEBHOOK_HEADERS");
        if (headers != null && headers.length() > 2 && headers.startsWith("{") && headers.endsWith("}")) {
            // DESCRIBE shows the headers as {name=value, name=value}.
            final ObjectNode headerNode = RestJson.object();
            for (final String pair : headers.substring(1, headers.length() - 1).split(", ")) {
                final int equals = pair.indexOf('=');
                if (equals > 0) {
                    headerNode.put(pair.substring(0, equals), pair.substring(equals + 1));
                }
            }
            hook.set("webhook_headers", headerNode);
        } else {
            hook.putNull("webhook_headers");
        }
    }

    /**
     * A queue's own properties; the identities the cloud provider would hand out for it (the IAM user, the
     * consent URL, the service account) are not invented and are sent as null.
     */
    private static void queue(final ObjectNode hook, final String type, final Map<String, String> properties) {
        if ("QUEUE_AWS_SNS_OUTBOUND".equals(type)) {
            RestIntegrations.text(hook, "aws_sns_topic_arn", properties.get("AWS_SNS_TOPIC_ARN"));
            RestIntegrations.text(hook, "aws_sns_role_arn", properties.get("AWS_SNS_ROLE_ARN"));
            RestJson.nulls(hook, "sf_aws_iam_user_arn", "sf_aws_external_id");
        } else if ("QUEUE_AZURE_EVENT_GRID_OUTBOUND".equals(type)) {
            RestIntegrations.text(hook, "azure_event_grid_topic_endpoint",
                properties.get("AZURE_EVENT_GRID_TOPIC_ENDPOINT"));
            RestIntegrations.text(hook, "azure_tenant_id", properties.get("AZURE_TENANT_ID"));
            RestJson.nulls(hook, "azure_consent_url", "azure_multi_tenant_app_name");
        } else if ("QUEUE_AZURE_EVENT_GRID_INBOUND".equals(type)) {
            RestIntegrations.text(hook, "azure_storage_queue_primary_uri",
                properties.get("AZURE_STORAGE_QUEUE_PRIMARY_URI"));
            RestIntegrations.text(hook, "azure_tenant_id", properties.get("AZURE_TENANT_ID"));
            RestJson.nulls(hook, "azure_consent_url", "azure_multi_tenant_app_name");
        } else if ("QUEUE_GCP_PUBSUB_OUTBOUND".equals(type)) {
            RestIntegrations.text(hook, "gcp_pubsub_topic_name", properties.get("GCP_PUBSUB_TOPIC_NAME"));
            RestJson.nulls(hook, "gcp_pubsub_service_account");
        } else {
            RestIntegrations.text(hook, "gcp_pubsub_subscription_name",
                properties.get("GCP_PUBSUB_SUBSCRIPTION_NAME"));
            RestJson.nulls(hook, "gcp_pubsub_service_account");
        }
    }

    /** The hook type of the SQL variant: SHOW's type (EMAIL, WEBHOOK, QUEUE - provider) and the direction. */
    private static String hookType(final String shownType, final Map<String, String> properties) {
        if (shownType == null || !shownType.startsWith("QUEUE")) {
            return shownType;
        }
        final String provider = properties.get("NOTIFICATION_PROVIDER");
        final boolean outbound = "OUTBOUND".equals(properties.get("DIRECTION"));
        if ("AWS_SNS".equals(provider)) {
            return "QUEUE_AWS_SNS_OUTBOUND";
        }
        if ("AZURE_EVENT_GRID".equals(provider)) {
            return "QUEUE_AZURE_EVENT_GRID_OUTBOUND";
        }
        if ("AZURE_STORAGE_QUEUE".equals(provider)) {
            return "QUEUE_AZURE_EVENT_GRID_INBOUND";
        }
        return outbound ? "QUEUE_GCP_PUBSUB_OUTBOUND" : "QUEUE_GCP_PUBSUB_INBOUND";
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestIntegrations.bodyName(body, null);
        final JsonNode hook = body.get("notification_hook");
        if (hook == null || !hook.isObject() || RestJson.text(hook, "type") == null) {
            throw RestException.badRequest("Missing required property 'notification_hook'.");
        }
        final String type = RestJson.text(hook, "type").toUpperCase(Locale.ROOT);
        final RestStatement sql = new RestStatement(RestIntegrations.createHead(call, KIND, name));
        switch (type) {
            case "EMAIL":
                sql.property("TYPE", "EMAIL");
                sql.stringList(hook, "allowed_recipients", "ALLOWED_RECIPIENTS");
                sql.stringList(hook, "default_recipients", "DEFAULT_RECIPIENTS");
                sql.string(hook, "default_subject", "DEFAULT_SUBJECT");
                break;
            case "WEBHOOK":
                sql.property("TYPE", "WEBHOOK");
                sql.string(hook, "webhook_url", "WEBHOOK_URL");
                webhookSecret(sql, hook);
                sql.string(hook, "webhook_body_template", "WEBHOOK_BODY_TEMPLATE");
                webhookHeaders(sql, hook);
                break;
            case "QUEUE_AWS_SNS_OUTBOUND":
                queue(sql, "OUTBOUND", "AWS_SNS");
                sql.string(hook, "aws_sns_topic_arn", "AWS_SNS_TOPIC_ARN");
                sql.string(hook, "aws_sns_role_arn", "AWS_SNS_ROLE_ARN");
                break;
            case "QUEUE_AZURE_EVENT_GRID_OUTBOUND":
                queue(sql, "OUTBOUND", "AZURE_EVENT_GRID");
                sql.string(hook, "azure_event_grid_topic_endpoint", "AZURE_EVENT_GRID_TOPIC_ENDPOINT");
                sql.string(hook, "azure_tenant_id", "AZURE_TENANT_ID");
                break;
            case "QUEUE_GCP_PUBSUB_OUTBOUND":
                queue(sql, "OUTBOUND", "GCP_PUBSUB");
                sql.string(hook, "gcp_pubsub_topic_name", "GCP_PUBSUB_TOPIC_NAME");
                break;
            case "QUEUE_AZURE_EVENT_GRID_INBOUND":
                queue(sql, null, "AZURE_STORAGE_QUEUE");
                sql.string(hook, "azure_storage_queue_primary_uri", "AZURE_STORAGE_QUEUE_PRIMARY_URI");
                sql.string(hook, "azure_tenant_id", "AZURE_TENANT_ID");
                break;
            case "QUEUE_GCP_PUBSUB_INBOUND":
                queue(sql, null, "GCP_PUBSUB");
                sql.string(hook, "gcp_pubsub_subscription_name", "GCP_PUBSUB_SUBSCRIPTION_NAME");
                break;
            default:
                throw RestException.badRequest("Invalid notification_hook type " + RestSql.literal(type) + ".");
        }
        sql.bool(body, "enabled", "ENABLED");
        sql.string(body, "comment", "COMMENT");
        return call.sql().action(sql.toString());
    }

    private static void queue(final RestStatement sql, final String direction, final String provider) {
        sql.property("TYPE", "QUEUE");
        if (direction != null) {
            sql.property("DIRECTION", direction);
        }
        sql.property("NOTIFICATION_PROVIDER", provider);
    }

    /** {@code WEBHOOK_SECRET = db.schema.name}, the secret written as the names the body gives. */
    private static void webhookSecret(final RestStatement sql, final JsonNode hook) {
        final JsonNode secret = hook.get("webhook_secret");
        if (secret == null || secret.isNull()) {
            return;
        }
        if (!secret.isObject()) {
            throw RestException.badRequest("Property 'webhook_secret' must be an object.");
        }
        final RestIdentifier database = RestJson.identifier(secret, "database_name", true);
        final RestIdentifier schema = RestJson.identifier(secret, "schema_name", true);
        final RestIdentifier name = RestJson.identifier(secret, "name", true);
        sql.property("WEBHOOK_SECRET", database.sql() + "." + schema.sql() + "." + name.sql());
    }

    /** {@code WEBHOOK_HEADERS = ('k'='v', …)} from the headers object. */
    private static void webhookHeaders(final RestStatement sql, final JsonNode hook) {
        final JsonNode headers = hook.get("webhook_headers");
        if (headers == null || headers.isNull()) {
            return;
        }
        if (!headers.isObject()) {
            throw RestException.badRequest("Property 'webhook_headers' must be an object.");
        }
        final StringBuilder pairs = new StringBuilder("(");
        int count = 0;
        for (final Map.Entry<String, JsonNode> header : headers.properties()) {
            if (count++ > 0) {
                pairs.append(", ");
            }
            final JsonNode value = header.getValue();
            pairs.append(RestSql.literal(header.getKey())).append('=')
                .append(RestSql.literal(value.isString() ? value.stringValue() : value.toString()));
        }
        if (count > 0) {
            sql.property("WEBHOOK_HEADERS", pairs.append(')').toString());
        }
    }
}
