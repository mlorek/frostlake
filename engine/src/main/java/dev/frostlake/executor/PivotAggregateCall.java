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
import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.window.WindowFunctionNames;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
 *   PIVOT (RANK(score) FOR …)             'RANK' is not a recognized aggregate function
 *   PIVOT (FIRST_VALUE(score) FOR s …)    syntax error … unexpected '('. / … unexpected 's'.
 *   PIVOT (SUM(t.score) FOR …)            invalid identifier 'T.SCORE'
 * </pre>
 *
 * FIRST_VALUE, LAST_VALUE, NTH_VALUE, LAG and LEAD are words of the account's grammar no call is read of here: the
 * syntax error stands at the call's parenthesis, and a second at the FOR column. An aggregate of two arguments is
 * refused in the call the pivot is rewritten into ({@link PivotRewrite}), once both columns are found.
 */
final class PivotAggregateCall {

    /** The window functions whose names are words the pivot reads no call of. */
    private static final Set<String> UNCALLED_WORDS = new HashSet<String>(Arrays.asList("FIRST_VALUE", "LAST_VALUE",
        "NTH_VALUE", "LAG", "LEAD"));

    /** The ordered-value aggregates, whose argument must be a constant. */
    private static final Set<String> ORDERED_VALUE = new HashSet<String>(Arrays.asList("PERCENTILE_CONT",
        "PERCENTILE_DISC"));

    private PivotAggregateCall() {
    }

    /**
     * The column the aggregate reads, after the call's shape and its function are judged.
     *
     * @param pivot     the clause
     * @param table     the relation pivoted
     * @param functions the registry telling an aggregate from another function
     * @return the column's canonical name
     */
    static String column(final FrostlakeParser.PivotClauseContext pivot, final Table table,
                         final FunctionRegistry functions) {
        final FrostlakeParser.AggregateFunctionContext call = pivot.aggregateFunction();
        final String written = call.functionName().getText();
        if (!written.startsWith("\"") && UNCALLED_WORDS.contains(written.toUpperCase(Locale.ROOT))) {
            final int[] paren = LeadingCommentOffset.rebase(call.LPAREN().getSymbol().getLine(),
                call.LPAREN().getSymbol().getCharPositionInLine());
            final Token forColumn = pivot.pivotColumn.getStart();
            final int[] at = LeadingCommentOffset.rebase(forColumn.getLine(), forColumn.getCharPositionInLine());
            throw new RuntimeException(SqlCompilationError.of("syntax error line " + paren[0] + " at position "
                + paren[1] + " unexpected '('.\nsyntax error line " + at[0] + " at position " + at[1]
                + " unexpected '" + forColumn.getText() + "'."));
        }
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
        // A window function is a function the pivot knows of, though it aggregates nothing (live-verified).
        final boolean known = functions.hasFunction(name) || functions.hasBuiltInAggregateFunction(name)
            || WindowFunctionNames.handles(name);
        if (written.startsWith("\"") || !known || "GROUPING".equals(name)) {
            throw new RuntimeException(SqlCompilationError.of("invalid function '" + written + "'"));
        }
        if (!functions.hasBuiltInAggregateFunction(name) || "ANY_VALUE".equals(name)) {
            throw new RuntimeException(SqlCompilationError.of("'" + name + "' is not a recognized aggregate function"));
        }
    }

    /**
     * Refuses an aggregate the pivot cannot call with its one column, in the call the pivot is rewritten into
     * (live-verified): one that takes two arguments is not given enough, and an ordered-value aggregate's argument
     * is no constant.
     *
     * <pre>
     *   PIVOT (CORR(n) FOR s IN ('x'))
     *     SQL compilation error: error line 0 at position -1
     *     not enough arguments for function [CORR(IFF('x' = FL2.S, FL2.N, SYSTEM$NULL_TO_FIXED(null)))], expected 2,
     *     got 1
     * </pre>
     *
     * @param pivot     the clause
     * @param table     the relation pivoted
     * @param functions the registry the aggregate is found in
     * @param relation  the relation's name as the rewrite spells it
     * @param column    the aggregated column's canonical name
     * @param forColumn the FOR column's canonical name
     */
    static void requireArity(final FrostlakeParser.PivotClauseContext pivot, final Table table,
                             final FunctionRegistry functions, final String relation, final String column,
                             final String forColumn) {
        final String name = pivot.aggregateFunction().functionName().getText().toUpperCase(Locale.ROOT);
        final AggregateFunction aggregate = functions.hasBuiltInAggregateFunction(name)
            ? functions.getAggregateFunction(name) : null;
        final boolean ordered = ORDERED_VALUE.contains(name);
        if (aggregate == null || !ordered && aggregate.getMinArgCount() < 2) {
            return;
        }
        final String argument = PivotRewrite.argument(pivot, relation, forColumn,
            table.getColumn(forColumn).getDataType(), column, table.getColumn(column).getDataType());
        if (ordered) {
            throw new RuntimeException(SqlCompilationError.of("argument 1 to function " + name
                + " needs to be constant, found '" + argument + "'"));
        }
        throw new RuntimeException(SqlCompilationError.PREFIX + " error line 0 at position -1\n"
            + "not enough arguments for function [" + name + "(" + argument + ")], expected "
            + aggregate.getMinArgCount() + ", got 1");
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
