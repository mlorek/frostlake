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

package dev.frostlake.functions.scalar.semistructured;

import dev.frostlake.values.VariantUndefined;

import tools.jackson.databind.JsonNode;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * The elements of an array that a long run of {@code undefined} holes pads: the elements before the run, the run's
 * length, and the elements after it. ARRAY_INSERT pads up to the position it inserts at, and the account builds an
 * array of a thousand million elements so (live-verified); held one node per hole the array would fill the heap long
 * before. The holes are read where they stand, without a node of their own. Elements may be appended; no other change
 * is taken.
 */
final class PaddedArrayElements extends AbstractList<JsonNode> {

    private final List<JsonNode> before;
    private final int holes;
    private final List<JsonNode> after;

    /**
     * Elements padded by a run of holes.
     *
     * @param before the elements before the run
     * @param holes  how many holes the run holds
     * @param after  the elements after the run
     */
    PaddedArrayElements(final List<JsonNode> before, final int holes, final List<JsonNode> after) {
        this.before = new ArrayList<JsonNode>(before);
        this.holes = holes;
        this.after = new ArrayList<JsonNode>(after);
    }

    @Override
    public JsonNode get(final int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
        }
        if (index < before.size()) {
            return before.get(index);
        }
        if (index < before.size() + holes) {
            return VariantUndefined.node();
        }
        return after.get(index - before.size() - holes);
    }

    @Override
    public int size() {
        return before.size() + holes + after.size();
    }

    @Override
    public void add(final int index, final JsonNode element) {
        if (index != size()) {
            throw new UnsupportedOperationException("an array padded by holes takes elements at its end only");
        }
        after.add(element);
        modCount++;
    }
}
