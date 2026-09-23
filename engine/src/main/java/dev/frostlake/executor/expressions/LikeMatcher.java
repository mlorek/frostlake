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

import dev.frostlake.functions.scalar.SharedFunctionHelpers;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Pure SQL LIKE/ILIKE evaluation extracted from {@link ExpressionEvaluatorVisitor}: translate a LIKE
 * pattern to a regex and match against it, caching the compiled Pattern.
 *
 * <p>A LIKE written without ESCAPE escapes nothing: {@code 'a_' LIKE 'a\_'} is FALSE, the backslash being an
 * ordinary character. With an escape character, the escape makes whatever follows it literal, an ordinary
 * character included, so {@code 'a' LIKE '!a' ESCAPE '!'} is TRUE.
 *
 * <p>An escape with nothing after it is a fault only where the pattern is matched in full. A case-sensitive
 * LIKE whose pattern is a literal run, optionally behind or before a run of {@code %}, is answered as the
 * comparison it amounts to (equality, a prefix, a suffix or containment), and there the dangling escape is
 * one more literal character: {@code 'ab!' LIKE '%b!' ESCAPE '!'} is TRUE. Every other pattern, every ILIKE,
 * and every pattern whose escape is {@code %} or {@code _} is matched in full and refused with {@value
 * #DANGLING_ESCAPE} (live-verified).
 */
public final class LikeMatcher {

    /** Live's sentence for a pattern that ends in its escape character. */
    public static final String DANGLING_ESCAPE = "Escape character at end of LIKE pattern";

    // SQL LIKE/ILIKE compiles to a regex. String.matches() recompiles the regex on every call, so for a
    // constant pattern over a 500k-row scan that meant 500k pattern translations + 500k Pattern.compile
    // calls. The pattern is almost always constant across rows, so cache the compiled Pattern keyed by the
    // (already case-folded) pattern text. Bounded so a sweep of distinct-literal patterns can't grow it
    // without limit; the per-row path is a lock-free get and the rare miss evicts an arbitrary entry.
    private static final int LIKE_PATTERN_CACHE_CAPACITY = 1024;
    private static final Map<String, Pattern> LIKE_PATTERN_CACHE = new ConcurrentHashMap<>();

    private LikeMatcher() {
    }

    /**
     * The text a LIKE operand is matched as: a DATE, TIME or timestamp by its display text, the text
     * {@code ||} gives it — live, {@code ts LIKE '% %'} is TRUE over {@code 2020-01-01 10:00:00.000}, and
     * {@code TIME(9)} matches {@code '10:00:00'} exactly. Anything else by its ordinary text.
     *
     * @param value the operand, not NULL
     * @return the text matched
     */
    public static String likeText(final Object value) {
        return SharedFunctionHelpers.isNativeTemporal(value) ? SharedFunctionHelpers.textOf(value)
            : value.toString();
    }

    /**
     * One LIKE, NOT LIKE, ILIKE or NOT ILIKE.
     *
     * @param value   the subject
     * @param pattern the pattern
     * @param op      the operator
     * @param escape  the escape character, or null for none
     * @return the answer, NULL for a NULL subject or pattern
     */
    public static Object evaluateLike(final Object value, final Object pattern,
                                      final BinaryOperator op, final Character escape) {
        // Three-valued logic: a NULL subject or NULL pattern makes the predicate UNKNOWN, not FALSE —
        // so NULL NOT LIKE 'x%' is NULL too (a WHERE still drops the row, but NOT no longer flips it to TRUE).
        if (value == null || pattern == null) {
            return null;
        }
        final boolean not = op == BinaryOperator.NOT_LIKE || op == BinaryOperator.NOT_ILIKE;
        final boolean caseInsensitive = op == BinaryOperator.ILIKE || op == BinaryOperator.NOT_ILIKE;
        final String subject = likeText(value);
        final String written = likeText(pattern);
        final boolean matches;
        if (caseInsensitive) {
            matches = matchesInFull(subject.toLowerCase(), written.toLowerCase(), escape);
        } else {
            final Boolean simple = matchesSimply(subject, written, escape);
            matches = simple != null ? simple.booleanValue() : matchesInFull(subject, written, escape);
        }
        return not ? !matches : matches;
    }

    /**
     * Whether {@code subject} matches one of {@code patterns} (ANY) or all of them (ALL), NULL patterns
     * skipped. The patterns are taken in the order live takes them, which decides whether a dangling escape
     * is ever reached: LIKE ALL matches each in full, in the order written, and stops at the first miss;
     * ILIKE ANY refuses a dangling escape in any of them before matching; LIKE ANY answers from its simple
     * patterns first and meets the others, all of them checked, only when none of those matched.
     *
     * @param subject         the subject, not NULL
     * @param patterns        the patterns, NULL entries included
     * @param caseInsensitive whether the predicate is ILIKE ANY
     * @param all             whether it is LIKE ALL
     * @param escape          the escape character, or null for none
     * @return the answer, or NULL when every pattern is NULL
     */
    public static Boolean evaluateLikeAnyAll(final String subject, final List<String> patterns,
                                             final boolean caseInsensitive, final boolean all,
                                             final Character escape) {
        boolean sawPattern = false;
        for (final String pattern : patterns) {
            sawPattern |= pattern != null;
        }
        if (!sawPattern) {
            return null;
        }
        if (all) {
            for (final String pattern : patterns) {
                if (pattern != null && !matchesInFull(subject, pattern, escape)) {
                    return Boolean.FALSE;
                }
            }
            return Boolean.TRUE;
        }
        if (caseInsensitive) {
            final String folded = subject.toLowerCase();
            for (final String pattern : patterns) {
                if (pattern != null) {
                    compiled(pattern.toLowerCase(), escape);
                }
            }
            for (final String pattern : patterns) {
                if (pattern != null && matchesInFull(folded, pattern.toLowerCase(), escape)) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        }
        boolean sawFull = false;
        for (final String pattern : patterns) {
            if (pattern == null) {
                continue;
            }
            final Boolean simple = matchesSimply(subject, pattern, escape);
            if (simple == null) {
                sawFull = true;
            } else if (simple.booleanValue()) {
                return Boolean.TRUE;
            }
        }
        if (!sawFull) {
            return Boolean.FALSE;
        }
        for (final String pattern : patterns) {
            if (pattern != null && matchesSimply(subject, pattern, escape) == null) {
                compiled(pattern, escape);
            }
        }
        for (final String pattern : patterns) {
            if (pattern != null && matchesSimply(subject, pattern, escape) == null
                    && matchesInFull(subject, pattern, escape)) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    private static boolean matchesInFull(final String subject, final String pattern, final Character escape) {
        return compiled(pattern, escape).matcher(subject).matches();
    }

    /**
     * The answer of a pattern that is a literal run, optionally behind or before a run of {@code %}, or null
     * for any other pattern: one with {@code _}, with {@code %} inside, made of {@code %} alone, or escaped by
     * {@code %} or {@code _}. An escape with nothing after it is kept as a literal character here.
     */
    private static Boolean matchesSimply(final String subject, final String pattern, final Character escape) {
        if (escape != null && (escape.charValue() == '%' || escape.charValue() == '_')) {
            return null;
        }
        final int length = pattern.length();
        int i = 0;
        int leading = 0;
        while (i < length && pattern.charAt(i) == '%') {
            leading++;
            i++;
        }
        final StringBuilder literal = new StringBuilder();
        int trailing = 0;
        while (i < length) {
            final char c = pattern.charAt(i);
            if (c == '%') {
                trailing++;
                i++;
                continue;
            }
            if (c == '_' || trailing > 0) {
                return null;
            }
            if (escape != null && c == escape.charValue() && i + 1 < length) {
                literal.append(pattern.charAt(i + 1));
                i += 2;
            } else {
                literal.append(c);
                i++;
            }
        }
        if (literal.length() == 0 && (leading > 0 || trailing > 0)) {
            return null;
        }
        final String text = literal.toString();
        if (leading > 0 && trailing > 0) {
            return Boolean.valueOf(subject.contains(text));
        }
        if (leading > 0) {
            return Boolean.valueOf(subject.endsWith(text));
        }
        return Boolean.valueOf(trailing > 0 ? subject.startsWith(text) : subject.equals(text));
    }

    /** Compiled regex for a SQL LIKE pattern, cached by (escape char, pattern) — normally constant across rows. */
    private static Pattern compiled(final String patternStr, final Character escape) {
        final String cacheKey = (escape == null ? "-" : "+" + escape) + " " + patternStr;
        final Pattern existing = LIKE_PATTERN_CACHE.get(cacheKey);
        if (existing != null) {
            return existing;
        }
        // DOTALL: Snowflake's % and _ match ANY character including newlines — multi-line subjects
        // (multi-line payloads, log text) must match '%needle%' even when the needle sits between newlines.
        final Pattern compiled = Pattern.compile(likeToRegex(patternStr, escape), Pattern.DOTALL);
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
     * Translate a SQL LIKE pattern to an equivalent regex ({@code %}→{@code .*}, {@code _}→{@code .}): the
     * escape character makes the character after it literal, whatever it is, and an escape with nothing after
     * it is refused.
     */
    private static String likeToRegex(final String patternStr, final Character escape) {
        final StringBuilder regex = new StringBuilder();
        for (int i = 0; i < patternStr.length(); i++) {
            final char c = patternStr.charAt(i);
            if (escape != null && c == escape.charValue()) {
                if (i + 1 == patternStr.length()) {
                    throw new RuntimeException(DANGLING_ESCAPE);
                }
                appendLiteral(regex, patternStr.charAt(i + 1));
                i++; // consume the escaped character
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
