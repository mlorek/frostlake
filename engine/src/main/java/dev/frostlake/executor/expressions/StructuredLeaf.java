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

package dev.frostlake.executor.expressions;

import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredField;
import dev.frostlake.types.StructuredObjectType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * What a read of a structured value finds — an element of a structured ARRAY, a field of a structured OBJECT, a value
 * of a MAP — and the type it has: the one the structured value declares for it, rather than VARIANT (documented: "The
 * returned values and elements have the type specified for the structured value, rather than VARIANT"). A read of a
 * semi-structured value has no such type and stays a VARIANT.
 */
public final class StructuredLeaf {

    private StructuredLeaf() {
    }

    /**
     * The type a subscript or a GET reads from a value of type {@code base}: a structured ARRAY's element, a MAP's
     * value, or the field of a structured OBJECT a constant key names.
     *
     * @param base       the type of the value read
     * @param key        the subscript or key when it is a constant — a number or a text — or null
     * @param ignoreCase whether a field name matches in any case, as GET_IGNORE_CASE matches it
     * @return the type read, or null when {@code base} is no structured type or nothing names a field
     */
    public static DataType ofSubscript(final DataType base, final Object key, final boolean ignoreCase) {
        if (base instanceof StructuredArrayType) {
            return ((StructuredArrayType) base).getElementType();
        }
        if (base instanceof MapType) {
            return ((MapType) base).getValueType();
        }
        if (base instanceof StructuredObjectType && key instanceof String) {
            final StructuredField field = fieldOf((StructuredObjectType) base, (String) key, ignoreCase);
            return field != null ? field.getDataType() : null;
        }
        return null;
    }

    /**
     * The type a path of names reads ({@code o:a.b}): each name a field of a structured OBJECT, or a key of a MAP.
     *
     * @param base  the type of the value read
     * @param parts the names, in order
     * @return the type read, or null when a step is no structured type or names no field
     */
    public static DataType ofPath(final DataType base, final List<String> parts) {
        DataType type = base;
        for (final String part : parts) {
            type = ofSubscript(type, part, false);
            if (type == null) {
                return null;
            }
        }
        return type;
    }

    /**
     * The type a GET_PATH path reads ({@code 'a.b[0]'}): each name a field of a structured OBJECT or a key of a MAP,
     * each subscript an element or a value.
     *
     * @param base the type of the value read
     * @param path the path as written
     * @return the type read, or null when a step is no structured type, names no field, or the path is not one
     */
    public static DataType ofPathText(final DataType base, final String path) {
        final List<Object> steps = pathSteps(path);
        if (steps == null) {
            return null;
        }
        DataType type = base;
        for (final Object step : steps) {
            type = ofSubscript(type, step, false);
            if (type == null) {
                return null;
            }
        }
        return type;
    }

    /**
     * A GET_PATH path's steps: a name for each dotted part, an integer for each numeric subscript, the text of a quoted
     * one.
     *
     * @param path the path as written
     * @return the steps, or null when the path is not one
     */
    public static List<Object> pathSteps(final String path) {
        final List<Object> steps = new ArrayList<>();
        int at = 0;
        final int end = path.length();
        while (at < end) {
            final char c = path.charAt(at);
            if (c == '.') {
                at++;
            } else if (c == '[') {
                final int close = path.indexOf(']', at);
                if (close < 0) {
                    return null;
                }
                final String inner = path.substring(at + 1, close).trim();
                if (inner.length() >= 2 && (inner.charAt(0) == '"' || inner.charAt(0) == '\'')) {
                    steps.add(inner.substring(1, inner.length() - 1));
                } else {
                    try {
                        steps.add(Integer.valueOf(inner));
                    } catch (final NumberFormatException notIndex) {
                        return null;
                    }
                }
                at = close + 1;
            } else {
                int stop = at;
                while (stop < end && path.charAt(stop) != '.' && path.charAt(stop) != '[') {
                    stop++;
                }
                steps.add(path.substring(at, stop));
                at = stop;
            }
        }
        return steps;
    }

    /**
     * The field a name picks out of a structured OBJECT: the one spelled exactly so, or, where the name matches in any
     * case, the one spelled in another case when none is spelled exactly so.
     *
     * @param type       the structured OBJECT type
     * @param name       the name
     * @param ignoreCase whether another case matches
     * @return the field, or null when there is none
     */
    public static StructuredField fieldOf(final StructuredObjectType type, final String name,
                                          final boolean ignoreCase) {
        final StructuredField exact = type.field(name);
        if (exact != null || !ignoreCase) {
            return exact;
        }
        for (final StructuredField field : type.getFields()) {
            if (field.getName().equalsIgnoreCase(name)) {
                return field;
            }
        }
        return null;
    }

    /**
     * The first name along a read's steps that a structured OBJECT on the way does not declare.
     *
     * @param base       the type of the value read
     * @param steps      the read's steps: names, and subscripts of any kind
     * @param ignoreCase whether a name matches a field in another case
     * @return the name, or null when every name read is declared or the read leaves the structured types
     */
    public static String undeclaredField(final DataType base, final List<?> steps, final boolean ignoreCase) {
        DataType type = base;
        for (final Object step : steps) {
            if (type instanceof StructuredObjectType && step instanceof String
                    && fieldOf((StructuredObjectType) type, (String) step, ignoreCase) == null) {
                return (String) step;
            }
            type = ofSubscript(type, step, ignoreCase);
            if (type == null) {
                return null;
            }
        }
        return null;
    }

    /**
     * The refusal of a name a structured OBJECT does not declare, as the account words it for every way of reading
     * one (live-verified for {@code o:missing}).
     *
     * @param name the name read
     * @return the refusal
     */
    public static RuntimeException missingField(final String name) {
        return new RuntimeException("Function GET: expected structured object to contain field " + name
            + " but it did not.");
    }

    /**
     * A value read from a structured value as its declared type holds it: an exact number at the type's scale, an
     * approximate one as a double, a DATE, a TIME or a timestamp as the temporal value, a BOOLEAN as a boolean. A
     * nested structured value, a text and anything else stay as they were read.
     *
     * @param value the value read
     * @param leaf  its declared type, or null when it has none
     * @return the value as its type holds it
     */
    public static Object typed(final Object value, final DataType leaf) {
        if (value == null || leaf == null) {
            return value;
        }
        if (leaf instanceof NumericType && value instanceof Number) {
            if (NumericType.isApproximate(leaf)) {
                return Double.valueOf(((Number) value).doubleValue());
            }
            final BigDecimal exact = value instanceof BigDecimal ? (BigDecimal) value
                : new BigDecimal(value.toString());
            final int scale = ((NumericType) leaf).getScale();
            return scale >= 0 && exact.scale() != scale ? exact.setScale(scale, RoundingMode.HALF_UP) : exact;
        }
        if (leaf instanceof DateTimeType || leaf instanceof BinaryType
                || (leaf instanceof BooleanType && !(value instanceof Boolean))) {
            return ValueCaster.castValue(value, leaf.toString());
        }
        return value;
    }
}
