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

import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.DecfloatTextNode;
import dev.frostlake.values.Decfloats;

import java.math.BigDecimal;

/**
 * A DECFLOAT written into a VARIANT or a text column. A runtime DECFLOAT is a bare decimal, as a NUMBER's is, so the
 * value alone cannot say how the column holds it; the written expression's static type does, and every write path -
 * INSERT … SELECT, INSERT … VALUES, UPDATE, UPDATE … FROM and both MERGE branches - asks here once it is known:
 *
 * <ul>
 *   <li>a VARIANT holds it as the DECFLOAT member {@code TO_VARIANT} makes, so {@code TYPEOF(v)} over such a row is
 *       DECFLOAT and it equals {@code TO_VARIANT(DECFLOAT '1e-50')} (live-verified for INSERT … SELECT);</li>
 *   <li>a text column holds the text its cast to VARCHAR gives, as every value written there does (live-verified):
 *       DECFLOAT 1e-50 is {@code 1e-50}, five characters, and 1e100 fits a VARCHAR(10);</li>
 *   <li>an exact NUMBER column holds it at its scale, and one too narrow refuses it naming the conversion, inside the
 *       write's DML sentence: {@code DML operation to table DN failed on column N with error: Decfloat out of
 *       representable range, operation: TO_NUMBER(1e38, 38, 0)} (live-verified for INSERT … SELECT and VALUES).</li>
 * </ul>
 *
 * A value of any other column, or of any other type, is written as it was.
 */
final class DecfloatWrites {

    private DecfloatWrites() {
    }

    /**
     * Whether a value of the static type {@code source} written into a column of {@code target} is held in the
     * column's own form of a DECFLOAT.
     *
     * @param source the written expression's static type, or null when it cannot be told
     * @param target the column's type
     * @return whether {@link #stored} reshapes it
     */
    static boolean reshapes(final DataType source, final DataType target) {
        return NumericType.isDecfloat(source) && receives(target);
    }

    /**
     * Whether a column of {@code target} takes a DECFLOAT in a way of its own: in its own form (see
     * {@link #holdsOwnForm}) or narrowed into an exact NUMBER (see {@link #narrows}).
     *
     * @param target the column's type
     * @return whether it does
     */
    static boolean receives(final DataType target) {
        return holdsOwnForm(target) || narrows(target);
    }

    /**
     * Whether a column of {@code target} is an exact NUMBER, which holds a DECFLOAT at its scale or refuses it in the
     * DECFLOAT's words (see {@link Decfloats#toNumber}).
     *
     * @param target the column's type
     * @return whether it is one
     */
    static boolean narrows(final DataType target) {
        return target instanceof NumericType && !NumericType.isApproximate(target) && !NumericType.isDecfloat(target)
            && ((NumericType) target).getScale() >= 0 && ((NumericType) target).getPrecision() > 0;
    }

    /**
     * Whether a column of {@code target} holds a DECFLOAT in a form of its own: a VARIANT or a text column.
     *
     * @param target the column's type
     * @return whether it does
     */
    static boolean holdsOwnForm(final DataType target) {
        return target instanceof VariantType || target instanceof StringType;
    }

    /**
     * A computed value as its column holds it, where {@code targets} marks the column as written a DECFLOAT (see
     * {@link #reshapes}); as it is otherwise.
     *
     * @param value   the computed value
     * @param targets the column type each DECFLOAT-written column has, by column index, or null when none is
     * @param index   the column's index
     * @return the value to write
     */
    static Object stored(final Object value, final DataType[] targets, final int index) {
        return targets != null && targets[index] != null ? stored(value, targets[index]) : value;
    }

    /**
     * A DECFLOAT as a VARIANT, a text or an exact NUMBER column holds it; any other value as it is. A DECFLOAT the
     * NUMBER has no room for is the cell's fault, raised where the write reads the row, inside its DML sentence.
     *
     * @param value  the computed value
     * @param target the column's type
     * @return the value to write
     */
    static Object stored(final Object value, final DataType target) {
        if (!(value instanceof Number) || value instanceof Double || value instanceof Float) {
            return value;
        }
        final BigDecimal held = Decfloats.of((Number) value, "TO_DECFLOAT");
        if (narrows(target)) {
            final NumericType number = (NumericType) target;
            try {
                return Decfloats.toNumber(held, number.getPrecision(), number.getScale());
            } catch (final RuntimeException tooWide) {
                return new DeferredFault(tooWide);
            }
        }
        if (target instanceof VariantType) {
            return DecfloatTextNode.variantOf(held);
        }
        return target instanceof StringType ? Decfloats.text(held) : value;
    }
}
