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

import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.executor.SignedStorageWidth;
import dev.frostlake.values.NonFiniteDoubles;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;

/**
 * A NON-FINITE cast to the exact numeric family, refused the way the SOURCE's family words it.
 *
 * <p>An infinity or a NaN has no fixed-point form, so the cast cannot succeed — but the sentence is
 * not one sentence. Live words it TWO ways, and the difference is not the target (a bare
 * {@code ::NUMBER}, an {@code ::INT} and a {@code ::DECIMAL(10,2)} all say the same thing) but the
 * SOURCE:
 *
 * <ul>
 *   <li>from a VARIANT the type is the bare word {@code FIXED}, and a NaN is spelled
 *       {@code NaN};</li>
 *   <li>from a FLOAT the type is the full internal form of the TARGET, {@code FIXED[SB16](38,0)} for a bare
 *       NUMBER or an INT and {@code FIXED[SB8](10,2)} for a NUMBER(10,2) — the same shape every other
 *       out-of-range numeric refusal carries, its nullability the source's: {@code {nullable}} from a nullable
 *       column, {@code {not null}} from a literal — and the same NaN is spelled {@code nan}, in lower case.</li>
 * </ul>
 *
 * <p>Both spell an infinity {@code inf} / {@code -inf} rather than echoing the word as written or as
 * it renders. None of this is derivable from any one cell, which is why each is measured.
 */
public final class NonFiniteCastRefusal {

    /** The sentence both spellings open with — the same lead every out-of-range numeric refusal uses. */
    private static final String LEAD = "Number out of representable range: type FIXED";

    /** The precision of a bare NUMBER, an INT and every other spelling of NUMBER(38,0). */
    private static final int BARE_PRECISION = 38;

    private NonFiniteCastRefusal() {
    }

    /**
     * The refusal for a non-finite source cast to a bare NUMBER from a value that is never NULL, or null when this
     * cast is not one.
     *
     * @param value the cast's source
     * @param family the numeric family being cast to — only the exact one refuses, since a FLOAT
     *               target holds a non-finite perfectly well
     * @return the refusal, or null
     */
    public static RuntimeException refusalFor(final Object value, final String family) {
        return refusalFor(value, family, BARE_PRECISION, 0, false);
    }

    /**
     * The refusal for a non-finite source, or null when this cast is not one: {@code TO_DECIMAL('NaN'::FLOAT, 10,
     * 2)} is "Number out of representable range: type FIXED[SB8](10,2){not null}, value nan", and the same cast of a
     * nullable FLOAT column {@code FIXED[SB8](10,2){nullable}} (live-verified).
     *
     * @param value     the cast's source
     * @param family    the numeric family being cast to
     * @param precision the target's precision
     * @param scale     the target's scale
     * @param nullable  whether the source can be NULL
     * @return the refusal, or null
     */
    public static RuntimeException refusalFor(final Object value, final String family, final int precision,
                                              final int scale, final boolean nullable) {
        if (!"FIXED".equals(family)) {
            return null;
        }
        if (value instanceof VariantValue) {
            final JsonNode node = ((VariantValue) value).node();
            if (node == null || !node.isFloatingPointNumber() || node.isBigDecimal()) {
                return null;
            }
            final String word = variantWord(node.doubleValue());
            return word == null ? null : new RuntimeException(LEAD + ", value " + word);
        }
        if (!(value instanceof Number) || !NonFiniteDoubles.isNonFinite((Number) value)) {
            return null;
        }
        return new RuntimeException(NumericRangeRefusal.typedText(SignedStorageWidth.tagOfPrecision(precision),
            precision, scale, nullable, NumericRangeRefusal.nonFiniteWord(((Number) value).doubleValue())));
    }

    /** A VARIANT source's spelling: NaN keeps its camel case here, unlike the FLOAT one. */
    private static String variantWord(final double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        return Double.isInfinite(value) ? (value > 0 ? "inf" : "-inf") : null;
    }
}
