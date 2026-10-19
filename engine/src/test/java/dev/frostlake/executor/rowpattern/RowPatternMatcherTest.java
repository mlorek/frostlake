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
package dev.frostlake.executor.rowpattern;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The row-pattern matcher on its own: rows are letters, a symbol reads the rows of its letter and {@code ANY}
 * reads every row.
 */
public class RowPatternMatcherTest {

    private static RowPatternNode symbol(final String name) {
        return RowPatternNode.symbol(name);
    }

    private static RowPatternNode sequence(final RowPatternNode... parts) {
        return RowPatternNode.of(RowPatternKind.SEQUENCE, Arrays.asList(parts));
    }

    private static RowPatternNode either(final RowPatternNode... parts) {
        return RowPatternNode.of(RowPatternKind.ALTERNATION, Arrays.asList(parts));
    }

    private static RowPatternNode excluded(final RowPatternNode part) {
        final List<RowPatternNode> parts = new ArrayList<>();
        parts.add(part);
        return RowPatternNode.of(RowPatternKind.EXCLUSION, parts);
    }

    /** The match at a row as the symbols it read, an excluded row in brackets; null when there is none. */
    private static String match(final RowPatternNode pattern, final String rows, final int start) {
        final RowPatternMatcher matcher = new RowPatternMatcher(pattern, rows.length(), new RowPatternTest() {
            @Override
            public boolean holds(final String symbol, final int row) {
                return "ANY".equals(symbol) || symbol.charAt(0) == rows.charAt(row);
            }
        });
        final RowPatternMatch found = matcher.matchAt(start);
        if (found == null) {
            return null;
        }
        final StringBuilder read = new StringBuilder();
        for (int i = 0; i < found.size(); i++) {
            read.append(found.isExcluded(i) ? "[" + found.symbolAt(i) + "]" : found.symbolAt(i));
        }
        assertEquals(start, found.getStart());
        assertEquals(start + found.size(), found.getEnd());
        return read.toString();
    }

    @Test
    public void aQuantifierTakesAllItCanAndGivesBackWhatTheRestNeeds() {
        final RowPatternNode star = RowPatternNode.quantified(symbol("A"), 0, RowPatternNode.UNBOUNDED, false);
        assertEquals("AAA", match(star, "AAAB", 0));
        assertEquals("", match(star, "BAAA", 0));
        assertEquals("AAA", match(sequence(star, symbol("A")), "AAAB", 0));
        assertEquals("AAAB", match(sequence(star, symbol("B")), "AAAB", 0));
        assertNull(match(sequence(star, symbol("C")), "AAAB", 0));
        assertEquals("AA", match(RowPatternNode.quantified(symbol("A"), 1, 2, false), "AAAB", 0));
        assertNull(match(RowPatternNode.quantified(symbol("A"), 4, 4, false), "AAAB", 0));
    }

    @Test
    public void aReluctantQuantifierTakesTheLeastItCan() {
        assertEquals("", match(RowPatternNode.quantified(symbol("A"), 0, RowPatternNode.UNBOUNDED, true), "AAAB", 0));
        assertEquals("A", match(RowPatternNode.quantified(symbol("A"), 1, RowPatternNode.UNBOUNDED, true), "AAAB", 0));
        assertEquals("AAAB", match(sequence(
            RowPatternNode.quantified(symbol("A"), 0, RowPatternNode.UNBOUNDED, true), symbol("B")), "AAAB", 0));
        assertEquals("A", match(RowPatternNode.quantified(symbol("A"), 1, 2, true), "AAAB", 0));
    }

    @Test
    public void theFirstAlternativeThatLeadsToAMatchWins() {
        assertEquals("A", match(either(symbol("A"), sequence(symbol("A"), symbol("A"))), "AAB", 0));
        assertEquals("AA", match(either(sequence(symbol("A"), symbol("A")), symbol("A")), "AAB", 0));
        assertEquals("AAB", match(sequence(either(symbol("A"), sequence(symbol("A"), symbol("A"))), symbol("B")), "AAB", 0));
        assertNull(match(either(symbol("B"), symbol("C")), "AAB", 0));
    }

    @Test
    public void anchorsHoldAMatchToThePartitionsEnds() {
        final RowPatternNode start = RowPatternNode.anchor(RowPatternKind.PARTITION_START);
        final RowPatternNode end = RowPatternNode.anchor(RowPatternKind.PARTITION_END);
        assertEquals("A", match(sequence(start, symbol("A")), "AAB", 0));
        assertNull(match(sequence(start, symbol("A")), "AAB", 1));
        assertEquals("B", match(sequence(symbol("B"), end), "AAB", 2));
        assertNull(match(sequence(symbol("A"), end), "AAB", 0));
    }

    @Test
    public void anExclusionMatchesRowsAndMarksThem() {
        assertEquals("[A]B", match(sequence(excluded(symbol("A")), symbol("B")), "AB", 0));
        assertEquals("A[B][B]A", match(sequence(symbol("A"),
            excluded(RowPatternNode.quantified(symbol("B"), 1, RowPatternNode.UNBOUNDED, false)), symbol("A")), "ABBA", 0));
    }

    @Test
    public void aPermutationIsItsPartsInEveryOrderFromTheWrittenOneOn() {
        final RowPatternNode permuted = RowPatternNode.permutation(Arrays.asList(symbol("A"), symbol("B"), symbol("ANY")));
        assertEquals("ABANY", match(permuted, "ABC", 0));
        assertEquals("BAANY", match(permuted, "BAC", 0));
        assertEquals("AANYB", match(permuted, "ACB", 0));
        assertNull(match(permuted, "AAC", 0));
    }

    @Test
    public void aLoopThatReadsNoRowEnds() {
        final RowPatternNode optional = RowPatternNode.quantified(symbol("A"), 0, 1, false);
        final RowPatternNode loop = RowPatternNode.quantified(optional, 0, RowPatternNode.UNBOUNDED, false);
        assertEquals("AA", match(loop, "AAB", 0));
        assertEquals("", match(loop, "BBB", 0));
        assertEquals("B", match(sequence(loop, symbol("B")), "BBB", 0));
    }

    @Test
    public void aLongMatchNeedsNoDeepCalls() {
        final StringBuilder rows = new StringBuilder();
        for (int i = 0; i < 200_000; i++) {
            rows.append('A');
        }
        final RowPatternNode plus = RowPatternNode.quantified(symbol("A"), 1, RowPatternNode.UNBOUNDED, false);
        final RowPatternMatcher matcher = new RowPatternMatcher(plus, rows.length(), new RowPatternTest() {
            @Override
            public boolean holds(final String symbol, final int row) {
                return true;
            }
        });
        assertEquals(200_000, matcher.matchAt(0).size());
    }

    @Test
    public void aPatternKnowsTheSymbolsEveryMatchHolds() {
        final RowPatternNode pattern = sequence(symbol("A"),
            either(symbol("B"), sequence(symbol("B"), symbol("C"))),
            RowPatternNode.quantified(symbol("D"), 0, RowPatternNode.UNBOUNDED, false));
        assertTrue(pattern.alwaysMatches("A"));
        assertTrue(pattern.alwaysMatches("B"));
        assertFalse(pattern.alwaysMatches("C"));
        assertFalse(pattern.alwaysMatches("D"));
        assertFalse(pattern.holdsExclusion());
        assertTrue(sequence(excluded(symbol("A")), symbol("B")).holdsExclusion());
    }
}
