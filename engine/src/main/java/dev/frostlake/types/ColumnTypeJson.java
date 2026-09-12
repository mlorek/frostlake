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
 * SHOW COLUMNS' {@code data_type} cell. Snowflake answers a JSON DESCRIPTOR there rather than a type
 * name, and spells the type in its internal vocabulary — {@code TEXT}, {@code FIXED}, {@code REAL} —
 * with a key set that differs per family. Every rule below is live-measured:
 *
 * <ul>
 *   <li>{@code {"type":"FIXED","precision":10,"scale":2,"nullable":true}} — the whole exact-numeric
 *       family, integers included, since they are all NUMBER(38,0).</li>
 *   <li>{@code {"type":"TEXT","length":9,"byteLength":36,"nullable":true,"fixed":false}} — a string's
 *       byte budget is four bytes per character, and CHAR is a VARCHAR here, so {@code fixed} reads
 *       false for the whole family.</li>
 *   <li>{@code {"type":"BINARY","length":20,"byteLength":20,"nullable":true,"fixed":true}} — one byte
 *       per byte. Both binary spellings report the type BINARY and are told apart by {@code fixed}
 *       alone, which reads the SPELLING rather than the family — see {@link BinaryType#isFixed}.</li>
 *   <li>{@code {"type":"TIME","precision":0,"scale":3,"nullable":true}} — the declared precision is
 *       reported as the SCALE for every time-of-day and timestamp type; the {@code precision} key is
 *       always zero. DATE carries neither.</li>
 *   <li>{@code {"type":"REAL","nullable":true}} for the approximate numerics, and the same bare pair
 *       for BOOLEAN, DATE, VARIANT and the unstructured OBJECT and ARRAY.</li>
 *   <li>{@code {"type":"GEOGRAPHY","outputType":"OBJECT","nullable":true}} — GEOMETRY and FILE take
 *       the same shape.</li>
 *   <li>The structured types nest a descriptor of their own: OBJECT gains {@code fields} (each a
 *       {@code fieldName}/{@code fieldType} pair whose name keeps its declared case), ARRAY gains
 *       {@code elementType}, MAP gains {@code keyType} and {@code valueType}, and VECTOR gains
 *       {@code vectorElementType} and {@code dimension}. A map key and a vector element are never
 *       nullable; an array element and a map value are.</li>
 * </ul>
 */
public final class ColumnTypeJson {

    /** A character's byte budget in a VARCHAR's byteLength. */
    private static final int BYTES_PER_CHARACTER = 4;

    /**
     * The widest length this descriptor ever prints for a string. An EXPRESSION may be wider — live
     * reads {@code t || t} over two 16MB columns as VARCHAR(33554432) and {@code CAST(i AS VARCHAR)}
     * as VARCHAR(134217728), through a view, a derived table and a CTE alike — but the descriptor a
     * COLUMN shows stops here, so a view over either of those reads 16777216 in SHOW COLUMNS while
     * selecting the same column still reports the full width.
     */
    private static final long DESCRIBED_STRING_MAXIMUM = SqlTypeNames.DESCRIBED_STRING_MAXIMUM;

    private ColumnTypeJson() {
    }

    /**
     * Renders one column's descriptor.
     *
     * @param type     the column's declared type
     * @param nullable whether the column accepts NULL
     * @return the JSON descriptor Snowflake reports for it
     */
    public static String render(final DataType type, final boolean nullable) {
        if (type == null) {
            return null;
        }
        final StringBuilder out = new StringBuilder();
        append(out, type, nullable);
        return out.toString();
    }

    /**
     * The same for a column declared with a collation: live adds it after every other fact —
     * {@code {"type":"TEXT",...,"fixed":false,"collation":"en-ci"}} — and a column without one, or
     * with the empty specification, has no such key.
     *
     * @param type      the column's type
     * @param nullable  whether it takes NULL
     * @param collation its collation specification, or null
     * @return the descriptor
     */
    public static String render(final DataType type, final boolean nullable, final String collation) {
        final String rendered = render(type, nullable);
        if (rendered == null || collation == null || collation.isEmpty() || !(type instanceof StringType)) {
            return rendered;
        }
        final String escaped = collation.replace("\\", "\\\\").replace("\"", "\\\"");
        return rendered.substring(0, rendered.length() - 1) + ",\"collation\":\"" + escaped + "\"}";
    }

    private static void append(final StringBuilder out, final DataType type, final boolean nullable) {
        final String name = SqlTypeNames.internalName(type);
        out.append("{\"type\":\"").append(name).append('"');
        if ("FIXED".equals(name)) {
            final NumericType numeric = (NumericType) type;
            out.append(",\"precision\":").append(numeric.getPrecision())
               .append(",\"scale\":").append(numeric.getScale());
        } else if (type instanceof StringType && !(type instanceof UuidType)) {
            // A UUID is no string here: live's descriptor is {"type":"UUID","nullable":true}.
            final long length = Math.min(((StringType) type).getMaxLength(),
                DESCRIBED_STRING_MAXIMUM);
            out.append(",\"length\":").append(length)
               .append(",\"byteLength\":").append(length * BYTES_PER_CHARACTER);
        } else if (type instanceof BinaryType) {
            final long length = ((BinaryType) type).getMaxLength();
            out.append(",\"length\":").append(length).append(",\"byteLength\":").append(length);
        } else if (type instanceof DateTimeType && !"DATE".equals(name)) {
            out.append(",\"precision\":0,\"scale\":").append(((DateTimeType) type).getPrecision());
        } else if (hasOutputType(type)) {
            out.append(",\"outputType\":\"OBJECT\"");
        }
        out.append(",\"nullable\":").append(nullable);
        if (type instanceof StringType && !(type instanceof UuidType)) {
            out.append(",\"fixed\":false");
        } else if (type instanceof BinaryType) {
            out.append(",\"fixed\":").append(((BinaryType) type).isFixed());
        }
        appendContained(out, type);
        out.append('}');
    }

    /** The three types Snowflake hands back as an OBJECT rather than in their own representation. */
    private static boolean hasOutputType(final DataType type) {
        return type instanceof GeographyType || type instanceof GeometryType || type instanceof FileType;
    }

    /** The nested descriptors a structured type carries after its own {@code nullable}. */
    private static void appendContained(final StringBuilder out, final DataType type) {
        if (type instanceof StructuredObjectType) {
            final List<StructuredField> fields = ((StructuredObjectType) type).getFields();
            out.append(",\"fields\":[");
            for (int i = 0; i < fields.size(); i++) {
                final StructuredField field = fields.get(i);
                out.append(i == 0 ? "" : ",").append("{\"fieldName\":\"").append(field.getName())
                   .append("\",\"fieldType\":");
                append(out, field.getDataType(), !field.isNotNull());
                out.append('}');
            }
            out.append(']');
        } else if (type instanceof StructuredArrayType) {
            out.append(",\"elementType\":");
            append(out, ((StructuredArrayType) type).getElementType(), true);
        } else if (type instanceof MapType) {
            final MapType map = (MapType) type;
            out.append(",\"keyType\":");
            append(out, map.getKeyType(), false);
            out.append(",\"valueType\":");
            append(out, map.getValueType(), true);
        } else if (type instanceof VectorType) {
            final VectorType vector = (VectorType) type;
            out.append(",\"vectorElementType\":");
            append(out, vectorElement(vector), false);
            out.append(",\"dimension\":").append(vector.getDimension());
        }
    }

    /** A VECTOR of INT describes its element as NUMBER(38,0); a VECTOR of FLOAT as the bare REAL. */
    private static DataType vectorElement(final VectorType vector) {
        return vector.getElementType() == VectorElementType.INT ? NumericType.NUMBER : NumericType.FLOAT;
    }
}
