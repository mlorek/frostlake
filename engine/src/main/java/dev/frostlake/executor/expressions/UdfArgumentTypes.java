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

package dev.frostlake.executor.expressions;

import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredTypes;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which argument types a user-defined function's parameter refuses while the query compiles, before any
 * row is read. The pairs are the measured ones (live-verified, every cell); every other pair binds and is
 * converted as the value arrives, so a text into a NUMBER parameter is parsed then and a VARIANT into a
 * DATE one is cast then:
 *
 * <pre>
 *   parameter            refuses
 *   VARCHAR              ARRAY, OBJECT, BINARY, GEOGRAPHY, VECTOR
 *   NUMBER, FLOAT        BOOLEAN, DATE, TIME, TIMESTAMP_*, ARRAY, OBJECT, BINARY, GEOGRAPHY, VECTOR
 *   BOOLEAN              DATE, TIME, TIMESTAMP_*, ARRAY, OBJECT, BINARY, GEOGRAPHY, VECTOR
 *   DATE                 NUMBER, FLOAT, BOOLEAN, TIME, ARRAY, OBJECT, BINARY, GEOGRAPHY, VECTOR
 *   TIMESTAMP_NTZ, _LTZ  NUMBER, FLOAT, BOOLEAN, TIMESTAMP_TZ, ARRAY, OBJECT, BINARY, GEOGRAPHY, VECTOR
 *   TIMESTAMP_TZ         NUMBER, FLOAT, BOOLEAN, ARRAY, OBJECT, BINARY, GEOGRAPHY, VECTOR
 *   TIME                 NUMBER, FLOAT, BOOLEAN, DATE, ARRAY, OBJECT, BINARY, GEOGRAPHY, VECTOR
 *   VARIANT              VARCHAR, DATE, TIME, TIMESTAMP_*, BINARY, GEOGRAPHY
 *   ARRAY                everything but a VARIANT and an ARRAY
 *   OBJECT               everything but a VARIANT and an OBJECT
 *   BINARY, GEOGRAPHY, VECTOR   everything but their own type, a VARIANT included
 * </pre>
 *
 * A TIME into a TIMESTAMP parameter has its own sentence, "incompatible types: [TIME(9)] and
 * [TIMESTAMP_NTZ(9)]". An untyped NULL binds to anything. A structured type is judged elsewhere.
 */
final class UdfArgumentTypes {

    private static final String[] TEMPORAL = {"DATE", "TIME", "NTZ", "LTZ", "TZ"};

    private static final String[] EVERY_FAMILY = {"TEXT", "FIXED", "FLOAT", "BOOLEAN", "DATE", "TIME", "NTZ", "LTZ",
        "TZ", "VARIANT", "ARRAY", "OBJECT", "BINARY", "GEOGRAPHY", "VECTOR"};

    /** A parameter family to the argument families it refuses. */
    private static final Map<String, Set<String>> REFUSED = new HashMap<String, Set<String>>();

    static {
        refuse("TEXT", "ARRAY", "OBJECT", "BINARY", "GEOGRAPHY", "VECTOR");
        for (final String numeric : new String[] {"FIXED", "FLOAT"}) {
            refuse(numeric, "BOOLEAN", "ARRAY", "OBJECT", "BINARY", "GEOGRAPHY", "VECTOR");
            refuse(numeric, TEMPORAL);
        }
        refuse("BOOLEAN", "ARRAY", "OBJECT", "BINARY", "GEOGRAPHY", "VECTOR");
        refuse("BOOLEAN", TEMPORAL);
        refuse("DATE", "FIXED", "FLOAT", "BOOLEAN", "TIME", "ARRAY", "OBJECT", "BINARY", "GEOGRAPHY", "VECTOR");
        for (final String stamp : new String[] {"NTZ", "LTZ"}) {
            refuse(stamp, "FIXED", "FLOAT", "BOOLEAN", "TZ", "ARRAY", "OBJECT", "BINARY", "GEOGRAPHY", "VECTOR");
        }
        refuse("TZ", "FIXED", "FLOAT", "BOOLEAN", "ARRAY", "OBJECT", "BINARY", "GEOGRAPHY", "VECTOR");
        refuse("TIME", "FIXED", "FLOAT", "BOOLEAN", "DATE", "ARRAY", "OBJECT", "BINARY", "GEOGRAPHY", "VECTOR");
        refuse("VARIANT", "TEXT", "BINARY", "GEOGRAPHY");
        refuse("VARIANT", TEMPORAL);
        refuseAllBut("ARRAY", "VARIANT", "ARRAY");
        refuseAllBut("OBJECT", "VARIANT", "OBJECT");
        refuseAllBut("BINARY", "BINARY");
        refuseAllBut("GEOGRAPHY", "GEOGRAPHY");
        refuseAllBut("VECTOR", "VECTOR");
    }

    private UdfArgumentTypes() {
    }

    private static void refuse(final String parameter, final String... arguments) {
        Set<String> refused = REFUSED.get(parameter);
        if (refused == null) {
            refused = new HashSet<String>();
            REFUSED.put(parameter, refused);
        }
        for (final String argument : arguments) {
            refused.add(argument);
        }
    }

    private static void refuseAllBut(final String parameter, final String... kept) {
        final Set<String> keep = new HashSet<String>();
        for (final String family : kept) {
            keep.add(family);
        }
        for (final String family : EVERY_FAMILY) {
            if (!keep.contains(family)) {
                refuse(parameter, family);
            }
        }
    }

    /** Whether a parameter of {@code parameter} refuses an argument of {@code argument}. */
    static boolean refuses(final DataType parameter, final DataType argument) {
        final String parameterFamily = family(parameter);
        final String argumentFamily = family(argument);
        if (parameterFamily == null || argumentFamily == null) {
            return false;
        }
        final Set<String> refused = REFUSED.get(parameterFamily);
        return refused != null && refused.contains(argumentFamily);
    }

    /** Whether the pair is a TIME into a TIMESTAMP parameter, refused with its own sentence. */
    static boolean timeIntoTimestamp(final DataType parameter, final DataType argument) {
        final String parameterFamily = family(parameter);
        return "TIME".equals(family(argument))
            && ("NTZ".equals(parameterFamily) || "LTZ".equals(parameterFamily) || "TZ".equals(parameterFamily));
    }

    /** The family a type binds as, or null for a type no pair has been measured for. */
    private static String family(final DataType type) {
        if (type == null || StructuredTypes.isStructured(type)) {
            return null;
        }
        if (type instanceof VariantType) {
            return "VARIANT";
        }
        if (type instanceof ObjectType) {
            return "OBJECT";
        }
        if (type instanceof ArrayType) {
            return "ARRAY";
        }
        if (type instanceof BinaryType) {
            return "BINARY";
        }
        if (type instanceof GeographyType) {
            return "GEOGRAPHY";
        }
        if (type instanceof VectorType) {
            return "VECTOR";
        }
        if (type instanceof BooleanType) {
            return "BOOLEAN";
        }
        if (type instanceof StringType) {
            return "TEXT";
        }
        if (type instanceof NumericType) {
            return NumericType.isApproximate(type) ? "FLOAT" : "FIXED";
        }
        if (type instanceof DateTimeType && type.getName() != null) {
            final String name = type.getName().toUpperCase(Locale.ROOT);
            if ("DATE".equals(name) || "TIME".equals(name)) {
                return name;
            }
            if (name.contains("LTZ")) {
                return "LTZ";
            }
            return name.contains("TZ") && !name.contains("NTZ") ? "TZ" : "NTZ";
        }
        return null;
    }
}
