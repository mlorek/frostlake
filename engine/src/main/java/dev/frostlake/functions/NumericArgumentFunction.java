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

import dev.frostlake.types.DataType;

/**
 * A built-in that reads its arguments as NUMBERS, and therefore refuses a statically OBJECT- or
 * ARRAY-typed value in EVERY argument position — the numeric twin of {@link TextArgumentFunction},
 * and the same live message shape ("Invalid argument types for function 'ABS': (OBJECT)", SQLSTATE
 * 42P13).
 *
 * <p>Every position, not merely the first — measured, not assumed. Live over an OBJECT
 * column, {@code ROUND(o, 2)} and {@code ROUND(n, o)} are both argument-type errors, as are
 * {@code ATAN2(o, 1)} / {@code ATAN2(1, o)}, {@code LOG(o, 2)} / {@code LOG(2, o)},
 * {@code HAVERSINE(o, 1, 2, 3)} / {@code HAVERSINE(1, 2, 3, o)} and
 * {@code WIDTH_BUCKET(1, o, 10, 3)}. A semi-structured value coerces to a NUMBER nowhere, so the
 * whole signature refuses it.
 *
 * <p>Membership is by measurement, so neighbours in the same package deliberately stay out.
 * {@code NULLIFZERO(o)} fails a DIFFERENT way live ("Can not convert parameter '0' of type
 * [NUMBER(1,0)] into expected type [OBJECT]" — there the OBJECT wins the type unification and the
 * literal is what is rejected), and {@code BIT_COUNT} is a name Snowflake does not know at all, so
 * neither has this behaviour to copy. The conversion family ({@code TO_NUMBER}, {@code TO_DOUBLE},
 * {@code TO_DECIMAL}) rejects with the "invalid type [...] for parameter" shape instead and is
 * likewise not a member.
 *
 * <p>VARIANT is never refused, even when it holds an object: {@code ABS(v)} returned 1 and 2 live,
 * while {@code ABS(vo)} over a VARIANT HOLDING an object is a RUN-TIME cast failure — a different
 * outcome from the compile-time rejection here, and both are correct. An explicit conversion is legal
 * too; the rule reads the DECLARED type, so {@code ABS(v::OBJECT)} — the same value, cast — IS
 * refused.
 */
public abstract class NumericArgumentFunction extends BuiltInFunction {

    protected NumericArgumentFunction(final String name, final DataType returnType) {
        super(name, returnType);
    }

    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
