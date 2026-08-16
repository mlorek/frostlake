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

import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DeclaredTypeFold;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

/**
 * The type a SQL UDF's parameter carries INSIDE the body, which is not always the type it was declared
 * with. The account types the body as if the call were inlined: a parameter takes the ARGUMENT's own
 * static type wherever the argument needs no conversion to reach it, and the declared type only where
 * it does. Live-measured, family by family:
 *
 * <ul>
 *   <li>a VARCHAR parameter takes a text argument's width, whatever it is — wider or narrower than the
 *       declaration — and the UNBOUNDED width for anything else, a number, a date and NULL alike;</li>
 *   <li>a BINARY parameter takes a binary argument's width, and the declared one otherwise;</li>
 *   <li>an exact NUMBER parameter takes an exact number argument's precision and scale only when that
 *       scale is at least the parameter's — {@code 1.25} into NUMBER(10,2) stays NUMBER(3,2) while a
 *       bare {@code 1} becomes NUMBER(10,2) — and the declared type for a FLOAT, a text or NULL;</li>
 *   <li>every other family, FLOAT among them, keeps the declared type.</li>
 * </ul>
 */
public final class UdfParameterTypes {

    private UdfParameterTypes() {
    }

    /**
     * The type the body reads this parameter at.
     *
     * @param declared the parameter's declared type
     * @param argument the argument's static type, or null when it could not be typed
     * @return the type to compile the body with
     */
    public static DataType effective(final DataType declared, final DataType argument) {
        if (declared instanceof StringType) {
            return argument instanceof StringType ? argument
                : new StringType(declared.getName(), (int) DeclaredTypeFold.UNKNOWN_LENGTH_VARCHAR);
        }
        if (argument == null) {
            return declared;
        }
        if (declared instanceof BinaryType) {
            return argument instanceof BinaryType ? argument : declared;
        }
        if (declared instanceof NumericType && !NumericType.isApproximate(declared)
                && argument instanceof NumericType && !NumericType.isApproximate(argument)
                && ((NumericType) argument).getScale() >= ((NumericType) declared).getScale()) {
            return argument;
        }
        return declared;
    }
}
