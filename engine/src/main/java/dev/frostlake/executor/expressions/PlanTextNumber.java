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

import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * How the account's plan reads a text LITERAL as a number beside an exact one — the width its echoes spell
 * ({@code RT.N * (TO_NUMBER('1.50', 18, 1))}) and the constant its folding carries out
 * ({@code '1.50' * 1} is {@code CAST(1.5 AS NUMBER(19,1))}), live-verified:
 *
 * <ul>
 *   <li>a text that spells a number with nothing about it — a sign, digits, a point, an exponent — reads as the
 *       number it spells, eighteen digits wide, or as wide as its whole digits need, at the scale of its value:
 *       {@code '1.50'}, {@code '01.10'} and {@code '15e-1'} are NUMBER(18,1), {@code '1.0'}, {@code '1e2'} and
 *       {@code '1.5e1'} NUMBER(18,0), {@code '1e20'} NUMBER(21,0);</li>
 *   <li>any other text — a blank about it ({@code ' 1 '}) or no number at all ({@code 'abc'}) — reads as a text
 *       VALUE does, NUMBER(18,5): its number, where it has one, is rounded to five decimals
 *       ({@code ' 1.123456 '} is 1.12346), and one past thirteen whole digits has none.</li>
 * </ul>
 */
final class PlanTextNumber {

    private static final int DIGITS = 18;
    private static final int MAX_PRECISION = 38;
    private static final int MAX_SCALE = 37;

    private PlanTextNumber() {
    }

    /**
     * The number a text spells with nothing about it, or null for any other text.
     *
     * @param text the literal's text
     * @return the exact number, or null
     */
    static BigDecimal spelled(final String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        final BigDecimal number;
        try {
            number = DecimalText.parse(text);
        } catch (final NumberFormatException notSpelled) {
            return null;
        }
        final BigDecimal stripped = number.signum() == 0 ? BigDecimal.ZERO : number.stripTrailingZeros();
        return stripped.scale() > MAX_SCALE || stripped.precision() - stripped.scale() > MAX_PRECISION
            ? null : stripped;
    }

    /**
     * The width the text reads at.
     *
     * @param text the literal's text
     * @return NUMBER(18, s) or wider for a spelled number, NUMBER(18,5) for any other text
     */
    static NumericType type(final String text) {
        final BigDecimal number = spelled(text);
        if (number == null) {
            return ImpliedTextNumber.COLUMN_READING;
        }
        final int scale = Math.max(0, number.scale());
        final int whole = Math.max(0, number.precision() - number.scale());
        return new NumericType("NUMBER", Math.min(MAX_PRECISION, Math.max(DIGITS, whole + scale)), scale);
    }

    /**
     * The width a conditional over text literals reads at beside an exact number: IFF and CASE whose results are
     * all text literals read at the widest scale their results spell — {@code IFF(TRUE, '1.50', '2') * 1} and
     * {@code IFF(FALSE, '1.50', '2') * 1} are both NUMBER(19,1) — where NVL and COALESCE over the same texts
     * read as any text value does. A NULL result spells nothing and widens nothing: {@code IFF(TRUE, '1.50',
     * NULL) * 1} and {@code CASE WHEN FALSE THEN '1.50' END * 1} are NUMBER(19,1) too (live-verified).
     *
     * @param operand the operand
     * @return the width, or null for any other operand
     */
    static NumericType typeOfSettled(final Expression operand) {
        final List<Expression> results = settledResults(operand);
        if (results == null) {
            return null;
        }
        int precision = DIGITS;
        int scale = 0;
        for (final Expression result : results) {
            final NumericType read = type(String.valueOf(((LiteralExpression) result).getValue()));
            precision = Math.max(precision, read.getPrecision());
            scale = Math.max(scale, read.getScale());
        }
        return new NumericType("NUMBER", precision, scale);
    }

    /**
     * The width the numbers such a conditional's results spell need between them — the most whole digits and the
     * widest scale: {@code IFF(TRUE, '1.50', '2')} NUMBER(2,1). A term beside the conditional is raised to its
     * scale as beside a number of this width, {@code … + 0} casting the 0 to NUMBER(3,1) (live-verified).
     *
     * @param operand the operand
     * @return the width, or null for any other operand and for a result that spells no number
     */
    static NumericType spelledWidthOfSettled(final Expression operand) {
        final List<Expression> results = settledResults(operand);
        if (results == null) {
            return null;
        }
        int whole = 1;
        int scale = 0;
        for (final Expression result : results) {
            final BigDecimal number = spelled(String.valueOf(((LiteralExpression) result).getValue()));
            if (number == null) {
                return null;
            }
            whole = Math.max(whole, number.precision() - number.scale());
            scale = Math.max(scale, Math.max(0, number.scale()));
        }
        return new NumericType("NUMBER", Math.min(MAX_PRECISION, whole + scale), scale);
    }

    /**
     * Whether a conditional over text literals has a NULL among its results — a CASE with no ELSE has one — which
     * the plan converts as it stands: its product by one is not folded and LAST_QUERY_ID's index is not cast,
     * {@code (TO_NUMBER(IFF(…), 18, 1)) * 1} (live-verified).
     *
     * @param operand the operand
     * @return whether it is such a conditional
     */
    static boolean settlesOverNull(final Expression operand) {
        final List<Expression> results = conditionalResults(operand);
        if (results == null || settledResults(operand) == null) {
            return false;
        }
        for (final Expression result : results) {
            if (result == null || !ImpliedTextNumber.isTextLiteral(result)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The text-literal results of an IFF or a CASE whose every result is a text literal or a NULL, one at least
     * a text; null otherwise.
     */
    private static List<Expression> settledResults(final Expression operand) {
        final List<Expression> results = conditionalResults(operand);
        if (results == null) {
            return null;
        }
        final List<Expression> texts = new ArrayList<Expression>();
        for (final Expression result : results) {
            if (result != null && ImpliedTextNumber.isTextLiteral(result)) {
                texts.add(result);
            } else if (result != null && !UntypedNullFold.isUntypedNull(result)
                    && !UntypedNullFold.isNullTextConstant(result)) {
                return null;
            }
        }
        return texts.isEmpty() ? null : texts;
    }

    /** The results of an IFF or a CASE, a CASE with no ELSE ending in a null entry; null for any other operand. */
    private static List<Expression> conditionalResults(final Expression operand) {
        final List<Expression> results = new ArrayList<Expression>();
        if (operand instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) operand;
            if (!"IFF".equalsIgnoreCase(call.getFunctionName()) || call.getArguments().size() != 3) {
                return null;
            }
            results.add(call.getArguments().get(1));
            results.add(call.getArguments().get(2));
        } else if (operand instanceof CaseExpression) {
            final CaseExpression chosen = (CaseExpression) operand;
            for (final WhenClause when : chosen.getWhenClauses()) {
                results.add(when.getResult());
            }
            results.add(chosen.getElseExpression());
        } else {
            return null;
        }
        return results;
    }

    /**
     * The number the text reads as, at the width {@link #type} gives it.
     *
     * @param text the literal's text
     * @return the number, or null where the text has none at that width
     */
    static BigDecimal value(final String text) {
        final BigDecimal number = spelled(text);
        if (number != null) {
            // 1e2 is the 100 it spells, at no scale below zero.
            return number.scale() < 0 ? number.setScale(0) : number;
        }
        try {
            return ImpliedTextNumber.read(text);
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }
}
