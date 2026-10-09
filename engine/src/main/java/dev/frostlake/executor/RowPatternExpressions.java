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
import dev.frostlake.functions.window.WindowFunctionNames;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Writes the expressions of a MATCH_RECOGNIZE clause as the window expressions that answer them, read off their
 * parse trees. A definition is evaluated over the clause's whole partition before any match exists, so its
 * navigation reads the partition: {@code LAG(v)} is {@code LAG(v) OVER (PARTITION BY … ORDER BY …)}. A measure is
 * evaluated over the rows of one match, in match order, to the current row under RUNNING and to the last under
 * FINAL: {@code FIRST(v)} is {@code FIRST_VALUE(v) OVER (PARTITION BY match ORDER BY row …)}, and a column
 * predicated by a symbol reads the rows the match read as that symbol alone.
 *
 * <p>What the account refuses in either place is refused here in its words (live-verified): a qualified name,
 * PREV, FIRST, CLASSIFIER and MATCH_NUMBER in a definition, an aggregate there with no frame of its own, RUNNING
 * under ONE ROW PER MATCH, a symbol the pattern does not hold, two symbols in one call, and a symbol where the
 * semantics do not take one.
 */
final class RowPatternExpressions {

    /** The hidden columns of the matched rows a measure is evaluated over. */
    static final String ROW = "MATCH$ROW";
    static final String MATCH = "MATCH$ID";
    static final String PLACE = "MATCH$SEQ";
    static final String NUMBER = "MATCH$MN";
    static final String SEQUENCE = "MATCH$MSQ";
    static final String CLASSIFIER = "MATCH$CL";
    static final String EMPTY = "MATCH$EMPTY";

    private static final String WHOLE = "ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING";
    private static final String SO_FAR = "ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW";
    private static final String IN_MATCH = "PARTITION BY " + MATCH + " ORDER BY " + PLACE;

    private final FunctionRegistry functions;
    private final Set<String> symbols;
    /** The window a definition's navigation reads: the clause's own PARTITION BY and ORDER BY. */
    private final String partitionWindow;
    private final boolean oneRow;

    /** What a column read with no symbol before it counts as among an argument's symbols. */
    private static final String NO_SYMBOL = "";

    private boolean defining;
    private boolean finalNow;
    /**
     * The symbols the columns of the argument being written are predicated by — {@link #NO_SYMBOL} for a column
     * read with none — or null outside every call that takes one.
     */
    private Set<String> argumentSymbols;
    /** The names of the columns the clause reads; null until they are known. */
    private Set<String> columns;
    /** How many lambdas enclose what is being written: a lambda's parameters are names of its own. */
    private int lambdas;

    /**
     * @param functions       the registry that tells an aggregate from a scalar
     * @param symbols         the symbols the pattern names, upper-cased
     * @param partitionWindow the clause's PARTITION BY and ORDER BY as a window's text
     * @param oneRow          whether the clause answers ONE ROW PER MATCH, whose measures are FINAL
     */
    RowPatternExpressions(final FunctionRegistry functions, final Set<String> symbols, final String partitionWindow,
                          final boolean oneRow) {
        this.functions = functions;
        this.symbols = symbols;
        this.partitionWindow = partitionWindow;
        this.oneRow = oneRow;
    }

    /**
     * Tells the columns of the rows the clause reads. A measure's bare name is one of them or nothing: the word
     * of a date part is none, so {@code DATEDIFF(day, FIRST(d), LAST(d))} is refused for {@code DAY} where
     * {@code DATEDIFF('day', …)} is answered (live-verified).
     *
     * @param names the columns' names
     */
    void readColumns(final Set<String> names) {
        this.columns = names;
    }

    /**
     * A symbol's definition as an expression over the rows the clause reads.
     *
     * @param condition the definition's condition
     * @return its text
     */
    String definition(final ParserRuleContext condition) {
        defining = true;
        finalNow = false;
        argumentSymbols = null;
        final StringBuilder out = new StringBuilder();
        write(condition, out);
        return out.toString().trim();
    }

    /**
     * A measure as an expression over the matched rows.
     *
     * @param measure the measure's expression
     * @return its text
     */
    String measure(final ParserRuleContext measure) {
        defining = false;
        finalNow = oneRow;
        argumentSymbols = null;
        final StringBuilder out = new StringBuilder();
        write(measure, out);
        return out.toString().trim();
    }

    private void write(final ParseTree node, final StringBuilder out) {
        if (node instanceof TerminalNode) {
            if (((TerminalNode) node).getSymbol().getType() != Token.EOF) {
                out.append(node.getText()).append(' ');
            }
            return;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            out.append(ParseTreeText.getOriginalText((ParserRuleContext) node)).append(' ');
            return;
        }
        if (node instanceof FrostlakeParser.LambdaFunctionContext) {
            lambdas++;
            try {
                for (int i = 0; i < node.getChildCount(); i++) {
                    write(node.getChild(i), out);
                }
            } finally {
                lambdas--;
            }
            return;
        }
        if (node instanceof FrostlakeParser.RowPatternSemanticsExprContext) {
            semantics((FrostlakeParser.RowPatternSemanticsExprContext) node, out);
            return;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            final List<ParserRuleContext> arguments = new ArrayList<>();
            if (call.functionArgList() != null) {
                arguments.addAll(call.functionArgList().functionArg());
            }
            if (defining ? definedCall(call, nameOf(call.functionName()), arguments, call.overClause(), out)
                    : measuredCall(call, nameOf(call.functionName()), arguments, call.overClause(), out)) {
                return;
            }
        } else if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext call = (FrostlakeParser.FunctionCallStarExprContext) node;
            if (defining ? definedCall(call, nameOf(call.functionName()), null, null, out)
                    : measuredStar(call, nameOf(call.functionName()), out)) {
                return;
            }
        } else if (node instanceof FrostlakeParser.QualifiedNameExprContext
                && name((FrostlakeParser.QualifiedNameExprContext) node, out)) {
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            write(node.getChild(i), out);
        }
    }

    private static String nameOf(final FrostlakeParser.FunctionNameContext name) {
        return name.getText().toUpperCase(Locale.ROOT);
    }

    // ── FINAL and RUNNING ───────────────────────────────────────────────────────────

    private void semantics(final FrostlakeParser.RowPatternSemanticsExprContext node, final StringBuilder out) {
        final boolean wantsFinal = "FINAL".equalsIgnoreCase(node.ROW_PATTERN_SEMANTICS().getText());
        if (defining) {
            if (wantsFinal) {
                throw new RuntimeException(SqlCompilationError.of(
                    "FINAL semantics not allowed in MATCH_RECOGNIZE DEFINE clause."));
            }
            write(node.expression(), out);
            return;
        }
        if (!wantsFinal && oneRow) {
            throw new RuntimeException(SqlCompilationError.of(
                "RUNNING semantics not allowed in MATCH_RECOGNIZE MEASURES clause when using ONE ROW PER MATCH."));
        }
        final boolean before = finalNow;
        finalNow = wantsFinal;
        try {
            write(node.expression(), out);
        } finally {
            finalNow = before;
        }
    }

    // ── a definition ────────────────────────────────────────────────────────────────

    /** Writes a call of a definition; false where the call is an ordinary one, written as it stands. */
    private boolean definedCall(final ParserRuleContext call, final String name, final List<ParserRuleContext> arguments,
                                final FrostlakeParser.OverClauseContext over, final StringBuilder out) {
        if (over != null) {
            // A window of the definition's own carries a frame alone; the clause's partition and order are its.
            if (over.partitionByClause() != null) {
                throw new RuntimeException(placed(over.partitionByClause().expressionList(),
                    "PARTITION BY clause not allowed within MATCH_RECOGNIZE DEFINE clause."));
            }
            if (over.orderByClause() != null) {
                throw new RuntimeException(placed(over.orderByClause(),
                    "ORDER BY clause not allowed within MATCH_RECOGNIZE DEFINE clause."));
            }
            for (int i = 0; i < call.getChildCount(); i++) {
                if (call.getChild(i) != over) {
                    write(call.getChild(i), out);
                }
            }
            out.append("OVER ( ").append(partitionWindow).append(' ');
            if (over.windowFrame() != null) {
                out.append(ParseTreeText.getOriginalText(over.windowFrame())).append(' ');
            }
            out.append(") ");
            return true;
        }
        switch (name) {
            case "LAG":
            case "LEAD":
                writeCall(name, arguments, out);
                out.append("OVER ( ").append(partitionWindow).append(" ) ");
                return true;
            case "NEXT":
                writeCall("LEAD", arguments, out);
                out.append("OVER ( ").append(partitionWindow).append(" ) ");
                return true;
            case "FIRST_VALUE":
                writeCall(name, arguments, out);
                out.append("OVER ( ").append(partitionWindow).append(' ').append(WHOLE).append(" ) ");
                return true;
            case "LAST":
            case "LAST_VALUE":
                // The last row of a definition's frame is the row being judged.
                if (arguments == null || arguments.size() != 1) {
                    return false;
                }
                out.append("( ");
                write(arguments.get(0), out);
                out.append(") ");
                return true;
            case "PREV":
            case "FIRST":
            case "CLASSIFIER":
            case "MATCH_NUMBER":
            case "MATCH_SEQUENCE_NUMBER":
                throw new RuntimeException(SqlCompilationError.of("Unknown function " + name + "."));
            default:
                if (functions.hasAggregateFunction(name) || WindowFunctionNames.handles(name)) {
                    throw new RuntimeException(placed(call, "This function requires an explicit window frame"
                        + " definition (OVER clause without PARTITION BY and ORDER BY)."));
                }
                return false;
        }
    }

    // ── a measure ───────────────────────────────────────────────────────────────────

    /** Writes a call of a measure; false where the call is an ordinary one, written as it stands. */
    private boolean measuredCall(final FrostlakeParser.FunctionCallExprContext call, final String name,
                                 final List<ParserRuleContext> arguments,
                                 final FrostlakeParser.OverClauseContext over, final StringBuilder out) {
        if (over != null) {
            return false;
        }
        final boolean window = functions.hasAggregateFunction(name) || "FIRST".equals(name) || "LAST".equals(name)
            || "PREV".equals(name) || "NEXT".equals(name) || WindowFunctionNames.handles(name);
        if (window && argumentSymbols != null) {
            throw new RuntimeException(nested(call));
        }
        switch (name) {
            case "MATCH_NUMBER":
                out.append(NUMBER).append(' ');
                return true;
            case "MATCH_SEQUENCE_NUMBER":
                out.append(SEQUENCE).append(' ');
                return true;
            case "CLASSIFIER":
                if (!arguments.isEmpty()) {
                    return false;
                }
                out.append(CLASSIFIER).append(' ');
                return true;
            case "FIRST":
            case "FIRST_VALUE":
            case "LAST":
            case "LAST_VALUE":
                if (arguments.size() != 1) {
                    return false;
                }
                edge(call, name.startsWith("FIRST") ? "FIRST_VALUE" : "LAST_VALUE", arguments.get(0), out);
                return true;
            case "PREV":
            case "LAG":
            case "NEXT":
            case "LEAD":
                if (arguments.isEmpty()) {
                    return false;
                }
                neighbour(call, "PREV".equals(name) || "LAG".equals(name) ? "LAG" : "LEAD", arguments, out);
                return true;
            default:
                if (!functions.hasAggregateFunction(name)) {
                    return false;
                }
                aggregate(call, arguments, out);
                return true;
        }
    }

    /** FIRST or LAST of a value over the match: its first row, or the last the semantics reach. */
    private void edge(final ParserRuleContext call, final String function, final ParserRuleContext argument,
                      final StringBuilder out) {
        final StringBuilder value = new StringBuilder();
        final String symbol = symbolOf(call, argument, value);
        out.append("IFF ( ").append(EMPTY).append(" , NULL , ").append(function).append(" ( ");
        if (symbol == null) {
            out.append(value).append(") ");
        } else {
            if (!finalNow) {
                throw new RuntimeException(unsupported());
            }
            out.append("IFF ( ").append(CLASSIFIER).append(" = '").append(symbol).append("' , ").append(value)
                .append(", NULL ) ) IGNORE NULLS ");
        }
        out.append("OVER ( ").append(IN_MATCH).append(' ').append(finalNow || symbol != null ? WHOLE : SO_FAR)
            .append(" ) ) ");
    }

    /** PREV or NEXT of a value: the row so many places before or after, inside the match. */
    private void neighbour(final ParserRuleContext call, final String function, final List<ParserRuleContext> arguments,
                           final StringBuilder out) {
        final StringBuilder value = new StringBuilder();
        if (symbolOf(call, arguments.get(0), value) != null) {
            throw new RuntimeException(unsupported());
        }
        out.append("IFF ( ").append(EMPTY).append(" , NULL , ");
        if (finalNow && !oneRow) {
            // Read from the match's last row on every row of it: nothing follows that row, and the row so many
            // places before it is that many places from the end.
            final StringBuilder otherwise = new StringBuilder("NULL ");
            if (arguments.size() > 2) {
                otherwise.setLength(0);
                write(arguments.get(2), otherwise);
            }
            if ("LEAD".equals(function)) {
                out.append("IFF ( FALSE , ").append(value).append(", ").append(otherwise).append(") ) ");
                return;
            }
            final long back = arguments.size() > 1 ? placesOf(call, arguments.get(1)) : 1;
            out.append("IFF ( COUNT ( * ) OVER ( PARTITION BY ").append(MATCH).append(" ) > ").append(back)
                .append(" , NTH_VALUE ( ").append(value).append(", ").append(back + 1).append(" ) OVER ( PARTITION BY ")
                .append(MATCH).append(" ORDER BY ").append(PLACE).append(" DESC ").append(WHOLE).append(" ) , ")
                .append(otherwise).append(") ) ");
            return;
        }
        out.append(function).append(" ( ").append(value);
        for (int i = 1; i < arguments.size(); i++) {
            out.append(", ");
            write(arguments.get(i), out);
        }
        out.append(") OVER ( ").append(IN_MATCH).append(" ) ) ");
    }

    /** How many places a PREV steps back, written as a whole number. */
    private static long placesOf(final ParserRuleContext call, final ParserRuleContext places) {
        try {
            return Long.parseLong(places.getText());
        } catch (final NumberFormatException computed) {
            throw new RuntimeException(placed(call, "argument 2 to function "
                + ParseTreeText.getOriginalText(call).toUpperCase(Locale.ROOT) + " needs to be constant"));
        }
    }

    private static String nested(final ParserRuleContext call) {
        return SqlCompilationError.of("Window function [" + ParseTreeText.getOriginalText(call).toUpperCase(Locale.ROOT)
            + "] may not be nested inside another window function.");
    }

    private static String unsupported() {
        return "Unsupported feature 'this window function and semantic combination does currently not support"
            + " pattern variable predicates'.";
    }

    /** An aggregate over the match: every row of it under FINAL, the rows up to the current one under RUNNING. */
    private void aggregate(final FrostlakeParser.FunctionCallExprContext call, final List<ParserRuleContext> arguments,
                           final StringBuilder out) {
        final List<String> values = new ArrayList<>();
        final List<Boolean> predicated = new ArrayList<>();
        final Set<String> read = new LinkedHashSet<>();
        for (final ParserRuleContext argument : arguments) {
            final StringBuilder value = new StringBuilder();
            writeArgument(argument, value, read);
            values.add(value.toString());
            predicated.add(Boolean.valueOf(reads(argument)));
        }
        final String symbol = read.isEmpty() || read.contains(NO_SYMBOL) ? null : read.iterator().next();
        final String name = nameOf(call.functionName());
        out.append("IFF ( ").append(EMPTY).append(" , ").append("COUNT".equals(name) ? "0" : "NULL").append(" , ");
        // The call as written — its quantifier, its null treatment and its WITHIN GROUP — around the new arguments.
        for (int i = 0; i < call.getChildCount(); i++) {
            final ParseTree child = call.getChild(i);
            if (child != call.functionArgList()) {
                write(child, out);
                continue;
            }
            for (int a = 0; a < values.size(); a++) {
                if (a > 0) {
                    out.append(", ");
                }
                if (symbol != null && predicated.get(a).booleanValue()) {
                    out.append("IFF ( ").append(CLASSIFIER).append(" = '").append(symbol).append("' , ")
                        .append(values.get(a)).append(", NULL ) ");
                } else {
                    out.append(values.get(a));
                }
            }
        }
        appendMatchWindow(out);
        out.append(") ");
    }

    /** COUNT(*) and COUNT(symbol.*) over the match. */
    private boolean measuredStar(final FrostlakeParser.FunctionCallStarExprContext call, final String name,
                                 final StringBuilder out) {
        if (!functions.hasAggregateFunction(name)) {
            return false;
        }
        if (argumentSymbols != null) {
            throw new RuntimeException(nested(call));
        }
        out.append("IFF ( ").append(EMPTY).append(" , ").append("COUNT".equals(name) ? "0" : "NULL").append(" , ");
        if (call.starQualifiedName() == null) {
            for (int i = 0; i < call.getChildCount(); i++) {
                write(call.getChild(i), out);
            }
        } else {
            final String[] parts = ParseTreeText.qualifiedNameParts(call.starQualifiedName());
            final String symbol = parts[0];
            if (parts.length != 1 || !symbols.contains(symbol)) {
                throw new RuntimeException(placed(call.starQualifiedName(),
                    "Pattern variable '" + symbol + "' does not occur in the pattern."));
            }
            out.append(name).append(" ( IFF ( ").append(CLASSIFIER).append(" = '").append(symbol)
                .append("' , 1 , NULL ) ) ");
        }
        appendMatchWindow(out);
        out.append(") ");
        return true;
    }

    private void appendMatchWindow(final StringBuilder out) {
        if (finalNow) {
            out.append("OVER ( PARTITION BY ").append(MATCH).append(" ) ");
        } else {
            out.append("OVER ( ").append(IN_MATCH).append(' ').append(SO_FAR).append(" ) ");
        }
    }

    /**
     * Writes a call's argument and answers the one symbol its columns are predicated by, or null when none is.
     */
    private String symbolOf(final ParserRuleContext call, final ParserRuleContext argument, final StringBuilder value) {
        final Set<String> read = new LinkedHashSet<>();
        writeArgument(argument, value, read);
        return read.isEmpty() || read.contains(NO_SYMBOL) ? null : read.iterator().next();
    }

    /** Whether an argument reads a column at all, outside the queries nested in it. */
    private boolean reads(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (reads(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Counts a column of the argument being written as predicated by a symbol, or by none: one call reads the rows
     * of one symbol, or of every symbol, never both.
     */
    private void predicatedBy(final String symbol, final ParserRuleContext reference) {
        if (!argumentSymbols.isEmpty() && !argumentSymbols.contains(symbol)) {
            throw new RuntimeException(placed(reference, "Illegal use of multiple pattern variables in window function."));
        }
        argumentSymbols.add(symbol);
    }

    private void writeArgument(final ParserRuleContext argument, final StringBuilder value, final Set<String> read) {
        final Set<String> before = argumentSymbols;
        argumentSymbols = read;
        try {
            write(argument, value);
        } finally {
            argumentSymbols = before;
        }
    }

    private void writeCall(final String name, final List<ParserRuleContext> arguments, final StringBuilder out) {
        out.append(name).append(" ( ");
        if (arguments != null) {
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                write(arguments.get(i), out);
            }
        }
        out.append(") ");
    }

    // ── names ───────────────────────────────────────────────────────────────────────

    /** Writes a qualified name; false where the name is a bare one, written as it stands. */
    private boolean name(final FrostlakeParser.QualifiedNameExprContext reference, final StringBuilder out) {
        final String[] parts = ParseTreeText.qualifiedNameParts(reference.qualifiedName());
        if (parts.length < 2) {
            if (defining || lambdas > 0 || columns == null) {
                return false;
            }
            if (!columns.contains(parts[0])) {
                throw new RuntimeException(placed(reference, "invalid identifier '" + parts[0] + "'"));
            }
            if (argumentSymbols != null) {
                predicatedBy(NO_SYMBOL, reference);
            }
            return false;
        }
        if (defining) {
            throw new RuntimeException("Unsupported feature 'Correlated pattern definitions'.");
        }
        // The qualifier is read as any identifier is — "up" is not the symbol UP — where the pattern's own
        // symbols are named without regard to case (live-verified).
        final String symbol = parts[0];
        final FrostlakeParser.QualifiedNameContext written = reference.qualifiedName();
        if (parts.length != 2 || written.namePart().size() != 1 || !symbols.contains(symbol)) {
            throw new RuntimeException(placed(reference,
                "Pattern variable '" + symbol + "' does not occur in the pattern."));
        }
        final String column = ParseTreeText.getOriginalText(written.namePart(0));
        if (argumentSymbols != null) {
            predicatedBy(symbol, reference);
            out.append(column).append(' ');
            return true;
        }
        // Outside every call the predicated column is its last value in the match: LAST(symbol.column).
        if (!finalNow) {
            throw new RuntimeException(
                "Unsupported feature 'pattern variable predicate for a column with RUNNING semantic'.");
        }
        out.append("IFF ( ").append(EMPTY).append(" , NULL , LAST_VALUE ( IFF ( ").append(CLASSIFIER).append(" = '")
            .append(symbol).append("' , ").append(column).append(" , NULL ) ) IGNORE NULLS OVER ( ").append(IN_MATCH)
            .append(' ').append(WHOLE).append(" ) ) ");
        return true;
    }

    private static String placed(final ParserRuleContext at, final String sentence) {
        final Token start = at.getStart();
        return SqlCompilationError.at(start.getLine(), start.getCharPositionInLine(), sentence);
    }
}
