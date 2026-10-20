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
import dev.frostlake.metastore.model.PropertyValueKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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
        "AZURE_PRIVATE_API_MANAGEMENT", "GOOGLE_API_GATEWAY", "GIT_HTTPS_API", "PYPI");
    /** What an API integration of the package index's provider takes: nothing of the gateways' own. */
    private static final List<String> PYPI_TAKES = Arrays.asList("API_PROVIDER", "ENABLED", "COMMENT");

    private static final List<String> SECURITY_TAKES = Arrays.asList("TYPE", "AUTH_TYPE", "ENABLED", "COMMENT",
        "OAUTH_CLIENT_ID", "OAUTH_CLIENT_SECRET", "OAUTH_CLIENT_AUTH_METHOD", "OAUTH_TOKEN_ENDPOINT",
        "OAUTH_AUTHORIZATION_ENDPOINT", "OAUTH_ALLOWED_SCOPES", "OAUTH_GRANT", "OAUTH_ACCESS_TOKEN_VALIDITY",
        "OAUTH_REFRESH_TOKEN_VALIDITY", "AWS_ROLE_ARN");
    /** What an OAuth client's integration takes that an AWS role's does not, and the other way round. */
    private static final List<String> OAUTH_ONLY = Arrays.asList("OAUTH_CLIENT_ID", "OAUTH_CLIENT_SECRET",
        "OAUTH_CLIENT_AUTH_METHOD", "OAUTH_TOKEN_ENDPOINT", "OAUTH_AUTHORIZATION_ENDPOINT", "OAUTH_ALLOWED_SCOPES",
        "OAUTH_GRANT", "OAUTH_ACCESS_TOKEN_VALIDITY", "OAUTH_REFRESH_TOKEN_VALIDITY");
    private static final List<String> AWS_IAM_ONLY = Arrays.asList("AWS_ROLE_ARN");
    /** Properties DESCRIBE tells of a security integration that no statement sets. */
    private static final List<String> SECURITY_TOLD_ONLY = Arrays.asList("OAUTH_USERNAME", "OAUTH_RESOURCE_URL",
        "OAUTH_ASSERTION_ISSUER", "PARENT_INTEGRATION");
    private static final List<String> SECURITY_FIXED = Arrays.asList("TYPE", "AUTH_TYPE");
    private static final List<String> SECURITY_UNSETS = Arrays.asList("ENABLED", "COMMENT");
    private static final List<String> SECURITY_TYPES = Arrays.asList("API_AUTHENTICATION");
    /** The security integration types other than API_AUTHENTICATION, each with its own property set. */
    private static final List<String> SECURITY_TYPES_OWN = Arrays.asList("OAUTH", "EXTERNAL_OAUTH", "SAML2", "SCIM");
    /** What a Snowflake OAuth integration takes, for a partner application or a custom client (documented). */
    private static final List<String> OAUTH_TAKES = Arrays.asList("TYPE", "OAUTH_CLIENT", "OAUTH_REDIRECT_URI",
        "ENABLED", "OAUTH_ISSUE_REFRESH_TOKENS", "OAUTH_REFRESH_TOKEN_VALIDITY",
        "OAUTH_SINGLE_USE_REFRESH_TOKENS_REQUIRED", "OAUTH_USE_SECONDARY_ROLES", "NETWORK_POLICY",
        "ALLOWED_ROLES_LIST", "BLOCKED_ROLES_LIST", "USE_PRIVATELINK_FOR_AUTHORIZATION_ENDPOINT", "COMMENT",
        "OAUTH_CLIENT_TYPE", "OAUTH_ALLOW_NON_TLS_REDIRECT_URI", "OAUTH_ALTERNATE_REDIRECT_URIS",
        "OAUTH_ENFORCE_PKCE", "OAUTH_ENABLE_ROLE_SELECTION", "PRE_AUTHORIZED_ROLES_LIST", "OAUTH_ANY_ROLE_MODE",
        "OAUTH_CLIENT_RSA_PUBLIC_KEY", "OAUTH_CLIENT_RSA_PUBLIC_KEY_2", "IS_AGENTIC");
    /** What an External OAuth integration takes (documented). */
    private static final List<String> EXTERNAL_OAUTH_TAKES = Arrays.asList("TYPE", "ENABLED", "EXTERNAL_OAUTH_TYPE",
        "EXTERNAL_OAUTH_ISSUER", "EXTERNAL_OAUTH_TOKEN_USER_MAPPING_CLAIM",
        "EXTERNAL_OAUTH_SNOWFLAKE_USER_MAPPING_ATTRIBUTE", "EXTERNAL_OAUTH_JWS_KEYS_URL",
        "EXTERNAL_OAUTH_BLOCKED_ROLES_LIST", "EXTERNAL_OAUTH_ALLOWED_ROLES_LIST", "EXTERNAL_OAUTH_RSA_PUBLIC_KEY",
        "EXTERNAL_OAUTH_RSA_PUBLIC_KEY_2", "EXTERNAL_OAUTH_AUDIENCE_LIST", "EXTERNAL_OAUTH_ANY_ROLE_MODE",
        "EXTERNAL_OAUTH_SCOPE_DELIMITER", "EXTERNAL_OAUTH_SCOPE_MAPPING_ATTRIBUTE", "NETWORK_POLICY", "COMMENT");
    /** What a SAML2 integration takes: a metadata URL or the identity provider's own four (documented). */
    private static final List<String> SAML2_TAKES = Arrays.asList("TYPE", "ENABLED", "METADATA_URL", "SAML2_ISSUER",
        "SAML2_SSO_URL", "SAML2_PROVIDER", "SAML2_X509_CERT", "ALLOWED_USER_DOMAINS", "ALLOWED_EMAIL_PATTERNS",
        "SAML2_SP_INITIATED_LOGIN_PAGE_LABEL", "SAML2_ENABLE_SP_INITIATED", "SAML2_SNOWFLAKE_X509_CERT",
        "SAML2_SIGN_REQUEST", "SAML2_REQUESTED_NAMEID_FORMAT", "SAML2_POST_LOGOUT_REDIRECT_URL", "SAML2_FORCE_AUTHN",
        "SAML2_SNOWFLAKE_ISSUER_URL", "SAML2_SNOWFLAKE_ACS_URL", "COMMENT");
    private static final List<String> SAML2_PROVIDER_OWN = Arrays.asList("SAML2_ISSUER", "SAML2_SSO_URL",
        "SAML2_PROVIDER", "SAML2_X509_CERT");
    /** What a SCIM integration takes (documented). */
    private static final List<String> SCIM_TAKES = Arrays.asList("TYPE", "ENABLED", "SCIM_CLIENT", "RUN_AS_ROLE",
        "NETWORK_POLICY", "SYNC_PASSWORD", "COMMENT");
    private static final List<String> OAUTH_CLIENTS = Arrays.asList("CUSTOM", "TABLEAU_DESKTOP", "TABLEAU_SERVER",
        "LOOKER");
    private static final List<String> OAUTH_CLIENT_TYPES = Arrays.asList("CONFIDENTIAL", "PUBLIC");
    private static final List<String> EXTERNAL_OAUTH_TYPES = Arrays.asList("OKTA", "AZURE", "PING_FEDERATE",
        "CUSTOM");
    private static final List<String> USER_MAPPING_ATTRIBUTES = Arrays.asList("LOGIN_NAME", "EMAIL_ADDRESS");
    private static final List<String> ANY_ROLE_MODES = Arrays.asList("DISABLE", "ENABLE", "ENABLE_FOR_PRIVILEGE");
    private static final List<String> SECONDARY_ROLE_USES = Arrays.asList("IMPLICIT", "NONE");
    private static final List<String> SCIM_CLIENTS = Arrays.asList("OKTA", "AZURE", "GENERIC");
    private static final List<String> AUTH_TYPES = Arrays.asList("OAUTH2", "AWS_IAM");

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

    /** What an external access integration takes, every one of which ALTER may set (documented). */
    private static final List<String> EXTERNAL_ACCESS_TAKES = Arrays.asList("ALLOWED_NETWORK_RULES",
        "ALLOWED_API_AUTHENTICATION_INTEGRATIONS", "ALLOWED_AUTHENTICATION_SECRETS", "ENABLED", "COMMENT");
    private static final List<String> EXTERNAL_ACCESS_UNSETS = Arrays.asList("ALLOWED_NETWORK_RULES",
        "ALLOWED_API_AUTHENTICATION_INTEGRATIONS", "ALLOWED_AUTHENTICATION_SECRETS", "COMMENT");
    /** What a storage integration takes, whichever cloud it reaches (documented). */
    private static final List<String> STORAGE_TAKES = Arrays.asList("TYPE", "STORAGE_PROVIDER",
        "STORAGE_AWS_ROLE_ARN", "STORAGE_AWS_EXTERNAL_ID", "STORAGE_AWS_OBJECT_ACL", "AZURE_TENANT_ID",
        "USE_PRIVATELINK_ENDPOINT", "ENABLED", "STORAGE_ALLOWED_LOCATIONS", "STORAGE_BLOCKED_LOCATIONS", "COMMENT");
    /** What a storage integration of every provider takes. */
    private static final List<String> STORAGE_COMMON = Arrays.asList("TYPE", "STORAGE_PROVIDER", "ENABLED",
        "STORAGE_ALLOWED_LOCATIONS", "STORAGE_BLOCKED_LOCATIONS", "COMMENT");
    /** What an S3 provider adds to the common set, and what an Azure one adds. */
    private static final List<String> STORAGE_S3_OWN = Arrays.asList("STORAGE_AWS_ROLE_ARN", "STORAGE_AWS_EXTERNAL_ID",
        "STORAGE_AWS_OBJECT_ACL", "USE_PRIVATELINK_ENDPOINT");
    private static final List<String> STORAGE_AZURE_OWN = Arrays.asList("AZURE_TENANT_ID", "USE_PRIVATELINK_ENDPOINT");
    /** What ALTER may set on a storage integration: all but its type and its provider (documented). */
    private static final List<String> STORAGE_FIXED = Arrays.asList("TYPE", "STORAGE_PROVIDER");
    private static final List<String> STORAGE_UNSETS = Arrays.asList("ENABLED", "STORAGE_BLOCKED_LOCATIONS",
        "COMMENT");
    private static final List<String> STORAGE_TYPES = Arrays.asList("EXTERNAL_STAGE", "POSTGRES_EXTERNAL_STORAGE");
    private static final List<String> STORAGE_PROVIDERS = Arrays.asList("S3", "S3CHINA", "S3GOV", "GCS", "AZURE");
    private static final List<String> NOTIFICATION_PROVIDERS = Arrays.asList("AWS_SNS", "AZURE_EVENT_GRID",
        "GCP_PUBSUB", "AZURE_STORAGE_QUEUE");
    private static final List<String> DIRECTIONS = Arrays.asList("INBOUND", "OUTBOUND");

    /** A Pub/Sub subscription's full name, its project's id six characters or more. */
    /**
     * A Pub/Sub name: {@code projects/<project>/topics|subscriptions/<name>}, the two words in any case, the project
     * 6 to 30 lower-case letters, digits and hyphens, a letter first and no hyphen last, the name three characters or
     * more of letters, digits and {@code - _ . ~ + %}, a letter first and not {@code goog} (live-verified).
     */
    private static final String PUBSUB_NAME = "(?i:projects)/[a-z][a-z0-9-]{4,28}[a-z0-9]/(?i:%s)/(?!goog)"
        + "[A-Za-z][A-Za-z0-9._~+%%-]{2,}";

    private static final Pattern SUBSCRIPTION_NAME = Pattern.compile(String.format(PUBSUB_NAME, "subscriptions"));

    private static final Pattern TOPIC_NAME = Pattern.compile(String.format(PUBSUB_NAME, "topics"));

    private static final List<String> SECRETS = Arrays.asList("API_KEY", "OAUTH_CLIENT_SECRET", "BEARER_TOKEN",
        "AWS_SECRET_KEY");

    /** The first of these a security integration's CREATE leaves out is named alone; the rest are named in a list. */
    private static final List<String> SECURITY_LEADING = Arrays.asList("TYPE", "ENABLED", "AUTH_TYPE");

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
        if (kind == IntegrationKind.SECURITY) {
            checkCreateSecurity(properties);
            return;
        }
        final PropertyValue storageType = properties.get("TYPE");
        if (kind == IntegrationKind.STORAGE && storageType != null
                && "POSTGRES_INTERNAL_STORAGE".equalsIgnoreCase(storageType.getText())) {
            // Its storage is a Snowflake Postgres instance's, which the engine does not model.
            throw new RuntimeException("Unsupported feature 'STORAGE INTEGRATION of type POSTGRES_INTERNAL_STORAGE'.");
        }
        for (final Map.Entry<String, PropertyValue> property : properties.entrySet()) {
            if (!takes(kind).contains(property.getKey())) {
                throw invalidValue(property.getKey(), property.getValue());
            }
        }
        final List<String> required = new ArrayList<>();
        if (kind == IntegrationKind.API) {
            final String provider = choice(properties, "API_PROVIDER", API_PROVIDERS);
            required.add("API_PROVIDER");
            if ("PYPI".equals(provider)) {
                refusePackageIndexOptions(properties);
            } else {
                required.add("API_ALLOWED_PREFIXES");
            }
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
        } else if (kind == IntegrationKind.EXTERNAL_ACCESS) {
            checkExternalAccessLists(properties);
            required.add("ALLOWED_NETWORK_RULES");
        } else if (kind == IntegrationKind.STORAGE) {
            requireStorage(properties, required);
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
                    // A topic's name is judged first; then Pub/Sub carries notifications out of a GCP account alone,
                    // and this account is an AWS one.
                    checkPubSubName("GCP_PUBSUB_TOPIC_NAME", TOPIC_NAME, properties.get("GCP_PUBSUB_TOPIC_NAME"));
                    if ("OUTBOUND".equals(direction)) {
                        throw new RuntimeException(SqlCompilationError.invalidValueForProperty("OUTBOUND",
                            "Direction"));
                    }
                    required.add("GCP_PUBSUB_SUBSCRIPTION_NAME");
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
        checkPubSubName("GCP_PUBSUB_SUBSCRIPTION_NAME", SUBSCRIPTION_NAME,
            properties.get("GCP_PUBSUB_SUBSCRIPTION_NAME"));
    }

    /**
     * What a storage integration requires: its type, its provider, what the provider is reached through — an AWS
     * role for S3, a tenant for Azure — and the locations it allows. A property of another provider's is refused as
     * one the integration does not take (documented).
     */
    private static void requireStorage(final Map<String, PropertyValue> properties, final List<String> required) {
        choice(properties, "TYPE", STORAGE_TYPES);
        final String provider = choice(properties, "STORAGE_PROVIDER", STORAGE_PROVIDERS);
        if (provider != null) {
            final List<String> takes = storageTakes(provider);
            for (final Map.Entry<String, PropertyValue> property : properties.entrySet()) {
                if (!takes.contains(property.getKey())) {
                    throw invalidValue(property.getKey(), property.getValue());
                }
            }
        }
        required.add("TYPE");
        required.add("STORAGE_PROVIDER");
        if (provider != null && provider.startsWith("S3")) {
            required.add("STORAGE_AWS_ROLE_ARN");
        } else if ("AZURE".equals(provider)) {
            required.add("AZURE_TENANT_ID");
        }
        required.add("STORAGE_ALLOWED_LOCATIONS");
    }

    /** What a storage integration of the provider takes. */
    private static List<String> storageTakes(final String provider) {
        final List<String> takes = new ArrayList<>(STORAGE_COMMON);
        if (provider != null && provider.startsWith("S3")) {
            takes.addAll(STORAGE_S3_OWN);
        } else if ("AZURE".equals(provider)) {
            takes.addAll(STORAGE_AZURE_OWN);
        }
        return takes;
    }

    /**
     * The lists of an external access integration, as its CREATE and ALTER pages write them: the network rules a
     * parenthesized list of names, the allowed security integrations such a list or NONE, the allowed secrets such
     * a list, ALL or NONE (documented). The words are stored upper-cased.
     */
    static void checkExternalAccessLists(final Map<String, PropertyValue> properties) {
        nameList(properties, "ALLOWED_NETWORK_RULES", false, false);
        nameList(properties, "ALLOWED_API_AUTHENTICATION_INTEGRATIONS", true, false);
        nameList(properties, "ALLOWED_AUTHENTICATION_SECRETS", true, true);
    }

    private static void nameList(final Map<String, PropertyValue> properties, final String key, final boolean none,
                                 final boolean all) {
        final PropertyValue value = properties.get(key);
        if (value == null) {
            return;
        }
        if (value.getKind() == PropertyValueKind.WORD) {
            final String word = value.getText().toUpperCase(Locale.ROOT);
            if (none && "NONE".equals(word) || all && "ALL".equals(word)) {
                properties.put(key, PropertyValue.word(word).writtenAs(value.getWritten()));
                return;
            }
            throw invalidValue(key, value);
        }
        if (value.getKind() != PropertyValueKind.LIST) {
            throw invalidValue(key, value);
        }
        for (final PropertyValue item : value.getItems()) {
            if (item.getKind() != PropertyValueKind.WORD) {
                throw invalidValue(key, value);
            }
        }
    }

    /** A Pub/Sub topic's or subscription's name (see {@code PUBSUB_NAME}). */
    private static void checkPubSubName(final String key, final Pattern shape, final PropertyValue value) {
        if (value != null && (value.getText() == null || !shape.matcher(value.getText()).matches())) {
            throw invalidValue(key, value);
        }
    }

    /** An API integration of the package index's provider takes none of the gateways' options. */
    private static void refusePackageIndexOptions(final Map<String, PropertyValue> properties) {
        final List<String> refused = new ArrayList<>();
        for (final String key : properties.keySet()) {
            if (!PYPI_TAKES.contains(key)) {
                refused.add(key);
            }
        }
        if (!refused.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("Invalid option(s) for API integration: " + refused));
        }
    }

    /**
     * Checks a security integration's CREATE. TYPE, ENABLED and AUTH_TYPE are asked for one at a time, and what
     * the authentication type needs beyond them is asked for as a list (live-verified). With ENABLED and AUTH_TYPE
     * both left out the account names either one; ENABLED is named here.
     */
    private static void checkCreateSecurity(final Map<String, PropertyValue> properties) {
        final PropertyValue writtenType = properties.get("TYPE");
        final String ownType = writtenType == null || writtenType.getText() == null ? null
            : writtenType.getText().toUpperCase(Locale.ROOT);
        if (SECURITY_TYPES_OWN.contains(ownType)) {
            checkCreateOwnSecurity(ownType, properties);
            return;
        }
        for (final String key : properties.keySet()) {
            if (SECURITY_TOLD_ONLY.contains(key)) {
                throw new RuntimeException(SqlCompilationError.of("invalid property '" + key
                    + "' for 'INTEGRATION - API_AUTHENTICATION'"));
            }
            if (!SECURITY_TAKES.contains(key)) {
                throw new RuntimeException(SqlCompilationError.of("invalid property '" + key + "' for 'INTEGRATION'"));
            }
        }
        choice(properties, "TYPE", SECURITY_TYPES);
        String authType = null;
        for (final String key : SECURITY_LEADING) {
            if (!properties.containsKey(key)) {
                throw new RuntimeException(SqlCompilationError.of("Missing option(s): " + key));
            }
            if ("AUTH_TYPE".equals(key)) {
                authType = choice(properties, key, AUTH_TYPES);
            }
        }
        // A property of the other authentication type is refused as none of this one's.
        for (final String key : properties.keySet()) {
            if (("AWS_IAM".equals(authType) ? OAUTH_ONLY : AWS_IAM_ONLY).contains(key)) {
                throw new RuntimeException(SqlCompilationError.of("invalid property '" + key + "' for 'AUTH_TYPE - "
                    + authType + "'"));
            }
        }
        final List<String> missing = new ArrayList<>();
        if ("AWS_IAM".equals(authType)) {
            require(properties, missing, "AWS_ROLE_ARN");
        } else {
            require(properties, missing, "OAUTH_CLIENT_ID");
            require(properties, missing, "OAUTH_CLIENT_SECRET");
        }
        if (!missing.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): " + missing));
        }
    }

    /**
     * Checks the CREATE of a Snowflake OAuth, External OAuth, SAML2 or SCIM integration against its page: every
     * property one its type takes, each variant-choosing one set to a value the type knows, ENABLED asked for alone
     * where the type requires it, and the rest the type requires asked for as a list (documented).
     */
    private static void checkCreateOwnSecurity(final String type, final Map<String, PropertyValue> properties) {
        final List<String> takes = securityTakes(type);
        for (final String key : properties.keySet()) {
            if (!takes.contains(key)) {
                throw new RuntimeException(SqlCompilationError.of("invalid property '" + key + "' for 'INTEGRATION'"));
            }
        }
        properties.put("TYPE", PropertyValue.word(type).writtenAs(properties.get("TYPE").getWritten()));
        final List<String> missing = new ArrayList<>();
        if ("OAUTH".equals(type)) {
            final String client = choice(properties, "OAUTH_CLIENT", OAUTH_CLIENTS);
            choice(properties, "OAUTH_CLIENT_TYPE", OAUTH_CLIENT_TYPES);
            choice(properties, "OAUTH_ANY_ROLE_MODE", ANY_ROLE_MODES);
            choice(properties, "OAUTH_USE_SECONDARY_ROLES", SECONDARY_ROLE_USES);
            require(properties, missing, "OAUTH_CLIENT");
            if ("CUSTOM".equals(client)) {
                require(properties, missing, "OAUTH_CLIENT_TYPE");
                require(properties, missing, "OAUTH_REDIRECT_URI");
            } else if ("LOOKER".equals(client)) {
                require(properties, missing, "OAUTH_REDIRECT_URI");
            }
        } else {
            if (!properties.containsKey("ENABLED")) {
                throw new RuntimeException(SqlCompilationError.of("Missing option(s): ENABLED"));
            }
            if ("EXTERNAL_OAUTH".equals(type)) {
                choice(properties, "EXTERNAL_OAUTH_TYPE", EXTERNAL_OAUTH_TYPES);
                choice(properties, "EXTERNAL_OAUTH_SNOWFLAKE_USER_MAPPING_ATTRIBUTE", USER_MAPPING_ATTRIBUTES);
                choice(properties, "EXTERNAL_OAUTH_ANY_ROLE_MODE", ANY_ROLE_MODES);
                require(properties, missing, "EXTERNAL_OAUTH_TYPE");
                require(properties, missing, "EXTERNAL_OAUTH_ISSUER");
                require(properties, missing, "EXTERNAL_OAUTH_TOKEN_USER_MAPPING_CLAIM");
                require(properties, missing, "EXTERNAL_OAUTH_SNOWFLAKE_USER_MAPPING_ATTRIBUTE");
            } else if ("SAML2".equals(type)) {
                if (!properties.containsKey("METADATA_URL")) {
                    for (final String key : SAML2_PROVIDER_OWN) {
                        require(properties, missing, key);
                    }
                }
            } else {
                choice(properties, "SCIM_CLIENT", SCIM_CLIENTS);
                require(properties, missing, "SCIM_CLIENT");
                require(properties, missing, "RUN_AS_ROLE");
            }
        }
        if (!missing.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): " + missing));
        }
    }

    /** What a security integration of the type takes; API_AUTHENTICATION's set for that type or none written. */
    private static List<String> securityTakes(final String type) {
        if ("OAUTH".equals(type)) {
            return OAUTH_TAKES;
        }
        if ("EXTERNAL_OAUTH".equals(type)) {
            return EXTERNAL_OAUTH_TAKES;
        }
        if ("SAML2".equals(type)) {
            return SAML2_TAKES;
        }
        return "SCIM".equals(type) ? SCIM_TAKES : SECURITY_TAKES;
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
        if (integration.getKind() == IntegrationKind.SECURITY) {
            // A property a security integration does not have is refused as no property of an integration, and
            // one that cannot change as no property of its type (live-verified).
            final List<String> takes = securityTakes(integration.text("TYPE"));
            for (final String key : properties.keySet()) {
                if (!takes.contains(key)) {
                    throw new RuntimeException(SqlCompilationError.of("invalid property '" + key
                        + "' for 'INTEGRATION'"));
                }
                if (SECURITY_FIXED.contains(key)) {
                    throw new RuntimeException(SqlCompilationError.of("invalid property '" + key
                        + "' for 'INTEGRATION - " + integration.getType() + "'"));
                }
            }
            return;
        }
        if (integration.getKind() == IntegrationKind.EXTERNAL_ACCESS) {
            checkExternalAccessLists(properties);
        }
        if (integration.getKind() == IntegrationKind.API && "PYPI".equals(integration.text("API_PROVIDER"))) {
            for (final String key : properties.keySet()) {
                if (!PYPI_TAKES.contains(key) || "API_PROVIDER".equals(key)) {
                    throw new RuntimeException(SqlCompilationError.of(
                        "Invalid property on API integration with api provider PYPI."));
                }
            }
            return;
        }
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
        if (kind == IntegrationKind.STORAGE) {
            return STORAGE_TAKES;
        }
        if (kind == IntegrationKind.EXTERNAL_ACCESS) {
            return EXTERNAL_ACCESS_TAKES;
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
        if (integration.getKind() == IntegrationKind.EXTERNAL_ACCESS) {
            return EXTERNAL_ACCESS_TAKES;
        }
        if (integration.getKind() == IntegrationKind.STORAGE) {
            final List<String> sets = storageTakes(integration.text("STORAGE_PROVIDER"));
            sets.removeAll(STORAGE_FIXED);
            return sets;
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
        if (kind == IntegrationKind.SECURITY) {
            return SECURITY_UNSETS;
        }
        if (kind == IntegrationKind.EXTERNAL_ACCESS) {
            return EXTERNAL_ACCESS_UNSETS;
        }
        if (kind == IntegrationKind.STORAGE) {
            return STORAGE_UNSETS;
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
