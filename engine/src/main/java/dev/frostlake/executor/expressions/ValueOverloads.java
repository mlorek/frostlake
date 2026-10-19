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

import dev.frostlake.metastore.model.Function;

import java.util.ArrayList;
import java.util.List;

/**
 * The overload of a user's function that argument values run, chosen as a scalar function's overload is: of the
 * overloads that take as many arguments and accept the values, the one whose conversions weigh least, and of several
 * that weigh alike the first in the order of their signatures (see UdfInvoker). An aggregate function's overloads are
 * chosen so for its rows' values: {@code pa(1)} runs {@code pa(INT)} and {@code pa('a')} runs {@code pa(VARCHAR)}
 * (live-verified).
 */
public final class ValueOverloads {

    private ValueOverloads() {
    }

    /**
     * The overload the values run.
     *
     * @param overloads the function's overloads
     * @param values    one value per argument, NULL where none decides
     * @return the overload, or null when none takes as many arguments
     */
    public static Function choose(final List<Function> overloads, final List<Object> values) {
        final List<Function> byCount = new ArrayList<>();
        for (final Function overload : overloads) {
            if (overload.getParameters().size() == values.size()) {
                byCount.add(overload);
            }
        }
        if (byCount.size() <= 1) {
            return byCount.isEmpty() ? null : byCount.get(0);
        }
        final List<Function> accepting = new ArrayList<>();
        for (final Function overload : byCount) {
            boolean accepts = true;
            for (int i = 0; accepts && i < values.size(); i++) {
                accepts = UdfInvoker.isCompatibleArgument(values.get(i), overload.getParameters().get(i).getDataType());
            }
            if (accepts) {
                accepting.add(overload);
            }
        }
        if (accepting.isEmpty()) {
            return byCount.get(0);
        }
        return UdfInvoker.firstBySignature(UdfInvoker.lightest(accepting, null, null, values));
    }
}
