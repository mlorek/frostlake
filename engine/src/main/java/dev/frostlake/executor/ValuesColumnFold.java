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

package dev.frostlake.executor;

import dev.frostlake.executor.expressions.BetweenExpression;
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.InExpression;
import dev.frostlake.executor.expressions.IsNullExpression;
import dev.frostlake.executor.expressions.LikeAnyAllExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.QuantifiedComparisonExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.expressions.ValueCaster;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.DeclaredTypeFold;
import dev.frostlake.types.NumericLiteralTypes;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.math.BigDecimal;
import java.util.List;

/**
 * The type a multi-row VALUES gives each of its columns, and each row's value converted to it
 * (live-verified). A column's rows fold in written order the way a UNION ALL folds its arms (see
 * {@link DeclaredTypeFold#combine}). Numbers meet at their supertype and an approximate one swallows an
 * exact one; strings meet at the longest; temporals widen; and a leading BOOLEAN absorbs a number or a
 * string that follows it. Three rules are the VALUES clause's own:
 * <ul>
 *   <li>a string beside a number or a temporal joins that family. A string literal contributes the
 *       number it spells ({@code '7.50'} is NUMBER(2,1)), and any other string NUMBER(18,5);</li>
 *   <li>a predicate's BOOLEAN folds with no other family, from either side: {@code (1 = 1), (2)} is
 *       refused, while {@code (TRUE), (2)} is BOOLEAN;</li>
 *   <li>a row whose type does not fold is refused, naming that row's type: {@code (1), (TRUE)} is
 *       "Invalid data type [BOOLEAN] in VALUES clause".</li>
 * </ul>
 * The column's type match then judges the folded type, and every row's value is converted to it before
 * it is written. So {@code (1), (2.5)} writes 1.0, {@code (TRUE), (2)} fails its row with "Boolean value
 * '2' is not recognized", and {@code ('x'), (1)} with "Numeric value 'x' is not recognized". A row the
 * static channel cannot type takes no part: the fold never guesses.
 */
final class ValuesColumnFold {

    /** What a string that spells no number contributes beside one. */
    private static final NumericType UNSPELLED_STRING_NUMBER = new NumericType("NUMBER", 18, 5);

    private ValuesColumnFold() {
    }

    /**
     * One column's rows folded in written order.
     *
     * @param cells each row's expression for the column
     * @param types each row's static type: null for a NULL, a DEFAULT or a value the channel cannot type
     * @return the column's type, or null when no row carries one
     */
    static DataType fold(final List<Expression> cells, final List<DataType> types) {
        DataType held = null;
        boolean heldIsPredicate = false;
        // Every string row seen so far, as the number it would contribute beside one.
        NumericType stringsAsNumber = null;
        for (int i = 0; i < types.size(); i++) {
            final DataType next = types.get(i);
            if (next == null) {
                continue;
            }
            final Expression cell = cells.get(i);
            final boolean predicate = isPredicate(cell);
            if (held == null) {
                held = next;
                heldIsPredicate = predicate;
            } else {
                final DataType folded = foldPair(held, heldIsPredicate, stringsAsNumber, next, predicate, cell);
                if (folded == null) {
                    throw new RuntimeException(SqlCompilationError.of("Invalid data type ["
                        + ColumnTypeFamilies.spell(next) + "] in VALUES clause"));
                }
                held = folded;
            }
            if (next instanceof StringType) {
                final NumericType spelled = spelledNumber(cell);
                stringsAsNumber = stringsAsNumber == null ? spelled
                    : DeclaredTypeFold.numericSupertype(stringsAsNumber, spelled);
            }
        }
        return held;
    }

    /** The type held so far folded with the next row's, or null when the two do not fold. */
    private static DataType foldPair(final DataType held, final boolean heldIsPredicate,
                                     final NumericType stringsAsNumber, final DataType next,
                                     final boolean nextIsPredicate, final Expression nextCell) {
        final boolean heldBoolean = held instanceof BooleanType;
        final boolean nextBoolean = next instanceof BooleanType;
        if (heldBoolean && nextBoolean) {
            return held;
        }
        if (heldBoolean && heldIsPredicate || nextBoolean && nextIsPredicate) {
            return null;
        }
        if (held instanceof NumericType && next instanceof NumericType) {
            return numericFold((NumericType) held, (NumericType) next);
        }
        if (held instanceof StringType && next instanceof NumericType) {
            return numericFold(stringsAsNumber != null ? stringsAsNumber : UNSPELLED_STRING_NUMBER,
                (NumericType) next);
        }
        if (held instanceof NumericType && next instanceof StringType) {
            return numericFold((NumericType) held, spelledNumber(nextCell));
        }
        if (held instanceof StringType && next instanceof DateTimeType) {
            return next;
        }
        if (held instanceof DateTimeType && next instanceof StringType) {
            return held;
        }
        return DeclaredTypeFold.combine(held, next);
    }

    /** Two numbers folded: an approximate one swallows an exact one, and two exact ones meet at the supertype. */
    private static NumericType numericFold(final NumericType left, final NumericType right) {
        if (NumericType.isApproximate(left)) {
            return left;
        }
        if (NumericType.isApproximate(right)) {
            return right;
        }
        return DeclaredTypeFold.numericSupertype(left, right);
    }

    /** The number a string row contributes beside one: a literal's own spelling, else NUMBER(18,5). */
    private static NumericType spelledNumber(final Expression cell) {
        if (cell instanceof LiteralExpression && ((LiteralExpression) cell).getType() == LiteralType.STRING
                && ((LiteralExpression) cell).getValue() != null) {
            try {
                return NumericLiteralTypes.forDecimal(
                    new BigDecimal(String.valueOf(((LiteralExpression) cell).getValue())));
            } catch (final NumberFormatException notANumber) {
                return UNSPELLED_STRING_NUMBER;
            }
        }
        return UNSPELLED_STRING_NUMBER;
    }

    /**
     * Whether a row's BOOLEAN comes from a predicate: a comparison, a LIKE, an IN, a BETWEEN, an IS, an
     * EXISTS, or a NOT, AND or OR over one. {@code TRUE AND TRUE} and {@code NOT FALSE} are plain booleans.
     */
    private static boolean isPredicate(final Expression cell) {
        if (cell instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) cell;
            switch (binary.getOperator()) {
                case EQUAL:
                case NOT_EQUAL:
                case LESS_THAN:
                case LESS_THAN_OR_EQUAL:
                case GREATER_THAN:
                case GREATER_THAN_OR_EQUAL:
                case LIKE:
                case ILIKE:
                case NOT_LIKE:
                case NOT_ILIKE:
                    return true;
                case AND:
                case OR:
                    return isPredicate(binary.getLeft()) || isPredicate(binary.getRight());
                default:
                    return false;
            }
        }
        if (cell instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) cell;
            return unary.getOperator() == UnaryOperator.EXISTS
                || unary.getOperator() == UnaryOperator.NOT && isPredicate(unary.getOperand());
        }
        return cell instanceof InExpression || cell instanceof BetweenExpression
            || cell instanceof IsNullExpression || cell instanceof LikeAnyAllExpression
            || cell instanceof QuantifiedComparisonExpression;
    }

    /**
     * A row's value converted to its column's folded type; a value already of that type is returned as is.
     * A number becomes a BOOLEAN only when it is 0 or 1, and a BOOLEAN becomes the text TRUE or FALSE;
     * every other conversion is the cast's.
     *
     * @param value    the row's value
     * @param cellType the row's own static type, or null when the channel cannot type it
     * @param folded   the column's folded type, or null
     * @return the converted value
     */
    static Object convert(final Object value, final DataType cellType, final DataType folded) {
        if (value == null || cellType == null || folded == null || !needsConversion(cellType, folded)) {
            return value;
        }
        if (folded instanceof BooleanType && value instanceof Number) {
            return numberAsBoolean(value);
        }
        if (folded instanceof StringType && value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? "TRUE" : "FALSE";
        }
        return ValueCaster.castValue(value, ColumnTypeFamilies.spell(folded));
    }

    /** Whether a value of the row's own type differs from the folded type's representation. */
    private static boolean needsConversion(final DataType cellType, final DataType folded) {
        if (cellType instanceof StringType && folded instanceof StringType
                || cellType instanceof BooleanType && folded instanceof BooleanType
                || cellType instanceof BinaryType && folded instanceof BinaryType) {
            return false;
        }
        if (cellType instanceof NumericType && folded instanceof NumericType) {
            if (NumericType.isApproximate(folded)) {
                return !NumericType.isApproximate(cellType);
            }
            return NumericType.isApproximate(cellType)
                || ((NumericType) cellType).getScale() != ((NumericType) folded).getScale();
        }
        if (cellType instanceof DateTimeType && folded instanceof DateTimeType) {
            return !cellType.getName().equalsIgnoreCase(folded.getName());
        }
        return true;
    }

    /** A number read as a BOOLEAN the way a VALUES row converts it: 0 and 1 only. */
    private static Boolean numberAsBoolean(final Object value) {
        final String text = SharedFunctionHelpers.textOf(value);
        try {
            final BigDecimal number = value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(text);
            if (number.signum() == 0) {
                return Boolean.FALSE;
            }
            if (number.compareTo(BigDecimal.ONE) == 0) {
                return Boolean.TRUE;
            }
        } catch (final NumberFormatException notDecimal) {
            // A non-finite double is no 0 or 1 either.
        }
        throw new RuntimeException("Boolean value '" + text + "' is not recognized");
    }
}
