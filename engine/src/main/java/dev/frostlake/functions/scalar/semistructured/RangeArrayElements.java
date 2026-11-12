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

import dev.frostlake.functions.scalar.ArrayFunctionHelper;

import tools.jackson.databind.JsonNode;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * The elements of a long range ARRAY_GENERATE_RANGE makes, each read where it stands rather than held as a node of
 * its own: a range of a hundred million numbers held one node each would fill the heap before its size was asked.
 * Elements may be appended; no other change is taken.
 */
final class RangeArrayElements extends AbstractList<JsonNode> {

    private final long start;
    private final long step;
    private final int count;
    private final List<JsonNode> appended = new ArrayList<JsonNode>();

    /**
     * A range.
     *
     * @param start its first number
     * @param step  what each later number adds
     * @param count how many numbers it holds
     */
    RangeArrayElements(final long start, final long step, final int count) {
        this.start = start;
        this.step = step;
        this.count = count;
    }

    @Override
    public JsonNode get(final int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
        }
        if (index < count) {
            return ArrayFunctionHelper.MAPPER.getNodeFactory().numberNode(start + index * step);
        }
        return appended.get(index - count);
    }

    @Override
    public int size() {
        return count + appended.size();
    }

    @Override
    public void add(final int index, final JsonNode element) {
        if (index != size()) {
            throw new UnsupportedOperationException("a range takes elements at its end only");
        }
        appended.add(element);
        modCount++;
    }
}
