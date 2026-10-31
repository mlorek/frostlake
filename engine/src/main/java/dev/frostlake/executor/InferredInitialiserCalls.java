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

import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionAstBuilder;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LambdaExpression;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.functions.SystemFunctionNames;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The calls of a declaration whose type the block infers from its value — {@code LET x := …}, and a DECLARE item
 * written with a value and no type — looked up as the block compiles, before any name the block reads is judged.
 * A call that names no function is the block's refusal, bare: on a branch that never runs, inside a loop or a
 * nested block, past every handler, and in a procedure's body when the procedure is called. The value's unknown
 * calls are named in one sentence, {@code Unknown functions NOSUCH_F, NOSUCH_G.}, and of several such
 * declarations the first written is refused; a call over a window whose function takes none is refused for its
 * kind. A declaration that names its type, an assignment and every other expression of the block leave their
 * calls to the statement that runs them, and a function the block itself creates is no function yet (all
 * live-verified).
 */
public final class InferredInitialiserCalls {

    private InferredInitialiserCalls() {
    }

    /**
     * Refuse the first declaration of the block whose inferred value calls a function that does not exist.
     *
     * @param block         the block, before any of it runs
     * @param queryExecutor the executor whose functions and catalog resolve the names, or null where there is none
     */
    public static void require(final FrostlakeParser.BeginEndBlockContext block, final QueryExecutor queryExecutor) {
        if (queryExecutor == null) {
            return;
        }
        final List<FrostlakeParser.BooleanExprContext> values = new ArrayList<FrostlakeParser.BooleanExprContext>();
        collect(block, values);
        if (values.isEmpty()) {
            return;
        }
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(null, queryExecutor.getFunctionRegistry(),
            queryExecutor.getCatalog(), queryExecutor);
        final OverCallRules windows = new OverCallRules(queryExecutor.getFunctionRegistry(), null);
        for (final FrostlakeParser.BooleanExprContext value : values) {
            final Expression parsed;
            try {
                parsed = new ExpressionAstBuilder().visit(value);
            } catch (final RuntimeException unreadable) {
                // A value this builder cannot read is refused where the declaration is compiled.
                continue;
            }
            if (parsed == null) {
                continue;
            }
            final List<FunctionCallExpression> unknown = new ArrayList<FunctionCallExpression>();
            parsed.accept(new AstPrinterVisitor() {
                @Override
                public String visitFunctionCall(final FunctionCallExpression expr) {
                    if (!evaluator.resolvesToAFunctionName(expr) || isUnknownSystemFunction(expr)) {
                        unknown.add(expr);
                    }
                    return super.visitFunctionCall(expr);
                }

                @Override
                public String visitSubquery(final SubqueryExpression expr) {
                    // A subquery compiles as a query of its own, with the declaration.
                    return "";
                }

                @Override
                public String visitLambda(final LambdaExpression expr) {
                    return "";
                }
            });
            if (!unknown.isEmpty()) {
                throw new RuntimeException(SqlCompilationError.of(evaluator.unknownFunctionSentence(unknown)));
            }
            windows.rejectKinds(Collections.<ParseTree>singletonList(value));
        }
    }

    /**
     * Whether a call names a SYSTEM$ function by a name this engine does not implement. A logging statement's name is
     * left to the expression that calls it, which refuses it when it runs (live-verified).
     */
    private static boolean isUnknownSystemFunction(final FunctionCallExpression call) {
        final List<String> parts = call.getNameParts();
        final String name = parts == null ? call.getFunctionName() : parts.size() == 1 ? parts.get(0) : null;
        return name != null && name.toUpperCase(Locale.ROOT).startsWith("SYSTEM$")
            && !SystemFunctionNames.contains(name) && !SystemFunctionNames.isScriptingStatement(name);
    }

    /**
     * The values of the block's untyped declarations, in the order written: the block's own statements and those
     * of every branch, loop, handler and nested block, never a statement the block runs as SQL.
     */
    private static void collect(final ParseTree node, final List<FrostlakeParser.BooleanExprContext> values) {
        if (node instanceof FrostlakeParser.StatementContext
                && ((FrostlakeParser.StatementContext) node).proceduralStatement() == null
                || node instanceof FrostlakeParser.CursorSourceContext
                || node instanceof FrostlakeParser.ResultSetSourceContext) {
            return;
        }
        if (node instanceof FrostlakeParser.LetStatementContext) {
            final FrostlakeParser.LetStatementContext let = (FrostlakeParser.LetStatementContext) node;
            if (let.dataTypeName() == null && let.booleanExpr() != null) {
                values.add(let.booleanExpr());
            }
            return;
        }
        if (node instanceof FrostlakeParser.UntypedDeclarationItemContext) {
            final FrostlakeParser.BooleanExprContext value =
                ((FrostlakeParser.UntypedDeclarationItemContext) node).booleanExpr();
            if (value != null) {
                values.add(value);
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), values);
        }
    }
}
