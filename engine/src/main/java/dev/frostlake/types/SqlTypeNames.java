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
import java.util.Locale;

/**
 * How Snowflake SPELLS a type back: the canonical name with its parameters, never the alias a column
 * was declared with. INT, INTEGER, BIGINT, SMALLINT and NUMBER all read {@code NUMBER(38,0)}; STRING,
 * TEXT and VARCHAR read {@code VARCHAR(16777216)}; DOUBLE reads {@code FLOAT}; a bare TIME reads
 * {@code TIME(9)} and a bare TIMESTAMP {@code TIMESTAMP_NTZ(9)}. CHAR, NCHAR and NVARCHAR are all
 * VARCHAR of their length, a bare BINARY is {@code BINARY(8388608)}, and OBJECT, ARRAY, VARIANT,
 * GEOGRAPHY, GEOMETRY and VECTOR read back as declared. Live-verified across the family.
 */
public final class SqlTypeNames {

    /** The widest length a COLUMN's metadata prints for a string. */
    public static final long DESCRIBED_STRING_MAXIMUM = 16777216;

    private SqlTypeNames() {
    }

    /**
     * The canonical spelling of a type.
     *
     * @param type the declared type
     * @return the name Snowflake reports for it, parameters included
     */
    /**
     * The canonical name a COLUMN's metadata prints, which stops a string at the width a column can
     * declare. An EXPRESSION may be wider — live reports {@code t || t} over two 16MB columns as
     * VARCHAR(33554432) and {@code CAST(i AS VARCHAR)} as VARCHAR(134217728) when selected, through a
     * view, a derived table and a CTE alike — but DESCRIBE and SHOW COLUMNS over the same view stop at
     * 16777216.
     *
     * @param type the column's declared type
     * @return the canonical name, with a string's width capped
     */
    public static String columnMetadata(final DataType type) {
        if (type instanceof StringType
                && ((StringType) type).getMaxLength() > DESCRIBED_STRING_MAXIMUM) {
            return canonical(new StringType(type.getName(), (int) DESCRIBED_STRING_MAXIMUM));
        }
        return canonical(type);
    }

    public static String canonical(final DataType type) {
        if (type == null) {
            return null;
        }
        if (type instanceof NumericType) {
            final NumericType numeric = (NumericType) type;
            final String name = numeric.getName().toUpperCase(Locale.ROOT);
            if (isFixedPoint(name)) {
                return "NUMBER(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
            }
            // FLOAT is the whole approximate family: DOUBLE, REAL and FLOAT4/8 all report FLOAT.
            return "FLOAT";
        }
        if (type instanceof StringType) {
            final StringType string = (StringType) type;
            return "VARCHAR(" + string.getMaxLength() + ")";
        }
        if (type instanceof BinaryType) {
            // A bare BINARY is 8MB wide, and its length is always spelled.
            return "BINARY(" + ((BinaryType) type).getMaxLength() + ")";
        }
        if (type instanceof DateTimeType) {
            final DateTimeType dateTime = (DateTimeType) type;
            final String name = dateTime.getName().toUpperCase(Locale.ROOT);
            if ("DATE".equals(name)) {
                return "DATE";
            }
            return ("TIMESTAMP".equals(name) ? "TIMESTAMP_NTZ" : name) + "(" + dateTime.getPrecision() + ")";
        }
        if (type instanceof StructuredObjectType) {
            // Each field is "name TYPE", separated by ", ", with the name in the case it was
            // DECLARED — MixedCase stays MixedCase — and NOT NULL spelled inside the parentheses.
            // Nesting is recursive (live-verified). A plain OBJECT is not one of these and stays bare.
            final StringBuilder out = new StringBuilder("OBJECT(");
            final List<StructuredField> fields = ((StructuredObjectType) type).getFields();
            for (int i = 0; i < fields.size(); i++) {
                final StructuredField field = fields.get(i);
                out.append(i == 0 ? "" : ", ").append(field.getName()).append(' ')
                   .append(canonical(field.getDataType()));
                if (field.isNotNull()) {
                    out.append(" NOT NULL");
                }
            }
            return out.append(')').toString();
        }
        if (type instanceof StructuredArrayType) {
            return "ARRAY(" + canonical(((StructuredArrayType) type).getElementType()) + ")";
        }
        if (type instanceof MapType) {
            // A MAP spells both halves, with a space after the comma (live-verified, in DESCRIBE,
            // GET_DDL and the type-matching refusals alike).
            final MapType map = (MapType) type;
            return "MAP(" + canonical(map.getKeyType()) + ", " + canonical(map.getValueType()) + ")";
        }
        return type.getName().toUpperCase(Locale.ROOT);
    }

    /**
     * The INTERNAL name Snowflake uses for a type where it does not spell the SQL one: TEXT for the
     * string family, FIXED for the exact numerics, REAL for the approximate ones, and the SQL name
     * for everything else — never with parameters. A masking policy's argument type is reported this
     * way, and so is SHOW COLUMNS' data_type blob (live-verified).
     */
    public static String internalName(final DataType type) {
        if (type == null) {
            return null;
        }
        final String canonical = canonical(type);
        final int paren = canonical.indexOf('(');
        final String family = paren < 0 ? canonical : canonical.substring(0, paren);
        if ("VARCHAR".equals(family)) {
            return "TEXT";
        }
        if ("NUMBER".equals(family)) {
            return "FIXED";
        }
        if ("FLOAT".equals(family)) {
            return "REAL";
        }
        return family;
    }

    /**
     * Whether two types belong to the same FAMILY, which is what a policy attachment is judged on:
     * the parameters are ignored — a VARCHAR(5) policy argument takes a VARCHAR(20) column — while the
     * timestamp variants stay distinct from each other, and NUMBER never matches FLOAT (live-verified).
     */
    public static boolean sameFamily(final DataType left, final DataType right) {
        return family(left).equals(family(right));
    }

    private static String family(final DataType type) {
        final String canonical = canonical(type);
        if (canonical == null) {
            return "";
        }
        final int paren = canonical.indexOf('(');
        return paren < 0 ? canonical : canonical.substring(0, paren);
    }

    private static boolean isFixedPoint(final String name) {
        return "NUMBER".equals(name) || "DECIMAL".equals(name) || "NUMERIC".equals(name)
            || "DEC".equals(name) || "INT".equals(name) || "INTEGER".equals(name)
            || "BIGINT".equals(name) || "SMALLINT".equals(name) || "TINYINT".equals(name)
            || "BYTEINT".equals(name);
    }
}
