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

import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.NumericType;

import java.util.List;

/**
 * JAROWINKLER_SIMILARITY(string1, string2) — the Jaro-Winkler similarity of the two strings scaled to an
 * integer 0–100 (0 = no similarity, 100 = exact match), matching Snowflake. The computation is
 * case-insensitive but sensitive to whitespace and other formatting characters. Either NULL yields NULL.
 *
 * <p>Implemented from first principles: the Jaro similarity (matching characters within a sliding window,
 * minus half the transpositions) plus the Winkler bonus for a common prefix (capped at 4 characters, scaling
 * factor 0.1), then {@code round(similarity * 100)}. For example {@code JAROWINKLER_SIMILARITY('Snowflake',
 * 'Oracle')} is 61 (Jaro 0.6111, no common prefix).
 */
public class JarowinklerSimilarity extends TextArgumentFunction {
    private static final int MAX_PREFIX = 4;
    private static final double PREFIX_SCALE = 0.1;

    public JarowinklerSimilarity() { super("JAROWINKLER_SIMILARITY", NumericType.INTEGER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        final String s1 = args.get(0).toString().toLowerCase();
        final String s2 = args.get(1).toString().toLowerCase();
        final double jaro = jaro(s1, s2);
        int prefix = 0;
        final int prefixLimit = Math.min(MAX_PREFIX, Math.min(s1.length(), s2.length()));
        for (int i = 0; i < prefixLimit; i++) {
            if (s1.charAt(i) != s2.charAt(i)) break;
            prefix++;
        }
        final double similarity = jaro + prefix * PREFIX_SCALE * (1.0 - jaro);
        return Math.round(similarity * 100);
    }

    private static double jaro(final String s1, final String s2) {
        final int len1 = s1.length();
        final int len2 = s2.length();
        if (len1 == 0 && len2 == 0) return 1.0;
        if (len1 == 0 || len2 == 0) return 0.0;
        final int window = Math.max(len1, len2) / 2 - 1;
        final boolean[] matched1 = new boolean[len1];
        final boolean[] matched2 = new boolean[len2];
        int matches = 0;
        for (int i = 0; i < len1; i++) {
            final int start = Math.max(0, i - window);
            final int end = Math.min(i + window + 1, len2);
            for (int j = start; j < end; j++) {
                if (matched2[j] || s1.charAt(i) != s2.charAt(j)) continue;
                matched1[i] = true;
                matched2[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) return 0.0;
        double transpositions = 0;
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!matched1[i]) continue;
            while (!matched2[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) transpositions++;
            k++;
        }
        transpositions /= 2.0;
        final double m = matches;
        return (m / len1 + m / len2 + (m - transpositions) / m) / 3.0;
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
