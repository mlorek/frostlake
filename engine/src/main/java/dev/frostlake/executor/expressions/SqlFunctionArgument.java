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

package dev.frostlake.executor.expressions;

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The SQL function a subquery is written inside the call of. A scalar subquery whose shape cannot be evaluated is
 * refused by another sentence there, one that names the function and gives no place:
 * {@code SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz} is "Unsupported subquery type cannot be evaluated
 * inside Function object: F1". The function is the innermost one whose arguments hold the subquery, however deep
 * in them it stands — under a built-in call, a CASE, a cast or an operator — so {@code ABS(f2(1, ABS((SELECT …))))}
 * names F2, and it is named as it is stored, whatever path the call writes. A function written in another language
 * keeps the placed sentence, as a built-in does (all live-verified).
 */
public final class SqlFunctionArgument {

    private SqlFunctionArgument() {
    }

    /**
     * The SQL function whose call holds a subquery.
     *
     * @param executor the statement's executor
     * @param subquery the subquery's node
     * @return the innermost such function inside the subquery's own query, or null
     */
    public static Function calledWith(final QueryExecutor executor, final ParseTree subquery) {
        for (ParseTree at = subquery.getParent(); at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.SelectStatementContext) {
                return null;
            }
            final FrostlakeParser.FunctionNameContext name;
            final int arguments;
            if (at instanceof FrostlakeParser.FunctionCallExprContext) {
                final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) at;
                name = call.functionName();
                arguments = call.functionArgList() == null ? 0 : call.functionArgList().functionArg().size();
            } else if (at instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
                final FrostlakeParser.FunctionCallNamedArgsExprContext call =
                    (FrostlakeParser.FunctionCallNamedArgsExprContext) at;
                name = call.functionName();
                arguments = call.namedArgumentList().namedArgument().size();
            } else if (at instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
                final FrostlakeParser.FunctionCallMixedArgsExprContext call =
                    (FrostlakeParser.FunctionCallMixedArgsExprContext) at;
                name = call.functionName();
                arguments = call.expression().size() + call.namedArgument().size();
            } else {
                continue;
            }
            final Function called = sqlFunction(executor, name, arguments);
            if (called != null) {
                return called;
            }
        }
        return null;
    }

    /** The user's SQL function a call of so many arguments names, or null when it names another function or none. */
    private static Function sqlFunction(final QueryExecutor executor, final FrostlakeParser.FunctionNameContext name,
                                        final int arguments) {
        final String[] parts = name == null ? null : ParseTreeText.functionNameParts(name);
        final Catalog catalog = executor.getCatalog();
        if (parts == null || parts.length == 0 || parts.length > 3 || catalog.getCurrentDatabase() == null) {
            return null;
        }
        final String simple = parts[parts.length - 1];
        Schema schema = null;
        try {
            if (parts.length == 3) {
                schema = catalog.databaseExact(parts[0]).schemaExact(parts[1]);
            } else if (parts.length == 2) {
                schema = catalog.databaseExact(catalog.getCurrentDatabase()).schemaExact(parts[0]);
            } else {
                for (final Schema searched : executor.searchPathSchemas()) {
                    if (!searched.getFunctionOverloads(simple).isEmpty()) {
                        schema = searched;
                        break;
                    }
                }
            }
        } catch (final RuntimeException unresolved) {
            return null;
        }
        if (schema == null) {
            return null;
        }
        for (final Function overload : schema.getFunctionOverloads(simple)) {
            if (UdfInvoker.acceptsArgumentCount(overload, arguments)) {
                return overload.getUdfLanguage() == UdfLanguage.SQL ? overload : null;
            }
        }
        return null;
    }
}
