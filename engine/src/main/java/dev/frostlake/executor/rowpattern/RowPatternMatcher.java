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

import java.util.Arrays;

/**
 * Matches a row pattern against the rows of one partition, backtracking as a regular expression does: a
 * quantifier takes as many rows as it can, or as few when reluctant, and gives them back one by one while the
 * rest of the pattern cannot follow; of an alternation's parts the first that leads to a match wins, whatever
 * its length. The ways not yet tried are kept on a stack of the matcher's own, so a match of any length is found
 * without a call for each of its rows.
 */
public final class RowPatternMatcher {

    private final RowPatternProgram program;
    private final int rows;
    private final RowPatternTest test;
    private final int[] readAs;
    private final boolean[] excluded;
    /** The ways kept for later: per way its step, its row, how many exclusions stood open, and its marks. */
    private int[] kept = new int[64];
    private int[][] keptMarks = new int[16][];
    private int depth;

    /**
     * @param pattern the pattern
     * @param rows    how many rows the partition holds
     * @param test    which rows each symbol's definition holds for
     */
    public RowPatternMatcher(final RowPatternNode pattern, final int rows, final RowPatternTest test) {
        this.program = new RowPatternProgram(pattern);
        this.rows = rows;
        this.test = test;
        this.readAs = new int[rows];
        this.excluded = new boolean[rows];
    }

    /**
     * The match that starts at a row.
     *
     * @param start the row's place in the partition, from 0
     * @return the match — an empty one when the pattern matches no row there — or null when it does not match
     */
    public RowPatternMatch matchAt(final int start) {
        depth = 0;
        int[] marks = new int[program.marks()];
        int step = 0;
        int row = start;
        int open = 0;
        while (true) {
            boolean failed = false;
            switch (program.operation(step)) {
                case RowPatternProgram.SYMBOL:
                    if (row < rows && test.holds(program.symbol(program.first(step)), row)) {
                        readAs[row] = program.first(step);
                        excluded[row] = open > 0;
                        row++;
                        step++;
                    } else {
                        failed = true;
                    }
                    break;
                case RowPatternProgram.SPLIT:
                    keep(program.second(step), row, open, marks);
                    step = program.first(step);
                    break;
                case RowPatternProgram.JUMP:
                    step = program.first(step);
                    break;
                case RowPatternProgram.PARTITION_START:
                    failed = row != 0;
                    step++;
                    break;
                case RowPatternProgram.PARTITION_END:
                    failed = row != rows;
                    step++;
                    break;
                case RowPatternProgram.EXCLUDE:
                    open++;
                    step++;
                    break;
                case RowPatternProgram.INCLUDE:
                    open--;
                    step++;
                    break;
                case RowPatternProgram.MARK:
                    marks[program.first(step)] = row;
                    step++;
                    break;
                case RowPatternProgram.ADVANCED:
                    failed = marks[program.first(step)] == row;
                    step++;
                    break;
                default:
                    return matched(start, row);
            }
            if (failed) {
                if (depth == 0) {
                    return null;
                }
                depth--;
                step = kept[depth * 3];
                row = kept[depth * 3 + 1];
                open = kept[depth * 3 + 2];
                marks = keptMarks[depth];
            }
        }
    }

    private void keep(final int step, final int row, final int open, final int[] marks) {
        if (depth * 3 + 3 > kept.length) {
            kept = Arrays.copyOf(kept, kept.length * 2);
        }
        if (depth >= keptMarks.length) {
            keptMarks = Arrays.copyOf(keptMarks, keptMarks.length * 2);
        }
        kept[depth * 3] = step;
        kept[depth * 3 + 1] = row;
        kept[depth * 3 + 2] = open;
        keptMarks[depth] = marks.length == 0 ? marks : marks.clone();
        depth++;
    }

    private RowPatternMatch matched(final int start, final int end) {
        final String[] symbols = new String[end - start];
        final boolean[] left = new boolean[end - start];
        for (int i = start; i < end; i++) {
            symbols[i - start] = program.symbol(readAs[i]);
            left[i - start] = excluded[i];
        }
        return new RowPatternMatch(start, symbols, left);
    }
}
