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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A refusal of a statement whose USING values were written out in place of its binds, worded as live words it: a
 * bind that stands as a whole predicate is named as the text wrote it (all live-verified):
 *
 * <pre>
 *   WHERE ?   over a BOOLEAN TRUE   Invalid data type [VARCHAR(4)] for predicate [?]
 *   WHERE :1  over a BOOLEAN TRUE   Invalid data type [VARCHAR(4)] for predicate [:1]
 *   WHERE ?   over a NULL           Invalid data type [VARCHAR(134217728)] for predicate [?]
 * </pre>
 *
 * The same in a cursor's query opened with USING and in a text EXECUTE IMMEDIATE runs with USING.
 */
public final class UsingBindEcho {

    /** The predicate a type refusal echoes, at the end of its sentence. */
    private static final Pattern PREDICATE = Pattern.compile("\\] for predicate \\[(.*)\\]$", Pattern.DOTALL);

    /** Longest digit run a {@code :n} bind is numbered with. */
    private static final int MOST_DIGITS = 6;

    private UsingBindEcho() {
    }

    /**
     * The refusal with a bound value it echoes as a predicate named by its placeholder as written.
     *
     * @param refused the refusal the statement raised
     * @param text    the statement as written, its placeholders in place
     * @param values  the USING values in order
     * @param unbound the 1-based numbers of the values that bind nothing
     * @return the refusal as live words it
     */
    public static RuntimeException named(final RuntimeException refused, final String text, final List<Object> values,
                                         final Set<Integer> unbound) {
        final String message = refused.getMessage();
        if (message == null || text == null || values.isEmpty() || refused.getClass() != RuntimeException.class) {
            return refused;
        }
        final Matcher predicate = PREDICATE.matcher(message);
        if (!predicate.find()) {
            return refused;
        }
        final String placeholder = placeholderEchoed(predicate.group(1), text, values, unbound);
        if (placeholder == null) {
            return refused;
        }
        return new RuntimeException(message.substring(0, predicate.start(1)) + placeholder
            + message.substring(predicate.end(1)), refused.getCause());
    }

    /** The first placeholder as written whose value the echo prints, or null. */
    private static String placeholderEchoed(final String echoed, final String text, final List<Object> values,
                                            final Set<Integer> unbound) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(text));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Token> tokens = stream.getTokens();
        int question = 0;
        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            int number = 0;
            String spelled = null;
            if (token.getType() == FrostlakeLexer.QUESTION) {
                question++;
                number = question;
                spelled = "?";
            } else if (token.getType() == FrostlakeLexer.COLON && i + 1 < tokens.size()
                    && tokens.get(i + 1).getType() == FrostlakeLexer.INTEGER_LITERAL) {
                final String digits = tokens.get(i + 1).getText();
                if (!digits.startsWith("0") && digits.length() <= MOST_DIGITS) {
                    number = Integer.parseInt(digits);
                    spelled = ":" + digits;
                }
            }
            if (spelled != null && number >= 1 && number <= values.size() && !unbound.contains(number)
                    && echoed.equals(echoOf(values.get(number - 1)))) {
                return spelled;
            }
        }
        return null;
    }

    /** What a predicate's echo prints for a bound value: a NULL as the plan holds a text with no value. */
    private static String echoOf(final Object value) {
        return value == null ? "SYSTEM$NULL_TO_TEXT(null)" : JdbcMarshaling.formatLiteral(value);
    }
}
