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
 * An accumulator whose result depends on whether its argument is APPROXIMATE, which the values alone
 * cannot always say. A FLOAT column holds a double, but a FLOAT-declared expression can still reach an
 * accumulator in an exact carrier — a value computed exactly, or one restored from an older snapshot —
 * and then a 1.5 from a FLOAT and a 1.5 from a NUMBER(2,1) are the same object; only the declared type
 * separates them.
 *
 * <p>The distinction decides a real difference: an interpolating percentile over an exact column adds
 * three decimals to its scale, while over a FLOAT there is no scale to add to and live answers the
 * plain double. Without this the FLOAT answer came back padded — 2.5000 for a live 2.5.
 *
 * <p>The caller sets the flag once, after building the accumulator and before feeding it.
 */
public interface ApproximateAwareAccumulator {

    /**
     * Tell the accumulator what its argument's declared type is.
     *
     * @param approximate whether the aggregated expression is declared FLOAT / DOUBLE / REAL
     */
    void setApproximateArgument(boolean approximate);
}
