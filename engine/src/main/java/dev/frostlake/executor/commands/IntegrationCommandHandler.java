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
import dev.frostlake.metastore.IntegrationRegistry;
import dev.frostlake.metastore.model.Integration;
import dev.frostlake.metastore.model.IntegrationKind;
import dev.frostlake.metastore.model.PropertyValue;
import dev.frostlake.metastore.model.PropertyValueKind;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CREATE, ALTER, DROP, SHOW and DESCRIBE of integrations — API, catalog and notification integrations, which
 * Frostlake keeps as configuration without ever calling the service they describe. Every kind shares one
 * namespace, the ENABLED flag and the comment; the rest of an integration's properties are kept as written and
 * checked against its kind's property set ({@link IntegrationRules}). Secrets are write-only: DESCRIBE masks them.
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
            registry().remove(name);
            return StatusResults.dropped(name);
        }
        if (integration == null) {
            if (ctx.if_exists() != null) {
                return StatusResults.of(StatusResults.EXECUTED);
            }
            throw missing(name);
        }
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
        final Integration standing = registry().find(name);
        if (standing != null && ctx.or_replace() == null) {
            if (ctx.if_not_exists() != null) {
                return StatusResults.alreadyExists(name);
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(name)
                + "' already exists."));
        }
        final Integration integration = new Integration(name, kind);
        integration.setOwner(catalog.currentRoleForOwner());
        apply(integration, properties);
        registry().put(integration);
        return StatusResults.created("Integration", name);
    }

    private void alter(final Integration integration, final FrostlakeParser.IntegrationActionContext action) {
        if (action.tagSet() != null) {
            for (final FrostlakeParser.TagAssignContext assign : action.tagSet().tagAssign()) {
                final Tag tag = catalog.getTag(ParseTreeText.getQualifiedName(assign.qualifiedName()));
                final String value = TagValues.text(assign.qualifiedName(), assign.tagValue(), queryExecutor);
                TagValues.requireAllowed(tag, value);
                integration.setTag(tag.getName(), value);
            }
        } else if (action.tagUnset() != null) {
            for (final FrostlakeParser.QualifiedNameContext tagName : action.tagUnset().qualifiedName()) {
                integration.unsetTag(catalog.getTag(ParseTreeText.getQualifiedName(tagName)).getName());
            }
        } else if (action.SET() != null) {
            final Map<String, PropertyValue> properties = ObjectProperties.read(action.objectProperty());
            IntegrationRules.checkSet(integration, properties);
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
        if (integration != null && kind != null && integration.getKind() != kind) {
            throw new RuntimeException("Integration " + name + " is not a " + kind.getWords() + " integration.");
        }
        return integration;
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
            if (kind != null && integration.getKind() != kind) {
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
            case API:
                final String provider = String.valueOf(integration.text("API_PROVIDER"));
                if ("GIT_HTTPS_API".equals(provider)) {
                    return new String[][] {enabled, {"USE_PRIVATELINK_ENDPOINT", "Boolean", "false"},
                        {"API_PROVIDER", "String", ""}, {"API_ALLOWED_PREFIXES", "List", "[]"},
                        {"API_BLOCKED_PREFIXES", "List", "[]"}, {"TLS_TRUSTED_CERTIFICATES", "List", "[]"}};
                }
                final String[][] head = {enabled, {"API_KEY", "String", ""}, {"API_PROVIDER", "String", ""}};
                final String[][] own = provider.startsWith("AZURE_")
                    ? new String[][] {{"AZURE_TENANT_ID", "String", ""}, {"AZURE_AD_APPLICATION_ID", "String", ""}}
                    : provider.startsWith("GOOGLE_") ? new String[][] {{"GOOGLE_AUDIENCE", "String", ""}}
                    : new String[][] {{"API_AWS_ROLE_ARN", "String", ""}};
                return concat(concat(head, own), new String[][] {{"API_ALLOWED_PREFIXES", "List", "[]"},
                    {"API_BLOCKED_PREFIXES", "List", "[]"}});
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
                        {"GLUE_CATALOG_ID", "String", ""}, {"GLUE_REGION", "String", ""}});
                }
                return concat(common, new String[][] {{"REST_CONFIG", "Object", ""},
                    {"REST_AUTHENTICATION", "Object", ""}});
            default:
                return notificationLayout(integration, enabled);
        }
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
                {"AWS_SNS_ROLE_ARN", "String", ""}});
        }
        if ("AZURE_EVENT_GRID".equals(provider)) {
            return concat(head, new String[][] {{"AZURE_EVENT_GRID_TOPIC_ENDPOINT", "String", ""},
                {"AZURE_TENANT_ID", "String", ""}});
        }
        if ("AZURE_STORAGE_QUEUE".equals(provider)) {
            return concat(head, new String[][] {{"AZURE_STORAGE_QUEUE_PRIMARY_URI", "String", ""},
                {"AZURE_TENANT_ID", "String", ""}});
        }
        return concat(head, new String[][] {{"OUTBOUND".equals(direction(integration)) ? "GCP_PUBSUB_TOPIC_NAME"
            : "GCP_PUBSUB_SUBSCRIPTION_NAME", "String", ""}});
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
    private static String value(final Integration integration, final String key) {
        if ("ENABLED".equals(key)) {
            return integration.isEnabled() ? "true" : "false";
        }
        final PropertyValue value = integration.property(key);
        if (value == null) {
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
