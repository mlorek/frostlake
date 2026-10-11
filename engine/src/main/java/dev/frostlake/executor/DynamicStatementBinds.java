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

import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.SqlCharStream;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.List;
import java.util.Set;

/**
 * The binds of a statement run from its text, EXECUTE IMMEDIATE's. Its USING list binds each {@code ?} in turn and
 * each {@code :n} by its number, and nothing else binds: the text is a statement of its own and reads none of the
 * block's variables, so a {@code :name}, and a {@code :n} the list does not reach, is a bind nothing sets —
 *
 * <pre>
 *   DECLARE s INT DEFAULT 5; BEGIN EXECUTE IMMEDIATE 'SELECT :s AS a'; … END;
 *       SQL compilation error: error line 1 at position 7
 *       Bind variable :s not set.
 * </pre>
 *
 * placed in the text (live-verified). A text that is itself a block compiles as one, binds nothing of the list,
 * and names what it does not declare in a block's own words.
 */
public final class DynamicStatementBinds {

    private DynamicStatementBinds() {
    }

    /**
     * Bind a USING list into the text and refuse what stays unbound.
     *
     * @param text    the statement's text
     * @param values  the USING values in order, empty for none
     * @param unbound the 1-based numbers of the values that bind nothing
     * @return the text with its binds written out
     */
    public static String bind(final String text, final List<Object> values, final Set<Integer> unbound) {
        if (isBlock(text)) {
            // A block binds nothing of a USING list: its ? is an unnamed bind and its :1 a name nothing declares,
            // each refused as the block compiles (live-verified).
            return text;
        }
        requireSet(text, values.size(), unbound);
        return values.isEmpty() ? text : JdbcMarshaling.bindUsing(text, values, unbound);
    }

    /**
     * Refuse the first bind of a text that a USING list of {@code values} values leaves unset, placed in the text as
     * written: a cursor's query is held to this too, when it opens.
     *
     * @param text    the statement's text
     * @param values  how many values the USING list gives
     * @param unbound the 1-based numbers of the values that bind nothing
     */
    static void requireSet(final String text, final int values, final Set<Integer> unbound) {
        final Token unset = firstUnset(text, values, unbound);
        if (unset != null) {
            // The colon stands right before a name; before a number a blank may stand between them.
            final int back = text.lastIndexOf(':', unset.getStartIndex());
            final int column = unset.getCharPositionInLine() - (unset.getStartIndex() - back);
            throw new RuntimeException(SqlCompilationError.at(unset.getLine(), column,
                "Bind variable :" + unset.getText() + " not set."));
        }
    }

    /** The first bind of the text that the USING list leaves unset: a name, or a number it does not reach. */
    private static Token firstUnset(final String text, final int values, final Set<Integer> unbound) {
        String rest = text;
        Token bind = BindVariableSubstitutor.firstBind(rest, true);
        while (bind != null && bind.getType() == FrostlakeLexer.INTEGER_LITERAL
                && isBound(bind.getText(), values, unbound)) {
            // A bound number is written out of the copy that is searched, blank for blank, so places stay put.
            final int colon = rest.lastIndexOf(':', bind.getStartIndex());
            final StringBuilder blanked = new StringBuilder(rest);
            for (int i = colon; i <= bind.getStopIndex(); i++) {
                blanked.setCharAt(i, ' ');
            }
            rest = blanked.toString();
            bind = BindVariableSubstitutor.firstBind(rest, true);
        }
        return bind;
    }

    private static boolean isBound(final String digits, final int values, final Set<Integer> unbound) {
        if (digits.startsWith("0") || digits.length() > 6) {
            return false;
        }
        final int number = Integer.parseInt(digits);
        return number >= 1 && number <= values && !unbound.contains(number);
    }

    /** Whether a text opens as a scripting block, which compiles its own names. */
    private static boolean isBlock(final String text) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(text));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final int first = stream.LA(1);
        return first == FrostlakeLexer.BEGIN || first == FrostlakeLexer.DECLARE;
    }
}
