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
 * A partition's SUM or AVG over every cumulative RANGE frame from its first row: the value through each peer group,
 * by the position of the group's last row. A value may be the refusal its frame meets.
 */
final class CumulativeFrameValues {

    private final List<Integer> ends;
    private final List<Object> values;

    /**
     * @param ends   each peer group's last position, ascending
     * @param values each group's frame value, in the same order
     */
    CumulativeFrameValues(final List<Integer> ends, final List<Object> values) {
        this.ends = new ArrayList<Integer>(ends);
        this.values = new ArrayList<Object>(values);
    }

    /**
     * The value of the frame that ends at a position.
     *
     * @param end        the frame's last position
     * @param noSuchEnd  what to answer when no peer group ends there
     * @return the value, the refusal its frame meets, or {@code noSuchEnd}
     */
    Object through(final int end, final Object noSuchEnd) {
        final int group = Collections.binarySearch(ends, Integer.valueOf(end));
        return group < 0 ? noSuchEnd : values.get(group);
    }
}
