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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.AppObject;
import dev.frostlake.metastore.model.AppObjectKind;
import dev.frostlake.metastore.model.AppObjectStore;
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
            return drop(kind, ctx.dropInstanceName(), ctx.if_exists() != null);
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
            final AppObject object = startCreate(kind, nameCtx, ctx.or_replace() != null,
                ctx.if_not_exists() != null);
            if (object == null) {
                return null;
            }
            object.setFromLocation(ctx.FROM() != null ? literal(ctx.STRING_LITERAL()) : null);
            for (final FrostlakeParser.StreamlitOptionContext option : ctx.streamlitOption()) {
                applyStreamlitOption(object, option);
            }
            if (object.getRootLocation() == null) {
                object.addVersion();
            }
            finishCreate(nameCtx, object);
            return null;
        }
        if (ctx.DROP() != null) {
            return drop(kind, ctx.dropInstanceName(), ctx.if_exists() != null);
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
                applyStreamlitOption(object, option);
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

    private void applyStreamlitOption(final AppObject object, final FrostlakeParser.StreamlitOptionContext option) {
        if (option.ROOT_LOCATION() != null) {
            object.setRootLocation(literal(option.STRING_LITERAL(0)));
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
     * and a missing object is forgiven by IF EXISTS alone. CASCADE and RESTRICT change nothing.
     */
    private Object drop(final AppObjectKind kind, final FrostlakeParser.DropInstanceNameContext instance,
                        final boolean ifExists) {
        final String[] parts = instance.objectName() != null
            ? queryExecutor.resolveObjectNameParts(instance.objectName())
            : SqlIdentifiers.canonicalTextParts(instance.getText());
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        final AppObject object = schema.getAppObjects().get(kind, name);
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
        final String newName = nameOf(toCtx);
        if (to.getAppObjects().get(object.getKind(), newName) != null) {
            throw alreadyExists(newName);
        }
        from.getAppObjects().remove(object.getKind(), object.getName());
        object.setName(newName);
        object.setDatabaseName(to.getDatabaseName());
        object.setSchemaName(to.getName());
        to.getAppObjects().put(object);
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
        if (!catalog.hasWarehouse(warehouse)) {
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
