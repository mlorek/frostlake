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

/**
 * One match of a row pattern in a partition: the rows it spans, and for each of them the symbol it was read as
 * and whether it stood in an exclusion. A match that spans no row is an empty match.
 */
public final class RowPatternMatch {

    private final int start;
    private final String[] symbols;
    private final boolean[] excluded;

    /**
     * @param start    the place of the match's first row in the partition, from 0
     * @param symbols  the symbol each row of the match was read as, in row order
     * @param excluded whether each row stood in an exclusion
     */
    public RowPatternMatch(final int start, final String[] symbols, final boolean[] excluded) {
        this.start = start;
        this.symbols = symbols;
        this.excluded = excluded;
    }

    /** The place of the match's first row in the partition, from 0. */
    public int getStart() {
        return start;
    }

    /** The place after the match's last row. */
    public int getEnd() {
        return start + symbols.length;
    }

    /** How many rows the match spans. */
    public int size() {
        return symbols.length;
    }

    /**
     * @param offset a row of the match, from 0
     * @return the symbol the row was read as
     */
    public String symbolAt(final int offset) {
        return symbols[offset];
    }

    /**
     * @param offset a row of the match, from 0
     * @return whether the row stood in an exclusion
     */
    public boolean isExcluded(final int offset) {
        return excluded[offset];
    }

    /**
     * The place in the partition of the first or the last row read as a symbol.
     *
     * @param symbol the symbol's name, upper-cased
     * @param first  whether the first such row is wanted, the last otherwise
     * @return the row's place, or -1 when no row of the match was read as the symbol
     */
    public int rowOf(final String symbol, final boolean first) {
        int found = -1;
        for (int i = 0; i < symbols.length; i++) {
            if (symbols[i].equals(symbol)) {
                if (first) {
                    return start + i;
                }
                found = start + i;
            }
        }
        return found;
    }
}
