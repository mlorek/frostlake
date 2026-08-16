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

package dev.frostlake.functions.scalar.conversion;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.util.List;

public class ToDate extends BuiltInFunction {
    public ToDate() { super("TO_DATE", DateTimeType.DATE); }

    protected ToDate(final String name) { super(name, DateTimeType.DATE); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        if (!acceptsNumericEpoch() && isVariantNumber(args.get(0))) {
            // A VARIANT number is no date: live fails the variant's cast, "Failed to cast variant value
            // 1579046400 to DATE", where the DATE() alias reads it as an epoch (live-verified).
            throw new RuntimeException("Failed to cast variant value " + ((VariantValue) args.get(0)).text()
                + " to DATE");
        }
        if (!acceptsNumericEpoch() && args.get(0) instanceof Number) {
            throw new RuntimeException("invalid type [" + getName() + "(" + args.get(0)
                + ")] for parameter 'TO_DATE'");
        }
        if (args.get(0) instanceof Number && !isWholeNumber((Number) args.get(0))) {
            // The epoch reading takes a WHOLE number only: a fraction is converted to its text and
            // fails as a date — "Date '1.50' is not recognized" for a NUMBER(10,2), "Date '1.5'" for
            // a FLOAT — while 2.0 is the epoch second 2 (live-verified).
            throw new RuntimeException("Date '" + SharedFunctionHelpers.textOf(args.get(0))
                + "' is not recognized");
        }
        // A NULL format is a NULL answer (live-verified: TO_DATE('2020-01-15', NULL::VARCHAR) is NULL).
        if (args.size() >= 2 && args.get(1) == null) return null;
        final String format = args.size() >= 2 ? args.get(1).toString() : null;
        return SharedFunctionHelpers.parseDateWithFormat(args.get(0), format);
    }

    /**
     * Whether a NUMBER argument may be read as an epoch. Live-verified on a real account:
     * {@code TO_DATE(1631711999)} and {@code TRY_TO_DATE(1631711999)} both fail "invalid type
     * [TO_DATE(1631711999)] for parameter 'TO_DATE'", while the {@code DATE()} alias of the very same
     * function returns 2021-09-15 and {@code TO_TIMESTAMP(1631711999)} works — so the restriction
     * belongs to the TO_DATE spelling, not to the epoch conversion. A numeric STRING stays legal
     * ({@code TO_DATE('1631711999')} is 2021-09-15).
     */
    protected boolean acceptsNumericEpoch() { return false; }

    /** Whether the value is a VARIANT holding a number. */
    private static boolean isVariantNumber(final Object value) {
        return value instanceof VariantValue && ((VariantValue) value).node() != null
            && ((VariantValue) value).node().isNumber();
    }

    /** Whether a number carries no fraction — 2.0 and 86400 do, 1.50 does not. */
    private static boolean isWholeNumber(final Number value) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).stripTrailingZeros().scale() <= 0;
        }
        if (value instanceof Double || value instanceof Float) {
            final double d = value.doubleValue();
            return !Double.isNaN(d) && !Double.isInfinite(d) && d == Math.rint(d);
        }
        return true;
    }

    /** A VECTOR is refused as the conversion's own invalid type, as TO_CHAR refuses it. */
    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.INVALID_TYPE_PARAMETER
            : SemiStructuredRejection.NONE;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
