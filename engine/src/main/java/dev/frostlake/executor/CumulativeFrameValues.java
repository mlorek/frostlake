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

package dev.frostlake.executor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A partition's SUM or AVG over every cumulative RANGE frame pinned to one of its edges: the value through each peer
 * group, by the position at which the group's frame stops — the group's last row for a frame from the partition's first
 * row, its first row for a frame to the partition's last. A value may be the refusal its frame meets.
 */
final class CumulativeFrameValues {

    private final List<Integer> stops;
    private final List<Object> values;

    /**
     * @param stops  each peer group's stopping position, ascending
     * @param values each group's frame value, in the same order
     */
    CumulativeFrameValues(final List<Integer> stops, final List<Object> values) {
        this.stops = new ArrayList<Integer>(stops);
        this.values = new ArrayList<Object>(values);
    }

    /**
     * The value of the frame that stops at a position.
     *
     * @param stop        the frame's stopping position
     * @param noSuchStop  what to answer when no peer group's frame stops there
     * @return the value, the refusal its frame meets, or {@code noSuchStop}
     */
    Object through(final int stop, final Object noSuchStop) {
        final int group = Collections.binarySearch(stops, Integer.valueOf(stop));
        return group < 0 ? noSuchStop : values.get(group);
    }
}
