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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * SEARCH(data, query [, ANALYZER =&gt; '…'] [, SEARCH_MODE =&gt; 'OR'|'AND']) — full-text token search,
 * matching Snowflake's semantics with the default analyzer: both sides tokenize on non-alphanumeric
 * boundaries, case-insensitively; OR mode (the default) is TRUE when ANY query token occurs among the
 * data tokens, AND mode when ALL of them do. {@code data} may be a single value or — for the
 * {@code SEARCH((col1, col2), …)} form — a tuple, which matches across every element. A NULL data or
 * query yields NULL; an empty token set yields FALSE.
 *
 * <p>Named arguments reach the evaluation positionally: a trailing string equal to AND/OR selects the
 * mode, any other trailing string names an analyzer — which must be one Snowflake actually ships
 * (tokenization is the default analyzer's either way).
 */
public class Search extends BuiltInFunction {

    /** The analyzers Snowflake ships; any other ANALYZER name is an "Object does not exist" error. */
    private static final Set<String> ANALYZERS = new HashSet<>(
        Arrays.asList("DEFAULT_ANALYZER", "UNICODE_ANALYZER", "NO_OP_ANALYZER"));

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

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.size() < 2 || args.get(0) == null || args.get(1) == null) {
            return null;
        }
        boolean andMode = false;
        for (int i = 2; i < args.size(); i++) {
            final Object extra = args.get(i);
            if (extra == null) {
                continue;
            }
            final String text = extra.toString().trim();
            if ("AND".equalsIgnoreCase(text)) {
                andMode = true;
            } else if ("OR".equalsIgnoreCase(text)) {
                andMode = false;
            } else if (!ANALYZERS.contains(text.toUpperCase())) {
                // Any other value is an analyzer NAME, and Snowflake ships exactly three. Live-verified
                // on a real account: DEFAULT_ANALYZER / UNICODE_ANALYZER / NO_OP_ANALYZER
                // all work, while ANALYZER => 'PATTERN_ANALYZER' and ANALYZER => 'BOGUS_ANALYZER' both
                // fail "Object 'PATTERN_ANALYZER' does not exist or not authorized."
                throw new RuntimeException(SqlCompilationError.doesNotExist("Object", text));
            }
        }

        final Set<String> dataTokens = new HashSet<>();
        final Object data = args.get(0);
        if (data instanceof List) {
            for (final Object element : (List<?>) data) {
                if (element != null) {
                    tokenize(element.toString(), dataTokens);
                }
            }
        } else {
            tokenize(data.toString(), dataTokens);
        }

        final Set<String> queryTokens = new HashSet<>();
        tokenize(args.get(1).toString(), queryTokens);
        if (queryTokens.isEmpty()) {
            return Boolean.FALSE;
        }

        if (andMode) {
            for (final String token : queryTokens) {
                if (!dataTokens.contains(token)) {
                    return Boolean.FALSE;
                }
            }
            return Boolean.TRUE;
        }
        for (final String token : queryTokens) {
            if (dataTokens.contains(token)) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    /** Split on non-alphanumeric boundaries, lowercased — the default analyzer's tokenization. */
    private static void tokenize(final String text, final Set<String> into) {
        for (final String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty()) {
                into.add(token);
            }
        }
    }
}
