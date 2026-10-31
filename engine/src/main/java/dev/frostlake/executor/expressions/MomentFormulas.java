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

/**
 * The formulas the plan writes for the variance family and the moments, as the echo of a misplaced aggregate prints
 * them (live-verified). Each is spelled over the value's COUNT and the SUMs of its powers, and typed as the plan types
 * arithmetic: over a FLOAT every exact operand is cast to FLOAT, and over exact numbers
 *
 * <ul>
 *   <li>a product keeps the sum of its operands' scales up to 12, and past that is SCALED_DECIMAL_MULTIPLY naming
 *       the digits dropped: {@code SCALED_DECIMAL_MULTIPLY((4 * (M)) * (M), M, 6)} for a mean of scale 6;</li>
 *   <li>a sum or a difference casts its lower-scale operand to the higher scale, as many leading digits kept as 38
 *       digits allow: {@code (CAST(SUM(((X * X) * X) * X) AS NUMBER(38,6))) - …};</li>
 *   <li>a quotient the formula writes is SCALED_ROUND_INT_DIVIDE, of the dividend's scale plus 6 up to 12, while a
 *       mean over a scaled value casts its sum to that scale first: {@code (CAST(SUM(D) AS NUMBER(28,8))) / (COUNT(D))}
 *       over a NUMBER(10,2), whose SUM is a NUMBER(22,2);</li>
 *   <li>a zero beside a value of another scale is cast to the value's type: {@code < (CAST(0 AS NUMBER(38,4)))}.</li>
 * </ul>
 *
 * <p>A text or a VARIANT is the FLOAT it converts to, counted as it is: {@code SUM(CAST(S AS FLOAT))} beside
 * {@code COUNT(S)}.
 */
final class MomentFormulas {

    /** The scale a product or a quotient keeps at most, unless an operand's own scale is higher. */
    private static final int SCALE_LIMIT = 12;

    /** The digits a quotient's scale gains over its dividend's. */
    private static final int QUOTIENT_DIGITS = 6;

    /** The digits a SUM's precision gains over its argument's. */
    private static final int SUM_DIGITS = 12;

    /** A COUNT's digits: it is a NUMBER(18,0). */
    private static final int COUNT_DIGITS = 18;

    private MomentFormulas() {
    }

    /**
     * The formula an aggregate of the variance family or a moment is planned as, or null for any other name.
     *
     * @param name     the aggregate's name, upper case
     * @param argument the value it reads
     * @param distinct whether the call reads distinct values, which every COUNT and SUM of the formula then reads
     * @return the formula, or null
     */
    static String oneArgument(final String name, final MomentArgument argument, final boolean distinct) {
        final PlanTerm[] parts = parts(argument.value(), argument.getPrinted(), distinct);
        switch (name) {
            case "VARIANCE":
            case "VARIANCE_SAMP":
            case "VAR_SAMP":
                return variance(parts, true).getText();
            case "VARIANCE_POP":
            case "VAR_POP":
                return variance(parts, false).getText();
            case "STDDEV":
            case "STDDEV_SAMP":
                return root(variance(parts, true)).getText();
            case "STDDEV_POP":
                return root(variance(parts, false)).getText();
            case "KURTOSIS":
                return kurtosis(parts).getText();
            case "SKEW":
                return skew(parts).getText();
            default:
                return null;
        }
    }

    /**
     * The formula a covariance, a correlation or a regression aggregate is planned as, or null for any other name. Each
     * reads the pairs whose members are both there ({@link MomentArgument#pairedWith}), as FLOAT, the dependent value
     * first; REGR_R2 asks the variances of the arguments themselves first.
     *
     * @param name   the aggregate's name, upper case
     * @param first  its first argument, the dependent value
     * @param second its second argument, the independent value
     * @return the formula, or null
     */
    static String twoArguments(final String name, final MomentArgument first, final MomentArgument second) {
        switch (name) {
            case "REGR_COUNT":
                return count(second.pairedWith(first)).getText();
            case "REGR_AVGX":
                return mean(second.pairedWith(first)).getText();
            case "REGR_AVGY":
                return mean(first.pairedWith(second)).getText();
            case "REGR_SXX":
                return squares(second.pairedWith(first), second.pairedWith(first)).getText();
            case "REGR_SYY":
                return squares(first.pairedWith(second), second.pairedWith(first)).getText();
            case "REGR_SXY":
                return crossProducts(first, second).getText();
            case "COVAR_POP":
                return covariance(first, second, false).getText();
            case "COVAR_SAMP":
                return covariance(first, second, true).getText();
            case "REGR_SLOPE":
                return slope(first, second).getText();
            case "REGR_INTERCEPT":
                return minus(mean(first.pairedWith(second)), times(slope(first, second),
                    mean(second.pairedWith(first)))).getText();
            case "CORR":
                return correlation(first, second).getText();
            case "REGR_R2":
                return determination(first, second).getText();
            default:
                return null;
        }
    }

    /** The covariance: the sum of the pairs' products less the product of their sums over n, over n, or n - 1. */
    private static PlanTerm covariance(final MomentArgument first, final MomentArgument second, final boolean sample) {
        final PlanTerm one = second.pairedWith(first);
        final PlanTerm other = first.pairedWith(second);
        final PlanTerm n = count(one);
        final PlanTerm centred = minus(sum(times(one, other), ""), quotient(times(sum(one, ""), sum(other, "")),
            nullIfZero(n)));
        return quotient(centred, nullIfZero(sample ? minus(n, literal(1)) : n));
    }

    /** The slope: the covariance of the pairs, independent value first, over the independent value's variance. */
    private static PlanTerm slope(final MomentArgument first, final MomentArgument second) {
        return quotient(covariance(second, first, false), nullIfZero(pairVariance(second.pairedWith(first))));
    }

    /** The correlation: the covariance over the product of the two members' deviations. */
    private static PlanTerm correlation(final MomentArgument first, final MomentArgument second) {
        return quotient(covariance(first, second, false), nullIfZero(times(root(pairVariance(
            second.pairedWith(first))), root(pairVariance(first.pairedWith(second))))));
    }

    /**
     * The coefficient of determination: NULL where the independent value does not vary, 1 where only the dependent one
     * does not, the correlation squared otherwise — the variances being the arguments' own, not the pairs'.
     */
    private static PlanTerm determination(final MomentArgument first, final MomentArgument second) {
        final PlanTerm dependent = variance(parts(first.value(), first.getPrinted(), false), false);
        final PlanTerm independent = variance(parts(second.value(), second.getPrinted(), false), false);
        final PlanTerm still = new PlanTerm(compared(dependent, "=", beside(dependent, literal(0))).operand()
            + " AND " + compared(independent, "<>", beside(independent, literal(0))).operand(), false, false, 0, 0);
        final PlanTerm squared = new PlanTerm("SQUARE(" + correlation(first, second).getText() + ")", false, true, 0,
            0);
        return either(compared(independent, "=", beside(independent, literal(0))),
            new PlanTerm("SYSTEM$NULL_TO_REAL(null)", false, true, 0, 0), either(still, literal(1), squared));
    }

    /** The sum of a pair member's squares less its sum squared over the count of {@code counted}. */
    private static PlanTerm squares(final PlanTerm member, final PlanTerm counted) {
        return minus(new PlanTerm("SUM(SQUARE(" + member.getText() + "))", false, true, 0, 0),
            quotient(new PlanTerm("SQUARE(SUM(" + member.getText() + "))", false, true, 0, 0), count(counted)));
    }

    /** The sum of the arguments' own products less the product of the pairs' sums over their count. */
    private static PlanTerm crossProducts(final MomentArgument first, final MomentArgument second) {
        final PlanTerm one = second.pairedWith(first);
        return minus(sum(times(second.value(), first.value()), ""),
            quotient(times(sum(one, ""), sum(first.pairedWith(second), "")), count(one)));
    }

    /** The population variance of a pair member, counted as itself. */
    private static PlanTerm pairVariance(final PlanTerm member) {
        return variance(parts(member, member.getText(), false), false);
    }

    /** The value's COUNT and the SUMs of its first four powers, in that order. */
    private static PlanTerm[] parts(final PlanTerm value, final String counted, final boolean distinct) {
        final String word = distinct ? "DISTINCT " : "";
        final PlanTerm square = times(value, value);
        final PlanTerm cube = times(square, value);
        return new PlanTerm[] {new PlanTerm("COUNT(" + word + counted + ")", false, false, COUNT_DIGITS, 0),
            sum(value, word), sum(square, word), sum(cube, word), sum(times(cube, value), word)};
    }

    /** The count of a term. */
    private static PlanTerm count(final PlanTerm term) {
        return new PlanTerm("COUNT(" + term.getText() + ")", false, false, COUNT_DIGITS, 0);
    }

    /** A pair member's mean, its sum over its count as FLOAT. */
    private static PlanTerm mean(final PlanTerm member) {
        return quotient(sum(member, ""), count(member));
    }

    /** A term NULL where it is zero: {@code NULLIF(n, 0)}, the zero cast to a FLOAT term's type. */
    private static PlanTerm nullIfZero(final PlanTerm term) {
        return new PlanTerm("NULLIF(" + term.getText() + ", " + (term.isReal() ? beside(term, literal(0)).getText()
            : "0") + ")", false, term.isReal(), term.getLeading(), term.getScale());
    }

    /**
     * The sample variance, {@code n * SUM(x * x) - SUM(x) * SUM(x)} clipped at zero over {@code n * (n - 1)}, or the
     * population's over {@code n * n}.
     */
    private static PlanTerm variance(final PlanTerm[] parts, final boolean sample) {
        final PlanTerm count = parts[0];
        final PlanTerm spread = minus(times(count, parts[2]), times(parts[1], parts[1]));
        final PlanTerm zero = beside(spread, literal(0));
        final PlanTerm clipped = either(compared(spread, "<", zero), zero, spread);
        final PlanTerm pairs = sample ? times(count, minus(count, literal(1))) : times(count, count);
        return quotient(clipped, nullIfZero(pairs));
    }

    /**
     * The sample excess kurtosis: {@code (n + 1) * n / ((n - 1) * (n - 2) * (n - 3))} times the fourth central sum
     * over the variance squared, less {@code 3 * (n - 1) * (n - 1) / ((n - 2) * (n - 3))}; NULL where fewer than four
     * values are read.
     */
    private static PlanTerm kurtosis(final PlanTerm[] parts) {
        final PlanTerm n = parts[0];
        final PlanTerm mean = valueMean(n, parts[1]);
        final PlanTerm four = mean.isReal() ? beside(mean, literal(4)) : literal(4);
        final PlanTerm six = mean.isReal() ? beside(mean, literal(6)) : literal(6);
        final PlanTerm central = plus(minus(plus(minus(parts[4], times(times(four, mean), parts[3])),
            times(times(times(six, mean), mean), parts[2])), times(times(times(times(four, mean), mean), mean),
            parts[1])), times(times(times(times(n, mean), mean), mean), mean));
        final PlanTerm factor = quotient(fraction(times(plus(n, literal(1)), n)),
            times(times(minus(n, literal(1)), minus(n, literal(2))), minus(n, literal(3))));
        final PlanTerm variance = variance(parts, true);
        final PlanTerm shift = quotient(times(times(literal(3), minus(n, literal(1))), minus(n, literal(1))),
            times(minus(n, literal(2)), minus(n, literal(3))));
        return guarded(compared(times(times(times(n, minus(n, literal(1))), minus(n, literal(2))),
            minus(n, literal(3))), "=", literal(0)),
            minus(quotient(times(factor, central), times(variance, variance)), shift));
    }

    /**
     * The sample skewness: {@code n / ((n - 1) * (n - 2))} times the third central sum over the standard deviation
     * cubed, a FLOAT; NULL where fewer than three values are read.
     */
    private static PlanTerm skew(final PlanTerm[] parts) {
        final PlanTerm n = parts[0];
        final PlanTerm mean = valueMean(n, parts[1]);
        final PlanTerm three = mean.isReal() ? beside(mean, literal(3)) : literal(3);
        final PlanTerm central = minus(plus(minus(parts[3], times(times(three, mean), parts[2])),
            times(times(times(three, mean), mean), parts[1])), times(times(times(n, mean), mean), mean));
        final PlanTerm factor = quotient(fraction(n), times(minus(n, literal(1)), minus(n, literal(2))));
        final PlanTerm deviation = root(variance(parts, true));
        return guarded(compared(times(times(n, minus(n, literal(1))), minus(n, literal(2))), "=", literal(0)),
            quotient(times(factor, central), times(times(deviation, deviation), deviation)));
    }

    /** A value NULL where its guard holds, the NULL typed by the value's family. */
    private static PlanTerm guarded(final PlanTerm guard, final PlanTerm value) {
        return either(guard, new PlanTerm("SYSTEM$NULL_TO_" + (value.isReal() ? "REAL" : "FIXED") + "(null)", false,
            value.isReal(), 0, 0), value);
    }

    /** A count cast to NUMBER(38,10), as a moment's leading fraction divides it. */
    private static PlanTerm fraction(final PlanTerm count) {
        return new PlanTerm("CAST(" + count.getText() + " AS NUMBER(38,10))", false, false, 28, 10);
    }

    /** The mean, a sum over its count: SCALED_ROUND_INT_DIVIDE over whole numbers, the sum rescaled otherwise. */
    private static PlanTerm valueMean(final PlanTerm count, final PlanTerm sum) {
        if (sum.isReal() || sum.getScale() == 0) {
            return quotient(sum, count);
        }
        final int scale = quotientScale(sum.getScale());
        final PlanTerm scaled = new PlanTerm("CAST(" + sum.getText() + " AS NUMBER("
            + Math.min(PlanTerm.MAX_DIGITS, sum.getLeading() + scale) + "," + scale + "))", false, false,
            sum.getLeading(), scale);
        return new PlanTerm(scaled.operand() + " / " + count.operand(), false, false,
            scaled.getLeading() + count.getScale(), scale);
    }

    private static PlanTerm sum(final PlanTerm value, final String word) {
        final String text = "SUM(" + word + value.getText() + ")";
        if (value.isReal()) {
            return new PlanTerm(text, false, true, 0, 0);
        }
        final int precision = Math.min(PlanTerm.MAX_DIGITS, value.getLeading() + value.getScale() + SUM_DIGITS);
        return new PlanTerm(text, false, false, precision - value.getScale(), value.getScale());
    }

    private static PlanTerm times(final PlanTerm a, final PlanTerm b) {
        if (a.isReal() || b.isReal()) {
            return new PlanTerm(real(a).operand() + " * " + real(b).operand(), false, true, 0, 0);
        }
        final int full = a.getScale() + b.getScale();
        final int scale = Math.min(full, Math.max(SCALE_LIMIT, Math.max(a.getScale(), b.getScale())));
        final int leading = a.getLeading() + b.getLeading();
        if (full > scale) {
            return new PlanTerm("SCALED_DECIMAL_MULTIPLY(" + a.getText() + ", " + b.getText() + ", " + (full - scale)
                + ")", false, false, leading, scale);
        }
        return new PlanTerm(a.operand() + " * " + b.operand(), false, false, leading, scale);
    }

    private static PlanTerm plus(final PlanTerm a, final PlanTerm b) {
        return combined(a, b, "+");
    }

    private static PlanTerm minus(final PlanTerm a, final PlanTerm b) {
        return combined(a, b, "-");
    }

    private static PlanTerm combined(final PlanTerm a, final PlanTerm b, final String sign) {
        if (a.isReal() || b.isReal()) {
            return new PlanTerm(real(a).operand() + " " + sign + " " + real(b).operand(), false, true, 0, 0);
        }
        final int scale = Math.max(a.getScale(), b.getScale());
        return new PlanTerm(aligned(a, scale).operand() + " " + sign + " " + aligned(b, scale).operand(), false, false,
            Math.max(a.getLeading(), b.getLeading()) + 1, scale);
    }

    /** A term at a higher scale: cast to it, its leading digits kept as far as 38 digits allow. */
    private static PlanTerm aligned(final PlanTerm term, final int scale) {
        if (term.getScale() >= scale) {
            return term;
        }
        return new PlanTerm("CAST(" + term.getText() + " AS NUMBER("
            + Math.min(PlanTerm.MAX_DIGITS, term.getLeading() + scale) + "," + scale + "))", false, false,
            term.getLeading(), scale);
    }

    private static PlanTerm quotient(final PlanTerm a, final PlanTerm b) {
        if (a.isReal() || b.isReal()) {
            return new PlanTerm(real(a).operand() + " / " + real(b).operand(), false, true, 0, 0);
        }
        return new PlanTerm("SCALED_ROUND_INT_DIVIDE(" + a.getText() + ", " + b.getText() + ")", false, false,
            a.getLeading() + b.getScale(), quotientScale(a.getScale()));
    }

    private static int quotientScale(final int scale) {
        return Math.max(scale, Math.min(scale + QUOTIENT_DIGITS, SCALE_LIMIT));
    }

    /** A literal beside a FLOAT or a value of another scale, cast to that value's type; as it is otherwise. */
    private static PlanTerm beside(final PlanTerm value, final PlanTerm literal) {
        if (value.isReal()) {
            return new PlanTerm("CAST(" + literal.getText() + " AS FLOAT)", false, true, 0, 0);
        }
        if (value.getScale() != literal.getScale()) {
            return new PlanTerm("CAST(" + literal.getText() + " AS " + value.typeName() + ")", false, false,
                value.getLeading(), value.getScale());
        }
        return literal;
    }

    private static PlanTerm compared(final PlanTerm a, final String operator, final PlanTerm b) {
        return new PlanTerm(a.operand() + " " + operator + " " + b.operand(), false, false, 0, 0);
    }

    private static PlanTerm either(final PlanTerm condition, final PlanTerm a, final PlanTerm b) {
        final boolean real = a.isReal() || b.isReal();
        final PlanTerm first = real ? real(a) : a;
        final PlanTerm second = real ? real(b) : b;
        return new PlanTerm("IFF(" + condition.getText() + ", " + first.getText() + ", " + second.getText() + ")",
            false, real, Math.max(first.getLeading(), second.getLeading()), Math.max(first.getScale(),
            second.getScale()));
    }

    private static PlanTerm root(final PlanTerm value) {
        return new PlanTerm("SQRT(" + real(value).getText() + ")", false, true, 0, 0);
    }

    private static PlanTerm real(final PlanTerm term) {
        return term.isReal() ? term : new PlanTerm("CAST(" + term.getText() + " AS FLOAT)", false, true, 0, 0);
    }

    private static PlanTerm literal(final int value) {
        final String text = String.valueOf(value);
        return new PlanTerm(text, true, false, text.length(), 0);
    }
}
