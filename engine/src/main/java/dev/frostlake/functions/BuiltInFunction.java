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

    public abstract Object evaluate(final List<Object> args);

    public abstract int getMinArgCount();

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
     */
    public SemiStructuredRejection geoRejection(final int position) {
        final SemiStructuredRejection semiStructured = semiStructuredRejection(position);
        return semiStructured != SemiStructuredRejection.NONE ? semiStructured
            : structuredRejection(position);
    }
}
