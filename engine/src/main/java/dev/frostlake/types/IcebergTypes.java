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
import java.util.Locale;

/**
 * An Iceberg table's column types in the format's own names (live-verified). A column a column definition typed —
 * in the list of CREATE ICEBERG TABLE, in ADD COLUMN — is spelled by GET_DDL in the format's names, and a change of
 * its type is judged by the format's promotions and refused in the format's lowercase names:
 *
 * <pre>
 *   written                                 GET_DDL             a refused change
 *   INT, INTEGER                            INT                 int
 *   BIGINT                                  LONG                long
 *   NUMBER(p,s), DECIMAL, NUMERIC, DEC      DECIMAL(p, s)       decimal(p, s)
 *   FLOAT, FLOAT4, FLOAT8                   FLOAT               float
 *   DOUBLE, DOUBLE PRECISION, REAL          DOUBLE              double
 *   VARCHAR, STRING, TEXT                   STRING              string
 *   BINARY                                  BINARY              binary
 *   BOOLEAN, DATE, UUID                     as written          boolean, date, uuid
 *   TIME                                    TIME(6)             time
 *   TIMESTAMP_NTZ, TIMESTAMP, DATETIME      TIMESTAMP_NTZ(6)    timestamp
 *   TIMESTAMP_LTZ                           TIMESTAMP_LTZ(6)    timestamptz
 *   MAP, ARRAY, OBJECT                      members so named    a table's names
 * </pre>
 *
 * A structured OBJECT's field is spelled without the NOT NULL it was declared with. A column a query or LIKE gave
 * the table holds a table's types, and is spelled and judged in a table's names.
 */
public final class IcebergTypes {

    private IcebergTypes() {
    }

    /**
     * A column's type as GET_DDL spells it for an Iceberg table's column a column definition typed.
     *
     * @param type the column's type
     * @return the format's spelling
     */
    public static String ddlName(final DataType type) {
        if (type instanceof IcebergNumericType) {
            return ((IcebergNumericType) type).formatName().toUpperCase(Locale.ROOT);
        }
        if (type instanceof MapType) {
            final MapType map = (MapType) type;
            return "MAP(" + ddlName(map.getKeyType()) + ", " + ddlName(map.getValueType()) + ")";
        }
        if (type instanceof StructuredArrayType) {
            return "ARRAY(" + ddlName(((StructuredArrayType) type).getElementType()) + ")";
        }
        if (type instanceof StructuredObjectType) {
            final StringBuilder out = new StringBuilder("OBJECT(");
            final List<StructuredField> fields = ((StructuredObjectType) type).getFields();
            for (int i = 0; i < fields.size(); i++) {
                out.append(i == 0 ? "" : ", ").append(fields.get(i).getName()).append(' ')
                   .append(ddlName(fields.get(i).getDataType()));
            }
            return out.append(')').toString();
        }
        if (type instanceof UuidType) {
            return "UUID";
        }
        if (type instanceof NumericType && !NumericType.isApproximate(type)) {
            final NumericType number = (NumericType) type;
            return "DECIMAL(" + number.getPrecision() + ", " + number.getScale() + ")";
        }
        if (type instanceof StringType) {
            return "STRING";
        }
        if (type instanceof BinaryType) {
            return "BINARY";
        }
        return SqlTypeNames.canonical(type);
    }

    /**
     * A type as a refused change of an Iceberg table's column names it.
     *
     * @param type a column's type, or the one a change writes
     * @return the format's lowercase name, or null for a structured type, which is named as a table's
     */
    public static String formatName(final DataType type) {
        if (type instanceof IcebergNumericType) {
            return ((IcebergNumericType) type).formatName();
        }
        if (isStructured(type)) {
            return null;
        }
        if (type instanceof UuidType) {
            return "uuid";
        }
        if (type instanceof NumericType) {
            final NumericType number = (NumericType) type;
            return NumericType.isApproximate(type) ? "double"
                : "decimal(" + number.getPrecision() + ", " + number.getScale() + ")";
        }
        if (type instanceof StringType) {
            return "string";
        }
        if (type instanceof BinaryType) {
            return "binary";
        }
        if (type instanceof DateTimeType) {
            final String name = type.getName().toUpperCase(Locale.ROOT);
            if ("DATE".equals(name) || "TIME".equals(name)) {
                return name.toLowerCase(Locale.ROOT);
            }
            return ((DateTimeType) type).hasTimeZone() ? "timestamptz" : "timestamp";
        }
        return SqlTypeNames.canonical(type).toLowerCase(Locale.ROOT);
    }

    /**
     * Whether a type is a structured OBJECT, ARRAY or MAP.
     *
     * @param type the type
     * @return true for a structured type
     */
    public static boolean isStructured(final DataType type) {
        return type instanceof MapType || type instanceof StructuredArrayType || type instanceof StructuredObjectType;
    }

    /**
     * The type a table holds for one of an Iceberg column's: an int, a long, a float and a double are the NUMBER and
     * the FLOAT they report, the members of a structured type too. A column a query or LIKE gives a table holds it.
     *
     * @param type the type
     * @return the table's type
     */
    public static DataType tableType(final DataType type) {
        if (type instanceof IcebergNumericType) {
            final IcebergNumericType number = (IcebergNumericType) type;
            return NumericType.isApproximate(number) ? NumericType.FLOAT
                : new NumericType("NUMBER", number.getPrecision(), number.getScale());
        }
        if (type instanceof MapType) {
            final MapType map = (MapType) type;
            return new MapType(tableType(map.getKeyType()), tableType(map.getValueType()), map.isValueNotNull());
        }
        if (type instanceof StructuredArrayType) {
            return new StructuredArrayType(tableType(((StructuredArrayType) type).getElementType()),
                ((StructuredArrayType) type).isElementNotNull());
        }
        if (type instanceof StructuredObjectType) {
            final List<StructuredField> fields = new ArrayList<>();
            for (final StructuredField field : ((StructuredObjectType) type).getFields()) {
                fields.add(new StructuredField(field.getName(), tableType(field.getDataType()), field.isNotNull()));
            }
            return new StructuredObjectType(fields);
        }
        return type;
    }
}
