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

package dev.frostlake.types;

import java.util.ArrayList;
import java.util.List;

/**
 * The type a set of BRANCHES agree on — the rule a UNION applies to its arms and a conditional applies
 * to its results. The two surfaces share most of their answers, measured cell by cell on a live
 * account, which is why the rule lives here rather than in either of them; where they DIVERGE the
 * branch-only half sits in {@link #foldPair} and {@link #combine} names the cells that prove it:
 *
 * <pre>
 *   VARCHAR(4)     with VARCHAR(100)     TEXT(100)              the widest
 *   BINARY(4)      with BINARY(100)      BINARY(100)            and its fixed spelling, see BinaryType
 *   NUMBER(5,1)    with NUMBER(10,3)     NUMBER(10,3)
 *   NUMBER(5,4)    with NUMBER(10,0)     NUMBER(14,4)           NOT (10,4)
 *   TIMESTAMP_NTZ(3) with TIMESTAMP_NTZ(9)   scale 9            the wider precision
 *   DATE           with DATE             DATE
 *   VARCHAR(4)     with NUMBER(5,1)      NUMBER(18,5)           the string joins the other family
 * </pre>
 *
 * <p>THE NUMERIC RULE IS THE ONE WORTH READING TWICE: the widest INTEGER PART meets the widest SCALE,
 * so (5,4) with (10,0) needs ten integer digits and four decimals — fourteen — where a naive
 * max-precision/max-scale would answer (10,4) and could not hold the value.
 */
public final class DeclaredTypeFold {

    /** A NUMBER's widest precision. */
    private static final int MAX_PRECISION = 38;

    /**
     * The width a VARCHAR takes when it folds with a branch that carries no length of its own — the
     * 128MB conversion width, not the 16MB one a declared column defaults to.
     */
    public static final int UNKNOWN_LENGTH_VARCHAR = 134217728;

    /** The temporals that widen into one another, narrowest first. */
    private static final String[] TEMPORAL_WIDENING_ORDER = {
        "DATE", "TIMESTAMP_NTZ", "TIMESTAMP_LTZ", "TIMESTAMP_TZ"};

    private DeclaredTypeFold() {
    }

    /**
     * Whether two declared types are the SAME type — the family and its parameters. A VECTOR's
     * dimension and a structured OBJECT's fields are rendered into the name; the width of a string or
     * binary, a number's precision and scale, and a temporal's precision are not, so those compare
     * their parameters explicitly.
     *
     * @param left  one type
     * @param right the other
     * @return true when they are indistinguishable as declared types
     */
    public static boolean sameDeclaredType(final DataType left, final DataType right) {
        if (left == null || right == null || !left.getClass().equals(right.getClass())
                || !left.getName().equalsIgnoreCase(right.getName())) {
            return false;
        }
        if (left instanceof NumericType) {
            return ((NumericType) left).getPrecision() == ((NumericType) right).getPrecision()
                && ((NumericType) left).getScale() == ((NumericType) right).getScale();
        }
        if (left instanceof StringType) {
            return ((StringType) left).getMaxLength() == ((StringType) right).getMaxLength();
        }
        if (left instanceof BinaryType) {
            // FIXEDNESS is part of the declared type, not decoration: both spellings report the name
            // BINARY, so without this a fixed and a non-fixed binary of one width would read as the
            // same type and the fold would hand back the first rather than applying its own rule.
            return ((BinaryType) left).getMaxLength() == ((BinaryType) right).getMaxLength()
                && ((BinaryType) left).isFixed() == ((BinaryType) right).isFixed()
                && ((BinaryType) left).getWidthSpelling() == ((BinaryType) right).getWidthSpelling();
        }
        // A temporal's PRECISION is deliberately not compared here. The set-operation path has always
        // treated two same-named temporals as one declared type, and the vendor gate depends on it —
        // making them differ changed how an EXCEPT compared timestamp columns. The measured
        // max-precision rule is applied by the folds themselves instead (combine and foldBranches),
        // which change the declared type and never how two values compare.
        return true;
    }

    /**
     * The type two branches — or two set-operation ARMS — fold to, or null when they cannot be
     * combined.
     *
     * <p>The two surfaces share most of their rules and are measured together, but they are NOT the
     * same function, which is why the branch-only rules stay in {@link #foldPair}. Three cells prove
     * the split, each measured on the account:
     *
     * <pre>
     *   NUMBER then BOOLEAN   COALESCE(n, bo) is BOOLEAN, while n UNION bo is a compile-time refusal
     *   STRING beside BOOLEAN COALESCE(v, bo) widens to VARCHAR(134217728), v UNION bo stays VARCHAR(5)
     *   STRING beside NULL    COALESCE(v, NULL) widens the same way, v UNION NULL stays VARCHAR(5)
     * </pre>
     *
     * <p>So the unknown-length widening is a BRANCH rule: an arm keeps the width it declares. What both
     * surfaces do share is here — the numeric supertype, the common string and binary width, an
     * approximate number swallowing an exact one, temporal widening at the wider fractional-second
     * precision, and a leading BOOLEAN winning over whatever follows it.
     *
     * @param left  one branch's type
     * @param right the other's
     * @return the combined type, or null when the pair has no measured answer
     */
    public static DataType combine(final DataType left, final DataType right) {
        if (sameDeclaredType(left, right)) {
            return atWiderPrecision(left, left, right);
        }
        if (left == null || right == null) {
            return null;
        }
        if (left instanceof NumericType && right instanceof NumericType) {
            if (NumericType.isApproximate(left)) {
                return left;
            }
            if (NumericType.isApproximate(right)) {
                return right;
            }
            if ("NUMBER".equalsIgnoreCase(left.getName())
                    && "NUMBER".equalsIgnoreCase(right.getName())) {
                return numericSupertype((NumericType) left, (NumericType) right);
            }
        }
        if (left instanceof StringType && right instanceof StringType
                || left instanceof BinaryType && right instanceof BinaryType) {
            return left.getCommonType(right);
        }
        // A BOOLEAN written FIRST wins over a number or a string that follows it; the reverse order is
        // where the two disagree — a string keeps the lead, and a number is refused outright, which is a
        // refusal this fold does not raise. Returning null there leaves that arm exactly as it was.
        if (left instanceof BooleanType && (right instanceof NumericType
                || right instanceof StringType)) {
            return left;
        }
        if (left instanceof StringType && right instanceof BooleanType) {
            return left;
        }
        if (left instanceof IntervalDayTimeType && right instanceof IntervalDayTimeType) {
            // Two intervals of one family fold to the FIELDS of the one written first, whatever fields the other
            // spans: INTERVAL '2' HOUR UNION ALL INTERVAL '1' DAY is an INTERVAL HOUR(9), the reverse an INTERVAL
            // DAY(9), and COALESCE takes its first branch's fields the same way. The digits widen to the larger
            // of the two, leading and fractional alike: DAY(2) with HOUR(5) is DAY(5), SECOND(2,3) with MINUTE(4)
            // SECOND(4,3), DAY(3) TO SECOND(3) with HOUR(9) DAY(9) TO SECOND(3) (live-verified). The two
            // families never meet: a day-time arm beside a year-month one is refused.
            final IntervalDayTimeType first = (IntervalDayTimeType) left;
            final IntervalDayTimeType second = (IntervalDayTimeType) right;
            return IntervalDayTimeType.of(first.getQualifier(),
                Math.max(first.getLeadingPrecision(), second.getLeadingPrecision()),
                Math.max(first.getFractionalPrecision(), second.getFractionalPrecision()));
        }
        if (left instanceof IntervalYearMonthType && right instanceof IntervalYearMonthType) {
            // The first one's fields, the larger leading digits: YEAR(2) with MONTH(5) is YEAR(5), the reverse
            // MONTH(5) (live-verified).
            return IntervalYearMonthType.of(((IntervalYearMonthType) left).getQualifier(),
                Math.max(((IntervalYearMonthType) left).getLeadingPrecision(),
                    ((IntervalYearMonthType) right).getLeadingPrecision()));
        }
        return widerTemporal(left, right);
    }

    /**
     * The wider of two temporal ARMS, or null when live does not fold that pair at all.
     *
     * <p>The arms are not the clean total order the branches are. A branch takes the wider of any two,
     * in either written order; an arm does too EXCEPT that a TIMESTAMP_TZ arriving SECOND after an
     * unzoned or local timestamp is refused rather than folded — while the same TZ written FIRST
     * absorbs them both, and a DATE yields to a TZ from either side. The full arm matrix:
     *
     * <pre>
     *          ∪ DATE   ∪ NTZ   ∪ LTZ   ∪ TZ
     *   DATE     DATE    NTZ     LTZ     TZ
     *   NTZ      NTZ     NTZ     LTZ     refused
     *   LTZ      LTZ     LTZ     LTZ     refused
     *   TZ       TZ      TZ      TZ      TZ
     * </pre>
     *
     * <p>TIME is absent from both surfaces: it does not join the family in any order. The flavour that
     * wins carries the wider precision of the two arms, not its own (see {@link #atWiderPrecision}).
     *
     * @param left  the arm written first
     * @param right the arm written second
     * @return the wider temporal, or null where the pair has no fold
     */
    private static DataType widerTemporal(final DataType left, final DataType right) {
        if (!(left instanceof DateTimeType) || !(right instanceof DateTimeType)) {
            return null;
        }
        final int leftRank = temporalRank(left.getName());
        final int rightRank = temporalRank(right.getName());
        if (leftRank < 0 || rightRank < 0) {
            return null;
        }
        if (rightRank == TEMPORAL_WIDENING_ORDER.length - 1 && leftRank > 0) {
            return null;
        }
        return atWiderPrecision(leftRank >= rightRank ? left : right, left, right);
    }

    /**
     * The temporal a fold settled on, carrying the WIDER fractional-second precision of the two it
     * folded. The precision folds apart from the flavour: whichever arm wins the flavour, and in
     * whichever order the two are written, the column keeps every digit either side can hold. Live
     * declares these for a set operation's arms, and a VALUES list's rows fold the same way — a
     * TIMESTAMP_NTZ(0) row beside a TIMESTAMP_NTZ(6) one is TIMESTAMP_NTZ(6):
     *
     * <pre>
     *   TIMESTAMP_NTZ(3) with TIMESTAMP_NTZ(9)   TIMESTAMP_NTZ(9)   either order
     *   TIME(0)          with TIME(9)            TIME(9)
     *   TIMESTAMP_NTZ(9) with TIMESTAMP_LTZ(3)   TIMESTAMP_LTZ(9)   the flavour of one, the digits of the other
     *   TIMESTAMP_TZ(0)  with TIMESTAMP_LTZ(3)   TIMESTAMP_TZ(3)
     *   DATE             with TIMESTAMP_NTZ(3)   TIMESTAMP_NTZ(3)   a DATE carries no fraction to add
     * </pre>
     *
     * <p>A pair that is not two temporals, and a DATE that wins, come back as the winner they were.
     *
     * @param winner the type the fold settled on
     * @param left   one side of the fold
     * @param right  the other
     * @return the winner, at the wider precision when both sides are temporals
     */
    private static DataType atWiderPrecision(final DataType winner, final DataType left, final DataType right) {
        if (!(winner instanceof DateTimeType) || !(left instanceof DateTimeType)
                || !(right instanceof DateTimeType) || "DATE".equalsIgnoreCase(winner.getName())) {
            return winner;
        }
        final DateTimeType held = (DateTimeType) winner;
        final int precision = Math.max(fractionalDigits((DateTimeType) left), fractionalDigits((DateTimeType) right));
        return held.getPrecision() == precision ? held
            : new DateTimeType(held.getName(), precision, held.hasTimeZone());
    }

    /** A temporal's fractional-second digits; a DATE has none. */
    private static int fractionalDigits(final DateTimeType temporal) {
        return "DATE".equalsIgnoreCase(temporal.getName()) ? 0 : temporal.getPrecision();
    }

    /**
     * A recursive CTE's column once its recursive term has run: the anchor's type, at the wider
     * fractional-second precision when the anchor is a TIMESTAMP and the term's is one of the same
     * flavour. Neither side wins outright — live declares the column of an anchor {@code n3} with a term
     * {@code DATEADD(day, 1, x)} TIMESTAMP_NTZ(9), and of an anchor {@code n9} with a term
     * {@code x::TIMESTAMP_NTZ(3)} TIMESTAMP_NTZ(9) too. A TIME is left as the anchor declares it: live
     * does not fold two TIME precisions here at all, it refuses the pair as a type mismatch between the
     * anchor and the recursive term. Every other pair keeps the anchor's type as well.
     *
     * @param anchor the anchor's column type
     * @param term   the recursive term's column type
     * @return the column's type
     */
    public static DataType recursiveColumn(final DataType anchor, final DataType term) {
        if (anchor instanceof DateTimeType && temporalRank(anchor.getName()) > 0 && sameDeclaredType(anchor, term)) {
            return atWiderPrecision(anchor, anchor, term);
        }
        return anchor;
    }

    /**
     * Snowflake's numeric supertype: the widest INTEGER PART meets the widest SCALE.
     *
     * @param left  one number
     * @param right the other
     * @return the number that holds both
     */
    public static NumericType numericSupertype(final NumericType left, final NumericType right) {
        final int integerDigits = Math.max(left.getPrecision() - left.getScale(),
            right.getPrecision() - right.getScale());
        final int scale = Math.max(left.getScale(), right.getScale());
        return new NumericType("NUMBER", Math.min(integerDigits + scale, MAX_PRECISION), scale);
    }

    /** Whether any branch is an exact or approximate NUMBER, which is what a string literal joins. */
    private static boolean hasNumericBranch(final List<DataType> branchTypes) {
        for (final DataType type : branchTypes) {
            if (type instanceof NumericType) {
                return true;
            }
        }
        return false;
    }

    /**
     * What a VARCHAR branch contributes when it sits beside exactly one OTHER family: it joins that
     * family rather than dragging everything to text.
     *
     * @param nonString the one other family present
     * @return the type the string contributes, or null when the pairing is not a measured one
     */
    public static DataType stringContribution(final DataType nonString) {
        if (nonString instanceof NumericType) {
            final String name = nonString.getName();
            if ("NUMBER".equalsIgnoreCase(name)) {
                return new NumericType("NUMBER", 18, 5);
            }
            if ("FLOAT".equalsIgnoreCase(name) || "DOUBLE".equalsIgnoreCase(name)) {
                return nonString;
            }
            return null;
        }
        // EVERY temporal takes the string with it — live declares COALESCE(vt, ts) TIMESTAMP_NTZ,
        // COALESCE(vt, tl) TIMESTAMP_LTZ and COALESCE(vt, tm) TIME, in either written order. TIME is
        // included even though temporalRank leaves it OUTSIDE the widening order: that rank governs
        // which temporal wins against ANOTHER temporal, where a TIME beside a TIMESTAMP is refused,
        // and it has nothing to say about a string, which joins all four alike.
        if (nonString instanceof DateTimeType) {
            return nonString;
        }
        return null;
    }

    /**
     * Temporals of ONE flavour fold to the WIDER precision — live declares
     * {@code IFF(c, TIMESTAMP_NTZ(3), TIMESTAMP_NTZ(9))} at scale 9. Only branches reach this; a set
     * operation's arms fold pairwise in {@link #combine}, which takes the wider precision the same way.
     *
     * @param branchTypes the branch types
     * @return the folded temporal, or null when the branches are not all temporals of one flavour
     */
    private static DataType temporalFold(final List<DataType> branchTypes) {
        int precision = -1;
        DateTimeType winner = null;
        for (final DataType type : branchTypes) {
            if (!(type instanceof DateTimeType)) {
                return null;
            }
            final DateTimeType temporal = (DateTimeType) type;
            if (winner == null) {
                winner = temporal;
            } else if (!winner.getName().equalsIgnoreCase(temporal.getName())) {
                final int challenger = temporalRank(temporal.getName());
                final int held = temporalRank(winner.getName());
                if (challenger < 0 || held < 0) {
                    return null;
                }
                if (challenger > held) {
                    winner = temporal;
                }
            }
            precision = Math.max(precision, temporal.getPrecision());
        }
        return winner == null ? null
            : new DateTimeType(winner.getName(), precision, winner.hasTimeZone());
    }

    /**
     * Where a temporal sits in the widening order, or -1 for one that does not widen into the others.
     * A DATE joins a TIMESTAMP, and the zoned flavours take the zone with them — live declares
     * {@code COALESCE(d, ts)} TIMESTAMP_NTZ, {@code COALESCE(ts, tl)} TIMESTAMP_LTZ and
     * {@code COALESCE(tl, tz)} TIMESTAMP_TZ, in either written order. A TIME is outside the order
     * entirely: live refuses {@code COALESCE(tm, ts)} and {@code COALESCE(d, tm)} outright.
     *
     * @param name the temporal's name
     * @return its rank, or -1 when it does not widen
     */
    private static int temporalRank(final String name) {
        for (int i = 0; i < TEMPORAL_WIDENING_ORDER.length; i++) {
            if (TEMPORAL_WIDENING_ORDER[i].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Two branches folded by the rules that hold for BRANCHES alone. A set operation's arms reach
     * {@link #combine} directly and are deliberately left as they were: those arms feed the value scan
     * when they disagree, and narrowing them changes which rows come back, not just the metadata.
     *
     * @param left  the type held so far, in written order
     * @param right the next branch's type
     * @return the folded type, or null when the pair has no measured answer
     */
    private static DataType foldPair(final DataType left, final DataType right) {
        if (left instanceof NumericType && right instanceof NumericType) {
            // An approximate number swallows an exact one whichever side it is written on: live
            // declares COALESCE(n, f), COALESCE(f, i), GREATEST(n, f) and NVL(f, n) all FLOAT.
            if (NumericType.isApproximate(left)) {
                return left;
            }
            if (NumericType.isApproximate(right)) {
                return right;
            }
        }
        if (left instanceof BooleanType && right instanceof NumericType) {
            return left;
        }
        if (left instanceof NumericType && right instanceof BooleanType) {
            return right;
        }
        // A boolean beside a STRING is the one pair whose answer depends on which was written first:
        // live declares COALESCE(bo, v) BOOLEAN and COALESCE(v, bo) VARCHAR(134217728) — the string
        // survives, but at the width it takes when the other side carries no length of its own.
        if (left instanceof BooleanType && right instanceof StringType) {
            return left;
        }
        if (left instanceof StringType && right instanceof BooleanType) {
            return new StringType("VARCHAR", UNKNOWN_LENGTH_VARCHAR);
        }
        return combine(left, right);
    }

    /**
     * The type a whole set of branches folds to, or null when any branch is undetermined or the set
     * cannot be combined. Undetermined stays undetermined: a branch whose type is unknown may hold
     * anything, so narrowing to the others' type would be a guess.
     *
     * @param branchTypes each branch's declared type, in written order
     * @return the folded type, or null
     */
    /**
     * A UUID branch DOMINATES the text ones beside it: the account folds a UUID with a VARCHAR, and a
     * UUID with an untyped NULL, to UUID rather than to the widest text. Null when no branch is one, or
     * when some branch belongs to another family — that pair is the ordinary fold's to settle.
     *
     * @param branchTypes each branch's declared type
     * @return UUID when the branches fold to it, otherwise null
     */
    private static DataType uuidFold(final List<DataType> branchTypes) {
        boolean anyUuid = false;
        for (final DataType type : branchTypes) {
            if (type instanceof UuidType) {
                anyUuid = true;
            } else if (type != null && !(type instanceof StringType)) {
                // An untyped branch — a bare NULL — does not stop the fold: live reads IFF(TRUE, u, NULL)
                // as a UUID.
                return null;
            }
        }
        return anyUuid ? UuidType.UUID : null;
    }

    public static DataType foldBranches(final List<DataType> branchTypes) {
        return foldBranches(branchTypes, null);
    }

    /**
     * The branch fold, told which branches were written as STRING LITERALS and what each measures as
     * a number. A string literal beside a number contributes exactly what the same number written
     * WITHOUT quotes would — measured on a real account, and the pair is identical either way:
     *
     * <pre>
     *   COALESCE(n2, '5.12345')        NUMBER(13,5)      n2 is NUMBER(10,2)
     *   COALESCE(n2,  5.12345 )        NUMBER(13,5)      the same, unquoted
     *   COALESCE(n2, '123456789012')   NUMBER(14,2)
     *   COALESCE(n2,  123456789012 )   NUMBER(14,2)      the same again
     * </pre>
     *
     * <p>A string COLUMN is different and keeps the flat NUMBER(18,5) — it has no literal text to
     * measure, so live cannot narrow it. That column-versus-literal split is the whole reason this
     * needs the branch EXPRESSION and not just its type.
     *
     * <p>The old behaviour answered NUMBER(18,5) for every string branch. It happened to be right for
     * a one-digit literal like {@code '5'}, where the fold with NUMBER(10,2) lands on NUMBER(10,2)
     * either way — which is exactly why measuring only that shape left the rule underdetermined.
     *
     * @param branchTypes         each branch's declared type
     * @param literalMeasurements per branch, the numeric type a STRING LITERAL branch measures as, or
     *                            null for a branch that is not one; null for the whole list when the
     *                            caller has no expressions to read
     * @return the folded type, or null when the branches do not fold
     */
    public static DataType foldBranches(final List<DataType> branchTypes,
                                        final List<DataType> literalMeasurements) {
        if (branchTypes.isEmpty()) {
            return null;
        }
        if (literalMeasurements != null) {
            final List<DataType> measured = new ArrayList<>(branchTypes.size());
            boolean anyMeasured = false;
            for (int i = 0; i < branchTypes.size(); i++) {
                final DataType measurement = i < literalMeasurements.size()
                    ? literalMeasurements.get(i) : null;
                if (measurement != null && branchTypes.get(i) instanceof StringType) {
                    measured.add(measurement);
                    anyMeasured = true;
                } else {
                    measured.add(branchTypes.get(i));
                }
            }
            // Only when some OTHER branch is a number: a string literal beside a string column is
            // still text, and measuring it would drag a text fold into the numeric family.
            if (anyMeasured && hasNumericBranch(branchTypes)) {
                return foldBranches(measured, null);
            }
        }
        final DataType temporal = temporalFold(branchTypes);
        if (temporal != null) {
            return temporal;
        }
        final DataType uuid = uuidFold(branchTypes);
        if (uuid != null) {
            return uuid;
        }
        boolean anyString = false;
        DataType nonString = null;
        boolean mixedNonString = false;
        for (final DataType type : branchTypes) {
            if (type == null) {
                return null;
            }
            if (type instanceof StringType) {
                anyString = true;
            } else if (nonString == null) {
                nonString = type;
            } else if (!nonString.getClass().equals(type.getClass())) {
                mixedNonString = true;
            }
        }
        DataType combined = null;
        for (final DataType type : branchTypes) {
            DataType contribution = type;
            // A BOOLEAN is the one other family a string does not join: it has no length to join it
            // WITH, so that pair is settled left to right in foldPair instead.
            if (anyString && nonString != null && !mixedNonString && type instanceof StringType
                    && !(nonString instanceof BooleanType)) {
                contribution = stringContribution(nonString);
                if (contribution == null) {
                    return null;
                }
            }
            combined = combined == null ? contribution : foldPair(combined, contribution);
            if (combined == null) {
                return null;
            }
        }
        return combined;
    }
}
