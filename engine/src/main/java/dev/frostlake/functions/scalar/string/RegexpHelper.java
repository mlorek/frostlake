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

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Shared helpers for the REGEXP_* functions: translating Snowflake's {@code regex_parameters} flag string
 * to {@link java.util.regex.Pattern} flags, detecting the {@code e} (extract) flag, and converting a
 * Snowflake replacement string (backreferences {@code \0}–{@code \9}) to Java's replacement syntax.
 */
public final class RegexpHelper {

    private RegexpHelper() {
    }

    /**
     * Map Snowflake {@code regex_parameters} to {@code java.util.regex} flags: {@code i} → CASE_INSENSITIVE,
     * {@code m} → MULTILINE, {@code s} → DOTALL. {@code c} (case-sensitive, the default) and {@code e}
     * (handled by the caller) contribute no flag; unknown characters are ignored.
     */
    public static int toJavaFlags(final String parameters) {
        int flags = 0;
        if (parameters == null) {
            return flags;
        }
        for (int i = 0; i < parameters.length(); i++) {
            switch (parameters.charAt(i)) {
                case 'i':
                    flags |= Pattern.CASE_INSENSITIVE;
                    break;
                case 'm':
                    flags |= Pattern.MULTILINE;
                    break;
                case 's':
                    flags |= Pattern.DOTALL;
                    break;
                default:
                    break;
            }
        }
        return flags;
    }

    /** Whether the {@code e} (extract submatch) flag is present in the parameters string. */
    public static boolean hasExtract(final String parameters) {
        return parameters != null && parameters.indexOf('e') >= 0;
    }

    // Every REGEXP_* function compiles per evaluate(), i.e. per ROW, while the pattern and flags
    // are almost always query constants — the same problem LikeMatcher's pattern cache fixed.
    private static final int PATTERN_CACHE_CAPACITY = 512;
    private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();

    /** Compile a Snowflake pattern honoring the {@code i}/{@code m}/{@code s} flags in {@code parameters},
     *  cached by (flags, pattern) — normally constant across rows. */
    public static Pattern compile(final String pattern, final String parameters) {
        final String cacheKey = (parameters == null ? "" : parameters) + '\0' + pattern;
        final Pattern existing = PATTERN_CACHE.get(cacheKey);
        if (existing != null) {
            return existing;
        }
        final Pattern compiled = Pattern.compile(pattern, toJavaFlags(parameters));
        if (PATTERN_CACHE.size() >= PATTERN_CACHE_CAPACITY) {
            final Iterator<String> it = PATTERN_CACHE.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        PATTERN_CACHE.put(cacheKey, compiled);
        return compiled;
    }

    /**
     * Convert a Snowflake replacement string to a {@code Matcher.appendReplacement} replacement: a
     * backslash-digit ({@code \1}) becomes {@code $1}; {@code \\} a literal backslash; a bare {@code $}
     * or trailing {@code \} is escaped so it is treated literally.
     */
    public static String translateReplacement(final String replacement) {
        final StringBuilder out = new StringBuilder(replacement.length());
        int i = 0;
        while (i < replacement.length()) {
            final char c = replacement.charAt(i);
            if (c == '\\' && i + 1 < replacement.length()) {
                final char next = replacement.charAt(i + 1);
                if (next >= '0' && next <= '9') {
                    out.append('$').append(next);
                } else if (next == '\\') {
                    out.append("\\\\");
                } else if (next == '$') {
                    out.append("\\$");
                } else {
                    out.append(next);
                }
                i += 2;
            } else if (c == '\\') {
                out.append("\\\\");
                i++;
            } else if (c == '$') {
                out.append("\\$");
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
