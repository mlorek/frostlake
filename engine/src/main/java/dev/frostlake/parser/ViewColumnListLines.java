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

import org.antlr.v4.runtime.Token;

import java.util.Collections;
import java.util.List;

/**
 * The line live reports for a view's column list followed by a comma, {@code CREATE VIEW v (b), CHANGE_TRACKING =
 * TRUE AS …}: the list's first name, where this grammar names the comma (live-verified, with one name or two, before
 * a property or before AS, for a secure view and one created IF NOT EXISTS alike).
 */
final class ViewColumnListLines {

    private ViewColumnListLines() {
    }

    /**
     * The line for a CREATE VIEW whose first fault is a comma right after its column list, or null.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the line, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0 && faultIndex >= 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 0 || type(spoken, fault) != FrostlakeLexer.COMMA) {
            return null;
        }
        int start = fault;
        while (start > 0 && type(spoken, start - 1) != FrostlakeLexer.SEMI) {
            start--;
        }
        int at = start;
        if (type(spoken, at) != FrostlakeLexer.CREATE) {
            return null;
        }
        at++;
        if (type(spoken, at) == FrostlakeLexer.OR && type(spoken, at + 1) == FrostlakeLexer.REPLACE) {
            at += 2;
        }
        if (type(spoken, at) == FrostlakeLexer.SECURE) {
            at++;
        }
        if (type(spoken, at) != FrostlakeLexer.VIEW) {
            return null;
        }
        at++;
        if (type(spoken, at) == FrostlakeLexer.IF && type(spoken, at + 1) == FrostlakeLexer.NOT
                && type(spoken, at + 2) == FrostlakeLexer.EXISTS) {
            at += 3;
        }
        at++;
        while (type(spoken, at) == FrostlakeLexer.DOT) {
            at += 2;
        }
        if (type(spoken, at) != FrostlakeLexer.LPAREN || fault < at + 3
                || type(spoken, fault - 1) != FrostlakeLexer.RPAREN) {
            return null;
        }
        for (int i = at + 1; i < fault - 1; i++) {
            if (type(spoken, i) == FrostlakeLexer.LPAREN || type(spoken, i) == FrostlakeLexer.RPAREN) {
                return null;
            }
        }
        return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(at + 1)));
    }

    private static int type(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }
}
