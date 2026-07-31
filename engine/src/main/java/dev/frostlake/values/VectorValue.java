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
import dev.frostlake.types.VectorType;

import tools.jackson.databind.JsonNode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runtime value of Snowflake's {@code VECTOR(FLOAT|INT, n)} type: a fixed-length list of 32-BIT
 * elements plus the element type. The elements are held as {@code double} because every 32-bit float
 * and every 32-bit int is exactly representable there — the narrowing is applied on the way IN, so a
 * stored element is always a value the declared type can hold.
 *
 * <p><b>The precision split (live-verified).</b> Vector ELEMENTS are 32-bit while the
 * arithmetic OVER them is 64-bit:
 * <ul>
 *   <li>{@code VECTOR_NORMALIZE([1,2,3]::VECTOR(FLOAT,3))} is {@code [0.26726124,0.5345225,
 *       0.80178374]} — the float32 renderings of the float64 quotients, not
 *       {@code 0.2672612419124244};</li>
 *   <li>{@code VECTOR_L1_DISTANCE([0.1,0.2,0.3], [0.4,0.5,0.6])} is {@code 0.9000000134110451},
 *       which is EXACTLY the float64 sum of the three float64-widened float32 differences — a
 *       float32 accumulation would round to {@code 0.90000004};</li>
 *   <li>{@code VECTOR_NORMALIZE([1e-30,2e-30,2e-30]::VECTOR(FLOAT,3))} is
 *       {@code [0.33333334,0.6666667,0.6666667]}, which float32 arithmetic could not produce at all
 *       (the squares underflow to zero in float32).</li>
 * </ul>
 * So: read the 32-bit elements, compute in {@code double}, and narrow only the ELEMENTS of a vector
 * RESULT — {@link #of}. A scalar result keeps full {@code double} precision.
 *
 * <p>Rendering matches the account exactly, because Snowflake prints the shortest text that
 * round-trips at the element's width, which is what {@link Float#toString} does:
 * {@code [1.0,2.0,3.0]}, {@code [0.57735026,...]}, {@code [1.0,1.0E-20,1.0E-20]}, and {@code [1,2,3]}
 * for INT.
 */
public final class VectorValue implements Comparable<VectorValue>, Serializable {

    private static final long serialVersionUID = 1L;

    /** Snowflake's message when a value's shape does not fit the declared vector type. */
    private static final String NOT_A_VECTOR =
        "Vector value being cast to a vector is not an array or vector, or has incorrect dimension "
            + "or element type";

    private final VectorElementType elementType;
    private final double[] elements;

    private VectorValue(final VectorElementType elementType, final double[] elements) {
        this.elementType = elementType;
        this.elements = elements;
    }

    /**
     * A vector of {@code computed} float64 values NARROWED to {@code elementType} — the one way a
     * function builds a vector result, so the 32-bit element rule is applied in a single place.
     */
    public static VectorValue of(final VectorElementType elementType, final double[] computed) {
        final double[] narrowed = new double[computed.length];
        for (int i = 0; i < computed.length; i++) {
            narrowed[i] = narrow(elementType, computed[i]);
        }
        return new VectorValue(elementType, narrowed);
    }

    /**
     * One element narrowed to the declared width. INT wraps like a 32-bit two's-complement integer —
     * live, {@code [2147483648,0,0]::VECTOR(INT,3)} is {@code [-2147483648,0,0]} and
     * {@code [9007199254740993,0,0]} is {@code [1,0,0]}.
     */
    private static double narrow(final VectorElementType elementType, final double value) {
        if (elementType == VectorElementType.INT) {
            return (int) (long) value;
        }
        return (float) value;
    }

    /**
     * {@code expr::VECTOR(t, n)} — the value converted to the declared vector type, with Snowflake's
     * live conversion errors. An ARRAY (or its JSON text) of the right length converts; a vector of
     * exactly the same type passes through; anything else is rejected.
     */
    public static VectorValue cast(final Object value, final VectorType target) {
        if (value instanceof VectorValue) {
            final VectorValue source = (VectorValue) value;
            if (!source.type().equals(target)) {
                throw new RuntimeException(NOT_A_VECTOR);
            }
            return source;
        }
        final List<JsonNode> raw = readElements(value);
        if (raw.size() != target.getDimension()) {
            throw new RuntimeException(NOT_A_VECTOR);
        }
        final double[] converted = new double[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            converted[i] = convertElement(raw.get(i), target);
        }
        return new VectorValue(target.getElementType(), converted);
    }

    /**
     * One source element narrowed to the declared element type, rejecting a value the type cannot hold.
     * Live: {@code ['a','b','c']::VECTOR(FLOAT,3)} is "Array-like value being cast to a float
     * vector has elements that are not real numbers" and {@code [1.7,2.2,3.9]::VECTOR(INT,3)} is
     * "Array-like value being cast to an integer vector has elements that are not integers", while a
     * whole-valued {@code [1.0,2.0,3.0]} converts to {@code [1,2,3]}.
     *
     * <p>An INT element is read as an exact {@code long} before it wraps, because {@code double} cannot
     * carry every 64-bit integer: live, {@code [9007199254740993,0,0]::VECTOR(INT,3)} is {@code [1,0,0]}
     * — the low 32 bits of 2^53+1 — which a float64 round-trip would turn into 0.
     */
    private static double convertElement(final JsonNode element, final VectorType target) {
        if (!element.isNumber()) {
            throw new RuntimeException(elementError(target));
        }
        if (target.getElementType() != VectorElementType.INT) {
            return (float) element.doubleValue();
        }
        final double asDouble = element.doubleValue();
        if (Double.isNaN(asDouble) || Double.isInfinite(asDouble) || asDouble != Math.rint(asDouble)) {
            throw new RuntimeException(elementError(target));
        }
        return (int) element.longValue();
    }

    /** The source value's elements, or the "not a vector" error when it is not array-shaped. */
    private static List<JsonNode> readElements(final Object value) {
        final JsonNode array = arrayNode(value);
        if (array == null) {
            throw new RuntimeException(NOT_A_VECTOR);
        }
        final List<JsonNode> raw = new ArrayList<JsonNode>();
        for (final JsonNode element : array) {
            raw.add(element);
        }
        return raw;
    }

    private static String elementError(final VectorType target) {
        return target.getElementType() == VectorElementType.INT
            ? "Array-like value being cast to an integer vector has elements that are not integers"
            : "Array-like value being cast to a float vector has elements that are not real numbers";
    }

    /** The source value read as a JSON array node, or null when it is not array-shaped. */
    private static JsonNode arrayNode(final Object value) {
        if (value == null) {
            return null;
        }
        final JsonNode node;
        try {
            node = value instanceof VariantValue
                ? ((VariantValue) value).node()
                : VariantValue.of(value.toString()).node();
        } catch (final RuntimeException notJson) {
            return null;
        }
        return node != null && node.isArray() ? node : null;
    }

    public VectorElementType getElementType() {
        return elementType;
    }

    public int dimension() {
        return elements.length;
    }

    /** Element {@code index} as float64 — exact, because the stored value is already 32-bit. */
    public double element(final int index) {
        return elements[index];
    }

    /** The elements as float64, ready to compute over (a copy: the value is immutable). */
    public double[] elements() {
        return elements.clone();
    }

    /** The declared type this value belongs to — element type AND dimension. */
    public VectorType type() {
        return new VectorType(elementType, elements.length);
    }

    @Override
    public String toString() {
        final StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(elementType == VectorElementType.INT
                ? Integer.toString((int) elements[i])
                : Float.toString((float) elements[i]));
        }
        return text.append(']').toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VectorValue)) {
            return false;
        }
        final VectorValue o = (VectorValue) other;
        return o.elementType == elementType && Arrays.equals(o.elements, elements);
    }

    @Override
    public int hashCode() {
        return elementType.hashCode() * 31 + Arrays.hashCode(elements);
    }

    /** Element-wise ordering, shorter-first on a common prefix — the ordering the display text implies. */
    @Override
    public int compareTo(final VectorValue other) {
        final int shared = Math.min(elements.length, other.elements.length);
        for (int i = 0; i < shared; i++) {
            final int cmp = Double.compare(elements[i], other.elements[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(elements.length, other.elements.length);
    }
}
