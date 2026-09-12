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

package dev.frostlake.values;

import tools.jackson.databind.node.DoubleNode;

/**
 * A JSON double node that remembers it came from a SQL FLOAT — TO_VARIANT of a FLOAT column, a
 * computed double, a {@code ::FLOAT} cast — rather than from JSON text.
 *
 * <p>The two spell differently under the string conversions (live-verified): a FLOAT through
 * TO_VARIANT and {@code ::VARCHAR} is {@code 3.0} / {@code 1.0E18} / {@code -0.0}, the shortest
 * round-trip form, where a double read from JSON text ({@code PARSE_JSON('3e0')}) is the FLOAT text
 * {@code 3} / {@code 1e+18} / {@code -0}. Nothing else tells them apart: TO_JSON, the canonical text,
 * equality and hashing are all the double's own, so this node IS a double node and serialises
 * exactly as one. The origin rides along in memory only; a value re-parsed from its text (a
 * persistence or wire round-trip, an extracted member re-wrapped) is a plain double again and takes
 * the FLOAT text, the same answer as before this node existed.
 */
public final class FloatOriginNode extends DoubleNode {
    private static final long serialVersionUID = 1L;

    public FloatOriginNode(final double value) {
        super(value);
    }
}
