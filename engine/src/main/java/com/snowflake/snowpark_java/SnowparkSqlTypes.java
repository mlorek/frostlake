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

package com.snowflake.snowpark_java;

import com.snowflake.snowpark_java.types.DataType;
import com.snowflake.snowpark_java.types.DecimalType;

import java.util.Locale;

/**
 * The name a Snowpark type has in SQL, which is not the name Snowpark prints for it — Snowpark calls a
 * string column {@code StringType} while a CAST has to say {@code VARCHAR}.
 *
 * <p>Internal on purpose. The mapping used to hang off {@code DataType.sqlTypeName()}, but the real
 * Snowpark {@code DataType} publishes only {@code typeName}, {@code toString}, {@code equals} and
 * {@code hashCode} (live-verified by reflection inside a handler on the account), so a handler written
 * against the stub could call a method that does not exist on Snowflake. The vocabulary lives here
 * instead, where nothing outside the package can reach it.
 */
final class SnowparkSqlTypes {

    private SnowparkSqlTypes() {
    }

    /** The SQL spelling to put in a CAST for {@code type}. */
    static String of(final DataType type) {
        if (type instanceof DecimalType) {
            final DecimalType decimal = (DecimalType) type;
            return "NUMBER(" + decimal.getPrecision() + ", " + decimal.getScale() + ")";
        }
        final String name = type.typeName();
        if ("StringType".equals(name)) {
            return "VARCHAR";
        }
        if ("ByteType".equals(name) || "ShortType".equals(name)
                || "IntegerType".equals(name) || "LongType".equals(name)) {
            return "NUMBER";
        }
        if ("FloatType".equals(name) || "DoubleType".equals(name)) {
            return "DOUBLE";
        }
        if ("StructType".equals(name) || "MapType".equals(name)) {
            return "OBJECT";
        }
        final String bare = name.endsWith("Type") ? name.substring(0, name.length() - "Type".length()) : name;
        return bare.toUpperCase(Locale.ROOT);
    }
}
