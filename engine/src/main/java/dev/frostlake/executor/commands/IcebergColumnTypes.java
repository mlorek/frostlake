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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.FileType;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.GeometryType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredField;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.UuidType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;
import org.antlr.v4.runtime.misc.Interval;

import java.util.ArrayList;
import java.util.List;

/**
 * The types a column of an Iceberg table takes, which are the Iceberg format's and fewer than a table's
 * (live-verified):
 *
 * <pre>
 *   VARCHAR / STRING / TEXT / NVARCHAR, unsized or (134217728)   VARCHAR(134217728)
 *   any other length                                             For Iceberg tables, only max length (134,217,728) is
 *                                                                supported for 'VARCHAR(L)/STRING(L)' or use STRING directly.
 *   BINARY, unsized or (67108864)                                BINARY(67108864)
 *   any other length                                             For Iceberg tables, only max length (67,108,864) is
 *                                                                supported for 'BINARY(L)'. Alternatively, use BINARY directly.
 *   INT / INTEGER                                                NUMBER(10,0)
 *   BIGINT                                                       NUMBER(19,0)
 *   NUMBER(p,s) / DECIMAL(p,s) / NUMERIC(p,s)                    as written
 *   NUMBER / NUMBER(p) / DECIMAL / BYTEINT                       data type 'NUMBER/DECIMAL' without precision and scale is
 *                                                                not supported for Iceberg tables.
 *   TIME / TIMESTAMP_NTZ / TIMESTAMP_LTZ, unsized or (6)         the type at scale 6
 *   any other scale                                              Invalid time type scale specified for column 'C' with
 *                                                                data type 'TIME(3)'. ...
 *   FLOAT family, BOOLEAN, DATE, UUID                            as a table's
 *   CHAR / NCHAR / SMALLINT / TINYINT / VARBINARY / VECTOR ...   Unsupported data type 'char(3)' for iceberg tables.
 *   VARIANT / OBJECT / ARRAY / GEOGRAPHY / TIMESTAMP_TZ          Unsupported data type 'TIMESTAMP_TZ(9)' for iceberg tables.
 * </pre>
 *
 * A structured OBJECT, ARRAY or MAP holds the same types in its members. A name the format has no type for is echoed
 * as it was written; a type it knows and does not hold is echoed as the whole column's type reads. A table created
 * from a query takes its columns' types at the same widths, and a result's own NUMBER(p,s) stays as it is.
 */
final class IcebergColumnTypes {

    /** The one length a string column of an Iceberg table has. */
    static final int STRING_LENGTH = 134217728;
    /** The one length a binary column of an Iceberg table has. */
    static final int BINARY_LENGTH = 67108864;
    /** The one scale a time or a timestamp column of an Iceberg table has. */
    static final int TIME_SCALE = 6;

    private static final String PREFIX = "SQL Compilation error:\n";
    private static final String STRING_REFUSAL = "For Iceberg tables, only max length (134,217,728) is supported for"
        + " 'VARCHAR(L)/STRING(L)' or use STRING directly.";
    private static final String BINARY_REFUSAL = "For Iceberg tables, only max length (67,108,864) is supported for"
        + " 'BINARY(L)'. Alternatively, use BINARY directly.";
    private static final String NUMBER_REFUSAL = "data type 'NUMBER/DECIMAL' without precision and scale is not"
        + " supported for Iceberg tables.";

    private IcebergColumnTypes() {
    }

    /**
     * The type a column written in an Iceberg table's column list holds.
     *
     * @param name       the type as written
     * @param parameters its parameters, or null
     * @param parsed     the type a table's column of that spelling holds
     * @param column     the column's name
     * @return the Iceberg table's type for it
     */
    static DataType declared(final FrostlakeParser.DataTypeNameContext name,
                             final FrostlakeParser.TypeParametersContext parameters, final DataType parsed,
                             final String column) {
        final DataType held = written(name, parameters, parsed);
        refuseUnheld(held, held, column, true);
        return held;
    }

    /**
     * The type a column of an Iceberg table created from a query holds: a string and a binary at the format's one
     * length, everything else as the result declares it.
     *
     * @param result the result column's type
     * @param column the column's name
     * @return the Iceberg table's type for it
     */
    static DataType selected(final DataType result, final String column) {
        final DataType held = widened(result);
        refuseUnheld(held, held, column, false);
        return held;
    }

    /** Refuses a column list whose types an Iceberg table does not take, column by column in the order written. */
    static void validate(final FrostlakeParser.ColumnListContext columns, final ColumnDefinitionParser parser) {
        if (columns == null) {
            return;
        }
        for (final FrostlakeParser.ColumnOrConstraintContext item : columns.columnOrConstraint()) {
            if (item.columnDef() != null) {
                validate(item.columnDef(), parser);
            }
        }
    }

    /** Refuses a column definition whose type an Iceberg table does not take. */
    static void validate(final FrostlakeParser.ColumnDefContext column, final ColumnDefinitionParser parser) {
        declared(column.dataTypeName(), column.typeParameters(),
            parser.parseDataType(column.dataTypeName(), column.typeParameters()),
            ParseTreeText.namePartText(column.columnDefName()));
    }

    private static DataType written(final FrostlakeParser.DataTypeNameContext name,
                                    final FrostlakeParser.TypeParametersContext parameters, final DataType parsed) {
        if (name.MAP() != null && parsed instanceof MapType) {
            final MapType map = (MapType) parsed;
            return new MapType(written(name.mapKey, name.mapKeyParameters, map.getKeyType()),
                written(name.mapValue, name.mapValueParameters, map.getValueType()));
        }
        if (name.ARRAY() != null && parsed instanceof StructuredArrayType) {
            return new StructuredArrayType(written(name.dataTypeName(0),
                name.typeParameters().isEmpty() ? null : name.typeParameters(0),
                ((StructuredArrayType) parsed).getElementType()));
        }
        if (name.OBJECT() != null && parsed instanceof StructuredObjectType) {
            final List<StructuredField> declaredFields = ((StructuredObjectType) parsed).getFields();
            final List<StructuredField> fields = new ArrayList<>();
            if (name.structuredFieldList() != null) {
                final List<FrostlakeParser.StructuredFieldContext> writtenFields =
                    name.structuredFieldList().structuredField();
                for (int i = 0; i < writtenFields.size(); i++) {
                    final StructuredField field = declaredFields.get(i);
                    fields.add(new StructuredField(field.getName(), written(writtenFields.get(i).dataTypeName(),
                        writtenFields.get(i).typeParameters(), field.getDataType()), field.isNotNull()));
                }
            }
            return new StructuredObjectType(fields);
        }
        // The names the format has no type for, echoed as they were written.
        if (name.VECTOR() != null || name.INTERVAL() != null || name.FILE() != null || name.DECFLOAT() != null
                || name.SMALLINT() != null || name.TINYINT() != null || name.VARBINARY() != null
                || (name.CHAR() != null || name.CHARACTER() != null || name.NCHAR() != null)
                    && name.VARYING() == null) {
            throw unsupported(sourceText(name, parameters));
        }
        if (name.INTEGER() != null || name.INT() != null) {
            return new NumericType("NUMBER", 10, 0);
        }
        if (name.BIGINT() != null) {
            return new NumericType("NUMBER", 19, 0);
        }
        if (name.BYTEINT() != null || name.NUMBER() != null || name.DECIMAL() != null || name.NUMERIC() != null
                || name.DEC() != null) {
            if (parameters == null || parameters.INTEGER_LITERAL().size() < 2) {
                throw new RuntimeException(PREFIX + NUMBER_REFUSAL);
            }
            return parsed;
        }
        if (parsed instanceof UuidType) {
            return parsed;
        }
        if (parsed instanceof StringType) {
            if (sized(parameters) && ((StringType) parsed).getMaxLength() != STRING_LENGTH) {
                throw new RuntimeException(PREFIX + STRING_REFUSAL);
            }
            return new StringType("VARCHAR", STRING_LENGTH);
        }
        if (parsed instanceof BinaryType) {
            if (sized(parameters) && ((BinaryType) parsed).getMaxLength() != BINARY_LENGTH) {
                throw new RuntimeException(PREFIX + BINARY_REFUSAL);
            }
            return new BinaryType("BINARY", BINARY_LENGTH);
        }
        if (isTime(parsed) && !sized(parameters) && !isZoned(parsed)) {
            // A scale left out is the format's; one written is judged with the column's whole type.
            final DateTimeType time = (DateTimeType) parsed;
            return new DateTimeType(time.getName(), TIME_SCALE, time.hasTimeZone());
        }
        return parsed;
    }

    private static DataType widened(final DataType type) {
        if (type instanceof MapType) {
            final MapType map = (MapType) type;
            return new MapType(widened(map.getKeyType()), widened(map.getValueType()));
        }
        if (type instanceof StructuredArrayType) {
            return new StructuredArrayType(widened(((StructuredArrayType) type).getElementType()));
        }
        if (type instanceof StructuredObjectType) {
            final List<StructuredField> fields = new ArrayList<>();
            for (final StructuredField field : ((StructuredObjectType) type).getFields()) {
                fields.add(new StructuredField(field.getName(), widened(field.getDataType()), field.isNotNull()));
            }
            return new StructuredObjectType(fields);
        }
        if (type instanceof UuidType) {
            return type;
        }
        if (type instanceof StringType) {
            return new StringType("VARCHAR", STRING_LENGTH);
        }
        if (type instanceof BinaryType) {
            return new BinaryType("BINARY", BINARY_LENGTH);
        }
        return type;
    }

    /**
     * Refuses the first member of a column's type that an Iceberg table does not hold, naming the column's whole
     * type. A written TIMESTAMP_TZ is refused as a type whatever its scale; a query's is refused for its scale
     * first, as every time type is.
     */
    private static void refuseUnheld(final DataType whole, final DataType member, final String column,
                                     final boolean declared) {
        if (member instanceof MapType) {
            refuseUnheld(whole, ((MapType) member).getKeyType(), column, declared);
            refuseUnheld(whole, ((MapType) member).getValueType(), column, declared);
            return;
        }
        if (member instanceof StructuredArrayType) {
            refuseUnheld(whole, ((StructuredArrayType) member).getElementType(), column, declared);
            return;
        }
        if (member instanceof StructuredObjectType) {
            for (final StructuredField field : ((StructuredObjectType) member).getFields()) {
                refuseUnheld(whole, field.getDataType(), column, declared);
            }
            return;
        }
        if (isTime(member)) {
            if (isZoned(member) && declared) {
                throw unsupported(spelled(whole));
            }
            if (((DateTimeType) member).getPrecision() != TIME_SCALE) {
                throw new RuntimeException(PREFIX + "Invalid time type scale specified for column '" + column
                    + "' with data type '" + spelled(whole) + "'. Please consult the Snowflake documentation for"
                    + " supported scales of TIME and TIMESTAMP types in Iceberg tables.");
            }
            if (isZoned(member)) {
                throw unsupported(spelled(whole));
            }
            return;
        }
        if (member instanceof VariantType || member instanceof ObjectType || member instanceof ArrayType
                || member instanceof GeographyType || member instanceof GeometryType || member instanceof VectorType
                || member instanceof FileType || member instanceof IntervalDayTimeType
                || member instanceof IntervalYearMonthType) {
            throw unsupported(spelled(whole));
        }
    }

    private static boolean isTime(final DataType type) {
        return type instanceof DateTimeType && !"DATE".equalsIgnoreCase(type.getName());
    }

    private static boolean isZoned(final DataType type) {
        return "TIMESTAMP_TZ".equalsIgnoreCase(type.getName());
    }

    private static boolean sized(final FrostlakeParser.TypeParametersContext parameters) {
        return parameters != null && !parameters.INTEGER_LITERAL().isEmpty();
    }

    /** A type as the refusal names it: its canonical name, a GEOMETRY with the reference system it defaults to. */
    private static String spelled(final DataType type) {
        return type instanceof GeometryType ? "GEOMETRY(4326)" : SqlTypeNames.canonical(type);
    }

    private static String sourceText(final FrostlakeParser.DataTypeNameContext name,
                                     final FrostlakeParser.TypeParametersContext parameters) {
        final int stop = parameters != null ? parameters.getStop().getStopIndex() : name.getStop().getStopIndex();
        return name.getStart().getInputStream().getText(Interval.of(name.getStart().getStartIndex(), stop));
    }

    private static RuntimeException unsupported(final String type) {
        return new RuntimeException(PREFIX + "Unsupported data type '" + type + "' for iceberg tables.");
    }
}
