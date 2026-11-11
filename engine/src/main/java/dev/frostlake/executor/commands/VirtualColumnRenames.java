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

import dev.frostlake.parser.FrostlakeLexer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

/**
 * A virtual column's expression with a column it reads renamed (see VirtualColumnDefinitions#followRename): each
 * name in it that reads the column — a bare name, not a function's — is written as the column's new name, unquoted
 * where the name reads as an unquoted one and quoted otherwise, the rest of the text as written.
 */
final class VirtualColumnRenames {

    private VirtualColumnRenames() {
    }

    /**
     * @param written the expression as written
     * @param was     the column's name before
     * @param now     its name now
     * @return the expression reading the column by its new name
     */
    static String renamed(final String written, final String was, final String now) {
        final List<Token> tokens = new ArrayList<>();
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(written));
        lexer.removeErrorListeners();
        for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                tokens.add(token);
            }
        }
        final StringBuilder out = new StringBuilder();
        int copied = 0;
        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            final boolean called = i + 1 < tokens.size() && "(".equals(tokens.get(i + 1).getText());
            final boolean qualified = i > 0 && ".".equals(tokens.get(i - 1).getText());
            if (called || qualified || !was.equals(nameOf(token))) {
                continue;
            }
            out.append(written, copied, token.getStartIndex()).append(spelled(now));
            copied = token.getStopIndex() + 1;
        }
        return out.append(written.substring(copied)).toString();
    }

    /** The name a token reads as, or null when it reads as none: a quoted name as it is, a bare one upper-cased. */
    private static String nameOf(final Token token) {
        final String text = token.getText();
        if (token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER && text.length() >= 2) {
            return text.substring(1, text.length() - 1).replace("\"\"", "\"");
        }
        if (text.isEmpty() || !Character.isLetter(text.charAt(0)) && text.charAt(0) != '_') {
            return null;
        }
        for (int i = 1; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '$') {
                return null;
            }
        }
        return text.toUpperCase(Locale.ROOT);
    }

    /** A name as an expression writes it: bare where it reads as a bare one, quoted otherwise. */
    private static String spelled(final String name) {
        boolean bare = !name.isEmpty() && (Character.isUpperCase(name.charAt(0)) || name.charAt(0) == '_');
        for (int i = 1; bare && i < name.length(); i++) {
            final char c = name.charAt(i);
            bare = Character.isUpperCase(c) || Character.isDigit(c) || c == '_' || c == '$';
        }
        return bare ? name : "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
