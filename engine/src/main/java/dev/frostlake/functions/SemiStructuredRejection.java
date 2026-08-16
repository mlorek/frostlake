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
     * The two-argument statistics — CORR, the covariances, the regressions bar REGR_R2 — convert each
     * argument to a DOUBLE inside a null guard on the OTHER argument, and it is that conversion live
     * refuses, quoting the guarded plan back and naming TO_DOUBLE as the parameter: {@code CORR(bo, n)}
     * over a BOOLEAN and a NUMBER is "invalid type [TO_DOUBLE(IFF(AT.N IS NULL,
     * SYSTEM$NULL_TO_BOOLEAN(NULL), AT.BO))] for parameter 'TO_DOUBLE'" — unpositioned, the null
     * substitute spelled after the refused argument's family. Every family but the numbers, the
     * strings and VARIANT is refused this way, at either position.
     */
    DOUBLE_CONVERSION_PARAMETER,

    /**
     * The product of a call's TWO arguments, refused in the multiplication's name with the X — the
     * second argument — listed first: {@code REGR_SXY(bo, n)} over a BOOLEAN and a NUMBER(10,2) is
     * "Invalid argument types for function '*': (NUMBER(10,2), BOOLEAN)", and with the arguments
     * swapped "(BOOLEAN, NUMBER(10,2))" — live reaches its sum of x·y first (live-verified). The
     * squares family lists one argument twice instead ({@link #MULTIPLY_OPERANDS}).
     */
    CROSS_PRODUCT_OPERANDS,

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
    UNSUPPORTED_KEY_ARGUMENT_TYPE,

    /**
     * The boolean aggregates' conversion refusal — {@code invalid type [TO_BOOLEAN(x)] for parameter
     * 'TO_BOOLEAN'}, the argument quoted from the plan, no position — for a FLOAT, a temporal, a BINARY
     * or a structured value the account's TO_BOOLEAN cannot take (live-verified).
     */
    BOOLEAN_CONVERSION_PARAMETER,

    /**
     * REPEAT(s, n) judged as the {@code LPAD('', n * LENGTH(s), s)} the account plans it as. The product
     * is typed first, LENGTH declaring NUMBER(18,0), so a count the multiplication refuses is refused in
     * the product's words — "Invalid argument types for function '*': (BINARY(8388608), NUMBER(18,0))" —
     * and a BINARY string by LPAD, the empty pad listed as VARCHAR(1) and the product at its own width:
     * "Invalid argument types for function 'LPAD': (VARCHAR(1), NUMBER(19,0), BINARY(8388608))" for a
     * count of 2. SQLSTATE 42P13, at the call (live-verified).
     */
    REPEAT_REWRITE_OPERANDS,

    /**
     * SPACE(n) judged as the {@code LPAD('', n, ' ')} the account plans it as: "Invalid argument types
     * for function 'LPAD': (VARCHAR(1), BINARY(8388608), VARCHAR(1))" for a BINARY count, at the call
     * (live-verified).
     */
    SPACE_REWRITE_OPERANDS,

    /**
     * INSERT(s, p, l, i) judged as the {@code SUBSTR(s, 1, p - 1) || i || SUBSTR(s, p + l)} the account
     * plans it as: a BINARY position is the subtraction's refusal, "'-': (BINARY(8388608),
     * NUMBER(1,0))", a BINARY length the addition's, "'+': (NUMBER(1,0), BINARY(8388608))", and a
     * BINARY beside any other family the concatenation's, the base listed twice around the insertion:
     * "'||': (BINARY(8388608), VARCHAR(1), BINARY(8388608))". Two binaries, or a NULL insertion,
     * concatenate — so this is the one shape that can find its call legal (live-verified).
     */
    INSERT_REWRITE_OPERANDS,

    /**
     * "incompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]" — SQLSTATE 42846, no position. The TIME
     * synonym takes a text, a VARIANT or a timestamp, a DATE reading as its midnight; a TIME-typed
     * argument is carried toward the TIMESTAMP_LTZ form and refused there, its own precision spelled —
     * over a TIME(3) column the sentence lists [TIME(3)] (live-verified).
     */
    TIME_TO_TIMESTAMP_LTZ
}
