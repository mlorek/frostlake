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

import dev.frostlake.parser.FrostlakeLexer;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.List;

/**
 * Whether a projection expression NAMES a given column — read from the expression's own tokens, the
 * way {@link MaskingPolicyApplier} rewrites references, so a name inside a string literal and a
 * same-named function call are both left out of it.
 */
public final class ExpressionColumns {

    private ExpressionColumns() {
    }

    /**
     * @param expression the projection expression as written ({@code A}, {@code t.A}, {@code UPPER(A)})
     * @param columnName the column to look for
     * @return true when the expression references that column
     */
    public static boolean references(final String expression, final String columnName) {
        if (expression == null || columnName == null) {
            return false;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(expression));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Token> tokens = stream.getTokens();
        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (token.getType() == Token.EOF || !isName(token.getText(), columnName)) {
                continue;
            }
            // A function name is followed by '(' — UPPER(a) names the column a, not a column UPPER.
            final Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
            if (next != null && "(".equals(next.getText())) {
                continue;
            }
            // A qualified name's LAST part is the column: t.a names a, while a.t does not.
            if (next != null && ".".equals(next.getText())) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean isName(final String tokenText, final String columnName) {
        if (tokenText == null || tokenText.isEmpty()) {
            return false;
        }
        final char first = tokenText.charAt(0);
        if (first == '\'' || first == '$') {
            return false;
        }
        final String bare = first == '"' && tokenText.length() > 1 && tokenText.endsWith("\"")
            ? tokenText.substring(1, tokenText.length() - 1) : tokenText;
        return bare.equalsIgnoreCase(columnName);
    }
}
