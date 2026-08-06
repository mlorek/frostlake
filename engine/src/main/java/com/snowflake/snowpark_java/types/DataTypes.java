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

package com.snowflake.snowpark_java.types;

/**
 * The type instances a handler names, e.g. {@code new StructField("id", DataTypes.LongType)}.
 *
 * <p>Each constant is typed as its own class rather than as {@link DataType}, matching Snowpark, so
 * {@code StringType s = DataTypes.StringType} compiles.
 */
public final class DataTypes {

    public static final BinaryType BinaryType = new BinaryType();
    public static final BooleanType BooleanType = new BooleanType();
    public static final ByteType ByteType = new ByteType();
    public static final DateType DateType = new DateType();
    public static final DoubleType DoubleType = new DoubleType();
    public static final FloatType FloatType = new FloatType();
    public static final GeographyType GeographyType = new GeographyType();
    public static final GeometryType GeometryType = new GeometryType();
    public static final IntegerType IntegerType = new IntegerType();
    public static final LongType LongType = new LongType();
    public static final ShortType ShortType = new ShortType();
    public static final StringType StringType = new StringType();
    public static final TimeType TimeType = new TimeType();
    public static final TimestampType TimestampType = new TimestampType();
    public static final VariantType VariantType = new VariantType();

    private DataTypes() {
    }

    public static DecimalType createDecimalType(final int precision, final int scale) {
        return new DecimalType(precision, scale);
    }

    public static ArrayType createArrayType(final DataType elementType) {
        return new ArrayType(elementType);
    }

    public static MapType createMapType(final DataType keyType, final DataType valueType) {
        return new MapType(keyType, valueType);
    }

    public static StructType createStructType(final StructField[] fields) {
        return new StructType(fields);
    }
}
