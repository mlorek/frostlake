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
import dev.frostlake.executor.udf.TemporaryObjectStatements;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.RelationKind;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;

/**
 * What the account judges of a DDL statement when an EXPLAIN compiles it for its plan, nothing the statement would do
 * being done (live-verified):
 *
 * <ul>
 *   <li>a CREATE of a table, a view, a materialized view, a sequence or a schema is refused over a name that is taken,
 *       {@code Object 'ET' already exists.}, and over one another kind of relation holds, {@code Object 'ET' already
 *       exists as TABLE}, whatever OR REPLACE says; IF NOT EXISTS over a taken name makes it do nothing. The source of
 *       a CLONE or a LIKE, and the table a stream is created on, must be there;</li>
 *   <li>a DROP of a table, a view, a sequence, a schema or a function, an ALTER of a table or a view and a TRUNCATE
 *       must find their object, unless IF EXISTS forgives it, and a DROP naming a relation of another kind is refused
 *       as the running statement refuses it; an ALTER TABLE … RENAME TO is refused onto a taken name, and an ALTER
 *       SESSION's parameters are judged as the running statement judges them.</li>
 * </ul>
 *
 * <p>What only running the statement finds is not judged: a column added twice, a column dropped that is not there,
 * an UNDROP with nothing to restore.
 */
public final class ExplainedStatementChecks {

    private ExplainedStatementChecks() {
    }

    /**
     * Judge a DDL statement as an EXPLAIN compiles it.
     *
     * @param executor the executor, which resolves the statement's names
     * @param ddl      the statement
     * @return false where the statement would do nothing: a create IF NOT EXISTS of a name that is taken
     */
    public static boolean compile(final QueryExecutor executor, final FrostlakeParser.DdlStatementContext ddl) {
        if (ddl.createStatement() != null) {
            return created(executor, ddl.createStatement());
        }
        if (ddl.dropStatement() != null) {
            dropped(executor, ddl.dropStatement());
        } else if (ddl.alterStatement() != null) {
            altered(executor, ddl.alterStatement());
        } else if (ddl.truncateStatement() != null && ddl.truncateStatement().if_exists() == null) {
            requireRelation(executor, executor.resolveObjectNameParts(ddl.truncateStatement().objectName()), "Table");
        }
        return true;
    }

    private static boolean created(final QueryExecutor executor, final FrostlakeParser.CreateStatementContext create) {
        final Catalog catalog = executor.getCatalog();
        final boolean replaced = create.or_replace() != null || create.or_alter() != null;
        final RelationKind kind = createdRelation(create);
        if (kind != null) {
            final String[] parts = catalog.withoutAccount(executor.resolveObjectNameParts(create.objectName()), 3);
            final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
            final String name = parts[parts.length - 1];
            final boolean temporary = TemporaryObjectStatements.isTemporary(create);
            schema.rejectNameHeldByOtherKind(name, kind, temporary);
            if (!replaced && schema.holdsRelation(name, kind)) {
                if (create.if_not_exists() != null) {
                    return false;
                }
                throw taken(parts);
            }
            if (create.CLONE() != null || create.LIKE() != null) {
                catalog.resolveTableAsWritten(executor.searchPathQualified(
                    ParseTreeText.getQualifiedName(create.qualifiedName(0))), "Object",
                    create.CLONE() != null ? "CLONE" : "DUPLICATE");
            }
            return true;
        }
        if (create.SEQUENCE() != null || create.SCHEMA() != null) {
            final String[] parts = catalog.withoutAccount(
                ParseTreeText.qualifiedNameParts(create.qualifiedName(0)), create.SCHEMA() != null ? 2 : 3);
            final boolean held = create.SCHEMA() != null ? schemaHeld(catalog, parts)
                : catalog.requireOwningSchema(QualifiedName.of(parts)).hasSequenceExact(parts[parts.length - 1]);
            if (!replaced && held) {
                if (create.if_not_exists() != null) {
                    return false;
                }
                throw taken(parts);
            }
            return true;
        }
        if (create.STREAM() != null && create.streamSourceKind() != null && create.streamSourceKind().TABLE() != null
                && create.qualifiedName().size() > 1) {
            catalog.resolveTableAsWritten(ParseTreeText.getQualifiedName(create.qualifiedName(1)), "Table");
        }
        return true;
    }

    /** The relation a CREATE makes, for a table, a view and a materialized view; null for any other CREATE. */
    private static RelationKind createdRelation(final FrostlakeParser.CreateStatementContext create) {
        if (create.objectName() == null || create.DYNAMIC() != null || create.EVENT() != null) {
            return null;
        }
        if (create.VIEW() != null) {
            return create.MATERIALIZED() != null ? RelationKind.MATERIALIZED_VIEW : RelationKind.VIEW;
        }
        return create.TABLE() != null ? RelationKind.TABLE : null;
    }

    /** Whether a schema of this name is there: the current database's for a bare name. */
    private static boolean schemaHeld(final Catalog catalog, final String[] parts) {
        final String database = parts.length == 1 ? catalog.getCurrentDatabase() : parts[0];
        return database != null && catalog.hasDatabaseExact(database)
            && catalog.databaseExact(database).hasSchemaExact(parts[parts.length - 1]);
    }

    private static void dropped(final QueryExecutor executor, final FrostlakeParser.DropStatementContext drop) {
        final boolean ifExists = drop.if_exists() != null;
        if (drop.objectName() != null && drop.ICEBERG() == null && drop.EVENT() == null && drop.DYNAMIC() == null) {
            final String[] parts = executor.resolveObjectNameParts(drop.objectName());
            if (drop.TABLE() != null) {
                requireDropped(executor, parts, RelationKind.TABLE, "Table", ifExists);
            } else if (drop.VIEW() != null && drop.MATERIALIZED() == null) {
                requireDropped(executor, parts, RelationKind.VIEW, "View", ifExists);
            } else if (drop.SEQUENCE() != null && !ifExists) {
                final String[] qualified = qualified(executor.getCatalog(), parts);
                if (!executor.getCatalog().requireOwningSchema(QualifiedName.of(parts))
                        .hasSequenceExact(qualified[2])) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Sequence",
                        QualifiedName.join(qualified)));
                }
            } else if (drop.SCHEMA() != null && !ifExists) {
                final Catalog catalog = executor.getCatalog();
                final String[] named = catalog.withoutAccount(parts, 2);
                if (!schemaHeld(catalog, named)) {
                    final String database = named.length == 1 ? catalog.getCurrentDatabase() : named[0];
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Schema",
                        QualifiedName.join(database, named[named.length - 1])));
                }
            }
        } else if (drop.FUNCTION() != null && !ifExists) {
            final String[] parts = ParseTreeText.qualifiedNameParts(drop.qualifiedName());
            final String[] qualified = qualified(executor.getCatalog(), parts);
            if (executor.getCatalog().requireOwningSchema(QualifiedName.of(parts))
                    .getFunctionOverloads(qualified[2]).isEmpty()) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("Function", QualifiedName.join(qualified)));
            }
        }
    }

    /**
     * A DROP of a relation finds it, of the kind it names: a name another kind holds is refused whatever IF EXISTS
     * says, a name nothing holds unless IF EXISTS forgives it.
     */
    private static void requireDropped(final QueryExecutor executor, final String[] parts, final RelationKind kind,
                                       final String noun, final boolean ifExists) {
        final Schema schema = executor.getCatalog().requireOwningSchema(QualifiedName.of(parts));
        final String[] qualified = qualified(executor.getCatalog(), parts);
        final RelationKind held = schema.relationKindOf(qualified[2]);
        if (held != null && held != kind) {
            throw new RuntimeException(SqlCompilationError.objectOfOtherType(held.spelling(), kind.spelling()));
        }
        if (held == null && !ifExists) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(noun, QualifiedName.join(qualified)));
        }
    }

    private static void altered(final QueryExecutor executor, final FrostlakeParser.AlterStatementContext alter) {
        if (alter.SESSION() != null && alter.sessionAction() != null) {
            final FrostlakeParser.SessionActionContext action = alter.sessionAction();
            if (action.SET() != null) {
                SessionParameterSettings.judged(action.sessionAssignment(), executor, executor.getCatalog());
            } else {
                SessionParameterSettings.named(action.sessionParameter());
            }
            return;
        }
        if (alter.objectName() == null || alter.if_exists() != null) {
            return;
        }
        final String[] parts = executor.resolveObjectNameParts(alter.objectName());
        if (alter.TABLE() != null && alter.tableAction() != null && alter.ICEBERG() == null
                && alter.EXTERNAL() == null && alter.DYNAMIC() == null) {
            requireRelation(executor, parts, "Table");
            final FrostlakeParser.WholeObjectNameContext renamed = alter.tableAction().wholeObjectName();
            if (renamed != null && renamed.qualifiedName() != null) {
                final String[] target = ParseTreeText.qualifiedNameParts(renamed.qualifiedName());
                final String[] placed = executor.getCatalog().withoutAccount(target, 3);
                if (executor.getCatalog().requireOwningSchema(QualifiedName.of(placed))
                        .relationKindOf(placed[placed.length - 1]) != null) {
                    throw taken(placed);
                }
            }
        } else if (alter.VIEW() != null && alter.MATERIALIZED() == null && alter.viewAction() != null) {
            requireRelation(executor, parts, "View");
        }
    }

    /** A relation the statement names is there, refused in full as a DDL statement names it. */
    private static void requireRelation(final QueryExecutor executor, final String[] parts, final String noun) {
        final Schema schema = executor.getCatalog().requireOwningSchema(QualifiedName.of(parts));
        final String[] qualified = qualified(executor.getCatalog(), parts);
        if (schema.relationKindOf(qualified[2]) == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(noun, QualifiedName.join(qualified)));
        }
    }

    /** A schema-level object's name in full: the session's database and schema where the name leaves them out. */
    private static String[] qualified(final Catalog catalog, final String[] written) {
        final String[] parts = catalog.withoutAccount(written, 3);
        if (parts.length == 3) {
            return parts;
        }
        return parts.length == 2 ? new String[] {catalog.getCurrentDatabase(), parts[0], parts[1]}
            : new String[] {catalog.getCurrentDatabase(), catalog.getCurrentSchema(), parts[0]};
    }

    /** The refusal of a create onto a name that is taken, the name as the statement wrote it. */
    private static RuntimeException taken(final String[] parts) {
        final StringBuilder written = new StringBuilder();
        for (final String part : parts) {
            written.append(written.length() > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(part));
        }
        return new RuntimeException(SqlCompilationError.of("Object '" + written + "' already exists."));
    }
}
