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

import dev.frostlake.types.VectorElementType;
import java.math.BigDecimal;
import java.util.Locale;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * A VECTOR living inside a VARIANT: an array of its ELEMENTS, still knowing it is a vector.
 *
 * <p>★ THE ELEMENTS ARE NOT SPELLED THE DISPLAY WAY. A vector DISPLAYS at six decimals, but embedding
 * it in a VARIANT writes the numbers at FULL PRECISION — a float32 element widened to float64 and
 * printed in full, so {@code 0.1234567} comes out {@code 0.1234567016363144}. Three surfaces, three
 * spellings of the same element: display, this one, and TO_JSON's 15-decimal scientific form. Sharing
 * a renderer between any two of them makes one of them wrong.
 *
 * <p>★ IT IS AN ARRAY THAT DOES NOT BEHAVE LIKE ONE. Live prints the member as a JSON array but
 * answers NULL to element access, to a path into it and to ARRAY_SIZE — so the member is VECTOR-typed
 * rather than an array that happens to hold numbers, and TYPEOF says so. Extending the array node is
 * what makes the TEXT come out right without a custom writer; the identity is what keeps the access
 * paths honest.
 */
public final class TypedVectorNode extends ArrayNode {

    private static final long serialVersionUID = 1L;

    private final transient VectorValue vector;

    public TypedVectorNode(final VectorValue vector) {
        super(JsonNodeFactory.instance);
        this.vector = vector;
        for (int i = 0; i < vector.dimension(); i++) {
            final double element = vector.element(i);
            if (vector.getElementType() == VectorElementType.INT) {
                // An INT vector writes whole numbers, not doubles — live: [7,8], never [7.0,8.0].
                add((long) element);
            } else {
                add(embeddedElementText(element));
            }
        }
    }

    /**
     * One element as the VARIANT spells it: SIXTEEN significant digits, trailing zeros dropped.
     *
     * <p>Measured, not chosen. A float32 {@code 0.1234567} widens to the double
     * {@code 0.12345670163631439} — seventeen digits, which is what Java's shortest round-tripping
     * form prints — and live writes {@code 0.1234567016363144}, one digit shorter. Sixteen digits is
     * enough to recover the FLOAT the element actually is, which is presumably why that is the width.
     * Whole values keep a single decimal ({@code 1.0}, {@code 3.5}), so the trailing-zero strip stops
     * at one place rather than producing a bare integer.
     */
    private static BigDecimal embeddedElementText(final double element) {
        final BigDecimal exact = new BigDecimal(String.format(Locale.ROOT, "%.16g", element));
        final BigDecimal trimmed = exact.stripTrailingZeros();
        return trimmed.scale() < 1 ? trimmed.setScale(1) : trimmed;
    }

    /** The vector this member was built from, or null when the node is not one. */
    public static VectorValue vectorValueOf(final JsonNode node) {
        return node instanceof TypedVectorNode ? ((TypedVectorNode) node).vector : null;
    }
}
