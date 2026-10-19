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

package dev.frostlake.functions;

import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;

/**
 * What an array function's argument position is declared to take, and so which statically typed arguments the
 * statement's compilation refuses there — {@code Invalid argument types for function 'ARRAY_SIZE': (VARCHAR(3))},
 * positioned at the call, every argument's type listed. Each kind is the set of families measured as refused
 * (live-verified, over a literal, a cast NULL and a column of each family); a VARIANT, an untyped NULL and a type
 * outside these families are never judged here, and what a position accepts while compiling may still fail on a
 * row ({@code ARRAY_COMPACT(1)} is {@code First argument of ARRAY_COMPACT is not an array}).
 */
public enum ArrayArgumentKind {

    /** An array and nothing else: text, numbers, booleans, dates and times, binaries and objects are refused. */
    ARRAY,

    /** A value read as a VARIANT: text, dates and times and binaries are refused; a number or a boolean is taken. */
    VALUE,

    /** A whole number: booleans, dates and times, binaries, objects and arrays are refused; text is read on the row. */
    WHOLE_NUMBER,

    /** A boolean flag: dates and times, binaries, objects and arrays are refused; text is read on the row. */
    FLAG,

    /** A text or a number: binaries, objects and arrays are refused. */
    SCALAR;

    /**
     * Whether an argument of a static type is refused in a position of this kind.
     *
     * @param type the argument's static type, or null when it has none
     * @return whether the statement is refused for it
     */
    public boolean refuses(final DataType type) {
        if (type == null) {
            return false;
        }
        final boolean temporalOrBinary = type instanceof DateTimeType || type instanceof BinaryType;
        // A structured OBJECT or ARRAY is a type of its own, judged by its own rules.
        final boolean object = type.getClass() == ObjectType.class;
        final boolean container = object || type.getClass() == ArrayType.class;
        switch (this) {
            case ARRAY:
                return temporalOrBinary || type instanceof StringType || type instanceof NumericType
                    || type instanceof BooleanType || object;
            case VALUE:
                return temporalOrBinary || type instanceof StringType;
            case WHOLE_NUMBER:
                return temporalOrBinary || container || type instanceof BooleanType;
            case FLAG:
                return temporalOrBinary || container;
            default:
                return type instanceof BinaryType || container;
        }
    }
}
