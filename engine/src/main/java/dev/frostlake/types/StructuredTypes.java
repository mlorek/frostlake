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
        return source.getClass() == target.getClass();
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
            return "BINARY(8388608)";
        }
        if (type instanceof DateTimeType) {
            final String name = type.getName().toUpperCase();
            if (name.equals("DATE")) {
                return "DATE";
            }
            if (name.equals("TIME")) {
                return "TIME(9)";
            }
            return "TIMESTAMP_NTZ(9)";
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
