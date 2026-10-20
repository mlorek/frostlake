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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.GrantNames;
import dev.frostlake.metastore.IntegrationRegistry;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.TagAssignments;
import dev.frostlake.metastore.model.Integration;
import dev.frostlake.metastore.model.IntegrationKind;
import dev.frostlake.metastore.model.PropertyValue;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.PropertyValueKind;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObject;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CREATE, ALTER, DROP, SHOW and DESCRIBE of integrations — API, catalog, notification, storage, security and
 * external access integrations, which Frostlake keeps as configuration without ever calling the service they
 * describe. Every kind
 * shares one namespace, the ENABLED flag and the comment; the rest of an integration's properties are kept as written
 * and checked against its kind's property set ({@link IntegrationRules}). Secrets are write-only: DESCRIBE masks them.
 *
 * <p>An integration is an account object with an owner: CREATE takes CREATE INTEGRATION on the account, ALTER, DROP
 * and a CREATE OR REPLACE over one take its OWNERSHIP, and SHOW and DESCRIBE answer for an integration only to a role
 * that holds some privilege on it, or MANAGE GRANTS; to any other it is not there (documented: each statement's access
 * control table, and SHOW lists "only objects for which the current role has been granted at least one access
 * privilege"). Every role could create, widen, disable and drop every integration (FL-430).
 */
public final class IntegrationCommandHandler {

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    /**
     * @param catalog the catalog holding the account's integrations
     * @param queryExecutor the executor whose session variables a tag value may read
     */
    public IntegrationCommandHandler(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    private IntegrationRegistry registry() {
        return catalog.getIntegrations();
    }

    /** Runs a CREATE, ALTER or DROP of an integration and answers its status. */
    public ResultSet handle(final FrostlakeParser.IntegrationStatementContext ctx) {
        final String name = SqlIdentifiers.canonical(ctx.identifier());
        if (ctx.CREATE() != null) {
            return create(ctx, name);
        }
        final IntegrationKind kind = kindOf(ctx.integrationKind());
        final Integration integration = find(name, kind);
        if (ctx.DROP() != null) {
            if (integration == null) {
                if (ctx.if_exists() != null) {
                    return StatusResults.alreadyDropped(name);
                }
                throw missing(name);
            }
            requireOwnership(integration);
            refuseWhileReferenced(integration);
            registry().remove(name);
            // Its privileges go with it: an integration created later under the name starts with none.
            GrantNames.created(catalog, "INTEGRATION", integration.getName());
            return StatusResults.dropped(name);
        }
        if (integration == null) {
            if (ctx.if_exists() != null) {
                return StatusResults.of(StatusResults.EXECUTED);
            }
            throw missing(name);
        }
        requireOwnership(integration);
        alter(integration, ctx.integrationAction());
        return StatusResults.of(StatusResults.EXECUTED);
    }

    private ResultSet create(final FrostlakeParser.IntegrationStatementContext ctx, final String name) {
        if (ctx.if_not_exists() != null && ctx.or_replace() != null) {
            throw new RuntimeException("options IF NOT EXISTS and OR REPLACE are incompatible.");
        }
        final IntegrationKind kind = createdKind(ctx.createdIntegrationKind());
        final Map<String, PropertyValue> properties = ObjectProperties.read(ctx.objectProperty());
        IntegrationRules.checkCreate(kind, properties);
        if (kind == IntegrationKind.EXTERNAL_ACCESS) {
            resolveExternalAccess(properties);
        }
        AccountPrivileges.requireOnAccount(queryExecutor, catalog, Privilege.CREATE_INTEGRATION);
        final Integration standing = registry().find(name);
        if (standing != null && ctx.or_replace() != null) {
            requireOwnership(standing);
        }
        if (standing != null && ctx.or_replace() == null) {
            if (ctx.if_not_exists() != null) {
                return StatusResults.alreadyExists(name);
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(name)
                + "' already exists."));
        }
        final Integration integration = new Integration(name, kind);
        integration.setOwner(catalog.currentRoleForOwner());
        // ENABLED defaults to TRUE where a kind lets it be left out.
        integration.setEnabled(true);
        apply(integration, properties);
        registry().put(integration);
        // A replaced integration's privileges are not the new one's.
        GrantNames.created(catalog, "INTEGRATION", integration.getName());
        return StatusResults.created("Integration", name);
    }

    private void alter(final Integration integration, final FrostlakeParser.IntegrationActionContext action) {
        if (action.tagSet() != null) {
            for (final FrostlakeParser.TagAssignContext assign : action.tagSet().tagAssign()) {
                final Tag tag = catalog.getTagExact(ParseTreeText.getQualifiedName(assign.qualifiedName()));
                final String value = TagValues.text(assign.qualifiedName(), assign.tagValue(), queryExecutor);
                TagValues.requireAllowed(tag, value);
                TagAssignments.set(integration, tag, value);
            }
        } else if (action.tagUnset() != null) {
            for (final FrostlakeParser.QualifiedNameContext tagName : action.tagUnset().qualifiedName()) {
                TagAssignments.unset(integration, catalog.getTagExact(ParseTreeText.getQualifiedName(tagName)));
            }
        } else if (action.SET() != null) {
            final Map<String, PropertyValue> properties = ObjectProperties.read(action.objectProperty());
            IntegrationRules.checkSet(integration, properties);
            if (integration.getKind() == IntegrationKind.EXTERNAL_ACCESS) {
                resolveExternalAccess(properties);
            }
            if (properties.containsKey("ENABLED") && !ObjectProperties.flag("ENABLED", properties.get("ENABLED"))) {
                refuseWhileReferenced(integration);
            }
            final Map<String, PropertyValue> merged = new LinkedHashMap<>();
            for (final Map.Entry<String, PropertyValue> entry : properties.entrySet()) {
                final PropertyValue standing = integration.property(entry.getKey());
                if (standing != null && standing.getKind() == PropertyValueKind.PROPERTIES
                        && entry.getValue().getKind() == PropertyValueKind.PROPERTIES) {
                    // A nested property list set again keeps the entries it does not name.
                    final Map<String, PropertyValue> entries = new LinkedHashMap<>(standing.getEntries());
                    entries.putAll(entry.getValue().getEntries());
                    merged.put(entry.getKey(), PropertyValue.properties(entries));
                } else {
                    merged.put(entry.getKey(), entry.getValue());
                }
            }
            apply(integration, merged);
        } else {
            final List<String> keys = new ArrayList<>();
            for (final FrostlakeParser.OptionKeyContext key : action.optionKey()) {
                keys.add(ObjectProperties.key(key));
            }
            IntegrationRules.checkUnset(integration, keys);
            for (final String key : keys) {
                if ("ENABLED".equals(key)) {
                    // ENABLED defaults to TRUE.
                    integration.setEnabled(true);
                } else if ("COMMENT".equals(key)) {
                    integration.setComment(null);
                } else {
                    integration.getProperties().remove(key);
                }
            }
        }
    }

    /**
     * Resolves the names an external access integration's lists hold to the objects' full names: every network rule
     * has to exist and be an egress rule, every secret has to exist, and every allowed integration has to be a
     * security integration for external API authentication (documented).
     */
    private void resolveExternalAccess(final Map<String, PropertyValue> properties) {
        resolveSchemaObjects(properties, "ALLOWED_NETWORK_RULES", SecurityObjectKind.NETWORK_RULE);
        resolveSchemaObjects(properties, "ALLOWED_AUTHENTICATION_SECRETS", SecurityObjectKind.SECRET);
        final PropertyValue allowed = properties.get("ALLOWED_API_AUTHENTICATION_INTEGRATIONS");
        if (allowed == null || allowed.getKind() != PropertyValueKind.LIST) {
            return;
        }
        for (final PropertyValue item : allowed.getItems()) {
            final Integration named = registry().find(item.getText());
            if (named == null) {
                throw new RuntimeException(SqlCompilationError.of("Integration '" + item.getText()
                    + "' does not exist or not authorized."));
            }
            if (named.getKind() != IntegrationKind.SECURITY || !"API_AUTHENTICATION".equals(named.text("TYPE"))) {
                throw new RuntimeException(SqlCompilationError.of("Integration '" + item.getText()
                    + "' is not a security integration of type API_AUTHENTICATION."));
            }
        }
    }

    /** Resolves a list of schema objects of one kind, each to its full name; one that is not there is refused. */
    private void resolveSchemaObjects(final Map<String, PropertyValue> properties, final String key,
                                      final SecurityObjectKind kind) {
        final PropertyValue value = properties.get(key);
        if (value == null || value.getKind() != PropertyValueKind.LIST) {
            return;
        }
        final List<PropertyValue> resolved = new ArrayList<>();
        for (final PropertyValue item : value.getItems()) {
            final String[] parts = QualifiedName.parse(item.getText()).parts();
            final String last = parts[parts.length - 1];
            final Schema owner = owningSchema(parts);
            final SecurityObject object = owner == null ? null : owner.getSecurityObjects().get(kind, last);
            if (object == null) {
                throw new RuntimeException(kind == SecurityObjectKind.SECRET
                    ? SqlCompilationError.of("Secret '" + last + "' does not exist or operation not authorized.")
                    : SqlCompilationError.doesNotExist("Network rule", owner == null ? item.getText()
                        : owner.qualifiedName(last)));
            }
            if (kind == SecurityObjectKind.NETWORK_RULE && !"EGRESS".equals(object.text("MODE"))) {
                throw new RuntimeException(SqlCompilationError.of("Network rule '"
                    + SqlIdentifiers.spellAlreadyCanonicalPath(owner.qualifiedName(last))
                    + "' is not an egress rule; an external access integration allows egress rules only."));
            }
            resolved.add(PropertyValue.word(owner.qualifiedName(last)));
        }
        properties.put(key, PropertyValue.list(resolved).writtenAs(value.getWritten()));
    }

    /** The schema a written name belongs to, or null when its database or schema is not there. */
    private Schema owningSchema(final String[] parts) {
        try {
            return catalog.requireOwningSchema(QualifiedName.of(parts));
        } catch (final RuntimeException missing) {
            return null;
        }
    }

    /** A security integration a secret authenticates through can be neither dropped nor disabled (live-verified). */
    private void refuseWhileReferenced(final Integration integration) {
        if (integration.getKind() != IntegrationKind.SECURITY) {
            return;
        }
        final List<String> secrets = new IntegrationReferences(catalog).secretsNaming(integration);
        if (!secrets.isEmpty()) {
            throw new RuntimeException("Cannot drop or disable integration that is referenced by the secrets "
                + String.join(",", secrets) + ".");
        }
    }

    /** Stores the properties: ENABLED and COMMENT as the integration's own fields, the rest as written. */
    private static void apply(final Integration integration, final Map<String, PropertyValue> properties) {
        for (final Map.Entry<String, PropertyValue> entry : properties.entrySet()) {
            final String key = entry.getKey();
            if ("ENABLED".equals(key)) {
                integration.setEnabled(ObjectProperties.flag(key, entry.getValue()));
            } else if ("COMMENT".equals(key)) {
                integration.setComment(entry.getValue().getText());
            } else {
                integration.getProperties().put(key, entry.getValue());
            }
        }
    }

    /**
     * The integration of that name, or null.
     *
     * @throws RuntimeException when a kind is named and the integration is of another kind
     */
    private Integration find(final String name, final IntegrationKind kind) {
        final Integration integration = registry().find(name);
        if (integration == null || !sees(integration)) {
            // One the role holds no privilege on is not there for it, of whatever kind.
            return null;
        }
        if (kind != null && integration.getKind() != kind) {
            throw new RuntimeException("Integration " + name + " is not a " + kind.getWords() + " integration.");
        }
        return integration;
    }

    /** Whether the session holds some privilege on an integration, or MANAGE GRANTS, so that it is told about it. */
    private boolean sees(final Integration integration) {
        final SecurityManager security = queryExecutor.getSecurityManager();
        return security == null || security.sees("INTEGRATION", integration.getName(), integration.getOwner());
    }

    /** Refuses changing or dropping an integration the session does not own. */
    private void requireOwnership(final Integration integration) {
        queryExecutor.requireOwnership("INTEGRATION", new String[] {integration.getName()}, null);
    }

    private static RuntimeException missing(final String name) {
        return new RuntimeException(SqlCompilationError.doesNotExist("Integration", name));
    }

    /** SHOW [kind] INTEGRATIONS: every integration of the kind, or of every kind, by name. */
    public ResultSet show(final FrostlakeParser.IntegrationKindContext kindContext) {
        final IntegrationKind kind = kindOf(kindContext);
        final boolean notifications = kind == IntegrationKind.NOTIFICATION;
        final List<ResultSetColumn> columns = new ArrayList<>(Arrays.asList(
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("type", StringType.VARCHAR),
            new ResultSetColumn("category", StringType.VARCHAR),
            new ResultSetColumn("enabled", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON)));
        if (notifications) {
            columns.add(new ResultSetColumn("direction", StringType.VARCHAR));
        }
        final List<Row> rows = new ArrayList<>();
        for (final Integration integration : registry().all()) {
            if (kind != null && integration.getKind() != kind || !sees(integration)) {
                continue;
            }
            final List<Object> values = new ArrayList<>(Arrays.asList((Object) integration.getName(),
                integration.getType(), integration.getKind().getCategory(), integration.isEnabled() ? "true" : "false",
                integration.getComment(), ShowResultHelpers.createdOn(integration.getCreatedTime())));
            if (notifications) {
                values.add(direction(integration));
            }
            rows.add(new Row(values));
        }
        return new ResultSet(columns, rows);
    }

    /**
     * DESCRIBE [kind] INTEGRATION: one row per property — ENABLED, the kind's own properties as written, and the
     * comment — with lists comma-joined, nested property lists as JSON objects and secrets masked.
     */
    public ResultSet describe(final FrostlakeParser.IntegrationKindContext kindContext,
                              final FrostlakeParser.IdentifierContext nameContext) {
        final String name = SqlIdentifiers.canonical(nameContext);
        final Integration integration = find(name, kindOf(kindContext));
        if (integration == null) {
            throw missing(name);
        }
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("property", StringType.VARCHAR),
            new ResultSetColumn("property_type", StringType.VARCHAR),
            new ResultSetColumn("property_value", StringType.VARCHAR),
            new ResultSetColumn("property_default", StringType.VARCHAR));
        final List<Row> rows = new ArrayList<>();
        final List<String> shown = new ArrayList<>();
        shown.add("TYPE");
        if (integration.getKind() == IntegrationKind.SECURITY) {
            // The client's secret is never told, masked or not.
            shown.add("OAUTH_CLIENT_SECRET");
        }
        if (inboundPubSub(integration)) {
            // An inbound Pub/Sub queue lists its subscription and its service account, not its provider.
            shown.add("NOTIFICATION_PROVIDER");
        }
        for (final String[] row : layout(integration)) {
            shown.add(row[0]);
            rows.add(new Row(Arrays.asList((Object) row[0], row[1], value(integration, row[0]), row[2])));
        }
        for (final Map.Entry<String, PropertyValue> entry : integration.getProperties().entrySet()) {
            if (!shown.contains(entry.getKey())) {
                rows.add(new Row(Arrays.asList((Object) entry.getKey(), typeName(entry.getValue()),
                    value(integration, entry.getKey()), "")));
            }
        }
        rows.add(new Row(Arrays.asList((Object) "COMMENT", "String",
            ShowResultHelpers.text(integration.getComment()), "")));
        return new ResultSet(columns, rows);
    }

    /**
     * The properties DESCRIBE shows for the integration's variant, in its order, each as {name, type, default};
     * a property set outside the layout follows them, and the comment comes last.
     */
    private static String[][] layout(final Integration integration) {
        final String[] enabled = {"ENABLED", "Boolean", "true"};
        switch (integration.getKind()) {
            case SECURITY:
                return securityLayout(integration, enabled);
            case EXTERNAL_ACCESS:
                return new String[][] {enabled, {"ALLOWED_NETWORK_RULES", "List", "[]"},
                    {"ALLOWED_API_AUTHENTICATION_INTEGRATIONS", "List", "[]"},
                    {"ALLOWED_AUTHENTICATION_SECRETS", "List", "[]"}};
            case API:
                final String provider = String.valueOf(integration.text("API_PROVIDER"));
                if ("PYPI".equals(provider)) {
                    return new String[][] {enabled, {"API_PROVIDER", "String", ""}};
                }
                if ("GIT_HTTPS_API".equals(provider)) {
                    return new String[][] {enabled, {"USE_PRIVATELINK_ENDPOINT", "Boolean", "false"},
                        {"API_PROVIDER", "String", ""}, {"API_ALLOWED_PREFIXES", "List", "[]"},
                        {"API_BLOCKED_PREFIXES", "List", "[]"}, {"TLS_TRUSTED_CERTIFICATES", "List", "[]"}};
                }
                final String[][] head = {enabled, {"API_KEY", "String", ""}, {"API_PROVIDER", "String", ""}};
                final String[][] own = provider.startsWith("AZURE_")
                    ? new String[][] {{"AZURE_TENANT_ID", "String", ""}, {"AZURE_AD_APPLICATION_ID", "String", ""}}
                    : provider.startsWith("GOOGLE_") ? new String[][] {{"API_GCP_SERVICE_ACCOUNT", "String", ""},
                        {"GOOGLE_AUDIENCE", "String", ""}}
                    : new String[][] {{"API_AWS_IAM_USER_ARN", "String", ""}, {"API_AWS_ROLE_ARN", "String", ""},
                        {"API_AWS_EXTERNAL_ID", "String", ""}};
                return concat(concat(head, own), new String[][] {{"API_ALLOWED_PREFIXES", "List", "[]"},
                    {"API_BLOCKED_PREFIXES", "List", "[]"}});
            case STORAGE:
                return storageLayout(integration, enabled);
            case CATALOG:
                final String source = String.valueOf(integration.text("CATALOG_SOURCE"));
                final String[] refresh = {"REFRESH_INTERVAL_SECONDS", "Integer", "30"};
                if ("OBJECT_STORE".equals(source)) {
                    return new String[][] {enabled, {"CATALOG_SOURCE", "String", ""}, {"TABLE_FORMAT", "String", ""},
                        refresh};
                }
                final String[][] common = {enabled, {"CATALOG_SOURCE", "String", ""},
                    {"CATALOG_NAMESPACE", "String", ""}, {"TABLE_FORMAT", "String", ""}, refresh};
                if ("GLUE".equals(source)) {
                    return concat(common, new String[][] {{"GLUE_AWS_ROLE_ARN", "String", ""},
                        {"GLUE_CATALOG_ID", "String", ""}, {"GLUE_REGION", "String", ""},
                        {"GLUE_AWS_IAM_USER_ARN", "String", ""}, {"GLUE_AWS_EXTERNAL_ID", "String", ""}});
                }
                return concat(common, new String[][] {{"REST_CONFIG", "Object", ""},
                    {"REST_AUTHENTICATION", "Object", ""}});
            default:
                return notificationLayout(integration, enabled);
        }
    }

    /**
     * A storage integration's rows: the provider, the locations, and how the provider is reached — an S3 bucket
     * through an AWS role the account assumes as its IAM user (live-verified), a GCS bucket as the account's service
     * account, an Azure container through the account's application in the tenant (documented).
     */
    private static String[][] storageLayout(final Integration integration, final String[] enabled) {
        final String[][] head = {enabled, {"STORAGE_PROVIDER", "String", ""},
            {"STORAGE_ALLOWED_LOCATIONS", "List", "[]"}, {"STORAGE_BLOCKED_LOCATIONS", "List", "[]"}};
        final String provider = String.valueOf(integration.text("STORAGE_PROVIDER"));
        final String[] privateLink = {"USE_PRIVATELINK_ENDPOINT", "Boolean", "false"};
        if ("GCS".equals(provider)) {
            return concat(head, new String[][] {{"STORAGE_GCP_SERVICE_ACCOUNT", "String", ""}});
        }
        if ("AZURE".equals(provider)) {
            return concat(head, new String[][] {{"AZURE_TENANT_ID", "String", ""}, {"AZURE_CONSENT_URL", "String", ""},
                {"AZURE_MULTI_TENANT_APP_NAME", "String", ""}, privateLink});
        }
        return concat(head, new String[][] {{"STORAGE_AWS_IAM_USER_ARN", "String", ""},
            {"STORAGE_AWS_ROLE_ARN", "String", ""}, {"STORAGE_AWS_EXTERNAL_ID", "String", ""}, privateLink});
    }

    /**
     * A security integration's rows: an AWS role's, or an OAuth client's. A Snowflake OAuth, External OAuth, SAML2 or
     * SCIM integration shows ENABLED, then what it was written with.
     */
    private static String[][] securityLayout(final Integration integration, final String[] enabled) {
        final String type = String.valueOf(integration.text("TYPE"));
        if ("OAUTH".equals(type) || "EXTERNAL_OAUTH".equals(type) || "SAML2".equals(type) || "SCIM".equals(type)) {
            return new String[][] {enabled};
        }
        if ("AWS_IAM".equals(integration.text("AUTH_TYPE"))) {
            return new String[][] {enabled, {"PARENT_INTEGRATION", "String", ""},
                {"API_AWS_IAM_USER_ARN", "String", ""}, {"API_AWS_EXTERNAL_ID", "String", ""},
                {"AUTH_TYPE", "String", ""}, {"AWS_ROLE_ARN", "String", ""}};
        }
        return new String[][] {enabled, {"OAUTH_ACCESS_TOKEN_VALIDITY", "Long", ""},
            {"OAUTH_REFRESH_TOKEN_VALIDITY", "Long", "7776000"}, {"OAUTH_CLIENT_ID", "String", ""},
            {"OAUTH_CLIENT_AUTH_METHOD", "String", "CLIENT_SECRET_BASIC"},
            {"OAUTH_AUTHORIZATION_ENDPOINT", "String", ""}, {"OAUTH_TOKEN_ENDPOINT", "String", ""},
            {"OAUTH_ALLOWED_SCOPES", "List", "[]"}, {"OAUTH_GRANT", "String", ""}, {"OAUTH_USERNAME", "String", ""},
            {"OAUTH_RESOURCE_URL", "String", ""}, {"PARENT_INTEGRATION", "String", ""},
            {"AUTH_TYPE", "String", ""}, {"OAUTH_ASSERTION_ISSUER", "String", ""}};
    }

    private static String[][] notificationLayout(final Integration integration, final String[] enabled) {
        final String type = String.valueOf(integration.text("TYPE"));
        final String[] direction = {"DIRECTION", "String", ""};
        if ("EMAIL".equals(type)) {
            return new String[][] {enabled, direction, {"ALLOWED_RECIPIENTS", "List", "[]"},
                {"DEFAULT_RECIPIENTS", "List", "[]"}, {"DEFAULT_SUBJECT", "String", "Snowflake Email Notification"}};
        }
        if ("WEBHOOK".equals(type)) {
            return new String[][] {enabled, direction, {"WEBHOOK_URL", "String", ""},
                {"WEBHOOK_SECRET", "String", ""}, {"WEBHOOK_BODY_TEMPLATE", "String", ""},
                {"WEBHOOK_HEADERS", "Map", "{}"}};
        }
        final String provider = String.valueOf(integration.text("NOTIFICATION_PROVIDER"));
        final String[][] head = {enabled, {"NOTIFICATION_PROVIDER", "String", ""}, direction};
        if ("AWS_SNS".equals(provider)) {
            return concat(head, new String[][] {{"AWS_SNS_TOPIC_ARN", "String", ""},
                {"AWS_SNS_ROLE_ARN", "String", ""}, {"SF_AWS_IAM_USER_ARN", "String", ""},
                {"SF_AWS_EXTERNAL_ID", "String", ""}});
        }
        if ("AZURE_EVENT_GRID".equals(provider)) {
            return concat(head, new String[][] {{"AZURE_EVENT_GRID_TOPIC_ENDPOINT", "String", ""},
                {"AZURE_TENANT_ID", "String", ""}});
        }
        if ("AZURE_STORAGE_QUEUE".equals(provider)) {
            return concat(head, new String[][] {{"AZURE_STORAGE_QUEUE_PRIMARY_URI", "String", ""},
                {"AZURE_TENANT_ID", "String", ""}});
        }
        if (inboundPubSub(integration)) {
            return new String[][] {enabled, direction, {"GCP_PUBSUB_SUBSCRIPTION_NAME", "String", ""},
                {"GCP_PUBSUB_SERVICE_ACCOUNT", "String", ""}};
        }
        return concat(head, new String[][] {{"GCP_PUBSUB_TOPIC_NAME", "String", ""}});
    }

    /** Whether the integration is a Pub/Sub queue read into the account (live-verified DESCRIBE layout). */
    private static boolean inboundPubSub(final Integration integration) {
        return integration.getKind() == IntegrationKind.NOTIFICATION
            && "GCP_PUBSUB".equals(integration.text("NOTIFICATION_PROVIDER"))
            && !"OUTBOUND".equals(direction(integration));
    }

    private static String[][] concat(final String[][] first, final String[][] second) {
        final String[][] out = new String[first.length + second.length][];
        System.arraycopy(first, 0, out, 0, first.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }

    /** A notification integration's direction: as written, else OUTBOUND but for a queue written without one. */
    private static String direction(final Integration integration) {
        final String written = integration.text("DIRECTION");
        if (written != null) {
            return written;
        }
        return "QUEUE".equals(integration.text("TYPE")) ? "INBOUND" : "OUTBOUND";
    }

    /** The DESCRIBE value of one property: secrets masked, lists comma-joined, a header map as {k=v}. */
    private String value(final Integration integration, final String key) {
        if ("ENABLED".equals(key)) {
            return integration.isEnabled() ? "true" : "false";
        }
        final PropertyValue value = integration.property(key);
        if (integration.getKind() == IntegrationKind.SECURITY) {
            return securityValue(integration, key, value);
        }
        if (value == null) {
            if (key.endsWith("_AWS_IAM_USER_ARN")) {
                return GeneratedCloudIdentity.USER_ARN;
            }
            if (key.endsWith("_AWS_EXTERNAL_ID")) {
                return externalId(integration);
            }
            if ("API_GCP_SERVICE_ACCOUNT".equals(key) || "GCP_PUBSUB_SERVICE_ACCOUNT".equals(key)
                    || "STORAGE_GCP_SERVICE_ACCOUNT".equals(key)) {
                return GeneratedCloudIdentity.SERVICE_ACCOUNT;
            }
            if ("AZURE_CONSENT_URL".equals(key)) {
                return GeneratedCloudIdentity.consentUrl(integration.text("AZURE_TENANT_ID"));
            }
            if ("AZURE_MULTI_TENANT_APP_NAME".equals(key)) {
                return GeneratedCloudIdentity.APPLICATION_NAME;
            }
            if ("GLUE_REGION".equals(key)) {
                return accountRegion();
            }
            if ("DIRECTION".equals(key)) {
                return direction(integration);
            }
            if ("REFRESH_INTERVAL_SECONDS".equals(key)) {
                return "30";
            }
            if ("USE_PRIVATELINK_ENDPOINT".equals(key)) {
                return "false";
            }
            return "TLS_TRUSTED_CERTIFICATES".equals(key) ? "[]" : "";
        }
        if (value.getKind() == PropertyValueKind.PAIRS) {
            final StringBuilder out = new StringBuilder("{");
            for (final Map.Entry<String, PropertyValue> pair : value.getEntries().entrySet()) {
                if (out.length() > 1) {
                    out.append(", ");
                }
                out.append(pair.getKey()).append('=').append(pair.getValue().getText());
            }
            return out.append('}').toString();
        }
        return masked(key, value).describe();
    }

    /**
     * A security integration's DESCRIBE value: a validity left out reads 0, the allowed scopes read as a bracketed
     * list, and the identity the account generates for an AWS role is derived from the integration's name.
     */
    private String securityValue(final Integration integration, final String key, final PropertyValue value) {
        if (value == null) {
            if (key.endsWith("_VALIDITY")) {
                return "0";
            }
            if ("API_AWS_IAM_USER_ARN".equals(key)) {
                return GeneratedCloudIdentity.USER_ARN;
            }
            if ("API_AWS_EXTERNAL_ID".equals(key)) {
                return externalId(integration);
            }
            return "";
        }
        if ("OAUTH_ALLOWED_SCOPES".equals(key)) {
            final List<String> scopes = new ArrayList<>();
            for (final PropertyValue scope : value.getItems()) {
                scopes.add(scope.getText());
            }
            return scopes.toString();
        }
        return value.describe();
    }

    /** The external ID the account generates for an integration that assumes a cloud role. */
    private String externalId(final Integration integration) {
        return GeneratedCloudIdentity.externalId(catalog.getAccountLocator(),
            integration.getKind().getWords() + " INTEGRATION", integration.getName(), integration.getIdentitySeed());
    }

    /**
     * The cloud region of the account as a cloud names it, which a Glue catalog written without a region is read
     * in: {@code eu-west-1} for an account in AWS_EU_WEST_1 (live-verified); empty for an account of another cloud.
     */
    private String accountRegion() {
        final String region = queryExecutor.getEngineConfig().getRegion().toUpperCase(Locale.ROOT);
        return region.startsWith("AWS_") ? region.substring("AWS_".length()).toLowerCase(Locale.ROOT).replace('_', '-')
            : "";
    }

    /** The value with every secret it holds, at any depth, masked. */
    private static PropertyValue masked(final String key, final PropertyValue value) {
        if (IntegrationRules.isSecret(key) && value.getText() != null) {
            return PropertyValue.text(IntegrationRules.mask(value.getText()));
        }
        if (value.getKind() != PropertyValueKind.PROPERTIES) {
            return value;
        }
        final Map<String, PropertyValue> entries = new LinkedHashMap<>();
        for (final Map.Entry<String, PropertyValue> entry : value.getEntries().entrySet()) {
            entries.put(entry.getKey(), masked(entry.getKey(), entry.getValue()));
        }
        return PropertyValue.properties(entries);
    }

    private static String typeName(final PropertyValue value) {
        switch (value.getKind()) {
            case BOOLEAN:
                return "Boolean";
            case NUMBER:
                return "Integer";
            case LIST:
                return "List";
            case PAIRS:
                return "Map";
            case PROPERTIES:
                return "Object";
            default:
                return "String";
        }
    }

    private static IntegrationKind createdKind(final FrostlakeParser.CreatedIntegrationKindContext ctx) {
        if (ctx.API() != null) {
            return IntegrationKind.API;
        }
        if (ctx.SECURITY() != null) {
            return IntegrationKind.SECURITY;
        }
        if (ctx.EXTERNAL() != null) {
            return IntegrationKind.EXTERNAL_ACCESS;
        }
        if (ctx.STORAGE() != null) {
            return IntegrationKind.STORAGE;
        }
        return ctx.CATALOG() != null ? IntegrationKind.CATALOG : IntegrationKind.NOTIFICATION;
    }

    private static IntegrationKind kindOf(final FrostlakeParser.IntegrationKindContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.API() != null) {
            return IntegrationKind.API;
        }
        if (ctx.CATALOG() != null) {
            return IntegrationKind.CATALOG;
        }
        if (ctx.NOTIFICATION() != null) {
            return IntegrationKind.NOTIFICATION;
        }
        if (ctx.STORAGE() != null) {
            return IntegrationKind.STORAGE;
        }
        return ctx.SECURITY() != null ? IntegrationKind.SECURITY : IntegrationKind.EXTERNAL_ACCESS;
    }
}
