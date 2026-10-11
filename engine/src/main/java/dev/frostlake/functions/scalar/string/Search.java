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
 * SEARCH(data, query [, ANALYZER =&gt; '…'] [, SEARCH_MODE =&gt; 'OR'|'AND'|'PHRASE']) — full-text token search.
 * Both sides are cut into tokens by the analyzer; OR mode (the default) is TRUE when ANY query token occurs
 * among the data's, AND mode when ALL of them do, and PHRASE mode when they occur side by side in the query's
 * order. {@code data} may be a single value or — for the {@code SEARCH((col1, col2), …)} form — a tuple, which
 * matches across every element. A NULL data or query yields NULL; a query with no token yields FALSE.
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
 * <p>Named arguments reach the evaluation positionally: a trailing string equal to AND, OR or PHRASE selects
 * the mode, any other trailing string names an analyzer. The names are judged while the statement compiles (see
 * {@code ExpressionEvaluatorVisitor}); an unknown one that reaches here is refused in the same words.
 */
public class Search extends BuiltInFunction {

    /** The analyzers the account answers with; any other ANALYZER name is an "Object does not exist" error. */
    private static final Set<String> ANALYZERS = new HashSet<>(
        Arrays.asList("DEFAULT_ANALYZER", "UNICODE_ANALYZER", "NO_OP_ANALYZER"));

    /** The search modes the account takes. */
    private static final Set<String> MODES = new HashSet<>(Arrays.asList("OR", "AND", "PHRASE"));

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
     * the underscore run together; a point or an apostrophe between two letters joins them, and a point between
     * two digits; everything else — a blank, a hyphen, an at sign, a slash, a comma — ends the word.
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

    /** Whether the character at {@code at} stands between two letters or two digits it joins into one word. */
    private static boolean joinsItsNeighbours(final String text, final int at) {
        if (at == 0 || at + 1 >= text.length()) {
            return false;
        }
        final char c = text.charAt(at);
        final char before = text.charAt(at - 1);
        final char after = text.charAt(at + 1);
        if (c == '.' && Character.isDigit(before) && Character.isDigit(after)) {
            return true;
        }
        return (c == '.' || c == '\'') && isLetterLike(before) && isLetterLike(after);
    }

    private static boolean isLetterLike(final char c) {
        return Character.isLetter(c) || c == '_';
    }
}
