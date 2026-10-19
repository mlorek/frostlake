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

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * Where live refuses a SHOW listing whose WITH opens no privilege list, and a SHOW's or a DESCRIBE's name that ends
 * in a dot (all live-verified):
 *
 * <pre>
 *   SHOW TABLES WITH x AS (SELECT 1) SELECT 1                  'x': the WITH is read on, the word after it refused
 *   SHOW TABLES IN DATASET WITH x (SELECT 1)                   'x'
 *   SHOW FUNCTIONS IN WITH x y                                 'WITH', then 'x': a WITH is no scope
 *   SHOW TABLES IN WITH                                        'WITH', then the end of the statement
 *   SHOW TABLES IN WITH PRIVILEGES USAGE                       'WITH' alone
 *   SHOW TABLES IN DATASET .                                   the end of the statement: the dot is read on,
 *   DESCRIBE TABLE t1 .;                                       after a keyword as after any other name
 * </pre>
 */
final class ShowWithSyntax {

    private ShowWithSyntax() {
    }

    /**
     * The tokens live names when the parse's first fault stands in a SHOW listing at or after such a WITH, or at
     * the dot that ends a keyword scope; null for any other fault.
     *
     * @param stream   the parse's tokens
     * @param reported the token the parse's first fault names
     * @return the refused token, and the one stacked after it when there is one, or null
     */
    static List<Token> lines(final TokenStream stream, final Token reported) {
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
        final List<Token> statement = new ArrayList<>();
        int at = -1;
        for (int i = statementStart(stream, reported); i < stream.size(); i++) {
            final Token token = stream.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token == reported) {
                at = statement.size();
            }
            statement.add(token);
            if (token.getType() == FrostlakeLexer.SEMI || token.getType() == Token.EOF) {
                break;
            }
        }
        final int opener = statement.isEmpty() ? Token.EOF : statement.get(0).getType();
        if (at < 0 || opener != FrostlakeLexer.SHOW && opener != FrostlakeLexer.DESCRIBE
                && opener != FrostlakeLexer.DESC) {
            return null;
        }
        final Token ended = endAfterDots(statement, at);
        if (ended != null) {
            final List<Token> lines = new ArrayList<>();
            lines.add(ended);
            return lines;
        }
        if (opener != FrostlakeLexer.SHOW) {
            return null;
        }
        for (int i = 1; i <= at && i + 1 < statement.size(); i++) {
            if (statement.get(i).getType() != FrostlakeLexer.WITH
                    || statement.get(i - 1).getType() == FrostlakeLexer.STARTS) {
                continue;
            }
            final Token next = statement.get(i + 1);
            final List<Token> lines = new ArrayList<>();
            if (statement.get(i - 1).getType() == FrostlakeLexer.IN) {
                lines.add(statement.get(i));
                if (next.getType() != FrostlakeLexer.PRIVILEGES) {
                    lines.add(next);
                }
                return lines;
            }
            if (next.getType() == FrostlakeLexer.PRIVILEGES || next.getType() == FrostlakeLexer.SEMI
                    || next.getType() == Token.EOF || next == reported) {
                // A fault already at the word after the WITH is read as it stands.
                return null;
            }
            lines.add(next);
            return lines;
        }
        return null;
    }

    /**
     * The token that ends the statement — its semicolon, or the end of the input — when the fault is a dot written
     * after a name and nothing but dots follows it; null otherwise.
     */
    private static Token endAfterDots(final List<Token> statement, final int at) {
        if (statement.get(at).getType() != FrostlakeLexer.DOT || at < 1
                || !SyntaxErrorListener.isNameLike(statement.get(at - 1))
                    && statement.get(at - 1).getType() != FrostlakeLexer.QUOTED_IDENTIFIER
                || statement.get(at - 1).getType() == FrostlakeLexer.IN) {
            return null;
        }
        for (int i = at; i < statement.size(); i++) {
            final int type = statement.get(i).getType();
            if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return statement.get(i);
            }
            if (type != FrostlakeLexer.DOT) {
                return null;
            }
        }
        return null;
    }

    /** The index of the first token of the statement a token stands in. */
    private static int statementStart(final TokenStream stream, final Token token) {
        int start = token.getTokenIndex();
        for (int i = token.getTokenIndex() - 1; i >= 0; i--) {
            final Token read = stream.get(i);
            if (read.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (read.getType() == FrostlakeLexer.SEMI || read.getType() == FrostlakeLexer.BEGIN
                    || read.getType() == FrostlakeLexer.THEN || read.getType() == FrostlakeLexer.ELSE
                    || read.getType() == FrostlakeLexer.DO || read.getType() == FrostlakeLexer.LOOP
                    || read.getType() == FrostlakeLexer.REPEAT) {
                break;
            }
            start = i;
        }
        return start;
    }
}
