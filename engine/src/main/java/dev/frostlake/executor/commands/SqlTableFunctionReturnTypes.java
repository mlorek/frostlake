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

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.expressions.SqlUdfBodyFrame;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;

/**
 * A SQL table function's query body judged against its declared {@code RETURNS TABLE (...)} columns at CREATE,
 * the table-valued counterpart of {@link RoutineReturnTypeChecker}'s scalar check (live-verified):
 * <ul>
 *   <li>the body must produce as many columns as are declared: {@code Mismatch between declared return signature
 *       column count (1) and actual column count (2)}, judged before any column's type;</li>
 *   <li>then each declared column, in order, must share its {@link ReturnTypeFamily} with the body's column in
 *       the same position, a TIME's or timestamp's precision included: {@code Declared return type 'FLOAT' for
 *       column 'X' is incompatible with actual return type 'NUMBER(1,0)'} — the declared type spelled in full,
 *       the column named as it resolves ({@code "x"} stays lower case), and no compilation-error prefix.</li>
 * </ul>
 * A body that closes its own frame before a semicolon is judged by what the frame holds, the statement a call runs
 * (see {@link SqlUdfBodyFrame}): the body {@code SELECT 'x' AS a);} under {@code RETURNS TABLE (a INT)} is refused
 * for its VARCHAR(1) column.
 * A body the engine cannot compile on its own — one reading the function's parameters — is left alone, as is one
 * reading the function itself through a TABLE source, which the account refuses as a cycle rather than by its
 * types; an untyped NULL is taken by any column, so the check only ever refuses what it has typed.
 */
final class SqlTableFunctionReturnTypes {

    private SqlTableFunctionReturnTypes() {
    }

    /**
     * @param routineName   the function's canonical name
     * @param declaredNames the declared columns' names, as they resolve
     * @param declaredTypes the declared columns' types, in the same order
     */
    static void check(final QueryExecutor queryExecutor, final String routineName, final List<String> declaredNames,
                      final List<DataType> declaredTypes, final String body, final Schema home) {
        if (queryExecutor == null || home == null || body == null || declaredTypes.isEmpty()) {
            return;
        }
        final String trimmed = SqlUdfBodyFrame.executableBody(body).trim();
        final FrostlakeParser.SqlScriptContext query = queryExecutor.queryStatementOf(trimmed);
        if (query == null || readsItself(query, routineName)) {
            return;
        }
        final List<TableColumn> shape;
        try {
            shape = queryExecutor.resolveQueryResultColumnsInScope(home.getDatabaseName(), home.getName(), trimmed);
        } catch (final RuntimeException uncompiled) {
            return;
        }
        if (shape == null) {
            return;
        }
        if (shape.size() != declaredTypes.size()) {
            throw new RuntimeException("Mismatch between declared return signature column count ("
                + declaredTypes.size() + ") and actual column count (" + shape.size() + ")");
        }
        for (int i = 0; i < declaredTypes.size(); i++) {
            final DataType declared = declaredTypes.get(i);
            final DataType actual = shape.get(i).getDataType();
            if (actual instanceof StringType && ((StringType) actual).getMaxLength() == 0) {
                // A NULL is taken by any column, and the engine types it as an empty text.
                continue;
            }
            final ReturnTypeFamily declaredFamily = ReturnTypeFamily.of(declared);
            final ReturnTypeFamily actualFamily = ReturnTypeFamily.of(actual);
            if (declaredFamily == null || actualFamily == null
                    || declaredFamily == actualFamily && !ReturnTypeFamily.precisionDiffers(declared, actual)) {
                continue;
            }
            throw new RuntimeException("Declared return type '" + spellDeclared(declared) + "' for column '"
                + declaredNames.get(i) + "' is incompatible with actual return type '"
                + RoutineReturnTypeChecker.spellColumn(actual) + "'");
        }
    }

    /** Whether a TABLE source under the node calls the function by its own name. */
    private static boolean readsItself(final ParseTree node, final String routineName) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && node.getParent() instanceof FrostlakeParser.TableSourceContext) {
            final FrostlakeParser.FunctionNameContext name = ((FrostlakeParser.FunctionCallExprContext) node).functionName();
            if (name != null && !name.identifier().isEmpty() && routineName.equals(
                    SqlIdentifiers.canonical(name.identifier().get(name.identifier().size() - 1)))) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsItself(node.getChild(i), routineName)) {
                return true;
            }
        }
        return false;
    }

    /** A declared column's type as the refusal spells it: a CHAR, TEXT or STRING column is a VARCHAR. */
    private static String spellDeclared(final DataType declared) {
        final String spelled = RoutineReturnTypeChecker.spell(declared);
        if (declared instanceof StringType) {
            return "VARCHAR" + spelled.substring(spelled.indexOf('('));
        }
        return spelled;
    }
}
