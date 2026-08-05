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

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/**
 * Parses an expression string into an {@link Expression} AST using the ANTLR grammar and
 * {@link ExpressionAstBuilder}. This is the replacement for the hand-rolled
 * {@code ExpressionParser}; callers fall back to that legacy parser when this throws (a
 * not-yet-ported construct, or input that is not a single complete expression).
 *
 * <p>Parsing is deliberately quiet: the default console error listeners are removed and a
 * {@link BailErrorStrategy} makes the parser throw on the first syntax error instead of
 * recovering and logging. A trailing-input check ensures the whole string was consumed, so a
 * partial parse falls back rather than silently truncating.
 */
public final class AntlrExpressionParser {

    private AntlrExpressionParser() {
    }

    public static Expression parse(final String expression) {
        return new ExpressionAstBuilder().build(parseTree(expression));
    }

    /**
     * The PARSE TREE {@link #parse(String)} builds its {@link Expression} from, for a caller that must
     * inspect the expression's syntax rather than evaluate it (the CREATE-time SQL-UDF body check reads
     * which functions a body calls). Throws exactly as {@code parse} does on input that is not one
     * complete expression.
     */
    public static FrostlakeParser.BooleanExprContext parseTree(final String expression) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(expression));
        lexer.removeErrorListeners();

        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final FrostlakeParser parser = new FrostlakeParser(tokens);
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());

        final FrostlakeParser.BooleanExprContext ctx = parser.booleanExpr();
        if (parser.getCurrentToken().getType() != Token.EOF) {
            throw new IllegalStateException("Trailing input after expression: " + expression);
        }
        return ctx;
    }

    /**
     * Where {@link #parse(String)} stops on an input it cannot turn into a single complete expression: the
     * token the parser was looking at when it gave up, which is the offending token for a malformed expression
     * and the first unconsumed token when the expression parsed but did not reach end of input (the EOF token
     * when the input is simply exhausted).
     *
     * <p>Diagnostics only — the routine-body compiler renders it as Snowflake's
     * {@code syntax error line L at position P unexpected 'TOK'}. Kept here so the knowledge of HOW an
     * expression is parsed (grammar entry rule, bail strategy) stays in one place.
     */
    public static Token failurePoint(final String expression) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(expression));
        lexer.removeErrorListeners();

        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final FrostlakeParser parser = new FrostlakeParser(tokens);
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());

        try {
            parser.booleanExpr();
        } catch (final RuntimeException e) {
            // Expected: this method is only called for input already known not to parse. The parser's
            // current token is exactly where it stopped, which is what we want to report.
        }
        return parser.getCurrentToken();
    }
}
