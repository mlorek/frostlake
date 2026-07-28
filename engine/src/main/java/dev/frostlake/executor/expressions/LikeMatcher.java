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

package dev.frostlake.executor.expressions;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Pure SQL LIKE/ILIKE evaluation extracted from {@link ExpressionEvaluatorVisitor}: translate a LIKE
 * pattern (with an explicit ESCAPE char) to a regex and match against it, caching the compiled Pattern.
 */
public final class LikeMatcher {

    // SQL LIKE/ILIKE compiles to a regex. String.matches() recompiles the regex on every call, so for a
    // constant pattern over a 500k-row scan that meant 500k pattern translations + 500k Pattern.compile
    // calls. The pattern is almost always constant across rows, so cache the compiled Pattern keyed by the
    // (already case-folded) pattern text. Bounded so a sweep of distinct-literal patterns can't grow it
    // without limit; the per-row path is a lock-free get and the rare miss evicts an arbitrary entry.
    private static final int LIKE_PATTERN_CACHE_CAPACITY = 1024;
    private static final Map<String, Pattern> LIKE_PATTERN_CACHE = new ConcurrentHashMap<>();

    private LikeMatcher() {
    }

    public static Object evaluateLike(final Object value, final Object pattern,
                                final BinaryOperator op, final char escapeChar) {
        // Three-valued logic: a NULL subject or NULL pattern makes the predicate UNKNOWN, not FALSE —
        // so NULL NOT LIKE 'x%' is NULL too (a WHERE still drops the row, but NOT no longer flips it to TRUE).
        if (value == null || pattern == null) {
            return null;
        }

        String valueStr = value.toString();
        String patternStr = pattern.toString();

        final boolean caseInsensitive = (op == BinaryOperator.ILIKE ||
                                         op == BinaryOperator.NOT_ILIKE);
        final boolean not = (op == BinaryOperator.NOT_LIKE ||
                            op == BinaryOperator.NOT_ILIKE);

        if (caseInsensitive) {
            valueStr = valueStr.toLowerCase();
            patternStr = patternStr.toLowerCase();
        }

        final boolean matches = likePattern(patternStr, escapeChar).matcher(valueStr).matches();

        return not ? !matches : matches;
    }

    /** Compiled regex for a SQL LIKE pattern, cached by (escape char, pattern) — normally constant across rows. */
    private static Pattern likePattern(final String patternStr, final char escapeChar) {
        final String cacheKey = escapeChar + " " + patternStr;
        final Pattern existing = LIKE_PATTERN_CACHE.get(cacheKey);
        if (existing != null) {
            return existing;
        }
        // DOTALL: Snowflake's % and _ match ANY character including newlines — multi-line subjects
        // (multi-line payloads, log text) must match '%needle%' even when the needle sits between newlines.
        final Pattern compiled = Pattern.compile(likeToRegex(patternStr, escapeChar), Pattern.DOTALL);
        if (LIKE_PATTERN_CACHE.size() >= LIKE_PATTERN_CACHE_CAPACITY) {
            final Iterator<String> it = LIKE_PATTERN_CACHE.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        LIKE_PATTERN_CACHE.put(cacheKey, compiled);
        return compiled;
    }

    /**
     * Translate a SQL LIKE pattern to an equivalent regex ({@code %}→{@code .*}, {@code _}→{@code .}),
     * honoring {@code escapeChar}: the escape before {@code %}, {@code _} or itself makes that character
     * literal; before any other character it is kept literally (legacy behavior for the default '\').
     */
    private static String likeToRegex(final String patternStr, final char escapeChar) {
        final StringBuilder regex = new StringBuilder();
        for (int i = 0; i < patternStr.length(); i++) {
            final char c = patternStr.charAt(i);
            if (c == escapeChar && i + 1 < patternStr.length()) {
                final char next = patternStr.charAt(i + 1);
                if (next == '%' || next == '_' || next == escapeChar) {
                    appendLiteral(regex, next);
                    i++; // consume the escaped character
                } else {
                    appendLiteral(regex, c); // escape before an ordinary char: keep it as a literal
                }
            } else if (c == '%') {
                regex.append(".*");
            } else if (c == '_') {
                regex.append(".");
            } else {
                appendLiteral(regex, c);
            }
        }
        return regex.toString();
    }

    /** Append {@code c} to the regex, backslash-escaping it when it is a regex metacharacter. */
    private static void appendLiteral(final StringBuilder regex, final char c) {
        if ("[](){}+*?.^$|\\".indexOf(c) >= 0) {
            regex.append('\\');
        }
        regex.append(c);
    }
}
