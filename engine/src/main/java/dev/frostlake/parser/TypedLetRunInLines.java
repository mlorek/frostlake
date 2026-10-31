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
import org.antlr.v4.runtime.atn.ATN;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines live reports for a block's LET that declares a type and no value, run into the next statement without a
 * semicolon: the statement's RETURN or LET, and the token after it, or after its semicolon where one comes next
 * (live-verified):
 *
 * <pre>
 *   BEGIN LET q INT RETURN q; END              'RETURN', then 'q'
 *   BEGIN LET q NUMBER(10, 2) RETURN q; END    'RETURN', then 'q'
 *   BEGIN LET q INT LET r INT; RETURN 1; END   'LET', then 'r'
 *   BEGIN LET q INT RETURN; END                'RETURN', then 'END'
 *   BEGIN LET q INT SELECT 1; END              'SELECT' alone, as the parse names it
 * </pre>
 */
final class TypedLetRunInLines {

    private TypedLetRunInLines() {
    }

    /**
     * The lines for a statement whose first fault is a RETURN or a LET right after a LET's type, or null.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0 && faultIndex >= 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 3 || type(spoken, fault) != FrostlakeLexer.RETURN && type(spoken, fault) != FrostlakeLexer.LET
                || !afterTypedLet(spoken, fault)) {
            return null;
        }
        final int next = type(spoken, fault + 1) == FrostlakeLexer.SEMI ? fault + 2 : fault + 1;
        if (type(spoken, next) == Token.EOF || type(spoken, next) == FrostlakeLexer.SEMI) {
            return null;
        }
        final List<String> lines = new ArrayList<String>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(next)));
        return lines;
    }

    /**
     * Whether the tokens before {@code fault} are {@code LET name type}, the type a word with its parameters in
     * brackets or without, and nothing else.
     */
    private static boolean afterTypedLet(final List<Token> spoken, final int fault) {
        int typeStart = fault - 1;
        if (type(spoken, typeStart) == FrostlakeLexer.RPAREN) {
            int depth = 0;
            for (int i = fault - 1; i >= 0; i--) {
                final int type = type(spoken, i);
                if (type == FrostlakeLexer.RPAREN) {
                    depth++;
                } else if (type == FrostlakeLexer.LPAREN && --depth == 0) {
                    typeStart = i - 1;
                    break;
                } else if (type == FrostlakeLexer.SEMI) {
                    return false;
                }
            }
            if (depth != 0) {
                return false;
            }
        }
        return typeStart >= 2 && isTypeWord(type(spoken, typeStart)) && isName(type(spoken, typeStart - 1))
            && type(spoken, typeStart - 2) == FrostlakeLexer.LET;
    }

    /** Whether a token of this type can begin a data type's name. */
    private static boolean isTypeWord(final int type) {
        final ATN atn = FrostlakeParser._ATN;
        return atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_dataTypeName]).contains(type);
    }

    /** Whether a token of this type can be a name. */
    private static boolean isName(final int type) {
        final ATN atn = FrostlakeParser._ATN;
        return atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(type);
    }

    private static int type(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }
}
