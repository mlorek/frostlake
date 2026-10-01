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

package dev.frostlake.functions.aggregate;

import dev.frostlake.values.BinaryValue;

/** Reading an HLL state argument, however the statement wrote it. */
public final class HllStates {

    private HllStates() {
    }

    /**
     * The sketch a state argument holds, or null for NULL and for an empty state — the empty state being
     * what a group with no value answers, whose estimate is 0.
     *
     * @param value the argument
     * @return the sketch, or null
     */
    public static HllSketch sketchOf(final Object value) {
        final byte[] bytes = bytesOf(value);
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return HllSketch.fromBinary(bytes, HllAccumulator.precision());
    }

    /** A state argument's bytes: a BINARY value, or the hex text a client sent in its place. */
    private static byte[] bytesOf(final Object value) {
        if (value instanceof BinaryValue) {
            return ((BinaryValue) value).bytes();
        }
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        if (value instanceof CharSequence) {
            final String text = value.toString();
            return text.isEmpty() ? new byte[0] : BinaryValue.fromHex(text).bytes();
        }
        return null;
    }
}
