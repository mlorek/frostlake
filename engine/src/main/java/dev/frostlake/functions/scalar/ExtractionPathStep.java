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

package dev.frostlake.functions.scalar;

import tools.jackson.databind.JsonNode;

/**
 * One step of an extraction path: the key of an object member, or the position of an array element.
 *
 * <p>A key reaches into an OBJECT only and a position into an ARRAY only — {@code ['0']} finds nothing in an
 * array and {@code [0]} nothing in an object that has a member named {@code 0} (live-verified).
 */
public final class ExtractionPathStep {

    /** The position that no array holds: a negative one, or one past what an array can count. */
    static final long NO_POSITION = -1L;

    private final String key;
    private final long position;

    private ExtractionPathStep(final String key, final long position) {
        this.key = key;
        this.position = position;
    }

    /**
     * A step that names an object's member.
     *
     * @param key the member's name
     * @return the step
     */
    static ExtractionPathStep ofKey(final String key) {
        return new ExtractionPathStep(key, NO_POSITION);
    }

    /**
     * A step that names an array's element.
     *
     * @param position the element's position, or {@link #NO_POSITION}
     * @return the step
     */
    static ExtractionPathStep ofPosition(final long position) {
        return new ExtractionPathStep(null, position);
    }

    /**
     * What the step reaches inside a value.
     *
     * @param node the value the step starts from
     * @return the member or element, or null when the value holds none
     */
    JsonNode in(final JsonNode node) {
        if (node == null) {
            return null;
        }
        if (key != null) {
            return node.isObject() ? node.get(key) : null;
        }
        if (!node.isArray() || position < 0 || position >= node.size()) {
            return null;
        }
        return node.get((int) position);
    }

    /**
     * Adds the step to a path the way FLATTEN's PATH column spells it.
     *
     * @param path the path so far
     */
    void appendTo(final StringBuilder path) {
        if (key != null) {
            ExtractionPath.appendMember(path, key);
        } else {
            path.append('[').append(position).append(']');
        }
    }
}
