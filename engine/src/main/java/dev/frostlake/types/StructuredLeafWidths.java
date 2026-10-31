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

import java.util.ArrayList;
import java.util.List;

/**
 * The leaves a structured CAST TARGET writes with no width of their own, and the surfaces that tell them apart from
 * a sized leaf (all live-verified):
 *
 * <pre>
 *                                    SYSTEM$TYPEOF                 DESCRIBE RESULT, a refusal
 *   ::OBJECT(a VARCHAR)              OBJECT(a VARCHAR)             OBJECT(a VARCHAR(134217728))
 *   ::OBJECT(a STRING), (a TEXT)     OBJECT(a VARCHAR)
 *   ::ARRAY(VARCHAR)                 ARRAY(VARCHAR)
 *   ::MAP(VARCHAR, VARCHAR)          MAP(VARCHAR, VARCHAR)
 *   ::OBJECT(a BINARY)               OBJECT(a BINARY)
 *   ::MAP(VARCHAR, BINARY)                                         MAP(VARCHAR(134217728), BINARY(67108864))
 *   ::OBJECT(a VARCHAR(16777216))    OBJECT(a VARCHAR(16777216))
 * </pre>
 *
 * <p>An unsized VARCHAR leaf is held at the conversion width a cast gives a bare VARCHAR and an unsized BINARY leaf at
 * the 64MB maximum, the widths DESCRIBE RESULT names; only SYSTEM$TYPEOF leaves them out. A COLUMN's structured type
 * is sized by its DDL ({@code o OBJECT(x VARCHAR)} reads {@code OBJECT(x VARCHAR(16777216))} even in SYSTEM$TYPEOF),
 * so only a cast target makes these leaves, and a stored shape built over one sizes them again ({@link #stored}).
 */
public final class StructuredLeafWidths {

    private StructuredLeafWidths() {
    }

    /**
     * A structured cast target's leaf, written with no width, as an unsized leaf: a VARCHAR-family leaf at the
     * conversion width it was read at, a BINARY or VARBINARY leaf at the 64MB maximum. Any other leaf is itself; a
     * CHAR, which has a width of one when none is written, among them.
     *
     * @param leaf            the leaf as the parser read it
     * @param conversionWidth the width a cast gives a bare VARCHAR
     * @return the unsized leaf, or {@code leaf}
     */
    public static DataType unsizedCastLeaf(final DataType leaf, final int conversionWidth) {
        if (leaf != null && leaf.getClass() == StringType.class
                && ((StringType) leaf).getMaxLength() == conversionWidth) {
            return new StringType("VARCHAR", conversionWidth, true);
        }
        if (leaf instanceof BinaryType) {
            return new BinaryType(BinaryType.NOMINAL_MAXIMUM, ((BinaryType) leaf).isFixed(),
                BinaryWidthSpelling.UNSIZED);
        }
        return leaf;
    }

    /**
     * A structured type as SYSTEM$TYPEOF names it: the refusal's spelling ({@link StructuredTypes#describe}) leaf for
     * leaf, except that an unsized leaf is named bare.
     *
     * @param type a structured type
     * @return its SYSTEM$TYPEOF text, without the storage tag
     */
    public static String typeOfText(final DataType type) {
        if (type instanceof StructuredObjectType) {
            final StringBuilder text = new StringBuilder("OBJECT(");
            final List<StructuredField> fields = ((StructuredObjectType) type).getFields();
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    text.append(", ");
                }
                text.append(fields.get(i).getName()).append(' ').append(typeOfText(fields.get(i).getDataType()));
                if (fields.get(i).isNotNull()) {
                    text.append(" NOT NULL");
                }
            }
            return text.append(')').toString();
        }
        if (type instanceof MapType) {
            return "MAP(" + typeOfText(((MapType) type).getKeyType()) + ", "
                + typeOfText(((MapType) type).getValueType()) + StructuredTypes.notNullMark(type) + ")";
        }
        if (type instanceof StructuredArrayType) {
            return "ARRAY(" + typeOfText(((ArrayType) type).getElementType()) + StructuredTypes.notNullMark(type)
                + ")";
        }
        if (type instanceof StringType && ((StringType) type).isUnsized()) {
            return "VARCHAR";
        }
        if (type instanceof BinaryType && ((BinaryType) type).getWidthSpelling() == BinaryWidthSpelling.UNSIZED) {
            return "BINARY";
        }
        return StructuredTypes.describe(type);
    }

    /**
     * The type a STORED shape — a CTAS column, a view's or a materialized view's column — takes for a structured
     * value: every unsized leaf sized, a VARCHAR at the width it is held at and a BINARY at the column default, which
     * is what such a column declared before cast leaves were told apart. Any type without an unsized leaf is itself.
     *
     * @param type the value's type
     * @return the type a stored column holds
     */
    public static DataType stored(final DataType type) {
        return StructuredTypes.isStructured(type) ? storedMember(type) : type;
    }

    /**
     * The type a structured shape takes where the account reads it as a cast target, as it does a block's structured
     * variable it writes out: every text leaf of the column default width held unsized, at the width a cast gives a
     * bare VARCHAR, so that {@code ARRAY(VARCHAR)} is written out with {@code VARCHAR(134217728)} (live-verified). Any
     * type without such a leaf is itself.
     *
     * @param type            the type
     * @param conversionWidth the width a cast gives a bare VARCHAR
     * @return the type a cast target holds
     */
    public static DataType castTarget(final DataType type, final int conversionWidth) {
        return StructuredTypes.isStructured(type) ? castMember(type, conversionWidth) : type;
    }

    private static DataType castMember(final DataType type, final int conversionWidth) {
        if (type instanceof StructuredObjectType) {
            final List<StructuredField> fields = ((StructuredObjectType) type).getFields();
            final List<StructuredField> unsized = new ArrayList<>(fields.size());
            boolean changed = false;
            for (final StructuredField field : fields) {
                final DataType member = castMember(field.getDataType(), conversionWidth);
                changed |= member != field.getDataType();
                unsized.add(member == field.getDataType() ? field
                    : new StructuredField(field.getName(), member, field.isNotNull()));
            }
            return changed ? new StructuredObjectType(unsized) : type;
        }
        if (type instanceof MapType) {
            final DataType key = castMember(((MapType) type).getKeyType(), conversionWidth);
            final DataType value = castMember(((MapType) type).getValueType(), conversionWidth);
            return key == ((MapType) type).getKeyType() && value == ((MapType) type).getValueType()
                ? type : new MapType(key, value, ((MapType) type).isValueNotNull());
        }
        if (type instanceof StructuredArrayType) {
            final DataType element = castMember(((ArrayType) type).getElementType(), conversionWidth);
            return element == ((ArrayType) type).getElementType() ? type
                : new StructuredArrayType(element, ((StructuredArrayType) type).isElementNotNull());
        }
        if (type != null && type.getClass() == StringType.class && !((StringType) type).isUnsized()
                && ((StringType) type).getMaxLength() == StringType.VARCHAR.getMaxLength()) {
            return new StringType("VARCHAR", conversionWidth, true);
        }
        return type;
    }

    private static DataType storedMember(final DataType type) {
        if (type instanceof StructuredObjectType) {
            final List<StructuredField> fields = ((StructuredObjectType) type).getFields();
            final List<StructuredField> sized = new ArrayList<>(fields.size());
            boolean changed = false;
            for (final StructuredField field : fields) {
                final DataType member = storedMember(field.getDataType());
                changed |= member != field.getDataType();
                sized.add(member == field.getDataType() ? field
                    : new StructuredField(field.getName(), member, field.isNotNull()));
            }
            return changed ? new StructuredObjectType(sized) : type;
        }
        if (type instanceof MapType) {
            final DataType key = storedMember(((MapType) type).getKeyType());
            final DataType value = storedMember(((MapType) type).getValueType());
            return key == ((MapType) type).getKeyType() && value == ((MapType) type).getValueType()
                ? type : new MapType(key, value, ((MapType) type).isValueNotNull());
        }
        if (type instanceof StructuredArrayType) {
            final DataType element = storedMember(((ArrayType) type).getElementType());
            return element == ((ArrayType) type).getElementType() ? type
                : new StructuredArrayType(element, ((StructuredArrayType) type).isElementNotNull());
        }
        if (type instanceof StringType && ((StringType) type).isUnsized()) {
            // A stored shape sizes a leaf written with no width at the column default (live-verified).
            return StringType.VARCHAR;
        }
        if (type instanceof BinaryType && ((BinaryType) type).getWidthSpelling() == BinaryWidthSpelling.UNSIZED) {
            return new BinaryType(BinaryType.DEFAULT_WIDTH, ((BinaryType) type).isFixed());
        }
        return type;
    }
}
