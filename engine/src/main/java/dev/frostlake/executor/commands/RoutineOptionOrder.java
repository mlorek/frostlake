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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

/**
 * The order a routine's options must come in, a syntax rule on the account for functions and procedures alike.
 * The account reads them as an optional LANGUAGE, an optional null-handling clause, an optional volatility and
 * an optional MEMOIZABLE, in that order, then any number of properties ({@code name = value}: RUNTIME_VERSION,
 * HANDLER, COMMENT, IMPORTS, PACKAGES, ...) in any order, each of which may repeat. Everything after the first
 * property is read as another property, so an option that belongs earlier is refused where it stops reading as
 * one: its first word is taken as a property's name and the token after it is unexpected ({@code COMMENT = 'c'
 * STRICT AS} at AS, {@code STRICT LANGUAGE SQL} at SQL, {@code IMMUTABLE MEMOIZABLE MEMOIZABLE AS} at AS).
 * Three shapes read further (live-verified):
 * <ul>
 *   <li>IMMUTABLE right after a property is refused at itself and skipped, and reading goes on;</li>
 *   <li>a misplaced {@code CALLED ON NULL INPUT} is refused at ON, and INPUT is then read as a property's name,
 *       so the token after it is refused too;</li>
 *   <li>a misplaced {@code RETURNS NULL ON NULL INPUT} is refused at its first NULL and at ON.</li>
 * </ul>
 * The grammar keeps its order-free option list and this check runs before anything else judges the routine.
 */
final class RoutineOptionOrder {

    /** An option's place: LANGUAGE, null handling, volatility, MEMOIZABLE, then the properties. */
    private static final int LANGUAGE_PLACE = 1;
    private static final int NULL_HANDLING_PLACE = 2;
    private static final int VOLATILITY_PLACE = 3;
    private static final int MEMOIZABLE_PLACE = 4;
    private static final int PROPERTY_PLACE = 5;

    private RoutineOptionOrder() {
    }

    /**
     * Refuse a routine whose options are out of order, with the syntax errors live reports for it. A procedure's
     * EXECUTE AS comes last: an option or a second EXECUTE AS after it is refused at its own first word.
     */
    static void requireOrder(final FrostlakeParser.CreateStatementContext statement) {
        final List<FrostlakeParser.FunctionOptionContext> options = statement.functionOption();
        final List<FrostlakeParser.ExecuteAsClauseContext> executeAs = statement.executeAsClause();
        final int lastOptionIndex = executeAs.isEmpty() ? Integer.MAX_VALUE
            : executeAs.get(0).getStart().getTokenIndex();
        int reached = 0;
        FrostlakeParser.FunctionOptionContext previous = null;
        for (final FrostlakeParser.FunctionOptionContext option : options) {
            if (option.getStart().getTokenIndex() > lastOptionIndex) {
                break;
            }
            final int place = placeOf(option);
            if (place == PROPERTY_PLACE || place > reached) {
                reached = place;
                previous = option;
                continue;
            }
            final List<String> lines = new ArrayList<>();
            describeMisplaced(statement, option, previous, lines);
            throw new RuntimeException(SqlCompilationError.of(String.join("\n", lines)));
        }
        if (executeAs.isEmpty()) {
            return;
        }
        final int executeAsEnd = executeAs.get(0).getStop().getTokenIndex();
        Token after = executeAs.size() > 1 ? executeAs.get(1).getStart() : null;
        for (final FrostlakeParser.FunctionOptionContext option : options) {
            final Token start = option.getStart();
            if (start.getTokenIndex() > executeAsEnd
                    && (after == null || start.getTokenIndex() < after.getTokenIndex())) {
                after = start;
            }
        }
        if (after != null) {
            throw new RuntimeException(SqlCompilationError.of(unexpected(after)));
        }
    }

    private static int placeOf(final FrostlakeParser.FunctionOptionContext option) {
        if (option.languageClause() != null) {
            return LANGUAGE_PLACE;
        }
        if (option.nullHandlingClause() != null) {
            return NULL_HANDLING_PLACE;
        }
        if (option.volatilityClause() != null) {
            return VOLATILITY_PLACE;
        }
        if (option.MEMOIZABLE() != null) {
            return MEMOIZABLE_PLACE;
        }
        return PROPERTY_PLACE;
    }

    private static void describeMisplaced(final FrostlakeParser.CreateStatementContext statement,
                                          final FrostlakeParser.FunctionOptionContext option,
                                          final FrostlakeParser.FunctionOptionContext previous,
                                          final List<String> lines) {
        final List<Token> tokens = statementTokens(statement);
        final int first = indexOf(tokens, option.getStart());
        final boolean afterProperty = previous != null && placeOf(previous) == PROPERTY_PLACE;
        if (option.volatilityClause() != null && option.volatilityClause().IMMUTABLE() != null && afterProperty) {
            lines.add(unexpected(tokens.get(first)));
            return;
        }
        final FrostlakeParser.NullHandlingClauseContext nullHandling = option.nullHandlingClause();
        if (nullHandling != null && nullHandling.RETURNS() != null) {
            lines.add(unexpected(tokens.get(first + 1)));
            lines.add(unexpected(tokens.get(first + 2)));
            return;
        }
        // CALLED ON NULL INPUT is refused at ON, and INPUT is then read as a property's name.
        final int name = nullHandling != null && nullHandling.CALLED() != null ? first + 3 : first;
        if (name != first) {
            lines.add(unexpected(tokens.get(first + 1)));
        }
        afterName(statement, tokens, name + 1, lines);
    }

    /**
     * Refuse the token at {@code index}, which follows a word read as a property's name. An IMMUTABLE there is
     * skipped and the reading goes on: a property is read whole and any other word is a name again. An EXECUTE
     * AS there is read as EXECUTE and then a body, so the word after AS is refused too.
     */
    private static void afterName(final FrostlakeParser.CreateStatementContext statement,
                                  final List<Token> tokens, final int index, final List<String> lines) {
        int at = index;
        while (at < tokens.size()) {
            final Token unexpected = tokens.get(at);
            lines.add(unexpected(unexpected));
            if (unexpected.getType() == FrostlakeLexer.EXECUTE && at + 2 < tokens.size()) {
                lines.add(unexpected(tokens.get(at + 2)));
                return;
            }
            if (unexpected.getType() != FrostlakeLexer.IMMUTABLE) {
                return;
            }
            at++;
            while (at < tokens.size() && !endsOptions(tokens.get(at))) {
                final FrostlakeParser.FunctionOptionContext option = optionStartingAt(statement, tokens.get(at));
                if (option == null || placeOf(option) != PROPERTY_PLACE) {
                    break;
                }
                at = indexOf(tokens, option.getStop()) + 1;
            }
            if (at >= tokens.size() || endsOptions(tokens.get(at))) {
                return;
            }
            at++;
        }
    }

    /** Whether a token ends the option list: the body's AS, EXECUTE AS, the statement's end or the input's. */
    private static boolean endsOptions(final Token token) {
        final int type = token.getType();
        return type == Token.EOF || type == FrostlakeLexer.AS || type == FrostlakeLexer.SEMI
            || type == FrostlakeLexer.EXECUTE;
    }

    private static FrostlakeParser.FunctionOptionContext optionStartingAt(
            final FrostlakeParser.CreateStatementContext statement, final Token token) {
        for (final FrostlakeParser.FunctionOptionContext option : statement.functionOption()) {
            if (option.getStart().getTokenIndex() == token.getTokenIndex()) {
                return option;
            }
        }
        return null;
    }

    private static String unexpected(final Token token) {
        final String text = token.getType() == Token.EOF ? "<EOF>" : token.getText();
        return "syntax error line " + token.getLine() + " at position " + token.getCharPositionInLine()
            + " unexpected '" + text + "'.";
    }

    private static int indexOf(final List<Token> tokens, final Token token) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).getTokenIndex() == token.getTokenIndex()) {
                return i;
            }
        }
        throw new IllegalStateException("token outside its statement: " + token.getText());
    }

    /** The statement's tokens and what the script holds after it, the end of input included. */
    private static List<Token> statementTokens(final FrostlakeParser.CreateStatementContext statement) {
        ParseTree root = statement;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        final List<Token> tokens = new ArrayList<>();
        collect(root, tokens);
        return tokens;
    }

    private static void collect(final ParseTree node, final List<Token> tokens) {
        if (node instanceof TerminalNode) {
            tokens.add(((TerminalNode) node).getSymbol());
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), tokens);
        }
    }
}
