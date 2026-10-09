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

import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.util.List;
import java.util.Locale;

/**
 * How a call of a SQL table function finds its overload. A scalar SQL function converts an argument where it reads
 * it; a table function's arguments are matched against its parameters' declared types while the statement
 * compiles, by family and exactly (live-verified):
 *
 * <pre>
 *   NUMBER(p,s)        a NUMBER of the same SCALE whatever its precision: NUMBER takes 1 and a NUMBER(20,0), never
 *                      1.5 or a NUMBER(10,2); NUMBER(10,2) takes a NUMBER(5,2), never 1
 *   FLOAT              a FLOAT, never a NUMBER
 *   VARCHAR(n)         text of any width
 *   BOOLEAN, DATE, TIME, BINARY, VARIANT, ARRAY, OBJECT      its own family alone: a VARIANT takes no ARRAY
 *   TIMESTAMP_NTZ, TIMESTAMP_LTZ, TIMESTAMP_TZ                its own flavour alone
 *   an untyped NULL    matches nothing; NULL::NUMBER matches as a NUMBER
 * </pre>
 *
 * <p>A parameter declared with a default may be left out, and a named argument fills the parameter of its name.
 * Overloads are told apart by the same rule, so {@code f(1)} and {@code f('1')} reach different functions.
 */
final class TableFunctionSignatures {

    private TableFunctionSignatures() {
    }

    /**
     * The overload a call's arguments match, with the argument each of its parameters takes.
     *
     * @param overloads   the table functions of the called name
     * @param positional  each positional argument's static type, null where it is undetermined
     * @param names       each named argument's name, canonical
     * @param named       each named argument's static type, null where it is undetermined
     * @param untypedNull whether each argument, positional ones first, is a bare NULL
     * @return the match, or null when no overload takes the arguments
     */
    static TableFunctionMatch match(final List<Function> overloads, final List<DataType> positional,
                                    final List<String> names, final List<DataType> named,
                                    final List<Boolean> untypedNull) {
        for (final Function overload : overloads) {
            final int[] taken = taken(overload.getParameters(), positional, names, named, untypedNull);
            if (taken != null) {
                return new TableFunctionMatch(overload, taken);
            }
        }
        return null;
    }

    /** The argument index each parameter takes, -1 where its default stands, or null when the call does not fit. */
    private static int[] taken(final List<Parameter> parameters, final List<DataType> positional,
                               final List<String> names, final List<DataType> named,
                               final List<Boolean> untypedNull) {
        if (positional.size() + names.size() > parameters.size()) {
            return null;
        }
        final int[] taken = new int[parameters.size()];
        for (int i = 0; i < taken.length; i++) {
            taken[i] = -1;
        }
        for (int i = 0; i < positional.size(); i++) {
            if (untypedNull.get(i).booleanValue() || !takes(parameters.get(i).getDataType(), positional.get(i))) {
                return null;
            }
            taken[i] = i;
        }
        for (int n = 0; n < names.size(); n++) {
            final int argument = positional.size() + n;
            int parameter = -1;
            for (int i = positional.size(); i < parameters.size(); i++) {
                // A name is matched exactly: a quoted name of another case names no parameter (live-verified).
                if (taken[i] < 0 && parameters.get(i).getName().equals(names.get(n))) {
                    parameter = i;
                }
            }
            if (parameter < 0 || untypedNull.get(argument).booleanValue()
                    || !takes(parameters.get(parameter).getDataType(), named.get(n))) {
                return null;
            }
            taken[parameter] = argument;
        }
        for (int i = 0; i < taken.length; i++) {
            if (taken[i] < 0 && !parameters.get(i).hasDefault()) {
                return null;
            }
        }
        return taken;
    }

    /**
     * Whether a parameter of a declared type takes an argument of a static type.
     *
     * @param parameter the parameter's declared type
     * @param argument  the argument's static type, or null when it is undetermined
     * @return whether the argument's family is the parameter's
     */
    static boolean takes(final DataType parameter, final DataType argument) {
        if (parameter == null || argument == null) {
            return true;
        }
        if (parameter instanceof NumericType) {
            if (!(argument instanceof NumericType)) {
                return false;
            }
            if (NumericType.isApproximate(parameter) || NumericType.isApproximate(argument)) {
                return NumericType.isApproximate(parameter) && NumericType.isApproximate(argument);
            }
            return ((NumericType) parameter).getScale() == ((NumericType) argument).getScale();
        }
        if (parameter instanceof StringType) {
            return argument instanceof StringType;
        }
        if (parameter instanceof DateTimeType) {
            return argument instanceof DateTimeType && flavour(parameter).equals(flavour(argument));
        }
        return parameter.getClass() == argument.getClass();
    }

    /** DATE, TIME, or the timestamp flavour a type's name spells; a bare TIMESTAMP and DATETIME are NTZ. */
    private static String flavour(final DataType temporal) {
        final String name = temporal.getName().toUpperCase(Locale.ROOT);
        if (name.equals("DATE") || name.equals("TIME")) {
            return name;
        }
        if (name.contains("LTZ")) {
            return "TIMESTAMP_LTZ";
        }
        return name.contains("NTZ") || !name.contains("TZ") ? "TIMESTAMP_NTZ" : "TIMESTAMP_TZ";
    }
}
