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

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Whether GETVARIABLE's name is one the plan folds to NULL, or through a NULL to a text, while the statement
 * compiles — the names it then takes as constants beside the text constants {@link ConstantTextArgument} knows.
 * The account carries a NULL through what is strict in it (all live-verified):
 *
 * <pre>
 *   NULL::INT  NULL::VARCHAR  NULL::DATE  CAST(NULL AS VARCHAR(5))      a conversion of a NULL
 *   +NULL  -NULL  NULL + 1  NULL * 2  NULL / 2  NULL AND TRUE           a sign, arithmetic, an AND
 *   ABS(NULL)  ROUND(NULL)  TRIM(NULL)  SUBSTR(NULL, 1)  LPAD(NULL, 3)
 *   LENGTH(NULL)  TO_VARCHAR(NULL)  TO_DATE(NULL)  DATEADD(day, 1, NULL)
 *   LEAST(NULL, 'a')  GREATEST(NULL, NULL)                              the calls measured to fold
 *   TRY_TO_NUMBER('abc')  TRY_CAST('abc' AS INT)                        a conversion to a number that fails
 *   NVL(NULL, NULL)  COALESCE(NULL, NULL)  IFNULL(NULL, NULL)
 *   NULLIF(NULL, NULL)                                                  conditionals over NULLs alone
 *   IFF(TRUE, NULL, NULL)  IFF(NULL, 'a', 'b')  CASE WHEN NULL THEN 'a' END
 *   DECODE(NULL, NULL, NULL)  DECODE(1, 1, NULL, 'x')                   a conditional a literal settles
 * </pre>
 *
 * <p>It does not fold a comparison, so {@code IFF(1 = 1, NULL, 'x')} is no constant, nor REPLACE, HASH,
 * ARRAY_SIZE, NULLIFZERO, ZEROIFNULL, NVL2, {@code NULLIF('a', 'a')}, {@code COALESCE(NULL, 'x')} — whose text
 * it converts — or a failing TRY conversion to a DATE or a BOOLEAN. A call this class does not name is not folded.
 */
final class FoldedVariableName {

    /** The calls that answer NULL for a NULL argument and that the plan folds over constants. */
    private static final Set<String> STRICT = Set.of("ABS", "ROUND", "TRIM", "SUBSTR", "LPAD", "LENGTH",
        "TO_VARCHAR", "TO_DATE", "DATEADD", "LEAST", "GREATEST");

    /** The conditionals that are NULL over NULLs alone. */
    private static final Set<String> NULL_OVER_NULLS = Set.of("NVL", "COALESCE", "IFNULL", "NULLIF");

    private FoldedVariableName() {
    }

    /**
     * Whether a name folds to NULL or, through a conditional a literal settles, to a text.
     *
     * @param name GETVARIABLE's argument as written
     * @return true when the plan folds it before the statement runs
     */
    static boolean folds(final Expression name) {
        final Expression folded = foldedTo(name);
        return folded != null;
    }

    /**
     * Whether a name is a predicate: a comparison, a NOT or an IS NULL, which is a BOOLEAN and no name at all.
     *
     * @param name GETVARIABLE's argument as written
     * @return true for a predicate the plan does not fold
     */
    static boolean isPredicate(final Expression name) {
        if (name instanceof IsNullExpression) {
            return true;
        }
        if (name instanceof UnaryOperationExpression) {
            return ((UnaryOperationExpression) name).getOperator() == UnaryOperator.NOT;
        }
        if (!(name instanceof BinaryOperationExpression)) {
            return false;
        }
        switch (((BinaryOperationExpression) name).getOperator()) {
            case EQUAL:
            case NOT_EQUAL:
            case LESS_THAN:
            case LESS_THAN_OR_EQUAL:
            case GREATER_THAN:
            case GREATER_THAN_OR_EQUAL:
                return true;
            default:
                return false;
        }
    }

    /** The literal an expression folds to — the NULL, or a text a settled conditional picks — or null for none. */
    private static Expression foldedTo(final Expression expr) {
        if (isNull(expr)) {
            return expr;
        }
        if (expr instanceof CastExpression) {
            final CastExpression cast = (CastExpression) expr;
            if (cast.isTryMode()) {
                return failsAsANumber(cast.getExpression()) && namesANumber(cast.getTargetType())
                    ? nullLiteral() : null;
            }
            return isNull(foldedTo(cast.getExpression())) ? nullLiteral() : null;
        }
        if (expr instanceof UnaryOperationExpression) {
            final UnaryOperationExpression signed = (UnaryOperationExpression) expr;
            return signed.getOperator() != UnaryOperator.NOT && isNull(foldedTo(signed.getOperand()))
                ? nullLiteral() : null;
        }
        if (expr instanceof BinaryOperationExpression) {
            return foldedOperation((BinaryOperationExpression) expr);
        }
        if (expr instanceof CaseExpression) {
            return foldedCase((CaseExpression) expr);
        }
        if (expr instanceof FunctionCallExpression) {
            return foldedCall((FunctionCallExpression) expr);
        }
        return null;
    }

    private static Expression foldedOperation(final BinaryOperationExpression operation) {
        final Expression left = operation.getLeft();
        final Expression right = operation.getRight();
        switch (operation.getOperator()) {
            case ADD:
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
            case MODULO:
                return isNull(foldedTo(left)) && isConstant(right) || isNull(foldedTo(right)) && isConstant(left)
                    ? nullLiteral() : null;
            case AND:
                // NULL AND TRUE is NULL; a FALSE beside it would settle the AND.
                return isNull(left) && isTrueOrNull(right) || isNull(right) && isTrueOrNull(left)
                    ? nullLiteral() : null;
            default:
                return null;
        }
    }

    private static Expression foldedCase(final CaseExpression chosen) {
        for (final WhenClause when : chosen.getWhenClauses()) {
            if (when.isOperandMatch()) {
                return null;
            }
            if (isTrue(when.getCondition())) {
                return textOrFolded(when.getResult());
            }
            if (!isNull(when.getCondition()) && !isFalse(when.getCondition())) {
                return null;
            }
        }
        return chosen.getElseExpression() == null ? nullLiteral() : textOrFolded(chosen.getElseExpression());
    }

    private static Expression foldedCall(final FunctionCallExpression call) {
        if (call.isDistinct() || call.isStar() || call.getFunctionName() == null || call.getNameExpression() != null) {
            return null;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> args = call.getArguments();
        if (STRICT.contains(name)) {
            boolean carriesNull = false;
            final int unit = DateTimeUnitSlot.positionIn(name);
            for (int i = 0; i < args.size(); i++) {
                if (i == unit) {
                    continue;
                }
                final boolean isNullHere = isNull(foldedTo(args.get(i)));
                if (!isNullHere && !isConstant(args.get(i))) {
                    return null;
                }
                carriesNull = carriesNull || isNullHere;
            }
            return carriesNull ? nullLiteral() : null;
        }
        if ("TRY_TO_NUMBER".equals(name)) {
            return args.size() == 1 && failsAsANumber(args.get(0)) ? nullLiteral() : null;
        }
        if (NULL_OVER_NULLS.contains(name)) {
            if (args.isEmpty()) {
                return null;
            }
            for (final Expression argument : args) {
                if (!isNull(foldedTo(argument))) {
                    return null;
                }
            }
            return nullLiteral();
        }
        if ("IFF".equals(name) && args.size() == 3) {
            final Expression condition = args.get(0);
            if (isTrue(condition)) {
                return textOrFolded(args.get(1));
            }
            return isNull(condition) || isFalse(condition) ? textOrFolded(args.get(2)) : null;
        }
        if ("DECODE".equals(name) && args.size() >= 3) {
            return foldedDecode(args);
        }
        return null;
    }

    /** DECODE over literals alone: the result of the first search its subject equals, a NULL equal to a NULL. */
    private static Expression foldedDecode(final List<Expression> args) {
        final Expression subject = args.get(0);
        if (!(subject instanceof LiteralExpression)) {
            return null;
        }
        for (int i = 1; i + 1 < args.size(); i += 2) {
            if (!(args.get(i) instanceof LiteralExpression)) {
                return null;
            }
            if (sameLiteral((LiteralExpression) subject, (LiteralExpression) args.get(i))) {
                return textOrFolded(args.get(i + 1));
            }
        }
        return args.size() % 2 == 0 ? textOrFolded(args.get(args.size() - 1)) : nullLiteral();
    }

    private static boolean sameLiteral(final LiteralExpression subject, final LiteralExpression search) {
        final Object left = subject.getValue();
        final Object right = search.getValue();
        if (left instanceof Number && right instanceof Number) {
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString())) == 0;
        }
        return Objects.equals(left, right);
    }

    /** A branch a settled conditional picks: a text literal as it is, or what the branch folds to. */
    private static Expression textOrFolded(final Expression branch) {
        if (branch instanceof LiteralExpression && ((LiteralExpression) branch).getType() == LiteralType.STRING) {
            return branch;
        }
        return foldedTo(branch);
    }

    /** An operand a fold reads beside a NULL: a literal, or something that folds to NULL itself. */
    private static boolean isConstant(final Expression expr) {
        return expr instanceof LiteralExpression || isNull(foldedTo(expr));
    }

    /** Whether a TRY conversion's source is a text that spells no number. */
    private static boolean failsAsANumber(final Expression source) {
        if (!(source instanceof LiteralExpression) || ((LiteralExpression) source).getType() != LiteralType.STRING) {
            return false;
        }
        try {
            new BigDecimal(String.valueOf(((LiteralExpression) source).getValue()).trim());
            return false;
        } catch (final NumberFormatException noNumber) {
            return true;
        }
    }

    private static boolean namesANumber(final String targetType) {
        final String name = targetType == null ? "" : targetType.trim().toUpperCase(Locale.ROOT);
        return name.startsWith("INT") || name.startsWith("NUMBER") || name.startsWith("DECIMAL")
            || name.startsWith("NUMERIC") || name.startsWith("BIGINT") || name.startsWith("SMALLINT")
            || name.startsWith("TINYINT") || name.startsWith("BYTEINT");
    }

    private static boolean isNull(final Expression expr) {
        return expr instanceof LiteralExpression && ((LiteralExpression) expr).getType() == LiteralType.NULL;
    }

    private static boolean isTrue(final Expression expr) {
        return expr instanceof LiteralExpression && Boolean.TRUE.equals(((LiteralExpression) expr).getValue());
    }

    private static boolean isFalse(final Expression expr) {
        return expr instanceof LiteralExpression && Boolean.FALSE.equals(((LiteralExpression) expr).getValue());
    }

    private static boolean isTrueOrNull(final Expression expr) {
        return isTrue(expr) || isNull(expr);
    }

    private static LiteralExpression nullLiteral() {
        return new LiteralExpression(null, LiteralType.NULL);
    }
}
