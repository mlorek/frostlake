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
package dev.frostlake.functions.window;

/**
 * The MIN or the MAX of any frame of a sorted window partition, read from a segment tree over the partition's values
 * instead of a walk over every frame row: a whole partition, a running frame and a moving one alike take a
 * logarithmic number of comparisons. Values compare as {@link WindowFunctionHelper#min} and
 * {@link WindowFunctionHelper#max} compare them, NULLs are passed over, and of equal values the first in the frame is
 * the answer, as the walk answers it.
 */
public final class FrameExtremes {

    private final Object[] values;
    private final boolean maximum;
    /** The tree's leaves start at {@code width}; each node holds the position of its range's extreme, or -1. */
    private final int width;
    private final int[] tree;

    /**
     * The extremes of a partition's values.
     *
     * @param values  the values in the partition's order, NULL for a missing one
     * @param maximum true for MAX, false for MIN
     */
    public FrameExtremes(final Object[] values, final boolean maximum) {
        this.values = values.clone();
        this.maximum = maximum;
        int leaves = 1;
        while (leaves < Math.max(1, values.length)) {
            leaves <<= 1;
        }
        this.width = leaves;
        this.tree = new int[2 * leaves];
        for (int i = 0; i < tree.length; i++) {
            tree[i] = -1;
        }
        for (int i = 0; i < values.length; i++) {
            tree[leaves + i] = values[i] == null ? -1 : i;
        }
        for (int node = leaves - 1; node >= 1; node--) {
            tree[node] = better(tree[2 * node], tree[2 * node + 1]);
        }
    }

    /**
     * The extreme of a frame.
     *
     * @param from the frame's first position
     * @param to   the frame's last position; a frame with {@code from > to} is empty
     * @return the extreme value, or null when the frame holds none
     */
    public Object over(final int from, final int to) {
        if (from > to) {
            return null;
        }
        int left = from + width;
        int right = to + width + 1;
        int leftBest = -1;
        int rightBest = -1;
        while (left < right) {
            if ((left & 1) == 1) {
                leftBest = better(leftBest, tree[left++]);
            }
            if ((right & 1) == 1) {
                rightBest = better(tree[--right], rightBest);
            }
            left >>= 1;
            right >>= 1;
        }
        final int best = better(leftBest, rightBest);
        return best < 0 ? null : values[best];
    }

    /** Of two positions, the earlier one, the later one taking it only when its value is strictly better. */
    private int better(final int earlier, final int later) {
        if (earlier < 0) {
            return later;
        }
        if (later < 0) {
            return earlier;
        }
        final int order = WindowFunctionHelper.compareValues(values[later], values[earlier]);
        return maximum ? (order > 0 ? later : earlier) : (order < 0 ? later : earlier);
    }
}
