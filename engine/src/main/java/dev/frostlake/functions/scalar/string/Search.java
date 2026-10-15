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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.BooleanType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * SEARCH(data, query [, ANALYZER =&gt; '…'] [, SEARCH_MODE =&gt; 'OR'|'AND'|'PHRASE'|'EXACT']) — full-text token
 * search. Both sides are cut into tokens by the analyzer; OR mode (the default) is TRUE when ANY query token
 * occurs among the data's, AND mode when ALL of them do, and PHRASE mode when they occur side by side in the
 * query's order. {@code data} may be a single value or — for the {@code SEARCH((col1, col2), …)} form — a tuple,
 * which matches across every element. A NULL data or query yields NULL; a query with no token yields FALSE.
 *
 * <p>EXACT mode finds the query as it is written, the characters between its tokens included: the query must
 * stand in one element of the data, case apart, with no token running on before it or after it. So
 * {@code 'a, b'} is found in {@code 'x a, b!'} and not by {@code 'a,b'}, {@code 'a b'} or {@code ', b'}, a query's
 * trailing delimiter must be there too ({@code 'a,'} finds {@code 'a, b'} and {@code 'a '} does not find
 * {@code 'a b'}, the {@code b} running on), and an empty query finds a text with no token alone. Under the
 * NO_OP_ANALYZER the whole text is the one token, so the two must be equal; the UNICODE_ANALYZER does not take
 * the mode (all live-verified).
 *
 * <p>The analyzers, each live-verified:
 * <ul>
 *   <li>DEFAULT_ANALYZER cuts on everything that is no letter and no digit and folds case: {@code 'X-Y z.w'}
 *       holds x, y, z and w;</li>
 *   <li>UNICODE_ANALYZER cuts at the Unicode word boundaries and folds case, so what a point, an apostrophe or
 *       an underscore joins stays whole: {@code 'X-Y z.w'} holds x, y and z.w, {@code '1.5 kg'} 1.5 and kg,
 *       {@code 'user@example.com'} user and example.com, {@code 'a_b'} and {@code 'don''t'} one token each;</li>
 *   <li>NO_OP_ANALYZER does not cut at all: the whole text is one token, compared as it is written, so
 *       {@code 'x y'} is found by {@code 'x y'} alone, and not by {@code 'x'} or {@code 'X Y'}.</li>
 * </ul>
 *
 * <p>Named arguments reach the evaluation positionally: a trailing string equal to AND, OR, PHRASE or EXACT
 * selects the mode, any other trailing string names an analyzer. The names are judged while the statement compiles (see
 * {@code ExpressionEvaluatorVisitor}); an unknown one that reaches here is refused in the same words.
 */
public class Search extends BuiltInFunction {

    /** The analyzers the account answers with; any other ANALYZER name is an "Object does not exist" error. */
    private static final Set<String> ANALYZERS = new HashSet<>(
        Arrays.asList("DEFAULT_ANALYZER", "UNICODE_ANALYZER", "NO_OP_ANALYZER"));

    /** The search modes the account takes. */
    private static final Set<String> MODES = new HashSet<>(Arrays.asList("OR", "AND", "PHRASE", "EXACT"));

    private static final String EXACT = "EXACT";

    public Search() {
        super("SEARCH", BooleanType.BOOLEAN);
    }

    @Override
    public int getMinArgCount() {
        return 2;
    }

    @Override
    public int getMaxArgCount() {
        return 4;    // data, query, and up to two named options (ANALYZER, SEARCH_MODE)
    }

    /**
     * The refusal of an ANALYZER name, or null for one the account answers with. ENTITY_ANALYZER is a name it
     * knows and does not offer, {@code Object 'Analyzer type 'ENTITY_ANALYZER'' does not exist or not
     * authorized.}; any other is refused by the name as written, the empty one too. No privilege hint follows.
     *
     * @param name the analyzer's name as written
     * @return the refusal, or null
     */
    public static RuntimeException analyzerRefusal(final String name) {
        final String upper = name.toUpperCase(Locale.ROOT);
        if (ANALYZERS.contains(upper)) {
            return null;
        }
        return new RuntimeException(SqlCompilationError.of("Object '"
            + ("ENTITY_ANALYZER".equals(upper) ? "Analyzer type '" + upper + "'" : name)
            + "' does not exist or not authorized."));
    }

    /**
     * The refusal of a SEARCH_MODE, or null for one the account takes: {@code Object 'Search mode 'NOSUCH'' does
     * not exist or not authorized.}
     *
     * @param mode the mode as written
     * @return the refusal, or null
     */
    public static RuntimeException modeRefusal(final String mode) {
        return MODES.contains(mode.toUpperCase(Locale.ROOT)) ? null
            : new RuntimeException(SqlCompilationError.of("Object 'Search mode '" + mode
                + "'' does not exist or not authorized."));
    }

    /**
     * The refusal of EXACT mode under an analyzer that does not take it, or null: the Unicode analyzer's words
     * are no stretches of the text a query could be found in as written.
     *
     * @param mode     the mode as written, or null for none
     * @param analyzer the analyzer as written, or null for none
     * @return the refusal, or null
     */
    public static RuntimeException exactModeRefusal(final String mode, final String analyzer) {
        return mode != null && analyzer != null && EXACT.equalsIgnoreCase(mode.trim())
            && "UNICODE_ANALYZER".equalsIgnoreCase(analyzer.trim())
            ? new RuntimeException(SqlCompilationError.of(
                "Search mode 'EXACT' is not supported with the 'UNICODE_ANALYZER' analyzer."))
            : null;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.size() < 2 || args.get(0) == null || args.get(1) == null) {
            return null;
        }
        String mode = "OR";
        String analyzer = "DEFAULT_ANALYZER";
        for (int i = 2; i < args.size(); i++) {
            final Object extra = args.get(i);
            if (extra == null) {
                continue;
            }
            final String text = extra.toString().trim();
            if (MODES.contains(text.toUpperCase(Locale.ROOT))) {
                mode = text.toUpperCase(Locale.ROOT);
            } else {
                final RuntimeException refused = analyzerRefusal(text);
                if (refused != null) {
                    throw refused;
                }
                analyzer = text.toUpperCase(Locale.ROOT);
            }
        }

        if (EXACT.equals(mode)) {
            final RuntimeException refused = exactModeRefusal(mode, analyzer);
            if (refused != null) {
                throw refused;
            }
            return Boolean.valueOf(holdsAsWritten(args.get(0), args.get(1).toString(), analyzer));
        }
        final List<String> queryTokens = tokens(args.get(1).toString(), analyzer);
        if (queryTokens.isEmpty()) {
            return Boolean.FALSE;
        }
        final List<List<String>> dataTokens = new ArrayList<>();
        final Object data = args.get(0);
        if (data instanceof List) {
            for (final Object element : (List<?>) data) {
                if (element != null) {
                    dataTokens.add(tokens(element.toString(), analyzer));
                }
            }
        } else {
            dataTokens.add(tokens(data.toString(), analyzer));
        }
        if ("PHRASE".equals(mode)) {
            for (final List<String> element : dataTokens) {
                if (holdsInOrder(element, queryTokens)) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        }
        final Set<String> held = new HashSet<>();
        for (final List<String> element : dataTokens) {
            held.addAll(element);
        }
        final boolean all = "AND".equals(mode);
        for (final String token : queryTokens) {
            if (held.contains(token) != all) {
                return Boolean.valueOf(!all);
            }
        }
        return Boolean.valueOf(all);
    }

    /** Whether an element of the data holds the query as it is written (EXACT mode). */
    private static boolean holdsAsWritten(final Object data, final String query, final String analyzer) {
        if (data instanceof List) {
            for (final Object element : (List<?>) data) {
                if (element != null && holdsAsWritten(element.toString(), query, analyzer)) {
                    return true;
                }
            }
            return false;
        }
        return holdsAsWritten(data.toString(), query, analyzer);
    }

    /**
     * Whether a text holds the query as written: equal to it where the analyzer does not cut, and otherwise
     * standing in it — both in the analyzer's lower case — where no token runs on before or after. An empty query
     * is held by a text with no token in it.
     */
    private static boolean holdsAsWritten(final String text, final String query, final String analyzer) {
        if ("NO_OP_ANALYZER".equals(analyzer)) {
            return text.equals(query);
        }
        final String written = query.toLowerCase(Locale.ROOT);
        final String read = text.toLowerCase(Locale.ROOT);
        if (written.isEmpty()) {
            // Nothing is asked for, and a text that holds no token holds it.
            return tokens(text, analyzer).isEmpty();
        }
        for (int at = read.indexOf(written); at >= 0; at = read.indexOf(written, at + 1)) {
            final int end = at + written.length();
            if ((at == 0 || !isTokenCharacter(read.charAt(at - 1)))
                    && (end == read.length() || !isTokenCharacter(read.charAt(end)))) {
                return true;
            }
        }
        return false;
    }

    /** Whether the default analyzer keeps a character in a token: a letter or a number. */
    private static boolean isTokenCharacter(final char c) {
        final int type = Character.getType(c);
        return Character.isLetter(c) || type == Character.DECIMAL_DIGIT_NUMBER || type == Character.LETTER_NUMBER
            || type == Character.OTHER_NUMBER;
    }

    /** Whether {@code phrase} stands in {@code text}, token beside token. */
    private static boolean holdsInOrder(final List<String> text, final List<String> phrase) {
        for (int start = 0; start + phrase.size() <= text.size(); start++) {
            if (text.subList(start, start + phrase.size()).equals(phrase)) {
                return true;
            }
        }
        return false;
    }

    /** A text's tokens, in order, as {@code analyzer} cuts them. */
    private static List<String> tokens(final String text, final String analyzer) {
        final List<String> into = new ArrayList<>();
        if ("NO_OP_ANALYZER".equals(analyzer)) {
            if (!text.isEmpty()) {
                into.add(text);
            }
            return into;
        }
        if ("UNICODE_ANALYZER".equals(analyzer)) {
            unicodeWords(text.toLowerCase(Locale.ROOT), into);
            return into;
        }
        // Split on non-alphanumeric boundaries, lowercased — the default analyzer's tokenization.
        for (final String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty()) {
                into.add(token);
            }
        }
        return into;
    }

    /**
     * The words of a text as the Unicode word boundaries cut them, for the cases measured: letters, digits and
     * the underscore run together; a point, an apostrophe, a colon or a middle dot between two letters joins
     * them, and a point or a comma between two digits; everything else — a blank, a hyphen, an at sign, a slash,
     * a comma between letters, a colon between digits — ends the word.
     */
    private static void unicodeWords(final String text, final List<String> into) {
        final StringBuilder word = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (isWordCharacter(c) || joinsItsNeighbours(text, i)) {
                word.append(c);
            } else if (word.length() > 0) {
                into.add(word.toString());
                word.setLength(0);
            }
        }
        if (word.length() > 0) {
            into.add(word.toString());
        }
    }

    private static boolean isWordCharacter(final char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** The middle dot, which joins two letters as a colon does. */
    private static final char MIDDLE_DOT = '\u00b7';

    /** Whether the character at {@code at} stands between two letters or two digits it joins into one word. */
    private static boolean joinsItsNeighbours(final String text, final int at) {
        if (at == 0 || at + 1 >= text.length()) {
            return false;
        }
        final char c = text.charAt(at);
        final char before = text.charAt(at - 1);
        final char after = text.charAt(at + 1);
        if ((c == '.' || c == ',') && Character.isDigit(before) && Character.isDigit(after)) {
            return true;
        }
        return (c == '.' || c == '\'' || c == ':' || c == MIDDLE_DOT) && isLetterLike(before) && isLetterLike(after);
    }

    private static boolean isLetterLike(final char c) {
        return Character.isLetter(c) || c == '_';
    }
}
