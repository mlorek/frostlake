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

package dev.frostlake.executor;

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;

import java.util.Locale;

/**
 * Which statements that would end a transaction are refused for the names they write, before the transaction is
 * touched. The account compiles a statement before it commits for it: a DROP, an ALTER or a COMMENT of a table or
 * a view that is not there — or is there as the other kind — an UNDROP of a table never dropped or of a name a
 * table holds, a CREATE TABLE or CREATE VIEW under a taken name and a table created like or as the clone of a
 * source that is not there are refused with the transaction still open — a
 * ROLLBACK afterwards undoes its rows — and, in a block or a procedure called under its caller's transaction,
 * with the statement's own refusal rather than the scope's (all live-verified):
 * <pre>
 *   BEGIN; INSERT INTO tx VALUES (1); DROP TABLE nosuch;                       refused, the transaction open
 *   BEGIN; INSERT INTO tx VALUES (1); CREATE VIEW t AS SELECT 1 AS a;          refused, the transaction open
 *   BEGIN; INSERT INTO tx VALUES (1); ALTER TABLE t ADD COLUMN a INT;          refused, the row committed
 * </pre>
 * What a statement is refused for once its names read — a column to add that is there, a column to drop that is
 * not, a row its query cannot compute — comes after the commit, as the statement's own run does.
 *
 * <p>The judgement only says that the statement's handler will refuse it before it changes anything; the handler
 * then raises the refusal itself. A statement this class cannot read is not judged, and commits as it did.
 */
final class NamesJudgedFirst {

    private NamesJudgedFirst() {
    }

    /**
     * Whether the statement names what the catalog does not hold, or takes a name in use.
     *
     * @param ctx           a statement that ends an open transaction
     * @param queryExecutor the executor whose catalog is asked
     * @return true when the statement's handler refuses it for a name, before it changes anything
     */
    static boolean refuses(final FrostlakeParser.DdlStatementContext ctx, final QueryExecutor queryExecutor) {
        try {
            final Catalog catalog = queryExecutor.getCatalog();
            if (ctx.dropStatement() != null) {
                return dropsNothing(ctx.dropStatement(), queryExecutor, catalog);
            }
            if (ctx.alterStatement() != null) {
                return altersNothing(ctx.alterStatement(), queryExecutor, catalog);
            }
            if (ctx.commentStatement() != null) {
                return commentsOnNothing(ctx.commentStatement(), catalog);
            }
            if (ctx.undropStatement() != null) {
                return restoresNothing(ctx.undropStatement(), catalog);
            }
            if (ctx.createStatement() != null && createsAViewThatDoesNotCompile(ctx.createStatement(), queryExecutor)) {
                return true;
            }
            return ctx.createStatement() != null
                && createsOverATakenName(ctx.createStatement(), queryExecutor, catalog);
        } catch (final RuntimeException unread) {
            // A name this reading cannot resolve is the statement's own to judge.
            return false;
        }
    }

    private static boolean dropsNothing(final FrostlakeParser.DropStatementContext drop,
                                        final QueryExecutor queryExecutor, final Catalog catalog) {
        if (drop.if_exists() != null) {
            return false;
        }
        if ((drop.FUNCTION() != null || drop.PROCEDURE() != null) && drop.qualifiedName() != null) {
            final String[] parts = ParseTreeText.qualifiedNameParts(drop.qualifiedName());
            final Schema home = parts.length == 1 ? currentSchema(catalog) : null;
            return home != null && (drop.FUNCTION() != null ? home.getFunctionOverloads(parts[0]).isEmpty()
                : home.getProcedureOverloads(parts[0]).isEmpty());
        }
        final boolean plainTable = drop.TABLE() != null && drop.ICEBERG() == null && drop.EVENT() == null
            && drop.DYNAMIC() == null;
        final boolean plainView = drop.VIEW() != null && drop.MATERIALIZED() == null;
        // A table under DROP VIEW and a view under DROP TABLE are refused by kind, as early.
        return (plainTable || plainView) && isMissing(drop.objectName(), plainTable, queryExecutor, catalog);
    }

    private static boolean altersNothing(final FrostlakeParser.AlterStatementContext alter,
                                         final QueryExecutor queryExecutor, final Catalog catalog) {
        final boolean plainTable = alter.TABLE() != null && alter.ICEBERG() == null && alter.EXTERNAL() == null
            && alter.DYNAMIC() == null;
        final boolean plainView = alter.VIEW() != null && alter.MATERIALIZED() == null;
        if (alter.if_exists() != null || !plainTable && !plainView || !isPlain(alter.objectName())) {
            return false;
        }
        return isMissing(alter.objectName(), plainTable, queryExecutor, catalog);
    }

    private static boolean commentsOnNothing(final FrostlakeParser.CommentStatementContext comment,
                                             final Catalog catalog) {
        final boolean plainTable = comment.TABLE() != null && comment.DYNAMIC() == null;
        final boolean plainView = comment.VIEW() != null && comment.MATERIALIZED() == null;
        if (comment.if_exists() != null || comment.COLUMN() != null || !plainTable && !plainView
                || comment.qualifiedName() == null) {
            return false;
        }
        final String named = String.join(".", ParseTreeText.qualifiedNameParts(comment.qualifiedName()));
        return plainTable ? !holdsTable(named, catalog) : !holdsView(named, catalog);
    }

    /** An UNDROP TABLE of a name no dropped table carries, or one a table holds. */
    private static boolean restoresNothing(final FrostlakeParser.UndropStatementContext undrop,
                                           final Catalog catalog) {
        if (undrop.TABLE() == null || undrop.ICEBERG() != null || undrop.DYNAMIC() != null
                || undrop.qualifiedName() == null) {
            return false;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(undrop.qualifiedName());
        if (holdsTable(String.join(".", parts), catalog)) {
            return true;
        }
        final String name = "." + parts[parts.length - 1].toUpperCase(Locale.ROOT);
        for (final String key : catalog.droppedVersions().keySet()) {
            if (key.startsWith("TABLE:") && key.toUpperCase(Locale.ROOT).endsWith(name)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A CREATE TABLE or a CREATE VIEW under the name of a table or a view, or a table as the like or the clone of
     * one that is not there.
     */
    private static boolean createsOverATakenName(final FrostlakeParser.CreateStatementContext create,
                                                 final QueryExecutor queryExecutor, final Catalog catalog) {
        final boolean table = create.TABLE() != null && create.STREAM() == null && create.EVENT() == null
            && create.DYNAMIC() == null;
        final boolean view = create.VIEW() != null && create.MATERIALIZED() == null;
        if (!table && !view || create.or_replace() != null || create.or_alter() != null
                || create.if_not_exists() != null || create.TEMPORARY() != null || create.TEMP() != null
                || create.VOLATILE() != null || !isPlain(create.objectName())) {
            return false;
        }
        if (isHeld(queryExecutor.resolveObjectName(create.objectName()), catalog)) {
            return true;
        }
        if (table && (create.LIKE() != null || create.CLONE() != null) && create.qualifiedName().size() == 1) {
            return !isHeld(String.join(".", ParseTreeText.qualifiedNameParts(create.qualifiedName(0))), catalog);
        }
        return false;
    }

    /**
     * Whether a CREATE [OR REPLACE] VIEW's query does not compile: a column or a relation it names that nothing
     * answers to refuses the view with the transaction still open (live-verified). The query is compiled for its
     * shape alone, as an EXPLAIN compiles it, so nothing it reads is carried out.
     */
    private static boolean createsAViewThatDoesNotCompile(final FrostlakeParser.CreateStatementContext create,
                                                          final QueryExecutor queryExecutor) {
        if (create.VIEW() == null || create.MATERIALIZED() != null || create.selectStatement() == null
                || create.TEMPORARY() != null || create.TEMP() != null || create.VOLATILE() != null
                || create.RECURSIVE() != null || create.if_not_exists() != null) {
            return false;
        }
        try {
            ExplainPlan.compileOnly(queryExecutor, ParseTreeText.getOriginalText(create.selectStatement()));
            return false;
        } catch (final RuntimeException doesNotCompile) {
            return true;
        }
    }

    private static boolean isPlain(final FrostlakeParser.ObjectNameContext name) {
        return name != null && name.qualifiedName() != null;
    }

    /** Whether no table, or no view, is held under a name written plainly. */
    private static boolean isMissing(final FrostlakeParser.ObjectNameContext name, final boolean table,
                                     final QueryExecutor queryExecutor, final Catalog catalog) {
        if (!isPlain(name)) {
            return false;
        }
        final String named = queryExecutor.resolveObjectName(name);
        return table ? !holdsTable(named, catalog) : !holdsView(named, catalog);
    }

    /** Whether a table or a view is held under the name: a lookup that refuses the name finds neither. */
    private static boolean isHeld(final String named, final Catalog catalog) {
        return holdsView(named, catalog) || holdsTable(named, catalog);
    }

    private static boolean holdsView(final String named, final Catalog catalog) {
        try {
            return catalog.resolveView(named) != null;
        } catch (final RuntimeException missing) {
            return false;
        }
    }

    private static boolean holdsTable(final String named, final Catalog catalog) {
        try {
            return catalog.resolveTable(named) != null;
        } catch (final RuntimeException missing) {
            return false;
        }
    }

    private static Schema currentSchema(final Catalog catalog) {
        return catalog.getCurrentDatabase() == null || catalog.getCurrentSchema() == null ? null
            : catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema());
    }
}
