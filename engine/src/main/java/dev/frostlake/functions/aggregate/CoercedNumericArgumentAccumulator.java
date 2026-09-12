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

package dev.frostlake.functions.aggregate;

/**
 * An accumulator whose numeric answer can be asked of a VARCHAR or VARIANT argument. Live orders such
 * an argument by its OWN type — text by text, a VARIANT member by its kind — and converts each value to
 * a whole number, NUMBER(9,0), before it computes: MEDIAN over '1.5' and '2.25' is 2.000, and
 * PERCENTILE_DISC hands back the converted whole number. The values alone cannot tell this argument
 * from a numeric one ('2' and 2 both convert), so the declaration is handed over through this hook.
 */
public interface CoercedNumericArgumentAccumulator {

    /**
     * Tell the accumulator whether its argument is declared VARCHAR or VARIANT.
     *
     * @param coerced whether the aggregated expression is text or VARIANT, converted value by value
     */
    void setCoercedNumericArgument(boolean coerced);
}
