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
 * <p>A declared BOOLEAN, a DATE / TIME / TIMESTAMP, a BINARY and a GEOGRAPHY are refused the same
 * way, in every position, with the same positioned sentence — live-verified member by member:
 * {@code ABS(TRUE)}, {@code ABS(d)}, {@code ABS(t)}, {@code ABS(ts)}, {@code ABS(bn)}, {@code ABS(g)},
 * {@code ROUND(d, 1)} ("(DATE, NUMBER(1,0))"), {@code ROUND(d, 'MONTH')} ("(DATE, VARCHAR(5))"),
 * {@code MOD(1, TRUE)} ("(NUMBER(1,0), BOOLEAN)"), {@code POWER(2, d)}, {@code LOG(10, d)},
 * {@code UNIFORM(TRUE, 2, RANDOM())}, {@code WIDTH_BUCKET(TRUE, 0, 10, 2)}, {@code BITAND(d, 1)},
 * {@code DIV0(TRUE, 1)}, {@code SIGN}, {@code CEIL}, {@code FLOOR}, {@code SQRT}, {@code EXP},
 * {@code SQUARE}, {@code FACTORIAL}, {@code CBRT}, {@code SIN}, {@code DEGREES}, {@code GETBIT},
 * {@code LN}, {@code ATAN2}, {@code HAVERSINE} — each "Invalid argument types for function 'X': (…)"
 * at the call's own position, listing every argument's declared type. The one arity-dependent
 * member is TRUNC, which over a temporal is DATE_TRUNC with a unit; see {@code Trunc}.
 *
 * <p>Membership is by measurement, so neighbours in the same package deliberately stay out.
 * {@code NULLIFZERO} is {@code NULLIF(x, 0)} and fails the conditional's way live — "Can not convert
 * parameter '0' of type [NUMBER(1,0)] into expected type [OBJECT]" over an OBJECT, [DATE] over a
 * DATE, [BINARY(8388608)] over a BINARY — there the argument wins the type unification and the
 * implicit literal is what is rejected, while a BOOLEAN is TAKEN (a NUMBER meets a BOOLEAN). The
 * conversion family ({@code TO_NUMBER}, {@code TO_DOUBLE}, {@code TO_DECIMAL}) rejects with the
 * "invalid type [...] for parameter" shape instead and is likewise not a member.
 *
 * <p>VARIANT is never refused, even when it holds an object: {@code ABS(v)} returned 1 and 2 live,
 * while {@code ABS(vo)} over a VARIANT HOLDING an object is a RUN-TIME cast failure — a different
 * outcome from the compile-time rejection here, and both are correct. An explicit conversion is legal
 * too; the rule reads the DECLARED type, so {@code ABS(v::OBJECT)} — the same value, cast — IS
 * refused.
 *
 * <p>At ROW time every argument IS read as a number, the way live converts: a numeric text in any
 * position converts — trimmed, in any spelling a number literal has ({@code ' 5 '}, {@code '1e2'},
 * {@code '+5'}, {@code '5.'}, {@code '.5'}) — while a text that reads as no number is refused with
 * live's own row-time sentence, "Numeric value 'x' is not recognized", the text echoed trimmed. The
 * dispatch does the reading, so a member never sees a numeric text and never has to word the refusal
 * (see the evaluator's numeric-text coercion). A text the member takes AS text — ROUND's rounding
 * mode — reads as no number and reaches it unchanged.
 */
public abstract class NumericArgumentFunction extends BuiltInFunction {

    protected NumericArgumentFunction(final String name, final DataType returnType) {
        super(name, returnType);
    }

    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    /** A VECTOR reads as neither text nor a number, and is refused in every position (live-verified). */
    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public SemiStructuredRejection temporalRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
