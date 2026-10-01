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

import dev.frostlake.executor.ConditionalDdlOutcome;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObject;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.SecurityObjectStore;
import dev.frostlake.metastore.model.SecurityPropertySpec;
import dev.frostlake.metastore.model.SecurityPropertySpecs;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.antlr.v4.runtime.tree.TerminalNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * CREATE, ALTER, DROP, SHOW and DESCRIBE of network rules, network policies, password policies and secrets, and the
 * attachment of a password policy or a network policy to the account or to a user.
 *
 * <p>The objects have no behaviour: nothing here authenticates, allows or blocks anyone. They are recorded so that
 * scripts that set an account up can create, describe, alter and drop them as they would on a real account. A
 * property is checked against its kind's documented set ({@link SecurityPropertySpecs}); one the kind does not have
 * is refused as an invalid property of the kind. A secret's credential — its password, secret string or OAuth
 * refresh token — is kept and never shown back by any listing or description.
 */
public final class SecurityObjectCommandHandler {

    private static final String ACCOUNT = "ACCOUNT";
    private static final String PASSWORD_POLICY_PREFIX = "PASSWORD_POLICY|";
    private static final String NETWORK_POLICY_PREFIX = "NETWORK_POLICY|";

    /** The modes a network rule of type IPV4 takes. */
    private static final List<String> IPV4_MODES = Collections.unmodifiableList(Arrays.asList("INGRESS",
        "POSTGRES_INGRESS", "POSTGRES_EGRESS"));

    /** One DNS label: letters and digits, with hyphens inside it only. */
    private static final Pattern HOST_LABEL = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?");

    /** The top-level domains reserved never to resolve. */
    private static final List<String> RESERVED_TOP_LEVEL_DOMAINS = Collections.unmodifiableList(Arrays.asList(
        "example", "invalid", "localhost", "test"));

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    /**
     * @param catalog the catalog the objects are recorded in
     * @param queryExecutor the executor whose session variables a tag value may read
     */
    public SecurityObjectCommandHandler(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    // ---- CREATE / ALTER / DROP ---------------------------------------------------------------------------------

    /**
     * Runs one CREATE, ALTER or DROP of the four kinds, or an attachment of a policy to the account or a user.
     *
     * @param ctx the statement
     * @return null: the statement answers with its status sentence
     */
    public Object handle(final FrostlakeParser.SecurityObjectStatementContext ctx) {
        if (ctx.ACCOUNT() != null || ctx.USER() != null) {
            return attach(ctx);
        }
        final SecurityObjectKind kind = kindOf(ctx.RULE() != null, ctx.NETWORK() != null, ctx.SECRET() != null);
        if (ctx.CREATE() != null) {
            return create(ctx, kind);
        }
        if (ctx.DROP() != null) {
            return drop(ctx, kind);
        }
        return alter(ctx, kind);
    }

    private static SecurityObjectKind kindOf(final boolean rule, final boolean network, final boolean secret) {
        if (rule) {
            return SecurityObjectKind.NETWORK_RULE;
        }
        if (network) {
            return SecurityObjectKind.NETWORK_POLICY;
        }
        return secret ? SecurityObjectKind.SECRET : SecurityObjectKind.PASSWORD_POLICY;
    }

    private Schema owningSchema(final FrostlakeParser.QualifiedNameContext name) {
        return catalog.requireOwningSchema(QualifiedName.of(ParseTreeText.qualifiedNameParts(name)));
    }

    private static String lastPart(final FrostlakeParser.QualifiedNameContext name) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name);
        return parts[parts.length - 1];
    }

    private SecurityObjectStore store(final SecurityObjectKind kind, final Schema schema) {
        return kind.isSchemaObject() ? schema.getSecurityObjects() : catalog.getSecurityObjects();
    }

    private static String display(final SecurityObjectKind kind, final Schema schema, final String name) {
        return kind.isSchemaObject() ? schema.qualifiedName(name) : name;
    }

    private static RuntimeException missing(final SecurityObjectKind kind, final Schema schema, final String name) {
        return new RuntimeException(SqlCompilationError.doesNotExist(kind.noun(), display(kind, schema, name)));
    }

    private Object create(final FrostlakeParser.SecurityObjectStatementContext ctx, final SecurityObjectKind kind) {
        if (ctx.or_replace() != null && ctx.if_not_exists() != null) {
            throw new RuntimeException(SqlCompilationError.of("options IF NOT EXISTS and OR REPLACE are incompatible."));
        }
        final Schema schema = kind.isSchemaObject() ? owningSchema(ctx.qualifiedName(0)) : null;
        final String name = kind.isSchemaObject() ? lastPart(ctx.qualifiedName(0))
            : SqlIdentifiers.canonical(ctx.identifier(0));
        final Map<String, Object> properties = read(kind, ctx.securityProperty());
        if (kind == SecurityObjectKind.NETWORK_POLICY) {
            requireIngressRules(properties, name);
        }
        final SecurityObjectStore store = store(kind, schema);
        final SecurityObject existing = store.get(kind, name);
        if (existing != null && ctx.or_alter() != null) {
            recreateInPlace(existing, properties);
            return created(kind, name);
        }
        if (existing != null && ctx.or_replace() != null) {
            // Replacing an object needs OWNERSHIP of it.
            queryExecutor.requireOwnership(kind.name(), nameParts(ctx, kind, name), null);
        }
        if (existing != null && ctx.or_replace() != null && kind == SecurityObjectKind.PASSWORD_POLICY
                && isAttached(PASSWORD_POLICY_PREFIX, display(kind, schema, name))) {
            throw policyInUse(name);
        }
        if (existing != null && ctx.or_replace() == null) {
            if (ctx.if_not_exists() != null) {
                ConditionalDdlOutcome.createSkipped();
                return null;
            }
            final String written = kind.isSchemaObject()
                ? String.join(".", ParseTreeText.qualifiedNameParts(ctx.qualifiedName(0))) : name;
            throw new RuntimeException(SqlCompilationError.of("Object '"
                + SqlIdentifiers.spellAlreadyCanonicalPath(written) + "' already exists."));
        }
        requireCreatable(kind, properties);
        if (kind == SecurityObjectKind.NETWORK_RULE && "HOST_PORT".equals(properties.get("TYPE"))
                && properties.get("VALUE_LIST") != null) {
            requireResolvableHosts(asStrings(properties.get("VALUE_LIST")));
        }
        final SecurityObject created = new SecurityObject(kind, name);
        created.setOwner(catalog.currentRoleForOwner());
        if (kind == SecurityObjectKind.NETWORK_RULE && !properties.containsKey("MODE")) {
            created.setProperty("MODE", "INGRESS");
        }
        if (kind == SecurityObjectKind.NETWORK_RULE && !properties.containsKey("VALUE_LIST")) {
            created.setProperty("VALUE_LIST", new ArrayList<String>());
        }
        apply(created, properties);
        store.add(created);
        return created(kind, name);
    }

    /**
     * The sentence a CREATE answers with: a network rule or a network policy "is created", a password policy is
     * "created successfully" under its quoted name, and a secret takes the common sentence.
     */
    private static ResultSet created(final SecurityObjectKind kind, final String name) {
        final String sentence;
        if (kind == SecurityObjectKind.NETWORK_RULE || kind == SecurityObjectKind.NETWORK_POLICY) {
            sentence = kind.noun() + " " + name + " is created.";
        } else if (kind == SecurityObjectKind.PASSWORD_POLICY) {
            sentence = kind.noun() + " '" + name + "' created successfully";
        } else {
            return null;
        }
        return new ResultSet(columns("status"), Collections.singletonList(new Row(Arrays.<Object>asList(sentence))));
    }

    /** The properties a CREATE must carry: a TYPE for a network rule or a secret, and what a secret's type needs. */
    private static void requireCreatable(final SecurityObjectKind kind, final Map<String, Object> properties) {
        if (kind != SecurityObjectKind.NETWORK_RULE && kind != SecurityObjectKind.SECRET) {
            return;
        }
        final Object type = properties.get("TYPE");
        if (type == null) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): [TYPE]"));
        }
        if (kind == SecurityObjectKind.NETWORK_RULE) {
            final Object mode = properties.containsKey("MODE") ? properties.get("MODE") : "INGRESS";
            if ("IPV4".equals(type) && !IPV4_MODES.contains(mode)) {
                throw new RuntimeException(SqlCompilationError.PREFIX + " The network rule mode " + mode
                    + " is not supported by the network rule type IPv4. The network rule mode must be one of "
                    + IPV4_MODES + ".\n");
            }
            return;
        }
        final List<String> takes = SecurityPropertySpecs.secretCreateProperties(type.toString());
        for (final String property : properties.keySet()) {
            if (!"TYPE".equals(property) && !takes.contains(property)) {
                throw new RuntimeException(wrongTypeProperty(property, type.toString()));
            }
        }
        final List<String> absent = new ArrayList<>();
        for (final String required : SecurityPropertySpecs.secretRequiredProperties(type.toString())) {
            if (!properties.containsKey(required)) {
                absent.add(required);
            }
        }
        if (!absent.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("Missing option(s): " + absent));
        }
    }

    /**
     * CREATE OR ALTER over an existing network rule or network policy: the properties the statement names are set,
     * and the others are left as they are. A network rule's TYPE and MODE cannot change.
     */
    private static void recreateInPlace(final SecurityObject existing, final Map<String, Object> properties) {
        if (existing.getKind() == SecurityObjectKind.NETWORK_RULE) {
            for (final String fixed : new String[] {"TYPE", "MODE"}) {
                final Object given = properties.containsKey(fixed) ? properties.get(fixed)
                    : "MODE".equals(fixed) ? "INGRESS" : null;
                if (!existing.getProperty(fixed).equals(given)) {
                    throw new RuntimeException(SqlCompilationError.PREFIX + " Property '" + fixed
                        + "' cannot be changed in CREATE OR ALTER.");
                }
            }
        }
        final Map<String, Object> changes = new LinkedHashMap<>(properties);
        changes.remove("TYPE");
        changes.remove("MODE");
        apply(existing, changes);
    }

    private static void apply(final SecurityObject target, final Map<String, Object> properties) {
        for (final Map.Entry<String, Object> property : properties.entrySet()) {
            target.setProperty(property.getKey(), property.getValue());
            if ("COMMENT".equals(property.getKey())) {
                target.setComment((String) property.getValue());
            }
        }
    }

    /** The properties a statement writes, each checked against its kind's documented set and value shape. */
    private Map<String, Object> read(final SecurityObjectKind kind,
                                     final List<FrostlakeParser.SecurityPropertyContext> written) {
        final Map<String, Object> properties = new LinkedHashMap<>();
        for (final FrostlakeParser.SecurityPropertyContext property : written) {
            final String key = property.optionKey().getText().toUpperCase(Locale.ROOT);
            final SecurityPropertySpec spec = SecurityPropertySpecs.find(kind, key);
            if (spec == null) {
                throw new RuntimeException(SqlCompilationError.invalidPropertyFor(key, kind.name()));
            }
            if (properties.containsKey(key)) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty(key));
            }
            Object value = value(spec, property.securityPropertyValue());
            if (key.endsWith("_NETWORK_RULE_LIST")) {
                value = ruleReferences(asStrings(value));
            }
            properties.put(key, value);
        }
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStrings(final Object value) {
        return (List<String>) value;
    }

    private static Object value(final SecurityPropertySpec spec, final FrostlakeParser.SecurityPropertyValueContext v) {
        final String written = v.getText();
        final String key = spec.getName();
        final TerminalNode text = v.STRING_LITERAL().isEmpty() || v.LPAREN() != null ? null : v.STRING_LITERAL(0);
        final String word = v.identifier() != null ? v.identifier().getText().toUpperCase(Locale.ROOT)
            : text != null ? SqlStringLiterals.decode(text.getText()).toUpperCase(Locale.ROOT) : null;
        switch (spec.getType()) {
            case TEXT:
                if (text == null) {
                    throw invalidValue(written, key);
                }
                return SqlStringLiterals.decode(text.getText());
            case INTEGER:
                if (v.INTEGER_LITERAL() == null) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(written, key));
                }
                final long number;
                try {
                    number = Long.parseLong(written);
                } catch (final NumberFormatException tooLarge) {
                    throw invalidValue(written, key);
                }
                if (number < spec.getMin() || number > spec.getMax()) {
                    throw invalidValue(written, key);
                }
                return Long.valueOf(number);
            case NAME:
                if (v.identifier() != null) {
                    return SqlIdentifiers.canonical(v.identifier());
                }
                if (text == null) {
                    throw invalidValue(written, key);
                }
                return SqlStringLiterals.decode(text.getText());
            case TEXT_LIST:
                if (v.LPAREN() == null) {
                    throw new RuntimeException(SqlCompilationError.of("invalid type of property '" + written + "' for '"
                        + key + "'"));
                }
                final List<String> items = new ArrayList<>();
                for (final TerminalNode item : v.STRING_LITERAL()) {
                    items.add(SqlStringLiterals.decode(item.getText()));
                }
                return items;
            case BOOLEAN:
                if ("TRUE".equals(word) || "FALSE".equals(word)) {
                    return Boolean.valueOf("TRUE".equals(word));
                }
                throw invalidValue(written, key);
            case CHOICE:
            default:
                if (word != null && Arrays.asList(spec.getChoices()).contains(word)) {
                    return word;
                }
                if ("ALGORITHM".equals(key)) {
                    throw new RuntimeException(SqlCompilationError.PREFIX + " Invalid algorithm name " + written
                        + " for SYMMETRIC_KEY Secret.");
                }
                if ("TYPE".equals(key) && spec.getChoices().length > 0 && "IPV4".equals(spec.getChoices()[0])) {
                    throw new RuntimeException(
                        "The network rule type must be one of the snowflake supported network rule type.");
                }
                throw invalidValue(written, key);
        }
    }

    /** The object's name, part by part: a schema object's as written, a network policy's its own. */
    private static String[] nameParts(final FrostlakeParser.SecurityObjectStatementContext ctx,
                                      final SecurityObjectKind kind, final String name) {
        return kind.isSchemaObject() ? ParseTreeText.qualifiedNameParts(ctx.qualifiedName(0)) : new String[] {name};
    }

    /**
     * A HOST_PORT rule's values name hosts the account resolves when the rule is made, each with an optional port,
     * and one that cannot resolve refuses the whole list. With no resolver at hand the name's shape is read instead:
     * a port is 0 to 65535, and a host is two or more DNS labels behind an optional leading {@code *.} wildcard,
     * whose last label is not numeric — an address is no host name — and is no top-level domain reserved never to
     * resolve.
     */
    private static void requireResolvableHosts(final List<String> values) {
        for (final String value : values) {
            if (!resolvableHostPort(value)) {
                throw new RuntimeException(SqlCompilationError.of("invalid value '" + values
                    + "' for property 'VALUE_LIST', Reason: One or more values might be an unresolvable host name."
                    + " Verify all hosts are resolvable, or use a wildcard pattern."));
            }
        }
    }

    private static boolean resolvableHostPort(final String value) {
        String host = value;
        final int colon = value.lastIndexOf(':');
        if (colon >= 0) {
            final String port = value.substring(colon + 1);
            if (port.isEmpty() || port.length() > 5) {
                return false;
            }
            for (int i = 0; i < port.length(); i++) {
                if (!Character.isDigit(port.charAt(i))) {
                    return false;
                }
            }
            if (Integer.parseInt(port) > 65535) {
                return false;
            }
            host = value.substring(0, colon);
        }
        if (host.startsWith("*.")) {
            host = host.substring(2);
        }
        final String[] labels = host.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (final String label : labels) {
            if (!HOST_LABEL.matcher(label).matches()) {
                return false;
            }
        }
        final String topLevel = labels[labels.length - 1].toLowerCase(Locale.ROOT);
        boolean numeric = true;
        for (int i = 0; i < topLevel.length(); i++) {
            numeric &= Character.isDigit(topLevel.charAt(i));
        }
        return !numeric && !RESERVED_TOP_LEVEL_DOMAINS.contains(topLevel);
    }

    /** A secret property the secret's type does not take. */
    private static String wrongTypeProperty(final String property, final String type) {
        return SqlCompilationError.of("invalid type of property '" + property + "' for '" + type + "'");
    }

    /** A password policy something is attached to, which can be neither dropped nor replaced. */
    private static RuntimeException policyInUse(final String name) {
        return new RuntimeException(SqlCompilationError.PREFIX + " Policy " + name
            + " cannot be dropped/replaced as it is associated with one or more entities.");
    }

    /** Whether a password or network policy, named as attachments record it, is attached to anything. */
    private boolean isAttached(final String prefix, final String attached) {
        for (final Map.Entry<String, String> attachment : catalog.getSecurityObjects().attachments().entrySet()) {
            if (attachment.getKey().startsWith(prefix) && attachment.getValue().equals(attached)) {
                return true;
            }
        }
        return false;
    }

    /** A network policy takes no egress rule: such a rule has no effect there. */
    private void requireIngressRules(final Map<String, Object> properties, final String policy) {
        for (final String list : new String[] {"ALLOWED_NETWORK_RULE_LIST", "BLOCKED_NETWORK_RULE_LIST"}) {
            if (properties.get(list) != null) {
                for (final String rule : asStrings(properties.get(list))) {
                    requireIngressRule(rule, policy);
                }
            }
        }
    }

    private void requireIngressRule(final String qualified, final String policy) {
        final QualifiedName name = QualifiedName.of(SqlIdentifiers.canonicalTextParts(qualified));
        final SecurityObject rule = catalog.requireOwningSchema(name).getSecurityObjects()
            .get(SecurityObjectKind.NETWORK_RULE, name.last());
        if (rule != null && "EGRESS".equals(rule.text("MODE"))) {
            throw new RuntimeException("The egress network rule " + name.last()
                + " cannot be attached to the network policy " + policy
                + ". Egress network rules do not take effect when attached to network policies.");
        }
    }

    private static RuntimeException invalidValue(final String written, final String property) {
        return new RuntimeException(SqlCompilationError.invalidValueForProperty(written, property));
    }

    /**
     * The network rules a network policy's rule list names, each resolved to an existing rule and kept as its
     * fully qualified name.
     */
    private List<String> ruleReferences(final List<String> written) {
        final List<String> resolved = new ArrayList<>();
        for (final String reference : written) {
            final String qualified = ruleReference(reference);
            if (!resolved.contains(qualified)) {
                resolved.add(qualified);
            }
        }
        return resolved;
    }

    private String ruleReference(final String reference) {
        final QualifiedName name = QualifiedName.of(SqlIdentifiers.canonicalTextParts(reference));
        final Schema schema = catalog.requireOwningSchema(name);
        if (schema.getSecurityObjects().get(SecurityObjectKind.NETWORK_RULE, name.last()) == null) {
            throw missing(SecurityObjectKind.NETWORK_RULE, schema, name.last());
        }
        return schema.qualifiedName(name.last());
    }

    private Object drop(final FrostlakeParser.SecurityObjectStatementContext ctx, final SecurityObjectKind kind) {
        final Schema schema = kind.isSchemaObject() ? owningSchema(ctx.qualifiedName(0)) : null;
        final String name = kind.isSchemaObject() ? lastPart(ctx.qualifiedName(0))
            : SqlIdentifiers.canonical(ctx.identifier(0));
        final SecurityObjectStore store = store(kind, schema);
        if (store.get(kind, name) == null) {
            if (ctx.if_exists() != null) {
                ConditionalDdlOutcome.dropSkipped();
                return null;
            }
            throw missing(kind, schema, name);
        }
        // Dropping an object needs OWNERSHIP of it, with or without IF EXISTS.
        queryExecutor.requireOwnership(kind.name(), nameParts(ctx, kind, name), null);
        if (kind == SecurityObjectKind.PASSWORD_POLICY && isAttached(PASSWORD_POLICY_PREFIX + "USER|",
                display(kind, schema, name))) {
            throw policyInUse(name);
        }
        if (kind == SecurityObjectKind.NETWORK_POLICY) {
            for (final Map.Entry<String, String> attachment : catalog.getSecurityObjects().attachments().entrySet()) {
                if (attachment.getKey().startsWith(NETWORK_POLICY_PREFIX + "USER|")
                        && attachment.getValue().equals(name)) {
                    throw new RuntimeException(SqlCompilationError.of("Cannot perform Drop operation on network policy "
                        + name + ". The policy is attached to USER with name "
                        + attachment.getKey().substring((NETWORK_POLICY_PREFIX + "USER|").length())
                        + ". Unset the network policy from USER and try the Drop operation again."));
                }
            }
        }
        store.remove(kind, name);
        forgetAttachments(kind, display(kind, schema, name), null);
        return null;
    }

    /** Moves (or, when {@code renamed} is null, removes) every attachment of a policy that is renamed or dropped. */
    private void forgetAttachments(final SecurityObjectKind kind, final String attached, final String renamed) {
        final String prefix = kind == SecurityObjectKind.PASSWORD_POLICY ? PASSWORD_POLICY_PREFIX
            : kind == SecurityObjectKind.NETWORK_POLICY ? NETWORK_POLICY_PREFIX : null;
        if (prefix == null) {
            return;
        }
        final SecurityObjectStore account = catalog.getSecurityObjects();
        for (final Map.Entry<String, String> attachment : account.attachments().entrySet()) {
            if (attachment.getKey().startsWith(prefix) && attachment.getValue().equals(attached)) {
                if (renamed == null) {
                    account.detach(attachment.getKey());
                } else {
                    account.attach(attachment.getKey(), renamed);
                }
            }
        }
    }

    private Object alter(final FrostlakeParser.SecurityObjectStatementContext ctx, final SecurityObjectKind kind) {
        final Schema schema = kind.isSchemaObject() ? owningSchema(ctx.qualifiedName(0)) : null;
        final String name = kind.isSchemaObject() ? lastPart(ctx.qualifiedName(0))
            : SqlIdentifiers.canonical(ctx.identifier(0));
        final SecurityObjectStore store = store(kind, schema);
        final SecurityObject target = store.get(kind, name);
        if (target == null) {
            if (ctx.if_exists() != null) {
                return null;
            }
            throw missing(kind, schema, name);
        }
        if (ctx.securityPropertyChange() != null) {
            change(target, ctx.securityPropertyChange());
        } else if (ctx.RENAME() != null) {
            rename(ctx, target, schema, store);
        } else if (ctx.ADD() != null || ctx.REMOVE() != null) {
            addOrRemoveRule(ctx, target);
        } else if (ctx.tagSet() != null) {
            for (final FrostlakeParser.TagAssignContext assign : ctx.tagSet().tagAssign()) {
                final Tag tag = catalog.getTag(ParseTreeText.getQualifiedName(assign.qualifiedName()));
                final String value = TagValues.text(assign.qualifiedName(), assign.tagValue(), queryExecutor);
                TagValues.requireAllowed(tag, value);
                target.setTag(tag.getName(), value);
            }
        } else if (ctx.tagUnset() != null) {
            for (final FrostlakeParser.QualifiedNameContext tagName : ctx.tagUnset().qualifiedName()) {
                target.unsetTag(catalog.getTag(ParseTreeText.getQualifiedName(tagName)).getName());
            }
        }
        return null;
    }

    private void change(final SecurityObject target, final FrostlakeParser.SecurityPropertyChangeContext change) {
        final SecurityObjectKind kind = target.getKind();
        final List<String> alterable = kind == SecurityObjectKind.SECRET
            ? SecurityPropertySpecs.secretAlterableProperties(target.text("TYPE")) : null;
        if (change.SET() != null) {
            final Map<String, Object> properties = read(kind, change.securityProperty());
            for (final String key : properties.keySet()) {
                final SecurityPropertySpec spec = SecurityPropertySpecs.find(kind, key);
                if (alterable != null && !alterable.contains(key)) {
                    throw new RuntimeException(wrongTypeProperty(key, target.text("TYPE")));
                }
                if (!spec.isAlterable()) {
                    throw new RuntimeException(SqlCompilationError.invalidPropertyFor(key, kind.name()));
                }
            }
            if (kind == SecurityObjectKind.NETWORK_POLICY) {
                requireIngressRules(properties, target.getName());
            }
            if (kind == SecurityObjectKind.NETWORK_RULE && "HOST_PORT".equals(target.text("TYPE"))
                    && properties.get("VALUE_LIST") != null) {
                requireResolvableHosts(asStrings(properties.get("VALUE_LIST")));
            }
            apply(target, properties);
            return;
        }
        if (kind == SecurityObjectKind.SECRET) {
            throw new RuntimeException("Unsupported feature 'SECRET'.");
        }
        final List<String> keys = new ArrayList<>();
        for (final FrostlakeParser.OptionKeyContext option : change.optionKey()) {
            final String key = option.getText().toUpperCase(Locale.ROOT);
            final SecurityPropertySpec spec = SecurityPropertySpecs.find(kind, key);
            if (spec == null || !spec.isUnsettable()) {
                throw new RuntimeException(SqlCompilationError.invalidPropertyFor(key, kind.name()));
            }
            keys.add(key);
        }
        for (final String key : keys) {
            target.setProperty(key, "VALUE_LIST".equals(key) ? new ArrayList<String>() : null);
            if ("COMMENT".equals(key)) {
                target.setComment(null);
            }
        }
    }

    private void rename(final FrostlakeParser.SecurityObjectStatementContext ctx, final SecurityObject target,
                        final Schema schema, final SecurityObjectStore store) {
        final SecurityObjectKind kind = target.getKind();
        if (kind == SecurityObjectKind.NETWORK_RULE) {
            throw new RuntimeException("Unsupported feature 'renaming NETWORK_RULE'.");
        }
        final String before = display(kind, schema, target.getName());
        if (kind == SecurityObjectKind.NETWORK_POLICY) {
            final String renamed = SqlIdentifiers.canonical(ctx.identifier(1));
            if (store.get(kind, renamed) != null) {
                throw new RuntimeException(SqlCompilationError.of("Object '" + renamed + "' already exists."));
            }
            store.remove(kind, target.getName());
            target.rename(renamed);
            store.add(target);
            forgetAttachments(kind, before, renamed);
            return;
        }
        final Schema into = owningSchema(ctx.qualifiedName(1));
        final String renamed = lastPart(ctx.qualifiedName(1));
        if (into.getSecurityObjects().get(kind, renamed) != null) {
            throw new RuntimeException(SqlCompilationError.of("Object '" + renamed + "' already exists."));
        }
        store.remove(kind, target.getName());
        target.rename(renamed);
        into.getSecurityObjects().add(target);
        forgetAttachments(kind, before, into.qualifiedName(renamed));
    }

    /** ALTER NETWORK POLICY … ADD | REMOVE {ALLOWED | BLOCKED}_NETWORK_RULE_LIST = 'rule'. */
    private void addOrRemoveRule(final FrostlakeParser.SecurityObjectStatementContext ctx, final SecurityObject target) {
        final String key = ctx.optionKey().getText().toUpperCase(Locale.ROOT);
        if (!"ALLOWED_NETWORK_RULE_LIST".equals(key) && !"BLOCKED_NETWORK_RULE_LIST".equals(key)) {
            throw new RuntimeException(SqlCompilationError.invalidPropertyFor(key, target.getKind().name()));
        }
        final FrostlakeParser.SecurityPropertyValueContext value = ctx.securityPropertyValue();
        if (value.LPAREN() == null) {
            throw new RuntimeException(SqlCompilationError.of("invalid type of property '" + value.getText() + "' for '"
                + key + "'"));
        }
        final List<String> rules = new ArrayList<>(target.list(key));
        for (final TerminalNode item : value.STRING_LITERAL()) {
            final String rule = ruleReference(SqlStringLiterals.decode(item.getText()));
            if (ctx.ADD() != null) {
                requireIngressRule(rule, target.getName());
                if (!rules.contains(rule)) {
                    rules.add(rule);
                }
            } else {
                rules.remove(rule);
            }
        }
        target.setProperty(key, rules);
    }

    /**
     * {@code ALTER ACCOUNT | USER … SET | UNSET PASSWORD POLICY} and {@code … NETWORK_POLICY}: the attachment is
     * recorded, where {@code SHOW PASSWORD POLICIES ON} reads it back, and never enforced.
     */
    private Object attach(final FrostlakeParser.SecurityObjectStatementContext ctx) {
        final String target;
        if (ctx.USER() != null) {
            final String user = SqlIdentifiers.canonical(ctx.identifier(0));
            if (!catalog.hasUser(user)) {
                if (ctx.if_exists() != null) {
                    return null;
                }
                throw new RuntimeException(SqlCompilationError.doesNotExist("User", user));
            }
            target = "USER|" + catalog.getUser(user).getName();
        } else {
            target = ACCOUNT;
        }
        final SecurityObjectStore account = catalog.getSecurityObjects();
        if (ctx.PASSWORD() != null) {
            if (ctx.UNSET() != null) {
                account.detach(PASSWORD_POLICY_PREFIX + target);
                return null;
            }
            final Schema schema = owningSchema(ctx.qualifiedName(0));
            final String policy = lastPart(ctx.qualifiedName(0));
            if (schema.getSecurityObjects().get(SecurityObjectKind.PASSWORD_POLICY, policy) == null) {
                throw missing(SecurityObjectKind.PASSWORD_POLICY, schema, policy);
            }
            if (ctx.USER() != null && ctx.FORCE() == null
                    && account.attachment(PASSWORD_POLICY_PREFIX + target) != null) {
                throw new RuntimeException("Object " + target.substring("USER|".length())
                    + " already has a PASSWORD_POLICY. Only one PASSWORD_POLICY is allowed at a time.");
            }
            account.attach(PASSWORD_POLICY_PREFIX + target, schema.qualifiedName(policy));
            return null;
        }
        if (ctx.UNSET() != null) {
            account.detach(NETWORK_POLICY_PREFIX + target);
            return null;
        }
        final FrostlakeParser.IdentifierContext written = ctx.identifier(ctx.USER() != null ? 1 : 0);
        final String policy = written != null ? SqlIdentifiers.canonical(written)
            : SqlIdentifiers.canonicalText(SqlStringLiterals.decode(ctx.STRING_LITERAL().getText()));
        if (account.get(SecurityObjectKind.NETWORK_POLICY, policy) == null) {
            throw new RuntimeException("Network policy " + policy + " does not exist or not authorized.");
        }
        account.attach(NETWORK_POLICY_PREFIX + target, policy);
        return null;
    }

    // ---- SHOW / DESCRIBE ---------------------------------------------------------------------------------------

    /**
     * Runs one SHOW or DESCRIBE of the four kinds.
     *
     * @param ctx the statement
     * @return the listing or the description
     */
    public ResultSet list(final FrostlakeParser.SecurityObjectListingContext ctx) {
        final SecurityObjectKind kind = kindOf(ctx.RULE() != null || ctx.RULES() != null, ctx.NETWORK() != null,
            ctx.SECRET() != null || ctx.SECRETS() != null);
        if (ctx.DESCRIBE() != null || ctx.DESC() != null) {
            return describe(ctx, kind);
        }
        if (kind == SecurityObjectKind.NETWORK_POLICY) {
            return filter(showNetworkPolicies(), ctx.LIKE() == null ? null
                : SqlStringLiterals.decode(ctx.STRING_LITERAL(0).getText()));
        }
        if (kind == SecurityObjectKind.PASSWORD_POLICY && ctx.ON() != null) {
            return showPasswordPoliciesOn(ctx);
        }
        final List<Row> rows = new ArrayList<>();
        for (final Schema schema : scope(ctx.securityListingScope())) {
            for (final SecurityObject object : schema.getSecurityObjects().list(kind)) {
                rows.add(showRow(kind, schema, object, null));
            }
        }
        ResultSet listing = new ResultSet(showColumns(kind, false), rows);
        final TerminalNode like = ctx.LIKE() != null ? ctx.STRING_LITERAL(0) : null;
        listing = sorted(filter(listing, like == null ? null : SqlStringLiterals.decode(like.getText())));
        if (ctx.STARTS() != null) {
            final String prefix = SqlStringLiterals.decode(ctx.STRING_LITERAL(like == null ? 0 : 1).getText());
            listing = startingWith(listing, prefix);
        }
        if (ctx.LIMIT() != null) {
            final int limit = Integer.parseInt(ctx.INTEGER_LITERAL().getText());
            if (limit <= 0) {
                throw new RuntimeException("page size \"" + limit + "\" must be greater than 0 in limit clause");
            }
            final String from = ctx.FROM() == null ? null
                : SqlStringLiterals.decode(ctx.STRING_LITERAL(ctx.STRING_LITERAL().size() - 1).getText());
            listing = page(listing, limit, from);
        }
        return listing;
    }

    /**
     * The schemas a listing's scope covers: every schema of the account, of a database, or one schema. With no
     * scope the current database is listed, or the account when there is none; a bare DATABASE or SCHEMA with no
     * current one has no effect.
     */
    private List<Schema> scope(final FrostlakeParser.SecurityListingScopeContext scope) {
        final String currentDatabase = catalog.getCurrentDatabase();
        if (scope == null || scope.ACCOUNT() != null) {
            return scope == null && currentDatabase != null ? schemasOf(catalog.databaseExact(currentDatabase))
                : allSchemas();
        }
        final String[] parts = scope.qualifiedName() == null ? new String[0]
            : ParseTreeText.qualifiedNameParts(scope.qualifiedName());
        if (scope.DATABASE() != null) {
            if (parts.length == 0) {
                return currentDatabase == null ? allSchemas() : schemasOf(catalog.databaseExact(currentDatabase));
            }
            return schemasOf(catalog.databaseExact(parts[parts.length - 1]));
        }
        if (scope.SCHEMA() == null && parts.length == 1
                && (currentDatabase == null || !catalog.databaseExact(currentDatabase).hasSchemaExact(parts[0]))) {
            return schemasOf(catalog.databaseExact(parts[0]));
        }
        if (parts.length == 0) {
            if (currentDatabase == null || catalog.getCurrentSchema() == null) {
                return allSchemas();
            }
            return Collections.singletonList(catalog.databaseExact(currentDatabase)
                .schemaExact(catalog.getCurrentSchema()));
        }
        return Collections.singletonList(catalog.resolveSchema(QualifiedName.of(parts)));
    }

    private List<Schema> allSchemas() {
        final List<Schema> schemas = new ArrayList<>();
        for (final Database database : catalog.getAllDatabases()) {
            schemas.addAll(schemasOf(database));
        }
        return schemas;
    }

    private static List<Schema> schemasOf(final Database database) {
        return database.getAllSchemas();
    }

    private static List<ResultSetColumn> columns(final String... names) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        for (final String name : names) {
            if ("created_on".equals(name)) {
                columns.add(new ResultSetColumn(name, ShowResultHelpers.CREATED_ON));
            } else if (name.endsWith("_expiry_time")) {
                columns.add(new ResultSetColumn(name, ShowResultHelpers.CREATED_ON));
            } else if (name.startsWith("entries_in_") || "key_length".equals(name)) {
                columns.add(new ResultSetColumn(name, NumericType.NUMBER));
            } else {
                columns.add(new ResultSetColumn(name, StringType.VARCHAR));
            }
        }
        return columns;
    }

    private static List<ResultSetColumn> showColumns(final SecurityObjectKind kind, final boolean setOn) {
        if (kind == SecurityObjectKind.NETWORK_RULE) {
            return columns("created_on", "name", "database_name", "schema_name", "owner", "comment", "type", "mode",
                "entries_in_valuelist", "owner_role_type");
        }
        if (kind == SecurityObjectKind.SECRET) {
            return columns("created_on", "name", "schema_name", "database_name", "owner", "comment", "secret_type",
                "oauth_scopes", "owner_role_type");
        }
        if (setOn) {
            return columns("created_on", "name", "database_name", "schema_name", "kind", "owner", "comment",
                "owner_role_type", "options", "set_on");
        }
        return columns("created_on", "name", "database_name", "schema_name", "kind", "owner", "comment",
            "owner_role_type", "options");
    }

    private static Row showRow(final SecurityObjectKind kind, final Schema schema, final SecurityObject object,
                               final String setOn) {
        final List<Object> cells = new ArrayList<>();
        cells.add(ShowResultHelpers.createdOn(object.getCreatedTime()));
        cells.add(object.getName());
        if (kind == SecurityObjectKind.SECRET) {
            cells.add(schema.getName());
            cells.add(schema.getDatabaseName());
        } else {
            cells.add(schema.getDatabaseName());
            cells.add(schema.getName());
        }
        if (kind == SecurityObjectKind.PASSWORD_POLICY) {
            cells.add("PASSWORD_POLICY");
        }
        cells.add(object.getOwner());
        cells.add(object.getComment() == null && kind != SecurityObjectKind.SECRET ? "" : object.getComment());
        if (kind == SecurityObjectKind.NETWORK_RULE) {
            cells.add(object.text("TYPE"));
            cells.add(object.text("MODE"));
            cells.add(Long.valueOf(object.list("VALUE_LIST").size()));
        } else if (kind == SecurityObjectKind.SECRET) {
            cells.add(object.text("TYPE"));
            cells.add(object.getProperty("OAUTH_SCOPES") == null ? null : scopes(object));
        }
        cells.add(ShowResultHelpers.ownerRoleType(object.getOwner()));
        if (kind == SecurityObjectKind.PASSWORD_POLICY) {
            cells.add("");
            if (setOn != null) {
                cells.add(setOn);
            }
        }
        return new Row(cells);
    }

    /** An OAuth refresh token's expiry, written as a date or a timestamp, as the timestamp DESCRIBE shows. */
    private static Object expiry(final String written) {
        if (written == null) {
            return null;
        }
        try {
            return ShowResultHelpers.createdOn(written.length() <= 10 ? LocalDate.parse(written).atStartOfDay()
                : LocalDateTime.parse(written.trim().replace(' ', 'T')));
        } catch (final DateTimeParseException unreadable) {
            return null;
        }
    }

    private static String scopes(final SecurityObject secret) {
        return "[" + String.join(", ", secret.list("OAUTH_SCOPES")) + "]";
    }

    private static ResultSet filter(final ResultSet listing, final String pattern) {
        if (pattern == null) {
            return listing;
        }
        final int name = nameIndex(listing);
        final List<Row> kept = new ArrayList<>();
        for (final Row row : listing.getRows()) {
            final Object value = row.getValue(name);
            if (value != null && ShowResultHelpers.matchesLike(value.toString(), pattern)) {
                kept.add(row);
            }
        }
        return new ResultSet(listing.getColumns(), kept);
    }

    private static int nameIndex(final ResultSet listing) {
        for (int i = 0; i < listing.getColumns().size(); i++) {
            if ("name".equals(listing.getColumns().get(i).getName())) {
                return i;
            }
        }
        return 1;
    }

    private static int columnIndex(final ResultSet listing, final String column) {
        for (int i = 0; i < listing.getColumns().size(); i++) {
            if (column.equals(listing.getColumns().get(i).getName())) {
                return i;
            }
        }
        return -1;
    }

    /** Ordered as live orders a listing: database, schema, then name. */
    private static ResultSet sorted(final ResultSet listing) {
        final List<Row> rows = new ArrayList<>(listing.getRows());
        Collections.sort(rows, new ShowNameComparator(columnIndex(listing, "database_name"),
            columnIndex(listing, "schema_name"), nameIndex(listing)));
        return new ResultSet(listing.getColumns(), rows);
    }

    private static ResultSet startingWith(final ResultSet listing, final String prefix) {
        final int name = nameIndex(listing);
        final List<Row> kept = new ArrayList<>();
        for (final Row row : listing.getRows()) {
            final Object value = row.getValue(name);
            if (value != null && value.toString().startsWith(prefix)) {
                kept.add(row);
            }
        }
        return new ResultSet(listing.getColumns(), kept);
    }

    private static ResultSet page(final ResultSet listing, final int limit, final String from) {
        final int name = nameIndex(listing);
        final List<Row> kept = new ArrayList<>();
        for (final Row row : listing.getRows()) {
            final Object value = row.getValue(name);
            if (from != null && (value == null || value.toString().compareTo(from) <= 0)) {
                continue;
            }
            kept.add(row);
            if (kept.size() >= limit) {
                break;
            }
        }
        return new ResultSet(listing.getColumns(), kept);
    }

    /** SHOW NETWORK POLICIES: every network policy of the account, by name. */
    private ResultSet showNetworkPolicies() {
        final List<Row> rows = new ArrayList<>();
        for (final SecurityObject policy : catalog.getSecurityObjects().list(SecurityObjectKind.NETWORK_POLICY)) {
            rows.add(new Row(Arrays.<Object>asList(
                ShowResultHelpers.createdOn(policy.getCreatedTime()),
                policy.getName(),
                policy.getComment() == null ? "" : policy.getComment(),
                Long.valueOf(policy.list("ALLOWED_IP_LIST").size()),
                Long.valueOf(policy.list("BLOCKED_IP_LIST").size()),
                Long.valueOf(policy.list("ALLOWED_NETWORK_RULE_LIST").size()),
                Long.valueOf(policy.list("BLOCKED_NETWORK_RULE_LIST").size()))));
        }
        final ResultSet listing = new ResultSet(columns("created_on", "name", "comment", "entries_in_allowed_ip_list",
            "entries_in_blocked_ip_list", "entries_in_allowed_network_rules", "entries_in_blocked_network_rules"),
            rows);
        final List<Row> ordered = new ArrayList<>(listing.getRows());
        Collections.sort(ordered, new ShowNameComparator(1));
        return new ResultSet(listing.getColumns(), ordered);
    }

    /** SHOW PASSWORD POLICIES ON ACCOUNT | ON USER u: the policy attached there, if any. */
    private ResultSet showPasswordPoliciesOn(final FrostlakeParser.SecurityObjectListingContext ctx) {
        final String target;
        final String setOn;
        if (ctx.USER() != null) {
            final String user = SqlIdentifiers.canonical(ctx.identifier());
            target = "USER|" + catalog.getUser(user).getName();
            setOn = "USER";
        } else {
            target = ACCOUNT;
            setOn = ACCOUNT;
        }
        final List<Row> rows = new ArrayList<>();
        String attached = catalog.getSecurityObjects().attachment(PASSWORD_POLICY_PREFIX + target);
        String level = setOn;
        if (attached == null && !ACCOUNT.equals(target)) {
            attached = catalog.getSecurityObjects().attachment(PASSWORD_POLICY_PREFIX + ACCOUNT);
            level = ACCOUNT;
        }
        if (attached != null) {
            final QualifiedName name = QualifiedName.of(SqlIdentifiers.canonicalTextParts(attached));
            final Schema schema = catalog.databaseExact(name.part(0)).schemaExact(name.part(1));
            final SecurityObject policy = schema.getSecurityObjects().get(SecurityObjectKind.PASSWORD_POLICY,
                name.last());
            if (policy != null) {
                rows.add(showRow(SecurityObjectKind.PASSWORD_POLICY, schema, policy, level));
            }
        }
        if (rows.isEmpty()) {
            // With no policy of its own, a user or the account is governed by the built-in one.
            rows.add(new Row(Arrays.<Object>asList(null, "BUILT-IN", null, null, "PASSWORD_POLICY", null, "", null, "",
                "SYSTEM")));
        }
        return new ResultSet(showColumns(SecurityObjectKind.PASSWORD_POLICY, true), rows);
    }

    private ResultSet describe(final FrostlakeParser.SecurityObjectListingContext ctx, final SecurityObjectKind kind) {
        if (kind == SecurityObjectKind.NETWORK_POLICY) {
            final String name = SqlIdentifiers.canonical(ctx.identifier());
            final SecurityObject policy = catalog.getSecurityObjects().get(kind, name);
            if (policy == null) {
                throw missing(kind, null, name);
            }
            final List<Row> rows = new ArrayList<>();
            for (final SecurityPropertySpec spec : SecurityPropertySpecs.of(kind)) {
                final List<String> items = policy.list(spec.getName());
                if ("COMMENT".equals(spec.getName()) || items.isEmpty()) {
                    continue;
                }
                if (spec.getName().endsWith("_NETWORK_RULE_LIST")) {
                    // A rule list is described as JSON, each rule by its fully qualified name.
                    final StringBuilder json = new StringBuilder("[");
                    for (final String rule : items) {
                        if (json.length() > 1) {
                            json.append(',');
                        }
                        json.append("{\"fullyQualifiedRuleName\":\"").append(rule.replace("\"", "\\\""))
                            .append("\"}");
                    }
                    rows.add(new Row(Arrays.<Object>asList(spec.getName(), json.append(']').toString())));
                } else {
                    rows.add(new Row(Arrays.<Object>asList(spec.getName(), String.join(",", items))));
                }
            }
            return new ResultSet(columns("name", "value"), rows);
        }
        final Schema schema = owningSchema(ctx.qualifiedName());
        final String name = lastPart(ctx.qualifiedName());
        final SecurityObject object = schema.getSecurityObjects().get(kind, name);
        if (object == null) {
            // Describing a rule asks for any of a list of account privileges, where the rest ask for MONITOR.
            throw kind == SecurityObjectKind.NETWORK_RULE
                ? new RuntimeException(SqlCompilationError.networkRuleToDescribeDoesNotExist(
                    schema.qualifiedName(name)))
                : missing(kind, schema, name);
        }
        final String comment = object.getComment();
        if (kind == SecurityObjectKind.PASSWORD_POLICY) {
            final List<Row> rows = new ArrayList<>();
            rows.add(new Row(Arrays.<Object>asList("NAME", object.getName(), null, "Name of password policy.")));
            rows.add(new Row(Arrays.<Object>asList("OWNER", object.getOwner(), null, "Owner of password policy.")));
            for (final SecurityPropertySpec spec : SecurityPropertySpecs.of(kind)) {
                final String value = object.text(spec.getName());
                rows.add(new Row(Arrays.<Object>asList(spec.getName(),
                    value != null ? value : spec.getDefaultValue(), spec.getDefaultValue(), spec.getDescription())));
            }
            // COMMENT is listed third, after the name and the owner.
            rows.add(2, rows.remove(rows.size() - 1));
            return new ResultSet(columns("property", "value", "default", "description"), rows);
        }
        if (kind == SecurityObjectKind.NETWORK_RULE) {
            return new ResultSet(columns("created_on", "name", "database_name", "schema_name", "owner", "comment",
                "type", "mode", "value_list"), Collections.singletonList(new Row(Arrays.<Object>asList(
                ShowResultHelpers.createdOn(object.getCreatedTime()), object.getName(), schema.getDatabaseName(),
                schema.getName(), object.getOwner(), comment == null ? "" : comment, object.text("TYPE"), object.text("MODE"),
                object.text("VALUE_LIST")))));
        }
        final String type = object.text("TYPE");
        return new ResultSet(columns("created_on", "name", "schema_name", "database_name", "owner", "comment",
            "secret_type", "username", "oauth_access_token_expiry_time", "oauth_refresh_token_expiry_time",
            "oauth_scopes", "integration_name", "algorithm", "key_length", "workload_identity_federation_issuer",
            "workload_identity_federation_subject"), Collections.singletonList(new Row(Arrays.<Object>asList(
            ShowResultHelpers.createdOn(object.getCreatedTime()), object.getName(), schema.getName(),
            schema.getDatabaseName(), object.getOwner(), comment, type, object.text("USERNAME"), null,
            expiry(object.text("OAUTH_REFRESH_TOKEN_EXPIRY_TIME")),
            object.getProperty("OAUTH_SCOPES") == null ? null : scopes(object),
            object.text("API_AUTHENTICATION"), object.text("ALGORITHM"),
            "SYMMETRIC_KEY".equals(type) ? Long.valueOf(256L) : null, null, null))));
    }
}
