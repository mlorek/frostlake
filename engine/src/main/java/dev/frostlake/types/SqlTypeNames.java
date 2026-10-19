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
 * GEOGRAPHY, GEOMETRY, VECTOR and UUID read back as declared. Live-verified across the family.
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
     * The canonical name a RESULT's column prints in DESCRIBE RESULT: the type the result declares, at its own width.
     * Unlike {@link #columnMetadata} it does not stop a string at a column's width, because the result is not
     * stored: an expression reads as it is selected (live: {@code CAST(i AS VARCHAR)} is VARCHAR(134217728), and so
     * are a stream's METADATA$ACTION and METADATA$ROW_ID), and a plan's lengthless VARCHAR reads at the width
     * nothing bounds.
     *
     * @param type the result column's declared type
     * @return the canonical name
     */
    public static String resultMetadata(final DataType type) {
        if (type instanceof LengthlessStringType) {
            return canonical(new StringType(type.getName(), ((StringType) type).getMaxLength()));
        }
        if (type instanceof BinaryType && ((BinaryType) type).getWidthSpelling() != BinaryWidthSpelling.DECLARED) {
            // A binary no width was written for reads at the type's maximum: TO_BINARY's and a procedure's BINARY
            // answer are BINARY(67108864), where a column's own width is its own.
            return ((BinaryType) type).refusalText();
        }
        return canonical(type);
    }

    /**
     * The canonical name a TABLE's own column prints: the width it was declared with, however wide. A table keeps
     * what its statement wrote — {@code VARCHAR(134217728)} and {@code VARCHAR(16777217)} read back as written —
     * where a view's column stops at 16777216 (see {@link #columnMetadata}) and a table created from a query never
     * held more (live-verified).
     *
     * @param type the column's declared type
     * @return the canonical name
     */
    public static String tableColumnMetadata(final DataType type) {
        if (type instanceof LengthlessStringType) {
            return canonical(new StringType(type.getName(), ((StringType) type).getMaxLength()));
        }
        return canonical(type);
    }

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
        if (type instanceof LengthlessStringType) {
            // A column built over it declares the full width the plan never spelled.
            return canonical(new StringType(type.getName(), ((StringType) type).getMaxLength()));
        }
        return canonical(type);
    }

    /** The width INFORMATION_SCHEMA spells for a routine returning text of no declared width. */
    public static final int ROUTINE_TEXT_MAXIMUM = 134217728;

    /**
     * A routine's return type as INFORMATION_SCHEMA.FUNCTIONS and PROCEDURES spell it in {@code data_type}: the
     * canonical name with every parameter, a text or binary of no declared width at the widest a value can be —
     * {@code VARCHAR(134217728)}, {@code BINARY(67108864)} — where DESCRIBE and SHOW print such a type bare
     * (live-verified).
     *
     * @param type the declared return type
     * @return the spelling
     */
    public static String routineDataType(final DataType type) {
        if (type instanceof StringType && !(type instanceof LengthlessStringType)
                && ((StringType) type).getMaxLength() != DESCRIBED_STRING_MAXIMUM) {
            return canonical(type);
        }
        if (type instanceof StringType) {
            return "VARCHAR(" + ROUTINE_TEXT_MAXIMUM + ")";
        }
        return refusalSpelling(type);
    }

    /**
     * The length INFORMATION_SCHEMA reports beside a routine's text return type; null for every other type.
     *
     * @param type the declared return type
     * @return the length in characters, or null
     */
    public static Integer routineTextLength(final DataType type) {
        if (!(type instanceof StringType)) {
            return null;
        }
        final int declared = ((StringType) type).getMaxLength();
        return Integer.valueOf(type instanceof LengthlessStringType || declared == DESCRIBED_STRING_MAXIMUM
            ? ROUTINE_TEXT_MAXIMUM : declared);
    }

    /**
     * A type as a refusal sentence spells it: the canonical name, except that a bare BINARY is always
     * BINARY(67108864), whatever width the engine holds one at (live-verified in the CALL argument and
     * declared-RETURNS sentences).
     */
    public static String refusalSpelling(final DataType type) {
        if (type instanceof BinaryType
                && ((BinaryType) type).getMaxLength() == BinaryType.BINARY.getMaxLength()) {
            return "BINARY(67108864)";
        }
        return canonical(type);
    }

    /**
     * A ROUTINE's type as the account spells it - in a signature, in a RETURNS clause and in a DESCRIBE
     * row alike. It is the type AS DECLARED: a string of the column default width is spelled BARE, a
     * sized one keeps its width, and everything else reads as {@link #canonical} spells it, so a bare
     * INT reads {@code NUMBER(38,0)} (live-verified).
     *
     * @param type the declared type
     * @return the spelling a routine surface prints
     */
    public static String routineType(final DataType type) {
        if (StructuredTypes.isStructured(type)) {
            // A leaf of the shape written with no width reads bare (see StructuredLeafWidths).
            return StructuredLeafWidths.typeOfText(type);
        }
        if (type instanceof StringType && !(type instanceof LengthlessStringType)
                && ((StringType) type).getMaxLength() == DESCRIBED_STRING_MAXIMUM) {
            return "VARCHAR";
        }
        return canonical(type);
    }

    /**
     * A routine's RETURNS type as its DDL and its description spell it, when it is known whether the type was
     * written with its width: a text or a binary written bare reads bare, one written with its width keeps it —
     * {@code VARCHAR} beside {@code VARCHAR(16777216)}, {@code BINARY} beside {@code BINARY(8388608)} — and every
     * other type reads as {@link #routineType(DataType)} spells it (live-verified).
     *
     * @param type  the declared type
     * @param sized whether the declaration carried type parameters, or null when that is not known
     * @return the spelling
     */
    public static String routineType(final DataType type, final Boolean sized) {
        if (sized == null || StructuredTypes.isStructured(type)) {
            return routineType(type);
        }
        if (type instanceof BinaryType) {
            return sized.booleanValue() || ((BinaryType) type).getMaxLength() != BinaryType.BINARY.getMaxLength()
                ? canonical(type) : "BINARY";
        }
        if (type instanceof StringType && !(type instanceof LengthlessStringType)
                && ((StringType) type).getMaxLength() == DESCRIBED_STRING_MAXIMUM) {
            return sized.booleanValue() ? canonical(type) : "VARCHAR";
        }
        return canonical(type);
    }

    /**
     * {@link #routineDataType(DataType)} when it is known whether the type was written with its width: a text or
     * a binary written with it keeps it — {@code VARCHAR(16777216)}, {@code BINARY(8388608)} — where one written
     * bare reads at the widest a value can be (live-verified).
     *
     * @param type  the declared return type
     * @param sized whether the declaration carried type parameters, or null when that is not known
     * @return the spelling
     */
    public static String routineDataType(final DataType type, final Boolean sized) {
        if (sized == null || !sized.booleanValue() || type instanceof LengthlessStringType) {
            return routineDataType(type);
        }
        return type instanceof StringType || type instanceof BinaryType ? canonical(type) : routineDataType(type);
    }

    /**
     * {@link #routineTextLength(DataType)} when it is known whether the type was written with its width.
     *
     * @param type  the declared return type
     * @param sized whether the declaration carried type parameters, or null when that is not known
     * @return the length in characters, or null
     */
    public static Integer routineTextLength(final DataType type, final Boolean sized) {
        if (sized == null || !sized.booleanValue() || !(type instanceof StringType)
                || type instanceof LengthlessStringType) {
            return routineTextLength(type);
        }
        return Integer.valueOf(((StringType) type).getMaxLength());
    }

    /**
     * A policy's RETURNS type as its description and its DDL spell it: the type as declared, so a string or a
     * binary written without a width reads bare where one written with its width keeps it — VARCHAR beside
     * VARCHAR(16777216) — and everything else reads as {@link #canonical} spells it (live-verified).
     *
     * @param type  the declared type
     * @param sized whether the declaration carried type parameters
     * @return the spelling
     */
    public static String declaredSpelling(final DataType type, final boolean sized) {
        if (StructuredTypes.isStructured(type)) {
            return routineType(type);
        }
        if (!sized && type instanceof BinaryType) {
            return "BINARY";
        }
        if (!sized && type instanceof StringType
                && ((StringType) type).getMaxLength() == DESCRIBED_STRING_MAXIMUM) {
            return "VARCHAR";
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
            if ("DECFLOAT".equals(name)) {
                return "DECFLOAT(38)";
            }
            // FLOAT is the whole approximate family: DOUBLE, REAL and FLOAT4/8 all report FLOAT.
            return "FLOAT";
        }
        if (type instanceof UuidType) {
            return "UUID";
        }
        if (type instanceof LengthlessStringType) {
            return "VARCHAR";
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
            return "ARRAY(" + canonical(((StructuredArrayType) type).getElementType())
                + StructuredTypes.notNullMark(type) + ")";
        }
        if (type instanceof MapType) {
            // A MAP spells both halves, with a space after the comma (live-verified, in DESCRIBE,
            // GET_DDL and the type-matching refusals alike).
            final MapType map = (MapType) type;
            return "MAP(" + canonical(map.getKeyType()) + ", " + canonical(map.getValueType())
                + StructuredTypes.notNullMark(type) + ")";
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
