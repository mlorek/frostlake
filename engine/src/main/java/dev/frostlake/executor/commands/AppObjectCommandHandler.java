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
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StagePrefix;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.GrantNames;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.AppObject;
import dev.frostlake.metastore.model.AppObjectKind;
import dev.frostlake.metastore.model.AppObjectStore;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CREATE, ALTER, DROP and UNDROP of notebooks and Streamlit apps, and EXECUTE NOTEBOOK. The objects are catalog
 * metadata — their settings and version history — kept in the owning schema's {@link AppObjectStore}; see
 * {@link AppObject} for the version model and {@link AppObjectActions} for the version and Git actions. A statement
 * answers null, taking its status sentence from the statement's shape, or the account's own status sentence.
 */
public class AppObjectCommandHandler {

    /** Why a notebook cannot run: it names no query warehouse. */
    private static final String NO_QUERY_WAREHOUSE =
        "To execute the notebook, set the Query warehouse and the Notebook warehouse / Compute pool.";

    /** The main file a Streamlit app runs when its statement names none. */
    private static final String DEFAULT_MAIN_FILE = "streamlit_app.py";
    /** The main file a notebook created FROM a location without MAIN_FILE is taken to run. */
    private static final String DEFAULT_NOTEBOOK_FILE = "notebook_app.ipynb";

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final SQLCommandVisitor visitor;
    private final AppObjectActions actions;

    /**
     * @param catalog the catalog holding the schemas
     * @param queryExecutor the executor, for resolving a DROP's object name
     * @param visitor the visitor, for decoding string literals
     */
    public AppObjectCommandHandler(final Catalog catalog, final QueryExecutor queryExecutor,
                                   final SQLCommandVisitor visitor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.visitor = visitor;
        this.actions = new AppObjectActions(catalog, visitor);
    }

    /**
     * A notebook statement: CREATE, ALTER (RENAME TO, SET, UNSET, the version and Git actions), EXECUTE NOTEBOOK,
     * DROP or UNDROP.
     */
    public Object handleNotebook(final FrostlakeParser.NotebookStatementContext ctx) {
        final AppObjectKind kind = AppObjectKind.NOTEBOOK;
        final FrostlakeParser.QualifiedNameContext nameCtx = ctx.qualifiedName(0);
        if (ctx.CREATE() != null) {
            AppObjectActions.refuseDuplicates(ctx.notebookOption());
            for (final FrostlakeParser.NotebookOptionContext option : ctx.notebookOption()) {
                if (option.SECRETS() != null) {
                    actions.checkSecrets(option.appSecretList(), true);
                }
            }
            if (ctx.FROM() != null) {
                // A notebook copies its location's files as an app does, and is refused in the same words but for
                // its main file's (live-verified).
                String mainFile = DEFAULT_NOTEBOOK_FILE;
                for (final FrostlakeParser.NotebookOptionContext option : ctx.notebookOption()) {
                    if (option.MAIN_FILE() != null) {
                        mainFile = literal(option.STRING_LITERAL());
                    }
                }
                requireCopiedSource(literal(ctx.STRING_LITERAL()), mainFile, "Notebook MAIN_FILE cannot be found.");
            }
            final AppObject object = startCreate(kind, nameCtx, ctx.or_replace() != null,
                ctx.if_not_exists() != null);
            if (object == null) {
                return null;
            }
            object.setFromLocation(ctx.FROM() != null ? literal(ctx.STRING_LITERAL()) : null);
            for (final FrostlakeParser.NotebookOptionContext option : ctx.notebookOption()) {
                applyNotebookOption(object, option);
            }
            object.addVersion();
            finishCreate(nameCtx, object);
            return null;
        }
        if (ctx.EXECUTE() != null) {
            return execute(existing(kind, nameCtx, false));
        }
        if (ctx.DROP() != null) {
            return drop(kind, ctx.dropInstanceName(), ctx.if_exists() != null, ctx.dropBehavior());
        }
        if (ctx.UNDROP() != null) {
            return undrop(kind, nameCtx);
        }
        if (ctx.appVersionAction() != null) {
            actions.check(ctx.appVersionAction());
            return actions.apply(existing(kind, nameCtx, false), ctx.appVersionAction());
        }
        final AppObject object = existing(kind, nameCtx, ctx.if_exists() != null);
        if (object == null) {
            return null;
        }
        if (ctx.RENAME() != null) {
            rename(object, nameCtx, ctx.qualifiedName(1));
        } else if (ctx.SET() != null) {
            AppObjectActions.refuseDuplicates(ctx.notebookSetOption());
            for (final FrostlakeParser.NotebookSetOptionContext option : ctx.notebookSetOption()) {
                if (option.SECRETS() != null) {
                    actions.checkSecrets(option.appSecretList(), false);
                }
            }
            for (final FrostlakeParser.NotebookSetOptionContext option : ctx.notebookSetOption()) {
                if (option.COMMENT() != null) {
                    object.setComment(literal(option.STRING_LITERAL()));
                } else if (option.QUERY_WAREHOUSE() != null) {
                    object.setQueryWarehouse(queryWarehouse(option.identifier()));
                } else if (option.IDLE_AUTO_SHUTDOWN_TIME_SECONDS() != null) {
                    object.setIdleAutoShutdownTimeSeconds(Long.valueOf(option.INTEGER_LITERAL().getText()));
                }
            }
        } else {
            AppObjectActions.refuseDuplicates(ctx.notebookUnsetProperty());
            for (final FrostlakeParser.NotebookUnsetPropertyContext property : ctx.notebookUnsetProperty()) {
                if (property.IDLE_AUTO_SHUTDOWN_TIME_SECONDS() != null) {
                    throw new RuntimeException(SqlCompilationError.of(
                        "invalid type of property 'null' for 'IDLE_AUTO_SHUTDOWN_TIME_SECONDS'"));
                }
            }
            for (final FrostlakeParser.NotebookUnsetPropertyContext property : ctx.notebookUnsetProperty()) {
                if (property.COMMENT() != null) {
                    object.setComment(null);
                } else if (property.QUERY_WAREHOUSE() != null) {
                    object.setQueryWarehouse(null);
                }
            }
        }
        return null;
    }

    /**
     * EXECUTE NOTEBOOK: a notebook runs from its live version on its query warehouse, so it needs both; Frostlake
     * runs no notebook code, and a notebook that could run answers as a run that succeeded. The arguments are not
     * read before those checks, as the account reads them.
     */
    private static Object execute(final AppObject notebook) {
        if (notebook.getQueryWarehouse() == null) {
            throw new RuntimeException(NO_QUERY_WAREHOUSE);
        }
        if (!notebook.hasLiveVersion()) {
            throw new RuntimeException("Live version is not found.");
        }
        return null;
    }

    /**
     * A Streamlit statement: CREATE, ALTER (SET, UNSET, RENAME TO, the version and Git actions), DROP or UNDROP.
     */
    public Object handleStreamlit(final FrostlakeParser.StreamlitStatementContext ctx) {
        final AppObjectKind kind = AppObjectKind.STREAMLIT;
        final FrostlakeParser.QualifiedNameContext nameCtx = ctx.qualifiedName(0);
        if (ctx.CREATE() != null) {
            AppObjectActions.refuseDuplicates(ctx.streamlitOption());
            for (final FrostlakeParser.StreamlitOptionContext option : ctx.streamlitOption()) {
                if (option.SECRETS() != null) {
                    actions.checkSecrets(option.appSecretList(), true);
                }
            }
            if (ctx.FROM() != null) {
                String mainFile = DEFAULT_MAIN_FILE;
                for (final FrostlakeParser.StreamlitOptionContext option : ctx.streamlitOption()) {
                    if (option.MAIN_FILE() != null) {
                        mainFile = literal(option.STRING_LITERAL(0));
                    }
                }
                requireCopiedSource(literal(ctx.STRING_LITERAL()), mainFile,
                    SqlCompilationError.of("Missing MAIN_FILE."));
            }
            for (final FrostlakeParser.StreamlitOptionContext option : ctx.streamlitOption()) {
                if (option.ROOT_LOCATION() != null) {
                    rootLocation(literal(option.STRING_LITERAL(0)), false);
                }
            }
            final AppObject object = startCreate(kind, nameCtx, ctx.or_replace() != null,
                ctx.if_not_exists() != null);
            if (object == null) {
                return null;
            }
            object.setFromLocation(ctx.FROM() != null ? literal(ctx.STRING_LITERAL()) : null);
            for (final FrostlakeParser.StreamlitOptionContext option : ctx.streamlitOption()) {
                applyStreamlitOption(object, option, false);
            }
            if (object.getRootLocation() == null) {
                object.addVersion();
            }
            finishCreate(nameCtx, object);
            return null;
        }
        if (ctx.DROP() != null) {
            return drop(kind, ctx.dropInstanceName(), ctx.if_exists() != null, ctx.dropBehavior());
        }
        if (ctx.UNDROP() != null) {
            return undrop(kind, nameCtx);
        }
        if (ctx.appVersionAction() != null) {
            actions.check(ctx.appVersionAction());
            return actions.apply(existing(kind, nameCtx, false), ctx.appVersionAction());
        }
        final AppObject object = existing(kind, nameCtx, ctx.if_exists() != null);
        if (object == null) {
            return null;
        }
        if (ctx.RENAME() != null) {
            rename(object, nameCtx, ctx.qualifiedName(1));
        } else if (ctx.SET() != null) {
            AppObjectActions.refuseDuplicates(ctx.streamlitOption());
            for (final FrostlakeParser.StreamlitOptionContext option : ctx.streamlitOption()) {
                if (option.SECRETS() != null) {
                    actions.checkSecrets(option.appSecretList(), false);
                }
            }
            for (final FrostlakeParser.StreamlitOptionContext option : ctx.streamlitOption()) {
                applyStreamlitOption(object, option, true);
            }
        } else {
            AppObjectActions.refuseDuplicates(ctx.streamlitUnsetProperty());
            for (final FrostlakeParser.StreamlitUnsetPropertyContext property : ctx.streamlitUnsetProperty()) {
                if (property.COMMENT() != null) {
                    object.setComment(null);
                } else if (property.TITLE() != null) {
                    object.setTitle(null);
                } else if (property.QUERY_WAREHOUSE() != null) {
                    object.setQueryWarehouse(null);
                } else if (property.EXTERNAL_ACCESS_INTEGRATIONS() != null) {
                    object.setExternalAccessIntegrations(new ArrayList<String>());
                }
            }
        }
        return null;
    }

    /**
     * Judge the location a Streamlit app or a notebook is created FROM. The object copies the location's files as it
     * is created, so the location names a stage, a named one that exists, and the object's main file is among the
     * files under it (live-verified); a ROOT_LOCATION is judged by {@link #rootLocation}. A stage that is not there
     * is refused in the app's words for a notebook too.
     *
     * @param from            the location as written
     * @param mainFile        the object's main file, relative to the location
     * @param missingMainFile the refusal of a main file the location does not hold
     */
    private void requireCopiedSource(final String from, final String mainFile, final String missingMainFile) {
        if (StatementClock.isReplay()) {
            // A replayed statement was judged when it first ran, against the files the stage held then.
            return;
        }
        if (!from.startsWith("@")) {
            throw new RuntimeException(SqlCompilationError.of("invalid URL prefix found in: '" + from + "'"));
        }
        final String reference = from.substring(1);
        final int slash = reference.indexOf('/');
        final String stage = slash >= 0 ? reference.substring(0, slash) : reference;
        if (stage.startsWith("~")) {
            throw new RuntimeException(SqlCompilationError.inline(
                "Copy files not support stage kind: 'USER' for stage '~'."));
        }
        if (stage.startsWith("%")) {
            throw new RuntimeException(SqlCompilationError.inline("Copy files not support stage kind: 'TABLE' for stage '"
                + SqlIdentifiers.canonicalText(QualifiedName.parse(stage.substring(1)).last()) + "'."));
        }
        final String stageName = SqlIdentifiers.canonicalText(QualifiedName.parse(stage).last());
        final Schema schema = catalog.resolveOwningSchema(SqlIdentifiers.canonicalText(stage));
        if (schema == null || !schema.hasStageExact(stageName)) {
            throw new RuntimeException("The specified stage " + stageName + " does not exist or the current role"
                + " does not have access. Owner of the Streamlit must have at least READ on the specified stage.");
        }
        // The stage is read by its name as the catalog holds it, whichever way the location spells it.
        final StagePrefix files = queryExecutor.resolveStagePrefix("@" + SqlIdentifiers.canonicalText(stage)
            + (slash >= 0 ? reference.substring(slash) : ""));
        if (files == null || files.getRoot() == null) {
            return;
        }
        final String path = slash >= 0 ? reference.substring(slash + 1) : "";
        final String folder = path.isEmpty() || path.endsWith("/") ? path : path + "/";
        if (!files.occupied(folder + mainFile)) {
            throw new RuntimeException(missingMainFile);
        }
    }

    private void applyNotebookOption(final AppObject object, final FrostlakeParser.NotebookOptionContext option) {
        if (option.MAIN_FILE() != null) {
            object.setMainFile(literal(option.STRING_LITERAL()));
        } else if (option.COMMENT() != null) {
            object.setComment(literal(option.STRING_LITERAL()));
        } else if (option.QUERY_WAREHOUSE() != null) {
            object.setQueryWarehouse(queryWarehouse(option.identifier()));
        } else if (option.IDLE_AUTO_SHUTDOWN_TIME_SECONDS() != null) {
            object.setIdleAutoShutdownTimeSeconds(Long.valueOf(option.INTEGER_LITERAL().getText()));
        } else if (option.RUNTIME_NAME() != null) {
            object.setRuntimeName(literal(option.STRING_LITERAL()));
        } else if (option.COMPUTE_POOL() != null) {
            object.setComputePool(literal(option.STRING_LITERAL()));
        } else if (option.WAREHOUSE() != null) {
            object.setWarehouse(SqlIdentifiers.canonical(option.identifier()));
        }
    }

    /**
     * A Streamlit app's ROOT_LOCATION as the app keeps it, judged as the account judges it: a location with no
     * {@code @} is refused, and so is a named stage that is not there, in CREATE's own words or, for an ALTER … SET,
     * as a stage that does not exist. The stage is kept by its name as the catalog holds it and the path after it as
     * written, {@code @stg/old} as {@code @STG/old}; the path itself is never looked at (live-verified). A user or a
     * table stage is taken as written.
     *
     * @param location the location as written
     * @param altering whether an ALTER … SET gives it
     * @return the location as the app keeps it
     */
    private String rootLocation(final String location, final boolean altering) {
        if (!location.startsWith("@")) {
            throw new RuntimeException(SqlCompilationError.of("invalid URL prefix found in: '" + location + "'"));
        }
        final String reference = location.substring(1);
        final int slash = reference.indexOf('/');
        final String stage = slash >= 0 ? reference.substring(0, slash) : reference;
        if (stage.startsWith("~") || stage.startsWith("%")) {
            return location;
        }
        final String canonical = SqlIdentifiers.canonicalText(stage);
        final String stageName = SqlIdentifiers.canonicalText(QualifiedName.parse(stage).last());
        final Schema schema = catalog.resolveOwningSchema(canonical);
        // A replayed statement was judged when it first ran, against the stages there then.
        if (!StatementClock.isReplay() && (schema == null || !schema.hasStageExact(stageName))) {
            if (altering) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("Stage", stagePath(canonical)));
            }
            throw new RuntimeException("The specified stage " + stageName + " does not exist or the current role"
                + " does not have access. Owner of the Streamlit must have at least READ on the specified stage.");
        }
        final String path = slash >= 0 ? reference.substring(slash) : "";
        return "@" + SqlIdentifiers.spellAlreadyCanonicalPath(canonical) + path;
    }

    /** A stage reference's whole path, the current database and schema supplying the parts it leaves out. */
    private String stagePath(final String canonical) {
        final String[] parts = SqlIdentifiers.canonicalTextParts(canonical);
        final List<String> path = new ArrayList<>();
        if (parts.length < 3) {
            path.add(catalog.getCurrentDatabase());
        }
        if (parts.length < 2) {
            path.add(catalog.getCurrentSchema());
        }
        path.addAll(Arrays.asList(parts));
        return QualifiedName.join(path.toArray(new String[0]));
    }

    private void applyStreamlitOption(final AppObject object, final FrostlakeParser.StreamlitOptionContext option,
                                      final boolean altering) {
        if (option.ROOT_LOCATION() != null) {
            object.setRootLocation(rootLocation(literal(option.STRING_LITERAL(0)), altering));
        } else if (option.MAIN_FILE() != null) {
            object.setMainFile(literal(option.STRING_LITERAL(0)));
        } else if (option.QUERY_WAREHOUSE() != null) {
            object.setQueryWarehouse(queryWarehouse(option.identifier(0)));
        } else if (option.RUNTIME_NAME() != null) {
            object.setRuntimeName(literal(option.STRING_LITERAL(0)));
        } else if (option.COMPUTE_POOL() != null) {
            object.setComputePool(SqlIdentifiers.canonical(option.identifier(0)));
        } else if (option.COMMENT() != null) {
            object.setComment(literal(option.STRING_LITERAL(0)));
        } else if (option.TITLE() != null) {
            object.setTitle(literal(option.STRING_LITERAL(0)));
        } else if (option.IMPORTS() != null) {
            final List<String> imports = new ArrayList<>();
            for (final TerminalNode path : option.STRING_LITERAL()) {
                imports.add(literal(path));
            }
            object.setImports(imports);
        } else if (option.EXTERNAL_ACCESS_INTEGRATIONS() != null) {
            final List<String> integrations = new ArrayList<>();
            for (final FrostlakeParser.IdentifierContext integration : option.identifier()) {
                integrations.add(SqlIdentifiers.canonical(integration));
            }
            object.setExternalAccessIntegrations(integrations);
        }
    }

    /**
     * The new object for a CREATE, or null when IF NOT EXISTS found one already. An existing object is replaced
     * under OR REPLACE and refused otherwise.
     */
    private AppObject startCreate(final AppObjectKind kind, final FrostlakeParser.QualifiedNameContext nameCtx,
                                  final boolean orReplace, final boolean ifNotExists) {
        final String name = nameOf(nameCtx);
        final Schema schema = schemaOf(nameCtx);
        queryExecutor.requireCreatePrivilege(Privilege.valueOf("CREATE_" + kind.name()), schema);
        final AppObjectStore store = schema.getAppObjects();
        if (store.get(kind, name) != null) {
            if (ifNotExists) {
                ConditionalDdlOutcome.createSkipped();
                return null;
            }
            if (!orReplace) {
                throw alreadyExists(schema.qualifiedName(name));
            }
        }
        final AppObject object = new AppObject(kind, name);
        object.setOwner(catalog.currentRoleForOwner());
        return object;
    }

    private void finishCreate(final FrostlakeParser.QualifiedNameContext nameCtx, final AppObject object) {
        final Schema schema = schemaOf(nameCtx);
        object.setDatabaseName(schema.getDatabaseName());
        object.setSchemaName(schema.getName());
        schema.getAppObjects().put(object);
    }

    /** The object an ALTER names, or null when it is absent and IF EXISTS forgives that. */
    private AppObject existing(final AppObjectKind kind, final FrostlakeParser.QualifiedNameContext nameCtx,
                               final boolean ifExists) {
        final Schema schema = schemaOf(nameCtx);
        final AppObject object = schema.getAppObjects().get(kind, nameOf(nameCtx));
        if (object == null && !ifExists) {
            throw missing(kind, schema, nameOf(nameCtx));
        }
        return object;
    }

    /**
     * DROP: the name resolves as any object's does — a missing database or schema is refused, IF EXISTS or not —
     * and a missing object is forgiven by IF EXISTS alone. CASCADE and RESTRICT change nothing; a PURGE is refused
     * once the name has resolved (see {@link DropPurge}).
     */
    private Object drop(final AppObjectKind kind, final FrostlakeParser.DropInstanceNameContext instance,
                        final boolean ifExists, final FrostlakeParser.DropBehaviorContext behavior) {
        final String[] parts = instance.objectName() != null
            ? queryExecutor.resolveObjectNameParts(instance.objectName())
            : SqlIdentifiers.canonicalTextParts(instance.getText());
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        final AppObject object = schema.getAppObjects().get(kind, name);
        if (DropPurge.written(behavior) && (ifExists || object != null)) {
            throw DropPurge.refusal(parts);
        }
        if (object == null) {
            if (ifExists) {
                ConditionalDdlOutcome.dropSkipped();
                return null;
            }
            throw missing(kind, schema, name);
        }
        schema.getAppObjects().drop(object);
        return null;
    }

    private Object undrop(final AppObjectKind kind, final FrostlakeParser.QualifiedNameContext nameCtx) {
        final Schema schema = schemaOf(nameCtx);
        final String name = nameOf(nameCtx);
        final AppObjectStore store = schema.getAppObjects();
        if (store.get(kind, name) != null) {
            throw alreadyExists(name);
        }
        final AppObject dropped = store.lastDropped(kind, name);
        if (dropped == null) {
            throw new RuntimeException(kind.displayName() + " " + SqlIdentifiers.spellCanonical(name)
                + " did not exist or was purged.");
        }
        store.undrop(dropped);
        return status(kind.displayName() + " " + SqlIdentifiers.spellCanonical(name) + " successfully restored.");
    }

    /** A one-row status answer carrying the statement's own sentence. */
    private static ResultSet status(final String sentence) {
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Arrays.<Object>asList(sentence)));
        return new ResultSet(Arrays.asList(new ResultSetColumn("status", StringType.VARCHAR)), rows);
    }

    /** RENAME TO: a qualified new name moves the object to that schema. */
    private void rename(final AppObject object, final FrostlakeParser.QualifiedNameContext fromCtx,
                        final FrostlakeParser.QualifiedNameContext toCtx) {
        final Schema from = schemaOf(fromCtx);
        final Schema to = schemaOf(toCtx);
        to.requireCreatable(Privilege.valueOf("CREATE_" + object.getKind().name()));
        final String newName = nameOf(toCtx);
        if (to.getAppObjects().get(object.getKind(), newName) != null) {
            throw alreadyExists(newName);
        }
        final String grantedBefore = from.qualifiedName(object.getName());
        from.getAppObjects().remove(object.getKind(), object.getName());
        object.setName(newName);
        object.setDatabaseName(to.getDatabaseName());
        object.setSchemaName(to.getName());
        to.getAppObjects().put(object);
        GrantNames.renamed(catalog, object.getKind().name(), grantedBefore, to.qualifiedName(newName));
    }

    private Schema schemaOf(final FrostlakeParser.QualifiedNameContext nameCtx) {
        return catalog.requireOwningSchema(QualifiedName.of(ParseTreeText.qualifiedNameParts(nameCtx)));
    }

    private static String nameOf(final FrostlakeParser.QualifiedNameContext nameCtx) {
        final String[] parts = ParseTreeText.qualifiedNameParts(nameCtx);
        return parts[parts.length - 1];
    }

    /**
     * A query warehouse, which must exist: {@code The specified warehouse X does not exist or the current role does
     * not have access. …}, the sentence the account answers for notebooks and Streamlit apps alike.
     */
    private String queryWarehouse(final FrostlakeParser.IdentifierContext name) {
        final String warehouse = SqlIdentifiers.canonical(name);
        if (!catalog.hasWarehouseExact(warehouse)) {
            throw new RuntimeException("The specified warehouse " + warehouse + " does not exist or the current role "
                + "does not have access. Owner of the Streamlit must have at least USAGE on the specified warehouse.");
        }
        return warehouse;
    }

    private String literal(final TerminalNode node) {
        return visitor.extractStringLiteral(node);
    }

    /** {@code Object '<name>' already exists.}; CREATE names the object fully qualified, UNDROP and RENAME bare. */
    private static RuntimeException alreadyExists(final String name) {
        return new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellAlreadyCanonicalPath(name)
            + "' already exists."));
    }

    private static RuntimeException missing(final AppObjectKind kind, final Schema schema, final String name) {
        return new RuntimeException(SqlCompilationError.doesNotExist(kind.displayName(),
            schema.qualifiedName(name)));
    }
}
