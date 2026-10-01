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

import dev.frostlake.executor.expressions.CollationSpec;
import dev.frostlake.types.DataType;
import java.util.List;

public abstract class BuiltInFunction {

    private final String name;
    private final DataType returnType;

    protected BuiltInFunction(final String name, final DataType returnType) {
        this.name = name;
        this.returnType = returnType;
    }

    public String getName() {
        return name;
    }

    public DataType getReturnType() {
        return returnType;
    }

    /**
     * Computes the function's result over its already-evaluated argument values. A SQL NULL argument
     * arrives as Java {@code null}, and returning {@code null} makes the SQL result NULL.
     *
     * @param args the argument values, in call order
     * @return the function's value for these arguments, or {@code null} for SQL NULL
     */
    public abstract Object evaluate(final List<Object> args);

    /**
     * The same, for a call whose arguments settled on a collation. A function that COMPARES text —
     * CONTAINS, REPLACE, NULLIF and their kin — looks for what the collation calls equal rather than
     * what matches byte for byte; every other function answers as it always did.
     *
     * @param args      the argument values, in call order
     * @param collation the collation the call settled on, or null when it carries none
     * @return the function's value for these arguments, or {@code null} for SQL NULL
     */
    public Object evaluate(final List<Object> args, final CollationSpec collation) {
        return evaluate(args);
    }

    /**
     * The fewest arguments a call to this function may pass; fewer is an argument-count error.
     *
     * @return the minimum accepted argument count
     */
    public abstract int getMinArgCount();

    /**
     * The most arguments a call to this function may pass; {@link Integer#MAX_VALUE} marks a variadic
     * function (see {@link #isVariadic}).
     *
     * @return the maximum accepted argument count
     */
    public abstract int getMaxArgCount();

    public boolean isVariadic() {
        return getMaxArgCount() == Integer.MAX_VALUE;
    }

    /**
     * How argument {@code position} refuses a statically OBJECT- or ARRAY-typed value, or
     * {@link SemiStructuredRejection#NONE} when it does not. Snowflake never implicitly coerces a
     * semi-structured value to the VARCHAR (or NUMBER) a text or numeric function reads, so such an
     * argument is an argument-type error at COMPILE time rather than a stringification or a fabricated
     * zero: live, {@code UPPER(o)} over an OBJECT column is "Invalid argument types for
     * function 'UPPER': (OBJECT)", {@code SUM(o)} is the same sentence for 'SUM', and
     * {@code SPLIT_PART(s, o, 1)} names the position it was given. VARIANT is never refused, even when
     * it holds an object.
     *
     * <p>Declared HERE, on the function, rather than in a name-keyed table beside the rule: the
     * registry is already the authority on a function's types ({@link #getReturnType}), a declaration
     * on the instance covers the function's ALIASES for free (SUBSTR and SUBSTRING are one object,
     * and so are VARIANCE_POP and VAR_POP), and a per-position answer is the only shape that fits —
     * {@code ARRAY_TO_STRING(a, ',')} takes an ARRAY in position 0 and refuses one in position 1.
     *
     * <p>The answer is an ENUM rather than a boolean because Snowflake phrases the refusal three
     * different ways, with three different SQLSTATEs — see {@link SemiStructuredRejection}.
     *
     * <p>The default is {@code NONE}, meaning UNDECLARED rather than "accepts": a function that has
     * not been measured against live Snowflake constrains nothing. Answering for every position is
     * what {@link TextArgumentFunction} and {@link NumericArgumentFunction} exist for.
     *
     * @param position the zero-based argument position being asked about
     * @return the refusal this position gives a semi-structured value, or {@code NONE} when undeclared
     */
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.NONE;
    }

    /**
     * The EXTRA refusal argument {@code position} reserves for a STRUCTURED value — {@code OBJECT(x
     * VARCHAR)}, {@code ARRAY(INT)}, {@code MAP(VARCHAR, INT)} — where the plain semi-structured
     * OBJECT or ARRAY is ACCEPTED. Consulted only when {@link #semiStructuredRejection} declares
     * nothing for the position, so a function that refuses every semi-structured value keeps saying so
     * in one place.
     *
     * <p>The two are separate because Snowflake genuinely treats {@code OBJECT} and
     * {@code OBJECT(x VARCHAR)} as different types, and they DIVERGE — measured over
     * populated columns of both: {@code ARRAY_AGG(o)} returns the array while {@code ARRAY_AGG(so)} is
     * an argument-type error, {@code TO_JSON(o)} returns the JSON text while {@code TO_JSON(so)} is
     * refused, and {@code TYPEOF}, {@code IS_OBJECT}, {@code AS_VARCHAR}, {@code ARRAY_TO_STRING} and
     * the constructors all split the same way. A single rule keyed on "is this semi-structured?" gets
     * both cases wrong in opposite directions, which is why the answer is asked twice.
     *
     * <p>What a structured type does NOT get is a rejection anywhere the plain types are accepted as
     * KEYS: live, {@code GROUP BY so}, {@code ORDER BY so}, {@code SELECT DISTINCT so},
     * {@code PARTITION BY so}, the set operators, {@code JOIN … ON so = so} and {@code COUNT(DISTINCT
     * so)} all work over each of the three structured kinds — that was measured rather than inferred
     * from the plain behaviour.
     *
     * <p>The default is {@code NONE} for the same reason as above: undeclared means unconstrained.
     *
     * @param position the zero-based argument position being asked about
     * @return the extra refusal this position gives a structured value, or {@code NONE} when undeclared
     */
    public SemiStructuredRejection structuredRejection(final int position) {
        return SemiStructuredRejection.NONE;
    }

    /**
     * How argument {@code position} refuses a statically FILE-typed value. A FILE IS an object of file
     * metadata, and Frostlake used to let it stringify to that descriptor — so {@code LENGTH(f)}
     * counted the JSON, {@code UPPER(f)} upper-cased its KEYS and {@code SUM(f)} answered a number —
     * but Snowflake refuses it at COMPILE time exactly as it refuses an OBJECT: live,
     * {@code UPPER(f)} is "Invalid argument types for function 'UPPER': (FILE)" and {@code SUM(f)} is
     * the same sentence for 'SUM'.
     *
     * <p>The DEFAULT is {@link #semiStructuredRejection}, because that is what the account says: over a
     * populated FILE column, every one of the 52 text functions, the 42 numeric functions and the
     * numeric / moment / percentile aggregates refuses a FILE with the SAME shape and the SAME
     * sentence it gives an OBJECT, down to the SQLSTATE — {@code MEDIAN(f)} is "incompatible types:
     * [FILE] and [NUMBER(9,0)]" (42846) where {@code SUM(f)} is the argument-type list (42P13), and
     * {@code STDDEV(f)} reports its internal "'*': (FILE, FILE)" just as {@code STDDEV(o)} does.
     *
     * <p>It is nevertheless a THIRD question rather than a reuse of the first, because FILE and the
     * semi-structured types genuinely DIVERGE in both directions, and a shared answer would be wrong
     * each way. {@code ARRAY_AGG} and {@code ARRAY_UNIQUE_AGG} refuse a structured value and ACCEPT a
     * FILE; {@code OBJECT_AGG}'s VALUE position takes a plain OBJECT and REFUSES a FILE; and
     * {@code OBJECT_CONSTRUCT} accepts a FILE as a value while refusing it as a KEY. Those three
     * override this method; everything else is answered by the default.
     *
     * <p>What a FILE does NOT get is a rejection where it is used as a VALUE rather than read: live,
     * {@code f = f}, {@code SELECT DISTINCT f}, {@code COUNT(f)}, {@code COUNT(DISTINCT f)},
     * {@code ANY_VALUE}, {@code ARRAY_AGG}, {@code HASH}, {@code HASH_AGG}, {@code MAX_BY} /
     * {@code MIN_BY}, {@code APPROX_COUNT_DISTINCT}, the conditionals, {@code JOIN … ON a.f = b.f} and
     * the set operators all take a FILE. Only {@code MAX} / {@code MIN} / {@code MODE} refuse it among
     * the aggregates that ORDER, and they say so with their own sentence.
     *
     * @param position the zero-based argument position being asked about
     * @return the refusal this position gives a FILE value; by default, the semi-structured answer
     */
    public SemiStructuredRejection fileRejection(final int position) {
        return semiStructuredRejection(position);
    }

    /**
     * How argument {@code position} refuses a statically GEOGRAPHY- or GEOMETRY-typed value. A geo
     * value is a DOMAIN type that coerces to nothing: not to the VARCHAR or NUMBER a text or numeric
     * function reads, and not to the VARIANT the semi-structured surface reads — so live refuses it in
     * both families at COMPILE time. Measured over a populated {@code GEOGRAPHY} column and
     * a {@code GEOMETRY} column in the same table: {@code UPPER(g)} is "Invalid argument types for
     * function 'UPPER': (GEOGRAPHY)", {@code SUM(g)} names 'SUM', {@code MEDIAN(g)} is "incompatible
     * types: [GEOGRAPHY] and [NUMBER(9,0)]", {@code STDDEV(g)} reports its internal "'*': (GEOGRAPHY,
     * GEOGRAPHY)", {@code ARRAY_CONSTRUCT(g)} is "does not support GEOGRAPHY argument type" and
     * {@code TO_VARCHAR(g)} is the conversion sentence. Frostlake used to accept every one of them and
     * answer from the GeoJSON text, so {@code UPPER(g)} upper-cased the JSON KEYS and {@code SUM(g)}
     * returned a number.
     *
     * <p>The DEFAULT is the UNION of the two semi-structured answers — {@link #semiStructuredRejection}
     * when it says anything, {@link #structuredRejection} otherwise — because that is what the account
     * says: a geo value is refused wherever EITHER a plain OBJECT or a merely STRUCTURED one is
     * refused, with the same sentence and the same SQLSTATE. It reaches the right answer for the text
     * family, the numeric family, {@code SUM} / {@code AVG} / {@code LISTAGG} / {@code MEDIAN} / the
     * moment aggregates (through the first), and for {@code TYPEOF}, {@code TO_JSON}, {@code IS_OBJECT},
     * {@code AS_VARCHAR}, {@code ARRAY_AGG}, {@code OBJECT_AGG}, the constructors and
     * {@code TO_VARCHAR} / {@code TO_CHAR} (through the second).
     *
     * <p>It is nevertheless a FOURTH question rather than a reuse, because geo REFUSES a surface both
     * semi-structured kinds accept, and the overriding classes say so one at a time: live,
     * {@code HASH_AGG(o)}, {@code MAX_BY(o, n)}, {@code APPROX_COUNT_DISTINCT(o)}, {@code GET(o, 'k')},
     * {@code OBJECT_KEYS(o)}, {@code ARRAY_SIZE(o)}, {@code OBJECT_INSERT(o, 'a', 1)},
     * {@code PARSE_JSON}, {@code GREATEST(o, o)}, {@code LEAST(o, o)} and {@code EQUAL_NULL(o, o)} all
     * return a value over an OBJECT and are argument-type errors over a GEOGRAPHY.
     *
     * <p>What a geo value does NOT get is a rejection where it is carried rather than read: live,
     * {@code SELECT DISTINCT g}, {@code COUNT(g)}, {@code COUNT(DISTINCT g)}, {@code ANY_VALUE(g)},
     * {@code HASH(g)}, {@code FIRST_VALUE} / {@code LAST_VALUE}, {@code IFF} / {@code COALESCE} /
     * {@code NVL} / {@code IFNULL} / {@code NVL2} / {@code DECODE} / {@code CASE}, the set operators
     * and {@code TO_GEOGRAPHY} / {@code TO_GEOMETRY} all take one.
     *
     * <p>The default is {@code NONE} for an unmeasured function, for the same reason as the other
     * three: undeclared means unconstrained.
     *
     * @param position the zero-based argument position being asked about
     * @return the refusal this position gives a GEOGRAPHY or GEOMETRY value; by default, the union of
     *     the two semi-structured answers
     */
    public SemiStructuredRejection geoRejection(final int position) {
        final SemiStructuredRejection semiStructured = semiStructuredRejection(position);
        return semiStructured != SemiStructuredRejection.NONE ? semiStructured
            : structuredRejection(position);
    }

    /**
     * The refusal a position gives a statically BINARY-typed value.
     *
     * <p>A fifth question rather than a reuse of any of the four above, because BINARY sits on the
     * opposite side of them: it is a scalar a text function is happy to be handed almost everywhere —
     * live answers {@code LENGTH(bn)}, {@code SUBSTR(bn, 1, 1)}, {@code CONCAT(bn, bn)} — where an
     * OBJECT is refused. Only the functions that read their argument AS TEXT and would have to decode
     * it refuse one, and they say so themselves: {@code VALIDATE_UTF8(bn)} and
     * {@code TRY_VALIDATE_UTF8(bn)} are "Invalid argument types for function 'TRY_VALIDATE_UTF8':
     * (BINARY(5))" while the same call over a VARCHAR, and even over a NUMBER, returns a value.
     *
     * <p>The default is {@code NONE}: undeclared means unconstrained, as for the other four.
     *
     * @param position the zero-based argument position being asked about
     * @return the refusal this position gives a BINARY value; by default none
     */
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.NONE;
    }

    /**
     * The refusal this position gives a VECTOR argument.
     *
     * <p>A vector is not semi-structured and is not binary, so it needs its own declaration: the
     * functions that refuse one are the TEXT CONVERSIONS and the concatenations, and they refuse in
     * two different shapes — the conversion sentence quotes the whole call, the concatenation lists
     * argument types and carries a position.
     *
     * <p>The default is {@code NONE}: undeclared means unconstrained, as for the others.
     *
     * @param position the zero-based argument position being asked about
     * @return the refusal this position gives a VECTOR value; by default none
     */
    public SemiStructuredRejection vectorRejection(final int position) {
        return SemiStructuredRejection.NONE;
    }

    /**
     * The refusal this position gives a declared UUID argument. A UUID is its own type on the account,
     * and the ordering aggregates refuse it — "Function MAX does not support UUID argument type" — while
     * the text functions read it as the text it holds.
     *
     * @param position the argument's position, counted from zero
     * @return how it is refused, or {@link SemiStructuredRejection#NONE} when it is taken
     */
    public SemiStructuredRejection uuidRejection(final int position) {
        return SemiStructuredRejection.NONE;
    }

    /**
     * The refusal this position gives a declared BOOLEAN argument. Membership is declared per
     * function and never assumed for a family: the whole NUMERIC family ({@code NumericArgumentFunction})
     * refuses a SQL BOOLEAN at compile time in every position — "Invalid argument types for function
     * 'ABS': (BOOLEAN)", {@code MOD(1, TRUE)} listing "(NUMBER(1,0), BOOLEAN)" — while the SAME value
     * inside a VARIANT converts to 1.0, and the conditionals ({@code NULLIF(b, 0)},
     * {@code NULLIFZERO(b)}) take it. The rule reads the DECLARED type, like every channel here.
     *
     * @param position the zero-based argument position being asked about
     * @return the refusal this position gives a BOOLEAN value; by default none
     */
    public SemiStructuredRejection booleanRejection(final int position) {
        return SemiStructuredRejection.NONE;
    }

    /**
     * The refusal this position gives a PREDICATE — a comparison, an IN, a LIKE, an EXISTS, a type test
     * — as opposed to a BOOLEAN value. A predicate never becomes text: the functions that read text refuse
     * {@code UPPER(1 = 1)} as "Invalid argument types for function 'UPPER': (BOOLEAN)" where they read
     * {@code UPPER(TRUE)} as 'TRUE'. Declared per function and per position, by measurement.
     *
     * @param position the zero-based argument position being asked about
     * @return the refusal this position gives a predicate; by default none
     */
    public SemiStructuredRejection predicateRejection(final int position) {
        return SemiStructuredRejection.NONE;
    }

    /**
     * The refusal this position gives a declared DATE / TIME / TIMESTAMP argument. Declared per
     * function: the numeric family refuses every temporal flavour at compile time, each named with
     * its own parameters — (DATE), (TIME(9)), (TIMESTAMP_NTZ(9)) and the LTZ/TZ twins — where the
     * datetime functions read them happily.
     *
     * @param position the zero-based argument position being asked about
     * @return the refusal this position gives a temporal value; by default none
     */
    public SemiStructuredRejection temporalRejection(final int position) {
        return SemiStructuredRejection.NONE;
    }

    /**
     * The refusal this position gives a declared temporal argument in a call of
     * {@code argumentCount} arguments. One member's answer depends on the call's ARITY: TRUNC over a
     * temporal is DATE_TRUNC with a unit, so a temporal first argument BESIDE a unit is legal while
     * the same argument alone is refused "(DATE)" — live-verified. Every other function answers the
     * arity-blind question, which is what this consults by default.
     *
     * @param position the zero-based argument position being asked about
     * @param argumentCount how many arguments the call carries
     * @return the refusal this position gives a temporal value in a call of that arity
     */
    public SemiStructuredRejection temporalRejection(final int position, final int argumentCount) {
        return temporalRejection(position);
    }

    /**
     * The refusal this position gives a declared temporal argument of type {@code temporal}. One
     * member's answer depends on WHICH temporal type arrives: the TIME synonym takes a DATE (its
     * midnight) and every timestamp flavour, and refuses only a TIME. Every other function answers
     * without looking, which is what this consults by default.
     *
     * @param position the zero-based argument position being asked about
     * @param argumentCount how many arguments the call carries
     * @param temporal the argument's declared temporal type
     * @return the refusal this position gives that temporal value
     */
    public SemiStructuredRejection temporalRejection(final int position, final int argumentCount,
                                                     final DataType temporal) {
        return temporalRejection(position, argumentCount);
    }

    /**
     * Whether a call with fewer arguments than {@link #getMinArgCount} is refused by its argument types
     * rather than by its count. A function the account resolves among overloads has no single count to
     * report: live, {@code TIME()} is "Invalid argument types for function 'TIME': ()" where
     * {@code LOG(10)} is "not enough arguments for function [LOG(10)], expected 2, got 1".
     *
     * @return true when the refusal lists the argument types; false by default
     */
    public boolean refusesMissingArgumentsByType() {
        return false;
    }

    /** How the function treats a TEXT argument at {@code position} — NONE for the many that take one. */
    public SemiStructuredRejection textRejection(final int position) {
        return SemiStructuredRejection.NONE;
    }

    /** How the function treats an APPROXIMATE numeric argument at {@code position}. */
    public SemiStructuredRejection approximateRejection(final int position) {
        return SemiStructuredRejection.NONE;
    }
}
