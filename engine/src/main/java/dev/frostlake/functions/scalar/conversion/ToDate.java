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
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.DateTimeType;

import java.util.List;

public class ToDate extends BuiltInFunction {
    public ToDate() { super("TO_DATE", DateTimeType.DATE); }

    protected ToDate(final String name) { super(name, DateTimeType.DATE); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        if (!acceptsNumericEpoch() && args.get(0) instanceof Number) {
            throw new RuntimeException("invalid type [" + getName() + "(" + args.get(0)
                + ")] for parameter 'TO_DATE'");
        }
        final String format = args.size() >= 2 && args.get(1) != null ? args.get(1).toString() : null;
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

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
