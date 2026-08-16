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

import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.aggregate.ConstantArgumentTexts;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The compile-time rules of the approximate summaries' constant arguments — APPROX_TOP_K's item and
 * counter limits, the counter limit of its accumulate / combine / estimate forms, and
 * APPROX_PERCENTILE_ESTIMATE's fraction — judged over the whole statement before a row is read, as
 * live judges them: an empty table is refused the same way. The sentences, all live-verified:
 *
 * <pre>
 *   APPROX_TOP_K(n, 0)          Invalid value [0] for function 'APPROX_TOP_K', parameter 1: Number of items must be a positive integer
 *   APPROX_TOP_K(n, 100001)     Invalid value [100001] for function 'APPROX_TOP_K', parameter 1: Number of items cannot be larger than 100000
 *   APPROX_TOP_K(n, NULL)       Invalid value [null] for function 'APPROX_TOP_K', parameter 1: Number of items must be a positive integer
 *   APPROX_TOP_K(n, 2, 0)       Invalid value [0] for function 'APPROX_TOP_K', parameter 2: Number of counters must be a positive integer
 *   APPROX_TOP_K(n, i)          argument 2 to function APPROX_TOP_K needs to be constant, found 'T.I'
 *   APPROX_TOP_K(n, 1.5)        Invalid value [CAST(1.5 AS NUMBER(18,0))] for function '{1}', parameter {2}: {3}
 *   APPROX_TOP_K(n, 'x')        Invalid value [TO_NUMBER('x', 18, 0)] for function '{1}', parameter {2}: {3}
 *   APPROX_TOP_K_ESTIMATE(s, k) Invalid value [T.K] for function '{1}', parameter {2}: {3}
 *   APPROX_PERCENTILE_ESTIMATE(s, 1.5)  Invalid value [1.5] for function 'APPROX_PERCENTILE_ESTIMATE', parameter 2: Percentile must be between 0 and 1 inclusive.
 *   APPROX_PERCENTILE_ESTIMATE(s, p)    argument 2 to function APPROX_PERCENTILE_ESTIMATE needs to be constant, found 'T.P'
 * </pre>
 *
 * <p>The three placeholder sentences are live's own — its message template left unfilled — and are
 * reproduced as measured. An integral constant passes in any spelling: {@code 2.0}, {@code 1e1},
 * {@code '2'}, {@code (2)}, {@code +2}. The accumulate, combine and estimate forms number their limit
 * parameter 1 and call it "Number of counters" even where it counts items; APPROX_TOP_K's own
 * counter limit is parameter 2. None of the sentences carries a position.
 */
final class ApproximateCallRules {

    private static final String TOP_K = "APPROX_TOP_K";
    private static final String TOP_K_ACCUMULATE = "APPROX_TOP_K_ACCUMULATE";
    private static final String TOP_K_COMBINE = "APPROX_TOP_K_COMBINE";
    private static final String TOP_K_ESTIMATE = "APPROX_TOP_K_ESTIMATE";
    private static final String PERCENTILE_ESTIMATE = "APPROX_PERCENTILE_ESTIMATE";

    /** The largest number of items or counters a call may ask for. */
    private static final BigDecimal LIMIT = new BigDecimal(100000);

    /** Live's unfilled template, which follows the bracketed value of a non-integral constant. */
    private static final String UNFILLED = "for function '{1}', parameter {2}: {3}";

    private static final String BARE_NAME = "[A-Za-z_$][A-Za-z_$0-9]*(\\.[A-Za-z_$][A-Za-z_$0-9]*)*";

    private final FunctionRegistry functionRegistry;
    private final Catalog catalog;

    ApproximateCallRules(final FunctionRegistry functionRegistry, final Catalog catalog) {
        this.functionRegistry = functionRegistry;
        this.catalog = catalog;
    }

    /**
     * Walks a statement's tree and refuses the first approximate-summary call whose constant
     * argument breaks a rule.
     *
     * @param node any parse-tree node; the walk covers the statement
     * @param table the leading relation, for echoing a column the way the plan names it
     * @param aliasToTable the FROM's alias map, or null
     * @param allTables every relation in the FROM, or null
     */
    void validate(final ParseTree node, final Table table, final Map<String, Table> aliasToTable,
                  final List<Table> allTables) {
        if (node == null) {
            return;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            judge((FrostlakeParser.FunctionCallExprContext) node, table, aliasToTable, allTables);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            validate(node.getChild(i), table, aliasToTable, allTables);
        }
    }

    private void judge(final FrostlakeParser.FunctionCallExprContext call, final Table table,
                       final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final String name = SqlIdentifiers.canonicalText(call.functionName().getText()).toUpperCase(Locale.ROOT);
        final List<FrostlakeParser.BooleanExprContext> args =
            ParseTreeText.functionBooleanArgs(call.functionArgList());
        switch (name) {
            case TOP_K:
                limit(name, args, 1, "1", "Number of items", true, table, aliasToTable, allTables);
                limit(name, args, 2, "2", "Number of counters", true, table, aliasToTable, allTables);
                break;
            case TOP_K_ACCUMULATE:
            case TOP_K_COMBINE:
                limit(name, args, 1, "1", "Number of counters", true, table, aliasToTable, allTables);
                break;
            case TOP_K_ESTIMATE:
                limit(name, args, 1, "1", "Number of counters", false, table, aliasToTable, allTables);
                break;
            case PERCENTILE_ESTIMATE:
                fraction(name, args, table, aliasToTable, allTables);
                break;
            default:
                break;
        }
    }

    /**
     * One item or counter limit: a positive integral constant of at most 100000.
     *
     * @param constantSentence whether a column refuses with the "needs to be constant" sentence
     *                         (the aggregates) or with the unfilled template (the estimate)
     */
    private void limit(final String name, final List<FrostlakeParser.BooleanExprContext> args, final int index,
                       final String parameter, final String noun, final boolean constantSentence,
                       final Table table, final Map<String, Table> aliasToTable, final List<Table> allTables) {
        if (args.size() <= index) {
            return;
        }
        final String written = ConstantArgumentTexts.unwrap(ParseTreeText.getOriginalText(args.get(index)));
        if ("NULL".equalsIgnoreCase(written)) {
            refuse(invalidValue("null", name, parameter, noun + " must be a positive integer"));
        }
        final BigDecimal value = ConstantArgumentTexts.numericConstant(written);
        final boolean quoted = ConstantArgumentTexts.isStringLiteral(written);
        if (value == null) {
            if (quoted) {
                refuse("Invalid value [TO_NUMBER(" + written + ", 18, 0)] " + UNFILLED);
            }
            final String echo = echo(args.get(index), written, table, aliasToTable, allTables);
            if (constantSentence) {
                refuse("argument " + (index + 1) + " to function " + name + " needs to be constant, found '"
                    + echo + "'");
            }
            refuse("Invalid value [" + echo + "] " + UNFILLED);
        }
        if (value.stripTrailingZeros().scale() > 0) {
            refuse(quoted
                ? "Invalid value [TO_NUMBER(" + written + ", 18, 0)] " + UNFILLED
                : "Invalid value [CAST(" + written + " AS NUMBER(18,0))] " + UNFILLED);
        }
        final String digits = value.stripTrailingZeros().toPlainString();
        if (value.signum() <= 0) {
            refuse(invalidValue(digits, name, parameter, noun + " must be a positive integer"));
        }
        if (value.compareTo(LIMIT) > 0) {
            refuse(invalidValue(digits, name, parameter, noun + " cannot be larger than 100000"));
        }
    }

    /** The estimate's fraction: a constant between 0 and 1 inclusive; a NULL is left to answer NULL. */
    private void fraction(final String name, final List<FrostlakeParser.BooleanExprContext> args,
                          final Table table, final Map<String, Table> aliasToTable, final List<Table> allTables) {
        if (args.size() < 2) {
            return;
        }
        final String written = ConstantArgumentTexts.unwrap(ParseTreeText.getOriginalText(args.get(1)));
        if ("NULL".equalsIgnoreCase(written)) {
            return;
        }
        final BigDecimal value = ConstantArgumentTexts.numericConstant(written);
        if (value == null) {
            if (ConstantArgumentTexts.isStringLiteral(written)) {
                return;
            }
            refuse("argument 2 to function " + name + " needs to be constant, found '"
                + echo(args.get(1), written, table, aliasToTable, allTables) + "'");
        }
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            refuse("Invalid value [" + written + "] for function '" + name
                + "', parameter 2: Percentile must be between 0 and 1 inclusive.");
        }
    }

    private static String invalidValue(final String echo, final String name, final String parameter,
                                       final String sentence) {
        return "Invalid value [" + echo + "] for function '" + name + "', parameter " + parameter + ": " + sentence;
    }

    /** A column is echoed as the plan resolves it, an expression as it was written. */
    private String echo(final FrostlakeParser.BooleanExprContext arg, final String written, final Table table,
                        final Map<String, Table> aliasToTable, final List<Table> allTables) {
        return written.matches(BARE_NAME)
            ? new PlanEcho(table, aliasToTable, allTables, functionRegistry, catalog).print(arg)
            : written;
    }

    private static void refuse(final String detail) {
        throw new RuntimeException(SqlCompilationError.of(detail));
    }
}
