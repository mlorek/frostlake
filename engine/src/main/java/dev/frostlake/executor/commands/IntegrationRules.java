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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.model.Integration;
import dev.frostlake.metastore.model.IntegrationKind;
import dev.frostlake.metastore.model.PropertyValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The property sets of the integration kinds Frostlake models, from their CREATE and ALTER pages: which
 * properties a kind takes, which of them its variant requires, which ALTER may SET and UNSET, which values a
 * variant-choosing property takes, and which properties are secrets DESCRIBE masks.
 */
final class IntegrationRules {

    /** The character DESCRIBE shows for each character of a secret. */
    static final String MASK = "\u263A";

    private static final List<String> API_TAKES = Arrays.asList("API_PROVIDER", "API_AWS_ROLE_ARN",
        "AZURE_TENANT_ID", "AZURE_AD_APPLICATION_ID", "GOOGLE_AUDIENCE", "API_KEY", "API_ALLOWED_PREFIXES",
        "API_BLOCKED_PREFIXES", "ALLOWED_AUTHENTICATION_SECRETS", "ALLOWED_API_AUTHENTICATION_INTEGRATIONS",
        "API_USER_AUTHENTICATION", "USE_PRIVATELINK_ENDPOINT", "ENABLED", "COMMENT");
    private static final List<String> API_SETS = Arrays.asList("API_AWS_ROLE_ARN", "AZURE_AD_APPLICATION_ID",
        "API_KEY", "ENABLED", "API_ALLOWED_PREFIXES", "API_BLOCKED_PREFIXES", "ALLOWED_AUTHENTICATION_SECRETS",
        "COMMENT");
    private static final List<String> API_UNSETS = Arrays.asList("API_KEY", "ENABLED", "API_BLOCKED_PREFIXES",
        "COMMENT");
    private static final List<String> API_PROVIDERS = Arrays.asList("AWS_API_GATEWAY", "AWS_PRIVATE_API_GATEWAY",
        "AWS_GOV_API_GATEWAY", "AWS_GOV_PRIVATE_API_GATEWAY", "AZURE_API_MANAGEMENT",
        "AZURE_PRIVATE_API_MANAGEMENT", "GOOGLE_API_GATEWAY", "GIT_HTTPS_API");

    private static final List<String> CATALOG_TAKES = Arrays.asList("CATALOG_SOURCE", "TABLE_FORMAT",
        "GLUE_AWS_ROLE_ARN", "GLUE_CATALOG_ID", "GLUE_REGION", "CATALOG_NAMESPACE", "REST_CONFIG",
        "REST_AUTHENTICATION", "REFRESH_INTERVAL_SECONDS", "ENABLED", "COMMENT");
    private static final List<String> CATALOG_SETS = Arrays.asList("ENABLED", "REST_AUTHENTICATION",
        "REFRESH_INTERVAL_SECONDS", "COMMENT");
    private static final List<String> CATALOG_UNSETS = Arrays.asList("REFRESH_INTERVAL_SECONDS", "COMMENT");
    private static final List<String> CATALOG_SOURCES = Arrays.asList("GLUE", "OBJECT_STORE", "POLARIS",
        "ICEBERG_REST");
    private static final List<String> TABLE_FORMATS = Arrays.asList("ICEBERG", "DELTA");

    private static final List<String> NOTIFICATION_TAKES = Arrays.asList("TYPE", "DIRECTION",
        "NOTIFICATION_PROVIDER", "ENABLED", "ALLOWED_RECIPIENTS", "DEFAULT_RECIPIENTS", "DEFAULT_SUBJECT",
        "WEBHOOK_URL", "WEBHOOK_SECRET", "WEBHOOK_BODY_TEMPLATE", "WEBHOOK_HEADERS", "AWS_SNS_TOPIC_ARN",
        "AWS_SNS_ROLE_ARN", "AZURE_EVENT_GRID_TOPIC_ENDPOINT", "AZURE_TENANT_ID", "GCP_PUBSUB_TOPIC_NAME",
        "AZURE_STORAGE_QUEUE_PRIMARY_URI", "GCP_PUBSUB_SUBSCRIPTION_NAME", "USE_PRIVATELINK_ENDPOINT", "COMMENT");
    private static final List<String> EMAIL_SETS = Arrays.asList("ENABLED", "ALLOWED_RECIPIENTS",
        "DEFAULT_RECIPIENTS", "DEFAULT_SUBJECT", "COMMENT");
    private static final List<String> WEBHOOK_SETS = Arrays.asList("ENABLED", "WEBHOOK_URL", "WEBHOOK_SECRET",
        "WEBHOOK_BODY_TEMPLATE", "WEBHOOK_HEADERS", "COMMENT");
    private static final List<String> QUEUE_SETS = Arrays.asList("ENABLED", "AWS_SNS_TOPIC_ARN", "AWS_SNS_ROLE_ARN",
        "AZURE_EVENT_GRID_TOPIC_ENDPOINT", "AZURE_TENANT_ID", "GCP_PUBSUB_TOPIC_NAME",
        "AZURE_STORAGE_QUEUE_PRIMARY_URI", "GCP_PUBSUB_SUBSCRIPTION_NAME", "USE_PRIVATELINK_ENDPOINT", "COMMENT");
    private static final List<String> NOTIFICATION_UNSETS = Arrays.asList("ENABLED", "ALLOWED_RECIPIENTS",
        "DEFAULT_RECIPIENTS", "DEFAULT_SUBJECT", "WEBHOOK_SECRET", "WEBHOOK_BODY_TEMPLATE", "WEBHOOK_HEADERS",
        "COMMENT");
    private static final List<String> NOTIFICATION_TYPES = Arrays.asList("EMAIL", "WEBHOOK", "QUEUE");
    private static final List<String> NOTIFICATION_PROVIDERS = Arrays.asList("AWS_SNS", "AZURE_EVENT_GRID",
        "GCP_PUBSUB", "AZURE_STORAGE_QUEUE");
    private static final List<String> DIRECTIONS = Arrays.asList("INBOUND", "OUTBOUND");

    private static final List<String> SECRETS = Arrays.asList("API_KEY", "OAUTH_CLIENT_SECRET", "BEARER_TOKEN",
        "AWS_SECRET_KEY");

    private IntegrationRules() {
    }

    /** Whether the property holds a secret, which DESCRIBE never shows. */
    static boolean isSecret(final String key) {
        return SECRETS.contains(key);
    }

    /**
     * Checks a CREATE's properties: every one taken by the kind, the variant-choosing ones set to a value the
     * kind knows, and every property the variant requires present — the first one missing is reported.
     */
    static void checkCreate(final IntegrationKind kind, final Map<String, PropertyValue> properties) {
        for (final Map.Entry<String, PropertyValue> property : properties.entrySet()) {
            if (!takes(kind).contains(property.getKey())) {
                throw invalidValue(property.getKey(), property.getValue());
            }
        }
        final List<String> required = new ArrayList<>();
        if (kind == IntegrationKind.API) {
            final String provider = choice(properties, "API_PROVIDER", API_PROVIDERS);
            required.add("API_PROVIDER");
            required.add("API_ALLOWED_PREFIXES");
            if (provider != null && provider.startsWith("AWS_")) {
                required.add("API_AWS_ROLE_ARN");
            } else if (provider != null && provider.startsWith("AZURE_")) {
                required.add("AZURE_TENANT_ID");
                required.add("AZURE_AD_APPLICATION_ID");
            } else if ("GOOGLE_API_GATEWAY".equals(provider)) {
                required.add("GOOGLE_AUDIENCE");
            }
        } else if (kind == IntegrationKind.CATALOG) {
            final String source = choice(properties, "CATALOG_SOURCE", CATALOG_SOURCES);
            choice(properties, "TABLE_FORMAT", TABLE_FORMATS);
            required.add("CATALOG_SOURCE");
            required.add("TABLE_FORMAT");
            if ("GLUE".equals(source)) {
                required.add("GLUE_AWS_ROLE_ARN");
                required.add("GLUE_CATALOG_ID");
            } else if ("POLARIS".equals(source) || "ICEBERG_REST".equals(source)) {
                required.add("REST_CONFIG");
                required.add("REST_AUTHENTICATION");
            }
        } else {
            final String type = choice(properties, "TYPE", NOTIFICATION_TYPES);
            final String provider = choice(properties, "NOTIFICATION_PROVIDER", NOTIFICATION_PROVIDERS);
            final String direction = choice(properties, "DIRECTION", DIRECTIONS);
            required.add("TYPE");
            if ("WEBHOOK".equals(type)) {
                required.add("WEBHOOK_URL");
            } else if ("QUEUE".equals(type)) {
                required.add("NOTIFICATION_PROVIDER");
                if ("AWS_SNS".equals(provider)) {
                    required.add("AWS_SNS_TOPIC_ARN");
                    required.add("AWS_SNS_ROLE_ARN");
                } else if ("AZURE_EVENT_GRID".equals(provider)) {
                    required.add("AZURE_EVENT_GRID_TOPIC_ENDPOINT");
                    required.add("AZURE_TENANT_ID");
                } else if ("GCP_PUBSUB".equals(provider)) {
                    required.add("OUTBOUND".equals(direction) ? "GCP_PUBSUB_TOPIC_NAME"
                        : "GCP_PUBSUB_SUBSCRIPTION_NAME");
                } else if ("AZURE_STORAGE_QUEUE".equals(provider)) {
                    required.add("AZURE_STORAGE_QUEUE_PRIMARY_URI");
                    required.add("AZURE_TENANT_ID");
                }
            }
        }
        required.add("ENABLED");
        for (final String key : required) {
            if (!properties.containsKey(key)) {
                throw new RuntimeException(SqlCompilationError.of("Missing option(s): " + key));
            }
        }
        checkWebhookTemplate(properties);
    }

    /** A webhook's body template must carry the placeholder the message replaces. */
    static void checkWebhookTemplate(final Map<String, PropertyValue> properties) {
        final PropertyValue template = properties.get("WEBHOOK_BODY_TEMPLATE");
        if (template != null && template.getText() != null
                && !template.getText().contains("SNOWFLAKE_WEBHOOK_MESSAGE")) {
            throw new RuntimeException(
                "WEBHOOK_BODY_TEMPLATE must contain message placeholder SNOWFLAKE_WEBHOOK_MESSAGE");
        }
    }

    /**
     * Checks the properties an ALTER … SET names against what the integration's kind lets ALTER set; any other is
     * refused as a property of the integration's type.
     */
    static void checkSet(final Integration integration, final Map<String, PropertyValue> properties) {
        checkWebhookTemplate(properties);
        for (final String key : properties.keySet()) {
            if (!sets(integration).contains(key)) {
                throw new RuntimeException(SqlCompilationError.of("invalid property '" + key + "' for 'INTEGRATION - "
                    + integration.getType() + "'"));
            }
        }
    }

    /** Checks the properties an ALTER … UNSET names: only the optional ones reset. */
    static void checkUnset(final Integration integration, final List<String> keys) {
        for (final String key : keys) {
            if (!unsets(integration.getKind()).contains(key)) {
                throw new RuntimeException(SqlCompilationError.of("Cannot unset property '" + key
                    + "' on integration '" + integration.getName() + "'."));
            }
        }
    }

    /** The refusal of a value a property cannot take — at CREATE, also of a property the kind does not take. */
    static RuntimeException invalidValue(final String key, final PropertyValue value) {
        return new RuntimeException(SqlCompilationError.of("invalid value [" + value.getWritten() + "] for parameter '"
            + key + "'"));
    }

    /** How DESCRIBE shows a secret: one mask character per character of the secret. */
    static String mask(final String secret) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < secret.length(); i++) {
            out.append(MASK);
        }
        return out.toString();
    }

    private static List<String> takes(final IntegrationKind kind) {
        if (kind == IntegrationKind.API) {
            return API_TAKES;
        }
        return kind == IntegrationKind.CATALOG ? CATALOG_TAKES : NOTIFICATION_TAKES;
    }

    /** What ALTER may set: the kind's settable properties, a notification integration's those of its TYPE. */
    private static List<String> sets(final Integration integration) {
        if (integration.getKind() == IntegrationKind.API) {
            return API_SETS;
        }
        if (integration.getKind() == IntegrationKind.CATALOG) {
            return CATALOG_SETS;
        }
        final String type = integration.text("TYPE");
        if ("EMAIL".equals(type)) {
            return EMAIL_SETS;
        }
        return "WEBHOOK".equals(type) ? WEBHOOK_SETS : QUEUE_SETS;
    }

    private static List<String> unsets(final IntegrationKind kind) {
        if (kind == IntegrationKind.API) {
            return API_UNSETS;
        }
        return kind == IntegrationKind.CATALOG ? CATALOG_UNSETS : NOTIFICATION_UNSETS;
    }

    private static void require(final Map<String, PropertyValue> properties, final List<String> missing,
                                final String key) {
        if (!properties.containsKey(key) && !missing.contains(key)) {
            missing.add(key);
        }
    }

    /**
     * A variant-choosing property's value, upper-cased and stored back that way, or null when it is absent.
     *
     * @throws RuntimeException for a value the kind does not know
     */
    private static String choice(final Map<String, PropertyValue> properties, final String key,
                                 final List<String> values) {
        final PropertyValue value = properties.get(key);
        if (value == null) {
            return null;
        }
        final String text = value.getText() == null ? null : value.getText().toUpperCase(Locale.ROOT);
        if (text == null || !values.contains(text)) {
            throw invalidValue(key, value);
        }
        properties.put(key, PropertyValue.word(text).writtenAs(value.getWritten()));
        return text;
    }
}
