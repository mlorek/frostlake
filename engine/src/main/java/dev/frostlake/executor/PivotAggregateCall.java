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

import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The aggregate a PIVOT writes, judged as the account judges it (live-verified). It is one aggregate over one
 * column, written by its bare name: DISTINCT, an expression, a star or a second argument is a syntax error where
 * it is written; a name in quotes or one nothing answers to is no function; and a function that aggregates nothing
 * — or ANY_VALUE, alone among the aggregates — is not recognized.
 *
 * <pre>
 *   PIVOT (COUNT(DISTINCT score) FOR …)   syntax error … unexpected 'DISTINCT'.
 *   PIVOT (SUM(score + 1) FOR …)          syntax error … unexpected '+'.
 *   PIVOT (MAX(UPPER(note)) FOR …)        syntax error … unexpected '('.
 *   PIVOT ("SUM"(score) FOR …)            invalid function '"SUM"'
 *   PIVOT (nosuch_fn(score) FOR …)        invalid function 'nosuch_fn'
 *   PIVOT (UPPER(note) FOR …)             'UPPER' is not a recognized aggregate function
 *   PIVOT (ANY_VALUE(note) FOR …)         'ANY_VALUE' is not a recognized aggregate function
 *   PIVOT (SUM(t.score) FOR …)            invalid identifier 'T.SCORE'
 * </pre>
 */
final class PivotAggregateCall {

    private PivotAggregateCall() {
    }

    /**
     * The column the aggregate reads, after the call's shape and its function are judged.
     *
     * @param call      the aggregate as written
     * @param table     the relation pivoted
     * @param functions the registry telling an aggregate from another function
     * @return the column's canonical name
     */
    static String column(final FrostlakeParser.AggregateFunctionContext call, final Table table,
                         final FunctionRegistry functions) {
        if (call.DISTINCT() != null) {
            throw unexpected(call.DISTINCT().getSymbol());
        }
        final List<Token> argument = new ArrayList<>();
        collect(call.expression(), argument);
        final Expression parsed = AntlrExpressionParser.parse(ParseTreeText.getOriginalText(call.expression()));
        if (!(parsed instanceof ColumnReferenceExpression) || argument.size() > 1 && !isQualified(parsed)) {
            // A name is read and what follows it is not the closing parenthesis; anything else is no name at all.
            throw unexpected(argument.size() > 1 && startsName(argument.get(0)) ? argument.get(1) : argument.get(0));
        }
        requireAggregate(call, functions);
        final ColumnReferenceExpression column = (ColumnReferenceExpression) parsed;
        if (column.isQualified() || !table.hasColumnExactly(column.getColumnName())) {
            final Token at = argument.get(0);
            final int[] shown = {at.getLine(), at.getCharPositionInLine()};
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(shown[0], shown[1],
                String.valueOf(column)));
        }
        return column.getColumnName();
    }

    private static boolean isQualified(final Expression parsed) {
        return parsed instanceof ColumnReferenceExpression && ((ColumnReferenceExpression) parsed).isQualified();
    }

    /** Refuses a function that is none, and one that aggregates nothing. */
    private static void requireAggregate(final FrostlakeParser.AggregateFunctionContext call,
                                         final FunctionRegistry functions) {
        final String written = call.functionName().getText();
        final String name = written.toUpperCase(Locale.ROOT);
        // A user's aggregate function is no function PIVOT knows of (live-verified).
        final boolean known = functions.hasFunction(name) || functions.hasBuiltInAggregateFunction(name);
        if (written.startsWith("\"") || !known || "GROUPING".equals(name)) {
            throw new RuntimeException(SqlCompilationError.of("invalid function '" + written + "'"));
        }
        if (!functions.hasBuiltInAggregateFunction(name) || "ANY_VALUE".equals(name)) {
            throw new RuntimeException(SqlCompilationError.of("'" + name + "' is not a recognized aggregate function"));
        }
    }

    private static boolean startsName(final Token token) {
        final char first = token.getText().charAt(0);
        return Character.isLetter(first) || first == '_' || first == '"';
    }

    private static void collect(final ParseTree tree, final List<Token> tokens) {
        if (tree instanceof TerminalNode) {
            tokens.add(((TerminalNode) tree).getSymbol());
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            collect(tree.getChild(i), tokens);
        }
    }

    private static RuntimeException unexpected(final Token token) {
        final int[] shown = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
        return new RuntimeException(SqlCompilationError.of("syntax error line " + shown[0] + " at position "
            + shown[1] + " unexpected '" + token.getText() + "'."));
    }
}
