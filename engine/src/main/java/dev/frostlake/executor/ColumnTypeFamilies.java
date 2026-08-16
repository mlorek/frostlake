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
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

/**
 * Snowflake's compile-time type matching of a value against the column it is written to (live-verified
 * matrix). A source whose static type family cannot implicitly convert to the column's family is refused
 * before any row is written, whether an INSERT, an UPDATE, either MERGE branch or a column-listed CTAS
 * writes it, and over an empty table too. VARCHAR still converts to numbers, booleans, temporals and BINARY
 * at row time, and a VARIANT casts to any scalar or container at row time. But a VARCHAR, temporal or
 * BINARY source never reaches a VARIANT column, containers never leave their own family except through
 * VARIANT, and BINARY takes nothing but strings. A type with no audited static family is left alone: the
 * channel never guesses.
 */
public final class ColumnTypeFamilies {

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
        final String targetFamily = family(target.getDataType());
        final String sourceFamily = family(sourceType);
        if (targetFamily == null || sourceFamily == null) {
            return;
        }
        if ("TIMESTAMP".equals(targetFamily) && "TIME".equals(sourceFamily)) {
            throw new RuntimeException(SqlCompilationError.of("incompatible types: ["
                + spell(sourceType) + "] and [" + spell(target.getDataType()) + "]"));
        }
        if (!accepts(targetFamily, sourceFamily)) {
            throw new RuntimeException(SqlCompilationError.of(
                "Expression type does not match column data type, expecting "
                + spell(target.getDataType()) + " but got " + spell(sourceType)
                + " for column " + target.getName()));
        }
    }

    /**
     * The conversion families the type-matching rule reasons in; null means the type takes no part
     * in the rule (GEOGRAPHY, VECTOR, FILE and other engine-specific types keep their own paths).
     */
    private static String family(final DataType type) {
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
            final String name = type.getName().toUpperCase();
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
        return null;
    }

    /** Whether a source family reaches a target column family without a compile refusal. */
    private static boolean accepts(final String target, final String source) {
        if (target.equals(source)) {
            return true;
        }
        if ("VARIANT".equals(source)) {
            // A VARIANT source casts at row time into every family except BINARY.
            return !"BINARY".equals(target);
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
        // ARRAY and OBJECT accept only themselves and VARIANT, both handled above.
        return false;
    }

    /** A type spelled the way live's type-matching refusal spells it, parameters included. */
    static String spell(final DataType type) {
        if (type instanceof NumericType) {
            final NumericType numeric = (NumericType) type;
            final String name = numeric.getName().toUpperCase();
            if (name.equals("FLOAT") || name.equals("DOUBLE")) {
                return "FLOAT";
            }
            return "NUMBER(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
        }
        if (type instanceof StringType) {
            final int length = ((StringType) type).getMaxLength();
            return "VARCHAR(" + (length > 0 ? length : 16777216) + ")";
        }
        if (type instanceof DateTimeType) {
            final String name = type.getName().toUpperCase();
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
        return type.getName().toUpperCase();
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
        if (!accepts(targetFamily, sourceFamily) || "TIMESTAMP".equals(targetFamily) && "TIME".equals(sourceFamily)) {
            throw new RuntimeException(SqlCompilationError.of("incompatible types: [" + spell(sourceType)
                + "] and [" + spell(targetType) + "]"));
        }
    }
}
