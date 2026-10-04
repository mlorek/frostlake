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

package dev.frostlake.types;

import java.util.List;

/**
 * Predicates and rendering shared by the STRUCTURED types ({@link StructuredObjectType},
 * {@link StructuredArrayType}, {@link MapType}).
 *
 * <p>Snowflake sorts these into two families for cast compatibility — {@code STRUCTURED_OBJECT}
 * (structured OBJECT and MAP) and {@code STRUCTURED_ARRAY} — and reports a cross-family cast as
 * "incompatible types: [STRUCTURED_ARRAY] and [STRUCTURED_OBJECT]" (live-verified).
 */
public final class StructuredTypes {

    /** Snowflake's default VARCHAR length as it appears in EXPRESSION type text. */
    private static final int DEFAULT_VARCHAR_LENGTH = 134217728;

    private StructuredTypes() {
    }

    /** Whether {@code type} is one of the STRUCTURED types (a declared shape, not plain OBJECT/ARRAY/VARIANT). */
    public static boolean isStructured(final DataType type) {
        return type instanceof StructuredObjectType || type instanceof StructuredArrayType
            || type instanceof MapType;
    }

    /** Whether {@code type} is in Snowflake's {@code STRUCTURED_OBJECT} family (structured OBJECT or MAP). */
    public static boolean isStructuredObjectFamily(final DataType type) {
        return type instanceof StructuredObjectType || type instanceof MapType;
    }

    /** Snowflake's family label for a structured type, used verbatim in its cast error messages. */
    public static String familyName(final DataType type) {
        return type instanceof StructuredArrayType ? "STRUCTURED_ARRAY" : "STRUCTURED_OBJECT";
    }

    /**
     * Whether two types match for UDF ARGUMENT BINDING: the same structured layout and the same leaf
     * type FAMILIES, ignoring length/precision. Live with a parameter declared
     * {@code OBJECT(x VARCHAR)}: an {@code OBJECT(x VARCHAR(10))} argument binds, an
     * {@code OBJECT(x INT)} argument fails "Invalid argument types", and so do a plain {@code OBJECT},
     * a {@code VARIANT}, an {@code OBJECT(y VARCHAR)} and a wider {@code OBJECT(x VARCHAR, y INT)}.
     *
     * <p>A leaf's family is {@link SqlTypeNames#sameFamily}'s: the exact and the approximate numerics are two,
     * and so are DATE, TIME and each timestamp flavour, text and UUID. Comparing the leaves' Java classes
     * lumped each of those pairs together, so an {@code OBJECT(x TIME)} bound an {@code OBJECT(x DATE)}
     * parameter and an {@code OBJECT(x FLOAT)} an {@code OBJECT(x INT)} one.
     */
    public static boolean sameBindingShape(final DataType source, final DataType target) {
        if (source == null || target == null) {
            return false;
        }
        if (source instanceof StructuredObjectType && target instanceof StructuredObjectType) {
            final List<StructuredField> left = ((StructuredObjectType) source).getFields();
            final List<StructuredField> right = ((StructuredObjectType) target).getFields();
            if (left.size() != right.size()) {
                return false;
            }
            for (int i = 0; i < left.size(); i++) {
                if (!left.get(i).getName().equals(right.get(i).getName())
                        || !sameBindingShape(left.get(i).getDataType(), right.get(i).getDataType())) {
                    return false;
                }
            }
            return true;
        }
        if (source instanceof MapType && target instanceof MapType) {
            return sameBindingShape(((MapType) source).getKeyType(), ((MapType) target).getKeyType())
                && sameBindingShape(((MapType) source).getValueType(), ((MapType) target).getValueType());
        }
        if (source instanceof StructuredArrayType && target instanceof StructuredArrayType) {
            return sameBindingShape(((ArrayType) source).getElementType(),
                ((ArrayType) target).getElementType());
        }
        if (isStructured(source) || isStructured(target)) {
            return false;   // one side structured, the other not (or a different structured family)
        }
        // Leaf types bind by FAMILY: VARCHAR(10) and VARCHAR(134217728) are the same parameter type.
        return SqlTypeNames.sameFamily(source, target);
    }

    /**
     * Whether an argument of type {@code argument} binds to a user-defined function parameter of type
     * {@code parameter} as far as structured types decide it: a structured parameter takes only an argument
     * of its own binding shape ({@link #sameBindingShape}), and a plainly semi-structured parameter (OBJECT,
     * ARRAY, VARIANT) refuses a structured argument. Every other pairing is left to the conversion rules, as
     * is an argument whose type is not known.
     *
     * @param argument  the argument's static type, or null when it is not known
     * @param parameter the parameter's declared type
     * @return false when the structured rule refuses the pairing
     */
    public static boolean bindsAsArgument(final DataType argument, final DataType parameter) {
        if (argument == null || parameter == null) {
            return true;
        }
        if (isStructured(parameter)) {
            return sameBindingShape(argument, parameter);
        }
        return !isStructured(argument) || !(parameter instanceof ObjectType || parameter instanceof ArrayType
            || parameter instanceof VariantType);
    }

    /** A type as Snowflake spells it in an argument-type / cast error message. */
    public static String describe(final DataType type) {
        if (type == null) {
            return "NULL";
        }
        if (type instanceof StructuredObjectType) {
            final StringBuilder text = new StringBuilder("OBJECT(");
            final List<StructuredField> fields = ((StructuredObjectType) type).getFields();
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    text.append(", ");
                }
                text.append(fields.get(i).getName()).append(' ')
                    .append(describe(fields.get(i).getDataType()));
                if (fields.get(i).isNotNull()) {
                    text.append(" NOT NULL");
                }
            }
            return text.append(')').toString();
        }
        if (type instanceof MapType) {
            return "MAP(" + describe(((MapType) type).getKeyType()) + ", "
                + describe(((MapType) type).getValueType()) + ")";
        }
        if (type instanceof StructuredArrayType) {
            return "ARRAY(" + describe(((ArrayType) type).getElementType()) + ")";
        }
        if (type instanceof UuidType) {
            // A UUID is a type of its own and takes no length (UuidType).
            return "UUID";
        }
        if (type instanceof StringType) {
            final int maxLength = ((StringType) type).getMaxLength();
            return "VARCHAR(" + (maxLength > 0 ? maxLength : DEFAULT_VARCHAR_LENGTH) + ")";
        }
        if (type instanceof NumericType) {
            final NumericType numeric = (NumericType) type;
            final String name = numeric.getName().toUpperCase();
            if (name.contains("FLOAT") || name.contains("DOUBLE") || name.contains("REAL")) {
                return "FLOAT";
            }
            return "NUMBER(" + (numeric.getPrecision() > 0 ? numeric.getPrecision() : 38) + ","
                + Math.max(numeric.getScale(), 0) + ")";
        }
        if (type instanceof BinaryType) {
            // A binary leaf at its own width, and an unsized one at the 64MB maximum: live, ARRAY_CONTAINS(1, ...)
            // names {'a': 'ab'}::OBJECT(a BINARY) as OBJECT(a BINARY(67108864)) and ::OBJECT(a BINARY(4)) as
            // OBJECT(a BINARY(4)). Every leaf used to read BINARY(8388608).
            return ((BinaryType) type).refusalText();
        }
        if (type instanceof DateTimeType) {
            // A temporal leaf keeps its flavour and its declared precision: SYSTEM$TYPEOF names a cast to
            // OBJECT(k TIMESTAMP_NTZ(3)) as OBJECT(k TIMESTAMP_NTZ(3)) (live-verified), where every TIME read
            // TIME(9) here and every timestamp TIMESTAMP_NTZ(9).
            final String name = type.getName().toUpperCase();
            if (name.equals("DATE")) {
                return "DATE";
            }
            final int precision = ((DateTimeType) type).getPrecision() < 0 ? 9 : ((DateTimeType) type).getPrecision();
            if (name.equals("TIME")) {
                return "TIME(" + precision + ")";
            }
            if (name.equals("TIMESTAMP_LTZ") || name.equals("TIMESTAMP_TZ")) {
                return name + "(" + precision + ")";
            }
            return "TIMESTAMP_NTZ(" + precision + ")";
        }
        if (type instanceof ArrayType) {
            return "ARRAY";
        }
        if (type instanceof ObjectType) {
            return "OBJECT";
        }
        return type.getName().toUpperCase();
    }
}
