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
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.LambdaExpression;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.SubqueryExpression;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The arguments of a table procedure called as a FROM source, compiled with the statement before the procedure is
 * matched to them (all live-verified):
 *
 * <pre>
 *   TABLE(qa(nosuchcol)), TABLE(qa($1))     invalid identifier 'NOSUCHCOL' where the name stands: no relation is
 *                                           in scope for it, and every argument's names come first
 *   TABLE(qa(ABS(1, 2)))                    too many arguments for function [ABS(1, 2)] where the call stands
 *   t1, TABLE(qa(t1.c)), t1, TABLE(qa(c))   Invalid argument T1.C for procedure in FROM clause, at the argument,
 *                                           once the arguments' types fit the procedure
 * </pre>
 *
 * A column of a relation written before the call is no value such a call may take: it is refused as an invalid
 * argument after the types are matched, the bare name spelled with its relation.
 */
final class TableProcedureArguments {

    private TableProcedureArguments() {
    }

    /**
     * Refuse the first name of the arguments that no relation answers, then the first call among them its own
     * compilation refuses.
     *
     * @param arguments the arguments as written, positional or the values of named ones
     * @param typer     the evaluator whose functions compile the calls
     * @param lateral   the columns of the relations written before the call, by name, or null for none
     */
    static void compile(final List<? extends ParserRuleContext> arguments, final ExpressionEvaluator typer,
                        final Map<String, Object> lateral) {
        final List<Expression> parsed = new ArrayList<>();
        for (final ParserRuleContext argument : arguments) {
            final Expression expression;
            try {
                expression = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(argument));
            } catch (final RuntimeException unread) {
                parsed.add(null);
                continue;
            }
            parsed.add(expression);
            for (final ColumnReferenceExpression reference : columnsOf(expression)) {
                if (!isLateral(reference, lateral) && reference.getPosition() != null) {
                    final SourcePosition at = placed(argument, reference.getPosition());
                    throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                        "invalid identifier '" + spelled(reference) + "'"));
                }
            }
        }
        for (int i = 0; i < arguments.size(); i++) {
            if (parsed.get(i) == null || !columnsOf(parsed.get(i)).isEmpty()) {
                continue;
            }
            final Token start = arguments.get(i).getStart();
            final SourcePosition displaced = ExpressionSource.beginNested(
                new SourcePosition(start.getLine(), start.getCharPositionInLine()));
            try {
                typer.validateStrict(parsed.get(i));
            } catch (final RuntimeException refused) {
                if (SqlCompilationError.isCompilationError(refused.getMessage())
                        && refused.getMessage().contains(" arguments for function [")) {
                    throw refused;
                }
            } finally {
                ExpressionSource.end(displaced);
            }
        }
    }

    /**
     * Refuse the first argument that reads a column of a relation written before the call.
     *
     * @param arguments the arguments as written, positional or the values of named ones
     * @param lateral   the columns of the relations written before the call, by name, or null for none
     */
    static void refuseLateral(final List<? extends ParserRuleContext> arguments, final Map<String, Object> lateral) {
        if (lateral == null || lateral.isEmpty()) {
            return;
        }
        for (final ParserRuleContext argument : arguments) {
            final Expression expression;
            try {
                expression = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(argument));
            } catch (final RuntimeException unread) {
                continue;
            }
            for (final ColumnReferenceExpression reference : columnsOf(expression)) {
                if (isLateral(reference, lateral)) {
                    final Token start = argument.getStart();
                    final int[] shown = LeadingCommentOffset.rebase(start.getLine(), start.getCharPositionInLine());
                    throw new RuntimeException(SqlCompilationError.PREFIX + " error line " + shown[0] + " at position "
                        + shown[1] + ": Invalid argument " + lateralName(reference, lateral)
                        + " for procedure in FROM clause");
                }
            }
        }
    }

    /** The columns an argument names, outside the queries and the lambdas in it, in the order written. */
    private static List<ColumnReferenceExpression> columnsOf(final Expression expression) {
        final List<ColumnReferenceExpression> columns = new ArrayList<>();
        expression.accept(new AstPrinterVisitor() {
            @Override
            public String visitColumnReference(final ColumnReferenceExpression expr) {
                columns.add(expr);
                return "";
            }

            @Override
            public String visitSubquery(final SubqueryExpression expr) {
                return "";
            }

            @Override
            public String visitLambda(final LambdaExpression expr) {
                return "";
            }
        });
        return columns;
    }

    private static boolean isLateral(final ColumnReferenceExpression reference, final Map<String, Object> lateral) {
        return lateral != null && reference.getPositionalOrdinal() == 0 && lateral.containsKey(key(reference));
    }

    private static String key(final ColumnReferenceExpression reference) {
        return (reference.isQualified() ? reference.getTableName() + "." : "") + reference.getColumnName();
    }

    /** A lateral column as the refusal names it: with its relation, a bare name with the relation it was found in. */
    private static String lateralName(final ColumnReferenceExpression reference, final Map<String, Object> lateral) {
        if (reference.isQualified()) {
            return spelled(reference);
        }
        final String suffix = "." + reference.getColumnName();
        final TreeSet<String> qualified = new TreeSet<>();
        for (final String key : lateral.keySet()) {
            if (key.endsWith(suffix) && key.length() > suffix.length()) {
                qualified.add(key);
            }
        }
        return qualified.isEmpty() ? spelled(reference) : SqlIdentifiers.spellCanonicalPath(qualified.first());
    }

    /** A name as an invalid identifier names it: {@code $1}, or its parts each spelled as it resolves. */
    private static String spelled(final ColumnReferenceExpression reference) {
        if (reference.getPositionalOrdinal() > 0) {
            return "$" + reference.getPositionalOrdinal();
        }
        return (reference.isQualified() ? SqlIdentifiers.spellCanonicalPath(reference.getTableName()) + "." : "")
            + SqlIdentifiers.spellCanonical(reference.getColumnName());
    }

    /** A place in an argument's own text as a place in the statement. */
    private static SourcePosition placed(final ParserRuleContext argument, final SourcePosition within) {
        final Token start = argument.getStart();
        final int line = start.getLine() + within.getLine() - 1;
        final int column = within.getLine() == 1 ? start.getCharPositionInLine() + within.getCharPositionInLine()
            : within.getCharPositionInLine();
        return new SourcePosition(line, column);
    }
}
