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

import dev.frostlake.functions.aggregate.AggregateNumerics;
import dev.frostlake.functions.aggregate.ExactMoments;
import dev.frostlake.functions.aggregate.SpreadAccumulator;

import java.util.Map;
import java.util.TreeMap;

/**
 * The variance family's answers over a frame moving along its partition, for an EXACT argument: one exact state that
 * the rows entering a frame join and the rows leaving it leave, so a partition of n rows costs O(n) exact additions
 * whatever the frame's width. Exact sums keep no trace of the order they were taken in, so each frame's answer is the
 * one its rows read afresh give (see {@link ExactMoments}), at the scale of the widest value the frame holds.
 */
public final class ExactFrameMoments {

    private final Object[] answers;

    /**
     * Every row's answer, in one walk.
     *
     * @param values    each row's value in the window's order, null where the row takes no part; every other one of
     *                  an exact type
     * @param lows      each row's first frame position
     * @param highs     each row's last frame position, below its first for an empty frame
     * @param statistic the statistic
     */
    public ExactFrameMoments(final Object[] values, final int[] lows, final int[] highs,
                             final MomentStatistic statistic) {
        answers = new Object[values.length];
        final ExactMoments moments = new ExactMoments();
        final TreeMap<Integer, Integer> scales = new TreeMap<Integer, Integer>();
        int low = 0;
        int high = -1;
        for (int row = 0; row < values.length; row++) {
            final int frameLow = lows[row];
            final int frameHigh = highs[row];
            if (frameLow > frameHigh) {
                // An empty frame holds no value; the state waits for the next frame as it is.
                continue;
            }
            if (high < low || frameLow > high || frameHigh < low) {
                // A frame that shares no row with the state starts afresh.
                while (low <= high) {
                    leave(values[low], moments, scales);
                    low++;
                }
                low = frameLow;
                high = frameLow - 1;
            }
            while (low < frameLow) {
                leave(values[low], moments, scales);
                low++;
            }
            while (low > frameLow) {
                low--;
                enter(values[low], moments, scales);
            }
            while (high < frameHigh) {
                high++;
                enter(values[high], moments, scales);
            }
            while (high > frameHigh) {
                leave(values[high], moments, scales);
                high--;
            }
            final int scale = scales.isEmpty() ? 0 : scales.lastKey().intValue();
            answers[row] = SpreadAccumulator.exactResult(moments, statistic.isSample(), statistic.isRoot(), scale);
        }
    }

    /**
     * The statistic of one row's frame.
     *
     * @param row the row's position in the window's order
     * @return the answer, or null when the frame holds too few values
     */
    public Object of(final int row) {
        return answers[row];
    }

    private static void enter(final Object value, final ExactMoments moments, final Map<Integer, Integer> scales) {
        if (value == null) {
            return;
        }
        moments.add(value);
        final Integer scale = Integer.valueOf(AggregateNumerics.scaleOfInput(value));
        final Integer held = scales.get(scale);
        scales.put(scale, Integer.valueOf(held == null ? 1 : held.intValue() + 1));
    }

    private static void leave(final Object value, final ExactMoments moments, final Map<Integer, Integer> scales) {
        if (value == null) {
            return;
        }
        moments.remove(value);
        final Integer scale = Integer.valueOf(AggregateNumerics.scaleOfInput(value));
        final Integer held = scales.get(scale);
        if (held == null || held.intValue() <= 1) {
            scales.remove(scale);
        } else {
            scales.put(scale, Integer.valueOf(held.intValue() - 1));
        }
    }
}
