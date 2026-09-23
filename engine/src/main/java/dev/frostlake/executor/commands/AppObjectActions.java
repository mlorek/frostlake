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
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.AppObject;
import dev.frostlake.metastore.model.AppObjectKind;
import dev.frostlake.metastore.model.AppObjectVersion;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObject;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What notebooks and Streamlit apps do beyond their properties: the version actions (ADD LIVE VERSION, ADD VERSION,
 * COMMIT, ABORT), the Git actions (PUSH, PULL), the SECRETS property, and the checks every property list gets.
 *
 * <p>Frostlake keeps no Git repositories and no external access integrations, so the Git actions refuse the way the
 * account refuses them for an app whose versions come from no repository — each with its own sentence, in the
 * account's order — and SECRETS, which only an integration that allows the secret makes usable, is refused once the
 * list itself reads well. An empty list is accepted by ALTER and changes nothing.
 */
final class AppObjectActions {

    private static final String LIVE_EXISTS = "There is already a live version. Please commit it first.";
    private static final String LIVE_MISSING = "Live version is not found.";
    /** What every version and Git action answers for a legacy ROOT_LOCATION app, which has no versions. */
    private static final String NO_VERSIONS = "Attached stage not exists.";
    private static final String LINEAGE = "Version contains invalid source lineage information: ";
    private static final String BOTH_CREDENTIALS =
        "Invalid property list: GIT_CREDENTIALS and USERNAME&PASSWORD pair cannot both be present.";
    private static final String THREE_SETTINGS = "Invalid property list: The following three settings should all be "
        + "specified, or none of them should be specified: 1. GIT_CREDENTIALS or USERNAME&PASSWORD pair 2.NAME "
        + "3.EMAIL.";
    private static final String PASSWORD_SECRET =
        "Invalid property list: GIT_CREDENTIALS must be a snowflake secret of PASSWORD type.";
    /** The account's sentence, its misspelling included. */
    private static final String NOT_A_REPOSITORY =
        "Invalid git branch path: The path speicifed does not point to a git repo.";
    private static final String GIT_SCHEME = "snow://";
    /** Why an alias is refused, after the alias itself; the account's sentence, its spelling included. */
    private static final String ALIAS_RULE = "Version alias must be a snowflake identifier. It cant start with "
        + "\"version$\", contain slashes, or be FIRST, LAST, LIVE or DEFAULT.";
    /** The start of a committed version's own name, which no alias may take. */
    private static final String VERSION_NAME_PREFIX = "VERSION$";
    /** The names the account gives versions itself, which no alias may take either. */
    private static final Set<String> VERSION_NAMES = Set.of("FIRST", "LAST", "LIVE", "DEFAULT");

    /** The parameters each action takes. */
    private static final Set<String> COMMENT_ONLY = Set.of("COMMENT");
    private static final Set<String> PUSH_PARAMETERS =
        Set.of("GIT_CREDENTIALS", "USERNAME", "PASSWORD", "NAME", "EMAIL", "COMMENT");

    private final Catalog catalog;
    private final SQLCommandVisitor visitor;

    /**
     * @param catalog the catalog holding the stages and secrets the actions name
     * @param visitor the visitor, for decoding string literals
     */
    AppObjectActions(final Catalog catalog, final SQLCommandVisitor visitor) {
        this.catalog = catalog;
        this.visitor = visitor;
    }

    // ---------------------------------------------------------------------------------------------- property lists

    /** Refuses a property list that names one property twice: {@code duplicate property 'COMMENT';}. */
    static void refuseDuplicates(final List<? extends ParserRuleContext> properties) {
        final List<String> names = new ArrayList<>();
        for (final ParserRuleContext property : properties) {
            final String name = property.getStart().getText().toUpperCase(Locale.ROOT);
            if (names.contains(name)) {
                throw new RuntimeException(SqlCompilationError.of("duplicate property '" + name + "';"));
            }
            names.add(name);
        }
    }

    /**
     * SECRETS = (…): the checks the account makes, in its order. The first variable must be quoted and every secret
     * named by identifier; a CREATE then refuses the list whatever it holds, as secrets need an external access
     * integration that allows them. ALTER goes on: a variable named twice, a secret that does not exist, and last
     * the integration no secret can be allowed by. An empty list passes ALTER.
     *
     * @param secrets the list
     * @param create whether a CREATE carries it
     */
    void checkSecrets(final FrostlakeParser.AppSecretListContext secrets, final boolean create) {
        final FrostlakeParser.AppSecretContext first = secrets.appSecret();
        if (first == null) {
            if (create) {
                throw usingSecrets();
            }
            return;
        }
        if (first.identifier() != null) {
            throw new RuntimeException(SqlCompilationError.inline("Secret names must be specified using single quotes."));
        }
        final List<String> keys = new ArrayList<>();
        final List<FrostlakeParser.AppSecretValueContext> values = new ArrayList<>();
        keys.add(visitor.extractStringLiteral(first.STRING_LITERAL()));
        values.add(first.appSecretValue());
        for (final FrostlakeParser.AppSecretEntryContext entry : secrets.appSecretEntry()) {
            keys.add(visitor.extractStringLiteral(entry.STRING_LITERAL()));
            values.add(entry.appSecretValue());
        }
        for (final FrostlakeParser.AppSecretValueContext value : values) {
            if (value.qualifiedName() == null) {
                throw new RuntimeException(SqlCompilationError.inline(
                    "Secret values must be specified using object identifier."));
            }
        }
        if (create) {
            throw usingSecrets();
        }
        for (int i = 0; i < keys.size(); i++) {
            if (keys.subList(0, i).contains(keys.get(i))) {
                throw new RuntimeException(SqlCompilationError.inline("Duplicate secret name '" + keys.get(i)
                    + "' passed in."));
            }
        }
        String firstSecret = null;
        for (final FrostlakeParser.AppSecretValueContext value : values) {
            final String[] parts = ParseTreeText.qualifiedNameParts(value.qualifiedName());
            final Schema schema = schemaOrNull(parts);
            final String name = parts[parts.length - 1];
            if (schema == null || schema.getSecurityObjects().get(SecurityObjectKind.SECRET, name) == null) {
                throw new RuntimeException(SqlCompilationError.of("Secret '" + SqlIdentifiers.spellCanonical(name)
                    + "' does not exist or operation not authorized."));
            }
            if (firstSecret == null) {
                firstSecret = schema.qualifiedName(name);
            }
        }
        throw new RuntimeException(SqlCompilationError.inline("Integrations do not allow secret '"
            + SqlIdentifiers.spellAlreadyCanonicalPath(firstSecret) + "'."));
    }

    private static RuntimeException usingSecrets() {
        return new RuntimeException(SqlCompilationError.inline(
            "Using secrets requires a valid External Access Integration with allowed secrets."));
    }

    /** The schema a name resolves in, or null when its database or schema does not exist. */
    private Schema schemaOrNull(final String[] parts) {
        try {
            return catalog.requireOwningSchema(QualifiedName.of(parts));
        } catch (final RuntimeException missing) {
            return null;
        }
    }

    // ------------------------------------------------------------------------------------- version and Git actions

    /**
     * What the account checks as it compiles a version or Git action, before it looks the notebook or app up: the
     * alias, then the location an ADD VERSION copies from or the branch a PUSH names, then each parameter in the
     * order written, its name before its value.
     *
     * @param action the action
     */
    void check(final FrostlakeParser.AppVersionActionContext action) {
        alias(action);
        if (action.VERSION() != null && action.LIVE() == null) {
            location(action);
        } else if (action.PUSH() != null && action.STRING_LITERAL() != null) {
            requireUrlPrefix(visitor.extractStringLiteral(action.STRING_LITERAL()));
        }
        parameters(action, action.PUSH() != null ? PUSH_PARAMETERS : COMMENT_ONLY);
    }

    /**
     * ALTER NOTEBOOK | STREAMLIT … ADD LIVE VERSION, ADD VERSION, COMMIT, ABORT, PUSH or PULL, once {@link #check}
     * has passed it.
     *
     * @param object the notebook or app
     * @param action the action
     * @return the status answer, or null for the flat sentence
     */
    Object apply(final AppObject object, final FrostlakeParser.AppVersionActionContext action) {
        if (action.PUSH() != null) {
            return push(object, action, parameters(action, PUSH_PARAMETERS));
        }
        final Map<String, FrostlakeParser.AppActionParameterContext> parameters =
            action.ABORT() != null ? new LinkedHashMap<String, FrostlakeParser.AppActionParameterContext>()
                : parameters(action, COMMENT_ONLY);
        final String location = action.VERSION() != null && action.LIVE() == null ? location(action) : null;
        requireVersions(object);
        if (action.LIVE() != null) {
            if (object.hasLiveVersion()) {
                throw new RuntimeException(LIVE_EXISTS);
            }
            final String alias = alias(action);
            if (alias != null && object.versionWithAlias(alias) != null) {
                throw new RuntimeException(aliasTaken(alias));
            }
            object.openLiveVersion(alias, text(parameters.get("COMMENT")), StatementClock.instant());
            return status(named("Live version", alias) + " successfully created.");
        }
        if (location != null) {
            return addVersion(object, action, location, text(parameters.get("COMMENT")));
        }
        if (action.COMMIT() != null) {
            if (!object.hasLiveVersion()) {
                throw new RuntimeException(LIVE_MISSING);
            }
            final String alias = object.getLiveVersion().getAlias();
            object.commitLiveVersion(text(parameters.get("COMMENT")), StatementClock.instant());
            return status(named("Live version", alias) + " successfully committed.");
        }
        if (action.ABORT() != null) {
            if (!object.hasLiveVersion()) {
                throw new RuntimeException(LIVE_MISSING);
            }
            final String alias = object.getLiveVersion().getAlias();
            object.setLiveVersion(false);
            return status(named("Live version", alias) + " successfully aborted.");
        }
        // PULL
        if (object.hasLiveVersion()) {
            throw new RuntimeException(LIVE_EXISTS);
        }
        throw lineage(object);
    }

    /**
     * ADD VERSION [IF NOT EXISTS] [alias] FROM '&lt;location&gt;': a committed version copied from a stage, which becomes
     * the last one. A taken alias is refused, or answered in the status under IF NOT EXISTS; a location on a stage
     * that does not exist, a user stage, or a {@code snow://} container is refused with the account's sentences.
     */
    private Object addVersion(final AppObject object, final FrostlakeParser.AppVersionActionContext action,
                              final String location, final String comment) {
        requireStage(location, action.stageRef());
        if (object.hasLiveVersion()) {
            throw new RuntimeException(LIVE_EXISTS);
        }
        final String alias = alias(action);
        if (alias != null && object.versionWithAlias(alias) != null) {
            if (action.if_not_exists() != null) {
                return status(aliasTaken(alias));
            }
            throw new RuntimeException(aliasTaken(alias));
        }
        object.addVersion(alias, comment, AppObject.sourceLocation(location), StatementClock.instant());
        return status(named("Version", alias) + " successfully created.");
    }

    /**
     * PUSH [TO '&lt;branch&gt;'] […]: the branch named, which is never a Git branch here and is refused before the
     * credentials are read; without one, the credentials' secret, then the credentials and author, and last the
     * version's own source, which is never a Git branch either.
     */
    private Object push(final AppObject object, final FrostlakeParser.AppVersionActionContext action,
                        final Map<String, FrostlakeParser.AppActionParameterContext> parameters) {
        if (action.STRING_LITERAL() != null) {
            final String branch = visitor.extractStringLiteral(action.STRING_LITERAL());
            requireUrlPrefix(branch);
            requireVersions(object);
            requireBranchObject(branch);
            requireStage(branch, null);
            throw new RuntimeException(NOT_A_REPOSITORY);
        }
        final boolean secret = parameters.containsKey("GIT_CREDENTIALS");
        final SecurityObject credentials = secret ? credentials(parameters.get("GIT_CREDENTIALS")) : null;
        requireVersions(object);
        final boolean username = parameters.containsKey("USERNAME");
        final boolean password = parameters.containsKey("PASSWORD");
        if (secret && (username || password)) {
            throw new RuntimeException(BOTH_CREDENTIALS);
        }
        final boolean named = parameters.containsKey("NAME");
        if (named != parameters.containsKey("EMAIL") || username != password || (secret || username) && !named) {
            throw new RuntimeException(THREE_SETTINGS);
        }
        if (credentials != null && !"PASSWORD".equalsIgnoreCase(credentials.text("TYPE"))) {
            throw new RuntimeException(PASSWORD_SECRET);
        }
        throw lineage(object);
    }

    /** The secret GIT_CREDENTIALS names, which must exist: {@code Secret '<db>.<schema>.<name>' does not exist…}. */
    private SecurityObject credentials(final FrostlakeParser.AppActionParameterContext parameter) {
        final FrostlakeParser.QualifiedNameContext written = parameter.appActionValue().qualifiedName();
        if (written == null) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(written);
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        final SecurityObject secret = schema.getSecurityObjects().get(SecurityObjectKind.SECRET, name);
        if (secret == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Secret", schema.qualifiedName(name)));
        }
        return secret;
    }

    /**
     * A branch named by an app's own {@code snow://streamlit/…} or {@code snow://notebook/…} location names that
     * app, which must exist ({@code Streamlit 'X' does not exist or not authorized.}), and has no stage of its own to
     * push to: {@code Streamlit ST2 does not have an embedded stage. Operation aborted.}, a notebook's included.
     */
    private void requireBranchObject(final String branch) {
        for (final AppObjectKind kind : AppObjectKind.values()) {
            final String prefix = GIT_SCHEME + kind.uriScheme() + "/";
            if (!branch.startsWith(prefix)) {
                continue;
            }
            final String rest = branch.substring(prefix.length());
            final int slash = rest.indexOf('/');
            final String[] parts = SqlIdentifiers.canonicalTextParts(slash >= 0 ? rest.substring(0, slash) : rest);
            final Schema schema = schemaOrNull(parts);
            final String name = parts[parts.length - 1];
            if (schema == null || schema.getAppObjects().get(kind, name) == null) {
                throw new RuntimeException(SqlCompilationError.doesNotExistWithoutHint(kind.displayName(),
                    QualifiedName.join(parts)));
            }
            throw new RuntimeException("Streamlit " + name + " does not have an embedded stage. Operation aborted.");
        }
    }

    /** A legacy ROOT_LOCATION app has no versions to act on. */
    private static void requireVersions(final AppObject object) {
        if (object.getRootLocation() != null) {
            throw new RuntimeException(NO_VERSIONS);
        }
    }

    /** Why a version cannot be pushed to or pulled from Git: it was copied from the template, or from a stage. */
    private static RuntimeException lineage(final AppObject object) {
        final AppObjectVersion last = object.lastVersion();
        return new RuntimeException(LINEAGE + (last == null || last.getSourceLocation() == null
            ? "Missing source domain id.." : "The version is not created from a git source.."));
    }

    /** ADD VERSION's location as written: the string's text, or the stage reference's. */
    private String location(final FrostlakeParser.AppVersionActionContext action) {
        if (action.stageRef() != null) {
            return action.stageRef().getText();
        }
        final String location = visitor.extractStringLiteral(action.STRING_LITERAL());
        requireUrlPrefix(location);
        return location;
    }

    /** A location must name a stage ({@code @…}) or a container ({@code snow://…}). */
    private static void requireUrlPrefix(final String location) {
        if (!location.startsWith("@") && !location.startsWith(GIT_SCHEME)) {
            throw new RuntimeException(SqlCompilationError.of("invalid URL prefix found in: '" + location + "'"));
        }
    }

    /**
     * The stage a location names must exist. A {@code snow://} container is no stage the account copies from, and a
     * user stage is refused by its kind.
     *
     * @param location the location as written
     * @param reference the location's stage reference when it was written as one, else null
     */
    private void requireStage(final String location, final FrostlakeParser.StageRefContext reference) {
        if (location.startsWith(GIT_SCHEME)) {
            final String rest = location.substring(GIT_SCHEME.length());
            final int slash = rest.indexOf('/');
            throw new RuntimeException("Unsupported feature 'stage container type: "
                + (slash >= 0 ? rest.substring(0, slash) : rest) + "'.");
        }
        final String[] parts;
        if (reference != null) {
            if (reference.TILDE() != null) {
                throw userStage();
            }
            final List<String> names = new ArrayList<>();
            for (final FrostlakeParser.IdentifierContext part : reference.identifier()) {
                names.add(SqlIdentifiers.canonical(part));
            }
            if (reference.PERCENT() != null) {
                // A table's stage: its name is the table's, behind the percent sign.
                names.set(names.size() - 1, "%" + names.get(names.size() - 1));
            }
            parts = names.toArray(new String[0]);
        } else {
            final String body = location.substring(1);
            final int slash = body.indexOf('/');
            final String stage = slash >= 0 ? body.substring(0, slash) : body;
            if (stage.startsWith("~")) {
                throw userStage();
            }
            parts = SqlIdentifiers.canonicalTextParts(stage);
        }
        final String name = parts[parts.length - 1];
        Schema schema;
        try {
            schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        } catch (final RuntimeException missing) {
            schema = null;
        }
        if (schema == null || !schema.hasStageExact(name)) {
            // The stage's own name, unquoted: '@"Mixed Case"/x' names Mixed Case, '@%"t"' names %t.
            throw new RuntimeException("The specified stage " + name + " does not exist or the current role does "
                + "not have access. Owner of the Streamlit must have at least READ on the specified stage.");
        }
    }

    private static RuntimeException userStage() {
        return new RuntimeException(SqlCompilationError.inline(
            "Copy files not support stage kind: 'USER' for stage '~'."));
    }

    /**
     * An action's parameters, by upper-case name, the last of a repeated one winning, each checked in the order
     * written: a name the action does not take is {@code invalid parameter '<NAME>'}, and a number or a boolean
     * where a string or a name belongs is {@code invalid value [<as written>] for parameter '<NAME>'}.
     */
    private static Map<String, FrostlakeParser.AppActionParameterContext> parameters(
            final FrostlakeParser.AppVersionActionContext action, final Set<String> allowed) {
        final Map<String, FrostlakeParser.AppActionParameterContext> parameters = new LinkedHashMap<>();
        for (final FrostlakeParser.AppActionParameterContext parameter : action.appActionParameter()) {
            final String name = parameter.PASSWORD() != null ? "PASSWORD"
                : SqlIdentifiers.canonical(parameter.identifier());
            if (!allowed.contains(name)) {
                throw new RuntimeException(SqlCompilationError.of("invalid parameter '" + name + "'"));
            }
            final FrostlakeParser.AppActionValueContext value = parameter.appActionValue();
            if (value.INTEGER_LITERAL() != null || value.FLOAT_LITERAL() != null || value.booleanValue() != null
                    || isBooleanWord(value.qualifiedName())) {
                throw new RuntimeException(SqlCompilationError.of("invalid value [" + value.getText()
                    + "] for parameter '" + name + "'"));
            }
            parameters.put(name, parameter);
        }
        return parameters;
    }

    /** Whether a name is TRUE or FALSE written alone, which reads as a boolean rather than a name. */
    private static boolean isBooleanWord(final FrostlakeParser.QualifiedNameContext name) {
        if (name == null || name.getStart() != name.getStop()) {
            return false;
        }
        final int type = name.getStart().getType();
        return type == FrostlakeLexer.TRUE || type == FrostlakeLexer.FALSE;
    }

    /**
     * A parameter's value: a string's text, quoted either way, a quoted name's own text, or a name as written —
     * {@code COMMENT = abc} is {@code abc} and {@code COMMENT = "Mixed Case"} is {@code Mixed Case}.
     */
    private String text(final FrostlakeParser.AppActionParameterContext parameter) {
        if (parameter == null) {
            return null;
        }
        final FrostlakeParser.AppActionValueContext value = parameter.appActionValue();
        if (value.STRING_LITERAL() != null) {
            return visitor.extractStringLiteral(value.STRING_LITERAL());
        }
        if (value.DOLLAR_QUOTED_STRING() != null) {
            final String token = value.DOLLAR_QUOTED_STRING().getText();
            return token.substring(2, token.length() - 2);
        }
        final Token start = value.qualifiedName().getStart();
        if (start == value.qualifiedName().getStop() && start.getType() == FrostlakeLexer.QUOTED_IDENTIFIER) {
            return start.getText().substring(1, start.getText().length() - 1).replace("\"\"", "\"");
        }
        return value.qualifiedName().getText();
    }

    /**
     * The alias a version action names, folded as any identifier is, or null. An alias the account keeps for its own
     * names is refused — one that starts with {@code version$}, holds a slash, or is FIRST, LAST, LIVE or DEFAULT,
     * whatever its case and quoted or not: {@code Invalid version alias VERSION$1. Version alias must be …}.
     */
    private static String alias(final FrostlakeParser.AppVersionActionContext action) {
        if (action.appVersionAlias() == null) {
            return null;
        }
        final String alias = SqlIdentifiers.canonical(action.appVersionAlias().identifier());
        final String upper = alias.toUpperCase(Locale.ROOT);
        if (upper.startsWith(VERSION_NAME_PREFIX) || alias.indexOf('/') >= 0 || VERSION_NAMES.contains(upper)) {
            throw new RuntimeException("Invalid version alias " + alias + ". " + ALIAS_RULE);
        }
        return alias;
    }

    /** A status sentence's subject: the version's words, then its alias as it is stored when it has one. */
    private static String named(final String words, final String alias) {
        return alias == null ? words : words + " " + alias;
    }

    private static String aliasTaken(final String alias) {
        return "There is already a version exists with alias " + alias + ".";
    }

    /** A one-row status answer carrying the statement's own sentence. */
    static ResultSet status(final String sentence) {
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.<Object>asList(sentence)));
        return new ResultSet(Arrays.asList(new ResultSetColumn("status", StringType.VARCHAR)), rows);
    }
}
