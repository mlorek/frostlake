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
import dev.frostlake.types.IntegerResultWidths;

import java.util.List;
import java.util.Locale;

/**
 * JAROWINKLER_SIMILARITY(string1, string2) — the Jaro-Winkler similarity of the two strings as an integer 0–100,
 * matching Snowflake. Both strings are lower-cased first, with the full Unicode mapping, and compared by
 * character (a supplementary character counts once), so the score is case-insensitive but sensitive to
 * whitespace and every other character. Either NULL yields NULL.
 *
 * <p>The Jaro similarity pairs equal characters no further apart than {@code max(0, longer length / 2 - 1)},
 * each character pairing once, and counts half the paired characters that sit out of order, rounded down, as
 * transpositions: {@code (m / length1 + m / length2 + (m - t) / m) / 3}, or 0 when nothing pairs, two empty
 * strings included. Only a Jaro similarity of at least 0.7 earns the Winkler bonus of 0.1 per shared leading
 * character, at most four. The answer is the similarity times 100 truncated, never rounded:
 * {@code JAROWINKLER_SIMILARITY('ab', 'ac')} is 66 and {@code JAROWINKLER_SIMILARITY('bcc', 'bbcacbccac')} is 78,
 * although its exact value is 79 (live-verified).
 */
public class JarowinklerSimilarity extends TextArgumentFunction {
    private static final int MAX_PREFIX = 4;
    private static final double PREFIX_SCALE = 0.1;
    private static final double BONUS_THRESHOLD = 0.7;

    public JarowinklerSimilarity() { super("JAROWINKLER_SIMILARITY", IntegerResultWidths.YEAR_PART); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        final int[] s1 = codePoints(args.get(0).toString().toLowerCase(Locale.ROOT));
        final int[] s2 = codePoints(args.get(1).toString().toLowerCase(Locale.ROOT));
        final double jaro = jaro(s1, s2);
        double similarity = jaro;
        if (jaro >= BONUS_THRESHOLD) {
            int prefix = 0;
            final int prefixLimit = Math.min(MAX_PREFIX, Math.min(s1.length, s2.length));
            for (int i = 0; i < prefixLimit; i++) {
                if (s1[i] != s2[i]) break;
                prefix++;
            }
            similarity = jaro + prefix * PREFIX_SCALE * (1.0 - jaro);
        }
        return (long) Math.floor(similarity * 100);
    }

    /** The characters of a text, a supplementary character as one. */
    private static int[] codePoints(final String text) {
        final int[] points = new int[text.codePointCount(0, text.length())];
        int offset = 0;
        for (int i = 0; i < points.length; i++) {
            points[i] = text.codePointAt(offset);
            offset += Character.charCount(points[i]);
        }
        return points;
    }

    private static double jaro(final int[] s1, final int[] s2) {
        final int len1 = s1.length;
        final int len2 = s2.length;
        if (len1 == 0 || len2 == 0) return 0.0;
        final int window = Math.max(0, Math.max(len1, len2) / 2 - 1);
        final boolean[] matched1 = new boolean[len1];
        final boolean[] matched2 = new boolean[len2];
        int matches = 0;
        for (int i = 0; i < len1; i++) {
            final int start = Math.max(0, i - window);
            final int end = Math.min(i + window + 1, len2);
            for (int j = start; j < end; j++) {
                if (matched2[j] || s1[i] != s2[j]) continue;
                matched1[i] = true;
                matched2[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) return 0.0;
        int outOfOrder = 0;
        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!matched1[i]) continue;
            while (!matched2[k]) k++;
            if (s1[i] != s2[k]) outOfOrder++;
            k++;
        }
        final int transpositions = outOfOrder / 2;
        final double m = matches;
        return (m / len1 + m / len2 + (m - transpositions) / m) / 3.0;
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
