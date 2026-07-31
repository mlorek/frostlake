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

/**
 * HOW a built-in refuses a statically OBJECT- or ARRAY-typed argument. Snowflake does not phrase this
 * rejection one way: six distinct message shapes have now been measured live over populated OBJECT,
 * ARRAY and STRUCTURED columns, and they carry different SQLSTATEs, so a function has to
 * say which one it uses rather than the rule guessing.
 *
 * <p>The alternative — one message with the function name substituted in — was tried against the
 * account and does not hold: {@code SUM(o)} and {@code MEDIAN(o)} disagree on the sentence, the
 * SQLSTATE and the vendor code alike.
 */
public enum SemiStructuredRejection {

    /**
     * Undeclared: this position constrains nothing. The default for every function, so a built-in that
     * has never been measured against live Snowflake cannot cause a false rejection.
     */
    NONE,

    /**
     * "Invalid argument types for function 'SUM': (OBJECT)" — SQLSTATE 42P13, vendor code 1044. The
     * argument-type list names EVERY argument as written, so the offending position is visible in
     * context. The text family ({@link TextArgumentFunction}) and the numeric family
     * ({@link NumericArgumentFunction}) both refuse this way, as do {@code SUM}, {@code AVG} and the
     * {@code BIT*_AGG} aggregates.
     */
    ARGUMENT_TYPES,

    /**
     * "incompatible types: [OBJECT] and [NUMBER(9,0)]" — SQLSTATE 42846, vendor code 1010. The
     * ordering-by-value aggregates ({@code MEDIAN} and the percentiles) report the value they were
     * handed against the fixed numeric type they accumulate into. The {@code NUMBER(9,0)} is a
     * CONSTANT, measured rather than assumed: {@code PERCENTILE_CONT(0.9)},
     * {@code PERCENTILE_CONT(0.25)} and {@code PERCENTILE_DISC(0.9999)} all name exactly
     * {@code NUMBER(9,0)}, so it is not derived from the requested fraction.
     */
    INCOMPATIBLE_TYPES,

    /**
     * "Invalid argument types for function '*': (OBJECT, OBJECT)" — SQLSTATE 42P13, vendor code 1044.
     * The moment aggregates ({@code STDDEV}, {@code VARIANCE}, {@code SKEW}, {@code KURTOSIS} and
     * their variants) reach their sum of squares before they reach any type check, so live reports the
     * internal MULTIPLICATION and lists the one offending argument TWICE — the function's own name
     * never appears.
     */
    MULTIPLY_OPERANDS,

    /**
     * "invalid type [TO_VARCHAR(ST.SO)] for parameter 'TO_VARCHAR'" — SQLSTATE 22023, vendor code
     * 1007. The CONVERSION shape: the whole CALL is quoted back rather than its argument types listed,
     * and the parameter named is the conversion the call routes through, which is the written function
     * name ({@code TO_CHAR(so)} names 'TO_CHAR'). Measured for {@code TO_VARCHAR} and
     * {@code TO_CHAR} over all three structured kinds; the same sentence covers the CAST spellings,
     * which are not function calls and are produced by their own rule.
     */
    INVALID_TYPE_PARAMETER,

    /**
     * "Function ARRAY_CONSTRUCT does not support OBJECT(x VARCHAR(16777216)) argument type" — SQLSTATE
     * 22000, vendor code 2016. The same sentence the ordering aggregates use, reached here by the
     * semi-structured CONSTRUCTORS: live, {@code ARRAY_CONSTRUCT},
     * {@code ARRAY_CONSTRUCT_COMPACT}, {@code OBJECT_CONSTRUCT} and {@code OBJECT_CONSTRUCT_KEEP_NULL}
     * refuse a STRUCTURED value in ANY position this way while taking a plain OBJECT or ARRAY happily.
     */
    UNSUPPORTED_ARGUMENT_TYPE,

    /**
     * "Function OBJECT_CONSTRUCT does not support OBJECT(x VARCHAR(16777216)) argument type for keys" —
     * SQLSTATE 22000, vendor code 2270. {@link #UNSUPPORTED_ARGUMENT_TYPE} with a different tail and a
     * different vendor code, used for the KEY half of {@code OBJECT_CONSTRUCT}'s alternating argument
     * list: live, {@code OBJECT_CONSTRUCT(so, 1)} says "for keys" where
     * {@code OBJECT_CONSTRUCT('a', so)} does not.
     */
    UNSUPPORTED_KEY_ARGUMENT_TYPE
}
