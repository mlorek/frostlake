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

import org.antlr.v4.runtime.Token;

/**
 * The lines the account reports for a PIVOT whose aggregate is no call of one column, {@code PIVOT (<aggregate>(col)
 * FOR …)}. The account's PIVOT reading takes an aggregate of one name; what it refuses, and how it reads on, depends
 * on the aggregate (all live-verified, over {@code PIVOT (… FOR subject IN ('Math', 'Art'))}):
 *
 * <pre>
 *   OBJECT_AGG(student, score), MAX(a, b)     the ',', then the call's ')'
 *   SUM(score + 1), AVG(score - 1)            the operator twice, then the pivot column after FOR
 *   SUM(score) + 1                            the operator, then the pivot column
 *   CAST(score), TRY_CAST(score AS INT)       the call's '(', then the pivot column
 *   IDENTIFIER(student)                       the name alone
 *   IDENTIFIER(student, x)                    the name, the ',' and the call's ')'
 *   IDENTIFIER('student')                     the FOR alone
 * </pre>
 */
final class PivotCallShapeLines {

    private PivotCallShapeLines() {
    }

    /**
     * The lines for a PIVOT aggregate of one of the shapes above, or the lines worked out so far for any other.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param faults the token indexes of the faults, in order, -1 for the end of input
     * @param lines  the lines worked out so far
     * @return the lines
     */
    static List<String> lines(final List<Token> spoken, final List<Integer> faults, final List<String> lines) {
        if (faults.isEmpty() || lines == null || lines.isEmpty()) {
            return lines;
        }
        final int fault = spokenIndex(spoken, faults.get(0).intValue());
        if (fault < 0) {
            return lines;
        }
        final int pivot = pivotBefore(spoken, fault);
        if (pivot < 0) {
            return lines;
        }
        final int name = pivot + 2;
        final int open = pivot + 3;
        final int close = StarModifierContextLines.closing(spoken, open);
        final int column = columnAfterFor(spoken, close);
        if (column < 0) {
            return lines;
        }
        final String called = spoken.get(name).getText();
        final List<Integer> at = new ArrayList<Integer>();
        if ("IDENTIFIER".equalsIgnoreCase(called)) {
            final boolean word = SyntaxErrorListener.isNameLike(spoken.get(open + 1));
            if (word && close == open + 2) {
                at.add(Integer.valueOf(open + 1));
            } else if (word && type(spoken, open + 2) == FrostlakeLexer.COMMA) {
                at.add(Integer.valueOf(open + 1));
                at.add(Integer.valueOf(open + 2));
                at.add(Integer.valueOf(close));
            } else if (type(spoken, open + 1) == FrostlakeLexer.STRING_LITERAL && close == open + 2) {
                at.add(Integer.valueOf(close + 1));
            }
        } else if (spoken.get(name).getType() == FrostlakeLexer.CAST
                || spoken.get(name).getType() == FrostlakeLexer.TRY_CAST) {
            at.add(Integer.valueOf(open));
            at.add(Integer.valueOf(column));
        } else if (fault > open && fault < close && spoken.get(fault).getType() == FrostlakeLexer.COMMA) {
            at.add(Integer.valueOf(fault));
            at.add(Integer.valueOf(close));
        } else if (fault > open && fault < close && isOperator(spoken.get(fault))) {
            at.add(Integer.valueOf(fault));
            at.add(Integer.valueOf(fault));
            at.add(Integer.valueOf(column));
        } else if (fault == close + 1 && isOperator(spoken.get(fault))) {
            at.add(Integer.valueOf(fault));
            at.add(Integer.valueOf(column));
        }
        if (at.isEmpty()) {
            return lines;
        }
        final List<String> told = new ArrayList<String>();
        for (final Integer index : at) {
            told.add(SyntaxErrorListener.sentence(spoken.get(index.intValue())));
        }
        return told;
    }

    /** The PIVOT whose aggregate holds or stands right before a fault: {@code PIVOT ( name (}; -1 for none. */
    private static int pivotBefore(final List<Token> spoken, final int fault) {
        for (int i = fault; i >= 0; i--) {
            if (spoken.get(i).getType() == FrostlakeLexer.PIVOT) {
                return type(spoken, i + 1) == FrostlakeLexer.LPAREN && i + 3 < spoken.size()
                    && type(spoken, i + 3) == FrostlakeLexer.LPAREN && fault >= i + 2 ? i : -1;
            }
        }
        return -1;
    }

    /** The pivot column, the name after the FOR that follows the aggregate's call; -1 when the shape is another. */
    private static int columnAfterFor(final List<Token> spoken, final int close) {
        for (int i = close + 1; i < spoken.size() && i <= close + 4; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.FOR) {
                return i + 1 < spoken.size() && SyntaxErrorListener.isNameLike(spoken.get(i + 1)) ? i + 1 : -1;
            }
        }
        return -1;
    }

    private static boolean isOperator(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR
            || type == FrostlakeLexer.SLASH;
    }

    private static int type(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }

    /** The index in {@code spoken} of the token with the stream index {@code tokenIndex}; -1 for the end of input. */
    private static int spokenIndex(final List<Token> spoken, final int tokenIndex) {
        if (tokenIndex < 0) {
            return -1;
        }
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == tokenIndex) {
                return i;
            }
        }
        return -1;
    }
}
