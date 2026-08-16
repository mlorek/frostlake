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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.FileType;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.GeometryType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredField;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorElementType;
import dev.frostlake.types.VectorType;

import java.util.ArrayList;
import java.util.List;

/**
 * The one {@code dataTypeName} parse-tree &rarr; {@link DataType} mapping, shared by column / parameter
 * definitions ({@link ColumnDefinitionParser}) and by expression casts (the expression AST builder), so
 * a type spelled in a CREATE TABLE and the same type spelled in a CAST produce the same object.
 *
 * <p>It reads the STRUCTURED spellings — {@code OBJECT(x VARCHAR, ...)}, {@code ARRAY(INT)},
 * {@code MAP(k, v)} — into {@link StructuredObjectType} / {@link StructuredArrayType} /
 * {@link MapType} rather than collapsing them onto the plain semi-structured singletons, because
 * Snowflake treats structured and plain semi-structured types as DIFFERENT types.
 */
public final class DataTypeParser {

    private DataTypeParser() {
    }

    public static DataType parse(final FrostlakeParser.DataTypeNameContext ctx,
                                 final FrostlakeParser.TypeParametersContext typeParams) {
        return parse(ctx, typeParams, DDL_STRING_DEFAULT);
    }

    /** The default length of a bare VARCHAR in a COLUMN definition, as INFORMATION_SCHEMA reports it. */
    private static final int DDL_STRING_DEFAULT = 16777216;

    /**
     * The default length of a bare VARCHAR inside a CAST TARGET. Live renders a cast-produced
     * structured type with the LONGER conversion default —
     * {@code ARRAY_AGG(CAST(... AS OBJECT(x VARCHAR)))} is refused as
     * "(OBJECT(x VARCHAR(134217728)))" where the same type DECLARED on a column reports
     * {@code VARCHAR(16777216)}.
     */
    public static final int CAST_STRING_DEFAULT = 134217728;

    /** {@link #parse} with the bare-VARCHAR default the CONTEXT implies — {@link #CAST_STRING_DEFAULT}
     *  for a cast target, {@code DDL_STRING_DEFAULT} for a column definition. */
    public static DataType parse(final FrostlakeParser.DataTypeNameContext ctx,
                                 final FrostlakeParser.TypeParametersContext typeParams,
                                 final int bareStringDefault) {
        int precision = 9; // Default precision for timestamp types

        // Extract precision if typeParameters present
        if (typeParams != null && typeParams.INTEGER_LITERAL() != null && typeParams.INTEGER_LITERAL().size() > 0) {
            precision = Integer.parseInt(typeParams.INTEGER_LITERAL(0).getText());
        }

        // VECTOR(FLOAT|INT, n) must be classified BEFORE the plain numeric checks: its element-type
        // token (FLOAT / INT) lives in the same context, so the FLOAT/INT branches would shadow it.
        if (ctx.VECTOR() != null) {
            final VectorElementType vectorElem = ctx.INT() != null
                ? VectorElementType.INT
                : VectorElementType.FLOAT;
            final int vectorDim = ctx.INTEGER_LITERAL() != null
                ? Integer.parseInt(ctx.INTEGER_LITERAL().getText())
                : 1;
            return new VectorType(vectorElem, vectorDim);
        }
        // MAP(k, v) likewise holds its key/value types in nested dataTypeName contexts, so it must be
        // recognized before the leaf-token branches those nested types would otherwise trigger.
        if (ctx.MAP() != null && ctx.dataTypeName().size() == 2) {
            return new MapType(nested(ctx.dataTypeName(0), null, bareStringDefault),
                nested(ctx.dataTypeName(1), null, bareStringDefault));
        }
        if (ctx.GEOGRAPHY() != null) return GeographyType.GEOGRAPHY;
        if (ctx.GEOMETRY() != null) return GeometryType.GEOMETRY;
        if (ctx.FILE() != null) {
            // FILE takes no type parameters — live, both `FILE(10)` and `FILE()` fail with
            // "syntax error ... unexpected '('". The grammar shares `dataTypeName typeParameters?` across
            // every type, so the parentheses have to be rejected here.
            if (typeParams != null) {
                throw new RuntimeException("SQL compilation error:\nsyntax error unexpected '('. "
                    + "The FILE type takes no parameters.");
            }
            return FileType.FILE;
        }
        if (ctx.ARRAY() != null) {
            // ARRAY(INT) is a STRUCTURED array; bare ARRAY stays the plain semi-structured singleton.
            if (ctx.dataTypeName().isEmpty()) {
                // `ARRAY()` has no meaning: empty type parentheses are a CHARACTER-type leniency only
                // (live: `VARCHAR()` and `NVARCHAR()` cast fine, while `ARRAY()`, `MAP()` and `NUMBER()`
                // are syntax errors). The grammar reaches here as bare ARRAY plus an empty
                // `typeParameters`, so the emptiness is what has to be rejected.
                if (typeParams != null && (typeParams.INTEGER_LITERAL() == null
                        || typeParams.INTEGER_LITERAL().isEmpty())) {
                    throw new RuntimeException("SQL compilation error:\nsyntax error unexpected ')'. "
                        + "ARRAY takes an element type, as in ARRAY(INTEGER).");
                }
                return ArrayType.ARRAY;
            }
            return new StructuredArrayType(nested(ctx.dataTypeName(0), ctx.typeParameters(), bareStringDefault));
        }
        if (ctx.OBJECT() != null) {
            // `OBJECT` (no parentheses) is the plain semi-structured type; `OBJECT()` is the ZERO-FIELD
            // STRUCTURED type, which is a distinct thing live (SYSTEM$TYPEOF → `OBJECT()`, an empty
            // object casts to it, a non-empty one fails the schema check). Both give a null
            // structuredFieldList, so the parenthesis is what separates them.
            if (ctx.LPAREN() == null) {
                return ObjectType.OBJECT;
            }
            return ctx.structuredFieldList() == null
                ? new StructuredObjectType(new ArrayList<StructuredField>())
                : new StructuredObjectType(parseFields(ctx.structuredFieldList(), bareStringDefault));
        }
        // Every integer alias IS NUMBER(38,0) in Snowflake — live, INT / INTEGER / BIGINT /
        // SMALLINT / TINYINT / BYTEINT all report NUMBER with precision 38 and scale 0 in
        // INFORMATION_SCHEMA.COLUMNS, and SYSTEM$TYPEOF of a cast to any of them is `NUMBER(38,0)`.
        // Snowflake keeps no narrower range for the small spellings, so the constants below now differ
        // only in the name they carry — see {@link NumericType}.
        // Every integer alias IS NUMBER(38,0) — live keeps no trace of the spelling, on any surface:
        // DESCRIBE, SHOW COLUMNS, INFORMATION_SCHEMA and a result column all answer NUMBER(38,0) for
        // INT, INTEGER, BIGINT, SMALLINT, TINYINT and BYTEINT alike (measured, all six).
        if (ctx.INTEGER() != null || ctx.INT() != null || ctx.BIGINT() != null
                || ctx.SMALLINT() != null || ctx.TINYINT() != null || ctx.BYTEINT() != null) {
            return NumericType.NUMBER;
        }
        // NUMERIC and DEC are plain NUMBER synonyms — live, a NUMERIC(7,3) / DEC(7,3) column
        // reports DATA_TYPE NUMBER with precision 7 and scale 3, exactly like NUMBER(7,3), and
        // `1.5::NUMERIC(8,5)` is `1.50000`. Missing them here made a NUMERIC column a VARCHAR, which
        // silently dropped the declared scale from every arithmetic result over it.
        if (ctx.NUMBER() != null || ctx.DECIMAL() != null || ctx.NUMERIC() != null || ctx.DEC() != null) {
            if (typeParams != null && typeParams.INTEGER_LITERAL() != null && !typeParams.INTEGER_LITERAL().isEmpty()) {
                final int numberScale = typeParams.INTEGER_LITERAL().size() > 1
                    ? Integer.parseInt(typeParams.INTEGER_LITERAL(1).getText()) : 0;
                return new NumericType("NUMBER", precision, numberScale);
            }
            return NumericType.NUMBER;
        }
        // DECFLOAT (decimal floating point) is approximated by DOUBLE — the engine has no
        // arbitrary-exponent decimal representation.
        if (ctx.DECFLOAT() != null) return NumericType.DOUBLE;
        if (ctx.FLOAT() != null || ctx.FLOAT4() != null || ctx.FLOAT8() != null || ctx.REAL() != null) return NumericType.FLOAT;
        if (ctx.DOUBLE() != null) return NumericType.FLOAT;   // DOUBLE and DOUBLE PRECISION are FLOAT
        // The character family collapses onto two Snowflake types, VARCHAR and CHAR, and the alias only
        // decides the DEFAULT length — live: VARCHAR / STRING / TEXT / NVARCHAR / NVARCHAR2
        // and any `... VARYING` spelling default to 16,777,216, while the fixed-length CHAR / CHARACTER /
        // NCHAR default to 1 (`'abc'::CHARACTER` fails "String 'abc' is too long and would be
        // truncated"). An explicit length applies to either family. SYSTEM$TYPEOF reports the collapsed
        // name, never the alias: `'x'::NCHAR` is `VARCHAR(1)`, `'x'::CHARACTER VARYING` is `VARCHAR`.
        if (ctx.VARCHAR() != null || ctx.STRING() != null || ctx.TEXT() != null
                || ctx.NVARCHAR() != null || ctx.NVARCHAR2() != null || ctx.VARYING() != null) {
            return hasTypeLength(typeParams) ? new StringType("VARCHAR", precision)
                : bareStringDefault == DDL_STRING_DEFAULT
                    ? StringType.VARCHAR : new StringType("VARCHAR", bareStringDefault);
        }
        // CHAR is not a fixed-width type on Snowflake: CHAR(3) is VARCHAR(3), reported that way
        // everywhere and not padded (SHOW COLUMNS says fixed:false for it). Bare CHAR keeps its
        // one-character length.
        if (ctx.CHAR() != null || ctx.CHARACTER() != null || ctx.NCHAR() != null) {
            return new StringType("VARCHAR", hasTypeLength(typeParams) ? precision : 1);
        }
        if (ctx.BOOLEAN() != null) return BooleanType.BOOLEAN;
        if (ctx.DATE() != null) return DateTimeType.DATE;
        if (ctx.DATETIME() != null) return new DateTimeType("TIMESTAMP_NTZ", precision, false);
        if (ctx.TIMESTAMP_NTZ() != null || ctx.TIMESTAMPNTZ() != null) return new DateTimeType("TIMESTAMP_NTZ", precision, false);
        if (ctx.TIMESTAMP_LTZ() != null || ctx.TIMESTAMPLTZ() != null) return new DateTimeType("TIMESTAMP_LTZ", precision, true);
        if (ctx.TIMESTAMP_TZ() != null || ctx.TIMESTAMPTZ() != null) return new DateTimeType("TIMESTAMP_TZ", precision, true);
        // The whole TIMESTAMP family has to precede the bare TIME check: the worded
        // `TIMESTAMP WITH LOCAL TIME ZONE` spelling carries a TIME token of its own, so a TIME-first
        // order classified it as TIME. Live it is TIMESTAMP_LTZ(9).
        if (ctx.TIMESTAMP() != null) {
            return ctx.LOCAL() != null
                ? new DateTimeType("TIMESTAMP_LTZ", precision, true)
                : new DateTimeType("TIMESTAMP", precision, false);
        }
        // TIME carries its declared precision like the rest of the family — DESCRIBE spells it back
        // as TIME(3) when the column said so, and TIME(9) when it did not.
        if (ctx.TIME() != null) {
            return hasTypeLength(typeParams) ? new DateTimeType("TIME", precision, false)
                : DateTimeType.TIME;
        }
        if (ctx.VARIANT() != null) return VariantType.VARIANT;
        if (ctx.BINARY() != null) {
            return hasTypeLength(typeParams) ? new BinaryType("BINARY", precision) : BinaryType.BINARY;
        }
        // VARBINARY stays its own name in the CATALOG even though DESCRIBE and INFORMATION_SCHEMA
        // both spell it BINARY: SHOW COLUMNS reports fixed:true for BINARY and fixed:false for
        // VARBINARY, so the two are not interchangeable here (measured on the account).
        if (ctx.VARBINARY() != null) {
            return hasTypeLength(typeParams) ? new BinaryType("VARBINARY", precision) : BinaryType.VARBINARY;
        }
        if (ctx.UUID() != null) return new StringType("UUID", 36);
        // Every `dataTypeName` alternative is classified above, so this is unreachable — and it has to
        // STAY unreachable. It used to `return StringType.VARCHAR`, which turned any grammar token
        // nobody had wired up here into a silent VARCHAR: that is how NUMERIC, CHARACTER, NCHAR,
        // NVARCHAR, TIMESTAMPLTZ and TIMESTAMPTZ all became text columns. Throwing turns the next such
        // omission into a visible failure instead of a wrong-typed column, and Snowflake's own wording
        // for a type name it does not know is "Unsupported data type 'X'." (live).
        throw new RuntimeException("SQL compilation error:\nUnsupported data type '" + ctx.getText() + "'.");
    }

    /**
     * The declared fields of an {@code OBJECT(...)} type. Field names are kept VERBATIM (see
     * {@link SqlIdentifiers#verbatim}); a duplicate name is a Snowflake compile error (live:
     * {@code CAST(... AS OBJECT(x VARCHAR, x INT))} fails "Duplicate field name 'x'").
     */
    private static List<StructuredField> parseFields(final FrostlakeParser.StructuredFieldListContext ctx,
                                                     final int bareStringDefault) {
        final List<StructuredField> fields = new ArrayList<>();
        for (final FrostlakeParser.StructuredFieldContext field : ctx.structuredField()) {
            // A field name is either a regular identifier or one of the keyword names the grammar
            // admits in this position (INNER, JOIN, …) — those read verbatim like unquoted text.
            final FrostlakeParser.StructuredFieldNameContext nameCtx = field.structuredFieldName();
            final String name = nameCtx.identifier() != null
                ? SqlIdentifiers.verbatim(nameCtx.identifier())
                : nameCtx.getText();
            for (final StructuredField existing : fields) {
                if (existing.getName().equals(name)) {
                    throw new RuntimeException("Duplicate field name '" + name + "'");
                }
            }
            fields.add(new StructuredField(name,
                nested(field.dataTypeName(), field.typeParameters(), bareStringDefault),
                field.NOT() != null));
        }
        return fields;
    }

    /**
     * A type in a NESTED position — a structured {@code ARRAY(...)} element, an {@code OBJECT(...)} field
     * or a {@code MAP(...)} key/value. Identical to {@link #parse} except that FILE is rejected: live
     * {@code ARRAY(FILE)}, {@code OBJECT(x FILE)} and {@code MAP(VARCHAR, FILE)} all fail with
     * "Unsupported data type 'FILE'." even though a top-level {@code f FILE} column is accepted.
     */
    private static DataType nested(final FrostlakeParser.DataTypeNameContext ctx,
                                   final FrostlakeParser.TypeParametersContext typeParams,
                                   final int bareStringDefault) {
        if (ctx.FILE() != null) {
            throw new RuntimeException("SQL compilation error:\nUnsupported data type 'FILE'.");
        }
        return parse(ctx, typeParams, bareStringDefault);
    }

    private static boolean hasTypeLength(final FrostlakeParser.TypeParametersContext typeParams) {
        return typeParams != null && typeParams.INTEGER_LITERAL() != null
            && !typeParams.INTEGER_LITERAL().isEmpty();
    }
}
