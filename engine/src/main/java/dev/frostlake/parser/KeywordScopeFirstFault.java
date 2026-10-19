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
package dev.frostlake.parser;

import dev.frostlake.executor.SqlCompilationError;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Collections;
import java.util.List;

/**
 * Two statements whose first fault live names alone, where this grammar reads on into a later one (all
 * live-verified).
 *
 * <p>A SHOW listing's word after a keyword written alone as its scope: live gives the scope up at the word and
 * names nothing after it, a parenthesis later in the statement included — {@code SHOW TABLES IN USER x (y)} is 'x'
 * and {@code SHOW TABLES IN MATERIALIZED VIEW (x)} 'VIEW', where this parse reads the parenthesis as a query of its
 * own and names a token inside it. In a block the lines are KeywordScopeLines'.
 *
 * <p>A DESCRIBE's WITH after the object's name: live reads WITH as opening the statement's {@code name = value}
 * properties and names the first token that does not fit — {@code DESCRIBE TABLE t WITH x AS (SELECT 1) SELECT 1}
 * is 'AS', and {@code … WITH x} and {@code … WITH} the end of the text — where this parse refuses the WITH.
 */
public final class KeywordScopeFirstFault {

    private KeywordScopeFirstFault() {
    }

    /**
     * Refuse the text for the one line live names, when its first fault is one of the two.
     *
     * @param sql      the script's source text
     * @param listener the parse's error listener
     */
    public static void requireBefore(final String sql, final CommonTokenStream parsed,
                                     final SyntaxErrorListener listener) {
        if (sql == null) {
            return;
        }
        // A WITH after a DESCRIBE's name opens a query of its own to this grammar, refused for its separator or for
        // its own fault.
        final Token with = describeWith(parsed);
        final Token describeNamed = with == null ? null : describeWithFault(parsed, with);
        if (describeNamed != null) {
            final String line = SyntaxErrorListener.sentence(describeNamed);
            throw new SqlSyntaxException(SqlCompilationError.of(line), Collections.singletonList(line), sql);
        }
        if (!listener.hasErrors()) {
            return;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final FrostlakeParser parser = new FrostlakeParser(tokens);
        ColdPrediction.arm(parser);
        parser.removeErrorListeners();
        final Token[] fault = new Token[1];
        final ParserRuleContext[] where = new ParserRuleContext[1];
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                if (fault[0] == null && offendingSymbol instanceof Token) {
                    fault[0] = (Token) offendingSymbol;
                    where[0] = e == null ? null : (ParserRuleContext) e.getCtx();
                }
            }
        });
        parser.sqlScript();
        tokens.fill();
        final Token refused = fault[0];
        if (refused == null || refused.getType() == Token.EOF || inBlock(where[0])) {
            return;
        }
        if (!opensWith(tokens, refused, FrostlakeLexer.SHOW, FrostlakeLexer.SHOW)
                || !ShowScopeWords.refusedAfterScopeWord(tokens, refused) || !parenthesisLater(tokens, refused)) {
            return;
        }
        final String line = SyntaxErrorListener.sentence(refused);
        throw new SqlSyntaxException(SqlCompilationError.of(line), Collections.singletonList(line), sql);
    }

    /** Whether a context stands in a block, whose statements' lines are read otherwise. */
    private static boolean inBlock(final ParseTree context) {
        for (ParseTree node = context; node != null; node = node.getParent()) {
            if (node instanceof FrostlakeParser.BeginEndBlockContext) {
                return true;
            }
        }
        return false;
    }

    /** Whether a parenthesis opens after the refused word before the statement ends. */
    private static boolean parenthesisLater(final CommonTokenStream tokens, final Token refused) {
        final List<Token> all = tokens.getTokens();
        for (int i = refused.getTokenIndex(); i < all.size(); i++) {
            final int type = all.get(i).getType();
            if (type == FrostlakeLexer.SEMI || type == Token.EOF) {
                return false;
            }
            if (type == FrostlakeLexer.LPAREN && all.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return true;
            }
        }
        return false;
    }

    /**
     * The token a DESCRIBE refused at a WITH after its name names, its properties read from the WITH: the token
     * where a property's name or its '=' should stand. Null for any other fault, and where the '=' stands.
     */
    private static Token describeWithFault(final CommonTokenStream tokens, final Token refused) {
        if (refused.getType() != FrostlakeLexer.WITH || !opensDescribe(tokens, refused)) {
            return null;
        }
        final Token name = nextSpoken(tokens, refused.getTokenIndex());
        if (name == null || name.getType() == Token.EOF || !SyntaxErrorListener.isNameLike(name)) {
            return name;
        }
        final Token equals = nextSpoken(tokens, name.getTokenIndex());
        return equals == null || equals.getType() == FrostlakeLexer.EQ ? null : equals;
    }

    /**
     * The WITH a text that opens with DESCRIBE or DESC writes straight after the statement's name, outside any
     * parenthesis and with no separator before it, or null: a type's WITH inside a signature,
     * {@code (TIMESTAMP WITH LOCAL TIME ZONE)}, is the type's own.
     */
    private static Token describeWith(final CommonTokenStream parsed) {
        final List<Token> all = parsed.getTokens();
        Token first = null;
        Token before = null;
        int depth = 0;
        for (final Token token : all) {
            if (token.getChannel() != Token.DEFAULT_CHANNEL || token.getType() == Token.EOF) {
                continue;
            }
            if (first == null) {
                first = token;
                if (first.getType() != FrostlakeLexer.DESCRIBE && first.getType() != FrostlakeLexer.DESC) {
                    return null;
                }
            } else if (token.getType() == FrostlakeLexer.SEMI) {
                return null;
            } else if (token.getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (token.getType() == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (depth == 0 && token.getType() == FrostlakeLexer.WITH && before != null
                    && SyntaxErrorListener.isNameLike(before)) {
                return token;
            }
            before = token;
        }
        return null;
    }

    /** Whether the statement holding the token opens with DESCRIBE or DESC. */
    private static boolean opensDescribe(final CommonTokenStream tokens, final Token inside) {
        return opensWith(tokens, inside, FrostlakeLexer.DESCRIBE, FrostlakeLexer.DESC);
    }

    /** Whether the statement holding the token opens with a token of either type. */
    private static boolean opensWith(final CommonTokenStream tokens, final Token inside, final int type,
                                     final int other) {
        final List<Token> all = tokens.getTokens();
        Token first = null;
        for (int i = inside.getTokenIndex() - 1; i >= 0; i--) {
            final Token token = all.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token.getType() == FrostlakeLexer.SEMI) {
                break;
            }
            first = token;
        }
        return first != null && (first.getType() == type || first.getType() == other);
    }

    private static Token nextSpoken(final CommonTokenStream tokens, final int index) {
        final List<Token> all = tokens.getTokens();
        for (int i = index + 1; i < all.size(); i++) {
            if (all.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return all.get(i);
            }
        }
        return null;
    }
}
