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

package dev.frostlake.executor;

import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.StructuredTypes;
import dev.frostlake.types.UuidType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;

import java.util.Locale;

/**
 * Snowflake's compile-time type matching of a value against the column it is written to (live-verified
 * matrix). A source whose static type family cannot implicitly convert to the column's family is refused
 * before any row is written, whether an INSERT, an UPDATE, either MERGE branch or a column-listed CTAS
 * writes it, and over an empty table too. VARCHAR still converts to numbers, booleans, temporals and BINARY
 * at row time, and a VARIANT casts to any scalar or container at row time. But a VARCHAR, temporal or
 * BINARY source never reaches a VARIANT column, containers never leave their own family except through
 * VARIANT, and BINARY takes nothing but strings. A type with no audited static family is left alone: the
 * channel never guesses.
 *
 * <p>The structured types are families of their own, structured OBJECT and MAP one and structured ARRAY the
 * other, as their casts sort them. A column of one takes only a value of its family, which is then converted to
 * the column's type at row time. Snowflake's documentation: a semi-structured value "can't be coerced" to a
 * structured one, nor the reverse, and a VARCHAR can't be coerced to a structured type either. So a plain OBJECT,
 * ARRAY or VARIANT needs an explicit cast to reach a structured column, and a structured value one to reach a
 * semi-structured column.
 */
public final class ColumnTypeFamilies {

    /** The prefix of the two interval families' names. */
    private static final String INTERVAL_FAMILY = "INTERVAL";

    /** Structured OBJECT and MAP, the family their casts share. */
    private static final String STRUCTURED_OBJECT_FAMILY = "STRUCTURED_OBJECT";

    /** Structured ARRAY. */
    private static final String STRUCTURED_ARRAY_FAMILY = "STRUCTURED_ARRAY";

    private ColumnTypeFamilies() {
    }

    /**
     * Refuse a value written to {@code target} whose static type the column's family does not take, with the
     * write's sentence, {@code Expression type does not match column data type, expecting NUMBER(38,0) but
     * got BOOLEAN for column N}. A TIME written to a TIMESTAMP column has a sentence of its own.
     *
     * @param target     the column written
     * @param sourceType the value's static type, or null when the channel cannot type it
     */
    public static void rejectMismatch(final TableColumn target, final DataType sourceType) {
        if (target == null || sourceType == null) {
            return;
        }
        // A VECTOR column takes a VECTOR of its own type and nothing else: not an ARRAY, a VARIANT or a text that a
        // cast would convert, nor a VECTOR of another element type, "expecting VECTOR(FLOAT, 3) but got ARRAY for
        // column V" (live-verified, for INSERT, UPDATE and MERGE).
        if (target.getDataType() instanceof VectorType && !target.getDataType().equals(sourceType)) {
            throw new RuntimeException(SqlCompilationError.of(
                "Expression type does not match column data type, expecting "
                + spell(target.getDataType()) + " but got " + spell(sourceType)
                + " for column " + target.getName()));
        }
        final String targetFamily = family(target.getDataType());
        final String sourceFamily = family(sourceType);
        if (targetFamily == null || sourceFamily == null) {
            return;
        }
        // A semi-structured value is no structured one: an ARRAY_CONSTRUCT written to an ARRAY(INT) column is
        // "incompatible types: [ARRAY] and [ARRAY(NUMBER(38,0))]", and an OBJECT or a VARIANT in an OBJECT, a MAP or a
        // structured ARRAY column likewise (live-verified, for INSERT, UPDATE, MERGE and a CTAS column list).
        if ("TIMESTAMP".equals(targetFamily) && "TIME".equals(sourceFamily)
                || "STRING".equals(targetFamily) && sourceFamily.startsWith(INTERVAL_FAMILY)
                || isStructuredFamily(targetFamily) && ("ARRAY".equals(sourceFamily) || "OBJECT".equals(sourceFamily)
                    || "VARIANT".equals(sourceFamily))) {
            throw new RuntimeException(SqlCompilationError.of("incompatible types: ["
                + spell(sourceType) + "] and [" + spell(target.getDataType()) + "]"));
        }
        // A structured value reaches a structured column only when it coerces to the column's type, its elements
        // to the column's elements (see StructuredTypes.coercible).
        if (!accepts(targetFamily, sourceFamily) || zonedIntoUnzoned(target.getDataType(), sourceType)
                || isStructuredFamily(targetFamily) && !StructuredTypes.coercible(sourceType, target.getDataType())) {
            throw new RuntimeException(SqlCompilationError.of(
                "Expression type does not match column data type, expecting "
                + spell(target.getDataType()) + " but got " + spell(sourceType)
                + " for column " + target.getName()));
        }
    }

    /**
     * Whether a TIMESTAMP_TZ is written to a TIMESTAMP_LTZ or a TIMESTAMP_NTZ column, the one pair inside the
     * timestamp family that live refuses while compiling: an NTZ or an LTZ reaches every flavour, and a TZ reaches
     * a TZ column (live-verified, a typed NULL included).
     */
    private static boolean zonedIntoUnzoned(final DataType target, final DataType source) {
        return "TIMESTAMP_TZ".equals(flavour(source)) && !"TIMESTAMP_TZ".equals(flavour(target))
            && "TIMESTAMP".equals(family(target));
    }

    /** A timestamp type's flavour as the refusal spells it, without its precision; null for another type. */
    private static String flavour(final DataType type) {
        if (!(type instanceof DateTimeType) || !"TIMESTAMP".equals(family(type))) {
            return null;
        }
        final String spelled = spell(type);
        final int paren = spelled.indexOf('(');
        return paren < 0 ? spelled : spelled.substring(0, paren);
    }

    /**
     * A value that is a subquery of more than one column, typed as the ROW of its items, which no column
     * takes: {@code UPDATE t SET a = (SELECT a, b FROM u)} is "Expression type does not match column data type,
     * expecting NUMBER(38,0) but got ROW(NUMBER(38,0), NUMBER(38,0)) for column A" (live-verified).
     *
     * @param target  the column written
     * @param rowType the value's ROW type, as printed
     */
    public static void rejectRowValue(final TableColumn target, final String rowType) {
        throw new RuntimeException(SqlCompilationError.of(
            "Expression type does not match column data type, expecting "
            + spell(target.getDataType()) + " but got " + rowType + " for column " + target.getName()));
    }

    /**
     * The conversion families the type-matching rule reasons in; null means the type takes no part
     * in the rule (GEOGRAPHY, VECTOR, FILE and other engine-specific types keep their own paths).
     */
    private static String family(final DataType type) {
        // Ahead of ARRAY and OBJECT, which the structured array and object types extend.
        if (type instanceof StructuredObjectType || type instanceof MapType) {
            return STRUCTURED_OBJECT_FAMILY;
        }
        if (type instanceof StructuredArrayType) {
            return STRUCTURED_ARRAY_FAMILY;
        }
        if (type instanceof NumericType) {
            return "NUMBER";
        }
        if (type instanceof StringType) {
            return "STRING";
        }
        if (type instanceof BooleanType) {
            return "BOOLEAN";
        }
        if (type instanceof DateTimeType) {
            final String name = type.getName().toUpperCase(Locale.ROOT);
            if (name.equals("DATE")) {
                return "DATE";
            }
            if (name.equals("TIME")) {
                return "TIME";
            }
            return "TIMESTAMP";
        }
        if (type instanceof BinaryType) {
            return "BINARY";
        }
        if (type instanceof ArrayType) {
            return "ARRAY";
        }
        if (type instanceof ObjectType) {
            return "OBJECT";
        }
        if (type instanceof VariantType) {
            return "VARIANT";
        }
        if (type instanceof IntervalDayTimeType) {
            return INTERVAL_FAMILY + " DAY TIME";
        }
        if (type instanceof IntervalYearMonthType) {
            return INTERVAL_FAMILY + " YEAR MONTH";
        }
        return null;
    }

    /** Whether a source family reaches a target column family without a compile refusal. */
    private static boolean accepts(final String target, final String source) {
        if (target.equals(source)) {
            return true;
        }
        if (target.startsWith(INTERVAL_FAMILY)) {
            // An interval column takes its own family and a text read in its fields, nothing else: a number, a
            // VARIANT, a DATE and the other interval family are refused while compiling (live-verified).
            return "STRING".equals(source);
        }
        if (source.startsWith(INTERVAL_FAMILY)) {
            // An interval reaches no other family's column: a NUMBER or a VARIANT column refuses it, and a text
            // one in the incompatible-types sentence above (live-verified).
            return false;
        }
        if ("VARIANT".equals(source)) {
            // A VARIANT source casts at row time into every family except BINARY and the structured ones.
            return !"BINARY".equals(target) && !isStructuredFamily(target);
        }
        if ("NUMBER".equals(target) || "BOOLEAN".equals(target)) {
            return "STRING".equals(source)
                || ("BOOLEAN".equals(target) && "NUMBER".equals(source));
        }
        if ("STRING".equals(target)) {
            return "NUMBER".equals(source) || "BOOLEAN".equals(source) || "DATE".equals(source)
                || "TIME".equals(source) || "TIMESTAMP".equals(source);
        }
        if ("DATE".equals(target)) {
            return "STRING".equals(source) || "TIMESTAMP".equals(source);
        }
        if ("TIME".equals(target)) {
            return "STRING".equals(source) || "TIMESTAMP".equals(source);
        }
        if ("TIMESTAMP".equals(target)) {
            return "STRING".equals(source) || "DATE".equals(source);
        }
        if ("BINARY".equals(target)) {
            return "STRING".equals(source);
        }
        if ("VARIANT".equals(target)) {
            return "NUMBER".equals(source) || "BOOLEAN".equals(source)
                || "ARRAY".equals(source) || "OBJECT".equals(source);
        }
        // ARRAY and OBJECT accept only themselves and VARIANT, both handled above; a structured family only
        // itself.
        return false;
    }

    private static boolean isStructuredFamily(final String family) {
        return STRUCTURED_OBJECT_FAMILY.equals(family) || STRUCTURED_ARRAY_FAMILY.equals(family);
    }

    /** A type spelled the way live's type-matching refusal spells it, parameters included. */
    static String spell(final DataType type) {
        if (StructuredTypes.isStructured(type)) {
            return StructuredTypes.describe(type);
        }
        if (type instanceof NumericType) {
            final NumericType numeric = (NumericType) type;
            final String name = numeric.getName().toUpperCase(Locale.ROOT);
            if (name.equals("FLOAT") || name.equals("DOUBLE")) {
                return "FLOAT";
            }
            if (NumericType.isDecfloat(numeric)) {
                return "DECFLOAT";
            }
            return "NUMBER(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
        }
        if (type instanceof UuidType) {
            // A UUID column is named by its own type, which takes no length (UuidType).
            return "UUID";
        }
        if (type instanceof StringType) {
            final int length = ((StringType) type).getMaxLength();
            return "VARCHAR(" + (length > 0 ? length : 16777216) + ")";
        }
        if (type instanceof DateTimeType) {
            final String name = type.getName().toUpperCase(Locale.ROOT);
            if (name.equals("DATE")) {
                return "DATE";
            }
            final int precision = ((DateTimeType) type).getPrecision();
            if (name.equals("TIME")) {
                return "TIME(" + precision + ")";
            }
            if (name.equals("DATETIME") || name.equals("TIMESTAMP")) {
                return "TIMESTAMP_NTZ(" + precision + ")";
            }
            return name + "(" + precision + ")";
        }
        if (type instanceof BinaryType) {
            // A declared width is spelled as declared; an unsized binary (TO_BINARY's) at the 64MB maximum.
            return ((BinaryType) type).refusalText();
        }
        return type.getName().toUpperCase(Locale.ROOT);
    }

    /**
     * Refuse a column-listed CTAS's source column whose family the declared column does not take, with the
     * sentence a CTAS gives: {@code CREATE TABLE t (n NUMBER) AS SELECT 1 = 1} is "incompatible types:
     * [BOOLEAN] and [NUMBER(38,0)]" (live-verified).
     *
     * @param targetType the declared column's type
     * @param sourceType the source column's static type, or null when the channel cannot type it
     */
    public static void rejectIncompatible(final DataType targetType, final DataType sourceType) {
        if (targetType == null || sourceType == null) {
            return;
        }
        final String targetFamily = family(targetType);
        final String sourceFamily = family(sourceType);
        if (targetFamily == null || sourceFamily == null) {
            return;
        }
        if (!accepts(targetFamily, sourceFamily) || "TIMESTAMP".equals(targetFamily) && "TIME".equals(sourceFamily)
                || zonedIntoUnzoned(targetType, sourceType)) {
            throw new RuntimeException(SqlCompilationError.of("incompatible types: [" + spell(sourceType)
                + "] and [" + spell(targetType) + "]"));
        }
    }
}
