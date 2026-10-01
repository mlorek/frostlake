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

package dev.frostlake.functions.scalar.datetime;

import dev.frostlake.executor.expressions.IntervalUnit;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * A difference DATEDIFF is planned as, callable by its own name: {@code DATE_DIFF<KIND>IN<UNITS>(from, to)} is
 * {@code DATEDIFF(unit, from, to)} with both operands moved to the kind first — the same argument order, so
 * {@code DATE_DIFFDATEINDAYS(a, b)} counts from {@code a} to {@code b}. Moving to DATE drops a timestamp's time,
 * so {@code DATE_DIFFDATEINHOURS} counts whole days' hours; moving to TIME keeps only a timestamp's time of day.
 * The units are the plural words in full; a TIME takes those from HOURS down. The width follows kind and unit
 * (see {@link DateDifferenceWidths}). Like the other internal names these are left out of {@code SHOW FUNCTIONS}.
 */
public class PlannedDateDiff extends BuiltInFunction {

    private final IntervalUnit unit;
    private final String kind;
    private final DateDiff difference = new DateDiff();

    /**
     * The difference counted in one unit between operands moved to one kind.
     *
     * @param kind DATE, TIMESTAMP or TIME
     * @param unit the unit counted
     */
    public PlannedDateDiff(final String kind, final IntervalUnit unit) {
        super("DATE_DIFF" + kind + "IN" + unit.name() + "S", DateDifferenceWidths.of(kind, unit));
        this.unit = unit;
        this.kind = kind;
    }

    /**
     * The kind the operands are moved to.
     *
     * @return DATE, TIMESTAMP or TIME
     */
    public String kind() {
        return kind;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object from = PlannedDateAdd.plain(args.get(0));
        final Object to = PlannedDateAdd.plain(args.get(1));
        if (from == null || to == null) {
            return null;
        }
        return difference.evaluate(Arrays.asList((Object) unit.name(), moved(from), moved(to)));
    }

    @Override
    public int getMinArgCount() {
        return 2;
    }

    @Override
    public int getMaxArgCount() {
        return 2;
    }

    private Object moved(final Object value) {
        if (DateDifferenceWidths.DATE.equals(kind)) {
            return SharedFunctionHelpers.toLocalDate(value);
        }
        if (DateDifferenceWidths.TIME.equals(kind)) {
            return SharedFunctionHelpers.toLocalTime(value);
        }
        return value instanceof ZonedDateTime || value instanceof OffsetDateTime ? value
            : SharedFunctionHelpers.toLocalDateTime(value);
    }
}
