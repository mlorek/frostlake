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
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The secrets of the functions a statement calls, judged while the statement compiles (live-verified). A function
 * that names secrets it cannot use — one that is gone, or any at all with no external access integration to allow
 * them ({@link RoutineSecretsCheck}) — is refused wherever a call of it is compiled, before a row is read: over an
 * empty table, in an EXPLAIN, in the query of a view and in the body of a routine being created, which are then
 * not created. The refusal is the function's by name: it comes ahead of the statement's own names and of the
 * call's argument count, and behind a relation that is not there.
 */
public final class CalledRoutineSecrets {

    private CalledRoutineSecrets() {
    }

    /**
     * Refuse the first call under a tree whose function cannot use its secrets.
     *
     * @param tree     a statement, a clause or an expression, as parsed; null reads nothing
     * @param executor the executor compiling it
     */
    public static void require(final ParseTree tree, final QueryExecutor executor) {
        if (tree == null) {
            return;
        }
        final FrostlakeParser.FunctionNameContext called = calledBy(tree);
        if (called != null && !called.identifier().isEmpty()) {
            final List<String> parts = new ArrayList<String>();
            for (final FrostlakeParser.IdentifierContext part : called.identifier()) {
                parts.add(SqlIdentifiers.canonical(part));
            }
            for (final Function overload : overloads(executor, parts)) {
                RoutineSecretsCheck.require(executor.getCatalog(), overload.getEnvironment());
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            require(tree.getChild(i), executor);
        }
    }

    /** The name a node calls a function by, or null for a node that is no call. */
    static FrostlakeParser.FunctionNameContext calledBy(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            return ((FrostlakeParser.FunctionCallExprContext) node).functionName();
        }
        if (node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallNamedArgsExprContext) node).functionName();
        }
        if (node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallMixedArgsExprContext) node).functionName();
        }
        return null;
    }

    /**
     * The user-defined functions a written name reaches: every overload of the name in the schema it is qualified
     * with, or in the first schema of the search path that holds one. A name a built-in answers to reaches none.
     *
     * @param executor the executor resolving the name
     * @param parts    the name's parts, canonical
     * @return the overloads, none when the name is no user-defined function's
     */
    public static List<Function> overloads(final QueryExecutor executor, final List<String> parts) {
        final Catalog catalog = executor.getCatalog();
        final String name = parts.get(parts.size() - 1);
        final String upper = name.toUpperCase(Locale.ROOT);
        if (parts.size() == 1 && (executor.getFunctionRegistry().hasFunction(upper)
                || executor.getFunctionRegistry().hasAggregateFunction(upper))) {
            return new ArrayList<Function>();
        }
        try {
            if (parts.size() == 3) {
                return catalog.databaseExact(parts.get(0)).schemaExact(parts.get(1)).getFunctionOverloads(name);
            }
            final String database = catalog.getCurrentDatabase();
            if (database == null || parts.size() > 3) {
                return new ArrayList<Function>();
            }
            if (parts.size() == 2) {
                return catalog.getDatabase(database).schemaExact(parts.get(0)).getFunctionOverloads(name);
            }
            for (final Schema onPath : executor.searchPathSchemas()) {
                if (!onPath.getFunctionOverloads(name).isEmpty()) {
                    return onPath.getFunctionOverloads(name);
                }
            }
            final String schema = catalog.getCurrentSchema();
            return schema == null ? new ArrayList<Function>()
                : catalog.getDatabase(database).getSchema(schema).getFunctionOverloads(name);
        } catch (final RuntimeException noSuchSchema) {
            return new ArrayList<Function>();
        }
    }
}
