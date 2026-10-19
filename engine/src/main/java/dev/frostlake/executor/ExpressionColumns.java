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
import dev.frostlake.parser.SqlCharStream;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.List;

/**
 * Whether a projection expression NAMES a given column — read from the expression's own tokens, the
 * way {@link MaskingPolicyApplier} rewrites references, so a name inside a string literal and a
 * same-named function call are both left out of it — and the expression with such a reference renamed.
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
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(expression));
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

    /**
     * Whether an expression reads a column, by the test {@link #renamed} applies: a string literal, a function's
     * name, a qualifier, a semi-structured path element and a type are not references.
     *
     * @param expression the expression as stored
     * @param columnName the column's canonical name
     * @return true when the expression reads the column
     */
    public static boolean reads(final String expression, final String columnName) {
        if (expression == null || columnName == null) {
            return false;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(expression));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Token> tokens = stream.getTokens();
        for (int i = 0; i < tokens.size(); i++) {
            if (namesColumn(tokens, i)
                    && columnName.equals(SqlIdentifiers.canonicalTextParts(tokens.get(i).getText())[0])) {
                return true;
            }
        }
        return false;
    }

    /**
     * The expression with every reference to a column renamed, read from its tokens as {@link #references} reads them:
     * a string literal, a function's name, a qualifier, a semi-structured path element after {@code :} and a type
     * after {@code ::} are left alone. Each reference is re-spelled as the new name and the rest of the text is kept
     * exactly as it was, so a clustering key, a CHECK or a search optimization target follows ALTER TABLE ... RENAME
     * COLUMN.
     *
     * @param expression the expression as stored
     * @param oldName    the column's canonical name before the rename
     * @param newName    its canonical name after it
     * @return the expression naming the column by its new name; the same text when it does not name it
     */
    public static String renamed(final String expression, final String oldName, final String newName) {
        if (expression == null || oldName == null || newName == null) {
            return expression;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(expression));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Token> tokens = stream.getTokens();
        final StringBuilder out = new StringBuilder(expression.length());
        int cursor = 0;
        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            if (!namesColumn(tokens, i) || !oldName.equals(SqlIdentifiers.canonicalTextParts(token.getText())[0])) {
                continue;
            }
            out.append(expression, cursor, token.getStartIndex());
            out.append(SqlIdentifiers.spellCanonicalEscaped(newName));
            cursor = token.getStopIndex() + 1;
        }
        if (cursor == 0) {
            return expression;
        }
        out.append(expression, cursor, expression.length());
        return out.toString();
    }

    /** Whether the token at {@code i} is a column reference: a name that is not a function, qualifier, path or type. */
    private static boolean namesColumn(final List<Token> tokens, final int i) {
        final Token token = tokens.get(i);
        if (token.getType() == Token.EOF
                || !(SqlTokens.isWord(token) || token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER)) {
            return false;
        }
        final Token next = i + 1 < tokens.size() ? tokens.get(i + 1) : null;
        if (next != null && (next.getType() == FrostlakeLexer.LPAREN || next.getType() == FrostlakeLexer.DOT)) {
            return false;
        }
        // The start of the dotted chain this name ends: t.a names the column a, and so does a alone.
        int start = i;
        while (start >= 2 && tokens.get(start - 1).getType() == FrostlakeLexer.DOT) {
            start -= 2;
        }
        final Token before = start > 0 ? tokens.get(start - 1) : null;
        return before == null
            || (before.getType() != FrostlakeLexer.COLON && before.getType() != FrostlakeLexer.DOUBLE_COLON);
    }

    /**
     * Whether a token names the column, read as any identifier is: an unquoted one by its upper-cased spelling, a
     * quoted one by exactly the name it spells. {@code "p"} is not the column {@code P}, which matching without
     * regard to case took it for.
     */
    private static boolean isName(final String tokenText, final String columnName) {
        if (tokenText == null || tokenText.isEmpty()) {
            return false;
        }
        final char first = tokenText.charAt(0);
        if (first == '\'' || first == '$') {
            return false;
        }
        return columnName.equals(SqlIdentifiers.canonicalTextParts(tokenText)[0]);
    }
}
