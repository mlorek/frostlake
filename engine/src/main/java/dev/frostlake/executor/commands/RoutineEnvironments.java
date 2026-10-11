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
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StagePathSegments;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.executor.udf.JavaFunctionCompiler;
import dev.frostlake.executor.udf.JavaRuntimeLevel;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.RoutineEnvironment;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * What a routine is given to run with, read from its options and judged as CREATE judges it: PACKAGES, the
 * ARTIFACT_REPOSITORY they come from, the TARGET_PATH a Java or Scala handler's classes are kept at, and the
 * EXTERNAL_ACCESS_INTEGRATIONS and SECRETS it may use. A repeated option is taken with the last one winning, and
 * an earlier one is never looked at (live-verified).
 */
final class RoutineEnvironments {

    private static final String SHARED_REPOSITORY = "SNOWFLAKE.SNOWPARK.PYPI_SHARED_REPOSITORY";
    private static final String JAR_SUFFIX = ".jar";

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    RoutineEnvironments(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    /**
     * Read a routine's environment from its options, refusing what the account refuses.
     *
     * @param options      the routine's options, in written order
     * @param language     the routine's language
     * @param procedure    whether the routine is a procedure
     * @param hasBody      whether the routine carries an inline body
     * @param replacedPath the target path of the overload this CREATE replaces, or null: a routine may be
     *                     replaced over its own file
     * @return the environment the routine keeps
     */
    RoutineEnvironment read(final List<FrostlakeParser.FunctionOptionContext> options, final UdfLanguage language,
                            final boolean procedure, final boolean hasBody, final String replacedPath) {
        FrostlakeParser.PackagesClauseContext packages = null;
        FrostlakeParser.RoutineArtifactRepositoryContext repository = null;
        FrostlakeParser.RoutineTargetPathContext targetPath = null;
        FrostlakeParser.RoutineIntegrationListContext integrations = null;
        FrostlakeParser.RoutineSecretListContext secrets = null;
        for (final FrostlakeParser.FunctionOptionContext option : options) {
            if (option.packagesClause() != null) {
                packages = option.packagesClause();
            } else if (option.routineArtifactRepository() != null) {
                repository = option.routineArtifactRepository();
            } else if (option.routineTargetPath() != null) {
                targetPath = option.routineTargetPath();
            } else if (option.routineIntegrationList() != null) {
                integrations = option.routineIntegrationList();
            } else if (option.routineSecretList() != null) {
                secrets = option.routineSecretList();
            }
        }
        final RoutineEnvironment environment = new RoutineEnvironment();
        if (packages != null && packages.stringLiteralList() != null) {
            final List<String> named = new ArrayList<>();
            for (final TerminalNode literal : packages.stringLiteralList().STRING_LITERAL()) {
                named.add(ParseTreeText.extractStringLiteral(literal));
            }
            environment.setPackages(named);
        }
        if (repository != null) {
            environment.setArtifactRepository(artifactRepository(repository, language));
        }
        if (targetPath != null) {
            environment.setTargetPath(targetPath(targetPath, language, hasBody, replacedPath));
        }
        if (integrations != null) {
            requireHandlerProperty("EXTERNAL_ACCESS_INTEGRATIONS", language, procedure);
            environment.setExternalAccessIntegrations(integrations(integrations));
        }
        if (secrets != null) {
            requireHandlerProperty("SECRETS", language, procedure);
            environment.setSecrets(secrets(secrets));
        }
        return environment;
    }

    /** The target path a routine's options name last, as written, or null when they name none. */
    static String writtenTargetPath(final List<FrostlakeParser.FunctionOptionContext> options) {
        String written = null;
        for (final FrostlakeParser.FunctionOptionContext option : options) {
            if (option.routineTargetPath() != null && option.routineTargetPath().STRING_LITERAL() != null) {
                written = ParseTreeText.extractStringLiteral(option.routineTargetPath().STRING_LITERAL());
            }
        }
        return written;
    }

    /** A SQL procedure takes neither integrations nor secrets; every function and handler procedure does. */
    private static void requireHandlerProperty(final String property, final UdfLanguage language,
                                               final boolean procedure) {
        if (procedure && language == UdfLanguage.SQL) {
            throw new RuntimeException(SqlCompilationError.invalidPropertyFor(property, "PROCEDURE"));
        }
    }

    /**
     * The repository a Python routine takes its packages from, as written: the shared one, or one the catalog
     * holds. A string is no name, and a routine of another language has no such property.
     */
    private String artifactRepository(final FrostlakeParser.RoutineArtifactRepositoryContext repository,
                                      final UdfLanguage language) {
        if (language != UdfLanguage.PYTHON) {
            throw new RuntimeException(SqlCompilationError.invalidPropertyFor("ARTIFACT_REPOSITORY", "FUNCTION"));
        }
        if (repository.STRING_LITERAL() != null) {
            throw new RuntimeException(SqlCompilationError.invalidValueForProperty(
                repository.STRING_LITERAL().getText(), "ARTIFACT_REPOSITORY"));
        }
        final String written = ParseTreeText.getOriginalText(repository.qualifiedName());
        final String[] parts = ParseTreeText.qualifiedNameParts(repository.qualifiedName());
        if (SHARED_REPOSITORY.equals(QualifiedName.join(parts))) {
            return written;
        }
        final Schema owner = owningSchema(parts);
        if (owner == null || owner.getContainerObjects().getArtifactRepository(parts[parts.length - 1]) == null) {
            throw new RuntimeException(SqlCompilationError.of("Object '" + written
                + "' does not exist or not authorized."));
        }
        return written;
    }

    /**
     * Where a Java or Scala handler's classes are kept, as {@code @DB.SCHEMA.STAGE/path/name.jar}. The location
     * is read first, so a stage that is missing is refused before the language is looked at; a path has to name
     * a JAR that is not there yet.
     */
    private String targetPath(final FrostlakeParser.RoutineTargetPathContext targetPath, final UdfLanguage language,
                              final boolean hasBody, final String replacedPath) {
        if (targetPath.STRING_LITERAL() == null) {
            throw new RuntimeException("Invalid value specified for property 'TARGET_PATH', use 'single quoted"
                + " string literals' instead.");
        }
        final String written = ParseTreeText.extractStringLiteral(targetPath.STRING_LITERAL());
        if (!written.startsWith("@")) {
            throw new RuntimeException(SqlCompilationError.of("invalid URL prefix found in: '" + written + "'"));
        }
        final int slash = written.indexOf('/');
        final String stage = slash >= 0 ? written.substring(1, slash) : written.substring(1);
        final String relative = slash >= 0 ? written.substring(slash + 1) : "";
        if (stage.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("missing stage name in URL: " + written));
        }
        final String location = stageLocation(stage);
        final String canonicalStage = canonicalStage(stage);
        if (language != UdfLanguage.JAVA && language != UdfLanguage.SCALA) {
            throw new RuntimeException(SqlCompilationError.invalidPropertyFor("TARGET_PATH",
                language.name() + " functions"));
        }
        if (!hasBody) {
            throw new RuntimeException(SqlCompilationError.inline(
                "Cannot specify TARGET_PATH without a function BODY."));
        }
        if (relative.length() <= JAR_SUFFIX.length()
                || !relative.toLowerCase(Locale.ROOT).endsWith(JAR_SUFFIX) || relative.endsWith("/" + JAR_SUFFIX)) {
            throw new RuntimeException(SqlCompilationError.inline(
                "TARGET_PATH must specify JAR name and location."));
        }
        final String kept = "@" + location + "/" + relative;
        final Path base = queryExecutor.resolveCopyBaseDir("@" + canonicalStage);
        // A replayed statement finds the JAR it wrote when it first ran: the file is on disk, not in the log.
        if (base != null && !StatementClock.isReplay() && !kept.equals(replacedPath)
                && Files.isRegularFile(StagePathSegments.fileAt(base, relative))) {
            throw new RuntimeException(SqlCompilationError.inline("TARGET_PATH cannot point to an existing file."));
        }
        return kept;
    }

    /**
     * The stage a target path names, spelled in full: a named stage by its three parts, a table's stage by the
     * table's schema and the quoted {@code "%TABLE"}, the user's stage as written. A stage that does not exist is
     * refused.
     */
    private String stageLocation(final String stage) {
        if (stage.startsWith("~")) {
            return stage;
        }
        final boolean ofTable = stage.startsWith("%");
        final String[] parts = SqlIdentifiers.canonicalTextParts(ofTable ? stage.substring(1) : stage);
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String last = parts[parts.length - 1];
        if (ofTable) {
            final Table table;
            try {
                table = owner.getTable(last);
            } catch (final RuntimeException missing) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("Stage", owner.qualifiedName("%" + last)));
            }
            return owner.qualifiedName("\"%" + table.getName() + "\"");
        }
        return owner.qualifiedName(SqlIdentifiers.spellCanonical(owner.getStage(last).getName()));
    }

    /** A stage as written in a path, in the spelling a stage location resolves by: its name canonical. */
    private static String canonicalStage(final String stage) {
        if (stage.startsWith("~")) {
            return stage;
        }
        final boolean ofTable = stage.startsWith("%");
        return (ofTable ? "%" : "") + SqlIdentifiers.canonicalText(ofTable ? stage.substring(1) : stage);
    }

    /**
     * Keep a handler's classes at its target path: a Java body compiled, each class an entry of the JAR; a Scala
     * body, which the engine does not compile, as its source.
     *
     * @param written  the target path as written
     * @param language the routine's language
     * @param handler  the routine's handler, {@code Class.method}
     * @param body     the routine's inline body
     * @param runtimeVersion the routine's RUNTIME_VERSION, whose Java level the classes are compiled at, or null
     */
    void store(final String written, final UdfLanguage language, final String handler, final String body,
               final String runtimeVersion) {
        final int slash = written.indexOf('/');
        final Path base = queryExecutor.resolveCopyBaseDir("@" + canonicalStage(written.substring(1, slash)));
        if (base == null || StatementClock.isReplay()
                && Files.isRegularFile(StagePathSegments.fileAt(base, written.substring(slash + 1)))) {
            return;
        }
        final int dot = handler == null ? -1 : handler.lastIndexOf('.');
        final String className = dot < 0 ? handler : handler.substring(0, dot);
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        if (language == UdfLanguage.JAVA && className != null) {
            for (final Map.Entry<String, byte[]> compiled
                    : new JavaFunctionCompiler().compileClasses(body, className,
                        JavaRuntimeLevel.of(runtimeVersion), "").entrySet()) {
                entries.put(compiled.getKey().replace('.', '/') + ".class", compiled.getValue());
            }
        } else {
            entries.put((className == null ? "Handler" : className.replace('.', '/')) + ".scala",
                body.getBytes(StandardCharsets.UTF_8));
        }
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try {
            final Path file = StagePathSegments.fileToWrite(base, written.substring(slash + 1));
            try (OutputStream out = Files.newOutputStream(file);
                 JarOutputStream jar = new JarOutputStream(out, manifest)) {
                for (final Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    jar.putNextEntry(new JarEntry(entry.getKey()));
                    jar.write(entry.getValue());
                    jar.closeEntry();
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException("Failed to write the handler JAR to " + written + ": " + e.getMessage(), e);
        }
    }

    /**
     * The integrations a list names, canonical. The list is parenthesized and each name plain: a value written
     * any other way is refused as the property's value, echoed as written. An integration has to exist.
     *
     * @param list the value written after EXTERNAL_ACCESS_INTEGRATIONS
     * @return the integrations' names, in written order
     */
    List<String> integrations(final FrostlakeParser.RoutineIntegrationListContext list) {
        final List<String> names = new ArrayList<>();
        if (list.routineIntegration() != null) {
            throw invalidIntegration(list.routineIntegration());
        }
        if (list.routineIntegrations() == null) {
            return names;
        }
        for (final FrostlakeParser.RoutineIntegrationContext item : list.routineIntegrations().routineIntegration()) {
            if (item.STRING_LITERAL() != null || ParseTreeText.qualifiedNameParts(item.qualifiedName()).length != 1) {
                throw invalidIntegration(item);
            }
            final String name = ParseTreeText.qualifiedNameParts(item.qualifiedName())[0];
            if (catalog.getIntegrations().find(name) == null) {
                throw new RuntimeException(SqlCompilationError.of("Integration '" + name
                    + "' does not exist or not authorized."));
            }
            names.add(name);
        }
        return names;
    }

    private static RuntimeException invalidIntegration(final FrostlakeParser.RoutineIntegrationContext item) {
        return new RuntimeException(SqlCompilationError.invalidValueForProperty(
            ParseTreeText.getOriginalText(item), "EXTERNAL_ACCESS_INTEGRATIONS"));
    }

    /**
     * The secrets a list names: the handler's variable for each, and the secret's full name. A variable is a
     * string and a secret a name; no variable is named twice; every secret has to exist.
     *
     * @param written the value written after SECRETS
     * @return the secrets by variable, in written order
     */
    Map<String, String> secrets(final FrostlakeParser.RoutineSecretListContext written) {
        final Map<String, String> named = new LinkedHashMap<>();
        if (written.routineSecretPart() != null) {
            throw invalidSecret(written.routineSecretPart());
        }
        final FrostlakeParser.RoutineSecretsContext list = written.routineSecrets();
        if (list == null) {
            return named;
        }
        final List<String> variables = new ArrayList<>();
        for (final FrostlakeParser.RoutineSecretContext secret : list.routineSecret()) {
            final FrostlakeParser.RoutineSecretPartContext variable = secret.routineSecretPart(0);
            if (secret.routineSecretPart().size() < 2) {
                throw invalidSecret(variable);
            }
            if (variable.STRING_LITERAL() == null) {
                throw new RuntimeException(SqlCompilationError.inline(
                    "Secret names must be specified using single quotes."));
            }
            if (secret.routineSecretPart(1).qualifiedName() == null) {
                throw new RuntimeException(SqlCompilationError.inline(
                    "Secret values must be specified using object identifier."));
            }
            final String name = ParseTreeText.extractStringLiteral(variable.STRING_LITERAL());
            if (variables.contains(name)) {
                throw new RuntimeException(SqlCompilationError.inline("Duplicate secret name '" + name
                    + "' passed in."));
            }
            variables.add(name);
        }
        for (int i = 0; i < variables.size(); i++) {
            final String[] parts = ParseTreeText.qualifiedNameParts(list.routineSecret(i).routineSecretPart(1)
                .qualifiedName());
            final String last = parts[parts.length - 1];
            final Schema owner = owningSchema(parts);
            if (owner == null || owner.getSecurityObjects().get(SecurityObjectKind.SECRET, last) == null) {
                throw new RuntimeException(SqlCompilationError.of("Secret '" + last
                    + "' does not exist or operation not authorized."));
            }
            named.put(variables.get(i), owner.qualifiedName(last));
        }
        return named;
    }

    /** A value that is no secret list or no pair of one: echoed as written, a string by what it holds. */
    private static RuntimeException invalidSecret(final FrostlakeParser.RoutineSecretPartContext part) {
        return new RuntimeException(SqlCompilationError.invalidValueForProperty(part.STRING_LITERAL() != null
            ? ParseTreeText.extractStringLiteral(part.STRING_LITERAL()) : ParseTreeText.getOriginalText(part),
            "SECRETS"));
    }

    /** The schema a written name belongs to, or null when its database or schema is not there. */
    private Schema owningSchema(final String[] parts) {
        try {
            return catalog.requireOwningSchema(QualifiedName.of(parts));
        } catch (final RuntimeException missing) {
            return null;
        }
    }
}
