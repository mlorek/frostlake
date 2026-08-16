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

import dev.frostlake.types.DataType;

/**
 * An accumulator that needs the aggregated expression's DECLARED type, which its values cannot give
 * — a NUMBER(10,2) column and a literal 1.5 both arrive as a BigDecimal, and only the declaration says
 * which width the APPROX_TOP_K_ACCUMULATE state must report.
 *
 * <p>Both aggregate paths hand over the static type of the first argument before the first row is fed,
 * or null when the expression cannot be typed.
 */
public interface DeclaredArgumentAccumulator {

    void setDeclaredArgumentType(final DataType declared);
}
